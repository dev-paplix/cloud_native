# Hands-On Lab: Setup Azure Monitor Alerts from App Insights Metrics

## Duration: 60 minutes

> **💡 Cloud Shell Ready!** This entire lab can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Lab Objectives
- Configure Azure Monitor alerts based on Application Insights metrics
- Implement the Four Golden Signals in alerts
- Create SLO-based alerts
- Set up action groups for notifications
- Trigger simulated failures
- Refine alert thresholds using SRE strategies

---

## Part 1: Prerequisites (5 min)

### Ensure You Have:
- Azure subscription
- Application Insights instance (from previous labs)
- Spring Boot app with metrics instrumentation
- Access to create alerts and action groups

### Verify Application Insights is Receiving Data

```bash
# Check metrics are flowing
curl http://localhost:8080/actuator/prometheus

# Generate some test traffic
for i in {1..100}; do
  curl http://localhost:8080/api/circuit-breaker/demo
  sleep 0.1
done
```

---

## Part 2: Create Action Groups (10 min)

Action Groups define who gets notified when an alert fires.

### Using Azure CLI

```bash
# Variables
RESOURCE_GROUP="rg-observability-lab"
LOCATION="eastus"
ACTION_GROUP_NAME="ag-sre-team"

# Create action group with email notification
az monitor action-group create \
  --name $ACTION_GROUP_NAME \
  --resource-group $RESOURCE_GROUP \
  --short-name "SRETeam" \
  --email-receiver \
    name=SRETeamEmail \
    email-address=sre-team@example.com

# Add SMS notification
az monitor action-group update \
  --name $ACTION_GROUP_NAME \
  --resource-group $RESOURCE_GROUP \
  --add-receiver \
    name=SREOncall \
    type=sms \
    country-code=1 \
    phone-number=5551234567

# Add webhook for Slack/Teams
az monitor action-group update \
  --name $ACTION_GROUP_NAME \
  --resource-group $RESOURCE_GROUP \
  --add-receiver \
    name=SlackWebhook \
    type=webhook \
    service-uri=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
```

### Using Azure Portal

1. Go to **Azure Portal** → **Monitor** → **Alerts** → **Action groups**
2. Click **+ Create**
3. Fill in details:
   - **Resource group**: rg-observability-lab
   - **Name**: ag-sre-team
   - **Display name**: SRE Team
4. Add **Notifications**:
   - Type: Email/SMS/Push/Voice
   - Name: SRE Team Email
   - Email: sre-team@example.com
5. Add **Actions** (optional):
   - Type: Webhook
   - Name: Slack Notification
   - URI: Your Slack webhook URL
6. Create

---

## Part 3: Golden Signal Alerts (25 min)

### Alert 1: Latency (P95 Response Time)

**SLO**: 95% of requests complete in < 200ms

```bash
# Create metric alert using Azure CLI
az monitor metrics alert create \
  --name "High P95 Latency" \
  --resource-group $RESOURCE_GROUP \
  --scopes /subscriptions/{subscription-id}/resourceGroups/$RESOURCE_GROUP/providers/microsoft.insights/components/{app-insights-name} \
  --condition "avg requests/duration > 200" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --severity 2 \
  --description "P95 latency exceeds 200ms SLO" \
  --action $ACTION_GROUP_NAME
```

**Via Portal:**
1. Go to **Application Insights** → **Alerts** → **+ New alert rule**
2. **Scope**: Select your App Insights resource
3. **Condition**:
   - Signal: `requests/duration`
   - Operator: `Greater than`
   - Aggregation type: `Average`
   - Threshold: `200` (milliseconds)
   - Aggregation granularity: `5 minutes`
   - Frequency: `1 minute`
4. **Actions**: Select action group
5. **Alert rule details**:
   - Severity: `2 - Warning`
   - Name: `High P95 Latency`
   - Description: `P95 request latency exceeds 200ms SLO for 5 minutes`
6. Create

### Alert 2: Traffic (Request Rate Drop)

**Detect**: Traffic drops by more than 50% (potential outage)

```bash
az monitor metrics alert create \
  --name "Traffic Drop Detected" \
  --resource-group $RESOURCE_GROUP \
  --scopes /subscriptions/{subscription-id}/resourceGroups/$RESOURCE_GROUP/providers/microsoft.insights/components/{app-insights-name} \
  --condition "avg requests/count < threshold dynamic" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --severity 1 \
  --description "Request rate dropped significantly" \
  --action $ACTION_GROUP_NAME
```

**Via Portal (Dynamic Threshold):**
1. **Condition**:
   - Signal: `requests/count`
   - Operator: `Less than`
   - Threshold type: `Dynamic`
   - Sensitivity: `Medium`
   - Number of violations: `2 out of 2`
