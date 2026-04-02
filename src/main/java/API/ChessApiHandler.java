package API;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static API.ChessServerAPI.logger;
import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import API.utility.WebSocketBroadcaster;
import ChessAlgorithms.EngineCalculations;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import logic.Board;

public class ChessApiHandler {

    public static ArrayList<Player> playersLoggedIn = new ArrayList<>();
    private static ArrayList<Player> playersReadyForGame = new ArrayList<>();
    private static ArrayList<String> boardStates = new ArrayList<>();
    private static ArrayList<String> bots = new ArrayList<>();

    public static void login(Context context) {
        try {
            String username = context.formParam("username");
            String password = context.formParam("password");

            if (username == null || username.trim().isEmpty()) {
                context.status(400).json(Map.of("success", false, "message", "Username is required"));
                return;
            }

            Optional<Player> optionalPlayer = PlayerDaoImplementation.findPlayerByUsername(username.trim());

            if (optionalPlayer.isEmpty()) {
                context.status(404).json(Map.of("success", false, "message", "User not found"));
                return;
            }

            Player player = optionalPlayer.get();

            if (!player.getPassword().equals(password)) {
                context.status(401).json(Map.of("success", false, "message", "Invalid password"));
                return;
            }

            context.sessionAttribute("user", player);
            playersLoggedIn.add(player);
            context.json(Map.of("success", true, "message", "Login successful", "username", player.getUsername()));

        } catch (Exception e) {
            logger.error("Login error", e);
            context.status(500).json(Map.of("success", false, "message", "Internal server error"));
        }
    }

    public static void loginGuest(Context context) {
        UUID guestID = UUID.randomUUID();
        String username = "Guest-" + guestID.toString();
        try {
            Player player = new Player(guestID);
            context.sessionAttribute("user", player);
            context.json(Map.of("success", true, "message", "Login successful", "username", username));
        } catch (Exception e) {
            logger.error("Login error", e);
            context.status(500).json(Map.of("success", false, "message", "Internal server error"));
        }
    }

    public static void getPlayerLoggedIn(Context context) {
        Player player = context.sessionAttribute("user");
        if (player != null) {
            context.json(Map.of("ok", true, "username", player.getUsername()));
        } else {
            context.status(401).json(Map.of("ok", false));
        }
    }

    public static void loadLoginPage(Context context) {
        context.redirect("/loginPage.html");
    }

    public static void newMatch(Context context) {
        Player player = context.sessionAttribute("user");
        if (player == null) { context.status(401).json(Map.of("error", "Not logged in")); return; }

        String existingGameId = GameManager.findGameIdByPlayer(player.getUsername());
        if (existingGameId != null) {
            chessMatchHandler game = GameManager.getGame(existingGameId);
            Board gameBoard = game.getBoard();
            if (gameBoard.getGameState().equals("ongoing")) {
                WebSocketBroadcaster.broadcastGameUpdate(existingGameId, game.getGameState());
                returnGameInfo(context, game, existingGameId);
                return;
            } else {
                GameManager.removeGame(existingGameId);
            }
        }

        boolean isDevBot = context.queryParam("devbot") != null && context.queryParam("devbot").equals("true");
        boolean isBot = context.queryParam("bot") != null && context.queryParam("bot").equals("true");
        boolean isAnalysis = context.queryParam("analysis") != null && context.queryParam("analysis").equals("true");

        if (isBot) {
            Player stockfish = new Player(UUID.randomUUID());
            stockfish.setUsername("Stockfish");


            String gameId = java.util.UUID.randomUUID().toString();

            chessMatchHandler newGame = new chessMatchHandler(gameId, player, stockfish);
            GameManager.addGame(gameId, newGame);

            returnGameInfo(context, newGame, gameId);
            return;
        }else if (isDevBot){
            Player devBot = new Player(UUID.randomUUID());
            devBot.setUsername("DevBot");


            String gameId = java.util.UUID.randomUUID().toString();

            chessMatchHandler newGame = new chessMatchHandler(gameId, player, devBot);
            GameManager.addGame(gameId, newGame);

            returnGameInfo(context, newGame, gameId);
            return;
        } else if (isAnalysis){
            String gameId = java.util.UUID.randomUUID().toString();
            NewGame newGameBody = context.bodyAsClass(NewGame.class);
            String fen = newGameBody.fen;

            chessMatchHandler newGame = new chessMatchHandler(gameId, player);

            if (fen != null){
                Board board = new Board(fen);
                newGame.setNewBoard(board);
            }

            GameManager.addGame(gameId, newGame);
            returnGameInfo(context, newGame, gameId);
            return;
        }

        if (!playersReadyForGame.contains(player)) {
            playersReadyForGame.add(player);
        }
        if (playersReadyForGame.size() >= 2) {
            Player p1 = playersReadyForGame.remove(0);
            Player p2 = playersReadyForGame.remove(0);

            String gameId = java.util.UUID.randomUUID().toString();
            chessMatchHandler newGame = new chessMatchHandler(gameId, p1, p2);
            GameManager.addGame(gameId, newGame);

            returnGameInfo(context, newGame, gameId);
        } else {
            context.json(Map.of("status", "waiting for player"));
        }
    }

