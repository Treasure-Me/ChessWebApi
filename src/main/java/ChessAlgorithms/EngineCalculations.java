// package ChessAlgorithms;

// import logic.Board;
// import logic.Moves;

// import java.util.*;

// public class EngineCalculations {

//     // --- OPTIMIZED PIECE VALUES (Lookup Table for Speed) ---
//     private static final Map<String, Integer> PIECE_VALUES = new HashMap<>();
//     static {
//         PIECE_VALUES.put("P", 100);   PIECE_VALUES.put("p", -100);
//         PIECE_VALUES.put("N", 320);   PIECE_VALUES.put("n", -320);
//         PIECE_VALUES.put("B", 330);   PIECE_VALUES.put("b", -330);
//         PIECE_VALUES.put("R", 500);   PIECE_VALUES.put("r", -500);
//         PIECE_VALUES.put("Q", 900);   PIECE_VALUES.put("q", -900);
//         PIECE_VALUES.put("K", 20000); PIECE_VALUES.put("k", -20000);
//     }

//     private static final int CHECKMATE_SCORE = 100000;
//     private static final int STALEMATE_SCORE = 0;

//     // --- MAIN SEARCH METHODS ---

//     /**
//      * Iterative Deepening: Searches depth 1, then 2, then 3...
//      * This ensures we always have a "best move" ready if time runs out.
//      */
//     public String iterativeDeepening(Board board, long maxTimeMillis) {
//         String bestMove = "";
//         long startTime = System.currentTimeMillis();
        
//         // LIMIT: Start with depth 4 for your i5 laptop. 
//         // Going deeper than 5 or 6 in Java without bitboards will be slow.
//         int maxDepth = 5; 

//         for (int depth = 1; depth <= maxDepth; depth++) {
//             if (System.currentTimeMillis() - startTime > maxTimeMillis) {
//                 break; // Time's up
//             }

//             try {
//                 String move = findBestMove(board, depth);
//                 if (!move.isEmpty()) {
//                     bestMove = move;
//                 }
//                 // Optional: Print info to console to debug
//                 // System.out.println("Depth " + depth + " | Best: " + bestMove);
//             } catch (Exception e) {
//                 System.err.println("Error at depth " + depth + ": " + e.getMessage());
//                 break;
//             }
//         }
//         return bestMove;
//     }

//     public String findBestMove(Board board, int depth) {
//         String currentTurn = getTurnFromFEN(board);
//         boolean maximizingPlayer = currentTurn.equals("w");

//         List<String> legalMoves = generateLegalMoves(board, currentTurn);
        
//         // Fallback if no moves are available (Mate/Stalemate)
//         if (legalMoves.isEmpty()) return "";

//         String bestMove = legalMoves.get(0);
//         double bestEval = maximizingPlayer ? -Double.MAX_VALUE : Double.MAX_VALUE;

//         // Alpha-Beta Pruning Initialization
//         double alpha = -Double.MAX_VALUE;
//         double beta = Double.MAX_VALUE;

//         for (String move : legalMoves) {
//             Board newBoard = simulateMove(board, move);
            
//             // Recursive Minimax Call
//             double eval = minimax(newBoard, depth - 1, alpha, beta, !maximizingPlayer);

//             if (maximizingPlayer) {
//                 if (eval > bestEval) {
//                     bestEval = eval;
//                     bestMove = move;
//                 }
//                 alpha = Math.max(alpha, eval);
//             } else {
//                 if (eval < bestEval) {
//                     bestEval = eval;
//                     bestMove = move;
//                 }
//                 beta = Math.min(beta, eval);
//             }
//         }
//         return bestMove;
//     }

//     public double minimax(Board board, int depth, double alpha, double beta, boolean maximizingPlayer) {
//         if (depth == 0) {
//             return evaluate(board);
//         }
        
//         // Check Game Over conditions
//         if (isGameOver(board)) {
//             return evaluate(board); 
//         }

//         String currentTurn = maximizingPlayer ? "w" : "b";
//         List<String> legalMoves = generateLegalMoves(board, currentTurn);

//         if (legalMoves.isEmpty()) return evaluate(board); // Should be handled by isGameOver, but safety check

