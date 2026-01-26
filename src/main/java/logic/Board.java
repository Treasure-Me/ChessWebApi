package logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Board {
    // squares[0] is Rank 8 (Black/Top)
    // squares[7] is Rank 1 (White/Bottom)
    private final String[][] squares = new String[8][8];
    private String FENStringPosition;
    private String gameState;

    private static final HashMap<String, Integer> fileToColumn = new HashMap<>(Map.of(
            "a", 0, "b", 1, "c", 2, "d", 3, "e", 4, "f", 5, "g", 6, "h", 7
    ));

    public Board(String FENStringPosition) {
        this.FENStringPosition = FENStringPosition;
        initializeSquares();
        gameState = "ongoing";
    }

    public Board() {
        this.FENStringPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        initializeSquares();
        gameState = "ongoing";
    }

    private void initializeSquares() {
        String position = FENStringPosition.split(" ")[0];
        String[] ranks = position.split("/");

        // FEN is Top-Down. We iterate 0 to 7 to match our array.
        for (int i = 0; i < 8; i++) {
            String rankData = ranks[i];
            int fileIndex = 0;

            for (int j = 0; j < rankData.length(); j++) {
                char c = rankData.charAt(j);
                if (Character.isDigit(c)) {
                    int emptyCount = Character.getNumericValue(c);
                    for (int k = 0; k < emptyCount; k++) {
                        // Mark internal empty squares
                        squares[i][fileIndex] = ((i + fileIndex) % 2 == 0) ? "o " : "x ";
                        fileIndex++;
                    }
                } else {
                    squares[i][fileIndex] = c + " ";
                    fileIndex++;
                }
            }
        }
    }

    public String[][] getCleanSquares() {
        String[][] cleanSquares = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String piece = squares[i][j].trim();
                // Strip internal markers for the API
                if (piece.equals("x") || piece.equals("o") || piece.isEmpty()) {
                    cleanSquares[i][j] = "";
                } else {
                    cleanSquares[i][j] = piece;
                }
            }
        }
        return cleanSquares;
    }

    public Integer[] processFileAndRank(String square) {
        // "a8" -> Col 0, Row 0
        // "a1" -> Col 0, Row 7
        int col = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '0';
        int row = 8 - rank;

        if (col < 0 || col > 7 || row < 0 || row > 7) {
            throw new IllegalArgumentException("Square out of bounds: " + square);
        }
        return new Integer[]{col, row};
    }

    public String getSquare(String square) {
        if (square == null || square.length() != 2) return " ";
        Integer[] coords = processFileAndRank(square);
        return squares[coords[1]][coords[0]].trim();
    }

    public void setSquare(String square, String piece) {
        Integer[] coords = processFileAndRank(square);
        squares[coords[1]][coords[0]] = piece.length() == 1 ? piece + " " : piece;
    }

    public void setFENStringPosition() {
        StringBuilder newFEN = new StringBuilder();

        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                String piece = squares[row][col].trim();
                if (piece.equals("x") || piece.equals("o") || piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        newFEN.append(emptyCount);
                        emptyCount = 0;
                    }
                    newFEN.append(piece);
                }
            }
            if (emptyCount > 0) newFEN.append(emptyCount);
            if (row < 7) newFEN.append("/");
        }

        String[] parts = FENStringPosition.split(" ");
        String nextTurn = parts[1].equals("w") ? "b" : "w";
        this.FENStringPosition = newFEN + " " + nextTurn + " - - 0 1";
    }

    public ArrayList<String> getPiecePositions(String piece) {
        ArrayList<String> positions = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (squares[r][c].trim().equals(piece)) {
                    char file = (char) ('a' + c);
                    int rank = 8 - r;
                    positions.add("" + file + rank);
                }
            }
        }
        return positions;
    }

    public String getFENStringPosition() { return FENStringPosition; }
    public String getGameState() { return gameState; }
    public void setGameState(String state) { this.gameState = state; }
    public String[][] getSquares() { return squares; }
}