package API.utility;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class PlayerDaoImplementation {

    private static Set<Player> setOfPlayers = Set.of();

    public PlayerDaoImplementation() {
        setOfPlayers = new HashSet<>();
    }

    public PlayerDaoImplementation(Collection<Player> players) {
        setOfPlayers = new HashSet<>();
    }

    public static Optional<Player> findPlayerByEmail(String email) {
        return setOfPlayers.stream().filter(p -> p.getEmail().equals(email)).findFirst();
    }

    public static Optional<Player> findPlayerByUsername(String username) {
        return setOfPlayers.stream().filter(p -> p.getUsername().equals(username)).findFirst();
    }

    public static Player savePlayer(Player player) {
        if (findPlayerByEmail(player.getEmail()).isEmpty()) setOfPlayers.add(player);
        return player;
    }

}
