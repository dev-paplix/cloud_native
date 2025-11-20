# Lecture: Observability with App Insights & App Insights Agent

## Duration: 45 minutes

## Learning Objectives
- Understand the three pillars of observability: Metrics, Logs, and Traces
- Learn Azure Application Insights capabilities
- Master Micrometer for metrics collection
- Implement distributed tracing
- Use Application Insights Agent for deeper debugging

---

## 1. Introduction to Observability (10 min)

### What is Observability?

**Observability** is the ability to understand the internal state of a system by examining its external outputs.

### Three Pillars of Observability

#### 1. **Metrics** (What happened?)
- Numerical measurements over time
- CPU usage, memory, request count, latency
- Aggregated data, low storage cost
- Good for alerting and dashboards

#### 2. **Logs** (Detailed events)
- Discrete events with context
- Application logs, error messages
- High volume, searchable
- Good for debugging specific issues

#### 3. **Traces** (Request flow)
- Request journey across services
- Shows latency at each step
- Distributed context propagation
- Good for understanding dependencies

### Observability vs Monitoring

| Monitoring | Observability |
|------------|---------------|
| "Known unknowns" | "Unknown unknowns" |
| Predefined dashboards | Exploratory analysis |
| Threshold-based alerts | Pattern detection |
| What is broken? | Why is it broken? |

---

## 2. Azure Application Insights (15 min)

### What is Application Insights?

Application Insights is Azure's Application Performance Management (APM) service that provides:
- Real-time performance monitoring
- Distributed tracing across microservices
- Application dependencies mapping
- Smart detection of anomalies
- Rich querying with KQL (Kusto Query Language)

### Architecture

```
┌─────────────────────────────────────┐
│   Your Application                  │
│                                     │
│  ┌───────────────────────────────┐ │
│  │ App Insights SDK/Agent        │ │
│  │ - Auto-instrumentation        │ │
│  │ - Custom telemetry            │ │
│  └────────────┬──────────────────┘ │
└───────────────┼─────────────────────┘
                │
                ▼
┌─────────────────────────────────────┐
│   Application Insights              │
│   (Azure Monitor)                   │
│                                     │
│  ┌─────────┐  ┌─────────┐         │
│  │ Metrics │  │  Logs   │         │
│  └─────────┘  └─────────┘         │
│  ┌─────────┐  ┌─────────┐         │
│  │ Traces  │  │ Depend. │         │
│  └─────────┘  └─────────┘         │
└───────────────┼─────────────────────┘
                │
                ▼
┌─────────────────────────────────────┐
│   Visualization & Alerts            │
│  - Dashboards                       │
│  - Live Metrics                     │
│  - Alerts                           │
│  - Workbooks                        │
└─────────────────────────────────────┘
```

### Key Features

#### 1. **Live Metrics Stream**
- Real-time performance monitoring
- Live request/failure rates
- Server health metrics
- No sampling delay

#### 2. **Application Map**
- Visual dependency graph
- Shows all microservice connections
- Identifies performance bottlenecks
- Highlights failing dependencies

#### 3. **Transaction Search**
- Search individual requests
- Drill into specific failures
- View complete request timeline
- Correlate logs and traces

#### 4. **Performance**
- Operation-level performance
- Slow queries identification
- Response time distribution
- Dependency call durations

#### 5. **Failures**
- Exception tracking
- Failure rate trends
- Stack traces
- Impact analysis

#### 6. **Smart Detection**
- Anomaly detection with ML
- Performance degradation alerts
- Dependency degradation
- Memory leak detection

### Integration Methods

#### Method 1: Auto-Instrumentation (Agent)
- Zero code changes
- Attach agent at runtime
- Automatic dependency tracking
- Best for existing applications

```bash
# Java
java -javaagent:applicationinsights-agent-3.4.18.jar -jar myapp.jar
```

#### Method 2: SDK Integration
- Manual instrumentation
- More control over telemetry
- Custom events and metrics
- Best for new applications

```xml
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-monitor</artifactId>
</dependency>
```

---

## 3. Micrometer for Metrics (10 min)

### What is Micrometer?

Micrometer is a metrics facade (like SLF4J for metrics) that provides:
- Vendor-neutral metrics API
- Multiple backend support (Prometheus, Azure Monitor, etc.)
- Built-in Spring Boot integration
- Common metric types

### Metric Types

#### 1. **Counter** - Ever-increasing value
```java
Counter.builder("orders.created")
    .description("Number of orders created")
    .tag("type", "online")
    .register(meterRegistry)
    .increment();
```

#### 2. **Gauge** - Current value snapshot
```java
Gauge.builder("orders.pending", orderService::getPendingCount)
    .description("Number of pending orders")
    .register(meterRegistry);
```

#### 3. **Timer** - Latency distribution
```java
Timer timer = Timer.builder("order.processing.time")
    .description("Order processing duration")
    .register(meterRegistry);

timer.record(() -> {
    // Processing logic
    processOrder(order);
});
```

#### 4. **Distribution Summary** - Value distribution
```java
DistributionSummary.builder("order.amount")
    .description("Order amount distribution")
    .baseUnit("dollars")
    .register(meterRegistry)
    .record(order.getTotalAmount());
```

### Spring Boot Integration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
      environment: production
    export:
      azure-monitor:
        enabled: true
        instrumentation-key: ${APPINSIGHTS_INSTRUMENTATIONKEY}
```

### Custom Metrics Example

```java
@Service
public class OrderService {
    
    private final Counter orderCounter;
    private final Timer processingTimer;
    
