package network.lynx.app;

import java.util.List;

public class LevelSystem {
    public static class Level {
        int level;
        int minedCoinsRequired;
        int referralsRequired;
        int miningBonus;

        public Level(int level, int minedCoinsRequired, int referralsRequired, int miningBonus) {
            this.level = level;
            this.minedCoinsRequired = minedCoinsRequired;
            this.referralsRequired = referralsRequired;
            this.miningBonus = miningBonus;
        }
    }

    public static List<Level> levels = List.of(
            new Level(1, 0, 0, 0),
            new Level(2, 100, 1, 5),
            new Level(3, 500, 3, 10),
            new Level(4, 2000, 7, 15),
            new Level(5, 5000, 15, 20),
            new Level(6, 10000, 30, 25),
            new Level(7, 25000, 50, 30),
            new Level(8, 50000, 75, 35),
            new Level(9, 100000, 100, 40),
            new Level(10, 250000, 150, 50)
    );

    public static int getNewLevel(double totalCoins, int referrals) {
        // Logic based on coins & referrals.
        if (totalCoins > 1000) return 5;
        else if (totalCoins > 500) return 4;
        else if (totalCoins > 250) return 3;
        else if (totalCoins > 100) return 2;
        return 1;
    }

    public static String getLevelBenefits(int level) {
        switch (level) {
            case 2: return "• Boost Speed x1.2\n• Daily Bonus +10%";
            case 3: return "• Boost Speed x1.5\n• Daily Bonus +20%";
            case 4: return "• Boost Speed x2\n• Ad-free Experience";
            case 5: return "• VIP Badge\n• Access to Premium Features";
            default: return "• Keep going to unlock rewards!";
        }
    }

}
