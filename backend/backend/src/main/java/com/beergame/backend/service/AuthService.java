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

    public RegisterResponseDTO  registerBeforeEmail(LoginDTO loginDTO){

        if(playerInfoRepository.existsByUserName(loginDTO.userName())){

            throw new UserNameAlreadyExistsException("The user name alreday exists!!");
        }

        if(playerInfoRepository.existsByEmail(loginDTO.email())){

            throw new EmailIdAlreadyExistsException("Email Id already exists!!");
        }

        int token = (int) (Math.random() * 900000) + 100000;

        log.info("Token created :"+token);

        emailService.sendVerificationEmail(loginDTO.email(),token);
        
        return new RegisterResponseDTO(token,LocalDateTime.now());
    }

    public void registerAfterEmail(LoginDTO loginDTO){

        PlayerInfo playerInfo = new PlayerInfo();

        playerInfo.setUserName(loginDTO.userName());
        playerInfo.setCreatedAt(LocalDateTime.now());
        playerInfo.setEmailId(loginDTO.email());
        playerInfo.setPassword(passwordEncoder.encode(loginDTO.password()));

        playerInfoRepository.save(playerInfo);

        log.info("Player registered successfully!!");
    }

    public String login(LoginDTO loginDTO){

        Authentication authentication = authenticationManager.authenticate(new 
                                       UsernamePasswordAuthenticationToken(loginDTO.userName(), loginDTO.password()));
        
        String jwt = jwtUtils.generatedEncodedToken(authentication);
        
        if(playerInfoRepository.existsByUserName(loginDTO.userName())){

            log.info("Playuer login successful");

            return jwt;
        }
        else{

            throw new UserDoesNotExistException();
        }
    }
}
