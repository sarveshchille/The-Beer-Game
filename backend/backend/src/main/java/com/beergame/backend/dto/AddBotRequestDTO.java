package com.beergame.backend.dto;

import com.beergame.backend.model.BotType;
import com.beergame.backend.model.Players;
import jakarta.validation.constraints.NotNull;

public record AddBotRequestDTO(
    @NotNull(message = "Role is required")
    Players.RoleType role,

    @NotNull(message = "Bot type is required")
    BotType botType
) {}