# Hands-On Lab: Integrate Micrometer + Azure Application Insights

## Duration: 60 minutes

> **💡 Cloud Shell Ready!** This entire lab can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Lab Objectives
- Set up Azure Application Insights instance
- Integrate App Insights SDK into Spring Boot application
- Configure custom metrics with Micrometer
- Implement distributed tracing
- Create dashboards and visualizations
- Test with Application Insights Agent

---

## Part 1: Azure Application Insights Setup (10 min)

### Step 1: Create Application Insights Resource

**Using Azure CLI:**
```bash
# Variables
RESOURCE_GROUP="rg-observability-lab"
LOCATION="eastus"
APP_INSIGHTS_NAME="appinsights-lab-$(date +%s)"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Application Insights
az monitor app-insights component create \
  --app $APP_INSIGHTS_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --application-type web

# Get connection string and instrumentation key
CONNECTION_STRING=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query connectionString -o tsv)

INSTRUMENTATION_KEY=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query instrumentationKey -o tsv)

echo "Connection String: $CONNECTION_STRING"
echo "Instrumentation Key: $INSTRUMENTATION_KEY"
```

**Using Azure Portal:**
1. Go to Azure Portal → Create a resource
2. Search for "Application Insights"
3. Fill in details:
   - Name: appinsights-observability-lab
   - Resource Group: rg-observability-lab
   - Region: East US
   - Workspace: Create new Log Analytics workspace
4. Create and note the Connection String

---

## Part 2: Spring Boot Integration (20 min)

### Step 1: Add Dependencies

Update `pom.xml`:

```xml
<dependencies>
    <!-- Existing dependencies... -->
    
    <!-- Azure Spring Cloud Starter for Application Insights -->
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-starter-monitor</artifactId>
    </dependency>
    
    <!-- Micrometer Azure Registry -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-azure-monitor</artifactId>
    </dependency>
    
    <!-- Micrometer Prometheus (optional, for local testing) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-dependencies</artifactId>
            <version>5.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Step 2: Configure Application

Update `application.yml`:

```yaml
spring:
  application:
    name: resilience4j-demo
  
  cloud:
    azure:
      monitor:
        enabled: true
        connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}

# Management endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,appinsights
  endpoint:
    health:
      show-details: always
  
  # Metrics configuration
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:development}
      region: eastus
    
    export:
      # Azure Monitor
      azuremonitor:
        enabled: true
        instrumentation-key: ${APPLICATIONINSIGHTS_INSTRUMENTATION_KEY}
      
      # Prometheus (optional)
      prometheus:
        enabled: true
    
    # Enable JVM metrics
    enable:
      jvm: true
      process: true
      system: true
      tomcat: true
      logback: true
  
  # Distributed tracing
  tracing:
    sampling:
      probability: 1.0  # Sample 100% for testing, reduce in production

