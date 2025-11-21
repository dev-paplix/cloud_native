package com.example.resilience.model;

import java.time.LocalDateTime;

/**
 * Response model for API responses
 */
public class ApiResponse {
    private String message;
    private String status;
    private LocalDateTime timestamp;
    private int attemptNumber;
    private String patternUsed;

    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(String message, String status) {
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(String message, String status, String patternUsed) {
        this.message = message;
        this.status = status;
        this.patternUsed = patternUsed;
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(String message, String status, int attemptNumber, String patternUsed) {
        this.message = message;
        this.status = status;
        this.attemptNumber = attemptNumber;
        this.patternUsed = patternUsed;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getPatternUsed() {
        return patternUsed;
    }

    public void setPatternUsed(String patternUsed) {
        this.patternUsed = patternUsed;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "message='" + message + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                ", attemptNumber=" + attemptNumber +
                ", patternUsed='" + patternUsed + '\'' +
                '}';
    }
}
