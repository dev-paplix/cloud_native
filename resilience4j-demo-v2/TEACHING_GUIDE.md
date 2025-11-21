# Cloud Native Java - Advanced Production Patterns Teaching Guide

This guide covers 8 essential production patterns for cloud-native applications using resilience4j-demo-v2.

## Table of Contents
1. [Apply Resiliency Patterns in Istio](#1-apply-resiliency-patterns-in-istio)
2. [Graceful Shutdown + Liveness + Readiness Probes](#2-graceful-shutdown--liveness--readiness-probes)
3. [Rate-Limiting + Caching at APIM](#3-rate-limiting--caching-at-apim)
4. [Event-Driven Async Flow with Azure Event Hub](#4-event-driven-async-flow-with-azure-event-hub)
5. [Cluster Autoscaler](#5-cluster-autoscaler)
6. [Blue-Green Deployment](#6-blue-green-deployment)
7. [Canary Release](#7-canary-release)
8. [Right-Sizing, QoS](#8-right-sizing-qos)

---

## 1. Apply Resiliency Patterns in Istio

### Theory
Istio provides service mesh capabilities for traffic management, security, and observability. Resilience patterns at the mesh level complement application-level patterns.

### Implementation

**File:** `k8s/istio-resilience.yaml`

```yaml
# Circuit Breaker at Istio Level
outlierDetection:
  consecutive5xxErrors: 5
  interval: 30s
  baseEjectionTime: 30s
  maxEjectionPercent: 50
```

**Key Patterns:**
- **Circuit Breaking:** Eject unhealthy pods after 5 consecutive 5xx errors
- **Connection Pooling:** Limit max connections (100 TCP, 50 HTTP pending)
- **Retries:** Automatic retry on 5xx, gateway errors (3 attempts, 3s timeout)
- **Fault Injection:** Test resilience by injecting delays/errors

### Hands-On Exercise

```bash
# Apply Istio configurations
kubectl apply -f k8s/istio-resilience.yaml

# Test circuit breaker
for i in {1..10}; do
  curl http://$INGRESS_HOST/api/circuit-breaker/fail
done

# Verify outlier detection
kubectl logs -l app=istio-ingressgateway -n istio-system | grep outlier

# Inject fault (edit istio-resilience.yaml)
# fault:
#   delay:
#     percentage:
#       value: 50
#     fixedDelay: 5s

kubectl apply -f k8s/istio-resilience.yaml
curl -w "@curl-format.txt" http://$INGRESS_HOST/api/circuit-breaker/demo
```

### Teaching Points
- **Layered Resilience:** Application (Resilience4j) + Service Mesh (Istio)
- **Fast Fail:** Circuit breaker prevents cascade failures
- **Load Balancing:** LEAST_REQUEST ensures even distribution
- **Chaos Engineering:** Fault injection tests resilience

---

## 2. Graceful Shutdown + Liveness + Readiness Probes

### Theory
Graceful shutdown ensures zero-downtime deployments by:
1. Stop accepting new requests
2. Complete in-flight requests
3. Close resources cleanly
4. Kubernetes waits for grace period before killing pod

### Implementation

**File:** `application.yml`

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState,ping
        readiness:
          include: readinessState,diskSpace,circuitBreakers
```

**File:** `k8s/deployment-v2.yaml`

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 30
  periodSeconds: 5

lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 15"]

terminationGracePeriodSeconds: 30
```

### Hands-On Exercise

```bash
# Deploy v2
kubectl apply -f k8s/deployment-v2.yaml

# Watch pod lifecycle during rolling update
kubectl get pods -w

# Update image to trigger rollout
kubectl set image deployment/resilience4j-demo-v2 app=myacr.azurecr.io/resilience4j-demo:v2-updated

# Generate traffic during rollout
while true; do curl http://$INGRESS_HOST/api/circuit-breaker/demo; sleep 1; done

# Check zero dropped requests
# No 502/503 errors should occur

# Test health endpoints
kubectl port-forward svc/resilience4j-demo-v2 8081:8081
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness
```

### Teaching Points
- **preStop Hook:** 15s sleep allows load balancer to de-register pod
- **Grace Period:** 30s total timeout for graceful shutdown
- **Liveness vs Readiness:**
  - Liveness: Pod is alive (restart if fails)
  - Readiness: Pod can serve traffic (remove from load balancer if fails)
- **Startup Probe:** 5min max startup time (30 failures × 10s)

---

## 3. Rate-Limiting + Caching at APIM

### Theory
Azure API Management provides centralized rate-limiting and caching to protect backend services and reduce latency.

### Implementation

**File:** `apim-policy.xml` (create this)

```xml
<policies>
    <inbound>
        <!-- Rate limiting: 100 calls per 60 seconds per subscription -->
        <rate-limit calls="100" renewal-period="60" />
        
        <!-- Rate limiting by client IP -->
        <rate-limit-by-key calls="10" renewal-period="60" 
                           counter-key="@(context.Request.IpAddress)" />
        
        <!-- Caching for GET requests -->
        <cache-lookup vary-by-developer="false" 
                      vary-by-developer-groups="false">
            <vary-by-query-parameter>version</vary-by-query-parameter>
        </cache-lookup>
    </inbound>
    
    <backend>
        <forward-request timeout="10" />
    </backend>
    
    <outbound>
        <!-- Cache for 300 seconds -->
        <cache-store duration="300" />
    </outbound>
    
    <on-error>
        <base />
    </on-error>
</policies>
```

### Hands-On Exercise

```bash
# Create APIM instance
az apim create \
  --resource-group rg-resilience-demo \
  --name apim-resilience \
  --publisher-email admin@example.com \
  --publisher-name "Cloud Native Demo" \
  --sku-name Developer

# Import API from AKS
az apim api import \
  --resource-group rg-resilience-demo \
  --service-name apim-resilience \
  --path /api \
  --specification-url http://<aks-ingress-ip>/v3/api-docs

# Apply rate-limiting policy
az apim api policy create \
  --resource-group rg-resilience-demo \
  --service-name apim-resilience \
  --api-id resilience4j-demo \
  --xml-content @apim-policy.xml

# Test rate limiting
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "https://apim-resilience.azure-api.net/api/circuit-breaker/demo"
done
# Should see: 200 (10x) then 429 (5x)

# Test caching
curl -w "@curl-format.txt" \
  "https://apim-resilience.azure-api.net/api/circuit-breaker/demo"
# First request: slow
# Subsequent requests within 5min: fast
```

### Teaching Points
- **Defense in Depth:** APIM protects backend from abuse
- **Cost Optimization:** Caching reduces backend calls
- **Subscription Keys:** Control access and monitor usage
- **Throttling Headers:** `RateLimit-Remaining`, `RateLimit-Reset`

---

## 4. Event-Driven Async Flow with Azure Event Hub

### Theory
Event-driven architecture decouples services using async messaging. Event Hub provides Kafka-compatible streaming at scale.

### Implementation

**File:** `pom.xml` (add dependencies)

```xml
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-stream-binder-eventhubs</artifactId>
</dependency>
```

**File:** `application.yml`

```yaml
spring:
  cloud:
    stream:
      bindings:
        resilience-out-0:
          destination: resilience-events
          content-type: application/json
        resilience-in-0:
          destination: resilience-events
          group: resilience-consumer-group
      eventhubs:
        binder:
          namespace: ${EVENTHUB_NAMESPACE}
          connection-string: ${EVENTHUB_CONNECTION_STRING}
```

**File:** `EventProducer.java`

```java
@Service
public class EventProducer {
    
    @Autowired
    private StreamBridge streamBridge;
    
    public void publishCircuitBreakerEvent(String event) {
        CircuitBreakerEvent cbevent = new CircuitBreakerEvent(
            event, 
            LocalDateTime.now(), 
            "v2"
        );
        streamBridge.send("resilience-out-0", cbevent);
    }
}
```

**File:** `EventConsumer.java`

```java
@Service
public class EventConsumer {
    
    @Bean
    public Consumer<CircuitBreakerEvent> resilienceIn() {
        return event -> {
            logger.info("Received event: {}", event);
            // Process event, update metrics, trigger workflows
        };
    }
}
```

### Hands-On Exercise

```bash
# Reference existing eventhub-producer and eventhub-consumer
cd ../day3/code/eventhub-producer
mvn spring-boot:run

cd ../eventhub-consumer
mvn spring-boot:run

# Publish event
curl -X POST http://localhost:8080/publish \
  -H "Content-Type: application/json" \
  -d '{"message":"Circuit breaker opened","severity":"WARN"}'

# View consumer logs
# Should see: "Received event: CircuitBreakerEvent{...}"

# Scale consumers
kubectl scale deployment eventhub-consumer --replicas=5

# View Event Hub metrics
az monitor metrics list \
  --resource /subscriptions/<sub-id>/resourceGroups/rg-resilience-demo/providers/Microsoft.EventHub/namespaces/<namespace> \
  --metric IncomingMessages
```

### Teaching Points
- **Async Processing:** Non-blocking, high throughput
- **Consumer Groups:** Multiple independent consumers
- **Checkpointing:** Resume from last processed offset
- **Partitioning:** Parallel processing across partitions

---

## 5. Cluster Autoscaler

### Theory
Horizontal Pod Autoscaler (HPA) scales pods based on metrics. Cluster Autoscaler scales nodes when pods are pending due to insufficient resources.

### Implementation

**File:** `k8s/hpa.yaml`

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: resilience4j-demo-v2-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resilience4j-demo-v2
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 2
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
      - type: Pods
        value: 4
        periodSeconds: 15
```

### Hands-On Exercise

```bash
# Apply HPA
kubectl apply -f k8s/hpa.yaml

# Generate load
kubectl run load-generator --image=busybox --restart=Never -- /bin/sh -c \
  "while true; do wget -q -O- http://resilience4j-demo-v2:8080/api/circuit-breaker/demo; done"

# Watch HPA scale up
kubectl get hpa resilience4j-demo-v2-hpa -w

# Check metrics
kubectl top pods -l app=resilience4j-demo

# Verify scaling events
kubectl describe hpa resilience4j-demo-v2-hpa

# Stop load
kubectl delete pod load-generator

# Watch HPA scale down (5min stabilization window)
kubectl get hpa resilience4j-demo-v2-hpa -w
```

### Teaching Points
- **Scale-Up:** Immediate (0s stabilization), aggressive (100%/4 pods per 15s)
- **Scale-Down:** Conservative (5min stabilization), slow (50%/2 pods per 60s)
- **Custom Metrics:** RPS from Prometheus adapter
- **Cluster Autoscaler:** Automatically adds nodes when pods pending

---

## 6. Blue-Green Deployment

### Theory
Blue-Green deployment runs two identical production environments. Traffic is switched from Blue (old) to Green (new) instantly, allowing instant rollback.

### Implementation

**File:** `k8s/istio-virtualservice.yaml` (blue-green variant)

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-demo-blue-green
spec:
  hosts:
  - resilience4j-demo
  http:
  - route:
    - destination:
        host: resilience4j-demo
        subset: blue
      weight: 100
    - destination:
        host: resilience4j-demo
        subset: green
      weight: 0
```

### Hands-On Exercise

```bash
# Deploy Blue (v1)
kubectl apply -f ../resilience4j-demo/k8s/deployment.yaml
kubectl label deployment resilience4j-demo version=blue

# Deploy Green (v2)
kubectl apply -f k8s/deployment-v2.yaml
kubectl label deployment resilience4j-demo-v2 version=green

# Apply Blue-Green VirtualService (100% Blue)
kubectl apply -f k8s/istio-virtualservice-bluegreen.yaml

# Test Blue version
curl http://$INGRESS_HOST/api/circuit-breaker/demo
# Should return v1

# Switch to Green (update VirtualService)
kubectl patch virtualservice resilience4j-demo-blue-green --type merge -p '
{
  "spec": {
    "http": [{
      "route": [
        {"destination": {"host": "resilience4j-demo", "subset": "blue"}, "weight": 0},
        {"destination": {"host": "resilience4j-demo", "subset": "green"}, "weight": 100}
      ]
    }]
  }
}'

# Test Green version
curl http://$INGRESS_HOST/api/circuit-breaker/demo
# Should return v2

# Instant Rollback to Blue
kubectl patch virtualservice resilience4j-demo-blue-green --type merge -p '
{
  "spec": {
    "http": [{
      "route": [
        {"destination": {"host": "resilience4j-demo", "subset": "blue"}, "weight": 100},
        {"destination": {"host": "resilience4j-demo", "subset": "green"}, "weight": 0}
      ]
    }]
  }
}'
```

### Teaching Points
- **Zero Downtime:** Instant traffic switch
- **Easy Rollback:** Change weight back to Blue
- **Cost:** Double resources during transition
- **Testing:** Green environment validated before switch

---

## 7. Canary Release

### Theory
Canary release gradually shifts traffic from old to new version, monitoring metrics at each step. Rollback if issues detected.

### Implementation

**File:** `k8s/istio-virtualservice.yaml`

```yaml
http:
- match:
  - headers:
      canary:
        exact: "true"
  route:
  - destination:
      host: resilience4j-demo
      subset: v2
- route:
  - destination:
      host: resilience4j-demo
      subset: v1
    weight: 90
  - destination:
      host: resilience4j-demo
      subset: v2
    weight: 10
```

### Hands-On Exercise

```bash
# Apply Canary VirtualService (90% v1, 10% v2)
kubectl apply -f k8s/istio-virtualservice.yaml

# Test traffic distribution
for i in {1..100}; do
  curl -s http://$INGRESS_HOST/api/circuit-breaker/demo | jq -r '.version'
done | sort | uniq -c
# Should see ~90 v1, ~10 v2

# Test canary header (100% v2)
curl -H "canary: true" http://$INGRESS_HOST/api/circuit-breaker/demo
# Should always return v2

# Monitor canary metrics
kubectl port-forward svc/resilience4j-demo-v2 8081:8081
curl http://localhost:8081/actuator/prometheus | grep http_server_requests

# Increase canary to 50%
kubectl patch virtualservice resilience4j-demo --type merge -p '
{
  "spec": {
    "http": [{
      "match": [{"headers": {"canary": {"exact": "true"}}}],
      "route": [{"destination": {"host": "resilience4j-demo", "subset": "v2"}}]
    }, {
      "route": [
        {"destination": {"host": "resilience4j-demo", "subset": "v1"}, "weight": 50},
        {"destination": {"host": "resilience4j-demo", "subset": "v2"}, "weight": 50}
      ]
    }]
  }
}'

# Monitor for 30min, if no errors:
# Increase to 100%
kubectl patch virtualservice resilience4j-demo --type merge -p '
{
  "spec": {
    "http": [{
      "route": [{"destination": {"host": "resilience4j-demo", "subset": "v2"}, "weight": 100}]
    }]
  }
}'
```

### Teaching Points
- **Gradual Rollout:** 10% → 50% → 100%
- **Header-Based Routing:** Internal testing before public canary
- **Metrics Monitoring:** Error rate, latency, throughput
- **Automated Canary:** Flagger (progressive delivery operator)

---

## 8. Right-Sizing, QoS

### Theory
Quality of Service (QoS) classes in Kubernetes determine pod scheduling priority and eviction order. Right-sizing ensures efficient resource utilization.

### Implementation

**File:** `k8s/deployment-v2.yaml`

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

**QoS Classes:**
- **Guaranteed:** requests = limits (highest priority)
- **Burstable:** requests < limits (medium priority)
- **BestEffort:** no requests/limits (lowest priority)

### Hands-On Exercise

```bash
# Deploy with different QoS classes

# Guaranteed (v2)
kubectl apply -f k8s/deployment-v2.yaml

# Burstable (v1)
kubectl apply -f ../resilience4j-demo/k8s/deployment.yaml

# Check QoS class
kubectl get pod <pod-name> -o jsonpath='{.status.qosClass}'

# Simulate memory pressure
kubectl run memory-hog --image=polinux/stress --restart=Never -- stress --vm 1 --vm-bytes 4G

# Verify eviction priority
kubectl get events --field-selector reason=Evicted
# BestEffort evicted first, then Burstable, Guaranteed last

# Right-sizing with VPA (Vertical Pod Autoscaler)
kubectl apply -f https://github.com/kubernetes/autoscaler/releases/download/vertical-pod-autoscaler-0.13.0/vpa-v0.13.0.yaml

cat <<EOF | kubectl apply -f -
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: resilience4j-demo-v2-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resilience4j-demo-v2
  updatePolicy:
    updateMode: "Recreate"
EOF

# View recommendations
kubectl describe vpa resilience4j-demo-v2-vpa
```

### Teaching Points
- **Guaranteed QoS:** Critical production workloads
- **Resource Efficiency:** requests based on actual usage (P95)
- **limits vs requests:** 
  - requests: scheduling, billing
  - limits: enforcement, OOMKill
- **JVM Tuning:** `-Xms512m -Xmx1024m` in JAVA_OPTS

---

## Summary

| Pattern | Purpose | Tool | Impact |
|---------|---------|------|--------|
| Istio Resilience | Service mesh circuit breaking | Istio DestinationRule | Prevent cascade failures |
| Graceful Shutdown | Zero-downtime deployments | Spring Boot + K8s probes | No dropped requests |
| APIM Rate-Limiting | Protect backend from abuse | Azure API Management | Cost control, DDoS protection |
| Event Hub | Async event-driven flow | Azure Event Hub + Spring Cloud Stream | Decouple services, high throughput |
| Cluster Autoscaler | Scale pods + nodes | HPA + Cluster Autoscaler | Handle traffic spikes |
| Blue-Green | Instant traffic switch | Istio VirtualService | Easy rollback, reduced risk |
| Canary | Gradual rollout with monitoring | Istio VirtualService | Early issue detection |
| QoS Right-Sizing | Resource efficiency + priority | K8s QoS + VPA | Cost optimization, reliability |

## References
- [Istio Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)
- [Spring Boot Graceful Shutdown](https://spring.io/blog/2020/03/27/spring-boot-2-3-0-available-now#graceful-shutdown)
- [Kubernetes HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- [Azure Event Hubs](https://docs.microsoft.com/en-us/azure/event-hubs/)
- [Azure APIM Policies](https://docs.microsoft.com/en-us/azure/api-management/api-management-policies)
