# Lecture: Istio Service Mesh & Gateway-Level Rate Limiting

## Duration: 60 minutes

## Learning Objectives
- Understand service mesh architecture and benefits
- Learn Istio core components and features
- Implement gateway-level rate limiting with Istio
- Compare app-level vs infrastructure-level rate limiting
- Configure traffic management and observability with Istio
- Integrate Istio with Azure APIM

---

## 1. Introduction to Service Mesh (10 min)

### What is a Service Mesh?

A **service mesh** is a dedicated infrastructure layer for handling service-to-service communication in microservices architectures. It provides:
- **Traffic management**: Routing, load balancing, retries
- **Security**: mTLS, authentication, authorization
- **Observability**: Metrics, logs, traces
- **Policy enforcement**: Rate limiting, quotas, access control

### The Microservices Communication Problem

```
Without Service Mesh:

┌──────────┐     ┌──────────┐     ┌──────────┐
│Service A │────▶│Service B │────▶│Service C │
└──────────┘     └──────────┘     └──────────┘

Each service needs:
❌ Circuit breaker code
❌ Retry logic
❌ Timeout handling
❌ Metrics collection
❌ Tracing instrumentation
❌ mTLS implementation
❌ Rate limiting
```

```
With Service Mesh (Istio):

┌──────────┐     ┌──────────┐     ┌──────────┐
│Service A │────▶│Service B │────▶│Service C │
│          │     │          │     │          │
│ [Proxy] │     │ [Proxy] │     │ [Proxy] │
└──────────┘     └──────────┘     └──────────┘
      │               │               │
      └───────────────┴───────────────┘
              Control Plane (Istiod)

✅ All features in sidecar proxy (no code changes)
✅ Centralized configuration
✅ Consistent policies
```

### Benefits of Service Mesh

1. **Separation of Concerns**: Business logic separate from networking
2. **Language Agnostic**: Works with any programming language
3. **Centralized Control**: Manage all services from one place
4. **Zero Code Changes**: Inject proxies automatically
5. **Observability**: Automatic metrics and traces
6. **Security**: mTLS without application code

---

## 2. Istio Architecture (15 min)

### Core Components

```
┌─────────────────────────────────────────────────┐
│              Istio Control Plane                │
│                  (Istiod)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │  Pilot   │  │ Citadel  │  │ Galley   │    │
│  │(Traffic) │  │(Security)│  │(Config)  │    │
│  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────┬───────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│   Pod A      │ │   Pod B      │ │   Pod C      │
│ ┌─────────┐  │ │ ┌─────────┐  │ │ ┌─────────┐  │
│ │  App    │  │ │ │  App    │  │ │ │  App    │  │
│ └─────────┘  │ │ └─────────┘  │ │ └─────────┘  │
│ ┌─────────┐  │ │ ┌─────────┐  │ │ ┌─────────┐  │
│ │ Envoy   │  │ │ │ Envoy   │  │ │ │ Envoy   │  │
│ │ Proxy   │  │ │ │ Proxy   │  │ │ │ Proxy   │  │
│ └─────────┘  │ │ └─────────┘  │ │ └─────────┘  │
└──────────────┘ └──────────────┘ └──────────────┘
   Data Plane      Data Plane      Data Plane
```

#### 1. **Data Plane** (Envoy Proxy)
- Deployed as sidecar container in each pod
- Intercepts all network traffic
- Enforces policies
- Collects telemetry
- Based on **Envoy** proxy

#### 2. **Control Plane** (Istiod)
- **Pilot**: Traffic management and service discovery
- **Citadel**: Certificate management and mTLS
- **Galley**: Configuration validation and distribution

### How Istio Works

1. **Sidecar Injection**: Istio injects Envoy proxy into every pod
2. **Traffic Interception**: iptables rules redirect traffic to proxy
3. **Policy Enforcement**: Proxy applies rules from control plane
4. **Telemetry Collection**: Proxy sends metrics to control plane
5. **Service Discovery**: Control plane provides service endpoints

