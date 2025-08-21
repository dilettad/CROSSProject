package OrderBook;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import com.google.gson.Gson;
import java.net.InetAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import Eseguibili.Server.*;

// Classe che rappresenta l'order book del mercato: gestendo ordine di bid e ask, processare marketOrders stopOrders e LimitOrders

public class OrderBook {
    public ConcurrentSkipListMap<Integer, Bookvalue> askMap;
    public int spread;
    public int lastOrderID = 0;
    public ConcurrentLinkedQueue<StopValue> stopOrders;
    public ConcurrentSkipListMap<Integer, Bookvalue> bidMap;

    public OrderBook(ConcurrentSkipListMap<Integer,Bookvalue> askMap, int spread, ConcurrentLinkedQueue<StopValue> stopOrders, ConcurrentSkipListMap<Integer,Bookvalue> bidMap){
            this.askMap = askMap;
            this.spread = spread;
            this.bidMap = bidMap;
            this.stopOrders = stopOrders;
            updateOrderBook();
    }
        
    /*Metodo notifica all'utente sullo stato dell'ordine tramite UDP con parametri;
        socketMap: Mappa degli utenti connessi e delle relative informazioni socket
        user: nome utente da notificare
        orderID: ID dell'ordine da notificare
        type: tipo di ordine (bid/ask)
        orderType: tipo di ordine (limit/market/stop)
        size: dimensione dell'ordine
        price: prezzo dell'ordine
        */

    public void notifyUser(Map<String, String> socketMap, String user, int orderID, String type, String orderType, int size, int price) {
        System.out.println("Notifying user: " + user + " about order ID: " + orderID + ", Type: " + type + ", Order Type: " + orderType + ", Size: " + size + ", Price: " + price);
        // Estrazione della porta dell'utente a cui mandare la notifica tramite la socketMap
        int port = 0;
        //String address = null;
        InetAddress Address = null;
            for (Map.Entry<String, String> entry : socketMap.entrySet()) {
                if (entry.getKey().equals(user)) {
                    port = entry.getValue().port;
                    Address = entry.getValue().Address;
                    sendUdpMessage(socketInfo, orderID, type, orderType, size, price);
                    break;
                }
            }

            if(port != 0 && Address != null){
                try(DatagramSocket sock = new DatagramSocket()){
                    //Creazione dell'oggetto Trade
                    Trade trade = new Trade(orderID, type, orderType, size, price);
                    // Utilizzo di una libreria JSON come Gson per la serializzazione
                    Gson gson = new Gson();
                    String json = gson.toJson(trade);
                    // Conversione del JSON in un array di byte
                    byte[] data = json.getBytes(StandardCharsets.UTF_8);
                    // Creazione di un pacchetto Datagram con i dati e indirizzo del server
                    DatagramPacket packet = new DatagramPacket(data, data.length, Address, port);
                    // Invio del pacchetto
                    sock.send(packet);

                } catch (Exception e){
                    System.err.println("NotifyUser() Error: " + e.getMessage());
                }

            }   else{
                System.out.println("User not online, message not sent.");
            }
    }
    
    // Metodo per restituire gli username presenti nella lista degli utenti
    public ConcurrentLinkedQueue<String> getUsers(ConcurrentLinkedQueue<UserBook> list) {
        ConcurrentLinkedQueue<String> users = new ConcurrentLinkedQueue<>();
            for (UserBook user : list) {
                users.add(user.username); // <-- CORRETTO
            }
            return users;
    }

