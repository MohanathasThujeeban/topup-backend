package com.example.topup.demo.repository;

import com.example.topup.demo.entity.RetailerKickbackParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetailerKickbackParticipationRepository extends JpaRepository<RetailerKickbackParticipation, Long> {
    
    List<RetailerKickbackParticipation> findByCampaignIdOrderByTotalSalesDesc(Long campaignId);
    
    List<RetailerKickbackParticipation> findByRetailerEmailOrderByCreatedAtDesc(String retailerEmail);
    
    Optional<RetailerKickbackParticipation> findByCampaignIdAndRetailerEmail(Long campaignId, String retailerEmail);
    
    @Query("SELECT COUNT(p) FROM RetailerKickbackParticipation p WHERE p.campaignId = :campaignId")
    Integer countParticipantsByCampaign(Long campaignId);
}
