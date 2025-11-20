# Hands-On Lab: Istio Service Mesh with Gateway-Level Rate Limiting

## Duration: 90 minutes

> **💡 Cloud Shell Ready!** This entire lab can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. Use **Bash mode** for best compatibility. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Lab Objectives
- Install Istio on Kubernetes cluster
- Deploy application with Istio sidecar injection
- Configure gateway-level rate limiting
- Implement traffic management (canary, circuit breaker)
- Test fault injection
- Visualize service mesh with Kiali

---

## Part 1: Install Istio (15 min)

### Prerequisites
- **Azure Subscription** with contributor access
- **Azure CLI** installed and logged in (`az login`)
- **kubectl** installed
- **Helm 3** installed
- **Azure Kubernetes Service (AKS)** cluster running
- **Azure Container Registry (ACR)** for images

### Step 0: Verify AKS Cluster (If not already created)

```bash
# Set variables
RESOURCE_GROUP="rg-cloud-native"
CLUSTER_NAME="aks-istio-demo"
LOCATION="eastus"
ACR_NAME="acrclouднative${RANDOM}"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create AKS cluster with required settings for Istio
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $CLUSTER_NAME \
  --node-count 3 \
  --node-vm-size Standard_D4s_v3 \
  --enable-managed-identity \
  --enable-addons monitoring \
  --network-plugin azure \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME

# Verify connection
kubectl get nodes

# Create ACR
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Standard

# Attach ACR to AKS
az aks update --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME --attach-acr $ACR_NAME
```

**Windows (PowerShell):**
```powershell
# Set variables
$RESOURCE_GROUP = "rg-cloud-native"
$CLUSTER_NAME = "aks-istio-demo"
$LOCATION = "eastus"
$ACR_NAME = "acrcloudnative$(Get-Random)"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create AKS cluster
az aks create `
  --resource-group $RESOURCE_GROUP `
  --name $CLUSTER_NAME `
  --node-count 3 `
  --node-vm-size Standard_D4s_v3 `
  --enable-managed-identity `
  --enable-addons monitoring `
  --network-plugin azure `
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME

# Verify
kubectl get nodes

# Create and attach ACR
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Standard
az aks update --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME --attach-acr $ACR_NAME
```

### Step 1: Download Istio

```bash
# Download latest Istio (1.20+)
curl -L https://istio.io/downloadIstio | sh -

# Move to Istio directory
cd istio-1.20.0

# Add istioctl to PATH
export PATH=$PWD/bin:$PATH

# Verify installation
istioctl version
```

**Windows (PowerShell):**
```powershell
# Download Istio
Invoke-WebRequest -Uri "https://github.com/istio/istio/releases/download/1.20.0/istio-1.20.0-win.zip" -OutFile "istio.zip"
Expand-Archive -Path "istio.zip" -DestinationPath "."
cd istio-1.20.0

# Add to PATH
$env:PATH += ";$PWD\bin"

# Verify
istioctl version
```

### Step 2: Install Istio on Kubernetes

```bash
# Install Istio with demo profile (includes all features)
istioctl install --set profile=demo -y

# Verify installation
kubectl get pods -n istio-system

# Expected output:
# istiod-xxx          1/1     Running
# istio-ingressgateway-xxx    1/1     Running
# istio-egressgateway-xxx     1/1     Running
```

**For Production:**
```bash
# Use production profile
istioctl install --set profile=production -y
```

### Step 3: Enable Sidecar Injection

```bash
# Label namespace for automatic sidecar injection
kubectl create namespace istio-demo
kubectl label namespace istio-demo istio-injection=enabled

# Verify label
kubectl get namespace istio-demo --show-labels
```

### Step 4: Install Observability Add-ons

```bash
# Install Kiali, Prometheus, Grafana, Jaeger
kubectl apply -f samples/addons/

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=kiali -n istio-system --timeout=300s

# Access Kiali dashboard
kubectl port-forward -n istio-system svc/kiali 20001:20001
```

Open browser: http://localhost:20001

---

## Part 2: Deploy Application with Istio (15 min)

### Step 1: Create Application Deployment

