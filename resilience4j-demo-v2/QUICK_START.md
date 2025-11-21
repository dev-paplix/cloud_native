# Quick Start Guide - Resilience4j Demo

## 🚀 Quick Setup (5 minutes)

### Step 1: Navigate to Project
```powershell
cd "c:\courses\Cloud Native Java\code\resilience4j-demo"
```

### Step 2: Build the Project
```powershell
mvn clean install
```

### Step 3: Run the Application
```powershell
mvn spring-boot:run
```

### Step 4: Verify It's Running
Open: http://localhost:8080/actuator/health

You should see: `{"status":"UP"}`

## 🎯 Quick Tests (Try These First!)

### Test 1: Retry Pattern (30 seconds)
```powershell
# Success case - should succeed on first try
curl http://localhost:8080/api/retry/success

# Failure case - watch it retry 3 times then fallback
curl http://localhost:8080/api/retry/fail

# Unreliable - random success/failure
curl http://localhost:8080/api/retry/unreliable
```

### Test 2: Circuit Breaker (2 minutes)
```powershell
# Step 1: Make successful calls - circuit stays CLOSED
curl http://localhost:8080/api/circuit-breaker/success

# Step 2: Trigger circuit to OPEN (make 10 failing calls)
curl -X POST http://localhost:8080/api/circuit-breaker/trigger-open

# Step 3: Check circuit state
curl http://localhost:8080/actuator/circuitbreakers

# Step 4: Try calling - circuit is OPEN, fallback executes immediately
curl http://localhost:8080/api/circuit-breaker/demo

# Step 5: Wait 10 seconds, circuit goes to HALF_OPEN, then try again
Start-Sleep -Seconds 10
curl http://localhost:8080/api/circuit-breaker/success
```

### Test 3: Rate Limiter (1 minute)
```powershell
# Make 15 rapid requests - first 10 succeed, rest are rate-limited
curl "http://localhost:8080/api/rate-limiter/burst-test?requestCount=15"

# Check rate limiter status
curl http://localhost:8080/actuator/ratelimiters
```

### Test 4: All Patterns Combined (1 minute)
```powershell
# Stress test - see all patterns working together
curl "http://localhost:8080/api/combined/stress-test?requestCount=20"
```

## 📊 Monitoring Dashboard

Open these URLs in your browser:

1. **Health Check:** http://localhost:8080/actuator/health
2. **Circuit Breakers:** http://localhost:8080/actuator/circuitbreakers
3. **Rate Limiters:** http://localhost:8080/actuator/ratelimiters
4. **Retry Metrics:** http://localhost:8080/actuator/retries
5. **All Metrics:** http://localhost:8080/actuator/metrics

## 🎓 Student Exercises

### Exercise 1: Make the Circuit Breaker Open
**Goal:** Understand circuit breaker states

**Steps:**
1. Open http://localhost:8080/actuator/circuitbreakers in browser
2. Note the circuit is CLOSED
3. Run: `curl -X POST http://localhost:8080/api/circuit-breaker/trigger-open`
4. Refresh the browser - circuit should be OPEN
5. Try: `curl http://localhost:8080/api/circuit-breaker/demo`
6. Notice it fails immediately without trying the service
7. Wait 10 seconds and try again - circuit goes to HALF_OPEN

**What You Learned:**
- CLOSED = Normal operation
- OPEN = Service blocked, fallback used
- HALF_OPEN = Testing if service recovered

### Exercise 2: Test Rate Limiting
**Goal:** Understand request throttling

**Steps:**
1. Run: `curl http://localhost:8080/api/rate-limiter/demo`
2. Keep running it rapidly (hit Enter quickly)
3. Notice after 10 requests per second, you get RATE_LIMITED
4. Wait 1 second and try again - limit refreshed

**What You Learned:**
- Rate limiters control request frequency
- Limits refresh after configured period
- Different services can have different limits

### Exercise 3: Observe Retry Behavior
**Goal:** See automatic retries in action

**Steps:**
1. Run: `curl http://localhost:8080/api/retry/fail`
2. Watch the logs in your terminal running the app
3. Notice it tries 3 times with exponential backoff
4. Then executes fallback method

**What You Learned:**
- Retries are automatic
- Exponential backoff increases wait time
- Fallback provides graceful degradation

## 🔧 Common Issues

### Issue: Port 8080 already in use
**Solution:** 
```powershell
# Edit src/main/resources/application.yml
# Change: server.port: 8080 to server.port: 8081
```

### Issue: mvn command not found
**Solution:**
```powershell
# Verify Maven is installed
mvn --version

# If not, download from: https://maven.apache.org/download.cgi
```

### Issue: Java version mismatch
**Solution:**
```powershell
# Check Java version
java -version

# Should be Java 23 or higher
```

## 💡 Tips for Learning

1. **Read the Logs:** The application logs show exactly what's happening
2. **Use Actuator:** Monitor endpoints show real-time state
3. **Experiment:** Change parameters in application.yml and restart
4. **Combine Patterns:** See how they work together in `/api/combined/*`

## 📞 Need Help?

- Check the full README.md for detailed documentation
- Review the code comments - every class is documented
- Look at the logs - they show what's happening
- Use actuator endpoints to see current state

## 🎉 Next Steps

Once you're comfortable with the basics:

1. **Modify Configuration:** Edit `application.yml` to change thresholds
2. **Add New Endpoints:** Create your own controllers
3. **Combine Patterns:** Experiment with different combinations
4. **Monitor Metrics:** Export to Prometheus/Grafana
5. **Write Tests:** Add unit and integration tests

---

**Happy Coding! 🚀**

Now you're ready to build resilient cloud-native applications!
