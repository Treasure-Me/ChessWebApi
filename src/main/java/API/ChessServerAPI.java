package API;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import API.utility.WebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import API.utility.Player;
import API.utility.PlayerDaoImplementation;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class ChessServerAPI {
    private final Javalin server;
    static final Logger logger = LoggerFactory.getLogger(ChessServerAPI.class);

    public ChessServerAPI() {
        ServiceRegistry.configure(PlayerDaoImplementation.class, new PlayerDaoImplementation());

        server = Javalin.create(config -> {
                    config.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = "/";
                        staticFiles.directory = "Front-End";
                        staticFiles.location = Location.CLASSPATH;
                        staticFiles.headers = Map.of("Cache-Control", "public, max-age=31536000");
                    });
                })
                .before(ctx -> {
                    if (ctx.contentType() == null) {
                        ctx.contentType("application/json");
                    }
                })
                .after(ctx -> {
                    logger.info("Request: {} {}", ctx.method(), ctx.url());
                });

        this.server.post("/api/new-game", ChessApiHandler::newMatch);
        this.server.post("/api/login", ChessApiHandler::login);
        this.server.post("/api/logout", ChessApiHandler::logout);
        this.server.post("/api/create-account", ChessApiHandler::createAccount);
        this.server.post("/api/login/guest", ChessApiHandler::loginGuest);
        this.server.get("/api/user", ChessApiHandler::getPlayerLoggedIn);
        this.server.get("/", ChessApiHandler::loadLoginPage);
        this.server.post("/api/resign", ChessApiHandler::resignMatch);
        this.server.post("/api/game/{gameId}/{action}", ChessApiHandler::handleGameAction);
        this.server.get("/api/game/board-states", ChessApiHandler::getBoardStates);
        this.server.get("/api/game/{gameId}/state", context -> {
            String gId = context.pathParam("gameId");
            chessMatchHandler g = GameManager.getGame(gId);
            if (g != null){
                context.json(g.getGameState());
            }
        });
        this.server.get("/api/health", ctx -> {
            ctx.json(Map.of(
                "status", "online",
                "players", ChessApiHandler.playersLoggedIn.size(),
                "timestamp", System.currentTimeMillis()
            ));
        });
        this.server.ws("/updates", ws -> {
            ws.onConnect(ctx -> {
                ctx.session.setIdleTimeout(Duration.ofHours(1));
                String gameId = ctx.queryParam("gameId");
                if (gameId == null || gameId.isBlank()) {
                    System.err.println("WebSocket connect rejected: missing gameId");
                    ctx.send("{\"error\":\"missing gameId\"}");
                    ctx.session.close();
                    return;
                } else {
                    ctx.sessionAttribute("gameId");
                    WebSocketBroadcaster.addSession(gameId, ctx);
                }

                System.out.println("WebSocket client connected for gameId=" + gameId);
            });

            ws.onMessage(ctx -> {
                if (ctx.message().contains("\"ping\"") || ctx.message().contains("ping")) {
                    return;
                }
                System.out.println("Received from client: " + ctx.message());
            });

            ws.onClose(ctx -> {

                String gameId = ctx.sessionAttribute("gameId");
                if (gameId != null) {
                    WebSocketBroadcaster.removeSession(gameId, ctx);
                    System.out.println("Cleaned up dead WebSocket for gameId=" + gameId);
                }
            });
        });
    }

    private static void seedDemoData() {
        Player player1 = new Player("samkelisozulu@mail.com", "Mageba", "abcdef");
        Player player2 = new Player("lukhoziarthur@mail.com", "Arturito", "123456");
        Player player3 = new Player("makamogugulakhe@mail.com", "optimusPrime", "IIIIIIIVVVI");
        Player player4 = new Player("donavanmakgokga@mail.com", "Don", "Nkunzebomvu");
        Player player5 = new Player("test1@mail.com", "Prototype1", "123456");
        Player player6 = new Player("test2@mail.com", "Prototype2", "123456");

        Stream.of(player1, player2, player3, player4, player5, player6).forEach(PlayerDaoImplementation::savePlayer);
    }

    public static void main(String[] args) {
        ChessServerAPI server = new ChessServerAPI();
        seedDemoData();

        String portStr = System.getenv("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 5000;

        System.out.println("Starting server on port: " + port);
        server.start(port);
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}