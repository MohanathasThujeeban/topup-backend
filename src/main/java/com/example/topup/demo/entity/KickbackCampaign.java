package com.example.topup.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kickback_campaigns")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KickbackCampaign {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String campaignName;
    
    @Column(nullable = false)
    private String productId;
    
    @Column(nullable = false)
    private String productName;
    
    @Column(nullable = false)
    private BigDecimal productPrice;
    
    @Column(nullable = false)
    private BigDecimal kickbackRate; // Percentage (e.g., 10.0 for 10%)
    
    @Column(nullable = false)
    private BigDecimal salesTarget; // Target sales amount (e.g., 1400 NOK)
    
    @Column(nullable = false)
    private Integer durationDays; // Campaign duration (e.g., 7 days)
    
    @Column(nullable = false)
    private LocalDate startDate;
    
    @Column(nullable = false)
    private LocalDate endDate;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.ACTIVE;
    
    @Column(length = 1000)
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String retailerIds; // Comma-separated list of retailer IDs eligible for this campaign
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime updatedAt;
    
    @Column
    private String createdBy; // Admin email who created the campaign
    
    public enum CampaignStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED,
        CANCELLED
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
