# Lecture: SRE Principles & Monitoring Strategies Based on Alerts

## Duration: 45 minutes

## Learning Objectives
- Understand Site Reliability Engineering (SRE) principles
- Learn about SLIs, SLOs, and Error Budgets
- Master the Four Golden Signals
- Implement effective alerting strategies
- Avoid alert fatigue and toil
- Design monitoring dashboards for SRE

---

## 1. Introduction to SRE (10 min)

### What is Site Reliability Engineering?

**SRE** is a discipline that incorporates software engineering practices to IT operations. Originally developed at Google, SRE focuses on creating scalable and highly reliable software systems.

### Core SRE Principles

#### 1. **Embrace Risk**
- Perfect reliability (100% uptime) is impossible and expensive
- Balance reliability with feature velocity
- Use error budgets to make informed decisions

#### 2. **Service Level Objectives (SLOs)**
- Define target reliability levels
- Measure what matters to users
- Drive engineering decisions

#### 3. **Eliminate Toil**
- Toil = manual, repetitive, automatable work
- Limit toil to < 50% of time
- Automate everything possible

#### 4. **Monitoring Distributed Systems**
- Focus on symptoms, not causes
- Alert on user-impacting issues
- Use metrics for proactive monitoring

#### 5. **Automation**
- Reduce manual intervention
- Make operations scalable
- Improve consistency

#### 6. **Release Engineering**
- Gradual rollouts (canary, blue-green)
- Automated testing
- Quick rollback capability

### SRE vs Traditional Ops

| Traditional Ops | SRE |
|----------------|-----|
| Manual processes | Automated operations |
| Reactive | Proactive |
| Separate dev/ops teams | Shared ownership |
| Uptime focus | User experience focus |
| Manual scaling | Automatic scaling |

---

## 2. SLIs, SLOs, and SLAs (12 min)

### Service Level Indicators (SLIs)

**SLI** = A quantitative measure of service level

Common SLIs:
- **Availability**: % of time service is usable
- **Latency**: Time to process a request
- **Throughput**: Requests per second
- **Error Rate**: % of failed requests
- **Data Durability**: % of data not lost

#### Examples

```
Request Latency SLI:
- Measure: Time from request received to response sent
- Metric: 95th percentile < 200ms

Availability SLI:
- Measure: Successful requests / Total requests
- Metric: > 99.9%

Error Rate SLI:
- Measure: Failed requests / Total requests
- Metric: < 0.1%
```

### Service Level Objectives (SLOs)

**SLO** = Target value or range for an SLI

SLO Formula:
```
SLI ≥ Target over a time window
```

#### Examples

```
Availability SLO:
99.9% of requests succeed over 30 days
(Allows 43 minutes downtime per month)

Latency SLO:
95% of requests complete in < 200ms over 7 days
99% of requests complete in < 500ms over 7 days

Error Rate SLO:
< 0.1% of requests fail over 24 hours
```

### Service Level Agreements (SLAs)

**SLA** = Business contract with consequences

```
SLO: Internal target (99.9% uptime)
SLA: Customer promise (99.5% uptime with credits if missed)

Buffer: 99.9% - 99.5% = 0.4% safety margin
```

### Error Budgets

**Error Budget** = Amount of unreliability you can afford

```
If SLO is 99.9% availability:
Error Budget = 100% - 99.9% = 0.1%

Over 30 days:
Total time: 43,200 minutes
Error Budget: 43.2 minutes of downtime

If you've used 30 minutes, you have 13.2 minutes left
```

#### Using Error Budgets

```
✅ Error budget remaining > 0:
   - Continue feature development
   - Deploy new features
   - Take calculated risks

❌ Error budget exhausted:
   - Freeze feature releases
   - Focus on reliability
   - Fix technical debt
   - Improve monitoring
```

### Measuring SLOs

#### Window-based SLO
```
Success rate over last 30 days ≥ 99.9%

Calculation:
Successful requests: 9,995,000
Total requests: 10,000,000
Success rate: 99.95% ✅ (meets SLO)
```

#### Request-based SLO
```
99.9% of requests have latency < 200ms

Calculation:
P99 latency over last 7 days: 180ms ✅
P95 latency over last 7 days: 120ms ✅
```

