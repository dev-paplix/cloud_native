# Azure Application Insights Integration Guide
## Metrics, Logs, and Distributed Tracing for Spring Boot on AKS

---

## 📊 Overview

This guide explains how to integrate **Azure Application Insights** with your Spring Boot application running on AKS to collect:
- **Metrics**: Performance counters, JVM metrics, custom metrics
- **Logs**: Application logs with correlation
- **Traces**: Distributed tracing across microservices

---

## 🎯 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                    │
│                                                                │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐      │
│  │  Actuator  │  │  Logback    │  │  Micrometer      │      │
│  │  Endpoints │  │  Appender   │  │  Tracing         │      │
│  └─────┬──────┘  └──────┬──────┘  └────────┬─────────┘      │
│        │                │                   │                 │
└────────┼────────────────┼───────────────────┼─────────────────┘
         │                │                   │
         ▼                ▼                   ▼
┌────────────────────────────────────────────────────────────────┐
│         Application Insights Java Agent (3.x)                  │
│         - Auto-instrumentation                                  │
│         - Telemetry Collection                                  │
│         - Correlation                                           │
└────────────────────────┬───────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────────┐
│              Azure Application Insights                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Metrics  │  │   Logs   │  │  Traces  │  │  Alerts  │      │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
└────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Setup Instructions

### **Step 1: Create Application Insights Resource**

```bash
# Set variables
RESOURCE_GROUP="rg-resilience4j-demo"
LOCATION="southeastasia"
APP_INSIGHTS_NAME="appi-resilience4j-demo"
WORKSPACE_NAME="log-resilience4j-demo"

# Create Log Analytics Workspace
az monitor log-analytics workspace create \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $WORKSPACE_NAME \
  --location $LOCATION

# Get Workspace ID
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $WORKSPACE_NAME \
  --query id -o tsv)

# Create Application Insights
az monitor app-insights component create \
  --app $APP_INSIGHTS_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --workspace $WORKSPACE_ID

# Get Connection String
CONNECTION_STRING=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query connectionString -o tsv)

echo "Connection String: $CONNECTION_STRING"
```

### **Step 2: Store Connection String in Kubernetes Secret**

```bash
# Create namespace if not exists
kubectl create namespace resilience4j-demo --dry-run=client -o yaml | kubectl apply -f -

# Create secret
kubectl create secret generic app-insights-secret \
  --from-literal=connection-string="$CONNECTION_STRING" \
  -n resilience4j-demo

# Verify secret
kubectl get secret app-insights-secret -n resilience4j-demo -o yaml
```

---

## 📦 Maven Dependencies

Add to `pom.xml`:

```xml
<dependencies>
    <!-- Application Insights Spring Boot Starter -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-spring-boot-starter</artifactId>
        <version>3.4.19</version>
    </dependency>
    
    <!-- Application Insights Agent (for auto-instrumentation) -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-agent</artifactId>
        <version>3.5.1</version>
    </dependency>
    
    <!-- Micrometer Azure Registry (for metrics) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-azure-monitor</artifactId>
    </dependency>
    
    <!-- Already included - Micrometer Tracing -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
    
    <!-- Logback Appender for Application Insights -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-logging-logback</artifactId>
        <version>3.4.19</version>
    </dependency>
</dependencies>
```

---

## ⚙️ Spring Boot Configuration

Update `application.yml`:

```yaml
spring:
  application:
    name: resilience4j-demo
    version: v2

# Azure Application Insights
azure:
  application-insights:
    enabled: true
    connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
    instrumentation-key: ${APPLICATIONINSIGHTS_INSTRUMENTATION_KEY:} # Deprecated, use connection string
    
    # Sampling (reduce telemetry volume in production)
    sampling:
      percentage: 100.0 # 100% for demo, use 10-20 in production
    
    # Auto-collection
    web:
      enabled: true
    heartbeat:
      enabled: true
    
    # Telemetry Initializers
    telemetry:
      enabled: true
      
    # Performance counters
    performance-counters:
      enabled: true
      
    # Custom dimensions
    cloud:
      role-name: ${spring.application.name}
      role-instance: ${HOSTNAME:localhost}

# Management endpoints
management:
  endpoints:
    web:
      exposure:
        include: "*"
  
  metrics:
    export:
      azure-monitor:
        enabled: true
        connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
        step: 60s # Export every 60 seconds
    
    tags:
      application: ${spring.application.name}
      version: v2
      environment: ${ENVIRONMENT:production}
      region: ${REGION:southeastasia}
  
  # Distributed Tracing
  tracing:
    sampling:
      probability: 1.0 # 100% for demo, use 0.1-0.2 in production

# Logging with Application Insights
logging:
  level:
    root: INFO
    com.example.resilience: DEBUG
    com.microsoft.applicationinsights: DEBUG
```

