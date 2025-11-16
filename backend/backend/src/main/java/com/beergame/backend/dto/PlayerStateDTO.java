package com.beergame.backend.dto;

import com.beergame.backend.model.Players;

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
        boolean isReadyForNextTurn,
        int lastOrderReceived // 👈 --- 1. ADD THIS LINE
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
                player.getLastOrderReceived() // 👈 --- 2. ADD THIS LINE
        );
    }
}