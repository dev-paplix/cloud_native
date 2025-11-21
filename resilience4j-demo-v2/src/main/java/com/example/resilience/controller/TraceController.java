package com.example.resilience.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {
    
    private final RestTemplate restTemplate;
    
    /**
     * Endpoint 1: Entry point
     */
    @GetMapping("/start")
    public ResponseEntity<String> start() {
        log.info("Start endpoint called",
            kv("endpoint", "/api/trace/start"));
        
        // Call second endpoint
        String result = restTemplate.getForObject(
            "http://localhost:8080/api/trace/middle",
            String.class
        );
        
        log.info("Start endpoint completed",
            kv("result", result));
        
        return ResponseEntity.ok("Start -> " + result);
    }
    
    /**
     * Endpoint 2: Middle
     */
    @GetMapping("/middle")
    public ResponseEntity<String> middle() {
        log.info("Middle endpoint called",
            kv("endpoint", "/api/trace/middle"));
        
        // Call third endpoint
        String result = restTemplate.getForObject(
            "http://localhost:8080/api/trace/end",
            String.class
        );
        
        log.info("Middle endpoint completed",
            kv("result", result));
        
        return ResponseEntity.ok("Middle -> " + result);
    }
    
    /**
     * Endpoint 3: End
     */
    @GetMapping("/end")
    public ResponseEntity<String> end() {
        log.info("End endpoint called",
            kv("endpoint", "/api/trace/end"));
        
        return ResponseEntity.ok("End");
    }
}
