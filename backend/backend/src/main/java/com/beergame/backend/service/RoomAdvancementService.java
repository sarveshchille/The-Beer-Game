package com.beergame.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomAdvancementService {

    private final GameService gameService; 

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