# Day 3 Quick Reference Guide

## 🚀 Quick Commands

### Event Hub

```bash
# Create Event Hub Namespace
az eventhubs namespace create \
  --name eventhub-lab \
  --resource-group rg-lab \
  --location eastus \
  --sku Standard

# Create Event Hub
az eventhubs eventhub create \
  --name orders \
  --namespace-name eventhub-lab \
  --resource-group rg-lab \
  --partition-count 4

# Get Connection String
az eventhubs namespace authorization-rule keys list \
  --resource-group rg-lab \
  --namespace-name eventhub-lab \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString -o tsv
```

### Application Insights

```bash
# Create Application Insights
az monitor app-insights component create \
  --app appinsights-lab \
  --location eastus \
  --resource-group rg-lab \
  --application-type web

# Get Connection String
az monitor app-insights component show \
  --app appinsights-lab \
  --resource-group rg-lab \
  --query connectionString -o tsv
```

### Kubernetes

```bash
# Apply all Day 3 configs
kubectl apply -f day3/k8s/

# Check deployment
kubectl get all -n observability-demo

# View logs with trace IDs
kubectl logs -f deployment/resilience4j-demo -n observability-demo

# Port forward for local testing
kubectl port-forward deployment/resilience4j-demo 8080:8080 -n observability-demo
```

---

## 📊 Key Metrics

### Event Hub Metrics
```
Throughput:        events/second
Incoming Messages: count
Outgoing Messages: count
Consumer Lag:      count
Throttled Requests: count
```

### Application Metrics
```
Request Rate:      requests/second
Error Rate:        errors/total requests
P95 Latency:       milliseconds
CPU Usage:         percentage
Memory Usage:      bytes
```

### SLO Targets
```
Availability:      99.9% (43.2 min downtime/month)
Latency P95:       < 200ms
Latency P99:       < 500ms
Error Rate:        < 0.1%
```

---

## 🔍 KQL Queries

### Request Performance
```kql
requests
| where timestamp > ago(1h)
| summarize 
    count = count(),
    p50 = percentile(duration, 50),
    p95 = percentile(duration, 95),
    p99 = percentile(duration, 99)
    by name
| order by p95 desc
```

### Error Analysis
```kql
requests
| where timestamp > ago(1h) and success == false
| summarize count() by resultCode, name
| order by count_ desc
```

### Trace Correlation
```kql
union requests, dependencies, exceptions
| where timestamp > ago(30m)
| where operation_Id == "abc123..."
| order by timestamp asc
| project timestamp, itemType, name, duration, success
```

### Availability
```kql
requests
| where timestamp > ago(30d)
| summarize 
    total = count(),
    success = countif(success)
| extend availability = (success * 100.0) / total
```

---

## 🏷️ Log Patterns

### Structured Logging
```java
log.info("Order created",
    kv("orderId", order.getId()),
    kv("amount", order.getAmount()),
    kv("customerId", order.getCustomerId()));
```

### MDC Context
```java
MDC.put("userId", userId);
MDC.put("sessionId", sessionId);
try {
    // Your code
} finally {
    MDC.clear();
}
```

### Exception Logging
```java
log.error("Failed to process order", exception,
    kv("orderId", orderId),
    kv("errorCode", "ORDER_PROCESSING_FAILED"));
```

---

## 📈 Micrometer Metrics

### Counter
```java
Counter counter = Counter.builder("orders.created")
    .tag("type", "online")
    .register(meterRegistry);
counter.increment();
```

### Timer
```java
Timer timer = Timer.builder("order.processing.time")
    .register(meterRegistry);
timer.record(() -> processOrder(order));
```

### Gauge
```java
Gauge.builder("orders.pending", orderService::getPendingCount)
    .register(meterRegistry);
```

---

## 🚨 Alert Thresholds

### Latency
```yaml
P95 Latency > 200ms for 5 minutes:
  Severity: Warning
  Action: Investigate performance

P99 Latency > 1000ms for 5 minutes:
  Severity: Critical
  Action: Page on-call
```

### Error Rate
```yaml
Error Rate > 1% for 5 minutes:
  Severity: Critical
  Action: Page on-call
  
Error Rate > 0.5% for 10 minutes:
  Severity: Warning
  Action: Create ticket
```

### Saturation
```yaml
CPU > 80% for 10 minutes:
  Severity: Warning
  Action: Scale out
  
Memory > 90% for 5 minutes:
  Severity: Critical
  Action: Restart + investigate
```

---

