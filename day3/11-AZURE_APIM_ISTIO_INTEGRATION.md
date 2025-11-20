# Azure API Management + Istio Integration Guide

## Duration: 45 minutes

> **💡 Cloud Shell Ready!** This entire guide can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. All Azure CLI commands work natively. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Overview

This guide demonstrates how to integrate **Azure API Management (APIM)** with **Istio service mesh** on AKS to create a complete API gateway solution that handles both external (north-south) and internal (east-west) traffic.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                         Internet                               │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│              Azure Front Door (Optional)                       │
│              - Global load balancing                           │
│              - DDoS protection                                 │
│              - WAF                                             │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│              Azure API Management (APIM)                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  External Gateway (North-South Traffic)                  │  │
│  │  ✅ OAuth 2.0 / Azure AD authentication                  │  │
│  │  ✅ Subscription key management                          │  │
│  │  ✅ Rate limiting (per subscription)                     │  │
│  │  ✅ Request/Response transformation                      │  │
│  │  ✅ CORS policies                                        │  │
│  │  ✅ Caching                                              │  │
│  │  ✅ Developer Portal                                     │  │
│  │  ✅ Azure Monitor integration                            │  │
│  └──────────────────────────────────────────────────────────┘  │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│         Azure Kubernetes Service (AKS) with Istio              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Istio Ingress Gateway                       │  │
│  │  ✅ Azure Load Balancer (Private or Internal)            │  │
│  │  ✅ mTLS for service-to-service                          │  │
│  │  ✅ Fine-grained rate limiting                           │  │
│  │  ✅ Circuit breaking                                     │  │
│  │  ✅ Traffic routing (canary, A/B)                        │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
│  Internal Service Mesh (East-West Traffic)                     │
│                       │                                         │
│       ┌───────────────┼────────────────┐                       │
│       ▼               ▼                ▼                       │
│  ┌─────────┐    ┌─────────┐     ┌─────────┐                   │
│  │Order Svc│    │Payment  │     │Shipping │                   │
│  │+ Envoy  │───▶│Svc      │────▶│Svc      │                   │
│  │Proxy    │    │+ Envoy  │     │+ Envoy  │                   │
│  └─────────┘    └─────────┘     └─────────┘                   │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│                    Azure Backend Services                      │
│  - Azure SQL Database                                          │
│  - Azure Cosmos DB                                             │
│  - Azure Service Bus / Event Hub                               │
│  - Azure Redis Cache                                           │
│  - Azure Key Vault                                             │
│  - Application Insights                                        │
└────────────────────────────────────────────────────────────────┘
```

---

## Part 1: Create Azure API Management Instance

### Step 1: Create APIM

```bash
# Set variables
RESOURCE_GROUP="rg-cloud-native"
LOCATION="eastus"
APIM_NAME="apim-resilience4j-${RANDOM}"
PUBLISHER_EMAIL="admin@example.com"
PUBLISHER_NAME="Cloud Native Training"

# Create APIM (Developer tier for testing, takes ~40 minutes)
az apim create \
  --name $APIM_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --publisher-email $PUBLISHER_EMAIL \
  --publisher-name "$PUBLISHER_NAME" \
  --sku-name Developer \
  --enable-managed-identity true

# For production, use Premium tier with VNet integration
# az apim create \
#   --name $APIM_NAME \
#   --resource-group $RESOURCE_GROUP \
#   --location $LOCATION \
#   --publisher-email $PUBLISHER_EMAIL \
#   --publisher-name "$PUBLISHER_NAME" \
#   --sku-name Premium \
#   --enable-managed-identity true \
#   --virtual-network External

# Get APIM gateway URL
APIM_GATEWAY=$(az apim show --name $APIM_NAME --resource-group $RESOURCE_GROUP --query gatewayUrl -o tsv)
echo "APIM Gateway: $APIM_GATEWAY"
```

**Windows (PowerShell):**
```powershell
$RESOURCE_GROUP = "rg-cloud-native"
$LOCATION = "eastus"
$APIM_NAME = "apim-resilience4j-$(Get-Random)"
$PUBLISHER_EMAIL = "admin@example.com"
$PUBLISHER_NAME = "Cloud Native Training"

