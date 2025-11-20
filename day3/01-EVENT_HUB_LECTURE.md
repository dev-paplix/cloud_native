# Lecture: Event-Driven Async Flow & Azure Event Hub Integration

## Duration: 60 minutes

## Learning Objectives
- Understand event-driven architecture patterns
- Learn Azure Event Hub capabilities and use cases
- Compare Event Hub vs Service Bus vs Kafka
- Master Spring Cloud Stream integration with Event Hub
- Implement high-throughput event processing

---

## 1. Introduction to Event-Driven Architecture (10 min)

### What is Event-Driven Architecture?
Event-Driven Architecture (EDA) is a design pattern where services communicate through events rather than direct calls.

**Key Characteristics:**
- **Asynchronous**: Producer doesn't wait for consumer response
- **Decoupled**: Services don't need to know about each other
- **Scalable**: Can handle millions of events per second
- **Resilient**: Failures don't cascade across services

### Benefits of Event-Driven Systems
1. **Loose Coupling**: Services evolve independently
2. **Scalability**: Easy to scale consumers independently
3. **Resilience**: Built-in retry and dead-letter handling
4. **Real-time Processing**: Stream processing for immediate insights
5. **Event Sourcing**: Complete audit trail of all changes

### Common Use Cases
- **Order Processing**: E-commerce order workflows
- **IoT Telemetry**: Device data ingestion
- **Log Aggregation**: Centralized logging from microservices
- **Stream Analytics**: Real-time data analysis
- **CQRS Pattern**: Command Query Responsibility Segregation

---

## 2. Azure Event Hub Overview (15 min)

### What is Azure Event Hub?
Azure Event Hubs is a big data streaming platform and event ingestion service capable of receiving and processing millions of events per second.

### Key Features
- **High Throughput**: Millions of events/sec
- **Low Latency**: Sub-second processing
- **Partitioning**: Parallel processing across multiple consumers
- **Retention**: Store events for 1-90 days
- **Capture**: Automatic archival to Azure Blob/Data Lake
- **Kafka Compatible**: Works with existing Kafka clients

### Event Hub Architecture

```
┌─────────────┐
│  Producers  │
│ (Multiple)  │
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────┐
│    Event Hub Namespace           │
│  ┌────────────────────────────┐  │
│  │  Event Hub: "orders"       │  │
│  │  ┌──────┬──────┬──────┐   │  │
│  │  │ P-0  │ P-1  │ P-2  │   │  │ ◄── Partitions
│  │  └──────┴──────┴──────┘   │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
       │
       ▼
┌──────────────┐
│  Consumers   │
│ (Consumer    │
│  Groups)     │
└──────────────┘
```

### Core Concepts

#### 1. Event Hub Namespace
- Container for multiple Event Hubs
- Provides DNS endpoint
- Manages authentication and authorization

#### 2. Event Hub
- Actual message channel
- Similar to Kafka topic
- Contains multiple partitions

#### 3. Partitions
- Ordered sequence of events
- Enables parallel processing
- Typically 2-32 partitions per Event Hub
- **Partition Key**: Ensures related events go to same partition

#### 4. Consumer Groups
- Independent view of the event stream
- Multiple applications can read same events
- Each consumer group maintains its own offset

#### 5. Checkpointing
- Track which events have been processed
- Enables resume from last position after failure
- Stored in Azure Blob Storage

### Event Hub vs Kafka vs Service Bus

| Feature | Event Hub | Kafka | Service Bus |
|---------|-----------|-------|-------------|
| **Purpose** | Big data streaming | General streaming | Enterprise messaging |
| **Throughput** | Millions/sec | Millions/sec | Thousands/sec |
| **Retention** | 1-90 days | Unlimited | 14 days max |
| **Ordering** | Per partition | Per partition | FIFO queues |
| **Protocol** | AMQP, Kafka | Kafka | AMQP, MQTT |
| **Message Size** | 1 MB | 1 MB | 256 KB (1 MB premium) |
| **Best For** | Telemetry, logs | General purpose | Transactional messages |
| **Transactions** | No | Yes | Yes |
| **Filters** | No | No | Yes (SQL filters) |

### When to Use Event Hub
✅ High-volume telemetry data  
✅ Log aggregation from microservices  
✅ IoT device data streams  
✅ Real-time analytics pipelines  
✅ Event sourcing with replay capability  

### When NOT to Use Event Hub
❌ Need complex message routing  
❌ Require guaranteed delivery (use Service Bus)  
❌ Need transactions across messages  
❌ Low volume, high complexity workflows  

