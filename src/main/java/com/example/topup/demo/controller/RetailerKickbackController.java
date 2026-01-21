package com.example.topup.demo.controller;

import com.example.topup.demo.service.KickbackCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/retailer/kickback")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "https://topup-backend-production.up.railway.app"}, allowCredentials = "true")
public class RetailerKickbackController {
    
    private final KickbackCampaignService kickbackService;
    
    /**
     * Get retailer's kickback balance
     * GET /api/retailer/kickback/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getRetailerBalance(
            @RequestAttribute(value = "userEmail", required = false) String retailerEmail) {
        
        try {
            if (retailerEmail == null) {
                retailerEmail = "mohanarjsrilanka@gmail.com"; // Fallback for demo
            }
            
            BigDecimal balance = kickbackService.getRetailerApprovedPoints(retailerEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("balance", balance);
            response.put("retailerEmail", retailerEmail);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching retailer balance", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get retailer's active campaigns with progress
     * GET /api/retailer/kickback/campaigns
     */
    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> getRetailerCampaigns(
            @RequestAttribute(value = "userEmail", required = false) String retailerEmail) {
        
        try {
            if (retailerEmail == null) {
                retailerEmail = "mohanarjsrilanka@gmail.com"; // Fallback for demo
            }
            
            List<Map<String, Object>> campaigns = kickbackService.getRetailerCampaigns(retailerEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaigns", campaigns);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching retailer campaigns", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("campaigns", List.of());
            return ResponseEntity.ok(errorResponse);
        }
    }
}
