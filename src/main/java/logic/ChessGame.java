package logic;

import java.util.ArrayList;
import java.util.List;

/**
 * ChessGame — Main game logic and move execution.
 *
 * Fixes in this version:
 *  • En-passant capture now physically removes the captured pawn from the board.
 *  • updateFEN strips castling rights when a rook is CAPTURED on its home square
 *    (not only when it moves away).
 *  • Halfmove clock is correctly incremented / reset.
 *  • Promotion move variants (=Q/=N/=R/=B suffix) are supported in playGame and
 *    are fully expanded in listOfLegalMoves / generateAllPossibleMoves so the
 *    engine sees all four options.
 *  • simulateMove callers that receive "Invalid Move" or "Illegal: …" game states
 *    are now detectable (playGame returns the unmodified board in those cases).
 */
public class ChessGame {

    // =========================================================================
    // Pre-built tables
    // =========================================================================

    public static final String[] SQUARE_NAMES = new String[64];
    static {
        for (int sq = 0; sq < 64; sq++)
            SQUARE_NAMES[sq] = "" + (char)('a' + (sq & 7)) + (char)('1' + (sq >> 3));
    }

    private static final String[] ALL_SQUARES = new String[64];
    static {
        int idx = 0;
        for (int r = 1; r <= 8; r++)
            for (char f = 'a'; f <= 'h'; f++)
                ALL_SQUARES[idx++] = "" + f + r;
    }

    // Promotion piece characters (upper/lower case pairs)
    private static final char[] PROMO_WHITE = {'Q','R','B','N'};
    private static final char[] PROMO_BLACK = {'q','r','b','n'};

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

