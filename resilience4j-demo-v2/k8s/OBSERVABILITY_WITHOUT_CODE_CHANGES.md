# Observability for Existing Kubernetes Deployment
## Monitor, Log, and Trace Without Application Code Changes

---

## 🎯 Overview

This guide shows how to get **metrics, logs, and traces** from your **existing** Resilience4j Demo deployment on AKS **without modifying** application code or Docker images.

### What We'll Use:
- ✅ **Azure Monitor Container Insights** - Metrics & logs
- ✅ **kubectl** - Pod logs and metrics
- ✅ **Prometheus** - Already exposed via `/actuator/prometheus`
- ✅ **Istio Service Mesh** - Distributed tracing
- ✅ **Azure Log Analytics** - Centralized logging

---

## 📊 Part 1: Container-Level Monitoring (No Code Changes)

### Enable Container Insights on AKS

```bash
# Set variables
RESOURCE_GROUP="rg-resilience4j-demo"
AKS_CLUSTER_NAME="aks-resilience4j-demo"
WORKSPACE_NAME="log-resilience4j-demo"
LOCATION="southeastasia"

# Create Log Analytics Workspace (if not exists)
az monitor log-analytics workspace create \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $WORKSPACE_NAME \
  --location $LOCATION

# Get Workspace ID
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $WORKSPACE_NAME \
  --query id -o tsv)

# Enable Container Insights on AKS
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER_NAME \
  --addons monitoring \
  --workspace-resource-id $WORKSPACE_ID

# Verify addon is enabled
az aks show \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER_NAME \
  --query addonProfiles.omsagent
```

**Expected output:**
```json
{
  "config": {
    "logAnalyticsWorkspaceResourceID": "/subscriptions/.../workspaces/log-resilience4j-demo"
  },
  "enabled": true
}
```

---

## 📈 Part 2: Access Metrics (Existing Deployment)

### 2.1 Kubernetes Metrics via kubectl

```bash
# Get pod resource usage
kubectl top pods -n resilience4j-demo

# Output example:
# NAME                                    CPU(cores)   MEMORY(bytes)
# resilience4j-demo-v1-7d8f9c8b6d-abc12   250m         512Mi
# resilience4j-demo-v1-7d8f9c8b6d-def34   240m         498Mi
# resilience4j-demo-v2-5c7d8e9f7g-ghi56   280m         768Mi

# Get node resource usage
kubectl top nodes

# Describe pod to see resource limits/requests
kubectl describe pod resilience4j-demo-v2-xxxxx -n resilience4j-demo | grep -A 5 "Requests:"
```

### 2.2 Prometheus Metrics (Already Available)

Your application already exposes Prometheus metrics at `/actuator/prometheus`. Access them:

```bash
# Port-forward to a pod
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# In another terminal, scrape metrics
curl http://localhost:8070/actuator/prometheus

# Or use browser
# http://localhost:8070/actuator/prometheus
```

**Key Metrics Available:**
```
# JVM Metrics
jvm_memory_used_bytes{area="heap"}
jvm_memory_max_bytes{area="heap"}
jvm_threads_live_threads
jvm_gc_pause_seconds_count

# System Metrics  
system_cpu_usage
process_cpu_usage
process_uptime_seconds

# HTTP Metrics
http_server_requests_seconds_count{uri="/api/test"}
http_server_requests_seconds_sum{uri="/api/test"}

# Resilience4j Metrics
resilience4j_circuitbreaker_state{name="backendService",state="closed"}
resilience4j_retry_calls_total{name="backendService",kind="successful"}
resilience4j_ratelimiter_available_permissions{name="default"}
```

### 2.3 Azure Monitor Container Insights Metrics

Navigate to Azure Portal:
```
AKS Cluster → Insights → Metrics

Available metrics:
- CPU Usage (millicores)
- Memory Working Set (bytes)
- Network In/Out (bytes)
- Disk Read/Write (bytes)
- Pod count
- Container restarts
```

**Sample queries in Insights:**

