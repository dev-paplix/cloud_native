# Deployment Checklist - Resilience4j Demo V1 & V2

Use this checklist to track your deployment progress.

## ✅ Prerequisites Setup

- [ ] Azure CLI installed and configured (`az --version`)
- [ ] Azure subscription selected (`az account set --subscription <id>`)
- [ ] kubectl installed (`kubectl version`)
- [ ] Istio installed (`istioctl version`)
- [ ] Docker installed and running (`docker --version`)
- [ ] Maven 3.9+ installed (`mvn --version`)
- [ ] Java 17 installed (`java -version`)
- [ ] Git repository updated (`git status`)

---

## 📦 V1 - Baseline Deployment

### Build Phase
- [ ] Navigate to `resilience4j-demo` folder
- [ ] Run `mvn clean package -DskipTests`
- [ ] Verify JAR created in `target/` folder
- [ ] Run `mvn spring-boot:run` to test locally (optional)
- [ ] Test endpoints: `curl http://localhost:8080/actuator/health`

### Docker Phase
- [ ] Build Docker image: `docker build -t resilience4j-demo:v1 .`
- [ ] Test Docker locally: `docker run -p 8080:8080 resilience4j-demo:v1`
- [ ] Verify container health: `curl http://localhost:8080/actuator/health`
- [ ] Stop local container: `docker stop <container-id>`

### Azure ACR Phase
- [ ] Set variables:
  ```bash
  export ACR_NAME="myresilienceacr"
  export RESOURCE_GROUP="rg-resilience-demo"
  export LOCATION="southeastasia"
  ```
- [ ] Create resource group: `az group create --name $RESOURCE_GROUP --location $LOCATION`
- [ ] Create ACR: `az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Standard`
- [ ] Login to ACR: `az acr login --name $ACR_NAME`
- [ ] Push v1 image:
  ```bash
  az acr build --registry $ACR_NAME --image resilience4j-demo:v1 .
  ```
- [ ] Verify image: `az acr repository show-tags --name $ACR_NAME --repository resilience4j-demo`

### AKS Deployment Phase
- [ ] Create AKS cluster:
  ```bash
  az aks create \
    --resource-group $RESOURCE_GROUP \
    --name aks-resilience-demo \
    --node-count 3 \
    --node-vm-size Standard_D4s_v3 \
    --enable-cluster-autoscaler \
    --min-count 3 \
    --max-count 10 \
    --attach-acr $ACR_NAME \
    --generate-ssh-keys
  ```
- [ ] Get AKS credentials: `az aks get-credentials --resource-group $RESOURCE_GROUP --name aks-resilience-demo`
- [ ] Verify connection: `kubectl get nodes`
- [ ] Install Istio: `istioctl install --set profile=demo -y`
- [ ] Enable Istio injection: `kubectl label namespace default istio-injection=enabled`
- [ ] Update v1 deployment.yaml with ACR image path
- [ ] Deploy v1: `kubectl apply -f k8s/deployment.yaml`
- [ ] Verify pods: `kubectl get pods -l app=resilience4j-demo`
- [ ] Verify service: `kubectl get svc resilience4j-demo`
- [ ] Test health: `kubectl port-forward svc/resilience4j-demo 8080:8080`
- [ ] Open browser: `http://localhost:8080/actuator/health`

### V1 Verification
- [ ] All v1 pods running (check `kubectl get pods`)
- [ ] Health endpoint responds
- [ ] Circuit breaker endpoint works: `curl http://localhost:8080/api/circuit-breaker/demo`
- [ ] Metrics endpoint works: `curl http://localhost:8080/actuator/prometheus`

---

## 🚀 V2 - Production Deployment

