package com.beergame.backend.dto;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.model.Game;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public record GameStateDTO(
        String gameId,
        int currentWeek,
        Game.GameStatus gameStatus,
        List<PlayerStateDTO> players,
        boolean isFestive,
        List<Integer> festiveWeeks) {

    public static GameStateDTO fromGame(Game game) {
        List<PlayerStateDTO> playerStates = game.getPlayers().stream()
                .map(PlayerStateDTO::fromPlayer)
                .collect(Collectors.toList());

        // FIX: use the game's own festiveWeeks (stored in DB per-game)
        // instead of GameConfig's JVM-global static set, which was shared
        // across all games and made every game's festive weeks identical.
        List<Integer> festiveWeekList = GameConfig.getFestiveWeeksSorted(game.getFestiveWeeks());

        if (festiveWeekList.isEmpty()) {
            log.warn("Game {} has no festive weeks set — was generateFestiveWeeks() called at creation?",
                    game.getId());
        }

        return new GameStateDTO(
                game.getId(),
                game.getCurrentWeek(),
                game.getGameStatus(),
                playerStates,
                game.isFestiveWeek(),
                festiveWeekList);
    }
}