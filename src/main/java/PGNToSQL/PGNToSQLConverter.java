package PGNToSQL;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class PGNToSQLConverter {

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"\\]");
    private static final Pattern MOVE_NUMBER_PATTERN = Pattern.compile("\\d+\\.");
    private static final Set<String> RESULTS = Set.of("1-0", "0-1", "1/2-1/2", "*");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String pgnFilePath;

        if (args.length < 1) {
            System.out.println("Enter the PGN file path:");
            pgnFilePath = scanner.nextLine().trim();
        } else {
            pgnFilePath = args[0];
        }

        String dbUrl = "jdbc:sqlite:chess-games.db";

        initializeDatabase(dbUrl);

        try {
            List<ChessGame> games = parsePGNFile(pgnFilePath);
            saveGamesToDatabase(games, dbUrl);
            System.out.println("Successfully processed " + games.size() + " games.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeDatabase(String dbUrl) {
        // Use the exact schema you requested for SQLite
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS chess_games (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 event TEXT,
                 site TEXT,
                 game_date TEXT,
                 round TEXT,
                 white_player TEXT NOT NULL,
                 black_player TEXT NOT NULL,
                 white_elo INTEGER,
                 black_elo INTEGER,
                 result TEXT NOT NULL,
                 eco TEXT,
                 opening TEXT,
                 moves_text TEXT NOT NULL,
                 move_count INTEGER DEFAULT 0,
                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Database table created successfully.");

            // Create indexes
            String[] indexSQLs = {
                    "CREATE INDEX IF NOT EXISTS idx_players ON chess_games(white_player, black_player)",
                    "CREATE INDEX IF NOT EXISTS idx_result ON chess_games(result)",
                    "CREATE INDEX IF NOT EXISTS idx_eco ON chess_games(eco)",
                    "CREATE INDEX IF NOT EXISTS idx_date ON chess_games(game_date)",
                    "CREATE INDEX IF NOT EXISTS idx_white_elo ON chess_games(white_elo)",
                    "CREATE INDEX IF NOT EXISTS idx_black_elo ON chess_games(black_elo)"
            };

            for (String indexSQL : indexSQLs) {
                try {
                    stmt.execute(indexSQL);
                } catch (SQLException e) {
                    System.out.println("Index already exists or couldn't be created: " + e.getMessage());
                }
            }

            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    private static List<ChessGame> parsePGNFile(String filePath) throws IOException {
        List<ChessGame> games = new ArrayList<>();

        // Try different encodings
        String content = readFileWithFallbackEncoding(filePath);

        if (content == null) {
            throw new IOException("Could not read file with any encoding. File may be corrupted.");
        }

        // Split content into individual games
        String[] rawGames = content.split("\\n\\s*\\n\\s*\\n");

        System.out.println("Found " + rawGames.length + " potential game sections");

        for (int i = 0; i < rawGames.length; i++) {
            String rawGame = rawGames[i].trim();
            if (rawGame.isEmpty()) continue;

            try {
                ChessGame game = parseSingleGame(rawGame);
                if (game != null && isValidGame(game)) {
                    games.add(game);
                    if (games.size() % 100 == 0) {
                        System.out.println("Parsed " + games.size() + " games so far...");
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse game " + (i + 1) + ": " + e.getMessage());
            }
        }

        return games;
    }

    private static String readFileWithFallbackEncoding(String filePath) {
        Charset[] charsets = {
                StandardCharsets.UTF_8,
                StandardCharsets.ISO_8859_1,
                Charset.forName("Windows-1252"),
                StandardCharsets.US_ASCII
        };

        for (Charset charset : charsets) {
            try {
                System.out.println("Trying encoding: " + charset.name());
                byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                return new String(bytes, charset);
            } catch (Exception e) {
                System.out.println("Failed with " + charset.name() + ": " + e.getMessage());
            }
        }
        return null;
    }

    private static ChessGame parseSingleGame(String gameText) {
        ChessGame game = new ChessGame();
        StringBuilder movesBuilder = new StringBuilder();
        String[] lines = gameText.split("\\r?\\n");
        boolean inMoveSection = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("[")) {
                parseHeader(line, game);
            } else {
                // Clean the line of any non-printable characters
                line = line.replaceAll("[^\\x20-\\x7E]", "").trim();
                if (!line.isEmpty()) {
                    movesBuilder.append(line).append(" ");
                    inMoveSection = true;
                }
            }
        }

        if (inMoveSection) {
            String movesText = cleanMoves(movesBuilder.toString());
            game.setMovesText(movesText);
            game.setMoveCount(countMoves(movesText));
        }

        return game;
    }

    private static int countMoves(String moves) {
        if (moves == null || moves.trim().isEmpty()) return 0;
        return moves.trim().split("\\s+").length;
    }

    private static boolean isValidGame(ChessGame game) {
        return game.getWhitePlayer() != null &&
                !game.getWhitePlayer().equals("Unknown") &&
                game.getBlackPlayer() != null &&
                !game.getBlackPlayer().equals("Unknown") &&
                game.getMovesText() != null &&
                !game.getMovesText().trim().isEmpty();
    }

    private static void parseHeader(String line, ChessGame game) {
        Matcher matcher = HEADER_PATTERN.matcher(line);
        if (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);

            switch (key.toUpperCase()) {
                case "EVENT" -> game.setEvent(value);
                case "SITE" -> game.setSite(value);
                case "DATE" -> game.setGameDate(value);
                case "ROUND" -> game.setRound(value);
                case "WHITE" -> game.setWhitePlayer(value);
                case "BLACK" -> game.setBlackPlayer(value);
                case "RESULT" -> game.setResult(value);
                case "WHITEELO" -> game.setWhiteElo(parseIntSafe(value));
                case "BLACKELO" -> game.setBlackElo(parseIntSafe(value));
                case "ECO" -> game.setEco(value);
                case "OPENING" -> game.setOpening(value);
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

    private static void saveGamesToDatabase(List<ChessGame> games, String dbUrl) {
        // Updated SQL to match your schema exactly
        String sql = """
            INSERT INTO chess_games 
            (event, site, game_date, round, white_player, black_player, result, white_elo, black_elo, eco, opening, moves_text, move_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int batchCount = 0;
            for (ChessGame game : games) {
                stmt.setString(1, game.getEvent());
                stmt.setString(2, game.getSite());
                stmt.setString(3, game.getGameDate());
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
                stmt.setString(11, game.getOpening());
                stmt.setString(12, game.getMovesText());
                stmt.setInt(13, game.getMoveCount());

                stmt.addBatch();
                batchCount++;

                // Execute in batches to avoid memory issues
                if (batchCount % 100 == 0) {
                    stmt.executeBatch();
                    System.out.println("Processed " + batchCount + " games...");
                }
            }

            // Execute remaining batch
            int[] results = stmt.executeBatch();
            System.out.println("Inserted " + results.length + " games into database.");

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Updated ChessGame data class to match your schema
    static class ChessGame {
        private String event = "Unknown";
        private String site = "Unknown";
        private String gameDate = "????.??.??";
        private String round = "?";
        private String whitePlayer = "Unknown";
        private String blackPlayer = "Unknown";
        private String result = "*";
        private Integer whiteElo;
        private Integer blackElo;
        private String eco;
        private String opening;
        private String movesText;
        private int moveCount = 0;

        // Getters and setters
        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }
        public String getSite() { return site; }
        public void setSite(String site) { this.site = site; }
        public String getGameDate() { return gameDate; }
        public void setGameDate(String gameDate) { this.gameDate = gameDate; }
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
        public String getOpening() { return opening; }
        public void setOpening(String opening) { this.opening = opening; }
        public String getMovesText() { return movesText; }
        public void setMovesText(String movesText) { this.movesText = movesText; }
        public int getMoveCount() { return moveCount; }
        public void setMoveCount(int moveCount) { this.moveCount = moveCount; }
    }
}