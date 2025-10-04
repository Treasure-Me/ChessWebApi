package chess.logic;

public class Board {
    private String[][] squares = new String[8][8];
    private String FENStringPosition;

    public Board(String FENStringPosition){
        this.FENStringPosition = FENStringPosition;
        initializeSquares();
    }

    public Board(){
        this.FENStringPosition = "PPPPPPPP/RNBQKBNR/8/8/8/8/pppppppp/rnbqkbnr w KQkq - 0 1";
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

    // Look up a square name like "e4" and return its value
    public String getSquare(String square){
        if (square == null || square.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + square);
        }

        char file = square.charAt(0);
        char rankChar = square.charAt(1);

        int col = file - 'a';
        int row = (rankChar - '1');

        if (col < 0 || col >= 8 || row < 0 || row >= 8) {
            throw new IllegalArgumentException("Square out of bounds: " + square);
        }

        return squares[row][col];
    }

    public void printBoard() {
        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                System.out.print(squares[row][col]);
            }
            System.out.println();
        }
    }
}