    private static void returnGameInfo(Context ctx, chessMatchHandler game, String gameId) {
        String white = "", black = "";
        for (Map.Entry<Player, String> e : game.players.entrySet()) {
            if (e.getValue().equals("w")) white = e.getKey().getUsername();
            else black = e.getKey().getUsername();
        }
        ctx.json(Map.of("status", "match_started", "white", white, "black", black, "gameId", gameId));
    }

    public static void handleGameAction(Context context) {
        Player sessionUser = context.sessionAttribute("user");
        String gameId = context.pathParam("gameId");
        String action = context.pathParam("action");

        chessMatchHandler game = GameManager.getGame(gameId);

        if (game == null) { 
            context.status(404).json(Map.of("error", "Game not found"));
            return; 
        }

        switch (action) {
            case "state":
                context.json(game.getGameState());
                break;

            case "move":
                MoveRequest req = context.bodyAsClass(MoveRequest.class);
                Player humanInGame = null;
                Player botInGame = null;

                for (Map.Entry<Player, String> entry : game.players.entrySet()) {
                    Player p = entry.getKey();
                    if (p.getUsername().equals(sessionUser.getUsername())) humanInGame = p;
                    if (p.getUsername().equals("Stockfish") || p.getUsername().equals("DevBot")) botInGame = p;
                }
                
                if (req.playerUsername == null) {

                    if (humanInGame == null) {
                        context.status(403).json(Map.of("success", false, "message", "You are not in this game"));
                        return;
                    }

                    if (botInGame == null) {
                        context.status(500).json(Map.of("error", "Bot missing from game"));
                        return;
                    }
                }

                    

                Object result = null;
                boolean success = false;

                try {

                    if (req.playerUsername == null){
                        result = game.processMove(botInGame, req.from, req.to, req.promotion);
                    }else{
                        result = game.processMove(humanInGame, req.from, req.to, req.promotion);
                    }
                    

                    if (result instanceof Map) {
                        Map<?, ?> rMap = (Map<?, ?>) result;
                        if (rMap.containsKey("success") && Boolean.TRUE.equals(rMap.get("success"))) {
                            success = true;
                            Map<String, Object> state = game.getGameState();
                            boardStates.add((String) state.get("fen"));
                        }
                    }
                } catch (Exception e) {
                }
                System.out.println(game.getBoard().getFENStringPosition());
                context.json(result != null ? result : Map.of("success", false));
                break;

            case "legal-moves":
                LegalRequest lReq = context.bodyAsClass(LegalRequest.class);
                context.json(game.getLegalMoves(lReq.square, lReq.piece));
                break;
            case "best-move":
//                int depth = context.bodyAsClass(Integer.class);
                Board board = game.getBoard();
                String move = EngineCalculations.iterativeDeepening(board, 10000).strip();
                if (move.isEmpty()){
                    context.json(Map.of("success", false));
                }else {
                    context.json(Map.of("success", true,
                                        "bestMove", move));
                }
                break;
            case "resign":
                Player resigner = null;
                for (Player p : game.players.keySet()) {
                    if (p.getUsername().equals(sessionUser.getUsername())) resigner = p;
                }
                if (resigner != null && game.processResignation(resigner)){
                    context.json(Map.of("success", true));
                } else{
                    context.status(403).json(Map.of("error", "Not authorized"));
                }
                break;

            default:
                context.status(400).json(Map.of("error", "Unknown action"));
        }
    }

    public static void resignMatch(Context context) {
        Player player = context.sessionAttribute("user");
        if (player == null) { context.status(401).json(Map.of("error", "Not logged in")); return; }

        String gId = GameManager.findGameIdByPlayer(player.getUsername());
        if (gId != null) {
            chessMatchHandler game = GameManager.getGame(gId);
            game.processResignation(player);
            context.json(Map.of("success", true));
        } else {
            context.status(400).json(Map.of("error", "No active game found"));
        }
    }

    public static void getBoardStates(Context context){
        context.json(boardStates);
    }

    public static void createAccount(Context context) {
        try {
            String username = context.formParam("username");
            String password = context.formParam("password");
            String email = context.formParam("email");

            if (username == null || username.trim().isEmpty()) {
                context.status(400).json(Map.of("success", false, "message", "Username is required"));
                return;
            }

            Player player = new Player(email, username, password);

            PlayerDaoImplementation.savePlayer(player);

            context.json(Map.of("success", true, "message", "Registration successful", "username", player.getUsername()));

        } catch (Exception e) {
            logger.error("Login error", e);
            context.status(500).json(Map.of("success", false, "message", "Internal server error"));
        }
    }

    public static void logout(Context context) {
        Player player = context.sessionAttribute("user");
        if (player != null) {
            playersLoggedIn.removeIf(p -> p.getUsername().equals(player.getUsername()));
        }

        context.req().getSession().invalidate();
        context.json(Map.of("success", true, "message", "Logged out"));
    }

    static class MoveRequest { 
        public String from; 
        public String to;
        public String promotion;
        public String playerUsername;
    }
    static class LegalRequest { public String square; public String piece; }
    static class NewGame { public String fen; }
}