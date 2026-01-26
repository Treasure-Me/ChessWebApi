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
        this.server.post("/api/load-fen", this::loadGameState);
    }

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
        var body = context.bodyAsClass(Map.class);
        String fromSquare = (String) body.get("square");
        String pieceType = (String) body.get("piece");

        ArrayList<String> destinations = ChessGame.generateAllPossibleMoves(board, fromSquare, pieceType);

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

    public boolean processResignation(Player resigningPlayer) {
        String resigningColor = null;
        for (Map.Entry<Player, String> entry : players.entrySet()) {
            if (entry.getKey().getUsername().equals(resigningPlayer.getUsername())) {
                resigningColor = entry.getValue();
                break;
            }
        }

        if (resigningColor == null) return false;

        String winner = resigningColor.equals("w") ? "Black" : "White";
        String loser = resigningColor.equals("w") ? "White" : "Black";

        board.setGameState("Game Over: " + winner + " wins! (" + loser + " resigned)");
        return true;
    }

    public UUID getGameId(){
        return id;
    }

    public Board getBoard(){
        return board;
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}