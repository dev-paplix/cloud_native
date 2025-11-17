# Lecture Notes: Resilience Patterns in Distributed Systems

## Table of Contents
1. [Retry & Circuit Breaker](#retry--circuit-breaker)
2. [Health Endpoints](#health-endpoints)
3. [Rate Limiting](#rate-limiting)

---

## Retry & Circuit Breaker

### Why Retries Matter in Distributed Systems

In distributed systems, failures are inevitable. Services communicate over networks that can be unreliable, and dependencies may become temporarily unavailable. Understanding when and how to retry failed operations is critical for building resilient applications.

#### Common Failure Scenarios in Distributed Systems

1. **Transient Failures** (Should Retry)
   - Network packet loss or timeout
   - Temporary service unavailability (e.g., container restarting)
   - Database connection pool exhaustion (temporary)
   - Rate limiting by external API (temporary)
   - Service temporarily overloaded but recovering

2. **Permanent Failures** (Should NOT Retry)
   - Invalid authentication credentials (401)
   - Resource not found (404)
   - Bad request format (400)
   - Service permanently removed (410)
   - Authorization denied (403)

3. **Why Retries Matter**
   - **Improved Reliability**: Mask transient failures from end users
   - **Better User Experience**: Avoid unnecessary error messages
   - **Cost Efficiency**: Prevent cascading failures that require manual intervention
   - **Service Level Objectives (SLOs)**: Meet availability targets (e.g., 99.9% uptime)

#### The Dangers of Naive Retries

**Example of Poor Retry Strategy:**
```java
// ❌ WRONG - This can make things worse!
public String callService() {
    for (int i = 0; i < 100; i++) {
        try {
            return httpClient.get("/api/data");
        } catch (Exception e) {
            // Retry immediately without backoff
            continue;
        }
    }
    throw new RuntimeException("Failed after 100 retries");
}
```

**Problems:**
- **Retry Storm**: All clients retry simultaneously, overwhelming the recovering service
- **No Backoff**: Immediate retries don't give the service time to recover
- **Resource Exhaustion**: Threads blocked waiting, consuming memory
- **Cascading Failures**: Problem spreads to other services

---

### Resilience4j Retry Strategies

Resilience4j provides sophisticated retry mechanisms to handle failures intelligently.

#### 1. Fixed Delay Retry

Waits a fixed amount of time between retry attempts.

**Configuration:**
```yaml
resilience4j.retry.instances.fixedRetry:
  maxAttempts: 3
  waitDuration: 2s
  enableExponentialBackoff: false
```

**Code Example:**
```java
@Retry(name = "fixedRetry")
public String callServiceWithFixedRetry() {
    return externalService.getData();
}
```

**Use Case:**
- When service recovery time is predictable
- For services with consistent response times
- Testing and development environments

**Timing Example:**
- Attempt 1: t=0s (fails)
- Wait: 2s
- Attempt 2: t=2s (fails)
- Wait: 2s
- Attempt 3: t=4s (success)

---

#### 2. Exponential Backoff

Increases wait time exponentially between retries. This is the **recommended strategy** for most production scenarios.

**Configuration:**
```yaml
resilience4j.retry.instances.exponentialRetry:
  maxAttempts: 5
  waitDuration: 1s
  enableExponentialBackoff: true
  exponentialBackoffMultiplier: 2
  maxDelay: 10s  # Cap maximum delay
```

**Code Example:**
```java
@Retry(name = "exponentialRetry")
public String callServiceWithExponentialBackoff() {
    return externalService.getData();
}
```

**Mathematical Formula:**
```
waitTime = waitDuration × (exponentialBackoffMultiplier ^ attemptNumber)
```

**Timing Example (multiplier = 2, waitDuration = 1s):**
- Attempt 1: t=0s (fails)
- Wait: 1s × 2^0 = 1s
- Attempt 2: t=1s (fails)
- Wait: 1s × 2^1 = 2s
- Attempt 3: t=3s (fails)
- Wait: 1s × 2^2 = 4s
- Attempt 4: t=7s (fails)
- Wait: 1s × 2^3 = 8s
- Attempt 5: t=15s (success or give up)

**Benefits:**
- Gives service more time to recover with each retry
- Reduces load on failing service
- Prevents retry storms
- Better for cascading failure scenarios

**Real-World Example:**
AWS SDK and Google Cloud SDKs use exponential backoff by default.

---

#### 3. Exponential Backoff with Jitter

Adds randomness to backoff delays to prevent synchronized retries from multiple clients.

**The "Thundering Herd" Problem:**
When 1000 clients all fail at the same time and retry with exponential backoff:
- All 1000 retry after 1 second
- All 1000 retry after 3 seconds
- All 1000 retry after 7 seconds
- Service gets hit with waves of synchronized traffic

**Solution - Add Jitter:**
```yaml
resilience4j.retry.instances.jitteredRetry:
  maxAttempts: 5
  waitDuration: 1s
  enableExponentialBackoff: true
  exponentialBackoffMultiplier: 2
  enableRandomizedWait: true
  randomizedWaitFactor: 0.5  # ±50% randomization
```

**Code Implementation:**
```java
@Retry(name = "jitteredRetry")
public String callServiceWithJitter() {
    return externalService.getData();
}
```

**How Jitter Works:**
```
baseDelay = waitDuration × (multiplier ^ attemptNumber)
jitter = baseDelay × randomizedWaitFactor × random(-1, 1)
actualDelay = baseDelay + jitter
```

**Example with 50% Jitter:**
- Client A: waits 1.2s, 2.8s, 5.1s, 9.3s
- Client B: waits 0.8s, 3.4s, 4.6s, 7.9s
- Client C: waits 1.5s, 2.1s, 5.8s, 8.2s

**Benefits:**
- Spreads retry load over time
- Prevents synchronized retry storms
- More graceful recovery for services
- Recommended by AWS, Google Cloud, and Microsoft Azure

---

### Circuit Breaker States and Integration

The Circuit Breaker pattern prevents an application from repeatedly trying to execute an operation that's likely to fail, allowing time for the fault to be corrected.

#### Circuit Breaker States

```
    Success ✓
   ┌─────────┐
   │ CLOSED  │ ◄── Normal operation, requests pass through
   └────┬────┘
        │ Failure rate exceeds threshold
        │
   ┌────▼────┐
   │  OPEN   │ ◄── Requests blocked immediately, fallback executed
   └────┬────┘
        │ After waitDuration timeout
        │
   ┌────▼────────┐
   │ HALF_OPEN   │ ◄── Testing if service recovered
   └─────────────┘
        │
        ├── Success → Back to CLOSED
        └── Failure → Back to OPEN
```

#### State Descriptions

1. **CLOSED (Normal State)**
   - All requests are allowed through
   - Failures are counted in a sliding window
   - If failure rate exceeds threshold → transition to OPEN

2. **OPEN (Fault Detected)**
   - Requests are **immediately rejected** without calling the service
   - Fallback method is executed
   - After `waitDurationInOpenState` → transition to HALF_OPEN

3. **HALF_OPEN (Testing Recovery)**
   - Limited number of test requests are allowed
   - If requests succeed → transition to CLOSED
   - If requests fail → transition back to OPEN

#### Configuration Example

```yaml
resilience4j.circuitbreaker.instances.paymentService:
  # Health Indicator
  registerHealthIndicator: true
  
  # Sliding Window Configuration
  slidingWindowType: COUNT_BASED  # or TIME_BASED
  slidingWindowSize: 10           # Last 10 calls
  minimumNumberOfCalls: 5         # Need at least 5 calls before calculating
  
  # Failure Threshold
  failureRateThreshold: 50        # Open circuit if 50% fail
  slowCallRateThreshold: 80       # Consider slow calls as failures
  slowCallDurationThreshold: 3s   # Calls slower than 3s are "slow"
  
  # State Transitions
  waitDurationInOpenState: 10s              # Stay OPEN for 10s
  permittedNumberOfCallsInHalfOpenState: 3  # Test with 3 calls
  automaticTransitionFromOpenToHalfOpenEnabled: true
  
  # What counts as failure
  recordExceptions:
    - java.io.IOException
    - java.util.concurrent.TimeoutException
    - org.springframework.web.client.HttpServerErrorException
  ignoreExceptions:
    - java.lang.IllegalArgumentException    # Don't count as failure
```

#### Code Example with Circuit Breaker

```java
@Service
public class PaymentService {
    
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        // Call external payment gateway
        return paymentGateway.charge(request);
    }
    
    // Fallback method - same signature + Exception parameter
    private PaymentResponse paymentFallback(PaymentRequest request, Exception ex) {
        logger.error("Payment failed, using fallback. Reason: {}", ex.getMessage());
        
        if (ex instanceof CallNotPermittedException) {
            // Circuit is OPEN
            return PaymentResponse.queued(request, "Payment gateway unavailable. Queued for later.");
        } else {
            // Individual failure
            return PaymentResponse.failed(request, "Payment failed. Please try again.");
        }
    }
}
```

#### Combining Retry and Circuit Breaker

**Annotation Order Matters!**

```java
@Service
public class OrderService {
    
    // ✅ CORRECT ORDER
    @CircuitBreaker(name = "orderService")  // First: Check if circuit allows call
    @Retry(name = "orderRetry")              // Second: Retry if circuit is closed
    public OrderResponse placeOrder(Order order) {
        return orderApi.submit(order);
    }
    
    // ❌ WRONG ORDER
    @Retry(name = "orderRetry")              // Would retry even when circuit is open
    @CircuitBreaker(name = "orderService")
    public OrderResponse wrongOrder(Order order) {
        return orderApi.submit(order);
    }
}
```

**Execution Flow (Correct Order):**
1. Circuit Breaker checks state
   - If OPEN → Execute fallback immediately (no retry)
   - If CLOSED or HALF_OPEN → Proceed to retry logic
2. Retry executes the call
   - If fails → Retry with backoff
   - Failures are recorded by Circuit Breaker
3. If failure threshold exceeded → Circuit opens

---

## Health Endpoints

### Liveness vs Readiness in Spring Boot Actuator

Spring Boot Actuator provides health endpoints that are essential for Kubernetes and container orchestration platforms.

#### Understanding Liveness and Readiness

| Aspect | Liveness | Readiness |
|--------|----------|-----------|
| **Question** | Is the app alive? | Is the app ready to serve traffic? |
| **Purpose** | Detect if app is stuck/frozen | Detect if app can handle requests |
| **On Failure** | Restart the container | Stop sending traffic, keep running |
| **Example Failure** | Deadlock, infinite loop | Database connection lost, cache warming |
| **Kubernetes Action** | `kubectl restart pod` | Remove from service load balancer |

#### Configuration

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true  # Enable liveness and readiness probes
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
    circuitbreakers:
      enabled: true
    ratelimiters:
      enabled: true
```

#### Health Endpoint URLs

```bash
# Overall health (aggregated)
GET /actuator/health

# Liveness probe (should the app be restarted?)
GET /actuator/health/liveness

# Readiness probe (should traffic be routed?)
GET /actuator/health/readiness
```

#### Response Examples

**Overall Health:**
```json
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "paymentService": {
          "status": "UP",
          "details": {
            "state": "CLOSED",
            "failureRate": "12.5%",
            "slowCallRate": "0.0%"
          }
        }
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 250000000000,
        "threshold": 10485760
      }
    },
    "livenessState": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    },
    "rateLimiters": {
      "status": "UP",
      "details": {
        "apiService": {
          "status": "UP",
          "availablePermissions": 5,
          "numberOfWaitingThreads": 0
        }
      }
    }
  }
}
```

**Liveness Probe:**
```json
{
  "status": "UP"
}
```

**Readiness Probe:**
```json
{
  "status": "UP"
}
```

#### Custom Health Indicators

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            // Test database connectivity
            conn.isValid(2); // 2 second timeout
            
            return Health.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("validationQuery", "Connection.isValid()")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
}
```

