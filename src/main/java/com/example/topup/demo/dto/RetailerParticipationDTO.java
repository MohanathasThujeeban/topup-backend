package com.example.topup.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetailerParticipationDTO {
    private Long participationId;
    private String retailerEmail;
    private String retailerName;
    private BigDecimal totalSales;
    private BigDecimal totalEarnedPoints;
    private BigDecimal approvedPoints;
    private BigDecimal pendingPoints;
    private Boolean targetReached;
    private String status;
    private List<DailyEarningDTO> dailyEarnings;
}
