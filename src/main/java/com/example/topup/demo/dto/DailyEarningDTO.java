package com.example.topup.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyEarningDTO {
    private Long id;
    private LocalDate earningDate;
    private BigDecimal dailySalesAmount;
    private BigDecimal earnedPoints;
    private BigDecimal cumulativeSales;
    private String status; // PENDING, APPROVED, REJECTED
    private String approvedBy;
    private String rejectionReason;
}
