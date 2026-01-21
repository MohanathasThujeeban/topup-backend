package com.example.topup.demo.repository;

import com.example.topup.demo.entity.KickbackEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface KickbackEarningRepository extends JpaRepository<KickbackEarning, Long> {
    
    List<KickbackEarning> findByCampaignIdOrderByEarningDateDesc(Long campaignId);
    
    List<KickbackEarning> findByRetailerEmailOrderByEarningDateDesc(String retailerEmail);
    
    List<KickbackEarning> findByCampaignIdAndRetailerEmailOrderByEarningDateDesc(Long campaignId, String retailerEmail);
    
    List<KickbackEarning> findByStatus(KickbackEarning.EarningStatus status);
    
    List<KickbackEarning> findByCampaignIdAndStatus(Long campaignId, KickbackEarning.EarningStatus status);
    
    Optional<KickbackEarning> findByCampaignIdAndRetailerEmailAndEarningDate(
        Long campaignId, String retailerEmail, LocalDate earningDate);
    
    @Query("SELECT SUM(e.earnedPoints) FROM KickbackEarning e WHERE e.campaignId = :campaignId AND e.status = 'APPROVED'")
    Double getTotalApprovedPointsByCampaign(Long campaignId);
    
    @Query("SELECT SUM(e.dailySalesAmount) FROM KickbackEarning e WHERE e.campaignId = :campaignId")
    Double getTotalSalesByCampaign(Long campaignId);
}
