package Gson;

public class GsonMessage <T extends Values> {
    public String operation;
    public T values;

    public GsonMessage(String operation, T values){
        this.operation = operation;
        this.values = values;
    }
    public String toString(){
        return "{ operation = " + this.operation + ", values = " + this.values.toString() + " }";
    }
}
