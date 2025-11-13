package com.beergame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.beergame.backend.model.PlayerInfo;

public interface PlayerInfoRepository extends JpaRepository<PlayerInfo, Long> {

    boolean existsByUserName(String userName);

    boolean existsByEmailId(String email);

    Optional<PlayerInfo> findByUserName(String userName);

    void deleteByUserName(String userName);

    @Modifying
    @Query("UPDATE PlayerInfo p SET p.userName = ?1 WHERE p.userName = ?2")
    void updateUserName(String newUserName, String oldUserName);

}
