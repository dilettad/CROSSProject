package Eseguibili.Client;
import java.io.BufferedReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import Eseguibili.Main.MainClient.SharedData;

// Thread che si occupa di gestire la ricezione e l'elaborazione dei messaggi dal server.

public class ReceiverClient implements Runnable{
    public Socket serverSock; //  Socket connesso al server
    public BufferedReader in; // Reader per ricevere i dati dal server
    public Printer printer; // Gestore per la stampa dei messaggi
    public SharedData sharedData; // Oggetto contenente i dati condivisi tra i thread

    public ReceiverClient(Socket servSock,BufferedReader in, Printer printer, SharedData sharedData){
        this.serverSock = servSock;
        this.in = in;
        this.printer = printer;
        this.sharedData = sharedData;
    }

    public void run(){
        try{
            String line;
            // Continua la lettura finché non viene chiuso o interrotto
            while(!Thread.currentThread().isInterrupted() && !serverSock.isClosed() && !sharedData.isShuttingDown.get() &&
                (line = in.readLine()) != null){

                // Si converte la stringa ricevuta in un oggetto JSON
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                
                // Elaborazione del messaggio in base al suo formato

                if(obj.get("type") != null && (obj.get("orderBook")!=null || obj.get("stopOrders")!=null)){ // Ricevuto messaggio di tipo GsonOrderBook
                    String type = obj.get("type").getAsString();

                    if(type.equals("showOrderBook")){
                        showOrderBook(obj);
                    }
                    if(type.equals("showStopOrders")){
                        showStopOrders(obj);
                    }
                    printer.promptUser();
                } else if((obj.get("type")) != null){ // Ricevuto un messaggio di tipo GsonResponse
                    String type = obj.get("type").getAsString();
                    String errorMessage = obj.get("errorMessage").getAsString();
                    switch(type){
                        case "register":
                            if(errorMessage.equals("OK"))
                                printer.print("Registration completed successfully");
                            else
                                printer.print(errorMessage);
                        break;

                        case "login":
                            if(errorMessage.equals("OK")){
                                sharedData.isLogged.set(true);
                                sharedData.loginError.set(false);
                                printer.print("Login completed successfully");
                            } else{
                                sharedData.loginError.set(true);
                                printer.print(errorMessage);
                            }
                        break;

                        case "updateCredentials":
                            if(errorMessage.equals("OK"))
                                printer.print("Your credentials have been successfully updated.");
                            else
                                printer.print(errorMessage);
                        break;

                        case "logout":
                            if(errorMessage.equals("OK"))
                                System.out.println("Logout completed. Thank you for using our service");
                            else
                                System.out.println(errorMessage);
                            
                            sharedData.isClosed.set(true);
                        return;
                        
                        case "cancelOrder":
                            if(errorMessage.equals("OK"))
                                printer.print("Cancellation completed for this order");
                            else
                                printer.print("Cancellation not available for this order");
                        break;

                        case "getPriceHistory":
                            printer.print(errorMessage);
                        break;

                        case "UDP": // Questo messaggio conterrà la porta UDP del Server
                            int response = obj.get("response").getAsInt();
                            sharedData.UDPport = response;
                        break;

                        case "disconnection":
                            System.out.println(errorMessage);
                            sharedData.isClosed.set(true);
                            System.exit(0);
                        break;
                    }
                    printer.promptUser(); // Viene mostrato il prompt per il comando successivo

                } else if((obj.get("orderID")) != null){ // Ricevuto un messaggio di tipo GsonResponseOrder

                    int orderID = obj.get("orderID").getAsInt();
                    if(orderID != -1)
                        printer.print("Your order ID is: " + orderID);
                    else
                        printer.print("Ops! Something went wrong");
                    printer.promptUser();
                }
            }
        }catch (Exception e){
            printer.print("[RECEIVER] Error:  " + e.getMessage() + " - Cause: " + e.getCause() + " - Class: " + e.getClass());
            sharedData.isClosed.set(true);
        }
    }

