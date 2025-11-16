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

    @PostMapping("/create")
    public ResponseEntity<GameStateDTO> createGame(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody JoinGameRequestDTO request) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        // Step 1: create empty game
        Game newGame = gameService.createGame(userDetails.getUsername());

        // Step 2: creator joins as the first player.
        // THIS CALL SAVES THE PLAYER AND RETURNS THE UPDATED GAME.
        Game updatedGame = gameService.joinGame(
                newGame.getId(),
                userDetails.getUsername(),
                request.role());

        // Step 3: Return the game state *from the joinGame call*.
        // This state is guaranteed to have the creator in it.
        // This fixes the "0/4 players" bug.
        return ResponseEntity.ok(GameStateDTO.fromGame(updatedGame));
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

    @GetMapping("/{gameId}/history")
    public ResponseEntity<?> getGameHistory(@PathVariable String gameId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(gameService.getGameHistory(gameId));
    }
}