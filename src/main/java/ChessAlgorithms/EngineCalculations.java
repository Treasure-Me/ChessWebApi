package ChessAlgorithms;

import logic.Board;
import logic.ChessGame;

import java.util.*;

/**
 * EngineCalculations — Highly optimised chess engine.
 *
 * Algorithms & techniques:
 *  1.  Iterative Deepening
 *  2.  Alpha-Beta Minimax
 *  3.  Quiescence Search
 *  4.  Move Ordering — TT best move, MVV-LVA, killer moves, history heuristic
 *  5.  Piece-Square Tables (flattened int[] for cache locality)
 *  6.  Transposition Table (64-bit Zobrist)
 *  7.  Killer Move Heuristic
 *  8.  History Heuristic (int[64*64] — no HashMap/String allocation)
 *  9.  King Safety Evaluation (bitboard-only)
 * 10.  Pawn Structure Evaluation
 * 11.  Null Move Pruning (R=2)
 * 12.  Late Move Reduction (LMR)
 * 13.  Aspiration Windows at root
 *
 * Promotion fixes:
 *  • simulateMove parses the "=Q" / "=N" suffix from the move string and passes
 *    it to ChessGame.playGame so the correct piece appears on the board.
 *  • The engine evaluates all four promotion variants because listOfLegalMoves
 *    now emits "e7-e8=Q", "e7-e8=R", "e7-e8=B", "e7-e8=N" as separate moves.
 *  • simulateMove returns null when playGame signals an illegal/invalid move,
 *    and all callers skip null boards rather than recursing into a stale position.
 */
public class EngineCalculations {

    // =========================================================================
    // Piece index constants
    // =========================================================================

    private static final int P=0, N=1, B=2, R=3, Q=4, K=5;
    private static final int p=6, n=7, b=8, r=9, q=10, k=11;

    // =========================================================================
    // Piece value tables (int[] indexed by char ASCII)
    // =========================================================================

    private static final int[] PIECE_VALUE = new int[128];
    static {
        PIECE_VALUE['P'] =  100; PIECE_VALUE['p'] = -100;
        PIECE_VALUE['N'] =  320; PIECE_VALUE['n'] = -320;
        PIECE_VALUE['B'] =  330; PIECE_VALUE['b'] = -330;
        PIECE_VALUE['R'] =  500; PIECE_VALUE['r'] = -500;
        PIECE_VALUE['Q'] =  900; PIECE_VALUE['q'] = -900;
        PIECE_VALUE['K'] =20000; PIECE_VALUE['k'] =-20000;
    }

    private static final int[] MVV_LVA_VAL = new int[128];
    static {
        MVV_LVA_VAL['P']=MVV_LVA_VAL['p']=1;
        MVV_LVA_VAL['N']=MVV_LVA_VAL['n']=2;
        MVV_LVA_VAL['B']=MVV_LVA_VAL['b']=3;
        MVV_LVA_VAL['R']=MVV_LVA_VAL['r']=4;
        MVV_LVA_VAL['Q']=MVV_LVA_VAL['q']=5;
        MVV_LVA_VAL['K']=MVV_LVA_VAL['k']=6;
    }

    // =========================================================================
    // Search constants
    // =========================================================================

    private static final int CHECKMATE_SCORE  = 100_000;
    private static final int STALEMATE_SCORE  = 0;
    private static final int MAX_DEPTH        = 20;
    private static final int Q_DEPTH_LIMIT    = 6;
    private static final int NULL_MOVE_R      = 2;
    private static final int LMR_MIN_DEPTH    = 3;
    private static final int LMR_FULL_MOVES   = 3;
    private static final int ASPIRATION_DELTA = 50;

    // =========================================================================
    // Piece-Square Tables — flattened int[64] for cache efficiency
    // White: pstRow = 7 - bitRank   Black: pstRow = bitRank (mirrored)
    // =========================================================================

    private static int[] flat(int[][] t) {
        int[] f = new int[64];
        for (int r = 0; r < 8; r++) System.arraycopy(t[r], 0, f, r * 8, 8);
        return f;
    }

