package com.beergame.backend.dto;

import com.beergame.backend.model.Players;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerStateDTO(
        Long id,
        String userName,
        Players.RoleType role,
        int inventory,
        int backlog,
        int currentOrder,
        int incomingShipment,
        double weeklyCost,
        double totalCost,
        @JsonProperty("readyForNextTurn") boolean isReadyForNextTurn,
        int lastOrderReceived,
        String gameId
) {
    public static PlayerStateDTO fromPlayer(Players player) {
        return new PlayerStateDTO(
                player.getId(),
                player.getUserName(),
                player.getRole(),
                player.getInventory(),
                player.getBackOrder(),
                player.getCurrentOrder(),
                player.getIncomingShipment(),
                player.getWeeklyCost(),
                player.getTotalCost(),
                player.isReadyForOrder(),
                player.getLastOrderReceived(),
                // 👈 2. SAFELY GET THE GAME ID
                (player.getGame() != null) ? player.getGame().getId() : null);
    }
}