### Build Phase
- [ ] Navigate to `resilience4j-demo-v2` folder
- [ ] Review configuration in `application.yml`
- [ ] Run `mvn clean package -DskipTests`
- [ ] Verify JAR created in `target/` folder
- [ ] Run `mvn spring-boot:run` to test locally (optional)
- [ ] Test v2 endpoints:
  - [ ] App: `curl http://localhost:8080/api/circuit-breaker/demo`
  - [ ] Liveness: `curl http://localhost:8081/actuator/health/liveness`
  - [ ] Readiness: `curl http://localhost:8081/actuator/health/readiness`
  - [ ] Metrics: `curl http://localhost:8081/actuator/prometheus`

### Docker Phase
- [ ] Build Docker image: `docker build -t resilience4j-demo:v2 .`
- [ ] Test Docker locally: `docker run -p 8080:8080 -p 8081:8081 resilience4j-demo:v2`
- [ ] Verify health probes:
  - [ ] `curl http://localhost:8081/actuator/health/liveness`
  - [ ] `curl http://localhost:8081/actuator/health/readiness`
- [ ] Stop local container

### Azure ACR Phase
- [ ] Push v2 image:
  ```bash
  az acr build --registry $ACR_NAME --image resilience4j-demo:v2 .
  ```
- [ ] Verify both versions: `az acr repository show-tags --name $ACR_NAME --repository resilience4j-demo`
- [ ] Expected output: `v1`, `v2`

### AKS Deployment Phase
- [ ] Update `deployment-v2.yaml` with ACR image path
- [ ] Deploy v2: `kubectl apply -f k8s/deployment-v2.yaml`
- [ ] Deploy HPA: `kubectl apply -f k8s/hpa.yaml`
- [ ] Deploy Istio VirtualService: `kubectl apply -f k8s/istio-virtualservice.yaml`
- [ ] Deploy Istio Resilience: `kubectl apply -f k8s/istio-resilience.yaml`
- [ ] Verify deployments:
  - [ ] V1 pods: `kubectl get pods -l app=resilience4j-demo,version=v1`
  - [ ] V2 pods: `kubectl get pods -l app=resilience4j-demo,version=v2`
  - [ ] HPA: `kubectl get hpa`
  - [ ] VirtualService: `kubectl get virtualservice`
  - [ ] DestinationRule: `kubectl get destinationrule`

### V2 Verification
- [ ] All v2 pods in Running state
- [ ] HPA created and monitoring (min: 3, max: 10)
- [ ] Health probes working (check pod events: `kubectl describe pod <v2-pod>`)
- [ ] Graceful shutdown configured (check deployment spec)
- [ ] QoS class is Guaranteed: `kubectl get pod <v2-pod> -o jsonpath='{.status.qosClass}'`

---

## 🎯 Canary Release Testing

### Initial State (90% v1, 10% v2)
- [ ] Get Istio Ingress IP:
  ```bash
  export INGRESS_HOST=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
  echo $INGRESS_HOST
  ```
- [ ] Test traffic distribution (100 requests):
  ```bash
  for i in {1..100}; do
    curl -s http://$INGRESS_HOST/api/circuit-breaker/demo | jq -r '.version'
  done | sort | uniq -c
  ```
- [ ] Expected: ~90 v1, ~10 v2
- [ ] Test canary header (100% v2):
  ```bash
  curl -H "canary: true" http://$INGRESS_HOST/api/circuit-breaker/demo
  ```
- [ ] Expected: Always v2

### Increase to 50% Canary
- [ ] Update VirtualService weights (50/50):
  ```bash
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
  ```
- [ ] Test traffic distribution again
- [ ] Expected: ~50 v1, ~50 v2
- [ ] Monitor for 30 minutes:
  - [ ] Error rate: `kubectl logs -l app=resilience4j-demo,version=v2 | grep ERROR`
  - [ ] Metrics: `kubectl port-forward svc/resilience4j-demo-v2 8081:8081`
  - [ ] Prometheus: `curl http://localhost:8081/actuator/prometheus | grep http_server_requests`

