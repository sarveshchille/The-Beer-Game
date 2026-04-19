package com.beergame.backend.controller;

import com.beergame.backend.dto.JoinRoomRequestDTO;
import com.beergame.backend.dto.RoomStateDTO;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.Players;
import com.beergame.backend.service.RoomManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
public class RoomController {

    private final RoomManagerService roomManagerService;

    @PostMapping("/create")
    public ResponseEntity<RoomStateDTO> createRoom() {
        GameRoom newRoom = roomManagerService.createRoom();
        // BUG 8 FIX: return DTO instead of raw entity to avoid LazyInitializationException
        return ResponseEntity.ok(RoomStateDTO.fromGameRoom(newRoom));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomStateDTO> joinRoom(@PathVariable String roomId,
            @Validated @RequestBody JoinRoomRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        GameRoom room = roomManagerService.joinRoom(
                roomId,
                request.teamName(),
                Players.RoleType.valueOf(request.role().toUpperCase()),
                userDetails.getUsername());
        // BUG 8 FIX: return DTO instead of raw entity to avoid LazyInitializationException
        return ResponseEntity.ok(RoomStateDTO.fromGameRoom(room));
    }
}