# Create APIM
az apim create `
  --name $APIM_NAME `
  --resource-group $RESOURCE_GROUP `
  --location $LOCATION `
  --publisher-email $PUBLISHER_EMAIL `
  --publisher-name $PUBLISHER_NAME `
  --sku-name Developer `
  --enable-managed-identity true

# Get gateway URL
$APIM_GATEWAY = az apim show --name $APIM_NAME --resource-group $RESOURCE_GROUP --query gatewayUrl -o tsv
```

### Step 2: Configure Internal Load Balancer for Istio

```bash
# Create internal load balancer for Istio (private access from APIM)
kubectl apply -f - <<EOF
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

# Get internal IP
ISTIO_INTERNAL_IP=$(kubectl -n istio-system get service istio-ingressgateway-internal -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Istio Internal IP: $ISTIO_INTERNAL_IP"
```

---

## Part 2: Create API in APIM

### Step 3: Import OpenAPI Spec

First, create an OpenAPI specification for your API:

```yaml
# api-spec.yaml
openapi: 3.0.0
info:
  title: Resilience4j Demo API
  version: 1.0.0
  description: API with resilience patterns
servers:
  - url: http://{istio-internal-ip}
    description: Istio Gateway
paths:
  /api/circuit-breaker/demo:
    get:
      summary: Circuit Breaker Demo
      operationId: circuitBreakerDemo
      parameters:
        - name: fail
          in: query
          schema:
            type: boolean
      responses:
        '200':
          description: Success
        '503':
          description: Circuit Open
  /api/retry/demo:
    get:
      summary: Retry Demo
      operationId: retryDemo
      responses:
        '200':
          description: Success
  /api/rate-limiter/demo:
    get:
      summary: Rate Limiter Demo
      operationId: rateLimiterDemo
      responses:
        '200':
          description: Success
        '429':
          description: Too Many Requests
```

### Step 4: Import API to APIM

```bash
# Import API from OpenAPI spec
az apim api import \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id resilience4j-api \
  --path resilience4j \
  --specification-path api-spec.yaml \
  --specification-format OpenApiJson \
  --display-name "Resilience4j API" \
  --protocols https \
  --service-url "http://${ISTIO_INTERNAL_IP}"
```

---

## Part 3: Configure APIM Policies

### Step 5: Global Policies

```xml
<!-- Global policy for all APIs -->
<policies>
    <inbound>
        <!-- Validate Azure AD JWT token -->
        <validate-jwt header-name="Authorization" failed-validation-httpcode="401" failed-validation-error-message="Unauthorized">
            <openid-config url="https://login.microsoftonline.com/{tenant-id}/v2.0/.well-known/openid-configuration" />
            <audiences>
                <audience>api://{client-id}</audience>
            </audiences>
        </validate-jwt>
        
        <!-- Rate limiting by subscription -->
        <rate-limit-by-key calls="1000" renewal-period="60" counter-key="@(context.Subscription.Id)" />
        
        <!-- Add correlation ID -->
        <set-header name="X-Correlation-ID" exists-action="override">
            <value>@(Guid.NewGuid().ToString())</value>
        </set-header>
        
        <!-- Forward user context to backend -->
        <set-header name="X-User-ID" exists-action="override">
            <value>@(context.User?.Id)</value>
        </set-header>
        
        <!-- Enable CORS -->
        <cors allow-credentials="true">
            <allowed-origins>
                <origin>https://yourdomain.com</origin>
            </allowed-origins>
            <allowed-methods>
                <method>GET</method>
                <method>POST</method>
                <method>PUT</method>
                <method>DELETE</method>
            </allowed-methods>
            <allowed-headers>
                <header>*</header>
            </allowed-headers>
        </cors>
        
        <!-- Request logging -->
        <log-to-eventhub logger-id="apim-logger">
            @{
                return new JObject(
                    new JProperty("EventTime", DateTime.UtcNow.ToString()),
                    new JProperty("ServiceName", context.Api.Name),
                    new JProperty("RequestId", context.RequestId),
                    new JProperty("RequestIp", context.Request.IpAddress),
                    new JProperty("OperationName", context.Operation.Name)
                ).ToString();
            }
        </log-to-eventhub>
    </inbound>
    
    <backend>
        <!-- Timeout configuration -->
        <timeout timeout="30" />
        <forward-request timeout="120" />
    </backend>
    
    <outbound>
        <!-- Remove backend headers -->
        <set-header name="X-Powered-By" exists-action="delete" />
        <set-header name="X-AspNet-Version" exists-action="delete" />
        
        <!-- Add response headers -->
        <set-header name="X-Response-Time" exists-action="override">
            <value>@(context.Elapsed.TotalMilliseconds.ToString())</value>
        </set-header>
    </outbound>
    
    <on-error>
        <!-- Error handling -->
        <set-body>@{
            return new JObject(
                new JProperty("error", new JObject(
                    new JProperty("code", context.LastError.Source),
                    new JProperty("message", context.LastError.Message),
                    new JProperty("requestId", context.RequestId)
                ))
            ).ToString();
        }</set-body>
        <set-status code="500" reason="Internal Server Error" />
    </on-error>
</policies>
```

