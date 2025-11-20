# Hands-On Lab: Configure Spring Cloud Sleuth + Logback JSON Logs

## Duration: 45 minutes

> **💡 Cloud Shell Ready!** This entire lab can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Lab Objectives
- Configure Spring Cloud Sleuth for distributed tracing
- Set up JSON logging with Logback
- Implement correlation IDs across services
- Test trace propagation across HTTP calls
- Query logs using Azure Monitor

---

## Part 1: Add Sleuth Dependencies (5 min)

### Update pom.xml

```xml
<dependencies>
    <!-- Existing dependencies... -->
    
    <!-- Spring Cloud Sleuth -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-sleuth</artifactId>
    </dependency>
    
    <!-- Logstash Logback Encoder for JSON logging -->
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
        <version>7.4</version>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Part 2: Configure Sleuth (10 min)

### Update application.yml

```yaml
spring:
  application:
    name: resilience4j-demo
  
  sleuth:
    enabled: true
    
    # Sampling configuration
    sampler:
      probability: 1.0  # Sample 100% for testing (reduce to 0.1 in production)
    
    # Use W3C Trace Context standard
    propagation:
      type: w3c
    
    # Baggage - propagate custom fields across services
    baggage:
      correlation-enabled: true
      correlation-fields:
        - userId
        - sessionId
        - requestId
      remote-fields:
        - userId
        - sessionId
        - requestId
    
    # Integration with specific frameworks
    web:
      enabled: true
      skip-pattern: /actuator.*|/health.*
    
    integration:
      enabled: true
    
    # Async support
    async:
      enabled: true
    
    # HTTP instrumentation
    http:
      enabled: true
    
    # RxJava support
    rxjava:
      schedulers:
        hook:
          enabled: true

# Logging pattern with trace information
logging:
  level:
    root: INFO
    com.example.resilience: DEBUG
    org.springframework.cloud.sleuth: DEBUG
  
  pattern:
    # Console pattern (development)
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%X{traceId}/%X{spanId}]){yellow} %clr(%-5level){cyan} %clr(%logger{36}){blue} - %msg%n"
    
    # File pattern
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId}/%X{spanId}] [%thread] %-5level %logger{36} - %msg%n"
```

---

## Part 3: Configure JSON Logging (15 min)

### Create logback-spring.xml

Create `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- Spring Boot properties -->
    <springProperty scope="context" name="applicationName" source="spring.application.name"/>
    <springProperty scope="context" name="profile" source="spring.profiles.active" defaultValue="dev"/>
    
    <!-- Console Appender - Human Readable -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%X{traceId:-}/%X{spanId:-}]){yellow} %clr(%-5level){cyan} %clr(%logger{36}){blue} - %msg%n
            </pattern>
            <charset>utf8</charset>
        </encoder>
    </appender>
    
    <!-- JSON Appender - Structured Logging -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Custom fields -->
            <customFields>
                {
                  "application": "${applicationName:-unknown}",
                  "environment": "${profile:-dev}"
                }
            </customFields>
            
            <!-- Include MDC keys -->
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>sessionId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            
            <!-- Field names -->
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <version>version</version>
                <message>message</message>
                <logger>logger</logger>
                <thread>thread</thread>
                <level>level</level>
                <levelValue>levelValue</levelValue>
            </fieldNames>
            
            <!-- Include context -->
            <includeContext>true</includeContext>
            
            <!-- Exclude unwanted fields -->
            <includeMdc>true</includeMdc>
            <includeStructuredArguments>true</includeStructuredArguments>
            <includeTags>true</includeTags>
            
            <!-- Timestamp format -->
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSX</timestampPattern>
            
            <!-- Stack traces -->
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>30</maxDepthPerThrowable>
                <maxLength>2048</maxLength>
                <shortenedClassNameLength>20</shortenedClassNameLength>
                <exclude>sun\.reflect\..*</exclude>
                <exclude>net\.sf\.cglib\..*</exclude>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
    </appender>
    
    <!-- File Appender with Rolling -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.json</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"application":"${applicationName}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
        
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    
    <!-- Async Appenders for better performance -->
    <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <appender-ref ref="JSON"/>
    </appender>
    
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <appender-ref ref="FILE"/>
    </appender>
    
    <!-- Development Profile: Human-readable console -->
    <springProfile name="dev,local,test">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.example.resilience" level="DEBUG"/>
        <logger name="org.springframework.cloud.sleuth" level="DEBUG"/>
    </springProfile>
    
    <!-- Production Profile: JSON logging -->
    <springProfile name="prod,staging">
        <root level="INFO">
            <appender-ref ref="ASYNC_JSON"/>
            <appender-ref ref="ASYNC_FILE"/>
        </root>
        <logger name="com.example.resilience" level="INFO"/>
        <logger name="org.springframework.cloud.sleuth" level="INFO"/>
    </springProfile>
