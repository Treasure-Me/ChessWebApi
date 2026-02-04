class UCIEngine {
    constructor(url) {
        const blobContent = `importScripts('${url}');`;
        const blob = new Blob([blobContent], { type: 'application/javascript' });
        const localUrl = URL.createObjectURL(blob);

        this.worker = new Worker(localUrl);
        this.isReady = false;

        this.onBestMove = (move) => console.log("Engine suggests:", move);
        this.onEvaluation = (score, isMate) => {};

        this._initListeners();
        this._initEngine();
    }

    _initListeners() {
        this.worker.onmessage = (event) => {
            const line = event.data;

            if (line === 'readyok') {
                this.isReady = true;
            }
            else if (line.startsWith('info') && line.includes('score')) {
                this._parseEvaluation(line);
            }
            else if (line.startsWith('bestmove')) {
                const parts = line.split(' ');
                if (parts.length > 1) this.onBestMove(parts[1]);
            }
        };
    }

    _parseEvaluation(line) {

        const parts = line.split(' ');
        let scoreIndex = parts.indexOf('score');

        if (scoreIndex !== -1) {
            const type = parts[scoreIndex + 1]; // 'cp' or 'mate'
            const value = parseInt(parts[scoreIndex + 2]);

            if (this.onEvaluation) {
                this.onEvaluation(value, type === 'mate');
            }
        }
    }

    _initEngine() {
        this.worker.postMessage('uci');
        this.worker.postMessage('setoption name Hash value 32');
        this.worker.postMessage('isready');
    }

    startThinking(fen, depth = 15) {
        if (!this.isReady) return;
        this.worker.postMessage(`position fen ${fen}`);
        this.worker.postMessage(`go depth ${depth}`);
    }

    stop() {
        this.worker.postMessage('stop');
    }
}