### Step 6: API-Specific Policies

```xml
<!-- Circuit breaker endpoint policy -->
<policies>
    <inbound>
        <base />
        <!-- Cache successful responses -->
        <cache-lookup vary-by-developer="false" vary-by-developer-groups="false">
            <vary-by-query-parameter>fail</vary-by-query-parameter>
        </cache-lookup>
    </inbound>
    
    <backend>
        <base />
        <!-- Retry on failure -->
        <retry condition="@(context.Response.StatusCode >= 500)" count="3" interval="1" delta="1">
            <forward-request timeout="10" />
        </retry>
    </backend>
    
    <outbound>
        <base />
        <!-- Store in cache for 60 seconds -->
        <cache-store duration="60" />
    </outbound>
</policies>
```

Apply policies using Azure CLI:

```bash
# Apply global policy
az apim api operation policy create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --api-id resilience4j-api \
  --policy-content @global-policy.xml
```

---

## Part 4: Subscription Management

### Step 7: Create Products and Subscriptions

```bash
# Create Free tier product
az apim product create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id free-tier \
  --product-name "Free Tier" \
  --description "10 requests per minute" \
  --subscription-required true \
  --approval-required false \
  --state published

# Create Standard tier product
az apim product create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id standard-tier \
  --product-name "Standard Tier" \
  --description "100 requests per minute" \
  --subscription-required true \
  --approval-required true \
  --state published

# Create Premium tier product
az apim product create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id premium-tier \
  --product-name "Premium Tier" \
  --description "1000 requests per minute" \
  --subscription-required true \
  --approval-required true \
  --state published

# Link API to products
az apim product api add \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --product-id free-tier \
  --api-id resilience4j-api

# Create subscription
az apim subscription create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --subscription-id test-subscription \
  --name "Test Subscription" \
  --scope /products/free-tier

# Get subscription key
SUBSCRIPTION_KEY=$(az apim subscription show \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --subscription-id test-subscription \
  --query primaryKey -o tsv)

echo "Subscription Key: $SUBSCRIPTION_KEY"
```

---

## Part 5: Integration Testing

### Step 8: Test APIM → Istio Flow

```bash
# Test through APIM (with subscription key)
curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
  "${APIM_GATEWAY}/resilience4j/api/circuit-breaker/demo"

# Expected flow:
# Client → APIM (auth, rate limit, transform) → Istio Gateway → Service

# Test rate limiting
for i in {1..15}; do
  curl -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" \
    "${APIM_GATEWAY}/resilience4j/api/rate-limiter/demo"
done
# Should see 429 after 10 requests (free tier limit)

# Test with invalid subscription key
curl -H "Ocp-Apim-Subscription-Key: invalid-key" \
  "${APIM_GATEWAY}/resilience4j/api/circuit-breaker/demo"
# Should return 401 Unauthorized
```

**Windows (PowerShell):**
```powershell
# Test through APIM
curl.exe -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" `
  "$APIM_GATEWAY/resilience4j/api/circuit-breaker/demo"

