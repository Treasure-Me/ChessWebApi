class ChessEngineAPI {
    static baseURL = 'http://localhost:5000';
    static matchURL = 'http://localhost:5001';

    static async makeMove(from, to) {
        try {
            const response = await fetch(`${this.matchURL}/api/move`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    from: from,
                    to: to
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

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
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    square: fromSquare,
                    piece: piece
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            return [];
        }
    }

    static async getBestMove(depth) {
        try {
            const response = await fetch(`${this.matchURL}/api/best-move`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    depth: depth
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            return null;
        }
    }

    static async newGame() {
        try {
            const response = await fetch(`${this.baseURL}/api/new-game`, {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async loadFEN(fenString) {
        try {
            const response = await fetch(`${this.matchURL}/api/load-fen`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    fen: fenString
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    }

    static async getLoggedInUser() {
        try {
            // GET request to check if server is alive
            const response = await fetch('/api/user', {
                method: 'GET'
            });

            if (response.ok) {
                const data = await response.json();
                return data.username;
            }
        } catch (error) {
            document.getElementById('serverStatus').textContent = 'Server: Offline';
            return false;
        }
    }
}