### Sidecar Injection

**Automatic (Recommended):**
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: production
  labels:
    istio-injection: enabled  # Auto-inject sidecars
```

**Manual:**
```bash
istioctl kube-inject -f deployment.yaml | kubectl apply -f -
```

---

## 3. Gateway-Level Rate Limiting (15 min)

### App-Level vs Gateway-Level Rate Limiting

| Aspect | App-Level (Resilience4j) | Gateway-Level (Istio) |
|--------|---------------------------|------------------------|
| **Location** | Inside application code | Istio Ingress Gateway |
| **Scope** | Per instance | Global (all instances) |
| **Configuration** | application.yml | Istio CRDs |
| **Language** | Java-specific | Language-agnostic |
| **Overhead** | Application memory | Infrastructure |
| **Best For** | Business logic limits | API protection |

### Why Gateway-Level Rate Limiting?

1. **Protect Infrastructure**: Block traffic before it reaches apps
2. **Consistent Policies**: Same rules for all services
3. **DDoS Protection**: Handle malicious traffic early
4. **Cost Savings**: Reduce unnecessary pod scaling
5. **Multi-tenancy**: Different limits per client/API key

### Istio Rate Limiting Architecture

```
┌─────────────────────────────────────────────┐
│  Client Request                             │
└───────────────┬─────────────────────────────┘
                ▼
┌─────────────────────────────────────────────┐
│  Istio Ingress Gateway                      │
│  ┌────────────────────────────────────────┐ │
│  │  Envoy Filter: Rate Limit              │ │
│  │  - Check quota with rate limit service │ │
│  │  - Return 429 if exceeded              │ │
│  └────────────────────────────────────────┘ │
└───────────────┬─────────────────────────────┘
                ▼
┌─────────────────────────────────────────────┐
│  Rate Limit Service (Redis-backed)          │
│  - Track request counts                     │
│  - Apply configured limits                  │
└───────────────┬─────────────────────────────┘
                ▼
        Allow / Deny Decision
```

### EnvoyFilter for Rate Limiting

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: filter-ratelimit
  namespace: istio-system
spec:
  workloadSelector:
    labels:
      istio: ingressgateway
  configPatches:
  # HTTP filter for rate limiting
  - applyTo: HTTP_FILTER
    match:
      context: GATEWAY
      listener:
        filterChain:
          filter:
            name: "envoy.filters.network.http_connection_manager"
            subFilter:
              name: "envoy.filters.http.router"
    patch:
      operation: INSERT_BEFORE
      value:
        name: envoy.filters.http.ratelimit
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.http.ratelimit.v3.RateLimit
          domain: resilience4j-ratelimit
          failure_mode_deny: true
          rate_limit_service:
            grpc_service:
              envoy_grpc:
                cluster_name: rate_limit_cluster
            transport_api_version: V3
  
  # Rate limit cluster configuration
  - applyTo: CLUSTER
    match:
      context: GATEWAY
    patch:
      operation: ADD
      value:
        name: rate_limit_cluster
        type: STRICT_DNS
        connect_timeout: 1s
        lb_policy: ROUND_ROBIN
        http2_protocol_options: {}
        load_assignment:
          cluster_name: rate_limit_cluster
          endpoints:
          - lb_endpoints:
            - endpoint:
                address:
                  socket_address:
                    address: redis-ratelimit.istio-system.svc.cluster.local
                    port_value: 8081
```

### Rate Limit Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ratelimit-config
  namespace: istio-system
data:
  config.yaml: |
    domain: resilience4j-ratelimit
    descriptors:
      # Global rate limit: 1000 requests per minute
      - key: header_match
        value: "api"
        rate_limit:
          unit: minute
          requests_per_unit: 1000
      
      # Per-client rate limit (based on API key)
      - key: generic_key
        value: "api_key"
        descriptors:
          - key: header_match
            value: "premium"
            rate_limit:
              unit: minute
              requests_per_unit: 10000
          - key: header_match
            value: "standard"
            rate_limit:
              unit: minute
              requests_per_unit: 1000
          - key: header_match
            value: "free"
            rate_limit:
              unit: minute
              requests_per_unit: 100
