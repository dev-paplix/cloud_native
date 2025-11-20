package com.example.resilience.service;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    public void recordRequest(String endpoint, boolean success, long durationMs) {
        // Increment counters
        meterRegistry.counter("api.requests.total",
            "endpoint", endpoint,
            "status", success ? "success" : "failure"
        ).increment();
        
        // Record duration
        meterRegistry.timer("api.requests.duration",
            "endpoint", endpoint
        ).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordBusinessEvent(String eventType, String... tags) {
        meterRegistry.counter("business.events",
            appendTags("type", eventType, tags)
        ).increment();
    }
    
    public void recordGauge(String name, double value, String... tags) {
        meterRegistry.gauge(name, 
            io.micrometer.core.instrument.Tags.of(tags), 
            value);
    }
    
    private String[] appendTags(String... tags) {
        return tags;
    }
}
