package network.lynx.app;

public class LeaderBoardModel {
    private String uid;
    private String username;
    private String image;
    private Double coins;

    public LeaderBoardModel(String uid, String username, String image, double coins) {
        this.uid = uid;
        this.username = username;
        this.image = image;
        this.coins = coins;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Double getCoins() {
        return coins;
    }

    public void setCoins(Double coins) {
        this.coins = coins;
    }
}