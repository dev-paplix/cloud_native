package com.example.resilience.service;

import com.example.resilience.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service demonstrating Combined Resilience Patterns
 * 
 * Shows how to use multiple Resilience4j patterns together:
 * - Rate Limiter (controls incoming request rate)
 * - Circuit Breaker (prevents cascading failures)
 * - Retry (handles transient failures)
 * 
 * The order of annotations matters:
 * 1. RateLimiter - First check if we should allow the request
 * 2. CircuitBreaker - Then check if the service is available
 * 3. Retry - Finally, retry if the operation fails
 */
@Service
public class CombinedPatternsService {

    private static final Logger logger = LoggerFactory.getLogger(CombinedPatternsService.class);
    private final AtomicInteger callCounter = new AtomicInteger(0);
    private final Random random = new Random();

    /**
     * Demonstrates all three patterns working together
     * This simulates a real-world scenario of calling an external payment API
     * 
     * @param amount - the payment amount
     * @return ApiResponse with operation result
     */
    @RateLimiter(name = "apiService")
    @CircuitBreaker(name = "paymentService")
    @Retry(name = "backendService")
    public ApiResponse processComplexOperation(double amount) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("=== Combined Patterns - Call #{} - Processing amount: ${} ===", callNumber, amount);

        // Simulate various failure scenarios
        double randomValue = random.nextDouble();
        
        if (randomValue < 0.2) {
            // 20% chance of transient failure (will trigger Retry)
            logger.warn("Call #{} - Transient failure occurred (will retry)", callNumber);
            throw new RuntimeException("Transient network error");
        } else if (randomValue < 0.3) {
            // 10% additional chance of service failure (may trigger Circuit Breaker)
            logger.error("Call #{} - Service failure occurred", callNumber);
            throw new RuntimeException("External service error");
        }

        logger.info("Call #{} - Operation completed successfully", callNumber);
        return new ApiResponse(
            "Complex operation completed successfully for amount: $" + amount + " (Call #" + callNumber + ")",
            "SUCCESS",
            "COMBINED (Rate Limiter + Circuit Breaker + Retry)"
        );
    }

    /**
     * Demonstrates combining Circuit Breaker and Retry for database operations
     * 
     * @param recordId - the database record ID
     * @return ApiResponse with operation result
     */
    @CircuitBreaker(name = "backendService", fallbackMethod = "databaseFallback")
    @Retry(name = "orderService")
    public ApiResponse saveToDatabaseWithResilience(String recordId) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("Database operation - Call #{} - Saving record: {}", callNumber, recordId);

        // Simulate occasional database failures
        if (random.nextDouble() < 0.15) {
            logger.warn("Call #{} - Database temporarily unavailable", callNumber);
            throw new RuntimeException("Database connection timeout");
        }

        logger.info("Call #{} - Record saved successfully", callNumber);
        return new ApiResponse(
            "Record '" + recordId + "' saved successfully (Call #" + callNumber + ")",
            "SUCCESS",
            "COMBINED (Circuit Breaker + Retry)"
        );
    }

    /**
     * Demonstrates all patterns with simulated processing time
     * 
     * @param orderId - the order ID to process
     * @return ApiResponse with operation result
     */
    @RateLimiter(name = "default")
    @CircuitBreaker(name = "backendService", fallbackMethod = "orderFallback")
    @Retry(name = "orderService")
    public ApiResponse processOrderWithResilience(String orderId) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("Order processing - Call #{} - Order: {}", callNumber, orderId);

        try {
            // Simulate processing time
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }

        // Simulate occasional failures
        if (random.nextDouble() < 0.1) {
            logger.warn("Call #{} - Order processing failed", callNumber);
            throw new RuntimeException("Order validation failed");
        }

        logger.info("Call #{} - Order processed successfully", callNumber);
        return new ApiResponse(
            "Order '" + orderId + "' processed successfully (Call #" + callNumber + ")",
            "SUCCESS",
            "COMBINED (Rate Limiter + Circuit Breaker + Retry)"
        );
    }

    /**
     * Fallback for database operations
     */
    private ApiResponse databaseFallback(String recordId, Exception ex) {
        logger.error("Database fallback for record: {}. Error: {}", recordId, ex.getMessage());
        return new ApiResponse(
            "Database operation failed for record '" + recordId + "'. Data cached locally for later sync.",
            "FALLBACK",
            "COMBINED"
        );
    }

    /**
     * Fallback for order processing
     */
    private ApiResponse orderFallback(String orderId, Exception ex) {
        logger.error("Order fallback for order: {}. Error: {}", orderId, ex.getMessage());
        return new ApiResponse(
            "Order '" + orderId + "' processing failed. Order queued for manual review.",
            "FALLBACK",
            "COMBINED"
        );
    }

    /**
     * Resets the call counter
     */
    public void resetCounter() {
        callCounter.set(0);
    }

    /**
     * Gets current call count
     */
    public int getCallCount() {
        return callCounter.get();
    }
}
