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

        if (piece.isEmpty() || piece.equals("x") || piece.equals("o")) return board;

        // Turn Check
        String turn = board.getFENStringPosition().split(" ")[1];
        boolean isWhiteTurn = turn.equals("w");
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        if (isWhiteTurn != isWhitePiece) {
            board.setGameState("Not your turn");
            return board;
        }

        // Own Capture Check
        String target = board.getSquare(to);
        boolean targetOccupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();
        if (targetOccupied) {
            boolean targetIsWhite = Character.isUpperCase(target.charAt(0));
            if (isWhitePiece == targetIsWhite) {
                board.setGameState("Cannot capture own piece");
                return board;
            }
        }

        Moves moves = new Moves(piece, board);
        if (identifyPlayPiece(piece, moves, from, to)) {
            // Check for Castling intent
            boolean isCastling = piece.equalsIgnoreCase("k") && Math.abs(from.charAt(0) - to.charAt(0)) == 2;

            // Check for passing through check (Castling only)
            if (isCastling) {
                String midSquare = getMiddleSquare(from, to);
                if (inCheck(board, from, isWhiteTurn) || inCheck(board, midSquare, isWhiteTurn)) {
                    board.setGameState("Illegal: Cannot castle out of or through check");
                    return board;
                }
            }

            // Execute Move
            String captured = board.getSquare(to);
            makeMove(from, to, board, piece);
            if (isCastling) handleCastlingRook(from, to, board, false);

            // King Safety
            String kingPiece = isWhiteTurn ? "K" : "k";
            String kingPos = board.getPiecePositions(kingPiece).get(0);

            if (inCheck(board, kingPos, isWhiteTurn)) {
                undoMove(from, to, board, piece, captured);
                if (isCastling) handleCastlingRook(from, to, board, true);
                board.setGameState("Illegal: King in check");
                return board;
            }

            // Update FEN and Check for Mate
            updateFEN(board, piece, from);

            String nextTurn = isWhiteTurn ? "b" : "w";
            if (isCheckmate(board, nextTurn)) {
                String winner = isWhiteTurn ? "White" : "Black";
                board.setGameState("Checkmate! " + winner + " wins!");
            } else {
                board.setGameState("ongoing");
            }
            return board;
        }

        board.setGameState("Invalid Move");
        return board;
    }

    private static String getMiddleSquare(String from, String to) {
        char fFile = from.charAt(0);
        char tFile = to.charAt(0);
        char midFile = (char) ((fFile + tFile) / 2);
        return "" + midFile + from.charAt(1);
    }

    private static void handleCastlingRook(String kFrom, String kTo, Board board, boolean undo) {
        int rank = kFrom.charAt(1) - '0';
        boolean kingSide = kTo.charAt(0) > kFrom.charAt(0);

        String rFrom = kingSide ? "h" + rank : "a" + rank;
        String rTo   = kingSide ? "f" + rank : "d" + rank;

        String rook = board.getSquare(undo ? rTo : rFrom);
        if (undo) {
            makeMove(rTo, rFrom, board, rook);
        } else {
            makeMove(rFrom, rTo, board, rook);
        }
    }

    private static void makeMove(String from, String to, Board board, String piece) {
        Integer[] coords = board.processFileAndRank(from);
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

    private static boolean isCheckmate(Board board, String turn) {
        boolean isWhite = turn.equals("w");
        String kingPiece = isWhite ? "K" : "k";
        ArrayList<String> kPos = board.getPiecePositions(kingPiece);
        if (kPos.isEmpty()) return true;

        if (!inCheck(board, kPos.get(0), isWhite)) return false;

        ArrayList<String> myPieces = isWhite ? whitePieces : blackPieces;
        char[] files = "abcdefgh".toCharArray();

        for (String p : myPieces) {
            for (String from : board.getPiecePositions(p)) {
                Moves m = new Moves(p, board);
                for (char f : files) {
                    for (int r = 1; r <= 8; r++) {
                        String to = "" + f + r;
                        if (from.equals(to)) continue;

                        if (identifyPlayPiece(p, m, from, to)) {
                            // Valid Capture Check
                            String target = board.getSquare(to);
                            boolean targetOccupied = !target.equals("o") && !target.equals("x") && !target.isEmpty();
                            if (targetOccupied && (isWhite == Character.isUpperCase(target.charAt(0)))) continue;

                            String captured = board.getSquare(to);
                            makeMove(from, to, board, p);
                            String newKingPos = p.equalsIgnoreCase("k") ? to : kPos.get(0);
                            boolean safe = !inCheck(board, newKingPos, isWhite);
                            undoMove(from, to, board, p, captured);

                            if (safe) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void updateFEN(Board board, String piece, String from) {
        board.setFENStringPosition();
        String rawFen = board.getFENStringPosition();
        String[] parts = rawFen.split(" ");
        String rights = parts[2];

        if (piece.equals("K")) rights = rights.replace("K", "").replace("Q", "");
        if (piece.equals("k")) rights = rights.replace("k", "").replace("q", "");
        if (piece.equals("R")) {
            if (from.equals("h1")) rights = rights.replace("K", "");
            if (from.equals("a1")) rights = rights.replace("Q", "");
        }
        if (piece.equals("r")) {
            if (from.equals("h8")) rights = rights.replace("k", "");
            if (from.equals("a8")) rights = rights.replace("q", "");
        }
        if (rights.isEmpty()) rights = "-";

        String newFen = parts[0] + " " + parts[1] + " " + rights + " " + parts[3] + " " + parts[4] + " " + parts[5];
        board.setRawFEN(newFen);
    }

    public static ArrayList<String> generateAllPossibleMoves(Board board, String from, String piece) {
        ArrayList<String> valid = new ArrayList<>();
        char[] files = "abcdefgh".toCharArray();
        for (char f : files) {
            for (int r = 1; r <= 8; r++) {
                String to = "" + f + r;
                if (!from.equals(to)) {
                    Moves m = new Moves(piece, board);
                    if (identifyPlayPiece(piece, m, from, to)) {
                        String t = board.getSquare(to);
                        boolean occ = !t.equals("o") && !t.equals("x") && !t.isEmpty();
                        if (occ && (Character.isUpperCase(piece.charAt(0)) == Character.isUpperCase(t.charAt(0)))) continue;

                        // Check if move exposes king to check
                        String captured = board.getSquare(to);
                        makeMove(from, to, board, piece);
                        String turn = board.getFENStringPosition().split(" ")[1];
                        boolean isWhite = turn.equals("w");
                        String kPiece = isWhite ? "K" : "k";
                        // Find king (might have moved)
                        String kPos = board.getPiecePositions(kPiece).isEmpty() ? "" : board.getPiecePositions(kPiece).get(0);
                        boolean safe = !inCheck(board, kPos, isWhite);

                        // Special Castling check logic
                        if (piece.equalsIgnoreCase("k") && Math.abs(from.charAt(0) - to.charAt(0)) == 2) {
                            String mid = getMiddleSquare(from, to);
                            if (inCheck(board, from, isWhite) || inCheck(board, mid, isWhite)) safe = false;
                        }

                        undoMove(from, to, board, piece, captured);

                        if (safe) valid.add(to);
                    }
                }
            }
        }
        return valid;
    }
}