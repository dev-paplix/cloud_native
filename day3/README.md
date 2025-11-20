# Day 3: Async Flow & Observability - Complete Guide

## 📚 Overview

This directory contains comprehensive training materials for Day 3 of the Cloud Native course, covering Event-Driven Architecture and Observability patterns for production-ready microservices.

## 📖 Course Materials

### Lectures

1. **[Event Hub Lecture](./01-EVENT_HUB_LECTURE.md)** (60 min)
   - Event-driven architecture patterns
   - Azure Event Hub overview and capabilities
   - Spring Cloud Stream integration
   - Event Hub vs Kafka vs Service Bus comparison
   - Advanced patterns (Event Sourcing, CQRS, Saga)

2. **[Observability Lecture](./03-OBSERVABILITY_LECTURE.md)** (45 min)
   - Three pillars: Metrics, Logs, Traces
   - Azure Application Insights deep dive
   - Micrometer for metrics collection
   - Distributed tracing fundamentals
   - Application Insights Agent usage

3. **[Logging Lecture](./05-LOGGING_LECTURE.md)** (30 min)
   - Structured logging principles
   - Correlation IDs for distributed tracing
   - Spring Cloud Sleuth configuration
   - JSON logging with Logback
   - Debugging distributed requests

4. **[SRE & Alerts Lecture](./07-SRE_ALERTS_LECTURE.md)** (45 min)
   - Site Reliability Engineering principles
   - SLIs, SLOs, and Error Budgets
   - The Four Golden Signals
   - Effective alerting strategies
   - Reducing toil through automation

### Hands-On Labs

1. **[Event Hub Lab](./02-EVENT_HUB_LAB.md)** (90 min)
   - Set up Azure Event Hub
   - Build producer microservice
   - Build consumer microservice
   - Test high-throughput scenarios
   - Implement error handling

2. **[Observability Lab](./04-OBSERVABILITY_LAB.md)** (60 min)
   - Create Application Insights resource
   - Integrate App Insights SDK
   - Configure custom metrics with Micrometer
   - Implement custom telemetry
   - Test with Application Insights Agent

3. **[Logging Lab](./06-LOGGING_LAB.md)** (45 min)
   - Configure Spring Cloud Sleuth
   - Set up JSON logging with Logback
   - Implement structured logging
   - Test trace propagation
   - Query logs in Azure Monitor

4. **[Azure Monitor Alerts Lab](./08-AZURE_MONITOR_LAB.md)** (60 min)
   - Create action groups
   - Configure Four Golden Signal alerts
   - Implement SLO-based alerts
   - Simulate failures
   - Refine alert thresholds

### Code Examples

- **[Event Hub Producer](./code/eventhub-producer/)** - Complete Spring Boot microservice
- **[Event Hub Consumer](./code/eventhub-consumer/)** - Complete Spring Boot microservice

## 🎯 Learning Objectives

By completing Day 3, you will be able to:

### Event-Driven Architecture
- ✅ Design event-driven microservices architectures
- ✅ Implement producer/consumer patterns with Azure Event Hub
- ✅ Handle high-throughput event processing
- ✅ Implement error handling and retry strategies
- ✅ Choose between Event Hub, Kafka, and Service Bus

### Observability
- ✅ Implement the three pillars of observability
- ✅ Integrate Azure Application Insights
- ✅ Create custom metrics with Micrometer
- ✅ Implement distributed tracing
- ✅ Use Application Insights Agent for zero-code instrumentation

### Logging
- ✅ Implement structured JSON logging
- ✅ Configure correlation IDs for distributed tracing
- ✅ Set up Spring Cloud Sleuth
- ✅ Debug requests across microservices
- ✅ Query logs effectively

### SRE & Alerting
- ✅ Apply SRE principles to operations
- ✅ Define and track SLIs and SLOs
- ✅ Calculate and manage error budgets
- ✅ Monitor the Four Golden Signals
- ✅ Create effective alerts that reduce toil

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- Azure subscription
- Azure CLI
- Docker Desktop (optional)
- IDE (IntelliJ IDEA or VS Code)

### Setup Steps

1. **Clone and navigate to Day 3 materials:**
   ```bash
   cd resilience4j-demo/day3
   ```

2. **Create Azure resources:**
   ```bash
   # Event Hub
   az eventhubs namespace create --name eventhub-lab-xyz --resource-group rg-lab

   # Application Insights
   az monitor app-insights component create --app appinsights-lab --location eastus --resource-group rg-lab
   ```

3. **Set environment variables:**
   ```powershell
   # Windows PowerShell
   $env:EVENTHUB_CONNECTION_STRING="Endpoint=sb://..."
   $env:APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=..."
   ```

4. **Run the examples:**
   ```bash
   # Producer
   cd code/eventhub-producer
   mvn spring-boot:run

   # Consumer (in another terminal)
   cd code/eventhub-consumer
   mvn spring-boot:run
   ```

