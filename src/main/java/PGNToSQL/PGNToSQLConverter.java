package PGNToSQL;

import PGNToSQL.PGN.PGNGame;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

public class PGNToSQLConverter {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String pgnFilePath = args.length > 0 ? args[0] : scanner.nextLine().trim();

        String dbUrl = "jdbc:sqlite:database/chess-games.db";

        initializeDatabase(dbUrl);

        try {
            List<PGNGame> games = parsePGNFile(pgnFilePath);
            saveGamesToDatabase(games, dbUrl);
            System.out.println("Successfully processed " + games.size() + " games.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeDatabase(String dbUrl) {
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

            // Indexes
            String[] indexes = {
                    "CREATE INDEX IF NOT EXISTS idx_players ON chess_games(white_player, black_player)",
                    "CREATE INDEX IF NOT EXISTS idx_result ON chess_games(result)",
                    "CREATE INDEX IF NOT EXISTS idx_eco ON chess_games(eco)",
                    "CREATE INDEX IF NOT EXISTS idx_date ON chess_games(game_date)"
            };
            for (String idx : indexes) stmt.execute(idx);

            System.out.println("Database initialized.");
        } catch (SQLException e) {
            System.err.println("DB init error: " + e.getMessage());
        }
    }

    private static List<PGNGame> parsePGNFile(String filePath) throws IOException {
        String content = readFile(filePath);
        List<PGNGame> games = new ArrayList<>();

        // Split into individual games by [Event ...] tag
        Pattern gameSplitter = Pattern.compile("(?=\\[Event \")");
        String[] rawGames = gameSplitter.split(content);

        for (String raw : rawGames) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            try {
                PGNGame game = new PGNGame(trimmed);
                if (isValidGame(game)) {
                    games.add(game);
                }
            } catch (Exception ignored) {
                // Skip malformed games silently
            }
        }

        System.out.println("Parsed " + games.size() + " valid games.");
        return games;
    }

    private static String readFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        // Try UTF-8 first, then fallback
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(bytes, Charset.forName("Windows-1252"));
        }
    }

    private static boolean isValidGame(PGNGame g) {
        return !g.getWhite().isEmpty() && !g.getBlack().isEmpty() && g.getMoveCount() > 0;
    }

    private static void saveGamesToDatabase(List<PGNGame> games, String dbUrl) {
        String sql = """
            INSERT INTO chess_games 
            (event, site, game_date, round, white_player, black_player, result, 
             white_elo, black_elo, eco, opening, moves_text, move_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (PGNGame g : games) {
                stmt.setString(1, g.getHeader("Event"));
                stmt.setString(2, g.getHeader("Site"));
                stmt.setString(3, g.getHeader("Date"));
                stmt.setString(4, g.getHeader("Round"));
                stmt.setString(5, g.getWhite());
                stmt.setString(6, g.getBlack());
                stmt.setString(7, g.getResult());
                stmt.setObject(8, g.getHeader("WhiteElo").isEmpty() ? null : Integer.parseInt(g.getHeader("WhiteElo")));
                stmt.setObject(9, g.getHeader("BlackElo").isEmpty() ? null : Integer.parseInt(g.getHeader("BlackElo")));
                stmt.setString(10, g.getHeader("ECO"));
                stmt.setString(11, g.getHeader("Opening"));
                stmt.setString(12, g.getMovesText());
                stmt.setInt(13, g.getMoveCount());

                stmt.addBatch();
            }
            stmt.executeBatch();
            System.out.println("Saved " + games.size() + " games to database.");

        } catch (SQLException e) {
            System.err.println("Database save error: " + e.getMessage());
        }
    }
}