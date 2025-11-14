package com.beergame.backend.controller;

import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.dto.JoinGameRequestDTO;
import com.beergame.backend.model.Game;
import com.beergame.backend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    /**
     * Creates a new game lobby.
     * 
     * @AuthenticationPrincipal injects the user details from the JWT.
     */
    @PostMapping("/create")
    public ResponseEntity<GameStateDTO> createGame(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody JoinGameRequestDTO request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build(); // Should be handled by security config, but good practice
        }

        Game newGame = gameService.createGame(userDetails.getUsername());
        gameService.joinGame(newGame.getId(), userDetails.getUsername(), request.role());
        return ResponseEntity.ok(GameStateDTO.fromGame(newGame));
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<GameStateDTO> joinGame(@PathVariable String gameId,
            @RequestBody JoinGameRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        Game game = gameService.joinGame(gameId, userDetails.getUsername(), request.role());
        return ResponseEntity.ok(GameStateDTO.fromGame(game));
    }
}
