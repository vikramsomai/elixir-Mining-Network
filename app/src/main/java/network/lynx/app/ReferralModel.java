package network.lynx.app;

public class ReferralModel {
    private String username;
    int image;
    public  ReferralModel(String name,int image){
        this.username=name;
        this.image=image;
    }

    public int getImage() {
        return image;
    }

    public String getUsername() {
        return username;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
