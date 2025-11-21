# Complete Kubernetes Observability Guide
## Monitoring, Logging, and Distributed Tracing for Resilience4j Demo on AKS

---

## 📋 Table of Contents

1. [Kubernetes Manifests Overview](#kubernetes-manifests-overview)
2. [Azure Application Insights Setup](#azure-application-insights-setup)
3. [Deployment with Observability](#deployment-with-observability)
4. [Monitoring & Metrics](#monitoring--metrics)
5. [Logging & Log Analysis](#logging--log-analysis)
6. [Distributed Tracing](#distributed-tracing)
7. [Alerting & Dashboards](#alerting--dashboards)
8. [Complete Deployment Workflow](#complete-deployment-workflow)

---

## 🎯 Kubernetes Manifests Overview

### 📁 Manifest Structure

```
k8s/
├── deployment.yaml              # V1 deployment (namespace, service, ingress, HPA)
├── deployment-v2.yaml           # V2 deployment with observability
├── hpa.yaml                     # Horizontal Pod Autoscaler for V2
├── istio-resilience.yaml        # Circuit breaker, retries, connection pools
├── istio-virtualservice.yaml    # Canary & Blue-Green deployments
└── COMPLETE_OBSERVABILITY_GUIDE.md  # This guide
```

### 🏗️ Architecture with Observability

```
┌────────────────────────────────────────────────────────────────────┐
│                     Istio Ingress Gateway                          │
│                   (External Traffic + Tracing)                      │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│         Istio VirtualService (90% v1, 10% v2 Canary)               │
│         + Distributed Tracing (Jaeger/Zipkin compatible)           │
└──────────┬──────────────────────────────────┬──────────────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────┐          ┌──────────────────────┐
│   Service: v1        │          │   Service: v2        │
│   Port: 80→8070      │          │   Port: 80→8070      │
└──────────┬───────────┘          └──────────┬───────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────┐          ┌──────────────────────┐
│  Deployment: v1      │          │  Deployment: v2      │
│  Replicas: 3-10      │          │  Replicas: 3-10      │
│  Port: 8070          │          │  Port: 8070          │
│                      │          │                      │
│  ┌────────────────┐ │          │  ┌────────────────┐ │
│  │ Spring Boot    │ │          │  │ Spring Boot    │ │
│  │ + Actuator     │ │          │  │ + Actuator     │ │
│  │ + Micrometer   │─┼──────────┼─→│ + Micrometer   │ │
│  │ + Logback      │ │          │  │ + Logback      │ │
│  │ + AI Agent     │ │          │  │ + AI Agent     │ │
│  └────────┬───────┘ │          │  └────────┬───────┘ │
└───────────┼─────────┘          └───────────┼─────────┘
            │                                 │
            └────────────┬────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│              Azure Application Insights                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ Metrics  │  │   Logs   │  │  Traces  │  │  Alerts  │          │
│  │ • CPU    │  │ • JSON   │  │ • E2E    │  │ • Error  │          │
│  │ • Memory │  │ • Errors │  │ • Deps   │  │ • Latency│          │
│  │ • RPS    │  │ • Trace  │  │ • Spans  │  │ • Custom │          │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘          │
└────────────────────────────────────────────────────────────────────┘
            │                                 │
            ▼                                 ▼
┌──────────────────────┐          ┌──────────────────────┐
│   Azure Monitor      │          │  Log Analytics       │
│   Dashboards         │          │  Workspace           │
└──────────────────────┘          └──────────────────────┘
```

---

## 🔧 Azure Application Insights Setup

### Step 1: Create Application Insights Resource

```bash
# Set variables
RESOURCE_GROUP="rg-resilience4j-demo"
LOCATION="southeastasia"
APP_INSIGHTS_NAME="appi-resilience4j-demo"
WORKSPACE_NAME="log-resilience4j-demo"
AKS_CLUSTER_NAME="aks-resilience4j-demo"

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

# Get Connection String and Instrumentation Key
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

### Step 2: Store Credentials in Kubernetes Secrets

```bash
# Create namespace
kubectl create namespace resilience4j-demo --dry-run=client -o yaml | kubectl apply -f -

# Create Application Insights secret
kubectl create secret generic app-insights-secret \
  --from-literal=connection-string="$CONNECTION_STRING" \
  --from-literal=instrumentation-key="$INSTRUMENTATION_KEY" \
  -n resilience4j-demo

# Verify secret
kubectl get secret app-insights-secret -n resilience4j-demo -o jsonpath='{.data.connection-string}' | base64 -d
```

---

## 📦 Update Application for Observability

### Maven Dependencies (pom.xml)

Add these dependencies to your existing `pom.xml`:

```xml
<dependencies>
    <!-- Existing dependencies -->
    
    <!-- Application Insights Spring Boot Starter -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-spring-boot-starter</artifactId>
        <version>3.4.19</version>
    </dependency>
    
    <!-- Application Insights Runtime Attach -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-runtime-attach</artifactId>
        <version>3.5.1</version>
    </dependency>
    
    <!-- Micrometer Azure Monitor (for metrics) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-azure-monitor</artifactId>
    </dependency>
    
    <!-- Application Insights Logback Appender -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>applicationinsights-logging-logback</artifactId>
        <version>3.4.19</version>
    </dependency>
</dependencies>
```

### Spring Boot Configuration (application.yml)

Update your `src/main/resources/application.yml`:

```yaml
server:
  port: 8070
  shutdown: graceful

spring:
  application:
    name: resilience4j-demo
    version: ${APP_VERSION:v2}
  
  lifecycle:
    timeout-per-shutdown-phase: 30s

# Azure Application Insights
azure:
  application-insights:
    enabled: true
    connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
    instrumentation-key: ${APPLICATIONINSIGHTS_INSTRUMENTATION_KEY:}
    
    # Sampling configuration
    sampling:
      percentage: ${AI_SAMPLING_PERCENTAGE:100.0}
    
    web:
      enabled: true
    
    heartbeat:
      enabled: true
    
    # Cloud role configuration
    cloud:
      role-name: ${spring.application.name}
      role-instance: ${HOSTNAME:localhost}

# Management endpoints
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: "*"
  
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState,ping
        readiness:
          include: readinessState,diskSpace
  
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
    diskspace:
      enabled: true
      threshold: 10MB
  
  # Metrics configuration
  metrics:
    tags:
      application: ${spring.application.name}
      version: ${APP_VERSION:v2}
      environment: ${ENVIRONMENT:production}
      region: ${REGION:southeastasia}
      pod: ${POD_NAME:unknown}
      node: ${NODE_NAME:unknown}
    
    distribution:
      percentiles-histogram:
        http.server.requests: true
    
    export:
      # Prometheus (for Kubernetes monitoring)
      prometheus:
        enabled: true
        step: 1m
        descriptions: true
      
      # Azure Monitor
      azure-monitor:
        enabled: true
        connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
        step: 60s
    
    enable:
      jvm: true
      process: true
      system: true
      tomcat: true
      logback: true
      http: true
  
  # Distributed Tracing
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_RATE:1.0}
    baggage:
      enabled: true
      correlation:
        enabled: true

# Logging configuration
logging:
  level:
    root: INFO
    com.example.resilience: DEBUG
    com.microsoft.applicationinsights: INFO
    io.github.resilience4j: DEBUG
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%mdc{traceId}/%mdc{spanId}]){yellow} %clr(%-5level){cyan} %clr(%logger{36}){blue} - %msg%n"
```

### Logback Configuration (logback-spring.xml)

Update `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <springProperty scope="context" name="applicationName" source="spring.application.name"/>
    <springProperty scope="context" name="aiConnectionString" source="azure.application-insights.connection-string"/>
    
    <!-- Application Insights Appender -->
    <appender name="AI_APPENDER" class="com.microsoft.applicationinsights.logback.ApplicationInsightsAppender">
        <connectionString>${aiConnectionString}</connectionString>
    </appender>
    
    <!-- JSON Console Appender -->
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>
                {
                  "application": "${applicationName:-unknown}",
                  "environment": "${ENVIRONMENT:-dev}"
                }
            </customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>sessionId</includeMdcKeyName>
        </encoder>
    </appender>
    
    <!-- File Appender with Rolling -->
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
    
    <!-- Default configuration (no profile) -->
    <root level="INFO">
        <appender-ref ref="JSON"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="AI_APPENDER"/>
    </root>
    
    <logger name="com.example.resilience" level="DEBUG"/>
    <logger name="io.github.resilience4j" level="DEBUG"/>
</configuration>
```

---

## 🐳 Update Dockerfile with Application Insights Agent

Update your `Dockerfile`:

```dockerfile
# Multi-stage build
FROM maven:3.9.9-eclipse-temurin-23-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:23-jre-alpine

# Add Spring user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy application JAR
COPY --from=builder /app/target/resilience4j-demo-1.0.0.jar app.jar

# Download Application Insights Java Agent
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.5.1/applicationinsights-agent-3.5.1.jar \
    /app/applicationinsights-agent.jar

# Optional: Copy custom AI configuration
# COPY applicationinsights.json /app/applicationinsights.json

RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8070

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8070/actuator/health/liveness || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Run with Application Insights Agent
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -javaagent:/app/applicationinsights-agent.jar -jar app.jar"]
```

---

## ☸️ Update Kubernetes Deployment with Observability

Create/update `deployment-v2-observability.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilience4j-demo-v2
  namespace: resilience4j-demo
  labels:
    app: resilience4j-demo
    version: v2
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: resilience4j-demo
      version: v2
  template:
    metadata:
      labels:
        app: resilience4j-demo
        version: v2
      annotations:
        # Prometheus scraping
        prometheus.io/scrape: "true"
        prometheus.io/port: "8070"
        prometheus.io/path: "/actuator/prometheus"
        # Istio sidecar injection
        sidecar.istio.io/inject: "true"
    spec:
      terminationGracePeriodSeconds: 30
      
      containers:
      - name: app
        image: myresilienceacr.azurecr.io/resilience4j-demo:v2
        imagePullPolicy: Always
        
        ports:
        - name: http
          containerPort: 8070
          protocol: TCP
        
        env:
        # Application Insights - from Secret
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: connection-string
        
        - name: APPLICATIONINSIGHTS_INSTRUMENTATION_KEY
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: instrumentation-key
        
        # Kubernetes metadata for observability
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
        
        - name: HOSTNAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        
        # Application configuration
        - name: APP_VERSION
          value: "v2"
        
        - name: ENVIRONMENT
          value: "production"
        
        - name: REGION
          value: "southeastasia"
        
        # Application Insights Agent configuration
        - name: APPLICATIONINSIGHTS_ROLE_NAME
          value: "resilience4j-demo"
        
        - name: APPLICATIONINSIGHTS_ROLE_INSTANCE
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        
        # Sampling rates (adjust for production)
        - name: AI_SAMPLING_PERCENTAGE
          value: "100.0"  # 100% for demo, use 10-20 in production
        
        - name: TRACING_SAMPLING_RATE
          value: "1.0"     # 100% for demo, use 0.1-0.2 in production
        
        # JVM settings
        - name: JAVA_OPTS
          value: "-Xms1024m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
        
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        
        # Health probes
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8070
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
          successThreshold: 1
        
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8070
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
          successThreshold: 1
        
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8070
          initialDelaySeconds: 0
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30
          successThreshold: 1
        
        # Graceful shutdown
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 15"]
---
apiVersion: v1
kind: Service
metadata:
  name: resilience4j-demo-v2
  namespace: resilience4j-demo
  labels:
    app: resilience4j-demo
    version: v2
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8070
    protocol: TCP
    name: http
  selector:
    app: resilience4j-demo
    version: v2
---
apiVersion: v1
kind: Service
metadata:
  name: resilience4j-demo
  namespace: resilience4j-demo
  labels:
    app: resilience4j-demo
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8070
    protocol: TCP
    name: http
  selector:
    app: resilience4j-demo
```

---

## 🚀 Complete Deployment Workflow

### Phase 1: Build and Push Images

```bash
cd resilience4j-demo-v2

# Build v2 image
docker build -t resilience4j-demo:v2 .

# Tag for ACR
docker tag resilience4j-demo:v2 myresilienceacr.azurecr.io/resilience4j-demo:v2

# Login to ACR
az acr login --name myresilienceacr

# Push to ACR
docker push myresilienceacr.azurecr.io/resilience4j-demo:v2
```

### Phase 2: Deploy to AKS

```bash
# Apply namespace (if not exists)
kubectl apply -f k8s/deployment.yaml

# Apply v2 deployment with observability
kubectl apply -f k8s/deployment-v2.yaml

# Apply HPA
kubectl apply -f k8s/hpa.yaml

# Apply Istio configurations
kubectl apply -f k8s/istio-virtualservice.yaml
kubectl apply -f k8s/istio-resilience.yaml

# Verify deployment
kubectl get all -n resilience4j-demo
kubectl get pods -n resilience4j-demo -w
```

### Phase 3: Verify Observability

```bash
# Check if Application Insights is connected
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo | grep -i "application insights"

# Expected output:
# AI: INFO ApplicationInsights Java Agent 3.5.1 loaded successfully
# AI: INFO Connection string: InstrumentationKey=...

# Test health endpoints
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl http://localhost:8070/actuator/health

# Test metrics endpoint
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl http://localhost:8070/actuator/metrics

# Test Prometheus endpoint
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl http://localhost:8070/actuator/prometheus
```

---

## 📊 Monitoring & Metrics

### Available Metrics Sources

#### 1. **Prometheus (via Actuator)**

```bash
# Port-forward to access locally
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# Access Prometheus metrics
curl http://localhost:8070/actuator/prometheus

# Key metrics available:
# - jvm_memory_used_bytes
# - jvm_threads_live_threads
# - process_cpu_usage
# - http_server_requests_seconds
# - resilience4j_circuitbreaker_state
# - resilience4j_retry_calls_total
# - system_cpu_usage
```

#### 2. **Azure Application Insights Metrics**

Navigate to Azure Portal:
```
Application Insights → Metrics

Select metrics:
- Server requests (count, rate)
- Server response time (avg, P50, P95, P99)
- Failed requests
- Dependency calls
- Dependency duration
- JVM memory used
- JVM CPU usage
- Process CPU usage
- Process memory usage
```

### Sample Kusto Queries for Metrics

```kusto
// Request rate per minute
requests
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize RequestsPerMinute = count() by bin(timestamp, 1m)
| render timechart

// Average response time by operation
requests
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize AvgDuration = avg(duration), P95 = percentile(duration, 95) 
  by name
| order by AvgDuration desc

// Error rate percentage
requests
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize TotalRequests = count(), 
            FailedRequests = countif(success == false)
| extend ErrorRate = (FailedRequests * 100.0) / TotalRequests
| project ErrorRate, TotalRequests, FailedRequests

// JVM Memory usage
customMetrics
| where cloud_RoleName == "resilience4j-demo"
| where name has "jvm.memory"
| summarize avg(value) by name, bin(timestamp, 5m)
| render timechart

// Circuit Breaker state
customMetrics
| where cloud_RoleName == "resilience4j-demo"
| where name == "resilience4j.circuitbreaker.state"
| project timestamp, name, value, customDimensions
| order by timestamp desc
```

---

## 📝 Logging & Log Analysis

### Log Collection Flow

```
Application (Logback)
    │
    ├─→ Console (JSON format)
    ├─→ File (Rolling, compressed)
    └─→ Application Insights (via AI Appender)
         │
         └─→ Log Analytics Workspace
              │
              └─→ Azure Monitor Logs
```

### Query Logs in Azure Portal

Navigate to:
```
Application Insights → Logs
```

#### Sample Log Queries

```kusto
// All application logs (last hour)
traces
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| project timestamp, severityLevel, message, customDimensions
| order by timestamp desc

// Error and warning logs only
traces
| where cloud_RoleName == "resilience4j-demo"
| where severityLevel >= 2  // 2=Warning, 3=Error, 4=Critical
| where timestamp > ago(24h)
| project timestamp, severityLevel, message, operation_Name, customDimensions
| order by timestamp desc

// Logs with specific trace ID (distributed tracing)
traces
| where cloud_RoleName == "resilience4j-demo"
| where customDimensions.traceId == "YOUR_TRACE_ID"
| project timestamp, message, severityLevel
| order by timestamp asc

// Logs by pod instance
traces
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| summarize count() by tostring(cloud_RoleInstance)
| order by count_ desc

// Exception analysis
exceptions
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(24h)
| summarize ExceptionCount = count() by type, outerMessage
| order by ExceptionCount desc
| take 10

// Circuit Breaker events in logs
traces
| where cloud_RoleName == "resilience4j-demo"
| where message has "CircuitBreaker" or message has "resilience4j"
| where timestamp > ago(1h)
| project timestamp, message, severityLevel
| order by timestamp desc
```

### Access Logs via kubectl

```bash
# Get logs from current running pod
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo

# Follow logs in real-time
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo

# Get logs from specific pod
kubectl logs resilience4j-demo-v2-xxxxx-yyy -n resilience4j-demo

# Get logs from previous crashed container
kubectl logs resilience4j-demo-v2-xxxxx-yyy -n resilience4j-demo --previous

# Filter logs by pattern
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo | grep ERROR

# Get logs with timestamps
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo --timestamps=true

# Get last N lines
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo --tail=100
```

---

## 🔍 Distributed Tracing

### Trace Collection Architecture

```
Request → Istio Sidecar (trace headers)
    │
    └─→ Spring Boot App
         │
         ├─→ Micrometer Tracing (Brave)
         │    └─→ Trace ID & Span ID generation
         │
         └─→ Application Insights Agent
              └─→ Auto-instrumentation
                   │
                   └─→ Azure Application Insights
                        └─→ Application Map & Transaction Search
```

### View Traces in Azure Portal

#### 1. **Transaction Search**

Navigate to:
```
Application Insights → Transaction search
```

Filter by:
- Event type: Request
- Result code: All / 2xx / 4xx / 5xx
- Duration: > 1000ms
- Time range: Last hour / 24 hours / Custom

#### 2. **Application Map**

Navigate to:
```
Application Insights → Application Map
```

Shows:
- Service dependencies
- Call volumes (requests/sec)
- Average response times
- Failure rates
- External dependencies (databases, APIs)

#### 3. **End-to-End Transaction Details**

Click on any request in Transaction Search to see:
- Complete trace timeline
- All spans (service calls)
- Dependencies (DB, HTTP calls)
- Exceptions if any
- Custom properties
- Trace ID for correlation

### Sample Trace Queries

```kusto
// Find slow requests (> 2 seconds)
requests
| where cloud_RoleName == "resilience4j-demo"
| where duration > 2000
| where timestamp > ago(1h)
| project timestamp, name, duration, success, resultCode, operation_Id
| order by duration desc

// Trace dependencies for a specific request
let operationId = "YOUR_OPERATION_ID";
union requests, dependencies
| where operation_Id == operationId
| project timestamp, itemType, name, duration, success, target
| order by timestamp asc

// Find failed dependency calls
dependencies
| where cloud_RoleName == "resilience4j-demo"
| where success == false
| where timestamp > ago(24h)
| summarize FailureCount = count() by target, name, resultCode
| order by FailureCount desc

// Request flow analysis
requests
| where cloud_RoleName == "resilience4j-demo"
| where timestamp > ago(1h)
| extend OperationName = operation_Name
| join kind=leftouter (
    dependencies
    | where cloud_RoleName == "resilience4j-demo"
    | summarize DependencyCalls = count(), AvgDepDuration = avg(duration)
      by operation_Id
) on operation_Id
| project timestamp, name, duration, DependencyCalls, AvgDepDuration
| order by timestamp desc
```

### Correlation with Logs

```kusto
// Find all logs for a specific trace
let traceId = "YOUR_TRACE_ID";
union traces, requests, dependencies, exceptions
| where operation_Id == traceId or customDimensions.traceId == traceId
| project timestamp, itemType, message, name, duration
| order by timestamp asc
```

---

## 🚨 Alerting & Dashboards

### Create Alert Rules

#### 1. **High Error Rate Alert**

```bash
az monitor metrics alert create \
  --name "Resilience4j-HighErrorRate" \
  --resource-group $RESOURCE_GROUP \
  --scopes $(az monitor app-insights component show \
    --app $APP_INSIGHTS_NAME \
    --resource-group $RESOURCE_GROUP \
    --query id -o tsv) \
  --condition "avg requests/failed > 10" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --description "Alert when failed requests exceed 10 per minute"
```

#### 2. **High Response Time Alert**

```bash
az monitor metrics alert create \
  --name "Resilience4j-HighLatency" \
  --resource-group $RESOURCE_GROUP \
  --scopes $(az monitor app-insights component show \
    --app $APP_INSIGHTS_NAME \
    --resource-group $RESOURCE_GROUP \
    --query id -o tsv) \
  --condition "avg requests/duration > 5000" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --description "Alert when average response time exceeds 5 seconds"
```

#### 3. **Pod Restart Alert** (via Log Analytics)

```kusto
// Create alert from this query
KubePodInventory
| where Namespace == "resilience4j-demo"
| where PodStatus == "Running"
| summarize RestartCount = sum(PodRestartCount) by Name
| where RestartCount > 3
```

### Create Custom Dashboard

Navigate to:
```
Azure Portal → Dashboards → + New dashboard
```

Add tiles:
1. **Request Rate Chart** (from metrics)
2. **Response Time Chart** (P50, P95, P99)
3. **Error Rate Chart**
4. **Application Map** (dependencies visualization)
5. **Active Pods** (from AKS metrics)
6. **CPU & Memory Usage** (pod metrics)
7. **Circuit Breaker Events** (custom metric)
8. **Recent Errors** (from logs query)

---

## 🧪 Testing Observability

### Generate Traffic

```bash
# Port-forward to access the service
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# Generate normal traffic
for i in {1..100}; do
  curl http://localhost:8070/api/test
  sleep 1
done

# Generate errors
for i in {1..20}; do
  curl http://localhost:8070/api/error
  sleep 1
done

# Test circuit breaker
for i in {1..50}; do
  curl http://localhost:8070/api/circuit-breaker/demo
  sleep 0.5
done

# Test with custom headers (for tracing)
curl -H "X-Request-ID: test-123" \
     -H "User-ID: user-456" \
     http://localhost:8070/api/test
```

### Verify in Azure Portal

Wait 2-3 minutes for telemetry to appear, then check:

1. **Metrics**: See request rate increase
2. **Logs**: Find your requests with trace IDs
3. **Traces**: See end-to-end transaction details
4. **Application Map**: View call patterns

---

## 📋 Complete Deployment Checklist

### ✅ Pre-Deployment
- [ ] Azure Application Insights created
- [ ] Log Analytics Workspace configured
- [ ] Kubernetes secret created with AI credentials
- [ ] pom.xml updated with AI dependencies
- [ ] application.yml configured for observability
- [ ] logback-spring.xml updated with AI appender
- [ ] Dockerfile updated with AI agent
- [ ] Docker image built and pushed to ACR

### ✅ Deployment
- [ ] Namespace created: `resilience4j-demo`
- [ ] V1 deployed: `kubectl apply -f deployment.yaml`
- [ ] V2 deployed: `kubectl apply -f deployment-v2.yaml`
- [ ] HPA applied: `kubectl apply -f hpa.yaml`
- [ ] Istio VirtualService applied: `kubectl apply -f istio-virtualservice.yaml`
- [ ] Istio resilience applied: `kubectl apply -f istio-resilience.yaml`
- [ ] All pods running: `kubectl get pods -n resilience4j-demo`

### ✅ Verification
- [ ] Health endpoints responding: `/actuator/health/liveness`, `/actuator/health/readiness`
- [ ] Prometheus metrics available: `/actuator/prometheus`
- [ ] Application Insights connection confirmed in logs
- [ ] Metrics appearing in Azure Portal (wait 2-3 minutes)
- [ ] Logs appearing in Log Analytics
- [ ] Traces visible in Transaction Search
- [ ] Application Map showing dependencies

### ✅ Monitoring Setup
- [ ] Alert rules created (error rate, latency)
- [ ] Custom dashboard configured
- [ ] Log queries saved
- [ ] Metric charts configured
- [ ] Team notifications configured

---

## 🎯 Summary

You now have **complete observability** for your Resilience4j demo application:

### ✨ What You Get:

1. **📊 Metrics**:
   - Real-time performance metrics (CPU, memory, requests)
   - Custom business metrics
   - Circuit breaker, retry, rate limiter metrics
   - Prometheus + Azure Monitor integration

2. **📝 Logs**:
   - Structured JSON logging
   - Centralized log aggregation in Azure
   - Correlation with trace IDs
   - Easy querying with Kusto (KQL)

3. **🔍 Traces**:
   - End-to-end distributed tracing
   - Request flow visualization
   - Dependency call tracking
   - Performance bottleneck identification

4. **🚨 Alerts**:
   - Proactive error detection
   - Performance degradation alerts
   - Custom business metric alerts

5. **📈 Dashboards**:
   - Real-time visibility
   - Historical analysis
   - Application Map
   - Custom visualizations

### 🔄 Next Steps:

1. Deploy to AKS following the workflow above
2. Generate test traffic
3. Explore metrics in Azure Portal
4. Query logs with provided Kusto queries
5. Analyze traces in Transaction Search
6. Set up custom alerts
7. Build team dashboards
8. Fine-tune sampling rates for production

---

## 📚 Additional Resources

- [Azure Application Insights Documentation](https://learn.microsoft.com/en-us/azure/azure-monitor/app/app-insights-overview)
- [Kusto Query Language (KQL)](https://learn.microsoft.com/en-us/azure/data-explorer/kusto/query/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Istio Observability](https://istio.io/latest/docs/tasks/observability/)
