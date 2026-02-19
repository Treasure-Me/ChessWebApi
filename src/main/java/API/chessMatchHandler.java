package API;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import API.utility.Player;
import logic.Board;
import logic.ChessGame;


public class chessMatchHandler {
    public final String gameId;
    public Map<Player, String> players = new ConcurrentHashMap<>();
    private Board board;
    private boolean vsBot = false;
    private Player botPlayer = null;


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

        if (p1.getUsername().equals("Stockfish")) {
            vsBot = true;
            botPlayer = p1;
            players.put(p2, "w");
            players.put(p1, "b");
        } else if (p2.getUsername().equals("Stockfish")) {
            vsBot = true;
            botPlayer = p2;
            players.put(p1, "w");
            players.put(p2, "b");
        }
    }


    public Map<String, Object> getGameState() {
        return Map.of(
                "newBoard", board.getCleanSquares(),
                "turn", board.getFENStringPosition().split(" ")[1],
                "status", board.getGameState(),
                "fen", board.getFENStringPosition()
        );
    }

    public Map<String, Object> processMove(Player player, String from, String to, String promotion) {
        System.out.println("I ended here");
        if (!players.containsKey(player)){
            System.out.println("Player error!");
            return Map.of("success", false, "message", "Not a player");
        }
            

        String color = players.get(player);
        String fenTurn = board.getFENStringPosition().split(" ")[1];

        if (!color.equals(fenTurn)){
            System.out.println("Color error!");
            return Map.of("success", false, "message", "Not your turn");
        }


        System.out.println("I made it past color and player");
        board = ChessGame.playGame(board, from + "-" + to, promotion);
        

        if (board.getGameState().startsWith("Invalid") || board.getGameState().startsWith("Illegal") || board.getGameState().startsWith("Cannot")) {
            System.out.println("Stuck state");
            return Map.of("success", false, "message", board.getGameState());
        }

        if (vsBot) {
            String nextTurn = board.getFENStringPosition().split(" ")[1];
            String botColor = players.get(botPlayer);

            if (nextTurn.equals(botColor) && board.getGameState().equals("ongoing")) {
                makeBotMove(from, to);
            }
        }
        System.out.println("Made it here");
        return Map.of(
                "success", true,
                "newBoard", board.getCleanSquares(),
                "status", board.getGameState()
        );
    }

    private void makeBotMove(String from, String to) {

        try {

            board = ChessGame.playGame(board, from + "-" + to, null);

        } catch (Exception e) {
            System.out.println("Bot move failed: " + e.getMessage());
        }
    }



    public ArrayList<String> getLegalMoves(String square, String piece) {
        return ChessGame.generateAllPossibleMoves(board, square, piece);
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

    public boolean hasPlayer(String username) {
        return players.keySet().stream().anyMatch(p -> p.getUsername().equals(username));
    }

    public Board getBoard(){
        return board;
    }
}