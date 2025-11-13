package com.beergame.backend.controller;

import com.beergame.backend.dto.OrderPayloadDTO;
import com.beergame.backend.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GameSocketController {

    private final GameService gameService;

    @MessageMapping("/game/{gameId}/placeOrder")
    public void placeOrder(@DestinationVariable String gameId,
            @Payload OrderPayloadDTO payload,
            Principal principal) {

        if (principal == null) {

            log.error("Cannot place order: user is not authenticated.");
            return;
        }

        String username = principal.getName();
        gameService.placeOrder(gameId, username, payload.orderAmount());
    }

    @MessageMapping("/room/{roomId}/placeOrder")
    public void placeRoomOrder(@DestinationVariable String roomId,
            @Payload OrderPayloadDTO payload,
            Principal principal) {

        if (principal == null) {
            log.error("Cannot place room order: user is not authenticated.");
            return;
        }

        String username = principal.getName();
        gameService.submitRoomOrder(roomId, username, payload.orderAmount());
    }
}