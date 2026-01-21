package com.example.topup.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "retailer_kickback_participation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetailerKickbackParticipation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long campaignId;
    
    @Column(nullable = false)
    private String retailerEmail;
    
    @Column(nullable = false)
    private String retailerName;
    
    @Column(nullable = false)
    private BigDecimal totalSales = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private BigDecimal totalEarnedPoints = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private BigDecimal approvedPoints = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private BigDecimal pendingPoints = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private Boolean targetReached = false;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ParticipationStatus status = ParticipationStatus.ACTIVE;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime updatedAt;
    
    public enum ParticipationStatus {
        ACTIVE,      // Retailer is actively participating
        COMPLETED,   // Target reached
        EXPIRED      // Campaign ended
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
