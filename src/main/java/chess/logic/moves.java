package chess.logic;

import java.util.HashMap;
import java.util.Map;

public class moves {
    private final String piece;
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


    public moves(String piece){
        this.piece = piece;
    }

    private Integer[][] processMoves(String fromSquare, String toSquare){
        String fromSquareFileString = String.valueOf(fromSquare.charAt(0));
        int fromSquareRank = Integer.parseInt(String.valueOf(fromSquare.charAt(1)));
        int fromSquareFile = fileToColumn.get(fromSquareFileString);

        String toSquareFileString = String.valueOf(toSquare.charAt(0));
        int toSquareRank = Integer.parseInt(String.valueOf(toSquare.charAt(1)));
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

        return (Math.abs(fromSquareFile - toSquareFile) == 3 && Math.abs(fromSquareRank-toSquareRank) == 1) || (Math.abs(fromSquareFile - toSquareFile) == 1 && Math.abs(fromSquareRank-toSquareRank) == 3);
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

        return (fromSquareFile-toSquareFile == 0 && toSquareRank != fromSquareRank) || (fromSquareFile-toSquareFile != 0 && toSquareRank == fromSquareRank);
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

        return (Math.abs(fromSquareFile-toSquareFile) == 1 && Math.abs(fromSquareRank-toSquareRank) == 1) || (Math.abs(fromSquareRank-toSquareRank) == 1 && Math.abs(fromSquareFile-toSquareFile) == 0) || (Math.abs(fromSquareRank-toSquareRank) == 0 && Math.abs(fromSquareFile-toSquareFile) == 1);
    }

    public boolean pawnMove(String fromSquare,  String toSquare){

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

        if (piece.equals("P")){
            return (fromSquareFile-toSquareFile == 1);
        } else if (piece.equals("p")) {
            return (fromSquareFile-toSquareFile == -1);
        }

        return false;
    }
}
