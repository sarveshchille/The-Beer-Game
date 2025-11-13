package com.beergame.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "gameturn")
public class GameTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weekDay")
    private int weekDay;

    @Column(name = "orderPlaced")
    private int orderPlaced;

    @Column(name = "demandRecieved")
    private int demandRecieved;

    @Column(name = "shipmentSent")
    private int shipmentSent;

    @Column(name = "shipmentRecieved")
    private int shipmentRecieved;

    @Column(name = "inventoryAtEndOfWeek")
    private int inventoryAtEndOfWeek;

    @Column(name = "backOrderAtEndOfWeek")
    private int backOrderAtEndOfWeek;

    @Column(name = "weeklyCost")
    private double weeklyCost;

    @Column(name = "totalCost")
    private double totalCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Players player;
}