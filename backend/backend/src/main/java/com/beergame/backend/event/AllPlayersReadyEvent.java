package com.beergame.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by GameService when all players have submitted their order.
 * AfkDetectionService listens and clears the AFK timer.
 * Breaks: GameService → AfkDetectionService
 */
@Getter
public class AllPlayersReadyEvent extends ApplicationEvent {

    private final String gameId;
    private final int    week;

    public AllPlayersReadyEvent(Object source, String gameId, int week) {
        super(source);
        this.gameId = gameId;
        this.week   = week;
    }
}