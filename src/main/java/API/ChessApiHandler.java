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
    private static chessMatchHandler activeGameHandler;

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
            context.json(Map.of(
                    "success", true,
                    "message", "Login successful",
                    "username", player.getUsername()
            ));

        } catch (Exception e) {
            logger.error("Login error", e);
            context.status(500).json(Map.of("success", false, "message", "Internal server error"));
        }
    }

    public static void loginGuest(Context context) {
        UUID guestID = UUID.randomUUID();
        String username = "Guest-" + guestID.toString().substring(0, 8);
        try {
            Player player = new Player(guestID);

            context.sessionAttribute("user", player);
            context.json(Map.of(
                    "success", true,
                    "message", "Login successful",
                    "username", username
            ));

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
        if (player == null) {
            context.status(401).json(Map.of("error", "Not logged in"));
            return;
        }

        if (activeGameHandler != null && activeGameHandler.players.containsKey(player)) {
            Map<Player, String> assignments = activeGameHandler.players;

            Board gameBoard = activeGameHandler.getBoard();

            if (gameBoard.getGameState().equals("ongoing")){
                String whiteName = "", blackName = "";
                for (Map.Entry<Player, String> entry : assignments.entrySet()) {
                    if (entry.getValue().equals("w")) whiteName = entry.getKey().getUsername();
                    else blackName = entry.getKey().getUsername();
                }

                context.json(Map.of(
                        "status", "match_started",
                        "white", whiteName,
                        "black", blackName,
                        "port", 5001
                ));
                return;
            }
        }

        if (!playersReadyForGame.contains(player)) {
            playersReadyForGame.add(player);
        }

        if (playersReadyForGame.size() >= 2) {
            Player p1 = playersReadyForGame.remove(0);
            Player p2 = playersReadyForGame.remove(0);

            if (activeGameHandler != null) {
                activeGameHandler.stop();
            }

            activeGameHandler = new chessMatchHandler(p1, p2);
            activeGameHandler.start(5001);

            Map<Player, String> assignments = activeGameHandler.players;
            String whiteName = assignments.get(p1).equals("w") ? p1.getUsername() : p2.getUsername();
            String blackName = assignments.get(p1).equals("b") ? p1.getUsername() : p2.getUsername();

            context.json(Map.of(
                    "status", "match_started",
                    "white", whiteName,
                    "black", blackName,
                    "port", 5001
            ));
        } else {
            context.json(Map.of("status", "waiting for player"));
        }
    }

    public static void resignMatch(Context context) {
        Player player = context.sessionAttribute("user");
        if (player == null) {
            context.status(401).json(Map.of("error", "Not logged in"));
            return;
        }

        if (activeGameHandler == null) {
            context.status(400).json(Map.of("error", "No active game found"));
            return;
        }

        boolean success = activeGameHandler.processResignation(player);

        if (success) {
            context.json(Map.of("success", true));
        } else {
            context.status(403).json(Map.of("error", "You are not part of the active game"));
        }
    }

    public static void stopMatch(){
        activeGameHandler.stop();
    }
}