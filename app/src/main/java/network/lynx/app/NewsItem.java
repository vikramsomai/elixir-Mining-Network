package network.lynx.app;

public class NewsItem {
    private String category;
    private String title;
    private String description;
    private String date;
    private int imageRes; // or String imageUrl if loading from network

    public NewsItem(String category, String title, String description, String date, int imageRes) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.date = date;
        this.imageRes = imageRes;
    }

    // Getters
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
    public int getImageRes() { return imageRes; }
}