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
public class CreateKickbackCampaignRequest {
    private String campaignName;
    private String productId;
    private String productName;
    private BigDecimal productPrice;
    private BigDecimal kickbackRate; // Percentage
    private BigDecimal salesTarget; // Target sales amount
    private Integer durationDays;
    private LocalDate startDate;
    private String description;
    private List<String> selectedRetailers; // List of retailer IDs eligible for this campaign
}
