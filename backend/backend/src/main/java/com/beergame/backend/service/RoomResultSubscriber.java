package com.beergame.backend.service;

import com.beergame.backend.dto.RoomResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Receives a deserialised RoomResultDTO from the Redis listener adapter
 * (channel: room-result:{roomId}) and forwards it to the WebSocket topic
 * /topic/room/{roomId}/result so the frontend can show the winner screen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomResultSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public void receiveMessage(RoomResultDTO result) {
        try {
            String topic = "/topic/room/" + result.getRoomId() + "/result";
            log.info("Received room result from Redis. Broadcasting to WebSocket topic: {}", topic);
            messagingTemplate.convertAndSend(topic, result);
        } catch (Exception e) {
            log.error("Error broadcasting room result to WebSocket", e);
        }
    }
}