---

## 3. Spring Cloud Stream with Event Hub (20 min)

### Spring Cloud Stream Overview
Spring Cloud Stream is a framework for building message-driven microservices.

**Key Abstractions:**
- **Binder**: Connects to messaging middleware (Event Hub, Kafka, RabbitMQ)
- **Binding**: Input/Output channels
- **Message**: Event payload + headers

### Architecture

```java
┌────────────────────────────────────┐
│   Spring Boot Application          │
│                                    │
│  ┌──────────┐      ┌───────────┐  │
│  │ Producer │──────│ Supplier  │  │
│  └──────────┘      └─────┬─────┘  │
│                          │         │
│  ┌──────────┐      ┌─────▼─────┐  │
│  │ Consumer │──────│ Consumer  │  │
│  └──────────┘      └───────────┘  │
│                                    │
│  ┌─────────────────────────────┐  │
│  │  Spring Cloud Stream        │  │
│  │  Event Hub Binder           │  │
│  └──────────────┬──────────────┘  │
└─────────────────┼──────────────────┘
                  │
                  ▼
         ┌─────────────────┐
         │  Azure Event Hub │
         └─────────────────┘
```

### Dependencies (pom.xml)

```xml
<dependencies>
    <!-- Spring Cloud Stream -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-stream</artifactId>
    </dependency>
    
    <!-- Event Hub Binder -->
    <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-stream-binder-eventhubs</artifactId>
    </dependency>
    
    <!-- Azure Identity for authentication -->
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-identity</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-dependencies</artifactId>
            <version>5.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Configuration (application.yml)

```yaml
spring:
  cloud:
    azure:
      eventhubs:
        # Connection string format:
        # Endpoint=sb://<namespace>.servicebus.windows.net/;SharedAccessKeyName=<key-name>;SharedAccessKey=<key>
        connection-string: ${EVENTHUB_CONNECTION_STRING}
        
    stream:
      bindings:
        # Producer binding
        orderProducer-out-0:
          destination: orders
          content-type: application/json
          producer:
            partition-key-expression: headers['partitionKey']
        
        # Consumer binding
        orderConsumer-in-0:
          destination: orders
          group: order-processor
          content-type: application/json
          consumer:
            checkpoint-mode: MANUAL
      
      eventhubs:
        bindings:
          orderConsumer-in-0:
            consumer:
              checkpoint:
                mode: MANUAL
                count: 10  # Checkpoint every 10 messages
```

### Producer Implementation

```java
@Configuration
public class EventProducerConfig {
    
    @Bean
    public Supplier<Flux<Message<OrderEvent>>> orderProducer() {
        return () -> Flux.interval(Duration.ofSeconds(1))
            .map(i -> {
                OrderEvent event = new OrderEvent(
                    UUID.randomUUID().toString(),
                    "ORDER-" + i,
                    LocalDateTime.now()
                );
                
                return MessageBuilder
                    .withPayload(event)
                    .setHeader("partitionKey", event.getOrderId())
                    .build();
            });
    }
}

@Data
@AllArgsConstructor
public class OrderEvent {
    private String eventId;
    private String orderId;
    private LocalDateTime timestamp;
}
```

### Consumer Implementation

```java
@Configuration
public class EventConsumerConfig {
    
    private static final Logger log = LoggerFactory.getLogger(EventConsumerConfig.class);
    
    @Bean
    public Consumer<Message<OrderEvent>> orderConsumer() {
        return message -> {
            OrderEvent event = message.getPayload();
            log.info("Received order event: {}", event);
            
            try {
                // Process the event
                processOrder(event);
                
                // Manual checkpoint
                Checkpointer checkpointer = message.getHeaders()
                    .get(CHECKPOINTER, Checkpointer.class);
                if (checkpointer != null) {
                    checkpointer.success()
                        .doOnSuccess(success -> 
                            log.debug("Checkpointed: {}", event.getEventId()))
                        .doOnError(error -> 
                            log.error("Checkpoint failed", error))
                        .subscribe();
                }
            } catch (Exception e) {
                log.error("Failed to process event: {}", event, e);
                // Could implement retry logic or dead-letter handling
            }
        };
    }
    
    private void processOrder(OrderEvent event) {
        // Business logic here
        log.info("Processing order: {}", event.getOrderId());
    }
}
```

### Error Handling Strategies

#### 1. Retry with Backoff
```yaml
spring:
  cloud:
    stream:
      bindings:
        orderConsumer-in-0:
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
            back-off-max-interval: 10000
            back-off-multiplier: 2.0
