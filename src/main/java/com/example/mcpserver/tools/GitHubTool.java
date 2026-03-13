package com.example.mcpserver.tools;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * GitHub Tool - Exposes GitHub API capabilities to AI agents.
 * <p>
 * This tool allows an LLM to: - Read issues from a repository - List files in a repository - Create
 * pull requests - Add comments to issues
 */
@Component
public class GitHubTool {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
      new ParameterizedTypeReference<>() {
      };
  private final WebClient webClient;
  private final String githubToken;

  public GitHubTool(
      @Value("${github.token:}") String githubToken,
      @Value("${github.api-url:https://api.github.com}") String apiUrl) {
    this.githubToken = githubToken;
    this.webClient = WebClient.builder()
        .baseUrl(apiUrl)
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build();
  }

  /**
   * Get details of a specific GitHub issue.
   */
  @Tool(description = "Fetch details of a GitHub issue including title, body, labels, and comments. Use this to understand what changes are requested.")
  public Map<String, Object> getIssue(
      @ToolParam(description = "Repository owner (e.g., 'octocat')") String owner,
      @ToolParam(description = "Repository name (e.g., 'hello-world')") String repo,
      @ToolParam(description = "Issue number") Integer issueNumber) {

    if (owner == null || repo == null || issueNumber == null) {
      return Map.of("error", "owner, repo, and issueNumber are required");
    }

    try {
      Map<String, Object> issue = webClient.get()
          .uri("/repos/{owner}/{repo}/issues/{issue_number}", owner, repo, issueNumber)
          .headers(h -> addAuthHeader(h))
          .retrieve()
          .bodyToMono(MAP_TYPE)
          .block();

      List<Map<String, Object>> comments = webClient.get()
          .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
          .headers(h -> addAuthHeader(h))
          .retrieve()
          .bodyToMono(LIST_MAP_TYPE)
          .block();

      return Map.of(
          "number", issue.get("number"),
          "title", issue.get("title"),
          "body", issue.get("body") != null ? issue.get("body") : "",
          "state", issue.get("state"),
          "labels", issue.get("labels"),
          "user", ((Map<?, ?>) issue.get("user")).get("login"),
          "created_at", issue.get("created_at"),
          "comments", comments.stream()
              .map(c -> Map.of(
                  "user", ((Map<?, ?>) c.get("user")).get("login"),
                  "body", c.get("body"),
                  "created_at", c.get("created_at")
              ))
              .toList()
      );
    } catch (Exception e) {
      return Map.of("error", "Failed to fetch issue: " + e.getMessage());
    }
  }

  /**
   * List open issues in a repository.
   */
  @Tool(description = "List open issues in a GitHub repository. Can filter by labels. Returns issue numbers, titles, and labels.")
  public List<Map<String, Object>> listIssues(
      @ToolParam(description = "Repository owner") String owner,
      @ToolParam(description = "Repository name") String repo,
      @ToolParam(description = "Filter by label (optional)") String label,
      @ToolParam(description = "Maximum number of issues to return (default 10)") Integer limit) {

    if (owner == null || repo == null) {
      return List.of(Map.of("error", "owner and repo are required"));
    }

    int maxResults = (limit != null && limit > 0) ? Math.min(limit, 30) : 10;

    try {
      String uri = label != null && !label.isBlank()
          ? "/repos/{owner}/{repo}/issues?state=open&labels={label}&per_page={limit}"
          : "/repos/{owner}/{repo}/issues?state=open&per_page={limit}";

      List<Map<String, Object>> issues = webClient.get()
          .uri(uri, owner, repo, label, maxResults)
          .headers(h -> addAuthHeader(h))
          .retrieve()
          .bodyToMono(LIST_MAP_TYPE)
          .block();

      return issues.stream()
          .filter(i -> !i.containsKey("pull_request"))
          .map(i -> Map.<String, Object>of(
              "number", i.get("number"),
              "title", i.get("title"),
              "labels", ((List<?>) i.get("labels")).stream()
                  .map(l -> ((Map<?, ?>) l).get("name"))
                  .toList(),
              "created_at", i.get("created_at")
          ))
          .toList();
    } catch (Exception e) {
      return List.of(Map.of("error", "Failed to list issues: " + e.getMessage()));
    }
  }

