package API;

import io.javalin.Javalin;

public class ChessServerAPI {
    private final Javalin server;

    public ChessServerAPI() {
        server = Javalin.create(config -> {
            config.defaultContentType = "application/json";
        });

        this.server.get("/games", context -> ChessApiHandler.getAll(context));
        this.server.get("/games/{id}", context -> ChessApiHandler.getOne(context));
        this.server.post("/games", context -> ChessApiHandler.create(context));
    }

    public static void main(String[] args) {
        ChessServerAPI server = new ChessServerAPI();
        server.start(5000);
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}
