package API;

import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import io.javalin.http.Context;
import logic.Board;
import logic.ChessGame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static API.ChessServerAPI.logger;

public class ChessApiHandler {

    ChessGame chessGame = new ChessGame();
    public static ArrayList<Player> playersLoggedIn = new ArrayList<>();
    private static ArrayList<Player> playersReadyForGame = new ArrayList<>();
    private static ArrayList<Player> playersInGameLobby = new ArrayList<>();

    /**
     * login user to site
     *
     * @param context The Javalin Context for the HTTP GET Request
     */
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

            // Login successful
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
        String username = guestID.toString();
        try {
            if (username == null || username.trim().isEmpty()) {
                context.status(400).json(Map.of("success", false, "message", "Username is required"));
                return;
            }
            Player player = new Player(guestID);

            // Login successful
            context.sessionAttribute("user", player);
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

    @Nullable
    public static Player getPlayerLoggedIn(Context context) {
        Player player = context.sessionAttribute("user");
        assert player != null;
        context.json(
                Map.of(
                        "ok", true,
                        "message", STR."Player logged in is \{player.getUsername()}",
                        "username", player.getUsername()
                )
        );
        return player;
    }

    public static void loadLoginPage(Context context) {
        context.redirect("/loginPage.html");
    }

    public static void newMatch(Context context) {
        Player player = getPlayerLoggedIn(context);
        playersReadyForGame.add(player);
        if (playersReadyForGame.size() >= 2){
            chessMatchHandler game = new chessMatchHandler(playersReadyForGame.get(0), playersReadyForGame.get(1));
            playersInGameLobby.add(playersReadyForGame.get(0));
            playersInGameLobby.add(playersReadyForGame.get(1));
            playersReadyForGame.remove(0);
            playersReadyForGame.remove(0);
            game.start(5001);
        }else{
            context.json(Map.of("status", "waiting for player"));
        }
    }

    /**
     * Make a move on the board
     *
     * @param context The Javalin Context for the HTTP POST Request
     */


//    public static void newGame(Context context, Board board){
//        chessMatchHandler.main(null);
//    }


}