        //Metodo per controllare tutti gli stopOrder per verificare se qualcuno di essi debba essere eseguito in base ai prezzi di mercato correnti
        /*  Uno stop order è un ordine di acquisto (risp. di vendita), che viene tentato (potrebbe fallire come un Market Order) al prezzo di mercato una volta che il prezzo di
            mercato raggiunge, i.e. è maggiore o uguale (risp. minore o uguale), un prezzo specifico, detto StopPrice */
    public synchronized void checkStopOrders(ConcurrentSkipListMap<String, SockMapValue> socketMap) {
        Iterator<StopValue> iterator = stopOrders.iterator();

        while (iterator.hasNext()) {
            StopValue order = iterator.next();
            boolean triggerCondition = false;
            ConcurrentLinkedQueue<String> userList = null;

            if (order.type.equals("ask")) {
                if (!bidMap.isEmpty()) {
                    Integer bestBidPrice = bidMap.firstKey();
                    if (order.stopPrice <= bestBidPrice.longValue()) {
                        userList = getUsers(bidMap.get(bestBidPrice).userList);
                        triggerCondition = userList.stream().anyMatch(u -> !u.equals(order.username));
                    }
                }
            } else if (order.type.equals("bid")) {
                if (!askMap.isEmpty()) {
                    Integer bestAskPrice = askMap.firstKey();
                    if (order.stopPrice >= bestAskPrice.longValue()) {
                        userList = getUsers(askMap.get(bestAskPrice).userList);
                        triggerCondition = userList.stream().anyMatch(u -> !u.equals(order.username));
                    }
                }
            }

            if (triggerCondition) {
                int res = tryMarketOrder(order.type, order.size, order.username, "stop", socketMap);
                lastOrderID--; // già assegnato in insertStopOrder()

                if (res != -1) {
                    System.out.printf("%s's StopOrder processed successfully. Order: %s\n", order.username, order);
                } else {
                    System.out.printf("%s's StopOrder was processed but failed. Order: %s\n", order.username, order);
                    notifyUser(socketMap, order.username, order.orderID, order.type, "stop", 0, 0);
                }

                iterator.remove();
            }
        }
    }

