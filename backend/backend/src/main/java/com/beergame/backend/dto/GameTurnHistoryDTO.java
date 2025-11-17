package com.beergame.backend.dto;

import com.beergame.backend.model.GameTurn;

public record GameTurnHistoryDTO(
        int weekDay,
        int demandRecieved, // keep same spelling as entity
        int orderPlaced,
        int shipmentSent,
        int shipmentRecieved,
        int inventoryAtEndOfWeek,
        int backOrderAtEndOfWeek,
        double weeklyCost,
        double totalCost) {
    public static GameTurnHistoryDTO fromEntity(GameTurn t) {
        return new GameTurnHistoryDTO(
                t.getWeekDay(),
                t.getDemandRecieved(),
                t.getOrderPlaced(),
                t.getShipmentSent(),
                t.getShipmentRecieved(),
                t.getInventoryAtEndOfWeek(),
                t.getBackOrderAtEndOfWeek(),
                t.getWeeklyCost(),
                t.getTotalCost());
    }
}
