package network.lynx.app;

public class Banner {
    private String imageUrl;
    private String link;

    public Banner() {
        // Default constructor required for Firebase
    }

    public Banner(String imageUrl, String link) {
        this.imageUrl = imageUrl;
        this.link = link;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