```

### VirtualService with Rate Limiting

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-demo
  namespace: production
spec:
  hosts:
  - "api.example.com"
  gateways:
  - istio-gateway
  http:
  - match:
    - uri:
        prefix: "/api"
    route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
    # Rate limiting actions
    headers:
      request:
        add:
          x-ratelimit-descriptor: "api"
```

---

## 4. Istio vs Azure APIM (10 min)

### Comparison Matrix

| Feature | Istio | Azure APIM |
|---------|-------|------------|
| **Deployment** | Kubernetes only | Standalone / K8s |
| **Cost** | Free (OSS) | Pay-as-you-go |
| **Rate Limiting** | Global/Per-route | Per-subscription |
| **Protocol** | HTTP, gRPC, TCP | HTTP, WebSocket, GraphQL |
| **Developer Portal** | No | Yes |
| **API Management** | Basic | Advanced |
| **Service Mesh** | Yes | No |
| **mTLS** | Built-in | Requires setup |
| **Observability** | Deep (Kiali, Jaeger) | Azure Monitor |
| **Best For** | East-west traffic | North-south traffic |

### When to Use What?

#### Use Istio for:
✅ **Service-to-service** communication (east-west)  
✅ **Kubernetes-native** environments  
✅ **Advanced traffic management** (canary, A/B testing)  
✅ **Zero-trust security** with mTLS  
✅ **Fine-grained observability**  
✅ **Multi-cluster** deployments  

#### Use Azure APIM for:
✅ **External API** exposure (north-south)  
✅ **Developer portal** for API consumers  
✅ **API versioning** and lifecycle management  
✅ **Monetization** and usage plans  
✅ **OAuth/Azure AD** integration  
✅ **Transformation** and validation  

#### Use Both Together:
```
Internet → Azure APIM → Istio Ingress → Services
           (External)   (Internal mesh)
```

### Integration Pattern (Azure Architecture)

```yaml
# Azure-native architecture with APIM and Istio on AKS

┌──────────────────────────────────────────────┐
│   Internet / Azure Front Door                │
└──────────────┬───────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│  Azure API Management (APIM)                 │
│  - OAuth 2.0 / Azure AD                      │
│  - Subscription keys                         │
│  - Rate limiting (north-south)               │
│  - Request/Response transformation           │
│  - Azure Monitor integration                 │
└──────────────┬───────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│  Azure Kubernetes Service (AKS)              │
│  ┌────────────────────────────────────────┐  │
│  │  Istio Ingress Gateway                 │  │
│  │  - Azure Load Balancer integration     │  │
│  │  - mTLS (east-west)                    │  │
│  │  - Rate limiting (internal services)   │  │
│  │  - Traffic routing                     │  │
│  └────────────┬───────────────────────────┘  │
│               │                               │
│       ┌───────┴────────┐                     │
│       ▼                ▼                     │
│  ┌─────────┐      ┌─────────┐               │
│  │Service A│      │Service B│               │
│  │+ Envoy  │      │+ Envoy  │               │
│  └─────────┘      └─────────┘               │
└──────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│  Azure Services                              │
│  - Azure Monitor (metrics/logs/traces)       │
│  - Application Insights                      │
│  - Azure Key Vault (secrets/certs)           │
│  - Azure Container Registry (ACR)            │
│  - Azure Redis Cache (rate limiting)         │
└──────────────────────────────────────────────┘
```

---

## 5. Traffic Management with Istio (10 min)

### Canary Deployments

