package logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Board {
    // Row 0 = Rank 8 (Top), Row 7 = Rank 1 (Bottom)
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
        this("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    private void initializeSquares() {
        String position = FENStringPosition.split(" ")[0];
        String[] ranks = position.split("/");

        for (int i = 0; i < 8; i++) {
            String rankData = ranks[i];
            int fileIndex = 0;
            for (int j = 0; j < rankData.length(); j++) {
                char c = rankData.charAt(j);
                if (Character.isDigit(c)) {
                    int emptyCount = Character.getNumericValue(c);
                    for (int k = 0; k < emptyCount; k++) {
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

        // Preserve existing metadata (Turn, Rights, EnPassant, Clocks)
        // We will toggle the turn, but keep rights (ChessGame handles rights updates)
        String[] oldParts = FENStringPosition.split(" ");
        String oldRights = (oldParts.length > 2) ? oldParts[2] : "-";
        String nextTurn = oldParts[1].equals("w") ? "b" : "w";

        // Reconstruct: [Board] [Turn] [Rights] [EnPassant] [Half] [Full]
        this.FENStringPosition = newFEN + " " + nextTurn + " " + oldRights + " - 0 1";
    }

    public String[][] getCleanSquares() {
        String[][] clean = new String[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                String p = squares[i][j].trim();
                clean[i][j] = (p.equals("x") || p.equals("o")) ? "" : p;
            }
        }
        return clean;
    }

    public Integer[] processFileAndRank(String square) {
        int col = square.charAt(0) - 'a';
        int row = '8' - square.charAt(1); // '8'->0, '1'->7
        if (col < 0 || col > 7 || row < 0 || row > 7) throw new IllegalArgumentException("Bounds");
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

    public ArrayList<String> getPiecePositions(String piece) {
        ArrayList<String> positions = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (squares[r][c].trim().equals(piece)) {
                    positions.add("" + (char)('a' + c) + (8 - r));
                }
            }
        }
        return positions;
    }

    // Getters/Setters
    public String getFENStringPosition() { return FENStringPosition; }
    public void setRawFEN(String fen) { this.FENStringPosition = fen; } // Used by ChessGame to force rights update
    public String getGameState() { return gameState; }
    public void setGameState(String state) { this.gameState = state; }
    public String[][] getSquares() { return squares; }
    public void printBoard() { /* ... console print ... */ }
}