```kusto
// CPU usage per pod
Perf
| where ObjectName == "K8SContainer"
| where CounterName == "cpuUsageNanoCores"
| where InstanceName contains "resilience4j-demo"
| summarize AvgCPU = avg(CounterValue) by InstanceName, bin(TimeGenerated, 5m)
| render timechart

// Memory usage per pod
Perf
| where ObjectName == "K8SContainer"
| where CounterName == "memoryWorkingSetBytes"
| where InstanceName contains "resilience4j-demo"
| summarize AvgMemory = avg(CounterValue) / 1024 / 1024 by InstanceName, bin(TimeGenerated, 5m)
| render timechart

// Pod count over time
KubePodInventory
| where Namespace == "resilience4j-demo"
| summarize PodCount = dcount(PodUid) by bin(TimeGenerated, 5m), PodStatus
| render timechart
```

---

## 📝 Part 3: Access Logs (Existing Deployment)

### 3.1 Real-Time Logs via kubectl

```bash
# Get logs from deployment (latest pod)
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo

# Follow logs in real-time
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo

# Get logs from all pods with label
kubectl logs -l app=resilience4j-demo,version=v2 -n resilience4j-demo --tail=100

# Get logs from specific pod
POD_NAME=$(kubectl get pods -n resilience4j-demo -l app=resilience4j-demo,version=v2 -o jsonpath='{.items[0].metadata.name}')
kubectl logs $POD_NAME -n resilience4j-demo

# Get logs from previous crashed container
kubectl logs $POD_NAME -n resilience4j-demo --previous

# Filter logs by level
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo | grep ERROR
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo | grep WARN

# Get logs with timestamps
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo --timestamps=true

# Get last 50 lines
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo --tail=50

# Stream logs from multiple pods
kubectl logs -f -l app=resilience4j-demo -n resilience4j-demo --all-containers=true
```

### 3.2 Container Logs in Azure Log Analytics

Once Container Insights is enabled, logs are automatically collected.

Navigate to:
```
AKS Cluster → Logs
```

**Sample Log Queries:**

```kusto
// All container logs for resilience4j-demo
ContainerLog
| where Namespace == "resilience4j-demo"
| where TimeGenerated > ago(1h)
| project TimeGenerated, ContainerID, LogEntry
| order by TimeGenerated desc
| take 100

// Error logs only
ContainerLog
| where Namespace == "resilience4j-demo"
| where LogEntry has "ERROR" or LogEntry has "Exception"
| where TimeGenerated > ago(24h)
| project TimeGenerated, Name, LogEntry
| order by TimeGenerated desc

// Logs by pod
ContainerLog
| where Namespace == "resilience4j-demo"
| where TimeGenerated > ago(1h)
| summarize LogCount = count() by Name
| order by LogCount desc

// Filter logs by container name
ContainerLog
| where Namespace == "resilience4j-demo"
| where Name contains "resilience4j-demo-v2"
| where TimeGenerated > ago(1h)
| project TimeGenerated, Name, LogEntry
| order by TimeGenerated desc

// Application startup logs
ContainerLog
| where Namespace == "resilience4j-demo"
| where LogEntry has "Spring Boot" or LogEntry has "Started"
| where TimeGenerated > ago(24h)
| project TimeGenerated, Name, LogEntry
| order by TimeGenerated desc

// JVM or memory related logs
ContainerLog
| where Namespace == "resilience4j-demo"
| where LogEntry has "OutOfMemory" or LogEntry has "GC" or LogEntry has "heap"
| where TimeGenerated > ago(24h)
| project TimeGenerated, Name, LogEntry
```

### 3.3 Export Logs to File

