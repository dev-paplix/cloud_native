# Hands-On Lab: Build Producer/Consumer Microservices with Event Hub

## Duration: 90 minutes

> **💡 Cloud Shell Ready!** This entire lab can be completed in [Azure Cloud Shell](https://shell.azure.com) without any local installations. See [AZURE_CLOUD_SHELL_GUIDE.md](AZURE_CLOUD_SHELL_GUIDE.md) for setup.

## Lab Objectives
- Set up Azure Event Hub namespace and event hub
- Create a Spring Boot producer microservice
- Create a Spring Boot consumer microservice
- Test high-throughput event processing
- Implement retry logic and error handling
- Simulate failures and observe behavior

---

## Prerequisites
- Azure subscription
- Azure CLI installed
- Java 17+ and Maven
- IDE (IntelliJ IDEA or VS Code)
- Docker Desktop (optional)

---

## Part 1: Azure Event Hub Setup (15 min)

### Step 1: Create Event Hub Namespace

```bash
# Login to Azure
az login

# Set variables
RESOURCE_GROUP="rg-eventhub-lab"
LOCATION="eastus"
NAMESPACE_NAME="eventhub-lab-$(date +%s)"  # Unique name
EVENTHUB_NAME="orders"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Event Hub namespace (Standard tier)
az eventhubs namespace create \
  --resource-group $RESOURCE_GROUP \
  --name $NAMESPACE_NAME \
  --location $LOCATION \
  --sku Standard \
  --capacity 1

# Create Event Hub with 4 partitions
az eventhubs eventhub create \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $NAMESPACE_NAME \
  --name $EVENTHUB_NAME \
  --partition-count 4 \
  --message-retention 1
```

### Step 2: Get Connection String

```bash
# Get the connection string
az eventhubs namespace authorization-rule keys list \
  --resource-group $RESOURCE_GROUP \
  --namespace-name $NAMESPACE_NAME \
  --name RootManageSharedAccessKey \
  --query primaryConnectionString \
  --output tsv

# Save this connection string - you'll need it later
```

**Alternative: Create via Azure Portal**
1. Navigate to Azure Portal → Create a resource
2. Search for "Event Hubs" → Create
3. Fill in namespace details
4. Create Event Hub within namespace
5. Go to Shared access policies → RootManageSharedAccessKey → Copy connection string

---

## Part 2: Create Producer Microservice (30 min)

### Step 1: Generate Spring Boot Project

```bash
# Create project directory
mkdir eventhub-producer
cd eventhub-producer

# Create with Spring Initializr (or use start.spring.io)
curl https://start.spring.io/starter.tgz \
  -d dependencies=web,actuator,lombok,cloud-stream \
  -d javaVersion=17 \
  -d type=maven-project \
  -d groupId=com.example \
  -d artifactId=eventhub-producer \
  -d name=eventhub-producer \
  -d packageName=com.example.eventhub.producer \
  | tar -xzf -
```

### Step 2: Update pom.xml

Add Event Hub dependencies:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>eventhub-producer</artifactId>
    <version>1.0.0</version>
    <name>eventhub-producer</name>
    
    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
        <spring-cloud-azure.version>5.8.0</spring-cloud-azure.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        
        <!-- Spring Cloud Stream -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>
        
        <!-- Azure Event Hub Binder -->
        <dependency>
            <groupId>com.azure.spring</groupId>
            <artifactId>spring-cloud-azure-stream-binder-eventhubs</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.azure.spring</groupId>
                <artifactId>spring-cloud-azure-dependencies</artifactId>
                <version>${spring-cloud-azure.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 3: Create application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: eventhub-producer
  
  cloud:
    azure:
      eventhubs:
        connection-string: ${EVENTHUB_CONNECTION_STRING}
    
    stream:
      function:
        definition: orderProducer
      
      bindings:
        orderProducer-out-0:
          destination: orders
          content-type: application/json
          producer:
            partition-key-expression: headers['partitionKey']
      
      eventhubs:
        bindings:
          orderProducer-out-0:
            producer:
              sync: false  # Async sending for better throughput

# Actuator configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# Logging
logging:
  level:
    com.example.eventhub: DEBUG
    com.azure.messaging: INFO
```

### Step 4: Create Domain Model

```java
package com.example.eventhub.producer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    
    public static OrderEvent createSample(int index) {
        return new OrderEvent(
            "evt-" + System.currentTimeMillis() + "-" + index,
            "ORDER-" + String.format("%06d", index),
            "CUST-" + (index % 100),
            "PROD-" + (index % 50),
            (index % 10) + 1,
            Math.random() * 1000,
            "PENDING",
            LocalDateTime.now()
        );
    }
}
```

### Step 5: Create Producer Service

```java
package com.example.eventhub.producer.service;

import com.example.eventhub.producer.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrderProducerService {
    
    private final StreamBridge streamBridge;
    private final AtomicInteger counter = new AtomicInteger(0);
    
    public OrderProducerService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    public void sendOrder(OrderEvent order) {
        int count = counter.incrementAndGet();
        
        // Create message with partition key
        Message<OrderEvent> message = MessageBuilder
            .withPayload(order)
            .setHeader("partitionKey", order.getCustomerId())
            .setHeader("messageId", order.getEventId())
            .build();
        
        // Send to Event Hub
        boolean sent = streamBridge.send("orderProducer-out-0", message);
        
        if (sent) {
            log.info("Sent order #{}: {} (Customer: {})", 
                count, order.getOrderId(), order.getCustomerId());
        } else {
            log.error("Failed to send order: {}", order.getOrderId());
        }
    }
    
    public void sendBatch(int batchSize) {
        log.info("Sending batch of {} orders", batchSize);
        for (int i = 0; i < batchSize; i++) {
            OrderEvent order = OrderEvent.createSample(counter.get() + i + 1);
            sendOrder(order);
        }
    }
    
    public int getMessageCount() {
        return counter.get();
    }
}
```

### Step 6: Create REST Controller

```java
package com.example.eventhub.producer.controller;

import com.example.eventhub.producer.model.OrderEvent;
import com.example.eventhub.producer.service.OrderProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderProducerService producerService;
    
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendOrder(@RequestBody OrderEvent order) {
        producerService.sendOrder(order);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "sent");
        response.put("orderId", order.getOrderId());
        response.put("eventId", order.getEventId());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/send-batch")
    public ResponseEntity<Map<String, Object>> sendBatch(
        @RequestParam(defaultValue = "10") int size) {
        
        log.info("Received request to send batch of {} orders", size);
        producerService.sendBatch(size);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "sent");
        response.put("batchSize", size);
        response.put("totalSent", producerService.getMessageCount());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessagesSent", producerService.getMessageCount());
        return ResponseEntity.ok(stats);
    }
}
```

### Step 7: Create Main Application

```java
package com.example.eventhub.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventhubProducerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EventhubProducerApplication.class, args);
    }
}
```

---

## Part 3: Create Consumer Microservice (30 min)

### Step 1: Create Consumer Project

```bash
mkdir eventhub-consumer
cd eventhub-consumer

