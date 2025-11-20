package com.example.eventhub.producer.service;

import com.example.eventhub.producer.model.OrderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

import static net.logstash.logback.argument.StructuredArguments.*;

/**
 * Order Producer Service
 * 
 * Handles sending order events to Event Hub with:
 * - Partition key routing
 * - Metrics collection
 * - Error handling
 */
@Slf4j
@Service
public class OrderProducerService {
    
    private final StreamBridge streamBridge;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final Counter sentCounter;
    private final Counter failedCounter;
    
    public OrderProducerService(StreamBridge streamBridge, MeterRegistry meterRegistry) {
        this.streamBridge = streamBridge;
        this.sentCounter = Counter.builder("orders.sent")
            .description("Number of orders sent to Event Hub")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("orders.failed")
            .description("Number of orders failed to send")
            .register(meterRegistry);
    }
    
    /**
     * Send a single order event
     */
    public void sendOrder(OrderEvent order) {
        int count = counter.incrementAndGet();
        
        try {
            // Create message with partition key for ordering
            // All events with same customer ID go to same partition
            Message<OrderEvent> message = MessageBuilder
                .withPayload(order)
                .setHeader("partitionKey", order.getCustomerId())
                .setHeader("messageId", order.getEventId())
                .setHeader("eventType", "OrderCreated")
                .build();
            
            // Send to Event Hub
            boolean sent = streamBridge.send("orderProducer-out-0", message);
            
            if (sent) {
                sentCounter.increment();
                log.info("✓ Sent order #{}", 
                    count,
                    kv("orderId", order.getOrderId()),
                    kv("customerId", order.getCustomerId()),
                    kv("amount", order.getTotalAmount()),
                    kv("eventId", order.getEventId()));
            } else {
                failedCounter.increment();
                log.error("✗ Failed to send order", kv("orderId", order.getOrderId()));
            }
        } catch (Exception e) {
            failedCounter.increment();
            log.error("✗ Exception sending order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to send order", e);
        }
    }
    
    /**
     * Send a batch of sample orders
     */
    public void sendBatch(int batchSize) {
        log.info("📦 Sending batch", kv("batchSize", batchSize));
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < batchSize; i++) {
            OrderEvent order = OrderEvent.createSample(counter.get() + i + 1);
            sendOrder(order);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double throughput = (batchSize * 1000.0) / duration;
        log.info("✓ Batch complete",
            kv("batchSize", batchSize),
            kv("durationMs", duration),
            kv("throughput", String.format("%.2f orders/sec", throughput)));
    }
    
    /**
     * Get total number of messages sent
     */
    public int getMessageCount() {
        return counter.get();
    }
}
