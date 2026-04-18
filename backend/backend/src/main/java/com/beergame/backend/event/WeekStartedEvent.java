package com.beergame.backend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WeekStartedEvent extends ApplicationEvent {

    private final String gameId;
    private final int    week;

    public WeekStartedEvent(Object source, String gameId, int week) {
        super(source);
        this.gameId = gameId;
        this.week   = week;
    }
}
