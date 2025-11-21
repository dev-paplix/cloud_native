package com.example.resilience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application for Resilience4j Demo
 * 
 * This application demonstrates the following resilience patterns:
 * 1. Retry - Automatically retry failed operations
 * 2. Circuit Breaker - Prevent cascading failures
 * 3. Rate Limiter - Control rate of requests
 * 4. Health Endpoints - Monitor application health
 */
@SpringBootApplication
public class Resilience4jDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(Resilience4jDemoApplication.class, args);
        System.out.println("\n==================================================");
        System.out.println("Resilience4j Demo Application Started!");
        System.out.println("==================================================");
        System.out.println("API Endpoints:");
        System.out.println("  - http://localhost:8080/api/retry/demo");
        System.out.println("  - http://localhost:8080/api/circuit-breaker/demo");
        System.out.println("  - http://localhost:8080/api/rate-limiter/demo");
        System.out.println("  - http://localhost:8080/api/combined/demo");
        System.out.println("\nHealth & Monitoring Endpoints:");
        System.out.println("  - http://localhost:8080/actuator/health");
        System.out.println("  - http://localhost:8080/actuator/circuitbreakers");
        System.out.println("  - http://localhost:8080/actuator/circuitbreakerevents");
        System.out.println("  - http://localhost:8080/actuator/ratelimiters");
        System.out.println("  - http://localhost:8080/actuator/retries");
        System.out.println("  - http://localhost:8080/actuator/metrics");
        System.out.println("==================================================\n");
    }
}
