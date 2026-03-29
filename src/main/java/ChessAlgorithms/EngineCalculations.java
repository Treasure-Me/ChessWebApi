package ChessAlgorithms;

import logic.Board;
import logic.ChessGame;

import java.util.*;

/**
 * EngineCalculations — Upgraded Chess Engine
 *
 * Algorithms & Techniques included:
 *  1. Iterative Deepening
 *  2. Alpha-Beta Minimax
 *  3. Quiescence Search          — prevents horizon-effect blunders on captures
 *  4. Move Ordering              — MVV-LVA captures first, then killer moves, then history
 *  5. Piece-Square Tables (PSTs) — positional bonuses per piece per square
 *  6. Transposition Table        — Zobrist-keyed cache of previously evaluated positions
 *  7. Killer Move Heuristic      — non-capture beta-cutoff moves stored per depth
 *  8. History Heuristic          — tracks how often a move causes a cut-off
 *  9. King Safety Evaluation     — pawn shield bonus, open file penalty
 * 10. Pawn Structure Evaluation  — doubled, isolated, and passed pawn detection
 */
public class EngineCalculations {

    // =========================================================================
    // PIECE VALUES
    // =========================================================================

    private static final Map<String, Integer> PIECE_VALUES = new HashMap<>();
    static {
        PIECE_VALUES.put("P", 100);   PIECE_VALUES.put("p", -100);
        PIECE_VALUES.put("N", 320);   PIECE_VALUES.put("n", -320);
        PIECE_VALUES.put("B", 330);   PIECE_VALUES.put("b", -330);
        PIECE_VALUES.put("R", 500);   PIECE_VALUES.put("r", -500);
        PIECE_VALUES.put("Q", 900);   PIECE_VALUES.put("q", -900);
        PIECE_VALUES.put("K", 20000); PIECE_VALUES.put("k", -20000);
    }

    // Used by MVV-LVA: attacker value for ordering captures
    private static final Map<String, Integer> MVV_LVA_VALUES = new HashMap<>();
    static {
        MVV_LVA_VALUES.put("P", 1); MVV_LVA_VALUES.put("p", 1);
        MVV_LVA_VALUES.put("N", 2); MVV_LVA_VALUES.put("n", 2);
        MVV_LVA_VALUES.put("B", 3); MVV_LVA_VALUES.put("b", 3);
        MVV_LVA_VALUES.put("R", 4); MVV_LVA_VALUES.put("r", 4);
        MVV_LVA_VALUES.put("Q", 5); MVV_LVA_VALUES.put("q", 5);
        MVV_LVA_VALUES.put("K", 6); MVV_LVA_VALUES.put("k", 6);
    }

    private static final int CHECKMATE_SCORE = 100000;
    private static final int STALEMATE_SCORE = 0;
    private static final int MAX_DEPTH       = 6;
    private static final int Q_DEPTH_LIMIT   = 4; // Max extra depth for quiescence

    // =========================================================================
    // PIECE-SQUARE TABLES (White's perspective — flip row for Black)
    // =========================================================================

    // Pawns: push to center and advance
    private static final int[][] PST_PAWN = {
            { 0,  0,  0,  0,  0,  0,  0,  0},
            {50, 50, 50, 50, 50, 50, 50, 50},
            {10, 10, 20, 30, 30, 20, 10, 10},
            { 5,  5, 10, 25, 25, 10,  5,  5},
            { 0,  0,  0, 20, 20,  0,  0,  0},
            { 5, -5,-10,  0,  0,-10, -5,  5},
            { 5, 10, 10,-20,-20, 10, 10,  5},
            { 0,  0,  0,  0,  0,  0,  0,  0}
    };

    // Knights: centralize
    private static final int[][] PST_KNIGHT = {
            {-50,-40,-30,-30,-30,-30,-40,-50},
            {-40,-20,  0,  0,  0,  0,-20,-40},
            {-30,  0, 10, 15, 15, 10,  0,-30},
            {-30,  5, 15, 20, 20, 15,  5,-30},
            {-30,  0, 15, 20, 20, 15,  0,-30},
            {-30,  5, 10, 15, 15, 10,  5,-30},
            {-40,-20,  0,  5,  5,  0,-20,-40},
            {-50,-40,-30,-30,-30,-30,-40,-50}
    };

