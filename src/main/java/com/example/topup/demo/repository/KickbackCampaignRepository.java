package com.example.topup.demo.repository;

import com.example.topup.demo.entity.KickbackCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface KickbackCampaignRepository extends JpaRepository<KickbackCampaign, Long> {
    
    List<KickbackCampaign> findByStatus(KickbackCampaign.CampaignStatus status);
    
    List<KickbackCampaign> findByStatusOrderByCreatedAtDesc(KickbackCampaign.CampaignStatus status);
    
    List<KickbackCampaign> findAllByOrderByCreatedAtDesc();
    
    @Query("SELECT c FROM KickbackCampaign c WHERE c.endDate >= :currentDate AND c.status = 'ACTIVE'")
    List<KickbackCampaign> findActiveCampaigns(LocalDate currentDate);
    
    @Query("SELECT c FROM KickbackCampaign c WHERE c.productId = :productId AND c.status = 'ACTIVE'")
    List<KickbackCampaign> findActiveCampaignsByProduct(String productId);
}