    public OrderService(MeterRegistry registry) {
        this.orderCounter = Counter.builder("orders.processed")
            .tag("status", "success")
            .register(registry);
            
        this.processingTimer = Timer.builder("orders.processing.duration")
            .description("Time to process orders")
            .register(registry);
    }
    
    public void processOrder(Order order) {
        processingTimer.record(() -> {
            // Processing logic
            doProcess(order);
            orderCounter.increment();
        });
    }
}
```

---

## 4. Distributed Tracing (10 min)

### The Problem: Distributed Systems Complexity

```
User Request → API Gateway → Order Service → Payment Service
                                          → Inventory Service → Database
                                          → Notification Service
```

**Questions:**
- Which service is slow?
- Where did the request fail?
- What's the end-to-end latency?

### Solution: Distributed Tracing

**Trace Context Propagation:**
- **Trace ID**: Unique identifier for entire request
- **Span ID**: Unique identifier for each service operation
- **Parent Span ID**: Links spans together

```
Trace ID: abc123 (flows through all services)

API Gateway
  ├─ Span: receive-request (10ms)
  └─ Span: call-order-service (5ms)
      ├─ Order Service
      │   ├─ Span: validate-order (3ms)
      │   ├─ Span: call-payment (50ms) ← SLOW!
      │   │   └─ Payment Service (45ms)
      │   └─ Span: call-inventory (20ms)
      │       └─ Inventory Service (18ms)
```

### W3C Trace Context Standard

HTTP Headers:
```
traceparent: 00-abc123...-def456...-01
tracestate: vendor=value
```

### Application Insights Integration

Application Insights automatically:
1. Generates trace and span IDs
2. Propagates context via HTTP headers
3. Collects timing data
4. Builds dependency graph
5. Visualizes in Application Map

### Spring Boot Configuration

```yaml
spring:
  cloud:
    azure:
      monitor:
        enabled: true
        instrumentation-key: ${APPINSIGHTS_INSTRUMENTATIONKEY}
        
# Automatic instrumentation includes:
# - HTTP requests/responses
# - Database calls
# - Redis operations
# - Message queue operations
```

---

## 5. Application Insights Agent (5 min)

### What is the Agent?

The Application Insights Agent (formerly "Status Monitor") provides:
- **Zero-code instrumentation**
- **Automatic dependency tracking**
- **SQL query capture**
- **HTTP dependency tracking**
- **Enhanced telemetry**

### Agent vs SDK

| Feature | Agent | SDK |
|---------|-------|-----|
| Code changes | None | Required |
| Setup effort | Low | Medium |
| Customization | Limited | Full control |
| Performance overhead | Low | Low |
| Dependency tracking | Automatic | Manual |

### Agent Configuration (applicationinsights-agent.json)

```json
{
  "connectionString": "InstrumentationKey=xxx...",
  "role": {
    "name": "order-service",
    "instance": "order-service-1"
  },
  "instrumentation": {
    "logging": {
      "level": "INFO"
    },
    "micrometer": {
      "enabled": true
    }
  },
  "preview": {
    "sampling": {
      "percentage": 100
    }
  }
}
```

### Running with Agent

```bash
# Download agent
curl -L https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.4.18/applicationinsights-agent-3.4.18.jar -o agent.jar

# Run application with agent
java -javaagent:agent.jar \
     -jar myapp.jar
```

---

## 6. Querying with KQL (5 min)

### Kusto Query Language (KQL)

KQL is used to query Application Insights data.

#### Example Queries

**1. Request failure rate:**
```kql
requests
| where timestamp > ago(1h)
| summarize 
    total = count(),
    failed = countif(success == false)
| extend failureRate = (failed * 100.0) / total
```

**2. P95 response time:**
```kql
requests
| where timestamp > ago(24h)
| summarize percentile(duration, 95) by bin(timestamp, 1h)
| render timechart
```

**3. Top slow dependencies:**
```kql
dependencies
| where timestamp > ago(1h)
| summarize avg(duration), count() by name
| order by avg_duration desc
| take 10
```

**4. Exception trends:**
```kql
exceptions
| where timestamp > ago(7d)
| summarize count() by bin(timestamp, 1d), type
| render timechart
```

**5. Custom event tracking:**
```kql
customEvents
| where name == "OrderProcessed"
| extend orderId = tostring(customDimensions.orderId)
| summarize count() by orderId
```

---

## Summary

### Key Takeaways

1. **Observability** = Metrics + Logs + Traces
2. **Application Insights** provides comprehensive APM for cloud applications
3. **Micrometer** offers vendor-neutral metrics collection
4. **Distributed Tracing** shows request flow across microservices
5. **Agent** provides zero-code instrumentation
6. **KQL** enables powerful telemetry querying

### Best Practices

1. ✅ Use structured logging (JSON)
2. ✅ Include correlation IDs in all logs
3. ✅ Tag metrics with meaningful dimensions
4. ✅ Monitor the Four Golden Signals (latency, traffic, errors, saturation)
5. ✅ Set up custom metrics for business events
6. ✅ Use sampling for high-volume applications
7. ✅ Create dashboards for different audiences (dev, ops, business)

### Next Steps
- Complete the hands-on lab to integrate Application Insights
- Create custom dashboards
- Set up alerts based on metrics
- Implement correlation IDs for tracing

---

## References
- [Application Insights Documentation](https://docs.microsoft.com/azure/azure-monitor/app/app-insights-overview)
- [Micrometer Documentation](https://micrometer.io/docs)
- [KQL Reference](https://docs.microsoft.com/azure/data-explorer/kusto/query/)
- [OpenTelemetry](https://opentelemetry.io/)
