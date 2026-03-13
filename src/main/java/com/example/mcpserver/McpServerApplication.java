package com.example.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI MCP Server Application
 * <p>
 * This application exposes Java methods as MCP tools that any LLM (Claude, GPT-4o, Gemini) can
 * connect to using the Model Context Protocol.
 * <p>
 * Think of MCP as the "USB-C port" for AI - build once, connect any LLM.
 */
@SpringBootApplication
public class McpServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(McpServerApplication.class, args);
  }
}