    private static final int[] PST_PAWN = flat(new int[][]{
            {  0,  0,  0,  0,  0,  0,  0,  0},
            { 50, 50, 50, 50, 50, 50, 50, 50},
            { 10, 10, 20, 30, 30, 20, 10, 10},
            {  5,  5, 10, 25, 25, 10,  5,  5},
            {  0,  0,  0, 20, 20,  0,  0,  0},
            {  5, -5,-10,  0,  0,-10, -5,  5},
            {  5, 10, 10,-20,-20, 10, 10,  5},
            {  0,  0,  0,  0,  0,  0,  0,  0}
    });
    private static final int[] PST_KNIGHT = flat(new int[][]{
            {-50,-40,-30,-30,-30,-30,-40,-50},
            {-40,-20,  0,  0,  0,  0,-20,-40},
            {-30,  0, 10, 15, 15, 10,  0,-30},
            {-30,  5, 15, 20, 20, 15,  5,-30},
            {-30,  0, 15, 20, 20, 15,  0,-30},
            {-30,  5, 10, 15, 15, 10,  5,-30},
            {-40,-20,  0,  5,  5,  0,-20,-40},
            {-50,-40,-30,-30,-30,-30,-40,-50}
    });
    private static final int[] PST_BISHOP = flat(new int[][]{
            {-20,-10,-10,-10,-10,-10,-10,-20},
            {-10,  0,  0,  0,  0,  0,  0,-10},
            {-10,  0,  5, 10, 10,  5,  0,-10},
            {-10,  5,  5, 10, 10,  5,  5,-10},
            {-10,  0, 10, 10, 10, 10,  0,-10},
            {-10, 10, 10, 10, 10, 10, 10,-10},
            {-10,  5,  0,  0,  0,  0,  5,-10},
            {-20,-10,-10,-10,-10,-10,-10,-20}
    });
    private static final int[] PST_ROOK = flat(new int[][]{
            {  0,  0,  0,  0,  0,  0,  0,  0},
            {  5, 10, 10, 10, 10, 10, 10,  5},
            { -5,  0,  0,  0,  0,  0,  0, -5},
            { -5,  0,  0,  0,  0,  0,  0, -5},
            { -5,  0,  0,  0,  0,  0,  0, -5},
            { -5,  0,  0,  0,  0,  0,  0, -5},
            { -5,  0,  0,  0,  0,  0,  0, -5},
            {  0,  0,  0,  5,  5,  0,  0,  0}
    });
    private static final int[] PST_QUEEN = flat(new int[][]{
            {-20,-10,-10, -5, -5,-10,-10,-20},
            {-10,  0,  0,  0,  0,  0,  0,-10},
            {-10,  0,  5,  5,  5,  5,  0,-10},
            { -5,  0,  5,  5,  5,  5,  0, -5},
            {  0,  0,  5,  5,  5,  5,  0, -5},
            {-10,  5,  5,  5,  5,  5,  0,-10},
            {-10,  0,  5,  0,  0,  0,  0,-10},
            {-20,-10,-10, -5, -5,-10,-10,-20}
    });
    private static final int[] PST_KING_MG = flat(new int[][]{
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-30,-40,-40,-50,-50,-40,-40,-30},
            {-20,-30,-30,-40,-40,-30,-30,-20},
            {-10,-20,-20,-20,-20,-20,-20,-10},
            { 20, 20,  0,  0,  0,  0, 20, 20},
            { 20, 30, 10,  0,  0, 10, 30, 20}
    });
    private static final int[] PST_KING_EG = flat(new int[][]{
            {-50,-40,-30,-20,-20,-30,-40,-50},
            {-30,-20,-10,  0,  0,-10,-20,-30},
            {-30,-10, 20, 30, 30, 20,-10,-30},
            {-30,-10, 30, 40, 40, 30,-10,-30},
            {-30,-10, 30, 40, 40, 30,-10,-30},
            {-30,-10, 20, 30, 30, 20,-10,-30},
            {-30,-30,  0,  0,  0,  0,-30,-30},
            {-50,-30,-30,-30,-30,-30,-30,-50}
    });

    private static final int[][] PST_BY_INDEX = {
            PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, PST_QUEEN, PST_KING_MG,
            PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, PST_QUEEN, PST_KING_MG
    };

    // =========================================================================
    // Transposition table (64-bit Zobrist)
    // =========================================================================

    private static final int    TT_SIZE  = 1 << 22;
    private static final long[] ttKey    = new long[TT_SIZE];
    private static final int[]  ttDepth  = new int[TT_SIZE];
    private static final int[]  ttScore  = new int[TT_SIZE];
    private static final byte[] ttFlag   = new byte[TT_SIZE];
    private static final String[] ttBestMove = new String[TT_SIZE];

    private static final byte TT_EXACT = 0, TT_LOWER = 1, TT_UPPER = 2;

    // =========================================================================
    // Zobrist hashing
    // =========================================================================

    private static final long[][] ZOBRIST_PIECE = new long[12][64];
    private static final long     ZOBRIST_BLACK_MOVE;

    static {
        Random rng = new Random(0xDEAD_BEEF_CAFE_BABAL);
        for (int i = 0; i < 12; i++)
            for (int s = 0; s < 64; s++)
                ZOBRIST_PIECE[i][s] = rng.nextLong();
        ZOBRIST_BLACK_MOVE = rng.nextLong();
    }

    private static long zobristHash(Board board) {
        long h = 0L;
        for (int i = 0; i < 12; i++) {
            long bb = board.getPieceBitboard(i);
            while (bb != 0) {
                h ^= ZOBRIST_PIECE[i][Long.numberOfTrailingZeros(bb)];
                bb &= bb - 1;
            }
        }
        if (board.getSideToMove().equals("b")) h ^= ZOBRIST_BLACK_MOVE;
        return h;
    }

    // =========================================================================
    // Killer & history heuristics
    // =========================================================================

    private static final String[][] killerMoves  = new String[MAX_DEPTH + Q_DEPTH_LIMIT + 2][2];
    private static final int[]      historyTable = new int[64 * 64];

    // =========================================================================
    // Search state
    // =========================================================================

    private static long    searchStartTime;
    private static long    searchTimeLimit;
    private static boolean timeUp;

    // =========================================================================
    // Iterative deepening
    // =========================================================================

    public static String iterativeDeepening(Board board, long maxTimeMillis) {
        searchStartTime = System.currentTimeMillis();
        searchTimeLimit = maxTimeMillis;
        timeUp          = false;

        clearKillers();
        Arrays.fill(historyTable, 0);

        String bestMove  = "";
        int    prevScore = 0;

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (timeUp || elapsed() > searchTimeLimit) break;

            int alpha, beta;
            if (depth >= 4) {
                alpha = prevScore - ASPIRATION_DELTA;
                beta  = prevScore + ASPIRATION_DELTA;
            } else {
                alpha = Integer.MIN_VALUE + 1;
                beta  = Integer.MAX_VALUE - 1;
            }

            String move;
            int    score;
            while (true) {
                move  = findBestMove(board, depth, alpha, beta);
                score = lastRootScore;
                if (timeUp) break;
                if      (score <= alpha) alpha = Integer.MIN_VALUE + 1;
                else if (score >= beta)  beta  = Integer.MAX_VALUE - 1;
                else                     break;
            }

            if (!move.isEmpty() && !timeUp) {
                bestMove  = move;
                prevScore = score;
            }
            System.out.printf("info depth %d score cp %d time %d%n", depth, prevScore, elapsed());
        }
        return bestMove;
    }

    private static int lastRootScore = 0;

    // =========================================================================
    // Root search
    // =========================================================================

    public static String findBestMove(Board board, int depth, int alpha, int beta) {
        String  turn   = board.getSideToMove();
        boolean maxing = turn.equals("w");
        List<String> moves = ChessGame.listOfLegalMoves(board, turn);
        if (moves.isEmpty()) { lastRootScore = 0; return ""; }

        orderMoves(moves, board, 0);

        String bestMove  = moves.get(0);
        int    bestScore = maxing ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;

        for (String move : moves) {
            if (timeUp) break;
            Board child = simulateMove(board, move);
            if (child == null) continue;   // playGame refused the move — skip

            int eval = minimax(child, depth - 1, alpha, beta, !maxing, 1, false);

            if (maxing ? (eval > bestScore) : (eval < bestScore)) {
                bestScore = eval;
                bestMove  = move;
            }
            if (maxing) alpha = Math.max(alpha, eval);
            else        beta  = Math.min(beta,  eval);
            if (beta <= alpha) break;
        }
        lastRootScore = bestScore;
        return bestMove;
    }

    // =========================================================================
    // Alpha-Beta minimax with Null Move Pruning and LMR
    // =========================================================================

    public static int minimax(Board board, int depth, int alpha, int beta,
                              boolean maxing, int ply, boolean nullMoveAllowed) {

        if (elapsed() > searchTimeLimit) { timeUp = true; return 0; }

        // ── Transposition table ───────────────────────────────────────────────
        long hash  = zobristHash(board);
        int  ttIdx = (int)(hash & (TT_SIZE - 1));
        if (ttKey[ttIdx] == hash && ttDepth[ttIdx] >= depth) {
            int  cached = ttScore[ttIdx];
            byte flag   = ttFlag[ttIdx];
            if (flag == TT_EXACT) return cached;
            if (flag == TT_LOWER) alpha = Math.max(alpha, cached);
            if (flag == TT_UPPER) beta  = Math.min(beta,  cached);
            if (alpha >= beta) return cached;
        }

        // ── Quiescence at leaf ────────────────────────────────────────────────
        if (depth == 0) return quiescence(board, alpha, beta, maxing, Q_DEPTH_LIMIT);

        // ── Terminal position ─────────────────────────────────────────────────
        String gs = board.getGameState();
        if (gs != null && (gs.contains("Game Over") || gs.contains("Checkmate")))
            return evaluate(board);

        String turn = maxing ? "w" : "b";
        List<String> legalMoves = ChessGame.listOfLegalMoves(board, turn);
        if (legalMoves.isEmpty()) return evaluate(board);

        // ── Null Move Pruning ─────────────────────────────────────────────────
        if (nullMoveAllowed && depth >= NULL_MOVE_R + 1 && !isInCheck(board, maxing)) {
            Board nullBoard = new Board(board.getFENStringPosition());
            nullBoard.toggleSideToMove();
            nullBoard.setFENFields(nullBoard.getCastlingRights(), "-");
            nullBoard.commitFEN();

            int nullScore = minimax(nullBoard, depth - 1 - NULL_MOVE_R,
                    alpha, beta, !maxing, ply + 1, false);
            if (maxing  && nullScore >= beta)  return beta;
            if (!maxing && nullScore <= alpha) return alpha;
        }

        // ── Move ordering ─────────────────────────────────────────────────────
        orderMoves(legalMoves, board, ply);

        int    originalAlpha = alpha;
        int    bestScore     = maxing ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;
        String bestMoveLocal = null;
        int    moveCount     = 0;

        for (String move : legalMoves) {
            if (timeUp) return 0;

            Board child = simulateMove(board, move);
            if (child == null) continue;   // playGame refused — skip silently
            moveCount++;

            int eval;

            // ── Late Move Reduction ───────────────────────────────────────────
            boolean lmrCandidate = depth >= LMR_MIN_DEPTH
                    && moveCount > LMR_FULL_MOVES
                    && !isCaptureMoveStr(board, move)
                    && (ply >= killerMoves.length || !move.equals(killerMoves[ply][0]));

            if (lmrCandidate) {
                int reduction = (moveCount > 6) ? 2 : 1;
                eval = minimax(child, depth - 1 - reduction, alpha, beta, !maxing, ply + 1, true);
                if (!timeUp && ((maxing && eval > alpha) || (!maxing && eval < beta)))
                    eval = minimax(child, depth - 1, alpha, beta, !maxing, ply + 1, true);
            } else {
                eval = minimax(child, depth - 1, alpha, beta, !maxing, ply + 1, true);
            }

            if (maxing) {
                if (eval > bestScore) { bestScore = eval; bestMoveLocal = move; }
                alpha = Math.max(alpha, eval);
            } else {
                if (eval < bestScore) { bestScore = eval; bestMoveLocal = move; }
                beta = Math.min(beta, eval);
            }

            if (beta <= alpha) {
                if (bestMoveLocal != null && !isCaptureMoveStr(board, bestMoveLocal)) {
                    storeKiller(ply, bestMoveLocal);
                    int hIdx = historyIndex(bestMoveLocal);
                    if (hIdx >= 0) historyTable[hIdx] += depth * depth;
                }
                break;
            }
        }

        // ── TT store ──────────────────────────────────────────────────────────
        byte flag;
        if      (bestScore <= originalAlpha) flag = TT_UPPER;
        else if (bestScore >= beta)          flag = TT_LOWER;
        else                                 flag = TT_EXACT;

        ttKey[ttIdx]      = hash;
        ttDepth[ttIdx]    = depth;
        ttScore[ttIdx]    = bestScore;
        ttFlag[ttIdx]     = flag;
        ttBestMove[ttIdx] = bestMoveLocal;

        return bestScore;
    }

    // =========================================================================
    // Quiescence search
    // =========================================================================

    private static int quiescence(Board board, int alpha, int beta,
                                  boolean maxing, int depthLeft) {
        int standPat = evaluate(board);
        if (maxing) {
            if (standPat >= beta)  return beta;
            if (standPat > alpha)  alpha = standPat;
        } else {
            if (standPat <= alpha) return alpha;
            if (standPat < beta)   beta  = standPat;
        }
        if (depthLeft == 0) return standPat;

        List<String> all  = ChessGame.listOfLegalMoves(board, maxing ? "w" : "b");
        List<String> caps = new ArrayList<>(all.size());
        for (String mv : all)
            if (isCaptureMoveStr(board, mv)) caps.add(mv);
        orderMovesMVVLVA(caps, board);

        for (String move : caps) {
            Board child = simulateMove(board, move);
            if (child == null) continue;
            int eval = quiescence(child, alpha, beta, !maxing, depthLeft - 1);
            if (maxing) {
                if (eval >= beta)  return beta;
                if (eval > alpha)  alpha = eval;
            } else {
                if (eval <= alpha) return alpha;
                if (eval < beta)   beta  = eval;
            }
        }
        return maxing ? alpha : beta;
    }

    // =========================================================================
    // Evaluation
    // =========================================================================

    public static int evaluate(Board board) {
        String gs = board.getGameState();
        if (gs != null) {
            if (gs.contains("Checkmate! Black wins!")) return -CHECKMATE_SCORE;
            if (gs.contains("Checkmate! White wins!")) return  CHECKMATE_SCORE;
            if (gs.contains("Stalemate"))              return  STALEMATE_SCORE;
        }
        return evaluateMaterial(board)
                + evaluatePST(board)
                + evaluatePawnStructure(board)
                + evaluateKingSafety(board);
    }

    private static int evaluateMaterial(Board board) {
        int s = 0;
        s += Long.bitCount(board.getPieceBitboard(P)) *  100;
        s += Long.bitCount(board.getPieceBitboard(N)) *  320;
        s += Long.bitCount(board.getPieceBitboard(B)) *  330;
        s += Long.bitCount(board.getPieceBitboard(R)) *  500;
        s += Long.bitCount(board.getPieceBitboard(Q)) *  900;
        s += Long.bitCount(board.getPieceBitboard(K)) *20000;
        s -= Long.bitCount(board.getPieceBitboard(p)) *  100;
        s -= Long.bitCount(board.getPieceBitboard(n)) *  320;
        s -= Long.bitCount(board.getPieceBitboard(b)) *  330;
        s -= Long.bitCount(board.getPieceBitboard(r)) *  500;
        s -= Long.bitCount(board.getPieceBitboard(q)) *  900;
        s -= Long.bitCount(board.getPieceBitboard(k)) *20000;
        return s;
    }

    private static int evaluatePST(Board board) {
        boolean eg    = isEndgame(board);
        int     score = 0;

        for (int i = 0; i < 6; i++) {
            long bb  = board.getPieceBitboard(i);
            int[] pst = (i == K && eg) ? PST_KING_EG : PST_BY_INDEX[i];
            while (bb != 0) {
                int sq    = Long.numberOfTrailingZeros(bb);
                score += pst[(7 - (sq >> 3)) * 8 + (sq & 7)];
                bb &= bb - 1;
            }
        }
        for (int i = 6; i < 12; i++) {
            long bb  = board.getPieceBitboard(i);
            int[] pst = (i == k && eg) ? PST_KING_EG : PST_BY_INDEX[i];
            while (bb != 0) {
                int sq    = Long.numberOfTrailingZeros(bb);
                score -= pst[(sq >> 3) * 8 + (sq & 7)];
                bb &= bb - 1;
            }
        }
        return score;
    }

    private static int evaluatePawnStructure(Board board) {
        int  score = 0;
        long wP    = board.getPieceBitboard(P);
        long bP    = board.getPieceBitboard(p);

        long fileMask = 0x0101_0101_0101_0101L;
        for (int file = 0; file < 8; file++, fileMask <<= 1) {
            int wCount = Long.bitCount(wP & fileMask);
            int bCount = Long.bitCount(bP & fileMask);
            if (wCount > 1) score -= 20 * (wCount - 1);
            if (bCount > 1) score += 20 * (bCount - 1);
            long left  = (file > 0) ? (fileMask >>> 1) : 0L;
            long right = (file < 7) ? (fileMask <<  1) : 0L;
            long adj   = left | right;
            if (wCount > 0 && Long.bitCount(wP & adj) == 0) score -= 15;
            if (bCount > 0 && Long.bitCount(bP & adj) == 0) score += 15;
        }
        return score;
    }

    private static int evaluateKingSafety(Board board) {
        if (isEndgame(board)) return 0;
        return kingSafetyBB(board, true) - kingSafetyBB(board, false);
    }

    private static int kingSafetyBB(Board board, boolean isWhite) {
        long kingBB = board.getPieceBitboard(isWhite ? K : k);
        if (kingBB == 0) return 0;
        int kSq   = Long.numberOfTrailingZeros(kingBB);
        int kFile = kSq & 7, kBitRank = kSq >> 3;
        long fp   = board.getPieceBitboard(isWhite ? P : p);
        int bonus = 0;

        int shieldRank = isWhite ? kBitRank + 1 : kBitRank - 1;
        if (shieldRank >= 0 && shieldRank <= 7) {
            long mask = 0L;
            for (int df = -1; df <= 1; df++) {
                int f = kFile + df;
                if (f >= 0 && f <= 7) mask |= 1L << (shieldRank * 8 + f);
            }
            bonus += Long.bitCount(fp & mask) * 10;
        }
        if ((fp & (0x0101_0101_0101_0101L << kFile)) == 0) bonus -= 25;
        return bonus;
    }

    private static boolean isEndgame(Board board) {
        int queens = Long.bitCount(board.getPieceBitboard(Q))
                + Long.bitCount(board.getPieceBitboard(q));
        if (queens == 0) return true;
        int minors = Long.bitCount(board.getPieceBitboard(N))
                + Long.bitCount(board.getPieceBitboard(B))
                + Long.bitCount(board.getPieceBitboard(R))
                + Long.bitCount(board.getPieceBitboard(n))
                + Long.bitCount(board.getPieceBitboard(b))
                + Long.bitCount(board.getPieceBitboard(r));
        return queens <= 2 && minors <= 4;
    }

    // =========================================================================
    // Move ordering
    // =========================================================================

    private static void orderMoves(List<String> moves, Board board, int ply) {
        int n = moves.size();
        int[] scores = new int[n];
        for (int i = 0; i < n; i++) scores[i] = scoreMoveForOrdering(moves.get(i), board, ply);
        for (int i = 1; i < n; i++) {
            String mv = moves.get(i); int sc = scores[i]; int j = i - 1;
            while (j >= 0 && scores[j] < sc) {
                moves.set(j + 1, moves.get(j)); scores[j + 1] = scores[j]; j--;
            }
            moves.set(j + 1, mv); scores[j + 1] = sc;
        }
    }

    private static void orderMovesMVVLVA(List<String> caps, Board board) {
        int n = caps.size();
        int[] scores = new int[n];
        for (int i = 0; i < n; i++) scores[i] = mvvLvaScore(caps.get(i), board);
        for (int i = 1; i < n; i++) {
            String mv = caps.get(i); int sc = scores[i]; int j = i - 1;
            while (j >= 0 && scores[j] < sc) {
                caps.set(j + 1, caps.get(j)); scores[j + 1] = scores[j]; j--;
            }
            caps.set(j + 1, mv); scores[j + 1] = sc;
        }
    }

    private static int scoreMoveForOrdering(String move, Board board, int ply) {
        long hash  = zobristHash(board);
        int  ttIdx = (int)(hash & (TT_SIZE - 1));
        if (ttKey[ttIdx] == hash && move.equals(ttBestMove[ttIdx])) return 12000;
        if (isCaptureMoveStr(board, move)) return 10000 + mvvLvaScore(move, board);
        if (ply < killerMoves.length) {
            if (move.equals(killerMoves[ply][0])) return 9000;
            if (move.equals(killerMoves[ply][1])) return 8000;
        }
        int hIdx = historyIndex(move);
        return (hIdx >= 0) ? historyTable[hIdx] : 0;
    }

    // =========================================================================
    // Move-string helpers
    // =========================================================================

    /** Returns the "from" part of "e2-e4" or "e7-e8=Q". */
    private static String moveFrom(String move) {
        int d = move.indexOf('-');
        return d >= 0 ? move.substring(0, d) : move;
    }

    /** Returns the base "to" square of "e2-e4" or "e7-e8=Q" (strips suffix). */
    private static String moveTo(String move) {
        int d = move.indexOf('-');
        if (d < 0) return move;
        String after = move.substring(d + 1);
        int eq = after.indexOf('=');
        return eq >= 0 ? after.substring(0, eq) : after;
    }

    /** Returns the promotion piece string ("Q","N", etc.) or null. */
    private static String movePromo(String move) {
        int eq = move.indexOf('=');
        return (eq >= 0 && eq + 1 < move.length()) ? move.substring(eq + 1) : null;
    }

    private static int mvvLvaScore(String move, Board board) {
        String victim   = board.getSquare(moveTo(move));
        String attacker = board.getSquare(moveFrom(move));
        if (victim.isEmpty()) return 0;
        char vc = victim.charAt(0);
        char ac = attacker.isEmpty() ? 'P' : attacker.charAt(0);
        return MVV_LVA_VAL[vc] * 10 - MVV_LVA_VAL[ac];
    }

    private static boolean isCaptureMoveStr(Board board, String move) {
        return !board.getSquare(moveTo(move)).isEmpty();
    }

    private static int historyIndex(String move) {
        int d = move.indexOf('-');
        if (d < 0) return -1;
        String fromSq = move.substring(0, d);
        String toFull = move.substring(d + 1);
        int eq = toFull.indexOf('=');
        String toSq = (eq >= 0) ? toFull.substring(0, eq) : toFull;
        if (fromSq.length() != 2 || toSq.length() != 2) return -1;
        return Board.sqIndex(fromSq) * 64 + Board.sqIndex(toSq);
    }

    // =========================================================================
    // Null-move check helper
    // =========================================================================

    private static boolean isInCheck(Board board, boolean isWhite) {
        long kingBB = board.getPieceBitboard(isWhite ? K : k);
        if (kingBB == 0) return false;
        String kPos = ChessGame.SQUARE_NAMES[Long.numberOfTrailingZeros(kingBB)];
        return ChessGame.inCheck(board, kPos, isWhite);
    }

    // =========================================================================
    // Killer move helpers
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
    // simulateMove — parses promotion suffix, guards against illegal moves
    // =========================================================================

    /**
     * Applies 'move' to a copy of 'original' and returns the resulting board.
     * Returns null if playGame signals the move was invalid or illegal,
     * so callers can skip without corrupting the search.
     *
     * Handles the "e7-e8=Q" suffix format produced by listOfLegalMoves.
     */
    private static Board simulateMove(Board original, String move) {
        Board nb = new Board(original);
        String promo = movePromo(move);
        // Rebuild the move string without the suffix — playGame accepts "e7-e8"
        // with a separate promotionPiece parameter.
        String baseMove = moveFrom(move) + "-" + moveTo(move);
        Board result = ChessGame.playGame(nb, baseMove, promo);
        String gs = result.getGameState();
        if (gs != null && (gs.startsWith("Invalid") || gs.startsWith("Illegal")
                || gs.startsWith("Not your") || gs.startsWith("Cannot"))) {
            return null;
        }
        return result;
    }

    private static long elapsed() {
        return System.currentTimeMillis() - searchStartTime;
    }

    public static void clearTT() {
        Arrays.fill(ttKey, 0L);
        Arrays.fill(ttDepth, 0);
        Arrays.fill(ttScore, 0);
        Arrays.fill(ttFlag, (byte)0);
        Arrays.fill(ttBestMove, null);
        clearKillers();
        Arrays.fill(historyTable, 0);
    }

    public static void stopSearch() {
        timeUp = true;
    }
}