2. **Details**:
   - Severity: `1 - Error`
   - Name: `Traffic Drop Detected`

### Alert 3: Errors (High Error Rate)

**SLO**: Error rate < 0.1%

```bash
az monitor metrics alert create \
  --name "High Error Rate" \
  --resource-group $RESOURCE_GROUP \
  --scopes /subscriptions/{subscription-id}/resourceGroups/$RESOURCE_GROUP/providers/microsoft.insights/components/{app-insights-name} \
  --condition "avg requests/failed > 1" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --severity 0 \
  --description "Error rate exceeds 1% - SLO breach" \
  --action $ACTION_GROUP_NAME
```

**Via Portal with KQL:**
1. **Condition**:
   - Signal type: `Custom log search`
   - Search query:
     ```kql
     requests
     | where timestamp > ago(5m)
     | summarize 
         total = count(),
         failed = countif(success == false)
     | extend errorRate = (failed * 100.0) / total
     | where errorRate > 1.0
     ```
   - Alert logic: `Number of results > 0`
2. **Details**:
   - Severity: `0 - Critical`
   - Name: `High Error Rate - SLO Breach`

### Alert 4: Saturation (High CPU Usage)

**Threshold**: CPU > 80% for 10 minutes

```bash
az monitor metrics alert create \
  --name "High CPU Usage" \
  --resource-group $RESOURCE_GROUP \
  --scopes /subscriptions/{subscription-id}/resourceGroups/$RESOURCE_GROUP/providers/microsoft.insights/components/{app-insights-name} \
  --condition "avg performanceCounters/processCpuPercentage > 80" \
  --window-size 10m \
  --evaluation-frequency 5m \
  --severity 2 \
  --description "CPU usage exceeds 80% - scale out needed" \
  --action $ACTION_GROUP_NAME
```

---

## Part 4: SLO-Based Alerts (10 min)

### Alert: Error Budget Burn Rate

**SLO**: 99.9% availability (0.1% error budget)

**Alert when**: Burning budget 10x faster than sustainable

```kql
// Error budget burn rate calculation
let slo = 0.999;  // 99.9% SLO
let errorBudget = 1 - slo;  // 0.1%
requests
| where timestamp > ago(1h)
| summarize 
    total = count(),
    failed = countif(success == false)
| extend 
    actualErrorRate = (failed * 1.0) / total,
    burnRate = actualErrorRate / errorBudget
| where burnRate > 10  // Alert if burning 10x faster
```

**Create Alert:**
1. **Signal type**: `Custom log search`
2. **Query**: (above KQL)
3. **Alert logic**: `Number of results > 0`
4. **Severity**: `0 - Critical`
5. **Name**: `Critical Error Budget Burn Rate`

### Alert: SLO Availability Warning

```kql
// 30-day availability trending below SLO
requests
| where timestamp > ago(30d)
| summarize 
    total = count(),
    success = countif(success == true)
| extend availability = (success * 100.0) / total
| where availability < 99.9
```

---

## Part 5: Simulate Failures and Test Alerts (10 min)

### Test 1: Simulate High Error Rate

Create a test endpoint that fails on demand:

```java
@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @GetMapping("/simulate-errors")
    public ResponseEntity<?> simulateErrors(
        @RequestParam(defaultValue = "50") int errorPercentage) {
        
        boolean shouldFail = Math.random() * 100 < errorPercentage;
        
        if (shouldFail) {
            throw new RuntimeException("Simulated error for testing");
        }
        
        return ResponseEntity.ok("Success");
    }
}
```

**Generate errors:**
```bash
# Generate 100 requests with 80% error rate
for i in {1..100}; do
  curl "http://localhost:8080/api/test/simulate-errors?errorPercentage=80"
  sleep 0.1
done
```

### Test 2: Simulate High Latency

```java
@GetMapping("/simulate-latency")
public ResponseEntity<?> simulateLatency(
    @RequestParam(defaultValue = "500") int delayMs) throws InterruptedException {
    
    Thread.sleep(delayMs);
    return ResponseEntity.ok("Response after " + delayMs + "ms");
}
```

**Generate slow requests:**
```bash
# Generate requests with 1000ms latency
for i in {1..100}; do
  curl "http://localhost:8080/api/test/simulate-latency?delayMs=1000"
  sleep 0.1
done
```

### Test 3: Simulate High Traffic Then Drop

```bash
# High traffic
for i in {1..1000}; do
  curl http://localhost:8080/api/circuit-breaker/demo &
done
wait

# Wait 5 minutes, then complete silence (simulates outage)
sleep 300

# Check if traffic drop alert fires
```

---

## Part 6: Refine Alert Thresholds (5 min)

