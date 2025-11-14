package com.beergame.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.beergame.backend.model.Game;

public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByGameStatusAndCreatedAt(Game.GameStatus status, LocalDateTime localDateTime);

    @Query("""
            SELECT DISTINCT g
            FROM Game g
            LEFT JOIN FETCH g.players p
            LEFT JOIN FETCH p.playerInfo
            WHERE g.id = :id
            """)
    Optional<Game> findByIdWithPlayers(String id);
}