```yaml
# deployment-istio.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: istio-demo
  labels:
    istio-injection: enabled
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilience4j-demo
  namespace: istio-demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: resilience4j-demo
      version: v1
  template:
    metadata:
      labels:
        app: resilience4j-demo
        version: v1
    spec:
      containers:
      - name: app
        image: ${ACR_NAME}.azurecr.io/resilience4j-demo:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: connection-string
        resources:
          requests:
            cpu: 250m
            memory: 512Mi
          limits:
            cpu: 500m
            memory: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: resilience4j-demo
  namespace: istio-demo
  labels:
    app: resilience4j-demo
spec:
  ports:
  - port: 80
    targetPort: 8080
    name: http
  selector:
    app: resilience4j-demo
```

### Step 2: Build and Push Image to ACR

```bash
# Navigate to application directory
cd e:/Courses/Cloud\ Native/cloud_native/resilience4j-demo

# Build and push to ACR using Azure ACR Tasks
az acr build --registry $ACR_NAME --image resilience4j-demo:latest .

# Or build locally and push
# docker build -t resilience4j-demo:latest .
# az acr login --name $ACR_NAME
# docker tag resilience4j-demo:latest ${ACR_NAME}.azurecr.io/resilience4j-demo:latest
# docker push ${ACR_NAME}.azurecr.io/resilience4j-demo:latest
```

**Windows (PowerShell):**
```powershell
# Navigate to application directory
cd "e:\Courses\Cloud Native\cloud_native\resilience4j-demo"

# Build and push to ACR
az acr build --registry $ACR_NAME --image resilience4j-demo:latest .
```

### Step 3: Create Application Insights Secret

```bash
# Create Application Insights instance
APP_INSIGHTS_NAME="ai-resilience4j-demo"
az monitor app-insights component create \
  --app $APP_INSIGHTS_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --application-type web

# Get connection string
CONNECTION_STRING=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query connectionString -o tsv)

# Create Kubernetes secret
kubectl create secret generic app-insights-secret \
  --from-literal=connection-string="$CONNECTION_STRING" \
  -n istio-demo
```

### Step 4: Deploy Application

```bash
# Update deployment file with your ACR name
sed -i "s/\${ACR_NAME}/$ACR_NAME/g" deployment-istio.yaml

# Apply deployment
kubectl apply -f deployment-istio.yaml

# Verify pods have 2 containers (app + istio-proxy)
kubectl get pods -n istio-demo

# Expected output:
# NAME                                READY   STATUS
# resilience4j-demo-xxx               2/2     Running

# Check sidecar injection
kubectl describe pod -n istio-demo resilience4j-demo-xxx | grep -i envoy
```

### Step 3: Create Gateway

```yaml
# gateway.yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: resilience4j-gateway
  namespace: istio-demo
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "*"
---
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-demo
  namespace: istio-demo
spec:
  hosts:
  - "*"
  gateways:
  - resilience4j-gateway
  http:
  - match:
    - uri:
        prefix: "/api"
    route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
```

```bash
# Apply gateway
kubectl apply -f gateway.yaml

# Get ingress gateway URL (Azure Load Balancer External IP)
kubectl get svc istio-ingressgateway -n istio-system

# Wait for External IP (Azure Load Balancer provisioning)
kubectl wait --for=jsonpath='{.status.loadBalancer.ingress[0].ip}' svc/istio-ingressgateway -n istio-system --timeout=300s

# Get the external IP
export GATEWAY_IP=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
export GATEWAY_URL="http://${GATEWAY_IP}"

echo "Gateway URL: $GATEWAY_URL"

# Test access
curl http://$GATEWAY_IP/api/circuit-breaker/demo
```

---

## Part 3: Gateway-Level Rate Limiting (25 min)

### Step 1: Create Azure Redis Cache

```bash
# Create Azure Redis Cache for rate limiting
REDIS_NAME="redis-ratelimit-${RANDOM}"

az redis create \
  --name $REDIS_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Basic \
  --vm-size c0 \
  --enable-non-ssl-port true

# Get Redis connection info
REDIS_HOST=$(az redis show --name $REDIS_NAME --resource-group $RESOURCE_GROUP --query hostName -o tsv)
REDIS_KEY=$(az redis list-keys --name $REDIS_NAME --resource-group $RESOURCE_GROUP --query primaryKey -o tsv)

# Create Kubernetes secret for Redis
kubectl create secret generic redis-secret \
  --from-literal=host=$REDIS_HOST \
  --from-literal=key=$REDIS_KEY \
  -n istio-system

echo "Redis Host: $REDIS_HOST"
```

