# Azure Cloud Shell Setup Guide for Day 3 Labs

## Overview

**YES!** All Day 3 labs can be completed entirely in **Azure Cloud Shell** without installing anything locally. Azure Cloud Shell provides a pre-configured browser-based shell with all required tools.

---

## What's Pre-installed in Azure Cloud Shell?

✅ **Azure CLI** - Latest version  
✅ **kubectl** - Kubernetes command-line tool  
✅ **Helm 3** - Package manager for Kubernetes  
✅ **Git** - Version control  
✅ **Docker** - Container runtime (Bash only)  
✅ **Maven** - Java build tool  
✅ **curl, jq** - API testing tools  
✅ **Code editor** - Built-in Monaco editor  
✅ **Persistent storage** - 5GB Azure Files share  

---

## Getting Started with Azure Cloud Shell

### Step 1: Open Azure Cloud Shell

1. Go to [https://portal.azure.com](https://portal.azure.com)
2. Click the **Cloud Shell** icon (>_) in top right
3. Choose **Bash** (recommended) or **PowerShell**
4. If first time, create storage account (free, one-time setup)

**Or direct link:** [https://shell.azure.com](https://shell.azure.com)

### Step 2: Verify Tools

```bash
# Check Azure CLI
az --version

# Check kubectl
kubectl version --client

# Check Helm
helm version

# Check Maven (for building Java apps)
mvn --version

# Check istioctl (will install in lab)
# Not pre-installed, but we'll download it
```

### Step 3: Set Up Your Environment

```bash
# Set default subscription
az account set --subscription "Your Subscription Name"

# Set default resource group and location
az configure --defaults group=rg-cloud-native location=eastus

# Verify
az account show
```

---

## Lab-Specific Setup

### Event Hub Lab (02-EVENT_HUB_LAB.md)

**Everything works in Cloud Shell!**

```bash
# Clone course repository to Cloud Shell storage
git clone https://github.com/your-repo/cloud_native.git
cd cloud_native/resilience4j-demo

# All Azure CLI commands work natively
az eventhubs namespace create --name eh-orders-demo --sku Standard

# Build with Maven (pre-installed)
cd day3/code/eventhub-producer
mvn clean package

# Deploy to Azure Container Apps or AKS
# (All tools available)
```

### Observability Lab (04-OBSERVABILITY_LAB.md)

**Fully supported in Cloud Shell!**

```bash
# Create Application Insights
az monitor app-insights component create \
  --app ai-resilience4j-demo \
  --location eastus \
  --resource-group rg-cloud-native

# All kubectl commands work
kubectl apply -f deployment.yaml

# Port forwarding works (use Cloud Shell's web preview)
kubectl port-forward svc/resilience4j-demo 8080:80
```

**To access forwarded ports:**
- Click **Web Preview** button in Cloud Shell toolbar
- Select port 8080
- Opens in new browser tab

### Logging Lab (06-LOGGING_LAB.md)

**100% compatible!**

```bash
# Edit files with built-in code editor
code application.yml

# Build and test
mvn clean package
mvn spring-boot:run

# Deploy to AKS
kubectl apply -f deployment.yaml
```

### Azure Monitor Lab (08-AZURE_MONITOR_LAB.md)

**Perfect for Cloud Shell!**

```bash
# All Azure Monitor commands work natively
az monitor metrics alert create \
  --name "High Latency Alert" \
  --resource-group rg-cloud-native \
  --scopes $RESOURCE_ID \
  --condition "avg Percentage CPU > 80"

# Query logs with KQL
az monitor log-analytics query \
  --workspace <workspace-id> \
  --analytics-query "requests | where timestamp > ago(1h)"
```

### Istio Lab (10-ISTIO_LAB.md)

**Works great in Cloud Shell!**

```bash
# Download Istio
curl -L https://istio.io/downloadIstio | sh -
cd istio-1.20.0
export PATH=$PWD/bin:$PATH

# Install on AKS
istioctl install --set profile=demo -y

# All kubectl commands work
kubectl get pods -n istio-system

# Access dashboards via port forwarding + Web Preview
kubectl port-forward -n istio-system svc/kiali 20001:20001
# Click "Web Preview" → Configure → Port 20001
```

### APIM Integration Lab (11-AZURE_APIM_ISTIO_INTEGRATION.md)

**Designed for Cloud Shell!**

```bash
# All Azure APIM commands work
az apim create \
  --name apim-demo \
  --resource-group rg-cloud-native \
  --publisher-email admin@example.com \
  --publisher-name "Demo"

# Import API specs
az apim api import \
  --resource-group rg-cloud-native \
  --service-name apim-demo \
  --path resilience4j \
  --specification-path api-spec.yaml
```

---

## Cloud Shell Tips & Tricks

### 1. Persistent Storage

Your `$HOME` directory (5GB) persists across sessions:

```bash
# Create workspace
mkdir -p ~/cloudnative
cd ~/cloudnative

# Clone repos (persists)
git clone https://github.com/your-repo/cloud_native.git

# Files persist even after closing Cloud Shell
```

### 2. Upload/Download Files

```bash
# Upload files (click Upload/Download button in toolbar)
# Or use curl
curl -o deployment.yaml https://raw.githubusercontent.com/...

# Download files to local machine
# Click Download button, select file
```

### 3. Code Editor

```bash
# Open built-in VS Code-style editor
code application.yml

# Edit, save, close
# Changes persist in Cloud Shell storage
```

### 4. Web Preview for Services

```bash
# Port forward service
kubectl port-forward svc/my-app 8080:80

# Click "Web Preview" in Cloud Shell toolbar
# Select port 8080
# Opens in browser tab
```

### 5. Multiple Terminal Sessions

```bash
# Split terminal (click Split button)
# Open multiple tabs
# Run commands in parallel
```

### 6. Environment Variables

```bash
# Set variables (persist in session)
export RESOURCE_GROUP="rg-cloud-native"
export LOCATION="eastus"

# Save to profile for all sessions
echo 'export RESOURCE_GROUP="rg-cloud-native"' >> ~/.bashrc
```

### 7. Installing Additional Tools

```bash
# Install istioctl
curl -L https://istio.io/downloadIstio | sh -
export PATH=$PWD/istio-1.20.0/bin:$PATH

# Verify
istioctl version
```

---

## Step-by-Step: Complete Day 3 in Cloud Shell

### Session 1: Event Hub (2 hours)

```bash
# 1. Open Cloud Shell (Bash)
# 2. Clone repository
git clone https://github.com/your-repo/cloud_native.git
cd cloud_native/resilience4j-demo

# 3. Follow 02-EVENT_HUB_LAB.md
# All commands work as-is

# 4. Build applications
cd day3/code/eventhub-producer
mvn clean package

cd ../eventhub-consumer
mvn clean package

# 5. Test locally in Cloud Shell
java -jar target/eventhub-producer-1.0.0.jar
```

### Session 2: Observability (2 hours)

```bash
# 1. Continue in same Cloud Shell session
cd ~/cloud_native/resilience4j-demo

# 2. Follow 04-OBSERVABILITY_LAB.md
# All Azure CLI and kubectl commands work

# 3. Access dashboards via Web Preview
kubectl port-forward svc/app 8080:80
# Click Web Preview → Port 8080
```

### Session 3: Logging (1.5 hours)

```bash
# 1. Follow 06-LOGGING_LAB.md
# 2. Edit configuration files
code src/main/resources/application.yml
code src/main/resources/logback-spring.xml

# 3. Build and test
mvn clean package
```

### Session 4: Monitoring & Alerts (2 hours)

```bash
# 1. Follow 08-AZURE_MONITOR_LAB.md
# All Azure Monitor commands work natively

# 2. Create alerts
az monitor metrics alert create ...

# 3. Query logs
az monitor log-analytics query ...
```

### Session 5: Istio (2.5 hours)

```bash
# 1. Install istioctl
curl -L https://istio.io/downloadIstio | sh -
cd istio-1.20.0
export PATH=$PWD/bin:$PATH

# 2. Follow 10-ISTIO_LAB.md
istioctl install --set profile=demo -y

# 3. Deploy applications
kubectl apply -f deployment-istio.yaml

# 4. Access Kiali
kubectl port-forward -n istio-system svc/kiali 20001:20001
# Web Preview → Port 20001
```

### Session 6: APIM Integration (1.5 hours)

```bash
# 1. Follow 11-AZURE_APIM_ISTIO_INTEGRATION.md
# All APIM commands work

# 2. Test APIs
curl -H "Ocp-Apim-Subscription-Key: $KEY" $APIM_URL/api/...
```

---

## Limitations & Workarounds

### ❌ Limitation 1: Docker Build (in PowerShell mode)

**Issue:** Docker not available in Cloud Shell PowerShell  
**Workaround:** Use **Bash mode** or **Azure ACR Tasks**

```bash
# Option 1: Use Bash Cloud Shell
# Docker works in Bash mode

# Option 2: Use ACR Tasks (no Docker needed)
az acr build --registry myacr --image app:latest .
```

### ❌ Limitation 2: Local Browser Access

**Issue:** Can't directly access http://localhost  
**Workaround:** Use **Web Preview** feature

```bash
# Instead of opening browser locally
kubectl port-forward svc/app 8080:80

# Click "Web Preview" button → Select port 8080
```

### ❌ Limitation 3: Session Timeout

**Issue:** Cloud Shell sessions timeout after 20 minutes of inactivity  
**Workaround:** Save work frequently, use `tmux`

```bash
# Use tmux to keep sessions alive
tmux new -s lab

# Detach: Ctrl+B, then D
# Reattach after timeout
tmux attach -t lab
```

### ❌ Limitation 4: Limited Resources

**Issue:** Cloud Shell has resource limits (CPU/Memory)  
**Workaround:** Build on Azure services, not locally

```bash
# Instead of building locally
mvn clean package

# Use Azure Container Apps or AKS
az acr build --registry myacr --image app:latest .
```

---

## Recommended Cloud Shell Configuration

### Initial Setup Script

Save this as `~/setup-day3.sh`:

```bash
#!/bin/bash

# Day 3 Cloud Shell Setup Script

# Set defaults
az configure --defaults \
  group=rg-cloud-native \
  location=eastus

# Install istioctl
if [ ! -d "~/istio-1.20.0" ]; then
  cd ~
  curl -L https://istio.io/downloadIstio | sh -
  echo 'export PATH=$HOME/istio-1.20.0/bin:$PATH' >> ~/.bashrc
fi

# Clone repository if not exists
if [ ! -d "~/cloud_native" ]; then
  cd ~
  git clone https://github.com/your-repo/cloud_native.git
fi

# Create workspace
mkdir -p ~/cloudnative/labs
cd ~/cloudnative/labs

# Set environment variables
export RESOURCE_GROUP="rg-cloud-native"
export LOCATION="eastus"
export CLUSTER_NAME="aks-istio-demo"

echo "✅ Day 3 environment ready!"
echo "📁 Working directory: $(pwd)"
echo "🌐 Resource Group: $RESOURCE_GROUP"
echo "📍 Location: $LOCATION"
```

Run once:
```bash
chmod +x ~/setup-day3.sh
~/setup-day3.sh
```

---

## Quick Reference: Cloud Shell Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl + C` | Cancel command |
| `Ctrl + L` | Clear screen |
| `Ctrl + R` | Search command history |
| `Tab` | Auto-complete |
| `Ctrl + A` | Jump to start of line |
| `Ctrl + E` | Jump to end of line |
| `Ctrl + U` | Delete line |
| `Ctrl + W` | Delete word |

---

## Summary

### ✅ What Works Perfectly

- All Azure CLI commands
- kubectl and Helm
- Maven builds
- Git operations
- File editing with built-in editor
- Port forwarding with Web Preview
- istioctl (after download)
- All Day 3 labs

### ⚠️ Minor Adjustments Needed

- Use Web Preview instead of localhost
- Use Bash mode for Docker (or ACR Tasks)
- Install istioctl manually (one command)
- Use tmux for long-running sessions

### 🎯 Bottom Line

**You can complete 100% of Day 3 training using only Azure Cloud Shell!**

No local installations required. Just open [shell.azure.com](https://shell.azure.com) and start learning!

---

## Next Steps

1. Open Azure Cloud Shell: https://shell.azure.com
2. Run setup script (see above)
3. Follow labs in order:
   - 02-EVENT_HUB_LAB.md
   - 04-OBSERVABILITY_LAB.md
   - 06-LOGGING_LAB.md
   - 08-AZURE_MONITOR_LAB.md
   - 10-ISTIO_LAB.md
   - 11-AZURE_APIM_ISTIO_INTEGRATION.md

Happy learning! 🚀
