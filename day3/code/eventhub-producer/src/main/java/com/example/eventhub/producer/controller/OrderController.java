package com.example.eventhub.producer.controller;

import com.example.eventhub.producer.model.OrderEvent;
import com.example.eventhub.producer.service.OrderProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Order API Controller
 * 
 * REST endpoints for sending orders to Event Hub
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderProducerService producerService;
    
    /**
     * Send a single order
     * 
     * POST /api/orders/send
     * {
     *   "orderId": "ORDER-001",
     *   "customerId": "CUST-100",
     *   "productId": "PROD-50",
     *   "quantity": 2,
     *   "totalAmount": 99.99,
     *   "status": "PENDING"
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendOrder(@RequestBody OrderEvent order) {
        log.info("Received request to send order: {}", order.getOrderId());
        
        try {
            producerService.sendOrder(order);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "sent");
            response.put("orderId", order.getOrderId());
            response.put("eventId", order.getEventId());
            response.put("customerId", order.getCustomerId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send order", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "failed");
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Send a batch of sample orders
     * 
     * POST /api/orders/send-batch?size=100
     */
    @PostMapping("/send-batch")
    public ResponseEntity<Map<String, Object>> sendBatch(
        @RequestParam(defaultValue = "10") int size) {
        
        log.info("Received request to send batch of {} orders", size);
        
        if (size > 10000) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "failed");
            error.put("error", "Batch size too large. Maximum is 10000");
            return ResponseEntity.badRequest().body(error);
        }
        
        try {
            producerService.sendBatch(size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "sent");
            response.put("batchSize", size);
            response.put("totalSent", producerService.getMessageCount());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send batch", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "failed");
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get producer statistics
     * 
     * GET /api/orders/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessagesSent", producerService.getMessageCount());
        stats.put("applicationName", "eventhub-producer");
        stats.put("status", "running");
        
        return ResponseEntity.ok(stats);
    }
}