```bash
# Export last 1000 lines to file
kubectl logs deployment/resilience4j-demo-v2 -n resilience4j-demo --tail=1000 > app-logs.txt

# Export logs from all v2 pods
for pod in $(kubectl get pods -n resilience4j-demo -l app=resilience4j-demo,version=v2 -o name); do
  echo "=== $pod ===" >> all-v2-logs.txt
  kubectl logs $pod -n resilience4j-demo >> all-v2-logs.txt
  echo "" >> all-v2-logs.txt
done

# Export logs with date range (from Azure)
# Use Azure CLI to export from Log Analytics
az monitor log-analytics query \
  --workspace $WORKSPACE_ID \
  --analytics-query "ContainerLog | where Namespace == 'resilience4j-demo' | take 1000" \
  --output table > azure-logs.txt
```

---

## 🔍 Part 4: Distributed Tracing with Istio

If you have Istio installed, tracing is automatic via sidecar proxies.

### 4.1 Verify Istio Sidecar Injection

```bash
# Check if pod has Istio sidecar
kubectl get pods -n resilience4j-demo -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[*].name}{"\n"}{end}'

# Expected output should show "istio-proxy" container:
# resilience4j-demo-v2-xxxxx    app istio-proxy

# Describe pod to see sidecar
kubectl describe pod resilience4j-demo-v2-xxxxx -n resilience4j-demo | grep -A 5 "istio-proxy"
```

### 4.2 Access Istio Tracing (Jaeger/Zipkin)

```bash
# Check if Jaeger is installed
kubectl get svc -n istio-system | grep jaeger

# Port-forward to Jaeger UI
kubectl port-forward -n istio-system svc/tracing 16686:80

# Open browser
# http://localhost:16686

# In Jaeger UI:
# - Service: resilience4j-demo
# - Operation: All
# - Lookback: Last hour
# Click "Find Traces"
```

### 4.3 View Traces via Kiali

```bash
# Port-forward to Kiali
kubectl port-forward -n istio-system svc/kiali 20001:20001

# Open browser
# http://localhost:20001

# Navigate to:
# Graph → Namespace: resilience4j-demo
# - Shows service topology
# - Traffic flow
# - Success/error rates
# - Response times
```

### 4.4 Istio Access Logs

```bash
# Get Istio sidecar logs
kubectl logs deployment/resilience4j-demo-v2 -c istio-proxy -n resilience4j-demo

# Follow sidecar logs
kubectl logs -f deployment/resilience4j-demo-v2 -c istio-proxy -n resilience4j-demo

# Filter for specific requests
kubectl logs deployment/resilience4j-demo-v2 -c istio-proxy -n resilience4j-demo | grep "GET /api"
```

---

## 🎨 Part 5: Azure Monitor Dashboards

### 5.1 Create Workbook for Application Monitoring

Navigate to:
```
Azure Portal → Monitor → Workbooks → New

Add queries:
```

**Query 1: Pod CPU Usage**
```kusto
Perf
| where ObjectName == "K8SContainer"
| where CounterName == "cpuUsageNanoCores"
| where InstanceName contains "resilience4j-demo"
| summarize AvgCPU = avg(CounterValue) / 1000000 by InstanceName, bin(TimeGenerated, 5m)
| render timechart
```

**Query 2: Pod Memory Usage**
```kusto
Perf
| where ObjectName == "K8SContainer"
| where CounterName == "memoryWorkingSetBytes"
| where InstanceName contains "resilience4j-demo"
| summarize AvgMemoryMB = avg(CounterValue) / 1024 / 1024 by InstanceName, bin(TimeGenerated, 5m)
| render timechart
```

**Query 3: Container Restarts**
```kusto
KubePodInventory
| where Namespace == "resilience4j-demo"
| where ContainerRestartCount > 0
| summarize RestartCount = max(ContainerRestartCount) by Name, bin(TimeGenerated, 1h)
| render timechart
```

**Query 4: Error Log Count**
```kusto
ContainerLog
| where Namespace == "resilience4j-demo"
| where LogEntry has "ERROR" or LogEntry has "Exception"
| summarize ErrorCount = count() by bin(TimeGenerated, 5m)
| render timechart
```

### 5.2 Create Alerts

