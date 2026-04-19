package com.beergame.backend.service;

import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.dto.RoomResultDTO;
import com.beergame.backend.dto.RoomStateDTO;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Centralises all Redis / WebSocket broadcast logic.
 *
 * Extracting this into its own bean breaks the circular dependency that would
 * otherwise exist between GameService ↔ TurnService when both need to broadcast.
 *
 * Dependency graph (no cycles):
 *   GameService  ──► BroadcastService ──► RedisTemplate
 *   TurnService  ──► BroadcastService     GameRepository
 *                                         GameRoomRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final GameRepository     gameRepository;
    private final GameRoomRepository gameRoomRepository;

    // ───────────────────────────────────────────────────────────────────── //
    //  Game broadcasts
    // ───────────────────────────────────────────────────────────────────── //

    public void broadcastGameState(Game game) {
        GameStateDTO dto    = GameStateDTO.fromGame(game);
        String       channel = "game-updates:" + game.getId();
        log.info("Publishing game state on Redis channel: {}", channel);
        redisTemplate.convertAndSend(channel, dto);
    }

    public void broadcastGameState(String gameId) {
        try {
            Game fresh = gameRepository.findByIdWithPlayers(gameId)
                    .orElseThrow(() -> new RuntimeException("Game not found: " + gameId));
            broadcastGameState(fresh);
        } catch (Exception e) {
            log.error("Failed to broadcast game state for id {}: {}", gameId, e.getMessage(), e);
        }
    }

    /**
     * Registers a callback that re-fetches the game from DB and broadcasts
     * ONLY after the current transaction has successfully committed.
     *
     * This is the safe pattern: it prevents sending stale or partially-written
     * state to clients.
     */
    public void broadcastGameAfterCommit(String gameId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("TX committed for game {}. Broadcasting.", gameId);
                broadcastGameState(gameId);
            }
        });
    }

    // ───────────────────────────────────────────────────────────────────── //
    //  Room broadcasts
    // ───────────────────────────────────────────────────────────────────── //

    public void broadcastRoomState(String roomId, GameRoom room) {
        RoomStateDTO dto     = RoomStateDTO.fromGameRoom(room);
        String       channel = "room-updates:" + roomId;
        log.info("Broadcasting room state for room {} to Redis channel: {}", roomId, channel);
        redisTemplate.convertAndSend(channel, dto);
    }

    public void broadcastRoomState(String roomId) {
        try {
            GameRoom fresh = gameRoomRepository.findByIdWithAllData(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));
            broadcastRoomState(roomId, fresh);
        } catch (Exception e) {
            log.error("Failed to broadcast room state for id {}: {}", roomId, e.getMessage(), e);
        }
    }

    /**
     * Same post-commit safety pattern as {@link #broadcastGameAfterCommit},
     * but re-fetches and broadcasts the room state instead.
     */
    public void broadcastRoomAfterCommit(String roomId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("TX committed for room {}. Broadcasting.", roomId);
                broadcastRoomState(roomId);
            }
        });
    }

    // ───────────────────────────────────────────────────────────────────── //
    //  Room result broadcast (fired once when room is FINISHED)
    // ───────────────────────────────────────────────────────────────────── //

    /**
     * Publishes the winner announcement + full leaderboard on a dedicated Redis
     * channel so all subscribers can render the results / comparison screen.
     *
     * Channel : room-result:{roomId}
     * WS topic: /topic/room/{roomId}/result
     */
    public void broadcastRoomResult(GameRoom room) {
        RoomResultDTO result = RoomResultDTO.fromRoom(room);
        String channel = "room-result:" + room.getId();
        log.info("Broadcasting room result for room {} — winner: {}", room.getId(), result.getWinnerTeamName());
        redisTemplate.convertAndSend(channel, result);
    }
}