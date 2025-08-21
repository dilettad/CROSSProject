package Eseguibili.Client;

import java.io.BufferedReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import com.google.gson.Gson;
import OrderBook.Trade;

public class UDP implements Runnable {
    public DatagramSocket socket;
    public BufferedReader in;
    public Printer printer;

    public UDP(DatagramSocket socket, BufferedReader in, Printer printer) {
        this.socket = socket;
        this.in = in;
        this.printer = printer;
    }

    public void run(){
        while (true) { 
            try { 
                // Creo il pacchetto in cui viene caricato il messaggio del server
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                
                socket.receive(receivePacket);
                String jsonString = new String(receivePacket.getData(), 0, receivePacket.getLength());
                Gson gson = new Gson();
                Trade receivedTrade = gson.fromJson(jsonString, Trade.class);

                // Estrazione dei valori
                int orderID = receivedTrade.getOrderID();
                String type = receivedTrade.getType();
                String orderType = receivedTrade.getOrderType();
                int size = receivedTrade.getSize();
                int price = receivedTrade.getPrice();
                
                // Stampa della stringa di notifica
                if(size == 0 && price == 0){
                    printer.print("\nYour " + orderType + " order with ID "+ orderID + " of type " + type + " has been processed but has failed.");
                } else{
                    printer.print("\nYour " + orderType + " order with ID "+ orderID + " of type " + type + " has been processed with size " + size + " and price " + price + ".");
                }

            } catch(Exception e){
                printer.print("[UDP] Error: " + e.getMessage() + " - Cause: " + e.getCause());
                break;
            }
        }
    }
}