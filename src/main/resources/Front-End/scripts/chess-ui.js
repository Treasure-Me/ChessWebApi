class ChessUI {
    constructor() {
        this.boardElement = document.getElementById('chess-board');
        this.selectedSquare = null;
        this.legalMoves = [];
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.moveHistory = [];

        this.initializeBoard();
        this.setupEventListeners();
        this.updateUI();
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

    initializeBoard() {
        this.boardElement.innerHTML = '';

        for (let row = 7; row >= 0; row--) {
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
        const ranks = '12345678';
        return files[col] + ranks[7 - row];
    }

    getPieceSymbol(piece) {
        const symbols = {
            'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
            'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟'
        };
        return symbols[piece] || '';
    }

    updatePieces() {
        const squares = this.boardElement.getElementsByClassName('square');

        for (let square of squares) {
            const row = parseInt(square.dataset.row);
            const col = parseInt(square.dataset.col);
            const piece = this.currentBoard[row][col];

            square.textContent = this.getPieceSymbol(piece);
            square.style.color = piece === piece.toUpperCase() ? 'white' : 'black';
        }
    }

    async handleSquareClick(row, col) {
        const position = this.getSquareNotation(row, col);
        const piece = this.currentBoard[row][col];

        // If a square is already selected
        if (this.selectedSquare) {
            const [selectedRow, selectedCol] = this.selectedSquare;
            const selectedPiece = this.currentBoard[selectedRow][selectedCol];

            // Check if this is a legal move
            const isLegalMove = this.legalMoves.some(move =>
                move.from === this.getSquareNotation(selectedRow, selectedCol) &&
                move.to === position
            );

            if (isLegalMove) {
                await this.makeMove(this.getSquareNotation(selectedRow, selectedCol), position);
                this.clearSelection();
            } else {
                // Select a different piece
                this.selectSquare(row, col, piece);
            }
        } else {
            // Select a piece
            if (piece && this.isOwnPiece(piece)) {
                this.selectSquare(row, col, piece);
            }
        }
    }

    isOwnPiece(piece) {
        if (this.currentPlayer === 'white') {
            return piece === piece.toUpperCase() && piece !== '';
        } else {
            return piece === piece.toLowerCase() && piece !== '';
        }
    }

    async selectSquare(row, col, piece) {
        this.clearSelection();
        this.selectedSquare = [row, col];

        const square = this.getSquareElement(row, col);
        square.classList.add('selected');

        // Get legal moves from engine
        this.legalMoves = await this.getLegalMoves(this.getSquareNotation(row, col), piece);
        this.highlightLegalMoves();
    }

    clearSelection() {
        if (this.selectedSquare) {
            const [row, col] = this.selectedSquare;
            const square = this.getSquareElement(row, col);
            square.classList.remove('selected');
        }

        this.selectedSquare = null;
        this.legalMoves = [];
        this.clearHighlights();
    }

    highlightLegalMoves() {
        this.legalMoves.forEach(move => {
            const [row, col] = this.getRowColFromNotation(move.to);
            const square = this.getSquareElement(row, col);

            if (this.currentBoard[row][col]) {
                square.classList.add('legal-capture');
            } else {
                square.classList.add('legal-move');
            }
        });
    }

    clearHighlights() {
        const squares = this.boardElement.getElementsByClassName('square');
        for (let square of squares) {
            square.classList.remove('legal-move', 'legal-capture', 'check');
        }
    }

    getSquareElement(row, col) {
        return document.querySelector(`.square[data-row="${row}"][data-col="${col}"]`);
    }

    getRowColFromNotation(notation) {
        const files = 'abcdefgh';
        const ranks = '12345678';
        const col = files.indexOf(notation[0]);
        const row = 7 - ranks.indexOf(notation[1]);
        return [row, col];
    }

    async makeMove(from, to) {
        try {
            const result = await ChessEngineAPI.makeMove(from, to);

            if (result.success) {
                // Update board state
                this.updateBoardState(result.newBoard);
                this.currentPlayer = this.currentPlayer === 'white' ? 'black' : 'white';
                this.addMoveToHistory(from, to);
                this.updateUI();

                // Check for game over
                if (result.gameOver) {
                    this.showGameOverModal(result.message);
                }
            } else {
                alert('Invalid move: ' + result.message);
            }
        } catch (error) {
            console.error('Move error:', error);
            alert('Error making move');
        }
    }

    updateBoardState(boardState) {
        // Convert board state from API to our internal representation
        // This depends on your API response format
        this.currentBoard = boardState;
    }

    addMoveToHistory(from, to) {
        const moveNumber = Math.ceil((this.moveHistory.length + 1) / 2);
        const moveNotation = `${from}-${to}`;

        if (this.currentPlayer === 'white') {
            this.moveHistory.push(`${moveNumber}. ${moveNotation}`);
        } else {
            const lastMove = this.moveHistory[this.moveHistory.length - 1];
            this.moveHistory[this.moveHistory.length - 1] = lastMove + ` ${moveNotation}`;
        }

        this.updateMoveHistory();
    }

    updateMoveHistory() {
        const moveList = document.getElementById('move-list');
        moveList.innerHTML = this.moveHistory.map(move =>
            `<div class="move-item">${move}</div>`
        ).join('');
        moveList.scrollTop = moveList.scrollHeight;
    }

    updateUI() {
        this.updatePieces();
        this.updatePlayerTurn();
        this.updateFEN();
    }

    updatePlayerTurn() {
        const turnElement = document.getElementById('player-turn');
        turnElement.textContent = `${this.currentPlayer.charAt(0).toUpperCase() + this.currentPlayer.slice(1)}'s Turn`;
    }

    updateFEN() {
        // Update FEN display - you'll need to get this from your engine
        document.getElementById('fen-input').value = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
    }

    async getLegalMoves(fromSquare, piece) {
        try {
            return await ChessEngineAPI.getLegalMoves(fromSquare, piece);
        } catch (error) {
            console.error('Error getting legal moves:', error);
            return [];
        }
    }

    setupEventListeners() {
        document.getElementById('new-game').addEventListener('click', () => this.newGame());
        document.getElementById('resign').addEventListener('click', () => this.resign());
        document.getElementById('engine-move').addEventListener('click', () => this.makeEngineMove());
        document.getElementById('modal-new-game').addEventListener('click', () => this.newGame());
        document.getElementById('modal-close').addEventListener('click', () => this.hideModal());
    }

    async newGame() {
        this.currentBoard = this.getInitialBoard();
        this.currentPlayer = 'white';
        this.moveHistory = [];
        this.clearSelection();
        this.updateUI();
        this.updateMoveHistory();
        this.hideModal();

        // Reset engine game
        await ChessEngineAPI.newGame();
    }

    resign() {
        const winner = this.currentPlayer === 'white' ? 'Black' : 'White';
        this.showGameOverModal(`${this.currentPlayer.charAt(0).toUpperCase() + this.currentPlayer.slice(1)} resigns. ${winner} wins!`);
    }

    async makeEngineMove() {
        const depth = parseInt(document.getElementById('engine-depth').value);
        const bestMove = await ChessEngineAPI.getBestMove(depth);

        if (bestMove) {
            await this.makeMove(bestMove.from, bestMove.to);
        }
    }

    showGameOverModal(message) {
        document.getElementById('modal-title').textContent = 'Game Over';
        document.getElementById('modal-message').textContent = message;
        document.getElementById('game-over-modal').style.display = 'block';
    }

    hideModal() {
        document.getElementById('game-over-modal').style.display = 'none';
    }
}

// Initialize the UI when the page loads
document.addEventListener('DOMContentLoaded', () => {
    new ChessUI();
});