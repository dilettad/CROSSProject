package OrderBook;

//Import per gestione del tempo
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UserBook {
    public int size;
    public String username;
    public int orderID;
    public String date;

    public UserBook(int size, String username, int orderID) {
        this.size = size;
        this.username = username;
        this.orderID = orderID;
        long time = System.currentTimeMillis();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.date = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), java.time.ZoneId.systemDefault())
                .format(formatter);
    }

    public int compareSize(int size) {
        return Integer.compare(this.size, size);
    }

    public boolean equals(UserBook user) {
        return orderID == user.orderID && size == user.size && username.equals(user.username);
    }

    public String toString() {
        return "UserBook{" +
                "size= " + size +
                ", username= '" + username + '\'' +
                ", orderID= " + orderID +
                ", date= '" + date + '\'' +
                '}';
    }
}
