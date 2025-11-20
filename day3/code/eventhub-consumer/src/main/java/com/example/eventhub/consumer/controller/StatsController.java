package com.example.eventhub.consumer.controller;

import com.example.eventhub.consumer.service.OrderConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Stats API Controller
 * 
 * Provides processing statistics and metrics
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    
    private final OrderConsumerService consumerService;
    
    /**
     * Get consumer processing statistics
     * 
     * GET /api/stats
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        int processed = consumerService.getProcessedCount();
        int errors = consumerService.getErrorCount();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("processedCount", processed);
        stats.put("errorCount", errors);
        stats.put("successCount", processed - errors);
        stats.put("successRate", calculateSuccessRate(processed, errors));
        stats.put("applicationName", "eventhub-consumer");
        stats.put("status", "running");
        
        return ResponseEntity.ok(stats);
    }
    
    private double calculateSuccessRate(int total, int errors) {
        if (total == 0) return 100.0;
        return Math.round(((total - errors) * 100.0 / total) * 100.0) / 100.0;
    }
}
