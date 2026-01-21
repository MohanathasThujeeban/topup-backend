package com.example.topup.demo.service;

import com.example.topup.demo.dto.*;
import com.example.topup.demo.entity.*;
import com.example.topup.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KickbackCampaignService {
    
    private final KickbackCampaignRepository campaignRepository;
    private final KickbackEarningRepository earningRepository;
    private final RetailerKickbackParticipationRepository participationRepository;
    
    /**
     * Create a new kickback campaign
     */
    @Transactional
    public KickbackCampaign createCampaign(CreateKickbackCampaignRequest request, String adminEmail) {
        log.info("Creating kickback campaign: {} by admin: {}", request.getCampaignName(), adminEmail);
        
        KickbackCampaign campaign = new KickbackCampaign();
        campaign.setCampaignName(request.getCampaignName());
        campaign.setProductId(request.getProductId());
        campaign.setProductName(request.getProductName());
        campaign.setProductPrice(request.getProductPrice());
        campaign.setKickbackRate(request.getKickbackRate());
        campaign.setSalesTarget(request.getSalesTarget());
        campaign.setDurationDays(request.getDurationDays());
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getStartDate().plusDays(request.getDurationDays()));
        campaign.setStatus(KickbackCampaign.CampaignStatus.ACTIVE);
        campaign.setDescription(request.getDescription());
        campaign.setCreatedBy(adminEmail);
        
        // Save selected retailer IDs as comma-separated string
        if (request.getSelectedRetailers() != null && !request.getSelectedRetailers().isEmpty()) {
            campaign.setRetailerIds(String.join(",", request.getSelectedRetailers()));
        }
        
        return campaignRepository.save(campaign);
    }
    
    /**
     * Get all campaigns with details
     */
    public List<KickbackCampaignDTO> getAllCampaigns() {
        List<KickbackCampaign> campaigns = campaignRepository.findAllByOrderByCreatedAtDesc();
        return campaigns.stream()
            .map(this::convertToCampaignDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get active campaigns
     */
    public List<KickbackCampaignDTO> getActiveCampaigns() {
        List<KickbackCampaign> campaigns = campaignRepository.findActiveCampaigns(LocalDate.now());
        return campaigns.stream()
            .map(this::convertToCampaignDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get campaign by ID with full details including participants
     */
    public KickbackCampaignDTO getCampaignById(Long campaignId) {
        KickbackCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));
        return convertToCampaignDTO(campaign);
    }
    
    /**
     * Update an existing campaign
     */
    @Transactional
    public KickbackCampaign updateCampaign(Long campaignId, CreateKickbackCampaignRequest request, String adminEmail) {
        log.info("Updating campaign {} by admin: {}", campaignId, adminEmail);
        
        KickbackCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));
        
        // Update campaign fields
        campaign.setCampaignName(request.getCampaignName());
        campaign.setProductId(request.getProductId());
        campaign.setProductName(request.getProductName());
        campaign.setProductPrice(request.getProductPrice());
        campaign.setKickbackRate(request.getKickbackRate());
        campaign.setSalesTarget(request.getSalesTarget());
        campaign.setDurationDays(request.getDurationDays());
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getStartDate().plusDays(request.getDurationDays()));
        campaign.setDescription(request.getDescription());
        
        // Update selected retailers
        if (request.getSelectedRetailers() != null && !request.getSelectedRetailers().isEmpty()) {
            campaign.setRetailerIds(String.join(",", request.getSelectedRetailers()));
        } else {
            campaign.setRetailerIds(null);
        }
        
        return campaignRepository.save(campaign);
    }
    
    /**
     * Delete a campaign
     */
    @Transactional
    public void deleteCampaign(Long campaignId) {
        log.info("Deleting campaign: {}", campaignId);
        
        KickbackCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));
        
        // Delete associated participations and earnings
        List<RetailerKickbackParticipation> participations = participationRepository.findByCampaignIdOrderByTotalSalesDesc(campaignId);
        participationRepository.deleteAll(participations);
        
        // Delete associated earnings for all statuses
        List<KickbackEarning> earnings = earningRepository.findByCampaignIdAndStatus(campaignId, KickbackEarning.EarningStatus.PENDING);
        earningRepository.deleteAll(earnings);
        earnings = earningRepository.findByCampaignIdAndStatus(campaignId, KickbackEarning.EarningStatus.APPROVED);
        earningRepository.deleteAll(earnings);
        earnings = earningRepository.findByCampaignIdAndStatus(campaignId, KickbackEarning.EarningStatus.REJECTED);
        earningRepository.deleteAll(earnings);
        
        // Delete the campaign
        campaignRepository.delete(campaign);
        
        log.info("Campaign {} deleted successfully", campaignId);
    }
    
    /**
     * Record daily sales and calculate kickback earnings
     * This should be called when a retailer makes a sale of the campaign product
     */
    @Transactional
    public void recordDailySales(Long campaignId, String retailerEmail, String retailerName, 
                                  BigDecimal salesAmount, LocalDate saleDate) {
        log.info("Recording sales for campaign: {}, retailer: {}, amount: {}", 
            campaignId, retailerEmail, salesAmount);
        
        KickbackCampaign campaign = campaignRepository.findById(campaignId)
            .orElseThrow(() -> new RuntimeException("Campaign not found"));
        
        // Check if campaign is active and within date range
        if (campaign.getStatus() != KickbackCampaign.CampaignStatus.ACTIVE) {
            log.warn("Campaign {} is not active", campaignId);
            return;
        }
        
        // Check if retailer is eligible for this campaign
        if (campaign.getRetailerIds() != null && !campaign.getRetailerIds().isEmpty()) {
            List<String> eligibleRetailers = List.of(campaign.getRetailerIds().split(","));
            if (!eligibleRetailers.contains(retailerEmail)) {
                log.warn("Retailer {} is not eligible for campaign {}", retailerEmail, campaignId);
                return;
            }
        }
        
        if (saleDate.isBefore(campaign.getStartDate()) || saleDate.isAfter(campaign.getEndDate())) {
            log.warn("Sale date {} is outside campaign period", saleDate);
            return;
        }
        
        // Get or create participation record
        RetailerKickbackParticipation participation = participationRepository
            .findByCampaignIdAndRetailerEmail(campaignId, retailerEmail)
            .orElseGet(() -> {
                RetailerKickbackParticipation newParticipation = new RetailerKickbackParticipation();
                newParticipation.setCampaignId(campaignId);
                newParticipation.setRetailerEmail(retailerEmail);
                newParticipation.setRetailerName(retailerName);
                newParticipation.setStatus(RetailerKickbackParticipation.ParticipationStatus.ACTIVE);
                return participationRepository.save(newParticipation);
            });
        
        // Calculate cumulative sales
        BigDecimal cumulativeSales = participation.getTotalSales().add(salesAmount);
        
        // Check if target is reached
        boolean targetReached = cumulativeSales.compareTo(campaign.getSalesTarget()) >= 0;
        
        // If target reached, cap the sales at target
        BigDecimal effectiveSalesForPoints = salesAmount;
        if (targetReached && !participation.getTargetReached()) {
            // This is the day target was reached
            BigDecimal excessSales = cumulativeSales.subtract(campaign.getSalesTarget());
            effectiveSalesForPoints = salesAmount.subtract(excessSales);
        } else if (participation.getTargetReached()) {
            // Target was already reached, no more points
            effectiveSalesForPoints = BigDecimal.ZERO;
        }
        
        // Calculate earned points for this day
        BigDecimal earnedPoints = effectiveSalesForPoints
            .multiply(campaign.getKickbackRate())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // Get or update daily earning record
        KickbackEarning earning = earningRepository
            .findByCampaignIdAndRetailerEmailAndEarningDate(campaignId, retailerEmail, saleDate)
            .orElseGet(() -> {
                KickbackEarning newEarning = new KickbackEarning();
                newEarning.setCampaignId(campaignId);
                newEarning.setRetailerEmail(retailerEmail);
                newEarning.setRetailerName(retailerName);
                newEarning.setEarningDate(saleDate);
                newEarning.setDailySalesAmount(BigDecimal.ZERO);
                newEarning.setEarnedPoints(BigDecimal.ZERO);
                newEarning.setStatus(KickbackEarning.EarningStatus.PENDING);
                return newEarning;
            });
        
        // Update earning record
        earning.setDailySalesAmount(earning.getDailySalesAmount().add(salesAmount));
        earning.setEarnedPoints(earning.getEarnedPoints().add(earnedPoints));
        earning.setCumulativeSales(cumulativeSales);
        earningRepository.save(earning);
        
        // Update participation record
        participation.setTotalSales(cumulativeSales);
        participation.setTotalEarnedPoints(participation.getTotalEarnedPoints().add(earnedPoints));
        participation.setPendingPoints(participation.getPendingPoints().add(earnedPoints));
        participation.setTargetReached(targetReached);
        if (targetReached) {
            participation.setStatus(RetailerKickbackParticipation.ParticipationStatus.COMPLETED);
        }
        participationRepository.save(participation);
        
        log.info("Recorded earning: {} NOK sales -> {} points earned", salesAmount, earnedPoints);
    }
    
    /**
     * Approve or reject daily earning
     */
    @Transactional
    public void approveOrRejectEarning(ApproveEarningRequest request) {
        KickbackEarning earning = earningRepository.findById(request.getEarningId())
            .orElseThrow(() -> new RuntimeException("Earning not found"));
        
        if (request.getApprove()) {
            earning.setStatus(KickbackEarning.EarningStatus.APPROVED);
            earning.setApprovedBy(request.getAdminEmail());
            earning.setApprovedAt(LocalDateTime.now());
            
            // Update participation approved points
            RetailerKickbackParticipation participation = participationRepository
                .findByCampaignIdAndRetailerEmail(earning.getCampaignId(), earning.getRetailerEmail())
                .orElseThrow(() -> new RuntimeException("Participation not found"));
            
            participation.setApprovedPoints(participation.getApprovedPoints().add(earning.getEarnedPoints()));
            participation.setPendingPoints(participation.getPendingPoints().subtract(earning.getEarnedPoints()));
            participationRepository.save(participation);
            
            log.info("Approved earning {} - {} points for retailer {}", 
                earning.getId(), earning.getEarnedPoints(), earning.getRetailerEmail());
        } else {
            earning.setStatus(KickbackEarning.EarningStatus.REJECTED);
            earning.setRejectionReason(request.getRejectionReason());
            
            // Update participation - remove from total earned and pending
            RetailerKickbackParticipation participation = participationRepository
                .findByCampaignIdAndRetailerEmail(earning.getCampaignId(), earning.getRetailerEmail())
                .orElseThrow(() -> new RuntimeException("Participation not found"));
            
            participation.setTotalEarnedPoints(participation.getTotalEarnedPoints().subtract(earning.getEarnedPoints()));
            participation.setPendingPoints(participation.getPendingPoints().subtract(earning.getEarnedPoints()));
            participationRepository.save(participation);
            
            log.info("Rejected earning {} for retailer {}: {}", 
                earning.getId(), earning.getRetailerEmail(), request.getRejectionReason());
        }
        
        earningRepository.save(earning);
    }
    
    /**
     * Get all pending earnings for approval
     */
    public List<DailyEarningDTO> getPendingEarnings() {
        List<KickbackEarning> earnings = earningRepository.findByStatus(KickbackEarning.EarningStatus.PENDING);
        return earnings.stream()
            .map(this::convertToEarningDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get pending earnings for a specific campaign
     */
    public List<DailyEarningDTO> getPendingEarningsByCampaign(Long campaignId) {
        List<KickbackEarning> earnings = earningRepository
            .findByCampaignIdAndStatus(campaignId, KickbackEarning.EarningStatus.PENDING);
        return earnings.stream()
            .map(this::convertToEarningDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Get retailer's approved kickback points balance
     */
    public BigDecimal getRetailerApprovedPoints(String retailerEmail) {
        List<RetailerKickbackParticipation> participations = 
            participationRepository.findByRetailerEmailOrderByCreatedAtDesc(retailerEmail);
        
        return participations.stream()
            .map(RetailerKickbackParticipation::getApprovedPoints)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Convert entity to DTO
     */
    private KickbackCampaignDTO convertToCampaignDTO(KickbackCampaign campaign) {
        KickbackCampaignDTO dto = new KickbackCampaignDTO();
        dto.setId(campaign.getId());
        dto.setCampaignName(campaign.getCampaignName());
        dto.setProductId(campaign.getProductId());
        dto.setProductName(campaign.getProductName());
        dto.setProductPrice(campaign.getProductPrice());
        dto.setKickbackRate(campaign.getKickbackRate());
        dto.setSalesTarget(campaign.getSalesTarget());
        dto.setDurationDays(campaign.getDurationDays());
        dto.setStartDate(campaign.getStartDate());
        dto.setEndDate(campaign.getEndDate());
        dto.setStatus(campaign.getStatus().name());
        dto.setDescription(campaign.getDescription());
        
        // Convert retailer IDs from comma-separated string to list
        if (campaign.getRetailerIds() != null && !campaign.getRetailerIds().isEmpty()) {
            dto.setRetailerIds(List.of(campaign.getRetailerIds().split(",")));
        } else {
            dto.setRetailerIds(List.of());
        }
        
        // Calculate days remaining
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), campaign.getEndDate());
        dto.setDaysRemaining(daysRemaining > 0 ? (int) daysRemaining : 0);
        
        // Get campaign statistics
        Integer participantCount = participationRepository.countParticipantsByCampaign(campaign.getId());
        dto.setTotalParticipants(participantCount != null ? participantCount : 0);
        
        Double totalSales = earningRepository.getTotalSalesByCampaign(campaign.getId());
        dto.setTotalSales(totalSales != null ? BigDecimal.valueOf(totalSales) : BigDecimal.ZERO);
        
        Double totalPoints = earningRepository.getTotalApprovedPointsByCampaign(campaign.getId());
        dto.setTotalPointsAwarded(totalPoints != null ? BigDecimal.valueOf(totalPoints) : BigDecimal.ZERO);
        
        // Get participants with their earnings
        List<RetailerKickbackParticipation> participants = 
            participationRepository.findByCampaignIdOrderByTotalSalesDesc(campaign.getId());
        
        dto.setParticipants(participants.stream()
            .map(p -> convertToParticipationDTO(p, campaign.getId()))
            .collect(Collectors.toList()));
        
        return dto;
    }
    
    private RetailerParticipationDTO convertToParticipationDTO(RetailerKickbackParticipation participation, Long campaignId) {
        RetailerParticipationDTO dto = new RetailerParticipationDTO();
        dto.setParticipationId(participation.getId());
        dto.setRetailerEmail(participation.getRetailerEmail());
        dto.setRetailerName(participation.getRetailerName());
        dto.setTotalSales(participation.getTotalSales());
        dto.setTotalEarnedPoints(participation.getTotalEarnedPoints());
        dto.setApprovedPoints(participation.getApprovedPoints());
        dto.setPendingPoints(participation.getPendingPoints());
        dto.setTargetReached(participation.getTargetReached());
        dto.setStatus(participation.getStatus().name());
        
        // Get daily earnings
        List<KickbackEarning> earnings = earningRepository
            .findByCampaignIdAndRetailerEmailOrderByEarningDateDesc(campaignId, participation.getRetailerEmail());
        
        dto.setDailyEarnings(earnings.stream()
            .map(this::convertToEarningDTO)
            .collect(Collectors.toList()));
        
        return dto;
    }
    
    private DailyEarningDTO convertToEarningDTO(KickbackEarning earning) {
        DailyEarningDTO dto = new DailyEarningDTO();
        dto.setId(earning.getId());
        dto.setEarningDate(earning.getEarningDate());
        dto.setDailySalesAmount(earning.getDailySalesAmount());
        dto.setEarnedPoints(earning.getEarnedPoints());
        dto.setCumulativeSales(earning.getCumulativeSales());
        dto.setStatus(earning.getStatus().name());
        dto.setApprovedBy(earning.getApprovedBy());
        dto.setRejectionReason(earning.getRejectionReason());
        return dto;
    }
    
    /**
     * Get retailer's active campaigns with their progress
     */
    public List<Map<String, Object>> getRetailerCampaigns(String retailerEmail) {
        // Get all active campaigns where this retailer is eligible
        List<KickbackCampaign> allCampaigns = campaignRepository.findByStatus(KickbackCampaign.CampaignStatus.ACTIVE);
        
        return allCampaigns.stream()
            .filter(campaign -> {
                String retailerIds = campaign.getRetailerIds();
                // If no specific retailers, all are eligible
                if (retailerIds == null || retailerIds.isEmpty()) {
                    return true;
                }
                // Check if retailer is in the eligible list
                return retailerIds.contains(retailerEmail);
            })
            .map(campaign -> {
                Map<String, Object> campaignData = new HashMap<>();
                campaignData.put("id", campaign.getId());
                campaignData.put("campaignName", campaign.getCampaignName());
                campaignData.put("productId", campaign.getProductId());
                campaignData.put("productName", campaign.getProductName());
                campaignData.put("kickbackRate", campaign.getKickbackRate());
                campaignData.put("targetAmount", campaign.getSalesTarget());
                campaignData.put("durationDays", campaign.getDurationDays());
                campaignData.put("startDate", campaign.getStartDate());
                campaignData.put("endDate", campaign.getEndDate());
                campaignData.put("status", campaign.getStatus().name());
                
                // Get retailer's participation data
                RetailerKickbackParticipation participation = participationRepository
                    .findByCampaignIdAndRetailerEmail(campaign.getId(), retailerEmail)
                    .orElse(null);
                
                if (participation != null) {
                    campaignData.put("earnedAmount", participation.getTotalSales());
                    campaignData.put("earnedPoints", participation.getTotalEarnedPoints());
                    campaignData.put("approvedPoints", participation.getApprovedPoints());
                    campaignData.put("targetReached", participation.getTargetReached());
                } else {
                    campaignData.put("earnedAmount", BigDecimal.ZERO);
                    campaignData.put("earnedPoints", BigDecimal.ZERO);
                    campaignData.put("approvedPoints", BigDecimal.ZERO);
                    campaignData.put("targetReached", false);
                }
                
                return campaignData;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Process a retailer sale and check if it qualifies for any campaigns
     */
    @Transactional
    public void processRetailerSale(String retailerEmail, String retailerName, String productId, BigDecimal saleAmount) {
        LocalDate today = LocalDate.now();
        
        log.info("🔍 Processing sale - Retailer: {}, ProductId: {}, Amount: {}", 
            retailerEmail, productId, saleAmount);
        
        // Find all active campaigns for this product
        List<KickbackCampaign> activeCampaigns = campaignRepository.findByStatus(KickbackCampaign.CampaignStatus.ACTIVE)
            .stream()
            .filter(campaign -> {
                // Check if product matches (flexible matching)
                String campaignProductId = campaign.getProductId() != null
                    ? campaign.getProductId().toLowerCase()
                    : "";
                String saleProductId = productId.toLowerCase();
                
                // Special case: campaign applies to all products
                boolean isAllProducts = "all_products".equals(campaignProductId);
                
                boolean productMatches = isAllProducts || 
                                       campaignProductId.equals(saleProductId) || 
                                       saleProductId.contains(campaignProductId) ||
                                       campaignProductId.contains(saleProductId);
                
                if (!productMatches) {
                    log.debug("Product mismatch - Campaign: {}, Sale: {}", campaign.getProductId(), productId);
                    return false;
                }
                
                // Check if date is within campaign period
                if (today.isBefore(campaign.getStartDate()) || today.isAfter(campaign.getEndDate())) {
                    log.debug("Date outside campaign period - Campaign: {}, Today: {}, Start: {}, End: {}", 
                        campaign.getCampaignName(), today, campaign.getStartDate(), campaign.getEndDate());
                    return false;
                }
                
                // Check if retailer is eligible
                String retailerIds = campaign.getRetailerIds();
                if (retailerIds != null && !retailerIds.isEmpty()) {
                    boolean eligible = retailerIds.contains(retailerEmail);
                    if (!eligible) {
                        log.debug("Retailer not eligible - Campaign: {}, Retailer: {}", 
                            campaign.getCampaignName(), retailerEmail);
                    }
                    return eligible;
                }
                
                return true;
            })
            .collect(Collectors.toList());
        
        // Record sales for each qualifying campaign
        for (KickbackCampaign campaign : activeCampaigns) {
            log.info("✅ Recording sale for campaign: {} - Product: {}, Amount: {}", 
                campaign.getCampaignName(), productId, saleAmount);
            recordDailySales(campaign.getId(), retailerEmail, retailerName, saleAmount, today);
        }
        
        if (activeCampaigns.isEmpty()) {
            log.warn("⚠️ No matching campaigns found for - Retailer: {}, Product: {}, Amount: {}", 
                retailerEmail, productId, saleAmount);
        } else {
            log.info("✅ Processed sale for retailer {} - Product: {}, Amount: {}, Campaigns affected: {}", 
                retailerEmail, productId, saleAmount, activeCampaigns.size());
        }
    }
}
