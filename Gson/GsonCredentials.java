package Gson;
public class GsonCredentials extends Values{
    public String username;
    public String old_password;
    public String new_password;

    public GsonCredentials(String username, String old_password, String new_password){
        this.username = username;
        this.old_password = old_password;
        this.new_password = new_password;
    }
    public String getUsername(){
        return username;
    }

    public String getOldPassword(){
        return old_password;
    }

    public String getNewPassword(){
        return new_password;
    }
}
