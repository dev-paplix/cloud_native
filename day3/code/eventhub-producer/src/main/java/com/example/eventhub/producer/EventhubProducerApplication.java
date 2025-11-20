package com.example.eventhub.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Event Hub Producer Application
 * 
 * This microservice demonstrates:
 * - Sending events to Azure Event Hub
 * - Partitioning strategy for load distribution
 * - High-throughput event production
 * - REST API for event publishing
 */
@SpringBootApplication
public class EventhubProducerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EventhubProducerApplication.class, args);
        System.out.println("\n==============================================");
        System.out.println("Event Hub Producer Started!");
        System.out.println("==============================================");
        System.out.println("API Endpoints:");
        System.out.println("  POST http://localhost:8081/api/orders/send");
        System.out.println("  POST http://localhost:8081/api/orders/send-batch?size=100");
        System.out.println("  GET  http://localhost:8081/api/orders/stats");
        System.out.println("\nHealth & Metrics:");
        System.out.println("  GET  http://localhost:8081/actuator/health");
        System.out.println("  GET  http://localhost:8081/actuator/metrics");
        System.out.println("  GET  http://localhost:8081/actuator/prometheus");
        System.out.println("==============================================\n");
    }
}
