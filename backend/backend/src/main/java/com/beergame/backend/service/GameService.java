package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.dto.GameTurnHistoryDTO;
import com.beergame.backend.event.AllPlayersReadyEvent;
import com.beergame.backend.event.WeekStartedEvent;
import com.beergame.backend.model.BotType;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.GameTurn;
import com.beergame.backend.model.PlayerInfo;
import com.beergame.backend.model.Players;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.GameRoomRepository;
import com.beergame.backend.repository.GameTurnRepository;
import com.beergame.backend.repository.PlayerInfoRepository;
import com.beergame.backend.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Changes from the original:
 *
 * 1. synchronized(gameId.intern()) → RedisLockService (cluster-safe).
 * 2. advanceTurn / postAdvanceRoomTurn moved to TurnService so Spring's proxy
 * intercepts @Transactional (self-invocation bypass fixed; @Lazy self gone).
 * 3. broadcast methods moved to BroadcastService (no circular deps).
 * 4. placeOrder: only registers ONE broadcast — either intermediate state OR
 * lets advanceTurn register it, never both.
 * 5. submitRoomOrder: intermediate broadcast is now post-commit safe.
 * 6. generateUniqueGameId: bounded retry loop with clear error on exhaustion.
 * 7. joinGame: uses game.getCurrentWeek() instead of hardcoded 1 for retailer.
 * 8. placeOrder: order amount validated with upper bound.
 * 9. Game entity now has @Version (optimistic locking) — see Game.java.
 * 10. Batch saves (saveAll) are handled inside TurnService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    // ── Maximum order a player may place in one turn ──────────────────────────
    // Prevents accidental or malicious integer overflow / runaway costs.
    public static final int MAX_ORDER_AMOUNT = 9_999;

    // ── ID generation ─────────────────────────────────────────────────────────
    private static final int MAX_ID_RETRIES = 10;
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final GameTurnRepository gameTurnRepository;
    private final GameRoomRepository gameRoomRepository;
    private final RoomAdvancementService roomAdvancementService;

    // New extracted services
    private final RedisLockService redisLockService;
    private final TurnService turnService;
    private final BroadcastService broadcastService;
    private final BotService botService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a unique 10-character game ID.
     * Bounded to MAX_ID_RETRIES attempts — avoids an infinite loop in the
     * (astronomically unlikely) case of repeated collisions.
     */
    private String generateUniqueGameId() {
        for (int attempt = 0; attempt < MAX_ID_RETRIES; attempt++) {
            String id = generateRandomId(10);
            if (!gameRepository.existsById(id)) {
                return id;
            }
            log.warn("Game ID collision on attempt {}: {}", attempt + 1, id);
        }
        throw new RuntimeException(
                "Failed to generate a unique game ID after " + MAX_ID_RETRIES + " attempts");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void triggerBotsOnWeekStart(WeekStartedEvent event) {
        // 1. Fetch game WITHOUT taking the lock
        Game game = gameRepository.findByIdWithPlayers(event.getGameId()).orElse(null);
        if (game == null || game.getGameStatus() != Game.GameStatus.IN_PROGRESS)
            return;

        // 2. Dispatch async tasks to BotService so we don't block this listener thread
        game.getPlayers().stream()
                .filter(p -> p.isBot() && !p.isReadyForOrder())
                .forEach(bot -> {
                    botService.calculateAndPlaceOrderAsync(game, bot, bot.getBotType(), game.getCurrentWeek());
                });
    }

    /**
     * Adds a bot player to fill an empty role slot.
     * The creator calls this when they want to start with fewer than 4 humans.
     */
    @Transactional
    public Game addBot(String gameId, Players.RoleType role, BotType botType) {
        String botUsername = "BOT_" + botType.name() + "_" + role.name();
        // Reuse joinGame — creates the player entry the same way
        // Bot players are identified by the isBot flag, not username pattern
        return redisLockService.executeWithLock(gameId, 10, () -> {
            Game game = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

            boolean roleTaken = game.getPlayers().stream()
                    .anyMatch(p -> p.getRole() == role);
            if (roleTaken)
                throw new RuntimeException("Role " + role + " already taken");

            Players bot = new Players();
            bot.setUserName(botUsername);
            bot.setRole(role);
            bot.setBot(true);
            bot.setBotType(botType);
            bot.setInventory(GameConfig.INITIAL_INVENTORY);
            bot.setBackOrder(0);
            bot.setWeeklyCost(0);
            bot.setTotalCost(0);
            bot.setReadyForOrder(false);

            int currentWeek = game.getCurrentWeek();
            if (role == Players.RoleType.RETAILER) {
                bot.setOrderArrivingNextWeek(
                        GameConfig.getCustomerDemand(currentWeek, game.getFestiveWeeks()));
            } else {
                bot.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);
            }
            bot.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
            bot.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL);
            bot.setGame(game);

            game.getPlayers().add(bot);
            playerRepository.save(bot);

            if (game.getPlayers().size() == 4 && game.getGameStatus() == Game.GameStatus.LOBBY) {
                game.setGameStatus(Game.GameStatus.IN_PROGRESS);
                gameRepository.save(game);
                eventPublisher.publishEvent(new WeekStartedEvent(this, gameId, 1));
            }

            broadcastService.broadcastGameAfterCommit(gameId);
            return game;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Game lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new game lobby.
     */
    @Transactional
    public Game createGame(String creatorUsername) {
        playerInfoRepository.findByUserName(creatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found: " + creatorUsername));

        Game game = new Game();
        game.setId(generateUniqueGameId());
        game.setGameStatus(Game.GameStatus.LOBBY);
        game.setCurrentWeek(1);
        game.setCreatedAt(LocalDateTime.now());
        game.setFestiveWeeks(GameConfig.generateFestiveWeeks());
        game.setFestiveWeek(GameConfig.isFestiveWeek(1, game.getFestiveWeeks()));

        Game saved = gameRepository.save(game);
        log.info("Created game id={}", saved.getId());
        
        // Ping the bot service to wake up the Render instance
        botService.ping();
        
        return saved;
    }

    /**
     * Adds a player to an existing game as the requested role.
     *
     * Uses a Redis distributed lock (not JVM synchronized) so the
     * race-condition protection works across multiple server instances.
     */
    @Transactional
    public Game joinGame(String gameIdRaw, String username, Players.RoleType role) {
        if (gameIdRaw == null) {
            throw new RuntimeException("gameId is null");
        }
        String gameId = gameIdRaw.trim();

        return redisLockService.executeWithLock(gameId, 10, () -> {

            Game game = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

            PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // ── Case 1: player already joined ─────────────────────────────────
            Optional<Players> existing = playerRepository.findByGameAndPlayerInfoUserName(game, username);
            if (existing.isPresent()) {
                log.info("Player {} already in game {} — returning current state.", username, gameId);
                broadcastService.broadcastGameAfterCommit(gameId);
                return game;
            }

            // ── Case 2: role already taken ────────────────────────────────────
            boolean roleTaken = game.getPlayers().stream()
                    .anyMatch(p -> p.getRole() == role);
            if (roleTaken) {
                throw new RuntimeException("Role " + role + " is already taken in game " + gameId);
            }

            // ── Case 3: create new player entry ───────────────────────────────
            Players player = new Players();
            player.setUserName(playerInfo.getUserName());
            player.setPlayerInfo(playerInfo);
            player.setRole(role);
            player.setInventory(GameConfig.INITIAL_INVENTORY);
            player.setBackOrder(0);
            player.setWeeklyCost(0);
            player.setTotalCost(0);
            player.setReadyForOrder(false);

            // FIX: use the game's actual currentWeek, not hardcoded 1.
            // (Matters if, e.g., a player reconnects mid-game to a saved lobby.)
            int currentWeek = game.getCurrentWeek();
            if (role == Players.RoleType.RETAILER) {
                player.setOrderArrivingNextWeek(GameConfig.getCustomerDemand(currentWeek, game.getFestiveWeeks()));

            } else {
                player.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);
            }
            player.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
            player.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL);
            player.setGame(game);

            // Add to in-memory list before saving so the returned Game object
            // is always consistent with what was just persisted.
            game.getPlayers().add(player);
            playerRepository.save(player);
            log.info("Player {} joined game {} as {}", username, gameId, role);

            // Auto-start when all 4 roles are filled
            if (game.getPlayers().size() == 4
                    && game.getGameStatus() == Game.GameStatus.LOBBY) {
                game.setGameStatus(Game.GameStatus.IN_PROGRESS);
                gameRepository.save(game);
                log.info("Game {} → IN_PROGRESS", gameId);
                eventPublisher.publishEvent(new WeekStartedEvent(this, gameId, 1));
            }

            broadcastService.broadcastGameAfterCommit(gameId);
            return game;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single-game order placement
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a player's order for the current week.
     *
     * Broadcast strategy (fixes the double-broadcast bug):
     * • Not all ready → register one post-commit broadcast showing
     * intermediate "player X is ready" state.
     * • All ready → delegate to turnService.advanceTurn(), which registers
     * its own post-commit broadcast. We do NOT register a
     * second one here.
     *
     * Since turnService.advanceTurn() is annotated @Transactional(REQUIRED), it
     * joins this method's transaction. Both the player save and the full turn
     * advance commit atomically. Hibernate flushes the player save before
     * advanceTurn's queries run, so the allReady check inside advanceTurn sees
     * consistent data.
     */
    public void placeOrder(String gameId, String username, int orderAmount) {
        orderService.placeOrder(gameId, username, orderAmount, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Room-mode order placement
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a player's order in room mode.
     *
     * FIX: intermediate state is now broadcast post-commit (not mid-transaction).
     */
    public void submitRoomOrder(String roomId, String username, int orderAmount) {
        redisLockService.executeWithLock(roomId, 10, () -> {
            return transactionTemplate.execute(status -> {
                // ── Validate ──────────────────────────────────────────────────────────
                if (orderAmount < 0 || orderAmount > MAX_ORDER_AMOUNT) {
                    throw new IllegalArgumentException(
                            "Order amount must be 0–" + MAX_ORDER_AMOUNT + ", got: " + orderAmount);
                }

                GameRoom room = gameRoomRepository.findByIdWithAllData(roomId)
                        .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

                if (room.getStatus() != GameRoom.RoomStatus.RUNNING) {
                    throw new RuntimeException("Room " + roomId + " is not currently running.");
                }

                Players player = room.getTeams().stream()
                        .flatMap(team -> team.getPlayers() != null ? team.getPlayers().stream() : java.util.stream.Stream.empty())
                        .filter(p -> p.getPlayerInfo().getUserName().equals(username))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Player not found in room: " + username));

                if (player.isReadyForOrder()) {
                    log.warn("Player {} already submitted order for room {}", username, roomId);
                    return null;
                }

                player.setCurrentOrder(orderAmount);
                player.setReadyForOrder(true);
                playerRepository.save(player);

                boolean allReady = room.getTeams().stream()
                        .flatMap(team -> team.getPlayers() != null ? team.getPlayers().stream() : java.util.stream.Stream.empty())
                        .allMatch(Players::isReadyForOrder);

                if (!allReady) {
                    // FIX: was calling broadcastRoomState() mid-transaction (before commit).
                    // Now we wait until after commit so clients see consistent DB state.
                    broadcastService.broadcastRoomAfterCommit(roomId);
                    return null;
                }

                log.info("All players in room {} ready. Advancing all games.", roomId);

                List<String> gameIds = room.getGames().stream().map(Game::getId).toList();
                
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        CompletableFuture<Void> g1 = roomAdvancementService.advanceGame(gameIds.get(0));
                        CompletableFuture<Void> g2 = roomAdvancementService.advanceGame(gameIds.get(1));
                        CompletableFuture<Void> g3 = roomAdvancementService.advanceGame(gameIds.get(2));
                        CompletableFuture<Void> g4 = roomAdvancementService.advanceGame(gameIds.get(3));

                        CompletableFuture.allOf(g1, g2, g3, g4).thenRun(() -> {
                            log.info("All games in room {} advanced. Running post-turn cleanup.", roomId);
                            // turnService is a different bean → @Transactional works correctly here
                            turnService.postAdvanceRoomTurn(roomId);
                        });
                    }
                });
                return null;
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, List<GameTurnHistoryDTO>> getGameHistory(String gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new RuntimeException("Game not found: " + gameId);
        }

        // Single query for all turns — avoids MultipleBagFetchException
        List<GameTurn> allTurns = gameTurnRepository.findByPlayer_Game_Id(gameId);

        Map<String, List<GameTurnHistoryDTO>> response = new HashMap<>();
        for (GameTurn turn : allTurns) {
            if (turn.getPlayer() == null || turn.getPlayer().getRole() == null)
                continue;
            String roleKey = turn.getPlayer().getRole().toString();
            response.computeIfAbsent(roleKey, k -> new ArrayList<>())
                    .add(GameTurnHistoryDTO.fromEntity(turn));
        }

        // Sort each role's history by ascending week number
        response.values().forEach(list -> list.sort(Comparator.comparingInt(GameTurnHistoryDTO::weekDay)));

        log.info("Fetched history for game {}: {} total turns.", gameId, allTurns.size());
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public broadcast delegate (used by GameController response path)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convenience passthrough so callers that already have a Game object don't
     * need to inject BroadcastService separately.
     */
    public void broadcastGameState(Game game) {
        broadcastService.broadcastGameState(game);
    }

    public void broadcastGameState(String gameId) {
        broadcastService.broadcastGameState(gameId);
    }
}