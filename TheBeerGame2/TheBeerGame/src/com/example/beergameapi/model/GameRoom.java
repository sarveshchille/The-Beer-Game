package com.example.beergameapi.model;

import com.example.beergameapi.service.GameEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [REFACTORED]
 * Holds the state for one 16-player "Room" (tournament).
 * Manages the 4 concurrent games and all player mappings.
 * Handles the 16-player "lock-step" synchronization and multithreading.
 * This class is managed by the RoomManager.
 */
public class GameRoom {

    /**
     * Defines the current state of the game room.
     */
    public enum RoomStatus { WAITING_FOR_PLAYERS, RUNNING, FINISHED }

    // --- Inner Classes ---
    /**
     * Holds the mapping for a player to their specific game, role,
     * and (most importantly) their initial team ID.
     */
    public static class PlayerGameMapping {
        public final GameEngine game;
        public final String role;
        public final String initialTeamId;
        
        public PlayerGameMapping(GameEngine game, String role, String initialTeamId) {
            this.game = game;
            this.role = role;
            this.initialTeamId = initialTeamId;
        }
        
        /** Gets the Player object from the assigned GameEngine. */
        public Player getPlayer() {
            return game.getPlayer(role);
        }
    }

    /** Simple class to hold a player's info (and their initial role) as they join. */
    public static class PlayerInfo {
        public final String userId;
        public final String role; // Their initial role
        
        public PlayerInfo(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }
    
    // --- Room State ---
    private RoomStatus status;
    private final GameEngine[] games; // The 4 independent GameEngine instances
    
    // Stores players as they join: Map<InitialTeamId, Map<InitialRole, PlayerInfo>>
    private final Map<String, Map<String, PlayerInfo>> waitingPlayers; 
    
    // Final assignments: Map<UserId, PlayerGameMapping>
    private final Map<String, PlayerGameMapping> playerAssignments;
    
    // --- Synchronization & Multithreading ---
    private final ExecutorService gameExecutor; // Thread pool for the 4 games
    private final Map<String, Integer> currentWeekOrders; // Holds all 16 orders
    private volatile boolean isAdvancingWeek = false; // "Lock" to prevent new orders during simulation

    /**
     * Creates a new, empty GameRoom.
     * It immediately creates the 4 independent GameEngine instances
     * and a thread pool to run them.
     */
    public GameRoom() {
        this.status = RoomStatus.WAITING_FOR_PLAYERS;
        this.waitingPlayers = new HashMap<>();
        this.playerAssignments = new HashMap<>();
        this.currentWeekOrders = new ConcurrentHashMap<>();
        
        // Create a thread pool with 4 threads, one for each game
        this.gameExecutor = Executors.newFixedThreadPool(4);
        
        // Create the 4 independent GameEngine instances
        this.games = new GameEngine[4];
        for (int i = 0; i < 4; i++) {
            this.games[i] = new GameEngine();
        }
    }
    
    // --- Public Getters ---
    
    public RoomStatus getStatus() { return this.status; }
    
    public int getPlayerCount() {
        // Count total players in the nested waiting map
        return waitingPlayers.values().stream().mapToInt(Map::size).sum();
    }
    
    public Map<String, PlayerGameMapping> getPlayerAssignments() {
        return this.playerAssignments;
    }

    /**
     * Adds a player to the waiting list with their initial team AND role.
     * Validates that a team doesn't have duplicate roles.
     * @param userId A unique ID for the player.
     * @param initialTeamId The ID of the team they are joining with (e.g., "Team-A")
     * @param role The player's role *within* their initial team.
     * @return A status message for the API.
     */
    public synchronized String addPlayer(String userId, String initialTeamId, String role) {
        if (this.status != RoomStatus.WAITING_FOR_PLAYERS) {
            return "Error: This room is already running or finished.";
        }
        
        // Validate Role
        if (!List.of("Retailer", "Wholesaler", "Distributor", "Producer").contains(role)) {
            return "Error: Invalid role. Must be Retailer, Wholesaler, Distributor, or Producer.";
        }

        Map<String, PlayerInfo> team = waitingPlayers.computeIfAbsent(initialTeamId, k -> new HashMap<>());

        // Validate duplicates
        if (team.containsKey(role)) {
            return "Error: Your team '" + initialTeamId + "' already has a " + role + ".";
        }
        
        // Check if this userId is already in another team
        for(Map<String, PlayerInfo> t : waitingPlayers.values()) {
            if(t.values().stream().anyMatch(p -> p.userId.equals(userId))) {
                return "Error: Player " + userId + " is already in this room.";
            }
        }

        // Add the player
        team.put(role, new PlayerInfo(userId, role));
        int totalPlayers = getPlayerCount();
        
        // Check if this is the 16th player
        if (totalPlayers == 16) {
            // Check if we have 4 full teams of 4 players
            if (waitingPlayers.size() == 4 && waitingPlayers.values().stream().allMatch(t -> t.size() == 4)) {
                startGame();
                return "You were the 16th player! The game is starting NOW.";
            } else {
                // 16 players, but unbalanced teams. Reset this player.
                team.remove(role);
                return "Error: 16 players reached, but teams are not balanced (4 teams of 4). Please check your team IDs.";
            }
        }
        return "Joined room. Waiting for " + (16 - totalPlayers) + " more players.";
    }

