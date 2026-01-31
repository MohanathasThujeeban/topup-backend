package com.example.topup.demo.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.topup.demo.entity.CustomerEnquiry;
import com.example.topup.demo.repository.CustomerEnquiryRepository;
import com.example.topup.demo.service.EmailService;

@RestController
@RequestMapping("/api/enquiries")
@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:5173", "https://topup-website-gmoj.vercel.app"},
    allowCredentials = "true"
)
public class CustomerEnquiryController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerEnquiryController.class);

    @Autowired
    private CustomerEnquiryRepository enquiryRepository;

    @Autowired
    private EmailService emailService;

    /**
     * Submit a new customer enquiry (public endpoint from support form)
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitEnquiry(@RequestBody Map<String, String> formData) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String name = formData.get("name");
            String email = formData.get("email");
            String phone = formData.get("phone");
            String category = formData.get("category");
            String message = formData.get("message");
            String supportType = formData.get("supportType");
            
            logger.info("Received enquiry from: {} - Category: {}", email, category);
            
            // Create enquiry entity
            CustomerEnquiry enquiry = new CustomerEnquiry();
            enquiry.setCustomerName(name);
            enquiry.setCustomerEmail(email);
            enquiry.setCustomerPhone(phone);
            enquiry.setCategory(category);
            enquiry.setMessage(message);
            enquiry.setSubject(category); // Use category as subject
            enquiry.setStatus(CustomerEnquiry.Status.OPEN);
            enquiry.setPriority(CustomerEnquiry.Priority.MEDIUM);
            enquiry.setChannel(CustomerEnquiry.Channel.CONTACT_FORM);
            
            // Save to database
            CustomerEnquiry savedEnquiry = enquiryRepository.save(enquiry);
            
            // Send confirmation email to customer
            try {
                emailService.sendEnquiryConfirmationEmail(
                    email,
                    name,
                    category
                );
                logger.info("Confirmation email sent to: {}", email);
            } catch (Exception emailError) {
                logger.error("Failed to send confirmation email", emailError);
                // Don't fail the request if email fails
            }
            
            response.put("success", true);
            response.put("message", "Your enquiry has been submitted successfully. We'll get back to you shortly!");
            response.put("enquiryId", savedEnquiry.getId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error submitting enquiry", e);
            response.put("success", false);
            response.put("message", "Failed to submit enquiry. Please try again later.");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get all enquiries (admin only)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllEnquiries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<CustomerEnquiry> enquiries;
            
            if (status != null && !status.isEmpty()) {
                CustomerEnquiry.Status enquiryStatus = CustomerEnquiry.Status.valueOf(status.toUpperCase());
                enquiries = enquiryRepository.findByStatus(enquiryStatus);
            } else if (category != null && !category.isEmpty()) {
                // For category filtering, we need to get all and filter manually
                enquiries = enquiryRepository.findAll();
                enquiries = enquiries.stream()
                    .filter(e -> category.equals(e.getCategory()))
                    .sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()))
                    .toList();
            } else {
                enquiries = enquiryRepository.findAll();
                enquiries = enquiries.stream()
                    .sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate()))
                    .toList();
            }
            
            response.put("success", true);
            response.put("enquiries", enquiries);
            response.put("totalCount", enquiries.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error fetching enquiries", e);
            response.put("success", false);
            response.put("message", "Failed to fetch enquiries");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Update enquiry status (admin only)
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateEnquiryStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> statusUpdate) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            CustomerEnquiry enquiry = enquiryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Enquiry not found"));
            
            String newStatus = statusUpdate.get("status");
            CustomerEnquiry.Status previousStatus = enquiry.getStatus();
            CustomerEnquiry.Status updatedStatus = CustomerEnquiry.Status.valueOf(newStatus.toUpperCase());
            
            enquiry.setStatus(updatedStatus);
            enquiry.setLastModifiedDate(LocalDateTime.now());
            
            if (statusUpdate.containsKey("notes")) {
                enquiry.setResolutionNotes(statusUpdate.get("notes"));
            }
            
            enquiryRepository.save(enquiry);
            
            // Send resolution email if status changed to RESOLVED
            if (updatedStatus == CustomerEnquiry.Status.RESOLVED && previousStatus != CustomerEnquiry.Status.RESOLVED) {
                try {
                    String customerEmail = enquiry.getCustomerEmail();
                    String customerName = enquiry.getCustomerName();
                    String enquiryId = enquiry.getId();
                    String subject = enquiry.getSubject() != null ? enquiry.getSubject() : enquiry.getCategory();
                    String resolution = enquiry.getResolutionNotes() != null ? enquiry.getResolutionNotes() : "Your inquiry has been resolved. If you have any further questions, please don't hesitate to contact us.";
                    
                    emailService.sendEnquiryResolutionEmail(
                        customerEmail,
                        customerName,
                        enquiryId,
                        subject,
                        resolution
                    );
                    
                    logger.info("Resolution email sent to customer: {}", customerEmail);
                } catch (Exception emailError) {
                    logger.error("Failed to send resolution email", emailError);
                    // Don't fail the request if email fails
                }
            }
            
            response.put("success", true);
            response.put("message", "Status updated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating enquiry status", e);
            response.put("success", false);
            response.put("message", "Failed to update status");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Delete enquiry (admin only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteEnquiry(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            enquiryRepository.deleteById(id);
            
            response.put("success", true);
            response.put("message", "Enquiry deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error deleting enquiry", e);
            response.put("success", false);
            response.put("message", "Failed to delete enquiry");
            return ResponseEntity.status(500).body(response);
        }
    }
}
