package chess.logic;

import java.util.*;

public class Board {
    private final String[][] squares = new String[8][8];
    private String FENStringPosition;
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

    public Board(String FENStringPosition){
        this.FENStringPosition = FENStringPosition;
        initializeSquares();
    }

    public Board(){
        this.FENStringPosition = "RNBQKBNR/PPPPPPPP/8/8/8/8/pppppppp/rnbqkbnr w KQkq - 0 1";
        initializeSquares();
    }

    // Initialize square names (a1–h8)
    private void initializeSquares() {
        String position = FENStringPosition.split(" ")[0];
        String[] ranks = position.split("/");

        for (int j = 0; j < ranks.length; j++) {
            String rank = ranks[j];
            int fileIndex = 0; // column index (0–7)

            for (int i = 0; i < rank.length(); i++) {
                char c = rank.charAt(i);

                if (Character.isDigit(c)) {
                    int emptyCount = c - '0';
                    for (int k = 0; k < emptyCount; k++) {
                        if ((j + fileIndex) % 2 == 0) {
                            squares[j][fileIndex] = "o ";  // light square
                        } else {
                            squares[j][fileIndex] = "x ";  // dark square
                        }
                        fileIndex++;
                    }
                } else {
                    squares[j][fileIndex] = c + " ";
                    fileIndex++;
                }
            }
        }
    }

    public String getFENStringPosition(){
        return FENStringPosition;
    }

    public String[][] getSquares(){
        return squares;
    }

    // Look up a square name like "e4" and return its value
    public String getSquare(String square){
        if (square == null || square.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + square);
        }

        Integer[] indexSquare = processFileAndRank(square);
        String value = squares[indexSquare[1]][indexSquare[0]];

        // Remove the space for clean piece identification
        return value.trim();
    }

    public void setSquare(String square, String piece) {
        square = square.toLowerCase();
        Integer[] indexSquare = processFileAndRank(square);

        // Add space back for consistent board storage
        if (piece.length() == 1) {
            squares[indexSquare[1]][indexSquare[0]] = piece + " ";
        } else {
            squares[indexSquare[1]][indexSquare[0]] = piece;
        }
    }

    public Integer[] processFileAndRank(String square){
        char file = square.charAt(0);
        char rankChar = square.charAt(1);

        int col = file - 'a';
        int row = (rankChar - '1');

        if (col < 0 || col >= 8 || row < 0 || row >= 8) {
            throw new IllegalArgumentException("Square out of bounds: " + square);
        }

        return new Integer[]{col, row};
    }

    public void setFENStringPosition() {
        StringBuilder newFENString = new StringBuilder();

        for (int row = 7; row >= 0; row--) { // FEN starts from rank 8 (top)
            String[] rank = squares[row];
            int emptyCount = 0;

            for (String square : rank) {
                String piece = square.trim(); // Remove any spaces

                if (piece.equals("x") || piece.equals("o") || piece.equals("")) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        newFENString.append(emptyCount);
                        emptyCount = 0;
                    }
                    newFENString.append(piece);
                }
            }

            // Add any remaining empty squares at the end of the rank
            if (emptyCount > 0) {
                newFENString.append(emptyCount);
            }

            // Add rank separator (but not after the last rank)
            if (row > 0) {
                newFENString.append("/");
            }
        }

        // Update game state information
        String[] fenParts = FENStringPosition.split(" ");
        String currentTurn = fenParts[1];
        String nextTurn = currentTurn.equals("w") ? "b" : "w";

        // Handle move counters more carefully
        int halfMoves = 0;
        int fullMoves = 1;

        try {
            if (fenParts.length > 4) {
                halfMoves = Integer.parseInt(fenParts[4]);
            }
            if (fenParts.length > 5) {
                fullMoves = Integer.parseInt(fenParts[5]);
            }
        } catch (NumberFormatException e) {
            // Use defaults if parsing fails
        }

        // Increment half move clock (resets on captures/pawn moves - you'll need to add this logic)
        halfMoves++;

        // Increment full move counter only after black moves
        if (currentTurn.equals("b")) {
            fullMoves++;
        }

        // Rebuild the FEN string
        StringBuilder finalFEN = new StringBuilder();
        finalFEN.append(newFENString)
                .append(" ").append(nextTurn)
                .append(" ").append(fenParts.length > 2 ? fenParts[2] : "-") // Castling rights
                .append(" ").append(fenParts.length > 3 ? fenParts[3] : "-") // En passant
                .append(" ").append(halfMoves)
                .append(" ").append(fullMoves);

        this.FENStringPosition = finalFEN.toString();
        System.out.println("New FEN: " + FENStringPosition);
    }

    public ArrayList<String> getPiecePositions(String piece){
        ArrayList<String> positions = new ArrayList<>();

        for (int i = 0; i < squares.length; i++) {
            for (int j = 0; j < squares[i].length; j++) {
                if ((squares[i][j]).strip().equals(piece)) {
                    for (String letter:fileToColumn.keySet()){
                        if (fileToColumn.get(letter).equals(j)){
                            positions.add(letter+(i+1));
                        }
                    }
                }
            }
        }
        return positions;
    }

    public void printBoard() {
        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                System.out.print(squares[row][col]);
            }
            System.out.println();
        }
        System.out.println();
    }

    public boolean gameOver() {
        int black = 1000;
        int white = -1000;
        return false;
    }
}