//         if (maximizingPlayer) {
//             double maxEval = -Double.MAX_VALUE;
//             for (String move : legalMoves) {
//                 Board newBoard = simulateMove(board, move);
//                 double eval = minimax(newBoard, depth - 1, alpha, beta, false);
//                 maxEval = Math.max(maxEval, eval);
//                 alpha = Math.max(alpha, eval);
//                 if (beta <= alpha) break; // Beta Cut-off
//             }
//             return maxEval;
//         } else {
//             double minEval = Double.MAX_VALUE;
//             for (String move : legalMoves) {
//                 Board newBoard = simulateMove(board, move);
//                 double eval = minimax(newBoard, depth - 1, alpha, beta, true);
//                 minEval = Math.min(minEval, eval);
//                 beta = Math.min(beta, eval);
//                 if (beta <= alpha) break; // Alpha Cut-off
//             }
//             return minEval;
//         }
//     }

//     // --- EVALUATION ---

//     public double evaluate(Board board) {
//         // 1. Check for immediate mates (Most important)
//         if (isCheckmate(board, "w")) return -CHECKMATE_SCORE; // White is mated -> Black wins
//         if (isCheckmate(board, "b")) return CHECKMATE_SCORE;  // Black is mated -> White wins
//         if (isStalemate(board)) return STALEMATE_SCORE;

//         double score = 0;
        
//         // 2. Material (The main driver)
//         score += evaluateMaterial(board);
        
//         // 3. Mobility (Simple: number of legal moves)
//         // Note: Calculating legal moves is expensive. For depth > 4, you might want to remove this.
//         score += evaluateMobility(board);

//         return score;
//     }

//     private double evaluateMaterial(Board board) {
//         double score = 0;
//         // Assuming Board has a method to get the grid or we iterate 0-7
//         // You need to adapt 'board.getPieceAt(r, c)' to your actual Board method
//         for (int r = 0; r < 8; r++) {
//             for (int c = 0; c < 8; c++) {
//                 // Adapting to your previous code style
//                 String piece = getPieceAt(board, r, c); 
//                 if (piece != null && !piece.trim().isEmpty() && PIECE_VALUES.containsKey(piece)) {
//                     score += PIECE_VALUES.get(piece);
//                 }
//             }
//         }
//         return score;
//     }

//     private double evaluateMobility(Board board) {
//         // Simple heuristic: 0.1 points per legal move
//         // This encourages the engine to develop pieces
//         List<String> whiteMoves = generateLegalMoves(board, "w");
//         List<String> blackMoves = generateLegalMoves(board, "b");
//         return (whiteMoves.size() - blackMoves.size()) * 0.1;
//     }

//     // --- CORE LOGIC & HELPERS ---

//     /**
//      * CRITICAL: Generates all valid moves for the engine to consider.
//      * Connects to your existing 'Moves' class logic.
//      */
//     private List<String> generateLegalMoves(Board board, String player) {
//         List<String> moves = new ArrayList<>();
//         boolean isWhiteTurn = player.equals("w");

//         for (int r = 0; r < 8; r++) {
//             for (int c = 0; c < 8; c++) {
//                 String piece = getPieceAt(board, r, c);
                
//                 // Skip empty squares or enemy pieces
//                 if (piece == null || piece.trim().isEmpty()) continue;
//                 boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
//                 if (isWhitePiece != isWhiteTurn) continue;

//                 // 1. Get raw pseudo-legal moves from your Moves class
//                 // Assumes Moves.getPossibleMoves returns standard algebraic notation or similar
//                 // You might need to adjust this call to match your Moves.java signature exactly
//                 List<String> rawMoves = Moves.processMoves(board, r, c); 

//                 // 2. Filter for King Safety (Legal Moves only)
//                 for (String moveStr : rawMoves) {
//                     // Assuming rawMoves format is "e2-e4" or similar
//                     // We must simulate to check if King is left in check
//                     if (isMoveSafe(board, moveStr, player)) {
//                         moves.add(moveStr);
//                     }
//                 }
//             }
//         }
//         return moves;
//     }

//     /**
//      * Creates a DEEP COPY of the board and applies the move.
//      * This is crucial so we don't destroy the actual game state during search.
//      */
//     private Board simulateMove(Board original, String move) {
//         // 1. Create Deep Copy
//         // IF your Board class has a .copy() method, use it: Board copy = original.copy();
//         // ELSE, we must manually copy the grid. Assuming Board takes a 2D array in constructor:
//         String[][] newGrid = new String[8][8];
//         for(int i=0; i<8; i++) {
//             for(int j=0; j<8; j++) {
//                 newGrid[i][j] = getPieceAt(original, i, j);
//             }
//         }
//         // You might need to adjust this constructor to match Board.java
//         Board newBoard = new Board(newGrid); 
        
