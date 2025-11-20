# How to Get Application Insights Connection String and Instrumentation Key

This guide shows you how to obtain the Application Insights credentials needed for the observability labs.

## Option 1: Using Azure Portal (Recommended)

### Step 1: Create Application Insights Resource

1. Go to [Azure Portal](https://portal.azure.com)
2. Click **"+ Create a resource"**
3. Search for **"Application Insights"**
4. Click **"Create"**

### Step 2: Configure the Resource

Fill in the required fields:
- **Subscription**: Select your Azure subscription
- **Resource Group**: Create new or use existing (e.g., `rg-observability-lab`)
- **Name**: Give it a unique name (e.g., `appinsights-resilience4j-demo`)
- **Region**: Select closest region (e.g., `East US`)
- **Resource Mode**: Select **"Workspace-based"**
- **Log Analytics Workspace**: Create new or select existing

Click **"Review + Create"** → **"Create"**

### Step 3: Get Connection String and Instrumentation Key

After the resource is created:

1. Go to your Application Insights resource
2. In the left menu, click **"Overview"** or look at the **"Essentials"** section at the top
3. You'll see:
   - **Connection String** - Copy this entire value
   - **Instrumentation Key** - Copy this value

**Example Connection String:**
```
InstrumentationKey=12345678-1234-1234-1234-123456789abc;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/
```

**Example Instrumentation Key:**
```
12345678-1234-1234-1234-123456789abc
```

---

## Option 2: Using Azure CLI

### Step 1: Login to Azure

```bash
az login
```

### Step 2: Set Variables

```bash
# Set your variables
RESOURCE_GROUP="rg-observability-lab"
LOCATION="eastus"
APP_INSIGHTS_NAME="appinsights-resilience4j-$(date +%s)"
```

### Step 3: Create Resource Group (if not exists)

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION
```

### Step 4: Create Application Insights

```bash
az monitor app-insights component create \
  --app $APP_INSIGHTS_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --application-type web
```

### Step 5: Get Connection String and Instrumentation Key

```bash
# Get Connection String
CONNECTION_STRING=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query connectionString -o tsv)

echo "Connection String: $CONNECTION_STRING"

# Get Instrumentation Key
INSTRUMENTATION_KEY=$(az monitor app-insights component show \
  --app $APP_INSIGHTS_NAME \
  --resource-group $RESOURCE_GROUP \
  --query instrumentationKey -o tsv)

echo "Instrumentation Key: $INSTRUMENTATION_KEY"
```

---

## Option 3: Using Azure Cloud Shell

1. Go to [Azure Cloud Shell](https://shell.azure.com)
2. Select **Bash** environment
3. Follow the same Azure CLI commands as Option 2

---

## How to Use the Credentials

### Method 1: Environment Variables (Recommended)

Set the credentials as environment variables before running your application:

**Windows PowerShell:**
```powershell
$env:APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx;IngestionEndpoint=https://..."
$env:APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="12345678-1234-1234-1234-123456789abc"
```

**Windows Command Prompt:**
```cmd
set APPLICATIONINSIGHTS_CONNECTION_STRING=InstrumentationKey=xxx;IngestionEndpoint=https://...
set APPLICATIONINSIGHTS_INSTRUMENTATION_KEY=12345678-1234-1234-1234-123456789abc
```

**Linux/Mac/Azure Cloud Shell (Bash):**
```bash
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx;IngestionEndpoint=https://..."
export APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="12345678-1234-1234-1234-123456789abc"
```

### Method 2: Update application.yml (Not Recommended for Production)

You can hardcode the values in `application.yml`, but this is **NOT recommended** for production:

```yaml
spring:
  cloud:
    azure:
      monitor:
        enabled: true
        connection-string: "InstrumentationKey=xxx;IngestionEndpoint=https://..."

management:
  metrics:
    export:
      azuremonitor:
        enabled: true
        instrumentation-key: "12345678-1234-1234-1234-123456789abc"
```

⚠️ **Security Warning**: Never commit credentials to Git! Add them to `.gitignore` or use environment variables.

### Method 3: Using application-local.yml (Better for Development)

Create a file `application-local.yml` in `src/main/resources/` (add to `.gitignore`):

```yaml
spring:
  cloud:
    azure:
      monitor:
        connection-string: "InstrumentationKey=xxx;IngestionEndpoint=https://..."

management:
  metrics:
    export:
      azuremonitor:
        instrumentation-key: "12345678-1234-1234-1234-123456789abc"