    /**
     * The "Shuffle" logic. Called when the 16th player joins.
     * This method performs a "Latin Square" assignment to ensure
     * no two players from the same initial team play in the same game,
     * and that roles are fairly distributed.
     */
    private void startGame() {
        this.status = RoomStatus.RUNNING;
        
        List<Map<String, PlayerInfo>> teamsList = new ArrayList<>(waitingPlayers.values());
        List<String> teamIds = new ArrayList<>(waitingPlayers.keySet());
        String[] roles = {"Retailer", "Wholesaler", "Distributor", "Producer"};

        // Latin Square Shuffle:
        // Game i gets 4 players.
        // Player j in game i comes from Team j.
        // The *initial role* we pick from Team j is (i+j)%4.
        // The *new role* they play in Game i is roles[j].
        
        for (int i = 0; i < 4; i++) { // i = Game number (0-3)
            GameEngine currentGame = this.games[i];
            
            for (int j = 0; j < 4; j++) { // j = Initial Team index (0-3)
                
                String initialRoleToGet = roles[(i + j) % 4]; // Get player with this role from team j
                String newRoleToPlay = roles[j];              // Assign them this role in game i
                
                PlayerInfo playerInfo = teamsList.get(j).get(initialRoleToGet);
                String initialTeamId = teamIds.get(j);
                                        
                PlayerGameMapping mapping = new PlayerGameMapping(
                    currentGame, 
                    newRoleToPlay, 
                    initialTeamId
                );
                
                // Store the final mapping by the player's unique ID
                this.playerAssignments.put(playerInfo.userId, mapping);
            }
        }
        
        this.waitingPlayers.clear();
        System.out.println("Room has started! All 16 players shuffled and assigned to 4 games.");
    }
    
    /**
     * Submits an order for a single player.
     * This is synchronized so that only one order is processed at a time.
     * When the 16th order is received, it triggers all 4 games
     * to run their simulations in parallel.
     */
    public synchronized String submitOrder(String userId, int orderAmount) {
        if (this.status != RoomStatus.RUNNING) {
            return "Error: The game is not running.";
        }
        // Check the "lock"
        if (isAdvancingWeek) {
            return "Error: The server is currently processing the week. Please wait.";
        }
        if (currentWeekOrders.containsKey(userId)) {
            return "Error: You have already submitted your order for this week.";
        }
        
        PlayerGameMapping mapping = this.playerAssignments.get(userId);
        if (mapping == null) {
            return "Error: You are not a player in this game.";
        }
        if (mapping.game.isGameOver()) {
            return "Error: Your game is already finished.";
        }

        // Store the order
        currentWeekOrders.put(userId, orderAmount);
        
        // Check if all 16 orders are in
        if (currentWeekOrders.size() == 16) {
            // All orders are in! Set the "lock" and start the parallel simulation.
            this.isAdvancingWeek = true;
            advanceAllGames();
            return "Order received. All 16 orders are in. Advancing to next week.";
        }
        
        return "Order received. Waiting for " + (16 - currentWeekOrders.size()) + " more players.";
    }
    
    /**
     * Runs all 4 games in parallel on a thread pool.
     * Uses a CountDownLatch to wait for all games to finish
     * before unlocking the next week.
     */
    private void advanceAllGames() {
        // A latch for 4 threads (our 4 games)
        final CountDownLatch latch = new CountDownLatch(4);
        
        for (final GameEngine game : games) {
            // Collect the 4 orders for *this specific game*
            final Map<String, Integer> gameOrders = new HashMap<>();
            for (Map.Entry<String, PlayerGameMapping> entry : playerAssignments.entrySet()) {
                if (entry.getValue().game == game) {
                    String userId = entry.getKey();
                    String role = entry.getValue().role;
                    int order = currentWeekOrders.get(userId);
                    gameOrders.put(role, order);
                }
            }

            // Submit the simulation task to the thread pool
            gameExecutor.submit(() -> {
                try {
                    game.runSimulationWeek(gameOrders);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown(); // This game simulation is done
                }
            });
        }

        // Create a "waiter" thread that unlocks the game
        // This allows the submitOrder() method to return an instant "processing" message
        new Thread(() -> {
            try {
                latch.await(); // Wait for all 4 games to finish
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // Now, finalize the week (this part must be synchronized)
            synchronized (this) {
                currentWeekOrders.clear(); // Clear orders for next week
                
                if (areAllGamesFinished()) {
                    status = RoomStatus.FINISHED;
                    gameExecutor.shutdown(); // Stop the thread pool
                    System.out.println("Room FINISHED. All games complete.");
                } else {
                    // All games are on the same week, so we can check any game
                    System.out.println("Room advanced to week " + games[0].getCurrentWeek());
                }
                
                isAdvancingWeek = false; // Unlock for next week's orders
            }
        }).start();
    }
    
    /**
     * Helper method to check if all 4 games are over.
     */
    private boolean areAllGamesFinished() {
        for (GameEngine game : games) {
            if (!game.isGameOver()) {
                return false;
            }
        }
        return true;
    }
}

