package com.beergame.backend.dto;

import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.Players;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class RoomStateDTO {

    private String roomId;
    private GameRoom.RoomStatus roomStatus;
    private int currentWeek;
    /** Total players currently in the room across all teams (max 16). */
    private int totalPlayers;
    /** Structured team list — each entry shows members + still-open roles. */
    private List<TeamSlotDTO> teams;

    // ─── Nested DTO ────────────────────────────────────────────────────────────

    @Data
    public static class TeamSlotDTO {
        private String teamName;
        private List<PlayerAssignmentDTO> members;
        /** Roles that have no player assigned yet on this team. */
        private List<Players.RoleType> availableRoles;
    }

    // ─── Factory ───────────────────────────────────────────────────────────────

    public static RoomStateDTO fromGameRoom(GameRoom room) {
        RoomStateDTO dto = new RoomStateDTO();
        dto.setRoomId(room.getId());
        dto.setRoomStatus(room.getStatus());

        // currentWeek is 0 while room is still in WAITING (no games yet)
        boolean hasGames = room.getGames() != null && !room.getGames().isEmpty();
        dto.setCurrentWeek(hasGames ? room.getGames().iterator().next().getCurrentWeek() : 0);

        List<Players.RoleType> ALL_ROLES = Arrays.asList(Players.RoleType.values());

        List<TeamSlotDTO> teamSlots = (room.getTeams() != null
                ? room.getTeams().stream()
                : Stream.<com.beergame.backend.model.Team>empty())
                .map(team -> {
                    TeamSlotDTO slot = new TeamSlotDTO();
                    slot.setTeamName(team.getTeamName());

                    List<PlayerAssignmentDTO> members = (team.getPlayers() != null)
                            ? team.getPlayers().stream()
                                    .map(PlayerAssignmentDTO::fromPlayer)
                                    .collect(Collectors.toList())
                            : Collections.emptyList();
                    slot.setMembers(members);

                    // Compute which roles are NOT yet taken on this team
                    Set<Players.RoleType> takenRoles = (team.getPlayers() != null)
                            ? team.getPlayers().stream()
                                    .map(p -> p.getRole())
                                    .collect(Collectors.toSet())
                            : Collections.emptySet();

                    List<Players.RoleType> available = ALL_ROLES.stream()
                            .filter(r -> !takenRoles.contains(r))
                            .collect(Collectors.toList());
                    slot.setAvailableRoles(available);

                    return slot;
                })
                .collect(Collectors.toList());

        dto.setTeams(teamSlots);

        int total = teamSlots.stream()
                .mapToInt(t -> t.getMembers().size())
                .sum();
        dto.setTotalPlayers(total);

        return dto;
    }
}