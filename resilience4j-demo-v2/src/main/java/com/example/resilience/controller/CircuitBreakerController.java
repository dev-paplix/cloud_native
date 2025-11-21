package com.example.resilience.controller;

import com.example.resilience.model.ApiResponse;
import com.example.resilience.service.CircuitBreakerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller demonstrating Circuit Breaker Pattern
 * 
 * Endpoints:
 * - GET  /api/circuit-breaker/demo - Basic circuit breaker demo
 * - GET  /api/circuit-breaker/success - Always succeeds
 * - GET  /api/circuit-breaker/fail - Fails to trigger circuit opening
 * - GET  /api/circuit-breaker/payment - Payment processing with circuit breaker
 * - GET  /api/circuit-breaker/slow - Slow service simulation
 * - POST /api/circuit-breaker/reset - Reset counters
 * 
 * To see circuit breaker in action:
 * 1. Call /fail endpoint multiple times (5-10 times)
 * 2. The circuit will OPEN after failure threshold is reached
 * 3. Subsequent calls will be rejected immediately (fallback executed)
 * 4. Wait for waitDurationInOpenState (10 seconds)
 * 5. Circuit transitions to HALF_OPEN and allows test requests
 */
@RestController
@RequestMapping("/api/circuit-breaker")
public class CircuitBreakerController {

    private final CircuitBreakerService circuitBreakerService;

    @Autowired
    public CircuitBreakerController(CircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
    }

    /**
     * Basic circuit breaker demonstration
     * Try with ?fail=true to simulate failures
     */
    @GetMapping("/demo")
    public ApiResponse circuitBreakerDemo(@RequestParam(defaultValue = "false") boolean fail) {
        return circuitBreakerService.callExternalService(fail);
    }

    /**
     * Operation that always succeeds
     */
    @GetMapping("/success")
    public ApiResponse alwaysSucceed() {
        return circuitBreakerService.callExternalService(false);
    }

    /**
     * Operation that always fails
     * Call this multiple times to open the circuit
     */
    @GetMapping("/fail")
    public ApiResponse alwaysFail() {
        return circuitBreakerService.callExternalService(true);
    }

    /**
     * Payment processing with circuit breaker
     * Demonstrates circuit breaker on critical business operations
     */
    @GetMapping("/payment")
    public ApiResponse processPayment(@RequestParam(defaultValue = "100.00") double amount) {
        return circuitBreakerService.processPayment(amount);
    }

    /**
     * Slow service simulation
     * Try different delays to see timeout behavior
     */
    @GetMapping("/slow")
    public ApiResponse slowService(@RequestParam(defaultValue = "1000") long delay) {
        return circuitBreakerService.slowExternalService(delay);
    }

    /**
     * Simulate multiple failures to open the circuit
     * This endpoint makes 10 failing calls to trigger circuit opening
     */
    @PostMapping("/trigger-open")
    public ApiResponse triggerCircuitOpen() {
        StringBuilder results = new StringBuilder("Triggering circuit breaker by making 10 failing calls:\n");
        
        for (int i = 1; i <= 10; i++) {
            try {
                circuitBreakerService.callExternalService(true);
            } catch (Exception e) {
                results.append("Call ").append(i).append(": Failed - ").append(e.getMessage()).append("\n");
            }
        }
        
        results.append("\nCircuit should now be OPEN. Try calling /demo or /success endpoints.");
        
        return new ApiResponse(
            results.toString(),
            "CIRCUIT_OPENED",
            "CIRCUIT_BREAKER"
        );
    }

    /**
     * Reset the circuit breaker counter
     */
    @PostMapping("/reset")
    public ApiResponse resetCounter() {
        circuitBreakerService.resetCounter();
        return new ApiResponse("Circuit breaker counter reset successfully", "SUCCESS", "CIRCUIT_BREAKER");
    }

    /**
     * Get current call count
     */
    @GetMapping("/status")
    public ApiResponse getStatus() {
        int calls = circuitBreakerService.getCallCount();
        return new ApiResponse(
            "Current call count: " + calls + 
            "\nTo see circuit breaker states, visit: http://localhost:8080/actuator/circuitbreakers",
            "SUCCESS",
            "CIRCUIT_BREAKER"
        );
    }
}