---

## 📝 Logback Configuration

Update `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <springProperty scope="context" name="applicationName" source="spring.application.name"/>
    <springProperty scope="context" name="aiConnectionString" source="azure.application-insights.connection-string"/>
    
    <!-- Application Insights Appender -->
    <appender name="aiAppender" class="com.microsoft.applicationinsights.logback.ApplicationInsightsAppender">
        <connectionString>${aiConnectionString}</connectionString>
        <instrumentationKey></instrumentationKey> <!-- Leave empty, use connection string -->
    </appender>
    
    <!-- JSON Console Appender (existing) -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>
                {
                  "application": "${applicationName:-unknown}",
                  "environment": "${profile:-dev}"
                }
            </customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>
    
    <!-- File Appender (existing) -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.json</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"application":"${applicationName}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
    </appender>
    
    <!-- Default configuration -->
    <root level="INFO">
        <appender-ref ref="JSON"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="aiAppender"/> <!-- Add Application Insights -->
    </root>
    
    <logger name="com.example.resilience" level="DEBUG"/>
</configuration>
```

---

## 🐳 Docker Configuration

### **Option 1: Using Java Agent (Recommended)**

Update `Dockerfile`:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-23-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:23-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy application JAR
COPY --from=builder /app/target/resilience4j-demo-1.0.0.jar app.jar

# Download Application Insights Java Agent
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.5.1/applicationinsights-agent-3.5.1.jar \
    /app/applicationinsights-agent.jar

RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8070

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Run with Application Insights Agent
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -javaagent:/app/applicationinsights-agent.jar -jar app.jar"]
```

### **Option 2: Using Starter Only (No Agent)**

If using only the Spring Boot starter without the agent:

```dockerfile
# Same as before, just run normally
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## ☸️ Kubernetes Deployment Configuration

Update `deployment-v2.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilience4j-demo-v2
spec:
  template:
    spec:
      containers:
      - name: app
        image: myresilienceacr.azurecr.io/resilience4j-demo:v2
        
        env:
        # Application Insights Connection String from Secret
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: connection-string
        
        # Optional: Instrumentation Key (deprecated, but some libraries need it)
        - name: APPINSIGHTS_INSTRUMENTATIONKEY
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: instrumentation-key
              optional: true
        
        # Application metadata
        - name: HOSTNAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        
        # Environment configuration
        - name: ENVIRONMENT
          value: "production"
        
        - name: REGION
          value: "southeastasia"
        
        # Java agent configuration (if using agent)
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/app/applicationinsights-agent.jar"
        
        # Application Insights Agent Configuration
        - name: APPLICATIONINSIGHTS_ROLE_NAME
          value: "resilience4j-demo"
        
        - name: APPLICATIONINSIGHTS_ROLE_INSTANCE
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
```

---

## 🎨 Application Insights Agent Configuration

Create `applicationinsights.json` (optional, for advanced configuration):

