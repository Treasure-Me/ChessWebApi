package API;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import API.utility.Player;
import API.utility.WebSocketBroadcaster;
import logic.Board;
import logic.ChessGame;


public class chessMatchHandler {
    public final String gameId;
    public Map<Player, String> players = new ConcurrentHashMap<>();
    private Board board;
    private boolean vsBot = false;
    private Player botPlayer = null;
    private boolean isAnalysis = false;


    public chessMatchHandler(String gameId, Player p1, Player p2) {
        this.gameId = gameId;
        this.board = new Board();

        if (p1.getUsername().equals("Stockfish") || p1.getUsername().equals("DevBot")) {
            vsBot = true;
            botPlayer = p1;
            players.put(p2, "w");
            players.put(p1, "b");
        } else if (p2.getUsername().equals("Stockfish") || p2.getUsername().equals("DevBot")) {
            vsBot = true;
            botPlayer = p2;
            players.put(p1, "w");
            players.put(p2, "b");
        }else{
            if (Math.random() < 0.5) {
                players.put(p1, "w");
                players.put(p2, "b");
            } else {
                players.put(p1, "b");
                players.put(p2, "w");
            }
        }
    }

    public chessMatchHandler(String gameId, Player p1) {
        this.gameId = gameId;
        this.board = new Board();
        this.isAnalysis = true;

        if (Math.random() < 0.5) {
            players.put(p1, "w");
        } else {
            players.put(p1, "b");
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
        if (!players.containsKey(player)){
            System.out.println("Player error!");
            return Map.of("success", false, "message", "Not a player");
        }
            

        String color = players.get(player);
        String fenTurn = board.getFENStringPosition().split(" ")[1];


        if (isAnalysis){
            //avoid next block
        }else if (!color.equals(fenTurn)){
            System.out.println("Color error!");
            return Map.of("success", false, "message", "Not your turn");
        }

        board = ChessGame.playGame(board, from + "-" + to, promotion);

        if (board.getGameState().startsWith("Invalid") || board.getGameState().startsWith("Illegal") || board.getGameState().startsWith("Cannot") || board.getGameState().startsWith("Not")) {
            System.out.println("Stuck state");
            return Map.of("success", false, "message", board.getGameState());
        }

        // if (vsBot) {
        //     String nextTurn = board.getFENStringPosition().split(" ")[1];
        //     String botColor = players.get(botPlayer);

        //     if (nextTurn.equals(botColor) && board.getGameState().equals("ongoing")) {
        //         makeBotMove(from, to);
        //     }
        // }

        WebSocketBroadcaster.broadcastGameUpdate(gameId, getGameState());
        return Map.of(
                "success", true,
                "newBoard", board.getCleanSquares(),
                "status", board.getGameState()
        );
    }

    // private void makeBotMove(String from, String to) {

    //     try {

    //         board = ChessGame.playGame(board, from + "-" + to, null);

    //     } catch (Exception e) {
    //         System.out.println("Bot move failed: " + e.getMessage());
    //     }
    // }



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
        WebSocketBroadcaster.broadcastGameUpdate(this.gameId, getGameState());
        return true;
    }

    public boolean hasPlayer(String username) {
        return players.keySet().stream().anyMatch(p -> p.getUsername().equals(username));
    }

    public Board getBoard(){
        return board;
    }

    public void setNewBoard(Board baord){
        this.board = board;
    }
}