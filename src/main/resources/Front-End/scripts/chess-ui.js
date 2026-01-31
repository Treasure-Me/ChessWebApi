class ChessUI {
    constructor() {
        this.boardElement = document.getElementById('chess-board');
        this.currentPage = window.location.pathname.includes('game.html') ? 'game' : 'home';

        const urlParams = new URLSearchParams(window.location.search);
        this.gameMode = urlParams.get('mode') === 'bot' ? 'bot' : 'online';

        this.selectedSquare = null;
        this.legalMoves = [];
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.myColor = 'white';
        this.currentUsername = "Guest";
        this.pollingInterval = null;
        this.lobbyInterval = null;
        this.botEngine = null;

        this.init();
    }

    async init() {
        this.setupGlobalListeners();
        if (this.boardElement) this.initializeBoard();

        if (this.currentPage === 'game') {
            await this.fetchAndSetUsername();
            if (this.gameMode === 'bot') {
                await this.initBotMode();
            } else {
                await this.initOnlineMode();
            }
        } else {
            this.initHomePage();
        }
    }

    // --- MODE 1: BOT ---
    async initBotMode() {
        console.log("Initializing Bot Mode...");
        this.updateStatus("Syncing...");

        try {
            const gameData = await ChessEngineAPI.newGame(true);
            console.log("Server Game Created:", gameData);
            this.updateStatus("Loading Engine...");
            // Start polling to get the "Green Beep" effect
            this.startPolling();
        } catch (e) {
            alert("Error: Server did not accept Bot Game.");
        }

        const engineUrl = 'https://cdnjs.cloudflare.com/ajax/libs/stockfish.js/10.0.0/stockfish.js';
        this.botEngine = new UCIEngine(engineUrl);

        this.botEngine.worker.addEventListener('message', (e) => {
            if (e.data === 'readyok') {
                this.updateStatus("Playing vs Stockfish");
            }
        });

        this.botEngine.onBestMove = (moveString) => {
            document.getElementById('loading-spinner').style.display = 'none';
            const move = ChessUtils.parseMove(moveString);
            const from = this.getSquareNotation(move.from.r, move.from.c);
            const to = this.getSquareNotation(move.to.r, move.to.c);
            console.log(`Bot moving: ${from} -> ${to}`);
            this.makeMove(from, to);
        };
    }

    triggerBot() {
        if (this.gameMode !== 'bot') return;
        const spinner = document.getElementById('loading-spinner');
        if(spinner) spinner.style.display = 'block';

        const fen = ChessUtils.boardToFen(this.currentBoard);
        setTimeout(() => this.botEngine.startThinking(fen, 1), 500);
    }

    // --- MODE 2: ONLINE ---
    async initOnlineMode() {
        this.updateStatus("Connecting...");
        try {
            const data = await ChessEngineAPI.newGame();
            if (data.status === "waiting for player") {
                this.updateStatus("Waiting for opponent...");
                this.lobbyInterval = setInterval(async () => {
                    try {
                        const check = await ChessEngineAPI.newGame();
                        if (check.status === "match_started") {
                            clearInterval(this.lobbyInterval);
                            this.startOnlineMatch(check);
                        }
                    } catch(e) {}
                }, 2000);
            } else {
                this.startOnlineMatch(data);
            }
        } catch (e) { this.updateStatus("Offline"); }
    }

    startOnlineMatch(data) {
        if (data.white === this.currentUsername) this.myColor = 'white';
        else if (data.black === this.currentUsername) this.myColor = 'black';
        else this.myColor = 'spectator';
        this.updateStatus(`You are ${this.myColor}`);
        this.startPolling();
    }

    // --- SHARED: POLLING (The Heartbeat) ---
    startPolling() {
        const dot = document.getElementById('sync-status');

        this.pollingInterval = setInterval(async () => {
            try {
                // This keeps the board synced AND checks connection
                const state = await ChessEngineAPI.getGameState();

                if (state) {
                    // 1. FLASH GREEN (The Beep)
                    if(dot) {
                        dot.style.backgroundColor = '#0f0'; // Bright Green
                        setTimeout(() => dot.style.backgroundColor = 'gray', 200); // Back to dim
                    }

                    if (state.newBoard) this.updateBoardState(state.newBoard);

                    if (state.turn) {
                        const sTurn = state.turn === 'w' ? 'white' : 'black';
                        if (this.currentPlayer !== sTurn) {
                            this.currentPlayer = sTurn;
                            this.updatePlayerTurn();
                        }
                    }

                    // CHECK GAME OVER
                    if (state.status && (state.status.includes("wins") || state.status.includes("Checkmate") || state.status.includes("Game Over"))) {
                        this.showGameOverModal(state.status);
                        clearInterval(this.pollingInterval);
                    }
                } else {
                    // Connection lost?
                    if(dot) dot.style.backgroundColor = 'red';
                }
            } catch (e) {
                // Server down?
                if(dot) dot.style.backgroundColor = 'red';
            }
        }, 1000); // Poll every 1 second
    }

    // --- ACTIONS ---
    async handleSquareClick(row, col) {
        if (this.gameMode === 'online') {
            if (this.myColor !== 'spectator' && this.myColor !== this.currentPlayer) return;
        } else if (this.gameMode === 'bot') {
            if (this.currentPlayer !== 'white') return;
        }

        const pos = this.getSquareNotation(row, col);
        const piece = this.currentBoard[row][col];

        if (this.selectedSquare) {
            const [selRow, selCol] = this.selectedSquare;
            const sourcePos = this.getSquareNotation(selRow, selCol);

            if (this.legalMoves.includes(pos)) {
                await this.makeMove(sourcePos, pos);
                this.clearSelection();
                return;
            }
            if (selRow === row && selCol === col) { this.clearSelection(); return; }
        }

        if (piece && this.isOwnPiece(piece)) {
            this.clearSelection();
            await this.selectSquare(row, col, piece);
        }
    }

    async selectSquare(row, col, piece) {
        this.selectedSquare = [row, col];
        const sq = document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
        if(sq) sq.classList.add('selected');

        try {
            const moves = await ChessEngineAPI.getLegalMoves(this.getSquareNotation(row, col), piece);
            this.legalMoves = moves || [];
            this.highlightLegalMoves();
        } catch (e) {}
    }

    async makeMove(from, to) {
        try {
            const result = await ChessEngineAPI.makeMove(from, to);
            if (result.success) {
                this.updateBoardState(result.newBoard);
                this.addToHistory(from, to);
                this.currentPlayer = (this.currentPlayer === 'white') ? 'black' : 'white';
                this.updatePlayerTurn();

                if (this.gameMode === 'bot' && this.currentPlayer === 'black') {
                    this.triggerBot();
                }
            }
        } catch (e) { console.error(e); }
    }

    // --- LOGOUT & RESIGN ---

    async logout() {
        // 1. Call API to clear server session
        await ChessEngineAPI.logout();
        // 2. Redirect to Login
        window.location.href = 'loginPage.html';
    }

    async resign() {
        if (!confirm("Are you sure you want to resign?")) return;

        try {
            await ChessEngineAPI.resignGame();
            // In Bot Mode, we manually trigger the Modal because the server response
            // might not reach the poller fast enough before we navigate away.
            if (this.gameMode === 'bot') {
                this.showGameOverModal("Game Over: Black wins! (White resigned)");
                clearInterval(this.pollingInterval);
            }
        } catch (e) { console.error(e); }
    }

    // --- SETUP & HELPERS ---

    setupGlobalListeners() {
        const bind = (id, fn) => { const el = document.getElementById(id); if (el) el.onclick = fn; };
        bind('home', () => window.location.href = 'home.html');
        bind('resign', () => this.resign());
        bind('logout-btn', () => this.logout()); // Bind Logout
    }

    initHomePage() {
        const btn = document.getElementById('menu-new-game');
        if (btn) btn.onclick = async () => {
            btn.textContent = "Joining...";
            try { await ChessEngineAPI.newGame(); window.location.href = 'game.html'; }
            catch (e) { alert("Server Error"); }
        };
        const btnBot = document.getElementById('menu-play-bot');
        if (btnBot) btnBot.onclick = () => window.location.href = 'game.html?mode=bot';

        // Home page logout
        const logoutBtn = document.getElementById('logout-btn');
        if (logoutBtn) logoutBtn.onclick = () => this.logout();
    }

    async fetchAndSetUsername() {
        try {
            const u = await ChessEngineAPI.getLoggedInUser();
            this.currentUsername = u || "Guest";
            const el = document.querySelector(".username");
            if(el) el.innerText = this.currentUsername;
        } catch(e){}
    }

    addToHistory(from, to) {
        const list = document.getElementById('move-list');
        if (list) {
            const div = document.createElement('div');
            div.textContent = `${from} -> ${to}`;
            list.appendChild(div);
            list.scrollTop = list.scrollHeight;
        }
    }

    showGameOverModal(msg) {
        const m = document.getElementById('game-over-modal');
        if (m) { document.getElementById('modal-message').textContent = msg; m.style.display = 'block'; }
    }

    updateStatus(msg) {
        const el = document.getElementById('game-status');
        if(el) el.textContent = msg;
    }

    updateBoardState(b) {
        this.currentBoard = b;
        this.updatePieces();
    }

    updatePieces() {
        const squares = document.getElementsByClassName('square');

        for (let sq of squares) {
            const r = parseInt(sq.dataset.row);
            const c = parseInt(sq.dataset.col);
            const p = this.currentBoard[r] ? this.currentBoard[r][c] : '';
            sq.textContent = p ? this.getPieceSymbol(p) : '';
            sq.style.color = (p === p.toUpperCase()) ? 'white' : 'black';
        }
    }
    highlightLegalMoves() {
        this.legalMoves.forEach(m => { const [r, c] = this.getRowColFromNotation(m);
            const sq = document.querySelector(`.square[data-row="${r}"][data-col="${c}"]`);
            if(sq) sq.classList.add(this.currentBoard[r][c] ? 'legal-capture' : 'legal-move');
        });
    }

    clearSelection() {
        if(this.selectedSquare) {
            const [r, c] = this.selectedSquare;
            const sq = document.querySelector(`.square[data-row="${r}"][data-col="${c}"]`);
            if(sq) sq.classList.remove('selected');
        }
        this.selectedSquare = null; this.legalMoves = [];
        document.querySelectorAll('.square').forEach(el => el.classList.remove('legal-move', 'legal-capture'));
    }

    isOwnPiece(p) {
        if (!p) return false;
        const w = p === p.toUpperCase();
        return (this.currentPlayer === 'white' && w) || (this.currentPlayer === 'black' && !w);
    }

    getPieceSymbol(p) {
        const s = { 'k':'♔', 'q':'♕', 'r':'♖', 'b':'♗', 'n':'♘', 'p':'♙', 'K':'♚', 'Q':'♛', 'R':'♜', 'B':'♝', 'N':'♞', 'P':'♟' };
        return s[p] || '';
    }

    updatePlayerTurn() {
        const el = document.getElementById('player-turn');
        if(el) el.textContent = `${this.currentPlayer.toUpperCase()}'s Turn`;
    }

    getInitialBoard() {
        return [['r','n','b','q','k','b','n','r'],
            ['p','p','p','p','p','p','p','p'],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['','','','','','','',''],
            ['P','P','P','P','P','P','P','P'],
            ['R','N','B','Q','K','B','N','R']];
    }

    getSquareNotation(r, c) {
        return 'abcdefgh'[c] + (8 - r);
    }

    getRowColFromNotation(n) {
        return [8 - parseInt(n[1]), 'abcdefgh'.indexOf(n[0])];
    }

    initializeBoard() {
        this.boardElement.innerHTML = '';
        for (let r = 0; r < 8; r++) {
            for (let c = 0; c < 8; c++) { const sq = document.createElement('div');
                sq.className = `square ${(r+c)%2===0?'light':'dark'}`;
                sq.dataset.row = r; sq.dataset.col = c;
                sq.onclick = () => this.handleSquareClick(r, c);
                this.boardElement.appendChild(sq);
            }
        }
        this.updatePieces();
    }
}

document.addEventListener('DOMContentLoaded', () => { window.chessApp = new ChessUI(); });