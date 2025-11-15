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

                // Convert each player → PlayerStateDTO
                List<PlayerStateDTO> playerStates = game.getPlayers().stream()
                                .map(PlayerStateDTO::fromPlayer)
                                .collect(Collectors.toList());

                if (!GameConfig.getFestiveWeeks().isEmpty()) {

                        log.info("SENDING COMPLETE LIST OF FESTIVES");

                } else {
                        log.error("SENDING EMPTY LIST OF FESTIVES");
                }

                return new GameStateDTO(
                                game.getId(),
                                game.getCurrentWeek(),
                                game.getGameStatus(),
                                playerStates,
                                game.isFestiveWeek(),
                                GameConfig.getFestiveWeeks());
        }
}
