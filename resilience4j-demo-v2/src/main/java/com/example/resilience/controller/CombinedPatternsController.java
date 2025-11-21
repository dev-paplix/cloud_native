package com.example.resilience.controller;

import com.example.resilience.model.ApiResponse;
import com.example.resilience.service.CombinedPatternsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller demonstrating Combined Resilience Patterns
 * 
 * Endpoints:
 * - GET  /api/combined/demo - All patterns combined
 * - GET  /api/combined/payment - Payment with all resilience patterns
 * - GET  /api/combined/database - Database operation with circuit breaker + retry
 * - GET  /api/combined/order - Order processing with all patterns
 * - POST /api/combined/reset - Reset counters
 */
@RestController
@RequestMapping("/api/combined")
public class CombinedPatternsController {

    private final CombinedPatternsService combinedService;

    @Autowired
    public CombinedPatternsController(CombinedPatternsService combinedService) {
        this.combinedService = combinedService;
    }

    /**
     * Demonstrates all three patterns working together
     * Rate Limiter -> Circuit Breaker -> Retry
     */
    @GetMapping("/demo")
    public ApiResponse combinedDemo(@RequestParam(defaultValue = "100.00") double amount) {
        return combinedService.processComplexOperation(amount);
    }

    /**
     * Payment processing with combined patterns
     * Simulates a real-world payment gateway integration
     */
    @PostMapping("/payment")
    public ApiResponse processPayment(@RequestParam double amount) {
        return combinedService.processComplexOperation(amount);
    }

    /**
     * Database operation with Circuit Breaker + Retry
     */
    @PostMapping("/database")
    public ApiResponse saveToDatabase(@RequestParam String recordId) {
        return combinedService.saveToDatabaseWithResilience(recordId);
    }

    /**
     * Order processing with all resilience patterns
     */
    @PostMapping("/order")
    public ApiResponse processOrder(@RequestParam String orderId) {
        return combinedService.processOrderWithResilience(orderId);
    }

    /**
     * Stress test - make multiple requests to see all patterns in action
     */
    @GetMapping("/stress-test")
    public ApiResponse stressTest(@RequestParam(defaultValue = "20") int requestCount) {
        StringBuilder results = new StringBuilder("Stress Testing Combined Patterns (" + requestCount + " requests):\n\n");
        int successCount = 0;
        int failureCount = 0;
        int rateLimitedCount = 0;

        for (int i = 1; i <= requestCount; i++) {
            try {
                ApiResponse response = combinedService.processComplexOperation(100.0 * i);
                results.append("Request ").append(i).append(": ").append(response.getStatus()).append("\n");
                
                if ("SUCCESS".equals(response.getStatus())) {
                    successCount++;
                } else if ("RATE_LIMITED".equals(response.getStatus())) {
                    rateLimitedCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                results.append("Request ").append(i).append(": EXCEPTION - ").append(e.getMessage()).append("\n");
                failureCount++;
            }
        }

        results.append("\n=== Summary ===\n");
        results.append("Successful: ").append(successCount).append("\n");
        results.append("Failed/Fallback: ").append(failureCount).append("\n");
        results.append("Rate Limited: ").append(rateLimitedCount).append("\n");
        results.append("\nPatterns Applied: Rate Limiter -> Circuit Breaker -> Retry");

        return new ApiResponse(
            results.toString(),
            "STRESS_TEST_COMPLETED",
            "COMBINED"
        );
    }

    /**
     * Reset all counters
     */
    @PostMapping("/reset")
    public ApiResponse resetCounters() {
        combinedService.resetCounter();
        return new ApiResponse(
            "All counters reset successfully",
            "SUCCESS",
            "COMBINED"
        );
    }

    /**
     * Get status of combined patterns
     */
    @GetMapping("/status")
    public ApiResponse getStatus() {
        int calls = combinedService.getCallCount();
        return new ApiResponse(
            "Current call count: " + calls + 
            "\n\nCombined Patterns Overview:\n" +
            "1. Rate Limiter - Controls request rate (5 req/sec for API)\n" +
            "2. Circuit Breaker - Prevents cascade failures (opens after 50% failure)\n" +
            "3. Retry - Handles transient failures (max 3 attempts)\n\n" +
            "Monitoring Endpoints:\n" +
            "  - /actuator/health\n" +
            "  - /actuator/circuitbreakers\n" +
            "  - /actuator/ratelimiters\n" +
            "  - /actuator/retries",
            "SUCCESS",
            "COMBINED"
        );
    }
}
