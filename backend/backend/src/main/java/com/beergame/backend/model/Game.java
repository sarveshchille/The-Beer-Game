package com.beergame.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Entity representing a Game.
 */
@Data
@Entity
@Table(name = "Game")
public class Game {

    @Id
    @Column(length = 10)
    private String id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "currentWeek")
    private int currentWeek;

    @Column(name = "gameStatus", nullable = false)
    @Enumerated(EnumType.STRING)
    private GameStatus gameStatus;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finishedAt")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Players> players = new ArrayList<>();

    /** Whether the CURRENT week is a festive week (drives UI highlight). */
    @Column(name = "festive")
    private boolean festiveWeek;

    /**
     * Per-game festive week numbers. Generated once at creation via
     * GameConfig.generateFestiveWeeks() and never mutated afterwards.
     */
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "game_festive_weeks", schema = "beergame_schema",
    joinColumns = @JoinColumn(name = "game_id"))
@Column(name = "festive_week")
private Set<Integer> festiveWeeks = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gameRoomId")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private GameRoom gameRoom;

    public enum GameStatus {
        LOBBY, IN_PROGRESS, FINISHED
    }
}