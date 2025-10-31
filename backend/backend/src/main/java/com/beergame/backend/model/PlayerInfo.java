package com.beergame.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name="PlayerInfo")
public class PlayerInfo {
     

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="userName",nullable = false,unique = true)
    private String userName;
    
    @Column(name="emailId",nullable = false,unique = true)
    private String emailId;

    @Column(name="password",nullable = false)
    private String password;

    @Column(name="createdAt",nullable = false)
    private LocalDateTime createdAt;

    @Column(name="tokenIssuedAt",nullable = false)
    private LocalDateTime tokenIssuedAt;


}
