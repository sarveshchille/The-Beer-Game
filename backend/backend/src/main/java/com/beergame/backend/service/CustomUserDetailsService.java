package com.beergame.backend.service;

import com.beergame.backend.model.PlayerInfo;
import com.beergame.backend.repository.PlayerInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User; // Import this
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList; // Import this

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final PlayerInfoRepository playerInfoRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        PlayerInfo playerInfo = playerInfoRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return new User(
                playerInfo.getUserName(),
                playerInfo.getPassword(),
                new ArrayList<>() 
        );
    }
}
