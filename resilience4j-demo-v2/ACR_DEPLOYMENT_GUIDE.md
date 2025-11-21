# Azure Container Registry (ACR) Deployment Guide

This guide covers deploying resilience4j-demo v1 and v2 to Azure Container Registry and AKS.

## Prerequisites

```bash
# Install Azure CLI
az --version

# Login to Azure
az login

# Set subscription
az account set --subscription <subscription-id>
```

## 1. Create Azure Container Registry

```bash
# Variables
ACR_NAME="myresilienceacr"
RESOURCE_GROUP="rg-resilience-demo"
LOCATION="southeastasia"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create ACR
az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Standard \
  --location $LOCATION

# Enable admin account (for development only)
az acr update --name $ACR_NAME --admin-enabled true

# Get ACR login credentials
az acr login --name $ACR_NAME
```

## 2. Build and Push V1 to ACR

```bash
# Navigate to v1 folder
cd resilience4j-demo

# Build Docker image
docker build -t resilience4j-demo:v1 .

# Tag for ACR
docker tag resilience4j-demo:v1 ${ACR_NAME}.azurecr.io/resilience4j-demo:v1

# Push to ACR
docker push ${ACR_NAME}.azurecr.io/resilience4j-demo:v1

# Verify
az acr repository list --name $ACR_NAME --output table
az acr repository show-tags --name $ACR_NAME --repository resilience4j-demo --output table
```

## 3. Build and Push V2 to ACR

```bash
# Navigate to v2 folder
cd ../resilience4j-demo-v2

# Build Docker image
docker build -t resilience4j-demo:v2 .

# Tag for ACR
docker tag resilience4j-demo:v2 ${ACR_NAME}.azurecr.io/resilience4j-demo:v2

# Push to ACR
docker push ${ACR_NAME}.azurecr.io/resilience4j-demo:v2

# Verify both versions
az acr repository show-tags --name $ACR_NAME --repository resilience4j-demo --output table
```

## 4. Create AKS Cluster with Istio

```bash
AKS_NAME="aks-resilience-demo"

# Create AKS cluster
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_NAME \
  --node-count 3 \
  --node-vm-size Standard_D4s_v3 \
  --enable-addons monitoring \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 10 \
  --attach-acr $ACR_NAME \
  --network-plugin azure \
  --enable-managed-identity \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_NAME

# Verify connection
kubectl get nodes

# Install Istio (using istioctl)
istioctl install --set profile=demo -y

# Enable Istio injection for default namespace
kubectl label namespace default istio-injection=enabled
```

## 5. Deploy V1 to AKS

```bash
cd resilience4j-demo/k8s

# Update deployment.yaml with ACR image
export ACR_NAME="myresilienceacr"
envsubst < deployment.yaml | kubectl apply -f -

# Wait for pods to be ready
kubectl get pods -w

# Verify deployment
kubectl get pods
kubectl get svc
```

## 6. Deploy V2 for Canary Release

```bash
cd ../../resilience4j-demo-v2/k8s

# Deploy v2
envsubst < deployment-v2.yaml | kubectl apply -f -

# Apply HPA
kubectl apply -f hpa.yaml

# Apply Istio configurations
kubectl apply -f istio-virtualservice.yaml
kubectl apply -f istio-resilience.yaml

# Verify both versions running
kubectl get pods -l app=resilience4j-demo
kubectl get hpa
```

## 7. Test Canary Deployment

```bash
# Get Istio Ingress Gateway IP
export INGRESS_HOST=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Test normal requests (90% v1, 10% v2)
for i in {1..100}; do
  curl -s http://$INGRESS_HOST/api/circuit-breaker/demo | jq -r '.version'
done | sort | uniq -c

# Test canary header (100% v2)
curl -H "canary: true" http://$INGRESS_HOST/api/circuit-breaker/demo
```

## 8. Monitoring

```bash
# View logs
kubectl logs -l app=resilience4j-demo -l version=v2 --tail=50 -f

# Check metrics
kubectl port-forward svc/resilience4j-demo-v2 8081:8081
curl http://localhost:8081/actuator/prometheus

# Check health
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness

# View HPA status
kubectl get hpa resilience4j-demo-v2-hpa -w
```

## 9. Blue-Green Deployment

To switch from blue (v1) to green (v2):

```bash
# Edit istio-virtualservice.yaml and change weight to 100 for v2
kubectl apply -f istio-virtualservice.yaml

# Or use kubectl patch
kubectl patch virtualservice resilience4j-demo --type merge -p '
{
  "spec": {
    "http": [{
      "route": [{
        "destination": {
          "host": "resilience4j-demo",
          "subset": "v2"
        },
        "weight": 100
      }]
    }]
  }
}'
```

## 10. Rollback

```bash
# Rollback to v1
kubectl rollout undo deployment/resilience4j-demo-v2

# Or update VirtualService to route to v1
kubectl patch virtualservice resilience4j-demo --type merge -p '
{
  "spec": {
    "http": [{
      "route": [{
        "destination": {
          "host": "resilience4j-demo",
          "subset": "v1"
        },
        "weight": 100
      }]
    }]
  }
}'
```

## 11. Cleanup

```bash
# Delete Kubernetes resources
kubectl delete -f k8s/

# Delete AKS cluster
az aks delete --resource-group $RESOURCE_GROUP --name $AKS_NAME --yes --no-wait

# Delete ACR
az acr delete --resource-group $RESOURCE_GROUP --name $ACR_NAME --yes

# Delete resource group
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

## Useful Commands

```bash
# Scale deployment manually
kubectl scale deployment resilience4j-demo-v2 --replicas=5

# Update image
kubectl set image deployment/resilience4j-demo-v2 app=${ACR_NAME}.azurecr.io/resilience4j-demo:v2-new

# View deployment history
kubectl rollout history deployment/resilience4j-demo-v2

# Describe pod
kubectl describe pod <pod-name>

# Execute into pod
kubectl exec -it <pod-name> -- /bin/sh

# View events
kubectl get events --sort-by='.lastTimestamp'
```

## Troubleshooting

### Image Pull Errors

```bash
# Verify ACR integration
az aks check-acr --name $AKS_NAME --resource-group $RESOURCE_GROUP --acr ${ACR_NAME}.azurecr.io

# Create image pull secret manually if needed
kubectl create secret docker-registry acr-secret \
  --docker-server=${ACR_NAME}.azurecr.io \
  --docker-username=$(az acr credential show --name $ACR_NAME --query username -o tsv) \
  --docker-password=$(az acr credential show --name $ACR_NAME --query passwords[0].value -o tsv)
```

### Pod Not Ready

```bash
# Check readiness probe
kubectl describe pod <pod-name>
kubectl logs <pod-name>

# Test health endpoint
kubectl port-forward <pod-name> 8081:8081
curl http://localhost:8081/actuator/health/readiness
```
