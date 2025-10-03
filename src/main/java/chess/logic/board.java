package chess.logic;

public class board {
    private String[][] squares = new String[8][8];
    private String FENStringPosition;

    public board(String FENStringPosition){
        this.FENStringPosition = FENStringPosition;
        initializeSquares();
    }

    public board(){
        this.FENStringPosition = "PPPPPPPP/RNBQKBNR/8/8/8/8/pppppppp/rnbqkbnr w KQkq - 0 1";
        initializeSquares();
    }

    // Initialize square names (a1–h8)
    private void initializeSquares() {
        String position = FENStringPosition.split(" ")[0];
        String[] ranks = position.split("/");

        for (int j = 0; j < ranks.length; j++){
            String rank = ranks[j];
            for (int i = 0; i < rank.length(); i++){
                try{
                    int numberOfEmptySquares = Integer.parseInt(String.valueOf(rank.charAt(i)));

                    for (int x = i; x < i+numberOfEmptySquares; x++){
                        if ((j + x) % 2 == 0) {
//                            squares[j][x] = "⬜"+"  ";
                            squares[j][x] = "o"+"  ";
                        } else {
//                            squares[j][x] = "⬛"+ "  ";
                            squares[j][x] = "x"+"  ";

                        }
                    }

                    i += numberOfEmptySquares - 1;
                }catch (IllegalArgumentException e){
                    squares[j][i] = String.valueOf(rank.charAt(i))+"  ";
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

    public static void main(String[] args) {
        board b = new board("5k2/ppp5/4P3/3R3p/6P1/1K2Nr2/PP3P2/8 b - - 1 32");
        b.printBoard();
        System.out.println("Square e4: " + b.getSquare("e4"));
    }
}
