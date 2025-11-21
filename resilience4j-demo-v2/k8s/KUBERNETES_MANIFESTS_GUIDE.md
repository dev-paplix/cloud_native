# Kubernetes Manifests Hierarchy & Guide

## 📁 Manifest Structure

```
k8s/
├── deployment.yaml              # V1 deployment with namespace, service, ingress, HPA
├── deployment-v2.yaml           # V2 deployment with enhanced features
├── hpa.yaml                     # Horizontal Pod Autoscaler for V2
├── istio-resilience.yaml        # Istio resilience patterns (circuit breaker, retries)
└── istio-virtualservice.yaml    # Istio traffic management (canary, blue-green)
```

---

## 🎯 Manifest Descriptions

### 1. **deployment.yaml** - V1 Base Deployment
**Purpose**: Complete V1 application deployment with core Kubernetes resources

**Resources Included:**
- `Namespace`: resilience4j-demo
- `Deployment`: 3 replicas with rolling update strategy
- `Service`: ClusterIP type for internal communication
- `Ingress`: NGINX ingress with TLS/SSL
- `HorizontalPodAutoscaler`: CPU/Memory based scaling

**Key Features:**
- ✅ Graceful shutdown (30s termination grace period)
- ✅ Health probes (liveness, readiness, startup)
- ✅ Resource limits (QoS Guaranteed)
- ✅ Security context (non-root user)
- ✅ Prometheus metrics integration

**Apply Order:** 
```bash
kubectl apply -f deployment.yaml
```

---

### 2. **deployment-v2.yaml** - V2 Enhanced Deployment
**Purpose**: Production-ready V2 with advanced configurations

**Resources Included:**
- `Deployment`: resilience4j-demo-v2
- `Service`: resilience4j-demo-v2 (version-specific)
- `Service`: resilience4j-demo (shared for canary)

**Enhancements over V1:**
- 🔹 Graceful shutdown with preStop hook (15s drain + 30s termination)
- 🔹 Enhanced health probe groups (liveness/readiness)
- 🔹 Production-optimized JVM settings
- 🔹 Environment-specific configuration (REGION, ENVIRONMENT)
- 🔹 Higher resource limits (2Gi memory)
- 🔹 Longer startup probe timeout (300s)

**Apply Order:**
```bash
kubectl apply -f deployment-v2.yaml
```

---

### 3. **hpa.yaml** - Horizontal Pod Autoscaler V2
**Purpose**: Advanced autoscaling with multiple metrics

**Scaling Metrics:**
1. **CPU**: Scale when > 70% utilization
2. **Memory**: Scale when > 80% utilization  
3. **Custom (RPS)**: Scale when > 100 requests/second per pod

**Scaling Behavior:**
- **Scale Up**: Immediate (0s stabilization), max 100% increase or 4 pods
- **Scale Down**: 5-minute stabilization, max 50% decrease or 2 pods

**Replica Range:** 3 (min) → 10 (max)

**Apply Order:**
```bash
kubectl apply -f hpa.yaml
```

---

### 4. **istio-resilience.yaml** - Istio Resilience Patterns
**Purpose**: Service mesh resilience configuration

**DestinationRule - Traffic Policy:**
- **Connection Pool**:
  - TCP: 100 max connections, 30ms timeout
  - HTTP: 50 pending requests, 100 concurrent requests
  - Keep-alive: 2 hours
- **Load Balancer**: LEAST_REQUEST with locality awareness
- **Outlier Detection** (Circuit Breaker):
  - 5 consecutive errors → eject endpoint
  - 30s ejection time
  - Max 50% ejection (min 40% healthy required)

**VirtualService - Request Handling:**
- **Timeout**: 10s overall
- **Retries**: 3 attempts, 3s per try
- **Retry Conditions**: 5xx, reset, connection failures
- **Fault Injection**: Disabled (0% delay/abort)