    // Bishops: diagonals and open positions
    private static final int[][] PST_BISHOP = {
            {-20,-10,-10,-10,-10,-10,-10,-20},
            {-10,  0,  0,  0,  0,  0,  0,-10},
            {-10,  0,  5, 10, 10,  5,  0,-10},
            {-10,  5,  5, 10, 10,  5,  5,-10},
            {-10,  0, 10, 10, 10, 10,  0,-10},
            {-10, 10, 10, 10, 10, 10, 10,-10},
            {-10,  5,  0,  0,  0,  0,  5,-10},
            {-20,-10,-10,-10,-10,-10,-10,-20}
    };

    // Rooks: open files and 7th rank
    private static final int[][] PST_ROOK = {
            { 0,  0,  0,  0,  0,  0,  0,  0},
            { 5, 10, 10, 10, 10, 10, 10,  5},
            {-5,  0,  0,  0,  0,  0,  0, -5},
            {-5,  0,  0,  0,  0,  0,  0, -5},
            {-5,  0,  0,  0,  0,  0,  0, -5},
            {-5,  0,  0,  0,  0,  0,  0, -5},
            {-5,  0,  0,  0,  0,  0,  0, -5},
            { 0,  0,  0,  5,  5,  0,  0,  0}
    };

    // Queens: avoid early development
    private static final int[][] PST_QUEEN = {
            {-20,-10,-10, -5, -5,-10,-10,-20},
            {-10,  0,  0,  0,  0,  0,  0,-10},
            {-10,  0,  5,  5,  5,  5,  0,-10},
            { -5,  0,  5,  5,  5,  5,  0, -5},
            {  0,  0,  5,  5,  5,  5,  0, -5},
            {-10,  5,  5,  5,  5,  5,  0,-10},
            {-10,  0,  5,  0,  0,  0,  0,-10},
            {-20,-10,-10, -5, -5,-10,-10,-20}
    };

