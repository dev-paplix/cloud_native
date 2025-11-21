# Azure Key Vault Integration with Istio for AKS

This guide demonstrates how to integrate Azure Key Vault with your AKS cluster using Istio's Secret Discovery Service (SDS) and Azure Key Vault provider for Secrets Store CSI driver.

## Table of Contents
- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Architecture](#architecture)
- [Setup Azure Key Vault](#setup-azure-key-vault)
- [Install Secrets Store CSI Driver](#install-secrets-store-csi-driver)
- [Configure Workload Identity](#configure-workload-identity)
- [Create SecretProviderClass](#create-secretproviderclass)
- [Update Deployment with Key Vault](#update-deployment-with-key-vault)
- [Istio Integration](#istio-integration)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

## Overview

This integration provides:
- **Centralized Secret Management**: Store all secrets in Azure Key Vault
- **Automatic Secret Rotation**: Secrets are synced from Key Vault to pods
- **Istio SDS Integration**: Leverage Istio's Secret Discovery Service
- **Pod Identity**: Use Azure Workload Identity for secure access
- **No Code Changes**: Transparent to application code

## Prerequisites

```bash
# Azure CLI
az version

# kubectl
kubectl version --client

# Helm (for CSI driver installation)
helm version

# Istio
istioctl version
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Azure Key Vault                          │
│  ┌────────────────────────────────────────────────────┐     │
│  │ Secrets:                                            │     │
│  │ - applicationinsights-connection-string             │     │
│  │ - applicationinsights-instrumentation-key           │     │
│  │ - database-password                                 │     │
│  │ - api-keys                                          │     │
│  └────────────────────────────────────────────────────┘     │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ Azure Workload Identity
                           │
┌──────────────────────────┴──────────────────────────────────┐
│              AKS Cluster with Istio                          │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Secrets Store CSI Driver                          │     │
│  │  - Mounts secrets as volumes                       │     │
│  │  - Syncs to Kubernetes Secrets (optional)          │     │
│  └────────────────────────────────────────────────────┘     │
│                           │                                  │
│  ┌────────────────────────┴────────────────────────────┐    │
│  │  SecretProviderClass                                │    │
│  │  - Defines Key Vault connection                    │    │
│  │  - Maps secrets to files/env vars                  │    │
│  └────────────────────────┬────────────────────────────┘    │
│                           │                                  │
│  ┌────────────────────────┴────────────────────────────┐    │
│  │  Pod with Istio Sidecar                             │    │
│  │  ┌──────────────┐    ┌──────────────────────┐      │    │
│  │  │ Application  │    │ Envoy Proxy (Istio)  │      │    │
│  │  │ Container    │◄───┤ - TLS Management     │      │    │
│  │  │              │    │ - Secret Discovery   │      │    │
│  │  └──────────────┘    └──────────────────────┘      │    │
│  │         │                                           │    │
│  │         ▼                                           │    │
│  │  Secrets Volume Mount                               │    │
│  │  /mnt/secrets-store/                                │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

## Setup Azure Key Vault

### 1. Create Resource Group and Key Vault

```bash
# Variables
RESOURCE_GROUP="rg-resilience4j-demo"
LOCATION="eastus"
KEYVAULT_NAME="kv-resilience4j-$(date +%s)"  # Must be globally unique
AKS_CLUSTER_NAME="aks-resilience4j"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Key Vault
az keyvault create \
  --name $KEYVAULT_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --enable-rbac-authorization false
```

### 2. Store Secrets in Key Vault

```bash
# Application Insights secrets
az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "applicationinsights-connection-string" \
  --value "InstrumentationKey=your-key;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/"

az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "applicationinsights-instrumentation-key" \
  --value "your-instrumentation-key-here"

# Additional secrets (examples)
az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "database-password" \
  --value "your-secure-password"

az keyvault secret set \
  --vault-name $KEYVAULT_NAME \
  --name "api-key" \
  --value "your-api-key"
```

### 3. Verify Secrets

```bash
# List all secrets
az keyvault secret list --vault-name $KEYVAULT_NAME --query "[].name" -o table

# Show secret value (for testing)
az keyvault secret show --vault-name $KEYVAULT_NAME --name "applicationinsights-connection-string" --query "value" -o tsv
```

## Install Secrets Store CSI Driver

### 1. Enable CSI Driver on AKS (if not already enabled)

```bash
# Check if addon is enabled
az aks show --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME --query addonProfiles.azureKeyvaultSecretsProvider

# Enable the addon
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER_NAME \
  --addons azure-keyvault-secrets-provider
```

### 2. Verify CSI Driver Installation

```bash
# Check CSI driver pods
kubectl get pods -n kube-system -l app=secrets-store-csi-driver

# Check provider pods
kubectl get pods -n kube-system -l app=csi-secrets-store-provider-azure

# Expected output:
# secrets-store-csi-driver-xxxxx        3/3     Running
# csi-secrets-store-provider-azure-xxx  1/1     Running
```

## Configure Workload Identity

Workload Identity is the recommended way to authenticate from AKS to Azure Key Vault.

### 1. Enable Workload Identity on AKS

```bash
# Update AKS cluster to enable workload identity
az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER_NAME \
  --enable-workload-identity \
  --enable-oidc-issuer

# Get OIDC issuer URL
OIDC_ISSUER=$(az aks show --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME --query "oidcIssuerProfile.issuerUrl" -o tsv)
echo "OIDC Issuer: $OIDC_ISSUER"
```

### 2. Create Managed Identity

```bash
# Create user-assigned managed identity
IDENTITY_NAME="id-resilience4j-keyvault"
az identity create \
  --resource-group $RESOURCE_GROUP \
  --name $IDENTITY_NAME

# Get identity client ID and principal ID
IDENTITY_CLIENT_ID=$(az identity show --resource-group $RESOURCE_GROUP --name $IDENTITY_NAME --query clientId -o tsv)
IDENTITY_PRINCIPAL_ID=$(az identity show --resource-group $RESOURCE_GROUP --name $IDENTITY_NAME --query principalId -o tsv)

echo "Client ID: $IDENTITY_CLIENT_ID"
echo "Principal ID: $IDENTITY_PRINCIPAL_ID"
```

### 3. Grant Key Vault Access to Managed Identity

```bash
# Grant 'Key Vault Secrets User' role (RBAC)
KEYVAULT_ID=$(az keyvault show --name $KEYVAULT_NAME --query id -o tsv)

az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee $IDENTITY_PRINCIPAL_ID \
  --scope $KEYVAULT_ID

# Alternative: Use access policies (if RBAC not enabled)
az keyvault set-policy \
  --name $KEYVAULT_NAME \
  --secret-permissions get list \
  --object-id $IDENTITY_PRINCIPAL_ID
```

### 4. Create Kubernetes Service Account

```bash
# Create namespace if not exists
kubectl create namespace resilience4j --dry-run=client -o yaml | kubectl apply -f -

# Create service account
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: resilience4j-sa
  namespace: resilience4j
  annotations:
    azure.workload.identity/client-id: $IDENTITY_CLIENT_ID
  labels:
    azure.workload.identity/use: "true"
EOF
```

### 5. Establish Federated Identity Credential

```bash
# Create federated credential
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

az identity federated-credential create \
  --name "resilience4j-federated-credential" \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP \
  --issuer $OIDC_ISSUER \
  --subject system:serviceaccount:resilience4j:resilience4j-sa \
  --audience api://AzureADTokenExchange
```

## Create SecretProviderClass

The `SecretProviderClass` defines which secrets to fetch from Key Vault and how to mount them.

### secretproviderclass.yaml

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: azure-keyvault-resilience4j
  namespace: resilience4j
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    useVMManagedIdentity: "false"
    clientID: "${IDENTITY_CLIENT_ID}"  # Replace with actual client ID
    keyvaultName: "${KEYVAULT_NAME}"   # Replace with your Key Vault name
    cloudName: ""
    objects: |
      array:
        - |
          objectName: applicationinsights-connection-string
          objectType: secret
          objectVersion: ""
        - |
          objectName: applicationinsights-instrumentation-key
          objectType: secret
          objectVersion: ""
        - |
          objectName: database-password
          objectType: secret
          objectVersion: ""
        - |
          objectName: api-key
          objectType: secret
          objectVersion: ""
    tenantId: "${TENANT_ID}"  # Replace with your Azure tenant ID
  secretObjects:
  - secretName: keyvault-secrets
    type: Opaque
    data:
    - objectName: applicationinsights-connection-string
      key: APPLICATIONINSIGHTS_CONNECTION_STRING
    - objectName: applicationinsights-instrumentation-key
      key: APPINSIGHTS_INSTRUMENTATIONKEY
    - objectName: database-password
      key: DATABASE_PASSWORD
    - objectName: api-key
      key: API_KEY
```

### Create the SecretProviderClass

```bash
# Get tenant ID
TENANT_ID=$(az account show --query tenantId -o tsv)

# Replace variables in the file
sed "s/\${IDENTITY_CLIENT_ID}/$IDENTITY_CLIENT_ID/g; s/\${KEYVAULT_NAME}/$KEYVAULT_NAME/g; s/\${TENANT_ID}/$TENANT_ID/g" secretproviderclass.yaml | kubectl apply -f -

# Or create directly with envsubst
export IDENTITY_CLIENT_ID KEYVAULT_NAME TENANT_ID
cat secretproviderclass.yaml | envsubst | kubectl apply -f -

# Verify
kubectl get secretproviderclass -n resilience4j
```

## Update Deployment with Key Vault

### deployment-v2-keyvault.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resilience4j-demo-v2
  namespace: resilience4j
  labels:
    app: resilience4j-demo
    version: v2
spec:
  replicas: 2
  selector:
    matchLabels:
      app: resilience4j-demo
      version: v2
  template:
    metadata:
      labels:
        app: resilience4j-demo
        version: v2
        azure.workload.identity/use: "true"  # Enable workload identity
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8070"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: resilience4j-sa  # Use the service account with workload identity
      containers:
      - name: resilience4j-demo
        image: <your-acr>.azurecr.io/resilience4j-demo:v2
        imagePullPolicy: Always
        ports:
        - name: http
          containerPort: 8070
          protocol: TCP
        
        # Mount secrets from Key Vault as environment variables
        env:
        - name: APPLICATIONINSIGHTS_CONNECTION_STRING
          valueFrom:
            secretKeyRef:
              name: keyvault-secrets
              key: APPLICATIONINSIGHTS_CONNECTION_STRING
        - name: APPINSIGHTS_INSTRUMENTATIONKEY
          valueFrom:
            secretKeyRef:
              name: keyvault-secrets
              key: APPINSIGHTS_INSTRUMENTATIONKEY
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: keyvault-secrets
              key: DATABASE_PASSWORD
              optional: true
        - name: API_KEY
          valueFrom:
            secretKeyRef:
              name: keyvault-secrets
              key: API_KEY
              optional: true
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        
        # Volume mounts for CSI secrets
        volumeMounts:
        - name: secrets-store
          mountPath: "/mnt/secrets-store"
          readOnly: true
        
        # Health probes
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8070
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8070
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        
        startupProbe:
          httpGet:
            path: /actuator/health/startup
            port: 8070
          initialDelaySeconds: 20
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 12
        
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
      
      # Volumes
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: azure-keyvault-resilience4j
---
apiVersion: v1
kind: Service
metadata:
  name: resilience4j-demo-v2
  namespace: resilience4j
  labels:
    app: resilience4j-demo
    version: v2
spec:
  type: ClusterIP
  ports:
  - port: 8070
    targetPort: 8070
    protocol: TCP
    name: http
  selector:
    app: resilience4j-demo
    version: v2
```

### Deploy

```bash
# Apply the deployment
kubectl apply -f deployment-v2-keyvault.yaml

# Wait for rollout
kubectl rollout status deployment/resilience4j-demo-v2 -n resilience4j

# Check pods
kubectl get pods -n resilience4j -l app=resilience4j-demo,version=v2
```

## Istio Integration

### 1. Istio Automatic Sidecar Injection

```bash
# Label namespace for automatic Istio injection
kubectl label namespace resilience4j istio-injection=enabled --overwrite

# Verify
kubectl get namespace resilience4j --show-labels
```

### 2. Istio VirtualService with Key Vault Secrets

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilience4j-vs
  namespace: resilience4j
spec:
  hosts:
  - resilience4j-demo.example.com
  gateways:
  - resilience4j-gateway
  http:
  - match:
    - uri:
        prefix: /api
    route:
    - destination:
        host: resilience4j-demo-v2.resilience4j.svc.cluster.local
        port:
          number: 8070
      weight: 100
    retries:
      attempts: 3
      perTryTimeout: 2s
      retryOn: 5xx,reset,connect-failure,refused-stream
    timeout: 10s
    headers:
      request:
        add:
          x-request-id: "istio-request"
---
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: resilience4j-gateway
  namespace: resilience4j
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - resilience4j-demo.example.com
```

### 3. Istio Authorization with Secrets

```yaml
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: resilience4j-authz
  namespace: resilience4j
spec:
  selector:
    matchLabels:
      app: resilience4j-demo
      version: v2
  action: ALLOW
  rules:
  - from:
    - source:
        principals: ["cluster.local/ns/resilience4j/sa/resilience4j-sa"]
    to:
    - operation:
        methods: ["GET", "POST"]
        paths: ["/api/*"]
```

### 4. Istio mTLS with Key Vault Certificates (Advanced)

For production, you can store TLS certificates in Key Vault:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: azure-tls-certs
  namespace: resilience4j
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    clientID: "${IDENTITY_CLIENT_ID}"
    keyvaultName: "${KEYVAULT_NAME}"
    objects: |
      array:
        - |
          objectName: tls-cert
          objectType: cert
        - |
          objectName: tls-key
          objectType: secret
    tenantId: "${TENANT_ID}"
  secretObjects:
  - secretName: istio-ingressgateway-certs
    type: kubernetes.io/tls
    data:
    - objectName: tls-cert
      key: tls.crt
    - objectName: tls-key
      key: tls.key
```

## Verification

### 1. Check Secret Synchronization

```bash
# Check if secrets are mounted in pod
POD_NAME=$(kubectl get pods -n resilience4j -l app=resilience4j-demo,version=v2 -o jsonpath='{.items[0].metadata.name}')

# List mounted secrets
kubectl exec -it $POD_NAME -n resilience4j -c resilience4j-demo -- ls -la /mnt/secrets-store

# Show secret content (for debugging)
kubectl exec -it $POD_NAME -n resilience4j -c resilience4j-demo -- cat /mnt/secrets-store/applicationinsights-connection-string

# Check Kubernetes secret (created by secretObjects)
kubectl get secret keyvault-secrets -n resilience4j
kubectl describe secret keyvault-secrets -n resilience4j
```

### 2. Verify Environment Variables

```bash
# Check environment variables in pod
kubectl exec -it $POD_NAME -n resilience4j -c resilience4j-demo -- env | grep -E "APPLICATIONINSIGHTS|DATABASE|API_KEY"

# Expected output:
# APPLICATIONINSIGHTS_CONNECTION_STRING=InstrumentationKey=...
# APPINSIGHTS_INSTRUMENTATIONKEY=your-key...
```

### 3. Test Application Insights Integration

```bash
# Port forward to the application
kubectl port-forward -n resilience4j $POD_NAME 8070:8070

# Generate traffic
curl http://localhost:8070/api/demo/circuit-breaker
curl http://localhost:8070/api/demo/retry
curl http://localhost:8070/actuator/health

# Check Application Insights in Azure Portal
# Navigate to: Azure Portal > Application Insights > Live Metrics
# You should see requests, dependencies, and metrics
```

### 4. Check CSI Driver Logs

```bash
# Get CSI driver logs
kubectl logs -n kube-system -l app=secrets-store-csi-driver --tail=50

# Get Azure provider logs
kubectl logs -n kube-system -l app=csi-secrets-store-provider-azure --tail=50
```

### 5. Verify Istio Sidecar Injection

```bash
# Check if Istio sidecar is injected
kubectl get pod $POD_NAME -n resilience4j -o jsonpath='{.spec.containers[*].name}'
# Expected: resilience4j-demo istio-proxy

# Check Istio proxy logs
kubectl logs $POD_NAME -n resilience4j -c istio-proxy --tail=20
```

## Troubleshooting

### Common Issues

#### 1. Secrets Not Mounting

**Symptoms**: Pod shows `FailedMount` events

```bash
# Check events
kubectl describe pod $POD_NAME -n resilience4j

# Common causes:
# - SecretProviderClass not found
# - Workload identity not configured
# - Key Vault access denied
```

**Solutions**:

```bash
# Verify SecretProviderClass exists
kubectl get secretproviderclass -n resilience4j

# Check service account annotations
kubectl get sa resilience4j-sa -n resilience4j -o yaml

# Verify federated credential
az identity federated-credential list \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP
```

#### 2. Access Denied to Key Vault

**Symptoms**: Logs show "Access denied" or "Forbidden"

```bash
# Check Azure provider logs
kubectl logs -n kube-system -l app=csi-secrets-store-provider-azure | grep -i "error\|denied"
```

**Solutions**:

```bash
# Verify role assignment
az role assignment list \
  --assignee $IDENTITY_PRINCIPAL_ID \
  --scope $KEYVAULT_ID

# Re-grant access
az keyvault set-policy \
  --name $KEYVAULT_NAME \
  --secret-permissions get list \
  --object-id $IDENTITY_PRINCIPAL_ID
```

#### 3. Workload Identity Not Working

**Symptoms**: Pod cannot authenticate to Azure

```bash
# Check pod labels
kubectl get pod $POD_NAME -n resilience4j -o jsonpath='{.metadata.labels}'
# Must include: azure.workload.identity/use: "true"

# Check service account
kubectl get sa resilience4j-sa -n resilience4j -o yaml
# Must have annotation: azure.workload.identity/client-id
```

**Solutions**:

```bash
# Restart pod to pick up new configuration
kubectl rollout restart deployment/resilience4j-demo-v2 -n resilience4j

# Verify OIDC issuer
az aks show --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER_NAME --query "oidcIssuerProfile.issuerUrl"
```

#### 4. Secret Rotation Not Working

**Symptoms**: Old secret values still in use after Key Vault update

```bash
# Check rotation settings (default: 2 minutes)
kubectl describe secretproviderclass azure-keyvault-resilience4j -n resilience4j
```

**Solutions**:

```bash
# Force immediate rotation
kubectl delete pod $POD_NAME -n resilience4j

# Or update deployment to trigger rollout
kubectl rollout restart deployment/resilience4j-demo-v2 -n resilience4j
```

### Debug Commands

```bash
# Complete diagnostic check
cat <<'EOF' > keyvault-diagnostics.sh
#!/bin/bash

echo "=== Checking SecretProviderClass ==="
kubectl get secretproviderclass -n resilience4j

echo -e "\n=== Checking Service Account ==="
kubectl get sa resilience4j-sa -n resilience4j -o yaml

echo -e "\n=== Checking Pods ==="
kubectl get pods -n resilience4j -l app=resilience4j-demo

echo -e "\n=== Checking Pod Events ==="
POD_NAME=$(kubectl get pods -n resilience4j -l app=resilience4j-demo,version=v2 -o jsonpath='{.items[0].metadata.name}')
kubectl describe pod $POD_NAME -n resilience4j | grep -A 20 Events

echo -e "\n=== Checking CSI Driver Logs ==="
kubectl logs -n kube-system -l app=csi-secrets-store-provider-azure --tail=30

echo -e "\n=== Checking Secrets ==="
kubectl get secret keyvault-secrets -n resilience4j 2>/dev/null && echo "Secret exists" || echo "Secret not found"

echo -e "\n=== Checking Mounted Secrets ==="
kubectl exec -it $POD_NAME -n resilience4j -c resilience4j-demo -- ls -la /mnt/secrets-store 2>/dev/null || echo "Failed to list secrets"
EOF

chmod +x keyvault-diagnostics.sh
./keyvault-diagnostics.sh
```

## Best Practices

### 1. Secret Rotation

```yaml
# Enable automatic rotation in SecretProviderClass
spec:
  parameters:
    rotation-poll-interval: "120s"  # Check for updates every 2 minutes
```

### 2. RBAC Principle of Least Privilege

```bash
# Only grant necessary permissions
az keyvault set-policy \
  --name $KEYVAULT_NAME \
  --secret-permissions get \
  --object-id $IDENTITY_PRINCIPAL_ID
```

### 3. Use Secret Versions

```yaml
# Pin to specific secret version for consistency
objects: |
  array:
    - |
      objectName: applicationinsights-connection-string
      objectType: secret
      objectVersion: "abc123def456"  # Specific version
```

### 4. Monitor Secret Access

```bash
# Enable Key Vault diagnostics
az monitor diagnostic-settings create \
  --name kv-diagnostics \
  --resource $KEYVAULT_ID \
  --logs '[{"category": "AuditEvent", "enabled": true}]' \
  --workspace <log-analytics-workspace-id>
```

### 5. Use Istio for Traffic Management

```yaml
# Circuit breaker for Key Vault dependencies
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: resilience4j-dr
spec:
  host: resilience4j-demo-v2.resilience4j.svc.cluster.local
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        http2MaxRequests: 100
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
```

## Cleanup

```bash
# Delete deployment
kubectl delete -f deployment-v2-keyvault.yaml

# Delete SecretProviderClass
kubectl delete secretproviderclass azure-keyvault-resilience4j -n resilience4j

# Delete service account
kubectl delete sa resilience4j-sa -n resilience4j

# Delete federated credential
az identity federated-credential delete \
  --name "resilience4j-federated-credential" \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP

# Delete managed identity
az identity delete \
  --name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP

# Delete Key Vault (optional)
az keyvault delete --name $KEYVAULT_NAME --resource-group $RESOURCE_GROUP
az keyvault purge --name $KEYVAULT_NAME  # Permanently delete
```

## Summary

This integration provides:
- ✅ Centralized secret management in Azure Key Vault
- ✅ Secure access via Azure Workload Identity
- ✅ Automatic secret synchronization to pods
- ✅ No code changes required
- ✅ Istio service mesh integration
- ✅ Secret rotation support
- ✅ Production-ready security

Secrets are now managed in Azure Key Vault, accessed securely via Workload Identity, and automatically synced to your Kubernetes pods with Istio handling traffic management and observability.
