package com.example.topup.demo.dto;

import com.example.topup.demo.entity.KickbackCampaign;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KickbackCampaignDTO {
    private Long id;
    private String campaignName;
    private String productId;
    private String productName;
    private BigDecimal productPrice;
    private BigDecimal kickbackRate;
    private BigDecimal salesTarget;
    private Integer durationDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String description;
    private List<String> retailerIds; // List of retailer IDs eligible for this campaign
    private Integer daysRemaining;
    private Integer totalParticipants;
    private BigDecimal totalSales;
    private BigDecimal totalPointsAwarded;
    private List<RetailerParticipationDTO> participants;
}
