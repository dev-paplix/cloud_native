# Docker and CI/CD Guide

## 📦 Docker Setup

### Building the Docker Image

```powershell
# Navigate to project directory
cd resilience4j-demo

# Build the Docker image
docker build -t resilience4j-demo:latest .

# Build with specific tag
docker build -t resilience4j-demo:1.0.0 .
```

### Running the Docker Container

```powershell
# Run container
docker run -p 8080:8080 resilience4j-demo:latest

# Run with environment variables
docker run -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=production `
  -e JAVA_OPTS="-Xmx512m" `
  resilience4j-demo:latest

# Run in detached mode
docker run -d -p 8080:8080 --name resilience4j resilience4j-demo:latest

# View logs
docker logs -f resilience4j

# Stop container
docker stop resilience4j

# Remove container
docker rm resilience4j
```

### Using Docker Compose

```powershell
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Rebuild and restart
docker-compose up -d --build
```

### Testing the Dockerized Application

```powershell
# Wait for application to start (about 30 seconds)
Start-Sleep -Seconds 30

# Test health endpoint
curl http://localhost:8080/actuator/health

# Test retry endpoint
curl http://localhost:8080/api/retry/demo

# Test circuit breaker
curl http://localhost:8080/api/circuit-breaker/demo
```

---

## 🔄 CI/CD Pipelines

### GitHub Actions

The project includes a complete GitHub Actions workflow (`.github/workflows/ci-cd.yml`) with the following jobs:

#### Pipeline Stages

1. **Build and Test**
   - Checks out code
   - Sets up JDK 23
   - Builds with Maven
   - Runs unit tests
   - Uploads build artifacts

2. **Build Docker Image**
   - Builds multi-platform Docker image (amd64, arm64)
   - Pushes to GitHub Container Registry
   - Uses layer caching for faster builds
   - Tags: latest, branch name, commit SHA, semantic versions

3. **Security Scan**
   - Scans Docker image with Trivy
   - Uploads results to GitHub Security
   - Checks for HIGH and CRITICAL vulnerabilities

4. **Deploy to Development**
   - Triggers on `develop` branch
   - Deploys to development Kubernetes cluster
   - Manual approval required

5. **Deploy to Production**
   - Triggers on version tags (v1.0.0, etc.)
   - Requires security scan to pass
   - Manual approval required
   - Deploys to production Kubernetes cluster

#### Triggering the Pipeline

```bash
# Push to main branch
git push origin main

# Push to develop branch
git push origin develop

# Create and push a version tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

#### Secrets Required

Configure these secrets in GitHub Settings > Secrets:

- `GITHUB_TOKEN` (automatically provided)
- `KUBE_CONFIG` (optional, for Kubernetes deployment)

---

### GitLab CI/CD

The project includes a GitLab CI/CD pipeline (`.gitlab-ci.yml`) with the following stages:

#### Pipeline Stages

1. **Build** - Compile the Java application
2. **Test** - Run unit tests with coverage reporting
3. **Docker** - Build and push Docker image
4. **Deploy** - Deploy to development/production

#### Variables to Configure

In GitLab Settings > CI/CD > Variables:

- `CI_REGISTRY` (GitLab Container Registry URL)
- `CI_REGISTRY_USER` (registry username)
- `CI_REGISTRY_PASSWORD` (registry password)
- `KUBE_CONFIG` (Kubernetes config for deployment)

#### Running the Pipeline

```bash
# Push to trigger pipeline
git push origin main

# Create tag for production deployment
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

---

## ☸️ Kubernetes Deployment

### Prerequisites

```powershell
# Install kubectl
choco install kubernetes-cli

# Verify installation
kubectl version --client
```

### Deploying to Kubernetes

```powershell
# Create namespace
kubectl create namespace resilience4j-demo

# Apply deployment
kubectl apply -f k8s/deployment.yaml

# Check deployment status
kubectl get deployments -n resilience4j-demo

# Check pods
kubectl get pods -n resilience4j-demo

# View logs
kubectl logs -f deployment/resilience4j-demo -n resilience4j-demo

# Check service
kubectl get svc -n resilience4j-demo

# Check ingress
kubectl get ingress -n resilience4j-demo
```

### Port Forwarding (for local testing)

```powershell
# Forward port 8080 to local machine
kubectl port-forward -n resilience4j-demo deployment/resilience4j-demo 8080:8080

# Test application
curl http://localhost:8080/actuator/health
```

### Scaling

```powershell
# Manual scaling
kubectl scale deployment resilience4j-demo --replicas=5 -n resilience4j-demo

# Check HPA (Horizontal Pod Autoscaler)
kubectl get hpa -n resilience4j-demo

# Describe HPA for details
kubectl describe hpa resilience4j-demo -n resilience4j-demo
```

### Monitoring Health Probes

```powershell
# Describe pod to see probe status
kubectl describe pod <pod-name> -n resilience4j-demo

# Watch pod status
kubectl get pods -n resilience4j-demo -w
```

### Updating the Deployment

