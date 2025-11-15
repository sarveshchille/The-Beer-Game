package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.dto.RoomStateDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// ✅ ADD THESE
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final GameTurnRepository gameTurnRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GameRoomRepository gameRoomRepository;
    private final RoomAdvancementService roomAdvancementService;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private GameService self;

    @Autowired
    @Lazy
    public void setSelf(GameService self) {
        this.self = self;
    }

    private String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Create a single game (lobby).
     */
    public Game createGame(String creatorUsername) {
        playerInfoRepository.findByUserName(creatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Game game = new Game();
        String newGameId = generateRandomId(10);
        while (gameRepository.existsById(newGameId)) {
            newGameId = generateRandomId(10);
        }
        game.setId(newGameId);
        game.setGameStatus(Game.GameStatus.LOBBY);
        game.setCurrentWeek(1);
        game.setCreatedAt(LocalDateTime.now());

        log.info("Creating new game. ID will be generated on save.");
        Game savedGame = gameRepository.save(game);
        log.info("Created new game with id: {}", savedGame.getId());
        return savedGame;
    }

    /**
     * Join an existing game as a role.
     * Synchronized on gameId.intern() to avoid concurrent duplicate inserts
     * when multiple requests try to join the same game at the same time.
     */
    /**
     * Join an existing game as a role.
     * Synchronized on gameId.intern() to avoid concurrent duplicate inserts
     * when multiple requests try to join the same game at the same time.
     */
    public Game joinGame(String gameIdRaw, String username, Players.RoleType role) {

        if (gameIdRaw == null)
            throw new RuntimeException("gameId is null");

        String gameId = gameIdRaw.trim();

        // synchronize per-game to avoid duplicate inserts / race around roles
        synchronized (gameId.intern()) {

            Game game = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

            PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // CASE 1: PLAYER ALREADY IN GAME
            Optional<Players> existing = playerRepository.findByGameAndPlayerInfoUserName(game, username);
            if (existing.isPresent()) {
                log.info("Player {} already in game {} — returning existing game", username, gameId);

                Game refreshed = gameRepository.findByIdWithPlayers(gameId)
                        .orElseThrow(() -> new RuntimeException("Game not found after join"));

                // ✅ CHANGED: Call the safe broadcast helper
                broadcastAfterCommit(gameId);
                return refreshed;
            }

            // CASE 2: NEW PLAYER - CHECK ROLE IS FREE
            boolean roleTaken = game.getPlayers().stream()
                    .anyMatch(p -> p.getRole() == role);

            if (roleTaken)
                throw new RuntimeException("Role " + role + " already taken");

            // CREATE NEW PLAYER ENTRY
            Players player = new Players();
            player.setUserName(playerInfo.getUserName());
            player.setPlayerInfo(playerInfo);
            player.setRole(role);
            player.setInventory(GameConfig.INITIAL_INVENTORY);
            player.setBackOrder(0);
            player.setWeeklyCost(0);
            player.setTotalCost(0);
            player.setReadyForOrder(false);

            if (role == Players.RoleType.RETAILER)
                player.setOrderArrivingNextWeek(GameConfig.getCustomerDemand(1));
            else
                player.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);

            player.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
            player.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL);

            player.setGame(game);

            playerRepository.save(player);
            log.info("Player {} joined game {} as {}", username, gameId, role);

            // RELOAD AGAIN to get the most up-to-date player list
            Game refreshed = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found after join"));

            // AUTO-START WHEN 4 PLAYERS JOIN
            if (refreshed.getPlayers().size() == 4 &&
                    refreshed.getGameStatus() == Game.GameStatus.LOBBY) {

                refreshed.setGameStatus(Game.GameStatus.IN_PROGRESS);
                gameRepository.save(refreshed); // save BEFORE broadcast
                log.info("Game {} moved to IN_PROGRESS (players: {})", gameId, refreshed.getPlayers().size());
            }

            // ✅ CHANGED: Call the safe broadcast helper
            broadcastAfterCommit(gameId);

            // Return the 100% fresh data
            return refreshed;
        }
    }

    /**
     * Safely broadcasts the game state ONLY after the current database transaction
     * has successfully committed. This prevents all race conditions.
     */

    /**
     * Safely broadcasts the game state ONLY after the current database transaction
     * has successfully committed. This prevents all race conditions.
     */
    private void broadcastAfterCommit(String gameId) {

        // Use TransactionSynchronizationAdapter to only override the method we need
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {

            @Override
            public void afterCommit() {
                // This code runs *after* the database commit
                log.info("Transaction committed for game {}. Broadcasting state.", gameId);

                // Re-fetch the game state AFTER commit to ensure data is fresh
                try {
                    Game freshGame = gameRepository.findByIdWithPlayers(gameId)
                            .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

                    broadcastGameState(freshGame); // This is your existing method
                } catch (Exception e) {
                    log.error("Failed to broadcast game state after commit for id {}: {}", gameId, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Player places order for a single game.
     */
    public void placeOrder(String gameId, String username, int orderAmount) {
        Game game = gameRepository.findByIdWithPlayers(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

        Players player = playerRepository.findByGameAndPlayerInfoUserName(game, username)
                .orElseThrow(() -> new RuntimeException("Player not in this game"));

        if (player.isReadyForOrder()) {
            log.warn("Player {} already submitted order for week {}", username, game.getCurrentWeek());
            return;
        }

        player.setCurrentOrder(Math.max(0, orderAmount));
        player.setReadyForOrder(true);
        playerRepository.save(player);
        log.info("Player {} placed order {} for week {}", username, orderAmount, game.getCurrentWeek());

        // re-load game to ensure we have current player list / flags
        Game reloaded = gameRepository.findByIdWithPlayers(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

        broadcastGameState(gameId);

        List<Players> players = reloaded.getPlayers();
        if (players == null || players.size() < 4) {
            return;
        }

        boolean allReady = players.stream().allMatch(Players::isReadyForOrder);
        if (allReady) {
            log.info("All players ready for game {}. Advancing turn.", gameId);
            advanceTurn(gameId);
        }
    }

    /**
     * Advance a single game's turn. Transactional to ensure DB consistency.
     */
    @Transactional
    public void advanceTurn(String gameId) {
        Game game = gameRepository.findByIdWithPlayers(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));

        if (game.getPlayers() == null || game.getPlayers().isEmpty()) {
            log.warn("Tried to advance game {} with no players.", gameId);
            return;
        }

        int currentWeek = game.getCurrentWeek();

        Map<Players.RoleType, Players> playerMap = game.getPlayers().stream()
                .collect(Collectors.toMap(Players::getRole, Function.identity()));

        Players retailer = playerMap.get(Players.RoleType.RETAILER);
        Players wholesaler = playerMap.get(Players.RoleType.WHOLESALER);
        Players distributor = playerMap.get(Players.RoleType.DISTRIBUTOR);
        Players manufacturer = playerMap.get(Players.RoleType.MANUFACTURER);

        if (retailer == null || wholesaler == null || distributor == null || manufacturer == null) {
            log.error("Game {} is in an invalid state: missing one or more roles.", gameId);
            return;
        }

        // Loop 1: receive shipments, fulfill orders
        for (Players p : game.getPlayers()) {
            int shipmentReceived = p.getIncomingShipment();
            p.setLastShipmentReceived(shipmentReceived);
            p.setInventory(p.getInventory() + shipmentReceived);
            p.setIncomingShipment(p.getShipmentArrivingWeekAfterNext());
            p.setShipmentArrivingWeekAfterNext(0);

            int orderReceived = (p.getRole() == Players.RoleType.RETAILER)
                    ? GameConfig.getCustomerDemand(currentWeek)
                    : p.getOrderArrivingNextWeek();

            p.setLastOrderReceived(orderReceived);
            if (p.getRole() != Players.RoleType.RETAILER)
                p.setOrderArrivingNextWeek(0);

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

            double holdingCost = p.getInventory() * GameConfig.INVENTORY_HOLDING_COST;
            double backlogCost = p.getBackOrder() * GameConfig.BACKORDER_COST;
            double weeklyCost = holdingCost + backlogCost;
            p.setWeeklyCost(weeklyCost);
            p.setTotalCost(p.getTotalCost() + weeklyCost);
        }

        // Loop 2: place & advance pipeline orders
        wholesaler.setOrderArrivingNextWeek(retailer.getCurrentOrder());
        distributor.setOrderArrivingNextWeek(wholesaler.getCurrentOrder());
        manufacturer.setOrderArrivingNextWeek(distributor.getCurrentOrder());

        manufacturer.setShipmentArrivingWeekAfterNext(manufacturer.getCurrentOrder());
        distributor.setShipmentArrivingWeekAfterNext(manufacturer.getOutgoingDelivery());
        wholesaler.setShipmentArrivingWeekAfterNext(distributor.getOutgoingDelivery());
        retailer.setShipmentArrivingWeekAfterNext(wholesaler.getOutgoingDelivery());

        // Loop 3: log history and reset players
        for (Players p : game.getPlayers()) {
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

            gameTurnRepository.save(turn);

            p.setReadyForOrder(false);
            playerRepository.save(p);
        }

        game.setCurrentWeek(currentWeek + 1);

        if (game.getCurrentWeek() > GameConfig.GAME_WEEKS) {
            game.setGameStatus(Game.GameStatus.FINISHED);
            game.setFinishedAt(LocalDateTime.now());
        }

        gameRepository.save(game);
        log.info("Game {} advanced to week {}", gameId, game.getCurrentWeek());

        // broadcast updated game state (or room state if part of a room)
        if (game.getGameRoom() != null) {
            GameRoom room = gameRoomRepository.findByIdWithAllData(game.getGameRoom().getId()).get();
            broadcastRoomState(room.getId(), room);
        } else {
            broadcastGameState(gameId);
        }
    }

    /**
     * Room mode: submit order for a room player
     */
    public void submitRoomOrder(String roomId, String username, int orderAmount) {
        GameRoom room = gameRoomRepository.findByIdWithAllData(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        if (room.getStatus() != GameRoom.RoomStatus.RUNNING) {
            throw new RuntimeException("This room is not running.");
        }

        Players player = room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .filter(p -> p.getPlayerInfo().getUserName().equals(username))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Player not found in this room"));

        if (player.isReadyForOrder()) {
            log.warn("Player {} already submitted order for room {}", username, roomId);
            return;
        }

        player.setCurrentOrder(Math.max(0, orderAmount));
        player.setReadyForOrder(true);
        playerRepository.save(player);

        // broadcast intermediate room state
        broadcastRoomState(roomId, room);

        boolean allReady = room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .allMatch(Players::isReadyForOrder);

        if (allReady) {
            log.info("All players in room {} are ready. Advancing all games.", roomId);

            List<Game> games = room.getGames();
            CompletableFuture<Void> g1 = roomAdvancementService.advanceGame(games.get(0).getId());
            CompletableFuture<Void> g2 = roomAdvancementService.advanceGame(games.get(1).getId());
            CompletableFuture<Void> g3 = roomAdvancementService.advanceGame(games.get(2).getId());
            CompletableFuture<Void> g4 = roomAdvancementService.advanceGame(games.get(3).getId());

            CompletableFuture.allOf(g1, g2, g3, g4).thenRun(() -> {
                log.info("All games in room {} advanced. Running post-turn cleanup.", roomId);
                self.postAdvanceRoomTurn(roomId);
            });
        }
    }

    /**
     * Post-advance cleanup for rooms (runs in a new transaction).
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
            log.info("Room {} finished.", roomId);
        }

        room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .forEach(player -> {
                    player.setReadyForOrder(false);
                    playerRepository.save(player);
                });

        gameRoomRepository.save(room);
        broadcastRoomState(roomId, room);
    }

    public void broadcastGameState(Game game) {
        GameStateDTO newState = GameStateDTO.fromGame(game);
        String channel = "game-updates:" + game.getId();
        log.info("Publishing game state on Redis channel: {}", channel);
        redisTemplate.convertAndSend(channel, newState);
    }

    public void broadcastGameState(String gameId) {
        try {
            Game fresh = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
            broadcastGameState(fresh);
        } catch (Exception e) {
            log.error("Failed to broadcast game state for id {}: {}", gameId, e.getMessage(), e);
        }
    }

    private void broadcastRoomState(String roomId, GameRoom room) {
        RoomStateDTO roomState = RoomStateDTO.fromGameRoom(room);
        String channel = "room-updates:" + roomId;
        log.info("Broadcasting state for room {} to Redis channel: {}", roomId, channel);
        redisTemplate.convertAndSend(channel, roomState);
    }

    public Game getGameWithPlayers(String gameId) {
        return gameRepository.findByIdWithPlayers(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
    }
}