```

#### 2. Dead Letter Queue
```java
@Bean
public Consumer<Message<OrderEvent>> orderConsumer(
    StreamBridge streamBridge) {
    
    return message -> {
        try {
            processOrder(message.getPayload());
        } catch (Exception e) {
            log.error("Moving to DLQ", e);
            streamBridge.send("orders-dlq", message);
        }
    };
}
```

---

## 4. Advanced Patterns (10 min)

### Pattern 1: Event Sourcing
Store all changes as events, rebuild state by replaying.

```java
@Service
public class OrderEventStore {
    
    private final StreamBridge streamBridge;
    
    public void saveEvent(OrderEvent event) {
        // Persist event
        streamBridge.send("order-events", event);
    }
    
    public Order rebuildOrderState(String orderId) {
        // Replay all events for this order
        List<OrderEvent> events = fetchEvents(orderId);
        return events.stream()
            .reduce(new Order(), this::applyEvent, (o1, o2) -> o2);
    }
}
```

### Pattern 2: CQRS (Command Query Responsibility Segregation)
Separate read and write models.

```java
// Write side - Commands
@Service
public class OrderCommandService {
    public void createOrder(CreateOrderCommand cmd) {
        // Validate and publish event
        OrderCreatedEvent event = new OrderCreatedEvent(cmd);
        eventBus.publish(event);
    }
}

// Read side - Queries (separate database)
@Service
public class OrderQueryService {
    public OrderDTO getOrder(String id) {
        return readDatabase.findById(id);
    }
}
```

### Pattern 3: Saga Pattern
Distributed transactions across microservices.

```java
@Service
public class OrderSaga {
    
    @StreamListener("order-created")
    public void onOrderCreated(OrderCreatedEvent event) {
        // Step 1: Reserve inventory
        inventoryService.reserve(event.getItems());
    }
    
    @StreamListener("inventory-reserved")
    public void onInventoryReserved(InventoryReservedEvent event) {
        // Step 2: Process payment
        paymentService.charge(event.getOrderId());
    }
    
    @StreamListener("payment-failed")
    public void onPaymentFailed(PaymentFailedEvent event) {
        // Compensating transaction: release inventory
        inventoryService.release(event.getOrderId());
    }
}
```

---

## 5. Best Practices (5 min)

### Performance Optimization
1. **Batch Processing**: Process multiple events together
2. **Partition Strategy**: Use meaningful partition keys
3. **Consumer Scaling**: Match partition count with consumer instances
4. **Prefetch Count**: Tune based on processing time

### Reliability
1. **Idempotency**: Handle duplicate events gracefully
2. **Manual Checkpointing**: Control exactly when to commit
3. **Error Handling**: Implement retry + DLQ strategies
4. **Monitoring**: Track lag, throughput, and errors

### Security
1. **Managed Identity**: Use Azure AD instead of connection strings
2. **Encryption**: Enable at-rest and in-transit encryption
3. **Network Isolation**: Use private endpoints
4. **Access Control**: Use RBAC for fine-grained permissions

### Sample Idempotency Implementation
```java
@Service
public class IdempotentEventProcessor {
    
    private final Set<String> processedEventIds = 
        ConcurrentHashMap.newKeySet();
    
    public void processEvent(OrderEvent event) {
        String eventId = event.getEventId();
        
        // Check if already processed
        if (!processedEventIds.add(eventId)) {
            log.warn("Duplicate event detected: {}", eventId);
            return;
        }
        
        // Process event
        doProcess(event);
        
        // Optional: Clean up old IDs periodically
    }
}
```

---

## Summary

### Key Takeaways
1. Event-driven architecture enables loosely coupled, scalable microservices
2. Azure Event Hub excels at high-throughput streaming scenarios
3. Spring Cloud Stream provides elegant abstraction over messaging systems
4. Proper error handling and checkpointing are critical for reliability
5. Partitioning strategy significantly impacts performance

### Next Steps
- Complete the hands-on lab to build a producer/consumer system
- Experiment with different partition strategies
- Implement error handling with DLQ
- Monitor Event Hub metrics in Azure Portal

---

## References
- [Azure Event Hubs Documentation](https://docs.microsoft.com/azure/event-hubs/)
- [Spring Cloud Stream Reference](https://spring.io/projects/spring-cloud-stream)
- [Spring Cloud Azure](https://docs.microsoft.com/azure/developer/java/spring-framework/)
- [Event-Driven Architecture Patterns](https://martinfowler.com/articles/201701-event-driven.html)