  /**
   * List files in a repository directory.
   */
  @Tool(description = "List files and directories in a GitHub repository path. Use this to explore the project structure.")
  public List<Map<String, Object>> listFiles(
      @ToolParam(description = "Repository owner") String owner,
      @ToolParam(description = "Repository name") String repo,
      @ToolParam(description = "Path within repository (empty or '/' for root)") String path,
      @ToolParam(description = "Branch name (default: main)") String branch) {

    if (owner == null || repo == null) {
      return List.of(Map.of("error", "owner and repo are required"));
    }

    String targetPath = (path == null || path.isBlank() || path.equals("/")) ? "" : path;
    String targetBranch = (branch == null || branch.isBlank()) ? "main" : branch;

    try {
      List<Map<String, Object>> contents = webClient.get()
          .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
              owner, repo, targetPath, targetBranch)
          .headers(h -> addAuthHeader(h))
          .retrieve()
          .bodyToMono(LIST_MAP_TYPE)
          .block();

      return contents.stream()
          .map(c -> Map.<String, Object>of(
              "name", c.get("name"),
              "path", c.get("path"),
              "type", c.get("type"),
              "size", c.get("size") != null ? c.get("size") : 0
          ))
          .toList();
    } catch (Exception e) {
      return List.of(Map.of("error", "Failed to list files: " + e.getMessage()));
    }
  }

  /**
   * Get file content from GitHub.
   */
  @Tool(description = "Read the content of a file from a GitHub repository. Returns the decoded file content.")
  public Map<String, Object> getFileContent(
      @ToolParam(description = "Repository owner") String owner,
      @ToolParam(description = "Repository name") String repo,
      @ToolParam(description = "File path within repository") String path,
      @ToolParam(description = "Branch name (default: main)") String branch) {

    if (owner == null || repo == null || path == null) {
      return Map.of("error", "owner, repo, and path are required");
    }

    String targetBranch = (branch == null || branch.isBlank()) ? "main" : branch;

    try {
      Map<String, Object> file = webClient.get()
          .uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}",
              owner, repo, path, targetBranch)
          .headers(h -> addAuthHeader(h))
          .retrieve()
          .bodyToMono(MAP_TYPE)
          .block();

      if (!"file".equals(file.get("type"))) {
        return Map.of("error", "Path is not a file");
      }

      String encodedContent = (String) file.get("content");
      String content = new String(java.util.Base64.getMimeDecoder().decode(encodedContent));

      return Map.of(
          "path", file.get("path"),
          "name", file.get("name"),
          "size", file.get("size"),
          "sha", file.get("sha"),
          "content", content
      );
    } catch (Exception e) {
      return Map.of("error", "Failed to get file: " + e.getMessage());
    }
  }

  /**
   * Create a pull request.
   */
  @Tool(description = "Create a pull request from a feature branch to the base branch. Use after pushing code changes.")
  public Map<String, Object> createPullRequest(
      @ToolParam(description = "Repository owner") String owner,
      @ToolParam(description = "Repository name") String repo,
      @ToolParam(description = "PR title") String title,
      @ToolParam(description = "PR description/body") String body,
      @ToolParam(description = "Source branch with changes") String head,
      @ToolParam(description = "Target branch (default: main)") String base) {

    if (owner == null || repo == null || title == null || head == null) {
      return Map.of("error", "owner, repo, title, and head branch are required");
    }

    String targetBase = (base == null || base.isBlank()) ? "main" : base;

    try {
      Map<String, Object> pr = webClient.post()
          .uri("/repos/{owner}/{repo}/pulls", owner, repo)
          .headers(h -> addAuthHeader(h))
          .bodyValue(Map.of(
              "title", title,
              "body", body != null ? body : "",
              "head", head,
              "base", targetBase
          ))
          .retrieve()
          .bodyToMono(MAP_TYPE)
          .block();

      return Map.of(
          "success", true,
          "number", pr.get("number"),
          "html_url", pr.get("html_url"),
          "state", pr.get("state")
      );
    } catch (Exception e) {
      return Map.of("error", "Failed to create PR: " + e.getMessage());
    }
  }

  /**
   * Add a comment to an issue.
   */
  @Tool(description = "Add a comment to a GitHub issue. Useful for providing status updates or asking clarifying questions.")
  public Map<String, Object> addIssueComment(
      @ToolParam(description = "Repository owner") String owner,
      @ToolParam(description = "Repository name") String repo,
      @ToolParam(description = "Issue number") Integer issueNumber,
      @ToolParam(description = "Comment body") String body) {

    if (owner == null || repo == null || issueNumber == null || body == null) {
      return Map.of("error", "owner, repo, issueNumber, and body are required");
    }

    try {
      Map<String, Object> comment = webClient.post()
          .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", owner, repo, issueNumber)
          .headers(h -> addAuthHeader(h))
          .bodyValue(Map.of("body", body))
          .retrieve()
          .bodyToMono(MAP_TYPE)
          .block();

      return Map.of(
          "success", true,
          "id", comment.get("id"),
          "html_url", comment.get("html_url")
      );
    } catch (Exception e) {
      return Map.of("error", "Failed to add comment: " + e.getMessage());
    }
  }

  private void addAuthHeader(HttpHeaders headers) {
    if (githubToken != null && !githubToken.isBlank()) {
      headers.setBearerAuth(githubToken);
    }
  }
}
