package logic;

import java.util.ArrayList;
import java.util.List;

public class ChessGame {
    private static final ArrayList<String> whitePieces = new ArrayList<>(List.of("R","N","K","Q","P","B"));
    private static final ArrayList<String> blackPieces = new ArrayList<>(List.of("r","n","k","q","p","b"));

    private static boolean identifyPlayPiece(String piece, Moves moves, String from, String to) {
        return switch (piece) {
            case "P", "p" -> moves.pawnMove(from, to);
            case "R", "r" -> moves.rookMove(from, to);
            case "N", "n" -> moves.knightMove(from, to);
            case "B", "b" -> moves.bishopMove(from, to);
            case "Q", "q" -> moves.queenMove(from, to);
            case "K", "k" -> moves.kingMove(from, to);
            default -> false;
        };
    }

    public static Board playGame(Board board, String playerMove) {
        String[] parts = playerMove.split("-");
        if (parts.length != 2) return board;

        String from = parts[0];
        String to = parts[1];
        String piece = board.getSquare(from);

        // 1. Check Source
        if (piece.isEmpty() || piece.equals("x") || piece.equals("o")) {
            board.setGameState("No piece selected");
            return board;
        }

        // 2. Turn Validation
        String turn = board.getFENStringPosition().split(" ")[1];
        boolean isWhiteTurn = turn.equals("w");
        System.out.println(turn);
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        System.out.println(piece.charAt(0));

        if (isWhiteTurn != isWhitePiece) {
            board.setGameState("Not your turn");
            return board;
        }

        // 3. Capture Own Piece Check
        String target = board.getSquare(to);
        boolean targetOccupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();
        if (targetOccupied) {
            boolean targetIsWhite = Character.isUpperCase(target.charAt(0));
            if (isWhitePiece == targetIsWhite) {
                board.setGameState("Cannot capture own piece");
                return board;
            }
        }

        // 4. Move Legality
        Moves moves = new Moves(piece, board);
        if (identifyPlayPiece(piece, moves, from, to)) {
            String captured = board.getSquare(to);
            makeMove(from, to, board, piece);

            // 5. King Safety
            String kingPiece = isWhiteTurn ? "K" : "k";
            ArrayList<String> kPos = board.getPiecePositions(kingPiece);
            String kingLoc = kPos.isEmpty() ? "" : kPos.get(0);

            if (inCheck(board, kingLoc, isWhiteTurn)) {
                undoMove(from, to, board, piece, captured);
                board.setGameState("Illegal: King in check");
                return board;
            }

            // 6. Success
            board.setFENStringPosition();
            board.setGameState("ongoing");
            return board;
        }

        board.setGameState("Invalid Move");
        return board;
    }

    private static void makeMove(String from, String to, Board board, String piece) {
        Integer[] coords = board.processFileAndRank(from);
        // Calculate empty square marker based on position (parity)
        String empty = ((coords[0] + coords[1]) % 2 == 0) ? "o" : "x";
        board.setSquare(from, empty);
        board.setSquare(to, piece);
    }

    private static void undoMove(String from, String to, Board board, String piece, String captured) {
        board.setSquare(from, piece);
        if (captured.isEmpty()) {
            Integer[] coords = board.processFileAndRank(to);
            captured = ((coords[0] + coords[1]) % 2 == 0) ? "o" : "x";
        }
        board.setSquare(to, captured);
    }

    private static boolean inCheck(Board board, String kingPos, boolean checkingWhiteKing) {
        ArrayList<String> opponents = checkingWhiteKing ? blackPieces : whitePieces;
        for (String opp : opponents) {
            for (String pos : board.getPiecePositions(opp)) {
                Moves m = new Moves(opp, board);
                if (identifyPlayPiece(opp, m, pos, kingPos)) return true;
            }
        }
        return false;
    }

    public static ArrayList<String> generateAllPossibleMoves(Board board, String from, String piece) {
        ArrayList<String> validMoves = new ArrayList<>();
        char[] files = "abcdefgh".toCharArray();

        for (char f : files) {
            for (int r = 1; r <= 8; r++) {
                String to = "" + f + r;
                if (from.equals(to)) continue;

                Moves m = new Moves(piece, board);
                if (identifyPlayPiece(piece, m, from, to)) {
                    // Basic Capture Check for Highlighting
                    String target = board.getSquare(to);
                    boolean isSameColor = false;
                    boolean occupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();

                    if (occupied) {
                        boolean pW = Character.isUpperCase(piece.charAt(0));
                        boolean tW = Character.isUpperCase(target.charAt(0));
                        isSameColor = (pW == tW);
                    }
                    if (!isSameColor) validMoves.add(to);
                }
            }
        }
        return validMoves;
    }
}