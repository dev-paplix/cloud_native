package com.example.resilience.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    
    @GetMapping("/simulate-errors")
    public ResponseEntity<?> simulateErrors(
        @RequestParam(defaultValue = "50") int errorPercentage) {
        
        log.info("Simulating errors", kv("errorPercentage", errorPercentage));
        
        boolean shouldFail = Math.random() * 100 < errorPercentage;
        
        if (shouldFail) {
            log.error("Simulated error", kv("errorPercentage", errorPercentage));
            throw new RuntimeException("Simulated error for testing");
        }
        
        log.info("Request successful");
        return ResponseEntity.ok("Success");
    }
    
    @GetMapping("/simulate-latency")
    public ResponseEntity<?> simulateLatency(
        @RequestParam(defaultValue = "500") int delayMs) throws InterruptedException {
        
        log.info("Simulating latency", kv("delayMs", delayMs));
        
        Thread.sleep(delayMs);
        
        log.info("Latency simulation completed", kv("delayMs", delayMs));
        return ResponseEntity.ok("Response after " + delayMs + "ms");
    }
}
