package com.example.topup.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveEarningRequest {
    private Long earningId;
    private Boolean approve; // true = approve, false = reject
    private String rejectionReason; // Required if approve = false
    private String adminEmail;
}