```

Run with profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Verify It's Working

### 1. Start Your Application

```bash
mvn spring-boot:run
```

### 2. Generate Some Traffic

```bash
# Make some requests
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/circuit-breaker/demo
curl http://localhost:8080/api/trace/start
```

### 3. Check Application Insights

1. Go to Azure Portal → Your Application Insights resource
2. Click **"Live Metrics"** in the left menu
3. You should see real-time data flowing within 10-30 seconds

4. Click **"Application Map"** to see service topology
5. Click **"Performance"** to see request metrics
6. Click **"Failures"** to see any errors

### 4. Wait for Data (2-5 minutes)

Initial telemetry may take 2-5 minutes to appear in:
- Performance metrics
- Custom metrics
- Logs

---

## Troubleshooting

### No Data in Application Insights

**Check 1: Verify credentials are set**
```bash
# PowerShell
echo $env:APPLICATIONINSIGHTS_CONNECTION_STRING

# Bash
echo $APPLICATIONINSIGHTS_CONNECTION_STRING
```

**Check 2: Check application logs**
Look for errors related to Application Insights:
```
ERROR c.a.m.ApplicationInsightsConfigurationFactory - Failed to create telemetry configuration
```

**Check 3: Verify network connectivity**
Ensure your application can reach:
- `*.applicationinsights.azure.com`
- `*.in.applicationinsights.azure.com`

**Check 4: Check actuator endpoint**
```bash
curl http://localhost:8080/actuator/appinsights
```

### Connection String Format Issues

Make sure your connection string includes all parts:
```
InstrumentationKey=xxx;IngestionEndpoint=https://region.in.applicationinsights.azure.com/;LiveEndpoint=https://region.livediagnostics.monitor.azure.com/
```

### Still Not Working?

1. Enable debug logging in `application.yml`:
```yaml
logging:
  level:
    com.azure.monitor: DEBUG
    com.microsoft.applicationinsights: DEBUG
```

2. Check the Application Insights status:
```bash
curl http://localhost:8080/actuator/health
```

Look for `applicationInsights` in the health response.

---

## Example: Complete Setup Flow

```bash
# 1. Create Application Insights (Azure CLI)
az monitor app-insights component create \
  --app appinsights-demo \
  --location eastus \
  --resource-group rg-observability-lab \
  --application-type web

# 2. Get credentials
CONNECTION_STRING=$(az monitor app-insights component show \
  --app appinsights-demo \
  --resource-group rg-observability-lab \
  --query connectionString -o tsv)

INSTRUMENTATION_KEY=$(az monitor app-insights component show \
  --app appinsights-demo \
  --resource-group rg-observability-lab \
  --query instrumentationKey -o tsv)

# 3. Set environment variables
export APPLICATIONINSIGHTS_CONNECTION_STRING="$CONNECTION_STRING"
export APPLICATIONINSIGHTS_INSTRUMENTATION_KEY="$INSTRUMENTATION_KEY"

# 4. Build and run
cd "c:/courses/Cloud Native Java/code/resilience4j-demo"
mvn clean package -DskipTests
mvn spring-boot:run

# 5. Generate test traffic
for i in {1..100}; do
  curl http://localhost:8080/api/circuit-breaker/demo
  sleep 0.1
done

# 6. Check Azure Portal → Application Insights → Live Metrics
```

---

## For All Three Projects

You need to set the same environment variables for all three applications:

1. **resilience4j-demo** (port 8080)
2. **eventhub-producer** (port 8081)
3. **eventhub-consumer** (port 8082)

All three will send telemetry to the same Application Insights instance, which allows you to:
- See the complete application map
- Track distributed traces across all services
- Monitor end-to-end performance
- Correlate events between producer and consumer

---

## Cost Considerations

- **Free Tier**: First 5 GB of data per month is free
- **Retention**: 90 days included
- **Additional Data**: ~$2.30 per GB (varies by region)
- **Typical Usage**: Small demo apps usually stay within free tier

To monitor costs:
1. Azure Portal → Your Application Insights → **"Usage and estimated costs"**

---

## Next Steps

Once you have Application Insights configured:
1. ✅ Complete [04-OBSERVABILITY_LAB.md](day3/04-OBSERVABILITY_LAB.md)
2. ✅ Set up [06-LOGGING_LAB.md](day3/06-LOGGING_LAB.md) 
3. ✅ Configure alerts in [08-AZURE_MONITOR_LAB.md](day3/08-AZURE_MONITOR_LAB.md)