#### CPU Alert
```bash
az monitor metrics alert create \
  --name "Resilience4j-HighCPU" \
  --resource-group $RESOURCE_GROUP \
  --scopes "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER_NAME" \
  --condition "avg Percentage CPU > 80" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --description "Alert when CPU exceeds 80%"
```

#### Memory Alert
```bash
az monitor metrics alert create \
  --name "Resilience4j-HighMemory" \
  --resource-group $RESOURCE_GROUP \
  --scopes "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.ContainerService/managedClusters/$AKS_CLUSTER_NAME" \
  --condition "avg Memory Working Set Percentage > 85" \
  --window-size 5m \
  --evaluation-frequency 1m \
  --description "Alert when memory exceeds 85%"
```

#### Pod Restart Alert (via Log Analytics)
```bash
# Create alert from Kusto query
az monitor scheduled-query create \
  --name "Resilience4j-PodRestart" \
  --resource-group $RESOURCE_GROUP \
  --scopes $WORKSPACE_ID \
  --condition "count > 0" \
  --condition-query "KubePodInventory | where Namespace == 'resilience4j-demo' | where ContainerRestartCount > 0 | summarize count()" \
  --description "Alert when pod restarts" \
  --evaluation-frequency 5m \
  --window-size 10m
```

---

## 📊 Part 6: Health Check Monitoring

### 6.1 Check Application Health

```bash
# Liveness probe
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl -s http://localhost:8070/actuator/health/liveness | jq .

# Readiness probe
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl -s http://localhost:8070/actuator/health/readiness | jq .

# Full health endpoint
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl -s http://localhost:8070/actuator/health | jq .

# Circuit breaker status
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl -s http://localhost:8070/actuator/circuitbreakers | jq .

# Metrics endpoint
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl -s http://localhost:8070/actuator/metrics | jq .
```

### 6.2 Monitor Pod Health Events

```bash
# Get pod events
kubectl get events -n resilience4j-demo --sort-by='.lastTimestamp' | grep resilience4j-demo

# Watch events in real-time
kubectl get events -n resilience4j-demo --watch

# Get events for specific pod
kubectl describe pod resilience4j-demo-v2-xxxxx -n resilience4j-demo | grep -A 10 Events:
```

### 6.3 Query Health Status from Azure

```kusto
// Pod readiness status
KubePodInventory
| where Namespace == "resilience4j-demo"
| where TimeGenerated > ago(1h)
| summarize arg_max(TimeGenerated, *) by Name
| project Name, PodStatus, ContainerStatus, ContainerRestartCount, Node
| order by ContainerRestartCount desc

// Unhealthy pods
KubeEvents
| where Namespace == "resilience4j-demo"
| where Reason in ("Unhealthy", "FailedScheduling", "FailedMount", "BackOff")
| where TimeGenerated > ago(24h)
| project TimeGenerated, Reason, Message, Name
| order by TimeGenerated desc
```

---

## 🔧 Part 7: Performance Analysis

### 7.1 Request Latency Analysis

```bash
# Port-forward and test response time
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# Test multiple requests
for i in {1..10}; do
  time curl -s http://localhost:8070/api/test > /dev/null
done

# Load testing with hey (install: go install github.com/rakyll/hey@latest)
hey -n 1000 -c 10 http://localhost:8070/api/test
```

### 7.2 Resource Utilization Report

```kusto
// Average resource usage per pod
Perf
| where ObjectName == "K8SContainer"
| where InstanceName contains "resilience4j-demo"
| where TimeGenerated > ago(24h)
| extend MetricType = case(
    CounterName == "cpuUsageNanoCores", "CPU",
    CounterName == "memoryWorkingSetBytes", "Memory",
    "Other"
)
| where MetricType in ("CPU", "Memory")
| summarize 
    AvgCPU_millicores = avgif(CounterValue / 1000000, MetricType == "CPU"),
    MaxCPU_millicores = maxif(CounterValue / 1000000, MetricType == "CPU"),
    AvgMemory_MB = avgif(CounterValue / 1024 / 1024, MetricType == "Memory"),
    MaxMemory_MB = maxif(CounterValue / 1024 / 1024, MetricType == "Memory")
  by InstanceName
| order by AvgCPU_millicores desc
```