# Use similar Spring Initializr setup as producer
```

### Step 2: Update pom.xml (Same as Producer)

Use the same dependencies as the producer project.

### Step 3: Create application.yml

```yaml
server:
  port: 8082

spring:
  application:
    name: eventhub-consumer
  
  cloud:
    azure:
      eventhubs:
        connection-string: ${EVENTHUB_CONNECTION_STRING}
        processor:
          checkpoint-store:
            container-name: checkpoints
            account-name: ${STORAGE_ACCOUNT_NAME}
            account-key: ${STORAGE_ACCOUNT_KEY}
    
    stream:
      function:
        definition: orderConsumer
      
      bindings:
        orderConsumer-in-0:
          destination: orders
          group: order-processor-group
          content-type: application/json
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000
            back-off-multiplier: 2.0
      
      eventhubs:
        bindings:
          orderConsumer-in-0:
            consumer:
              checkpoint:
                mode: MANUAL
                count: 10  # Checkpoint every 10 messages

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# Logging
logging:
  level:
    com.example.eventhub: DEBUG
    com.azure.messaging: INFO
```

### Step 4: Create Consumer Service

```java
package com.example.eventhub.consumer.service;

import com.azure.spring.messaging.checkpoint.Checkpointer;
import com.example.eventhub.consumer.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.azure.spring.messaging.AzureHeaders.CHECKPOINTER;

@Slf4j
@Configuration
public class OrderConsumerService {
    
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    
    @Bean
    public Consumer<Message<OrderEvent>> orderConsumer() {
        return message -> {
            try {
                OrderEvent order = message.getPayload();
                int count = processedCount.incrementAndGet();
                
                log.info("Processing order #{}: {} (Customer: {}, Amount: ${})",
                    count, order.getOrderId(), order.getCustomerId(), 
                    String.format("%.2f", order.getTotalAmount()));
                
                // Simulate processing time
                Thread.sleep(100);
                
                // Business logic
                processOrder(order);
                
                // Manual checkpoint every 10 messages
                if (count % 10 == 0) {
                    checkpoint(message);
                }
                
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Error processing order", e);
                // Could send to DLQ here
            }
        };
    }
    
    private void processOrder(OrderEvent order) {
        // Simulate business logic
        log.debug("Validating order: {}", order.getOrderId());
        log.debug("Updating inventory for product: {}", order.getProductId());
        log.debug("Order processing complete: {}", order.getOrderId());
    }
    