#### Kubernetes Integration

```yaml
# deployment.yaml
apiVersion: v1
kind: Pod
metadata:
  name: resilience-demo
spec:
  containers:
  - name: app
    image: resilience4j-demo:latest
    ports:
    - containerPort: 8080
    
    # Liveness Probe - Restart if fails
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 30  # Wait 30s after startup
      periodSeconds: 10        # Check every 10s
      timeoutSeconds: 5        # Timeout after 5s
      failureThreshold: 3      # Restart after 3 consecutive failures
    
    # Readiness Probe - Route traffic only if ready
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 10  # Wait 10s after startup
      periodSeconds: 5         # Check every 5s
      timeoutSeconds: 3        # Timeout after 3s
      failureThreshold: 2      # Remove from LB after 2 failures
```

---

## Rate Limiting

### Token Bucket vs Leaky Bucket Algorithms

Rate limiting controls the rate at which operations are executed to prevent system overload and ensure fair resource usage.

#### 1. Token Bucket Algorithm

**How it Works:**
- Bucket holds tokens (permissions to make requests)
- Tokens are added at a fixed rate
- Each request consumes one token
- If no tokens available → request is rejected or queued

**Visual Representation:**
```
Tokens added at rate: 10 tokens/second
Bucket capacity: 10 tokens

Time    Tokens  Request  Result
0s      10      ✓        Accept (9 remaining)
0.1s    10      ✓        Accept (8 remaining)
0.2s    10      ✓        Accept (7 remaining)
...
1.0s    7       ✓        Accept (6 remaining)
1.1s    7       ✗        Reject (bucket refilling)
1.5s    9       ✓        Accept (8 remaining)
```

