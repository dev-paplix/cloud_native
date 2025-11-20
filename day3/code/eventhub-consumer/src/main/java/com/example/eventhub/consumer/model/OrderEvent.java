package com.example.eventhub.consumer.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order Event Domain Model
 * 
 * Matches the event structure sent by the producer
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
}
