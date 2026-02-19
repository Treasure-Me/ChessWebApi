package API.utility;

import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class Player {
    private UUID id;
    private String email;
    private String username;
    private String password;

    public Player(UUID id){
        this.id = id;
        this.username = "Guest_"+id.toString();
        this.email = id+"@guest.chess";
    }

    public Player(String email,String username, String password){
        this.email = email;
        this.username = username;
        this.password = password;
    }

    public UUID getId(){
        return id;
    }

    public String getEmail(){
        return email;
    }

    public String getUsername(){
        return username;
    }

    public String getPassword(){
        return password;
    }

    public void setPassword(String newPassword){
        this.password = newPassword;
    }

    public void setUsername(String username){
        this.username = username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equal(email, player.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("email", email)
                .toString();
    }
}
