package API.utility;

import io.javalin.websocket.WsContext;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketBroadcaster {

    private static final ConcurrentHashMap<String, WsContext> sessions = new ConcurrentHashMap<>();

    public static void addSession(String gameId, WsContext ctx) {
        sessions.put(gameId, ctx);
    }

    public static void broadcastGameUpdate(String gameId, Object state) {
        WsContext ctx = sessions.get(gameId);
        if (ctx != null && ctx.session.isOpen()) {
            ctx.send(state);
        }
    }

    public static void removeSession(String gameId) {
        sessions.remove(gameId);
    }
}