# Rate limit test
1..15 | ForEach-Object {
  curl.exe -H "Ocp-Apim-Subscription-Key: $SUBSCRIPTION_KEY" `
    "$APIM_GATEWAY/resilience4j/api/rate-limiter/demo"
}
```

---

## Part 6: Azure Monitor Integration

### Step 9: Enable Monitoring

```bash
# Enable Application Insights for APIM
APP_INSIGHTS_ID=$(az monitor app-insights component show \
  --app ai-resilience4j-demo \
  --resource-group $RESOURCE_GROUP \
  --query id -o tsv)

az apim api create \
  --resource-group $RESOURCE_GROUP \
  --service-name $APIM_NAME \
  --logger-id app-insights-logger \
  --logger-type applicationInsights \
  --logger-credentials instrumentationKey=${APP_INSIGHTS_KEY}

# Enable diagnostic settings
az monitor diagnostic-settings create \
  --name apim-diagnostics \
  --resource $(az apim show --name $APIM_NAME --resource-group $RESOURCE_GROUP --query id -o tsv) \
  --logs '[{"category":"GatewayLogs","enabled":true}]' \
  --metrics '[{"category":"AllMetrics","enabled":true}]' \
  --workspace $(az monitor log-analytics workspace show --resource-group $RESOURCE_GROUP --workspace-name law-cloud-native --query id -o tsv)
```

### Step 10: Query Metrics

```kql
-- APIM request analytics
ApiManagementGatewayLogs
| where TimeGenerated > ago(1h)
| summarize 
    RequestCount = count(),
    AvgDuration = avg(TotalTime),
    ErrorRate = countif(ResponseCode >= 400) * 100.0 / count()
  by bin(TimeGenerated, 5m), ApiId
| render timechart

-- Rate limiting violations
ApiManagementGatewayLogs
| where ResponseCode == 429
| summarize RateLimitHits = count() by SubscriptionId, bin(TimeGenerated, 1m)
| render timechart

-- Error tracking
ApiManagementGatewayLogs
| where ResponseCode >= 500
| project TimeGenerated, RequestId, ApiId, OperationId, ResponseCode, LastError
| order by TimeGenerated desc
```

---

## Part 7: Security Best Practices

### OAuth 2.0 Integration

```bash
# Register app in Azure AD
az ad app create \
  --display-name "Resilience4j API" \
  --sign-in-audience AzureADMyOrg \
  --web-redirect-uris "https://${APIM_NAME}.azure-api.net/signin-oauth"

# Configure APIM OAuth 2.0
# (Done through Azure Portal: APIM → OAuth 2.0 → Add)
```

### Managed Identity for Backend Access

```bash
# Enable managed identity for APIM
az apim update \
  --resource-group $RESOURCE_GROUP \
  --name $APIM_NAME \
  --set identity.type=SystemAssigned

# Grant APIM access to Key Vault
APIM_IDENTITY=$(az apim show --name $APIM_NAME --resource-group $RESOURCE_GROUP --query identity.principalId -o tsv)

az keyvault set-policy \
  --name kv-cloud-native \
  --resource-group $RESOURCE_GROUP \
  --object-id $APIM_IDENTITY \
  --secret-permissions get list
```

---

## Comparison: APIM vs Istio

| Feature | Azure APIM | Istio | Best Practice |
|---------|------------|-------|---------------|
| **External APIs** | ✅ Excellent | ❌ Not designed for | Use APIM |
| **Internal APIs** | ❌ Overkill | ✅ Excellent | Use Istio |
| **Developer Portal** | ✅ Built-in | ❌ None | APIM only |
| **OAuth/Azure AD** | ✅ Native | ⚠️ Manual | APIM preferred |
| **mTLS** | ⚠️ Complex | ✅ Automatic | Istio excels |
| **Rate Limiting** | ✅ Per subscription | ✅ Per service | Both (layered) |
| **Monitoring** | ✅ Azure Monitor | ✅ Prometheus/Kiali | Use both |
| **Cost** | 💰 Pay-per-use | 🆓 Free (OSS) | Istio cheaper |

---

## Summary

**Use Azure APIM for:**
- Public API exposure
- Developer onboarding
- Subscription management
- Monetization
- OAuth 2.0 / Azure AD integration
- External partner APIs

**Use Istio for:**
- Service-to-service communication
- mTLS encryption
- Advanced traffic management
- Kubernetes-native deployments
- Zero-trust networking
- Fine-grained observability

**Use Both Together:**
- APIM handles **north-south** (external) traffic
- Istio handles **east-west** (internal) traffic
- Layered security and rate limiting
- Complete observability across the stack

---

## Next Steps
- Configure Azure Front Door for global distribution
- Implement custom domains with SSL certificates
- Set up Azure AD B2C for customer authentication
- Configure APIM Self-Hosted Gateway for hybrid scenarios
- Implement API versioning strategies
- Set up automated API testing with Azure DevOps
