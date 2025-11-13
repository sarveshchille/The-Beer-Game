package com.beergame.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.repository.GameRepository;
import com.beergame.backend.repository.GameRoomRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CleanUpService {

    private final GameRepository gameRepository;
    private final GameRoomRepository gameRoomRepository;

    private final int FINISHED_GAMES_CLEANUP_DAYS;
    private final int FINISHED_ROOMS_CLEANUP_DAYS;

    public CleanUpService(GameRepository gameRepository,
            GameRoomRepository gameRoomRepository,
            @Value("${app.cleanup.games.days}") int finishedGamesCleanupDays,
            @Value("${app.cleanup.rooms.days}") int finishedRoomsCleanupDays) {
        this.gameRepository = gameRepository;
        this.gameRoomRepository = gameRoomRepository;
        this.FINISHED_GAMES_CLEANUP_DAYS = finishedGamesCleanupDays;
        this.FINISHED_ROOMS_CLEANUP_DAYS = finishedRoomsCleanupDays;
    }

    @Scheduled(fixedRateString = "PT24H")
    @Transactional
    public void cleanUpFinishedGames() {
        log.info("Starting finished SINGLE games cleanup...");

        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(FINISHED_GAMES_CLEANUP_DAYS);

        List<Game> gamesToDelete = gameRepository.findByGameStatusAndCreatedAt(
                Game.GameStatus.FINISHED,
                expiryThreshold);

        if (gamesToDelete.isEmpty()) {
            log.info("No finished single games older than {} days to delete.", FINISHED_GAMES_CLEANUP_DAYS);
            return;
        }

        log.info("Found {} finished single games to delete.", gamesToDelete.size());
        gameRepository.deleteAll(gamesToDelete);
        log.info("Cleanup of finished single games and their data is complete.");
    }

    @Scheduled(fixedRateString = "PT24H", initialDelayString = "PT1H")
    @Transactional
    public void cleanUpFinishedRooms() {
        log.info("Starting finished ROOMS cleanup...");

        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(FINISHED_ROOMS_CLEANUP_DAYS);

        List<GameRoom> roomsToDelete = gameRoomRepository.findByStatusAndFinishedAtBefore(
                GameRoom.RoomStatus.FINISHED,
                expiryThreshold);

        if (roomsToDelete.isEmpty()) {
            log.info("No finished rooms older than {} days to delete.", FINISHED_ROOMS_CLEANUP_DAYS);
            return;
        }

        log.info("Found {} finished rooms to delete.", roomsToDelete.size());

        gameRoomRepository.deleteAll(roomsToDelete);

        log.info("Cleanup of finished rooms and all associated games/teams/players/turns is complete.");
    }
}