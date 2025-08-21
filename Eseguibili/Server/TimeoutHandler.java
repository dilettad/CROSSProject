package Eseguibili.Server;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

import OrderBook.OrderBook;
import OrderBook.StopValue;


//Thread che gestisce il timeout dei client connessi al server. 
//Monitora l'attività degli utenti e chiude le connessioni inattive, gestendo le eccezioni per gli utenti che attendono uno o più stop order

public class TimeoutHandler implements Runnable { // AGGIUNTO: implements Runnable
    private static final int TIMEOUT_MINUTES = 10;
    private static final long TIMEOUT_MILLIS = TIMEOUT_MINUTES * 60 * 1000;
    public String user = null;
    private final Worker.SharedState sharedState; // CORRETTO: Riferimento alla classe SharedState del Worker

    // Costruttore per TimeoutHandler
    public TimeoutHandler(Worker.SharedState sharedState) { // CORRETTO: Tipo parametro
        this.sharedState = sharedState;
    }

    //Imposta il timestamp dell'ultima attività dell'utente
    public void setTimestamp(long timestamp) {
        sharedState.lastActivity = timestamp;
    }

    //Imposta il nome utente associato a questo handler
    public void setUsername(String user) {
        this.user = user;
    }

    //Converte e stampa un timestamp in formato orario leggibile
    public void printTime (long millis){
        LocalTime orario = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        System.out.println("Ultima attività di " + user + ": " + orario.format(formatter));
    }

    //Aggiorna la lista degli stop orders nello stato condiviso
    public void updateStopOrders(ConcurrentLinkedQueue<StopValue> stopOrders) {
        // Se la lista degli stop orders è vuota, la inizializza
        if(sharedState.stopOrders == null) {
            sharedState.stopOrders = new ConcurrentLinkedQueue<>(stopOrders);
        } else {
            // Se la lista degli stop orders è già inizializzata, la aggiorna
            sharedState.stopOrders.clear();
            sharedState.stopOrders.addAll(stopOrders);
        }
    }

    // Sincronizza lo stato corrente con l'orderBook
    public void syncWithOrderBook(OrderBook orderBook) {
        updateStopOrders(orderBook.stopOrders);
    }

    @Override
    public void run(){
        // Ciclo di monitoraggio
        while(sharedState.runningHandler.get()){
            try {
                //Controllo se client è inattivo
                long currentTime = System.currentTimeMillis();
                if (currentTime - sharedState.lastActivity > TIMEOUT_MILLIS) {
                    //Se ha superato timeout
                    if(user == null){
                        System.out.println("[TIMEOUTHANDLER] Client inactive for more than " + TIMEOUT_MINUTES + " minutes. Closing Connection");
                        sharedState.activeUser.set(false);
                        break;
                    } else {
                        //Utente loggato, si controlla se è negli stopOrders
                        boolean contains = false;
                        if (sharedState.stopOrders != null) {
                            for (StopValue stop : sharedState.stopOrders) {
                                if (stop.getUser().equals(user)) {
                                    contains = true;
                                    break;
                                }
                            }
                        }
                        if (!contains){
                            System.out.println("[TIMEOUTHANDLER] Client " + user + " is not in stop orders.");
                            sharedState.activeUser.set(false);
                        } else {
                            System.out.println("[TIMEOUTHANDLER] Client " + user + " is waiting a stopOrder. Connection open");
                        }
                    }
                    sharedState.runningHandler.set(false);
                }
                //Controllo ogni 5 secondi
                Thread.sleep(5000);
            } catch (Exception e){
                System.err.println("[TIMEOUTHANDLER] Error in TimeoutHandler: " + e.getMessage() + " Cause: " + e.getCause());
            }
        }
        System.out.println("[TIMEOUTHANDLER] Handler of " + user + " stopped.");
    }
}