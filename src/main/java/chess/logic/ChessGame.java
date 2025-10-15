package chess.logic;

import java.util.*;

public class ChessGame {
    private static final ArrayList<String> blackPieces = new ArrayList<>(List.of("r","n","k","q","p","b"));
    private static final ArrayList<String> whitePieces = new ArrayList<>(List.of("R","N","K","Q","P","B"));

    private static boolean identifyPlayPiece(String piece, Moves moves, String fromSquare, String toSquare) {
        if (piece.equals("P")) {
            return moves.pawnMove(fromSquare, toSquare);
        }

        return switch (piece.toLowerCase()) {
            case "p" -> moves.pawnMove(fromSquare, toSquare);
            case "k" -> moves.kingMove(fromSquare, toSquare);
            case "b" -> moves.bishopMove(fromSquare, toSquare);
            case "r" -> moves.rookMove(fromSquare, toSquare);
            case "q" -> moves.queenMove(fromSquare, toSquare);
            case "n" -> moves.knightMove(fromSquare, toSquare);
            default -> false;
        };
    }

    private static boolean inCheck(Board board, String kingPosition) {
        String player = board.getFENStringPosition().split(" ")[1];
        String[] opponentPieces = (player.equals("w") ? blackPieces : whitePieces).toArray(new String[0]);

        for (String piece : opponentPieces) {
            ArrayList<String> piecePositions = board.getPiecePositions(piece);
            Moves moves = new Moves(piece, board);
            for (String position : piecePositions) {
                if (identifyPlayPiece(piece, moves, position, kingPosition)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMate(String playerTurn, String kingPosition, Board board) {
        // First, the king must be in check
        if (!inCheck(board, kingPosition)) {
            return false;
        }

        // Get all pieces for the current player
        ArrayList<String> playerPieces = playerTurn.equals("w") ? whitePieces : blackPieces;

        // Try every possible move for every piece of the current player
        for (String pieceType : playerPieces) {
            // Get all positions of this piece type on the board
            ArrayList<String> piecePositions = board.getPiecePositions(pieceType);

            for (String fromSquare : piecePositions) {
                // Get all possible moves for this piece from this square
                ArrayList<String> possibleMoves = generateAllPossibleMoves(board, fromSquare, pieceType);

                for (String toSquare : possibleMoves) {
                    // Test if this move gets out of check
                    if (testMoveForCheckEscape(board, fromSquare, toSquare, pieceType, playerTurn, kingPosition)) {
                        return false; // Found at least one legal move - not checkmate
                    }
                }
            }
        }

        // No legal moves found while in check - it's checkmate!
        return true;
    }

    /**
     * Generates all possible destination squares for a piece
     */
    private static ArrayList<String> generateAllPossibleMoves(Board board, String fromSquare, String pieceType) {
        ArrayList<String> possibleMoves = new ArrayList<>();

        // Generate moves to all 64 squares and filter valid ones
        for (char file = 'a'; file <= 'h'; file++) {
            for (int rank = 1; rank <= 8; rank++) {
                String toSquare = "" + file + rank;

                // Skip moving to the same square
                if (fromSquare.equals(toSquare)) continue;

                // Check if this is a valid move for the piece
                Moves moves = new Moves(pieceType, board);
                if (identifyPlayPiece(pieceType, moves, fromSquare, toSquare)) {
                    // Additional check: don't capture own pieces
                    String targetPiece = board.getSquare(toSquare);
                    if ((pieceType.equals(pieceType.toUpperCase()) && !whitePieces.contains(targetPiece)) ||
                            (pieceType.equals(pieceType.toLowerCase()) && !blackPieces.contains(targetPiece))) {
                        possibleMoves.add(toSquare);
                    }
                }
            }
        }

        return possibleMoves;
    }

    /**
     * Tests if a move gets the king out of check
     */
    private static boolean testMoveForCheckEscape(Board board, String fromSquare, String toSquare,
                                                  String pieceType, String playerTurn, String currentKingPos) {
        // Store original state
        String originalFrom = board.getSquare(fromSquare);
        String originalTo = board.getSquare(toSquare);

        // Make the move temporarily
        board.setSquare(fromSquare, " ");
        board.setSquare(toSquare, pieceType);

        // Update king position if the king is moving
        String newKingPosition = currentKingPos;
        if (pieceType.equalsIgnoreCase("k")) {
            newKingPosition = toSquare;
        }

        // Check if king is still in check after the move
        boolean stillInCheck = inCheck(board, newKingPosition);

        // Undo the move
        board.setSquare(fromSquare, originalFrom);
        board.setSquare(toSquare, originalTo);

        // If the move gets out of check, it's a legal escape
        return !stillInCheck;
    }

    private static boolean isStalemate(String playerTurn, String kingPosition, Board board) {
        // For stalemate: NOT in check but no legal moves
        if (inCheck(board, kingPosition)) {
            return false;
        }

        // Get all pieces for the current player
        ArrayList<String> playerPieces = playerTurn.equals("w") ? whitePieces : blackPieces;

        // Try every possible move for every piece
        for (String pieceType : playerPieces) {
            ArrayList<String> piecePositions = board.getPiecePositions(pieceType);

            for (String fromSquare : piecePositions) {
                ArrayList<String> possibleMoves = generateAllPossibleMoves(board, fromSquare, pieceType);

                for (String toSquare : possibleMoves) {
                    if (testMoveForCheckEscape(board, fromSquare, toSquare, pieceType, playerTurn, kingPosition)) {
                        return false; // Found at least one legal move - not stalemate
                    }
                }
            }
        }

        // No legal moves found while not in check - it's stalemate!
        return true;
    }

    private static void playGame(Board board) {
        board.printBoard();
        String playerTurn = board.getFENStringPosition().split(" ")[1];
        Scanner scanner = new Scanner(System.in);
        String kingPosition = "";

        // Get king position for current player
        if (playerTurn.equals("b")) {
            ArrayList<String> positions = board.getPiecePositions("k");
            if (!positions.isEmpty()) {
                kingPosition = positions.getFirst();
            }
        } else if (playerTurn.equals("w")) {
            ArrayList<String> positions = board.getPiecePositions("K");
            if (!positions.isEmpty()) {
                kingPosition = positions.getFirst();
                System.out.println(positions);
            }
        }

        // Check for checkmate before the move
        if (isMate(playerTurn, kingPosition, board)) {
            String winner = playerTurn.equals("w") ? "Black" : "White";
            System.out.println("Checkmate! " + winner + " wins!");
            return;
        }

        // Check for stalemate
        if (isStalemate(playerTurn, kingPosition, board)) {
            System.out.println("Stalemate! Game is a draw.");
            return;
        }

        if (playerTurn.equals("w")) {
            System.out.println("Whites turn. Play a move (e.g: e2-e4):");
        } else if (playerTurn.equals("b")) {
            System.out.println("Blacks turn. Play a move (e.g: e7-e5):");
        }

        String move = scanner.nextLine();
        if (move.equals("resign")){
            if (playerTurn.equals("w")){
                System.out.println("White resigns. Black is victorious.");
            }else if (playerTurn.equals("b")){
                System.out.println("Black resigns. White is victorious.");
            }
            return;
        }

        String[] moveList = move.split("-");

        // Validate move format
        if (moveList.length != 2) {
            System.out.println("Invalid move format. Use format like 'a1-a6'. Try again.");
            playGame(board);
            return;
        }

        String fromSquare = moveList[0].toLowerCase();
        String toSquare = moveList[1].toLowerCase();

        String piece = board.getSquare(fromSquare);

        // Check if there's actually a piece on the fromSquare
        if (piece == null || piece.equals(" ") || piece.equals("o") || piece.equals("x")) {
            System.out.println("No piece at " + fromSquare + ". Try again.");
            playGame(board);
            return;
        }

        // Validate piece color matches player turn
        if (playerTurn.equals("w") && !piece.equals(piece.toUpperCase())) {
            System.out.println("Wrong piece selected! Select white pieces.");
            playGame(board);
            return;
        } else if (playerTurn.equals("b") && !piece.equals(piece.toLowerCase())) {
            System.out.println("Wrong piece selected! Select black pieces.");
            playGame(board);
            return;
        }

        System.out.println(piece + ":" + fromSquare + "->" + toSquare);
        Moves moves = new Moves(piece, board);

        if (identifyPlayPiece(piece, moves, fromSquare, toSquare)) {
            String atToSquare = board.getSquare(toSquare);

            // Check for capturing own pieces
            if (playerTurn.equals("w") && whitePieces.contains(atToSquare)) {
                System.out.println("Cannot capture your own piece");
                playGame(board);
                return;
            } else if (playerTurn.equals("b") && blackPieces.contains(atToSquare)) {
                System.out.println("Cannot capture your own piece");
                playGame(board);
                return;
            }

            // Store the piece that was at the destination (for capture)
            String capturedPiece = atToSquare;

            // Make the move
            makeMove(fromSquare, toSquare, board, piece);

            // Update king position if king moved
            if (piece.equalsIgnoreCase("k")) {
                kingPosition = toSquare;
            }

            // Check if move puts own king in check
            if (inCheck(board, kingPosition)) {
                String player = playerTurn.equals("w") ? "White" : "Black";
                System.out.println(player + " in check. Move illegal.");

                // Undo the move
                undoMove(fromSquare, toSquare, board, piece, capturedPiece);
                playGame(board);
                return;
            }

            // Update FEN and switch turns
            board.setFENStringPosition();

            // Now check if the NEXT player is in checkmate or stalemate
            String nextPlayer = playerTurn.equals("w") ? "b" : "w";
            String nextKingPos = "";
            if (nextPlayer.equals("w")) {
                ArrayList<String> positions = board.getPiecePositions("K");
                if (!positions.isEmpty()) nextKingPos = positions.getFirst();
            } else {
                ArrayList<String> positions = board.getPiecePositions("k");
                if (!positions.isEmpty()) nextKingPos = positions.getFirst();
            }

            if (!nextKingPos.isEmpty()) {
                if (isMate(nextPlayer, nextKingPos, board)) {
                    String winner = playerTurn.equals("w") ? "White" : "Black";
                    System.out.println("Checkmate! " + winner + " wins!");
                    return;
                } else if (isStalemate(nextPlayer, nextKingPos, board)) {
                    System.out.println("Stalemate! Game is a draw.");
                    return;
                }
            }

            // Continue the game with next turn
            playGame(board);
        } else {
            System.out.println("Invalid move. Try again.");
            playGame(board);
        }
    }

    private static void makeMove(String fromSquare, String toSquare, Board board, String piece) {
        // Store what should be at the fromSquare after move
        Integer[] squareList = board.processFileAndRank(fromSquare);
        String emptySquare = ((squareList[0] + squareList[1]) % 2 == 0) ? "o" : "x";
        board.setSquare(fromSquare, emptySquare);
        board.setSquare(toSquare, piece);
    }

    private static void undoMove(String fromSquare, String toSquare, Board board, String piece, String capturedPiece) {
        // Move piece back
        board.setSquare(fromSquare, piece);
        // Restore what was at destination (either empty square or captured piece)
        board.setSquare(toSquare, capturedPiece);
    }

    public static void main(String[] args) {
        System.out.println("Select load game choice (Enter '1' or '2')");
        Scanner scanner = new Scanner(System.in);

        try {
            Board board;
            int choice = Integer.parseInt(scanner.nextLine());
            if (!(new ArrayList<>(Arrays.asList(1, 2)).contains(choice))) {
                throw new IllegalArgumentException();
            }

            if (choice == 1) {
                board = new Board();
                playGame(board);
            } else if (choice == 2) {
                System.out.println("Enter FEN-string (e.g: 2rq1rk1/4ppbp/6p1/3pP3/2pP4/2P1BN2/3QKP1P/RR4RK b - - 0 18):");
                String FENString = scanner.nextLine();
                board = new Board(FENString);
                playGame(board);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Make proper choice");
            main(new String[]{});
        }
    }
}