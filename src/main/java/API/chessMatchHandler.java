package API;

import java.util.*;
import java.util.random.RandomGenerator;

import API.utility.Player;
import io.javalin.Javalin;
import io.javalin.http.Context;
import logic.Board;
import logic.ChessGame;

public class chessMatchHandler {
    private final Javalin server;
    private Board board = new Board();
    public final Map<Player, String> players = new HashMap<>();
    private final UUID id;

    public chessMatchHandler(Player player1, Player player2) {
        RandomGenerator random = RandomGenerator.getDefault();
        String playerOneColour = List.of("b","w").get(random.nextInt(2));
        players.put(player1, playerOneColour);
        String playerTwoColour = playerOneColour.equals("w") ? "b":"w";
        players.put(player2, playerTwoColour);
        this.id = UUID.randomUUID();

        server = Javalin.create(config -> {
                    // Enable CORS so Port 5000 (Frontend) can fetch data from Port 5001 (Backend)
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.reflectClientOrigin = true;
                        });
                    });
                })
                .before(ctx -> {
                    if (ctx.contentType() == null) {
                        ctx.contentType("application/json");
                    }
                });

        this.server.post("/api/move", this::sendMove);
        this.server.post("/api/legal-moves", this::legalMoves);
        // We reuse /api/load-fen for state polling
        this.server.post("/api/load-fen", this::loadGameState);
        this.server.post("/api/resign", this::resignGame);
    }

    // Called by JS Polling Interval
    private void loadGameState(Context context) {
        String turn = board.getFENStringPosition().split(" ")[1];

        context.json(Map.of(
                "newBoard", board.getCleanSquares(), // Requires Board.getCleanSquares() returning String[][]
                "turn", turn,
                "fen", board.getFENStringPosition(),
                "status", board.getGameState()
        ));
    }

    private void legalMoves(Context context) {
        // Read JSON body
        var body = context.bodyAsClass(Map.class);
        String fromSquare = (String) body.get("square");
        String pieceType = (String) body.get("piece");

        // Generate destinations
        ArrayList<String> destinations = ChessGame.generateAllPossibleMoves(board, fromSquare, pieceType);

        // Transform for Frontend: ["e4"] -> [{"from":"e2", "to":"e4"}]
        List<Map<String, String>> movesForFrontend = new ArrayList<>();
        for (String dest : destinations) {
            movesForFrontend.add(Map.of("from", fromSquare, "to", dest));
        }

        context.json(movesForFrontend);
    }

    private void sendMove(Context context) {
        var moveRequest = context.bodyAsClass(Map.class);
        String from = (String) moveRequest.get("from");
        String to = (String) moveRequest.get("to");

        ChessGame.playGame(board, from + "-" + to);

        String turn = board.getFENStringPosition().split(" ")[1];

        if (board.getGameState().equals("ongoing") || board.getGameState().contains("Checkmate") || board.getGameState().contains("wins")) {
            context.json(Map.of(
                    "success", true,
                    "newBoard", board.getCleanSquares(),
                    "turn", turn,
                    "gameOver", board.getGameState().contains("Checkmate") || board.getGameState().contains("wins"),
                    "message", board.getGameState()
            ));
        } else {
            context.json(Map.of(
                    "success", false,
                    "message", board.getGameState()
            ));
        }
    }

    private void resignGame(Context context) {
        // 1. Identify who is trying to resign
        Player resigningPlayer = context.sessionAttribute("user");

        if (resigningPlayer == null) {
            context.status(401).json(Map.of("error", "Not logged in"));
            return;
        }

        // 2. Find their color in this specific match
        String resigningColor = null;

        // We compare Usernames to be safe (in case session objects differ in memory)
        for (Map.Entry<Player, String> entry : players.entrySet()) {
            if (entry.getKey().getUsername().equals(resigningPlayer.getUsername())) {
                resigningColor = entry.getValue(); // "w" or "b"
                break;
            }
        }

        if (resigningColor == null) {
            context.status(403).json(Map.of("error", "You are not a player in this match"));
            return;
        }

        // 3. Determine Winner (Opposite of whoever resigned)
        String winner = resigningColor.equals("w") ? "Black" : "White";
        String loser = resigningColor.equals("w") ? "White" : "Black";

        // 4. Update Game State
        board.setGameState("Game Over: " + winner + " wins! (" + loser + " resigned)");

        // 5. Send success response (Polling will handle the UI update for the other player)
        context.json(Map.of("success", true));
    }

    public UUID getGameId(){
        return id;
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}