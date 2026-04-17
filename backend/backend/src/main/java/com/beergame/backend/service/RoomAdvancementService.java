package com.beergame.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Runs individual game advances asynchronously so all four games in a room
 * advance in parallel rather than sequentially.
 *
 * Change from original:
 *   Was: injected GameService via @Lazy to avoid circular dep with GameService
 *        → RoomAdvancementService → GameService.
 *   Now: injects TurnService directly — no cycle, no @Lazy needed.
 */
@Service
@Slf4j
public class RoomAdvancementService {

    private final TurnService turnService;

    public RoomAdvancementService(TurnService turnService) {
        this.turnService = turnService;
    }

    /**
     * Advances a single game turn asynchronously.
     * Spring's @Async runs this on a thread-pool thread with no ambient
     * transaction, so TurnService.advanceTurn()'s @Transactional opens
     * a fresh transaction of its own — exactly what we want.
     */
    @Async
    public CompletableFuture<Void> advanceGame(String gameId) {
        try {
            log.info("Async advance started for game {}", gameId);
            turnService.advanceTurn(gameId);
            log.info("Async advance complete for game {}", gameId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Error in async advance for game {}: {}", gameId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}