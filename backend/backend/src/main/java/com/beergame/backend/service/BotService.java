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
// Keep this for standard bots
    public int calculateOrder(Game game, Players botPlayer) {
        return calculateOrder(game, botPlayer, botPlayer.getBotType());
    }

    // Use this for the AFK service
    public int calculateOrder(Game game, Players botPlayer, BotType activeBotType) {
        try {
            // FIX: Use activeBotType, not the entity's getBotType()
            String endpoint = resolveEndpoint(activeBotType); 

            int currentWeek = game.getCurrentWeek();

            int nextFestiveWeek = game.getFestiveWeeks().stream()
                    .filter(w -> w > currentWeek)
                    .mapToInt(Integer::intValue)
                    .min()
                    .orElse(0);

            Map<String, Object> payload = new HashMap<>();
            payload.put("game_id",           game.getId());
            payload.put("role",              botPlayer.getRole().toString());
            payload.put("week",              currentWeek);
            payload.put("festive_week",      nextFestiveWeek);

            payload.put("last_order_received", botPlayer.getLastOrderReceived());
            payload.put("inventory",           botPlayer.getInventory());
            payload.put("back_order",          botPlayer.getBackOrder());
            payload.put("incoming_shipment",   botPlayer.getIncomingShipment());

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
            return 0; 
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