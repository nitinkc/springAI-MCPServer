# Spring AI MCP Server - Copilot Instructions

## Project Overview

This is a Spring Boot 3.4.x application implementing an MCP (Model Context Protocol) Server using
Spring AI. It exposes Java methods as tools that any LLM can connect to.

## Tech Stack

- Java 21
- Spring Boot 3.4.x
- Spring AI MCP Server 1.0.0
- Spring Security
- Micrometer (Observability)
- H2 Database (Demo)
- Maven

## Key Annotations

- `@Tool` - Expose methods as AI-callable tools with description
- `@ToolParam` - Document tool parameters for LLM understanding
- `@Component` - Spring-managed tool beans are auto-registered

## Project Structure

```
src/main/java/com/example/mcpserver/
├── McpServerApplication.java      # Main entry point
├── config/
│   ├── McpServerConfig.java       # MCP server settings
│   └── SecurityConfig.java        # Security configuration
├── model/
│   ├── Document.java              # Document record model
│   └── NotificationRecord.java    # Notification record model
├── util/
│   ├── WorkspacePathResolver.java # Path validation & resolution
│   └── CommandExecutor.java       # Shell command execution
└── tools/
    ├── DatabaseTool.java          # Database query tools
    ├── DocumentTool.java          # Document search tools
    ├── FileTool.java              # File system operations
    ├── GitTool.java               # Git operations (clone, branch, commit)
    ├── GitHubTool.java            # GitHub API operations
    ├── NotificationTool.java      # Notification tools
    └── TestRunnerTool.java        # Test execution tools
```

## Running the Project

```bash
./mvnw spring-boot:run
```

## Adding New Tools

1. Create a new `@Component` class in the `tools` package
2. Add methods with `@Tool` annotation and clear descriptions
3. Use `@ToolParam` to document parameters
4. Use `WorkspacePathResolver` for file operations (security)
5. Use `CommandExecutor` for shell commands
6. Return structured data (Maps, records, DTOs)

## Testing MCP Connection

- Server: `http://localhost:8080`
- MCP Endpoint: `http://localhost:8080/mcp`
- Health: `http://localhost:8080/actuator/health`


