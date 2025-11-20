# Kubernetes Deployment with Application Insights and Observability

This directory contains Kubernetes manifests for deploying microservices with full observability.

## Files

- `deployment-with-observability.yaml` - Complete deployment with App Insights
- `configmap-observability.yaml` - Configuration for observability settings
- `service-monitor.yaml` - Prometheus ServiceMonitor for metrics scraping
- `ingress-with-tracing.yaml` - Ingress with distributed tracing

## Prerequisites

- Kubernetes cluster (AKS recommended)
- Application Insights instance
- Prometheus Operator (optional, for ServiceMonitor)

## Quick Deploy

```bash
# Create namespace
kubectl create namespace observability-demo

# Create secret with App Insights connection string
kubectl create secret generic appinsights-secret \
  --from-literal=connection-string='InstrumentationKey=xxx...' \
  -n observability-demo

# Apply manifests
kubectl apply -f configmap-observability.yaml
kubectl apply -f deployment-with-observability.yaml
kubectl apply -f service-monitor.yaml
kubectl apply -f ingress-with-tracing.yaml
```

## Verify

```bash
# Check pods
kubectl get pods -n observability-demo

# Check logs
kubectl logs -f deployment/resilience4j-demo -n observability-demo

# Check metrics endpoint
kubectl port-forward deployment/resilience4j-demo 8080:8080 -n observability-demo
curl http://localhost:8080/actuator/prometheus
```
