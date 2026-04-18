package com.beergame.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by AfkDetectionService when a bot order needs to be placed
 * for an AFK player. GameService listens and calls placeOrder().
 * Breaks: AfkDetectionService → GameService
 */
@Getter
public class AfkOrderRequestEvent extends ApplicationEvent {

    private final String gameId;
    private final String username;
    private final int    orderAmount;

    public AfkOrderRequestEvent(Object source, String gameId, String username, int orderAmount) {
        super(source);
        this.gameId       = gameId;
        this.username     = username;
        this.orderAmount  = orderAmount;
    }
}