//         // 2. Parse and Apply Move ("e2-e4")
//         String[] parts = move.split("-");
//         String from = parts[0];
//         String to = parts[1];
        
//         int[] fromCoords = parseSquare(from);
//         int[] toCoords = parseSquare(to);
        
//         // Get piece and move it
//         String piece = newGrid[fromCoords[0]][fromCoords[1]];
//         newBoard.setPieceAt(fromCoords[0], fromCoords[1], " "); // Clear old
//         newBoard.setPieceAt(toCoords[0], toCoords[1], piece);   // Set new
        
//         // TODO: Handle Pawn Promotion (Auto-Queen for engine simplicity)
//         if ((piece.equals("P") && toCoords[0] == 0) || (piece.equals("p") && toCoords[0] == 7)) {
//             newBoard.setPieceAt(toCoords[0], toCoords[1], piece.equals("P") ? "Q" : "q");
//         }

//         return newBoard;
//     }

//     // --- SAFETY CHECKS ---

//     private boolean isMoveSafe(Board board, String move, String player) {
//         // 1. Simulate the move on a temporary board
//         Board tempBoard = simulateMove(board, move);
        
//         // 2. Check if the King is under attack
//         return !isInCheck(tempBoard, player);
//     }

//     private boolean isInCheck(Board board, String player) {
//         String king = player.equals("w") ? "K" : "k";
//         int[] kingPos = findPiece(board, king);
        
//         if (kingPos == null) return true; // King missing? equivalent to checkmate/loss

//         // Check if any enemy piece can attack the King's square
//         String enemy = player.equals("w") ? "b" : "w";
//         return isSquareAttacked(board, kingPos[0], kingPos[1], enemy);
//     }

//     private boolean isSquareAttacked(Board board, int r, int c, String enemyColor) {
//         // Iterate all enemy pieces and see if they can move to (r, c)
//         boolean isEnemyWhite = enemyColor.equals("w");
        
//         for (int i = 0; i < 8; i++) {
//             for (int j = 0; j < 8; j++) {
//                 String p = getPieceAt(board, i, j);
//                 if (p == null || p.trim().isEmpty()) continue;
                
//                 if (Character.isUpperCase(p.charAt(0)) == isEnemyWhite) {
//                     // Check if this enemy piece targets the King's square
//                     // This relies on your Moves class handling capture logic correctly
//                     if (Moves.canPieceAttackSquare(board, i, j, r, c)) {
//                          return true;
//                     }
//                 }
//             }
//         }
//         return false;
//     }

//     // --- UTILITIES ---

//     private int[] findPiece(Board board, String piece) {
//         for (int r = 0; r < 8; r++) {
//             for (int c = 0; c < 8; c++) {
//                 if (piece.equals(getPieceAt(board, r, c))) {
//                     return new int[]{r, c};
//                 }
//             }
//         }
//         return null; // Should not happen for King
//     }

//     private int[] parseSquare(String sq) {
//         // "e2" -> [6, 4] (Row 6, Col 4) assuming 0=a8, 7=h1 or standard indexing
//         int col = sq.charAt(0) - 'a';
//         int row = 8 - Integer.parseInt(sq.substring(1));
//         return new int[]{row, col};
//     }

//     private boolean isGameOver(Board board) {
//         return isCheckmate(board, "w") || isCheckmate(board, "b") || isStalemate(board);
//     }

//     private boolean isCheckmate(Board board, String player) {
//         if (!isInCheck(board, player)) return false;
//         List<String> moves = generateLegalMoves(board, player);
//         return moves.isEmpty();
//     }

//     private boolean isStalemate(Board board) {
//         if (isInCheck(board, "w") || isInCheck(board, "b")) return false;
//         String turn = getTurnFromFEN(board);
//         return generateLegalMoves(board, turn).isEmpty();
//     }
    
//     // --- ADAPTERS (Adjust these to match your Board class) ---
    
//     private String getPieceAt(Board b, int r, int c) {
//         // Replace this with your actual method, e.g., b.getSquare(r, c)
//         // or b.grid[r][c]
//         return b.getBoardGrid()[r][c]; 
//     }
    
//     private String getTurnFromFEN(Board b) {
//         // If your Board keeps track of turn, use b.getTurn()
//         // Otherwise, parse FEN:
//         try {
//             return b.getFENStringPosition().split(" ")[1];
//         } catch (Exception e) {
//             return "w"; // Default safety
//         }
//     }
// }