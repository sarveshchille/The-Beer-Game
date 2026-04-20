package com.beergame.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GameFinishedEvent extends ApplicationEvent {

    private final String gameId;

    public GameFinishedEvent(Object source, String gameId) {
        super(source);
        this.gameId = gameId;
    }
}