```json
{
  "connectionString": "${APPLICATIONINSIGHTS_CONNECTION_STRING}",
  "role": {
    "name": "${APPLICATIONINSIGHTS_ROLE_NAME}",
    "instance": "${APPLICATIONINSIGHTS_ROLE_INSTANCE}"
  },
  "sampling": {
    "percentage": 100,
    "requestsPerSecond": -1
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
    "kafka": {
      "enabled": true
    },
    "jms": {
      "enabled": true
    },
    "mongo": {
      "enabled": true
    },
    "cassandra": {
      "enabled": true
    }
  },
  "preview": {
    "sampling": {
      "overrides": [
        {
          "telemetryType": "request",
          "attributes": [
            {
              "key": "http.url",
              "value": "https?://[^/]+/actuator/.*",
              "matchType": "regexp"
            }
          ],
          "percentage": 0
        }
      ]
    }
  },
  "customDimensions": {
    "service.version": "v2",
    "deployment.environment": "${ENVIRONMENT}"
  }
}
```

Add to `Dockerfile`:

```dockerfile
COPY applicationinsights.json /app/applicationinsights.json
ENV APPLICATIONINSIGHTS_CONFIGURATION_FILE=/app/applicationinsights.json
```

---

## 🚀 Deployment Steps

### **1. Build and Push Docker Image**

```bash
cd resilience4j-demo-v2

# Build image
docker build -t resilience4j-demo:v2 .

# Tag for ACR
docker tag resilience4j-demo:v2 myresilienceacr.azurecr.io/resilience4j-demo:v2

# Login to ACR
az acr login --name myresilienceacr

# Push image
docker push myresilienceacr.azurecr.io/resilience4j-demo:v2
```

### **2. Deploy to AKS**

```bash
# Apply deployment
kubectl apply -f k8s/deployment-v2.yaml

# Verify deployment
kubectl get pods -n resilience4j-demo -w

# Check logs for Application Insights connection
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo | grep -i "application insights"
```

Expected log output:
```
Application Insights telemetry initialized with connection string: InstrumentationKey=...
Application Insights Telemetry (unconfigured) is disabled
AI: INFO 21-11-2025 13:30:00.000 ApplicationInsightsAppender initialized with connection string
```

---

## 📊 Verify Telemetry in Azure Portal

### **1. View Metrics**

Navigate to:
```
Azure Portal → Application Insights → Metrics

Available metrics:
- Server requests (HTTP)
- Server response time
- Failed requests
- Dependency calls
- JVM memory used
- JVM CPU usage
- Process CPU usage
- Process memory usage
```

### **2. View Logs**

Navigate to:
```
Azure Portal → Application Insights → Logs

Sample queries:

# All application logs
traces
| where cloud_RoleName == "resilience4j-demo"
| order by timestamp desc

# Error logs only
traces
| where severityLevel >= 3
| where cloud_RoleName == "resilience4j-demo"
| project timestamp, message, severityLevel, customDimensions

# Logs with specific trace ID
traces
| where customDimensions.traceId == "abc123..."
```

### **3. View Distributed Traces**

Navigate to:
```
Azure Portal → Application Insights → Transaction search

Filter by:
- Event type: Request
- Result code: All / 2xx / 4xx / 5xx
- Duration: > 1000ms

End-to-end transaction details show:
- Request → Controller → Service → Database/External API
- Timing for each component
- Exception details if any
```

### **4. Application Map**

Navigate to:
```
Azure Portal → Application Insights → Application Map

Shows:
- Service dependencies
- Call volumes
- Average response times
- Failure rates
```

---

## 📈 Custom Metrics and Events

### **Java Code - Custom Metrics**

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    
    private final Counter orderCounter;
    private final Timer orderProcessingTimer;
    private final MeterRegistry meterRegistry;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Create custom counter
        this.orderCounter = Counter.builder("orders.processed")
            .description("Number of orders processed")
            .tag("service", "order-service")
            .register(meterRegistry);
        
        // Create custom timer
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .tag("service", "order-service")
            .register(meterRegistry);
    }
    
    public void processOrder(Order order) {
        orderProcessingTimer.record(() -> {
            // Business logic
            orderCounter.increment();
        });
    }
}
```

### **Java Code - Custom Events**

```java
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService {
    
    private final TelemetryClient telemetryClient;
    
    public TelemetryService(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
    }
    
    public void trackCustomEvent(String eventName, Map<String, String> properties) {
        telemetryClient.trackEvent(eventName, properties, null);
    }
    
    public void trackBusinessMetric(String metricName, double value) {
        telemetryClient.trackMetric(metricName, value);
    }
}
```

---

## 🔍 Kusto Query Examples

### **Query 1: HTTP Request Statistics**

```kusto
requests
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize 
    RequestCount = count(),
    AvgDuration = avg(duration),
    P50 = percentile(duration, 50),
    P95 = percentile(duration, 95),
    P99 = percentile(duration, 99)
  by name
