package OrderBook;
import java.time.Instant;

public class Trade {
    public int orderID;
    public String type;
    public String orderType;
    public int size;
    public int price;
    public long timestamp;

    public Trade(int orderID, String type, String orderType, int size, int price) {
        this.orderID = orderID;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = Instant.now().getEpochSecond();
    }

    public int getOrderID() {
        return orderID;
    }
    public String getType() {
        return type;
    }
    public String getOrderType() {
        return orderType;
    }
    public int getSize() {
        return size;
    }
    public int getPrice() {
        return price;
    }
    public long getTimestamp() {
        return timestamp;
    }
}
