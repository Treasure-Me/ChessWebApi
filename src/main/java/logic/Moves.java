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

    /**
     * Safety check for blocked paths (Rook/Bishop/Queen).
     */
    public boolean pieceInRange(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int x1 = m[0][0], y1 = m[0][1];
        int x2 = m[1][0], y2 = m[1][1];

        // Only valid for straight or diagonal lines
        if (x1 != x2 && y1 != y2 && Math.abs(x2 - x1) != Math.abs(y2 - y1)) return false;

        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);

        int currX = x1 + dx;
        int currY = y1 + dy;

        while (currX != x2 || currY != y2) {
            // Out of bounds check
            if (currX < 0 || currX > 7 || currY < 0 || currY > 7) return false;

            String p = board.getSquares()[currY][currX].trim();
            if (!p.equals("o") && !p.equals("x") && !p.isEmpty()) return true;

            currX += dx;
            currY += dy;
        }
        return false;
    }

    public boolean rookMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        if (m[0][0] != m[1][0] && m[0][1] != m[1][1]) return false;
        return !pieceInRange(from, to);
    }

    public boolean bishopMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        if (Math.abs(m[0][0] - m[1][0]) != Math.abs(m[0][1] - m[1][1])) return false;
        return !pieceInRange(from, to);
    }

    public boolean queenMove(String from, String to) {
        return rookMove(from, to) || bishopMove(from, to);
    }

    public boolean knightMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int dx = Math.abs(m[0][0] - m[1][0]);
        int dy = Math.abs(m[0][1] - m[1][1]);
        return (dx == 2 && dy == 1) || (dx == 1 && dy == 2);
    }

    public boolean kingMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int dx = Math.abs(m[0][0] - m[1][0]);
        int dy = Math.abs(m[0][1] - m[1][1]);
        return dx <= 1 && dy <= 1;
    }

    public boolean pawnMove(String from, String to) {
        Integer[][] m = processMoves(from, to);
        int startCol = m[0][0], startRow = m[0][1];
        int endCol = m[1][0], endRow = m[1][1];
        int colDiff = Math.abs(endCol - startCol);

        String target = board.getSquare(to);
        boolean occupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();

        if (piece.equals("P")) { // WHITE (Moves Up -> Decreasing Row Index)
            int rowDiff = startRow - endRow; // Positive value means moving up

            // 1. Single Step
            if (colDiff == 0 && rowDiff == 1 && !occupied) return true;

            // 2. Double Step (From Rank 2 = Index 6)
            if (colDiff == 0 && rowDiff == 2 && startRow == 6 && !occupied) {
                String mid = board.getSquares()[5][startCol].trim();
                return mid.equals("o") || mid.equals("x") || mid.isEmpty();
            }

            // 3. Capture
            if (colDiff == 1 && rowDiff == 1 && occupied) return true;

        } else if (piece.equals("p")) { // BLACK (Moves Down -> Increasing Row Index)
            int rowDiff = endRow - startRow; // Positive value means moving down

            // 1. Single Step
            if (colDiff == 0 && rowDiff == 1 && !occupied) return true;

            // 2. Double Step (From Rank 7 = Index 1)
            if (colDiff == 0 && rowDiff == 2 && startRow == 1 && !occupied) {
                String mid = board.getSquares()[2][startCol].trim();
                return mid.equals("o") || mid.equals("x") || mid.isEmpty();
            }

            // 3. Capture
            if (colDiff == 1 && rowDiff == 1 && occupied) return true;
        }

        return false;
    }
}