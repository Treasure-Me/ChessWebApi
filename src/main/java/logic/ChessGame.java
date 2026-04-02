package logic;

import java.util.ArrayList;
import java.util.List;

/**
 * ChessGame — Main game logic and move execution.
 *
 * ── FEN side-to-move contract ────────────────────────────────────────────────
 * Board.setSquare() NO LONGER toggles the side-to-move (that was the root bug).
 * This class owns the toggle: updateFEN() calls board.toggleSideToMove() exactly
 * once per completed half-move, then board.setFENFields(), then board.commitFEN().
 *
 * makeMove / undoMove call setSquare() as many times as needed — none of those
 * calls affect the side-to-move field.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ChessGame {

    // =========================================================================
    // Pre-built tables
    // =========================================================================

    /**
     * SQUARE_NAMES[sq] = algebraic name for bitboard square index sq.
     * sq = bitRank*8 + file,  bitRank 0 = rank-1, bitRank 7 = rank-8.
     */
    public static final String[] SQUARE_NAMES = new String[64];
    static {
        for (int sq = 0; sq < 64; sq++)
            SQUARE_NAMES[sq] = "" + (char)('a' + (sq & 7)) + (char)('1' + (sq >> 3));
    }

    /**
     * ALL_SQUARES[0..63] — every algebraic square in rank-1-first order.
     * Used as the destination candidate list in generateAllPossibleMoves.
     */
    private static final String[] ALL_SQUARES = new String[64];
    static {
        int idx = 0;
        for (int r = 1; r <= 8; r++)
            for (char f = 'a'; f <= 'h'; f++)
                ALL_SQUARES[idx++] = "" + f + r;
    }

    // Bitboard index ranges
    private static final int WHITE_START = 0, WHITE_END = 5;
    private static final int BLACK_START = 6, BLACK_END = 11;

    // =========================================================================
    // Piece dispatch
    // =========================================================================

    static boolean identifyPlayPiece(String piece, Moves moves, String from, String to) {
        return switch (piece) {
            case "P","p" -> moves.pawnMove(from, to);
            case "R","r" -> moves.rookMove(from, to);
            case "N","n" -> moves.knightMove(from, to);
            case "B","b" -> moves.bishopMove(from, to);
            case "Q","q" -> moves.queenMove(from, to);
            case "K","k" -> moves.kingMove(from, to);
            default      -> false;
        };
    }

    // =========================================================================
    // Main entry point
    // =========================================================================

    public static Board playGame(Board board, String playerMove, String promotionPiece) {
        int dash = playerMove.indexOf('-');
        if (dash < 0) { board.setGameState("Invalid Move"); return board; }

        String from  = playerMove.substring(0, dash);
        String to    = playerMove.substring(dash + 1);
        String piece = board.getSquare(from);

        if (piece.isEmpty()) { board.setGameState("Invalid Move"); return board; }

        // ── Turn validation ──────────────────────────────────────────────────
        boolean isWhiteTurn  = board.getSideToMove().equals("w");
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));

        if (isWhiteTurn != isWhitePiece) {
            board.setGameState("Not your turn");
            return board;
        }

        // ── Own-piece capture guard ──────────────────────────────────────────
        String target = board.getSquare(to);
        if (!target.isEmpty() && (isWhitePiece == Character.isUpperCase(target.charAt(0)))) {
            board.setGameState("Cannot capture own piece");
            return board;
        }

        // ── Move validation ──────────────────────────────────────────────────
        Moves moves = new Moves(piece, board);
        if (!identifyPlayPiece(piece, moves, from, to)) {
            board.setGameState("Invalid Move");
            return board;
        }

        // ── Castling: king must not start in or pass through check ───────────
        boolean isCastling = piece.equalsIgnoreCase("k")
                && Math.abs(from.charAt(0) - to.charAt(0)) == 2;

        if (isCastling) {
            String mid = getMiddleSquare(from, to);
            if (inCheck(board, from, isWhiteTurn) || inCheck(board, mid, isWhiteTurn)) {
                board.setGameState("Illegal: Cannot castle out of or through check");
                return board;
            }
        }

        // ── Execute move (bitboards only — no FEN side-to-move toggle yet) ───
        String captured = board.getSquare(to);
        makeMove(from, to, board, piece);
        if (isCastling) handleCastlingRook(from, to, board, false);

        // ── King-safety check ────────────────────────────────────────────────
        String kingPos = findKingPos(board, isWhiteTurn,
                piece.equalsIgnoreCase("k") ? to : null);

        if (!kingPos.isEmpty() && inCheck(board, kingPos, isWhiteTurn)) {
            undoMove(from, to, board, piece, captured);
            if (isCastling) handleCastlingRook(from, to, board, true);
            board.setGameState("Illegal: King in check");
            return board;
        }

        // ── Pawn promotion ────────────────────────────────────────────────────
        if (piece.equals("P") && to.charAt(1) == '8') {
            String promo = (promotionPiece == null || promotionPiece.isEmpty())
                    ? "Q" : promotionPiece.toUpperCase();
            board.setSquare(to, promo);
        } else if (piece.equals("p") && to.charAt(1) == '1') {
            String promo = (promotionPiece == null || promotionPiece.isEmpty())
                    ? "q" : promotionPiece.toLowerCase();
            board.setSquare(to, promo);
        }

        // ── FEN bookkeeping (toggles side-to-move exactly once) ──────────────
        String finalPiece = board.getSquare(to);
        String[] epInfo   = possibleEnPassant(from, to, piece);
        updateFEN(board, finalPiece, from, epInfo != null ? epInfo[0] : null);

        // ── Game-end detection ────────────────────────────────────────────────
        String nextTurn     = isWhiteTurn ? "b" : "w";
        boolean nextInCheck = inCheck(board, findKingPos(board, !isWhiteTurn, null), !isWhiteTurn);
        boolean hasMove     = hasLegalMove(board, nextTurn);

        if (!hasMove && nextInCheck) {
            board.setGameState("Checkmate! " + (isWhiteTurn ? "White" : "Black") + " wins!");
        } else if (!hasMove) {
            board.setGameState("Game Over: Stalemate.");
        } else {
            board.setGameState("ongoing");
        }
        return board;
    }

    // =========================================================================
    // King-position helper (no allocation when king just moved)
    // =========================================================================

    private static String findKingPos(Board board, boolean isWhite, String hint) {
        if (hint != null) return hint;
        long bb = board.getPieceBitboard(isWhite ? 5 : 11);   // K=5, k=11
        if (bb == 0) return "";
        return SQUARE_NAMES[Long.numberOfTrailingZeros(bb)];
    }

    // =========================================================================
    // En-passant square after a double pawn push
    // =========================================================================

    /**
     * Returns {"e3"} (or the relevant square) if the move was a double pawn push,
     * otherwise null.  Only the EP target square is needed.
     */
    private static String[] possibleEnPassant(String from, String to, String piece) {
        char fromRank = from.charAt(1);
        char toRank   = to.charAt(1);
        char file     = from.charAt(0);

        if (piece.equals("P") && fromRank == '2' && toRank == '4')
            return new String[]{"" + file + '3'};
        if (piece.equals("p") && fromRank == '7' && toRank == '5')
            return new String[]{"" + file + '6'};

        return null;
    }

    // =========================================================================
    // Castling helpers
    // =========================================================================

    private static String getMiddleSquare(String from, String to) {
        return "" + (char)((from.charAt(0) + to.charAt(0)) / 2) + from.charAt(1);
    }

    private static void handleCastlingRook(String kFrom, String kTo, Board board, boolean undo) {
        char rank    = kFrom.charAt(1);
        boolean kingSide = kTo.charAt(0) > kFrom.charAt(0);

        String rFrom = kingSide ? "h" + rank : "a" + rank;
        String rTo   = kingSide ? "f" + rank : "d" + rank;

        if (undo) {
            String rook = board.getSquare(rTo);
            makeMove(rTo, rFrom, board, rook);
        } else {
            String rook = board.getSquare(rFrom);
            makeMove(rFrom, rTo, board, rook);
        }
    }

    // =========================================================================
    // Low-level move / undo  (bitboard mutations only — no FEN side toggle)
    // =========================================================================

    static void makeMove(String from, String to, Board board, String piece) {
        board.setSquare(from, "");
        board.setSquare(to, piece);
    }

    static void undoMove(String from, String to, Board board, String piece, String captured) {
        board.setSquare(from, piece);
        board.setSquare(to, captured.isEmpty() ? "" : captured);
    }

    // =========================================================================
    // Check detection (bitboard scan — no ArrayList allocation)
    // =========================================================================

    /**
     * Returns true if 'kingPos' is currently attacked by any opponent piece.
     * Creates one Moves object per piece TYPE (not per piece instance).
     */
    public static boolean inCheck(Board board, String kingPos, boolean checkingWhiteKing) {
        if (kingPos == null || kingPos.isEmpty()) return false;

        int oppStart = checkingWhiteKing ? BLACK_START : WHITE_START;
        int oppEnd   = checkingWhiteKing ? BLACK_END   : WHITE_END;

        for (int i = oppStart; i <= oppEnd; i++) {
            long bb = board.getPieceBitboard(i);
            if (bb == 0) continue;

            String pieceStr = Board.INDEX_TO_FEN[i];
            Moves  m        = new Moves(pieceStr, board);

            while (bb != 0) {
                int sq = Long.numberOfTrailingZeros(bb);
                if (identifyPlayPiece(pieceStr, m, SQUARE_NAMES[sq], kingPos)) return true;
                bb &= bb - 1;
            }
        }
        return false;
    }

    // =========================================================================
    // FEN bookkeeping — owns the single side-to-move toggle per half-move
    // =========================================================================

    /**
     * Called once per completed half-move.  Responsibilities:
     *  1. Strip any castling rights that were forfeited by this move.
     *  2. Set the EP square (or clear it).
     *  3. Toggle the side-to-move exactly once.
     *  4. Call board.commitFEN() to produce a valid FEN string.
     *
     * @param piece     FEN piece char that just moved (post-promotion)
     * @param from      origin square (used to strip rook castling rights)
     * @param epSquare  EP target square after a double pawn push, or null
     */
    private static void updateFEN(Board board, String piece, String from, String epSquare) {
        // ── Castling rights ───────────────────────────────────────────────────
        String rights = board.getCastlingRights();

        if (!rights.equals("-")) {
            switch (piece) {
                case "K" -> rights = rights.replace("K","").replace("Q","");
                case "k" -> rights = rights.replace("k","").replace("q","");
                case "R" -> {
                    if (from.equals("h1"))      rights = rights.replace("K","");
                    else if (from.equals("a1")) rights = rights.replace("Q","");
                }
                case "r" -> {
                    if (from.equals("h8"))      rights = rights.replace("k","");
                    else if (from.equals("a8")) rights = rights.replace("q","");
                }
            }
            if (rights.isEmpty()) rights = "-";
        }

        // ── Write back fields, toggle side-to-move, commit ───────────────────
        board.setFENFields(rights, epSquare != null ? epSquare : "-");
        board.toggleSideToMove();   // exactly once per half-move
        board.commitFEN();
    }

    // =========================================================================
    // Early-exit legal-move existence check
    // =========================================================================

    /**
     * Returns true if 'player' ("w"/"b") has at least one legal move.
     * Stops at the first legal move found — does not build the full list.
     */
    private static boolean hasLegalMove(Board board, String player) {
        boolean isWhite = player.equals("w");
        int start = isWhite ? WHITE_START : BLACK_START;
        int end   = isWhite ? WHITE_END   : BLACK_END;

        for (int i = start; i <= end; i++) {
            long bb = board.getPieceBitboard(i);
            if (bb == 0) continue;
            String pieceStr = Board.INDEX_TO_FEN[i];

            while (bb != 0) {
                int    sq   = Long.numberOfTrailingZeros(bb);
                String from = SQUARE_NAMES[sq];
                Moves  m    = new Moves(pieceStr, board);

                for (String to : ALL_SQUARES) {
                    if (from.equals(to)) continue;
                    if (!identifyPlayPiece(pieceStr, m, from, to)) continue;

                    String t = board.getSquare(to);
                    if (!t.isEmpty() && (isWhite == Character.isUpperCase(t.charAt(0)))) continue;

                    boolean tryingToCastle = pieceStr.equalsIgnoreCase("k")
                            && Math.abs(from.charAt(0) - to.charAt(0)) == 2;
                    if (tryingToCastle) {
                        String mid = getMiddleSquare(from, to);
                        if (inCheck(board, from, isWhite) || inCheck(board, mid, isWhite)) continue;
                    }

                    String captured = board.getSquare(to);
                    makeMove(from, to, board, pieceStr);
                    if (tryingToCastle) handleCastlingRook(from, to, board, false);

                    String  kPos = findKingPos(board, isWhite, pieceStr.equalsIgnoreCase("k") ? to : null);
                    boolean safe = !kPos.isEmpty() && !inCheck(board, kPos, isWhite);

                    undoMove(from, to, board, pieceStr, captured);
                    if (tryingToCastle) handleCastlingRook(from, to, board, true);

                    if (safe) return true;
                }
                bb &= bb - 1;
            }
        }
        return false;
    }

    // =========================================================================
    // Move generation  (public — used by EngineCalculations)
    // =========================================================================

    /**
     * Returns all legal destination squares for 'piece' on 'from'.
     * The Moves object is created once and reused across all 64 candidates.
     */
    public static ArrayList<String> generateAllPossibleMoves(Board board,
                                                             String from,
                                                             String piece) {
        ArrayList<String> valid = new ArrayList<>();
        boolean isWhite = Character.isUpperCase(piece.charAt(0));
        Moves   m       = new Moves(piece, board);

        for (String to : ALL_SQUARES) {
            if (from.equals(to)) continue;
            if (!identifyPlayPiece(piece, m, from, to)) continue;

            String t = board.getSquare(to);
            if (!t.isEmpty() && (isWhite == Character.isUpperCase(t.charAt(0)))) continue;

            boolean tryingToCastle = piece.equalsIgnoreCase("k")
                    && Math.abs(from.charAt(0) - to.charAt(0)) == 2;
            if (tryingToCastle) {
                String mid = getMiddleSquare(from, to);
                if (inCheck(board, from, isWhite) || inCheck(board, mid, isWhite)) continue;
            }

            String captured = board.getSquare(to);
            makeMove(from, to, board, piece);
            if (tryingToCastle) handleCastlingRook(from, to, board, false);

            String  kPos = findKingPos(board, isWhite, piece.equalsIgnoreCase("k") ? to : null);
            boolean safe = !kPos.isEmpty() && !inCheck(board, kPos, isWhite);

            undoMove(from, to, board, piece, captured);
            if (tryingToCastle) handleCastlingRook(from, to, board, true);

            if (safe) valid.add(to);
        }
        return valid;
    }

    /**
     * Returns all legal "from-to" move strings for 'player'.
     * Used by the engine and for stalemate/checkmate detection.
     */
    public static List<String> listOfLegalMoves(Board board, String player) {
        List<String> moves  = new ArrayList<>();
        boolean isWhite     = player.equals("w");
        int start = isWhite ? WHITE_START : BLACK_START;
        int end   = isWhite ? WHITE_END   : BLACK_END;

        for (int i = start; i <= end; i++) {
            long bb = board.getPieceBitboard(i);
            if (bb == 0) continue;
            String pieceStr = Board.INDEX_TO_FEN[i];

            while (bb != 0) {
                int    sq   = Long.numberOfTrailingZeros(bb);
                String from = SQUARE_NAMES[sq];
                for (String dest : generateAllPossibleMoves(board, from, pieceStr))
                    moves.add(from + "-" + dest);
                bb &= bb - 1;
            }
        }
        return moves;
    }
}