package chess.logic;

import java.util.HashMap;
import java.util.Map;

public class Moves {
    private final String piece;
    private Board board;
    private static final HashMap<String, Integer> fileToColumn =
            new HashMap<>(Map.of(
                    "a", 0,
                    "b", 1,
                    "c", 2,
                    "d", 3,
                    "e", 4,
                    "f", 5,
                    "g", 6,
                    "h", 7
            ));

    public Moves(String piece){
        this.piece = piece;
    }

    private Integer[][] processMoves(String fromSquare, String toSquare){
        String fromSquareFileString = String.valueOf(fromSquare.charAt(0));
        int fromSquareRank = 8 - Integer.parseInt(String.valueOf(fromSquare.charAt(1))); // Fixed: convert to array index
        int fromSquareFile = fileToColumn.get(fromSquareFileString);

        String toSquareFileString = String.valueOf(toSquare.charAt(0));
        int toSquareRank = 8 - Integer.parseInt(String.valueOf(toSquare.charAt(1))); // Fixed: convert to array index
        int toSquareFile = fileToColumn.get(toSquareFileString);

        return new Integer[][]{{fromSquareRank, fromSquareFile},{toSquareRank, toSquareFile}};
    }

    public boolean queenMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("q")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        return (fromSquareFile - toSquareFile == 0) || (fromSquareRank - toSquareRank == 0) || (Math.abs(fromSquareFile-toSquareFile) == Math.abs(fromSquareRank-toSquareRank));
    }

    public boolean knightMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("n")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        int fileDiff = Math.abs(fromSquareFile - toSquareFile);
        int rankDiff = Math.abs(fromSquareRank - toSquareRank);
        return (fileDiff == 2 && rankDiff == 1) || (fileDiff == 1 && rankDiff == 2); // Fixed: correct knight L-shape
    }

    public boolean bishopMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("b")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        return (Math.abs(fromSquareFile-toSquareFile) == Math.abs(fromSquareRank-toSquareRank));
    }

    public boolean rookMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("r")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        return (fromSquareFile == toSquareFile && fromSquareRank != toSquareRank) ||
                (fromSquareFile != toSquareFile && fromSquareRank == toSquareRank); // Simplified
    }

    public boolean kingMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("k")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        int fileDiff = Math.abs(fromSquareFile - toSquareFile);
        int rankDiff = Math.abs(fromSquareRank - toSquareRank);
        return fileDiff <= 1 && rankDiff <= 1 && !(fileDiff == 0 && rankDiff == 0); // Fixed: simplified king movement
    }

    public boolean pawnMove(String fromSquare, String toSquare){

        if (!piece.equalsIgnoreCase("p")){
            return false;
        } else if (fromSquare.equals(toSquare)) {
            return false;
        }

        Integer[][] processedMoves = processMoves(fromSquare, toSquare);

        int fromSquareRank = processedMoves[0][0];
        int fromSquareFile = processedMoves[0][1];
        int toSquareRank = processedMoves[1][0];
        int toSquareFile = processedMoves[1][1];

        int fileDiff = Math.abs(fromSquareFile - toSquareFile);
        int rankDiff = fromSquareRank - toSquareRank; // Positive for white, negative for black

        if (piece.equals("P")){ // White pawn
            // Forward move (1 or 2 squares from starting position)
            if (fileDiff == 0 && toSquareFile == fromSquareFile) {
                if (rankDiff == 1) {
                    return true; // Single square forward
                } else if (rankDiff == 2 && fromSquareRank == 6) {
                    return true; // Double square from starting position
                }
            }
            // TODO: Add capture logic and en passant
        } else if (piece.equals("p")) { // Black pawn
            // Forward move (1 or 2 squares from starting position)
            if (fileDiff == 0 && toSquareFile == fromSquareFile) {
                if (rankDiff == -1) {
                    return true; // Single square forward
                } else if (rankDiff == -2 && fromSquareRank == 1) {
                    return true; // Double square from starting position
                }
            }
            // TODO: Add capture logic and en passant
        }

        return false;
    }
}