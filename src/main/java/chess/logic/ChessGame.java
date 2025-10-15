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

    private static boolean isMate(String playerTurn, String kingPosition, Board board){
        ArrayList<String> piecesIterated = new ArrayList<>();

        if (!inCheck(board, kingPosition)){
            return false;
        }
        ArrayList<String> playerPieces = Objects.equals(playerTurn, "w")? whitePieces:blackPieces;

        for (String piece:playerPieces){


            if (!piecesIterated.contains(piece)){
                piecesIterated.add(piece);
            }
        }
        return false;
    }

    private static void playGame(Board board) {
        board.printBoard();
        String playerTurn = board.getFENStringPosition().split(" ")[1];
        Scanner scanner = new Scanner(System.in);
        String kingPosition = "";

        // Get king position for current player
        if (playerTurn.equals("b")){
            ArrayList<String> positions = board.getPiecePositions("k");
            if (!positions.isEmpty()) {
                kingPosition = positions.getFirst();
            }
        } else if (playerTurn.equals("w")) {
            ArrayList<String> positions = board.getPiecePositions("K");
            if (!positions.isEmpty()) {
                kingPosition = positions.getFirst();
            }
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

            // Continue the game with next turn
            board.setFENStringPosition();
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