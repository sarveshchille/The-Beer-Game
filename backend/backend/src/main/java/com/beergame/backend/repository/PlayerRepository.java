package com.beergame.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;

public interface PlayerRepository extends JpaRepository<Players,Long>{

    Optional<Players> findByGameAndPlayerInfoUserName(Game game, String userName);

}
