import chess.logic.Board;
import chess.logic.Moves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

private void identifyPlayPiece(String piece, Moves moves, String fromSquare, String toSquare){

    switch (piece.toLowerCase()){
        case "p":
            moves.pawnMove(fromSquare, toSquare);
        case "k":
            moves.kingMove(fromSquare, toSquare);
        case "b":
            moves.bishopMove(fromSquare, toSquare);
        case "r":
            moves.rookMove(fromSquare, toSquare);
        case "q":
            moves.queenMove(fromSquare, toSquare);
    }
}

private void playGame(Board board){
    board.printBoard();
    String playerTurn = board.getFENStringPosition().split(" ")[1];
    Scanner scanner = new Scanner(System.in);

    if (playerTurn.equals("w")) {
        System.out.println("Whites turn. Play a move (e.g: a1-a6):");
    }else if (playerTurn.equals("b")){
        System.out.println("Blacks turn. Play a move (e.g: a1-a6):");
    }

    String move = scanner.nextLine();
    String[] moveList = move.split("-");
    String fromSquare = moveList[0].toLowerCase();
    String toSquare = moveList[1].toLowerCase();

    String piece = board.getSquare(fromSquare);

    Moves moves = new Moves(piece);

}


public static void main(String[] args) {
    System.out.println("Select load game choice (Enter '1' or '2')");
    Scanner scanner = new Scanner(System.in);

    try {
        Board board;
        int choice = Integer.parseInt(scanner.nextLine());
        if (!(new ArrayList<>(Arrays.asList(1,2))).contains(choice)){
            throw new IllegalArgumentException("Make correct choice.");
        }

        if (choice == 1){
            board = new Board();
        } else if (choice == 2) {
            System.out.println("Enter FEN-string. Carefully enter string to avoid improper result:");
            Scanner scanner1 = new Scanner(System.in);
            String FENString = scanner1.nextLine();
            board = new Board(FENString);
        }
    }catch (IllegalArgumentException e){
        System.out.println("Make proper choice.");
        main(new String[]{});
    }
}