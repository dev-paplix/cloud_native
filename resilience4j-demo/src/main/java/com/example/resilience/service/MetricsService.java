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
        String[] allTags = new String[tags.length + 2];
        allTags[0] = "type";
        allTags[1] = eventType;
        System.arraycopy(tags, 0, allTags, 2, tags.length);
        
        meterRegistry.counter("business.events", allTags).increment();
    }
    
    public void recordGauge(String name, double value, String... tags) {
        meterRegistry.gauge(name, 
            io.micrometer.core.instrument.Tags.of(tags), 
            value);
    }
}
