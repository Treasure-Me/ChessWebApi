package API;

import io.javalin.Javalin;
import io.javalin.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.Scanner;

public class ChessServerAPI {
    private final Javalin server;
    private static final Logger logger = LoggerFactory.getLogger(ChessServerAPI.class);

    public ChessServerAPI() {
        server = Javalin.create(config -> {
                })
                .before(ctx -> {
                    // Set default content type if not set
                    if (ctx.contentType() == null) {
                        ctx.contentType("application/json");
                    }
                })
                .after(ctx -> {
                    // Log the request method and path
                    logger.info("Request: {} {}", ctx.method(), ctx.url());
                });


//        this.server.get("/games", context -> ChessApiHandler.getAll(context));
//        this.server.get("/games/{id}", context -> ChessApiHandler.getOne(context));
//        this.server.post("/games", context -> ChessApiHandler.create(context));
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
