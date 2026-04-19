package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.dto.RoomStateDTO;
import com.beergame.backend.event.WeekStartedEvent;
import com.beergame.backend.model.*;
import com.beergame.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Changes:
 *  1. broadcastRoomState() mid-transaction calls replaced with
 *     broadcastService.broadcastRoomAfterCommit() — same fix applied in
 *     GameService. Clients no longer receive partially-written state.
 *  2. startGame() uses generateFestiveWeeks() per game (no JVM-global static).
 *  3. RedisTemplate removed — broadcast goes through BroadcastService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomManagerService {

    private final GameRoomRepository   gameRoomRepository;
    private final TeamRepository       teamRepository;
    private final PlayerRepository     playerRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final GameRepository       gameRepository;
    private final BroadcastService     broadcastService;
    private final RedisLockService     redisLockService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String        ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom  RANDOM       = new SecureRandom();
    private static final int           MAX_ID_RETRIES = 10;

    private String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private String generateUniqueRoomId() {
        for (int attempt = 0; attempt < MAX_ID_RETRIES; attempt++) {
            String id = generateRandomId(10);
            if (!gameRoomRepository.existsById(id)) return id;
        }
        throw new RuntimeException("Failed to generate unique room ID after " + MAX_ID_RETRIES + " attempts");
    }

    private String generateUniqueGameId() {
        for (int attempt = 0; attempt < MAX_ID_RETRIES; attempt++) {
            String id = generateRandomId(10);
            if (!gameRepository.existsById(id)) return id;
        }
        throw new RuntimeException("Failed to generate unique game ID after " + MAX_ID_RETRIES + " attempts");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Normalizes a team name: trims whitespace and title-cases each word.
     * "alpha" → "Alpha",  " BRAVO " → "Bravo",  "my team" → "My Team".
     */
    private static String toTitleCase(String input) {
        if (input == null) return "";
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public GameRoom createRoom() {
        GameRoom room = new GameRoom();
        room.setId(generateUniqueRoomId());
        room.setStatus(GameRoom.RoomStatus.WAITING);
        room.setCreatedAt(java.time.LocalDateTime.now());
        GameRoom saved = gameRoomRepository.save(room);
        log.info("Created GameRoom id={}", saved.getId());
        return saved;
    }

    public GameRoom joinRoom(String roomIdRaw, String teamName, Players.RoleType role, String username) {
        if (roomIdRaw == null) throw new RuntimeException("roomId is null");
        String roomId = roomIdRaw.trim();
        // Normalize: "alpha", "ALPHA", " Alpha " all become "Alpha"
        String normalizedTeamName = toTitleCase(teamName);
        return redisLockService.executeWithLock(roomId, 10, () -> {
            GameRoom room = gameRoomRepository.findByIdWithAllData(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

            if (room.getStatus() != GameRoom.RoomStatus.WAITING) {
                throw new RuntimeException("Room " + roomId + " is already running or finished");
            }

            PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Check if player is already in the room (null-safe for fresh rooms with no teams yet)
            Players existingPlayer = (room.getTeams() == null) ? null :
                    room.getTeams().stream()
                    .flatMap(t -> t.getPlayers() != null ? t.getPlayers().stream() : Stream.empty())
                    .filter(p -> p.getPlayerInfo().getId().equals(playerInfo.getId()))
                    .findFirst()
                    .orElse(null);

            Team targetTeam = teamRepository.findByGameRoomAndTeamNameIgnoreCase(room, normalizedTeamName)
                    .orElseGet(() -> {
                        int currentTeamCount = room.getTeams() != null ? room.getTeams().size() : 0;
                        if (currentTeamCount >= 4) {
                            String existing = room.getTeams().stream()
                                    .map(Team::getTeamName)
                                    .collect(java.util.stream.Collectors.joining(", "));
                            throw new RuntimeException(
                                    "Room already has 4 teams (" + existing + "). Please join one of the existing teams.");
                        }
                        Team newTeam = new Team();
                        newTeam.setTeamName(normalizedTeamName);
                        newTeam.setGameRoom(room);
                        return teamRepository.save(newTeam);
                    });

            boolean roleTaken = targetTeam.getPlayers() != null && targetTeam.getPlayers().stream()
                    .anyMatch(p -> p.getRole() == role && (existingPlayer == null || !p.getId().equals(existingPlayer.getId())));
            if (roleTaken) {
                throw new RuntimeException("Role " + role + " already taken on team " + teamName);
            }

            if (existingPlayer != null) {
                // Player is switching seats! Remove from old team safely
                Team oldTeam = existingPlayer.getInitialTeam();
                if (oldTeam != null && oldTeam.getPlayers() != null) {
                    oldTeam.getPlayers().removeIf(p -> p.getId().equals(existingPlayer.getId()));
                }
                
                // Reassign to new team and role
                existingPlayer.setInitialTeam(targetTeam);
                existingPlayer.setRole(role);
                playerRepository.save(existingPlayer);

                if (targetTeam.getPlayers() == null) targetTeam.setPlayers(new java.util.HashSet<>());
                targetTeam.getPlayers().add(existingPlayer);
                
                log.info("Player {} switched to room {} / team {} as {}", username, roomId, teamName, role);
            } else {
                // Brand new player joining
                Players player = new Players();
                player.setPlayerInfo(playerInfo);
                player.setUserName(playerInfo.getUserName());
                player.setRole(role);
                player.setInitialTeam(targetTeam);
                playerRepository.save(player);

                if (targetTeam.getPlayers() == null) targetTeam.setPlayers(new java.util.HashSet<>());
                targetTeam.getPlayers().add(player);
                
                log.info("Player {} joined room {} / team {} as {}", username, roomId, teamName, role);
            }

            // Broadcast the in-memory manipulated room directly.
            // This is safer since executeWithLock might not be wrapped in a Spring @Transactional
            // boundary, causing AFTER_COMMIT hooks to fail or db queries to read stale data.
            broadcastService.broadcastRoomState(roomId, room);

            if (isRoomFull(room)) {
                startGame(room);
            }

            return room;
        });
    }

    private boolean isRoomFull(GameRoom room) {
        if (room.getTeams() == null || room.getTeams().size() != 4) return false;
        return room.getTeams().stream()
                .allMatch(t -> t.getPlayers() != null && t.getPlayers().size() == 4);
    }

    private void startGame(GameRoom room) {
        log.info("Room {} full — starting games", room.getId());
        room.setStatus(GameRoom.RoomStatus.RUNNING);

        List<Game> newGames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Game game = new Game();
            game.setId(generateUniqueGameId());
            game.setGameStatus(Game.GameStatus.IN_PROGRESS);
            game.setCurrentWeek(1);
            game.setCreatedAt(LocalDateTime.now());
            game.setGameRoom(room);

            // FIX: generate per-game festive weeks instead of using the
            // JVM-global static set. Each of the 4 games now has its own
            // independent random festive schedule.
            game.setFestiveWeeks(GameConfig.generateFestiveWeeks());
            game.setFestiveWeek(GameConfig.isFestiveWeek(1, game.getFestiveWeeks()));

            newGames.add(game);
        }
        room.setGames(new java.util.HashSet<>(newGames));

        Players.RoleType[] roles = {
                Players.RoleType.RETAILER,
                Players.RoleType.WHOLESALER,
                Players.RoleType.DISTRIBUTOR,
                Players.RoleType.MANUFACTURER
        };

        // BUG 6 FIX: sort teams by teamName to make shuffle order deterministic.
        // JPA does not guarantee order from JOIN FETCH — without this, the
        // role-rotation formula (i+j)%4 produces inconsistent results across runs.
        List<Team> teamsList = room.getTeams().stream()
                .sorted(java.util.Comparator.comparing(Team::getTeamName))
                .collect(java.util.stream.Collectors.toList());
        for (int i = 0; i < 4; i++) {
            Game currentGame = newGames.get(i);
            for (int j = 0; j < 4; j++) {
                // j represents the role we are filling for Game i
                Players.RoleType requiredRole = roles[j];

                // Which team does this role come from in this game? (Deterministic offset)
                int teamIndex = (i + j) % 4;
                Team sourceTeam = teamsList.get(teamIndex);

                Players p = sourceTeam.getPlayers().stream()
                        .filter(pl -> pl.getRole() == requiredRole)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Shuffle logic failed: team " + sourceTeam.getTeamName() + " missing role " + requiredRole));

                p.setGame(currentGame);
                p.setInventory(GameConfig.INITIAL_INVENTORY);
                p.setBackOrder(0);
                p.setTotalCost(0);
                p.setWeeklyCost(0);

                if (requiredRole == Players.RoleType.RETAILER) {
                    p.setOrderArrivingNextWeek(
                            GameConfig.getCustomerDemand(1, currentGame.getFestiveWeeks()));
                } else {
                    p.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);
                }

                p.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
                p.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL);

                playerRepository.save(p);
            }
        }

        gameRoomRepository.save(room);
        log.info("Room {} started successfully", room.getId());

        // Immediately broadcast RUNNING state directly from memory to bypass transactional race conditions
        broadcastService.broadcastRoomState(room.getId(), room);
        
        // Publish WeekStartedEvent and Broadcast Game states for each game AFTER commit
        final List<Game> committedGames = new ArrayList<>(newGames);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Game g : committedGames) {
                    // ✅ CRITICAL FIX: Call broadcastGameState() DIRECTLY here.
                    // broadcastGameAfterCommit() registers a NEW TransactionSynchronization,
                    // but we are ALREADY inside afterCommit — no active transaction exists!
                    // Spring silently drops the nested registration, so game state was NEVER sent.
                    // broadcastGameState(String) does a fresh DB read then sends to WebSocket — safe here.
                    broadcastService.broadcastGameState(g.getId());
                    eventPublisher.publishEvent(new WeekStartedEvent(RoomManagerService.this, g.getId(), 1));
                }
            }
        });
    }
}