### Full Rollout to 100% v2
- [ ] Update VirtualService weights (100% v2):
  ```bash
  kubectl patch virtualservice resilience4j-demo --type merge -p '
  {
    "spec": {
      "http": [{
        "route": [{"destination": {"host": "resilience4j-demo", "subset": "v2"}, "weight": 100}]
      }]
    }
  }'
  ```
- [ ] Test traffic: All requests should go to v2
- [ ] Monitor for 30 minutes
- [ ] If stable, scale down v1: `kubectl scale deployment resilience4j-demo --replicas=0`

---

## 📊 HPA Autoscaling Testing

- [ ] Get HPA status: `kubectl get hpa resilience4j-demo-v2-hpa`
- [ ] Generate load:
  ```bash
  kubectl run load-generator --image=busybox --restart=Never -- \
    /bin/sh -c "while true; do wget -q -O- http://resilience4j-demo-v2:8080/api/circuit-breaker/demo; done"
  ```
- [ ] Watch HPA scale up:
  ```bash
  kubectl get hpa resilience4j-demo-v2-hpa -w
  ```
- [ ] Expected: Replicas increase from 3 → 4 → 5 → ... (up to 10)
- [ ] Check pod metrics: `kubectl top pods -l app=resilience4j-demo,version=v2`
- [ ] Stop load: `kubectl delete pod load-generator`
- [ ] Watch HPA scale down (5min stabilization):
  ```bash
  kubectl get hpa resilience4j-demo-v2-hpa -w
  ```
- [ ] Expected: Gradual scale down back to 3 replicas

---

## 🔒 Azure API Management Setup (Optional)

- [ ] Create APIM instance:
  ```bash
  az apim create \
    --resource-group $RESOURCE_GROUP \
    --name apim-resilience \
    --publisher-email admin@example.com \
    --publisher-name "Cloud Native Demo" \
    --sku-name Developer
  ```
- [ ] Wait for provisioning (~30 minutes)
- [ ] Get AKS ingress URL
- [ ] Import API from OpenAPI spec
- [ ] Apply policy from `apim-policy.xml`
- [ ] Test rate limiting:
  ```bash
  for i in {1..15}; do
    curl -s -o /dev/null -w "%{http_code}\n" \
      "https://apim-resilience.azure-api.net/api/circuit-breaker/demo"
  done
  ```
- [ ] Expected: First 10 succeed (200), next 5 rate-limited (429)
- [ ] Test caching: Verify X-Response-Time decreases on repeated requests

---

## 🧪 Istio Resilience Testing

### Circuit Breaker Test
- [ ] Trigger failures:
  ```bash
  for i in {1..10}; do
    curl http://$INGRESS_HOST/api/circuit-breaker/fail
  done
  ```
- [ ] Check Istio proxy logs: `kubectl logs <v2-pod> -c istio-proxy | grep outlier`
- [ ] Expected: Pod ejected after 5 consecutive errors
- [ ] Verify traffic routing to healthy pods

### Retry Test
- [ ] Trigger transient failures
- [ ] Check Istio retry behavior in proxy logs
- [ ] Expected: 3 retry attempts with 3s timeout

### Fault Injection Test (Optional)
- [ ] Edit `istio-resilience.yaml`, enable fault injection:
  ```yaml
  fault:
    delay:
      percentage:
        value: 50
      fixedDelay: 5s
  ```
- [ ] Apply: `kubectl apply -f k8s/istio-resilience.yaml`
- [ ] Test latency: `curl -w "@curl-format.txt" http://$INGRESS_HOST/api/circuit-breaker/demo`
- [ ] Expected: ~50% requests have 5s delay
- [ ] Disable fault injection after testing

---

## 📈 Monitoring & Observability

- [ ] Port forward Prometheus (if installed):
  ```bash
  kubectl port-forward -n istio-system svc/prometheus 9090:9090
  ```
