package com.beergame.backend.service;

import com.beergame.backend.dto.GameStateDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.lang.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameStateSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void receiveMessage(GameStateDTO gameState) {
        try {
            String gameId = gameState.gameId();
            String topic = "/topic/game/" + gameId;
            
            log.info("Received game state from Redis. Broadcasting to WebSocket topic: {}", topic);
            
            // Send the game state to all subscribed clients
            messagingTemplate.convertAndSend(topic, gameState);
            
        } catch (Exception e) {
            log.error("Error broadcasting WebSocket message", e);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void onMessage(@NonNull Message message, @NonNull byte[] pattern) {
        try {
            GameStateDTO gameState = objectMapper.readValue(message.getBody(), GameStateDTO.class);
            receiveMessage(gameState);
        } catch (Exception e) {
            log.error("Error processing message from Redis", e);
        }
    }
}