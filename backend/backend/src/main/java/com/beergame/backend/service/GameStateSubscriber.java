package com.beergame.backend.service;

import com.beergame.backend.dto.GameStateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Receives deserialised GameStateDTO from the Redis listener adapter and
 * forwards it to the correct WebSocket topic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameStateSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Called by the Redis MessageListenerAdapter (configured in RedisConfig)
     * with an already-deserialised GameStateDTO.
     */
    public void receiveMessage(GameStateDTO gameState) {
        try {
            String topic = "/topic/game/" + gameState.gameId();
            log.info("Received game state from Redis. Broadcasting to: {}", topic);
            messagingTemplate.convertAndSend(topic, gameState);
        } catch (Exception e) {
            log.error("Error broadcasting game state to WebSocket", e);
        }
    }
}