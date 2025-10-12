package DataHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {

    private static final String DB_URL = "jdbc:sqlite:chess-games.db";
    private static final String SCHEMA_FILE = "schema.sql";

    public static void main(String[] args) {
        initializeDatabase();
    }

    public static void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            System.out.println("✅ SQLite JDBC driver loaded successfully");

            // Create database directory if it doesn't exist
            createDatabaseDirectory();

            // Connect to database (this will create the file if it doesn't exist)
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                System.out.println("✅ Connected to SQLite database: " + DB_URL);

                // Check if schema file exists
                if (!Files.exists(Paths.get(SCHEMA_FILE))) {
                    System.err.println("❌ Schema file not found: " + SCHEMA_FILE);
                    createDefaultSchema(connection);
                    return;
                }

                // Read and execute SQL script
                String sqlScript = Files.readString(Paths.get(SCHEMA_FILE));
                executeSqlScript(connection, sqlScript);

                System.out.println("✅ Database schema has been applied successfully");

            } catch (IOException e) {
                System.err.println("❌ Error reading schema file: " + e.getMessage());
                // Create default schema if file doesn't exist
                try (Connection connection = DriverManager.getConnection(DB_URL)) {
                    createDefaultSchema(connection);
                }
            }

        } catch (ClassNotFoundException e) {
            System.err.println("❌ SQLite JDBC driver not found. Please check your dependencies.");
            System.err.println("Add this to your pom.xml:");
            System.err.println("<dependency>");
            System.err.println("    <groupId>org.xerial</groupId>");
            System.err.println("    <artifactId>sqlite-jdbc</artifactId>");
            System.err.println("    <version>3.45.1.0</version>");
            System.err.println("</dependency>");
        } catch (SQLException e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDatabaseDirectory() {
        try {
            Files.createDirectories(Paths.get("database"));
            System.out.println("✅ Database directory created/verified");
        } catch (IOException e) {
            System.err.println("⚠️ Could not create database directory: " + e.getMessage());
        }
    }

    private static void executeSqlScript(Connection connection, String sqlScript) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Split script into individual statements
            String[] statements = sqlScript.split(";");

            for (String stmt : statements) {
                String cleanStmt = stmt.trim();
                if (!cleanStmt.isEmpty()) {
                    try {
                        statement.execute(cleanStmt);
                        System.out.println("✓ Executed: " + cleanStmt.substring(0, Math.min(50, cleanStmt.length())) + "...");
                    } catch (SQLException e) {
                        System.err.println("✗ Failed to execute: " + cleanStmt);
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static void createDefaultSchema(Connection connection) {
        System.out.println("Creating default schema...");

        String defaultSchema = """
            -- Chess games table
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
                move_count INTEGER,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );

            -- Indexes for better performance
            CREATE INDEX IF NOT EXISTS idx_players ON chess_games(white_player, black_player);
            CREATE INDEX IF NOT EXISTS idx_result ON chess_games(result);
            CREATE INDEX IF NOT EXISTS idx_eco ON chess_games(eco);
            CREATE INDEX IF NOT EXISTS idx_date ON chess_games(game_date);
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(defaultSchema);
            System.out.println("✅ Default schema created successfully");
        } catch (SQLException e) {
            System.err.println("❌ Failed to create default schema: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}