package com.example.topup.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kickback_earnings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KickbackEarning {
    
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
    private LocalDate earningDate; // Date when sales occurred
    
    @Column(nullable = false)
    private BigDecimal dailySalesAmount; // Total sales for that day
    
    @Column(nullable = false)
    private BigDecimal earnedPoints; // Calculated kickback points (salesAmount * kickbackRate / 100)
    
    @Column(nullable = false)
    private BigDecimal cumulativeSales; // Total sales up to this day in the campaign
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EarningStatus status = EarningStatus.PENDING;
    
    @Column
    private String approvedBy; // Admin email who approved
    
    @Column
    private LocalDateTime approvedAt;
    
    @Column
    private String rejectionReason;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime updatedAt;
    
    public enum EarningStatus {
        PENDING,     // Waiting for admin approval
        APPROVED,    // Approved by admin - retailer can use these points
        REJECTED,    // Rejected by admin
        EXPIRED      // Campaign expired before approval
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
