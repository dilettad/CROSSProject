package Eseguibili.Server;

//Classe per statistiche giornaliere inviate al client che ha richiesto lo storico dei prezzi di apertura, chiusura, max e min, insieme ai timestamp della prima e ultima transazione

public class DailyParameters {
    public final String date;
    public int openPrice;
    public int closePrice;
    public int highPrice;
    public int lowPrice;
    public long firstTimestamp;
    public long lastTimestamp;

    // CORRETTO: Aggiunto parametri mancanti al costruttore
    public DailyParameters(String date, int price, long timestamp) {
        this.date = date;
        this.openPrice = price;
        this.closePrice = price;
        this.highPrice = price;
        this.lowPrice = price;
        this.firstTimestamp = timestamp;
        this.lastTimestamp = timestamp;
    }
    
    public void updatePrices (int price, long timestamp){
        //Aggiorniamo high e low
        if (price > highPrice) {
            highPrice = price;
        }
        if (price < lowPrice) {
            lowPrice = price;
        }
        //Aggiorniamo open se trade è più vecchio del primo conosciuto
        if (timestamp < firstTimestamp) {
            firstTimestamp = timestamp;
            openPrice = price;
        }
        if (timestamp > lastTimestamp) {
            lastTimestamp = timestamp;
            closePrice = price;
        }
    }
}