```powershell
# Update image to new version
kubectl set image deployment/resilience4j-demo app=ghcr.io/your-org/resilience4j-demo:v1.0.1 -n resilience4j-demo

# Check rollout status
kubectl rollout status deployment/resilience4j-demo -n resilience4j-demo

# Rollback if needed
kubectl rollout undo deployment/resilience4j-demo -n resilience4j-demo

# View rollout history
kubectl rollout history deployment/resilience4j-demo -n resilience4j-demo
```

---

## 🔍 Troubleshooting

### Docker Build Issues

**Problem: Build fails with "Unsupported class file major version"**
```powershell
# Solution: Ensure using correct Java version in Dockerfile
# Check Dockerfile uses JDK 23
docker build --no-cache -t resilience4j-demo:latest .
```

**Problem: Image size too large**
```powershell
# Solution: Multi-stage build already optimized
# Check image size
docker images resilience4j-demo:latest

# Analyze layers
docker history resilience4j-demo:latest
```

### Container Runtime Issues

**Problem: Container keeps restarting**
```powershell
# Check logs
docker logs <container-id>

# Check if port is already in use
netstat -ano | findstr :8080

# Run with more memory
docker run -p 8080:8080 -m 1g resilience4j-demo:latest
```

**Problem: Health check failing**
```powershell
# Exec into container
docker exec -it <container-id> sh

# Check if app is running
wget -O- http://localhost:8080/actuator/health

# Check Java process
ps aux | grep java
```

### Kubernetes Deployment Issues

**Problem: Pods stuck in Pending state**
```powershell
# Check pod events
kubectl describe pod <pod-name> -n resilience4j-demo

# Check node resources
kubectl top nodes

# Check persistent volume claims
kubectl get pvc -n resilience4j-demo
```

**Problem: ImagePullBackOff**
```powershell
# Check image exists
docker pull ghcr.io/your-org/resilience4j-demo:latest

# Check image pull secrets
kubectl get secrets -n resilience4j-demo

# Describe pod for detailed error
kubectl describe pod <pod-name> -n resilience4j-demo
```

**Problem: Liveness/Readiness probe failing**
```powershell
# Check application logs
kubectl logs <pod-name> -n resilience4j-demo

# Test probe endpoint manually
kubectl exec -it <pod-name> -n resilience4j-demo -- wget -O- http://localhost:8080/actuator/health/liveness

# Adjust probe timing in deployment.yaml if needed
```

---

## 📊 Monitoring and Metrics

### Prometheus Metrics

```powershell
# Access metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Key metrics to monitor:
# - resilience4j_circuitbreaker_state
# - resilience4j_ratelimiter_available_permissions
# - resilience4j_retry_calls
# - jvm_memory_used_bytes
# - http_server_requests_seconds
```

### Application Insights

```powershell
# View all actuator endpoints
curl http://localhost:8080/actuator

# Health check
curl http://localhost:8080/actuator/health

# Circuit breakers status
curl http://localhost:8080/actuator/circuitbreakers

# Rate limiters status
curl http://localhost:8080/actuator/ratelimiters

# Metrics
curl http://localhost:8080/actuator/metrics
```

---

## 🎯 Best Practices

### Docker Best Practices

1. **Multi-stage builds** ✅ - Reduces final image size
2. **Non-root user** ✅ - Improves security
3. **Health checks** ✅ - Enables container orchestration
4. **Layer caching** ✅ - Faster builds
5. **Minimal base image** ✅ - Alpine Linux for smaller size
6. **.dockerignore** ✅ - Excludes unnecessary files

### CI/CD Best Practices

1. **Automated testing** - Run tests on every commit
2. **Security scanning** - Scan images for vulnerabilities
3. **Multi-environment** - Dev, staging, production
4. **Manual approval** - For production deployments
5. **Rollback capability** - Quick recovery from issues
6. **Versioning** - Semantic versioning for releases

### Kubernetes Best Practices

1. **Resource limits** ✅ - Prevent resource exhaustion
2. **Health probes** ✅ - Liveness, readiness, startup
3. **Horizontal scaling** ✅ - HPA for automatic scaling
4. **Rolling updates** ✅ - Zero-downtime deployments
5. **Security context** ✅ - Run as non-root
6. **Namespace isolation** ✅ - Separate environments

---

## 📝 Quick Reference

### Docker Commands
```powershell
# Build
docker build -t resilience4j-demo:latest .

# Run
docker run -p 8080:8080 resilience4j-demo:latest

# Compose
docker-compose up -d

# Clean up
docker-compose down
docker system prune -a
```

### Kubernetes Commands
```powershell
# Deploy
kubectl apply -f k8s/deployment.yaml

# Status
kubectl get all -n resilience4j-demo

# Logs
kubectl logs -f deployment/resilience4j-demo -n resilience4j-demo

# Scale
kubectl scale deployment resilience4j-demo --replicas=5 -n resilience4j-demo
```

### Testing Commands
```powershell
# Health
curl http://localhost:8080/actuator/health

# Retry
curl http://localhost:8080/api/retry/demo

# Circuit Breaker
curl http://localhost:8080/api/circuit-breaker/demo

# Rate Limiter
curl http://localhost:8080/api/rate-limiter/burst-test?requestCount=15
```

---

**Ready for Production! 🚀**
