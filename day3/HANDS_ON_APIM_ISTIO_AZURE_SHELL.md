# Hands-On Lab: Azure APIM + Istio Integration
## Using Azure Cloud Shell Only

**Duration:** 60-90 minutes  
**Prerequisites:** Azure subscription, AKS cluster with Istio installed

---

## Lab Objectives

By the end of this lab, you will:
1. Deploy Azure API Management using Azure Cloud Shell
2. Configure Istio Gateway with internal load balancer
3. Create and publish APIs in APIM
4. Test the complete integration flow
5. Monitor traffic using Azure Monitor

---

## Part 1: Environment Setup (10 minutes)

### Step 1: Open Azure Cloud Shell

1. Navigate to https://shell.azure.com
2. Select **Bash** as your shell environment
3. Ensure you're in the correct subscription:

```bash
# Check current subscription
az account show --output table

# If needed, set the correct subscription
# az account set --subscription "Your Subscription Name"
```

### Step 2: Set Environment Variables

```bash
# Set these variables for your environment
export RESOURCE_GROUP="rg-cloud-native-lab"
export LOCATION="eastus"
export AKS_NAME="aks-cloud-native"
export APIM_NAME="apim-lab-$RANDOM"
export PUBLISHER_EMAIL="your-email@example.com"
export PUBLISHER_NAME="Cloud Native Training"

# Display values
echo "Resource Group: $RESOURCE_GROUP"
echo "APIM Name: $APIM_NAME"
```

### Step 3: Verify AKS Access

```bash
# Get AKS credentials
az aks get-credentials \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_NAME \
  --overwrite-existing

# Verify kubectl access
kubectl get nodes

# Verify Istio is installed
kubectl get namespace istio-system
kubectl get pods -n istio-system
```

**✅ Checkpoint:** You should see Istio pods running (istiod, istio-ingressgateway, etc.)

---

## Part 2: Deploy Application to AKS (15 minutes)

### Step 4: Create Sample Application

Create a simple demo service for testing:

```bash
# Create namespace with Istio injection
kubectl create namespace demo
kubectl label namespace demo istio-injection=enabled

# Deploy sample application
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-demo
  namespace: demo
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-demo
  template:
    metadata:
      labels:
        app: api-demo
        version: v1
    spec:
      containers:
      - name: api-demo
        image: kennethreitz/httpbin
        ports:
        - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: api-demo
  namespace: demo
spec:
  selector:
    app: api-demo
  ports:
  - port: 80
    targetPort: 80
EOF
```

### Step 5: Configure Istio Gateway

```bash
# Create Istio Gateway
cat <<EOF | kubectl apply -f -
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: api-gateway
  namespace: demo
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
  name: api-demo
  namespace: demo
spec:
  hosts:
  - "*"
  gateways:
  - api-gateway
  http:
  - match:
    - uri:
        prefix: /api
    rewrite:
      uri: /
    route:
    - destination:
        host: api-demo
        port:
          number: 80
EOF
```

### Step 6: Create Internal Load Balancer for Istio

```bash
# Create internal LB service for APIM to access
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: istio-ingressgateway-internal
  namespace: istio-system
  annotations:
    service.beta.kubernetes.io/azure-load-balancer-internal: "true"
spec:
  type: LoadBalancer
  selector:
    istio: ingressgateway
  ports:
  - name: http2
    port: 80
    targetPort: 8080
  - name: https
    port: 443
    targetPort: 8443
EOF

# Wait for internal IP to be assigned (may take 2-3 minutes)
echo "Waiting for internal IP assignment..."
sleep 30

# Get the internal IP
export ISTIO_INTERNAL_IP=$(kubectl -n istio-system get service istio-ingressgateway-internal -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

echo "Istio Internal IP: $ISTIO_INTERNAL_IP"

# Test the internal endpoint
curl -I http://$ISTIO_INTERNAL_IP/api/status/200
```

**✅ Checkpoint:** You should see HTTP/1.1 200 OK response

---

## Part 3: Deploy Azure API Management (20 minutes)

### Step 7: Create APIM Instance

```bash
# Create APIM (Developer SKU - takes ~40 minutes to provision)
# We'll use async creation and check status
az apim create \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --publisher-email $PUBLISHER_EMAIL \
  --publisher-name "$PUBLISHER_NAME" \
  --sku-name Consumption \
  --no-wait

# Check provisioning status
echo "APIM provisioning started. Checking status..."
az apim show \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "{Name:name, Status:provisioningState}" \
  --output table

# Note: Consumption tier provisions faster (~5 min) vs Developer tier (~40 min)
# For this lab, we'll proceed once status shows "Succeeded"
```

**⏰ While Waiting:** APIM takes time to provision. You can:
- Take a break
- Review the architecture diagram in the main guide
- Explore Istio observability: `kubectl -n istio-system port-forward svc/kiali 20001:20001`

