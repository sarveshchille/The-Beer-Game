package com.example.beergameapi.model;

import java.util.Queue;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a single player in the supply chain.
 * Holds all game state (inventory, costs) and logic (pipelines) for one role.
 * This class also records its own history week by week.
 *
 * FINAL VERSION
 */
public class Player {

    // --- Inner class to store weekly history ---
    /**
     * A simple data-only class to hold a snapshot of the
     * player's state at the end of a single week.
     * The fields match the API requirements.
     */
    public static class WeeklyState {
        public final int week;
        public final int inventory;
        public final int backorder;
        public final double costs; // Renamed from weeklyCost
        public final double cumulativeCost;
        
        public final int customerOrders;   // The order this player *received*
        public final int incomingDelivery; // The shipment this player *received*
        public final int outgoingDelivery; // The shipment this player *sent*
        public final int newOrderPlaced;   // The new order this player *placed*

        public WeeklyState(int week, int inventory, int backorder, double costs, double cumulativeCost,
                           int customerOrders, int incomingDelivery, int outgoingDelivery, int newOrderPlaced) {
            this.week = week;
            this.inventory = inventory;
            this.backorder = backorder;
            this.costs = costs;
            this.cumulativeCost = cumulativeCost;
            this.customerOrders = customerOrders;
            this.incomingDelivery = incomingDelivery;
            this.outgoingDelivery = outgoingDelivery;
            this.newOrderPlaced = newOrderPlaced;
        }
    }
    // ------------------------------------------------

    // Player Role
    private String role;

    // Core State
    private int inventory;
    private int backorder;

    // Cost State
    private double cumulativeCost;
    private double lastWeeklyCost; // for logging

    // Order/Shipment Pipelines (Queues)
    private Queue<Integer> incomingOrderPipeline;
    private Queue<Integer> incomingShipmentPipeline;

    // History & Logging
    private int lastShipmentReceived; // Used for "incomingDelivery"
    private int lastOrderReceived;    // Used for "customerOrders"
    private int lastShipmentSent;     // Used for "outgoingDelivery"
    private int lastOrderPlaced;      // Used for "newOrderPlaced"
    private List<WeeklyState> weeklyHistory; // List to store all snapshots

    /**
     * Creates a new player for a given role.
     * @param role The player's role (e.g., "Retailer")
     */
    public Player(String role) {
        this.role = role;
        this.inventory = GameConfig.INITIAL_INVENTORY;
        this.backorder = 0;
        this.cumulativeCost = 0;
        this.lastWeeklyCost = 0;
        this.lastShipmentReceived = 0;
        this.lastOrderReceived = 0;
        this.lastShipmentSent = 0;
        this.lastOrderPlaced = 0;
        
        this.weeklyHistory = new ArrayList<>(); 

        // Initialize pipelines
        this.incomingOrderPipeline = new LinkedList<>();
        this.incomingShipmentPipeline = new LinkedList<>();

        // Pre-fill pipelines to simulate initial "in-transit" stock
        // A 1-week order delay and 2-week shipment delay = 3 total
        for (int i = 0; i < 3; i++) {
            if (i < 1) { // 1-week order delay
                this.incomingOrderPipeline.add(GameConfig.INITIAL_PIPELINE_LEVEL);
            }
            if (i < 2) { // 2-week shipment delay
                this.incomingShipmentPipeline.add(GameConfig.INITIAL_PIPELINE_LEVEL);
            }
        }
    }

    // --- Core 6 Simulation Steps ---

    /**
     * STEP 1: Receive incoming shipment from the pipeline.
     */
    public void step1_receiveIncomingShipment() {
        this.lastShipmentReceived = this.incomingShipmentPipeline.poll();
        this.inventory += this.lastShipmentReceived;
    }

    /**
     * STEP 2: Receive incoming order from the pipeline.
     * This is the customer order for the Retailer.
     * @param week The current week (for customer demand lookup)
     */
    public void step2_receiveIncomingOrder(int week) {
        if ("Retailer".equals(this.role)) {
            // Retailer gets demand from the game config
            this.lastOrderReceived = GameConfig.getCustomerDemand(week);
        } else {
            // Other players get orders from their downstream partner
            this.lastOrderReceived = this.incomingOrderPipeline.poll();
        }
    }

    /**
     * STEP 3: Fulfill the order.
     * Fills as much as possible, updating inventory and backorders.
     */
    public void step3_fulfillOrder() {
        int totalDemand = this.lastOrderReceived + this.backorder;
        
        if (this.inventory >= totalDemand) {
            // Full demand is met
            this.lastShipmentSent = totalDemand;
            this.inventory -= totalDemand;
            this.backorder = 0;
        } else {
            // Partial fulfillment
            this.lastShipmentSent = this.inventory;
            this.backorder = totalDemand - this.inventory;
            this.inventory = 0;
        }
    }

    /**
     * STEP 4: Calculate costs.
     * Calculates holding and backorder costs and adds to cumulative.
     */
    public void step4_calculateWeeklyCost() {
        double holdingCost = this.inventory * GameConfig.INVENTORY_HOLDING_COST;
        double backorderCost = this.backorder * GameConfig.BACKORDER_COST;
        
        this.lastWeeklyCost = holdingCost + backorderCost;
        this.cumulativeCost += this.lastWeeklyCost;
    }

    /**
     * STEP 5: Place a new order. (Stores the order for the engine)
     * @param amount The number of units to order.
     */
    public void step5_placeOrder(int amount) {
        this.lastOrderPlaced = amount;
    }

    // STEP 6: "Advance pipelines" is handled by the GameEngine.

    // --- Pipeline Management Methods (Called by GameEngine) ---

    public void passOrderToPipeline(int orderAmount) {
        this.incomingOrderPipeline.add(orderAmount);
    }

    public void passShipmentToPipeline(int shipmentAmount) {
        this.incomingShipmentPipeline.add(shipmentAmount);
    }

    // --- History Logging Method ---
    /**
     * Creates a snapshot of the current state and
     * adds it to the weekly history list.
     */
    public void logWeeklyState(int week) {
        WeeklyState state = new WeeklyState(
            week,
            this.inventory,
            this.backorder,
            this.lastWeeklyCost,      // costs
            this.cumulativeCost,
            this.lastOrderReceived,   // customerOrders
            this.lastShipmentReceived,// incomingDelivery
            this.lastShipmentSent,    // outgoingDelivery
            this.lastOrderPlaced      // newOrderPlaced
        );
        this.weeklyHistory.add(state);
    }


    // --- Public Getters (for API) ---
    public String getRole() { return role; }
    public int getInventory() { return inventory; }
    public int getBackorder() { return backorder; }
    public double getCumulativeCost() { return cumulativeCost; }
    public int getLastShipmentReceived() { return lastShipmentReceived; }
    public int getLastOrderReceived() { return lastOrderReceived; }
    public int getLastShipmentSent() { return lastShipmentSent; }
    public double getLastWeeklyCost() { return lastWeeklyCost; }
    public int getLastOrderPlaced() { return lastOrderPlaced; }

    public List<WeeklyState> getWeeklyHistory() {
        return this.weeklyHistory;
    }
}

