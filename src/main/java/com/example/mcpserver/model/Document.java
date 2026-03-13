package com.example.mcpserver.model;

/**
 * Represents a document in the knowledge base.
 */
public record Document(
    String id,
    String title,
    String category,
    String content
) {
}

