package logic;

/**
 * Moves — Piece-specific move validation.
 *
 * All coordinate arithmetic is inline (no allocation).
 * Obstruction check uses a precomputed BETWEEN[64][64] bitboard table — O(1).
 * Castling rights and EP square are read via Board's cheap field accessors.
 *
 * Coordinate convention:
 *   col 0..7  (file a=0..h=7)
 *   row 0..7  (rank-8 = row 0, rank-1 = row 7)   ← array-row convention
 */
public class Moves {

    private final String piece;
    private final Board  board;
    private final char   pieceChar;   // extracted once to avoid repeated charAt(0)

    // =========================================================================
    // Precomputed BETWEEN table
    // between[from][to] = mask of squares strictly between from and to
    // on the same rank, file, or diagonal.  0 for unaligned or adjacent squares.
    // 64×64×8 bytes = 32 KB — fits in L1 cache.
    // =========================================================================

    private static final long[][] BETWEEN = new long[64][64];

    static {
        for (int from = 0; from < 64; from++) {
            int fr = from >> 3, ff = from & 7;
            for (int to = 0; to < 64; to++) {
                if (from == to) continue;
                int tr = to >> 3, tf = to & 7;
                int dr = Integer.compare(tr, fr);
                int df = Integer.compare(tf, ff);

                boolean rankAligned = (fr == tr);
                boolean fileAligned = (ff == tf);
                boolean diagAligned = (Math.abs(tr - fr) == Math.abs(tf - ff));
                if (!rankAligned && !fileAligned && !diagAligned) continue;

                long mask = 0L;
                int cr = fr + dr, cf = ff + df;
                while (cr != tr || cf != tf) {
                    mask |= 1L << (cr * 8 + cf);
                    cr += dr; cf += df;
                }
                BETWEEN[from][to] = mask;
            }
        }
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    public Moves(String piece, Board board) {
        this.piece     = piece;
        this.board     = board;
        this.pieceChar = piece.charAt(0);
    }

    // =========================================================================
    // Coordinate helpers — inline arithmetic, no allocation
    // =========================================================================

    /** Algebraic square → column (0 = a-file, 7 = h-file). */
    private static int col(String sq) { return sq.charAt(0) - 'a'; }

    /** Algebraic square → array-row (0 = rank-8, 7 = rank-1). */
    private static int row(String sq) { return '8' - sq.charAt(1); }

    // =========================================================================
    // Promotion detection
    // =========================================================================

    /** Returns true if this pawn move ends on the back rank (promotion). */
    public boolean isPromotion(String from, String to) {
        if (pieceChar != 'P' && pieceChar != 'p') return false;
        int endRow = row(to);
        return (pieceChar == 'P' && endRow == 0)    // White reaches rank-8 → row 0
                || (pieceChar == 'p' && endRow == 7);   // Black reaches rank-1 → row 7
    }

    // =========================================================================
    // En-passant (reads EP field directly — no FEN split)
    // =========================================================================

    private boolean isEnPassantCapture(String to) {
        String ep = board.getEnPassantSquare();
        return ep != null && ep.equals(to);
    }

    // =========================================================================
    // Pawn move
    // =========================================================================

    /**
     * Validates all pawn move types: single push, double push,
     * diagonal capture, en-passant.
     *
     * Row 0 = rank-8 (White's promotion rank), row 7 = rank-1.
     * White pawns start on row 6, advance toward row 0 (rowDiff negative).
     * Black pawns start on row 1, advance toward row 7 (rowDiff positive).
     */
    public boolean pawnMove(String from, String to) {
        int c1 = col(from), r1 = row(from);
        int c2 = col(to),   r2 = row(to);
        int colDiff = c2 - c1;
        int rowDiff = r2 - r1;

        boolean occupied = !board.getSquare(to).isEmpty();

        if (pieceChar == 'P') {
            if (colDiff == 0 && rowDiff == -1 && !occupied) return true;
            if (colDiff == 0 && rowDiff == -2 && r1 == 6 && !occupied) {
                // Passed-through square: rank-3 = bitRank 2
                return (board.occupancy & (1L << (2 * 8 + c1))) == 0;
            }
            if ((colDiff == 1 || colDiff == -1) && rowDiff == -1) {
                return occupied || isEnPassantCapture(to);
            }

        } else if (pieceChar == 'p') {
            if (colDiff == 0 && rowDiff == 1 && !occupied) return true;
            if (colDiff == 0 && rowDiff == 2 && r1 == 1 && !occupied) {
                // Passed-through square: rank-6 = bitRank 5
                return (board.occupancy & (1L << (5 * 8 + c1))) == 0;
            }
            if ((colDiff == 1 || colDiff == -1) && rowDiff == 1) {
                return occupied || isEnPassantCapture(to);
            }
        }
        return false;
    }

    // =========================================================================
    // Obstruction check — O(1) via BETWEEN table
    // =========================================================================

    /**
     * Returns true if any piece lies strictly between 'from' and 'to'.
     * Uses the precomputed BETWEEN table ANDed with the occupancy bitboard.
     * Always returns false for knights.
     */
    public boolean pieceInRange(String from, String to) {
        if (pieceChar == 'N' || pieceChar == 'n') return false;
        int fromSq = Board.sqIndex(from);
        int toSq   = Board.sqIndex(to);
        return (BETWEEN[fromSq][toSq] & board.occupancy) != 0;
    }

    // =========================================================================
    // Castling (reads castling rights without FEN split)
    // =========================================================================

    /**
     * Returns true if this king move is a legal castling attempt:
     * rights present and path clear.  Check-safety is enforced by ChessGame.
     */
    public boolean canCastle(String from, String to) {
        if (pieceChar != 'K' && pieceChar != 'k') return false;

        int dc = col(to) - col(from);
        int dr = row(to) - row(from);
        if ((dc != 2 && dc != -2) || dr != 0) return false;

        String rights  = board.getCastlingRights();
        boolean isWhite  = (pieceChar == 'K');
        boolean kingSide = (dc > 0);

        if (isWhite) {
            if ( kingSide && !rights.contains("K")) return false;
            if (!kingSide && !rights.contains("Q")) return false;
        } else {
            if ( kingSide && !rights.contains("k")) return false;
            if (!kingSide && !rights.contains("q")) return false;
        }

        // All squares between king and destination must be clear
        if (pieceInRange(from, to)) return false;

        // Destination square must be unoccupied
        return (board.occupancy & (1L << Board.sqIndex(to))) == 0;
    }

    // =========================================================================
    // Piece move validators
    // =========================================================================

    public boolean kingMove(String from, String to) {
        if (canCastle(from, to)) return true;
        int dx = Math.abs(col(from) - col(to));
        int dy = Math.abs(row(from) - row(to));
        return dx <= 1 && dy <= 1 && (dx + dy > 0);
    }

    public boolean rookMove(String from, String to) {
        int c1 = col(from), r1 = row(from);
        int c2 = col(to),   r2 = row(to);
        if (c1 != c2 && r1 != r2) return false;
        return !pieceInRange(from, to);
    }

    public boolean bishopMove(String from, String to) {
        int dc = Math.abs(col(from) - col(to));
        int dr = Math.abs(row(from) - row(to));
        if (dc != dr || dc == 0) return false;
        return !pieceInRange(from, to);
    }

    public boolean queenMove(String from, String to) {
        return rookMove(from, to) || bishopMove(from, to);
    }

    public boolean knightMove(String from, String to) {
        int dx = Math.abs(col(from) - col(to));
        int dy = Math.abs(row(from) - row(to));
        return (dx == 2 && dy == 1) || (dx == 1 && dy == 2);
    }
}