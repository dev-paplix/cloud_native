package com.example.eventhub.consumer.service;

import com.azure.spring.messaging.checkpoint.Checkpointer;
import com.example.eventhub.consumer.model.OrderEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.azure.spring.messaging.AzureHeaders.CHECKPOINTER;

/**
 * Order Consumer Service
 * 
 * Handles consuming order events from Event Hub with:
 * - Manual checkpointing for reliability
 * - Metrics collection
 * - Error handling with retry
 * - Processing time tracking
 */
@Slf4j
@Configuration
public class OrderConsumerService {
    
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;
    
    public OrderConsumerService(MeterRegistry meterRegistry) {
        this.processedCounter = Counter.builder("orders.processed")
            .description("Number of orders processed successfully")
            .register(meterRegistry);
        this.errorCounter = Counter.builder("orders.errors")
            .description("Number of orders failed to process")
            .register(meterRegistry);
        this.processingTimer = Timer.builder("orders.processing.time")
            .description("Time taken to process orders")
            .register(meterRegistry);
    }
    
    /**
     * Main consumer function for order events
     */
    @Bean
    public Consumer<Message<OrderEvent>> orderConsumer() {
        return message -> {
            Timer.Sample sample = Timer.start();
            
            try {
                OrderEvent order = message.getPayload();
                int count = processedCount.incrementAndGet();
                
                log.info("📥 Processing order #{}: {} (Customer: {}, Amount: ${})",
                    count, order.getOrderId(), order.getCustomerId(), 
                    String.format("%.2f", order.getTotalAmount()));
                
                // Simulate processing time (remove in production)
                Thread.sleep(50);
                
                // Business logic
                processOrder(order);
                
                // Update metrics
                processedCounter.increment();
                sample.stop(processingTimer);
                
                // Manual checkpoint every 10 messages for reliability
                if (count % 10 == 0) {
                    checkpoint(message, count);
                }
                
                log.debug("✓ Order processed: {}", order.getOrderId());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errorCount.incrementAndGet();
                errorCounter.increment();
                log.error("✗ Processing interrupted", e);
            } catch (Exception e) {
                errorCount.incrementAndGet();
                errorCounter.increment();
                log.error("✗ Error processing order", e);
                // In production, send to DLQ or implement retry logic
            }
        };
    }
    
    /**
     * Business logic for processing orders
     */
    private void processOrder(OrderEvent order) {
        // Simulate business operations
        log.debug("  → Validating order: {}", order.getOrderId());
        validateOrder(order);
        
        log.debug("  → Checking inventory for product: {}", order.getProductId());
        checkInventory(order);
        
        log.debug("  → Updating order status to PROCESSING");
        updateOrderStatus(order);
        
        log.debug("  → Order processing complete: {}", order.getOrderId());
    }
    
    private void validateOrder(OrderEvent order) {
        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid quantity: " + order.getQuantity());
        }
        if (order.getTotalAmount() <= 0) {
            throw new IllegalArgumentException("Invalid amount: " + order.getTotalAmount());
        }
    }
    
    private void checkInventory(OrderEvent order) {
        // Simulate inventory check
        // In production, this would call inventory service
    }
    
    private void updateOrderStatus(OrderEvent order) {
        // Simulate status update
        // In production, this would update database
    }
    
    /**
     * Perform manual checkpoint
     */
    private void checkpoint(Message<?> message, int count) {
        Checkpointer checkpointer = message.getHeaders().get(CHECKPOINTER, Checkpointer.class);
        if (checkpointer != null) {
            checkpointer.success()
                .doOnSuccess(success -> 
                    log.debug("✓ Checkpoint successful at message #{}", count))
                .doOnError(error -> 
                    log.error("✗ Checkpoint failed at message #{}", count, error))
                .subscribe();
        } else {
            log.warn("⚠ No checkpointer available for message #{}", count);
        }
    }
    
    public int getProcessedCount() {
        return processedCount.get();
    }
    
    public int getErrorCount() {
        return errorCount.get();
    }
}