    // Metodo per mostrare l'orderBook al client
    public void showOrderBook(JsonObject obj){

        // Estrazione dell'orderBook
        JsonObject orderBookObj = obj.getAsJsonObject("orderBook");
        
        // Formattazione dell'output
        StringBuilder output = new StringBuilder();
        output.append("\n==================== ORDER BOOK ===================\n");
        output.append("+------------------------+------------------------+\n");
        output.append("|       ASK (sellers)    |       BID (buyers)     |\n");
        output.append("+------------------------+------------------------+\n");
        output.append("|  Price   |  Quantity   |  Price   |  Quantity   |\n");
        output.append("+----------+-------------+----------+-------------+\n");
        
        // Estrazione delle mappe
        JsonObject askMap = null;
        if(orderBookObj.has("askMap") && orderBookObj.get("askMap").isJsonObject())
            askMap = orderBookObj.getAsJsonObject("askMap");
        JsonObject bidMap = null;
        if(orderBookObj.has("bidMap") && orderBookObj.get("bidMap").isJsonObject())
            bidMap = orderBookObj.getAsJsonObject("bidMap");

        // Estrazione dello spread
        int spread = 0;
        if(orderBookObj.has("spread") && orderBookObj.get("spread").isJsonPrimitive()) {
            spread = orderBookObj.get("spread").getAsInt();
        }
        
        // Calcolo del numero di righe necessarie
        int askRows = (askMap != null) ? askMap.size() : 0;
        int bidRows = (bidMap != null) ? bidMap.size() : 0;
        int maxRows = Math.max(askRows, bidRows);
        
        // Estrazione dei prezzi
        List<Integer> askPrices = new ArrayList<>();
        List<Integer> bidPrices = new ArrayList<>();
        
        if(askMap != null){
            for(String key : askMap.keySet()){
                askPrices.add(Integer.parseInt(key));
            }
        }
        
        if(bidMap != null){
            for(String key : bidMap.keySet()){
                bidPrices.add(Integer.parseInt(key));
            }
            Collections.sort(bidPrices, Collections.reverseOrder());
        }
        
        // Stampa delle righe della tabella
        for(int i = 0; i < maxRows; i++){
            StringBuilder row = new StringBuilder("| ");
            
            // Parte ASK (sinistra)
            if(i < askPrices.size()){
                // Estrazione del prezzo dall'array e recupero dell'oggeto JSON nella askMap
                int price = askPrices.get(i);
                JsonObject orders = askMap.getAsJsonObject(String.valueOf(price));
                
                int size = orders.get("size").getAsInt();
                
                row.append(String.format(" %-8d | %-10d ", price, size));
            } else {
                row.append("          |            ");
            }
            
            // Parte BID (destra)
            if(i < bidPrices.size()){
                // Estrazione del prezzo dall'array e recupero dell'oggeto JSON nella bidMap
                int price = bidPrices.get(i);
                JsonObject orders = bidMap.getAsJsonObject(String.valueOf(price));
                
                int size = orders.get("size").getAsInt();
                
                row.append(String.format("| %-8d | %-11d |", price, size));
            } else {
                row.append("|          |             |");
            }
            
            output.append(row).append("\n");
        }
        
        output.append("+----------+-------------+----------+-------------+\n");
        output.append("                    Spread: ").append(spread).append("\n");
        
        printer.print(output.toString());
    }

    public void showStopOrders(JsonObject obj) {
        try{
            // Verifica della presenza dell'array stopOrders
            if(!obj.has("stopOrders") || !obj.get("stopOrders").isJsonArray()){
                printer.print("[RECEIVER] Error: Invalid or missing stopOrders array");
                return;
            }

            JsonArray stopOrdersArray = obj.getAsJsonArray("stopOrders");
            
            StringBuilder output = new StringBuilder();
            output.append("\n====================== STOP ORDERS ======================\n");
            output.append("+--------+----------+------------+--------+-------------+\n");
            output.append("|   ID   |   Type   | Stop Price |   Qty  |    User     |\n");
            output.append("+--------+----------+------------+--------+-------------+\n");
            

            if(stopOrdersArray != null && !stopOrdersArray.isEmpty()){
                for(JsonElement element : stopOrdersArray){
                    if (!element.isJsonObject()) continue;
                    
                    JsonObject order = element.getAsJsonObject();
                    
                    // Estrazione dei valori con valori di default
                    int id = order.has("orderID") ? order.get("orderID").getAsInt() : -1;
                    String type = order.has("type") ? order.get("type").getAsString() : "N/A";
                    int price = order.has("stopPrice") ? order.get("stopPrice").getAsInt() : -1;
                    int qty = order.has("size") ? order.get("size").getAsInt() : -1;
                    String user = order.has("username") ? order.get("username").getAsString() : "N/A";
                        
                    // Formattazione della riga
                    output.append(String.format("| %-6d | %-8s | %-10d | %-6d | %-11s |\n", id, type, price, qty, user));
                }
            } else {
                output.append("|                No stop orders present                 |\n");
            }

            output.append("+--------+----------+------------+--------+-------------+\n");
            printer.print(output.toString());
        } catch (Exception e) {
            printer.print("[RECEIVER] Error in showStopOrders: " + e.getMessage());
        }
    }
}