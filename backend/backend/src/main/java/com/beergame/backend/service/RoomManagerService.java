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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomManagerService {

    private final GameRoomRepository gameRoomRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PlayerInfoRepository playerInfoRepository;
    private final RedisTemplate<String, Object> redisTemplate; // <-- INJECT

    public GameRoom createRoom() {
        // ... (your existing createRoom logic is fine) ...
        GameRoom room = new GameRoom();
        room.setStatus(GameRoom.RoomStatus.WAITING);
        GameRoom savedRoom = gameRoomRepository.save(room);
        log.info("Created new GameRoom with ID: {}", savedRoom.getId());
        return savedRoom;
    }

    public GameRoom joinRoom(String roomId, String teamName, Players.RoleType role, String username) {
        // ... (your existing joinRoom logic is here) ...
        // ... (find room, find user, find/create team, check duplicates, create player)
        // ...

        @SuppressWarnings("null")
        GameRoom room = gameRoomRepository.findById(roomId)
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

        boolean playerExists = playerRepository.findAll().stream()
                .anyMatch(p -> p.getInitialTeam() != null &&
                        p.getInitialTeam().getGameRoom().getId().equals(roomId) &&
                        p.getPlayerInfo().getId().equals(playerInfo.getId()));
        if (playerExists) {
            throw new RuntimeException("Player " + username + " is already in this room.");
        }

        Players player = new Players();
        player.setPlayerInfo(playerInfo);
        player.setUserName(playerInfo.getUserName());
        player.setRole(role);
        player.setInitialTeam(team);
        playerRepository.save(player);

        log.info("Player {} joined room {} on team {} as {}", username, roomId, teamName, role);

        // --- BROADCAST LOBBY UPDATE ---
        // Fetch all data and broadcast the new lobby state
        GameRoom finalRoom = gameRoomRepository.findByIdWithAllData(roomId).get();
        broadcastRoomState(roomId, finalRoom); // Broadcast player join

        if (isRoomFull(finalRoom)) {
            startGame(finalRoom);
            // startGame will broadcast the "Game Started" message
        }

        return finalRoom;
    }

    private boolean isRoomFull(GameRoom room) {
        if (room.getTeams() == null || room.getTeams().size() != 4)
            return false;
        return room.getTeams().stream().allMatch(t -> t.getPlayers() != null && t.getPlayers().size() == 4);
    }

    private void startGame(GameRoom room) {
        log.info("Room {} is full! Starting game and shuffling players...", room.getId());
        room.setStatus(GameRoom.RoomStatus.RUNNING);

        // ... (your existing shuffle logic is here) ...
        // ... (create 4 games, perform Latin Square shuffle, assign players) ...
        List<Game> newGames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Game game = new Game();
            game.setGameStatus(Game.GameStatus.IN_PROGRESS);
            game.setCurrentWeek(1);
            game.setCreatedAt(LocalDateTime.now());
            game.setGameRoom(room);
            newGames.add(game);
        }
        room.setGames(newGames);

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
                playerToAssign.setIncomingShipment(GameConfig.INITIAL_PIPELINE_LEVEL);
                playerToAssign.setOrderArrivingNextWeek(GameConfig.INITIAL_PIPELINE_LEVEL);

                playerRepository.save(playerToAssign);
            }
        }
        gameRoomRepository.save(room);
        log.info("Room {} started successfully.", room.getId());

        broadcastRoomState(room.getId(), room);
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