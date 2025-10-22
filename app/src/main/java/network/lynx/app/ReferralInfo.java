package network.lynx.app;


public class ReferralInfo {
    private String userId;
    private String username;
    private long joinDate;
    private boolean isActive;
    private double totalCommission;

    // Default constructor for Firebase
    public ReferralInfo() {
    }

    public ReferralInfo(String userId, String username, long joinDate) {
        this.userId = userId;
        this.username = username;
        this.joinDate = joinDate;
        this.isActive = false;
        this.totalCommission = 0.0;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(long joinDate) {
        this.joinDate = joinDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public double getTotalCommission() {
        return totalCommission;
    }

    public void setTotalCommission(double totalCommission) {
        this.totalCommission = totalCommission;
    }
}