### Step 8: Monitor Provisioning Status

```bash
# Check every few minutes until "Succeeded"
watch -n 30 "az apim show --name $APIM_NAME --resource-group $RESOURCE_GROUP --query provisioningState -o tsv"

# Once completed, get APIM gateway URL
export APIM_GATEWAY=$(az apim show \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --query gatewayUrl -o tsv)

echo "APIM Gateway URL: $APIM_GATEWAY"
```

**✅ Checkpoint:** APIM provisioning state should be "Succeeded"

---

## Part 4: Configure API in APIM (15 minutes)

### Step 9: Create API Definition

```bash
# Create OpenAPI specification file
cat > api-spec.json <<EOF
{
  "openapi": "3.0.0",
  "info": {
    "title": "Demo API via Istio",
    "version": "1.0.0",
    "description": "API exposed through Istio service mesh"
  },
  "servers": [
    {
      "url": "http://${ISTIO_INTERNAL_IP}",
      "description": "Istio Internal Gateway"
    }
  ],
  "paths": {
    "/api/status/{code}": {
      "get": {
        "summary": "Return status code",
        "operationId": "getStatus",
        "parameters": [
          {
            "name": "code",
            "in": "path",
            "required": true,
            "schema": {
              "type": "integer"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "Success"
          }
        }
      }
    },
    "/api/get": {
      "get": {
        "summary": "Get request info",
        "operationId": "getInfo",
        "responses": {
          "200": {
            "description": "Request information"
          }
        }
      }
    },
    "/api/delay/{seconds}": {
      "get": {
        "summary": "Delayed response",
        "operationId": "getDelay",
        "parameters": [
          {
            "name": "seconds",
            "in": "path",
            "required": true,
            "schema": {
              "type": "integer"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "Delayed response"
          }
        }
      }
    }
  }
}
EOF

echo "OpenAPI spec created"
cat api-spec.json
```

### Step 10: Import API to APIM

```bash
# Import the API
az apim api import \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --path demo \
  --specification-path api-spec.json \
  --specification-format OpenApi \
  --display-name "Demo API" \
  --protocols https http \
  --service-url "http://${ISTIO_INTERNAL_IP}"

echo "API imported successfully"

# List APIs to verify
az apim api list \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --output table
```

### Step 11: Create Product and Subscription

```bash
# Create a product (API bundle)
az apim product create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id starter \
  --product-name "Starter" \
  --description "Starter tier with basic rate limits" \
  --subscription-required true \
  --approval-required false \
  --state published

# Link API to product
az apim product api add \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id starter \
  --api-id demo-api

# Create a subscription
az apim subscription create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --subscription-id lab-subscription \
  --name "Lab Subscription" \
  --scope /products/starter \
  --state active

# Get subscription key
export SUBSCRIPTION_KEY=$(az apim subscription show \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --subscription-id lab-subscription \
  --query primaryKey -o tsv)

echo "Subscription Key: $SUBSCRIPTION_KEY"
```

**✅ Checkpoint:** You should have a valid subscription key

---

## Part 5: Configure APIM Policies (10 minutes)

### Step 12: Apply Rate Limiting Policy

```bash
# Create rate limiting policy
cat > rate-limit-policy.xml <<'EOF'
<policies>
    <inbound>
        <base />
        <!-- Rate limit: 10 requests per minute -->
        <rate-limit-by-key calls="10" 
                           renewal-period="60" 
                           counter-key="@(context.Subscription.Id)" />
        
        <!-- Add correlation ID -->
        <set-header name="X-Correlation-ID" exists-action="override">
            <value>@(Guid.NewGuid().ToString())</value>
        </set-header>
        
        <!-- Add request timestamp -->
        <set-header name="X-Request-Time" exists-action="override">
            <value>@(DateTime.UtcNow.ToString("o"))</value>
        </set-header>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
        <!-- Add response time -->
        <set-header name="X-Response-Time-Ms" exists-action="override">
            <value>@(context.Elapsed.TotalMilliseconds.ToString())</value>
        </set-header>
    </outbound>
    <on-error>
        <base />
    </on-error>
</policies>
EOF

# Apply policy to API
az apim api policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --xml-value "$(cat rate-limit-policy.xml)"

echo "Rate limiting policy applied"
```

### Step 13: Apply Caching Policy (Optional)

```bash
# Create caching policy for specific operation
cat > cache-policy.xml <<'EOF'
<policies>
    <inbound>
        <base />
        <!-- Cache lookup -->
        <cache-lookup vary-by-developer="false" 
                      vary-by-developer-groups="false">
            <vary-by-query-parameter>code</vary-by-query-parameter>
        </cache-lookup>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
        <!-- Cache responses for 30 seconds -->
        <cache-store duration="30" />
    </outbound>
    <on-error>
        <base />
    </on-error>
</policies>
EOF

# Apply to specific operation
az apim api operation policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --operation-id getStatus \
  --xml-value "$(cat cache-policy.xml)"

echo "Caching policy applied to getStatus operation"
```

