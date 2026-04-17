package com.beergame.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JoinRoomRequestDTO(
    @NotBlank(message = "Team name is required")
    String teamName,
    @NotNull(message = "Role is required")
    String role
) {}