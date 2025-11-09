package com.beergame.backend.dto;

import java.time.LocalDateTime;

public record RegisterResponseDTO(int token,LocalDateTime tokenIssuedAt) {

}
