# Resilience4j Demo - Spring Boot Educational Project

A comprehensive Spring Boot application demonstrating **Resilience4j** patterns for building resilient cloud-native applications.

## 📚 Learning Objectives

This project teaches students the following resilience patterns:

1. **Retry Pattern** - Automatically retry failed operations
2. **Circuit Breaker Pattern** - Prevent cascading failures
3. **Rate Limiter Pattern** - Control request rates
4. **Health Endpoints** - Monitor application health and resilience metrics

## 🛠️ Technologies Used

- **Java 23**
- **Spring Boot 3.3.5**
- **Resilience4j 2.2.0**
- **Spring Boot Actuator**
- **Maven 3.9.9**

## 📋 Prerequisites

- Java 23 or higher
- Maven 3.9.9 or higher
- Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)

## 🚀 Getting Started

### Option 1: Run with Maven (Traditional)

```powershell
cd resilience4j-demo
mvn clean install
mvn spring-boot:run
```

### Option 2: Run with Docker (Recommended for Production)

```powershell
cd resilience4j-demo

# Build and test using PowerShell script
.\build-docker.ps1

# Or build manually
docker build -t resilience4j-demo:latest .
docker run -p 8080:8080 resilience4j-demo:latest

# Or use Docker Compose
docker-compose up -d
```

### Option 3: Deploy to Kubernetes

```powershell
# Apply Kubernetes manifests
kubectl apply -f k8s/deployment.yaml

# Port forward to access locally
kubectl port-forward -n resilience4j-demo deployment/resilience4j-demo 8080:8080
```

The application will start on `http://localhost:8080`

### Verify Installation

Open your browser and visit:
- Application: http://localhost:8080/actuator/health
- Circuit Breakers: http://localhost:8080/actuator/circuitbreakers
- Rate Limiters: http://localhost:8080/actuator/ratelimiters

## 🎯 API Endpoints

### Retry Pattern Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/retry/demo` | Basic retry demo (use `?fail=true` to simulate failure) |
| GET | `/api/retry/success` | Always succeeds |
| GET | `/api/retry/fail` | Always fails (demonstrates fallback) |
| GET | `/api/retry/unreliable` | Random success/failure |
| GET | `/api/retry/status` | Get current retry status |
| POST | `/api/retry/reset` | Reset retry counter |

**Example:**
```powershell
# Success case
iwr http://localhost:8080/api/retry/demo

# Failure case (triggers retry)
iwr http://localhost:8080/api/retry/demo?fail=true

# Unreliable service
iwr http://localhost:8080/api/retry/unreliable
```

### Circuit Breaker Pattern Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/circuit-breaker/demo` | Basic circuit breaker demo |
| GET | `/api/circuit-breaker/success` | Always succeeds |
| GET | `/api/circuit-breaker/fail` | Fails to trigger circuit opening |
| GET | `/api/circuit-breaker/payment` | Payment processing demo |
| GET | `/api/circuit-breaker/slow` | Slow service simulation |
| POST | `/api/circuit-breaker/trigger-open` | Trigger circuit to open |
| GET | `/api/circuit-breaker/status` | Get circuit breaker status |
| POST | `/api/circuit-breaker/reset` | Reset counter |

**Example:**
```powershell
# Success case
curl http://localhost:8080/api/circuit-breaker/demo

# Trigger multiple failures to open circuit
curl -X POST http://localhost:8080/api/circuit-breaker/trigger-open

# Now try to call - circuit is OPEN, fallback will execute
curl http://localhost:8080/api/circuit-breaker/demo

# Payment processing
curl "http://localhost:8080/api/circuit-breaker/payment?amount=150.00"
```

### Rate Limiter Pattern Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/rate-limiter/demo` | Basic rate limiter (10 req/sec) |
| GET | `/api/rate-limiter/api` | API service (5 req/sec) |
| GET | `/api/rate-limiter/premium` | Premium service (100 req/sec) |
| GET | `/api/rate-limiter/burst-test` | Test with burst requests |
| GET | `/api/rate-limiter/status` | Get rate limiter status |
| POST | `/api/rate-limiter/reset` | Reset counter |

