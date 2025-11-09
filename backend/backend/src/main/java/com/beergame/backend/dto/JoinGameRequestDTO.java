package com.beergame.backend.dto;

import com.beergame.backend.model.Players;

public record JoinGameRequestDTO(Players.RoleType role) {
}