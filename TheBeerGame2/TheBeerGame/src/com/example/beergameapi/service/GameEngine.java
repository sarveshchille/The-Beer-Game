package com.example.beergameapi.service;

import com.example.beergameapi.model.GameConfig;
import com.example.beergameapi.model.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * [REFACTORED for 16-Player Tournament]
 * This class is NO LONGER a Singleton. It is a "calculator" that holds
 * the state for one 4-player game. The GameRoom creates 4 instances of this.
 */
public class GameEngine {

    // --- Game State ---
    private Map<String, Player> players;
    private int currentWeek;

    /**
     * Creates a new, independent 4-player Beer Game simulation.
     */
    public GameEngine() {
        this.initializeGame();
    }

    /**
     * Sets up the game to its initial state (Week 1).
     */
    private void initializeGame() {
        // This is the correct, final player chain
        final String[] PLAYER_ROLES = {"Retailer", "Wholesaler", "Distributor", "Producer"};

        this.currentWeek = 1;
        this.players = new LinkedHashMap<>();

        for (String role : PLAYER_ROLES) {
            players.put(role, new Player(role));
        }
    }

    // --- Public Getters ---
    public Player getPlayer(String role) {
        return players.get(role);
    }
    public int getCurrentWeek() {
        return this.currentWeek;
    }
    public boolean isGameOver() {
        return this.currentWeek > GameConfig.GAME_WEEKS;
    }

    /**
     * [THIS IS THE FIX]
     * This method is now called by the GameRoom's thread pool.
     * It runs the entire 6-step simulation for one week using
     * the orders provided by the Room.
     *
     * @param orders A map of {"Retailer": 10, "Wholesaler": 12, ...}
     */
    public synchronized void runSimulationWeek(Map<String, Integer> orders) {
        if (isGameOver()) {
            return;
        }

        String[] playerRoles = players.keySet().toArray(new String[0]);

        // Run steps 1-4 for ALL players
        for (String role : playerRoles) {
            Player p = players.get(role);
            p.step1_receiveIncomingShipment();
            p.step2_receiveIncomingOrder(this.currentWeek);
            p.step3_fulfillOrder();
            p.step4_calculateWeeklyCost();
        }

        // Step 5: Place the orders provided by the GameRoom
        for (String role : playerRoles) {
            int order = orders.getOrDefault(role, 0);
            players.get(role).step5_placeOrder(order);
        }

        // Step 6: Advance pipelines (Pass orders UP, shipments DOWN)
        Player retailer = players.get("Retailer");
        Player wholesaler = players.get("Wholesaler");
        Player distributor = players.get("Distributor");
        Player producer = players.get("Producer");

        // --- Pass orders UP the chain (Corrected Flow) ---
        wholesaler.passOrderToPipeline(retailer.getLastOrderPlaced());
        distributor.passOrderToPipeline(wholesaler.getLastOrderPlaced());
        producer.passOrderToPipeline(distributor.getLastOrderPlaced());

        // --- Pass shipments DOWN the chain (Corrected Flow) ---
        producer.passShipmentToPipeline(producer.getLastOrderPlaced());
        distributor.passShipmentToPipeline(producer.getLastShipmentSent());
        wholesaler.passShipmentToPipeline(distributor.getLastShipmentSent());
        retailer.passShipmentToPipeline(wholesaler.getLastShipmentSent());

        // --- Log history ---
        for (String role : playerRoles) {
            players.get(role).logWeeklyState(this.currentWeek);
        }

        // --- Advance the week ---
        this.currentWeek++;
    }
}