---

## 3. The Four Golden Signals (8 min)

Google's SRE book defines four key metrics to monitor:

### 1. **Latency**

Time taken to service a request.

**What to measure:**
- P50, P95, P99, P99.9 percentiles
- Distinguish success vs error latency

**Why it matters:**
- Directly impacts user experience
- Can indicate performance degradation
- Early warning of capacity issues

**Example metrics:**
```yaml
- http.request.duration.p95 < 200ms
- http.request.duration.p99 < 500ms
- database.query.duration.p95 < 100ms
```

### 2. **Traffic**

How much demand is being placed on your system.

**What to measure:**
- Requests per second
- Transactions per second
- Active connections
- Bandwidth usage

**Why it matters:**
- Understand capacity needs
- Detect traffic spikes or drops
- Plan for scaling

**Example metrics:**
```yaml
- http.requests.rate (requests/sec)
- active.users.count
- queue.messages.rate
- network.bandwidth.usage
```

### 3. **Errors**

Rate of requests that fail.

**What to measure:**
- HTTP 5xx error rate
- Application exceptions
- Failed background jobs
- Timeout rates

**Why it matters:**
- Direct impact on user experience
- Indicates system health problems
- Helps prioritize fixes

**Example metrics:**
```yaml
- http.requests.errors.rate < 0.1%
- http.status.5xx.count
- exceptions.unhandled.rate
- circuitbreaker.failures.rate
```

### 4. **Saturation**

How "full" your service is.

**What to measure:**
- CPU utilization
- Memory usage
- Disk I/O
- Network bandwidth
- Thread pool usage
- Queue depth

**Why it matters:**
- Predicts when to scale
- Identifies bottlenecks
- Prevents outages

**Example metrics:**
```yaml
- cpu.usage < 80%
- memory.usage < 90%
- disk.usage < 85%
- connection.pool.usage < 80%
- queue.depth < 1000
```

### Implementing Golden Signals

```yaml
# Prometheus metrics example
groups:
  - name: golden_signals
    interval: 30s
    rules:
      # Latency
      - record: http:request:duration:p95
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
      
      # Traffic
      - record: http:requests:rate
        expr: rate(http_requests_total[5m])
      
      # Errors
      - record: http:errors:rate
        expr: rate(http_requests_total{status=~"5.."}[5m])
      
      # Saturation
      - record: cpu:usage:percent
        expr: 100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

---

## 4. Effective Alerting Strategies (10 min)

### Alert Fatigue Problem

**Too many alerts** → **Engineers ignore them** → **Real issues missed**

### Principles of Good Alerts

#### 1. **Alert on Symptoms, Not Causes**

❌ Bad: "CPU usage > 80%"  
✅ Good: "P95 latency > 500ms for 5 minutes"

**Why?** Users don't care about CPU; they care about slow responses.

#### 2. **Alert on User-Impacting Issues**

❌ Bad: "Database connection pool at 90%"  
✅ Good: "Error rate > 1% for 5 minutes"

**Why?** Connection pool might be high but still working fine.

#### 3. **Make Alerts Actionable**

Each alert should answer:
- What is broken?
- Why does it matter?
- What should I do?

**Example:**
```
Alert: High Error Rate
Severity: Critical
Description: Error rate is 5.2% (threshold: 1%)
Impact: Users cannot complete purchases
Action: 
  1. Check Application Insights for exceptions
  2. Verify payment service health
  3. Rollback last deployment if needed
Runbook: https://wiki.company.com/runbooks/high-error-rate
```

#### 4. **Use Appropriate Severity Levels**

**Critical (Page immediately):**
- User-facing service down
- Data loss occurring
- SLO burn rate very high

**High (Alert during business hours):**
- Performance degraded but functional
- Non-critical service down
- Error budget at risk

**Medium (Create ticket):**
- Capacity warnings
- Certificate expiring soon
- Minor configuration issues

**Low (Log for review):**
- Informational
- Trends worth noting

#### 5. **Implement Alert Suppression**

Avoid alert storms:
- Maintenance windows
- Known issues
- Dependent alerts (if service A is down, suppress downstream alerts)

### Alert Threshold Tuning

#### Static Thresholds
```yaml
# Simple but can be noisy
- alert: HighErrorRate
  expr: http_errors_rate > 0.01  # 1%
  for: 5m