---

## Part 6: Testing the Integration (15 minutes)

### Step 14: Test Basic Connectivity

```bash
# Test direct access to Istio (should work from Cloud Shell as it's in same VNet)
echo "Testing direct Istio access..."
curl -i http://$ISTIO_INTERNAL_IP/api/status/200

# Test through APIM (requires subscription key)
echo -e "\nTesting APIM access..."
curl -i -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/api/status/200"
```

### Step 15: Test Rate Limiting

```bash
# Rapid fire requests to trigger rate limit (10 req/min limit)
echo "Testing rate limiting (sending 15 requests)..."
for i in {1..15}; do
  echo "Request $i:"
  curl -s -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
    -w "\nStatus: %{http_code}\n" \
    "${APIM_GATEWAY}/demo/api/get" | head -5
  echo "---"
done

# You should see 429 (Too Many Requests) after 10 requests
```

### Step 16: Test Different Endpoints

```bash
# Test status codes
echo "Test 200 OK:"
curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/api/status/200"

echo -e "\nTest 404 Not Found:"
curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/api/status/404"

# Test delay endpoint (simulates slow backend)
echo -e "\nTest delayed response (2 seconds):"
time curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/api/delay/2"

# Test caching (call same endpoint twice)
echo -e "\nTest caching - First call:"
time curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  -H "X-Request-ID: test-1" \
  "${APIM_GATEWAY}/demo/api/status/200"

echo -e "\nTest caching - Second call (should be faster):"
time curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  -H "X-Request-ID: test-2" \
  "${APIM_GATEWAY}/demo/api/status/200"
```

### Step 17: Test Without Subscription Key

```bash
# This should fail with 401 Unauthorized
echo "Testing without subscription key (should fail):"
curl -i "${APIM_GATEWAY}/demo/api/status/200"
```

**✅ Checkpoint:** 
- ✅ Direct Istio access works
- ✅ APIM with valid key works
- ✅ Rate limiting triggers after 10 requests
- ✅ Without subscription key returns 401

---

## Part 7: Monitor and Observe (10 minutes)

### Step 18: View APIM Analytics

```bash
# Get APIM resource ID
APIM_ID=$(az apim show \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --query id -o tsv)

# Query request logs (requires Log Analytics workspace)
# First, enable diagnostic settings if not already done
az monitor diagnostic-settings create \
  --name apim-diagnostics \
  --resource $APIM_ID \
  --logs '[{"category":"GatewayLogs","enabled":true}]' \
  --metrics '[{"category":"AllMetrics","enabled":true}]' \
  --workspace $(az monitor log-analytics workspace list \
    --resource-group $RESOURCE_GROUP \
    --query "[0].id" -o tsv) 2>/dev/null || echo "Diagnostic settings may already exist"

echo "Diagnostic logging configured"
```

### Step 19: View Metrics in Portal

1. Open Azure Portal: https://portal.azure.com
2. Navigate to your APIM instance
3. Go to **Monitoring** → **Metrics**
4. Select metrics:
   - **Requests** - Total API calls
   - **Duration** - Response times
   - **Capacity** - APIM utilization
5. Apply filters by API, Operation, Response Code

### Step 20: View Istio Metrics

```bash
# Check Istio metrics
echo "Istio traffic to demo service:"
kubectl -n demo get virtualservice,destinationrule

# View Istio Gateway access logs
echo -e "\nIstio Gateway logs:"
kubectl logs -n istio-system \
  -l istio=ingressgateway \
  --tail=20

# If Kiali is installed, port-forward to access UI
# kubectl -n istio-system port-forward svc/kiali 20001:20001
# Then access: http://localhost:20001
```

---

## Part 8: Advanced Scenarios (Bonus)

### Scenario 1: Add Request Transformation

```bash
# Add transformation policy to modify request/response
cat > transform-policy.xml <<'EOF'
<policies>
    <inbound>
        <base />
        <!-- Add custom header to backend -->
        <set-header name="X-Forwarded-By" exists-action="override">
            <value>Azure-APIM</value>
        </set-header>
        <!-- Modify query parameter -->
        <set-query-parameter name="source" exists-action="override">
            <value>apim</value>
        </set-query-parameter>
    </inbound>
    <backend>
        <base />
    </backend>
    <outbound>
        <base />
        <!-- Transform response body -->
        <set-body>@{
            var response = context.Response.Body.As<JObject>();
            response.Add("processed_by", "Azure APIM");
            response.Add("timestamp", DateTime.UtcNow.ToString("o"));
            return response.ToString();
        }</set-body>
    </outbound>
</policies>
EOF

az apim api operation policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --operation-id getInfo \
  --xml-value "$(cat transform-policy.xml)"

# Test transformation
curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/api/get" | jq .
```

