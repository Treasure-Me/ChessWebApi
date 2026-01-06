package API;

import java.util.*;
import java.util.random.RandomGenerator;

import API.utility.Player;
import io.javalin.Javalin;
import io.javalin.http.Context;
import logic.Board;
import logic.ChessGame;
import org.jetbrains.annotations.NotNull;

public class chessMatchHandler {
    private final Javalin server;
    private Board board = new Board();
    private final Map<Player, String> players = new HashMap<>();
    private final UUID id;

    public chessMatchHandler(Player player1, Player player2) {
        RandomGenerator random = RandomGenerator.getDefault();
        String playerOneColour = List.of("b","w").get(random.nextInt(2));
        players.put(player1, playerOneColour);
        String playerTwoColour = playerOneColour.equals("w") ? "b":"w";
        players.put(player2, playerTwoColour);
        this.id = UUID.randomUUID();

        server = Javalin.create(config -> {
                    // This is the critical part to fix the "Access-Control-Allow-Origin" error
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.reflectClientOrigin = true; // Allows your local frontend to talk to the backend
                        });
                    });
                })
                .before(ctx -> {
                    if (ctx.contentType() == null) {
                        ctx.contentType("application/json");
                    }
                });
        this.server.post("/api/move", context -> sendMove(context));
        this.server.post("/api/legal-moves", context -> legalMoves(context));
        this.server.post("/api/load-fen", this::loadFenString);
    }

    private void loadFenString(Context context) {
        context.json(board.getFENStringPosition());
    }

    private void legalMoves(Context context) {
        // Read the JSON body sent by chess-engine-api.js
        var body = context.bodyAsClass(Map.class);
        String fromSquare = (String) body.get("square");
        String pieceType = (String) body.get("piece");

        // Generate destinations
        ArrayList<String> destinations = ChessGame.generateAllPossibleMoves(board, fromSquare, pieceType);

        // Map them to the format the JS UI expects: [{from: "e2", to: "e4"}]
        List<Map<String, String>> movesForFrontend = new ArrayList<>();
        for (String dest : destinations) {
            movesForFrontend.add(Map.of("from", fromSquare, "to", dest));
        }

        context.json(movesForFrontend);
    }

    public UUID getGameId(){
        return id;
    }

    private void sendMove(Context context){
        var move = context.bodyAsClass(Map.class);
        String from = (String) move.get("from");
        String to = (String) move.get("to");

        // This calls your logic which updates the board internal state
        ChessGame.playGame(board, from + "-" + to);

        if (board.getGameState().equals("ongoing") || board.getGameState().contains("Checkmate")) {
            context.json(Map.of(
                    "success", true,
                    "newBoard", board.getCleanSquares(),
                    "gameOver", board.getGameState().contains("Checkmate"),
                    "message", board.getGameState()
            ));
        } else {
            context.json(Map.of("success", false, "message", board.getGameState()));
        }
    }

    public static void main(String[] args) {
        chessMatchHandler server = new chessMatchHandler(new Player(UUID.randomUUID()), new Player(UUID.randomUUID()));
        server.start(5000);
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}