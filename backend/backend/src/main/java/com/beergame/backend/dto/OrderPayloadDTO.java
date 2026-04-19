package com.beergame.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderPayloadDTO(
    @JsonProperty("orderAmount") int orderAmount
) {
}