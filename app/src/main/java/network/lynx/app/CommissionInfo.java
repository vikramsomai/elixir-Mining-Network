package network.lynx.app;



public class CommissionInfo {
    private String id;
    private String fromUserId;
    private String fromUsername;
    private double amount;
    private long timestamp;
    private String type; // "mining", "mining_tier2", etc.

    // Default constructor for Firebase
    public CommissionInfo() {
    }

    public CommissionInfo(String id, String fromUserId, String fromUsername, double amount, String type) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}