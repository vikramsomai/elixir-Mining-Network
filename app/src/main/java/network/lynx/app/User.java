package network.lynx.app;

import java.util.Map;

// Add to your User model class
public class User {
    // Existing fields...
    private long lastActive;
    private boolean isActive;
    private int consecutiveActiveDays;
    private Map<String, ReferralInfo> referrals;
    private Map<String, CommissionInfo> commissions;
    // Add these fields
}