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
```bash
curl localhost:8080/retry/success
curl localhost:8080/cb/success
curl localhost:8080/rl/test
curl localhost:8080/actuator/health
```

---

## 3. Exercise 1 — Retry
### Tasks
1. Trigger failure:
```bash
curl localhost:8080/retry/failure
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
```bash
curl localhost:8080/cb/success
```
2. Flood failures:
```bash
seq 1 40 | xargs -n1 -P10 curl -s localhost:8080/cb/failure
```
3. Observe OPEN state → fast fallback.
4. Wait for timeout → HALF-OPEN.
5. Call `/cb/success` to close breaker.

### Expected
- Circuit opens after threshold.
- After wait duration, half-open allows test calls.

---

## 5. Exercise 3 — Rate Limiter
### Tasks
1. Test:
```bash
curl localhost:8080/rl/test
```
2. Burst:
```bash
seq 1 50 | xargs -n1 -P20 curl -s localhost:8080/rl/test
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
```bash
curl localhost:8080/actuator/health/readiness
curl localhost:8080/actuator/health/liveness
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
Burst test:
```bash
seq 1 50 | xargs -n1 -P20 curl -s localhost:8080/rl/test
```

Check readiness:
```bash
curl localhost:8080/actuator/health/readiness
```

---

## 10. Bonus
- Add Bulkhead.
- Add metrics dashboard.
- Write circuit breaker integration test.
