package com.example.mcpserver.model;

import java.time.LocalDateTime;

/**
 * Represents a notification record.
 */
public record NotificationRecord(
    String id,
    String type,
    String recipient,
    String content,
    boolean urgent,
    LocalDateTime timestamp,
    String status
) {
}

