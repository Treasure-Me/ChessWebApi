package logic;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Board — Bitboard chess board.
 *
 * FEN side-to-move contract:
 *   setSquare() does NOT toggle the side-to-move and does NOT rebuild the FEN.
 *   ChessGame.updateFEN calls toggleSideToMove() exactly once per half-move,
 *   then setFENFields(), then commitFEN().
 *
 * Optimisations:
 *   FEN_TO_INDEX — 128-int array, no HashMap boxing.
 *   sqIndex()    — pure arithmetic, zero allocation.
 *   processFileAndRank() — primitive int[].
 *   buildFENPosition()   — char[] buffer, occupancy fast-path.
 *   Cheap field accessors avoid getFENStringPosition().split().
 */
public class Board {

    // =========================================================================
    // Lookup tables
    // =========================================================================

    static final int[] FEN_TO_INDEX = new int[128];
    static {
        Arrays.fill(FEN_TO_INDEX, -1);
        FEN_TO_INDEX['P'] = 0;  FEN_TO_INDEX['N'] = 1;  FEN_TO_INDEX['B'] = 2;
        FEN_TO_INDEX['R'] = 3;  FEN_TO_INDEX['Q'] = 4;  FEN_TO_INDEX['K'] = 5;
        FEN_TO_INDEX['p'] = 6;  FEN_TO_INDEX['n'] = 7;  FEN_TO_INDEX['b'] = 8;
        FEN_TO_INDEX['r'] = 9;  FEN_TO_INDEX['q'] = 10; FEN_TO_INDEX['k'] = 11;
    }

    static final String[] INDEX_TO_FEN =
            {"P","N","B","R","Q","K","p","n","b","r","q","k"};

    static final char[] INDEX_TO_CHAR =
            {'P','N','B','R','Q','K','p','n','b','r','q','k'};

    // =========================================================================
    // Bitboard state
    // =========================================================================

    final long[] pieceBB = new long[12];
    long occupancy = 0L;

    // =========================================================================
    // FEN state
    // =========================================================================

    private String fenString;
    private boolean fenDirty = false;

    private String fenSide     = "w";
    private String fenCastling = "KQkq";
    private String fenEP       = "-";
    private String fenHalf     = "0";
    private String fenFull     = "1";

    // =========================================================================
    // Game state
    // =========================================================================

    private String gameState = "ongoing";

    // =========================================================================
    // Constructors
    // =========================================================================

    public Board(String fen) {
        this.fenString = fen;
        loadFromFEN(fen);
    }