**Windows (PowerShell):**
```powershell
# Create Azure Redis Cache
$REDIS_NAME = "redis-ratelimit-$(Get-Random)"

az redis create `
  --name $REDIS_NAME `
  --resource-group $RESOURCE_GROUP `
  --location $LOCATION `
  --sku Basic `
  --vm-size c0 `
  --enable-non-ssl-port true

# Get connection info
$REDIS_HOST = az redis show --name $REDIS_NAME --resource-group $RESOURCE_GROUP --query hostName -o tsv
$REDIS_KEY = az redis list-keys --name $REDIS_NAME --resource-group $RESOURCE_GROUP --query primaryKey -o tsv

# Create secret
kubectl create secret generic redis-secret `
  --from-literal=host=$REDIS_HOST `
  --from-literal=key=$REDIS_KEY `
  -n istio-system
```

### Step 2: Deploy Rate Limit Service

```bash
# Create rate limit configuration and service
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: ratelimit-config
  namespace: istio-system
data:
  config.yaml: |
    domain: resilience4j-ratelimit
    descriptors:
      # Global rate limit: 100 requests per minute
      - key: header_match
        value: "global"
        rate_limit:
          unit: minute
          requests_per_unit: 100
      
      # Premium tier: 1000 req/min
      - key: generic_key
        value: "tier"
        descriptors:
          - key: header_match
            value: "premium"
            rate_limit:
              unit: minute
              requests_per_unit: 1000
          
          # Standard tier: 100 req/min
          - key: header_match
            value: "standard"
            rate_limit:
              unit: minute
              requests_per_unit: 100
          
          # Free tier: 10 req/min
          - key: header_match
            value: "free"
            rate_limit:
              unit: minute
              requests_per_unit: 10
---
# Redis service pointing to Azure Redis Cache
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: istio-system
spec:
  type: ExternalName
  externalName: $(kubectl get secret redis-secret -n istio-system -o jsonpath='{.data.host}' | base64 -d)
---
apiVersion: v1
kind: Service
metadata:
  name: ratelimit
  namespace: istio-system
spec:
  ports:
  - name: http-port
    port: 8080
    targetPort: 8080
    protocol: TCP
  - name: grpc-port
    port: 8081
    targetPort: 8081
    protocol: TCP
  - name: http-debug
    port: 6070
    targetPort: 6070
    protocol: TCP
  selector:
    app: ratelimit
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ratelimit
  namespace: istio-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ratelimit
  template:
    metadata:
      labels:
        app: ratelimit
    spec:
      containers:
      - name: ratelimit
        image: envoyproxy/ratelimit:master
        ports:
        - containerPort: 8080
        - containerPort: 8081
        - containerPort: 6070
        env:
        - name: LOG_LEVEL
          value: debug
        - name: REDIS_SOCKET_TYPE
          value: tcp
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: host
        - name: REDIS_AUTH
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: key
        - name: USE_STATSD
          value: "false"
        - name: RUNTIME_ROOT
          value: /data
        - name: RUNTIME_SUBDIRECTORY
          value: ratelimit
        volumeMounts:
        - name: config-volume
          mountPath: /data/ratelimit/config
      volumes:
      - name: config-volume
        configMap:
          name: ratelimit-config
EOF
```

### Step 2: Configure EnvoyFilter

```yaml
# envoyfilter-ratelimit.yaml
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
  # Add rate limit cluster
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
                    address: ratelimit.istio-system.svc.cluster.local
                    port_value: 8081
  
  # Add rate limit filter
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
```

```bash
kubectl apply -f envoyfilter-ratelimit.yaml
```

### Step 3: Update VirtualService with Rate Limit Actions

```yaml
# virtualservice-ratelimit.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-demo
  namespace: istio-demo
spec:
  hosts:
  - "*"
  gateways:
  - resilience4j-gateway
  http:
  - match:
    - uri:
        prefix: "/api"
    route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
    # Rate limiting configuration
    match:
    - headers:
        x-tier:
          exact: premium
    route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
    headers:
      request:
        set:
          x-ratelimit-tier: "premium"
  - match:
    - headers:
        x-tier:
          exact: standard
    route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
    headers:
      request:
        set:
          x-ratelimit-tier: "standard"
  - route:
    - destination:
        host: resilience4j-demo
        port:
          number: 80
    headers:
      request:
        set:
          x-ratelimit-tier: "free"
```

```bash
kubectl apply -f virtualservice-ratelimit.yaml
```

### Step 4: Test Rate Limiting

```bash
# Get gateway IP if not set
export GATEWAY_IP=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Test free tier (10 req/min)
for i in {1..15}; do
  curl -I http://$GATEWAY_IP/api/circuit-breaker/demo
