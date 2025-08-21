package Gson;

import java.io.PrintWriter;

import com.google.gson.Gson;
//import Gson.Values;
// Classe utilizzata per definire la struttura dei messaggi di risposta in seguito all'inserimento di un ordine
public class GsonResponseOrder extends Values{
    public int orderID;

    public void setResponseOrder(int orderID){
        this.orderID = orderID;
    }

    public int getOrderID(){
        return this.orderID;
    }

    // Serializzazione del messaggio di risposta e invio sullo stream out 
    public void sendMessage(Gson gson,PrintWriter out){
        String respMessage = gson.toJson(this);
        out.println(respMessage);
    }

    public String toString() {
        return "{orderID= " + this.orderID + "}";
     }
}