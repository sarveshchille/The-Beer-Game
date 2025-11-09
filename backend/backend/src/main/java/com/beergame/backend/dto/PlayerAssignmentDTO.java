package com.beergame.backend.dto;

import com.beergame.backend.model.Players;
import lombok.Data;

@Data
public class PlayerAssignmentDTO{

    private String username;
    private String initialTeamName;
    private String gameId;
    private Players.RoleType assignedRole;
    private boolean isReady;

    public static PlayerAssignmentDTO fromPlayer(Players player) {
        PlayerAssignmentDTO dto = new PlayerAssignmentDTO();
        dto.setUsername(player.getUserName());
        
        if (player.getInitialTeam() != null) {
            dto.setInitialTeamName(player.getInitialTeam().getTeamName());
        }
        
        if (player.getGame() != null) {
            dto.setGameId(player.getGame().getId());
        }

        dto.setAssignedRole(player.getRole());
        dto.setReady(player.isReadyForOrder());
        return dto;
    }
}