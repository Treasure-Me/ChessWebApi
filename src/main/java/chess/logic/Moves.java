package chess.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Moves {
    private static final ArrayList<String> blackPieces = new ArrayList<>(List.of("r","n","k","q","p","b"));
    private static final ArrayList<String> whitePieces = new ArrayList<>(List.of("R","N","K","Q","P","B"));
    private final String piece;
    private final Board board;
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

    public Moves(String piece, Board board){
        this.board = board;
        this.piece = piece;
    }

    public static <K, V> K findKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean liesInRange(String fromSquare, String toSquare, String pieceSquare) {
        String fromXLetter = String.valueOf(fromSquare.charAt(0));
        int fromX = fileToColumn.get(fromXLetter);
        int fromY = fromSquare.charAt(1) - '0';

        String toXLetter = String.valueOf(toSquare.charAt(0));
        int toX = fileToColumn.get(toXLetter);
        int toY = toSquare.charAt(1) - '0';

        String pieceXLetter = String.valueOf(pieceSquare.charAt(0));
        int pieceX = fileToColumn.get(pieceXLetter);
        int pieceY = pieceSquare.charAt(1) - '0';

        // Check if pieceSquare is collinear with fromSquare and toSquare
        if (fromX == toX && fromX == pieceX) {
            // Vertical line - check if pieceY is between fromY and toY
            return (fromY < pieceY && pieceY < toY) || (toY < pieceY && pieceY < fromY);
        } else if (fromY == toY && fromY == pieceY) {
            // Horizontal line - check if pieceX is between fromX and toX
            return (fromX < pieceX && pieceX < toX) || (toX < pieceX && pieceX < fromX);
        } else if (Math.abs(fromX - toX) == Math.abs(fromY - toY) &&
                Math.abs(fromX - pieceX) == Math.abs(fromY - pieceY)) {
            // Diagonal line - check if piece is between the endpoints
            boolean xBetween = (fromX < pieceX && pieceX < toX) || (toX < pieceX && pieceX < fromX);
            boolean yBetween = (fromY < pieceY && pieceY < toY) || (toY < pieceY && pieceY < fromY);
            return xBetween && yBetween;
        }

        return false;
    }

    public boolean pieceInRange(String fromSquare, String toSquare){
        for (String blackPiece : blackPieces) {
            ArrayList<String> pieceSquares = board.getPiecePositions(blackPiece);
            for (String pos: pieceSquares){
                if (liesInRange(fromSquare,toSquare, pos)){
                    return true;
                }
            }
        }

        for (String whitePiece : whitePieces) {
            ArrayList<String> pieceSquares = board.getPiecePositions(whitePiece);
            for (String pos: pieceSquares){
                if (liesInRange(fromSquare,toSquare, pos)){
                    return true;
                }
            }
        }

        return false;
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
        if (pieceInRange(fromSquare,toSquare)){
            return false;
        }

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
        if (pieceInRange(fromSquare,toSquare)){
            return false;
        }

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
        if (pieceInRange(fromSquare,toSquare)){
            return false;
        }

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
        if (pieceInRange(fromSquare,toSquare)){
            return false;
        }

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
        if (pieceInRange(fromSquare,toSquare)){
            return false;
        }

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
        System.out.println("here4");
        if (pieceInRange(fromSquare,toSquare)){
            System.out.println("here5");
            return false;
        }
        System.out.println("here4");

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