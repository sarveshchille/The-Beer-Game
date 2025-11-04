package com.beergame.backend.controller;

import com.beergame.backend.dto.AuthResponseDTO;
import com.beergame.backend.dto.LoginDTO;
import com.beergame.backend.dto.RegisterResponseDTO;
import com.beergame.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerUser(@RequestBody LoginDTO registerRequest) {
        
        RegisterResponseDTO response = authService.registerBeforeEmail(registerRequest);
        return ResponseEntity.ok(response);
    }
    

    @PostMapping("/verify")
    public ResponseEntity<String> registerAfterMail(@RequestBody LoginDTO registerRequest){

        authService.registerAfterEmail(registerRequest);
        return ResponseEntity.ok("Player registered");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> loginUser(@RequestBody LoginDTO loginRequest) {

        String jwt = authService.login(loginRequest);
        return ResponseEntity.ok(new AuthResponseDTO(jwt));
    }
}