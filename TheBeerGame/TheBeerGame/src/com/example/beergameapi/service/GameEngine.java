package com.example.beergameapi.service;

import com.example.beergameapi.model.GameConfig;
import com.example.beergameapi.model.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "brain" of the game. This is a Singleton class that manages
 * the game state, holds all the players, and executes the weekly simulation.
 * All API requests will talk to this single instance.
 *
 * FINAL VERSION:
 * - Uses correct player chain: Retailer -> Wholesaler -> Distributor -> Producer
 * - Implements correct 3-week delay (1-week order, 2-week shipment)
 * - All startup and logic bugs fixed.
 */
public class GameEngine {

    // --- Singleton Setup ---
    // This ensures only one instance of the game exists on the server.
    private static final GameEngine instance = new GameEngine();

    /**
     * Gets the single, shared instance of the GameEngine.
     * @return The singleton GameEngine instance.
     */
    public static GameEngine getInstance() {
        return instance;
    }
    // -----------------------

    // --- Game State ---
    private Map<String, Player> players;
    private Map<String, Integer> weeklyOrders; // Holds orders as they come in
    private int currentWeek;

    /**
     * Private constructor for the Singleton.
     * Initializes the game when the server first starts.
     */
    private GameEngine() {
        this.initializeGame();
    }

    /**
     * Sets up the game to its initial state (Week 1).
     * This is called by the constructor and can also be used to reset.
     */
    private void initializeGame() {
        
        // [FIX] Define the player roles array here as a local, final constant.
        // This solves the NullPointerException at startup.
        // [FIX] Uses the correct player chain.
        final String[] PLAYER_ROLES = {"Retailer", "Wholesaler", "Distributor", "Producer"};

        this.currentWeek = 1;
        this.players = new LinkedHashMap<>(); // Use LinkedHashMap to maintain order
        this.weeklyOrders = new LinkedHashMap<>();

        // Create the 4 players in their supply chain order
        for (String role : PLAYER_ROLES) {
            players.put(role, new Player(role));
        }
        System.out.println("--- New Beer Game Initialized (Chain: R -> W -> D -> P) ---");
        System.out.println("--- Waiting for orders for Week 1... ---");
    }

    // --- Public API Methods ---

    /**
     * Gets a specific player's current state.
     * @param role The role to get (e.g., "Retailer").
     * @return The Player object, or null if not found.
     */
    public Player getPlayer(String role) {
        return players.get(role);
    }

    /**
     * Gets the current game week.
     * @return The current week number (1-based).
     */
    public int getCurrentWeek() {
        return this.currentWeek;
    }

    /**
     * Checks if the game has finished.
     * @return true if the game is over, false otherwise.
     */
    public boolean isGameOver() {
        // The game runs FOR 25 weeks. It's over when we are *about to start* Week 26.
        return this.currentWeek > GameConfig.GAME_WEEKS;
    }

    /**
     * Resets the entire game back to Week 1.
     */
    public synchronized void resetGame() {
        this.initializeGame();
        System.out.println("--- GAME RESET by API call ---");
    }

    /**
     * This is the main entry point from the API.
     * A player submits their order, which is stored.
     * If this is the 4th order for the week, the simulation runs.
     *
     * @param role The role of the player submitting.
     * @param orderAmount The number of units they want to order.
     * @return A String message indicating the game's status.
     */
    public synchronized String receiveOrder(String role, int orderAmount) {
        if (isGameOver()) {
            return "Game over. Final week " + (currentWeek - 1) + " was completed.";
        }
        
        if (!players.containsKey(role)) {
            return "Error: Invalid role '" + role + "'.";
        }
        if (weeklyOrders.containsKey(role)) {
            return "Error: Order for '" + role + "' already submitted for Week " + currentWeek;
        }

        // Store the valid (non-negative) order
        int finalOrder = (orderAmount < 0) ? 0 : orderAmount;
        weeklyOrders.put(role, finalOrder);
        System.out.println("Received order from " + role + " for Week " + currentWeek + ": " + finalOrder);

        // Check if all 4 orders are in
        if (weeklyOrders.size() == 4) {
            // All orders are in! Run the simulation.
            runSimulationWeek();
            
            if (isGameOver()) {
                return "Final order received. Game is complete. Week " + (currentWeek - 1) + " was the last week.";
            } else {
                return "All 4 orders received. Week " + (currentWeek - 1) + " complete. Advanced to Week " + currentWeek + ".";
            }
        } else {
            int remaining = 4 - weeklyOrders.size();
            return "Order received. Waiting for " + remaining + " more player(s) for Week " + currentWeek + ".";
        }
    }

    /**
     * This private method runs the entire simulation for one week.
     * It is called by receiveOrder() only when all 4 orders are in.
     * The order of these steps is critical for the 3-week delay.
     */
    private void runSimulationWeek() {
        
        String[] playerRoles = players.keySet().toArray(new String[0]);

        // Run steps 1-4 for ALL players first.
        // This uses data from *previous* weeks (shipments arriving, orders arriving).
        for (String role : playerRoles) {
            Player p = players.get(role);
            p.step1_receiveIncomingShipment();       // Receive shipment from 2 weeks ago
            p.step2_receiveIncomingOrder(this.currentWeek); // Receive order from 1 week ago
            p.step3_fulfillOrder();                  // Fulfill that order
            p.step4_calculateWeeklyCost();           // Calculate costs
        }

        // Step 5: Place the new orders (from the API) into the player objects.
        for (String role : playerRoles) {
            int order = weeklyOrders.get(role);
            players.get(role).step5_placeOrder(order); // This is the order for *next* week
        }

        // Step 6: Advance pipelines (Pass orders UP, shipments DOWN)
        // This uses the data from Step 5 to set up future weeks.
        Player retailer = players.get("Retailer");
        Player wholesaler = players.get("Wholesaler"); // Corrected chain
        Player distributor = players.get("Distributor"); // Corrected chain
        Player producer = players.get("Producer");

        // --- Pass orders UP the chain (for next week) ---
        wholesaler.passOrderToPipeline(retailer.getLastOrderPlaced());   // Retailer orders from Wholesaler
        distributor.passOrderToPipeline(wholesaler.getLastOrderPlaced()); // Wholesaler orders from Distributor
        producer.passOrderToPipeline(distributor.getLastOrderPlaced());    // Distributor orders from Producer

        // --- Pass shipments DOWN the chain (for 2 weeks from now) ---
        producer.passShipmentToPipeline(producer.getLastOrderPlaced());     // Producer "ships" their own order
        distributor.passShipmentToPipeline(producer.getLastShipmentSent()); // Distributor gets from Producer
        wholesaler.passShipmentToPipeline(distributor.getLastShipmentSent());// Wholesaler gets from Distributor
        retailer.passShipmentToPipeline(wholesaler.getLastShipmentSent());  // Retailer gets from Wholesaler

        // --- Log history ---
        // This snapshots the state *after* all of this week's actions.
        for (String role : playerRoles) {
            players.get(role).logWeeklyState(this.currentWeek);
        }

        // --- Advance the week ---
        this.currentWeek++;
        this.weeklyOrders.clear(); // Get ready for the next week's orders

        System.out.println("--- Week " + (currentWeek - 1) + " Simulation Complete. Advancing to Week " + currentWeek + ". ---");
    }
}

