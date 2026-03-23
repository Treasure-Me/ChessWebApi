package API.utility;

import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketBroadcaster {

    private static final Map<String, Set<WsContext>> sessions = new ConcurrentHashMap<>();

    public static void addSession(String gameId, WsContext ctx) {
        sessions.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(ctx);
    }

    public static void broadcastGameUpdate(String gameId, Object state) {
        Set<WsContext> gameSessions = sessions.get(gameId);
        if (gameSessions != null) {
            for (WsContext ctx : gameSessions) {
                if (ctx.session.isOpen()) {
                    ctx.send(state);
                }
            }
        }
    }

    public static void removeSession(String gameId, WsContext ctx) {
        Set<WsContext> gameSessions = sessions.get(gameId);
        if (gameSessions != null) {
            gameSessions.remove(ctx);
            if (gameSessions.isEmpty()) {
                sessions.remove(gameId); // Clean up empty games
            }
        }
    }
}