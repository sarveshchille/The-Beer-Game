package com.beergame.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beergame.backend.model.GameTurn;

public interface GameTurnRepository extends JpaRepository<GameTurn, Long> {

}
