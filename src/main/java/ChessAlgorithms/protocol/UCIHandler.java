package ChessAlgorithms.protocol;

import logic.Board;
import logic.ChessGame;
import java.util.Scanner;
import ChessAlgorithms.EngineCalculations;

public class UCIHandler {

    private static Board currentBoard = new Board(); // Assuming this initializes standard startpos

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            String command = tokens[0];

            switch (command) {
                case "uci":
                    System.out.println("id name DevBot");
                    System.out.println("id author Gugulakhe Makamo");
                    System.out.println("uciok");
                    break;

                case "isready":
                    System.out.println("readyok");
                    break;

                case "ucinewgame":
                    // Reset internal engine states like Transposition Tables
                    EngineCalculations.clearTT();
                    break;

                case "position":
                    setupPosition(tokens, line);
                    break;

                case "go":
                    calculateGo(tokens);
                    break;

                case "stop":
                    // If you want to support sudden stops, you'd need to set a flag in EngineCalculations
                    EngineCalculations.stopSearch();
                    break;

                case "quit":
                    scanner.close();
                    System.exit(0);
                    break;
            }
        }
    }

    private static void setupPosition(String[] tokens, String line) {
        if (tokens.length < 2) return;

        int movesIndex = -1;
        if (tokens[1].equals("startpos")) {
            currentBoard = new Board(); // initialize default FEN
            movesIndex = 2;
        } else if (tokens[1].equals("fen")) {
            // Reconstruct FEN string from tokens
            StringBuilder fen = new StringBuilder();
            movesIndex = 2;
            while (movesIndex < tokens.length && !tokens[movesIndex].equals("moves")) {
                fen.append(tokens[movesIndex]).append(" ");
                movesIndex++;
            }
            currentBoard = new Board(fen.toString().trim());
        }

        // Apply moves if any are provided (e.g., "position startpos moves e2e4 e7e5")
        if (movesIndex < tokens.length && tokens[movesIndex].equals("moves")) {
            for (int i = movesIndex + 1; i < tokens.length; i++) {
                String uciMove = tokens[i];
                String engineMove = uciToEngineMove(uciMove);

                // Assuming playgame updates the state or returns a new board
                String promo = uciMove.length() == 5 ? String.valueOf(uciMove.charAt(4)).toUpperCase() : null;
                currentBoard = ChessGame.playGame(currentBoard, engineMove, promo);
            }
        }
    }

    private static void calculateGo(String[] tokens) {
        long timeLimit = 2000; // Default to 2 seconds

        // Basic Time Management parsing
        for (int i = 1; i < tokens.length; i++) {
            if (tokens[i].equals("movetime") && i + 1 < tokens.length) {
                timeLimit = Long.parseLong(tokens[i + 1]);
            }
            // For full UCI, you'd also parse "wtime", "btime", "winc", "binc" here
            // to calculate a dynamic timeLimit based on the current phase of the game.
        }

        // Run Iterative Deepening
        String bestEngineMove = EngineCalculations.iterativeDeepening(currentBoard, timeLimit);

        // Output in strict UCI format
        System.out.println("bestmove " + engineToUciMove(bestEngineMove));
    }

    // --- Move Conversion Utilities ---

    // Converts your engine's "e2-e4" or "e7-e8=Q" to UCI's "e2e4" or "e7e8q"
    private static String engineToUciMove(String engineMove) {
        if (engineMove == null || engineMove.isEmpty()) return "0000"; // Null move fallback
        String uciMove = engineMove.replace("-", "");
        if (uciMove.contains("=")) {
            uciMove = uciMove.replace("=", "").toLowerCase();
        }
        return uciMove;
    }

    // Converts UCI's "e2e4" or "e7e8q" back to your engine's "e2-e4"
    private static String uciToEngineMove(String uciMove) {
        if (uciMove.length() < 4) return uciMove;
        String from = uciMove.substring(0, 2);
        String to = uciMove.substring(2, 4);
        return from + "-" + to;
    }
}