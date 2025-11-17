package com.example.resilience.controller;

import com.example.resilience.model.ApiResponse;
import com.example.resilience.service.RetryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller demonstrating Retry Pattern
 * 
 * Endpoints:
 * - GET  /api/retry/demo - Basic retry demonstration
 * - GET  /api/retry/success - Always succeeds
 * - GET  /api/retry/fail - Always fails to show fallback
 * - GET  /api/retry/unreliable - Random success/failure
 * - POST /api/retry/reset - Reset counters
 */
@RestController
@RequestMapping("/api/retry")
public class RetryController {

    private final RetryService retryService;

    @Autowired
    public RetryController(RetryService retryService) {
        this.retryService = retryService;
    }

    /**
     * Basic retry demonstration
     * Try with ?fail=true to see retry behavior
     */
    @GetMapping("/demo")
    public ApiResponse retryDemo(@RequestParam(defaultValue = "false") boolean fail) {
        return retryService.performOperationWithRetry(fail);
    }

    /**
     * Operation that always succeeds
     */
    @GetMapping("/success")
    public ApiResponse alwaysSucceed() {
        return retryService.performOperationWithRetry(false);
    }

    /**
     * Operation that always fails to demonstrate fallback
     */
    @GetMapping("/fail")
    public ApiResponse alwaysFail() {
        return retryService.performOperationWithRetry(true);
    }

    /**
     * Unreliable operation with random failures
     */
    @GetMapping("/unreliable")
    public ApiResponse unreliableOperation() {
        return retryService.unreliableOperation();
    }

    /**
     * Reset the retry counter
     */
    @PostMapping("/reset")
    public ApiResponse resetCounter() {
        retryService.resetCounter();
        return new ApiResponse("Retry counter reset successfully", "SUCCESS", "RETRY");
    }

    /**
     * Get current attempt count
     */
    @GetMapping("/status")
    public ApiResponse getStatus() {
        int attempts = retryService.getCurrentAttempt();
        return new ApiResponse(
            "Current attempt count: " + attempts,
            "SUCCESS",
            attempts,
            "RETRY"
        );
    }
}
