package com.beergame.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "Game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "cuurentWeek")
    private int currentWeek;

    @Column(name = "gameStatus", nullable = false)
    private GameStatus gameStatus;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finishedAt")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Players> players = new ArrayList<>();

    @Column(name = "festive")
    private boolean festiveWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gameRoomId")
    private GameRoom gameRoom;

    public enum GameStatus {

        LOBBY, IN_PROGRESS, FINISHED
    }
}
