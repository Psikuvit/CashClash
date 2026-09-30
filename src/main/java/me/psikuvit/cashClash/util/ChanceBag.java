package me.psikuvit.cashClash.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Fixed luck" rolls. A chance is dealt out per player as a shuffled bag of outcomes instead of an
 * independent roll every time: 25% means exactly one success in every 4 rolls, at a random point
 * within each cycle of 4, so long streaks of good or bad luck can't happen. A player's bag is
 * rebuilt when it runs out or when the configured chance changes.
 */
public class ChanceBag {

    private static final int HUNDREDTHS_PER_PERCENT = 100;

    private final Map<UUID, Deque<Boolean>> bags = new HashMap<>();
    private final Map<UUID, Double> bagChances = new HashMap<>();

    /**
     * Draws the player's next outcome for a chance given in percent (0-100).
     */
    public boolean roll(UUID player, double chancePercent) {
        if (chancePercent <= 0) return false;
        if (chancePercent >= 100) return true;

        Deque<Boolean> bag = bags.get(player);
        if (bag == null || bag.isEmpty() || bagChances.get(player) != chancePercent) {
            bag = newBag(chancePercent);
            bags.put(player, bag);
            bagChances.put(player, chancePercent);
        }
        return Boolean.TRUE.equals(bag.poll());
    }

    /**
     * Starts the player's next roll from a fresh bag.
     */
    public void reset(UUID player) {
        bags.remove(player);
        bagChances.remove(player);
    }

    public void clear() {
        bags.clear();
        bagChances.clear();
    }

    private static Deque<Boolean> newBag(double chancePercent) {
        int total = 100 * HUNDREDTHS_PER_PERCENT;
        int successes = (int) Math.round(chancePercent * HUNDREDTHS_PER_PERCENT);
        int divisor = gcd(successes, total);
        successes /= divisor;
        total /= divisor;

        List<Boolean> outcomes = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            outcomes.add(i < successes);
        }
        Collections.shuffle(outcomes, ThreadLocalRandom.current());
        return new ArrayDeque<>(outcomes);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
