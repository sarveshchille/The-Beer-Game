package com.beergame.backend.repository;

import com.beergame.backend.model.GameRoom;
import com.beergame.backend.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByGameRoomAndTeamName(GameRoom gameRoom, String teamName);
}