**Resilience4j Configuration:**
```yaml
resilience4j.ratelimiter.instances.tokenBucket:
  limitForPeriod: 10              # 10 tokens
  limitRefreshPeriod: 1s          # Refresh every 1 second
  timeoutDuration: 0s             # Don't wait, fail immediately
```

**Code Example:**
```java
@RateLimiter(name = "tokenBucket", fallbackMethod = "rateLimitFallback")
public ApiResponse handleRequest() {
    return processRequest();
}

private ApiResponse rateLimitFallback(Exception ex) {
    return ApiResponse.error("Rate limit exceeded. Try again later.");
}
```

**Characteristics:**
- ✅ Allows bursts up to bucket capacity
- ✅ Simple to implement
- ✅ Memory efficient
- ❌ Can allow sudden traffic spikes

**Use Cases:**
- API rate limiting (e.g., "100 requests per minute")
- User action throttling
- Resource protection

---

#### 2. Leaky Bucket Algorithm

**How it Works:**
- Requests enter a bucket (queue)
- Requests "leak" out at a constant rate
- If bucket is full → new requests are dropped
- Smooths out bursts into steady stream

**Visual Representation:**
```
Leak rate: 5 requests/second
Bucket capacity: 20 requests

Time    Queue   Incoming  Processed  Dropped
0s      0       ▓▓▓▓▓     ▓▓▓▓▓     0
        (5)
0.2s    5       ▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓     0
        (10)
0.4s    10      ▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓     0
        (15)
0.6s    15      ▓▓▓▓▓▓▓▓▓▓ ▓▓▓▓▓     ▓▓▓▓▓
        (20)             (5 dropped - bucket full)
```

