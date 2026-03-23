class ChessEngineAPI {
    static baseURL = window.location.hostname === 'localhost'
        ? 'http://localhost:5000'
        : '';
    static activeGameId = null;

    static async newGame(isBot = false, isAnalysis = false, fen = null) {
        try {

            const url = isBot 
            ? `${this.baseURL}/api/new-game?bot=true` : (isAnalysis 
            ? `${this.baseURL}/api/new-game?analysis=true` : `${this.baseURL}/api/new-game`);

            console.log(url);

            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({fen: fen })
            });
            if (!response.ok) return null;

            const data = await response.json();

            if (data.gameId) {
                this.activeGameId = data.gameId;
                console.log("Joined Game ID:", this.activeGameId);
                UpdateInterface.startWebSocket(this.activeGameId);
            }

            return data;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async makeMove(from, to, promotion, playerUsername) {
        if (!this.activeGameId) return { success: false, message: "No active game" };

        try {
            const response = await fetch(`${this.baseURL}/api/game/${this.activeGameId}/move`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ from: from, to: to, promotion: promotion, playerUsername: playerUsername })
            });
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            return { success: false, message: "Connection Error" };
        }
    }

    static async getLegalMoves(square, piece) {
        if (!this.activeGameId) return [];

        try {
            const response = await fetch(`${this.baseURL}/api/game/${this.activeGameId}/legal-moves`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ square: square, piece: piece })
            });
            if (!response.ok) return [];
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            return [];
        }
    }

    static async getGameState() {
        if (!this.activeGameId) return null;

        try {
            const response = await fetch(`${this.baseURL}/api/game/${this.activeGameId}/state`, {
                method: 'GET'
            });
            if (!response.ok) throw new Error(`State change failed: ${response.status}`);
            return await response.json();
        } catch (error) {
            return null;
        }
    }

    static async getBoardStates() {
        if (!this.activeGameId) return null;

        try {
            const response = await fetch(`${this.baseURL}/api/game/board-states`, {
                method: 'GET'
            });
            if (!response.ok) throw new Error(`State change failed: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async resignGame() {
        if (!this.activeGameId) return;

        try {
            const response = await fetch(`${this.baseURL}/api/game/${this.activeGameId}/resign`, {
                method: 'POST'
            });
            if (!response.ok) throw new Error(`Resign failed: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async getLoggedInUser() {
        try {
            const response = await fetch(`${this.baseURL}/api/user`);
            if (response.ok) {
                const data = await response.json();
                return data.username;
            }
            return "Guest";
        } catch (error) {
            return "Offline";
        }
    }

    static async logout() {
        try {
            await fetch(`${this.baseURL}/api/logout`, { method: 'POST' });
            return true;
        } catch (e) {
            console.error("Logout failed", e);
            return false;
        }
    }

    static async getBestMove(depth) {
        return null;
    }

    static setMatchPort(port) {
        console.log("Ports are deprecated in favor of Game IDs.");
    }


}
document.addEventListener('DOMContentLoaded', () => { window.chessApi = new ChessEngineAPI(); });