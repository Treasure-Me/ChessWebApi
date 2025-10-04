package chess.logic;

import chess.logic.Board;
import chess.logic.Moves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

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
            case "n" -> moves.knightMove(fromSquare, toSquare); // Added missing knight case
            default -> false;
        };
    }

    private static void playGame(Board board) {
        board.printBoard();
        String playerTurn = board.getFENStringPosition().split(" ")[1];
        Scanner scanner = new Scanner(System.in);

        if (playerTurn.equals("w")) {
            System.out.println("Whites turn. Play a move (e.g: a1-a6):");
        } else if (playerTurn.equals("b")) {
            System.out.println("Blacks turn. Play a move (e.g: a1-a6):");
        }

        String move = scanner.nextLine();
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
        if (piece == null || piece.equals(" ")) {
            System.out.println("No piece at " + fromSquare + ". Try again.");
            playGame(board);
            return;
        }

        System.out.println(piece + ":" + fromSquare + ":" + toSquare);
        Moves moves = new Moves(piece);

        if (identifyPlayPiece(piece, moves, fromSquare, toSquare)) {
            String atToSquare = board.getSquare(toSquare);
            if (piece.toLowerCase().equals(piece) && blackPieces.contains(atToSquare)){
                System.out.println("Cannot capture your own piece");
                playGame(board);
            } else if (piece.toUpperCase().equals(piece) && whitePieces.contains(atToSquare)) {
                System.out.println("Cannot capture your own piece");
                playGame(board);
            }

            Integer[] squareList = board.processFileAndRank(toSquare);
            if ((squareList[0] + squareList[1])%2 == 0){
                board.setSquare(fromSquare, "x");
            }else{
                board.setSquare(fromSquare, "o");
            }
            board.setSquare(toSquare, piece);

            // Continue the game with next turn
            playGame(board);
        } else {
            System.out.println("Invalid move. Try again.");
            playGame(board);
        }
    }

    public static void main(String[] args) {
        System.out.println("Select load game choice (Enter '1' or '2')");
        Scanner scanner = new Scanner(System.in);

        try {
            Board board;
            int choice = Integer.parseInt(scanner.nextLine());
            if (!(new ArrayList<>(Arrays.asList(1, 2)).contains(choice))) {
                throw new IllegalArgumentException("Make correct choice.");
            }

            if (choice == 1) {
                board = new Board();
                playGame(board);
            } else if (choice == 2) {
                System.out.println("Enter FEN-string. Carefully enter string to avoid improper result:");
                String FENString = scanner.nextLine(); // Use same scanner
                board = new Board(FENString);
                playGame(board);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Make proper choice.");
            main(new String[]{});
        }
    }
}