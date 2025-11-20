package com.example.resilience.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Configuration
public class MetricsConfig {
    
    @Bean
    public Counter requestCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.total")
            .description("Total number of API requests")
            .register(registry);
    }
    
    @Bean
    public Counter successCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.success")
            .description("Number of successful requests")
            .register(registry);
    }
    
    @Bean
    public Counter failureCounter(MeterRegistry registry) {
        return Counter.builder("api.requests.failure")
            .description("Number of failed requests")
            .register(registry);
    }
    
    @Bean
    public Timer requestTimer(MeterRegistry registry) {
        return Timer.builder("api.requests.duration")
            .description("API request duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
}
