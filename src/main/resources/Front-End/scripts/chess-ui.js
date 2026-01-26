class ChessUI {
    constructor() {
        this.boardElement = document.getElementById('chess-board');
        this.selectedSquare = null;
        this.legalMoves = [];

        // Game State
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white'; // Synced from server
        this.myColor = null;          // 'white', 'black', or 'spectator'
        this.currentUsername = "Guest"; // Local cache of username
        this.moveHistory = [];
        this.pollingInterval = null;
        this.lobbyInterval = null;

        // Init
        this.setupEventListeners();
        this.initializeBoard();
        this.fetchAndSetUsername(); // Start this immediately
        this.updateUI();
    }

    // --- SETUP & INIT ---

    setupEventListeners() {
        const newGameBtn = document.getElementById('new-game');
        if (newGameBtn) newGameBtn.addEventListener('click', () => this.newGame());

        const resignBtn = document.getElementById('resign');
        if (resignBtn) resignBtn.addEventListener('click', () => this.resign());

        const modalNewGame = document.getElementById('modal-new-game');
        if (modalNewGame) modalNewGame.addEventListener('click', () => this.newGame());

        const modalClose = document.getElementById('modal-close');
        if (modalClose) modalClose.addEventListener('click', () => this.hideModal());
    }

    async fetchAndSetUsername() {
        try {
            const username = await ChessEngineAPI.getLoggedInUser();
            this.currentUsername = username || "Guest";

            // Try ID first, then Class (Fixes the null crash)
            const el = document.getElementById("username") || document.querySelector(".username");
            if (el) el.innerText = this.currentUsername;
        } catch (e) {
            console.error("Username fetch failed", e);
        }
    }

    // --- GAME LOGIC ---

    async newGame() {
        console.log("Requesting new game...");
        if (this.pollingInterval) clearInterval(this.pollingInterval);
        if (this.lobbyInterval) clearInterval(this.lobbyInterval);

        const statusEl = document.getElementById('game-status');
        if(statusEl) statusEl.textContent = "Connecting...";

        try {
            const data = await ChessEngineAPI.newGame();

            // CASE 1: Game Started Immediately
            if (data.status === "match_started") {
                this.initializeGameSession(data);
                return;
            }

            // CASE 2: Waiting in Lobby
            if (data.status === "waiting for player") {
                if(statusEl) statusEl.textContent = "Waiting for opponent...";
                alert("Lobby Created. Waiting for opponent...");

                // Poll Lobby every 2 seconds
                this.lobbyInterval = setInterval(async () => {
                    try {
                        const check = await ChessEngineAPI.newGame();
                        if (check.status === "match_started") {
                            clearInterval(this.lobbyInterval);
                            this.initializeGameSession(check);
                        }
                    } catch (e) { console.log("Lobby poll error", e); }
                }, 2000);
            }
        } catch (error) {
            console.error("New Game Error:", error);
            alert("Server Error: Could not connect.");
        }
    }

    initializeGameSession(data) {
        // 1. Set Port
        if (data.port) ChessEngineAPI.setMatchPort(data.port);

        // 2. Assign Color (Robust Check)
        // We use the cached username to avoid DOM issues
        console.log(`Match: White=${data.white}, Black=${data.black}, Me=${this.currentUsername}`);

        if (data.white === this.currentUsername) {
            this.myColor = 'white';
            alert("Game Started! You are WHITE.");
        } else if (data.black === this.currentUsername) {
            this.myColor = 'black';
            alert("Game Started! You are BLACK.");
        } else {
            this.myColor = 'spectator';
            alert("Game Started! You are spectating.");
        }

        // 3. Reset
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.clearSelection();
        this.updateUI();
        this.hideModal();

        // 4. Start Sync
        this.startPolling();
    }

    startPolling() {
        if (this.pollingInterval) clearInterval(this.pollingInterval);

        console.log("Sync started...");
        this.pollingInterval = setInterval(async () => {
            const state = await ChessEngineAPI.getGameState();

            if (state) {
                // Sync Board
                if (state.newBoard) {
                    this.updateBoardState(state.newBoard);
                }

                // Sync Turn ('w' -> 'white', 'b' -> 'black')
                if (state.turn) {
                    const serverTurn = state.turn === 'w' ? 'white' : 'black';
                    if (this.currentPlayer !== serverTurn) {
                        console.log(`Turn switched to ${serverTurn}`);
                        this.currentPlayer = serverTurn;
                        this.updatePlayerTurn();
                    }
                }

                // Sync Game Over
                if (state.status && (state.status.includes("wins") || state.status.includes("Checkmate"))) {
                    this.showGameOverModal(state.status);
                    clearInterval(this.pollingInterval);
                }
            }
        }, 1000); // 1 second refresh
    }

    // --- INTERACTION ---

    async handleSquareClick(row, col) {
        // 1. Strict Turn Blocking
        if (this.myColor && this.myColor !== 'spectator') {
            if (this.myColor !== this.currentPlayer) {
                console.log("Not your turn!");
                return;
            }
        }

        const position = this.getSquareNotation(row, col);
        const piece = this.currentBoard[row][col];

        // 2. Existing Selection Logic
        if (this.selectedSquare) {
            const [selectedRow, selectedCol] = this.selectedSquare;

            // Move?
            const isLegalMove = this.legalMoves.some(move => move.to === position);
            if (isLegalMove) {
                await this.makeMove(this.getSquareNotation(selectedRow, selectedCol), position);
                this.clearSelection();
                return;
            }

            // Clicked same piece? Deselect
            if (selectedRow === row && selectedCol === col) {
                this.clearSelection();
                return;
            }

            // Clicked own piece? Switch selection
            if (piece && this.isOwnPiece(piece)) {
                this.clearSelection();
                await this.selectSquare(row, col, piece);
                return;
            }

            this.clearSelection();
        } else {
            // Select new
            if (piece && this.isOwnPiece(piece)) {
                await this.selectSquare(row, col, piece);
            }
        }
    }

    async selectSquare(row, col, piece) {
        this.selectedSquare = [row, col];
        const square = this.getSquareElement(row, col);
        if (square) square.classList.add('selected');

        const moves = await ChessEngineAPI.getLegalMoves(this.getSquareNotation(row, col), piece);
        this.legalMoves = moves || [];
        this.highlightLegalMoves();
    }

    async makeMove(from, to) {
        // Optimistic UI update to feel responsive
        console.log(`Moving ${from} -> ${to}`);
        const result = await ChessEngineAPI.makeMove(from, to);

        if (result.success) {
            this.updateBoardState(result.newBoard);
            this.addMoveToHistory(from, to);
            // Wait for poll to confirm turn switch, or force it locally:
            // this.currentPlayer = (this.currentPlayer === 'white' ? 'black' : 'white');
            this.updateUI();
        } else {
            console.warn("Move rejected:", result.message);
            alert("Invalid Move");
            // Force refresh to fix state
            const state = await ChessEngineAPI.getGameState();
            if(state && state.newBoard) this.updateBoardState(state.newBoard);
        }
    }

    // --- HELPERS ---

    isOwnPiece(piece) {
        if (!piece || piece === '') return false;

        // Use myColor if set, otherwise fallback (for testing)
        const activeColor = this.myColor || this.currentPlayer;

        const isWhitePiece = piece === piece.toUpperCase();
        return (activeColor === 'white' && isWhitePiece) ||
            (activeColor === 'black' && !isWhitePiece);
    }

    initializeBoard() {
        this.boardElement.innerHTML = '';
        // CORRECT LOOP: 0 to 7 (Top to Bottom)
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
        const rank = 8 - row; // Row 0 -> Rank 8
        return files[col] + rank;
    }

    getRowColFromNotation(notation) {
        const files = 'abcdefgh';
        const col = files.indexOf(notation[0]);
        const rank = parseInt(notation[1]);
        const row = 8 - rank;
        return [row, col];
    }

    // ... Standard Helpers (No Logic Changes needed below) ...

    getInitialBoard() {
        // Just for visuals before game loads
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

    getPieceSymbol(piece) {
        const symbols = { 'k':'♔', 'q':'♕', 'r':'♖', 'b':'♗', 'n':'♘', 'p':'♙', 'K':'♚', 'Q':'♛', 'R':'♜', 'B':'♝', 'N':'♞', 'P':'♟' };
        return symbols[piece] || '';
    }

    updatePieces() {
        const squares = this.boardElement.getElementsByClassName('square');
        for (let square of squares) {
            const row = parseInt(square.dataset.row);
            const col = parseInt(square.dataset.col);
            const piece = this.currentBoard[row] ? this.currentBoard[row][col] : '';
            square.textContent = (piece && piece !== '') ? this.getPieceSymbol(piece) : '';
            if (piece) square.style.color = (piece === piece.toUpperCase()) ? 'white' : 'black';
        }
    }

    clearSelection() {
        if (this.selectedSquare) {
            const sq = this.getSquareElement(this.selectedSquare[0], this.selectedSquare[1]);
            if (sq) sq.classList.remove('selected');
        }
        this.selectedSquare = null;
        this.legalMoves = [];
        this.clearHighlights();
    }

    clearHighlights() {
        const squares = this.boardElement.getElementsByClassName('square');
        for (let sq of squares) sq.classList.remove('legal-move', 'legal-capture');
    }

    highlightLegalMoves() {
        this.legalMoves.forEach(move => {
            const [row, col] = this.getRowColFromNotation(move.to);
            const sq = this.getSquareElement(row, col);
            if (sq) sq.classList.add(this.currentBoard[row][col] ? 'legal-capture' : 'legal-move');
        });
    }

    getSquareElement(row, col) {
        return document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
    }

    updateBoardState(boardState) { this.currentBoard = boardState; this.updateUI(); }
    updateUI() { this.updatePieces(); this.updatePlayerTurn(); }
    updatePlayerTurn() {
        const el = document.getElementById('player-turn');
        if(el) el.textContent = `${this.currentPlayer.charAt(0).toUpperCase() + this.currentPlayer.slice(1)}'s Turn`;
    }

    addMoveToHistory(from, to) {
        const num = Math.ceil((this.moveHistory.length + 1) / 2);
        const notation = `${from}-${to}`;
        if (this.currentPlayer === 'white') this.moveHistory.push(`${num}. ${notation}`);
        else if (this.moveHistory.length > 0) this.moveHistory[this.moveHistory.length - 1] += ` ${notation}`;
        else this.moveHistory.push(`${num}. ... ${notation}`);

        const list = document.getElementById('move-list');
        if (list) {
            list.innerHTML = this.moveHistory.map(m => `<div class="move-item">${m}</div>`).join('');
            list.scrollTop = list.scrollHeight;
        }
    }

    showGameOverModal(msg) {
        const m = document.getElementById('game-over-modal');
        if (m) { document.getElementById('modal-message').textContent = msg; m.style.display = 'block'; }
    }
    hideModal() {
        const m = document.getElementById('game-over-modal');
        if (m) m.style.display = 'none';
    }
}

document.addEventListener('DOMContentLoaded', () => { new ChessUI(); });