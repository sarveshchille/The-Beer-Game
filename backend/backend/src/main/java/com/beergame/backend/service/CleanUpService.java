package com.beergame.backend.service;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically removes abandoned game sessions that were never filled.
 *
 * Without this, any game that is created but never gets 4 players stays in
 * LOBBY status forever, and any room that never gets 16 players stays in
 * WAITING status forever — both accumulate indefinitely in the database.
 *
 * Requires @EnableScheduling on BackendApplication (already present).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanUpService {

    private final GameRepository     gameRepository;
    private final GameRoomRepository gameRoomRepository;

    /** Lobbies older than this are considered abandoned. */
    private static final int LOBBY_EXPIRY_MINUTES = 30;

    /** Waiting rooms older than this are considered abandoned. */
    private static final int ROOM_EXPIRY_MINUTES  = 60;

    /**
     * Runs every 15 minutes.
     * Deletes LOBBY games that were created more than 30 minutes ago.
     *
     * CascadeType.ALL on Game.players means the orphaned Players rows are
     * deleted automatically.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    @Transactional
    public void expireStaleLobbyGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(LOBBY_EXPIRY_MINUTES);

        List<Game> staleLobbies = gameRepository
        .findByGameStatusAndCreatedAtBefore(Game.GameStatus.LOBBY, cutoff);

        if (staleLobbies.isEmpty()) {
            return;
        }

        log.info("Cleanup: deleting {} stale LOBBY game(s) older than {} minutes",
                staleLobbies.size(), LOBBY_EXPIRY_MINUTES);

        gameRepository.deleteAll(staleLobbies);
    }

    /**
     * Runs every 30 minutes.
     * Deletes WAITING rooms that were created more than 60 minutes ago.
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    @Transactional
    public void expireStaleWaitingRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ROOM_EXPIRY_MINUTES);

        List<GameRoom> staleRooms = gameRoomRepository
                .findByStatusAndFinishedAtBefore(GameRoom.RoomStatus.WAITING, cutoff);

        if (staleRooms.isEmpty()) {
            return;
        }

        log.info("Cleanup: deleting {} stale WAITING room(s) older than {} minutes",
                staleRooms.size(), ROOM_EXPIRY_MINUTES);

        gameRoomRepository.deleteAll(staleRooms);
    }
}