**Implementation (Conceptual):**
```java
public class LeakyBucket {
    private final Queue<Request> queue;
    private final int capacity;
    private final int leakRate; // requests per second
    
    public boolean allowRequest(Request req) {
        // Remove leaked requests
        processLeakedRequests();
        
        if (queue.size() < capacity) {
            queue.add(req);
            return true;
        }
        return false; // Bucket full, drop request
    }
    
    private void processLeakedRequests() {
        long now = System.currentTimeMillis();
        int toProcess = calculateLeaked(now);
        
        for (int i = 0; i < toProcess && !queue.isEmpty(); i++) {
            Request req = queue.poll();
            processRequest(req);
        }
    }
}
```

**Characteristics:**
- ✅ Smooth, constant output rate
- ✅ Prevents sudden traffic spikes
- ✅ Predictable resource usage
- ❌ May increase latency (queuing)
- ❌ More complex to implement

**Use Cases:**
- Network traffic shaping
- Video streaming (constant bitrate)
- Database write throttling

---

#### Comparison: Token Bucket vs Leaky Bucket

| Feature | Token Bucket | Leaky Bucket |
|---------|-------------|--------------|
| **Traffic Pattern** | Allows bursts | Smooths bursts |
| **Output Rate** | Variable (up to bucket size) | Constant |
| **Memory** | Low (just counter) | Higher (queue) |
| **Latency** | Lower | Higher (queuing delay) |
| **Implementation** | Simpler | More complex |
| **Best For** | API rate limiting | Traffic shaping |

