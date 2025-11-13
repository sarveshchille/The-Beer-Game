package com.beergame.backend.service;

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
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public RegisterResponseDTO registerBeforeEmail(LoginDTO loginDTO) {

        if (playerInfoRepository.existsByUserName(loginDTO.username())) {

            throw new UserNameAlreadyExistsException("The user name alreday exists!!");
        }

        if (playerInfoRepository.existsByEmailId(loginDTO.email())) {

            throw new EmailIdAlreadyExistsException("Email Id already exists!!");
        }

        int token = (int) (Math.random() * 900000) + 100000;

        log.info("Token created :" + token);

        emailService.sendVerificationEmail(loginDTO.email(), token);

        return new RegisterResponseDTO(token, LocalDateTime.now());
    }

    @Transactional
    public String registerAfterEmail(LoginDTO loginDTO) {

        log.info("Recieved username {}", loginDTO.username());
        log.info("Recieved email {}", loginDTO.email());
        log.info("Recieved password {}", loginDTO.password());

        PlayerInfo playerInfo = new PlayerInfo();

        playerInfo.setUserName(loginDTO.username());
        playerInfo.setCreatedAt(LocalDateTime.now());
        playerInfo.setEmailId(loginDTO.email());
        playerInfo.setPassword(passwordEncoder.encode(loginDTO.password()));
        playerInfo.setTokenIssuedAt(LocalDateTime.now());

        playerInfoRepository.save(playerInfo);

        log.info("Player registered successfully!!");

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginDTO.username(), loginDTO.password()));

        String jwt = jwtUtils.generatedEncodedToken(authentication);

        return jwt;
    }

    public String login(LoginDTO loginDTO) {

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginDTO.username(), loginDTO.password()));

        String jwt = jwtUtils.generatedEncodedToken(authentication);

        if (playerInfoRepository.existsByUserName(loginDTO.username())) {

            log.info("Player login successful");

            return jwt;
        } else {

            throw new UserDoesNotExistException();
        }
    }

    @Transactional
    public void deletePlayer(LoginDTO loginDTO) {

        PlayerInfo playerInfo = playerInfoRepository.findByUserName(loginDTO.username())
                .orElseThrow(() -> new UserDoesNotExistException());

        if (passwordEncoder.matches(loginDTO.password(), playerInfo.getPassword())) {

            playerInfoRepository.deleteByUserName(loginDTO.username());
            log.info("Player deleted successfully!!");

        } else {
            throw new RuntimeException("Passwords don't match");
        }
    }

    @Transactional
    public void updateUserName(LoginDTO loginDTO, String newUserName) {

        PlayerInfo playerInfo = playerInfoRepository.findByUserName(loginDTO.username())
                .orElseThrow(() -> new UserDoesNotExistException());

        if (passwordEncoder.matches(loginDTO.password(), playerInfo.getPassword())) {

            playerInfoRepository.updateUserName(newUserName, loginDTO.username());
            log.info("UserName updated successfully!!!");

        } else {
            throw new RuntimeException("Passwords don't match");
        }
    }

}
