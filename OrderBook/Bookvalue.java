package OrderBook;

import java.util.concurrent.ConcurrentLinkedQueue;

// Definire oggetto bookvalue che descrive i campi della askMap e della bidMap
public class Bookvalue {
    public int size;
    public int total;
    public ConcurrentLinkedQueue<UserBook> userList;

    public Bookvalue(int size, int total, ConcurrentLinkedQueue<UserBook> userList) {
        this.size = size;
        this.total = total;
        this.userList = userList;
    }

    public String toString() {
        return "Bookvalue{" +
                "size= " + size +
                ", total= " + total +
                ", userList= " + userList +
                '}';
    }
}
