package Eseguibili.Server;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import Eseguibili.Main.MainServer;
import Gson.GsonOrderBook;
import Gson.GsonResponse;
import Gson.GsonResponseOrder;
import Gson.GsonTrade;
import OrderBook.OrderBook;
import OrderBook.StopValue;

//Worker che gestisce la connessione e le richieste del client all'interno dell'orderbook

public class Worker implements Runnable {
    private Socket clientSocket; // CORRETTO: era clienSocket
    private ConcurrentSkipListMap<String, SockMapValue> socketMap;
    public String hostname;
    public int UDPport;
    public int clientPort;
    public InetAddress clientAddress; // CORRETTO: era inetAddress

    public ConcurrentHashMap<String,Tupla> userMap;
    public OrderBook orderBook;
    public TimeoutHandler handler;
    private SharedState sharedState; // AGGIUNTO: riferimento allo stato condiviso

    private String username = null;
    private String password = null;
    private String onlineUser = null;
    private String type;
    private int size;
    private int price;

    // AGGIUNTO: variabile per readDailyData
    private String date;

    //Oggetti JSON per la comunicazione
    public GsonResponse response = new GsonResponse();
    public GsonOrderBook responseOrderBook = new GsonOrderBook();
    public GsonResponseOrder responseOrder = new GsonResponseOrder(); // CORRETTO: era HsonResponseOrder
    private static Gson gson = new Gson();

    //Flags
    public static AtomicBoolean running = new AtomicBoolean(true);

    public Worker(Socket socket, ConcurrentHashMap<String,Tupla> userMap, OrderBook orderBook, ConcurrentSkipListMap<String,SockMapValue> socketMap, int UDPport){
        this.clientSocket = socket; // CORRETTO: era clienSocket
        this.userMap = userMap;
        this.orderBook = orderBook;
        this.socketMap = socketMap;
        this.UDPport = UDPport;
        
        // AGGIUNTO: Inizializzazione dello stato condiviso e del TimeoutHandler
        this.sharedState = new SharedState();
        this.handler = new TimeoutHandler(sharedState);
        
        updateJsonOrderBook(orderBook);
    }
    
    //Classe per condividere dati tra worker e timeoutHandler
    public class SharedState{
        public AtomicBoolean activeUser = new AtomicBoolean(true);
        public AtomicBoolean runningHandler = new AtomicBoolean(true);
        public volatile long lastActivity = System.currentTimeMillis(); // CORRETTO: era lastActiveTime
        public volatile ConcurrentLinkedQueue<StopValue> stopOrders = new ConcurrentLinkedQueue<>();
    }
    
    @Override
    public void run() {
        // AGGIUNTO: Implementazione base del metodo run
        try {
            // Qui va la logica principale del worker
            // Gestione delle connessioni client, lettura comandi, etc.
            
            while (running.get() && sharedState.activeUser.get()) {
                // Logica di elaborazione dei comandi del client
                // TODO: Implementare la logica specifica
                
                Thread.sleep(1000); // Pausa temporanea
            }
            
        } catch (Exception e) {
            System.err.println("[WORKER] Error in worker run: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("[WORKER] Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    //Metodo per chiudere il worker
    public void shutdown(){
        running.set(false);
        if (sharedState != null) {
            sharedState.activeUser.set(false);
            sharedState.runningHandler.set(false);
        }
    }

    //Metodo per leggere i dati giornalieri dello storico solo per mese e anno
    public String readDailyData(String date) { // AGGIUNTO: parametro date
        // Implementazione per leggere i dati giornalieri dello storico
        String monthPassed = date.substring(0,2); 
        String yearPassed = date.substring(2);

        StringBuilder keyBuilder = new StringBuilder();

        //Mappa per memorizzare i dati di ogni giorno
        ConcurrentSkipListMap<String, DailyParameters> daysMap = new ConcurrentSkipListMap<>();
        try (JsonReader reader = new JsonReader(new FileReader("src/Json/dailyData.json"))) {
            Gson gson = new Gson();
            
            //Formato per estrarre solo la data senza ora
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
            reader.beginObject();

            while(reader.hasNext()){
                String key = reader.nextName();
                //Se key è "trades"
                if (key.equals("trades")) {
                    //Leggo gli oggetti trade
                    reader.beginArray();
                    while (reader.hasNext()){
                        //Deserializzazione di trade come oggetto GsonTrade
                        GsonTrade trade = gson.fromJson(reader, GsonTrade.class);
                        //Creo oggetto date e estraggo mese e anno
                        Date tradeDate = new Date(trade.getTime()*1000);
                        String month = monthFormat.format(tradeDate);
                        String year = yearFormat.format(tradeDate);
                        //Filtro ordine per mese e anno
                        if (month.equals(monthPassed) && year.equals(yearPassed)) {
                            //Timestamp in data leggibile e aggiorno i dati giornalieri
                            String readableDate = dayFormat.format(tradeDate);
                            // Aggiornamento dei dati giornalieri 
                            if (!daysMap.containsKey(readableDate)) {
                                //Primo trade del giorno - CORRETTO: aggiunto parametri mancanti
                                daysMap.put(readableDate, new DailyParameters(readableDate, trade.getPrice(), trade.getTime()));
                            } else {
                                //Aggiornamento dei dati esistenti
                                daysMap.get(readableDate).updatePrices(trade.getPrice(), trade.getTime());
                            }
                        }
                    }
                    reader.endArray();
                } else {
                    //Se campi presenti salto
                    reader.skipValue();
                }
            }
            reader.endObject();
            //Creazione della stringa risultato contenente i dati giornalieri
            keyBuilder.append("Daily Data\n");

            for (Map.Entry<String, DailyParameters> entry: daysMap.entrySet()) {
                DailyParameters params = entry.getValue();
                keyBuilder.append(String.format("Date: %s, OpenPrice: %d, MaxPrice: %d, MinPrice: %d, ClosePrice: %d\n", 
                entry.getKey(), params.openPrice, params.highPrice, 
                params.lowPrice, params.closePrice));
            }
        } catch (Exception e) {
            System.err.printf("[WORKER] Error: %s \n", e.getMessage());
        }
        return keyBuilder.toString();
    }

    //Metodo per sincronizzare l'orderbook
    public void updateTimeoutHandler(){
      if (this.handler != null) {
          this.handler.syncWithOrderBook(orderBook);
      }
    }
    
    //Metodo per verificare se una stringa passata è valida
    public static boolean isValid(String string){
        if (string == null || string.isEmpty()) {
            return false;
        }
        //Ammessi caratteri alfanumerici
        return string.matches("^[a-zA-Z0-9]+$");
    }

    //Metodo per modificare il file JSON che mostra la userMap
    public static void updateJsonUserMap(ConcurrentHashMap<String, Tupla> userMap) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/Json/userMap.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer.write(gson.toJson(userMap));
        } catch (IOException e) {
            System.err.printf("[WORKER] updateJsonUserMap %s \n", e.getMessage());
        }
    }

    //Metodo per modificare il file JSON che mostra l'orderBook
    public static void updateJsonOrderBook(OrderBook orderBook) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/Json/orderBook.json"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer.write(gson.toJson(orderBook));
            //Sincro tutti i worker quando l'orderbook cambia
            for (Worker worker : MainServer.workerList) { // CORRETTO: era workersList
                if (worker != null && worker.handler != null){
                    worker.handler.syncWithOrderBook(orderBook);
                }
            }
        } catch (Exception e) {
            System.err.printf("[WORKER] updateJsonOrderBook %s \n", e.getMessage());
        }
    }
}