## 🔧 Configuration Snippets

### application.yml (Complete)
```yaml
spring:
  application:
    name: resilience4j-demo
  cloud:
    azure:
      monitor:
        enabled: true
        connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
  sleuth:
    enabled: true
    sampler:
      probability: 1.0
    propagation:
      type: w3c

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
    export:
      azuremonitor:
        enabled: true

logging:
  level:
    root: INFO
    com.example: DEBUG
  pattern:
    console: "%d [%X{traceId}/%X{spanId}] %-5level %logger - %msg%n"
```

### pom.xml Dependencies
```xml
<!-- Event Hub -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-stream-binder-eventhubs</artifactId>
</dependency>

<!-- Application Insights -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-monitor</artifactId>
</dependency>

<!-- Sleuth -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>

<!-- JSON Logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

---

## 🧪 Testing Commands

### Generate Traffic
```bash
# Normal traffic
for i in {1..100}; do
  curl http://localhost:8080/api/circuit-breaker/demo
  sleep 0.1
done

# Error traffic
for i in {1..50}; do
  curl "http://localhost:8080/api/circuit-breaker/demo?fail=true"
  sleep 0.1
done

# High latency
for i in {1..100}; do
  curl "http://localhost:8080/api/test/simulate-latency?delayMs=1000"
  sleep 0.1
done
```

### Check Metrics
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Health check
curl http://localhost:8080/actuator/health

# Specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests
```

---

## 📚 Common Patterns

### Event Producer
```java
@Service
public class OrderProducer {
    private final StreamBridge streamBridge;
    
    public void sendOrder(Order order) {
        Message<Order> message = MessageBuilder
            .withPayload(order)
            .setHeader("partitionKey", order.getCustomerId())
            .build();
        streamBridge.send("orders-out-0", message);
    }
}
```

### Event Consumer
```java
@Configuration
public class OrderConsumer {
    
    @Bean
    public Consumer<Message<Order>> processOrder() {
        return message -> {
            Order order = message.getPayload();
            // Process order
            
            // Checkpoint
            Checkpointer checkpointer = 
                message.getHeaders().get(CHECKPOINTER, Checkpointer.class);
            checkpointer.success().subscribe();
        };
    }
}
```

### Custom Span
```java
Span span = tracer.nextSpan().name("process-payment");
try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
    span.tag("orderId", orderId);
    span.tag("amount", String.valueOf(amount));
    // Process payment
} finally {
    span.end();
}
```

---

## 🎯 SRE Formulas

### Error Budget
```
Error Budget = 1 - SLO
If SLO = 99.9%, Error Budget = 0.1%

Over 30 days:
Total minutes = 43,200
Allowed downtime = 43.2 minutes
```

### Burn Rate
```
Burn Rate = Actual Error Rate / Error Budget

If consuming error budget 10x faster:
Burn Rate = 10
Time until exhausted = Total Period / Burn Rate
                     = 30 days / 10 = 3 days
```

### Availability
```
Availability = (Successful Requests / Total Requests) × 100
```

---

## 🔐 Security Best Practices

1. **Never log sensitive data**
   - Passwords, tokens, API keys
   - Credit card numbers
   - Personal identifiable information (PII)

2. **Use managed identities**
   - Avoid connection strings in code
   - Use Azure AD authentication

3. **Encrypt in transit**
   - TLS for all communications
   - HTTPS only

4. **Rotate secrets**
   - Regular key rotation
   - Store in Azure Key Vault

5. **Least privilege**
   - Minimal IAM permissions
   - Role-based access control

---

## 📞 Support Resources

### Documentation
- [Event Hubs](https://docs.microsoft.com/azure/event-hubs/)
- [App Insights](https://docs.microsoft.com/azure/azure-monitor/app/app-insights-overview)
- [Spring Cloud](https://spring.io/projects/spring-cloud)
- [Micrometer](https://micrometer.io/docs)

### Tools
- [Azure Portal](https://portal.azure.com)
- [Azure CLI](https://docs.microsoft.com/cli/azure/)
- [kubectl](https://kubernetes.io/docs/reference/kubectl/)

### Community
- [Stack Overflow - azure-eventhub](https://stackoverflow.com/questions/tagged/azure-eventhub)
- [Stack Overflow - azure-application-insights](https://stackoverflow.com/questions/tagged/azure-application-insights)
- [Spring Cloud Gitter](https://gitter.im/spring-cloud/spring-cloud)

---

**Tip:** Bookmark this page for quick reference during labs!
