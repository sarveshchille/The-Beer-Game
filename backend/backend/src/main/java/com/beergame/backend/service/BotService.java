package com.beergame.backend.service;

import com.beergame.backend.model.BotType;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.event.EventListener;
import com.beergame.backend.event.GameFinishedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class BotService {

    private final RestTemplate restTemplate;
    private final OrderService orderService;
    private final GameService gameService;

    @Autowired
    public BotService(RestTemplate restTemplate, @Lazy OrderService orderService, @Lazy GameService gameService) {
        this.restTemplate = restTemplate;
        this.orderService = orderService;
        this.gameService = gameService;
    }

    @Value("${bot.service.url}")
    private String botServiceUrl;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5_000; // 5s between retries

    @Async
    public void calculateAndPlaceOrderAsync(Game game, Players botPlayer, BotType activeBotType, int targetWeek) {
        int order = calculateOrder(game, botPlayer, activeBotType);
        if (game.getGameRoom() != null) {
            gameService.submitRoomOrder(game.getGameRoom().getId(), botPlayer.getUserName(), order);
        } else {
            orderService.placeOrder(game.getId(), botPlayer.getUserName(), order, targetWeek);
        }
    }

    @Async
    public void ping() {
        try {
            restTemplate.getForEntity(botServiceUrl + "/docs", String.class);
        } catch (Exception e) {
            log.trace("Pinged bot service.");
        }
    }

    @Async
    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("game_id", event.getGameId());
            restTemplate.postForObject(botServiceUrl + "/end_game", payload, Map.class);
            log.info("Cleared memory for game {} in ML service.", event.getGameId());
        } catch (Exception e) {
            log.warn("Failed to clear ML memory for game {}: {}", event.getGameId(), e.getMessage());
        }
    }

    public int calculateOrder(Game game, Players botPlayer) {
        return calculateOrder(game, botPlayer, botPlayer.getBotType());
    }

 // BotService.java - run in a separate thread with a hard timeout
public int calculateOrder(Game game, Players botPlayer, BotType activeBotType) {
    String endpoint = resolveEndpoint(activeBotType);
    Map<String, Object> payload = buildPayload(game, botPlayer);

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                    botServiceUrl + endpoint, payload, Map.class);
                return resp;
            });

            Map<String, Object> response = future.get(10, TimeUnit.SECONDS); // hard deadline

            if (response != null && response.containsKey("predicted_order")) {
                int order = ((Number) response.get("predicted_order")).intValue();
                order = Math.max(0, Math.min(order, GameService.MAX_ORDER_AMOUNT)); 
                return order;
            }
        } catch (TimeoutException e) {
            log.warn("Bot call timed out for {} attempt {}/{}", botPlayer.getUserName(), attempt, MAX_RETRIES);
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        } catch (Exception e) {
            log.warn("Bot call failed attempt {}/{}: {}", attempt, MAX_RETRIES, e.getMessage());
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }
    }
    log.error("All attempts failed for {}. Defaulting to 0.", botPlayer.getUserName());
    return 0;
}

    private Map<String, Object> buildPayload(Game game, Players botPlayer) {
        int currentWeek = game.getCurrentWeek();

        int nextFestiveWeek = game.getFestiveWeeks().stream()
                .filter(w -> w > currentWeek)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(99); // Use 99 so (festive_week - week) > 0 and prevents Python side math errors on week 22

        Map<String, Object> payload = new HashMap<>();
        payload.put("game_id", game.getId());
        payload.put("role", botPlayer.getRole().toString());
        payload.put("week", currentWeek);
        payload.put("festive_week", nextFestiveWeek);

        payload.put("last_order_received", botPlayer.getLastOrderReceived());
        payload.put("inventory", botPlayer.getInventory());
        payload.put("back_order", botPlayer.getBackOrder());
        payload.put("incoming_shipment", botPlayer.getIncomingShipment());

        payload.put("order_arriving_next_week", botPlayer.getOrderArrivingNextWeek());
        payload.put("shipment_arriving_week_after_next", botPlayer.getShipmentArrivingWeekAfterNext());
        payload.put("last_shipment_received", botPlayer.getLastShipmentReceived());
        payload.put("outgoing_delivery", botPlayer.getOutgoingDelivery());
        payload.put("weekly_cost", botPlayer.getWeeklyCost());

        return payload;
    }

    private String resolveEndpoint(BotType botType) {
        return switch (botType) {
            case EASY -> "/easy_bot/predict_order";
            case MEDIUM -> "/medium_bot/predict_order";
            case HARD -> "/hard_bot/predict_order";
        };
    }
}