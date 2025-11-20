package com.example.eventhub.producer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Event Domain Model
 * 
 * Represents an order event sent to Event Hub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    
    private String eventId;
    private String orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private Double totalAmount;
    private String status;
    private LocalDateTime timestamp;
    
    /**
     * Create a sample order event for testing
     */
    public static OrderEvent createSample(int index) {
        return new OrderEvent(
            UUID.randomUUID().toString(),
            "ORDER-" + String.format("%06d", index),
            "CUST-" + (index % 100),  // 100 different customers
            "PROD-" + (index % 50),    // 50 different products
            (index % 10) + 1,          // 1-10 items
            Math.round(Math.random() * 1000 * 100.0) / 100.0,  // $0-1000
            "PENDING",
            LocalDateTime.now()
        );
    }
}
