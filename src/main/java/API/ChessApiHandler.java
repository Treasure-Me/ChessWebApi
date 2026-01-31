package API;

import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import io.javalin.http.Context;
import logic.Board;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import static API.ChessServerAPI.logger;

public class ChessApiHandler {

    public static ArrayList<Player> playersLoggedIn = new ArrayList<>();
    private static ArrayList<Player> playersReadyForGame = new ArrayList<>();

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

    @Nullable
    public static Player getPlayerLoggedIn(Context context) {
        Player player = context.sessionAttribute("user");
        if (player != null) {
            context.json(Map.of("ok", true, "username", player.getUsername()));
        } else {
            context.status(401).json(Map.of("ok", false));
        }
        return player;
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
                returnGameInfo(context, game, existingGameId);
                return;
            }else{
                GameManager.removeGame(existingGameId);
            }
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
        Player player = context.sessionAttribute("user");
        String gameId = context.pathParam("gameId");
        String action = context.pathParam("action");

        chessMatchHandler game = GameManager.getGame(gameId);
        if (game == null) { context.status(404).json(Map.of("error", "Game not found")); return; }

        switch (action) {
            case "state":
                context.json(game.getGameState());
                break;
            case "move":
                MoveRequest req = context.bodyAsClass(MoveRequest.class);
                context.json(game.processMove(player, req.from, req.to));
                break;
            case "legal-moves":
                LegalRequest lReq = context.bodyAsClass(LegalRequest.class);
                context.json(game.getLegalMoves(lReq.square, lReq.piece));
                break;
            case "resign":
                if (game.processResignation(player)) context.json(Map.of("success", true));
                else context.status(403).json(Map.of("error", "Not authorized"));
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

    static class MoveRequest { public String from; public String to; }
    static class LegalRequest { public String square; public String piece; }
}