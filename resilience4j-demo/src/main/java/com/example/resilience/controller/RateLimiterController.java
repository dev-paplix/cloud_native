package com.example.resilience.controller;

import com.example.resilience.model.ApiResponse;
import com.example.resilience.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller demonstrating Rate Limiter Pattern
 * 
 * Endpoints:
 * - GET  /api/rate-limiter/demo - Basic rate limiter (10 req/sec)
 * - GET  /api/rate-limiter/api - API service rate limiter (5 req/sec)
 * - GET  /api/rate-limiter/premium - Premium service (100 req/sec)
 * - POST /api/rate-limiter/reset - Reset counters
 * 
 * To test rate limiting:
 * 1. Call an endpoint rapidly (more than limit per second)
 * 2. You'll receive RATE_LIMITED responses when limit is exceeded
 * 3. Wait 1 second for the limit to refresh
 */
@RestController
@RequestMapping("/api/rate-limiter")
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    @Autowired
    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Basic rate limiter demonstration
     * Limited to 10 requests per second
     */
    @GetMapping("/demo")
    public ApiResponse rateLimiterDemo() {
        return rateLimiterService.performStandardOperation();
    }

    /**
     * API service with stricter rate limiting
     * Limited to 5 requests per second
     */
    @GetMapping("/api")
    public ApiResponse apiServiceCall(@RequestParam(defaultValue = "user123") String userId) {
        return rateLimiterService.callExternalApi(userId);
    }

    /**
     * Premium service with higher rate limit
     * Limited to 100 requests per second
     */
    @GetMapping("/premium")
    public ApiResponse premiumServiceCall(@RequestParam(defaultValue = "premium456") String customerId) {
        return rateLimiterService.processPremiumRequest(customerId);
    }

    /**
     * Test endpoint to make multiple rapid requests
     * This will demonstrate rate limiting in action
     */
    @GetMapping("/burst-test")
    public ApiResponse burstTest(@RequestParam(defaultValue = "15") int requestCount) {
        StringBuilder results = new StringBuilder("Making " + requestCount + " rapid requests:\n\n");
        int successCount = 0;
        int rateLimitedCount = 0;

        for (int i = 1; i <= requestCount; i++) {
            ApiResponse response = rateLimiterService.performStandardOperation();
            results.append("Request ").append(i).append(": ").append(response.getStatus()).append("\n");
            
            if ("SUCCESS".equals(response.getStatus())) {
                successCount++;
            } else if ("RATE_LIMITED".equals(response.getStatus())) {
                rateLimitedCount++;
            }
        }

        results.append("\n=== Summary ===\n");
        results.append("Successful: ").append(successCount).append("\n");
        results.append("Rate Limited: ").append(rateLimitedCount).append("\n");
        results.append("\nRate limit: 10 requests per second");

        return new ApiResponse(
            results.toString(),
            "TEST_COMPLETED",
            "RATE_LIMITER"
        );
    }

    /**
     * Reset the request counter
     */
    @PostMapping("/reset")
    public ApiResponse resetCounter() {
        rateLimiterService.resetCounter();
        return new ApiResponse("Rate limiter counter reset successfully", "SUCCESS", "RATE_LIMITER");
    }

    /**
     * Get current request count and rate limiter status
     */
    @GetMapping("/status")
    public ApiResponse getStatus() {
        int requests = rateLimiterService.getRequestCount();
        return new ApiResponse(
            "Current request count: " + requests + 
            "\n\nRate Limits:\n" +
            "  - Default: 10 req/sec\n" +
            "  - API Service: 5 req/sec\n" +
            "  - Premium: 100 req/sec\n\n" +
            "To see rate limiter states, visit: http://localhost:8080/actuator/ratelimiters",
            "SUCCESS",
            "RATE_LIMITER"
        );
    }
}
