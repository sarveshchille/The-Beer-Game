package com.beergame.backend.service;

import com.beergame.backend.model.BotType;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final RestTemplate restTemplate;

    @Value("${bot.service.url}")
    private String botServiceUrl;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5_000; // 5s between retries

    public int calculateOrder(Game game, Players botPlayer) {
        return calculateOrder(game, botPlayer, botPlayer.getBotType());
    }

    public int calculateOrder(Game game, Players botPlayer, BotType activeBotType) {
        String endpoint = resolveEndpoint(activeBotType);
        Map<String, Object> payload = buildPayload(game, botPlayer);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        botServiceUrl + endpoint, payload, Map.class);

                if (response == null || !response.containsKey("predicted_order")) {
                    log.error("Invalid bot response for game {} (attempt {})", game.getId(), attempt);
                    continue; // retry
                }

                int order = ((Number) response.get("predicted_order")).intValue();
                log.info("Bot {} calculated order {} for game {} week {} (attempt {})",
                        botPlayer.getUserName(), order, game.getId(), game.getCurrentWeek(), attempt);
                return order;

            } catch (Exception e) {
                log.warn("Bot call failed for player {} in game {} (attempt {}/{}): {}",
                        botPlayer.getUserName(), game.getId(), attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("All {} attempts failed for player {} in game {}. Defaulting to order 0.",
                MAX_RETRIES, botPlayer.getUserName(), game.getId());
        return 0;
    }

    private Map<String, Object> buildPayload(Game game, Players botPlayer) {
        int currentWeek = game.getCurrentWeek();

        int nextFestiveWeek = game.getFestiveWeeks().stream()
                .filter(w -> w > currentWeek)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

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