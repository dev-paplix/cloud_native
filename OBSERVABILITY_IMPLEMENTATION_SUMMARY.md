# Observability Lab Implementation - Summary

## Changes Applied

All three projects have been updated to support the observability features mentioned in the lab files:

### 1. **resilience4j-demo** Updates

#### Dependencies Added (pom.xml):
- ✅ Azure Application Insights (`spring-cloud-azure-starter-monitor`)
- ✅ Micrometer Azure Monitor registry
- ✅ Spring Cloud Sleuth for distributed tracing
- ✅ Logstash Logback Encoder for JSON logging
- ✅ Spring Cloud dependencies (version 2023.0.0)
- ✅ Azure Spring Cloud dependencies (version 5.8.0)

#### Configuration Updates (application.yml):
- ✅ Application Insights integration with connection string support
- ✅ Sleuth configuration with W3C trace propagation
- ✅ Enhanced management endpoints (added `appinsights`)
- ✅ Metrics export to Azure Monitor and Prometheus
- ✅ Distributed tracing with 100% sampling
- ✅ Enhanced logging pattern with trace IDs

#### New Java Classes:
- ✅ `MetricsConfig.java` - Custom metrics beans (counters, timers)
- ✅ `MetricsService.java` - Business metrics recording service
- ✅ `LoggingService.java` - Structured logging utilities
- ✅ `HttpClientConfig.java` - RestTemplate with Sleuth instrumentation
- ✅ `TraceController.java` - Distributed tracing test endpoints
- ✅ `TestController.java` - Error and latency simulation endpoints

#### New Configuration Files:
- ✅ `logback-spring.xml` - JSON logging with dev/prod profiles

---

### 2. **eventhub-consumer** Updates

#### Dependencies Added (pom.xml):
- ✅ Azure Application Insights
- ✅ Micrometer Azure Monitor registry
- ✅ Spring Cloud Sleuth for distributed tracing
- ✅ Logstash Logback Encoder for JSON logging
- ✅ Updated Azure Spring Cloud version to 5.8.0

#### Configuration Updates (application.yml):
- ✅ Application Insights integration
- ✅ Sleuth configuration with messaging support
- ✅ Enhanced management endpoints (added `appinsights`, `bindings`)
- ✅ Metrics export to Azure Monitor
- ✅ Trace correlation with orderId, userId, messageId
- ✅ Enhanced logging pattern with trace IDs

#### Code Updates:
- ✅ `OrderConsumerService.java` - Updated with structured logging
- ✅ Trace-aware logging with structured arguments

#### New Configuration Files:
- ✅ `logback-spring.xml` - JSON logging configuration

---

### 3. **eventhub-producer** Updates

#### Dependencies Added (pom.xml):
- ✅ Azure Application Insights
- ✅ Micrometer Azure Monitor registry
- ✅ Spring Cloud Sleuth for distributed tracing
- ✅ Logstash Logback Encoder for JSON logging
- ✅ Updated Azure Spring Cloud version to 5.8.0

#### Configuration Updates (application.yml):
- ✅ Application Insights integration
- ✅ Sleuth configuration with messaging support
- ✅ Enhanced management endpoints (added `appinsights`, `bindings`)
- ✅ Metrics export to Azure Monitor
- ✅ Trace correlation with orderId, userId, messageId
- ✅ Enhanced logging pattern with trace IDs

#### Code Updates:
- ✅ `OrderProducerService.java` - Updated with structured logging
- ✅ Trace-aware logging with structured arguments

#### New Configuration Files:
- ✅ `logback-spring.xml` - JSON logging configuration

---

## How to Use

### Step 1: Set Environment Variables

Before running any application, set the Application Insights credentials:

**PowerShell:**
```powershell
$env:APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx..."
$env:APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="xxx..."
```

**Bash (Azure Cloud Shell):**
```bash
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx..."
export APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="xxx..."
```

### Step 2: Build Projects

```bash
# Build resilience4j-demo
cd "c:\courses\Cloud Native Java\code\resilience4j-demo"
mvn clean package -DskipTests

# Build eventhub-consumer
cd "c:\courses\Cloud Native Java\code\day3\code\eventhub-consumer"
mvn clean package -DskipTests

# Build eventhub-producer
cd "c:\courses\Cloud Native Java\code\day3\code\eventhub-producer"
mvn clean package -DskipTests
```

### Step 3: Run Applications

**Development Mode (Human-Readable Logs):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Production Mode (JSON Logs):**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## Lab Features Now Available