    public synchronized void loadBidOrder(int size, int price, String user, int orderID) {
        UserBook newuser = new UserBook(size, user, orderID); //Creo nuovo utente
            //Se prezzo già esiste
            if (bidMap.containsKey(price)) { 
                Bookvalue oldValue = bidMap.get(price);  //ERRORE DOVUTO ALL'IMPORT E AL PACKAGE
                ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList); // <-- AGGIUNTO ;
                newList.add(newuser); //Aggiungo il nuovo utente alla lista
                int newSize = oldValue.size + size; //Aggiorno la size
                Bookvalue newValue = new Bookvalue(newSize, newSize * price, newList); //Creo nuovo Bookvalue con la size aggiornata e la lista degli utenti
                bidMap.replace(price, newValue); //Sostituisco il vecchio Bookvalue con il nuovo
            } else {
                ConcurrentLinkedQueue<UserBook> newuserList = new ConcurrentLinkedQueue<>();
                newuserList.add(newuser); //Creo una nuova lista degli utenti con il nuovo utente
                Bookvalue newValue = new Bookvalue(size, price * size, newuserList);
                bidMap.put(price, newValue);
            }
    }

    //Metodo per elaborare un limitOrder di tipo bid: Il metodo cerca corrispondenze nella askMap eseguendo l'algoritmo tryMatch e carica parzialmente o totalmente l'ordine nella bidMap se non viene soddisfatto.
    public synchronized int tryBidOrder (int size, int price, String user, ConcurrentSkipListMap<String, SockMapValue> socketMap) {
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for (Map.Entry<Integer, Bookvalue> entry : askMap.entrySet()) {
            int askPrice = entry.getKey();
            Bookvalue askValue = entry.getValue();

            if (askPrice <= price) { // Se il prezzo di vendita è inferiore o uguale al prezzo di acquisto
                int matchedSize = Math.min(remainingSize, askValue.size); 
                remainingSize -= matchedSize;

                // Notifica gli utenti coinvolti
                for (UserBook userBook : askValue.userList) {
                    notifyUser(socketMap, userBook.username, orderID, "bid", "limit", matchedSize, askPrice);
                }

                // Rimuove l'ordine dalla askMap se completamente soddisfatto
                if (askValue.size == matchedSize) {
                    askMap.remove(askPrice);
                } else {
                    // Aggiorna la size dell'ordine nella askMap
                    askValue.size -= matchedSize;
                    askValue.total -= matchedSize * askPrice;
                    askValue.userList.removeIf(u -> u.username.equals(user)); // Rimuove l'utente che ha eseguito l'ordine
                    askMap.put(askPrice, askValue);
                }

                if (remainingSize == 0) { //Ordine completato
                    System.out.println("Order number" + orderID + " fully matched.");
                    updateOrderBook();
                    return orderID; // Ordine completamente soddisfatto
                }
            }
            if (remainingSize > 0) {
                loadBidOrder(remainingSize, price, user, orderID);
                if (remainingSize == size) {
                    System.out.println("Order number "+ orderID + " unmatched: " + remainingSize + " placed in the orderBook");
                } else {
                     System.out.println("Order number "+ orderID + " was partially completed; the remaining size of " + remainingSize + " was added to the orderBook");
                }
            }
            updateOrderBook();
            return orderID; // Ordine parzialmente soddisfatto o non soddisfatto
        }
    }

    //Metodo per caricare un ordine di vendita (ask) nella askMap: Il metodo cerca corrispondenze nella bidMap eseguendo l'algoritmo tryMatch e carica parzialmente o totalmente l'ordine nella askMap se non viene soddisfatto.
    public synchronized void loadAskOrder(int size, int price, String user, int orderID){
        UserBook newuser = new UserBook(size, user, orderID); //Creo nuovo utente 
        //Se il prezzo è già esistente
        if (askMap.containsKey(price)){
            Bookvalue oldValue = askMap.get(price); //Recupero il vecchio valore
            ConcurrentLinkedQueue<UserBook> newList = new ConcurrentLinkedQueue<>(oldValue.userList); //Creo una nuova lista degli utenti con la vecchia lista
            newList.add(newuser); //Aggiungo il nuovo utente alla lista
            int newSize = oldValue.size + size; //Aggiorno la size
            Bookvalue newValue = new Bookvalue(newSize, newSize * price, newList); //Creo nuovo Bookvalue con la size aggiornata e la lista degli utenti
            askMap.replace(price, newValue); //Sostituisco il vecchio Bookvalue con il nuovo    

        } else {
            ConcurrentLinkedQueue<UserBook> newuserList = new ConcurrentLinkedQueue<>();
            newuserList.add(newuser); //Creo una nuova lista degli utenti con il nuovo utente
            Bookvalue newValue = new Bookvalue(size, price * size, newuserList);
            askMap.put(price, newValue);
        }
    }
           
    public synchronized int tryAskOrder(int size, int price, String user, ConcurrentSkipListMap<String, SockMapValue> socketMap) {
        int remainingSize = size;
        int orderID = updateLastOrderID();

        for (Map.Entry<Integer, Bookvalue> entry : bidMap.entrySet()) {
            int bidPrice = entry.getKey();
            Bookvalue bidValue = entry.getValue();

            if (bidPrice >= price) { // Se il prezzo di acquisto è superiore o uguale al prezzo di vendita
                int matchedSize = Math.min(remainingSize, bidValue.size);
                remainingSize -= matchedSize;

                // Notifica gli utenti coinvolti
                for (UserBook userBook : bidValue.userList) {
                    notifyUser(socketMap, userBook.username, orderID, "ask", "limit", matchedSize, bidPrice);
                }

                // Rimuove l'ordine dalla bidMap se completamente soddisfatto
                if (bidValue.size == matchedSize) {
                    bidMap.remove(bidPrice);
                } else {
                    // Aggiorna la size dell'ordine nella bidMap
                    bidValue.size -= matchedSize;
                    bidValue.total -= matchedSize * bidPrice;
                    bidValue.userList.removeIf(u -> u.username.equals(user)); // Rimuove l'utente che ha eseguito l'ordine
                    bidMap.put(bidPrice, bidValue);
                }

                if (remainingSize == 0) { //Ordine completato
                    System.out.println("Order number" + orderID + " fully matched.");
                    updateOrderBook();
                    return 0; 
                }
            }
        }
        if (remainingSize > 0) {
            loadAskOrder(remainingSize, price, user, orderID);
            if (remainingSize == size) {
                System.out.println("Order number "+ orderID + " unmatched: " + remainingSize + " placed in the orderBook");
            } else {
                 System.out.println("Order number "+ orderID + " was partially completed; the remaining size of " + remainingSize + " was added to the orderBook");
            }
        }
        updateOrderBook();
        return orderID; // Ordine parzialmente soddisfatto o non soddisfatto
    }
    
    //Algoritmo per eseguire il matching tra ordini ask-bid. Restituisce la dimensione rimanente dopo aver eseguito i match possibili, parametri:
    /*
     * remainingSize: Dimensione dell'ordine da abbinare
     * user: Username del proprietario dell'ordine
     * userType: Tipo dell'ordine dell'utente ("bid" o "ask")
     * list: Lista degli ordini di controparte
     * listType: Tipo degli ordini di controparte ("bid" o "ask")
     * orderType: Categoria dell'ordine ("limit", "market", "stop")
     * price: Prezzo al quale gli ordini sono abbinati
     * orderID: ID dell'ordine dell'utente
     * socketMap: Mappa degli utenti connessi e delle relative informazioni socket usate per inviare la notifica UDP  
    */
    public synchronized int tryMatch(int remainingSize, String user, String userType, ConcurrentLinkedQueue<UserBook> list, String listType, String orderType, int price, int orderID, ConcurrentSkipListMap<String, SockMapValue> socketMap) {
        Iterator<UserBook> iterator = list.iterator();
        while(iterator.hasNext() && remainingSize > 0) {
            UserBook IUser = iterator.next();
            if (!IUser.username.equals(user)) {
                // Rimuove l'ordine dalla lista se completamente abbinato
                if (IUser.size > remainingSize){
                    IUser.size -= remainingSize; // Aggiorna la dimensione dell'ordine rimanente
                    //IUser.total -= remainingSize * price; // Aggiorna il totale dell'ordine
                    notifyUser(socketMap, IUser.username, IUser.orderID, listType, "limit", remainingSize, price); // Notifica l'utente del controordine parziale
                    notifyUser(socketMap, user, orderID, userType, orderType, remainingSize, price);
                    remainingSize = 0; // L'ordine dell'utente è stato parzialmente abbinato
                
                } else if (IUser.size < remainingSize) {
                    remainingSize -= IUser.size; // Aggiorna la dimensione rimanente
                    // Notifica l'utente del controordine completo
                    notifyUser(socketMap, IUser.username, IUser.orderID, listType, "limit", IUser.size, price);
                    notifyUser(socketMap, user, orderID, userType, orderType, IUser.size, price);
                    iterator.remove(); // Rimuove l'ordine dalla lista poiché è stato completamente abbinato
                }

            } else {
                iterator.remove(); // Rimuove l'ordine dell'utente dalla lista se è stato abbinato
                // Notifica l'utente del controordine parziale
                notifyUser(socketMap, IUser.username, IUser.orderID, listType, "limit", IUser.size, price);
                notifyUser(socketMap, user, orderID, userType, orderType, remainingSize, price);
                remainingSize = 0; // L'ordine dell'utente è stato completamente abbinato
            }
        }
        return remainingSize;
    }

    //Metodo per cancellare un ordine, restituisce 100 se l'ordine è stato eliminato correttamente else 101
    public synchronized int cancelOrder(int orderID, String onlineUser) {
        // Controllo della askMap
        for(Map.Entry<Integer, Bookvalue> entry : askMap.entrySet()) {
            Bookvalue bookValue = entry.getValue();
            Iterator<UserBook> iterator = bookValue.userList.iterator();
            while (iterator.hasNext()) {
                UserBook userBook = iterator.next();
                if (userBook.orderID == orderID && userBook.username.equals(onlineUser)) {
                    iterator.remove(); // Rimuove l'utente dalla lista
                    updateOrderBook();
                    if (bookValue.userList.isEmpty()) {
                        askMap.remove(entry.getKey()); // Rimuove l'entry se non ci sono più utenti
                    }
                    System.out.println("Order " + orderID + " cancelled successfully.");
                    return 100; // Ordine cancellato con successo
                }
            }
        }

        //Controllo della bidMap
        for(Map.Entry<Integer, Bookvalue> entry : bidMap.entrySet()) {
            Bookvalue bookValue = entry.getValue();
            Iterator<UserBook> iterator = bookValue.userList.iterator();
            while (iterator.hasNext()) {
                UserBook userBook = iterator.next();
                if (userBook.orderID == orderID && userBook.username.equals(onlineUser)) {
                    iterator.remove(); // Rimuove l'utente dalla lista
                    updateOrderBook();
                    if (bookValue.userList.isEmpty()) {
                        bidMap.remove(entry.getKey()); // Rimuove l'entry se non ci sono più utenti
                    }
                    System.out.println("Order " + orderID + " cancelled successfully.");
                    return 100; // Ordine cancellato con successo
                }
            }
        }
        
        //Controllo degli stopOrders
        Iterator<StopValue> iterator = stopOrders.iterator();
        while (iterator.hasNext()) {
            StopValue user = iterator.next();
            if (user.orderID == orderID && user.username.equals(onlineUser)) {
                iterator.remove(); // Rimuove lo stop order dalla lista
                updateOrderBook();
                System.out.println("Stop Order " + orderID + " cancelled successfully.");
                return 100; // Stop Order cancellato con successo
            }
        }
        return 101; 
    }   
    
    //Metodo per aggiornare la size e i totali delle ask e bid map, lo spread ed elimina i prezzi la cui userList è vuota
    public void updateOrderBook(){

        //Controllo e rimozione delle askMap
        Iterator<Map.Entry<Integer, Bookvalue>> askIterator = askMap.entrySet().iterator();
        while (askIterator.hasNext()) {
            Map.Entry<Integer, Bookvalue> entry = askIterator.next();
            int price = entry.getKey();
            Bookvalue bookValue = entry.getValue();
            if (bookValue.userList.isEmpty()) {
                askIterator.remove(); // Rimuove l'entry se la lista degli utenti è vuota
            } else {
                int newSize = 0;
                for (UserBook user : bookValue.userList) {
                    newSize += user.size; // Calcola la nuova size totale
                }
                bookValue.size = newSize; // Aggiorna la size totale
                bookValue.total = bookValue.size * price; // Aggiorna il totale
            }
        }    

        //Controllo e rimozione delle bidMap
        Iterator<Map.Entry<Integer, Bookvalue>> bidIterator = bidMap.entrySet().iterator();
        while (bidIterator.hasNext()) {
            Map.Entry<Integer, Bookvalue> entry = bidIterator.next();
            int price = entry.getKey();
            Bookvalue bookValue = entry.getValue();
            if (bookValue.userList.isEmpty()) {
                bidIterator.remove(); // Rimuove l'entry se la lista degli utenti è vuota
            } else {
                int newSize = 0;
                for (UserBook user : bookValue.userList) {
                    newSize += user.size; // Calcola la nuova size totale
                }
                bookValue.size = newSize; // Aggiorna la size totale
                bookValue.total = bookValue.size * price; // Aggiorna il totale
            }
        }
        updateSpread(); // Aggiorna lo spread
    }

    //Metodo per aggiornare il valore dello spread
    public void updateSpread(){
        if (!bidMap.isEmpty() && !askMap.isEmpty()){
            int maxBid = bidMap.firstKey(); 
            int minAsk = askMap.firstKey(); 
            this.spread = maxBid - minAsk; // Calcola lo spread
        } else if (bidMap.isEmpty() && !askMap.isEmpty()){
            this.spread = -1 * askMap.firstKey(); // Se non ci sono bid, lo spread è negativo del prezzo minimo ask
        } else if (!bidMap.isEmpty() && askMap.isEmpty()){
            this.spread = bidMap.firstKey(); // Se non ci sono ask, lo spread è il prezzo massimo bid
        } else this.spread = 0; // Se entrambi sono vuoti, lo spread è 0
    }

    //Metodo per incrementare il contatore degli ID degli ordini 
    public synchronized int updateLastOrderID() {
        lastOrderID++;
        return lastOrderID;
    }

    //Metodo che restituisce la size totale della askMap oppure della bidMap
    public int totalMapSize(ConcurrentSkipListMap<Integer,Bookvalue> map){
        int totalSize = 0;
        for (Map.Entry<Integer,Bookvalue> entry: map.entrySet()) {
            totalSize += entry.getValue().size; // Somma le size di tutti gli ordini nella mappa
        }
        return totalSize;
    }

    //Metodo che restituisce la size totale che un utente ha inserito nella askMap o bidMap
    public int totalUserSize(ConcurrentSkipListMap<Integer,Bookvalue> map, String username){
        int totalSize = 0;
        for (Map.Entry<Integer,Bookvalue> entry: map.entrySet()) {
            for (UserBook user : entry.getValue().userList) {
                if (user.username.equals(username)) {
                    totalSize += user.size; 
                }
            }
        }
        return totalSize;
    }

    public ConcurrentSkipListMap<Integer,BookValue> getAskMap(){
        return this.askMap;
    }

    public ConcurrentSkipListMap<Integer,BookValue> getBidMap(){
        return this.bidMap;
    }

    public int getSpread(){
        return this.spread;
    }

    public int getLastOrderID(){
        return lastOrderID;
    }

    public ConcurrentLinkedQueue<StopValue> getStopOrders(){
        return this.stopOrders;
    }

    public String toString(){
        return "OrderBook{" +
                "askMap=" + askMap +
                ", spread=" + spread +
                ", lastOrderID=" + lastOrderID +
                ", stopOrders=" + stopOrders +
                ", bidMap=" + bidMap +
                '}';
    }    
}
