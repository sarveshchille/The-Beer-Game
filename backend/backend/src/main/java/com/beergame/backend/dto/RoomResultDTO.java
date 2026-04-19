package com.beergame.backend.dto;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.Players;
import com.beergame.backend.model.Team;
import lombok.Data;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Broadcast when a 16-player room finishes all 4 games.
 * Contains the winning team, a ranked leaderboard, and per-player histories
 * so the frontend can show both the celebration screen and the comparison view.
 *
 * WebSocket topic: /topic/room/{roomId}/result
 * Redis channel  : room-result:{roomId}
 */
@Data
public class RoomResultDTO {

    private String roomId;
    private String winnerTeamName;
    private double winnerTotalCost;

    /** All 4 teams ranked by total cost ascending (lowest = best). */
    private List<TeamResultDTO> leaderboard;

    // ── Nested DTOs ────────────────────────────────────────────────────────

    @Data
    public static class TeamResultDTO {
        private int rank;
        private String teamName;
        /** Sum of totalCost across all 4 players (one per game). */
        private double totalCost;
        /** Each player's game-level summary for the comparison table. */
        private List<PlayerSummaryDTO> players;
    }

    @Data
    public static class PlayerSummaryDTO {
        private String username;
        private Players.RoleType lobbyRole;   // role they chose in the lobby
        private Players.RoleType gameRole;    // role assigned after shuffle
        private String gameId;
        private double totalCost;
        /** Link frontend to full per-week history: GET /api/game/{gameId}/history */
        private String historyUrl;
    }

    // ── Factory ────────────────────────────────────────────────────────────

    /**
     * Builds the result from a FINISHED GameRoom.
     * Assumes every Team has its players loaded (initialTeam → players relationship).
     */
    public static RoomResultDTO fromRoom(GameRoom room) {
        RoomResultDTO result = new RoomResultDTO();
        result.setRoomId(room.getId());

        List<TeamResultDTO> ranked = room.getTeams().stream()
                .map(team -> buildTeamResult(team))
                .sorted(Comparator.comparingDouble(TeamResultDTO::getTotalCost))
                .collect(Collectors.toList());

        // Assign ranks
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setRank(i + 1);
        }

        result.setLeaderboard(ranked);

        if (!ranked.isEmpty()) {
            result.setWinnerTeamName(ranked.get(0).getTeamName());
            result.setWinnerTotalCost(ranked.get(0).getTotalCost());
        }

        return result;
    }

    private static TeamResultDTO buildTeamResult(Team team) {
        TeamResultDTO dto = new TeamResultDTO();
        dto.setTeamName(team.getTeamName());

        List<PlayerSummaryDTO> playerSummaries = (team.getPlayers() != null)
                ? team.getPlayers().stream()
                        .map(p -> {
                            PlayerSummaryDTO ps = new PlayerSummaryDTO();
                            ps.setUsername(p.getUserName());
                            ps.setLobbyRole(p.getRole()); // after shuffle this reflects game role
                            ps.setGameRole(p.getRole());
                            ps.setTotalCost(p.getTotalCost());
                            if (p.getGame() != null) {
                                ps.setGameId(p.getGame().getId());
                                ps.setHistoryUrl("/api/game/" + p.getGame().getId() + "/history");
                            }
                            return ps;
                        })
                        .collect(Collectors.toList())
                : List.of();

        dto.setPlayers(playerSummaries);
        dto.setTotalCost(playerSummaries.stream()
                .mapToDouble(PlayerSummaryDTO::getTotalCost)
                .sum());
        return dto;
    }
}