done

# Expected: First 10 succeed, rest return 429 Too Many Requests

# Test premium tier (1000 req/min)
for i in {1..20}; do
  curl -I -H "x-tier: premium" http://$GATEWAY_IP/api/circuit-breaker/demo
done

# All should succeed
```

**Windows (PowerShell):**
```powershell
# Get gateway IP
$GATEWAY_IP = kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'

# Test free tier
1..15 | ForEach-Object {
  curl.exe -I "http://$GATEWAY_IP/api/circuit-breaker/demo"
}

# Test premium tier
1..20 | ForEach-Object {
  curl.exe -I -H "x-tier: premium" "http://$GATEWAY_IP/api/circuit-breaker/demo"
}
```

```bash

# Check rate limit service logs
kubectl logs -n istio-system deployment/ratelimit -f
```

---

## Part 4: Traffic Management (20 min)

### Scenario 1: Canary Deployment

```yaml
# canary-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilience4j-demo-v2
  namespace: istio-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: resilience4j-demo
      version: v2
  template:
    metadata:
      labels:
        app: resilience4j-demo
        version: v2
    spec:
      containers:
      - name: app
        image: ${ACR_NAME}.azurecr.io/resilience4j-demo:v2
        ports:
        - containerPort: 8080
        env:
        - name: VERSION
          value: "v2"
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: app-insights-secret
              key: connection-string
        resources:
          requests:
            cpu: 250m
            memory: 512Mi
          limits:
            cpu: 500m
            memory: 1Gi
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: resilience4j-demo
  namespace: istio-demo
spec:
  host: resilience4j-demo
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
---
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-canary
  namespace: istio-demo
spec:
  hosts:
  - resilience4j-demo
  http:
  - match:
    - headers:
        x-version:
          exact: "v2"
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
      weight: 10  # 10% to v2
```

```bash
kubectl apply -f canary-deployment.yaml

# Test canary
for i in {1..20}; do
  curl http://$GATEWAY_URL/api/circuit-breaker/demo
done

# Check which version responded (look at logs or response headers)
```

### Scenario 2: Circuit Breaking

```yaml
# circuit-breaker.yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: circuit-breaker
  namespace: istio-demo
spec:
  host: resilience4j-demo
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 10
      http:
        http1MaxPendingRequests: 5
        http2MaxRequests: 10
        maxRequestsPerConnection: 2
    outlierDetection:
      consecutiveErrors: 3
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
```

```bash
kubectl apply -f circuit-breaker.yaml

# Generate load to trigger circuit breaker
for i in {1..100}; do
  curl http://$GATEWAY_URL/api/circuit-breaker/demo?fail=true &
done
wait
```

### Scenario 3: Fault Injection

```yaml
# fault-injection.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: fault-injection
  namespace: istio-demo
spec:
  hosts:
  - resilience4j-demo
  http:
  - fault:
      delay:
        percentage:
          value: 20
        fixedDelay: 3s
      abort:
        percentage:
          value: 10
        httpStatus: 503
    route:
    - destination:
        host: resilience4j-demo
```

```bash
kubectl apply -f fault-injection.yaml

# Test - some requests will be delayed/failed
for i in {1..20}; do
  time curl http://$GATEWAY_URL/api/circuit-breaker/demo
done
```

---

## Part 5: Observability with Kiali (10 min)

### Access Kiali Dashboard

```bash
# Port forward Kiali
kubectl port-forward -n istio-system svc/kiali 20001:20001
```

Open: http://localhost:20001

### Generate Traffic

```bash
# Generate continuous traffic for visualization
while true; do
  curl http://$GATEWAY_URL/api/circuit-breaker/demo
  curl http://$GATEWAY_URL/api/retry/demo
  curl http://$GATEWAY_URL/api/rate-limiter/demo
  sleep 0.5
done
```

### Explore Kiali Features

1. **Graph View**
   - Select namespace: `istio-demo`
   - View service topology
   - See traffic flow and rates
   - Identify errors

2. **Applications**
   - Click on `resilience4j-demo`
   - View metrics
   - Check health

3. **Workloads**
   - View pod details
   - Check proxy configuration

4. **Istio Config**
   - Review VirtualServices
   - Check DestinationRules
   - Validate configuration

### Access Jaeger for Tracing

```bash
kubectl port-forward -n istio-system svc/tracing 16686:80
```

Open: http://localhost:16686

### Access Grafana Dashboards

```bash
kubectl port-forward -n istio-system svc/grafana 3000:3000
```

Open: http://localhost:3000

---

## Part 6: Comparison Testing (5 min)

### Test App-Level vs Gateway-Level Rate Limiting

**App-level (Resilience4j):**
```bash
# Direct to service (bypasses Istio gateway)
kubectl port-forward -n istio-demo svc/resilience4j-demo 8080:80

