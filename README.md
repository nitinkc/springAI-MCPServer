# Spring AI MCP Server

> **The "USB-C Port" for AI** - Build once, connect any LLM

This project demonstrates how to build an enterprise-grade **Model Context Protocol (MCP) Server**
using Spring AI. It exposes Java methods as tools that any LLM (Claude, GPT-4o, Gemini) can connect
to using a standardized protocol.

## 🎯 The Problem This Solves

**The M x N Problem**: If you want your Spring Boot app to connect an LLM to a database, a PDF, and
a Slack channel, you traditionally had to write three different connectors with three different JSON
schemas. Brittle, unscalable, and a maintenance nightmare.

**The Solution**: MCP provides a standardized protocol. Build the server once, and any
MCP-compatible LLM can instantly plug in to your data and tools.

## ✨ Features

| Feature                | Description                                                                 |
|------------------------|-----------------------------------------------------------------------------|
| `@McpTool`             | Instantly expose Java methods as functional tools for AI agents             |
| **Auto-Configuration** | No manual JSON schema generation - Spring AI handles reflection and mapping |
| **Enterprise-Grade**   | Spring Security, Micrometer observability, GraalVM native support           |
| **3 Example Tools**    | Database queries, document search, notifications                            |

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+

### Run the Server

```bash
# Build and run
./mvnw spring-boot:run

# Or build first, then run
./mvnw clean package
java -jar target/spring-ai-mcp-server-0.0.1-SNAPSHOT.jar
```

The MCP server will start on `http://localhost:8080`

### Environment Variables

```bash
# Required for GitHub tools - set your GitHub Personal Access Token
export GITHUB_TOKEN=$(gh auth token)
# Or manually: export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
```

### Verify It's Running

```bash
# Health check
curl http://localhost:8080/actuator/health

# MCP endpoint (SSE transport)
curl http://localhost:8080/mcp
```

## 🧪 Create a Test Repository

We provide a script to create a dummy GitHub repository for experimentation:

```bash
# Make sure you're authenticated with GitHub CLI
gh auth login

# Run the setup script
./scripts/create-test-repo.sh [repo-name] [github-username]

# Example:
./scripts/create-test-repo.sh mcp-test-sandbox
```

This creates a simple Java calculator project with:

- Sample source code with TODO items
- Unit tests
- 3 GitHub issues for the AI to work on

## 🔧 Available Tools

### 1. Database Tools (`DatabaseTool.java`)

| Tool              | Description                           |
|-------------------|---------------------------------------|
| `queryProducts`   | Search products by category and price |
| `checkInventory`  | Check stock levels for a product      |
| `getSalesSummary` | Get aggregate sales statistics        |

### 2. Document Tools (`DocumentTool.java`)

| Tool              | Description                        |
|-------------------|------------------------------------|
| `searchDocuments` | Search company knowledge base      |
| `getDocument`     | Retrieve full document content     |
| `listCategories`  | List available document categories |

### 3. Notification Tools (`NotificationTool.java`)

| Tool                      | Description                           |
|---------------------------|---------------------------------------|
| `sendSlackMessage`        | Send Slack messages to channels/users |
| `sendEmail`               | Send email notifications              |
| `getNotificationStatus`   | Check notification delivery status    |
| `listRecentNotifications` | View recently sent notifications      |

### 4. GitHub Tools (`GitHubTool.java`) 🆕

| Tool                | Description                             |
|---------------------|-----------------------------------------|
| `getIssue`          | Fetch GitHub issue details and comments |
| `listIssues`        | List open issues in a repository        |
| `listFiles`         | Browse repository file structure        |
| `getFileContent`    | Read file content from GitHub           |
| `createPullRequest` | Create a PR from a feature branch       |
| `addIssueComment`   | Add comments to issues                  |

### 5. Git Tools (`GitTool.java`) 🆕

| Tool               | Description                      |
|--------------------|----------------------------------|
| `cloneRepository`  | Clone a GitHub repo to workspace |
| `createBranch`     | Create and checkout a new branch |
| `getStatus`        | Get current git status           |
| `commitAndPush`    | Stage, commit, and push changes  |
| `listBranches`     | List repository branches         |
| `getCommitHistory` | View recent commits              |
| `getWorkspacePath` | Get workspace directory path     |

### 6. File Tools (`FileTool.java`) 🆕

| Tool            | Description                       |
|-----------------|-----------------------------------|
| `readFile`      | Read file contents from workspace |
| `writeFile`     | Create or overwrite files         |
| `appendToFile`  | Append content to existing files  |
| `listDirectory` | List directory contents           |
| `searchFiles`   | Search files by pattern           |
| `deleteFile`    | Delete a file                     |

### 7. Test Runner Tools (`TestRunnerTool.java`) 🆕

| Tool                   | Description                    |
|------------------------|--------------------------------|
| `runAllTests`          | Run all tests in a project     |
| `runTestClass`         | Run a specific test class      |
| `runTestMethod`        | Run a specific test method     |
| `compileProject`       | Compile without running tests  |
| `runTestsWithCoverage` | Run tests with JaCoCo coverage |

## 🔌 Connecting MCP Clients

### Claude Desktop

Add to your Claude Desktop configuration (
`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "spring-ai-server": {
      "url": "http://localhost:8080/mcp",
      "transport": "sse"
    }
  }
}
```

### Programmatic Client (Spring AI)

```java
McpClient client = McpClient.builder()
    .serverUrl("http://localhost:8080/mcp")
    .transport(McpTransport.SSE)
    .build();

// List available tools
List<Tool> tools = client.listTools();

// Call a tool
ToolResult result = client.callTool("query_products", 
    Map.of("category", "Electronics", "maxPrice", 500.0));
```

## 📊 Observability

### Endpoints

- **Health**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`
- **Prometheus**: `http://localhost:8080/actuator/prometheus`

### H2 Console (Development)

Access the database console at `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:mcpdb`
- Username: `sa`
- Password: (empty)

## 🏗️ Project Structure

```
src/main/java/com/example/mcpserver/
├── McpServerApplication.java      # Main application entry point
├── config/
│   ├── McpServerConfig.java       # MCP server configuration
│   └── SecurityConfig.java        # Spring Security setup
└── tools/
    ├── DatabaseTool.java          # @McpTool - Database queries
    ├── DocumentTool.java          # @McpTool - Document search
    └── NotificationTool.java      # @McpTool - Notifications
```

## 🔒 Security

The default configuration:

- MCP endpoints (`/mcp/**`) are open for LLM access
- Actuator health/info endpoints are public
- Other actuator endpoints require authentication
- H2 console is enabled for development only

**For production**, configure proper authentication:

- OAuth2/OIDC for user authentication
- API keys or mTLS for machine-to-machine
- Rate limiting and audit logging

## 🛠️ Creating Your Own Tools

```java
@Component
public class MyCustomTool {

    @McpTool(
        name = "my_tool_name",
        description = "Clear description of what this tool does for the LLM"
    )
    public Map<String, Object> myMethod(String param1, Integer param2) {
        // Your business logic here
        return Map.of("result", "success", "data", yourData);
    }
}
```

**Best Practices:**

1. Write clear, detailed descriptions - LLMs use these to understand when to call the tool
2. Use descriptive parameter names
3. Return structured data (Maps, DTOs) for easy AI parsing
4. Handle errors gracefully with meaningful error messages
