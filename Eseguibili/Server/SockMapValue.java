package Eseguibili.Server;

import java.net.InetAddress;

//Classe per usare la porta e indirizzo del client caricato nella mappa degli utenti attivi

public class SockMapValue {
    public int port;
    public InetAddress address;
    public SockMapValue(int port, InetAddress address) {
        this.port = port;
        this.address = address;
    }
    public String toString(){
        return "SockMapValue{" +
                "port=" + port +
                ", address=" + address +
                '}';
    }
}
