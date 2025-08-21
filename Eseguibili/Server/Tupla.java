package Eseguibili.Server;

//Classe per rappresentare una tupla di valori contenente password e stato di login dell'utente memorizzato sulla UserMap
public class Tupla {
    public String password;
    public Boolean isLogged;

    public Tupla(String password, Boolean isLogged){
        this.password = password;
        this.isLogged = isLogged;
    }
    //Metodo per impostare lo stato di login
    public void setLogged(boolean isLogged){
        this.isLogged = isLogged;
    }
    //Metodo per ottenere lo stato di login
    public Boolean getLogged(){
        return this.isLogged;
    }
    
    //Metodo per settare la password
    public void setPassword(String newPassword){
        this.password = newPassword;
    }

    //Metodo per ottenre la password
    public String getPassword(){
        return this.password;
    }

    public String toString() {
        return "Password: " + this.password + ", IsLogged: " + this.isLogged;
    }
}
