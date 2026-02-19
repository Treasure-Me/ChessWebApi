package logic;

public class Moves {
    private final String piece;
    private final Board board;

    public Moves(String piece, Board board) {
        this.board = board;
        this.piece = piece;
    }

    private Integer[][] processMoves(String from, String to) {
        Integer[] f = board.processFileAndRank(from);
        Integer[] t = board.processFileAndRank(to);
        return new Integer[][]{{f[0], f[1]}, {t[0], t[1]}};
    }


    public boolean isPromotion(String from, String to) {
        if (!piece.equalsIgnoreCase("p")) return false;
        
        Integer[][] m = processMoves(from, to);
        int endRow = m[1][1];

        return (piece.equals("P") && endRow == 0) || (piece.equals("p") && endRow == 7);
    }

    private boolean isEnPassantCapture(String from, String to) {
        if (!piece.equalsIgnoreCase("p")) return false;

        try {
            String[] fenParts = board.getFENStringPosition().split(" ");
            String enPassantTarget = fenParts[3];

            return enPassantTarget.equals(to);
        } catch (Exception e) {
            return false;
        }
    }


    public boolean pawnMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int c1 = m[0][0], r1 = m[0][1];
        int c2 = m[1][0], r2 = m[1][1];

        int colDiff = Math.abs(c2 - c1);
        int rowDiff = r2 - r1;

        String target = board.getSquare(to);
        boolean occupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();

        if (piece.equals("P")) {
            if (colDiff == 0 && rowDiff == -1 && !occupied) return true;
            if (colDiff == 0 && rowDiff == -2 && r1 == 6 && !occupied) {

                String mid = board.getSquares()[5][c1].trim(); 
                return mid.equals("o") || mid.equals("x");
            }

            if (colDiff == 1 && rowDiff == -1) {
                return occupied || isEnPassantCapture(from, to);
            }
        } 
        
        else if (piece.equals("p")) {
            if (colDiff == 0 && rowDiff == 1 && !occupied) return true;
            if (colDiff == 0 && rowDiff == 2 && r1 == 1 && !occupied) {
                String mid = board.getSquares()[2][c1].trim();
                return mid.equals("o") || mid.equals("x");
            }
            if (colDiff == 1 && rowDiff == 1) {
                return occupied || isEnPassantCapture(from, to);
            }
        }
        return false;
    }

    public boolean canCastle(String from, String to) {
        if (!piece.equalsIgnoreCase("k")) return false;

        Integer[][] m = processMoves(from, to);
        int startCol = m[0][0], startRow = m[0][1];
        int endCol = m[1][0], endRow = m[1][1];

        if (Math.abs(endCol - startCol) != 2 || startRow != endRow) return false;

        String rights = board.getFENStringPosition().split(" ")[2];
        boolean isWhite = piece.equals("K");
        boolean kingSide = endCol > startCol;

        if (isWhite) {
            if (kingSide && !rights.contains("K")) return false;
            if (!kingSide && !rights.contains("Q")) return false;
        } else {
            if (kingSide && !rights.contains("k")) return false;
            if (!kingSide && !rights.contains("q")) return false;
        }

        if (pieceInRange(from, to)) return false;

        String dest = board.getSquare(to);
        if (!dest.equals("o") && !dest.equals("x") && !dest.isEmpty()) return false;

        return true;
    }

    public boolean pieceInRange(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int x1 = m[0][0], y1 = m[0][1];
        int x2 = m[1][0], y2 = m[1][1];

        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);
        
        if (piece.equalsIgnoreCase("n")) return false; 

        int currX = x1 + dx;
        int currY = y1 + dy;

        while (currX != x2 || currY != y2) {
            if (currX < 0 || currX > 7 || currY < 0 || currY > 7) return false;
            String p = board.getSquares()[currY][currX].trim();
            if (!p.equals("o") && !p.equals("x") && !p.isEmpty()) return true;
            currX += dx;
            currY += dy;
        }
        return false;
    }

    public boolean kingMove(String from, String to) {
        if (canCastle(from, to)) return true;
        Integer[][] m = processMoves(from, to);
        int dx = Math.abs(m[0][0] - m[1][0]);
        int dy = Math.abs(m[0][1] - m[1][1]);
        return dx <= 1 && dy <= 1;
    }

    public boolean rookMove(String f, String t) {
        Integer[][] m = processMoves(f, t);
        if (m[0][0] != m[1][0] && m[0][1] != m[1][1]) return false;
        return !pieceInRange(f, t);
    }

    public boolean bishopMove(String f, String t) {
        Integer[][] m = processMoves(f, t);
        if (Math.abs(m[0][0] - m[1][0]) != Math.abs(m[0][1] - m[1][1])) return false;
        return !pieceInRange(f, t);
    }

    public boolean queenMove(String f, String t) {
        return rookMove(f, t) || bishopMove(f, t);
    }

    public boolean knightMove(String f, String t) {
        Integer[][] m = processMoves(f, t);
        int dx = Math.abs(m[0][0] - m[1][0]);
        int dy = Math.abs(m[0][1] - m[1][1]);
        return (dx == 2 && dy == 1) || (dx == 1 && dy == 2);
    }
}