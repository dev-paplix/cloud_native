package com.example.resilience.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.*;

@Slf4j
@Service
public class LoggingService {
    
    /**
     * Log with structured fields
     */
    public void logWithContext(String message, String key1, Object value1) {
        log.info(message, kv(key1, value1));
    }
    
    /**
     * Log business event with multiple fields
     */
    public void logBusinessEvent(String event, Object... keyValues) {
        log.info("Business event: {}", event, entries(toMap(keyValues)));
    }
    
    /**
     * Add user context to MDC
     */
    public void addUserContext(String userId, String sessionId) {
        MDC.put("userId", userId);
        MDC.put("sessionId", sessionId);
    }
    
    /**
     * Clear MDC context
     */
    public void clearContext() {
        MDC.clear();
    }
    
    /**
     * Helper to create map from var args
     */
    private java.util.Map<String, Object> toMap(Object... keyValues) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }
}
