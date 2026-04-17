package com.beergame.backend.dto;

import com.beergame.backend.model.Players;
import jakarta.validation.constraints.NotNull; 

public record JoinGameRequestDTO(
    @NotNull(message = "Role is required")
    Players.RoleType role
) {}

