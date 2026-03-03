# Chess Engine & Multiplayer Platform

A complete chess ecosystem built from scratch featuring a custom chess engine and real-time multiplayer capabilities.

## Technical Implementation

- **Custom Domain Logic & AI Integration**: Engineered chess domain logic in Java for move validation and game state management (`logic/Moves.java`, `logic/Board.java`). (Update: Clarified that the Java backend handles domain logic/rules, while the actual AI currently integrated is Stockfish via `UCIEngine.js`. The custom AI engine is pending integration.)
- **RESTful & WebSocket API**: Developed using Javalin framework to handle game sessions, player matching, and move processing. (Update: Added WebSockets to this description, as `WebSocketBroadcaster.java` and `chessMatchHandler.java` actively use WebSockets for real-time multiplayer, not just REST).
- **Data Layer**: Implemented SQLite database with PGN parser for game storage and future model training (`DataHandler/DatabaseManager.java`).
- **Automated QA & DevOps**: (Update: Added this entirely new bullet point to reflect the `Dockerfile`, GitHub Actions `.github/workflows/tests.yml`, and Selenium UI tests in `UserInterfaceTest/ChessUITest.java` which were missing from the template).
- **System Architecture**: Designed to support both AI opponents and human-vs-human gameplay

## Key Features

### Chess Engine
- Complete FEN (Forsyth–Edwards Notation) integration and parsing (`Board.java` initialization).
- Full rule enforcement including special moves (castling, en passant, promotion)
- Move validation with check/checkmate detection
- Board state management and game history

### Multiplayer Platform
- Real-time multiplayer functionality via WebSockets. (Update: Specified WebSockets instead of relying solely on REST for real-time).
- Web-based interface served via Javalin static files.
- Player matching, authentication, and game session management (`chessMatchHandler.java` and `GameManager.java`).

### Data & Analytics
- PGN to SQL converter for chess game database creation (`PGNToSQLConverter.java`).
- Automated PGN downloading (`PGNDownloader.java`). (Update: Added downloading feature found in code).
- Support for model training and chess AI development.

## Technology Stack

- **Backend**: Java 21, Javalin Framework, WebSockets. (Update: Added WebSockets).
- **FrontEnd**: Vanilla JavaScript, HTML5, CSS3, Stockfish.js. (Update: Added frontend stack based on `src/main/resources/Front-End/`).
- **Database**: SQLite with JDBC (`DatabaseConfig.java`).
- **QA & Testing**: Selenium WebDriver, JUnit 5, GitHub Actions. (Update: Added testing framework stack based on `pom.xml` and test folders).
- **Game Logic**: Custom chess rule enforcement (Java) with Stockfish bot integration. (Update: Clarified current bot status).
- **Data Processing**: PGN parsing, FEN notation handling

## Project Highlights

This project represents dedication to technical excellence and continuous learning, combining passion for chess with professional software development:

- **Advanced Algorithms**: Recursive move validation and game tree evaluation
- **Mathematical Foundation**: Algorithm optimization and game theory implementation
- **Data Engineering**: Database design and analytics preparation for AI training via bulk PGN parsing.
- **Performance Optimization**: Balancing computational constraints with system requirements
- **Continuous Integration**: Automated browser testing and build pipelines. (Update: Added CI highlight to reflect the `.github` workflows).

## Architecture

```
Chess Platform
├── Core Logic (Board, Moves, EngineCalculations)  *(Update: Renamed to match codebase)*
├── Multiplayer API (Javalin REST & WebSockets)    *(Update: Added WebSockets)*
├── Data Layer (SQLite + PGN Parser)
├── Web Interface (Vanilla JS/HTML/CSS)            *(Update: Added frontend explicit layer)*
└── QA Automation (Selenium & JUnit)               *(Update: Added missing testing layer)*
```

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- Google Chrome (for Selenium UI tests) (Update: Added Chrome requirement for `ChessUITest.java`)

### Installation
```bash
git clone <repository-url>
cd chess-platform
mvn clean compile
```

### Running the Application
```bash
mvn exec:java -Dexec.mainClass="API.ChessServerAPI"
```

### Importing Game Data
```bash
mvn exec:java -Dexec.mainClass="PGNToSQL.PGNToSQLConverter" -Dexec.args="path/to/your/file.pgn"
```

## Current Status

**Completed**
- Chess engine domain logic with complete move validation
- FEN string integration and parsing
- SQLite database with user and game storage
- PGN file parser and automated downloader
- Real-time multiplayer workflow via WebSockets (Update: Specified WebSockets)
- Automated Selenium UI testing pipeline (Update: Added testing status)

**In Development**
- Custom Java Chess AI implementation (to replace/supplement frontend Stockfish)
- Advanced analytics and model training from SQL database

---

**Link to Demo: [Demo](chessworld.duckdns.org)**

*Building complex systems from the ground up - from chess logic to full-stack application development.*