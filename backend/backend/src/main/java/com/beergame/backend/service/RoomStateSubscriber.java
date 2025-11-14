package com.beergame.backend.service;

import com.beergame.backend.dto.RoomStateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomStateSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    // This method is called by the 'roomListenerAdapter'
    public void receiveMessage(RoomStateDTO roomState) {
        try {
            String roomId = roomState.getRoomId(); // Assumes RoomStateDTO has getRoomId()
            String topic = "/topic/room/" + roomId; // 👈 Make sure your frontend listens here

            log.info("Received room state from Redis. Broadcasting to WebSocket topic: {}", topic);

            messagingTemplate.convertAndSend(topic, roomState);

        } catch (Exception e) {
            log.error("Error broadcasting WebSocket message", e);
        }
    }
}