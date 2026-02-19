package API;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static final Map<String, chessMatchHandler> activeGames = new ConcurrentHashMap<>();

    public static void addGame(String gameId, chessMatchHandler game) {
        activeGames.put(gameId, game);
    }

    public static chessMatchHandler getGame(String gameId) {
        return activeGames.get(gameId);
    }

    public static void removeGame(String gameId) {
        activeGames.remove(gameId);
    }

    public static String findGameIdByPlayer(String username) {
        for (Map.Entry<String, chessMatchHandler> entry : activeGames.entrySet()) {
            if (entry.getValue().hasPlayer(username)) {
                return entry.getKey();
            }
        }
        return null;
    }
}