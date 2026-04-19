package com.beergame.backend.dto;

import com.beergame.backend.model.GameRoom;
import lombok.Data;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class RoomStateDTO {
    private String roomId;
    private GameRoom.RoomStatus roomStatus;
    private int currentWeek;
    private List<PlayerAssignmentDTO> players;

    public static RoomStateDTO fromGameRoom(GameRoom room) {
        RoomStateDTO dto = new RoomStateDTO();
        dto.setRoomId(room.getId());
        dto.setRoomStatus(room.getStatus());

        // BUG 2 FIX: room.getGames() is null while room is in WAITING status
        boolean hasGames = room.getGames() != null && !room.getGames().isEmpty();
        dto.setCurrentWeek(hasGames ? room.getGames().iterator().next().getCurrentWeek() : 0);

        // BUG 1 FIX: team.getPlayers() can be null for brand-new teams with no players
        List<PlayerAssignmentDTO> playerStates = (room.getTeams() != null ? room.getTeams().stream() : Stream.<com.beergame.backend.model.Team>empty())
                .flatMap(team -> team.getPlayers() != null ? team.getPlayers().stream() : Stream.empty())
                .map(PlayerAssignmentDTO::fromPlayer)
                .collect(Collectors.toList());

        dto.setPlayers(playerStates);
        return dto;
    }
}