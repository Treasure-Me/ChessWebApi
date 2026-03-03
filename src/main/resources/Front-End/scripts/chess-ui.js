class ChessUI {
    constructor() {
        this.boardElement = document.getElementById('chess-board');
        this.currentPage = window.location.pathname.includes('game.html') ? 'game' : 'home';

        const urlParams = new URLSearchParams(window.location.search);
        const modeParam = urlParams.get('mode');
        this.gameMode = modeParam === 'bot' ? 'bot' : (modeParam === 'analysis' ? 'analysis' : 'online');

        this.selectedSquare = null;
        this.legalMoves = [];
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.myColor = 'white';
        this.currentUsername = "Guest";
        this.pollingInterval = null;
        this.lobbyInterval = null;
        this.botEngine = null;
        this.engineReady = false;
        this.engineLoading = true;

        this.gameLogic = null;
        this.isBoardFlipped = false;

        this.init();
    }

    async init() {
        this.setupGlobalListeners();
        if (this.boardElement) this.initializeBoard();

        if (this.currentPage === 'home') {
            await this.fetchAndSetUsername();
        }

        if (this.currentPage === 'game') {
            await this.fetchAndSetUsername();

            if (this.gameMode === 'bot') {
                await this.initBotMode();
            } else if (this.gameMode === 'analysis') {
                await this.initAnalysisMode();
            } else {
                await this.initOnlineMode();
            }
        } else {
            this.initHomePage();
        }
    }

    async initAnalysisMode() {
        console.log("Initializing Analysis Mode...");
        this.updateStatus("Analysis Mode");

        if (typeof Chess !== 'undefined') {
            this.gameLogic = new Chess();
        } else {
            alert("Error: chess.js library not loaded! Analysis disabled.");
            return;
        }

        document.getElementById('eval-bar').style.display = 'block';
        const resignBtn = document.getElementById('resign');
        if(resignBtn) resignBtn.style.display = 'none';

        const storedFen = sessionStorage.getItem('analysis_fen');
        if (storedFen) {
            const success = this.gameLogic.load(storedFen);
            if (!success) alert("Invalid FEN string. Loaded default.");
        }

        this.syncBoardFromFen(this.gameLogic.fen());
        this.currentPlayer = this.gameLogic.turn() === 'w' ? 'white' : 'black';
        this.updatePlayerTurn();

        const engineUrl = 'https://cdnjs.cloudflare.com/ajax/libs/stockfish.js/10.0.0/stockfish.js';
        this.botEngine = new UCIEngine(engineUrl);

        this.botEngine.worker.addEventListener('message', (e) => {
            if (e.data === 'readyok') {
                console.log("Engine Ready");
                this.analyzePosition();
            }
        });

        this.botEngine.onEvaluation = (score, isMate) => {
            this.updateEvalBar(score, isMate);
        };
    }

    async initBotMode() {
        console.log("Initializing Bot Mode...");
        this.updateStatus("Syncing...");

        if (typeof Chess !== 'undefined') {
            this.gameLogic = new Chess();
        }

        document.getElementById('opp-name').innerHTML = "Stockfish";

        try {
            const existingState = await ChessEngineAPI.getGameState();

            if (existingState && existingState.newBoard) {
                console.log("Resuming existing bot game...");

                this.updateBoardState(existingState.newBoard);
                this.gameLogic.load(existingState.fen);

                if (existingState.turn) {
                    this.currentPlayer = existingState.turn === 'w' ? 'white' : 'black';
                    this.updatePlayerTurn();

                    if (this.currentPlayer === 'black'){
                        UpdateInterface.startWebSocket();
                        this.setUpBot();
                        return;
                    }
                }

                this.updateStatus("Resumed vs Stockfish");
            }
            else {
                console.log("Creating new bot game...");
                const gameData = await ChessEngineAPI.newGame(true);
                console.log("Server Game Created:", gameData);
                this.updateStatus("New Game vs Stockfish");
            }
            UpdateInterface.startWebSocket();

        } catch (e) {
            alert("Error: Server did not accept Bot Game.");
        }

        this.setUpBot();

    }

    setUpBot(){
        const engineUrl = 'https://cdnjs.cloudflare.com/ajax/libs/stockfish.js/10.0.0/stockfish.js';
        this.botEngine = new UCIEngine(engineUrl);

        this.botEngine.worker.addEventListener('message', (e) => {
            if (e.data === 'readyok') {
                console.log("Stockfish Ready");
                this.engineReady = true;
                this.engineLoading = false;
                this.updateStatus("Playing vs Stockfish");

                if (this.currentPlayer === 'black') {
                    console.log("Resuming bot thinking...");
                    this.triggerBot();
                }
            }
        });

        this.botEngine.onBestMove = (moveString) => {
            document.getElementById('loading-spinner').style.display = 'none';
            const move = ChessUtils.parseMove(moveString);
            const from = this.getSquareNotation(move.from.r, move.from.c);
            const to = this.getSquareNotation(move.to.r, move.to.c);

            let promotionChar = null;
            if (moveString.length > 4) {
                console.log(moveString);
                promotionChar = moveString.charAt(4) + '';
                console.log(promotionChar);
            }
            console.log(from+":"+to);
            this.makeMove(from, to, promotionChar, null);
        };
    }

    triggerBot() {
        if (this.gameMode !== 'bot') return;

        if (!this.engineReady) {
            console.log("Engine not ready yet...");
            return;
        }

        const spinner = document.getElementById('loading-spinner');
        if (spinner) spinner.style.display = 'block';

        const fen = ChessUtils.boardToFen(this.currentBoard);

        setTimeout(() => this.botEngine.startThinking(fen, 18, 5000), 200);
    }


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
        let opponentName = "Opponent";

        if (data.white === this.currentUsername) {
            this.myColor = 'white';
            this.isBoardFlipped = false;
            opponentName = data.black || "Opponent";
        } 
        else if (data.black === this.currentUsername) {
            this.myColor = 'black';
            this.isBoardFlipped = true;
            opponentName = data.white || "Opponent";
        } 
        else {
            this.myColor = 'spectator';
            this.isBoardFlipped = false;
        }

        this.updateStatus(`You are ${this.myColor}`);

        const selfEl = document.getElementById('self-name');
        const oppEl = document.getElementById('opp-name');
        
        if (selfEl) selfEl.innerText = this.currentUsername;
        if (oppEl) oppEl.innerText = opponentName;

        this.initializeBoard();
        UpdateInterface.startWebSocket();
    }

    async handleSquareClick(row, col) {
        if (this.gameMode === 'analysis') {
        } else if (this.gameMode === 'online') {
            if (this.myColor !== 'spectator' && this.myColor !== this.currentPlayer) return;
        } else if (this.gameMode === 'bot') {
            if (!this.engineReady) return;
            if (this.currentPlayer !== 'white') return;
        }


        const pos = this.getSquareNotation(row, col);
        const piece = this.currentBoard[row][col];

        if (this.selectedSquare) {
            const [selRow, selCol] = this.selectedSquare;
            const sourcePos = this.getSquareNotation(selRow, selCol);
            const toSquare = this.getSquareNotation(row, col);

            const piece = this.currentBoard[selRow][selCol];
            
            const isWhitePawn = piece === 'P';
            const isBlackPawn = piece === 'p';

            const targetRow = row;

            if (isWhitePawn && targetRow === 0) {
                this.pendingPromotionMove = { from: sourcePos, to: toSquare };
                document.getElementById('white-promotion-modal').style.display = 'block';
                this.clearSelection();
                return;
            }

            if (isBlackPawn && targetRow === 7) {
                this.pendingPromotionMove = { from: sourcePos, to: toSquare };
                document.getElementById('black-promotion-modal').style.display = 'block';
                this.clearSelection();
                return;
            }

            let isLegal = false;
            if (this.gameMode === 'analysis' && this.gameLogic) {
                const moves = this.gameLogic.moves({ square: sourcePos, verbose: true });
                isLegal = moves.some(m => m.to === pos);
            } else {
                isLegal = this.legalMoves.includes(pos);
            }

            if (isLegal) {
                await this.makeMove(sourcePos, toSquare, null, this.currentUsername);
                this.clearSelection();
                return;
            }
            if (selRow === row && selCol === col) { this.clearSelection(); return; }
            if (selRow !== row || selCol !== col && (!piece || !this.isOwnPiece(piece))) {
                this.clearSelection();
                return;
            }
        }

        if (piece) {
            if (this.gameMode === 'analysis' || this.isOwnPiece(piece)) {
                this.clearSelection();
                await this.selectSquare(row, col, piece);
            }
        }
    }

    async selectSquare(row, col, piece) {
        this.selectedSquare = [row, col];
        const sq = document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
        if(sq) sq.classList.add('selected');

        if (this.gameMode === 'analysis' && this.gameLogic) {
            const pos = this.getSquareNotation(row, col);
            const moves = this.gameLogic.moves({ square: pos, verbose: true });
            this.legalMoves = moves.map(m => m.to);
            this.highlightLegalMoves();
        } else {
            try {
                const moves = await ChessEngineAPI.getLegalMoves(this.getSquareNotation(row, col), piece);
                this.legalMoves = moves || [];
                this.highlightLegalMoves();
            } catch (e) {}
        }
    }

    getPieceFromMove(from){
        const rowCol = this.getRowColFromNotation(from);
        return this.currentBoard[rowCol[0]][rowCol[1]];
    }

    async makeMove(from, to, promotionalPiece, playerUsername) {
        if (this.gameMode === 'analysis' && this.gameLogic) {
            const move = this.gameLogic.move({ from: from, to: to, promotion: promotionalPiece });
            if (move) {
                this.syncBoardFromFen(this.gameLogic.fen());
                this.addToHistory(from, to);
                this.currentPlayer = this.gameLogic.turn() === 'w' ? 'white' : 'black';
                this.updatePlayerTurn();
                this.analyzePosition();
            }
        } else {
            try {
                const result = await ChessEngineAPI.makeMove(from, to, promotionalPiece, playerUsername);
                if (result.success) {
                    this.updateBoardState(result.newBoard);
                    this.addToHistory(from, to);

                    if (this.gameMode === 'bot' && this.gameLogic) {
                        this.gameLogic.move({ 
                            from: from, 
                            to: to, 
                            promotion: promotionalPiece
                        });
                    }

                    this.currentPlayer = (this.currentPlayer === 'white') ? 'black' : 'white';
                    this.updatePlayerTurn();

                    if (this.gameMode === 'bot' && this.currentPlayer === 'black') {
                        this.triggerBot();
                    }
                }
            } catch (e) { console.error(e); }
        }
    }

    async commitPromotion(pieceType) {
    
        if (this.gameMode === 'bot' && this.currentPlayer === 'black'){
            var playerUsername = null
        }else{
            playerUsername = this.currentUsername
        }

        document.getElementById('white-promotion-modal').style.display = 'none';
        document.getElementById('black-promotion-modal').style.display = 'none';

        if (this.pendingPromotionMove) {
            const { from, to } = this.pendingPromotionMove;
            
            await this.makeMove(from, to, pieceType, playerUsername);
            
            this.pendingPromotionMove = null;
        }
    }

    analyzePosition() {
        if (this.botEngine && this.gameLogic) {
            this.botEngine.startThinking(this.gameLogic.fen(), 32, 10000);
        }
    }

    updateEvalBar(score, isMate) {
        const barFill = document.getElementById('eval-fill');
        const text = document.getElementById('eval-text');
        if (!barFill || !text || !this.gameLogic) return;

        let percentage = 50;
        let displayScore = "0.0";

        if (isMate) {
            if (score > 0) { percentage = 100; displayScore = `M${score}`; }
            else { percentage = 0; displayScore = `M${Math.abs(score)}`; }
        } else {
            let whiteScore = this.gameLogic.turn() === 'w' ? score : -score;
            displayScore = (whiteScore / 100).toFixed(1);
            if (whiteScore > 0) displayScore = "+" + displayScore;
            const winningChance = 1 / (1 + Math.pow(10, -whiteScore / 400));
            percentage = winningChance * 100;
        }

        barFill.style.height = `${percentage}%`;
        text.innerText = displayScore;

        text.style.color = percentage > 50 ? '#000' : '#fff';

        text.style.textShadow = percentage > 50 ? '0 0 3px rgba(255,255,255,0.8)' : '0 0 3px rgba(0,0,0,0.6)';

        text.style.top = percentage > 90 ? 'auto' : '5px';
        text.style.bottom = percentage > 90 ? '5px' : 'auto';
    }

    syncBoardFromFen(fen) {
        const rows = fen.split(' ')[0].split('/');
        const newBoard = [];
        for (let r = 0; r < 8; r++) {
            const rowArr = [];
            for (let char of rows[r]) {
                if (isNaN(char)) rowArr.push(char);
                else for (let k = 0; k < parseInt(char); k++) rowArr.push('');
            }
            newBoard.push(rowArr);
        }
        this.updateBoardState(newBoard);
    }

    async logout() { await ChessEngineAPI.logout(); window.location.href = 'loginPage.html'; }
    async resign() {
        if (!confirm("Are you sure you want to resign?")) return;
        try {
            await ChessEngineAPI.resignGame();
            if (this.gameMode === 'bot') {
                this.showGameOverModal("Game Over: Black wins! (White resigned)");
                clearInterval(this.pollingInterval);
            }
        } catch (e) { console.error(e); }
    }

    setupGlobalListeners() {
        const bind = (id, fn) => { const el = document.getElementById(id); if (el) el.onclick = fn; };
        bind('home', () => window.location.href = 'home.html');
        bind('resign', () => this.resign());
        bind('logout-btn', () => this.logout());
        bind('modal-new-game', () => location.reload());
    }

    initHomePage() {
        const bind = (id, fn) => { const el = document.getElementById(id); if (el) el.onclick = fn; };

        bind('menu-new-game', async () => {
            const btn = document.getElementById('menu-new-game');
            btn.textContent = "Joining...";
            try { await ChessEngineAPI.newGame(); window.location.href = 'game.html'; }
            catch (e) { alert("Server Error"); }
        });

        bind('menu-play-bot', () => window.location.href = 'game.html?mode=bot');
        bind('logout-btn', () => this.logout());
    }

    async fetchAndSetUsername() { 
        try { 
            const u = await ChessEngineAPI.getLoggedInUser(); 
            this.currentUsername = u || "Guest"; 

            const el = document.getElementById("self-name");
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

    showBlackPromotionModal(msg){
        const m = document.getElementById('black-promotion-modal');
        if (m) { document.getElementById('modal-message').textContent = msg; m.style.display = 'block'; }
    }

    showWhitePromotionModal(msg){
        const m = document.getElementById('white-promotion-modal');
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
        if(this.selectedSquare) { const [r, c] = this.selectedSquare; 
            const sq = document.querySelector(`.square[data-row="${r}"][data-col="${c}"]`); 
            if(sq) sq.classList.remove('selected'); 
        } 
        this.selectedSquare = null; 
        this.legalMoves = []; 
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
        for (let visualRow = 0; visualRow < 8; visualRow++) {
            for (let visualCol = 0; visualCol < 8; visualCol++) {
                
                let row, col;

                if (this.isBoardFlipped) {
                    row = 7 - visualRow; 
                    col = 7 - visualCol; 
                } else {
                    row = visualRow;
                    col = visualCol;
                }

                const sq = document.createElement('div');
                
                sq.className = `square ${(visualRow + visualCol) % 2 === 0 ? 'light' : 'dark'}`;
                
                sq.dataset.row = row;
                sq.dataset.col = col;
                
                sq.onclick = () => this.handleSquareClick(row, col);
                this.boardElement.appendChild(sq);
            }
        }
        this.updatePieces();
    }
}

document.addEventListener('DOMContentLoaded', () => { window.chessApp = new ChessUI(); });