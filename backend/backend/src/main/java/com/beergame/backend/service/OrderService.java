package com.beergame.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.beergame.backend.event.AllPlayersReadyEvent;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    public static final int MAX_ORDER_AMOUNT = 9_999;

    private final RedisLockService redisLockService;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final BroadcastService broadcastService;
    private final ApplicationEventPublisher eventPublisher;
    private final TurnService turnService;
    private final TransactionTemplate transactionTemplate;

    public void placeOrder(String gameId, String username, int orderAmount, Integer targetWeek) {
        // 1. Wrap the entire order processing in the distributed lock
        redisLockService.executeWithLock(gameId, 10, () -> {
            
            // 2. Open transaction INSIDE the lock
            return transactionTemplate.execute(status -> {

                if (orderAmount < 0 || orderAmount > MAX_ORDER_AMOUNT) {
                    throw new IllegalArgumentException(
                            "Order amount must be 0–" + MAX_ORDER_AMOUNT + ", got: " + orderAmount);
                }

                Game game = gameRepository.findByIdWithPlayers(gameId)
                        .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

                // 3. Failsafe check to prevent stale bot orders corrupting the state
                if (targetWeek != null && game.getCurrentWeek() != targetWeek) {
                    log.warn("Stale order rejected! Tried to submit for week {} but game is on week {}", targetWeek, game.getCurrentWeek());
                    return null;
                }

                // 🚨 FIX: Extract player directly from the fetched collection!
                // If we use playerRepository.findByGameAndUserName, Hibernate might return
                // a different object reference, leaving the instance inside game.getPlayers() stale.
                // This caused allReady to erroneously evaluate to false, stranding the game in Week 1.
                Players player = game.getPlayers().stream()
                        .filter(p -> p.getUserName().equals(username))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Player not in game: " + username));

                if (player.isReadyForOrder()) {
                    log.warn("Player {} already submitted an order for week {}", username, game.getCurrentWeek());
                    return null;
                }

                player.setCurrentOrder(orderAmount);
                player.setReadyForOrder(true);
                playerRepository.save(player);

                log.info("Player {} placed order {} for week {}", username, orderAmount, game.getCurrentWeek());

                if (game.getPlayers() == null || game.getPlayers().size() < 4) {
                    broadcastService.broadcastGameAfterCommit(gameId);
                    return null;
                }

                boolean allReady = game.getPlayers().stream().allMatch(Players::isReadyForOrder);

                if (allReady) {
                    log.info("All players ready for game {}. Advancing turn.", gameId);
                    eventPublisher.publishEvent(
                            new AllPlayersReadyEvent(this, gameId, game.getCurrentWeek()));
                    turnService.advanceTurn(gameId);
                } else {
                    broadcastService.broadcastGameAfterCommit(gameId);
                }
                return null;
            });
        });
    }
}