package ChessAlgorithms;

import chess.logic.Board;
import chess.logic.Moves;

import java.util.*;

public class EngineCalculations {
    // Piece values for evaluation (centipawns)
    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;

    private static final int CHECKMATE_SCORE = 100000;
    private static final int STALEMATE_SCORE = 0;

    /**
     * Evaluates the current board position from white's perspective
     * Positive = white is better, Negative = black is better
     */
    public double evaluate(Board board) {
        // Check for game over conditions first
        if (isCheckmate(board, "w")) return -CHECKMATE_SCORE; // Black wins
        if (isCheckmate(board, "b")) return CHECKMATE_SCORE;  // White wins
        if (isStalemate(board)) return STALEMATE_SCORE;

        double score = 0;

        // Material evaluation
        score += evaluateMaterial(board);

        // Piece activity/mobility
        score += evaluateMobility(board);

        // Pawn structure
        score += evaluatePawnStructure(board);

        // King safety
        score += evaluateKingSafety(board);

        return score;
    }

    /**
     * Basic minimax algorithm with alpha-beta pruning
     * @param board Current board state
     * @param depth How many moves to look ahead
     * @param alpha Best value for maximizing player (white)
     * @param beta Best value for minimizing player (black)
     * @param maximizingPlayer True if it's white's turn to move
     * @return The best evaluation score found
     */
    public double minimax(Board board, int depth, double alpha, double beta, boolean maximizingPlayer) {
        // Base case: reached max depth or game over
        if (depth == 0 || isGameOver(board)) {
            return evaluate(board);
        }

        String currentTurn = board.getFENStringPosition().split(" ")[1];
        List<String> legalMoves = generateLegalMoves(board, currentTurn);

        if (maximizingPlayer) {
            double maxEval = -Double.MAX_VALUE;
            for (String move : legalMoves) {
                // Make move
                Board newBoard = makeMove(board, move);

                // Recursive call
                double eval = minimax(newBoard, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);

                // Alpha-beta pruning
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    break; // Beta cutoff
                }
            }
            return maxEval;
        } else {
            double minEval = Double.MAX_VALUE;
            for (String move : legalMoves) {
                // Make move
                Board newBoard = makeMove(board, move);

                // Recursive call
                double eval = minimax(newBoard, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);

                // Alpha-beta pruning
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    break; // Alpha cutoff
                }
            }
            return minEval;
        }
    }

    /**
     * Finds the best move using minimax with alpha-beta pruning
     * @param board Current board state
     * @param depth Search depth
     * @return The best move in format "e2-e4"
     */
    public String findBestMove(Board board, int depth) {
        String currentTurn = board.getFENStringPosition().split(" ")[1];
        boolean maximizingPlayer = currentTurn.equals("w");

        List<String> legalMoves = generateLegalMoves(board, currentTurn);
        String bestMove = legalMoves.get(0); // Default to first move
        double bestEval = maximizingPlayer ? -Double.MAX_VALUE : Double.MAX_VALUE;

        for (String move : legalMoves) {
            // Make move
            Board newBoard = makeMove(board, move);

            // Evaluate position
            double eval = minimax(newBoard, depth - 1,
                    -Double.MAX_VALUE, Double.MAX_VALUE, !maximizingPlayer);

            // Update best move
            if ((maximizingPlayer && eval > bestEval) ||
                    (!maximizingPlayer && eval < bestEval)) {
                bestEval = eval;
                bestMove = move;
            }
        }

        return bestMove;
    }

    /**
     * Iterative deepening - searches progressively deeper until time runs out
     * Better for time management in actual games
     */
    public String iterativeDeepening(Board board, long maxTimeMillis) {
        String bestMove = "";
        long startTime = System.currentTimeMillis();

        for (int depth = 1; depth <= 10; depth++) {
            if (System.currentTimeMillis() - startTime > maxTimeMillis) {
                break; // Time's up
            }

            try {
                String move = findBestMove(board, depth);
                if (!move.isEmpty()) {
                    bestMove = move;
                }
                System.out.println("Depth " + depth + " completed. Best move: " + bestMove);
            } catch (Exception e) {
                System.out.println("Error at depth " + depth + ": " + e.getMessage());
                break;
            }
        }

        return bestMove;
    }

    // Helper methods for evaluation

    private double evaluateMaterial(Board board) {
        double material = 0;

        // You'll need to implement getPiecePositions in your Board class
        // This is a simplified version - you'll need to adapt to your Board class

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // This depends on how your Board class stores pieces
                // You'll need to adapt this to your actual implementation
                String piece = ""; // board.getPieceAt(row, col);

                switch (piece) {
                    case "P": material += PAWN_VALUE; break;
                    case "N": material += KNIGHT_VALUE; break;
                    case "B": material += BISHOP_VALUE; break;
                    case "R": material += ROOK_VALUE; break;
                    case "Q": material += QUEEN_VALUE; break;
                    case "K": material += KING_VALUE; break;
                    case "p": material -= PAWN_VALUE; break;
                    case "n": material -= KNIGHT_VALUE; break;
                    case "b": material -= BISHOP_VALUE; break;
                    case "r": material -= ROOK_VALUE; break;
                    case "q": material -= QUEEN_VALUE; break;
                    case "k": material -= KING_VALUE; break;
                }
            }
        }

        return material;
    }

    private double evaluateMobility(Board board) {
        // Count how many legal moves each side has
        // More mobility = better position
        List<String> whiteMoves = generateLegalMoves(board, "w");
        List<String> blackMoves = generateLegalMoves(board, "b");

        return (whiteMoves.size() - blackMoves.size()) * 0.1; // Small weight
    }

    private double evaluatePawnStructure(Board board) {
        // Simplified pawn structure evaluation
        // You can expand this with doubled pawns, isolated pawns, passed pawns, etc.
        double pawnScore = 0;

        // This would require analyzing pawn positions
        // For now, return 0 as a placeholder

        return pawnScore;
    }

    private double evaluateKingSafety(Board board) {
        // Simplified king safety
        // Penalize exposed kings, reward castled kings
        double safetyScore = 0;

        // This would require analyzing king position and pawn shield
        // For now, return 0 as a placeholder

        return safetyScore;
    }

    // Game state detection methods

    private boolean isGameOver(Board board) {
        return isCheckmate(board, "w") || isCheckmate(board, "b") || isStalemate(board);
    }

    private boolean isCheckmate(Board board, String player) {
        // Check if the player has any legal moves and is in check
        List<String> legalMoves = generateLegalMoves(board, player);
        return legalMoves.isEmpty() && isInCheck(board, player);
    }

    private boolean isStalemate(Board board) {
        // Check if current player has no legal moves but is not in check
        String currentTurn = board.getFENStringPosition().split(" ")[1];
        List<String> legalMoves = generateLegalMoves(board, currentTurn);
        return legalMoves.isEmpty() && !isInCheck(board, currentTurn);
    }

    private boolean isInCheck(Board board, String player) {
        // You already have this logic in your ChessGame class
        // You might want to move it here or call it from there
        String kingPosition = findKingPosition(board, player);
        return isSquareAttacked(board, kingPosition, player.equals("w") ? "b" : "w");
    }

    // Core engine methods you'll need to implement

    private List<String> generateLegalMoves(Board board, String player) {
        List<String> legalMoves = new ArrayList<>();

        // You'll need to implement this based on your existing move generation
        // This should return all legal moves for the given player
        // Format: ["e2-e4", "g1-f3", ...]

        // Pseudocode:
        // 1. Get all pieces for the player
        // 2. For each piece, generate possible moves using your Moves class
        // 3. Filter out moves that leave king in check

        return legalMoves;
    }

    private Board makeMove(Board originalBoard, String move) {
        // Create a copy of the board and apply the move
        // You'll need to implement board copying in your Board class

        Board newBoard = originalBoard; // You'll need to implement copy() in Board
        String[] squares = move.split("-");
        String fromSquare = squares[0];
        String toSquare = squares[1];

        String piece = newBoard.getSquare(fromSquare);
        newBoard.setSquare(fromSquare, " "); // Clear original square
        newBoard.setSquare(toSquare, piece); // Move piece to new square

        return newBoard;
    }

    private String findKingPosition(Board board, String player) {
        String king = player.equals("w") ? "K" : "k";

        // You'll need to implement getPiecePositions in your Board class
        ArrayList<String> positions = board.getPiecePositions(king);
        return positions.isEmpty() ? "" : positions.get(0);
    }

    private boolean isSquareAttacked(Board board, String square, String byPlayer) {
        // Check if any of byPlayer's pieces can attack the given square
        // Similar to your inCheck method but for any square

        // You can adapt your existing inCheck logic here
        return false; // Placeholder
    }

    /**
     * Quick evaluation for faster search - uses only material
     */
    public double quickEvaluate(Board board) {
        return evaluateMaterial(board);
    }
}