## 📋 Lab Sequence

### Recommended Order

1. **Start with Event Hub materials** (3 hours)
   - Read lecture → Complete lab → Test code examples
   
2. **Move to Observability** (2 hours)
   - Read lecture → Complete lab → Verify dashboards
   
3. **Implement Logging** (1.5 hours)
   - Read lecture → Complete lab → Test tracing
   
4. **Finish with SRE & Alerts** (2 hours)
   - Read lecture → Complete lab → Simulate failures

**Total Time:** ~8-9 hours (full day with breaks)

## 🔑 Key Concepts

### Event-Driven Architecture
- **Event Hub**: High-throughput event streaming platform
- **Partitioning**: Parallel processing for scalability
- **Consumer Groups**: Independent event stream views
- **Checkpointing**: Track processed events

### Observability
- **Metrics**: Time-series numerical data
- **Logs**: Discrete events with context
- **Traces**: Request flow across services
- **Correlation**: Link related telemetry

### SRE Principles
- **SLI**: Service Level Indicator (what to measure)
- **SLO**: Service Level Objective (target value)
- **Error Budget**: Allowed unreliability
- **Toil**: Repetitive manual work to eliminate

### Four Golden Signals
1. **Latency**: Request duration
2. **Traffic**: Request volume
3. **Errors**: Failure rate
4. **Saturation**: Resource utilization

## 🛠️ Technologies Used

- **Spring Boot 3.2.0**
- **Spring Cloud Stream 2023.0.0**
- **Azure Event Hubs**
- **Azure Application Insights**
- **Spring Cloud Sleuth**
- **Micrometer**
- **Logback with Logstash encoder**
- **Azure Monitor**

## 📊 Sample Metrics

### Event Hub Performance
```
Throughput: 10,000+ events/second
Latency: < 50ms (producer to consumer)
Partitions: 4 (configurable)
Retention: 1-90 days
```

### Application Insights
```
Telemetry ingestion: Real-time
Retention: 90 days
Query response: < 2 seconds
Live metrics: Sub-second updates
```

## 🐛 Troubleshooting

### Event Hub Issues
**Problem:** Connection timeout
- Check connection string
- Verify firewall rules
- Ensure namespace is active

**Problem:** Consumer not receiving
- Check consumer group name
- Verify partition assignment
- Review checkpoint configuration

### Observability Issues
**Problem:** No telemetry in App Insights
- Verify connection string
- Check 2-3 minute initial delay
- Review SDK configuration

**Problem:** Missing trace IDs
- Ensure Sleuth dependency
- Check logging pattern
- Verify propagation type

## 📚 Additional Resources

### Azure Documentation
- [Event Hubs Documentation](https://docs.microsoft.com/azure/event-hubs/)
- [Application Insights](https://docs.microsoft.com/azure/azure-monitor/app/app-insights-overview)
- [Azure Monitor](https://docs.microsoft.com/azure/azure-monitor/)

### Spring Documentation
- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth)
- [Micrometer](https://micrometer.io/docs)

### SRE Resources
- [Google SRE Book](https://sre.google/sre-book/table-of-contents/)
- [SRE Workbook](https://sre.google/workbook/table-of-contents/)

### Best Practices
- [12-Factor App](https://12factor.net/)
- [Cloud Design Patterns](https://docs.microsoft.com/azure/architecture/patterns/)

## 🎓 Assessment

### Knowledge Check

After completing Day 3, you should be able to answer:

1. What are the differences between Event Hub and Service Bus?
2. What are the three pillars of observability?
3. How does distributed tracing work?
4. What are the Four Golden Signals?
5. How do you calculate error budget?
6. When should you alert on symptoms vs causes?

### Practical Skills

You should be able to:

- [ ] Build event-driven microservices
- [ ] Implement producer/consumer patterns
- [ ] Configure Application Insights
- [ ] Create custom metrics
- [ ] Implement structured logging
- [ ] Set up distributed tracing
- [ ] Configure effective alerts
- [ ] Calculate and track SLOs

## 🆘 Getting Help

### During the Course
- Ask your instructor
- Collaborate with peers
- Review lecture notes
- Check troubleshooting sections

### After the Course
- Azure documentation
- Stack Overflow
- GitHub issues
- Azure support

## 📝 Notes

- All code examples are production-ready templates
- Configuration values need to be updated for your environment
- Security best practices are included but simplified for learning
- Scale parameters should be adjusted for production workloads

## ✅ Completion Checklist

- [ ] Completed all 4 lectures
- [ ] Finished all 4 hands-on labs
- [ ] Tested Event Hub producer/consumer
- [ ] Verified Application Insights integration
- [ ] Implemented structured logging
- [ ] Created alerts in Azure Monitor
- [ ] Simulated and debugged failures
- [ ] Created SRE dashboard

---

**Ready to start?** Begin with [DAY3_OVERVIEW.md](../DAY3_OVERVIEW.md) for the full course introduction!