# Logging with correlation
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%X{traceId}/%X{spanId}] %-5level %logger{36} - %msg%n"
```

### Step 3: Set Environment Variables

**Windows (PowerShell):**
```powershell
$env:APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx..."
$env:APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="xxx..."
```

**Linux/Mac:**
```bash
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx..."
export APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="xxx..."
```

---

## Part 3: Custom Metrics Implementation (15 min)

### Step 1: Create Metrics Configuration

```java
package com.example.resilience.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    
    @Bean
    public Counter requestCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.total")
            .description("Total number of API requests")
            .register(registry);
    }
    
    @Bean
    public Counter successCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.success")
            .description("Number of successful requests")
            .register(registry);
    }
    
    @Bean
    public Counter failureCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.failure")
            .description("Number of failed requests")
            .register(registry);
    }
    
    @Bean
    public Timer requestTimer(MeterRegistry registry) {
        return Timer.builder("api.requests.duration")
            .description("API request duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
}
```

### Step 2: Create Metrics Service

```java
package com.example.resilience.service;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    public void recordRequest(String endpoint, boolean success, long durationMs) {
        // Increment counters
        meterRegistry.counter("api.requests.total",
            "endpoint", endpoint,
            "status", success ? "success" : "failure"
        ).increment();
        
        // Record duration
        meterRegistry.timer("api.requests.duration",
            "endpoint", endpoint
        ).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    public void recordBusinessEvent(String eventType, String... tags) {
        meterRegistry.counter("business.events",
            appendTags("type", eventType, tags)
        ).increment();
    }
    
    public void recordGauge(String name, double value, String... tags) {
        meterRegistry.gauge(name, 
            io.micrometer.core.instrument.Tags.of(tags), 
            value);
    }
    
    private String[] appendTags(String... tags) {
        return tags;
    }
}
```

### Step 3: Add Metrics to Controllers

Update your controller to include metrics:

```java
package com.example.resilience.controller;

import com.example.resilience.service.MetricsService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {
    
    private final MetricsService metricsService;
    private final Timer requestTimer;
    
    @GetMapping("/demo")
    public ResponseEntity<String> demo(@RequestParam(defaultValue = "false") boolean fail) {
        Timer.Sample sample = Timer.start();
        
        try {
            // Your business logic
            if (fail) {
                throw new RuntimeException("Simulated failure");
            }
            
            String response = "Circuit Breaker Demo - Success";
            
            // Record success metric
            sample.stop(requestTimer);
            metricsService.recordRequest("/api/circuit-breaker/demo", true, 
                sample.stop(requestTimer));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            // Record failure metric
            metricsService.recordRequest("/api/circuit-breaker/demo", false, 0);
            
            log.error("Request failed", e);
            return ResponseEntity.internalServerError()
                .body("Request failed: " + e.getMessage());
        }
    }
}
```

---

## Part 4: Custom Telemetry (10 min)

### Create Telemetry Client Service

```java
package com.example.resilience.service;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    
    private final TelemetryClient telemetryClient;
    
    public void trackEvent(String eventName, Map<String, String> properties) {
        telemetryClient.trackEvent(eventName, properties, null);
        log.debug("Tracked event: {} with properties: {}", eventName, properties);
    }
    
    public void trackException(Exception exception, Map<String, String> properties) {
        telemetryClient.trackException(exception, properties, null);
        log.error("Tracked exception", exception);
    }
    
    public void trackMetric(String name, double value, Map<String, String> properties) {
        telemetryClient.trackMetric(name, value, null, null, null, properties);
    }
    
    public void trackDependency(String dependencyName, String command, 
                                long duration, boolean success) {
        telemetryClient.trackDependency(
            dependencyName, 
            command, 
            java.time.Duration.ofMillis(duration), 
            success
        );
    }
}
```

### Use in Service Layer

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final TelemetryService telemetryService;
    
    public void processOrder(Order order) {
        // Track business event
        telemetryService.trackEvent("OrderProcessed", Map.of(
            "orderId", order.getId(),
            "amount", String.valueOf(order.getAmount()),
            "customer", order.getCustomerId()
        ));
        
        try {
            // Process order
            doProcess(order);
            
        } catch (Exception e) {
            telemetryService.trackException(e, Map.of(
                "orderId", order.getId(),
                "stage", "processing"
            ));
            throw e;
        }
    }
}
```

---

## Part 5: Application Insights Agent (5 min)

### Step 1: Download Agent

```bash
# Download latest agent
curl -L https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.4.18/applicationinsights-agent-3.4.18.jar -o applicationinsights-agent.jar
```

### Step 2: Create Agent Configuration

Create `applicationinsights.json`:

```json
{
  "connectionString": "${APPLICATIONINSIGHTS_CONNECTION_STRING}",
  "role": {
    "name": "resilience4j-demo",
    "instance": "${HOSTNAME:localhost}"
  },
  "instrumentation": {
    "logging": {
      "level": "INFO"
    },
    "micrometer": {
      "enabled": true
    },
    "jdbc": {
      "enabled": true
    },
    "redis": {
      "enabled": true
    },
    "springScheduling": {
      "enabled": true
    }
  },
  "preview": {
    "sampling": {
      "percentage": 100
    },
    "liveMetrics": {
      "enabled": true
    }
  }
}
```

### Step 3: Run with Agent

```bash
# Build the application
mvn clean package

# Run with agent
java -javaagent:applicationinsights-agent.jar \
     -jar target/resilience4j-demo-1.0.0.jar
```

