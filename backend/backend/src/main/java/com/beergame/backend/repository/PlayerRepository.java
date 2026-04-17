package com.beergame.backend.repository;

import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Players, Long> {

    /**
     * Original method — kept for any callers that already have a Game object.
     * Internally hits the DB to match by game's PK.
     */
    Optional<Players> findByGameAndPlayerInfoUserName(Game game, String userName);

    /**
     * FIX: Query by game ID string directly.
     *
     * The old pattern required a full Game entity to be passed in just to
     * filter by its ID. This adds zero safety (Spring still generates the
     * same WHERE game_id = ? clause) but forces callers to load or hold a
     * Game object unnecessarily, creating an N+1 opportunity.
     *
     * Use this version when you only have a gameId string available.
     */
    @Query("""
            SELECT p FROM Players p
            JOIN FETCH p.playerInfo
            WHERE p.game.id = :gameId
              AND p.playerInfo.userName = :username
            """)
    Optional<Players> findByGameIdAndUsername(
            @Param("gameId")   String gameId,
            @Param("username") String username);
}