package com.beergame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beergame.backend.model.PlayerInfo;

public interface PlayerInfoRepository extends JpaRepository<PlayerInfo,Long> {

    boolean existsByUserName(String userName);
    boolean existsByEmail(String email);
    Optional<PlayerInfo> findByUserName(String userName);

}