```

#### Dynamic Thresholds (Anomaly Detection)
```yaml
# Better for varying traffic patterns
- alert: ErrorRateAnomaly
  expr: |
    http_errors_rate > 
    (avg_over_time(http_errors_rate[1h]) + 3 * stddev_over_time(http_errors_rate[1h]))
  for: 10m
```

#### Burn Rate Alerts
```yaml
# Alert based on error budget consumption rate
- alert: HighErrorBudgetBurnRate
  expr: |
    (
      (1 - http_success_rate) / (1 - 0.999)  # SLO is 99.9%
    ) > 10  # Burning budget 10x faster than acceptable
  for: 1h
```

### Alert Notification Channels

**Severity-based routing:**
```
Critical → PagerDuty (24/7 on-call)
High     → Slack + Email (business hours)
Medium   → Jira ticket
Low      → Log aggregation
```

---

## 5. Reducing Toil (5 min)

### What is Toil?

Work that is:
- **Manual** (human intervention)
- **Repetitive** (does it over and over)
- **Automatable** (machine could do it)
- **Tactical** (interrupt-driven)
- **No lasting value** (doesn't improve system)
- **Scales linearly** (grows with traffic)

### Examples of Toil

❌ **Toil:**
- Manually restarting services
- Manually scaling resources
- Responding to false alerts
- Manual deployment steps
- Manually investigating the same issues

✅ **Not Toil:**
- Incident response (judgment required)
- Designing new systems
- Writing automation
- Strategic planning

### Eliminating Toil

1. **Automate repetitive tasks**
   ```bash
   # Manual: ssh to server, restart service
   # Automated: kubectl rollout restart deployment
   ```

2. **Implement self-healing**
   ```yaml
   # Kubernetes liveness/readiness probes
   # Auto-restart unhealthy pods
   ```

3. **Use auto-scaling**
   ```yaml
   # HPA scales based on metrics
   # No manual intervention needed
   ```

4. **Fix root causes**
   ```
   # Don't just restart - fix the memory leak
   ```

5. **Improve monitoring**
   ```
   # Reduce false positives
   # Add better runbooks
   ```

### Toil Budget

Google SRE recommends:
```
Engineering work: > 50% of time
Toil: < 50% of time

Measure and track toil:
- Log time spent on toil tasks
- Review monthly
- Set reduction goals
```

---

## Summary

### Key Takeaways

1. **SRE** balances reliability with feature velocity using data-driven approaches
2. **SLOs** define target reliability; error budgets enable risk-taking
3. **Four Golden Signals** (Latency, Traffic, Errors, Saturation) are essential metrics
4. **Good alerts** are actionable, user-focused, and properly prioritized
5. **Reducing toil** through automation improves team effectiveness

### SRE Best Practices Checklist

- [ ] Define SLIs for your services
- [ ] Set realistic SLOs (don't aim for 100%)
- [ ] Track error budgets
- [ ] Monitor the Four Golden Signals
- [ ] Alert on symptoms, not causes
- [ ] Make alerts actionable with runbooks
- [ ] Use appropriate severity levels
- [ ] Implement alert suppression
- [ ] Track and reduce toil
- [ ] Automate everything possible
- [ ] Review and adjust thresholds regularly
- [ ] Conduct blameless postmortems

### The SRE Mindset

```
1. Measure everything
2. Set realistic goals (SLOs)
3. Make data-driven decisions
4. Automate relentlessly
5. Focus on user experience
6. Balance reliability and features
7. Learn from failures
8. Share knowledge
```

---

## References
- [Google SRE Book](https://sre.google/sre-book/table-of-contents/)
- [The Site Reliability Workbook](https://sre.google/workbook/table-of-contents/)
- [Implementing SLOs](https://sre.google/workbook/implementing-slos/)
- [Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/)
- [Azure Monitor Best Practices](https://docs.microsoft.com/azure/azure-monitor/best-practices)
