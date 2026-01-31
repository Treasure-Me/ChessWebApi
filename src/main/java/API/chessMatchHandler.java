package API;

import API.utility.Player;
import logic.Board;
import logic.ChessGame;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class chessMatchHandler {
    public final String gameId;
    public Map<Player, String> players = new ConcurrentHashMap<>();
    private Board board;

    public chessMatchHandler(String gameId, Player p1, Player p2) {
        this.gameId = gameId;
        this.board = new Board();

        if (Math.random() < 0.5) {
            players.put(p1, "w");
            players.put(p2, "b");
        } else {
            players.put(p1, "b");
            players.put(p2, "w");
        }
    }


    public Map<String, Object> getGameState() {
        return Map.of(
                "newBoard", board.getCleanSquares(),
                "turn", board.getFENStringPosition().split(" ")[1],
                "status", board.getGameState()
        );
    }

    public Map<String, Object> processMove(Player player, String from, String to) {
        if (!players.containsKey(player)) return Map.of("success", false, "message", "Not a player");

        String color = players.get(player);
        String fenTurn = board.getFENStringPosition().split(" ")[1];
        if (!color.equals(fenTurn)) return Map.of("success", false, "message", "Not your turn");

        board = ChessGame.playGame(board, from + "-" + to);

        if (board.getGameState().startsWith("Invalid") || board.getGameState().startsWith("Illegal") || board.getGameState().startsWith("Cannot")) {
            return Map.of("success", false, "message", board.getGameState());
        }
        return Map.of("success", true, "newBoard", board.getCleanSquares(), "status", board.getGameState());
    }

    public ArrayList<String> getLegalMoves(String square, String piece) {
        return ChessGame.generateAllPossibleMoves(board, square, piece);
    }

    public boolean processResignation(Player resigningPlayer) {
        String resigningColor = null;
        // Match by username to ensure safety
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

    public boolean hasPlayer(String username) {
        return players.keySet().stream().anyMatch(p -> p.getUsername().equals(username));
    }

    public Board getBoard(){
        return board;
    }
}