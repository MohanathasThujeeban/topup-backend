package com.example.topup.demo.controller;

import com.example.topup.demo.dto.*;
import com.example.topup.demo.entity.KickbackCampaign;
import com.example.topup.demo.service.KickbackCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/kickback")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "https://topup-backend-production.up.railway.app"}, allowCredentials = "true")
public class KickbackCampaignController {
    
    private final KickbackCampaignService kickbackService;
    
    /**
     * Create a new kickback campaign
     * POST /api/admin/kickback/campaigns
     */
    @PostMapping("/campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @RequestBody CreateKickbackCampaignRequest request,
            @RequestAttribute(value = "userEmail", required = false) String adminEmail) {
        
        log.info("Creating kickback campaign: {}", request.getCampaignName());
        
        try {
            if (adminEmail == null) {
                adminEmail = "admin@easytopup.no"; // Fallback for demo
            }
            
            KickbackCampaign campaign = kickbackService.createCampaign(request, adminEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Kickback campaign created successfully");
            response.put("campaignId", campaign.getId());
            response.put("campaign", campaign);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating kickback campaign", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get all kickback campaigns
     * GET /api/admin/kickback/campaigns
     */
    @GetMapping("/campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllCampaigns() {
        try {
            List<KickbackCampaignDTO> campaigns = kickbackService.getAllCampaigns();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaigns", campaigns);
            response.put("total", campaigns.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching campaigns", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get active kickback campaigns
     * GET /api/admin/kickback/campaigns/active
     */
    @GetMapping("/campaigns/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getActiveCampaigns() {
        try {
            List<KickbackCampaignDTO> campaigns = kickbackService.getActiveCampaigns();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaigns", campaigns);
            response.put("total", campaigns.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching active campaigns", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get campaign by ID with full details
     * GET /api/admin/kickback/campaigns/{id}
     */
    @GetMapping("/campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCampaignById(@PathVariable Long id) {
        try {
            KickbackCampaignDTO campaign = kickbackService.getCampaignById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("campaign", campaign);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching campaign", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Update an existing kickback campaign
     * PUT /api/admin/kickback/campaigns/{id}
     */
    @PutMapping("/campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateCampaign(
            @PathVariable Long id,
            @RequestBody CreateKickbackCampaignRequest request,
            @RequestAttribute(value = "userEmail", required = false) String adminEmail) {
        
        log.info("Updating kickback campaign {}: {}", id, request.getCampaignName());
        
        try {
            if (adminEmail == null) {
                adminEmail = "admin@easytopup.no";
            }
            
            KickbackCampaign campaign = kickbackService.updateCampaign(id, request, adminEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Campaign updated successfully");
            response.put("campaign", campaign);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating kickback campaign", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Delete a kickback campaign
     * DELETE /api/admin/kickback/campaigns/{id}
     */
    @DeleteMapping("/campaigns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteCampaign(@PathVariable Long id) {
        log.info("Deleting kickback campaign: {}", id);
        
        try {
            kickbackService.deleteCampaign(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Campaign deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting kickback campaign", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get all pending earnings for approval
     * GET /api/admin/kickback/earnings/pending
     */
    @GetMapping("/earnings/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPendingEarnings() {
        try {
            List<DailyEarningDTO> earnings = kickbackService.getPendingEarnings();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("earnings", earnings);
            response.put("total", earnings.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pending earnings", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get pending earnings for a specific campaign
     * GET /api/admin/kickback/campaigns/{campaignId}/earnings/pending
     */
    @GetMapping("/campaigns/{campaignId}/earnings/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPendingEarningsByCampaign(@PathVariable Long campaignId) {
        try {
            List<DailyEarningDTO> earnings = kickbackService.getPendingEarningsByCampaign(campaignId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("earnings", earnings);
            response.put("total", earnings.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pending earnings for campaign", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Approve or reject a daily earning
     * POST /api/admin/kickback/earnings/approve
     */
    @PostMapping("/earnings/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> approveEarning(
            @RequestBody ApproveEarningRequest request,
            @RequestAttribute(value = "userEmail", required = false) String adminEmail) {
        
        try {
            if (adminEmail == null) {
                adminEmail = "admin@easytopup.no"; // Fallback for demo
            }
            request.setAdminEmail(adminEmail);
            
            kickbackService.approveOrRejectEarning(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", request.getApprove() ? "Earning approved successfully" : "Earning rejected");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving/rejecting earning", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Bulk approve multiple earnings
     * POST /api/admin/kickback/earnings/bulk-approve
     */
    @PostMapping("/earnings/bulk-approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkApproveEarnings(
            @RequestBody Map<String, Object> requestBody,
            @RequestAttribute(value = "userEmail", required = false) String adminEmail) {
        
        try {
            if (adminEmail == null) {
                adminEmail = "admin@easytopup.no";
            }
            
            @SuppressWarnings("unchecked")
            List<Long> earningIds = (List<Long>) requestBody.get("earningIds");
            
            int successCount = 0;
            int failCount = 0;
            
            for (Long earningId : earningIds) {
                try {
                    ApproveEarningRequest request = new ApproveEarningRequest();
                    request.setEarningId(earningId);
                    request.setApprove(true);
                    request.setAdminEmail(adminEmail);
                    
                    kickbackService.approveOrRejectEarning(request);
                    successCount++;
                } catch (Exception e) {
                    log.error("Error approving earning {}", earningId, e);
                    failCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", String.format("Approved %d earnings, %d failed", successCount, failCount));
            response.put("successCount", successCount);
            response.put("failCount", failCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error bulk approving earnings", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * Get retailer's kickback balance (for retailer dashboard)
     * GET /api/retailer/kickback/balance
     */
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getRetailerBalance(
            @RequestAttribute(value = "userEmail", required = false) String retailerEmailAttr,
            @RequestParam(value = "retailerEmail", required = false) String retailerEmailParam) {
        
        try {
            String retailerEmail = retailerEmailParam;
            if (retailerEmail == null || retailerEmail.isBlank()) {
                retailerEmail = retailerEmailAttr;
            }
            if (retailerEmail == null || retailerEmail.isBlank()) {
                retailerEmail = "mohanrajsrilanka@gmail.com"; // Fallback for demo (matches seeded retailer)
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
     * Get retailer's active campaigns
     * GET /api/retailer/kickback/campaigns
     */
    @GetMapping("/retailer/campaigns")
    public ResponseEntity<Map<String, Object>> getRetailerCampaigns(
            @RequestAttribute(value = "userEmail", required = false) String retailerEmailAttr,
            @RequestParam(value = "retailerEmail", required = false) String retailerEmailParam) {
        
        try {
            String retailerEmail = retailerEmailParam;
            if (retailerEmail == null || retailerEmail.isBlank()) {
                retailerEmail = retailerEmailAttr;
            }
            if (retailerEmail == null || retailerEmail.isBlank()) {
                retailerEmail = "mohanrajsrilanka@gmail.com"; // Fallback for demo
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
    
    /**
     * Record a sale for kickback calculation
     * POST /api/admin/kickback/record-sale
     */
    @PostMapping("/record-sale")
    public ResponseEntity<Map<String, Object>> recordSale(
            @RequestBody Map<String, Object> saleData,
            @RequestAttribute(value = "userEmail", required = false) String retailerEmail) {
        
        try {
            log.info("📥 Received sale recording request - Retailer attr: {}, Data: {}", retailerEmail, saleData);

            // Prefer retailerEmail from payload if provided
            String payloadRetailerEmail = (String) saleData.get("retailerEmail");
            if (payloadRetailerEmail != null && !payloadRetailerEmail.isBlank()) {
                retailerEmail = payloadRetailerEmail;
            }
            
            if (retailerEmail == null || retailerEmail.isBlank()) {
                retailerEmail = "mohanarjsrilanka@gmail.com"; // Fallback for demo
                log.warn("⚠️ No retailer email from auth or payload, using fallback: {}", retailerEmail);
            }
            
            String productId = (String) saleData.getOrDefault("productId", "");
            String productName = (String) saleData.getOrDefault("productName", "");
            Double saleAmount = ((Number) saleData.get("saleAmount")).doubleValue();
            String retailerName = (String) saleData.getOrDefault("retailerName", "Retailer");

            // Use productId primarily; if missing, fall back to productName for matching
            String saleProductKey = !productId.isBlank() ? productId : productName;
            
            log.info("🎯 Recording sale - ProductKey: {}, Amount: {}, Retailer: {} ({})", 
                saleProductKey, saleAmount, retailerName, retailerEmail);
            
            // Check if product is part of any active campaign
            kickbackService.processRetailerSale(retailerEmail, retailerName, saleProductKey, BigDecimal.valueOf(saleAmount));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Sale recorded successfully");
            
            log.info("✅ Sale recording completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error recording sale", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
}
