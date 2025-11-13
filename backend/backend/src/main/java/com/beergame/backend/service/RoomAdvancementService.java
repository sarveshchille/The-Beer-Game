package com.beergame.backend.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class RoomAdvancementService {

    private final GameService gameService;

    public RoomAdvancementService(@Lazy GameService gameService) {
        this.gameService = gameService;
    }

    @Async
    public CompletableFuture<Void> advanceGame(String gameId) {
        try {

            log.info("Starting async advance for game {}", gameId);
            gameService.advanceTurn(gameId);
            log.info("Finished async advance for game {}", gameId);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {

            log.error("Error in async game advance for {}: {}", gameId, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}