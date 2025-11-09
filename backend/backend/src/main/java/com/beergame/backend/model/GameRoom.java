package com.beergame.backend.model;


import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name="gameRoom")
public class GameRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private RoomStatus status;

    @OneToMany(mappedBy = "gameRoom",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private List<Team> teams;
    
    @OneToMany(mappedBy = "gameRoom",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private List<Game> games;

    @Column(name="finishedAt")
    private LocalDateTime finishedAt;

    public enum RoomStatus {
        WAITING,
        RUNNING, 
        FINISHED
    }

}