---

## 📚 Part 8: Useful Scripts

### 8.1 Monitor Script (Bash)

```bash
#!/bin/bash
# monitor-app.sh

NAMESPACE="resilience4j-demo"
DEPLOYMENT="resilience4j-demo-v2"

echo "=== Resilience4j Monitoring Dashboard ==="
echo ""

# Pod status
echo "📦 Pod Status:"
kubectl get pods -n $NAMESPACE -l app=resilience4j-demo,version=v2

echo ""
echo "📊 Resource Usage:"
kubectl top pods -n $NAMESPACE -l app=resilience4j-demo,version=v2

echo ""
echo "🔄 HPA Status:"
kubectl get hpa -n $NAMESPACE

echo ""
echo "📝 Recent Events:"
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -5

echo ""
echo "❤️ Health Check:"
POD=$(kubectl get pods -n $NAMESPACE -l app=resilience4j-demo,version=v2 -o jsonpath='{.items[0].metadata.name}')
kubectl exec $POD -n $NAMESPACE -- curl -s http://localhost:8070/actuator/health | jq '.status'
```

### 8.2 Log Collector Script (PowerShell)

```powershell
# collect-logs.ps1

$namespace = "resilience4j-demo"
$outputDir = "logs-$(Get-Date -Format 'yyyy-MM-dd-HHmmss')"

New-Item -ItemType Directory -Path $outputDir -Force

# Get all pods
$pods = kubectl get pods -n $namespace -l app=resilience4j-demo -o name

foreach ($pod in $pods) {
    $podName = $pod -replace 'pod/', ''
    Write-Host "Collecting logs from $podName..."
    
    # Application container logs
    kubectl logs $pod -n $namespace > "$outputDir\$podName-app.log"
    
    # Istio sidecar logs (if exists)
    kubectl logs $pod -c istio-proxy -n $namespace 2>$null > "$outputDir\$podName-istio.log"
    
    # Previous container logs (if crashed)
    kubectl logs $pod -n $namespace --previous 2>$null > "$outputDir\$podName-previous.log"
}

Write-Host "Logs collected in $outputDir"
```

---

## 🎯 Quick Reference Commands

```bash
# Metrics
kubectl top pods -n resilience4j-demo
kubectl top nodes

# Logs
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo
kubectl logs -l app=resilience4j-demo -n resilience4j-demo --tail=100

# Health
kubectl exec deployment/resilience4j-demo-v2 -n resilience4j-demo -- curl http://localhost:8070/actuator/health

# Events
kubectl get events -n resilience4j-demo --sort-by='.lastTimestamp'

# Describe
kubectl describe pod <pod-name> -n resilience4j-demo

# Port-forward
kubectl port-forward deployment/resilience4j-demo-v2 8070:8070 -n resilience4j-demo

# HPA status
kubectl get hpa -n resilience4j-demo

# Service endpoints
kubectl get endpoints -n resilience4j-demo
```

---

## ✅ Validation Checklist

- [ ] Container Insights enabled on AKS
- [ ] Can view metrics in Azure Monitor
- [ ] Can access pod logs via kubectl
- [ ] Logs appearing in Log Analytics
- [ ] Prometheus metrics accessible at `/actuator/prometheus`
- [ ] Health endpoints responding
- [ ] Istio sidecar injected (if using Istio)
- [ ] Traces visible in Jaeger/Kiali
- [ ] Alerts configured
- [ ] Dashboards created

---

## 📖 Next Steps

Once you validate the existing observability:

1. ✅ Monitor for 24-48 hours to establish baseline
2. ✅ Identify performance bottlenecks
3. ✅ Set appropriate alert thresholds
4. ✅ Create custom dashboards
5. ⏭️ **Then** consider adding Application Insights for deeper insights

This approach lets you observe the current deployment before making any code changes!
