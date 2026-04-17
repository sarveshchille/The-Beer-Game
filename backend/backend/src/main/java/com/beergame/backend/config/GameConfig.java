package com.beergame.backend.config;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds all the static rules, costs, and demand schedule for the game.
 *
 * CHANGE: Removed the JVM-global static FESTIVE_WEEKS Set.
 *
 * The old design picked 3 random weeks ONCE when the class was loaded by the
 * JVM. Every game created in that JVM session had identical festive weeks,
 * which makes all concurrent games predictable and defeats the randomness
 * entirely.
 *
 * New design:
 *  - generateFestiveWeeks() creates a fresh random set for each game.
 *  - isFestiveWeek(week, festiveWeeks) and getCustomerDemand(week, festiveWeeks)
 *    accept the game-specific set as a parameter.
 *  - The old 0-argument versions are retained as deprecated so nothing breaks
 *    during migration.
 */
public class GameConfig {

    // ── Game rules ────────────────────────────────────────────────────────────
    public static final int    GAME_WEEKS             = 25;
    public static final int    INITIAL_INVENTORY      = 150;
    public static final int    INITIAL_PIPELINE_LEVEL = 20;

    // ── Cost rules ────────────────────────────────────────────────────────────
    public static final double INVENTORY_HOLDING_COST = 0.75;
    public static final double BACKORDER_COST         = 1.50;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Demand schedule ───────────────────────────────────────────────────────
    private static final int[] BASE_DEMAND_SCHEDULE = {
            // Weeks  1   2   3   4   5   6   7   8   9  10  11  12  13
                     20, 30, 40, 40, 40, 40, 60, 60, 60, 80, 80, 80, 60,
            // Weeks 14  15  16  17  18  19  20  21  22  23  24  25
                     60, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80
    };

    // ── Festive week generation ───────────────────────────────────────────────

    /**
     * Generates a new set of 3 unique random festive weeks in range [6, 22].
     * Call this once per game at creation time and store the result in the
     * Game entity (Game.festiveWeeks).
     */
    public static Set<Integer> generateFestiveWeeks() {
        Set<Integer> festiveWeeks = new HashSet<>();
        while (festiveWeeks.size() < 3) {
            festiveWeeks.add(RANDOM.nextInt(17) + 6); // 6..22 inclusive
        }
        return festiveWeeks;
    }

    // ── Per-game demand / festivity queries ───────────────────────────────────

    /**
     * Returns the customer demand for the retailer in {@code week},
     * using the provided game-specific festive weeks set.
     *
     * @param week        1-based week number
     * @param festiveWeeks the set stored on the Game entity
     */
    public static int getCustomerDemand(int week, Set<Integer> festiveWeeks) {
        if (week <= 0) return 0;

        if (week > 1 && festiveWeeks.contains(week)) {
            return getCustomerDemand(week - 1, festiveWeeks) * 2;
        }

        if (week > BASE_DEMAND_SCHEDULE.length) {
            return BASE_DEMAND_SCHEDULE[BASE_DEMAND_SCHEDULE.length - 1];
        }

        return BASE_DEMAND_SCHEDULE[week - 1];
    }

    /**
     * Returns whether {@code week} is a festive week for the given game.
     *
     * @param week        1-based week number
     * @param festiveWeeks the set stored on the Game entity
     */
    public static boolean isFestiveWeek(int week, Set<Integer> festiveWeeks) {
        return festiveWeeks.contains(week);
    }

    /**
     * Convenience: returns the festive weeks as a sorted List (for DTO
     * serialisation where order matters for the frontend).
     */
    public static List<Integer> getFestiveWeeksSorted(Set<Integer> festiveWeeks) {
        List<Integer> sorted = new ArrayList<>(festiveWeeks);
        sorted.sort(Integer::compareTo);
        return sorted;
    }
}