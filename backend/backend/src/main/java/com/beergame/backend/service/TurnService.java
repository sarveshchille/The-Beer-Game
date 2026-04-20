package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.event.WeekStartedEvent;
import com.beergame.backend.event.GameFinishedEvent;
import com.beergame.backend.model.*;
import com.beergame.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles game-turn advancement logic.
 *
 * WHY a separate bean?
 * When advanceTurn() was in GameService and called via `this.advanceTurn()`,
 * Spring's proxy was bypassed — meaning @Transactional had no effect. Moving
 * it here means every call goes through the proxy and transaction semantics
 * are respected. The `self` injection hack is no longer needed anywhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TurnService {

    private final GameRepository     gameRepository;
    private final PlayerRepository   playerRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameRoomRepository gameRoomRepository;
    private final BroadcastService   broadcastService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Advances one game by one week.
     *
     * Propagation = REQUIRED (default): when called from GameService.placeOrder
     * (which is also @Transactional), it joins the existing transaction so that
     * the player's readyForOrder=true save and the turn advance commit atomically.
     * When called from RoomAdvancementService (no active tx, @Async thread), it
     * opens a fresh transaction of its own.
     */
    @Transactional
    public void advanceTurn(String gameId) {
        // Backward-compatible overload: fetches game from DB
        Game game = gameRepository.findByIdWithPlayers(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
        advanceTurn(game);
    }

    /**
     * Overload that accepts a pre-loaded Game object.
     * 
     * CRITICAL FIX: OrderService already has the Game with the 4th player's
     * readyForOrder=true set in memory. If we re-fetch from DB here, Hibernate
     * might return a stale snapshot where that player is still false, causing
     * the turn to silently fail to advance.
     */
    @Transactional
    public void advanceTurn(Game game) {
        String gameId = game.getId();

        if (game.getPlayers() == null || game.getPlayers().isEmpty()) {
            log.warn("Tried to advance game {} with no players.", gameId);
            return;
        }

        int currentWeek = game.getCurrentWeek();

        Map<Players.RoleType, Players> playerMap = game.getPlayers().stream()
                .collect(Collectors.toMap(Players::getRole, Function.identity()));

        Players retailer     = playerMap.get(Players.RoleType.RETAILER);
        Players wholesaler   = playerMap.get(Players.RoleType.WHOLESALER);
        Players distributor  = playerMap.get(Players.RoleType.DISTRIBUTOR);
        Players manufacturer = playerMap.get(Players.RoleType.MANUFACTURER);

        if (retailer == null || wholesaler == null || distributor == null || manufacturer == null) {
            log.error("Game {} missing one or more roles — aborting advance.", gameId);
            return;
        }

        // ── Loop 1: receive shipments, fulfil orders, calculate costs ──────────
        for (Players p : game.getPlayers()) {
            // Receive shipment that was on its way
            int shipmentReceived = p.getIncomingShipment();
            p.setLastShipmentReceived(shipmentReceived);
            p.setInventory(p.getInventory() + shipmentReceived);

            // Advance pipeline: next week's incoming = what was "week after next"
            p.setIncomingShipment(p.getShipmentArrivingWeekAfterNext());
            p.setShipmentArrivingWeekAfterNext(0);

            // Retailer demand comes from schedule; others receive upstream order
            int orderReceived = (p.getRole() == Players.RoleType.RETAILER)
                    ? GameConfig.getCustomerDemand(currentWeek, game.getFestiveWeeks())
                    : p.getOrderArrivingNextWeek();

            p.setLastOrderReceived(orderReceived);
            if (p.getRole() != Players.RoleType.RETAILER) {
                p.setOrderArrivingNextWeek(0);
            }

            // Fulfil demand + existing backlog
            int totalDemand = orderReceived + p.getBackOrder();
            int shipmentSent;
            if (p.getInventory() >= totalDemand) {
                shipmentSent = totalDemand;
                p.setInventory(p.getInventory() - totalDemand);
                p.setBackOrder(0);
            } else {
                shipmentSent = p.getInventory();
                p.setBackOrder(totalDemand - p.getInventory());
                p.setInventory(0);
            }
            p.setOutgoingDelivery(shipmentSent);

            // Weekly cost
            double holdingCost = p.getInventory() * GameConfig.INVENTORY_HOLDING_COST;
            double backlogCost = p.getBackOrder()  * GameConfig.BACKORDER_COST;
            p.setWeeklyCost(holdingCost + backlogCost);
            p.setTotalCost(p.getTotalCost() + p.getWeeklyCost());
        }

        // ── Loop 2: propagate orders down the supply chain ────────────────────
        wholesaler.setOrderArrivingNextWeek(retailer.getCurrentOrder());
        distributor.setOrderArrivingNextWeek(wholesaler.getCurrentOrder());
        manufacturer.setOrderArrivingNextWeek(distributor.getCurrentOrder());

        manufacturer.setShipmentArrivingWeekAfterNext(manufacturer.getCurrentOrder());
        distributor.setShipmentArrivingWeekAfterNext(manufacturer.getOutgoingDelivery());
        wholesaler.setShipmentArrivingWeekAfterNext(distributor.getOutgoingDelivery());
        retailer.setShipmentArrivingWeekAfterNext(wholesaler.getOutgoingDelivery());

        // ── Loop 3: record history, reset ready flag ───────────────────────────
        // Build all GameTurn records in memory, then batch-insert in one call.
        List<GameTurn> turns = game.getPlayers().stream().map(p -> {
            GameTurn turn = new GameTurn();
            turn.setWeekDay(currentWeek);
            turn.setPlayer(p);
            turn.setOrderPlaced(p.getCurrentOrder());
            turn.setDemandRecieved(p.getLastOrderReceived());
            turn.setShipmentSent(p.getOutgoingDelivery());
            turn.setShipmentRecieved(p.getLastShipmentReceived());
            turn.setInventoryAtEndOfWeek(p.getInventory());
            turn.setBackOrderAtEndOfWeek(p.getBackOrder());
            turn.setWeeklyCost(p.getWeeklyCost());
            turn.setTotalCost(p.getTotalCost());

            p.setReadyForOrder(false); // reset for next week
            return turn;
        }).collect(Collectors.toList());

        // Batch saves — avoids N individual INSERT/UPDATE round-trips
        gameTurnRepository.saveAll(turns);
        playerRepository.saveAll(game.getPlayers());

        // ── Advance week counter and check game-over ───────────────────────────
        game.setCurrentWeek(currentWeek + 1);

        if (game.getCurrentWeek() > GameConfig.GAME_WEEKS) {
            game.setGameStatus(Game.GameStatus.FINISHED);
            game.setFinishedAt(LocalDateTime.now());
            game.setFestiveWeek(false);
            log.info("Game {} FINISHED after week {}.", gameId, currentWeek);
            eventPublisher.publishEvent(new GameFinishedEvent(this, gameId));
        } else {
            boolean festive = GameConfig.isFestiveWeek(game.getCurrentWeek(), game.getFestiveWeeks());
            game.setFestiveWeek(festive);
            log.info("Game {} advanced to week {} (festive={})",
                    gameId, game.getCurrentWeek(), festive);
        }

        gameRepository.save(game);

        // Capture final values for the lambda before registerSynchronization
        final boolean gameStillRunning = game.getGameStatus() == Game.GameStatus.IN_PROGRESS;
        final int nextWeek = game.getCurrentWeek();
        final String capturedGameId = gameId;

        // Publish WeekStartedEvent AFTER commit so the AFK timer is only armed
        // once the turn data is safely persisted. If published mid-transaction
        // and the transaction rolls back, the event would have fired for a week
        // that was never written — leaving orphaned AFK keys in Redis.
        TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (gameStillRunning) {
                            eventPublisher.publishEvent(
                                    new WeekStartedEvent(TurnService.this, capturedGameId, nextWeek));
                        }
                    }
                });

        // Broadcast AFTER this transaction commits so clients always see
        // consistent, fully-written state.
        broadcastService.broadcastGameAfterCommit(gameId);
    }

    /**
     * Post-advance cleanup for a room: marks the room FINISHED if all its games
     * are done, then broadcasts the final room state.
     *
     * Always runs in its own transaction (called from a CompletableFuture
     * callback on a different thread, so there is no ambient transaction).
     *
     * NOTE: readyForOrder was already reset to false inside advanceTurn for
     * every player — no need to do it again here.
     */
    @Transactional
    public void postAdvanceRoomTurn(String roomId) {
        GameRoom room = gameRoomRepository.findByIdWithAllData(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        boolean allGamesFinished = room.getGames().stream()
                .allMatch(g -> g.getGameStatus() == Game.GameStatus.FINISHED);

        if (allGamesFinished) {
            room.setStatus(GameRoom.RoomStatus.FINISHED);
            room.setFinishedAt(LocalDateTime.now());
            log.info("Room {} is now FINISHED.", roomId);
        }

        gameRoomRepository.save(room);
        broadcastService.broadcastRoomState(roomId, room);

        // Announce winner once all games are done
        if (allGamesFinished) {
            log.info("Room {} — all games finished. Broadcasting result.", roomId);
            broadcastService.broadcastRoomResult(room);
        }
    }
}