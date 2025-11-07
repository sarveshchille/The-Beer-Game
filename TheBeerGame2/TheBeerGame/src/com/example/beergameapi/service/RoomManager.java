package com.example.beergameapi.service;

import com.example.beergameapi.model.GameRoom;
import com.example.beergameapi.model.Player;
import com.example.beergameapi.model.GameRoom.PlayerGameMapping; // Import inner class

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The new Singleton "brain" of the server.
 * This class manages all active 16-player GameRooms.
 */
public class RoomManager {

    // --- Singleton Setup ---
    private static final RoomManager instance = new RoomManager();
    public static RoomManager getInstance() {
        return instance;
    }
    // -----------------------

    private final Map<String, GameRoom> activeRooms;

    private RoomManager() {
        this.activeRooms = new HashMap<>();
    }

    /**
     * Creates a new, empty 16-player room.
     * @return The unique ID for the new room.
     */
    public String createRoom() {
        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 6);
        GameRoom newRoom = new GameRoom();
        activeRooms.put(roomId, newRoom);
        System.out.println("New room created: " + roomId);
        return roomId;
    }

    /**
     * Gets a specific room by its ID.
     */
    public GameRoom getRoom(String roomId) {
        return activeRooms.get(roomId);
    }

    /**
     * Calculates the final results for a finished room.
     * This now correctly calculates team scores based on initial team.
     *
     * @return A Map containing team results and the MVP.
     */
    public Map<String, Object> getResults(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            return Map.of("error", "Room not found.");
        }
        if (room.getStatus() != GameRoom.RoomStatus.FINISHED) {
            return Map.of("error", "Results are not ready. The game is still " + room.getStatus());
        }

        Map<String, Double> teamCosts = new HashMap<>();
        String mvpPlayerId = "";
        double mvpMinCost = Double.MAX_VALUE;
        
        Map<String, Double> allPlayerCosts = new HashMap<>();

        // "Un-shuffle" players to get results
        for (Map.Entry<String, PlayerGameMapping> entry : room.getPlayerAssignments().entrySet()) {
            String userId = entry.getKey();
            PlayerGameMapping mapping = entry.getValue();
            
            String initialTeamId = mapping.initialTeamId;
            double playerCost = mapping.getPlayer().getCumulativeCost();
            
            allPlayerCosts.put(userId, playerCost);

            // Check for MVP
            if (playerCost < mvpMinCost) {
                mvpMinCost = playerCost;
                mvpPlayerId = userId;
            }
            
            // Add to the player's initial team's total cost
            teamCosts.put(initialTeamId, teamCosts.getOrDefault(initialTeamId, 0.0) + playerCost);
        }
        
        // Find the winning team
        String winningTeamId = "";
        double winningMinCost = Double.MAX_VALUE;
        for (Map.Entry<String, Double> teamEntry : teamCosts.entrySet()) {
            if (teamEntry.getValue() < winningMinCost) {
                winningMinCost = teamEntry.getValue();
                winningTeamId = teamEntry.getKey();
            }
        }

        // Build the final results map
        Map<String, Object> results = new HashMap<>();
        results.put("mvp", Map.of("userId", mvpPlayerId, "totalCost", String.format("%.2f", mvpMinCost)));
        results.put("winningTeam", Map.of("teamId", winningTeamId, "totalCost", String.format("%.2f", winningMinCost)));
        
        // Format team costs for display
        Map<String, String> formattedTeamCosts = teamCosts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> String.format("%.2f", e.getValue())
            ));
        results.put("allTeamCosts", formattedTeamCosts);

        // Format player costs for display
        Map<String, String> formattedPlayerCosts = allPlayerCosts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> String.format("%.2f", e.getValue())
            ));
        results.put("allPlayerCosts", formattedPlayerCosts);
        
        return results;
    }
}

