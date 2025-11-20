package com.example.eventhub.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Event Hub Consumer Application
 * 
 * This microservice demonstrates:
 * - Consuming events from Azure Event Hub
 * - Manual checkpointing for reliability
 * - Error handling and retry logic
 * - Processing metrics collection
 */
@SpringBootApplication
public class EventhubConsumerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EventhubConsumerApplication.class, args);
        System.out.println("\n==============================================");
        System.out.println("Event Hub Consumer Started!");
        System.out.println("==============================================");
        System.out.println("Listening to Event Hub: orders");
        System.out.println("Consumer Group: order-processor-group");
        System.out.println("\nAPI Endpoints:");
        System.out.println("  GET  http://localhost:8082/api/stats");
        System.out.println("\nHealth & Metrics:");
        System.out.println("  GET  http://localhost:8082/actuator/health");
        System.out.println("  GET  http://localhost:8082/actuator/metrics");
        System.out.println("  GET  http://localhost:8082/actuator/prometheus");
        System.out.println("==============================================\n");
    }
}
