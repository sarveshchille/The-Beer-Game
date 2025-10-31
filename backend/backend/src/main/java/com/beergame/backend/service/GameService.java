package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.dto.GameStateDTO; 
import com.beergame.backend.model.*;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.PlayerInfoRepository;
import com.beergame.backend.repository.PlayerRepository;
import com.beergame.backend.repository.GameTurnRepository; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate; 
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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


    public Game createGame( String creatorUsername) {

         playerInfoRepository.findByUserName(creatorUsername)
                             .orElseThrow(() -> new RuntimeException("User not found"));

        Game game = new Game();
        game.setGameStatus(Game.GameStatus.LOBBY);
        game.setCurrentWeek(1);
        game.setCreatedAt(LocalDateTime.now());
        
        log.info("Creating new game. ID will be generated on save.");
        Game savedGame = gameRepository.save(game);
        log.info("Created new game with id: {}", savedGame.getId());
        return savedGame;
    }

    public Game joinGame(String gameId, String username, Players.RoleType role) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
        PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        boolean roleTaken = game.getPlayers().stream()
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
        player.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL); // Arrives week 1
        player.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL); // Arrives week 2
        
        player.setGame(game);
        
        playerRepository.save(player); 
        log.info("Player {} joined game {} as {}", username, gameId, role);

        game.getPlayers().add(player);
        broadcastGameState(gameId);
        return game;
    }

    public void placeOrder(String gameId, String username, int orderAmount) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        
        Players player = playerRepository.findByGameAndPlayerInfoUserName(game, username)
                .orElseThrow(() -> new RuntimeException("Player not in this game"));

        if(player.isReadyForOrder()) {
            log.warn("Player {} already submitted order for week {}", username, game.getCurrentWeek());
            return; 
        }

        player.setCurrentOrder(orderAmount < 0 ? 0 : orderAmount); 
        player.setReadyForOrder(true);
        playerRepository.save(player);
        log.info("Player {} placed order of {} for week {}", username, orderAmount, game.getCurrentWeek());

        broadcastGameState(gameId); 

        List<Players> players = game.getPlayers();
        if (players.size() < 4) {
            return; 
        }

        boolean allReady = players.stream().allMatch(Players::isReadyForOrder);
        
        if (allReady) {
            log.info("All players are ready. Advancing turn for game {}", gameId);
            advanceTurn(gameId);
        }
    }
    public void advanceTurn(String gameId) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new RuntimeException("Game not found"));
        int currentWeek = game.getCurrentWeek();
        
        Map<Players.RoleType, Players> playerMap = game.getPlayers().stream()
            .collect(Collectors.toMap(Players::getRole, Function.identity()));

        Players retailer = playerMap.get(Players.RoleType.RETAILER);
        Players wholesaler = playerMap.get(Players.RoleType.WHOLESALER);
        Players distributor = playerMap.get(Players.RoleType.DISTRIBUTOR);
        Players manufacturer = playerMap.get(Players.RoleType.MANUFACTURER);

        // ** Loop 1: Steps 1-4 (Receive & Fulfill) **
        for (Players p : game.getPlayers()) {
            
            // --- Ported from `step1_receiveIncomingShipment()` ---
            int shipmentReceived = p.getIncomingShipment();
            p.setLastShipmentReceived(shipmentReceived); // Log what arrived
            p.setInventory(p.getInventory() + shipmentReceived);
            // Advance pipeline: item from 2 weeks away moves to 1 week away
            p.setIncomingShipment(p.getShipmentArrivingWeekAfterNext());
            p.setShipmentArrivingWeekAfterNext(0); // Clear slot
            
            // --- Ported from `step2_receiveIncomingOrder()` ---
            int orderReceived = 0;
            if (p.getRole() == Players.RoleType.RETAILER) {
                orderReceived = GameConfig.getCustomerDemand(currentWeek);
            } else {
                orderReceived = p.getOrderArrivingNextWeek();
                p.setOrderArrivingNextWeek(0); // Clear slot
            }
            p.setLastOrderReceived(orderReceived); // Log what arrived

            // --- Ported from `step3_fulfillOrder()` ---
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
            p.setOutgoingDelivery(shipmentSent); // Log what was sent

            // --- Ported from `step4_calculateWeeklyCost()` ---
            double holdingCost = p.getInventory() * GameConfig.INVENTORY_HOLDING_COST;
            double backlogCost = p.getBackOrder() * GameConfig.BACKORDER_COST;
            double weeklyCost = holdingCost + backlogCost;
            
            p.setWeeklyCost(weeklyCost);
            p.setTotalCost(p.getTotalCost() + weeklyCost);
        }

        // ** Loop 2: Steps 5 & 6 (Place & Advance Pipelines) **
        
        // Step 5 (`step5_placeOrder`) is already done. The order is in `p.getCurrentOrder()`.

        // --- Ported from `GameEngine.runSimulationWeek()` (Step 6) ---
        
        // --- Pass orders UP the chain (for *next* week) ---
        // (Ported from `passOrderToPipeline`)
        wholesaler.setOrderArrivingNextWeek(retailer.getCurrentOrder());
        distributor.setOrderArrivingNextWeek(wholesaler.getCurrentOrder());
        manufacturer.setOrderArrivingNextWeek(distributor.getCurrentOrder());
        
        // --- Pass shipments DOWN the chain (for *2 weeks* from now) ---
        // (Ported from `passShipmentToPipeline`)
        manufacturer.setShipmentArrivingWeekAfterNext(manufacturer.getCurrentOrder()); // Manufacturer orders from self
        distributor.setShipmentArrivingWeekAfterNext(manufacturer.getOutgoingDelivery());
        wholesaler.setShipmentArrivingWeekAfterNext(distributor.getOutgoingDelivery());
        retailer.setShipmentArrivingWeekAfterNext(wholesaler.getOutgoingDelivery());
        
        // ** Loop 3: Log history & Reset Player **
        for (Players p : game.getPlayers()) {
            GameTurn turn = new GameTurn();
            turn.setWeekDay(currentWeek);
            turn.setPlayer(p);
            
            // Log all fields from friend's `WeeklyState`
            turn.setOrderPlaced(p.getCurrentOrder());         // newOrderPlaced
            turn.setDemandRecieved(p.getLastOrderReceived());  // customerOrders
            turn.setShipmentSent(p.getOutgoingDelivery());   // outgoingDelivery
            turn.setShipmentRecieved(p.getLastShipmentReceived()); // incomingDelivery
            turn.setInventoryAtEndOfWeek(p.getInventory());    // inventory
            turn.setBackOrderAtEndOfWeek(p.getBackOrder());    // backorder
            turn.setWeeklyCost(p.getWeeklyCost());           // costs
            turn.setTotalCost(p.getTotalCost());             // cumulativeCost
            
            gameTurnRepository.save(turn);

            // Reset player for next turn
            p.setReadyForOrder(false);
            playerRepository.save(p); // Save all changes to the player
        }
        
        game.setCurrentWeek(currentWeek + 1);
        
        if (game.getCurrentWeek() > GameConfig.GAME_WEEKS) {
            game.setGameStatus(Game.GameStatus.FINISHED);
        }
        
        gameRepository.save(game);
        
        log.info("Game {} advanced to week {}", gameId, game.getCurrentWeek());
        
        broadcastGameState(gameId);
    }

    /**
     * Helper method to fetch the current game state and publish it to Redis.
     */
    private void broadcastGameState(String gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        
        GameStateDTO newState = GameStateDTO.fromGame(game);
        String channel = "game-updates:" + gameId;
        
        log.info("Publishing new game state to Redis channel: {}", channel);
        redisTemplate.convertAndSend(channel, newState);
    }
}