    private void checkpoint(Message<?> message) {
        Checkpointer checkpointer = message.getHeaders().get(CHECKPOINTER, Checkpointer.class);
        if (checkpointer != null) {
            checkpointer.success()
                .doOnSuccess(success -> 
                    log.debug("Checkpoint successful at message #{}", processedCount.get()))
                .doOnError(error -> 
                    log.error("Checkpoint failed", error))
                .subscribe();
        }
    }
    
    public int getProcessedCount() {
        return processedCount.get();
    }
    
    public int getErrorCount() {
        return errorCount.get();
    }
}
```

### Step 5: Create Stats Controller

```java
package com.example.eventhub.consumer.controller;

import com.example.eventhub.consumer.service.OrderConsumerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    
    private final OrderConsumerService consumerService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("processedCount", consumerService.getProcessedCount());
        stats.put("errorCount", consumerService.getErrorCount());
        stats.put("successRate", calculateSuccessRate());
        
        return ResponseEntity.ok(stats);
    }
    
    private double calculateSuccessRate() {
        int total = consumerService.getProcessedCount();
        int errors = consumerService.getErrorCount();
        if (total == 0) return 100.0;
        return ((total - errors) * 100.0) / total;
    }
}
```

### Step 6: Create Main Application

```java
package com.example.eventhub.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventhubConsumerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EventhubConsumerApplication.class, args);
    }
}
```

---

## Part 4: Testing (15 min)

### Step 1: Set Environment Variables

**Windows (PowerShell):**
```powershell
$env:EVENTHUB_CONNECTION_STRING="Endpoint=sb://..."
```

**Linux/Mac:**
```bash
export EVENTHUB_CONNECTION_STRING="Endpoint=sb://..."
```

### Step 2: Start Producer

```bash
cd eventhub-producer
mvn spring-boot:run
```

Verify it's running: http://localhost:8081/actuator/health

### Step 3: Start Consumer

```bash
cd eventhub-consumer
mvn spring-boot:run
```

Verify it's running: http://localhost:8082/actuator/health

### Step 4: Send Test Messages

**Send single order:**
```bash
curl -X POST http://localhost:8081/api/orders/send \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "orderId": "ORDER-001",
    "customerId": "CUST-100",
    "productId": "PROD-50",
    "quantity": 2,
    "totalAmount": 99.99,
    "status": "PENDING",
    "timestamp": "2024-01-15T10:30:00"
  }'
```

**Send batch:**
```bash
curl -X POST "http://localhost:8081/api/orders/send-batch?size=100"
```

**Check producer stats:**
```bash
curl http://localhost:8081/api/orders/stats
```

**Check consumer stats:**
```bash
curl http://localhost:8082/api/stats
```

### Step 5: Monitor in Azure Portal

1. Go to your Event Hub namespace
2. Click on "orders" Event Hub
3. View metrics:
   - Incoming Messages
   - Outgoing Messages
   - Throttled Requests
   - Consumer Lag

---

## Part 5: Advanced Scenarios (Optional)

### Scenario 1: Simulate High Throughput

```bash
# Send 10,000 messages
for i in {1..100}; do
  curl -X POST "http://localhost:8081/api/orders/send-batch?size=100"
  sleep 1
done
```

### Scenario 2: Scale Consumer

Start multiple consumer instances:
```bash
# Terminal 2
SERVER_PORT=8083 mvn spring-boot:run

# Terminal 3
SERVER_PORT=8084 mvn spring-boot:run
```

Each instance will consume from different partitions.

### Scenario 3: Implement Dead Letter Queue

Add to consumer:
```java
@Bean
public Consumer<Message<OrderEvent>> orderConsumer(StreamBridge streamBridge) {
    return message -> {
        try {
            processOrder(message.getPayload());
        } catch (Exception e) {
            log.error("Sending to DLQ", e);
            streamBridge.send("orders-dlq", message);
        }
    };
}
```

---

## Cleanup

```bash
# Delete Azure resources
az group delete --name $RESOURCE_GROUP --yes --no-wait
```

---

## Lab Verification Checklist

- [ ] Event Hub namespace created
- [ ] Producer application running on port 8081
- [ ] Consumer application running on port 8082
- [ ] Successfully sent single message
- [ ] Successfully sent batch of 100 messages
- [ ] Consumer processed all messages
- [ ] Verified stats in both applications
- [ ] Checked metrics in Azure Portal
- [ ] Tested with multiple consumer instances

---

## Troubleshooting

**Connection issues:**
- Verify connection string is correct
- Check firewall rules in Event Hub namespace
- Ensure namespace is in Standard or Premium tier

**Consumer not receiving messages:**
- Check consumer group name
- Verify partition count matches
- Look for errors in consumer logs
- Check checkpoint storage configuration

**Performance issues:**
- Increase partition count
- Scale out consumers
- Adjust prefetch and batch settings
- Monitor Event Hub metrics for throttling

---

## Next Steps
- Implement retry logic with exponential backoff
- Add correlation IDs for distributed tracing
- Integrate with Application Insights
- Implement event replay functionality
