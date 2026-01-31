package API;

import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import io.javalin.http.Context;
import logic.Board;

import java.util.*;
import java.util.stream.Stream;

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

        // Check for existing game
        String existingGameId = GameManager.findGameIdByPlayer(player.getUsername());
        if (existingGameId != null) {
            chessMatchHandler game = GameManager.getGame(existingGameId);
            Board gameBoard = game.getBoard();
            if (gameBoard.getGameState().equals("ongoing")) {
                returnGameInfo(context, game, existingGameId);
                return;
            } else {
                GameManager.removeGame(existingGameId);
            }
        }

        // --- BOT GAME CREATION ---
        boolean isBot = context.queryParam("bot") != null && context.queryParam("bot").equals("true");
        if (isBot) {
            // Create a dummy Stockfish player
            Player stockfish = new Player(UUID.randomUUID());

            String gameId = java.util.UUID.randomUUID().toString();
            // Start match immediately: Player vs Stockfish
            chessMatchHandler newGame = new chessMatchHandler(gameId, player, stockfish);
            GameManager.addGame(gameId, newGame);

            returnGameInfo(context, newGame, gameId);
            return;
        }
        // -------------------------

        // Normal Queue Logic
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
        if (game == null) { context.status(404).json(Map.of("error", "Game not found")); return; }

        switch (action) {
            case "state":
                context.json(game.getGameState());
                break;

            case "move":
                MoveRequest req = context.bodyAsClass(MoveRequest.class);

                System.out.println("--- PROCESSING MOVE ---");
                System.out.println("User: " + sessionUser.getUsername() + " | Move: " + req.from + " -> " + req.to);

                // 1. IDENTIFY PLAYERS
                Player humanInGame = null;
                Player botInGame = null;

                // Loop through your ConcurrentHashMap 'players'
                for (Map.Entry<Player, String> entry : game.players.entrySet()) {
                    Player p = entry.getKey();
                    if (p.getUsername().equals(sessionUser.getUsername())) humanInGame = p;
                    if (p.getUsername().equals("Stockfish")) botInGame = p;
                }

                if (humanInGame == null) {
                    context.status(403).json(Map.of("success", false, "message", "You are not in this game"));
                    return;
                }

                // 2. SELF-HEALING: ADD MISSING BOT
                // Your chessMatchHandler.java manages players in a Map. We must add the bot there.
                if (botInGame == null) {
                    System.out.println(">> WARNING: Stockfish missing. Auto-Adding now.");
                    botInGame = new Player(UUID.randomUUID());

                    // Find Human's color ("w" or "b")
                    String humanColor = game.players.get(humanInGame);
                    String botColor = humanColor.equals("w") ? "b" : "w";

                    // Add Bot to the game map
                    game.players.put(botInGame, botColor);

                    // Force state to "ongoing" via the BOARD object (since handler doesn't have setter)
                    // Note: This relies on your Board class having setGameState, which is implied by processResignation
                    game.getBoard().setGameState("ongoing");

                    System.out.println(">> Stockfish added as " + botColor);
                }

                // 3. EXECUTE MOVE (Try-Fail-Retry)
                Object result = null;
                boolean success = false;

                try {
                    // Attempt 1: Try as Human
                    System.out.println("Attempt 1: Moving as Human (" + humanInGame.getUsername() + ")...");
                    result = game.processMove(humanInGame, req.from, req.to);

                    if (result instanceof Map) {
                        Map<?, ?> rMap = (Map<?, ?>) result;
                        if (rMap.containsKey("success") && Boolean.TRUE.equals(rMap.get("success"))) {
                            success = true;
                            System.out.println(" > Success!");
                        }
                    }
                } catch (Exception e) {
                    System.out.println(" > Attempt 1 Crashed: " + e.getMessage());
                }

                // Attempt 2: If Human failed, RETRY as Bot
                if (!success && botInGame != null) {
                    System.out.println("Attempt 1 failed. Retrying as Bot (" + botInGame.getUsername() + ")...");
                    try {
                        result = game.processMove(botInGame, req.from, req.to);
                        System.out.println(" > Bot Move Result: " + result);
                    } catch (Exception e) {
                        System.out.println(" > Attempt 2 Crashed: " + e.getMessage());
                        result = Map.of("success", false, "message", "Bot move failed: " + e.getMessage());
                    }
                }

                context.json(result != null ? result : Map.of("success", false));
                break;

            case "legal-moves":
                LegalRequest lReq = context.bodyAsClass(LegalRequest.class);
                context.json(game.getLegalMoves(lReq.square, lReq.piece));
                break;

            case "resign":
                Player resigner = null;
                for (Player p : game.players.keySet()) {
                    if (p.getUsername().equals(sessionUser.getUsername())) resigner = p;
                }
                if (resigner != null && game.processResignation(resigner)) context.json(Map.of("success", true));
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

    // Add this method to ChessApiHandler.java
    public static void logout(Context context) {
        Player player = context.sessionAttribute("user");
        if (player != null) {
            playersLoggedIn.removeIf(p -> p.getUsername().equals(player.getUsername()));
        }

        context.req().getSession().invalidate();
        context.json(Map.of("success", true, "message", "Logged out"));
    }

    static class MoveRequest { public String from; public String to; }
    static class LegalRequest { public String square; public String piece; }
}