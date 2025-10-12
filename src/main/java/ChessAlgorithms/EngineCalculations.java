package ChessAlgorithms;

import chess.logic.Board;

import java.util.Objects;

public class EngineCalculations {
    public double evaluate(Board board){
        // if black wins return -1000
        // if white wins return 1000
        // is a draw return 0.
        // Assign numbers to pieces
        return 0.0;
    }

    public double minimax(Board board, int depth){
         if (board.gameOver() || depth == 0){
             return evaluate(board);
         }

         String[] boardDetailsList = board.getFENStringPosition().split(" ");
         String playTurn = boardDetailsList[1];

         if (Objects.equals(playTurn, "w")){
             int best = -1000;
         }
         return 0.0;
    }
}
