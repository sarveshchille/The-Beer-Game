package com.beergame.backend.repository;

import com.beergame.backend.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, String> {

    /**
     * Finds games by status and created before a certain timestamp.
     */
    List<Game> findByGameStatusAndCreatedAtBefore(Game.GameStatus status, LocalDateTime cutoff);

    @Query("""
            SELECT DISTINCT g
            FROM Game g
            LEFT JOIN FETCH g.players p
            LEFT JOIN FETCH p.playerInfo
            WHERE g.id = :id
            """)
    Optional<Game> findByIdWithPlayers(String id);

    @Query("""
            SELECT DISTINCT g
            FROM Game g
            LEFT JOIN FETCH g.players p
            LEFT JOIN FETCH p.turnHistory th
            LEFT JOIN FETCH p.playerInfo pi
            WHERE g.id = :id
            """)
    Optional<Game> findByIdWithPlayersAndTurnHistory(String id);
    List<Game> findByGameStatus(Game.GameStatus status);

    @Query("SELECT distinct g FROM Game g LEFT JOIN FETCH g.players WHERE g.gameStatus = :status")
List<Game> findActiveGamesWithPlayers(@Param("status") Game.GameStatus status);
}