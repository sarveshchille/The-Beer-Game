package com.beergame.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Changes:
 *  1. @Version for optimistic locking.
 *  2. festiveWeeks stored per-game as @ElementCollection — every game now
 *     gets its own independently-generated festive weeks. Previously all games
 *     shared the JVM-global static Set in GameConfig, making every game
 *     identically predictable.
 *
 * MIGRATION:
 *   ALTER TABLE Game ADD COLUMN version BIGINT DEFAULT 0;
 *   CREATE TABLE game_festive_weeks (
 *       game_id    VARCHAR(10) NOT NULL,
 *       festive_week INT       NOT NULL,
 *       FOREIGN KEY (game_id) REFERENCES Game(id)
 *   );
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
    private List<Players> players = new ArrayList<>();

    /** Whether the CURRENT week is a festive week (drives UI highlight). */
    @Column(name = "festive")
    private boolean festiveWeek;

    /**
     * Per-game festive week numbers. Generated once at creation via
     * GameConfig.generateFestiveWeeks() and never mutated afterwards.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "game_festive_weeks",
            joinColumns = @JoinColumn(name = "game_id")
    )
    @Column(name = "festive_week")
    private Set<Integer> festiveWeeks = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gameRoomId")
    private GameRoom gameRoom;

    public enum GameStatus {
        LOBBY, IN_PROGRESS, FINISHED
    }
}