**Apply Order:**
```bash
kubectl apply -f istio-resilience.yaml
```

---

### 5. **istio-virtualservice.yaml** - Traffic Management
**Purpose**: Canary and Blue-Green deployment strategies

**Three Deployment Strategies:**

#### A. **Canary Deployment** (Default)
```yaml
# Normal traffic: 90% v1, 10% v2
route:
  - v1: 90%
  - v2: 10%

# Canary header traffic: 100% v2
headers:
  canary: "true" → 100% v2
```

#### B. **Progressive Canary**
Gradually increase v2 traffic:
- Phase 1: 90% v1, 10% v2
- Phase 2: 70% v1, 30% v2
- Phase 3: 50% v1, 50% v2
- Phase 4: 20% v1, 80% v2
- Phase 5: 0% v1, 100% v2

#### C. **Blue-Green Deployment**
Instant switch between versions:
```yaml
# Current: 100% v2 (Green)
# Switch to: 100% v1 (Blue)
```

**Apply Order:**
```bash
kubectl apply -f istio-virtualservice.yaml
```

---

## 📊 Application Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Istio Ingress Gateway                    │
│                    (External Traffic Entry)                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Istio VirtualService (Canary)                   │
│              90% v1  ←→  10% v2                             │
└────────┬───────────────────────────────┬────────────────────┘
         │                               │
         ▼                               ▼
┌────────────────────┐         ┌────────────────────┐
│  Service: v1       │         │  Service: v2       │
│  (ClusterIP)       │         │  (ClusterIP)       │
└────────┬───────────┘         └────────┬───────────┘
         │                               │
         ▼                               ▼
┌────────────────────┐         ┌────────────────────┐
│ Deployment: v1     │         │ Deployment: v2     │
│ Replicas: 3        │         │ Replicas: 3        │
│ Port: 8070         │         │ Port: 8070         │
└────────┬───────────┘         └────────┬───────────┘
         │                               │
         ▼                               ▼
┌────────────────────┐         ┌────────────────────┐
│  HPA (v1)          │         │  HPA (v2)          │
│  3-10 replicas     │         │  3-10 replicas     │
│  CPU/Memory        │         │  CPU/Memory/RPS    │
└────────────────────┘         └────────────────────┘
```

---

## 🚀 Deployment Workflow

### **Step 1: Initial Setup (V1)**
```bash
# Create namespace and deploy V1
kubectl apply -f deployment.yaml

# Verify deployment
kubectl get all -n resilience4j-demo
kubectl get pods -n resilience4j-demo -w
```

### **Step 2: Deploy V2 (Canary)**
```bash
# Deploy V2 alongside V1
kubectl apply -f deployment-v2.yaml

# Apply HPA for V2
kubectl apply -f hpa.yaml

# Apply Istio traffic management
kubectl apply -f istio-virtualservice.yaml
kubectl apply -f istio-resilience.yaml

# Verify canary split
kubectl get virtualservice -n resilience4j-demo
```

### **Step 3: Monitor Canary**
```bash
# Watch traffic distribution
istioctl dashboard kiali

# Check metrics
kubectl top pods -n resilience4j-demo

# View logs
kubectl logs -f deployment/resilience4j-demo-v2 -n resilience4j-demo
```

### **Step 4: Progressive Rollout**
```bash
# Edit VirtualService to increase v2 traffic
kubectl edit virtualservice resilience4j-demo -n resilience4j-demo

# Change weights:
# v1: 70%, v2: 30%
# Then: v1: 50%, v2: 50%
# Finally: v1: 0%, v2: 100%
```

### **Step 5: Blue-Green Switch**
```bash
# Apply blue-green configuration
kubectl apply -f istio-virtualservice.yaml

# Edit to switch versions
kubectl patch virtualservice resilience4j-demo-bluegreen -n resilience4j-demo \
  --type merge -p '{"spec":{"http":[{"route":[{"destination":{"subset":"v1"},"weight":100}]}]}}'