    public Board() {
        this("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    // =========================================================================
    // FEN parsing (single pass, no regex)
    // =========================================================================

    private void loadFromFEN(String fen) {
        Arrays.fill(pieceBB, 0L);
        occupancy = 0L;

        fenSide = "w"; fenCastling = "KQkq"; fenEP = "-"; fenHalf = "0"; fenFull = "1";

        int len = fen.length(), fieldStart = 0, fieldIdx = 0;
        String position = null;

        for (int i = 0; i <= len; i++) {
            if (i == len || fen.charAt(i) == ' ') {
                String field = fen.substring(fieldStart, i);
                switch (fieldIdx) {
                    case 0 -> position    = field;
                    case 1 -> fenSide     = field;
                    case 2 -> fenCastling = field;
                    case 3 -> fenEP       = field;
                    case 4 -> fenHalf     = field;
                    case 5 -> fenFull     = field;
                }
                fieldStart = i + 1;
                if (++fieldIdx > 5) break;
            }
        }

        if (position == null) return;

        int rank = 7, file = 0;
        for (int i = 0, plen = position.length(); i < plen; i++) {
            char c = position.charAt(i);
            if (c == '/') { rank--; file = 0; }
            else if (c >= '1' && c <= '8') { file += (c - '0'); }
            else {
                int idx = (c < 128) ? FEN_TO_INDEX[c] : -1;
                if (idx >= 0) {
                    long bit = 1L << (rank * 8 + file);
                    pieceBB[idx] |= bit;
                    occupancy    |= bit;
                }
                file++;
            }
        }
        fenDirty = false;
    }

    // =========================================================================
    // FEN building
    // =========================================================================

    private String buildFENPosition() {
        char[] buf = new char[72];
        int pos = 0;
        for (int rank = 7; rank >= 0; rank--) {
            long rankMask = 0xFFL << (rank * 8);
            if ((occupancy & rankMask) == 0) {
                buf[pos++] = '8';
            } else {
                int empty = 0;
                for (int f = 0; f < 8; f++) {
                    long bit = 1L << (rank * 8 + f);
                    if ((occupancy & bit) == 0) {
                        empty++;
                    } else {
                        if (empty > 0) { buf[pos++] = (char)('0' + empty); empty = 0; }
                        for (int i = 0; i < 12; i++) {
                            if ((pieceBB[i] & bit) != 0) { buf[pos++] = INDEX_TO_CHAR[i]; break; }
                        }
                    }
                }
                if (empty > 0) buf[pos++] = (char)('0' + empty);
            }
            if (rank > 0) buf[pos++] = '/';
        }
        return new String(buf, 0, pos);
    }

    /**
     * Forces a complete FEN rebuild from current bitboards + field values.
     * Called once by ChessGame.updateFEN after each completed half-move.
     */
    public void commitFEN() {
        fenString = buildFENPosition()
                + " " + fenSide     + " " + fenCastling
                + " " + fenEP       + " " + fenHalf + " " + fenFull;
        fenDirty = false;
    }

    /** Toggles the side-to-move field. Called once per half-move by updateFEN. */
    public void toggleSideToMove() {
        fenSide  = fenSide.equals("w") ? "b" : "w";
        fenDirty = true;
    }

    /**
     * Updates castling rights and EP square.
     * Must be followed by commitFEN() to produce a valid FEN string.
     */
    public void setFENFields(String castling, String ep) {
        this.fenCastling = (castling == null || castling.isEmpty()) ? "-" : castling;
        this.fenEP       = (ep       == null || ep.isEmpty())       ? "-" : ep;
        fenDirty = true;
    }

    // =========================================================================
    // Public FEN accessors
    // =========================================================================

    public String getFENStringPosition() {
        if (fenDirty) commitFEN();
        return fenString;
    }

    public String getSideToMove()      { return fenSide; }
    public String getCastlingRights()  { return fenCastling; }
    public String getEnPassantSquare() { return fenEP; }
    public String getHalfmoveClock()   { return fenHalf; }
    public String getFullmoveNumber()  { return fenFull; }

    /** Updates the halfmove clock (called by ChessGame.updateFEN). */
    public void setHalfmoveClock(int half) {
        fenHalf  = String.valueOf(half);
        fenDirty = true;
    }

    public void setRawFEN(String fen) {
        this.fenString = fen;
        loadFromFEN(fen);
        fenDirty = false;
    }

    // =========================================================================
    // Square index helpers
    // =========================================================================

    public static int sqIndex(String square) {
        return ((square.charAt(1) - '1') << 3) | (square.charAt(0) - 'a');
    }

    public int[] processFileAndRank(String square) {
        if (square == null || square.length() != 2)
            throw new IllegalArgumentException("Invalid square: " + square);
        int col = square.charAt(0) - 'a';
        int row = '8' - square.charAt(1);
        if ((col | row | (7 - col) | (7 - row)) < 0)
            throw new IllegalArgumentException("Square out of bounds: " + square);
        return new int[]{col, row};
    }

    // =========================================================================
    // Square accessors
    // =========================================================================

    public String getSquare(String square) {
        if (square == null || square.length() != 2) return "";
        long bit = 1L << sqIndex(square);
        if ((occupancy & bit) == 0) return "";
        for (int i = 0; i < 12; i++) {
            if ((pieceBB[i] & bit) != 0) return INDEX_TO_FEN[i];
        }
        return "";
    }

    public int getPieceAt(int sq) {
        long bit = 1L << sq;
        if ((occupancy & bit) == 0) return -1;
        for (int i = 0; i < 12; i++) {
            if ((pieceBB[i] & bit) != 0) return i;
        }
        return -1;
    }

    /**
     * Places 'piece' on 'square', or clears it if piece is null/"".
     * Does NOT toggle side-to-move. Marks fenDirty only.
     */
    public void setSquare(String square, String piece) {
        long bit = 1L << sqIndex(square);
        for (int i = 0; i < 12; i++) pieceBB[i] &= ~bit;
        occupancy &= ~bit;

        if (piece != null && !piece.isEmpty()) {
            char c = piece.charAt(0);
            if (c < 128) {
                int idx = FEN_TO_INDEX[c];
                if (idx >= 0) { pieceBB[idx] |= bit; occupancy |= bit; }
            }
        }
        fenDirty = true;
    }

    // =========================================================================
    // 2-D board view
    // =========================================================================

    public String[][] getCleanSquares() {
        String[][] squares = new String[8][8];
        for (int row = 0; row < 8; row++) {
            int  bitRank = 7 - row;
            long rankBB  = (occupancy >>> (bitRank * 8)) & 0xFFL;
            for (int col = 0; col < 8; col++) {
                if ((rankBB & (1L << col)) == 0) {
                    squares[row][col] = "";
                } else {
                    long bit = 1L << (bitRank * 8 + col);
                    squares[row][col] = "";
                    for (int i = 0; i < 12; i++) {
                        if ((pieceBB[i] & bit) != 0) { squares[row][col] = INDEX_TO_FEN[i]; break; }
                    }
                }
            }
        }
        return squares;
    }

    // =========================================================================
    // Piece-position queries
    // =========================================================================

    public long getBitboardFor(char pieceChar) {
        int idx = (pieceChar < 128) ? FEN_TO_INDEX[pieceChar] : -1;
        return (idx >= 0) ? pieceBB[idx] : 0L;
    }

    public ArrayList<String> getPiecePositions(String piece) {
        ArrayList<String> positions = new ArrayList<>();
        if (piece == null || piece.isEmpty()) return positions;
        char c = piece.charAt(0);
        int idx = (c < 128) ? FEN_TO_INDEX[c] : -1;
        if (idx < 0) return positions;
        long bb = pieceBB[idx];
        while (bb != 0) {
            int sq = Long.numberOfTrailingZeros(bb);
            positions.add("" + (char)('a' + (sq & 7)) + (char)('1' + (sq >> 3)));
            bb &= bb - 1;
        }
        return positions;
    }

    // =========================================================================
    // Bitboard accessors
    // =========================================================================

    public long getPieceBitboard(int i) { return pieceBB[i]; }
    public long getOccupancy()          { return occupancy; }
    public long getWhitePieces()        { return pieceBB[0]|pieceBB[1]|pieceBB[2]|pieceBB[3]|pieceBB[4]|pieceBB[5]; }
    public long getBlackPieces()        { return pieceBB[6]|pieceBB[7]|pieceBB[8]|pieceBB[9]|pieceBB[10]|pieceBB[11]; }

    // =========================================================================
    // Game state
    // =========================================================================

    public String getGameState()         { return gameState; }
    public void   setGameState(String s) { this.gameState = s; }

    // =========================================================================
    // Debug
    // =========================================================================

    public void printBoard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String p = getSquare("" + (char)('a' + col) + (char)('8' - row));
                System.out.print((p.isEmpty() ? "." : p) + " ");
            }
            System.out.println();
        }
        System.out.println("FEN: " + getFENStringPosition());
    }
}