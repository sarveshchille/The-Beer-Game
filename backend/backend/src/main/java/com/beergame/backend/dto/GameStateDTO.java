package com.beergame.backend.dto;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import java.util.List;
import java.util.stream.Collectors;

public record GameStateDTO(
    String gameId,
    int currentWeek,
    Game.GameStatus gameStatus,
    List<PlayerStateDTO> players
) {
    public static GameStateDTO fromGame(Game game) {
        List<PlayerStateDTO> playerStates = game.getPlayers().stream()
                .map(PlayerStateDTO::fromPlayer)
                .collect(Collectors.toList());
                
        return new GameStateDTO(game.getId(), game.getCurrentWeek(), game.getGameStatus(), playerStates);
    }
}

record PlayerStateDTO(
    Long id,
    String userName,
    Players.RoleType role,
    int inventory,
    int backlog,
    int currentOrder,
    int incomingShipment,
    double totalCost,
    boolean isReadyForNextTurn
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
            player.getWeeklyCost(), // This should be player.getTotalCost()
            player.isReadyForOrder()
        );
    }
}