---

### Resilience4j Rate Limiter Deep Dive

#### Configuration Options

```yaml
resilience4j.ratelimiter.instances.apiService:
  # Basic Configuration
  limitForPeriod: 10              # Number of permissions available
  limitRefreshPeriod: 1s          # How often to refresh permissions
  timeoutDuration: 0s             # Time thread waits for permission
  
  # Advanced Configuration
  registerHealthIndicator: true   # Show in /actuator/health
  eventConsumerBufferSize: 100    # Size of event buffer
  
  # Metrics
  allowHealthIndicatorToFail: false
```

#### Multiple Rate Limit Tiers

```yaml
resilience4j.ratelimiter.instances:
  # Free tier
  free:
    limitForPeriod: 10
    limitRefreshPeriod: 1s
  
  # Standard tier
  standard:
    limitForPeriod: 100
    limitRefreshPeriod: 1s
  
  # Premium tier
  premium:
    limitForPeriod: 1000
    limitRefreshPeriod: 1s
```

**Usage:**
```java
@RestController
public class ApiController {
    
    @GetMapping("/api/free")
    @RateLimiter(name = "free")
    public Response freeEndpoint() {
        return process();
    }
    
    @GetMapping("/api/premium")
    @RateLimiter(name = "premium")
    public Response premiumEndpoint() {
        return process();
    }
}
```

#### Dynamic Rate Limiting by User

```java
@Service
public class DynamicRateLimiterService {
    
    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;
    
    public ApiResponse handleRequest(String userId, Supplier<ApiResponse> action) {
        // Determine tier based on user
        String tier = getUserTier(userId); // "free", "standard", or "premium"
        
        // Get appropriate rate limiter
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(tier);
        
        // Execute with rate limiting
        return Try.ofSupplier(
            RateLimiter.decorateSupplier(rateLimiter, action)
        ).recover(RequestNotPermitted.class, ex -> {
            return ApiResponse.error("Rate limit exceeded for " + tier + " tier");
        }).get();
    }
    
    private String getUserTier(String userId) {
        // Look up user's subscription tier
        return userService.getTier(userId);
    }
}
```

#### Monitoring Rate Limiters

```bash
# Check rate limiter status
curl http://localhost:8080/actuator/ratelimiters

# Response:
{
  "rateLimiters": {
    "apiService": {
      "availablePermissions": 7,
      "numberOfWaitingThreads": 0
    },
    "premium": {
      "availablePermissions": 998,
      "numberOfWaitingThreads": 0
    }
  }
}
```

---

## Summary

### Key Takeaways

1. **Retry Strategies**
   - Use exponential backoff with jitter for production
   - Avoid naive retries that can cause retry storms
   - Only retry transient failures, not permanent errors

2. **Circuit Breaker**
   - Prevents cascading failures
   - Three states: CLOSED, OPEN, HALF_OPEN
   - Combine with retry (circuit breaker first, then retry)

3. **Health Endpoints**
   - Liveness: "Is the app alive?" (restart if fails)
   - Readiness: "Can it serve traffic?" (remove from load balancer)
   - Essential for Kubernetes deployments

4. **Rate Limiting**
   - Token bucket: allows bursts, simple
   - Leaky bucket: constant rate, complex
   - Use for API quotas, resource protection, fairness

---

**Next Steps:**
- Experiment with the code examples in `resilience4j-demo`
- Adjust configuration values and observe behavior
- Monitor using Actuator endpoints
- Deploy to Kubernetes and test health probes
