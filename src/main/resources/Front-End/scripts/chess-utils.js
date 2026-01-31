const ChessUtils = {
    parseMove: (s) => ({
        from: { r: "87654321".indexOf(s[1]), c: "abcdefgh".indexOf(s[0]) },
        to:   { r: "87654321".indexOf(s[3]), c: "abcdefgh".indexOf(s[2]) }
    }),
    boardToFen: (b) => {
        let fen = "";
        for (let r=0; r<8; r++) {
            let e = 0;
            for (let c=0; c<8; c++) {
                if (!b[r][c]) e++;
                else { if(e) fen+=e; e=0; fen+=b[r][c]; }
            }
            if(e) fen+=e;
            if(r<7) fen+="/";
        }
        return fen + " b KQkq - 0 1";
    }
};