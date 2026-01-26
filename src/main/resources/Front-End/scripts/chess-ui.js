class ChessUI {
    constructor() {
        this.boardElement = document.getElementById('chess-board');
        this.currentPage = window.location.pathname.includes('game.html') ? 'game' : 'home';

        // State
        this.selectedSquare = null;
        this.legalMoves = [];
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.myColor = null;
        this.currentUsername = "Guest";
        this.moveHistory = [];
        this.pollingInterval = null;
        this.lobbyInterval = null;

        // CRITICAL: Start the async chain
        this.init();
    }

    async init() {
        this.setupCommonListeners();

        // 1. WAIT for Username (Fixes the "Spectator" bug)
        await this.fetchAndSetUsername();

        // 2. Route to correct page logic
        if (this.currentPage === 'game') {
            this.initGamePage();
        } else {
            this.initHomePage();
        }
    }

    // --- SETUP ---

    setupCommonListeners() {
        // Any global listeners
    }

    async fetchAndSetUsername() {
        try {
            const username = await ChessEngineAPI.getLoggedInUser();
            this.currentUsername = username || "Guest";
            console.log("Logged in as:", this.currentUsername);

            const el = document.querySelector(".username");
            if (el) el.innerText = this.currentUsername;
        } catch (e) { console.error("Username error:", e); }
    }

    // --- HOME PAGE ---

    initHomePage() {
        const btn = document.getElementById('menu-new-game');
        if (btn) {
            btn.addEventListener('click', async () => {
                btn.textContent = "Joining...";
                btn.disabled = true;
                try {
                    await ChessEngineAPI.newGame();
                    window.location.href = 'game.html';
                } catch (e) {
                    alert("Could not connect to server");
                    btn.disabled = false;
                }
            });
        }
    }

    // --- GAME PAGE ---

    initGamePage() {
        // Button Listeners
        const resignBtn = document.getElementById('resign');
        if(resignBtn) resignBtn.addEventListener('click', () => this.resign());

        const modalNewGame = document.getElementById('modal-new-game');
        if(modalNewGame) modalNewGame.addEventListener('click', () => window.location.href = 'home.html');

        // Draw Board
        this.initializeBoard();

        // Start Logic
        this.checkGameStatus();
    }

    async checkGameStatus() {
        const statusEl = document.getElementById('game-status');
        try {
            const data = await ChessEngineAPI.newGame();

            if (data.status === "waiting for player") {
                if(statusEl) statusEl.textContent = "Waiting for opponent...";
                // Poll waiting status
                this.lobbyInterval = setInterval(async () => {
                    const check = await ChessEngineAPI.newGame();
                    if (check.status === "match_started") {
                        clearInterval(this.lobbyInterval);
                        this.startGame(check);
                    }
                }, 2000);
            }
            else if (data.status === "match_started") {
                this.startGame(data);
            }
        } catch (e) {
            console.error(e);
            alert("Error checking game status. Server might be offline.");
        }
    }

    startGame(data) {
        if (data.port) ChessEngineAPI.setMatchPort(data.port);

        // Determine Color
        // Since we awaited username, this check will now be accurate
        if (data.white === this.currentUsername) {
            this.myColor = 'white';
            alert("You are WHITE");
        } else if (data.black === this.currentUsername) {
            this.myColor = 'black';
            alert("You are BLACK");
        } else {
            this.myColor = 'spectator';
            console.log("Username mismatch. Defaulting to Spectator Mode.");
        }

        this.startPolling();
    }

    startPolling() {
        const dot = document.getElementById('sync-status');

        this.pollingInterval = setInterval(async () => {
            const state = await ChessEngineAPI.getGameState();

            if (state) {
                if(dot) dot.style.backgroundColor = '#0f0';
                setTimeout(() => { if(dot) dot.style.backgroundColor = 'gray'; }, 200);

                if (state.newBoard) this.updateBoardState(state.newBoard);

                if (state.turn) {
                    const serverTurn = state.turn === 'w' ? 'white' : 'black';
                    if (this.currentPlayer !== serverTurn) {
                        this.currentPlayer = serverTurn;
                        this.updatePlayerTurn();
                    }
                }

                // IMPROVED CHECK: Looks for "wins", "Checkmate", or "Game Over"
                if (state.status) {
                    const s = state.status.toLowerCase();
                    if (s.includes("wins") || s.includes("checkmate") || s.includes("game over")) {
                        this.showGameOverModal(state.status);
                        clearInterval(this.pollingInterval); // Stop polling
                    }
                }
            } else {
                if(dot) dot.style.backgroundColor = 'red';
            }
        }, 500);
    }

    // --- INTERACTION ---

    async handleSquareClick(row, col) {
        // Debug Log
        console.log(`Clicked ${row},${col}. MyColor: ${this.myColor}, Turn: ${this.currentPlayer}`);

        // Strict Turn Check
        if (this.myColor !== 'spectator' && this.myColor !== this.currentPlayer) {
            console.log("Blocked: Not your turn.");
            return;
        }

        const position = this.getSquareNotation(row, col);
        const piece = this.currentBoard[row][col];

        if (this.selectedSquare) {
            const [selectedRow, selectedCol] = this.selectedSquare;
            const isLegalMove = this.legalMoves.some(move => move.to === position);

            if (isLegalMove) {
                await this.makeMove(this.getSquareNotation(selectedRow, selectedCol), position);
                this.clearSelection();
                return;
            }
            if (selectedRow === row && selectedCol === col) {
                this.clearSelection();
                return;
            }
            if (piece && this.isOwnPiece(piece)) {
                this.clearSelection();
                await this.selectSquare(row, col, piece);
                return;
            }
            this.clearSelection();
        } else {
            if (piece && this.isOwnPiece(piece)) {
                await this.selectSquare(row, col, piece);
            }
        }
    }

    // --- HELPERS ---

    isOwnPiece(piece) {
        if (!piece || piece === '') return false;

        // FIX: Allow spectators (or mismatched users) to click pieces for testing
        let activeColor = this.myColor;
        if (activeColor === 'spectator' || !activeColor) {
            activeColor = this.currentPlayer; // Fallback: Allow moving current turn's pieces
        }

        const isWhitePiece = piece === piece.toUpperCase();
        return (activeColor === 'white' && isWhitePiece) ||
            (activeColor === 'black' && !isWhitePiece);
    }

    initializeBoard() {
        if(!this.boardElement) return;
        this.boardElement.innerHTML = '';
        // Loop 0 to 7 (Top-Down)
        for (let row = 0; row < 8; row++) {
            for (let col = 0; col < 8; col++) {
                const square = document.createElement('div');
                square.className = `square ${(row + col) % 2 === 0 ? 'light' : 'dark'}`;
                square.dataset.row = row;
                square.dataset.col = col;
                square.dataset.position = this.getSquareNotation(row, col);
                square.addEventListener('click', () => this.handleSquareClick(row, col));
                this.boardElement.appendChild(square);
            }
        }
        this.updatePieces();
    }

    getSquareNotation(row, col) {
        const files = 'abcdefgh';
        const rank = 8 - row;
        return files[col] + rank;
    }

    getRowColFromNotation(notation) {
        const files = 'abcdefgh';
        const col = files.indexOf(notation[0]);
        const rank = parseInt(notation[1]);
        const row = 8 - rank;
        return [row, col];
    }

    async makeMove(from, to) {
        const result = await ChessEngineAPI.makeMove(from, to);
        if (result.success) {
            this.updateBoardState(result.newBoard);
        } else {
            alert("Invalid Move: " + result.message);
        }
    }

    async selectSquare(row, col, piece) {
        this.selectedSquare = [row, col];
        const square = document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
        if(square) square.classList.add('selected');

        const moves = await ChessEngineAPI.getLegalMoves(this.getSquareNotation(row, col), piece);
        this.legalMoves = moves || [];
        this.highlightLegalMoves();
    }

    updateBoardState(boardState) {
        this.currentBoard = boardState;
        this.updatePieces();
        this.updatePlayerTurn();
    }

    updatePieces() {
        const squares = document.getElementsByClassName('square');
        for (let square of squares) {
            const row = parseInt(square.dataset.row);
            const col = parseInt(square.dataset.col);
            const piece = this.currentBoard[row] ? this.currentBoard[row][col] : '';
            square.textContent = (piece && piece !== '') ? this.getPieceSymbol(piece) : '';
            if(piece) square.style.color = (piece === piece.toUpperCase()) ? 'white' : 'black';
        }
    }

    getPieceSymbol(piece) {
        const symbols = { 'k':'♔', 'q':'♕', 'r':'♖', 'b':'♗', 'n':'♘', 'p':'♙', 'K':'♚', 'Q':'♛', 'R':'♜', 'B':'♝', 'N':'♞', 'P':'♟' };
        return symbols[piece] || '';
    }

    updatePlayerTurn() {
        const el = document.getElementById('player-turn');
        if(el) el.textContent = `${this.currentPlayer.charAt(0).toUpperCase() + this.currentPlayer.slice(1)}'s Turn`;
    }

    highlightLegalMoves() {
        this.legalMoves.forEach(move => {
            const [row, col] = this.getRowColFromNotation(move.to);
            const sq = document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
            if (sq) sq.classList.add(this.currentBoard[row][col] ? 'legal-capture' : 'legal-move');
        });
    }

    clearSelection() {
        if (this.selectedSquare) {
            const sq = document.querySelector(`.square[data-row="${this.selectedSquare[0]}"][data-col="${this.selectedSquare[1]}"]`);
            if (sq) sq.classList.remove('selected');
        }
        this.selectedSquare = null;
        this.legalMoves = [];
        this.clearHighlights();
    }

    clearHighlights() {
        const squares = document.getElementsByClassName('square');
        for (let sq of squares) sq.classList.remove('legal-move', 'legal-capture');
    }

    async resign() {
        if (!confirm("Are you sure you want to resign? This will end the game.")) return;

        try {
            // Call the new API method
            await ChessEngineAPI.resignGame();

            // Immediate feedback for the person who clicked
            // The polling loop will update the board/modal for both players shortly after
            alert("Resigned. Game Over.");
        } catch (e) {
            console.error("Resign error", e);
            alert("Failed to resign. Server might be unreachable.");
        }
    }

    getInitialBoard() {
        return [
            ['r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'],
            ['p', 'p', 'p', 'p', 'p', 'p', 'p', 'p'],
            ['', '', '', '', '', '', '', ''],
            ['', '', '', '', '', '', '', ''],
            ['', '', '', '', '', '', '', ''],
            ['', '', '', '', '', '', '', ''],
            ['P', 'P', 'P', 'P', 'P', 'P', 'P', 'P'],
            ['R', 'N', 'B', 'Q', 'K', 'B', 'N', 'R']
        ];
    }

    showGameOverModal(msg) {
        const m = document.getElementById('game-over-modal');
        if (m) { document.getElementById('modal-message').textContent = msg; m.style.display = 'block'; }
    }
}

document.addEventListener('DOMContentLoaded', () => { new ChessUI(); });