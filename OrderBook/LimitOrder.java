package OrderBook;

// Classe utilizzata per rappresentare gli oggetti contenuti nella lista degli stopOrders
public class LimitOrder {
    public String type;
    public int size;
    public String username;
    public int orderID;
    public int limitPrice;

    public LimitOrder(String type, int size, String username, int orderID, int limitPrice){
        this.type = type;
        this.size = size;
        this.username = username;
        this.orderID = orderID;
        this.limitPrice = limitPrice;
    }

    public String toString(){
        return "LimitOrder{" +
                "type='" + type + '\'' +
                ", size=" + size +
                ", username='" + username + '\'' +
                ", orderID=" + orderID +
                ", limitPrice=" + limitPrice +
                '}';
    }
}