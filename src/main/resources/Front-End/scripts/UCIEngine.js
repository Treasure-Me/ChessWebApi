class UCIEngine {
    constructor(url) {

        const blobContent = `importScripts('${url}');`;
        const blob = new Blob([blobContent], { type: 'application/javascript' });
        const localUrl = URL.createObjectURL(blob);

        console.log("Attempting to load engine from:", url);
        this.worker = new Worker(localUrl);
        // -----------------------

        this.isReady = false;
        this.onBestMove = (m) => console.log(m);

        this._initListeners();
        this._initEngine();
    }

    _initListeners() {
        this.worker.onmessage = (event) => {
            const line = event.data;

            // Debugging: See if the engine is alive
            // console.log("Engine:", line);

            if (line === 'readyok') {
                this.isReady = true;
                console.log("Stockfish Engine Ready.");

                // FORCE UPDATE THE UI
                const status = document.getElementById('game-status');
                if(status) status.textContent = "Ready vs Bot";

                const turn = document.getElementById('player-turn');
                if(turn) turn.textContent = "Your Turn (White)";
            }
            else if (line.startsWith('bestmove')) {
                const move = line.split(' ')[1];
                this.onBestMove(move);
            }
        };

        // If your internet is too slow, this will fire
        this.worker.onerror = (err) => {
            console.error("Worker Failed:", err.message);
            alert("Engine failed to download. Internet might be too slow.");
        };
    }

    _initEngine() {
        this.worker.postMessage('uci');
        this.worker.postMessage('setoption name Hash value 16');
        this.worker.postMessage('isready');
    }

    startThinking(fen, level=1) {
        if (!this.isReady) {
            console.log("Engine still downloading...");
            return;
        }
        this.worker.postMessage(`position fen ${fen}`);
        this.worker.postMessage(`go movetime ${level * 500}`);
    }
}