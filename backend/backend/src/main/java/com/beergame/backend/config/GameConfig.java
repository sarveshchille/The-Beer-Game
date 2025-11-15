package com.beergame.backend.config;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Holds all the static rules, costs, and demand for the game.
 * Ported from your friend's code.
 */
public class GameConfig {

    // --- Game Rules ---
    public static final int GAME_WEEKS = 25;
    public static final int INITIAL_INVENTORY = 150;

    /**
     * The initial order level in the pipeline.
     * This will be the order that Wholesaler, Distributor, and Producer
     * receive in Week 1.
     */
    public static final int INITIAL_PIPELINE_LEVEL = 20;

    // --- Cost Rules ---
    public static final double INVENTORY_HOLDING_COST = 0.75; // 75 cents per unit
    public static final double BACKORDER_COST = 1.50; // 1.5 per unit

    /**
     * The base customer demand for the Retailer.
     * The first value is 20 to meet the Week 1 requirement.
     */
    private static final int[] BASE_DEMAND_SCHEDULE = {
            // Week: 1 2 3 4 5 6 7 8 9 10 11 12 13
            20, 30, 40, 40, 40, 40, 60, 80, 80, 80, 80, 80, 60,
            // Week: 14 15 16 17 18 19 20 21 22 23 24 25
            60, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80
    };

    /**
     * Stores the 3 randomly selected "festive weeks"
     * where demand will be double the previous week.
     */
    private static final Set<Integer> FESTIVE_WEEKS = new HashSet<>();

    /**
     * This `static` block runs once when the class is loaded.
     * It picks the 3 random festive weeks.
     */
    static {
        Random rand = new Random();
        // Loop until we have 3 unique weeks
        while (FESTIVE_WEEKS.size() < 3) {
            // Generates a random week from 6 to 22 (inclusive)
            int randomWeek = rand.nextInt(17) + 6; // (0-16) + 6 = 6-22
            FESTIVE_WEEKS.add(randomWeek);
        }
        System.out.println("--- Game Initialized with Festive Weeks: " + FESTIVE_WEEKS + " ---");
    }

    /**
     * Gets the customer demand for a given week.
     * This is ONLY for the Retailer.
     * 
     * @param week The current week (1-based)
     * @return The customer demand for that week.
     */
    public static int getCustomerDemand(int week) {
        if (week <= 0)
            return 0;

        // Check for festive week (but not for week 1)
        if (week > 1 && FESTIVE_WEEKS.contains(week)) {
            // Demand is double the *previous* week's demand
            // We use recursion to ensure this stacks correctly
            return getCustomerDemand(week - 1) * 2;
        }

        // Handle weeks outside the schedule (e.g., if GAME_WEEKS > 25)
        if (week > BASE_DEMAND_SCHEDULE.length) {
            return BASE_DEMAND_SCHEDULE[BASE_DEMAND_SCHEDULE.length - 1]; // Use last value
        }

        // Return the demand from the base schedule
        return BASE_DEMAND_SCHEDULE[week - 1]; // -1 for 0-based array index
    }

    public static boolean isFestiveWeek(int week) {
        return FESTIVE_WEEKS.contains(week);
    }

    /**
     * Public helper to get the complete list of festive weeks.
     * 
     * @return A List of the random festive week numbers.
     */
    public static List<Integer> getFestiveWeeks() {
        // Convert the Set to a List for the DTO
        return List.copyOf(FESTIVE_WEEKS);
    }
}