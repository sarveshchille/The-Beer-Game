package com.beergame.backend.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

// customer order             orderplacing
// bavkorder                  
// inventory
// incoming shipment
// outgoing delivery
// weeklycost
@Data
@Entity
@Table(name = "players")
public class Players {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userName", nullable = false)
    private String userName;

    @ManyToOne
    @JoinColumn(name = "playerInfoId", referencedColumnName = "id")
    private PlayerInfo playerInfo;

    @Column(name = "inventory")
    private int inventory;

    @Column(name = "backOrder")
    private int backOrder;

    @Column(name = "currentOrder")
    private int currentOrder;

    @Column(name = "weeklyCost")
    private double weeklyCost;

    @Column(name = "totalCost")
    private double totalCost;

    @Column(name = "ready")
    private boolean isReadyForOrder;

    @Column(name = "outgoingDelivery")
    private int outgoingDelivery;

    @Column(name = "lastOrderReceived")
    private int lastOrderReceived;

    @Column(name = "lastShipmentReceived")
    private int lastShipmentReceived;

    @Column(name = "orderArrivingNextWeek")
    private int orderArrivingNextWeek;

    @Column(name = "incomingShipment")
    private int incomingShipment;

    @Column(name = "shipmentArrivingWeekAfterNext")
    private int shipmentArrivingWeekAfterNext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GameTurn> turnHistory = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private RoleType role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initialTeamId")
    private Team initialTeam;

    public enum RoleType {
        RETAILER,
        WHOLESALER,
        DISTRIBUTOR,
        MANUFACTURER
    }
}