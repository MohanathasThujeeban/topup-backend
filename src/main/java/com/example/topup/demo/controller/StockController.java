package com.example.topup.demo.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.multipart.MultipartFile;

import com.example.topup.demo.entity.EsimOrderRequest;
import com.example.topup.demo.entity.EsimPosSale;
import com.example.topup.demo.entity.RetailerLimit;
import com.example.topup.demo.entity.RetailerOrder;
import com.example.topup.demo.entity.StockPool;
import com.example.topup.demo.entity.User;
import com.example.topup.demo.repository.EsimOrderRequestRepository;
import com.example.topup.demo.repository.EsimPosSaleRepository;
import com.example.topup.demo.repository.RetailerLimitRepository;
import com.example.topup.demo.repository.RetailerOrderRepository;
import com.example.topup.demo.repository.StockPoolRepository;
import com.example.topup.demo.repository.UserRepository;
import com.example.topup.demo.service.EmailService;
import com.example.topup.demo.service.RetailerService;
import com.example.topup.demo.service.StockService;

@RestController
@RequestMapping("/api/admin/stock")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8080", "https://topup-website-gmoj.vercel.app"})
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    @Autowired
    private StockService stockService;

    @Autowired
    private StockPoolRepository stockPoolRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RetailerOrderRepository retailerOrderRepository;

    @Autowired
    private RetailerLimitRepository retailerLimitRepository;

    @Autowired
    private EsimOrderRequestRepository esimOrderRequestRepository;

    @Autowired
    private EsimPosSaleRepository esimPosSaleRepository;

    @Autowired
    private RetailerService retailerService;

    @Autowired
    private com.example.topup.demo.repository.RetailerKickbackLimitRepository retailerKickbackLimitRepository;

    @Autowired
    private com.example.topup.demo.service.KickbackCampaignService kickbackCampaignService;

    @Autowired
    private com.example.topup.demo.service.RetailerLimitService retailerLimitService;

    // Test endpoint to verify controller is loaded
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Stock Controller is working!");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // 1. Get all stock pools with optional filters (PINs are masked by default)
    @GetMapping("/pools")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllStockPools(
            @RequestParam(required = false) String stockType,
            @RequestParam(required = false) String productId) {
        try {
            List<StockPool> pools;
            
            if (stockType != null) {
                StockPool.StockType type = StockPool.StockType.valueOf(stockType.toUpperCase());
                pools = stockService.getStockPoolsByType(type);
            } else if (productId != null) {
                pools = stockService.getStockPoolsByProduct(productId);
            } else {
                pools = stockService.getAllStockPools();
            }
            
            // Mask sensitive data in response
            List<Map<String, Object>> maskedPools = new ArrayList<>();
            for (StockPool pool : pools) {
                Map<String, Object> poolMap = new HashMap<>();
                poolMap.put("id", pool.getId());
                poolMap.put("name", pool.getName());
                poolMap.put("bundleName", pool.getBatchNumber()); // CSV filename
                poolMap.put("stockType", pool.getStockType());
                poolMap.put("productId", pool.getProductId());
                poolMap.put("totalQuantity", pool.getTotalQuantity());
                poolMap.put("availableQuantity", pool.getAvailableQuantity());
                poolMap.put("usedQuantity", pool.getUsedQuantity());
                poolMap.put("reservedQuantity", pool.getReservedQuantity());
                poolMap.put("status", pool.getStatus());
                poolMap.put("networkProvider", pool.getNetworkProvider());
                poolMap.put("productType", pool.getProductType());
                poolMap.put("price", pool.getPrice());
                poolMap.put("description", pool.getDescription());
                poolMap.put("supplier", pool.getSupplier());
                poolMap.put("createdDate", pool.getCreatedDate());
                poolMap.put("lastModifiedDate", pool.getLastModifiedDate());
                poolMap.put("createdBy", pool.getCreatedBy());
                poolMap.put("lastModifiedBy", pool.getLastModifiedBy());
                
                // Mask individual items (don't send full PINs/ICCIDs)
                List<Map<String, Object>> maskedItems = new ArrayList<>();
                for (StockPool.StockItem item : pool.getItems()) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("itemId", item.getItemId());
                    // Decrypt then mask for display
                    String decrypted = stockService.decryptData(item.getItemData());
                    itemMap.put("itemData", stockService.maskData(decrypted)); // ****1234
                    itemMap.put("status", item.getStatus());
                    itemMap.put("assignedToOrderId", item.getAssignedToOrderId());
                    maskedItems.add(itemMap);
                }
                poolMap.put("items", maskedItems);
                poolMap.put("itemCount", maskedItems.size());
                
                maskedPools.add(poolMap);
            }
            
            return ResponseEntity.ok(maskedPools);
        } catch (Exception e) {
            log.error("Error fetching stock pools: ", e);
            // Return empty list instead of error to prevent frontend crash
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // NEW: Get stock pools formatted for Bundle Management display
    @GetMapping("/pools/bundles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getStockPoolsForBundleManagement() {
        try {
            List<Map<String, Object>> bundles = stockService.getAllStockPoolsForBundleManagement();
            return ResponseEntity.ok(bundles);
        } catch (Exception e) {
            log.error("Error fetching stock bundles: ", e);
            // Return empty list instead of error
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // 2. Get stock pool by ID
    @GetMapping("/pools/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockPool> getStockPoolById(@PathVariable String id) {
        try {
            StockPool pool = stockService.getStockPoolById(id);
            if (pool != null) {
                return ResponseEntity.ok(pool);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 3. Create new stock pool - TODO: Add method to StockService
    @PostMapping("/pools")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> createStockPool(@RequestBody StockPool stockPool) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Create stock pool functionality will be added soon");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    // 4. Update stock pool - TODO: Add method to StockService
    @PutMapping("/pools/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> updateStockPool(
            @PathVariable String id, 
            @RequestBody StockPool stockPool) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Update stock pool functionality will be added soon");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    // 6. Bulk upload PIN stock from CSV
    @PostMapping("/pins/bulk-upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUploadPins(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson,
            @RequestParam(required = false, defaultValue = "admin") String uploadedBy) {
        try {
            // Parse metadata JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, String> metadata = mapper.readValue(metadataJson, Map.class);
            
            String poolName = metadata.get("poolName");
            String productType = metadata.get("productType");
            String networkProvider = metadata.get("networkProvider");
            String productId = metadata.get("productId");
            String price = metadata.get("price");
            String notes = metadata.get("notes");
            
            // Log received parameters
            System.out.println("===============================================");
            System.out.println("📥 Received PIN upload request:");
            System.out.println("File: " + (file != null ? file.getOriginalFilename() + " (" + file.getSize() + " bytes)" : "NULL"));
            System.out.println("Pool Name: " + poolName);
            System.out.println("Product Type: " + productType);
            System.out.println("Network Provider: " + networkProvider);
            System.out.println("Product ID: " + productId);
            System.out.println("Price: " + price);
            System.out.println("Notes: " + notes);
            System.out.println("Uploaded By: " + uploadedBy);
            System.out.println("===============================================");
            
            if (file == null) {
                throw new IllegalArgumentException("No file uploaded");
            }
            
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded file is empty");
            }
            
            Map<String, Object> result = stockService.uploadPinStock(file, uploadedBy, poolName, productId, price, notes, productType, networkProvider);
            
            System.out.println("✅ Upload successful!");
            System.out.println("Result: " + result);
            System.out.println("===============================================");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("❌ Upload failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("===============================================");
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to upload PINs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // 7. Bulk upload eSIM stock from CSV with QR code images
    @PostMapping("/esims/bulk-upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUploadEsims(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson,
            @RequestParam(value = "qrCodes", required = false) List<MultipartFile> qrCodeFiles,
            @RequestParam(required = false, defaultValue = "admin") String uploadedBy) {
        try {
            // Parse metadata JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, String> metadata = mapper.readValue(metadataJson, Map.class);
            
            String poolName = metadata.get("poolName");
            String productType = metadata.get("productType");
            String networkProvider = metadata.get("networkProvider");
            String productId = metadata.get("productId");
            String price = metadata.get("price");
            String notes = metadata.get("notes");
            
            System.out.println("===============================================");
            System.out.println("📥 Received eSIM upload request:");
            System.out.println("File: " + (file != null ? file.getOriginalFilename() + " (" + file.getSize() + " bytes)" : "NULL"));
            System.out.println("Pool Name: " + poolName);
            System.out.println("Product Type: " + productType);
            System.out.println("Network Provider: " + networkProvider);
            System.out.println("Product ID: " + productId);
            System.out.println("Price: " + price);
            System.out.println("Notes: " + notes);
            System.out.println("QR Code Files: " + (qrCodeFiles != null ? qrCodeFiles.size() : 0));
            System.out.println("===============================================");
            
            // Convert QR code files to Base64 strings for storage
            if (qrCodeFiles != null && !qrCodeFiles.isEmpty()) {
                System.out.println("🔄 Processing " + qrCodeFiles.size() + " QR code images...");
                
                // Create a map of filename (without extension) to Base64 QR code
                Map<String, String> qrCodeByFilename = new HashMap<>();
                for (MultipartFile qrFile : qrCodeFiles) {
                    String filename = qrFile.getOriginalFilename();
                    if (filename != null) {
                        // Remove file extension and clean the filename
                        String filenameWithoutExt = filename.replaceFirst("[.][^.]+$", "").trim();
                        
                        byte[] qrBytes = qrFile.getBytes();
                        String base64QR = java.util.Base64.getEncoder().encodeToString(qrBytes);
                        qrCodeByFilename.put(filenameWithoutExt, base64QR);
                        
                        System.out.println("   📸 Loaded QR: " + filename + " (key: " + filenameWithoutExt + ")");
                        System.out.println("      📏 Original file size: " + qrFile.getSize() + " bytes");
                        System.out.println("      📏 Byte array length: " + qrBytes.length + " bytes");
                        System.out.println("      📏 Base64 string length: " + base64QR.length() + " chars");
                        System.out.println("      🔍 Base64 starts with: " + base64QR.substring(0, Math.min(100, base64QR.length())));
                    }
                }
                System.out.println("✅ QR codes loaded successfully");
                
                // Pass the filename-based map instead of index-based map
                Map<String, Object> result = stockService.uploadEsimStockWithQRByFilename(
                    file, qrCodeByFilename, uploadedBy, poolName, productId, price, notes, productType, networkProvider);
                return ResponseEntity.ok(result);
            } else {
                // No QR codes provided, use regular upload
                Map<String, Object> result = stockService.uploadEsimStockWithQR(
                    file, new HashMap<>(), uploadedBy, poolName, productId, price, notes, productType, networkProvider);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to upload eSIMs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // 8. Get stock statistics
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStockStatistics() {
        try {
            // Call the actual method that exists: getStockUsageStatistics()
            Map<String, Object> stats = stockService.getStockUsageStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 9. Get stock usage report
    @GetMapping("/usage-report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStockUsageReport() {
        try {
            // Call the actual method that exists (no parameters)
            Map<String, Object> stats = stockService.getStockUsageStatistics();
            
            // Wrap response in 'statistics' property to match frontend expectations
            Map<String, Object> response = new HashMap<>();
            response.put("statistics", stats);
            response.put("success", true);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 10. Get low stock alerts - TODO: Implement in service
    @GetMapping("/low-stock-alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getLowStockAlerts(
            @RequestParam(defaultValue = "10") int threshold) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Low stock alerts functionality will be added soon");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    // 11. Get stock items from a pool - TODO: Implement in service
    @GetMapping("/pools/{poolId}/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getStockItems(
            @PathVariable String poolId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Get stock items functionality will be added soon");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(response);
    }

    // NEW: Get stock items with decryption for admin viewing
    @GetMapping("/pools/{poolId}/items/decrypted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDecryptedStockItems(@PathVariable String poolId) {
        try {
            StockPool pool = stockService.getStockPoolById(poolId);
            if (pool == null) {
                return ResponseEntity.notFound().build();
            }

            List<Map<String, Object>> decryptedItems = new ArrayList<>();
            for (StockPool.StockItem item : pool.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("itemId", item.getItemId());

                // Decrypt underlying data once for this item
                String decryptedData = stockService.decryptData(item.getItemData());

                // Always return masked value for display safety
                itemMap.put("itemData", stockService.maskData(decryptedData));

                // Derive serial/ICCID value
                String serialNumber = item.getSerialNumber();
                if (pool.getStockType() == StockPool.StockType.ESIM) {
                    // For eSIMs, fall back to full ICCID when serialNumber is missing
                    if (serialNumber == null || serialNumber.trim().isEmpty()) {
                        serialNumber = decryptedData;
                    }
                }
                itemMap.put("serialNumber", serialNumber);

                // Derive price, falling back to pool price when item price is empty
                String itemPrice = item.getPrice();
                if ((itemPrice == null || itemPrice.trim().isEmpty()) &&
                    pool.getPrice() != null && !pool.getPrice().trim().isEmpty()) {
                    itemPrice = pool.getPrice();
                }

                itemMap.put("status", item.getStatus());
                itemMap.put("assignedDate", item.getAssignedDate());
                itemMap.put("assignedToOrderId", item.getAssignedToOrderId());
                itemMap.put("assignedToUserId", item.getAssignedToUserId());
                itemMap.put("assignedToUserEmail", item.getAssignedToUserEmail());
                itemMap.put("notes", item.getNotes());
                itemMap.put("productId", item.getProductId()); // Add productId from item
                itemMap.put("price", itemPrice); // Add price from item or pool
                itemMap.put("type", item.getType()); // Add type from item
                decryptedItems.add(itemMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("poolId", pool.getId());
            response.put("poolName", pool.getName());
            response.put("bundleName", pool.getBatchNumber());
            response.put("stockType", pool.getStockType());
            response.put("productId", pool.getProductId()); // Add productId from pool
            response.put("notes", pool.getDescription()); // Add notes/description from pool
            response.put("items", decryptedItems);
            response.put("totalCount", decryptedItems.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve items: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // 12. Download PIN CSV template (Simplified: PIN ID and PINS only)
    @GetMapping("/templates/pin-template.csv")
    public ResponseEntity<Resource> downloadPinTemplate() {
        try {
            String csvContent = "PIN ID,PINS\n" +
                    "22828126454,5.00004E+11\n" +
                    "99989145671,5.00004E+11\n" +
                    "62497545631,5.00004E+11\n";
            
            ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pin-upload-template.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 13. Download eSIM CSV template
    @GetMapping("/templates/esim-template.csv")
    public ResponseEntity<Resource> downloadEsimTemplate() {
        try {
            String csvContent = "iccid,activation_code,qr_code_url,validity_days,batch_number\n" +
                    "89011234567890123456,ACT001,https://example.com/qr1,30,BATCH001\n" +
                    "89011234567890123457,ACT002,https://example.com/qr2,30,BATCH001\n" +
                    "89011234567890123458,ACT003,https://example.com/qr3,30,BATCH001\n";
            
            ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"esim-upload-template.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Additional helper endpoint: Assign stock to order (called by order service)
    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> assignStockToOrder(
            @RequestParam String productId,
            @RequestParam StockPool.StockType stockType,
            @RequestParam String orderId,
            @RequestParam String userId,
            @RequestParam String userEmail) {
        try {
            StockPool.StockItem assignedItem = stockService.assignStockToOrder(
                    productId, stockType, orderId, userId, userEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("item", assignedItem);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to assign stock: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // Delete entire stock pool
    @DeleteMapping("/pools/{poolId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteStockPool(@PathVariable String poolId) {
        try {
            stockService.deleteStockPool(poolId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Stock pool deleted successfully");
            response.put("poolId", poolId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete pool: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // Delete individual item from pool
    @DeleteMapping("/pools/{poolId}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteStockItem(
            @PathVariable String poolId,
            @PathVariable String itemId) {
        try {
            stockService.deleteStockItem(poolId, itemId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item deleted successfully");
            response.put("poolId", poolId);
            response.put("itemId", itemId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // Update individual item details (price, type, notes)
    @PutMapping("/pools/{poolId}/items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateStockItem(
            @PathVariable String poolId,
            @PathVariable String itemId,
            @RequestBody Map<String, String> updates) {
        try {
            stockService.updateStockItem(poolId, itemId, updates);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Item updated successfully");
            response.put("poolId", poolId);
            response.put("itemId", itemId);
            response.put("updates", updates);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to update item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // Delete ALL stock pools (use with caution!)
    @DeleteMapping("/pools/clear-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clearAllStockPools() {
        try {
            long deletedCount = stockService.deleteAllStockPools();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All stock pools deleted successfully");
            response.put("deletedCount", deletedCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete all pools: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // Get available eSIMs with decrypted QR codes for retailer Point of Sale
    @GetMapping("/esims/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableEsimsForSale(
            @RequestParam(required = false) String networkProvider,
            @RequestParam(required = false) String productId) {
        try {
            System.out.println("📱 Fetching available eSIMs for Point of Sale");
            System.out.println("   - Network Provider: " + networkProvider);
            System.out.println("   - Product ID: " + productId);
            
            // Get all eSIM stock pools
            List<StockPool> pools = stockService.getStockPoolsByType(StockPool.StockType.ESIM);
            
            // Filter by network provider if specified
            if (networkProvider != null && !networkProvider.equals("All Operators")) {
                pools = pools.stream()
                    .filter(pool -> networkProvider.equals(pool.getNetworkProvider()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            // Filter by product ID if specified
            if (productId != null && !productId.isEmpty()) {
                pools = pools.stream()
                    .filter(pool -> productId.equals(pool.getProductId()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            List<Map<String, Object>> availableEsims = new ArrayList<>();
            
            for (StockPool pool : pools) {
                // Only include pools with available stock
                if (pool.getAvailableQuantity() <= 0) {
                    continue;
                }
                
                Map<String, Object> esimProduct = new HashMap<>();
                esimProduct.put("id", pool.getId());
                esimProduct.put("poolName", pool.getName());
                esimProduct.put("productId", pool.getProductId());
                esimProduct.put("networkProvider", pool.getNetworkProvider());
                esimProduct.put("productType", pool.getProductType());
                esimProduct.put("price", pool.getPrice());
                esimProduct.put("totalQuantity", pool.getTotalQuantity());
                esimProduct.put("availableQuantity", pool.getAvailableQuantity());
                esimProduct.put("description", pool.getDescription());
                
                // Get available eSIM items with decrypted QR codes
                List<Map<String, Object>> availableItems = new ArrayList<>();
                for (StockPool.StockItem item : pool.getItems()) {
                    if (item.getStatus() == StockPool.StockItem.ItemStatus.AVAILABLE) {
                        Map<String, Object> itemData = new HashMap<>();
                        
                        // Decrypt sensitive data
                        itemData.put("itemId", item.getItemId());
                        itemData.put("iccid", stockService.decryptData(item.getItemData()));
                        
                        if (item.getActivationCode() != null) {
                            itemData.put("activationCode", stockService.decryptData(item.getActivationCode()));
                        }
                        
                        // Decrypt QR code image (Base64)
                        if (item.getQrCodeImage() != null && !item.getQrCodeImage().isEmpty()) {
                            itemData.put("qrCodeImage", stockService.decryptData(item.getQrCodeImage()));
                        }
                        
                        // Include SM-DP+ Address
                        if (item.getActivationUrl() != null && !item.getActivationUrl().isEmpty()) {
                            itemData.put("smDpAddress", stockService.decryptData(item.getActivationUrl()));
                        } else if (item.getActivationCode() != null) {
                            // Try to extract from activation code
                            String decryptedCode = stockService.decryptData(item.getActivationCode());
                            if (decryptedCode != null && decryptedCode.contains("$")) {
                                String[] parts = decryptedCode.split("\\$");
                                if (parts.length > 1) {
                                    itemData.put("smDpAddress", parts[1]);
                                }
                            }
                        }
                        
                        // Include PIN/PUK if available (already encryptrd)
                        if (item.getPin1() != null) {
                            itemData.put("pin1", stockService.decryptData(item.getPin1()));
                        }
                        if (item.getPuk1() != null) {
                            itemData.put("puk1", stockService.decryptData(item.getPuk1()));
                        }
                        if (item.getPin2() != null) {
                            itemData.put("pin2", stockService.decryptData(item.getPin2()));
                        }
                        if (item.getPuk2() != null) {
                            itemData.put("puk2", stockService.decryptData(item.getPuk2()));
                        }
                        
                        availableItems.add(itemData);
                    }
                }
                
                esimProduct.put("availableEsims", availableItems);
                esimProduct.put("availableCount", availableItems.size());
                
                availableEsims.add(esimProduct);
            }
            
            System.out.println("✅ Found " + availableEsims.size() + " eSIM products with available stock");
            return ResponseEntity.ok(availableEsims);
            
        } catch (Exception e) {
            System.err.println("❌ Error fetching available eSIMs: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Send eSIM QR code to customer email
    @PostMapping("/esims/send-qr")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Map<String, Object>> sendEsimQRCode(@RequestBody String requestBodyStr, Authentication authentication) {
        System.out.println("\n🔍 ===== eSIM Send QR Request Received =====");
        System.out.println("Raw request body: " + requestBodyStr);
        System.out.println("Authentication principal: " + (authentication != null ? authentication.getPrincipal() : "null"));
        
        Map<String, Object> requestData;
        try {
            // Parse JSON string manually
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            requestData = mapper.readValue(requestBodyStr, java.util.LinkedHashMap.class);
            System.out.println("✅ JSON parsed successfully");
            System.out.println("Parsed data keys: " + requestData.keySet());
        } catch (Exception e) {
            System.err.println("❌ JSON parsing failed: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Invalid JSON in request body");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("receivedBody", requestBodyStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        try {
            System.out.println("\n📋 Extracting parameters from request data:");
            
            Object itemIdObj = requestData.get("itemId");
            Object iccidObj = requestData.get("iccid");
            Object customerEmailObj = requestData.get("customerEmail");
            Object customerNameObj = requestData.get("customerName");
            Object passportIdObj = requestData.get("passportId");
            Object poolIdObj = requestData.get("poolId");
            Object priceObj = requestData.get("price");
            Object paymentModeObj = requestData.get("paymentMode");
            Object skipEmailObj = requestData.get("skipEmail");
            
            // Convert to strings with null safety
            String itemId = (itemIdObj != null ? itemIdObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String iccid = (iccidObj != null ? iccidObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String customerEmail = (customerEmailObj != null ? customerEmailObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String customerName = (customerNameObj != null ? customerNameObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String passportId = (passportIdObj != null ? passportIdObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String poolId = (poolIdObj != null ? poolIdObj.toString().trim() : "").replaceAll("[;'\"]", "");
            String priceStr = (priceObj != null ? priceObj.toString().trim() : "0").replaceAll("[;'\"]", "");
            String paymentMode = (paymentModeObj != null ? paymentModeObj.toString().trim() : "credit").replaceAll("[;'\"]", ""); // Default to 'credit'
            boolean skipEmail = (skipEmailObj != null && Boolean.parseBoolean(skipEmailObj.toString())); // Check if email should be skipped
            
            System.out.println("   itemId: [" + itemId + "]");
            System.out.println("   iccid: [" + iccid + "]");
            System.out.println("   customerEmail: [" + customerEmail + "]");
            System.out.println("   customerName: [" + customerName + "]");
            System.out.println("   passportId: [" + passportId + "]");
            System.out.println("   poolId: [" + poolId + "]");
            System.out.println("   price: [" + priceStr + "]");
            System.out.println("   paymentMode: [" + paymentMode + "]");
            System.out.println("   skipEmail: [" + skipEmail + "]");
            
            // Validate required fields
            if (itemId.isEmpty()) {
                throw new IllegalArgumentException("itemId is required but was empty or missing");
            }
            if (iccid.isEmpty()) {
                throw new IllegalArgumentException("iccid is required but was empty or missing");
            }
            // Only validate email fields if we're NOT skipping email
            if (!skipEmail) {
                if (customerEmail.isEmpty()) {
                    throw new IllegalArgumentException("customerEmail is required but was empty or missing");
                }
                if (customerName.isEmpty()) {
                    throw new IllegalArgumentException("customerName is required but was empty or missing");
                }
            }
            if (poolId.isEmpty()) {
                throw new IllegalArgumentException("poolId is required but was empty or missing");
            }
            
            // Parse price
            double price = 0.0;
            try {
                price = Double.parseDouble(priceStr);
                System.out.println("✅ Price parsed: " + price);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Invalid price format: " + priceStr + ", using 0");
                price = 0.0;
            }

            System.out.println("\n📧 Preparing to send eSIM QR code:");
            System.out.println("   - Customer: " + customerName);
            System.out.println("   - Email: " + customerEmail);
            System.out.println("   - ICCID: " + iccid);
            System.out.println("   - Pool ID: " + poolId);
            System.out.println("   - Price: " + price);

            // Get the stock pool and find the item
            StockPool pool = stockService.getStockPoolById(poolId);
            if (pool == null) {
                throw new IllegalArgumentException("Stock pool not found with ID: " + poolId);
            }
            System.out.println("✅ Stock pool found: " + pool.getName());
            System.out.println("   Total items in pool: " + pool.getItems().size());
            
            // Find the eSIM item in inventory by ICCID
            System.out.println("🔍 Searching for eSIM item with ICCID: " + iccid);
            System.out.println("🔍 Searching for eSIM item with ItemId: " + itemId);
            
            // First, let's log all items to see what we have
            System.out.println("📋 All items in pool:");
            for (int i = 0; i < pool.getItems().size(); i++) {
                var item = pool.getItems().get(i);
                System.out.println("   Item " + i + ":");
                System.out.println("      - ItemId: " + item.getItemId());
                System.out.println("      - SerialNumber: " + item.getSerialNumber());
                
                // Try to decrypt itemData to see if it contains ICCID
                if (item.getItemData() != null && !item.getItemData().isEmpty()) {
                    try {
                        String decryptedItemData = stockService.decryptData(item.getItemData());
                        System.out.println("      - ItemData (decrypted): " + decryptedItemData);
                    } catch (Exception e) {
                        System.out.println("      - ItemData (encrypted): " + item.getItemData().substring(0, Math.min(20, item.getItemData().length())) + "...");
                    }
                }
            }
            
            // Search for the item - check multiple fields
            var esimItem = pool.getItems().stream()
                    .filter(item -> {
                        // Match by itemId first
                        if (itemId.equals(item.getItemId())) {
                            System.out.println("✅ Found by ItemId: " + item.getItemId());
                            return true;
                        }
                        
                        // Match by serialNumber
                        if (iccid.equals(item.getSerialNumber())) {
                            System.out.println("✅ Found by SerialNumber: " + item.getSerialNumber());
                            return true;
                        }
                        
                        // Match by decrypted itemData (ICCID might be stored here)
                        if (item.getItemData() != null && !item.getItemData().isEmpty()) {
                            try {
                                String decryptedData = stockService.decryptData(item.getItemData());
                                if (iccid.equals(decryptedData)) {
                                    System.out.println("✅ Found by decrypted ItemData: " + decryptedData);
                                    return true;
                                }
                            } catch (Exception e) {
                                // Ignore decryption errors
                            }
                        }
                        
                        return false;
                    })
                    .findFirst()
                    .orElse(null);
            
            if (esimItem == null) {
                System.err.println("❌ eSIM item not found!");
                System.err.println("   Searched for ICCID: " + iccid);
                System.err.println("   Searched for ItemId: " + itemId);
                System.err.println("   Total items in pool: " + pool.getItems().size());
                throw new IllegalArgumentException("eSIM item not found with ICCID: " + iccid + " or ItemId: " + itemId + " in pool: " + poolId);
            }
            
            System.out.println("✅ eSIM item found - ItemId: " + esimItem.getItemId());
            
            // Decrypt eSIM details for email
            String decryptedActivationCode = "";
            String smDpAddress = "";
            String qrCodeBase64 = "";
            
            System.out.println("🔍 Checking eSIM item data:");
            System.out.println("   - ICCID (serialNumber): " + esimItem.getSerialNumber());
            System.out.println("   - ItemId: " + esimItem.getItemId());
            System.out.println("   - Has activationCode: " + (esimItem.getActivationCode() != null && !esimItem.getActivationCode().isEmpty()));
            System.out.println("   - Has qrCodeImage: " + (esimItem.getQrCodeImage() != null && !esimItem.getQrCodeImage().isEmpty()));
            System.out.println("   - Has activationUrl: " + (esimItem.getActivationUrl() != null && !esimItem.getActivationUrl().isEmpty()));
            System.out.println("   - Has qrCodeUrl: " + (esimItem.getQrCodeUrl() != null && !esimItem.getQrCodeUrl().isEmpty()));
            
            try {
                // Decrypt activation code
                if (esimItem.getActivationCode() != null && !esimItem.getActivationCode().isEmpty()) {
                    decryptedActivationCode = stockService.decryptData(esimItem.getActivationCode());
                    System.out.println("✅ Decrypted activation code for ICCID " + esimItem.getSerialNumber());
                }
                
                // Get QR code from qrCodeImage - check if it needs decryption or is already base64
                if (esimItem.getQrCodeImage() != null && !esimItem.getQrCodeImage().isEmpty()) {
                    String rawQrCode = esimItem.getQrCodeImage();
                    System.out.println("🔍 Raw QR code length: " + rawQrCode.length() + " chars");
                    
                    // Check if it's already valid base64 (PNG starts with iVBORw0KGgo)
                    if (rawQrCode.startsWith("iVBORw0KGgo")) {
                        // Already plain base64, use directly
                        qrCodeBase64 = rawQrCode;
                        System.out.println("✅ QR code is already in base64 format (not encrypted) - length: " + qrCodeBase64.length() + " chars");
                    } else {
                        // Needs decryption
                        try {
                            qrCodeBase64 = stockService.decryptData(rawQrCode);
                            System.out.println("✅ Decrypted QR code - length: " + qrCodeBase64.length() + " chars");
                        } catch (Exception e) {
                            System.err.println("⚠️ Failed to decrypt QR code, using raw value: " + e.getMessage());
                            qrCodeBase64 = rawQrCode;
                        }
                    }
                    
                    System.out.println("   QR code starts with: " + (qrCodeBase64.length() > 50 ? qrCodeBase64.substring(0, 50) + "..." : qrCodeBase64));
                    System.out.println("   Is valid PNG: " + qrCodeBase64.startsWith("iVBORw0KGgo"));
                } else {
                    System.out.println("⚠️ No QR code image stored in database");
                }
                
                // SM-DP address - decrypt from activationUrl or extract from activation code
                if (esimItem.getActivationUrl() != null && !esimItem.getActivationUrl().isEmpty()) {
                    smDpAddress = stockService.decryptData(esimItem.getActivationUrl());
                    System.out.println("✅ Decrypted SM-DP address from activationUrl: " + smDpAddress);
                } else if (decryptedActivationCode != null && decryptedActivationCode.startsWith("LPA:")) {
                    // Extract SM-DP+ from LPA string (format: LPA:1$SM-DP-ADDRESS$ACTIVATION-CODE)
                    String[] parts = decryptedActivationCode.split("\\$");
                    if (parts.length >= 2) {
                        smDpAddress = parts[1];
                        System.out.println("✅ Extracted SM-DP address from activation code: " + smDpAddress);
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error decrypting eSIM data: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("📊 Final email data:");
            System.out.println("   - Activation Code: " + (decryptedActivationCode != null && !decryptedActivationCode.isEmpty() ? "✅" : "❌"));
            System.out.println("   - SM-DP Address: " + (smDpAddress != null && !smDpAddress.isEmpty() ? "✅" : "❌"));
            System.out.println("   - QR Code: " + (qrCodeBase64 != null && !qrCodeBase64.isEmpty() ? "✅ (" + qrCodeBase64.length() + " chars)" : "❌"));
            
            // Generate order ID
            String orderId = "eSIM-" + System.currentTimeMillis();
            
            // Send email only if skipEmail is false
            if (!skipEmail) {
                System.out.println("📤 Sending professional eSIM activation email to: " + customerEmail);
                System.out.println("   - Product: " + pool.getName());
                System.out.println("   - ICCID: " + iccid);
                System.out.println("   - Price: " + priceStr + " NOK");
                System.out.println("   - Has QR Code: " + (qrCodeBase64 != null && !qrCodeBase64.isEmpty()));
                System.out.println("   - QR Code Length: " + (qrCodeBase64 != null ? qrCodeBase64.length() : 0));
                if (qrCodeBase64 != null && qrCodeBase64.length() > 0) {
                    System.out.println("   - QR Code Starts With: " + qrCodeBase64.substring(0, Math.min(50, qrCodeBase64.length())));
                    System.out.println("   - Is Valid PNG: " + qrCodeBase64.startsWith("iVBORw0KGgo"));
                } else {
                    System.out.println("   - ❌ QR CODE IS NULL OR EMPTY - EMAIL WILL NOT HAVE QR CODE!");
                }
                System.out.println("   - Has Activation Code: " + (decryptedActivationCode != null && !decryptedActivationCode.isEmpty()));
                emailService.sendEsimApprovalEmail(
                    customerEmail,
                    customerName,
                    orderId,
                    iccid,
                    qrCodeBase64,
                    decryptedActivationCode,
                    smDpAddress,
                    priceStr + " NOK"
                );
                System.out.println("✅ Professional email sent successfully with embedded QR code and price");
            } else {
                System.out.println("⏭️ Skipping email send (skipEmail=true) - POS Print mode");
            }
            
            // Mark item as USED and remove from pool
            System.out.println("📦 Updating stock pool - marking item as USED and removing from inventory");
            esimItem.setStatus(StockPool.StockItem.ItemStatus.USED);
            
            // Set assignment info - use placeholder for print orders
            String assignedEmail = skipEmail ? "print@easytopup.no" : customerEmail;
            String customerInfo = skipEmail ? "POS Print Order" : customerName + " (" + customerEmail + ")";
            
            esimItem.setAssignedToUserEmail(assignedEmail);
            esimItem.setUsedDate(java.time.LocalDateTime.now());
            esimItem.setNotes("Sold to: " + customerInfo);
            
            // Remove from available inventory
            pool.getItems().remove(esimItem);
            
            // Update pool quantities
            if (pool.getAvailableQuantity() != null && pool.getAvailableQuantity() > 0) {
                pool.setAvailableQuantity(pool.getAvailableQuantity() - 1);
            }
            if (pool.getUsedQuantity() != null) {
                pool.setUsedQuantity(pool.getUsedQuantity() + 1);
            } else {
                pool.setUsedQuantity(1);
            }
            
            stockPoolRepository.save(pool);
            System.out.println("✅ Item marked as USED and removed from pool. Remaining items: " + pool.getItems().size());
            
            // Record the sale in database - CRITICAL: This must happen for analytics and credit updates
            System.out.println("\n=== STARTING SALE RECORDING PROCESS ===");
            System.out.println("📋 Authentication object: " + (authentication != null ? "Present" : "NULL"));
            if (authentication != null) {
                System.out.println("📋 Authentication name: " + authentication.getName());
                System.out.println("📋 Authentication principal: " + authentication.getPrincipal());
                System.out.println("📋 Authentication authorities: " + authentication.getAuthorities());
            }
            
            // Get retailer - with fallback to any BUSINESS user if authentication is null
            String retailerEmail = null;
            if (authentication != null && authentication.getName() != null) {
                retailerEmail = authentication.getName();
                System.out.println("💾 Using authenticated user: " + retailerEmail);
            } else {
                // Fallback: Find any BUSINESS user
                System.out.println("⚠️ Authentication is null - checking for any BUSINESS user");
                List<User> businessUsers = userRepository.findByAccountType(User.AccountType.BUSINESS);
                if (!businessUsers.isEmpty()) {
                    retailerEmail = businessUsers.get(0).getEmail();
                    System.out.println("💾 Using fallback BUSINESS user: " + retailerEmail);
                } else {
                    System.err.println("❌ No BUSINESS user found for fallback");
                }
            }
            
            if (retailerEmail != null) {
                System.out.println("💾 Recording eSIM sale for user: " + retailerEmail);
                
                var retailerOpt = userRepository.findByEmail(retailerEmail);
                if (!retailerOpt.isPresent()) {
                    System.err.println("⚠️ Retailer user not found: " + retailerEmail);
                } else {
                    User retailer = retailerOpt.get();
                    try {
                        // Create RetailerOrder with OrderItem
                        RetailerOrder order = new RetailerOrder();
                        order.setRetailerId(retailer.getId());
                        order.setOrderNumber("eSIM-" + System.currentTimeMillis());
                        
                        // Add customer details in notes field - use placeholder for print orders
                        String orderNotes = skipEmail ? "POS Print Order" : "Customer: " + customerName + " (" + customerEmail + ")";
                        order.setNotes(orderNotes);
                        
                        // Create OrderItem for the eSIM
                        RetailerOrder.OrderItem item = new RetailerOrder.OrderItem();
                        item.setProductId(poolId);
                        item.setProductName(pool.getName());
                        item.setProductType("ESIM");
                        item.setCategory("ESIM");
                        item.setQuantity(1);
                        item.setUnitPrice(BigDecimal.valueOf(price));
                        item.setRetailPrice(BigDecimal.valueOf(price));
                        item.setSerialNumbers(java.util.Arrays.asList(iccid));
                        
                        // Set network provider from pool
                        if (pool.getNetworkProvider() != null && !pool.getNetworkProvider().isEmpty()) {
                            item.setNetworkProvider(pool.getNetworkProvider());
                            System.out.println("   📡 OrderItem Network Provider set: " + pool.getNetworkProvider());
                        } else {
                            System.out.println("   ⚠️ Network Provider not available in pool for OrderItem");
                        }
                        
                        order.addItem(item);
                        order.setTotalAmount(BigDecimal.valueOf(price));
                        order.setCurrency("NOK");
                        order.setStatus(RetailerOrder.OrderStatus.COMPLETED);
                        order.setPaymentStatus(RetailerOrder.PaymentStatus.COMPLETED);
                        order.setPaymentMethod("POINT_OF_SALE");
                        order.setCreatedDate(java.time.LocalDateTime.now());
                        order.setLastModifiedDate(java.time.LocalDateTime.now());
                        order.setCreatedBy(retailerEmail);
                        
                        retailerOrderRepository.save(order);
                        System.out.println("=== RetailerOrder SAVED SUCCESSFULLY ===");
                        System.out.println("✅ RetailerOrder created with ID: " + order.getId());
                        System.out.println("📝 Order Number: " + order.getOrderNumber());
                        System.out.println("📝 Retailer ID: " + order.getRetailerId());
                        System.out.println("📝 Payment Method: " + order.getPaymentMethod());
                        System.out.println("📝 Status: " + order.getStatus());
                        System.out.println("📝 Customer: " + customerName + " (" + customerEmail + ")");
                        System.out.println("📝 ICCID: " + iccid);
                        System.out.println("📝 Amount: NOK " + price);
                        
                        // Create EsimOrderRequest to store customer details for sales report
                        EsimOrderRequest esimOrderRequest = new EsimOrderRequest();
                        esimOrderRequest.setOrderNumber(order.getOrderNumber());
                        esimOrderRequest.setCustomerFullName(skipEmail ? "POS Print Order" : customerName);
                        esimOrderRequest.setCustomerEmail(skipEmail ? "print@easytopup.no" : customerEmail);
                        esimOrderRequest.setProductName(pool.getName());
                        esimOrderRequest.setProductId(poolId);
                        esimOrderRequest.setAmount((double) price);
                        esimOrderRequest.setPaymentMethod("POINT_OF_SALE");
                        esimOrderRequest.setStatus("APPROVED");
                        esimOrderRequest.setAssignedEsimSerial(iccid);
                        esimOrderRequest.setApprovedByAdmin(retailerEmail);
                        esimOrderRequest.setApprovedDate(java.time.LocalDateTime.now());
                        esimOrderRequest.setRequestDate(java.time.LocalDateTime.now());
                        esimOrderRequestRepository.save(esimOrderRequest);
                        System.out.println("✅ EsimOrderRequest created for sales report with ICCID: " + iccid);
                        
                        // ========== SAVE TO NEW esim_pos_sales COLLECTION ==========
                        EsimPosSale savedPosSale = null;
                        try {
                            EsimPosSale posSale = new EsimPosSale(retailer, skipEmail ? "print@easytopup.no" : customerEmail);
                            posSale.setCustomerName(skipEmail ? "POS Print Order" : customerName);
                            posSale.setIccid(iccid);
                            posSale.setProductName(pool.getName());
                            posSale.setProductId(poolId);
                            posSale.setStockPoolId(poolId);
                            posSale.setStockPoolName(pool.getName());
                            posSale.setSalePrice(BigDecimal.valueOf(price));
                            posSale.setOrderId(order.getId());
                            posSale.setOrderReference(order.getOrderNumber());
                            posSale.setStatus(EsimPosSale.SaleStatus.COMPLETED);
                            posSale.setEmailSent(!skipEmail); // Only mark as sent if email was actually sent
                            posSale.setDeliveryMethod(skipEmail ? "print" : "email"); // Set delivery method based on skipEmail flag
                            posSale.setCreatedBy(retailerEmail);
                            
                            // Store QR code as base64 data URL if available
                            if (qrCodeBase64 != null && !qrCodeBase64.isEmpty()) {
                                String qrDataUrl = "data:image/png;base64," + qrCodeBase64;
                                posSale.setQrCodeUrl(qrDataUrl);
                                System.out.println("   📸 QR Code saved to POS sale (length: " + qrDataUrl.length() + " chars)");
                            } else {
                                System.out.println("   ⚠️ No QR code available to save");
                            }
                            
                            // Set cost price if available
                            if (pool.getPrice() != null && !pool.getPrice().isEmpty()) {
                                try {
                                    BigDecimal poolCostPrice = new BigDecimal(pool.getPrice());
                                    posSale.setCostPrice(poolCostPrice);
                                    posSale.setMargin(BigDecimal.valueOf(price).subtract(poolCostPrice));
                                } catch (NumberFormatException e) {
                                    posSale.setCostPrice(BigDecimal.ZERO);
                                }
                            }
                            
                            // Set bundle info if available - use pool name as bundle name
                            posSale.setBundleName(pool.getName());
                            posSale.setBundleId(poolId);
                            
                            // Set operator from networkProvider if available
                            if (pool.getNetworkProvider() != null && !pool.getNetworkProvider().isEmpty()) {
                                posSale.setOperator(pool.getNetworkProvider());
                                System.out.println("   📡 Network Provider set: " + pool.getNetworkProvider());
                            } else {
                                System.out.println("   ⚠️ Network Provider not set in pool");
                            }
                            
                            savedPosSale = esimPosSaleRepository.save(posSale);
                            System.out.println("✅ EsimPosSale saved to esim_pos_sales collection with ID: " + savedPosSale.getId());
                            System.out.println("   📊 Customer: " + customerName + " (" + customerEmail + ")");
                            System.out.println("   📊 ICCID: " + iccid);
                            System.out.println("   📊 Sale Price: NOK " + savedPosSale.getSalePrice());
                            System.out.println("   📊 Retailer: " + retailerEmail);
                            System.out.println("   📊 Operator: " + savedPosSale.getOperator());
                        } catch (Exception posSaleEx) {
                            System.err.println("⚠️ Error saving to esim_pos_sales collection: " + posSaleEx.getMessage());
                            posSaleEx.printStackTrace();
                        }
                        // ========== END SAVE TO esim_pos_sales ==========
                        
                        // ========== DEDUCT CREDIT BASED ON PAYMENT MODE ==========
                        if (savedPosSale != null && savedPosSale.getSalePrice() != null) {
                            System.out.println("\n=== STARTING CREDIT DEDUCTION (from esim_pos_sales) ===");
                            System.out.println("📊 POS Sale ID: " + savedPosSale.getId());
                            System.out.println("📊 Retailer ID: " + retailer.getId());
                            System.out.println("📊 Sale Price from POS Sale: " + savedPosSale.getSalePrice());
                            System.out.println("📊 Payment Mode: " + paymentMode);
                            
                            BigDecimal salePriceFromPOS = savedPosSale.getSalePrice();
                            
                            try {
                                if ("kickback".equalsIgnoreCase(paymentMode)) {
                                    // ========== DEDUCT FROM KICKBACK BONUS ==========
                                    System.out.println("💰 Deducting from KICKBACK BONUS");
                                    var kickbackLimitOpt = retailerKickbackLimitRepository.findByRetailerId(retailer.getId());
                                    
                                    if (kickbackLimitOpt.isPresent()) {
                                        var kickbackLimit = kickbackLimitOpt.get();
                                        System.out.println("📊 BEFORE - Kickback Available: " + kickbackLimit.getAvailableKickback());
                                        System.out.println("📊 BEFORE - Kickback Used: " + kickbackLimit.getUsedKickback());
                                        
                                        // Use kickback for eSIM sale
                                        kickbackLimit.useKickback(salePriceFromPOS);
                                        
                                        System.out.println("📊 AFTER useKickback() - Kickback Available: " + kickbackLimit.getAvailableKickback());
                                        System.out.println("📊 AFTER useKickback() - Kickback Used: " + kickbackLimit.getUsedKickback());
                                        
                                        var savedKickback = retailerKickbackLimitRepository.save(kickbackLimit);
                                        System.out.println("✅ KICKBACK DEDUCTED for eSIM Sale: " + salePriceFromPOS);
                                        System.out.println("📊 SAVED - Kickback Available: " + savedKickback.getAvailableKickback());
                                        System.out.println("📊 SAVED - Kickback Used: " + savedKickback.getUsedKickback());
                                        
                                        // Update the POS sale with kickback deduction info
                                        savedPosSale.setNotes("Kickback deducted: " + salePriceFromPOS + " NOK");
                                        esimPosSaleRepository.save(savedPosSale);
                                        
                                        order.setNotes(order.getNotes() + " | Kickback Bonus Updated from POS Sale");
                                    } else {
                                        System.err.println("⚠️ No Kickback Limit record found for retailer: " + retailer.getId());
                                    }
                                } else {
                                    // ========== DEDUCT FROM UNIFIED CREDIT (DEFAULT) ==========
                                    System.out.println("💳 Deducting from UNIFIED CREDIT");
                                    var limitOpt = retailerLimitRepository.findByRetailer_Id(retailer.getId());
                                    
                                    if (limitOpt.isPresent()) {
                                        RetailerLimit limit = limitOpt.get();
                                        System.out.println("📊 BEFORE - Credit Limit: " + limit.getCreditLimit());
                                        System.out.println("📊 BEFORE - Available Credit: " + limit.getAvailableCredit());
                                        System.out.println("📊 BEFORE - Used Credit: " + limit.getUsedCredit());
                                        
                                        // Deduct from unified credit limit (used for both eSIM and ePIN)
                                        limit.useCredit(salePriceFromPOS, savedPosSale.getId(), 
                                            "eSIM POS Sale: " + pool.getName() + " to " + customerEmail);
                                        
                                        System.out.println("📊 AFTER useCredit() - Available: " + limit.getAvailableCredit());
                                        System.out.println("📊 AFTER useCredit() - Used: " + limit.getUsedCredit());
                                        
                                        RetailerLimit savedLimit = retailerLimitRepository.save(limit);
                                        System.out.println("✅ UNIFIED CREDIT DEDUCTED using POS Sale price: " + salePriceFromPOS);
                                        System.out.println("📊 SAVED - Available Credit: " + savedLimit.getAvailableCredit());
                                        System.out.println("📊 SAVED - Used Credit: " + savedLimit.getUsedCredit());
                                        System.out.println("=== UNIFIED CREDIT DEDUCTION COMPLETE ===\n");
                                        
                                        // Update the POS sale with credit deduction info
                                        savedPosSale.setNotes("Credit deducted: " + salePriceFromPOS + " NOK");
                                        esimPosSaleRepository.save(savedPosSale);
                                        
                                        // Store updated credit info for response
                                        order.setNotes(order.getNotes() + " | Unified Credit Updated from POS Sale");
                                    } else {
                                        System.err.println("⚠️ No Credit Limit record found for retailer: " + retailer.getId());
                                        System.err.println("⚠️ Admin needs to set credit limit for this retailer first");
                                    }
                                }
                            } catch (Exception creditEx) {
                                System.err.println("❌ CRITICAL: Error deducting credit: " + creditEx.getMessage());
                                creditEx.printStackTrace();
                            }
                        } else {
                            System.err.println("⚠️ No POS Sale saved - skipping credit deduction");
                        }
                        // ========== END CREDIT DEDUCTION ==========
                        
                        // Record profit/earnings for this sale
                        try {
                            BigDecimal saleAmount = savedPosSale != null ? savedPosSale.getSalePrice() : BigDecimal.valueOf(price);
                            // For eSIMs, cost price is typically the wholesale price
                            // Assuming pool.getPrice() is the cost price, or use a default margin
                            BigDecimal costPrice = BigDecimal.ZERO;
                            if (pool.getPrice() != null && !pool.getPrice().isEmpty()) {
                                try {
                                    costPrice = new BigDecimal(pool.getPrice());
                                } catch (NumberFormatException e) {
                                    System.err.println("⚠️ Invalid price format in pool: " + pool.getPrice());
                                    costPrice = BigDecimal.ZERO;
                                }
                            }
                            
                            String bundleName = pool.getName();
                            String bundleId = poolId;
                            Double marginRate = 0.0; // Can be configured or passed from frontend
                            
                            // If cost price is same as sale price, assume 0 margin (retail = wholesale)
                            if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
                                marginRate = ((saleAmount.subtract(costPrice)).divide(costPrice, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))).doubleValue();
                            }
                            
                            System.out.println("📊 Recording profit - Sale: " + saleAmount + ", Cost: " + costPrice + ", Margin: " + marginRate + "%");
                            retailerService.recordProfit(retailer, saleAmount, costPrice, bundleName, bundleId, marginRate);
                            System.out.println("✅ Profit/earnings recorded in retailer_profits collection");
                        } catch (Exception profitEx) {
                            System.err.println("⚠️ Error recording profit: " + profitEx.getMessage());
                            profitEx.printStackTrace();
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Error recording eSIM sale: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                System.err.println("❌ No retailer email found - cannot record sale");
            }
            
            System.out.println("\n✅ eSIM QR code sent and sale recorded successfully\n");
            
            // Build response with updated eSIM credit information
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "eSIM QR code sent to " + customerEmail);
            response.put("customerId", itemId);
            response.put("iccid", iccid);
            response.put("salePrice", price);
            
            // Include updated unified credit balance in response for immediate UI update
            // Use retailerEmail variable instead of authentication.getName()
            if (retailerEmail != null) {
                try {
                    var retailerOpt = userRepository.findByEmail(retailerEmail);
                    if (retailerOpt.isPresent()) {
                        var limitOpt = retailerLimitRepository.findByRetailer_Id(retailerOpt.get().getId());
                        if (limitOpt.isPresent()) {
                            RetailerLimit limit = limitOpt.get();
                            Map<String, Object> creditInfo = new HashMap<>();
                            creditInfo.put("creditLimit", limit.getCreditLimit() != null ? limit.getCreditLimit().doubleValue() : 0.0);
                            creditInfo.put("availableCredit", limit.getAvailableCredit() != null ? limit.getAvailableCredit().doubleValue() : 0.0);
                            creditInfo.put("usedCredit", limit.getUsedCredit() != null ? limit.getUsedCredit().doubleValue() : 0.0);
                            
                            // Calculate usage percentage
                            if (limit.getCreditLimit() != null && limit.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                                double usagePercentage = limit.getUsedCredit()
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(limit.getCreditLimit(), 2, java.math.RoundingMode.HALF_UP)
                                    .doubleValue();
                                creditInfo.put("creditUsagePercentage", usagePercentage);
                            } else {
                                creditInfo.put("creditUsagePercentage", 0.0);
                            }
                            
                            response.put("updatedCredit", creditInfo);
                            System.out.println("📊 Response includes updated unified credit: " + creditInfo);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Could not include updated credit in response: " + ex.getMessage());
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Validation error: " + e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Invalid request");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            System.err.println("❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to send eSIM QR code");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // POS Sale endpoint - Sell eSIM from app and record transaction
    @PostMapping("/esims/pos-sale")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Map<String, Object>> processPOSSale(
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        
        System.out.println("\n🛒 ===== POS Sale Request Received =====");
        System.out.println("Request data: " + requestData);
        String retailerEmail = authentication != null ? authentication.getName() : null;
        if (retailerEmail == null) {
            System.out.println("⚠️ Authentication is null - falling back to any BUSINESS user for POS sale");
            List<User> businessUsers = userRepository.findByAccountType(User.AccountType.BUSINESS);
            if (!businessUsers.isEmpty()) {
                retailerEmail = businessUsers.get(0).getEmail();
                System.out.println("👉 Using fallback BUSINESS user: " + retailerEmail);
            } else {
                retailerEmail = "pos-app@local";
                System.out.println("👉 Using POS app fallback email: " + retailerEmail);
            }
        }
        
        try {
            // Extract request parameters
            String poolId = (String) requestData.get("poolId");
            String itemId = (String) requestData.get("itemId");
            String iccid = (String) requestData.get("iccid");
            Double price = requestData.get("price") != null ? 
                ((Number) requestData.get("price")).doubleValue() : 0.0;
            String productId = (String) requestData.get("productId");
            String productType = (String) requestData.get("productType");
            String networkProvider = (String) requestData.get("networkProvider");
            
            // Validate required fields
            if (poolId == null || itemId == null || iccid == null) {
                throw new IllegalArgumentException("Missing required fields: poolId, itemId, or iccid");
            }
            
            System.out.println("📋 Sale Details:");
            System.out.println("   - Pool ID: " + poolId);
            System.out.println("   - Item ID: " + itemId);
            System.out.println("   - ICCID: " + iccid);
            System.out.println("   - Price: " + price);
            System.out.println("   - Product ID: " + productId);
            System.out.println("   - Network Provider: " + networkProvider);
            
            // Get the stock pool
            StockPool pool = stockService.getStockPoolById(poolId);
            if (pool == null) {
                throw new IllegalArgumentException("Stock pool not found");
            }
            
            // Find the specific item
            StockPool.StockItem item = null;
            for (StockPool.StockItem stockItem : pool.getItems()) {
                if (stockItem.getItemId().equals(itemId)) {
                    item = stockItem;
                    break;
                }
            }
            
            if (item == null) {
                throw new IllegalArgumentException("eSIM item not found in stock pool");
            }
            
            // Check if item is available
            if (item.getStatus() != StockPool.StockItem.ItemStatus.AVAILABLE) {
                throw new IllegalStateException("eSIM item is not available for sale");
            }
            
            System.out.println("✅ eSIM item found and available");
            
            // Mark item as used (sold) in existing status model
            item.setStatus(StockPool.StockItem.ItemStatus.USED);
            item.setUsedDate(java.time.LocalDateTime.now());
            item.setAssignedToUserEmail(retailerEmail);
            
            // Update pool quantities
            pool.setAvailableQuantity(pool.getAvailableQuantity() - 1);
            pool.setUsedQuantity(pool.getUsedQuantity() + 1);
            
            // Save updated pool
            stockPoolRepository.save(pool);
            System.out.println("✅ Stock updated - Available: " + pool.getAvailableQuantity());
            
            // Create POS sale record
            EsimPosSale posSale = new EsimPosSale();
            posSale.setPoolId(poolId);
            posSale.setItemId(itemId);
            posSale.setIccid(iccid);
            posSale.setProductId(productId);
            posSale.setProductType(productType);
            posSale.setNetworkProvider(networkProvider);
            posSale.setPrice(price);
            posSale.setPosType("APP"); // Distinguish app POS from website POS
            posSale.setDeliveryMethod("print"); // App POS uses print/display
            posSale.setStockPoolId(poolId);
            posSale.setStockPoolName(pool.getName());
            posSale.setProductName(pool.getName());
            posSale.setOperator(networkProvider);
            posSale.setSalePrice(BigDecimal.valueOf(price));
            posSale.setCreatedBy(retailerEmail);
            posSale.setStatus(EsimPosSale.SaleStatus.COMPLETED);
            
            // Decrypt and save QR code to POS sale
            if (item.getQrCodeImage() != null && !item.getQrCodeImage().isEmpty()) {
                try {
                    String decryptedQr = stockService.decryptData(item.getQrCodeImage());
                    String qrDataUrl = "data:image/png;base64," + decryptedQr;
                    posSale.setQrCodeUrl(qrDataUrl);
                    System.out.println("   📸 QR Code saved to POS sale (APP) - length: " + qrDataUrl.length() + " chars");
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to decrypt/save QR code: " + e.getMessage());
                }
            }
            
            // Get user details for retailer info
            User retailer = userRepository.findByEmail(retailerEmail).orElse(null);
            if (retailer != null) {
                posSale.setRetailer(retailer);
            } else {
                posSale.setRetailerId(retailerEmail);
                posSale.setRetailerEmail(retailerEmail);
            }
            
            // Save sale record
            EsimPosSale savedPosSale = esimPosSaleRepository.save(posSale);
            System.out.println("✅ POS sale recorded: " + savedPosSale.getId());
            
            // **CRITICAL ADDITION: Process credit limit and kickback like website POS**
            if (retailer != null) {
                try {
                    System.out.println("🔄 Processing credit limit and kickback for POS sale...");
                    
                    // 1. DEDUCT FROM CREDIT LIMIT (like website POS does)
                    try {
                        retailerLimitService.useCredit(retailer.getId(), BigDecimal.valueOf(price), 
                            savedPosSale.getId(), "POS App Sale - " + networkProvider + " eSIM");
                        System.out.println("✅ Credit limit updated - Amount deducted: " + price);
                    } catch (Exception creditEx) {
                        System.err.println("⚠️ Credit limit update failed: " + creditEx.getMessage());
                        // Continue processing even if credit limit update fails
                    }
                    
                    // 2. PROCESS KICKBACK CAMPAIGNS (like website POS does)
                    try {
                        String retailerName = retailer.getFirstName() + " " + retailer.getLastName();
                        kickbackCampaignService.processRetailerSale(
                            retailerEmail, 
                            retailerName, 
                            productId != null ? productId : networkProvider,
                            BigDecimal.valueOf(price)
                        );
                        System.out.println("✅ Kickback campaigns processed for sale");
                    } catch (Exception kickbackEx) {
                        System.err.println("⚠️ Kickback processing failed: " + kickbackEx.getMessage());
                        // Continue processing even if kickback fails
                    }
                    
                    System.out.println("✅ Credit limit and kickback processing completed");
                } catch (Exception ex) {
                    System.err.println("⚠️ Error in credit/kickback processing: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            
            // Prepare response with decrypted data for receipt
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "eSIM sold successfully");
            response.put("saleId", savedPosSale.getId());
            response.put("iccid", iccid);
            response.put("networkProvider", networkProvider);
            response.put("productType", productType);
            response.put("price", price);
            
            // Include updated credit information (like website POS)
            if (retailer != null) {
                try {
                    RetailerLimit updatedLimit = retailerLimitRepository.findByRetailer(retailer).orElse(null);
                    if (updatedLimit != null) {
                        Map<String, Object> creditInfo = new HashMap<>();
                        creditInfo.put("availableCredit", updatedLimit.getAvailableCredit());
                        creditInfo.put("usedCredit", updatedLimit.getUsedCredit());
                        creditInfo.put("creditLimit", updatedLimit.getCreditLimit());
                        
                        // Calculate usage percentage
                        double usagePercentage = 0.0;
                        if (updatedLimit.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                            usagePercentage = updatedLimit.getUsedCredit()
                                .divide(updatedLimit.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).doubleValue();
                        }
                        creditInfo.put("creditUsagePercentage", usagePercentage);
                        
                        response.put("updatedCredit", creditInfo);
                        System.out.println("📊 Updated credit info included in response");
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Could not include updated credit info: " + ex.getMessage());
                }
            }
            
            // Decrypt and include QR code for receipt
            if (item.getQrCodeImage() != null && !item.getQrCodeImage().isEmpty()) {
                String decryptedQr = stockService.decryptData(item.getQrCodeImage());
                response.put("qrCodeImage", decryptedQr);
            }
            
            System.out.println("✅ POS sale completed successfully");
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Validation error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            
        } catch (Exception e) {
            System.err.println("❌ Error processing POS sale: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to process sale: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // Send eSIM QR code via email - New endpoint for POS app (includes full sale transaction)
    @PostMapping("/esims/send-via-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Map<String, Object>> sendEsimViaEmail(
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        
        System.out.println("\n📧 ===== Send eSIM via Email Request Received =====");
        System.out.println("Request data: " + requestData);
        
        String retailerEmail = authentication != null ? authentication.getName() : null;
        if (retailerEmail == null) {
            System.out.println("⚠️ Authentication is null - falling back to any BUSINESS user for email sale");
            List<User> businessUsers = userRepository.findByAccountType(User.AccountType.BUSINESS);
            if (!businessUsers.isEmpty()) {
                retailerEmail = businessUsers.get(0).getEmail();
                System.out.println("👉 Using fallback BUSINESS user: " + retailerEmail);
            } else {
                retailerEmail = "pos-app@local";
                System.out.println("👉 Using POS app fallback email: " + retailerEmail);
            }
        }
        
        try {
            // Extract request parameters
            String poolId = (String) requestData.get("poolId");
            String itemId = (String) requestData.get("itemId");
            String customerFirstName = (String) requestData.get("firstName");
            String customerLastName = (String) requestData.get("lastName");
            String customerEmail = (String) requestData.get("email");
            String networkProvider = (String) requestData.get("networkProvider");
            String productType = (String) requestData.get("productType");
            Double price = requestData.get("price") != null ? 
                ((Number) requestData.get("price")).doubleValue() : 0.0;
            String productId = (String) requestData.get("productId");
            String iccid = (String) requestData.get("iccid");
            
            // Validate required fields
            if (poolId == null || itemId == null || customerEmail == null || 
                customerFirstName == null || customerLastName == null) {
                throw new IllegalArgumentException("Missing required fields");
            }
            
            System.out.println("📋 Email Sale Details:");
            System.out.println("   - Pool ID: " + poolId);
            System.out.println("   - Item ID: " + itemId);
            System.out.println("   - Customer: " + customerFirstName + " " + customerLastName);
            System.out.println("   - Email: " + customerEmail);
            System.out.println("   - Price: " + price);
            System.out.println("   - Retailer: " + retailerEmail);
            
            // Get the stock pool
            StockPool pool = stockService.getStockPoolById(poolId);
            if (pool == null) {
                throw new IllegalArgumentException("Stock pool not found");
            }
            
            // Find the specific item
            StockPool.StockItem item = null;
            for (StockPool.StockItem stockItem : pool.getItems()) {
                if (stockItem.getItemId().equals(itemId)) {
                    item = stockItem;
                    break;
                }
            }
            
            if (item == null) {
                throw new IllegalArgumentException("eSIM item not found in stock pool");
            }
            
            // Check if item is available
            if (item.getStatus() != StockPool.StockItem.ItemStatus.AVAILABLE) {
                throw new IllegalStateException("eSIM item is not available");
            }
            
            System.out.println("✅ eSIM item found and available");
            
            // Get QR code image
            if (item.getQrCodeImage() == null || item.getQrCodeImage().isEmpty()) {
                throw new IllegalStateException("QR code not available for this eSIM");
            }
            
            System.out.println("📸 QR Code data - Encrypted length: " + item.getQrCodeImage().length());
            
            // Decrypt QR code early to validate
            String decryptedQr = stockService.decryptData(item.getQrCodeImage());
            System.out.println("📸 QR Code data - Decrypted length: " + decryptedQr.length());
            System.out.println("📸 QR Code data - Starts with PNG signature: " + decryptedQr.startsWith("iVBORw0KGgo"));
            
            // **STEP 1: PROCESS SALE TRANSACTION (same as pos-sale endpoint)**
            System.out.println("🔄 Processing sale transaction...");
            
            // Mark item as used (sold)
            item.setStatus(StockPool.StockItem.ItemStatus.USED);
            item.setUsedDate(java.time.LocalDateTime.now());
            item.setAssignedToUserEmail(retailerEmail);
            
            // Update pool quantities
            pool.setAvailableQuantity(pool.getAvailableQuantity() - 1);
            pool.setUsedQuantity(pool.getUsedQuantity() + 1);
            
            // Save updated pool
            stockPoolRepository.save(pool);
            System.out.println("✅ Stock updated - Available: " + pool.getAvailableQuantity());
            
            // Create POS sale record
            EsimPosSale posSale = new EsimPosSale();
            posSale.setPoolId(poolId);
            posSale.setItemId(itemId);
            posSale.setIccid(iccid != null ? iccid : item.getSerialNumber());
            posSale.setProductId(productId);
            posSale.setProductType(productType);
            posSale.setNetworkProvider(networkProvider);
            posSale.setPrice(price);
            posSale.setPosType("APP"); 
            posSale.setDeliveryMethod("email"); // Via email delivery
            posSale.setStockPoolId(poolId);
            posSale.setStockPoolName(pool.getName());
            posSale.setProductName(pool.getName());
            posSale.setOperator(networkProvider);
            posSale.setSalePrice(BigDecimal.valueOf(price));
            posSale.setCreatedBy(retailerEmail);
            posSale.setStatus(EsimPosSale.SaleStatus.COMPLETED);
            posSale.setCustomerEmail(customerEmail);
            posSale.setCustomerName(customerFirstName + " " + customerLastName);
            
            // Save QR code to POS sale (already decrypted earlier)
            String qrDataUrl = "data:image/png;base64," + decryptedQr;
            posSale.setQrCodeUrl(qrDataUrl);
            System.out.println("   📸 QR Code saved to POS sale (EMAIL) - length: " + qrDataUrl.length() + " chars");
            
            // Get user details for retailer info
            User retailer = userRepository.findByEmail(retailerEmail).orElse(null);
            if (retailer != null) {
                posSale.setRetailer(retailer);
            } else {
                posSale.setRetailerId(retailerEmail);
                posSale.setRetailerEmail(retailerEmail);
            }
            
            // Save sale record
            EsimPosSale savedPosSale = esimPosSaleRepository.save(posSale);
            System.out.println("✅ POS sale recorded: " + savedPosSale.getId());
            
            // **STEP 2: PROCESS CREDIT LIMIT AND KICKBACK**
            if (retailer != null) {
                try {
                    System.out.println("🔄 Processing credit limit and kickback for email sale...");
                    
                    // Deduct from credit limit
                    try {
                        retailerLimitService.useCredit(retailer.getId(), BigDecimal.valueOf(price), 
                            savedPosSale.getId(), "POS App Email Sale - " + networkProvider + " eSIM");
                        System.out.println("✅ Credit limit updated - Amount deducted: " + price);
                    } catch (Exception creditEx) {
                        System.err.println("⚠️ Credit limit update failed: " + creditEx.getMessage());
                    }
                    
                    // Process kickback campaigns
                    try {
                        String retailerName = retailer.getFirstName() + " " + retailer.getLastName();
                        kickbackCampaignService.processRetailerSale(
                            retailerEmail, 
                            retailerName,
                            savedPosSale.getId(),
                            BigDecimal.valueOf(price)
                        );
                        System.out.println("✅ Kickback campaigns processed");
                    } catch (Exception kickbackEx) {
                        System.err.println("⚠️ Kickback processing failed: " + kickbackEx.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Credit/Kickback processing error: " + e.getMessage());
                }
            }
            
            // **STEP 3: SEND EMAIL WITH QR CODE**
            System.out.println("📧 Sending eSIM QR code via email...");
            // Use the actual ICCID from the item's serial number or itemData
            String actualIccid = iccid != null ? iccid : 
                                (item.getSerialNumber() != null ? item.getSerialNumber() : 
                                (item.getItemData() != null ? stockService.decryptData(item.getItemData()) : "N/A"));
            
            System.out.println("   📋 Email data - ICCID: " + actualIccid);
            System.out.println("   📋 Email data - QR length: " + decryptedQr.length());
            
            emailService.sendEsimQrCodeEmail(
                customerEmail, 
                customerFirstName, 
                customerLastName,
                networkProvider != null ? networkProvider : pool.getNetworkProvider(),
                "eSIM", // Always use "eSIM" as the product type for display
                decryptedQr,
                actualIccid
            );
            
            System.out.println("✅ eSIM sold and QR code sent via email successfully");
            
            // Build response with all sale details
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "eSIM sold and sent to " + customerEmail + " successfully");
            response.put("saleId", savedPosSale.getId());
            response.put("customerEmail", customerEmail);
            response.put("iccid", iccid != null ? iccid : item.getSerialNumber());
            response.put("networkProvider", networkProvider);
            response.put("productType", productType);
            response.put("price", price);
            response.put("qrCodeImage", decryptedQr); // Include QR code for receipt screen
            
            // Include updated credit information
            if (retailer != null) {
                try {
                    RetailerLimit updatedLimit = retailerLimitRepository.findByRetailer(retailer).orElse(null);
                    if (updatedLimit != null) {
                        Map<String, Object> creditInfo = new HashMap<>();
                        creditInfo.put("availableCredit", updatedLimit.getAvailableCredit());
                        creditInfo.put("usedCredit", updatedLimit.getUsedCredit());
                        creditInfo.put("creditLimit", updatedLimit.getCreditLimit());
                        
                        double usagePercentage = 0.0;
                        if (updatedLimit.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                            usagePercentage = updatedLimit.getUsedCredit()
                                .divide(updatedLimit.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).doubleValue();
                        }
                        creditInfo.put("creditUsagePercentage", usagePercentage);
                        
                        response.put("updatedCredit", creditInfo);
                        System.out.println("📊 Updated credit info included in response");
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Could not include updated credit info: " + ex.getMessage());
                }
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Validation error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            
        } catch (Exception e) {
            System.err.println("❌ Error sending eSIM via email: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to send eSIM via email: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ePIN POS Sale endpoint - Sell ePIN from app and record transaction
    @PostMapping("/pins/pos-sale")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<Map<String, Object>> processEpinPOSSale(
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        
        System.out.println("\n🏷️ ===== ePIN POS Sale Request Received =====");
        System.out.println("Request data: " + requestData);
        String retailerEmail = authentication != null ? authentication.getName() : null;
        if (retailerEmail == null) {
            System.out.println("⚠️ Authentication is null - falling back to any BUSINESS user for ePIN POS sale");
            List<User> businessUsers = userRepository.findByAccountType(User.AccountType.BUSINESS);
            if (!businessUsers.isEmpty()) {
                retailerEmail = businessUsers.get(0).getEmail();
                System.out.println("👉 Using fallback BUSINESS user: " + retailerEmail);
            } else {
                retailerEmail = "pos-app@local";
                System.out.println("👉 Using POS app fallback email: " + retailerEmail);
            }
        }
        
        try {
            // Extract request parameters
            String productId = (String) requestData.get("productId");
            String pinNumber = (String) requestData.get("pinNumber");
            String serialNumber = (String) requestData.get("serialNumber");
            Double price = requestData.get("price") != null ? 
                ((Number) requestData.get("price")).doubleValue() : 0.0;
            String productType = (String) requestData.get("productType");
            String networkProvider = (String) requestData.get("networkProvider");
            
            // Validate required fields
            if (productId == null || pinNumber == null || serialNumber == null) {
                throw new IllegalArgumentException("Missing required fields: productId, pinNumber, or serialNumber");
            }
            
            System.out.println("📋 ePIN Sale Details:");
            System.out.println("   - Stock Pool ID: " + productId);
            System.out.println("   - PIN Number: " + pinNumber.substring(0, Math.min(4, pinNumber.length())) + "***");
            System.out.println("   - Serial Number: " + serialNumber);
            System.out.println("   - Price: " + price);
            System.out.println("   - Network Provider: " + networkProvider);
            
            // Get the stock pool (NOT product table)
            StockPool stockPool = stockPoolRepository.findById(productId).orElse(null);
            if (stockPool == null || stockPool.getStockType() != StockPool.StockType.EPIN) {
                throw new IllegalArgumentException("ePIN stock pool not found");
            }
            
            System.out.println("📦 Stock pool found: " + stockPool.getName());
            System.out.println("   Available quantity: " + stockPool.getAvailableQuantity());
            
            // Find and mark the specific PIN as used in stock pool
            List<StockPool.StockItem> items = stockPool.getItems();
            StockPool.StockItem targetItem = null;
            
            if (items != null) {
                for (StockPool.StockItem item : items) {
                    // Decrypt the PIN to compare
                    String decryptedPin = stockService.decryptData(item.getItemData());
                    if (decryptedPin.equals(pinNumber) && item.getStatus() == StockPool.StockItem.ItemStatus.AVAILABLE) {
                        targetItem = item;
                        break;
                    }
                }
            }
            
            if (targetItem == null) {
                throw new IllegalStateException("ePIN not available for sale (already sold or not found)");
            }
            
            System.out.println("✅ ePIN found and available in stock pool");
            System.out.println("   Serial Number: " + targetItem.getSerialNumber());
            
            // Store the actual serial number for later use in order
            String actualSerialNumber = targetItem.getSerialNumber();
            
            // Mark PIN as USED (sold)
            targetItem.setStatus(StockPool.StockItem.ItemStatus.USED);
            targetItem.setAssignedToOrderId("POS-" + System.currentTimeMillis());
            targetItem.setAssignedToUserEmail(retailerEmail);
            targetItem.setUsedDate(java.time.LocalDateTime.now());
            
            // Update stock pool quantities
            stockPool.setAvailableQuantity(stockPool.getAvailableQuantity() - 1);
            Integer currentUsed = stockPool.getUsedQuantity();
            stockPool.setUsedQuantity((currentUsed != null ? currentUsed : 0) + 1);
            
            // Save updated stock pool
            stockPoolRepository.save(stockPool);
            System.out.println("✅ ePIN marked as SOLD and stock pool updated");
            
            // Get user details for retailer info  
            User retailer = userRepository.findByEmail(retailerEmail).orElse(null);
            
            // **CRITICAL: Process credit limit and kickback like eSIM POS**
            if (retailer != null) {
                try {
                    System.out.println("🔄 Processing credit limit and kickback for ePIN POS sale...");
                    
                    // 1. DEDUCT FROM CREDIT LIMIT
                    try {
                        retailerLimitService.useCredit(retailer.getId(), BigDecimal.valueOf(price), 
                            productId, "POS App ePIN Sale - " + networkProvider + " PIN");
                        System.out.println("✅ Credit limit updated - Amount deducted: " + price);
                    } catch (Exception creditEx) {
                        System.err.println("⚠️ Credit limit update failed: " + creditEx.getMessage());
                    }
                    
                    // 2. PROCESS KICKBACK CAMPAIGNS
                    try {
                        String retailerName = retailer.getFirstName() + " " + retailer.getLastName();
                        kickbackCampaignService.processRetailerSale(
                            retailerEmail, 
                            retailerName, 
                            productId,
                            BigDecimal.valueOf(price)
                        );
                        System.out.println("✅ Kickback campaigns processed for ePIN sale");
                    } catch (Exception kickbackEx) {
                        System.err.println("⚠️ Kickback processing failed: " + kickbackEx.getMessage());
                    }
                    
                    System.out.println("✅ Credit limit and kickback processing completed");
                } catch (Exception ex) {
                    System.err.println("⚠️ Error in credit/kickback processing: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            
            // **NEW: Create RetailerOrder and record profit (match eSIM implementation)**
            try {
                System.out.println("📝 Creating RetailerOrder and recording profit...");
                
                // 1. CREATE RETAILER ORDER
                RetailerOrder order = new RetailerOrder();
                order.setRetailerId(retailer.getId());
                order.setOrderNumber("ePIN-POS-" + System.currentTimeMillis());
                order.setNotes("POS App ePIN Sale - " + (networkProvider != null ? networkProvider : stockPool.getNetworkProvider()));
                
                // Create OrderItem for the ePIN
                RetailerOrder.OrderItem item = new RetailerOrder.OrderItem();
                item.setProductId(productId);
                item.setProductName(stockPool.getName());
                item.setProductType("EPIN");
                item.setCategory("EPIN");
                item.setQuantity(1);
                item.setUnitPrice(BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice())));
                item.setRetailPrice(BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice())));
                
                // Store both the PIN (for POS app display) and serial number (for analytics)
                // PINs are stored separately for display, serial number is for tracking
                List<String> itemSerials = new ArrayList<>();
                itemSerials.add(pinNumber); // First element is the PIN for POS app compatibility
                if (actualSerialNumber != null && !actualSerialNumber.isEmpty()) {
                    itemSerials.add(actualSerialNumber); // Second element is the actual serial number
                }
                item.setSerialNumbers(itemSerials);
                
                // Set network provider from pool
                if (stockPool.getNetworkProvider() != null && !stockPool.getNetworkProvider().isEmpty()) {
                    item.setNetworkProvider(stockPool.getNetworkProvider());
                    System.out.println("   📡 OrderItem Network Provider set: " + stockPool.getNetworkProvider());
                }
                
                order.addItem(item);
                order.setTotalAmount(BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice())));
                order.setCurrency("NOK");
                order.setStatus(RetailerOrder.OrderStatus.COMPLETED);
                order.setPaymentStatus(RetailerOrder.PaymentStatus.COMPLETED);
                order.setPaymentMethod("POINT_OF_SALE");
                order.setCreatedDate(java.time.LocalDateTime.now());
                order.setLastModifiedDate(java.time.LocalDateTime.now());
                order.setCreatedBy(retailerEmail);
                
                RetailerOrder savedOrder = retailerOrderRepository.save(order);
                System.out.println("✅ RetailerOrder created: " + savedOrder.getOrderNumber());
                
                // 2. CREATE ESIM POS SALE RECORD (reuse for ePIN tracking)
                EsimPosSale posSale = new EsimPosSale();
                posSale.setRetailer(retailer);
                posSale.setRetailerId(retailer.getId());
                posSale.setRetailerEmail(retailerEmail);
                posSale.setRetailerName(retailer.getFirstName() + " " + retailer.getLastName());
                posSale.setProductName(stockPool.getName());
                posSale.setProductId(productId);
                posSale.setProductType("EPIN");
                posSale.setBundleName(stockPool.getName());
                posSale.setBundleId(productId);
                posSale.setStockPoolId(productId);
                posSale.setStockPoolName(stockPool.getName());
                posSale.setSalePrice(BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice())));
                posSale.setCurrency("NOK");
                posSale.setOrderId(savedOrder.getId());
                posSale.setOrderReference(savedOrder.getOrderNumber());
                posSale.setStatus(EsimPosSale.SaleStatus.COMPLETED);
                posSale.setPosType("APP"); // Distinguish app POS from website POS
                posSale.setDeliveryMethod("print"); // App POS uses print/display
                posSale.setCreatedBy(retailerEmail);
                
                // Set cost price if available
                if (stockPool.getPrice() != null && !stockPool.getPrice().isEmpty()) {
                    try {
                        BigDecimal poolCostPrice = new BigDecimal(stockPool.getPrice());
                        posSale.setCostPrice(poolCostPrice);
                        BigDecimal salePrice = BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice()));
                        posSale.setMargin(salePrice.subtract(poolCostPrice));
                    } catch (NumberFormatException e) {
                        posSale.setCostPrice(BigDecimal.ZERO);
                    }
                }
                
                // Set operator from networkProvider
                if (stockPool.getNetworkProvider() != null && !stockPool.getNetworkProvider().isEmpty()) {
                    posSale.setOperator(stockPool.getNetworkProvider());
                }
                
                EsimPosSale savedPosSale = esimPosSaleRepository.save(posSale);
                System.out.println("✅ EsimPosSale created for ePIN tracking: " + savedPosSale.getId());
                
                // 3. RECORD PROFIT
                try {
                    BigDecimal saleAmount = BigDecimal.valueOf(price > 0 ? price : Double.parseDouble(stockPool.getPrice()));
                    BigDecimal costPrice = BigDecimal.ZERO;
                    if (stockPool.getPrice() != null && !stockPool.getPrice().isEmpty()) {
                        try {
                            costPrice = new BigDecimal(stockPool.getPrice());
                        } catch (NumberFormatException e) {
                            costPrice = BigDecimal.ZERO;
                        }
                    }
                    
                    String bundleName = stockPool.getName();
                    String bundleId = productId;
                    Double marginRate = 0.0;
                    
                    if (costPrice.compareTo(BigDecimal.ZERO) > 0) {
                        marginRate = ((saleAmount.subtract(costPrice)).divide(costPrice, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))).doubleValue();
                    }
                    
                    System.out.println("📊 Recording profit - Sale: " + saleAmount + ", Cost: " + costPrice + ", Margin: " + marginRate + "%");
                    retailerService.recordProfit(retailer, saleAmount, costPrice, bundleName, bundleId, marginRate);
                    System.out.println("✅ Profit/earnings recorded in retailer_profits collection");
                } catch (Exception profitEx) {
                    System.err.println("⚠️ Error recording profit: " + profitEx.getMessage());
                }
                
                System.out.println("✅ RetailerOrder, POS Sale, and Profit tracking completed");
            } catch (Exception orderEx) {
                System.err.println("⚠️ Error creating order/profit records: " + orderEx.getMessage());
                orderEx.printStackTrace();
            }
            
            // Prepare response with complete receipt information
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ePIN sold successfully");
            
            // Generate unique IDs for tracking
            String timestamp = String.valueOf(System.currentTimeMillis());
            String orderId = "ORD-" + timestamp;
            String transactionId = "TXN-" + timestamp;
            
            response.put("saleId", orderId);
            response.put("orderId", orderId); // Add explicit orderId field
            response.put("transactionId", transactionId);
            
            // PIN Information
            response.put("pinNumber", pinNumber);
            response.put("pinCode", pinNumber); // For display on receipt
            
            // Serial Number - Get from stock item or generate
            String finalSerialNumber = serialNumber.equals("AUTO") || serialNumber == null || serialNumber.isEmpty() 
                ? (targetItem.getSerialNumber() != null && !targetItem.getSerialNumber().isEmpty() 
                    ? targetItem.getSerialNumber() 
                    : "SN-" + timestamp.substring(timestamp.length() - 10))
                : serialNumber;
            response.put("serialNumber", finalSerialNumber);
            
            // Network Provider - Get from stock pool
            String finalNetworkProvider = (networkProvider != null && !networkProvider.equals("AUTO")) 
                ? networkProvider 
                : (stockPool.getNetworkProvider() != null ? stockPool.getNetworkProvider() : "Unknown Provider");
            response.put("networkProvider", finalNetworkProvider);
            
            // Product Information
            response.put("productName", stockPool.getName());
            response.put("productType", stockPool.getProductType() != null ? stockPool.getProductType() : "Bundle plans");
            response.put("productCategory", stockPool.getProductType() != null ? stockPool.getProductType() : "Bundle plans");
            
            // Description - Build comprehensive description
            String description = stockPool.getName();
            if (stockPool.getDescription() != null && !stockPool.getDescription().isEmpty()) {
                description += " - " + stockPool.getDescription();
            }
            response.put("description", description);
            
            // Price
            double finalPrice = price > 0 ? price : Double.parseDouble(stockPool.getPrice());
            response.put("price", finalPrice);
            
            // Additional details
            response.put("validity", "30 days");
            response.put("dataAmount", ""); // Not applicable for ePINs
            
            // Include updated credit information
            if (retailer != null) {
                try {
                    RetailerLimit updatedLimit = retailerLimitRepository.findByRetailer(retailer).orElse(null);
                    if (updatedLimit != null) {
                        Map<String, Object> creditInfo = new HashMap<>();
                        creditInfo.put("availableCredit", updatedLimit.getAvailableCredit());
                        creditInfo.put("usedCredit", updatedLimit.getUsedCredit());
                        creditInfo.put("creditLimit", updatedLimit.getCreditLimit());
                        
                        // Calculate usage percentage
                        double usagePercentage = 0.0;
                        if (updatedLimit.getCreditLimit().compareTo(BigDecimal.ZERO) > 0) {
                            usagePercentage = updatedLimit.getUsedCredit()
                                .divide(updatedLimit.getCreditLimit(), 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).doubleValue();
                        }
                        creditInfo.put("creditUsagePercentage", usagePercentage);
                        
                        response.put("updatedCredit", creditInfo);
                        System.out.println("📊 Updated credit info included in ePIN response");
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Could not include updated credit info: " + ex.getMessage());
                }
            }
            
            // Log complete response for debugging
            System.out.println("\n📋 ===== ePIN POS Sale Response =====");
            System.out.println("✅ Sale ID: " + response.get("saleId"));
            System.out.println("✅ Order ID: " + response.get("orderId"));
            System.out.println("✅ Transaction ID: " + response.get("transactionId"));
            System.out.println("✅ Serial Number: " + response.get("serialNumber"));
            System.out.println("✅ Network Provider: " + response.get("networkProvider"));
            System.out.println("✅ Product Name: " + response.get("productName"));
            System.out.println("✅ Product Type: " + response.get("productType"));
            System.out.println("✅ Product Category: " + response.get("productCategory"));
            System.out.println("✅ Description: " + response.get("description"));
            System.out.println("✅ Price: NOK " + response.get("price"));
            System.out.println("✅ PIN Code: " + pinNumber.substring(0, Math.min(4, pinNumber.length())) + "***");
            System.out.println("=========================================\n");
            
            System.out.println("✅ ePIN POS sale completed successfully");
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            System.err.println("❌ Validation error: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            
        } catch (Exception e) {
            System.err.println("❌ Error processing ePIN POS sale: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to process ePIN sale: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // Get available ePINs with decrypted data for retailer Point of Sale
    @GetMapping("/pins/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableEpinsForSale(
            @RequestParam(required = false) String networkProvider,
            @RequestParam(required = false) String productCategory) {
        try {
            System.out.println("🏷️ Fetching available ePINs for Point of Sale");
            System.out.println("   - Network Provider: " + networkProvider);
            System.out.println("   - Product Category: " + productCategory);
            
            // Get all ePIN stock pools (changed from products to stock_pools)
            List<StockPool> stockPools = stockPoolRepository.findAll();
            System.out.println("📦 Total stock pools found: " + stockPools.size());
            
            // Filter by stockType = EPIN (enum comparison)
            stockPools = stockPools.stream()
                .filter(pool -> pool.getStockType() != null && pool.getStockType() == StockPool.StockType.EPIN)
                .collect(java.util.stream.Collectors.toList());
            System.out.println("📦 After EPIN filter: " + stockPools.size() + " pools");
            
            // Filter by network provider if specified  
            if (networkProvider != null && !networkProvider.isEmpty() && !networkProvider.equals("All Providers")) {
                stockPools = stockPools.stream()
                    .filter(pool -> networkProvider.equals(pool.getNetworkProvider()))
                    .collect(java.util.stream.Collectors.toList());
                System.out.println("📦 After network provider filter: " + stockPools.size() + " pools");
            }
            
            // Filter by category if specified
            if (productCategory != null && !productCategory.isEmpty()) {
                final int beforeCategoryFilter = stockPools.size();
                stockPools = stockPools.stream()
                    .filter(pool -> {
                        boolean matches = productCategory.equals(pool.getProductType());
                        System.out.println("   Pool: " + pool.getName() + " productType='" + pool.getProductType() + "' vs requested='" + productCategory + "' matches=" + matches);
                        return matches;
                    })
                    .collect(java.util.stream.Collectors.toList());
                System.out.println("📦 After category filter: " + stockPools.size() + " pools (from " + beforeCategoryFilter + ")");
            }
            
            List<Map<String, Object>> availableEpins = new ArrayList<>();
            
            for (StockPool pool : stockPools) {
                System.out.println("🔍 Checking pool: " + pool.getName() + " availableQty=" + pool.getAvailableQuantity());
                // Only include pools with available stock
                if (pool.getAvailableQuantity() <= 0) {
                    System.out.println("   ⏭️ Skipping (no available quantity)");
                    continue;
                }
                
                Map<String, Object> epinProduct = new HashMap<>();
                epinProduct.put("id", pool.getId());
                epinProduct.put("name", pool.getName());
                epinProduct.put("productId", pool.getProductId());
                epinProduct.put("networkProvider", pool.getNetworkProvider());
                epinProduct.put("category", pool.getProductType()); // "Topups", "Bundle plans", "Data plans"
                double poolPrice = Double.parseDouble(pool.getPrice());
                epinProduct.put("price", poolPrice);
                epinProduct.put("basePrice", poolPrice); // Add basePrice for Android app
                epinProduct.put("validity", "30 days"); // Default validity
                epinProduct.put("dataAmount", ""); // Not applicable for ePINs
                epinProduct.put("description", pool.getDescription());
                epinProduct.put("stockQuantity", pool.getAvailableQuantity());
                epinProduct.put("soldQuantity", pool.getUsedQuantity());
                epinProduct.put("productType", pool.getProductType()); // Add productType field
                epinProduct.put("status", "ACTIVE"); // Add status field
                epinProduct.put("retailerCommissionPercentage", 30.0); // Default commission
                
                // Get available ePIN items - decrypt for POS display
                List<String> availablePinNumbers = new ArrayList<>();
                if (pool.getItems() != null) {
                    for (StockPool.StockItem item : pool.getItems()) {
                        // Check enum status correctly
                        if (item.getStatus() == StockPool.StockItem.ItemStatus.AVAILABLE) {
                            // Decrypt PIN for POS app to display
                            String decryptedPin = stockService.decryptData(item.getItemData());
                            availablePinNumbers.add(decryptedPin);
                        }
                    }
                }
                
                epinProduct.put("availablePins", availablePinNumbers);
                epinProduct.put("availableCount", availablePinNumbers.size());
                
                availableEpins.add(epinProduct);
            }
            
            System.out.println("✅ Found " + availableEpins.size() + " ePIN products with available stock");
            return ResponseEntity.ok(availableEpins);
            
        } catch (Exception e) {
            System.err.println("❌ Error fetching available ePINs: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Get network providers for ePIN selection
    @GetMapping("/network-providers")
    @PreAuthorize("hasAnyRole('ADMIN', 'RETAILER')")
    public ResponseEntity<List<Map<String, Object>>> getNetworkProviders() {
        System.out.println("🌐 Fetching network providers for ePIN selection");
        
        List<Map<String, Object>> networkProviders = new ArrayList<>();
        
        // Add the 3 network providers
        Map<String, Object> lycamobile = new HashMap<>();
        lycamobile.put("id", "lycamobile");
        lycamobile.put("name", "Lycamobile");
        lycamobile.put("logo", "lycamobile_logo.png");
        lycamobile.put("categories", Arrays.asList("Topups", "Bundle plans", "Data plans"));
        networkProviders.add(lycamobile);
        
        Map<String, Object> tellenor = new HashMap<>();
        tellenor.put("id", "tellenor");
        tellenor.put("name", "Tellenor");
        tellenor.put("logo", "tellenor_logo.png");
        tellenor.put("categories", Arrays.asList("Topups", "Bundle plans", "Data plans"));
        networkProviders.add(tellenor);
        
        Map<String, Object> telia = new HashMap<>();
        telia.put("id", "telia");
        telia.put("name", "Telia");
        telia.put("logo", "telia_logo.png");
        telia.put("categories", Arrays.asList("Topups", "Bundle plans", "Data plans"));
        networkProviders.add(telia);
        
        System.out.println("✅ Returning " + networkProviders.size() + " network providers");
        return ResponseEntity.ok(networkProviders);
    }
}
