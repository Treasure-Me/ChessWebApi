package API;

import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import io.javalin.Javalin;
import io.javalin.http.*;
import io.javalin.http.staticfiles.Location;
import logic.Board;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

public class ChessServerAPI {
    private final Javalin server;
    static final Logger logger = LoggerFactory.getLogger(ChessServerAPI.class);
    private static chessMatchHandler chessMatchHandler;

    public ChessServerAPI() {
        ServiceRegistry.configure(PlayerDaoImplementation.class, new PlayerDaoImplementation());
        server = Javalin.create(config -> {
                    config.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = "/";              // URL path prefix
                        staticFiles.directory = "Front-End";         // folder inside classpath
                        staticFiles.location = Location.CLASSPATH; // CLASSPATH or EXTERNAL
                    });

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
        Board board = new Board();
        this.server.post("/api/new-game", context -> ChessApiHandler.newMatch(context));
        this.server.post("api/login", context -> ChessApiHandler.login(context));
        this.server.post("api/login/guest", context -> ChessApiHandler.loginGuest(context));
        this.server.get("/api/user", context -> ChessApiHandler.getPlayerLoggedIn(context));
        this.server.get("/", context -> ChessApiHandler.loadLoginPage(context));
        this.server.get("/api/health", ctx -> {
            ctx.json(Map.of(
                    "status", "online",
                    "players", ChessApiHandler.playersLoggedIn.size(), // You can add actual player count later
                    "timestamp", System.currentTimeMillis()
            ));
        });
    }

    private static void seedDemoData() {
        PlayerDaoImplementation playerDaoImplementation = ServiceRegistry.lookup(PlayerDaoImplementation.class);

        Player player1 = new Player("samkelisozulu@mail.com", "Mageba", "abcdef");
        Player player2 = new Player("lukhoziarthur@mail.com", "Arturito", "123456");
        Player player3 = new Player("makamogugulakhe@mail.com", "optimusPrime", "IIIIIIIVVVI");
        Player player4 = new Player("donavanmakgokga@mail.com", "Don", "Nkunzebomvu");

        Stream.of(player1, player2, player3, player4).forEach(PlayerDaoImplementation::savePlayer);
    }

    public static void main(String[] args) {
        ChessServerAPI server = new ChessServerAPI();
        seedDemoData();
        server.start(5000);
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}
