package com.beergame.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beergame.backend.model.Game;

public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByGameStatusAndCreatedAt(Game.GameStatus status, LocalDateTime localDateTime);

}
