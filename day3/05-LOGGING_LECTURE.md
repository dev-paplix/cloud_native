# Lecture: Logging & Troubleshooting with Distributed Tracing

## Duration: 30 minutes

## Learning Objectives
- Understand structured logging principles
- Implement correlation IDs for distributed tracing
- Configure Spring Cloud Sleuth for trace context propagation
- Set up JSON logging with Logback
- Debug requests across multiple microservices

---

## 1. Structured Logging Principles (7 min)

### What is Structured Logging?

Structured logging is the practice of producing logs in a consistent, machine-readable format (typically JSON) rather than free-form text.

**Traditional Logging:**
```
2024-01-15 10:30:45 INFO - User john@example.com created order ORDER-12345 with amount $99.99
```

**Structured Logging:**
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "message": "Order created",
  "userId": "john@example.com",
  "orderId": "ORDER-12345",
  "amount": 99.99,
  "currency": "USD",
  "traceId": "abc123",
  "spanId": "def456"
}
```

### Benefits

1. **Queryable**: Easy to search and filter
2. **Aggregatable**: Simple to aggregate metrics from logs
3. **Parseable**: No regex needed to extract fields
4. **Contextual**: Rich metadata included
5. **Tool-friendly**: Works with log aggregation tools (ELK, Splunk, Azure Monitor)

### Key Principles

✅ **Use consistent field names**
- `timestamp` not `time`, `date`, or `ts`
- `level` not `severity` or `log_level`

✅ **Include contextual data**
- User ID, session ID, correlation ID
- Request/response data
- Environment information

✅ **Avoid logging sensitive data**
- No passwords, tokens, or PII
- Mask credit card numbers
- Redact sensitive fields

✅ **Use appropriate log levels**
- **TRACE**: Very detailed (method entry/exit)
- **DEBUG**: Detailed diagnostic information
- **INFO**: Important business events
- **WARN**: Potentially harmful situations
- **ERROR**: Error events that might still allow the app to continue
- **FATAL**: Very severe errors that lead to termination

---

## 2. Correlation IDs & Distributed Tracing (8 min)

### The Problem

In microservices, a single user request may flow through multiple services:

```
User → API Gateway → Order Service → Payment Service
                                  → Inventory Service
                                  → Notification Service
```

**Challenge**: How do you correlate logs across all services for a single request?

### Solution: Correlation IDs

A **correlation ID** (also called trace ID) is a unique identifier that:
- Is generated for each incoming request
- Flows through all services
- Is included in every log message
- Enables tracing the entire request path

### Trace Context Components

#### 1. **Trace ID**
- Unique identifier for the entire distributed transaction
- Same across all services for one request
- Example: `abc123-def456-ghi789`

#### 2. **Span ID**
- Unique identifier for each operation/service call
- Changes for each service in the path
- Example: `span-001`, `span-002`

#### 3. **Parent Span ID**
- Links child spans to parent
- Creates the call hierarchy

### W3C Trace Context Standard

The W3C Trace Context specification defines how trace context is propagated via HTTP headers:

**Headers:**
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             ^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^ ^^
             || trace-id                    span-id          flags
             version
```

### Example Flow

```
1. API Gateway receives request
   traceId: abc123
   spanId: span-001
   Log: [abc123/span-001] Received GET /orders

2. API Gateway calls Order Service
   traceId: abc123 (same)
   spanId: span-002 (new)
   parentSpanId: span-001
   Log: [abc123/span-002] Processing order

3. Order Service calls Payment Service
   traceId: abc123 (same)
   spanId: span-003 (new)
   parentSpanId: span-002
   Log: [abc123/span-003] Processing payment

4. Payment Service completes
   Log: [abc123/span-003] Payment successful

5. Order Service completes
   Log: [abc123/span-002] Order created

6. API Gateway returns response
   Log: [abc123/span-001] Response sent
```

**Result**: All logs with `abc123` can be correlated!

---

## 3. Spring Cloud Sleuth (7 min)

### What is Spring Cloud Sleuth?

Spring Cloud Sleuth provides distributed tracing for Spring Boot applications:
- Automatically generates trace and span IDs
- Propagates context across HTTP calls
- Integrates with logging frameworks
- Supports various backends (Zipkin, Jaeger, Azure App Insights)

### Key Features

1. **Auto-instrumentation** of:
   - HTTP requests/responses
   - Message channels (Kafka, RabbitMQ, Event Hub)
   - Scheduled tasks
   - Async operations

2. **Context propagation** via:
   - HTTP headers (W3C standard)
   - Message headers
   - Thread local storage

3. **Integration** with:
   - Logback/Log4j2
   - Application Insights
   - Zipkin
   - Brave

### Configuration

**Dependencies (pom.xml):**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>

<!-- Optional: for Zipkin export -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

**Application.yml:**
```yaml
spring:
  application:
    name: order-service
  
  sleuth:
    sampler:
      probability: 1.0  # Sample 100% of requests (reduce in production)
    
    # Trace ID and Span ID in logs
    enabled: true
    
    # Propagate context in HTTP headers
    propagation:
      type: w3c  # Use W3C Trace Context standard
    
    # Baggage - custom fields propagated with trace
    baggage:
      correlation-fields:
        - userId
        - sessionId
      remote-fields:
        - userId
        - sessionId

# Logging with trace IDs
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%X{traceId}/%X{spanId}] %-5level %logger{36} - %msg%n"
```

### Automatic Trace Propagation

Sleuth automatically propagates trace context through:

