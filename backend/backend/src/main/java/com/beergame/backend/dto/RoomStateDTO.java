package com.beergame.backend.dto;

import com.beergame.backend.model.GameRoom;
import lombok.Data;
import java.util.List;
import java.util.stream.Collectors;

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

        dto.setCurrentWeek(
                room.getGames().isEmpty() ? 0 : room.getGames().get(0).getCurrentWeek());

        List<PlayerAssignmentDTO> playerStates = room.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .map(PlayerAssignmentDTO::fromPlayer)
                .collect(Collectors.toList());

        dto.setPlayers(playerStates);
        return dto;
    }
}