**Example:**
```powershell
# Single request
curl http://localhost:8080/api/rate-limiter/demo

# Burst test (15 rapid requests)
curl "http://localhost:8080/api/rate-limiter/burst-test?requestCount=15"

# API service rate limiting
curl "http://localhost:8080/api/rate-limiter/api?userId=student123"
```

### Combined Patterns Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/combined/demo` | All patterns combined |
| POST | `/api/combined/payment` | Payment with all patterns |
| POST | `/api/combined/database` | Database with CB + Retry |
| POST | `/api/combined/order` | Order processing with all patterns |
| GET | `/api/combined/stress-test` | Stress test all patterns |
| GET | `/api/combined/status` | Get combined status |
| POST | `/api/combined/reset` | Reset all counters |

**Example:**
```powershell
# Combined patterns demo
curl "http://localhost:8080/api/combined/demo?amount=100.00"

# Stress test
curl "http://localhost:8080/api/combined/stress-test?requestCount=25"

# Process order
curl -X POST "http://localhost:8080/api/combined/order?orderId=ORD-12345"
```

## 📊 Health & Monitoring Endpoints

Spring Boot Actuator provides comprehensive monitoring:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall application health |
| `/actuator/circuitbreakers` | Circuit breaker states and metrics |
| `/actuator/circuitbreakerevents` | Recent circuit breaker events |
| `/actuator/ratelimiters` | Rate limiter states and metrics |
| `/actuator/retries` | Retry metrics |
| `/actuator/metrics` | General application metrics |
| `/actuator/prometheus` | Prometheus-formatted metrics |

**Example:**
```powershell
# Check health
curl http://localhost:8080/actuator/health

# View circuit breaker status
curl http://localhost:8080/actuator/circuitbreakers

# View rate limiter status
curl http://localhost:8080/actuator/ratelimiters
```

## 🎓 Learning Exercises

### Exercise 1: Understanding Retry Pattern

1. Call the retry endpoint that always succeeds:
   ```powershell
   curl http://localhost:8080/api/retry/success
   ```
   **Expected:** Success on first attempt

2. Call the retry endpoint with failures:
   ```powershell
   curl http://localhost:8080/api/retry/fail
   ```
   **Expected:** Multiple retry attempts, then fallback

3. Call the unreliable endpoint multiple times:
   ```powershell
   for($i=1; $i -le 5; $i++) { curl http://localhost:8080/api/retry/unreliable; Start-Sleep -Seconds 1 }
   ```
   **Expected:** See different retry behaviors

**Questions to Consider:**
- How many times does it retry before giving up?
- What happens when all retries are exhausted?
- How does exponential backoff work?

### Exercise 2: Understanding Circuit Breaker Pattern

1. Make successful calls:
   ```powershell
   for($i=1; $i -le 5; $i++) { curl http://localhost:8080/api/circuit-breaker/success }
   ```
   **Expected:** Circuit remains CLOSED

2. Trigger circuit to open:
   ```powershell
   curl -X POST http://localhost:8080/api/circuit-breaker/trigger-open
   ```
   **Expected:** Circuit opens after failure threshold

3. Check circuit state:
   ```powershell
   curl http://localhost:8080/actuator/circuitbreakers
   ```
   **Expected:** See circuit state (CLOSED/OPEN/HALF_OPEN)

4. Try calling while circuit is open:
   ```powershell
   curl http://localhost:8080/api/circuit-breaker/demo
   ```
   **Expected:** Immediate fallback, no actual call made

**Questions to Consider:**
- What is the failure rate threshold?
- How long does the circuit stay open?
- What is the HALF_OPEN state used for?

### Exercise 3: Understanding Rate Limiter Pattern

1. Test within rate limit:
   ```powershell
   curl http://localhost:8080/api/rate-limiter/demo
   ```
   **Expected:** Success

2. Test burst of requests:
   ```powershell
   curl "http://localhost:8080/api/rate-limiter/burst-test?requestCount=15"
   ```
   **Expected:** First 10 succeed, rest are rate limited

3. Check rate limiter status:
   ```powershell
   curl http://localhost:8080/actuator/ratelimiters
   ```
   **Expected:** See available permissions

**Questions to Consider:**
- How many requests are allowed per second?
- What happens to requests that exceed the limit?
- How does the limit refresh?

### Exercise 4: Combined Patterns

1. Run stress test:
   ```powershell
   curl "http://localhost:8080/api/combined/stress-test?requestCount=30"
   ```
   **Expected:** See all patterns in action

