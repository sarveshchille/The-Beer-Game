package com.beergame.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.beergame.backend.dto.LoginDTO;
import com.beergame.backend.dto.RegisterResponseDTO;
import com.beergame.backend.exception.EmailIdAlreadyExistsException;
import com.beergame.backend.exception.UserDoesNotExistException;
import com.beergame.backend.exception.UserNameAlreadyExistsException;
import com.beergame.backend.model.PlayerInfo;
import com.beergame.backend.repository.PlayerInfoRepository;
import com.beergame.backend.utils.JwtUtils;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final PlayerInfoRepository playerInfoRepository;
    private final PasswordEncoder      passwordEncoder;
    private final EmailService         emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils             jwtUtils;

    // FIX: Math.random() is not cryptographically secure — replaced with
    // SecureRandom. An attacker who can observe the seed or timing of
    // Math.random() can predict future OTPs. SecureRandom uses OS entropy.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RegisterResponseDTO registerBeforeEmail(LoginDTO loginDTO) {
        if (playerInfoRepository.existsByUserName(loginDTO.username())) {
            throw new UserNameAlreadyExistsException("Username already taken");
        }
        if (playerInfoRepository.existsByEmailId(loginDTO.email())) {
            throw new EmailIdAlreadyExistsException("Email already registered");
        }

        // FIX: was Math.random() — now SecureRandom
        int token = 100_000 + SECURE_RANDOM.nextInt(900_000);
        log.info("OTP generated for {}", loginDTO.email());

        emailService.sendVerificationEmail(loginDTO.email(), token);
        return new RegisterResponseDTO(token, LocalDateTime.now());
    }

    @Transactional
    public String registerAfterEmail(LoginDTO loginDTO) {
        log.info("Completing registration for username={}", loginDTO.username());

        PlayerInfo playerInfo = new PlayerInfo();
        playerInfo.setUserName(loginDTO.username());
        playerInfo.setCreatedAt(LocalDateTime.now());
        playerInfo.setEmailId(loginDTO.email());
        playerInfo.setPassword(passwordEncoder.encode(loginDTO.password()));
        playerInfo.setTokenIssuedAt(LocalDateTime.now());

        playerInfoRepository.save(playerInfo);
        log.info("Player {} registered successfully", loginDTO.username());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.username(), loginDTO.password()));

        return jwtUtils.generatedEncodedToken(authentication);
    }

    public String login(LoginDTO loginDTO) {
        // authenticationManager.authenticate() already throws
        // BadCredentialsException if the user does not exist or the password
        // is wrong — GlobalExceptionHandler maps that to HTTP 401.
        //
        // FIX: removed the dead-code block:
        //   if (playerInfoRepository.existsByUserName(...)) { return jwt; }
        //   else { throw new UserDoesNotExistException(); }
        // That branch was unreachable: if authenticate() succeeds the user
        // must exist, so existsByUserName is always true and the else
        // branch can never fire. It also added a pointless extra DB round-trip.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.username(), loginDTO.password()));

        log.info("Login successful for {}", loginDTO.username());
        return jwtUtils.generatedEncodedToken(authentication);
    }

    @Transactional
    public void deletePlayer(LoginDTO loginDTO) {
        PlayerInfo playerInfo = playerInfoRepository.findByUserName(loginDTO.username())
                .orElseThrow(UserDoesNotExistException::new);

        if (!passwordEncoder.matches(loginDTO.password(), playerInfo.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }

        playerInfoRepository.deleteByUserName(loginDTO.username());
        log.info("Player {} deleted", loginDTO.username());
    }

    @Transactional
    public void updateUserName(LoginDTO loginDTO, String newUserName) {
        PlayerInfo playerInfo = playerInfoRepository.findByUserName(loginDTO.username())
                .orElseThrow(UserDoesNotExistException::new);

        if (!passwordEncoder.matches(loginDTO.password(), playerInfo.getPassword())) {
            throw new RuntimeException("Incorrect password");
        }

        playerInfoRepository.updateUserName(newUserName, loginDTO.username());
        log.info("Username updated: {} → {}", loginDTO.username(), newUserName);
    }
}