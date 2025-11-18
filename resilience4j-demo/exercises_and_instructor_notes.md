# Hands-On Student Exercises & Instructor Notes  
**Project:** `resilience4j-demo`  
**Tech:** Spring Boot, Resilience4j (Retry, Circuit Breaker, RateLimiter), Spring Boot Actuator  
**Duration:** ~90 minutes

---

## 1. Goals & Learning Outcomes
Students will:
- Configure and observe retry behavior.
- Trigger circuit breaker transitions.
- Test rate limiting behavior under load.
- Use health endpoints & create a custom HealthIndicator.

---

## 2. Setup & Quick Checks
Build & run:
```bash
cd resilience4j-demo
mvn clean package -DskipTests
mvn spring-boot:run
```

Verify:
```
GET http://localhost:8080/api/retry/demo
GET http://localhost:8080/api/circuit-breaker/success
GET http://localhost:8080/api/rate-limiter/demo
GET http://localhost:8080/actuator/health
```

---

## 3. Exercise 1 — Retry
### Tasks
1. Trigger failure:
```
GET http://localhost:8080/api/retry/demo?fail=true
```
2. Observe retry attempts & fallback in logs.
3. Modify retry config in `application.yml`:
```yaml
maxAttempts: 4
waitDuration: 500ms
enableExponentialBackoff: true
enableRandomizedWait: true
```
4. Restart & retest.

### Expected
- Multiple retries.
- Backoff delays visible.
- Fallback returned after final failure.

---

## 4. Exercise 2 — Circuit Breaker
### Tasks
1. Confirm success:
```
GET http://localhost:8080/api/circuit-breaker/success
```
2. Flood failures (call this endpoint 10+ times rapidly):
```
GET http://localhost:8080/api/circuit-breaker/fail
```
Or use the trigger endpoint:
```
POST http://localhost:8080/api/circuit-breaker/trigger-open
```
3. Observe OPEN state → fast fallback.
4. Wait for timeout → HALF-OPEN.
5. Call `/api/circuit-breaker/success` to close breaker.

### Expected
- Circuit opens after threshold.
- After wait duration, half-open allows test calls.

---

## 5. Exercise 3 — Rate Limiter
### Tasks
1. Test:
```
GET http://localhost:8080/api/rate-limiter/demo
```
2. Burst (use Postman Runner or call rapidly 20+ times):
```
GET http://localhost:8080/api/rate-limiter/demo
```
3. Modify:
```yaml
limitForPeriod: 10
limitRefreshPeriod: 1s
```
4. Restart & burst again.

### Expected
- Only fixed number of requests succeed.
- Others hit fallback.

---

## 6. Exercise 4 — Health Probes
### Tasks
1. Check:
```
GET http://localhost:8080/actuator/health/readiness
GET http://localhost:8080/actuator/health/liveness
```
2. Enable if needed:
```yaml
management.health.probes.enabled: true
```
3. Create custom HealthIndicator that returns DOWN.

### Expected
- Readiness shows DOWN when simulated dependency fails.

---

## 7. Instructor Notes
- Highlight idempotency with retries.
- Show logs during each test.
- Explain combining patterns for resilience.

---

## 8. Troubleshooting
- Restart app after YAML changes.
- Fallback signatures must match original method.
- Ensure controller paths are correct.

---

## 9. Quick Reference
Burst test (use Postman Runner with 50 iterations):
```
GET http://localhost:8080/api/rate-limiter/demo
```

Check readiness:
```
GET http://localhost:8080/actuator/health/readiness
```

---

## 10. Bonus
- Add Bulkhead.
- Add metrics dashboard.
- Write circuit breaker integration test.
