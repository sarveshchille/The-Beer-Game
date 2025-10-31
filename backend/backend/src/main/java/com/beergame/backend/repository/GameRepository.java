package com.beergame.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.beergame.backend.model.Game;

public interface GameRepository extends JpaRepository<Game,String> {

}