Deploy new version to small percentage of users:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-canary
spec:
  hosts:
  - resilience4j-demo
  http:
  - match:
    - headers:
        user-type:
          exact: beta-tester
    route:
    - destination:
        host: resilience4j-demo
        subset: v2
        port:
          number: 80
  - route:
    - destination:
        host: resilience4j-demo
        subset: v1
        port:
          number: 80
      weight: 90
    - destination:
        host: resilience4j-demo
        subset: v2
        port:
          number: 80
      weight: 10  # 10% traffic to v2
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: resilience4j-demo
spec:
  host: resilience4j-demo
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
```

### Circuit Breaking

```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: resilience4j-circuit-breaker
spec:
  host: payment-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        http2MaxRequests: 100
        maxRequestsPerConnection: 2
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
      minHealthPercent: 40
```

### Retries and Timeouts

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-retry
spec:
  hosts:
  - order-service
  http:
  - route:
    - destination:
        host: order-service
    retries:
      attempts: 3
      perTryTimeout: 2s
      retryOn: 5xx,reset,connect-failure
    timeout: 10s
```

### Fault Injection (Testing)

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: fault-injection
spec:
  hosts:
  - payment-service
  http:
  - fault:
      delay:
        percentage:
          value: 10  # 10% of requests
        fixedDelay: 5s  # Delayed by 5 seconds
      abort:
        percentage:
          value: 5  # 5% of requests
        httpStatus: 500  # Return 500 error
    route:
    - destination:
        host: payment-service
```

---

## 6. Observability with Istio (5 min)

### Built-in Integrations

1. **Kiali** - Service mesh visualization
2. **Jaeger** - Distributed tracing
3. **Prometheus** - Metrics collection
4. **Grafana** - Metrics visualization

### Automatic Metrics

Istio automatically collects:
- Request rate
- Request duration
- Request size
- Response codes
- TCP connections

### Access Kiali Dashboard

```bash
# Port forward Kiali
kubectl port-forward -n istio-system svc/kiali 20001:20001

# Open browser
http://localhost:20001
```

### Distributed Tracing

Istio automatically propagates trace headers:
- `x-request-id`
- `x-b3-traceid`
- `x-b3-spanid`
- `x-b3-parentspanid`

Application only needs to forward these headers!

---

## Summary

### Key Takeaways

1. **Service Mesh** provides infrastructure-level features without code changes
2. **Istio** excels at service-to-service communication and security
3. **Gateway-level rate limiting** protects infrastructure globally
4. **Azure APIM** and Istio solve different problems (external vs internal)
5. **Traffic management** enables advanced deployment strategies
6. **Observability** is built-in with Istio

### Best Practices

1. ✅ Use **Istio** for internal service mesh
2. ✅ Use **Azure APIM** for external API management
3. ✅ Implement **rate limiting** at both levels
4. ✅ Enable **mTLS** for zero-trust security
5. ✅ Use **canary deployments** for safe rollouts
6. ✅ Leverage **automatic observability**
7. ✅ Test with **fault injection**

### Istio vs App-Level Patterns

| Pattern | App-Level (Resilience4j) | Infrastructure (Istio) | Best Approach |
|---------|---------------------------|------------------------|---------------|
| Circuit Breaker | ✅ Fine-grained control | ✅ Consistent policies | Both (defense in depth) |
| Retry | ✅ Business logic aware | ✅ Transparent | App-level preferred |
| Rate Limiting | ✅ Per-instance | ✅ Global | Gateway for API protection |
| Timeout | ✅ Operation-specific | ✅ Service-level | Both layers |
| Bulkhead | ✅ Thread pools | ✅ Connection pools | App-level preferred |

---

## References
- [Istio Documentation](https://istio.io/latest/docs/)
- [Envoy Proxy](https://www.envoyproxy.io/)
- [Rate Limiting with Envoy](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/rate_limit_filter)
- [Azure APIM + Istio](https://docs.microsoft.com/azure/api-management/)
- [Kiali](https://kiali.io/)
