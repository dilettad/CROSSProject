package Gson;
import java.io.PrintWriter;

import com.google.gson.Gson;

// Classe utilizzata per definire la struttura dei messaggi di risposta che il server invia al client
public class GsonResponse{
    public String type;
    public int response;
    public String errorMessage;

    public void setResponse(String type, int response, String errorMessage){
        this.type = type;
        this.response = response;
        this.errorMessage = errorMessage;
    }

    public String getResponseType(){
        return type;
    }

    public Integer getResponseNumber(){
        return response;
    }

    public String getResponseMessage(){
        return errorMessage;
    }

    // Serializzazione del messaggio di risposta e invio sullo stream out 
    public void sendMessage(Gson gson,PrintWriter out){
        String respMessage = gson.toJson(this);
        out.println(respMessage);
    }

    public String toString() {
        return "{response= " + this.response + ", errorMessage= " + this.errorMessage + "}";
    }
    
}