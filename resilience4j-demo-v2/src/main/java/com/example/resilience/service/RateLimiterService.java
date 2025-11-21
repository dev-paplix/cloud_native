package com.example.resilience.service;

import com.example.resilience.model.ApiResponse;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service demonstrating Rate Limiter Pattern with Resilience4j
 * 
 * The Rate Limiter pattern controls the rate at which operations are executed.
 * This prevents overloading services and ensures fair resource usage.
 */
@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    /**
     * Demonstrates basic rate limiting
     * Limited to 10 requests per second (configured in application.yml)
     * 
     * @return ApiResponse with operation result
     */
    @RateLimiter(name = "default", fallbackMethod = "rateLimiterFallback")
    public ApiResponse performStandardOperation() {
        int requestNumber = requestCounter.incrementAndGet();
        logger.info("Standard operation - Request #{} at {}", requestNumber, LocalDateTime.now());
        
        return new ApiResponse(
            "Standard operation completed. Request #" + requestNumber,
            "SUCCESS",
            "RATE_LIMITER"
        );
    }

    /**
     * Demonstrates rate limiting for API service
     * Limited to 5 requests per second - simulates external API restrictions
     * 
     * @param userId - the user making the request
     * @return ApiResponse with operation result
     */
    @RateLimiter(name = "apiService", fallbackMethod = "apiRateLimiterFallback")
    public ApiResponse callExternalApi(String userId) {
        int requestNumber = requestCounter.incrementAndGet();
        logger.info("API call for user '{}' - Request #{}", userId, requestNumber);
        
        return new ApiResponse(
            "External API called successfully for user: " + userId + " (Request #" + requestNumber + ")",
            "SUCCESS",
            "RATE_LIMITER"
        );
    }

    /**
     * Demonstrates rate limiting for premium service
     * Limited to 100 requests per second - higher limit for premium tier
     * 
     * @param customerId - the premium customer ID
     * @return ApiResponse with operation result
     */
    @RateLimiter(name = "premiumService", fallbackMethod = "premiumRateLimiterFallback")
    public ApiResponse processPremiumRequest(String customerId) {
        int requestNumber = requestCounter.incrementAndGet();
        logger.info("Premium service - Customer '{}' - Request #{}", customerId, requestNumber);
        
        return new ApiResponse(
            "Premium service request processed for customer: " + customerId + " (Request #" + requestNumber + ")",
            "SUCCESS",
            "RATE_LIMITER"
        );
    }

    /**
     * Fallback method for standard rate limiter
     * Called when rate limit is exceeded
     * 
     * @param ex - the RequestNotPermitted exception
     * @return ApiResponse with rate limit message
     */
    private ApiResponse rateLimiterFallback(Exception ex) {
        logger.warn("Rate limit exceeded for standard operation");
        
        return new ApiResponse(
            "Rate limit exceeded. You can make 10 requests per second. Please slow down and try again.",
            "RATE_LIMITED",
            "RATE_LIMITER"
        );
    }

    /**
     * Fallback method for API service rate limiter
     */
    private ApiResponse apiRateLimiterFallback(String userId, Exception ex) {
        logger.warn("Rate limit exceeded for API service - User: {}", userId);
        
        return new ApiResponse(
            "API rate limit exceeded for user: " + userId + 
            ". You can make 5 API calls per second. Please wait before making more requests.",
            "RATE_LIMITED",
            "RATE_LIMITER"
        );
    }

    /**
     * Fallback method for premium service rate limiter
     */
    private ApiResponse premiumRateLimiterFallback(String customerId, Exception ex) {
        logger.warn("Rate limit exceeded for premium service - Customer: {}", customerId);
        
        return new ApiResponse(
            "Premium service rate limit exceeded for customer: " + customerId + 
            ". You can make 100 requests per second. Please wait before making more requests.",
            "RATE_LIMITED",
            "RATE_LIMITER"
        );
    }

    /**
     * Resets the request counter
     */
    public void resetCounter() {
        requestCounter.set(0);
    }

    /**
     * Gets current request count
     */
    public int getRequestCount() {
        return requestCounter.get();
    }
}
