package com.example.resilience.service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.resilience.model.ApiResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Service demonstrating Circuit Breaker Pattern with Resilience4j
 * 
 * The Circuit Breaker pattern prevents an application from repeatedly trying to execute
 * an operation that's likely to fail. It has three states:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Too many failures detected, requests are blocked
 * - HALF_OPEN: Testing if the service has recovered
 */
@Service
public class CircuitBreakerService {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);
    private final AtomicInteger callCounter = new AtomicInteger(0);
    private final Random random = new Random();

    /**
     * Demonstrates basic circuit breaker functionality
     * After 50% failure rate (configured in application.yml), the circuit will OPEN
     * 
     * @param shouldFail - controls whether the operation should fail
     * @return ApiResponse with operation result
     */
    @CircuitBreaker(name = "backendService", fallbackMethod = "circuitBreakerFallback")
    public ApiResponse callExternalService(boolean shouldFail) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("Circuit Breaker - Call #{} to external service at {}", callNumber, LocalDateTime.now());

        if (shouldFail) {
            logger.error("Call #{} - Simulating external service failure", callNumber);
            throw new RuntimeException("External service is down");
        }

        logger.info("Call #{} - External service responded successfully", callNumber);
        return new ApiResponse(
            "External service call #" + callNumber + " successful",
            "SUCCESS",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Demonstrates circuit breaker with payment service
     * More aggressive thresholds for critical operations
     * 
     * @return ApiResponse with operation result
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public ApiResponse processPayment(double amount) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("Processing payment #{} for amount: ${}", callNumber, amount);

        // Simulate random failures (30% failure rate)
        if (random.nextDouble() < 0.3) {
            logger.error("Payment #{} failed - payment gateway error", callNumber);
            throw new RuntimeException("Payment gateway timeout");
        }

        logger.info("Payment #{} processed successfully", callNumber);
        return new ApiResponse(
            "Payment of $" + amount + " processed successfully (Call #" + callNumber + ")",
            "SUCCESS",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Simulates a slow external service that might cause timeouts
     * 
     * @param delayMs - delay in milliseconds
     * @return ApiResponse with operation result
     */
    @CircuitBreaker(name = "backendService", fallbackMethod = "circuitBreakerFallback")
    public ApiResponse slowExternalService(long delayMs) {
        int callNumber = callCounter.incrementAndGet();
        logger.info("Call #{} to slow service (delay: {}ms)", callNumber, delayMs);

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Service interrupted", e);
        }

        if (delayMs > 3000) {
            logger.error("Call #{} - Service timeout (delay was {}ms)", callNumber, delayMs);
            throw new RuntimeException("Service timeout");
        }

        logger.info("Call #{} - Slow service responded after {}ms", callNumber, delayMs);
        return new ApiResponse(
            "Slow service call #" + callNumber + " completed after " + delayMs + "ms",
            "SUCCESS",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Fallback method for callExternalService
     * This is called when:
     * 1. The circuit is OPEN (too many failures)
     * 2. The operation fails and we want to provide a graceful response
     * 
     * @param shouldFail - original method parameter
     * @param ex - the exception that triggered the fallback
     * @return ApiResponse with fallback message
     */
    private ApiResponse handleFallback(boolean shouldFail, Exception ex) {
        logger.warn("Circuit Breaker Fallback executed. Reason: {}", ex.getMessage());
        
        String message;
        if (ex instanceof CallNotPermittedException) {
            message = "Circuit is OPEN - Service temporarily unavailable. Please try again later.";
        } else {
            message = "Service call failed. Using fallback response. Error: " + ex.getMessage();
        }

        return new ApiResponse(
            message,
            "FALLBACK",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Fallback for slowExternalService
     * @param delayMs - original method parameter
     * @param ex - the exception that triggered the fallback
     * @return ApiResponse with fallback message
     */
    private ApiResponse circuitBreakerFallback(long delayMs, Exception ex) {
        logger.warn("Circuit Breaker Fallback executed for slow service. Reason: {}", ex.getMessage());

        String message;
        if (ex instanceof CallNotPermittedException) {
            message = "Circuit is OPEN - Service temporarily unavailable. Please try again later.";
        } else {
            message = "Service call failed. Using fallback response. Error: " + ex.getMessage();
        }

        return new ApiResponse(
            message,
            "FALLBACK",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Fallback method specifically for payment processing
     * Provides critical business logic for handling payment failures
     * 
     * @param amount - the payment amount
     * @param ex - the exception that triggered the fallback
     * @return ApiResponse with fallback payment handling
     */
    private ApiResponse paymentFallback(double amount, Exception ex) {
        logger.error("Payment fallback executed for amount: ${}. Reason: {}", amount, ex.getMessage());
        
        String message;
        if (ex instanceof CallNotPermittedException) {
            message = "Payment service circuit is OPEN. Payment of $" + amount + 
                     " has been queued for processing. You will be notified when processed.";
        } else {
            message = "Payment of $" + amount + " failed. Transaction will be retried. Reference saved.";
        }

        return new ApiResponse(
            message,
            "FALLBACK",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Resets the call counter (useful for testing)
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
