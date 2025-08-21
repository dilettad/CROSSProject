package OrderBook;
// Classe utilizzata per rappresentare gli oggetti contenuti nella lista degli stopOrders
public class StopValue{
    public String type;
    public int size;
    public String username;
    public int orderID;
    public int stopPrice;

    public StopValue(String type, int size, String username, int orderID, int stopPrice){
        this.type = type;
        this.size = size;
        this.username = username;
        this.orderID = orderID;
        this.stopPrice = stopPrice;
    }

    public String toString(){
        return "StopValue{" +
                "type='" + type + '\'' +
                ", size=" + size +
                ", username='" + username + '\'' +
                ", orderID=" + orderID +
                ", stopPrice=" + stopPrice +
                '}';
    }
}