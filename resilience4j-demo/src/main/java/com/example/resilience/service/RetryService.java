package com.example.resilience.service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.resilience.model.ApiResponse;

import io.github.resilience4j.retry.annotation.Retry;

/**
 * Service demonstrating Retry Pattern with Resilience4j
 * 
 * The Retry pattern automatically retries failed operations before giving up.
 * This is useful for handling transient failures like network issues.
 */
@Service
public class RetryService {

    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    private final AtomicInteger attemptCounter = new AtomicInteger(0);
    private final Random random = new Random();

    /**
     * Demonstrates basic retry functionality
     * This method will automatically retry up to 3 times (configured in application.yml)
     * 
     * @param shouldFail - controls whether the operation should simulate failure
     * @return ApiResponse with operation result
     */
    @Retry(name = "backendService", fallbackMethod = "performOperationFallback")
    public ApiResponse performOperationWithRetry(boolean shouldFail) {
        int attempt = attemptCounter.incrementAndGet();
        logger.info("Retry attempt #{} at {}", attempt, LocalDateTime.now());

        if (shouldFail && attempt < 3) {
            logger.warn("Simulating failure on attempt #{}", attempt);
            attemptCounter.set(0); // Reset for next call
            throw new RuntimeException("Simulated failure - this will trigger a retry");
        }

        attemptCounter.set(0); // Reset counter after success
        logger.info("Operation succeeded on attempt #{}", attempt);
        return new ApiResponse(
            "Operation successful after " + attempt + " attempt(s)",
            "SUCCESS",
            attempt,
            "RETRY"
        );
    }

    /**
     * Demonstrates retry with random failures
     * Simulates unpredictable service behavior
     * 
     * @return ApiResponse with operation result
     */
    @Retry(name = "orderService", fallbackMethod = "unreliableOperationFallback")
    public ApiResponse unreliableOperation() {
        int attempt = attemptCounter.incrementAndGet();
        logger.info("Unreliable operation attempt #{}", attempt);

        // 60% chance of failure on first two attempts
        if (attempt <= 2 && random.nextDouble() < 0.6) {
            logger.warn("Random failure on attempt #{}", attempt);
            throw new RuntimeException("Random service failure");
        }

        attemptCounter.set(0);
        logger.info("Unreliable operation succeeded on attempt #{}", attempt);
        return new ApiResponse(
            "Unreliable operation succeeded after " + attempt + " attempt(s)",
            "SUCCESS",
            attempt,
            "RETRY"
        );
    }

    /**
     * Fallback method for performOperationWithRetry
     * Called when all retry attempts are exhausted
     *
     * @param shouldFail - original method parameter
     * @param ex - the exception that caused the failure
     * @return ApiResponse with fallback message
     */
    private ApiResponse performOperationFallback(boolean shouldFail, Exception ex) {
        int attempts = attemptCounter.get();
        attemptCounter.set(0);
        logger.error("All retry attempts failed. Last attempt: #{}", attempts, ex);

        return new ApiResponse(
            "Operation failed after " + attempts + " attempts. Fallback executed. Error: " + ex.getMessage(),
            "FALLBACK",
            attempts,
            "RETRY"
        );
    }

    /**
     * Fallback method for unreliableOperation
     * Called when all retry attempts are exhausted
     * 
     * @param ex - the exception that caused the failure
     * @return ApiResponse with fallback message
     */
    private ApiResponse unreliableOperationFallback(Exception ex) {
        int attempts = attemptCounter.get();
        attemptCounter.set(0);
        logger.error("All retry attempts failed. Last attempt: #{}", attempts, ex);
        
        return new ApiResponse(
            "Unreliable operation failed after " + attempts + " attempts. Fallback executed. Error: " + ex.getMessage(),
            "FALLBACK",
            attempts,
            "RETRY"
        );
    }

    /**
     * Resets the attempt counter (useful for testing)
     */
    public void resetCounter() {
        attemptCounter.set(0);
    }

    /**
     * Gets current attempt count (useful for monitoring)
     */
    public int getCurrentAttempt() {
        return attemptCounter.get();
    }
}
