# Chess Engine & Multiplayer Platform

A complete chess ecosystem built from scratch featuring a custom chess engine and real-time multiplayer capabilities.

## 🚀 Technical Implementation

- **Custom Chess Engine**: Engineered chess domain logic in Java with recursive algorithms for move validation and game state management
- **RESTful API**: Developed using Javalin framework to handle game sessions, player matching, and move processing
- **Data Layer**: Implemented SQLite database with PGN parser for game storage and future model training
- **System Architecture**: Designed to support both AI opponents and human-vs-human gameplay

## 🎯 Key Features

### Chess Engine
- Complete FEN (Forsyth–Edwards Notation) integration and parsing
- Full rule enforcement including special moves (castling, en passant, promotion)
- Move validation with check/checkmate detection
- Board state management and game history

### Multiplayer Platform
- Real-time multiplayer functionality
- Web-based interface using Javalin
- Player matching and game session management

### Data & Analytics
- PGN to SQL converter for chess game database creation
- Support for model training and chess AI development
- Optimized computational performance

## 🛠 Technology Stack

- **Backend**: Java 21, Javalin Framework
- **Database**: SQLite with JDBC
- **Game Logic**: Custom chess engine with complete rule enforcement
- **Data Processing**: PGN parsing, FEN notation handling

## 📚 Project Highlights

This project represents dedication to technical excellence and continuous learning, combining passion for chess with professional software development:

- **Advanced Algorithms**: Recursive move validation and game tree evaluation
- **Mathematical Foundation**: Algorithm optimization and game theory implementation
- **Data Engineering**: Database design and analytics preparation for AI training
- **Performance Optimization**: Balancing computational constraints with system requirements

## 🏗 Architecture

```
Chess Platform
├── Core Engine (Board, Moves, Game Logic)
├── Multiplayer API (Javalin RESTful endpoints)  
├── Data Layer (SQLite + PGN Parser)
└── Web Interface
```

## 🚦 Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.6+

### Installation
```bash
git clone <repository-url>
cd chess-platform
mvn clean compile
```

### Running the Application
```bash
mvn exec:java -Dexec.mainClass="chess.logic.ChessGame"
```

### Importing Game Data
```bash
java PGNToSQL.PGNToSQLConverter path/to/your/file.pgn
```

## 📊 Current Status

✅ **Completed**
- Chess engine with complete move validation
- FEN string integration and parsing
- SQLite database with game storage
- PGN file parser and importer
- Basic game workflow

🔄 **In Development**
- Chess AI implementation
- Web interface enhancement
- Multiplayer functionality
- Advanced analytics

---

*Building complex systems from the ground up - from chess logic to full-stack application development.*