# Test Resilience4j rate limiter
for i in {1..20}; do
  curl http://localhost:8080/api/rate-limiter/demo
done
```

**Gateway-level (Istio):**
```bash
# Through Istio gateway
for i in {1..20}; do
  curl http://$GATEWAY_URL/api/circuit-breaker/demo
done
```

**Compare:**
- App-level: Per-instance limit
- Gateway-level: Global limit across all instances

---

## Lab Verification Checklist

- [ ] Istio installed successfully
- [ ] Sidecar injection enabled
- [ ] Application deployed with Istio proxy
- [ ] Gateway and VirtualService configured
- [ ] Rate limiting service deployed
- [ ] Rate limits working (429 responses)
- [ ] Canary deployment tested
- [ ] Circuit breaker triggered
- [ ] Fault injection working
- [ ] Kiali dashboard accessible
- [ ] Service graph visible
- [ ] Jaeger traces visible

---

## Cleanup

```bash
# Delete application namespace
kubectl delete namespace istio-demo

# Uninstall Istio
istioctl uninstall --purge -y

# Delete istio-system namespace
kubectl delete namespace istio-system

# Delete Azure resources (optional - if you want to delete everything)
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

**Windows (PowerShell):**
```powershell
# Delete Kubernetes resources
kubectl delete namespace istio-demo
istioctl uninstall --purge -y
kubectl delete namespace istio-system

# Delete Azure resource group
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

---

## Troubleshooting

**Sidecar not injected:**
```bash
# Check namespace label
kubectl get namespace istio-demo --show-labels

# Manual injection
kubectl apply -f <(istioctl kube-inject -f deployment.yaml)
```

**Rate limiting not working:**
```bash
# Check rate limit service logs
kubectl logs -n istio-system deployment/ratelimit

# Check Envoy config
istioctl proxy-config all istio-ingressgateway-xxx -n istio-system
```

**Gateway not accessible:**
```bash
# Check ingress gateway status
kubectl get svc -n istio-system istio-ingressgateway

# Wait for Azure Load Balancer to provision External IP
kubectl wait --for=jsonpath='{.status.loadBalancer.ingress[0].ip}' svc/istio-ingressgateway -n istio-system --timeout=300s

# Check gateway configuration
kubectl describe gateway resilience4j-gateway -n istio-demo

# Verify Azure Load Balancer in Azure Portal
az network lb list --resource-group MC_${RESOURCE_GROUP}_${CLUSTER_NAME}_${LOCATION} -o table
```

**Azure Redis connection issues:**
```bash
# Verify Redis is accessible
az redis show --name $REDIS_NAME --resource-group $RESOURCE_GROUP

# Check firewall rules (add AKS subnet if needed)
az redis firewall-rules list --name $REDIS_NAME --resource-group $RESOURCE_GROUP

# Test Redis connectivity from pod
kubectl run redis-test --image=redis:alpine -it --rm -- redis-cli -h $REDIS_HOST -a $REDIS_KEY ping
```

**ACR image pull errors:**
```bash
# Verify ACR attachment
az aks show --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME --query "servicePrincipalProfile"

# Re-attach ACR if needed
az aks update --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME --attach-acr $ACR_NAME

# Or use image pull secret
kubectl create secret docker-registry acr-secret \
  --docker-server=${ACR_NAME}.azurecr.io \
  --docker-username=$ACR_NAME \
  --docker-password=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" -o tsv) \
  -n istio-demo
```

---

## Next Steps
- Implement mTLS for service-to-service encryption
- Configure external authorization with Azure AD
- Set up multi-cluster mesh across Azure regions
- **Integrate with Azure API Management (APIM)**
- Configure Azure Front Door for global load balancing
- Enable Azure Monitor Container Insights for Istio
- Set up Azure Key Vault for certificate management
- Implement Azure Policy for Istio governance
- Configure Azure Private Link for secure Redis access
- Enable Azure Defender for Kubernetes security scanning