---

## Part 6: Testing and Verification (10 min)

### Step 1: Generate Test Traffic

```bash
# Generate successful requests
for i in {1..100}; do
  curl http://localhost:8080/api/circuit-breaker/demo
  sleep 0.1
done

# Generate some failures
for i in {1..20}; do
  curl "http://localhost:8080/api/circuit-breaker/demo?fail=true"
  sleep 0.1
done

# Test retry endpoint
for i in {1..50}; do
  curl "http://localhost:8080/api/retry/demo?shouldFail=false"
  sleep 0.1
done
```

### Step 2: View Metrics Locally

```bash
# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# View specific metrics
curl http://localhost:8080/actuator/metrics/api.requests.total
curl http://localhost:8080/actuator/metrics/api.requests.duration
```

### Step 3: Check Application Insights

1. **Open Azure Portal** → Your Application Insights resource

2. **Live Metrics**
   - Real-time request rate
   - Response time
   - Failure rate
   - Server metrics

3. **Application Map**
   - View service dependencies
   - See health status
   - Identify bottlenecks

4. **Performance**
   - Click "Operations"
   - View response times
   - Analyze slow requests

5. **Failures**
   - View exception types
   - See failure trends
   - Drill into stack traces

6. **Metrics Explorer**
   - Create custom charts
   - Add filters
   - Compare metrics

### Step 4: Run KQL Queries

Go to **Logs** in Application Insights and run:

```kql
// Request rate per minute
requests
| where timestamp > ago(1h)
| summarize count() by bin(timestamp, 1m)
| render timechart

// Average response time
requests
| where timestamp > ago(1h)
| summarize avg(duration) by name
| order by avg_duration desc

// Failure rate by operation
requests
| where timestamp > ago(1h)
| summarize 
    total = count(),
    failures = countif(success == false)
    by name
| extend failureRate = (failures * 100.0) / total
| order by failureRate desc

// Custom metrics
customMetrics
| where name == "api.requests.total"
| summarize sum(value) by bin(timestamp, 5m)
| render timechart

// Distributed trace example
requests
| where timestamp > ago(30m)
| take 1
| extend traceId = operation_Id
| join kind=inner (
    dependencies
    | where timestamp > ago(30m)
) on operation_Id
| project timestamp, operation_Name, dependency_Name, dependency_Duration
```

---

## Part 7: Create Dashboard (Optional)

### Step 1: Create Custom Dashboard

1. Go to **Dashboards** in Azure Portal
2. Click **+ New dashboard**
3. Name it "Resilience4j Monitoring"

### Step 2: Pin Key Metrics

Add tiles for:
- Request rate (timechart)
- Response time (P95, P99)
- Failure rate
- Dependency health
- Custom business metrics

### Step 3: Add KQL-based Charts

Create custom tiles using KQL queries:

```kql
// Success rate over time
requests
| where timestamp > ago(24h)
| summarize 
    success_rate = (countif(success) * 100.0) / count()
    by bin(timestamp, 1h)
| render timechart
```

---

## Lab Verification Checklist

- [ ] Application Insights resource created
- [ ] SDK dependencies added to project
- [ ] Connection string configured
- [ ] Application runs successfully
- [ ] Metrics visible in /actuator/prometheus
- [ ] Telemetry appears in Application Insights
- [ ] Live Metrics show real-time data
- [ ] Application Map displays correctly
- [ ] Custom metrics tracked
- [ ] KQL queries executed successfully
- [ ] Agent configuration tested (optional)

---

## Troubleshooting

**No data in Application Insights:**
- Verify connection string is correct
- Check application logs for errors
- Ensure firewall allows outbound to *.applicationinsights.azure.com
- Wait 2-3 minutes for initial data

**Metrics not appearing:**
- Check Micrometer configuration
- Verify MeterRegistry is autowired correctly
- Ensure metrics tags are valid

**Agent not working:**
- Check agent version compatibility
- Verify applicationinsights.json is in correct location
- Check agent logs for errors

---

## Next Steps
- Implement correlation IDs for distributed tracing
- Create alerts based on metrics
- Set up continuous export for long-term storage
- Integrate with Log Analytics workbooks