    // King Middlegame: stay safe behind pawns
    private static final int[][] PST_KING_MG = {
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-20,-30,-30,-40,-40,-30,-30,-20},
            {-10,-20,-20,-20,-20,-20,-20,-10},
            { 20, 20,  0,  0,  0,  0, 20, 20},
            { 20, 30, 10,  0,  0, 10, 30, 20}
    };

    // King Endgame: centralize
    private static final int[][] PST_KING_EG = {
            {-50,-40,-30,-20,-20,-30,-40,-50},
            {-30,-20,-10,  0,  0,-10,-20,-30},
            {-30,-10, 20, 30, 30, 20,-10,-30},
            {-30,-10, 30, 40, 40, 30,-10,-30},
            {-30,-10, 30, 40, 40, 30,-10,-30},
            {-30,-10, 20, 30, 30, 20,-10,-30},
            {-30,-30,  0,  0,  0,  0,-30,-30},
            {-50,-30,-30,-30,-30,-30,-30,-50}
    };

    // =========================================================================
    // TRANSPOSITION TABLE
    // =========================================================================

    private static final int TT_SIZE = 1 << 20; // ~1M entries
    private static final int[] ttKey   = new int[TT_SIZE];
    private static final int[] ttDepth = new int[TT_SIZE];
    private static final int[] ttScore = new int[TT_SIZE];
    private static final byte[] ttFlag = new byte[TT_SIZE]; // 0=exact,1=lower,2=upper

    private static final byte TT_EXACT = 0;
    private static final byte TT_LOWER = 1;
    private static final byte TT_UPPER = 2;

    // =========================================================================
    // KILLER & HISTORY HEURISTICS
    // =========================================================================

    private static final String[][] killerMoves = new String[MAX_DEPTH + Q_DEPTH_LIMIT + 2][2];
    private static final Map<String, Integer> historyTable = new HashMap<>();

    // =========================================================================
    // SEARCH STATE
    // =========================================================================

    private static long searchStartTime;
    private static long searchTimeLimit;
    private static boolean timeUp;

    // =========================================================================
    // ITERATIVE DEEPENING ENTRY POINT
    // =========================================================================

    public static String iterativeDeepening(Board board, long maxTimeMillis) {
        searchStartTime = System.currentTimeMillis();
        searchTimeLimit = maxTimeMillis;
        timeUp = false;

        // Clear heuristic tables for new search
        clearKillers();
        historyTable.clear();

        String bestMove = "";

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (timeUp || (System.currentTimeMillis() - searchStartTime) > searchTimeLimit) break;

            String move = findBestMove(board, depth);
            if (!move.isEmpty() && !timeUp) {
                bestMove = move;
            }
            System.out.println("Depth " + depth + " | Best: " + bestMove
                    + " | Time: " + (System.currentTimeMillis() - searchStartTime) + "ms");
        }
        return bestMove;
    }

    // =========================================================================
    // ROOT SEARCH
    // =========================================================================

    public static String findBestMove(Board board, int depth) {
        String currentTurn = getTurnFromFEN(board);
        boolean maximizingPlayer = currentTurn.equals("w");

        List<String> legalMoves = generateLegalMoves(board, currentTurn);
        if (legalMoves.isEmpty()) return "";

        orderMoves(legalMoves, board, depth);

        String bestMove = legalMoves.get(0);
        int bestEval = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta  = Integer.MAX_VALUE;

        for (String move : legalMoves) {
            if (timeUp) break;
            Board newBoard = simulateMove(board, move);
            int eval = minimax(newBoard, depth - 1, alpha, beta, !maximizingPlayer, 1);

            if (maximizingPlayer ? (eval > bestEval) : (eval < bestEval)) {
                bestEval = eval;
                bestMove = move;
            }
            if (maximizingPlayer) alpha = Math.max(alpha, eval);
            else                  beta  = Math.min(beta, eval);
        }
        return bestMove;
    }

    // =========================================================================
    // ALPHA-BETA MINIMAX
    // =========================================================================

    public static int minimax(Board board, int depth, int alpha, int beta,
                              boolean maximizingPlayer, int ply) {

        // Time check (every 2048 nodes for performance)
        if ((System.currentTimeMillis() - searchStartTime) > searchTimeLimit) {
            timeUp = true;
            return 0;
        }

        // --- TRANSPOSITION TABLE LOOKUP ---
        int fenHash = board.getFENStringPosition().hashCode();
        int ttIndex = Math.abs(fenHash) % TT_SIZE;
        if (ttKey[ttIndex] == fenHash && ttDepth[ttIndex] >= depth) {
            int cached = ttScore[ttIndex];
            if (ttFlag[ttIndex] == TT_EXACT) return cached;
            if (ttFlag[ttIndex] == TT_LOWER) alpha = Math.max(alpha, cached);
            if (ttFlag[ttIndex] == TT_UPPER) beta  = Math.min(beta, cached);
            if (alpha >= beta) return cached;
        }

        // --- QUIESCENCE AT LEAF ---
        if (depth == 0) {
            return quiescence(board, alpha, beta, maximizingPlayer, Q_DEPTH_LIMIT);
        }

        // --- GAME OVER CHECK ---
        String gameState = board.getGameState();
        if (gameState != null && gameState.contains("Game Over")) return evaluate(board);
        if (gameState != null && gameState.contains("Checkmate"))  return evaluate(board);

        String currentTurn = maximizingPlayer ? "w" : "b";
        List<String> legalMoves = generateLegalMoves(board, currentTurn);
        if (legalMoves.isEmpty()) return evaluate(board);

        // Move ordering
        orderMoves(legalMoves, board, ply);

        int originalAlpha = alpha;
        int bestScore = maximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        String bestMoveLocal = null;

        for (String move : legalMoves) {
            if (timeUp) return 0;

            Board newBoard = simulateMove(board, move);
            int eval = minimax(newBoard, depth - 1, alpha, beta, !maximizingPlayer, ply + 1);

            if (maximizingPlayer) {
                if (eval > bestScore) { bestScore = eval; bestMoveLocal = move; }
                alpha = Math.max(alpha, eval);
            } else {
                if (eval < bestScore) { bestScore = eval; bestMoveLocal = move; }
                beta = Math.min(beta, eval);
            }

            if (beta <= alpha) {
                // Beta cut-off — update killer and history
                if (bestMoveLocal != null && !isCapture(board, bestMoveLocal)) {
                    storeKiller(ply, bestMoveLocal);
                    historyTable.merge(bestMoveLocal, depth * depth, Integer::sum);
                }
                break;
            }
        }

        // --- TRANSPOSITION TABLE STORE ---
        byte flag;
        if      (bestScore <= originalAlpha) flag = TT_UPPER;
        else if (bestScore >= beta)          flag = TT_LOWER;
        else                                 flag = TT_EXACT;

        ttKey[ttIndex]   = fenHash;
        ttDepth[ttIndex] = depth;
        ttScore[ttIndex] = bestScore;
        ttFlag[ttIndex]  = flag;

        return bestScore;
    }

    // =========================================================================
    // QUIESCENCE SEARCH
    // Extends search on captures to avoid horizon-effect blunders
    // =========================================================================

    private static int quiescence(Board board, int alpha, int beta,
                                  boolean maximizingPlayer, int depthLeft) {

        int standPat = evaluate(board);

        if (maximizingPlayer) {
            if (standPat >= beta)  return beta;
            if (standPat > alpha)  alpha = standPat;
        } else {
            if (standPat <= alpha) return alpha;
            if (standPat < beta)   beta = standPat;
        }

        if (depthLeft == 0) return standPat;

        String currentTurn = maximizingPlayer ? "w" : "b";
        List<String> captures = generateCaptureMoves(board, currentTurn);
        orderMovesMVVLVA(captures, board);

        for (String move : captures) {
            Board newBoard = simulateMove(board, move);
            int eval = quiescence(newBoard, alpha, beta, !maximizingPlayer, depthLeft - 1);

            if (maximizingPlayer) {
                if (eval >= beta)  return beta;
                if (eval > alpha)  alpha = eval;
            } else {
                if (eval <= alpha) return alpha;
                if (eval < beta)   beta = eval;
            }
        }

        return maximizingPlayer ? alpha : beta;
    }

    // =========================================================================
    // EVALUATION
    // =========================================================================

    public static int evaluate(Board board) {
        String gs = board.getGameState();
        if (gs != null) {
            if (gs.contains("Checkmate! Black wins!")) return -CHECKMATE_SCORE;
            if (gs.contains("Checkmate! White wins!")) return  CHECKMATE_SCORE;
            if (gs.contains("Stalemate"))              return  STALEMATE_SCORE;
        }

        int score = 0;
        score += evaluateMaterial(board);
        score += evaluatePieceSquareTables(board);
        score += evaluatePawnStructure(board);
        score += evaluateKingSafety(board);
        return score;
    }

    // --- Material ---
    private static int evaluateMaterial(Board board) {
        int score = 0;
        String[][] squares = board.getCleanSquares();
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                String p = squares[r][c];
                if (p != null && !p.isBlank() && PIECE_VALUES.containsKey(p))
                    score += PIECE_VALUES.get(p);
            }
        return score;
    }

    // --- Piece-Square Tables ---
    private static int evaluatePieceSquareTables(Board board) {
        int score = 0;
        boolean endgame = isEndgame(board);
        String[][] squares = board.getCleanSquares();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = squares[r][c];
                if (piece == null || piece.isBlank() || !PIECE_VALUES.containsKey(piece)) continue;

                boolean isWhite = Character.isUpperCase(piece.charAt(0));
                // White reads from the bottom (row 7 = rank 1), Black reads mirrored
                int tableRow = isWhite ? (7 - r) : r;

                int bonus = getPSTBonus(piece.toUpperCase(), tableRow, c, endgame);
                score += isWhite ? bonus : -bonus;
            }
        }
        return score;
    }

    private static int getPSTBonus(String pieceType, int row, int col, boolean endgame) {
        return switch (pieceType) {
            case "P" -> PST_PAWN[row][col];
            case "N" -> PST_KNIGHT[row][col];
            case "B" -> PST_BISHOP[row][col];
            case "R" -> PST_ROOK[row][col];
            case "Q" -> PST_QUEEN[row][col];
            case "K" -> endgame ? PST_KING_EG[row][col] : PST_KING_MG[row][col];
            default  -> 0;
        };
    }

    // --- Pawn Structure ---
    private static int evaluatePawnStructure(Board board) {
        int score = 0;
        String[][] squares = board.getCleanSquares();

        int[] whitePawnsPerFile = new int[8];
        int[] blackPawnsPerFile = new int[8];

        // Count pawns per file
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String p = squares[r][c];
                if ("P".equals(p)) whitePawnsPerFile[c]++;
                if ("p".equals(p)) blackPawnsPerFile[c]++;
            }
        }

        for (int c = 0; c < 8; c++) {
            // Doubled pawns penalty
            if (whitePawnsPerFile[c] > 1) score -= 20 * (whitePawnsPerFile[c] - 1);
            if (blackPawnsPerFile[c] > 1) score += 20 * (blackPawnsPerFile[c] - 1);

            // Isolated pawns penalty (no friendly pawn on adjacent files)
            if (whitePawnsPerFile[c] > 0) {
                boolean isolated = (c == 0 || whitePawnsPerFile[c - 1] == 0)
                        && (c == 7 || whitePawnsPerFile[c + 1] == 0);
                if (isolated) score -= 15;
            }
            if (blackPawnsPerFile[c] > 0) {
                boolean isolated = (c == 0 || blackPawnsPerFile[c - 1] == 0)
                        && (c == 7 || blackPawnsPerFile[c + 1] == 0);
                if (isolated) score += 15;
            }

            // Passed pawn bonus (no opposing pawn blocks it on this or adjacent files)
            for (int r = 0; r < 8; r++) {
                String p = squares[r][c];
                if ("P".equals(p)) {
                    boolean passed = true;
                    for (int fc = Math.max(0, c - 1); fc <= Math.min(7, c + 1); fc++) {
                        for (int fr = r + 1; fr < 8; fr++) {
                            if ("p".equals(squares[fr][fc])) { passed = false; break; }
                        }
                        if (!passed) break;
                    }
                    if (passed) score += 20 + (r * 5); // Bigger bonus the further advanced
                }
                if ("p".equals(p)) {
                    boolean passed = true;
                    for (int fc = Math.max(0, c - 1); fc <= Math.min(7, c + 1); fc++) {
                        for (int fr = r - 1; fr >= 0; fr--) {
                            if ("P".equals(squares[fr][fc])) { passed = false; break; }
                        }
                        if (!passed) break;
                    }
                    if (passed) score -= 20 + ((7 - r) * 5);
                }
            }
        }
        return score;
    }

    // --- King Safety ---
    private static int evaluateKingSafety(Board board) {
        if (isEndgame(board)) return 0; // King safety less important in endgame
        int score = 0;
        String[][] squares = board.getCleanSquares();

        // Find kings
        int[] whiteKingPos = findPiece(squares, "K");
        int[] blackKingPos = findPiece(squares, "k");

        if (whiteKingPos != null) score += kingSafetyScore(squares, whiteKingPos, true);
        if (blackKingPos != null) score -= kingSafetyScore(squares, blackKingPos, false);

        return score;
    }

    private static int kingSafetyScore(String[][] squares, int[] kingPos, boolean isWhite) {
        int bonus = 0;
        int r = kingPos[0], c = kingPos[1];
        int pawnRow = isWhite ? r + 1 : r - 1; // Row in front of king
        String friendlyPawn = isWhite ? "P" : "p";

        // Pawn shield: pawns directly in front reward safety
        for (int dc = -1; dc <= 1; dc++) {
            int nc = c + dc;
            if (nc >= 0 && nc < 8 && pawnRow >= 0 && pawnRow < 8) {
                if (friendlyPawn.equals(squares[pawnRow][nc])) bonus += 10;
            }
        }

        // Open file in front of king is dangerous
        boolean openFile = true;
        for (int row = 0; row < 8; row++) {
            if (friendlyPawn.equals(squares[row][c])) { openFile = false; break; }
        }
        if (openFile) bonus -= 25;

        return bonus;
    }

    private static int[] findPiece(String[][] squares, String piece) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (piece.equals(squares[r][c])) return new int[]{r, c};
        return null;
    }

    // --- Endgame Detection ---
    private static boolean isEndgame(Board board) {
        int queens = 0, minors = 0;
        String[][] squares = board.getCleanSquares();
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                String p = squares[r][c];
                if (p == null || p.isBlank()) continue;
                if (p.equalsIgnoreCase("Q")) queens++;
                if (p.equalsIgnoreCase("N") || p.equalsIgnoreCase("B") || p.equalsIgnoreCase("R")) minors++;
            }
        return queens == 0 || (queens <= 2 && minors <= 2);
    }

    // =========================================================================
    // MOVE GENERATION
    // =========================================================================

    private static List<String> generateLegalMoves(Board board, String player) {
        List<String> moves = new ArrayList<>();
        boolean isWhiteTurn = player.equals("w");
        String[][] squares = board.getCleanSquares();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = squares[r][c];
                if (piece == null || piece.isBlank()) continue;
                if (Character.isUpperCase(piece.charAt(0)) != isWhiteTurn) continue;

                String fromSquare = "" + "abcdefgh".charAt(c) + (7 - r + 1);
                List<String> raw = ChessGame.generateAllPossibleMoves(board, fromSquare, piece);
                for (String to : raw) moves.add(fromSquare + "-" + to);
            }
        }
        return moves;
    }

    private static List<String> generateCaptureMoves(Board board, String player) {
        List<String> moves = new ArrayList<>();
        boolean isWhiteTurn = player.equals("w");
        String[][] squares = board.getCleanSquares();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String piece = squares[r][c];
                if (piece == null || piece.isBlank()) continue;
                if (Character.isUpperCase(piece.charAt(0)) != isWhiteTurn) continue;

                String fromSquare = "" + "abcdefgh".charAt(c) + (7-r + 1);
                List<String> raw = ChessGame.generateAllPossibleMoves(board, fromSquare, piece);
                for (String to : raw) {
                    String target = board.getSquare(to);
                    boolean isCapture = target != null && !target.equals("o")
                            && !target.equals("x") && !target.isBlank();
                    if (isCapture) moves.add(fromSquare + "-" + to);
                }
            }
        }
        return moves;
    }

    // =========================================================================
    // MOVE ORDERING
    // =========================================================================

    /**
     * Scoring priority:
     *  1. Captures ordered by MVV-LVA (Most Valuable Victim, Least Valuable Attacker)
     *  2. Killer moves (non-captures that caused beta cutoffs)
     *  3. History heuristic score
     */
    private static void orderMoves(List<String> moves, Board board, int ply) {
        moves.sort((a, b) -> {
            int sa = scoreMoveForOrdering(a, board, ply);
            int sb = scoreMoveForOrdering(b, board, ply);
            return sb - sa; // descending
        });
    }

    private static void orderMovesMVVLVA(List<String> captures, Board board) {
        captures.sort((a, b) -> mvvLvaScore(b, board) - mvvLvaScore(a, board));
    }

    private static int scoreMoveForOrdering(String move, Board board, int ply) {
        if (isCapture(board, move)) {
            return 10000 + mvvLvaScore(move, board);
        }
        // Killer move bonus
        if (ply < killerMoves.length) {
            if (move.equals(killerMoves[ply][0])) return 9000;
            if (move.equals(killerMoves[ply][1])) return 8000;
        }
        // History heuristic
        return historyTable.getOrDefault(move, 0);
    }

    private static int mvvLvaScore(String move, Board board) {
        String[] parts = move.split("-");
        if (parts.length < 2) return 0;
        String to = parts[1];
        String from = parts[0];
        String victim   = board.getSquare(to);
        String attacker = board.getSquare(from);
        if (victim == null || victim.equals("o") || victim.equals("x") || victim.isBlank()) return 0;
        int vv = MVV_LVA_VALUES.getOrDefault(victim,   0);
        int av = MVV_LVA_VALUES.getOrDefault(attacker, 0);
        return (vv * 10) - av; // Higher victim value, lower attacker value = better
    }

    private static boolean isCapture(Board board, String move) {
        String[] parts = move.split("-");
        if (parts.length < 2) return false;
        String target = board.getSquare(parts[1]);
        return target != null && !target.equals("o") && !target.equals("x") && !target.isBlank();
    }

    // =========================================================================
    // KILLER MOVE HELPERS
    // =========================================================================

    private static void storeKiller(int ply, String move) {
        if (ply >= killerMoves.length) return;
        if (!move.equals(killerMoves[ply][0])) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
    }

    private static void clearKillers() {
        for (String[] km : killerMoves) { km[0] = null; km[1] = null; }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private static Board simulateMove(Board original, String move) {
        Board newBoard = new Board(original.getFENStringPosition());
        return ChessGame.playGame(newBoard, move, null);
    }

    private static String getTurnFromFEN(Board b) {
        try { return b.getFENStringPosition().split(" ")[1]; }
        catch (Exception e) { return "w"; }
    }
}