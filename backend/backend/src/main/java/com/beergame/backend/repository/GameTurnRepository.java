package com.beergame.backend.repository;

import com.beergame.backend.model.GameTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GameTurnRepository extends JpaRepository<GameTurn, Long> {

    // ✅ ADD THIS METHOD
    // This finds all turns where Turn -> Player -> Game -> ID matches
    List<GameTurn> findByPlayer_Game_Id(String gameId);
}