package com.beergame.backend.repository;

import com.beergame.backend.model.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, String> {

    @Query("SELECT DISTINCT r FROM GameRoom r " +
           "LEFT JOIN FETCH r.teams t " +
           "LEFT JOIN FETCH t.players p " +
           "LEFT JOIN FETCH r.games g " +
           "WHERE r.id = :roomId")
    Optional<GameRoom> findByIdWithAllData(String roomId);
    List<GameRoom> findByStatusAndFinishedAtBefore(GameRoom.RoomStatus status, LocalDateTime expiryThreshold);
}