| order by RequestCount desc
```

### **Query 2: Error Analysis**

```kusto
exceptions
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(24h)
| summarize ErrorCount = count() by type, outerMessage
| order by ErrorCount desc
```

### **Query 3: Dependency Performance**

```kusto
dependencies
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize 
    CallCount = count(),
    AvgDuration = avg(duration),
    FailureRate = countif(success == false) * 100.0 / count()
  by target, name
| order by CallCount desc
```

### **Query 4: Distributed Trace Analysis**

```kusto
let traceId = "abc123...";
union requests, dependencies
| where operation_Id == traceId
| project timestamp, itemType, name, duration, success
| order by timestamp asc
```

### **Query 5: Custom Metrics**

```kusto
customMetrics
| where cloud_RoleName == "resilience4j-demo"
| where name == "orders.processed"
| summarize TotalOrders = sum(value) by bin(timestamp, 5m)
| render timechart
```

---

## 🚨 Alerts Configuration

### **Create Alert Rules**

```bash
# High error rate alert
az monitor metrics alert create \
  --name "High Error Rate" \
  --resource-group $RESOURCE_GROUP \
  --scopes $(az monitor app-insights component show --app $APP_INSIGHTS_NAME --resource-group $RESOURCE_GROUP --query id -o tsv) \
  --condition "avg requests/failed > 10" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --action-group "NotificationActionGroup"

# High response time alert
az monitor metrics alert create \
  --name "High Response Time" \
  --resource-group $RESOURCE_GROUP \
  --scopes $(az monitor app-insights component show --app $APP_INSIGHTS_NAME --resource-group $RESOURCE_GROUP --query id -o tsv) \
  --condition "avg requests/duration > 5000" \
  --window-size 5m \
  --evaluation-frequency 1m
```

---

## 🎯 Best Practices

### ✅ **DO:**
- Use connection string (not instrumentation key)
- Set appropriate sampling rate for production (10-20%)
- Add custom dimensions for better filtering
- Use structured logging (JSON)
- Correlate logs with trace IDs
- Monitor dependency calls
- Set up alerts for critical metrics
- Use Application Map for visualization

### ❌ **DON'T:**
- Log sensitive data (passwords, tokens, PII)
- Use 100% sampling in high-traffic production
- Ignore sampling bias in analysis
- Send unnecessary telemetry (health check endpoints)
- Forget to set cloud role name/instance
- Mix environment telemetry (dev/staging/prod)

---

## 🧪 Testing

### **Local Testing**

```bash
# Set connection string locally
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=...;IngestionEndpoint=..."

# Run application
mvn spring-boot:run

# Generate some requests
curl http://localhost:8070/api/test
curl http://localhost:8070/actuator/health
```

### **AKS Testing**

```bash
# Port-forward to test locally
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# Generate traffic
for i in {1..100}; do
  curl http://localhost:8070/api/test
  sleep 1
done

# Check Application Insights in 2-3 minutes
```

---

## 📚 References

- [Application Insights Java Documentation](https://learn.microsoft.com/en-us/azure/azure-monitor/app/java-in-process-agent)
- [Micrometer Azure Monitor](https://micrometer.io/docs/registry/azure-monitor)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kusto Query Language](https://learn.microsoft.com/en-us/azure/data-explorer/kusto/query/)

---

## 🎓 Next Steps

1. ✅ Set up Application Insights resource
2. ✅ Add dependencies to pom.xml
3. ✅ Configure application.yml
4. ✅ Update Dockerfile with Java agent
5. ✅ Create Kubernetes secret
6. ✅ Deploy to AKS
7. ✅ Verify telemetry in Azure Portal
8. ✅ Create custom metrics and events
9. ✅ Set up alerts
10. ✅ Build dashboards in Azure Portal
