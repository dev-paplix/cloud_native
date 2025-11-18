# Docker Build and Test Script for Windows PowerShell
# This script builds the Docker image and runs basic tests

param(
    [Parameter(Mandatory=$false)]
    [string]$Version = "latest",
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipTests = $false,
    
    [Parameter(Mandatory=$false)]
    [switch]$Push = $false,
    
    [Parameter(Mandatory=$false)]
    [string]$Registry = "ghcr.io/your-org"
)

$ErrorActionPreference = "Stop"
$ImageName = "resilience4j-demo"
$FullImageName = "${Registry}/${ImageName}:${Version}"

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "Docker Build Script" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "Image: $FullImageName" -ForegroundColor Yellow
Write-Host ""

# Step 1: Clean Maven build
Write-Host "[1/6] Cleaning previous builds..." -ForegroundColor Green
mvn clean

# Step 2: Run tests (unless skipped)
if (-not $SkipTests) {
    Write-Host "[2/6] Running tests..." -ForegroundColor Green
    mvn test
} else {
    Write-Host "[2/6] Skipping tests (--SkipTests flag set)" -ForegroundColor Yellow
}

# Step 3: Build the application
Write-Host "[3/6] Building application with Maven..." -ForegroundColor Green
mvn package -DskipTests

# Step 4: Build Docker image
Write-Host "[4/6] Building Docker image..." -ForegroundColor Green
docker build -t "${ImageName}:${Version}" -t "${ImageName}:latest" .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker build failed!" -ForegroundColor Red
    exit 1
}

# Step 5: Run container for testing
Write-Host "[5/6] Testing Docker image..." -ForegroundColor Green

# Stop and remove any existing container
docker stop resilience4j-test 2>$null
docker rm resilience4j-test 2>$null

# Run container
Write-Host "Starting test container..." -ForegroundColor Cyan
docker run -d -p 8080:8080 --name resilience4j-test "${ImageName}:${Version}"

# Wait for application to start
Write-Host "Waiting for application to start (30 seconds)..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

# Test health endpoint
Write-Host "Testing health endpoint..." -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Health check passed!" -ForegroundColor Green
        Write-Host $response.Content -ForegroundColor Gray
    } else {
        Write-Host "✗ Health check failed with status: $($response.StatusCode)" -ForegroundColor Red
        docker logs resilience4j-test
        exit 1
    }
} catch {
    Write-Host "✗ Failed to connect to application" -ForegroundColor Red
    Write-Host "Container logs:" -ForegroundColor Yellow
    docker logs resilience4j-test
    docker stop resilience4j-test
    docker rm resilience4j-test
    exit 1
}

# Test API endpoints
Write-Host "`nTesting API endpoints..." -ForegroundColor Cyan

# Test retry endpoint
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/retry/demo" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Retry endpoint working!" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Retry endpoint failed" -ForegroundColor Red
}

# Test circuit breaker endpoint
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/circuit-breaker/demo" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Circuit breaker endpoint working!" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Circuit breaker endpoint failed" -ForegroundColor Red
}

# Test rate limiter endpoint
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/rate-limiter/demo" -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Rate limiter endpoint working!" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Rate limiter endpoint failed" -ForegroundColor Red
}

# Clean up test container
Write-Host "`nCleaning up test container..." -ForegroundColor Cyan
docker stop resilience4j-test
docker rm resilience4j-test

# Step 6: Push to registry (if requested)
if ($Push) {
    Write-Host "[6/6] Pushing image to registry..." -ForegroundColor Green
    docker tag "${ImageName}:${Version}" $FullImageName
    docker push $FullImageName
    
    if ($Version -ne "latest") {
        docker tag "${ImageName}:${Version}" "${Registry}/${ImageName}:latest"
        docker push "${Registry}/${ImageName}:latest"
    }
    
    Write-Host "✓ Image pushed to $FullImageName" -ForegroundColor Green
} else {
    Write-Host "[6/6] Skipping push (use -Push flag to push to registry)" -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host "Local image: ${ImageName}:${Version}" -ForegroundColor Yellow
if ($Push) {
    Write-Host "Registry image: $FullImageName" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "To run the container:" -ForegroundColor Cyan
Write-Host "  docker run -p 8080:8080 ${ImageName}:${Version}" -ForegroundColor White
Write-Host ""
Write-Host "To use Docker Compose:" -ForegroundColor Cyan
Write-Host "  docker-compose up -d" -ForegroundColor White
Write-Host ""
