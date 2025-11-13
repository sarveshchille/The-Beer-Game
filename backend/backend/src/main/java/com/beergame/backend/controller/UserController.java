package com.beergame.backend.controller;

import com.beergame.backend.dto.AuthResponseDTO;
import com.beergame.backend.dto.LoginDTO;
import com.beergame.backend.dto.RegisterResponseDTO;
import com.beergame.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerUser(@RequestBody LoginDTO registerRequest) {

        RegisterResponseDTO response = authService.registerBeforeEmail(registerRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponseDTO> registerAfterMail(@RequestBody LoginDTO registerRequest) {

        String jwt = authService.registerAfterEmail(registerRequest);
        return ResponseEntity.ok(new AuthResponseDTO(registerRequest.username(), registerRequest.email(), jwt));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> loginUser(@RequestBody LoginDTO loginRequest) {

        String jwt = authService.login(loginRequest);
        return ResponseEntity.ok(new AuthResponseDTO(loginRequest.username(), loginRequest.email(), jwt));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteUser(@RequestBody LoginDTO loginDTO) {

        authService.deletePlayer(loginDTO);

        return ResponseEntity.ok("Player deleted successfully");
    }

    @PatchMapping("/update/username")
    public ResponseEntity<String> updateUserName(@RequestBody LoginDTO loginDTO, @RequestParam String username) {

        authService.updateUserName(loginDTO, username);

        return ResponseEntity.ok("UserName updated successfully");
    }

}