### Analyze Alert History

```bash
# List recent alerts
az monitor metrics alert list \
  --resource-group $RESOURCE_GROUP \
  --output table

# Get alert details
az monitor metrics alert show \
  --name "High Error Rate" \
  --resource-group $RESOURCE_GROUP
```

### Query Alert Firing History

```kql
// Alert firing history
AzureMetrics
| where TimeGenerated > ago(7d)
| where MetricName == "requests/failed"
| summarize 
    avg(Average), 
    max(Maximum), 
    min(Minimum) 
    by bin(TimeGenerated, 1h)
| render timechart
```

### Adjust Thresholds Based on Data

1. **Too many false positives?**
   - Increase threshold
   - Increase time window
   - Use dynamic thresholds

2. **Missing real issues?**
   - Decrease threshold
   - Decrease time window
   - Add more specific conditions

3. **Alert fatigue?**
   - Consolidate related alerts
   - Use multi-signal alerts
   - Implement suppression rules

### Example Refinement

**Original:** CPU > 70% for 5 minutes → Too noisy  
**Refined:** CPU > 80% for 10 minutes AND request latency > 200ms

```bash
# Multi-condition alert
az monitor metrics alert create \
  --name "Performance Degradation" \
  --resource-group $RESOURCE_GROUP \
  --scopes {app-insights-resource-id} \
  --condition "avg performanceCounters/processCpuPercentage > 80" \
  --condition "avg requests/duration > 200" \
  --condition-operator "and" \
  --window-size 10m \
  --evaluation-frequency 5m
```

---

## Part 7: Create Alert Dashboard (5 min)

### Create Custom Dashboard

1. Go to **Azure Portal** → **Dashboard**
2. Click **+ New dashboard**
3. Name: `SRE Operations Dashboard`

### Add Tiles

**Tile 1: Active Alerts**
- Type: Metric chart
- Resource: Application Insights
- Metric: Fired alerts count
- Time range: Last 24 hours

**Tile 2: Request Rate**
- Metric: `requests/count`
- Aggregation: Sum
- Chart type: Line chart

**Tile 3: Error Rate**
```kql
requests
| summarize 
    errorRate = (countif(success == false) * 100.0) / count()
    by bin(timestamp, 5m)
| render timechart
```

**Tile 4: P95 Latency**
```kql
requests
| summarize percentile(duration, 95) by bin(timestamp, 5m)
| render timechart
```

**Tile 5: Availability**
```kql
requests
| summarize 
    availability = (countif(success) * 100.0) / count()
    by bin(timestamp, 1h)
| render timechart
```

---

## Lab Verification Checklist

- [ ] Action group created with notifications
- [ ] Latency alert configured
- [ ] Traffic drop alert configured
- [ ] Error rate alert configured
- [ ] CPU/saturation alert configured
- [ ] Error budget burn rate alert created
- [ ] Test failures successfully triggered alerts
- [ ] Alert notifications received
- [ ] Thresholds refined based on data
- [ ] SRE dashboard created

---

## Advanced: Alert Runbooks

Create runbooks for common alerts:

### High Error Rate Runbook

```markdown
# Runbook: High Error Rate

## Alert Triggered
Error rate exceeded 1% threshold

## Impact
Users experiencing failures when accessing the service

## Investigation Steps
1. Check Application Insights Failures blade
2. Identify top failing operations
3. Review exception stack traces
4. Check dependency health (databases, APIs)
5. Review recent deployments

## Mitigation
- If caused by recent deployment: Rollback
- If database issue: Failover to replica
- If external API issue: Enable circuit breaker

## Resolution
- Fix root cause
- Deploy fix
- Verify error rate returns to normal
- Update postmortem

## Escalation
If unable to resolve in 30 minutes:
- Page senior SRE: +1-555-XXX-XXXX
- Join incident bridge: https://meet.company.com/incidents
```

### Store Runbooks

Upload to:
- Wiki (Confluence, GitHub Wiki)
- Azure Automation Runbooks
- Link in alert description

---

## Troubleshooting

**Alerts not firing:**
- Verify metrics are flowing to App Insights
- Check alert rule is enabled
- Verify threshold is appropriate
- Check evaluation frequency

**Too many alerts:**
- Increase thresholds
- Use dynamic thresholds
- Implement alert suppression
- Consolidate related alerts

**Notifications not received:**
- Verify action group configuration
- Check email spam folder
- Test action group manually
- Verify webhook URLs

---

## Next Steps
- Implement automated remediation with Azure Automation
- Create detailed runbooks for all critical alerts
- Set up on-call rotation in PagerDuty
- Implement alert suppression during deployments
- Create weekly SLO review process
- Track and report on error budget usage
