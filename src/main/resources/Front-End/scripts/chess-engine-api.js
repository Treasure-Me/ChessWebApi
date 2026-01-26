class ChessEngineAPI {
    static baseURL = 'http://localhost:5000';
    static matchURL = 'http://localhost:5001'; // Default, updates dynamically

    static setMatchPort(port) {
        this.matchURL = `http://localhost:${port}`;
        console.log("Game Server Port set to:", port);
    }

    static async makeMove(from, to) {
        try {
            const response = await fetch(`${this.matchURL}/api/move`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ from: from, to: to })
            });
            if (!response.ok) throw new Error(`Move failed: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async getLegalMoves(fromSquare, piece) {
        try {
            const response = await fetch(`${this.matchURL}/api/legal-moves`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ square: fromSquare, piece: piece })
            });
            if (!response.ok) throw new Error(`Legal moves failed: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            return [];
        }
    }

    // Used for Polling (Syncing state across tabs)
    static async getGameState() {
        try {
            const response = await fetch(`${this.matchURL}/api/load-fen`, {
                method: 'POST'
            });
            if (!response.ok) return null;
            return await response.json();
        } catch (error) {
            return null;
        }
    }

    static async newGame() {
        try {
            const response = await fetch(`${this.baseURL}/api/new-game`, {
                method: 'POST' // Matches Java server.post
            });
            if (!response.ok) throw new Error(`New Game failed: ${response.status}`);
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

    // Placeholder if you implement engine depth later
    static async getBestMove(depth) {
        return null;
    }
}