- [ ] Open Prometheus UI: `http://localhost:9090`
- [ ] Query metrics:
  - [ ] `http_server_requests_seconds_count{app="resilience4j-demo"}`
  - [ ] `resilience4j_circuitbreaker_state{app="resilience4j-demo"}`
  - [ ] `container_memory_usage_bytes{pod=~"resilience4j-demo-v2.*"}`
  
- [ ] Port forward Grafana (if installed):
  ```bash
  kubectl port-forward -n istio-system svc/grafana 3000:3000
  ```
- [ ] Open Grafana UI: `http://localhost:3000`
- [ ] Import Istio dashboards
- [ ] Verify metrics visualization

- [ ] View application logs:
  ```bash
  kubectl logs -l app=resilience4j-demo,version=v2 --tail=50 -f
  ```
- [ ] Expected: JSON formatted logs with trace IDs

---

## 🎓 Teaching Preparation

- [ ] Review `TEACHING_GUIDE.md` for all 8 topics
- [ ] Prepare demo environment (AKS + Istio + APIM ready)
- [ ] Create presentation slides (use content from TEACHING_GUIDE.md)
- [ ] Set up monitoring dashboards for classroom display
- [ ] Prepare hands-on exercise environment for students
- [ ] Test all code examples from TEACHING_GUIDE.md
- [ ] Create student handouts with key commands
- [ ] Prepare quiz/assessment questions for each topic

---

## 🧹 Cleanup (After Demo/Course)

- [ ] Delete load generator: `kubectl delete pod load-generator`
- [ ] Delete Kubernetes resources:
  ```bash
  kubectl delete -f k8s/
  ```
- [ ] Delete AKS cluster:
  ```bash
  az aks delete --resource-group $RESOURCE_GROUP --name aks-resilience-demo --yes
  ```
- [ ] Delete APIM (if created):
  ```bash
  az apim delete --resource-group $RESOURCE_GROUP --name apim-resilience --yes
  ```
- [ ] Delete ACR:
  ```bash
  az acr delete --resource-group $RESOURCE_GROUP --name $ACR_NAME --yes
  ```
- [ ] Delete resource group:
  ```bash
  az group delete --name $RESOURCE_GROUP --yes
  ```
- [ ] Verify all resources deleted: `az resource list --resource-group $RESOURCE_GROUP`

---

## ✅ Success Criteria

### V1 Deployment Success:
- ✅ V1 pods running in AKS
- ✅ Health endpoint accessible
- ✅ Circuit breaker working
- ✅ Metrics available

### V2 Deployment Success:
- ✅ V2 pods running with QoS Guaranteed
- ✅ Liveness and readiness probes passing
- ✅ HPA configured and monitoring
- ✅ Graceful shutdown working (test with `kubectl rollout restart`)
- ✅ Separate management port (8081) accessible

### Canary Release Success:
- ✅ Traffic distribution matches weights (90/10, 50/50, 100/0)
- ✅ Header-based routing works (canary: true → 100% v2)
- ✅ No errors during traffic shift
- ✅ Metrics show successful requests

### Istio Resilience Success:
- ✅ Circuit breaker ejects unhealthy pods
- ✅ Retries working (3 attempts)
- ✅ Connection pooling enforced
- ✅ Fault injection works (if tested)

### APIM Success (Optional):
- ✅ Rate limiting enforced (100 calls/min)
- ✅ Caching working (5min TTL)
- ✅ Custom headers present
- ✅ Error responses structured

### HPA Success:
- ✅ Scales up under load (3 → 10 replicas)
- ✅ Scales down when idle (back to 3)
- ✅ Metrics-based scaling works (CPU, memory, RPS)
- ✅ Stabilization windows prevent flapping

---

## 📝 Notes

**Date Started:** _______________

**Completion Date:** _______________

**Issues Encountered:**
- 
- 
- 

**Resolutions:**
- 
- 
- 

**Students/Participants:**
- Count: _______________
- Feedback: _______________

---

*Good luck with your deployment and teaching! 🚀*
