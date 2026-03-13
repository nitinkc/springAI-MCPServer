package com.example.mcpserver.tools;

import com.example.mcpserver.model.Document;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Document Tool - Exposes document search capabilities to AI agents.
 * <p>
 * This tool allows an LLM to search and retrieve documents from a knowledge base.
 */
@Component
public class DocumentTool {

  // Simulated document store - in production, use a vector database or search engine
  private final List<Document> documents = List.of(
      new Document("DOC001", "Company Policies", "employee-handbook",
          "Our company values integrity, innovation, and collaboration. " +
              "All employees must complete annual compliance training. " +
              "Remote work is permitted with manager approval. " +
              "Vacation policy allows 20 days per year for full-time employees."),
      new Document("DOC002", "Product Roadmap 2026", "internal",
          "Q1: Launch MCP integration platform. " +
              "Q2: Add support for additional LLM providers. " +
              "Q3: Enterprise security features including SSO and audit logs. " +
              "Q4: Self-hosted deployment options and Kubernetes support."),
      new Document("DOC003", "API Documentation", "technical",
          "The REST API supports JSON and XML formats. " +
              "Authentication is via Bearer tokens or API keys. " +
              "Rate limiting is set to 1000 requests per minute. " +
              "Webhook support available for real-time notifications."),
      new Document("DOC004", "Onboarding Guide", "employee-handbook",
          "Welcome to the team! Your first week includes orientation sessions. " +
              "IT will provide your laptop and access credentials. " +
              "Complete the security awareness training within 7 days. " +
              "Schedule a 1:1 with your manager for goal setting.")
  );

  /**
   * Search documents by keyword.
   */
  @Tool(description = "Search through company documents and knowledge base. Provide keywords to find relevant documents. Can filter by category (e.g., 'technical', 'employee-handbook', 'internal').")
  public List<Map<String, Object>> searchDocuments(
      @ToolParam(description = "Keywords to search for") String query,
      @ToolParam(description = "Category filter (optional)") String category) {
    if (query == null || query.isBlank()) {
      return List.of(Map.of("error", "Search query is required"));
    }

    String searchLower = query.toLowerCase();

    return documents.stream()
        .filter(doc -> category == null || category.isBlank() ||
            doc.category().equalsIgnoreCase(category))
        .filter(doc -> doc.title().toLowerCase().contains(searchLower) ||
            doc.content().toLowerCase().contains(searchLower))
        .map(doc -> {
          Map<String, Object> result = new HashMap<>();
          result.put("documentId", doc.id());
          result.put("title", doc.title());
          result.put("category", doc.category());
          result.put("snippet", extractSnippet(doc.content(), searchLower));
          return result;
        })
        .collect(Collectors.toList());
  }

  /**
   * Get full document content by ID.
   */
  @Tool(description = "Retrieve the full content of a specific document by its ID. Use this after searching to get complete document text.")
  public Map<String, Object> getDocument(
      @ToolParam(description = "The document ID to retrieve") String documentId) {
    if (documentId == null || documentId.isBlank()) {
      return Map.of("error", "Document ID is required");
    }

    return documents.stream()
        .filter(doc -> doc.id().equalsIgnoreCase(documentId))
        .findFirst()
        .map(doc -> Map.<String, Object>of(
            "documentId", doc.id(),
            "title", doc.title(),
            "category", doc.category(),
            "content", doc.content()
        ))
        .orElse(Map.of("error", "Document not found", "documentId", documentId));
  }

  /**
   * List available document categories.
   */
  @Tool(description = "List all available document categories and how many documents are in each. Useful for understanding what types of documents are available.")
  public List<Map<String, Object>> listCategories() {
    return documents.stream()
        .collect(Collectors.groupingBy(Document::category, Collectors.counting()))
        .entrySet().stream()
        .map(e -> Map.<String, Object>of(
            "category", e.getKey(),
            "documentCount", e.getValue()
        ))
        .collect(Collectors.toList());
  }

  private String extractSnippet(String content, String query) {
    int index = content.toLowerCase().indexOf(query);
    if (index == -1) {
      return content.substring(0, Math.min(100, content.length())) + "...";
    }
    int start = Math.max(0, index - 30);
    int end = Math.min(content.length(), index + query.length() + 70);
    String snippet = content.substring(start, end);
    if (start > 0) {
      snippet = "..." + snippet;
    }
    if (end < content.length()) {
      snippet = snippet + "...";
    }
    return snippet;
  }
}