**HTTP Calls (using RestTemplate or WebClient):**
```java
@Service
public class OrderService {
    
    private final RestTemplate restTemplate;
    
    public Payment processPayment(Order order) {
        // Trace context automatically added to HTTP headers
        return restTemplate.postForObject(
            "http://payment-service/api/payments",
            order,
            Payment.class
        );
    }
}
```

**Message Queues:**
```java
@Service
public class OrderProducer {
    
    private final KafkaTemplate<String, Order> kafka;
    
    public void sendOrder(Order order) {
        // Trace context automatically added to message headers
        kafka.send("orders", order);
    }
}
```

### Custom Spans

Create custom spans for important operations:

```java
@Service
public class OrderService {
    
    private final Tracer tracer;
    
    public void processOrder(Order order) {
        Span span = tracer.nextSpan().name("process-order");
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            // Add tags for better searchability
            span.tag("orderId", order.getId());
            span.tag("amount", String.valueOf(order.getAmount()));
            
            // Your business logic
            doProcess(order);
            
        } finally {
            span.end();
        }
    }
}
```

---

## 4. JSON Logging with Logback (8 min)

### Why JSON Logging?

JSON logs are:
- **Structured**: Each field is explicitly defined
- **Searchable**: Easy to query specific fields
- **Parseable**: No regex needed
- **Tool-friendly**: Works with ELK, Splunk, Azure Monitor

### Logback JSON Configuration

**Add Dependency:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**Create logback-spring.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Console Appender - Human Readable (Development) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}/%X{spanId}] %-5level %logger{36} - %msg%n
            </pattern>
        </encoder>
    </appender>
    
    <!-- JSON Appender (Production) -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Add custom fields -->
            <customFields>
                {"application":"${spring.application.name}"}
            </customFields>
            
            <!-- Include MDC (Mapped Diagnostic Context) -->
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            
            <!-- Include caller data (expensive, use carefully) -->
            <includeCallerData>false</includeCallerData>
            
            <!-- Include context name -->
            <includeContext>true</includeContext>
            
            <!-- Timestamp format -->
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSX</timestampPattern>
        </encoder>
    </appender>
    
    <!-- File Appender with Rolling -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.json</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    
    <!-- Use different appenders based on profile -->
    <springProfile name="dev,local">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
    
    <springProfile name="prod,staging">
        <root level="INFO">
            <appender-ref ref="JSON"/>
            <appender-ref ref="FILE"/>
        </root>
    </springProfile>
</configuration>
```

### Example JSON Log Output

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "@version": "1",
  "message": "Order created successfully",
  "logger_name": "com.example.OrderService",
  "thread_name": "http-nio-8080-exec-1",
  "level": "INFO",
  "level_value": 20000,
  "application": "order-service",
  "traceId": "abc123-def456-ghi789",
  "spanId": "span-002",
  "userId": "john@example.com",
  "orderId": "ORDER-12345",
  "amount": 99.99,
  "currency": "USD"
}
```

### Structured Logging in Code

```java
@Slf4j
@Service
public class OrderService {
    
    public void createOrder(Order order) {
        // Add MDC context
        MDC.put("userId", order.getUserId());
        MDC.put("orderId", order.getId());
        
        try {
            log.info("Processing order", 
                kv("amount", order.getAmount()),
                kv("productId", order.getProductId()));
            
            processOrder(order);
            
            log.info("Order created successfully");
            
        } catch (Exception e) {
            log.error("Failed to create order", e,
                kv("errorCode", "ORDER_CREATION_FAILED"));
            throw e;
        } finally {
            MDC.clear();
        }
    }
    
    // Helper method for structured fields
    private static Object kv(String key, Object value) {
        return net.logstash.logback.argument.StructuredArguments.kv(key, value);
    }
}
```

---

## 5. Debugging Distributed Requests (5 min)

### Step-by-Step Debugging

1. **Identify the trace ID** from error message or logs
2. **Query logs** for all entries with that trace ID
3. **Order by timestamp** to see the request flow
4. **Identify the failure point**
5. **Examine context** before and after the failure

### Azure Monitor KQL Query

```kql
traces
| where timestamp > ago(1h)
| where customDimensions.traceId == "abc123-def456"
| order by timestamp asc
| project 
    timestamp,
    customDimensions.spanId,
    customDimensions.serviceName,
    message,
    severityLevel
```

### ELK Stack Query

```json
{
  "query": {
    "bool": {
      "must": [
        { "term": { "traceId": "abc123-def456" } },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  },
  "sort": [ { "@timestamp": "asc" } ]
}
```

### Best Practices

1. ✅ Always log entry/exit of important operations
2. ✅ Include relevant context (IDs, amounts, statuses)
3. ✅ Use consistent field names across services
4. ✅ Log at appropriate levels
5. ✅ Avoid logging in tight loops
6. ✅ Never log sensitive data
7. ✅ Use sampling in high-traffic scenarios

---

## Summary

### Key Takeaways

1. **Structured logging** enables powerful querying and analysis
2. **Correlation IDs** connect logs across distributed services
3. **Spring Cloud Sleuth** automates trace context propagation
4. **JSON logging** is machine-readable and tool-friendly
5. **Proper instrumentation** is essential for debugging production issues

### Logging Checklist

- [ ] Use structured (JSON) logging format
- [ ] Include trace ID and span ID in all logs
- [ ] Add relevant business context to logs
- [ ] Configure different log levels per environment
- [ ] Implement log rotation and retention
- [ ] Never log sensitive data
- [ ] Test log aggregation and querying

---

## References
- [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth)
- [Logback Documentation](http://logback.qos.ch/manual/)
- [Logstash Logback Encoder](https://github.com/logfellow/logstash-logback-encoder)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
