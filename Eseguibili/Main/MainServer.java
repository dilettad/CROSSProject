package Eseguibili.Main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import Eseguibili.Server.*;
import OrderBook.*;  
import OrderBook.Bookvalue;


public class MainServer {
    //Socket e stream
    public static final String configFile = "server.properties"; // Corretto il nome del file
    public static int TCPport;              // Porta TCP per le connessioni client
    public static int UDPport;              // Porta base UDP per le comunicazioni asincrone
    public static String hostname;          // Nome host del server
    public static int maxDelay;             // Ritardo massimo per timeout
    public static ServerSocket serverSocket;
    
    public static ConcurrentLinkedQueue<Worker> workerList = new ConcurrentLinkedQueue<>();
    public static ConcurrentHashMap<String,Tupla> userMap = new ConcurrentHashMap<>();
    private static final ConcurrentSkipListMap<String, SockMapValue> socketMap = new ConcurrentSkipListMap<>();

    //bidMap: ordine di acquisto, ordinati in modo decrescente
    public static ConcurrentSkipListMap<Integer, Bookvalue> bidMap = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    //askMap: ordine di vendita, ordinati in modo crescente
    public static ConcurrentSkipListMap<Integer, Bookvalue> askMap = new ConcurrentSkipListMap<>();
    public static ConcurrentLinkedQueue<StopValue> stopOrders;
    public static OrderBook orderBook = new OrderBook(askMap, 0, new ConcurrentLinkedQueue<StopValue>(), bidMap);

    public static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    public static void main (String [] args) throws Exception {
        //Lettura dei parametri
        readConfig();
        try{
            serverSocket = new ServerSocket(TCPport);

            // Viene associato un handler per gestire la terminazione con ctrl-C
            Runtime.getRuntime().addShutdownHook(new Thread(){
                public void run(){
                    System.out.println("\nShutting down...");
                    shutdown();
                }
            });

            // Lettura della userMap dalla memoria
            loadUserMap();

            // Lettura dell'orderBook dalla memoria
            loadOrderBook();

            System.out.printf("[MAIN SERVER]: listening on port %d\n",TCPport);
            int i = 1;
            while(true){
                Socket receivedSocket = serverSocket.accept();
                // Creazione del worker che si occuperà di gestire il client
                Worker worker = new Worker(receivedSocket,userMap,orderBook,socketMap,UDPport+i);
                workerList.add(worker); // Aggiunta del thread alla lista che mantiene tutti worker
                executorService.execute((Runnable) worker);
                i++;
            }
        }catch (SocketException e){
            // La accept() è stata interrotta: questa eccezione viene ignorata
        } catch (Exception e){
            System.err.println("[MAINSERVER] Error: " + e.getMessage());
        }
    }
    
    //Metodo per aggiornare tutti i gestori dei timeout per tutti i worker attivi
    public static void updateTimeoutHandlers() {
        for (Worker worker : workerList) {
            worker.updateTimeoutHandler();
        }
    }
    
    //Metodo per caricare la mappa degli utenti dal file JSON
    public static void loadUserMap(){
        try (JsonReader reader = new JsonReader(new FileReader("src/Json/userMap.json"))) {
            reader.beginObject();
            while (reader.hasNext()){
                String name = reader.nextName();
                Tupla value = new Gson().fromJson(reader, Tupla.class);  
                userMap.put(name, value);
            }
            reader.endObject();
        }
        catch (EOFException e){
            System.out.println("[MAINSERVER] userMap.json is empty, no users loaded.");
            return;
        } catch (Exception e) {
            System.err.println("[MAINSERVER] Error loading userMap.json: " + e.getMessage());
            System.exit(0);
        }
    }
    
    //Metodo che carica l'orderBook dal file JSON
    public static void loadOrderBook(){
        try (JsonReader reader = new JsonReader(new FileReader("src/Json/orderBook.json"))) {
           Gson gson = new Gson();
           reader.beginObject();

            while (reader.hasNext()){
                String name = reader.nextName();

                if (name.equals("askMap")) {
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, Bookvalue> askMap = orderBook.askMap;

                    while (reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName()); // CORRETTO: era reader.hasNext()
                        Bookvalue value = gson.fromJson(reader, Bookvalue.class); // CORRETTO: era BookValue
                        askMap.put(price, value);
                    }
                    reader.endObject();
                } else if (name.equals("bidMap")) {
                    reader.beginObject();
                    ConcurrentSkipListMap<Integer, Bookvalue> bidMap = orderBook.bidMap;

                    while (reader.hasNext()){
                        int price = Integer.parseInt(reader.nextName());
                        Bookvalue value = gson.fromJson(reader, Bookvalue.class); // CORRETTO: era BookValue
                        bidMap.put(price, value);
                    }
                    reader.endObject();
                } else if (name.equals("spread")){
                    orderBook.spread = reader.nextInt();
                } else if(name.equals("lastOrderID")){
                    orderBook.lastOrderID = reader.nextInt();
                } else if(name.equals("stopOrders")){
                    reader.beginArray();
                    orderBook.stopOrders = new ConcurrentLinkedQueue<StopValue>();
                    
                    // Gli elementi della lista di stopOrders vengono saltati
                    while(reader.hasNext()){
                        reader.skipValue();
                    }
                    reader.endArray();
                }
            }
            reader.endObject();
        } catch (EOFException e) {
            System.out.println("[MAINSERVER] orderBook.json is empty, no orders loaded.");
            return;
        } catch (Exception e) {
            System.err.println("[MAINSERVER] Error loading orderBook.json: " + e.getMessage());
            System.exit(0);
        }
    }  

    private static void shutdown(){
        //Chiusura del ServerSocket per interrompere il ciclo accept()
        try {
            if (serverSocket !=null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[MAINSERVER] Error closing server socket: " + e.getMessage());
        }
        //Notifica a tutti i worker della chiamata
        if (!workerList.isEmpty()) {
            for (Worker worker : workerList) {
                worker.shutdown();
            }
        }
        //Terminare i thread
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(maxDelay, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        System.out.println("[MAINSERVER] Server shutdown complete.");
    }

    //Metodo per leggere il file di configurazione del server
    public static void readConfig() throws FileNotFoundException, IOException{
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        TCPport = Integer.parseInt(prop.getProperty("TCPport"));
        maxDelay = Integer.parseInt(prop.getProperty("maxDelay"));
        UDPport = Integer.parseInt(prop.getProperty("UDPport"));
        hostname = prop.getProperty("hostname");
        input.close();
    }
}