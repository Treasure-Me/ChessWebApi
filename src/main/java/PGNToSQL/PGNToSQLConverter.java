package PGNToSQL;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class PGNToSQLConverter {

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"\\]");
    private static final Pattern MOVE_NUMBER_PATTERN = Pattern.compile("\\d+\\.");
    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2", "*");

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java PGNToSQLConverter <pgn-file-path>");
            return;
        }

        String pgnFilePath = args[0];
        String dbUrl = "jdbc:mysql://localhost:3306/chessdb";
        String user = "username"; // Change this
        String password = "password"; // Change this

        initializeDatabase(dbUrl, user, password);

        try {
            List<ChessGame> games = parsePGNFile(pgnFilePath);
            saveGamesToDatabase(games, dbUrl, user, password);
            System.out.println("Successfully processed " + games.size() + " games.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeDatabase(String dbUrl, String user, String password) {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS chess_games (
                id INT AUTO_INCREMENT PRIMARY KEY,
                event VARCHAR(255),
                site VARCHAR(255),
                date VARCHAR(50),
                round VARCHAR(50),
                white_player VARCHAR(255),
                black_player VARCHAR(255),
                result VARCHAR(10),
                white_elo INT,
                black_elo INT,
                eco VARCHAR(10),
                moves TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    private static List<ChessGame> parsePGNFile(String filePath) throws IOException {
        List<ChessGame> games = new ArrayList<>();
        String content = Files.readString(Paths.get(filePath));

        // Split content into individual games (separated by empty lines)
        String[] rawGames = content.split("\\n\\s*\\n\\s*\\n");

        for (String rawGame : rawGames) {
            if (rawGame.trim().isEmpty()) continue;

            ChessGame game = parseSingleGame(rawGame);
            if (game != null) {
                games.add(game);
            }
        }

        return games;
    }

    private static ChessGame parseSingleGame(String gameText) {
        ChessGame game = new ChessGame();
        StringBuilder movesBuilder = new StringBuilder();
        String[] lines = gameText.split("\\n");
        boolean inMoveSection = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                // Parse header
                parseHeader(line, game);
            } else {
                // Move section
                if (!inMoveSection) {
                    inMoveSection = true;
                }
                movesBuilder.append(line).append(" ");
            }
        }

        String movesText = movesBuilder.toString().trim();
        if (!movesText.isEmpty()) {
            game.setMoves(cleanMoves(movesText));
        }

        // Validate that we have at least players and moves
        if (game.getWhitePlayer() == null || game.getBlackPlayer() == null || game.getMoves() == null) {
            return null; // Skip invalid games
        }

        return game;
    }

    private static void parseHeader(String line, ChessGame game) {
        Matcher matcher = HEADER_PATTERN.matcher(line);
        if (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);

            switch (key.toUpperCase()) {
                case "EVENT" -> game.setEvent(value);
                case "SITE" -> game.setSite(value);
                case "DATE" -> game.setDate(value);
                case "ROUND" -> game.setRound(value);
                case "WHITE" -> game.setWhitePlayer(value);
                case "BLACK" -> game.setBlackPlayer(value);
                case "RESULT" -> game.setResult(value);
                case "WHITEELO" -> game.setWhiteElo(parseIntSafe(value));
                case "BLACKELO" -> game.setBlackElo(parseIntSafe(value));
                case "ECO" -> game.setEco(value);
            }
        }
    }

    private static String cleanMoves(String movesText) {
        // Remove move numbers
        String cleaned = MOVE_NUMBER_PATTERN.matcher(movesText).replaceAll("");

        // Remove result at the end
        for (String result : RESULTS) {
            if (cleaned.endsWith(result)) {
                cleaned = cleaned.substring(0, cleaned.length() - result.length()).trim();
                break;
            }
        }

        // Remove annotations and comments
        cleaned = cleaned.replaceAll("\\{.*?\\}", "")  // {comments}
                .replaceAll("\\$\\d+", "")     // $1, $2 NAGs
                .replaceAll("[?!]+", "")       // !, ?, !!, ??
                .replaceAll("\\s+", " ")       // Multiple spaces
                .trim();

        return cleaned;
    }

    private static Integer parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void saveGamesToDatabase(List<ChessGame> games, String dbUrl, String user, String password) {
        String sql = """
            INSERT INTO chess_games 
            (event, site, date, round, white_player, black_player, result, white_elo, black_elo, eco, moves)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(dbUrl, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (ChessGame game : games) {
                stmt.setString(1, game.getEvent());
                stmt.setString(2, game.getSite());
                stmt.setString(3, game.getDate());
                stmt.setString(4, game.getRound());
                stmt.setString(5, game.getWhitePlayer());
                stmt.setString(6, game.getBlackPlayer());
                stmt.setString(7, game.getResult());

                if (game.getWhiteElo() != null) {
                    stmt.setInt(8, game.getWhiteElo());
                } else {
                    stmt.setNull(8, Types.INTEGER);
                }

                if (game.getBlackElo() != null) {
                    stmt.setInt(9, game.getBlackElo());
                } else {
                    stmt.setNull(9, Types.INTEGER);
                }

                stmt.setString(10, game.getEco());
                stmt.setString(11, game.getMoves());

                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            System.out.println("Inserted " + results.length + " games into database.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ChessGame data class
    static class ChessGame {
        private String event = "Unknown";
        private String site = "Unknown";
        private String date = "????.??.??";
        private String round = "?";
        private String whitePlayer = "Unknown";
        private String blackPlayer = "Unknown";
        private String result = "*";
        private Integer whiteElo;
        private Integer blackElo;
        private String eco;
        private String moves;

        // Getters and setters
        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }

        public String getSite() { return site; }
        public void setSite(String site) { this.site = site; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getRound() { return round; }
        public void setRound(String round) { this.round = round; }

        public String getWhitePlayer() { return whitePlayer; }
        public void setWhitePlayer(String whitePlayer) { this.whitePlayer = whitePlayer; }

        public String getBlackPlayer() { return blackPlayer; }
        public void setBlackPlayer(String blackPlayer) { this.blackPlayer = blackPlayer; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }

        public Integer getWhiteElo() { return whiteElo; }
        public void setWhiteElo(Integer whiteElo) { this.whiteElo = whiteElo; }

        public Integer getBlackElo() { return blackElo; }
        public void setBlackElo(Integer blackElo) { this.blackElo = blackElo; }

        public String getEco() { return eco; }
        public void setEco(String eco) { this.eco = eco; }

        public String getMoves() { return moves; }
        public void setMoves(String moves) { this.moves = moves; }
    }
}