-- Chess games table for SQLite
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
);

-- Create indexes separately (SQLite requires separate index creation)
CREATE INDEX IF NOT EXISTS idx_players ON chess_games(white_player, black_player);
CREATE INDEX IF NOT EXISTS idx_result ON chess_games(result);
CREATE INDEX IF NOT EXISTS idx_eco ON chess_games(eco);
CREATE INDEX IF NOT EXISTS idx_date ON chess_games(game_date);
CREATE INDEX IF NOT EXISTS idx_white_elo ON chess_games(white_elo);
CREATE INDEX IF NOT EXISTS idx_black_elo ON chess_games(black_elo);