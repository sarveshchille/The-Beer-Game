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
        double totalCost, // FIXED — correctly using totalCost
        boolean isReadyForNextTurn) {
    public static PlayerStateDTO fromPlayer(Players player) {
        return new PlayerStateDTO(
                player.getId(),
                player.getUserName(),
                player.getRole(),
                player.getInventory(),
                player.getBackOrder(),
                player.getCurrentOrder(),
                player.getIncomingShipment(),
                player.getTotalCost(), // FIXED (was weeklyCost before)
                player.isReadyForOrder());
    }
}
