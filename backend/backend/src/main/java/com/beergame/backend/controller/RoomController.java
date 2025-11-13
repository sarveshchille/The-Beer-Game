package com.beergame.backend.controller;

import com.beergame.backend.dto.JoinRoomRequestDTO;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.Players;
import com.beergame.backend.service.RoomManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomManagerService roomManagerService;

    @PostMapping("/create")
    public ResponseEntity<GameRoom> createRoom() {
        GameRoom newRoom = roomManagerService.createRoom();
        return ResponseEntity.ok(newRoom);
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoom> joinRoom(@PathVariable String roomId,
            @RequestBody JoinRoomRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        GameRoom room = roomManagerService.joinRoom(
                roomId,
                request.teamName(),
                Players.RoleType.valueOf(request.role().toUpperCase()),
                userDetails.getUsername());
        return ResponseEntity.ok(room);
    }
}
