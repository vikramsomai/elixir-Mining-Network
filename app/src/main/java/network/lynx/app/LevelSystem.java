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
    public static int getNewLevel(Double minedCoins, int referrals) {
        int newLevel = 1;
        for (Level level : levels) {
            if (minedCoins >= level.minedCoinsRequired && referrals >= level.referralsRequired) {
                newLevel = level.level;
            }
        }
        return newLevel;
    }
}
