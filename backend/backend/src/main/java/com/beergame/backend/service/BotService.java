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
    private String botServiceUrl; // e.g. http://localhost:8001

    /**
     * Builds the game state payload, calls the correct FastAPI endpoint
     * based on bot type, and submits the returned order via GameService.
     */
public int calculateOrder(Game game, Players botPlayer) {
    try {
        String endpoint = resolveEndpoint(botPlayer.getBotType());

int currentWeek = game.getCurrentWeek();

// Strictly greater than currentWeek — current week's festive status
// is already reflected in inventory/backlog the bot can see.
// 0 = no upcoming festive week (Python handles this as the no-panic sentinel)
int nextFestiveWeek = game.getFestiveWeeks().stream()
        .filter(w -> w > currentWeek)
        .mapToInt(Integer::intValue)
        .min()
        .orElse(0);

// Inside BotService.java -> calculateOrder()

Map<String, Object> payload = new HashMap<>();
payload.put("game_id",           game.getId());
payload.put("role",              botPlayer.getRole().toString());
payload.put("week",              currentWeek); // Changed to match pandas
payload.put("festive_week",      nextFestiveWeek);

// Base state variables (matching features.py expectations)
payload.put("last_order_received", botPlayer.getLastOrderReceived());
payload.put("inventory",           botPlayer.getInventory());
payload.put("back_order",          botPlayer.getBackOrder());
payload.put("incoming_shipment",   botPlayer.getIncomingShipment());

// Pipeline & Cost variables needed for the feature engineer
payload.put("order_arriving_next_week",          botPlayer.getOrderArrivingNextWeek());
payload.put("shipment_arriving_week_after_next", botPlayer.getShipmentArrivingWeekAfterNext());
payload.put("last_shipment_received",            botPlayer.getLastShipmentReceived());
payload.put("outgoing_delivery",                 botPlayer.getOutgoingDelivery());
payload.put("weekly_cost",                       botPlayer.getWeeklyCost());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                botServiceUrl + endpoint, payload, Map.class);

        if (response == null || !response.containsKey("predicted_order")) {
            log.error("Invalid bot response for game {}", game.getId());
            return 0;
        }

        int order = ((Number) response.get("predicted_order")).intValue();
        log.info("Bot {} calculated order {} for game {} week {}",
                botPlayer.getUserName(), order, game.getId(), game.getCurrentWeek());
        return order;

    } catch (Exception e) {
        log.error("Bot calculation failed for player {} in game {}: {}",
                botPlayer.getUserName(), game.getId(), e.getMessage(), e);
        return 0; // Fallback — game won't be stuck
    }
}

    private String resolveEndpoint(BotType botType) {
        return switch (botType) {
            case EASY   -> "/easy_bot/predict_order";
            case MEDIUM -> "/medium_bot/predict_order";
            case HARD   -> "/hard_bot/predict_order";
        };
    }
}