```

---

## 🔍 Validation & Testing

### **Health Check**
```bash
# Check liveness
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl http://localhost:8070/actuator/health/liveness

# Check readiness
kubectl exec -it deployment/resilience4j-demo-v2 -n resilience4j-demo -- \
  curl http://localhost:8070/actuator/health/readiness
```

### **Traffic Testing**
```bash
# Test canary routing
for i in {1..100}; do
  curl -H "Host: resilience4j-demo" http://INGRESS_IP/api/test
done

# Test with canary header (100% v2)
curl -H "canary: true" -H "Host: resilience4j-demo" http://INGRESS_IP/api/test
```

### **Autoscaling Test**
```bash
# Generate load
kubectl run -it --rm load-generator --image=busybox --restart=Never -- \
  /bin/sh -c "while sleep 0.01; do wget -q -O- http://resilience4j-demo/api/test; done"

# Watch HPA
kubectl get hpa -n resilience4j-demo -w
```

---

## 🎯 Key Configuration Parameters

### **Port Configuration**
- Application Port: **8070**
- Service Port: **80** → Target: **8070**
- Prometheus Scrape: **8070/actuator/prometheus**

### **Health Probes**
- **Liveness**: `/actuator/health/liveness` on port 8070
- **Readiness**: `/actuator/health/readiness` on port 8070
- **Startup**: 30 attempts × 10s = 300s max startup time

### **Resource Limits**
- **V1**: 512Mi-1Gi memory, 500m-1000m CPU
- **V2**: 1Gi-2Gi memory, 500m-1000m CPU

### **Graceful Shutdown**
- PreStop Hook: 15s sleep
- Termination Grace Period: 30s
- Total shutdown time: ~45s

---

## 📋 Troubleshooting Guide

### **Issue: Pods not ready**
```bash
# Check pod events
kubectl describe pod <pod-name> -n resilience4j-demo

# Check logs
kubectl logs <pod-name> -n resilience4j-demo --previous

# Check readiness probe
kubectl get pod <pod-name> -n resilience4j-demo -o jsonpath='{.status.conditions}'
```

### **Issue: Traffic not routing to v2**
```bash
# Verify VirtualService
kubectl get virtualservice resilience4j-demo -n resilience4j-demo -o yaml

# Check DestinationRule subsets
kubectl get destinationrule resilience4j-demo -n resilience4j-demo -o yaml

# Verify pod labels
kubectl get pods -n resilience4j-demo --show-labels
```

### **Issue: HPA not scaling**
```bash
# Check metrics server
kubectl top nodes
kubectl top pods -n resilience4j-demo

# Verify HPA status
kubectl describe hpa resilience4j-demo-v2-hpa -n resilience4j-demo

# Check metrics
kubectl get --raw /apis/metrics.k8s.io/v1beta1/namespaces/resilience4j-demo/pods
```

---

## 🔐 Security Best Practices

✅ **Non-root containers**: User 1000 (spring)  
✅ **Resource limits**: QoS Guaranteed class  
✅ **TLS/SSL**: Enabled via Ingress with cert-manager  
✅ **Network policies**: Restrict pod-to-pod communication  
✅ **Secrets management**: Use Kubernetes Secrets for sensitive data  
✅ **RBAC**: Least privilege service accounts  

---

## 📚 Related Documentation

- [HOW_TO_GET_APPLICATION_INSIGHTS_CREDENTIALS.md](../HOW_TO_GET_APPLICATION_INSIGHTS_CREDENTIALS.md)
- [ACR_DEPLOYMENT_GUIDE.md](../ACR_DEPLOYMENT_GUIDE.md)
- [DEPLOYMENT_CHECKLIST.md](../DEPLOYMENT_CHECKLIST.md)
- [V2_COMPLETION_SUMMARY.md](../V2_COMPLETION_SUMMARY.md)
