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

        int nextFestiveWeek = game.getFestiveWeeks().stream()
                .filter(w -> w >= game.getCurrentWeek())
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("game_id",           game.getId());
        payload.put("turn_number",        game.getCurrentWeek());
        payload.put("role",               botPlayer.getRole().toString());
        payload.put("consumer_demand",    botPlayer.getLastOrderReceived());
        payload.put("current_inventory",  botPlayer.getInventory());
        payload.put("current_backlog",    botPlayer.getBackOrder());
        payload.put("incoming_shipments", botPlayer.getIncomingShipment());
        payload.put("festive_week",       nextFestiveWeek);

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