### 04-OBSERVABILITY_LAB.md
✅ Application Insights integration
✅ Custom metrics with Micrometer
✅ Distributed tracing
✅ Custom telemetry
✅ Metrics endpoints (`/actuator/prometheus`, `/actuator/metrics`)
✅ Live Metrics support

### 06-LOGGING_LAB.md
✅ Spring Cloud Sleuth integration
✅ JSON logging with Logback
✅ Correlation IDs across services
✅ Trace propagation via HTTP
✅ MDC context management
✅ Structured logging with key-value pairs

### 08-AZURE_MONITOR_LAB.md
✅ Metrics ready for Azure Monitor alerts
✅ Golden Signals metrics (latency, traffic, errors, saturation)
✅ Test endpoints for error simulation
✅ Test endpoints for latency simulation
✅ Business event tracking

---

## Testing the Features

### Test Distributed Tracing (resilience4j-demo)
```bash
# Start the application
mvn spring-boot:run

# Call the trace test endpoint
curl http://localhost:8080/api/trace/start

# Check logs - you'll see same traceId across all 3 calls
```

### Test Error Simulation
```bash
# Generate errors (80% error rate)
for ($i=0; $i -lt 100; $i++) {
  iwr "http://localhost:8080/api/test/simulate-errors?errorPercentage=80"
  Start-Sleep -Milliseconds 100
}
```

### Test Latency Simulation
```bash
# Generate slow requests (1000ms latency)
for ($i=0; $i -lt 100; $i++) {
  iwr "http://localhost:8080/api/test/simulate-latency?delayMs=1000"
  Start-Sleep -Milliseconds 100
}
```

### View Metrics Locally
```bash
# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# View specific metrics
curl http://localhost:8080/actuator/metrics/api.requests.total
curl http://localhost:8080/actuator/metrics/api.requests.duration
```

### View JSON Logs
```bash
# After running in prod profile
cat logs/application.json | ConvertFrom-Json | Format-List

# Filter by trace ID (example)
Get-Content logs/application.json | ConvertFrom-Json | Where-Object { $_.traceId -eq "abc123" }
```

---

## Actuator Endpoints Available

All three applications now expose:
- `/actuator/health` - Health status
- `/actuator/info` - Application info
- `/actuator/metrics` - All available metrics
- `/actuator/prometheus` - Prometheus format metrics
- `/actuator/appinsights` - Application Insights status

**resilience4j-demo additional endpoints:**
- `/actuator/circuitbreakers` - Circuit breaker status
- `/actuator/circuitbreakerevents` - Circuit breaker events
- `/actuator/ratelimiters` - Rate limiter status
- `/actuator/retries` - Retry status

**Event Hub apps additional endpoints:**
- `/actuator/bindings` - Stream bindings status

---

## Profiles

### Development Profile (`dev`, `local`, `test`)
- Human-readable console logs
- Console appender only
- DEBUG level for application code
- Trace IDs visible in colored format

### Production Profile (`prod`, `staging`)
- JSON structured logs
- File appender with rotation (30 days, 10GB max)
- Async logging for performance
- INFO level for application code
- Logs written to `logs/` directory

---

## Application Insights Agent (Optional)

To use the Java agent instead of SDK:

1. Download agent:
```bash
curl -L https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.4.18/applicationinsights-agent-3.4.18.jar -o applicationinsights-agent.jar
```

2. Create `applicationinsights.json` configuration file

3. Run with agent:
```bash
java -javaagent:applicationinsights-agent.jar -jar target/resilience4j-demo-1.0.0.jar
```

---

## Notes

- Compile errors will resolve after running `mvn clean package`
- The logstash-logback-encoder dependency provides the `kv()` structured arguments
- Sleuth automatically propagates trace context across HTTP calls
- Event Hub apps will propagate trace context in message headers
- All metrics are automatically collected and exported to both Prometheus and Azure Monitor

---

## Troubleshooting

### No trace IDs in logs
- Verify Sleuth dependency is downloaded: `mvn dependency:tree | grep sleuth`
- Check that Sleuth is enabled in application.yml

### JSON logs not appearing
- Verify profile is set: `--spring.profiles.active=prod`
- Check logback-spring.xml exists in src/main/resources

### Metrics not in Application Insights
- Verify connection string is set correctly
- Wait 2-3 minutes for initial telemetry
- Check application logs for errors

### Build failures
- Run `mvn clean install` to download all dependencies
- Check that Spring Cloud dependencies are properly imported
