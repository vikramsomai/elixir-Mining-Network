package network.lynx.app;

public class NotificationInfo {
    private String id;
    private String type;
    private String title;
    private String message;
    private String senderId;
    private String senderName;
    private long timestamp;
    private boolean read;

    // Default constructor for Firebase
    public NotificationInfo() {
    }

    public NotificationInfo(String id, String type, String title, String message,
                            String senderId, String senderName, long timestamp, boolean read) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.senderId = senderId;
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.read = read;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}