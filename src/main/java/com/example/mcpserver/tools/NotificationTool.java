package com.example.mcpserver.tools;

import com.example.mcpserver.model.NotificationRecord;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Notification Tool - Exposes notification capabilities to AI agents.
 * <p>
 * This tool allows an LLM to send notifications via Slack, email, etc.
 * In production, integrate with actual notification services.
 */
@Component
public class NotificationTool {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  // Simulated notification log - in production, use actual messaging services
  private final Map<String, NotificationRecord> sentNotifications = new ConcurrentHashMap<>();

  /**
   * Send a Slack message to a channel or user.
   */
  @Tool(description = "Send a message to a Slack channel or user. Specify the channel (e.g., '#general', '#engineering') or user (e.g., '@john'). Set urgent=true for important notifications.")
  public Map<String, Object> sendSlackMessage(
      @ToolParam(description = "Slack channel (e.g., '#general') or user (e.g., '@john')") String channel,
      @ToolParam(description = "Message content") String message,
      @ToolParam(description = "Whether this is urgent") Boolean urgent) {
    if (channel == null || channel.isBlank()) {
      return Map.of("error", "Channel is required");
    }
    if (message == null || message.isBlank()) {
      return Map.of("error", "Message is required");
    }

    String notificationId = generateId();
    boolean isUrgent = urgent != null && urgent;

    NotificationRecord record = new NotificationRecord(
        notificationId, "slack", channel, message, isUrgent, LocalDateTime.now(), "delivered"
    );
    sentNotifications.put(notificationId, record);

    // In production: integrate with Slack API
    // slackClient.chat().postMessage(channel, message);

    return Map.of(
        "success", true,
        "notificationId", notificationId,
        "channel", channel,
        "status", "delivered",
        "timestamp", record.timestamp().format(DATE_FORMATTER),
        "message", isUrgent ? "🚨 URGENT: " + message : message
    );
  }

  /**
   * Send an email notification.
   */
  @Tool(description = "Send an email notification. Provide recipient email address, subject line, and body content.")
  public Map<String, Object> sendEmail(
      @ToolParam(description = "Recipient email address") String to,
      @ToolParam(description = "Email subject") String subject,
      @ToolParam(description = "Email body content") String body) {
    if (to == null || to.isBlank()) {
      return Map.of("error", "Recipient email is required");
    }
    if (subject == null || subject.isBlank()) {
      return Map.of("error", "Subject is required");
    }
    if (body == null || body.isBlank()) {
      return Map.of("error", "Email body is required");
    }

    String notificationId = generateId();

    NotificationRecord record = new NotificationRecord(
        notificationId, "email", to, subject + ": " + body, false, LocalDateTime.now(), "queued"
    );
    sentNotifications.put(notificationId, record);

    // In production: integrate with email service (SendGrid, SES, etc.)
    // emailService.send(to, subject, body);

    return Map.of(
        "success", true,
        "notificationId", notificationId,
        "recipient", to,
        "subject", subject,
        "status", "queued",
        "timestamp", record.timestamp().format(DATE_FORMATTER)
    );
  }

  /**
   * Get the status of a sent notification.
   */
  @Tool(description = "Check the delivery status of a previously sent notification. Provide the notification ID returned when the message was sent.")
  public Map<String, Object> getNotificationStatus(
      @ToolParam(description = "The notification ID to check") String notificationId) {
    if (notificationId == null || notificationId.isBlank()) {
      return Map.of("error", "Notification ID is required");
    }

    NotificationRecord record = sentNotifications.get(notificationId);
    if (record == null) {
      return Map.of("error", "Notification not found", "notificationId", notificationId);
    }

    return Map.of(
        "notificationId", record.id(),
        "type", record.type(),
        "recipient", record.recipient(),
        "status", record.status(),
        "urgent", record.urgent(),
        "sentAt", record.timestamp().format(DATE_FORMATTER)
    );
  }

  /**
   * List recent notifications.
   */
  @Tool(description = "List recently sent notifications. Optionally specify a limit (default 10).")
  public List<Map<String, Object>> listRecentNotifications(
      @ToolParam(description = "Maximum number to return (default 10)") Integer limit) {
    int maxResults = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;

    return sentNotifications.values().stream()
        .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
        .limit(maxResults)
        .map(record -> Map.<String, Object>of(
            "notificationId", record.id(),
            "type", record.type(),
            "recipient", record.recipient(),
            "status", record.status(),
            "sentAt", record.timestamp().format(DATE_FORMATTER)
        ))
        .toList();
  }

  private String generateId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