2. Monitor the health:
   ```powershell
   curl http://localhost:8080/actuator/health
   ```
   **Expected:** See health of all resilience components

**Questions to Consider:**
- In what order are the patterns applied?
- How do they work together?
- What happens when multiple patterns trigger?

## ⚙️ Configuration

The resilience patterns are configured in `src/main/resources/application.yml`:

### Retry Configuration
```yaml
resilience4j.retry.instances.backendService:
  maxAttempts: 3
  waitDuration: 1s
  enableExponentialBackoff: true
  exponentialBackoffMultiplier: 2
```

### Circuit Breaker Configuration
```yaml
resilience4j.circuitbreaker.instances.backendService:
  slidingWindowSize: 10
  minimumNumberOfCalls: 5
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
```

### Rate Limiter Configuration
```yaml
resilience4j.ratelimiter.instances.default:
  limitForPeriod: 10
  limitRefreshPeriod: 1s
  timeoutDuration: 0s
```

## 📖 Key Concepts

### 1. Retry Pattern
- **Purpose:** Handle transient failures
- **Use Case:** Network glitches, temporary service unavailability
- **Configuration:** Max attempts, wait duration, exponential backoff

### 2. Circuit Breaker Pattern
- **Purpose:** Prevent cascading failures
- **States:** CLOSED → OPEN → HALF_OPEN
- **Use Case:** Failing external services, overloaded systems
- **Configuration:** Failure threshold, wait duration, sliding window

### 3. Rate Limiter Pattern
- **Purpose:** Control request rate
- **Use Case:** API quotas, resource protection, fair usage
- **Configuration:** Requests per period, timeout

### 4. Health Endpoints
- **Purpose:** Monitor application and resilience components
- **Use Case:** Observability, alerting, debugging
- **Provides:** Real-time metrics, state information

## 🔍 Troubleshooting

### Application won't start
```powershell
# Check Java version
java -version

# Clean and rebuild
mvn clean install -U
```

### Port 8080 already in use
Change the port in `application.yml`:
```yaml
server:
  port: 8081
```

### Circuit breaker not opening
- Make enough calls to reach `minimumNumberOfCalls`
- Ensure failure rate exceeds `failureRateThreshold`
- Check logs for circuit breaker events

## 📚 Additional Resources

### Project Documentation
- **[LECTURE_NOTES.md](LECTURE_NOTES.md)** - Comprehensive lecture notes covering:
  - Why retries matter in distributed systems
  - Resilience4j retry strategies (fixed, exponential backoff, jitter)
  - Circuit breaker states and integration
  - Liveness vs Readiness health probes
  - Token bucket vs Leaky bucket rate limiting algorithms

- **[DOCKER_CICD_GUIDE.md](DOCKER_CICD_GUIDE.md)** - Docker and CI/CD documentation:
  - Docker build and deployment
  - Docker Compose usage
  - GitHub Actions CI/CD pipeline
  - GitLab CI/CD pipeline
  - Kubernetes deployment
  - Monitoring and troubleshooting

- **[QUICK_START.md](QUICK_START.md)** - Quick reference for students

### External Resources
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)

## 🤝 Contributing

This is an educational project. Feel free to:
- Add more examples
- Improve documentation
- Create additional exercises
- Report issues

## 📝 License

This project is created for educational purposes.

## 👨‍🏫 For Instructors

### Teaching Points

1. **Retry Pattern**
   - Explain transient vs permanent failures
   - Demonstrate exponential backoff
   - Show fallback mechanisms

2. **Circuit Breaker**
   - Explain the state machine (CLOSED/OPEN/HALF_OPEN)
   - Demonstrate failure threshold
   - Show how it prevents cascading failures

3. **Rate Limiter**
   - Explain fair resource usage
   - Demonstrate different tier limits
   - Show burst handling

4. **Combined Patterns**
   - Explain pattern composition
   - Demonstrate order of execution
   - Show real-world scenarios

### Suggested Demos

1. **Live Demo:** Use Postman or curl to show patterns in action
2. **Monitoring:** Display Actuator endpoints on screen
3. **Failure Scenarios:** Simulate service failures
4. **Recovery:** Show how services recover

---

**Happy Learning! 🚀**
