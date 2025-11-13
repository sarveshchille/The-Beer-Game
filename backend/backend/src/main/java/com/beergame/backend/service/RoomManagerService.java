package com.beergame.backend.service;

import com.beergame.backend.config.GameConfig;
import com.beergame.backend.dto.RoomStateDTO;
import com.beergame.backend.model.*;
import com.beergame.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomManagerService {

    private final GameRoomRepository gameRoomRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GameRepository gameRepository; // <-- NEEDED FOR BUG 2 FIX

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateRandomId(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    public GameRoom createRoom() {
        GameRoom room = new GameRoom();
        
        String newRoomId = generateRandomId(10);
        while(gameRoomRepository.existsById(newRoomId)) {
            newRoomId = generateRandomId(10);
        }
        
        room.setId(newRoomId); // Manually set the new ID
        room.setStatus(GameRoom.RoomStatus.WAITING);
        GameRoom savedRoom = gameRoomRepository.save(room);
        log.info("Created new GameRoom with ID: {}", savedRoom.getId());
        return savedRoom;
    }

    public GameRoom joinRoom(String roomId, String teamName, Players.RoleType role, String username) {
        
        @SuppressWarnings("null")
        GameRoom room = gameRoomRepository.findByIdWithAllData(roomId) // <-- FIX 1: Fetch all data at the start
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        if (room.getStatus() != GameRoom.RoomStatus.WAITING) {
            throw new RuntimeException("This room is already running or finished.");
        }

        PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Team team = teamRepository.findByGameRoomAndTeamName(room, teamName)
                .orElseGet(() -> {
                    if (room.getTeams() != null && room.getTeams().size() >= 4) {
                        throw new RuntimeException("Room is full (4 teams already exist).");
                    }
                    Team newTeam = new Team();
                    newTeam.setTeamName(teamName);
                    newTeam.setGameRoom(room);
                    return teamRepository.save(newTeam);
                });

        boolean roleTaken = team.getPlayers() != null && team.getPlayers().stream()
                .anyMatch(p -> p.getRole() == role);
        if (roleTaken) {
            throw new RuntimeException("Role " + role + " is already taken on team " + teamName);
        }

        // --- FIX 1 (CONTINUED): Efficient player check ---
        // Check if this player is already in any team in *this* room
        boolean playerExists = room.getTeams().stream()
            .flatMap(t -> (t.getPlayers() != null ? t.getPlayers().stream() : Stream.empty()))
            .anyMatch(p -> p.getPlayerInfo().getId().equals(playerInfo.getId()));
        
        if (playerExists) {
            throw new RuntimeException("Player " + username + " is already in this room.");
        }
        // --- End of Fix 1 ---

        Players player = new Players();
        player.setPlayerInfo(playerInfo);
        player.setUserName(playerInfo.getUserName());
        player.setRole(role);
        player.setInitialTeam(team);
        playerRepository.save(player);
        
        // Add player to in-memory list for isRoomFull() check
        if (team.getPlayers() == null) {
            team.setPlayers(new ArrayList<>());
        }
        team.getPlayers().add(player);

        log.info("Player {} joined room {} on team {} as {}", username, roomId, teamName, role);
        
        broadcastRoomState(roomId, room); // Broadcast player join

        if (isRoomFull(room)) {
            startGame(room);
            // startGame will re-fetch data and broadcast the "Game Started" message
        }

        return room;
    }

    private boolean isRoomFull(GameRoom room) {
        if (room.getTeams() == null || room.getTeams().size() != 4)
            return false;
        return room.getTeams().stream().allMatch(t -> t.getPlayers() != null && t.getPlayers().size() == 4);
    }

    private void startGame(GameRoom room) {
        log.info("Room {} is full! Starting game and shuffling players...", room.getId());
        room.setStatus(GameRoom.RoomStatus.RUNNING);

        List<Game> newGames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Game game = new Game();

            // --- FIX 2: Manually generate IDs for the 4 games ---
            String newGameId = generateRandomId(10);
            while(gameRepository.existsById(newGameId)) {
                newGameId = generateRandomId(10);
            }
            game.setId(newGameId);
            // --- End of Fix 2 ---
            game.setGameStatus(Game.GameStatus.IN_PROGRESS);
            game.setCurrentWeek(1);
            game.setCreatedAt(LocalDateTime.now());
            game.setGameRoom(room);
            newGames.add(game);
        }
        room.setGames(newGames); // This will cascade-save the new games

        List<Team> teams = room.getTeams();
        Players.RoleType[] roles = {
                Players.RoleType.RETAILER,
                Players.RoleType.WHOLESALER,
                Players.RoleType.DISTRIBUTOR,
                Players.RoleType.MANUFACTURER
        };

        for (int i = 0; i < 4; i++) {
            Game currentGame = newGames.get(i);

            for (int j = 0; j < 4; j++) {
                Players.RoleType initialRoleToGet = roles[(i + j) % 4];
                Players.RoleType newRoleToPlay = roles[j];

                Team team = teams.get(j);
                Players playerToAssign = team.getPlayers().stream()
                        .filter(p -> p.getRole() == initialRoleToGet)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Shuffle logic failed: Player not found"));

                playerToAssign.setGame(currentGame);
                playerToAssign.setRole(newRoleToPlay);

                playerToAssign.setInventory(GameConfig.INITIAL_INVENTORY);
                playerToAssign.setBackOrder(0);
                playerToAssign.setTotalCost(0);
                playerToAssign.setWeeklyCost(0);

                if (newRoleToPlay == Players.RoleType.RETAILER) {
                    playerToAssign.setOrderArrivingNextWeek(GameConfig.getCustomerDemand(1));
                } else {
                    playerToAssign.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);
                }
                
                // --- FIX 3: Correct pipeline initialization ---
                playerToAssign.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL); // Arrives week 1
                playerToAssign.setShipmentArrivingWeekAfterNext(GameConfig.INITIAL_PIPELINE_LEVEL); // Arrives week 2
                // --- End of Fix 3 ---

                playerRepository.save(playerToAssign);
            }
        }
        gameRoomRepository.save(room); // Save the room with all games and player updates
        log.info("Room {} started successfully.", room.getId());

        // Re-fetch the fully saved room before broadcasting
        GameRoom finalRoom = gameRoomRepository.findByIdWithAllData(room.getId()).get();
        broadcastRoomState(finalRoom.getId(), finalRoom);
    }

    @SuppressWarnings("null")
    private void broadcastRoomState(String roomId, GameRoom room) {
        // Create the new DTO
        RoomStateDTO roomState = RoomStateDTO.fromGameRoom(room);
        String channel = "room-updates:" + roomId;

        log.info("Broadcasting state for room {} to Redis channel: {}", roomId, channel);
        redisTemplate.convertAndSend(channel, roomState);
    }
}