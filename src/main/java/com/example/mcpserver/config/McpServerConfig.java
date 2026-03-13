package com.example.mcpserver.config;

import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Configuration
 * <p>
 * Spring AI MCP Server auto-discovers @Tool annotated methods in @Component classes and exposes
 * them via the MCP protocol.
 * <p>
 * Configuration is handled via application.yml
 */
@Configuration
public class McpServerConfig {
  // Auto-configuration handles tool registration
  // Add any custom MCP server beans here if needed
}
