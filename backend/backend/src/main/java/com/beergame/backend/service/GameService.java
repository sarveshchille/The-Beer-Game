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

    public Game joinGame(String gameId, String username, Players.RoleType role) {
        @SuppressWarnings("null")
        gameId.trim();
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
        PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Optional<Players> existing = playerRepository.findByGameAndPlayerInfoUserName(game, username);
        if (existing.isPresent()) {
            log.info("Player {} already in game {} — skipping insert", username, gameId);
            return game;
        }
        boolean roleTaken = game.getPlayers() != null && game.getPlayers().stream()
                .anyMatch(p -> p.getRole() == role);
        if (roleTaken) {
            throw new RuntimeException("Role " + role + " is already taken");
        }

        Players player = new Players();
        player.setUserName(playerInfo.getUserName());
        player.setPlayerInfo(playerInfo);
        player.setRole(role);

        player.setInventory(GameConfig.INITIAL_INVENTORY);
        player.setBackOrder(0);
        player.setTotalCost(0);
        player.setWeeklyCost(0);

        if (player.getRole() == Players.RoleType.RETAILER) {
            player.setOrderArrivingNextWeek(GameConfig.getCustomerDemand(1));
        } else {
            player.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);
        }
        player.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
        player.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL);

        player.setGame(game);

        playerRepository.save(player);
        log.info("Player {} joined game {} as {}", username, gameId, role);

        game.getPlayers().add(player);
        broadcastGameState(gameId);
        return game;
    }

    public void placeOrder(String gameId, String username, int orderAmount) {
        @SuppressWarnings("null")
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        Players player = playerRepository.findByGameAndPlayerInfoUserName(game, username)
                .orElseThrow(() -> new RuntimeException("Player not in this game"));

        if (player.isReadyForOrder()) {
            log.warn("Player {} already submitted order for week {}", username, game.getCurrentWeek());
            return;
        }

        player.setCurrentOrder(orderAmount < 0 ? 0 : orderAmount);
        player.setReadyForOrder(true);
        playerRepository.save(player);
        log.info("Player {} placed order of {} for week {}", username, orderAmount, game.getCurrentWeek());

        broadcastGameState(gameId);

        List<Players> players = game.getPlayers();
        if (players == null || players.size() < 4) {
            return;
        }

        boolean allReady = players.stream().allMatch(Players::isReadyForOrder);

        if (allReady) {
            log.info("All players are ready. Advancing turn for game {}", gameId);
            advanceTurn(gameId);
        }
    }

    // --- SHARED GAME ENGINE LOGIC ---

    /**
     * This is the "Game Engine" logic, fully ported from your friend's code.
     * It runs the simulation for one week for ONE game.
     */
    @Transactional // This logic MUST be transactional
    public void advanceTurn(String gameId) {
        @SuppressWarnings("null")
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new RuntimeException("Game not found"));

        // Ensure we have players
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

        // Check if all players exist
        if (retailer == null || wholesaler == null || distributor == null || manufacturer == null) {
            log.error("Game {} is in an invalid state: missing one or more roles.", gameId);
            return;
        }

        // ** Loop 1: Steps 1-4 (Receive & Fulfill) **
        for (Players p : game.getPlayers()) {
            int shipmentReceived = p.getIncomingShipment();
            p.setLastShipmentReceived(shipmentReceived);
            p.setInventory(p.getInventory() + shipmentReceived);
            p.setIncomingShipment(p.getShipmentArrivingWeekAfterNext());
            p.setShipmentArrivingWeekAfterNext(0);

            int orderReceived = 0;
            if (p.getRole() == Players.RoleType.RETAILER) {
                orderReceived = GameConfig.getCustomerDemand(currentWeek);
            } else {
                orderReceived = p.getOrderArrivingNextWeek();
                p.setOrderArrivingNextWeek(0);
            }
            p.setLastOrderReceived(orderReceived);

            int totalDemand = orderReceived + p.getBackOrder();
            int shipmentSent = 0;

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

        // ** Loop 2: Steps 5 & 6 (Place & Advance Pipelines) **
        wholesaler.setOrderArrivingNextWeek(retailer.getCurrentOrder());
        distributor.setOrderArrivingNextWeek(wholesaler.getCurrentOrder());
        manufacturer.setOrderArrivingNextWeek(distributor.getCurrentOrder());

        manufacturer.setShipmentArrivingWeekAfterNext(manufacturer.getCurrentOrder());
        distributor.setShipmentArrivingWeekAfterNext(manufacturer.getOutgoingDelivery());
        wholesaler.setShipmentArrivingWeekAfterNext(distributor.getOutgoingDelivery());
        retailer.setShipmentArrivingWeekAfterNext(wholesaler.getOutgoingDelivery());

        // ** Loop 3: Log history & Reset Player **
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

        // If this game is part of a room, broadcast the room state.
        // Otherwise, broadcast the single game state.
        if (game.getGameRoom() != null) {
            // Re-fetch full room data to send a complete update
            GameRoom room = gameRoomRepository.findByIdWithAllData(game.getGameRoom().getId()).get();
            broadcastRoomState(room.getId(), room);
        } else {
            broadcastGameState(gameId);
        }
    }

    // --- ROOM GAME METHODS ---

    /**
     * Called by the WebSocket when a player in a room submits their order.
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

        player.setCurrentOrder(orderAmount < 0 ? 0 : orderAmount);
        player.setReadyForOrder(true);
        playerRepository.save(player);

        // Broadcast the "player is ready" state
        broadcastRoomState(roomId, room);

        boolean allReady = room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .allMatch(Players::isReadyForOrder);

        if (allReady) {
            log.info("All 16 players in room {} are ready. Advancing all 4 games in parallel.", roomId);

            List<Game> games = room.getGames();
            CompletableFuture<Void> g1 = roomAdvancementService.advanceGame(games.get(0).getId());
            CompletableFuture<Void> g2 = roomAdvancementService.advanceGame(games.get(1).getId());
            CompletableFuture<Void> g3 = roomAdvancementService.advanceGame(games.get(2).getId());
            CompletableFuture<Void> g4 = roomAdvancementService.advanceGame(games.get(3).getId());

            // Call postAdvanceRoomTurn using the injected 'self'
            CompletableFuture.allOf(g1, g2, g3, g4).thenRun(() -> {
                log.info("All 4 games in room {} have finished. Running post-turn cleanup.", roomId);
                self.postAdvanceRoomTurn(roomId); // Use self to ensure new transaction
            });
        }
    }

    /**
     * This runs *after* all 4 async games have advanced.
     * It runs in a new transaction.
     */
    @Transactional
    public void postAdvanceRoomTurn(String roomId) {
        GameRoom room = gameRoomRepository.findByIdWithAllData(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        boolean allGamesFinished = room.getGames().stream()
                .allMatch(g -> g.getGameStatus() == Game.GameStatus.FINISHED);

        if (allGamesFinished) {
            log.info("All games in room {} are finished. Setting room status to FINISHED.", roomId);
            room.setStatus(GameRoom.RoomStatus.FINISHED);
            room.setFinishedAt(LocalDateTime.now());
        }

        // Reset all 16 players for the next turn
        room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .forEach(player -> {
                    player.setReadyForOrder(false);
                    playerRepository.save(player);
                });

        gameRoomRepository.save(room);

        // Broadcast the final new state to everyone in the room
        broadcastRoomState(roomId, room);
    }

    @SuppressWarnings("null")
    private void broadcastGameState(String gameId) {
        @SuppressWarnings("null")
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        GameStateDTO newState = GameStateDTO.fromGame(game);
        String channel = "game-updates:" + gameId;

        log.info("Publishing new game state to Redis channel: {}", channel);
        redisTemplate.convertAndSend(channel, newState);
    }

    @SuppressWarnings("null")
    private void broadcastRoomState(String roomId, GameRoom room) {
        RoomStateDTO roomState = RoomStateDTO.fromGameRoom(room);
        String channel = "room-updates:" + roomId;

        log.info("Broadcasting state for room {} to Redis channel: {}", roomId, channel);
        redisTemplate.convertAndSend(channel, roomState);
    }
}