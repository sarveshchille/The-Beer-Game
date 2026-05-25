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
         * Query by game ID string directly.
         */
        @Query("""
                        SELECT p FROM Players p
                        JOIN FETCH p.playerInfo
                        WHERE p.game.id = :gameId
                          AND p.playerInfo.userName = :username
                        """)
        Optional<Players> findByGameIdAndUsername(
                        @Param("gameId") String gameId,
                        @Param("username") String username);

        Optional<Players> findByGameAndUserName(Game game, String userName);
}