### Scenario 2: Configure Retry Policy

```bash
cat > retry-policy.xml <<'EOF'
<policies>
    <inbound>
        <base />
    </inbound>
    <backend>
        <!-- Retry on 5xx errors, max 3 attempts -->
        <retry condition="@(context.Response.StatusCode >= 500)" 
               count="3" 
               interval="1" 
               delta="1">
            <forward-request timeout="10" />
        </retry>
    </backend>
    <outbound>
        <base />
    </outbound>
</policies>
EOF

az apim api policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --xml-value "$(cat retry-policy.xml)"

echo "Retry policy applied"
```

### Scenario 3: Mock Response

```bash
# Add mock response for testing
cat > mock-policy.xml <<'EOF'
<policies>
    <inbound>
        <base />
        <mock-response status-code="200" content-type="application/json">
            <body>{"message": "This is a mock response from APIM", "timestamp": "@(DateTime.UtcNow.ToString())"}</body>
        </mock-response>
    </inbound>
</policies>
EOF

# Create new operation for mocking
az apim api operation create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --operation-id mock \
  --display-name "Mock Endpoint" \
  --method GET \
  --url-template "/mock"

az apim api operation policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id demo-api \
  --operation-id mock \
  --xml-value "$(cat mock-policy.xml)"

# Test mock
curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/demo/mock"
```

---

## Part 9: Cleanup (5 minutes)

### Step 21: Remove Resources

```bash
# Option 1: Delete just APIM (keep AKS and demo app)
az apim delete \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --yes

# Option 2: Delete demo namespace (keep AKS)
kubectl delete namespace demo

# Option 3: Delete everything (if you created the resource group for this lab)
# az group delete --name $RESOURCE_GROUP --yes --no-wait

echo "Cleanup completed"
```

---

## Lab Summary

### What You Accomplished

✅ **Deployed** Azure API Management via Cloud Shell  
✅ **Configured** Istio Gateway with internal load balancer  
✅ **Created** API definitions with OpenAPI spec  
✅ **Applied** APIM policies (rate limiting, caching, transformation)  
✅ **Tested** the complete integration flow  
✅ **Monitored** traffic through APIM and Istio  

### Key Takeaways

1. **APIM handles external (north-south) traffic:**
   - Authentication & authorization
   - Subscription management
   - Rate limiting per customer
   - Developer portal

2. **Istio handles internal (east-west) traffic:**
   - Service-to-service mTLS
   - Traffic routing & splitting
   - Circuit breaking
   - Observability

3. **Together they provide complete API management:**
   - External API gateway (APIM)
   - Internal service mesh (Istio)
   - End-to-end security and monitoring

### Architecture Flow

```
Internet → APIM → Internal LB → Istio Gateway → Services (with Envoy sidecars)
```

---

## Troubleshooting Guide

### Issue 1: APIM Taking Too Long to Provision
**Solution:** Use Consumption tier instead of Developer tier for faster provisioning (~5 min vs ~40 min)

### Issue 2: Cannot Access Istio Internal IP from APIM
**Solution:** Ensure APIM and AKS are in the same VNet, or set up VNet peering

### Issue 3: 401 Unauthorized from APIM
**Solution:** Verify subscription key is correct and product is published

### Issue 4: Rate Limit Not Working
**Solution:** Check policy is applied at correct level (API vs Operation) and counter-key is correct

### Issue 5: Istio Gateway Not Responding
**Solution:** 
- Verify Istio installation: `kubectl get pods -n istio-system`
- Check Gateway configuration: `kubectl get gateway,virtualservice -n demo`
- View logs: `kubectl logs -n istio-system -l istio=ingressgateway`

---

## Additional Resources

- [Azure APIM Documentation](https://docs.microsoft.com/azure/api-management/)
- [Istio Documentation](https://istio.io/latest/docs/)
- [APIM Policy Reference](https://docs.microsoft.com/azure/api-management/api-management-policies)
- [Istio Traffic Management](https://istio.io/latest/docs/concepts/traffic-management/)

---

## Next Steps

1. **Implement OAuth 2.0** authentication with Azure AD
2. **Configure custom domains** with SSL certificates
3. **Set up Application Insights** for detailed telemetry
4. **Implement API versioning** strategies
5. **Configure autoscaling** for APIM and AKS
6. **Add WAF** via Azure Front Door for DDoS protection
7. **Implement canary deployments** using Istio

**Congratulations!** 🎉 You've successfully integrated Azure APIM with Istio service mesh using only Azure Cloud Shell!