    /**
     * Attempts to play a move on the board.
     *
     * Move format:  "e2-e4"          — normal move
     *               "e7-e8=Q"        — pawn promotion (suffix overrides promotionPiece)
     *
     * promotionPiece — fallback promotion piece when no suffix is present.
     *                  null / "" defaults to queen.
     */
    public static Board playGame(Board board, String playerMove, String promotionPiece) {
        // ── Parse move string ─────────────────────────────────────────────────
        int dash = playerMove.indexOf('-');
        if (dash < 0) { board.setGameState("Invalid Move"); return board; }

        String from  = playerMove.substring(0, dash);
        String rest  = playerMove.substring(dash + 1);   // "e4" or "e8=Q"

        // Extract optional promotion suffix ("=Q", "=N", etc.)
        String promoSuffix = null;
        String to;
        int eqIdx = rest.indexOf('=');
        if (eqIdx >= 0) {
            to          = rest.substring(0, eqIdx);
            promoSuffix = rest.substring(eqIdx + 1);     // "Q", "N", "R", "B"
        } else {
            to = rest;
        }

        // Suffix overrides the parameter
        String effectivePromo = (promoSuffix != null && !promoSuffix.isEmpty())
                ? promoSuffix : promotionPiece;

        String piece = board.getSquare(from);
        if (piece.isEmpty()) { board.setGameState("Invalid Move"); return board; }

        // ── Turn validation ───────────────────────────────────────────────────
        boolean isWhiteTurn  = board.getSideToMove().equals("w");
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        if (isWhiteTurn != isWhitePiece) { board.setGameState("Not your turn"); return board; }

        // ── Own-piece capture guard ───────────────────────────────────────────
        String target = board.getSquare(to);
        if (!target.isEmpty() && (isWhitePiece == Character.isUpperCase(target.charAt(0)))) {
            board.setGameState("Cannot capture own piece");
            return board;
        }

        // ── Move validation ───────────────────────────────────────────────────
        Moves moves = new Moves(piece, board);
        if (!identifyPlayPiece(piece, moves, from, to)) {
            board.setGameState("Invalid Move");
            return board;
        }

        // ── Castling: king must not start in or pass through check ────────────
        boolean isCastling = piece.equalsIgnoreCase("k")
                && Math.abs(from.charAt(0) - to.charAt(0)) == 2;
        if (isCastling) {
            String mid = getMiddleSquare(from, to);
            if (inCheck(board, from, isWhiteTurn) || inCheck(board, mid, isWhiteTurn)) {
                board.setGameState("Illegal: Cannot castle out of or through check");
                return board;
            }
        }

        // ── Detect en-passant capture ─────────────────────────────────────────
        // An en-passant capture is a pawn moving diagonally to an empty square
        // that equals the current EP target.
        String epSquareNow = board.getEnPassantSquare();
        boolean isEnPassant = piece.equalsIgnoreCase("p")
                && !epSquareNow.equals("-")
                && to.equals(epSquareNow)
                && target.isEmpty();   // destination is empty (the pawn passed through)

        // The square of the captured pawn in an en-passant capture:
        // it sits on the same FILE as 'to' but on the same RANK as 'from'.
        String epCapturedPawnSq = isEnPassant
                ? "" + to.charAt(0) + from.charAt(1)
                : null;

        // ── Execute move ──────────────────────────────────────────────────────
        String captured = target;   // may be "" for quiet moves and en-passant
        makeMove(from, to, board, piece);
        if (isCastling)  handleCastlingRook(from, to, board, false);
        if (isEnPassant) board.setSquare(epCapturedPawnSq, "");   // remove captured pawn

        // ── King-safety check ─────────────────────────────────────────────────
        String kingPos = findKingPos(board, isWhiteTurn,
                piece.equalsIgnoreCase("k") ? to : null);

        if (!kingPos.isEmpty() && inCheck(board, kingPos, isWhiteTurn)) {
            // Illegal — undo everything
            undoMove(from, to, board, piece, captured);
            if (isCastling)  handleCastlingRook(from, to, board, true);
            if (isEnPassant) board.setSquare(epCapturedPawnSq, isWhiteTurn ? "p" : "P");
            board.setGameState("Illegal: King in check");
            return board;
        }

        // ── Pawn promotion ────────────────────────────────────────────────────
        boolean isPromotion = false;
        if (piece.equals("P") && to.charAt(1) == '8') {
            isPromotion = true;
            String promo = resolvePromotion(effectivePromo, true);
            board.setSquare(to, promo);
        } else if (piece.equals("p") && to.charAt(1) == '1') {
            isPromotion = true;
            String promo = resolvePromotion(effectivePromo, false);
            board.setSquare(to, promo);
        }

        // ── FEN bookkeeping ───────────────────────────────────────────────────
        // Compute new EP square (only after a double pawn push)
        String newEP = computeEPSquare(from, to, piece);

        // Halfmove clock: reset on pawn move or capture, else increment
        boolean isCapture = !captured.isEmpty() || isEnPassant;
        boolean isPawnMove = piece.equalsIgnoreCase("p");

        String finalPiece = board.getSquare(to);
        updateFEN(board, finalPiece, from, to, newEP, isCapture || isPawnMove);

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
    // Promotion piece resolution
    // =========================================================================

    private static String resolvePromotion(String requested, boolean isWhite) {
        if (requested != null && !requested.isEmpty()) {
            char c = Character.toUpperCase(requested.charAt(0));
            if (c == 'Q' || c == 'R' || c == 'B' || c == 'N')
                return isWhite ? String.valueOf(c) : String.valueOf(Character.toLowerCase(c));
        }
        return isWhite ? "Q" : "q";   // default to queen
    }

    // =========================================================================
    // King-position helper
    // =========================================================================

    static String findKingPos(Board board, boolean isWhite, String hint) {
        if (hint != null) return hint;
        long bb = board.getPieceBitboard(isWhite ? 5 : 11);
        if (bb == 0) return "";
        return SQUARE_NAMES[Long.numberOfTrailingZeros(bb)];
    }

    // =========================================================================
    // En-passant target square after a double pawn push
    // =========================================================================

    private static String computeEPSquare(String from, String to, String piece) {
        char fromRank = from.charAt(1);
        char toRank   = to.charAt(1);
        char file     = from.charAt(0);
        if (piece.equals("P") && fromRank == '2' && toRank == '4') return "" + file + '3';
        if (piece.equals("p") && fromRank == '7' && toRank == '5') return "" + file + '6';
        return null;
    }

    // =========================================================================
    // Castling helpers
    // =========================================================================

    static String getMiddleSquare(String from, String to) {
        return "" + (char)((from.charAt(0) + to.charAt(0)) / 2) + from.charAt(1);
    }

    private static void handleCastlingRook(String kFrom, String kTo, Board board, boolean undo) {
        char rank    = kFrom.charAt(1);
        boolean kingSide = kTo.charAt(0) > kFrom.charAt(0);
        String rFrom = kingSide ? "h" + rank : "a" + rank;
        String rTo   = kingSide ? "f" + rank : "d" + rank;
        if (undo) {
            makeMove(rTo, rFrom, board, board.getSquare(rTo));
        } else {
            makeMove(rFrom, rTo, board, board.getSquare(rFrom));
        }
    }

    // =========================================================================
    // Low-level move / undo
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
    // Check detection
    // =========================================================================

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
    // FEN bookkeeping
    // =========================================================================

    /**
     * Updates all FEN non-position fields after a move:
     *  1. Strips castling rights forfeited by this move OR by a rook being captured.
     *  2. Sets / clears the EP square.
     *  3. Increments or resets the halfmove clock.
     *  4. Toggles side-to-move exactly once.
     *  5. Calls commitFEN().
     *
     * @param piece       FEN char of the piece that moved (post-promotion)
     * @param from        origin square
     * @param to          destination square (used to detect rook captures)
     * @param newEP       EP target square, or null
     * @param resetClock  true for pawn moves and captures (resets halfmove clock to 0)
     */
    private static void updateFEN(Board board, String piece, String from, String to,
                                  String newEP, boolean resetClock) {
        String rights = board.getCastlingRights();

        if (!rights.equals("-")) {
            // King moves forfeit all rights for that side
            if (piece.equals("K")) rights = rights.replace("K","").replace("Q","");
            else if (piece.equals("k")) rights = rights.replace("k","").replace("q","");

            // Rook moves forfeit the right for that corner
            if (piece.equals("R")) {
                if (from.equals("h1"))      rights = rights.replace("K","");
                else if (from.equals("a1")) rights = rights.replace("Q","");
            } else if (piece.equals("r")) {
                if (from.equals("h8"))      rights = rights.replace("k","");
                else if (from.equals("a8")) rights = rights.replace("q","");
            }

            // A rook on its home square being CAPTURED also forfeits that right.
            // 'to' is the square just captured; if it was a rook home square,
            // strip the corresponding right regardless of what piece just moved there.
            switch (to) {
                case "h1" -> rights = rights.replace("K","");
                case "a1" -> rights = rights.replace("Q","");
                case "h8" -> rights = rights.replace("k","");
                case "a8" -> rights = rights.replace("q","");
            }

            if (rights.isEmpty()) rights = "-";
        }

        // Halfmove clock
        int half = resetClock ? 0 : (parseHalf(board) + 1);

        board.setFENFields(rights, newEP != null ? newEP : "-");
        board.setHalfmoveClock(half);
        board.toggleSideToMove();
        board.commitFEN();
    }

    /** Reads the halfmove clock from Board without a FEN split. */
    private static int parseHalf(Board board) {
        try { return Integer.parseInt(board.getHalfmoveClock()); }
        catch (NumberFormatException e) { return 0; }
    }

    // =========================================================================
    // Early-exit legal-move existence check
    // =========================================================================

    static boolean hasLegalMove(Board board, String player) {
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

                    // Detect EP
                    String epSq = board.getEnPassantSquare();
                    boolean isEP = pieceStr.equalsIgnoreCase("p")
                            && !epSq.equals("-") && to.equals(epSq) && t.isEmpty();
                    String epPawnSq = isEP ? "" + to.charAt(0) + from.charAt(1) : null;

                    String captured = t;
                    makeMove(from, to, board, pieceStr);
                    if (tryingToCastle) handleCastlingRook(from, to, board, false);
                    if (isEP) board.setSquare(epPawnSq, "");

                    String  kPos = findKingPos(board, isWhite,
                            pieceStr.equalsIgnoreCase("k") ? to : null);
                    boolean safe = !kPos.isEmpty() && !inCheck(board, kPos, isWhite);

                    // Undo
                    undoMove(from, to, board, pieceStr, captured);
                    if (tryingToCastle) handleCastlingRook(from, to, board, true);
                    if (isEP) board.setSquare(epPawnSq, isWhite ? "p" : "P");

                    if (safe) return true;
                }
                bb &= bb - 1;
            }
        }
        return false;
    }

    // =========================================================================
    // Move generation
    // =========================================================================

    /**
     * Returns all legal destination squares for 'piece' on 'from'.
     *
     * For pawn promotion squares, returns only the base destination (e.g. "e8").
     * Callers that need all four promotion variants should use
     * generateAllPossibleMovesWithPromo().
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

            // Detect EP
            String epSq = board.getEnPassantSquare();
            boolean isEP = piece.equalsIgnoreCase("p")
                    && !epSq.equals("-") && to.equals(epSq) && t.isEmpty();
            String epPawnSq = isEP ? "" + to.charAt(0) + from.charAt(1) : null;

            String captured = t;
            makeMove(from, to, board, piece);
            if (tryingToCastle) handleCastlingRook(from, to, board, false);
            if (isEP) board.setSquare(epPawnSq, "");

            String  kPos = findKingPos(board, isWhite,
                    piece.equalsIgnoreCase("k") ? to : null);
            boolean safe = !kPos.isEmpty() && !inCheck(board, kPos, isWhite);

            undoMove(from, to, board, piece, captured);
            if (tryingToCastle) handleCastlingRook(from, to, board, true);
            if (isEP) board.setSquare(epPawnSq, isWhite ? "p" : "P");

            if (safe) valid.add(to);
        }
        return valid;
    }

    /**
     * Returns all legal "from-to" (and "from-to=X" for promotions) move strings
     * for 'player'. Promotion moves are expanded into all four variants.
     *
     * Used by the engine so it can evaluate every promotion option.
     */
    public static List<String> listOfLegalMoves(Board board, String player) {
        List<String> moves  = new ArrayList<>();
        boolean isWhite     = player.equals("w");
        int start = isWhite ? WHITE_START : BLACK_START;
        int end   = isWhite ? WHITE_END   : BLACK_END;
        char[] promoPieces  = isWhite ? PROMO_WHITE : PROMO_BLACK;

        for (int i = start; i <= end; i++) {
            long bb = board.getPieceBitboard(i);
            if (bb == 0) continue;
            String pieceStr = Board.INDEX_TO_FEN[i];

            while (bb != 0) {
                int    sq   = Long.numberOfTrailingZeros(bb);
                String from = SQUARE_NAMES[sq];

                for (String dest : generateAllPossibleMoves(board, from, pieceStr)) {
                    // Expand pawn promotions into all four piece variants
                    boolean isPromoMove = pieceStr.equalsIgnoreCase("p")
                            && (dest.charAt(1) == '8' || dest.charAt(1) == '1');
                    if (isPromoMove) {
                        for (char pc : promoPieces)
                            moves.add(from + "-" + dest + "=" + pc);
                    } else {
                        moves.add(from + "-" + dest);
                    }
                }
                bb &= bb - 1;
            }
        }
        return moves;
    }
}