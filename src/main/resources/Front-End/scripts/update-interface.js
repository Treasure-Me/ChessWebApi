class UpdateInterface {
    static socket = null;
    static flashInterval = null;
    static boardStates = [];

    static startWebSocket(activeGameId) {
        const dot = document.getElementById('sync-status');
        this.startContinuousFlash(dot);
        this.socket = new WebSocket(`ws://localhost:5000/updates?gameId=${activeGameId}`);

        this.socket.onopen = () => {
            console.log("WebSocket connected");
            this.flashGreen(dot);
        };

        this.socket.onmessage = (event) => {
            try {
                const state = JSON.parse(event.data);
                console.log(state);
                this.flashGreen(dot);

                if (state.newBoard) {
                    window.chessApp.updateBoardState(state.newBoard);
                    console.log(state.newBoard);
                }

                if (state.turn) {
                    const sTurn = state.turn === 'w' ? 'white' : 'black';
                    if (window.chessApp.currentPlayer !== sTurn) {
                        window.chessApp.currentPlayer = sTurn;
                        window.chessApp.updatePlayerTurn();
                    }
                }
                if (state.status) {
                    if (state.status.includes("wins") || state.status.includes("Checkmate") || state.status.includes("Game Over")) {
                        window.chessApp.showGameOverModal(state.status);
                    }
                    if (state.status.includes("promoting")) {
                        state.status.includes("black")
                            ? window.chessApp.showBlackPromotionModal(state.status)
                            : window.chessApp.showWhitePromotionModal(state.status);
                    }
                }
            } catch (e) {
                console.error("WebSocket parse error:", e);
            }
        };

        this.socket.onerror = () => {
            if (dot) dot.style.backgroundColor = 'red';
            console.error("WebSocket error");
        };

        this.socket.onclose = () => {
            console.log("WebSocket closed. Reconnecting...");
            this.stopContinuousFlash();
            if (dot) dot.style.backgroundColor = 'red';
            setTimeout(() => UpdateInterface.startWebSocket(activeGameId), 3000);
        };
    }

    static startContinuousFlash(dot) {
        this.stopContinuousFlash();

        this.flashInterval = setInterval(() => {
            if (dot && this.socket && this.socket.readyState === WebSocket.OPEN) {
                this.flashGreen(dot);
            }
        }, 1000);
    }

    static stopContinuousFlash() {
        if (this.flashInterval) {
            clearInterval(this.flashInterval);
            this.flashInterval = null;
        }
    }

    static flashGreen(dot) {
        if (!dot) return;
        dot.style.backgroundColor = '#0f0';
        setTimeout(() => {
            if (dot) dot.style.backgroundColor = 'gray';
        }, 200);
    }
}