</configuration>
```

---

## Part 4: Implement Structured Logging (10 min)

### Create Logging Service

```java
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
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
```

### Update Service with Structured Logging

```java
package com.example.resilience.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerService {
    
    public String callExternalService() {
        // Add context to MDC
        MDC.put("operation", "callExternalService");
        
        try {
            log.info("Calling external service",
                kv("serviceUrl", "https://external-api.com"),
                kv("timeout", 5000));
            
            // Simulate external call
            boolean success = Math.random() > 0.3;
            
            if (!success) {
                log.warn("External service call failed",
                    kv("errorCode", "SERVICE_UNAVAILABLE"),
                    kv("retryable", true));
                throw new RuntimeException("Service unavailable");
            }
            
            log.info("External service call successful",
                kv("responseTime", 250),
                kv("statusCode", 200));
            
            return "Success from external service";
            
        } catch (Exception e) {
            log.error("Failed to call external service", e,
                kv("errorType", e.getClass().getSimpleName()));
            throw e;
        } finally {
            MDC.remove("operation");
        }
    }
}
```

---

## Part 5: Test Trace Propagation (10 min)

### Create HTTP Client Configuration

```java
package com.example.resilience.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {
    
    /**
     * RestTemplate with Sleuth instrumentation
     * Sleuth automatically adds trace headers to outgoing requests
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .build();
    }
}
```

### Create Test Controller for Propagation

```java
package com.example.resilience.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.Tracer;
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
    private final Tracer tracer;
    
    /**
     * Endpoint 1: Entry point
     */
    @GetMapping("/start")
    public ResponseEntity<String> start() {
        var traceId = tracer.currentSpan().context().traceId();
        var spanId = tracer.currentSpan().context().spanId();
        
        log.info("Start endpoint called",
            kv("endpoint", "/api/trace/start"),
            kv("traceId", traceId),
            kv("spanId", spanId));
        
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
        var traceId = tracer.currentSpan().context().traceId();
        var spanId = tracer.currentSpan().context().spanId();
        
        log.info("Middle endpoint called",
            kv("endpoint", "/api/trace/middle"),
            kv("traceId", traceId),
            kv("spanId", spanId));
        
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
        var traceId = tracer.currentSpan().context().traceId();
        var spanId = tracer.currentSpan().context().spanId();
        
        log.info("End endpoint called",
            kv("endpoint", "/api/trace/end"),
            kv("traceId", traceId),
            kv("spanId", spanId));
        
        return ResponseEntity.ok("End");
    }
}
```

---

## Part 6: Testing (5 min)

### Step 1: Run Application

```bash
# Build
mvn clean package

# Run with dev profile (human-readable logs)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or run with prod profile (JSON logs)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Step 2: Test Trace Propagation

```bash
# Call the trace test endpoint
curl http://localhost:8080/api/trace/start
```

**Expected Output (dev profile):**
```
2024-01-15 10:30:45.123 [abc123/span-001] INFO  c.e.r.controller.TraceController - Start endpoint called
2024-01-15 10:30:45.156 [abc123/span-002] INFO  c.e.r.controller.TraceController - Middle endpoint called
2024-01-15 10:30:45.189 [abc123/span-003] INFO  c.e.r.controller.TraceController - End endpoint called
2024-01-15 10:30:45.201 [abc123/span-002] INFO  c.e.r.controller.TraceController - Middle endpoint completed
2024-01-15 10:30:45.215 [abc123/span-001] INFO  c.e.r.controller.TraceController - Start endpoint completed
```

Notice: **Same traceId (abc123)** across all calls!

**Expected Output (prod profile - JSON):**
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "c.e.r.controller.TraceController",
  "message": "Start endpoint called",
  "application": "resilience4j-demo",
  "environment": "prod",
  "traceId": "abc123",
  "spanId": "span-001",
  "endpoint": "/api/trace/start"
}
```

### Step 3: Generate Test Traffic

```bash
# Generate multiple requests
for i in {1..10}; do
  curl http://localhost:8080/api/trace/start
  sleep 0.5
done
```

### Step 4: Check Log Files

```bash
# View JSON logs
cat logs/application.json | jq '.'

# Filter by trace ID
cat logs/application.json | jq 'select(.traceId == "abc123")'

# Count logs by level
cat logs/application.json | jq -r '.level' | sort | uniq -c
```

---

## Lab Verification Checklist

- [ ] Sleuth dependency added
- [ ] Logback configuration created
- [ ] Application starts successfully
- [ ] Trace IDs appear in logs
- [ ] Trace IDs are consistent across related calls
- [ ] JSON logging works in prod profile
- [ ] Structured arguments are captured
- [ ] MDC context is preserved
- [ ] Log files are created with rotation
- [ ] Async logging improves performance

---

## Query Logs in Azure Monitor

If you have Application Insights configured:

```kql
// All logs with specific trace ID
traces
| where customDimensions.traceId == "abc123"
| order by timestamp asc
| project 
    timestamp,
    message,
    customDimensions.spanId,
    customDimensions.endpoint

// Log distribution by level
traces
| where timestamp > ago(1h)
| summarize count() by severityLevel
| render piechart

// Slow requests (find by trace duration)
requests
| where timestamp > ago(1h)
| order by duration desc
| take 10
| project 
    timestamp,
    name,
    duration,
    operation_Id  // This is the trace ID
```

---

## Troubleshooting

**Trace IDs not appearing:**
- Verify Sleuth dependency is added
- Check that Sleuth is enabled in configuration
- Ensure logging pattern includes %X{traceId}

**JSON logs malformed:**
- Verify logstash-logback-encoder dependency
- Check logback-spring.xml syntax
- Ensure correct profile is active

**Trace not propagating across services:**
- Verify RestTemplate is autowired (not created manually)
- Check that propagation type is configured
- Ensure HTTP headers are not filtered

---

## Next Steps
- Integrate with Zipkin for visual trace analysis
- Set up centralized logging with ELK or Azure Monitor
- Implement custom baggage fields for business context
- Create alerts based on log patterns
