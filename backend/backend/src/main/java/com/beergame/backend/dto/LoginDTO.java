package com.beergame.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(
 
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be 3–30 characters")
        String username,
 
        @Email(message = "Must be a valid email address")
        String email,
 
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
 
) {}
