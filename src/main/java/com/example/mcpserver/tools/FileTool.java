package com.example.mcpserver.tools;

import com.example.mcpserver.util.WorkspacePathResolver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * File Tool - Exposes file system operations to AI agents.
 * <p>
 * This tool allows an LLM to read, write, list, and search files.
 * Operations are restricted to the workspace directory for security.
 */
@Component
public class FileTool {

  private final WorkspacePathResolver pathResolver;

  public FileTool(WorkspacePathResolver pathResolver) {
    this.pathResolver = pathResolver;
  }

  /**
   * Read file contents.
   */
  @Tool(description = "Read the contents of a file from the workspace. Use this to understand existing code before making changes.")
  public Map<String, Object> readFile(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "File path relative to repository root") String filePath) {

    if (repoDir == null || filePath == null) {
      return Map.of("error", "repoDir and filePath are required");
    }

    try {
      Path fullPath = pathResolver.resolve(repoDir, filePath);
      File file = fullPath.toFile();

      if (!file.exists()) {
        return Map.of("error", "File not found: " + filePath);
      }
      if (!file.isFile()) {
        return Map.of("error", "Not a file: " + filePath);
      }

      String content = Files.readString(fullPath, StandardCharsets.UTF_8);
      return Map.of(
          "path", filePath,
          "content", content,
          "size", file.length(),
          "lines", content.lines().count()
      );
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    } catch (IOException e) {
      return Map.of("error", "Failed to read file: " + e.getMessage());
    }
  }

  /**
   * Write/create a file.
   */
  @Tool(description = "Write content to a file in the workspace. Creates the file if it doesn't exist, or overwrites if it does. Creates parent directories as needed.")
  public Map<String, Object> writeFile(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "File path relative to repository root") String filePath,
      @ToolParam(description = "Content to write to the file") String content) {

    if (repoDir == null || filePath == null || content == null) {
      return Map.of("error", "repoDir, filePath, and content are required");
    }

    try {
      Path fullPath = pathResolver.resolve(repoDir, filePath);
      Files.createDirectories(fullPath.getParent());
      Files.writeString(fullPath, content, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      return Map.of(
          "success", true,
          "path", filePath,
          "size", content.length(),
          "message", "File written successfully"
      );
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    } catch (IOException e) {
      return Map.of("error", "Failed to write file: " + e.getMessage());
    }
  }

  /**
   * Append to a file.
   */
  @Tool(description = "Append content to an existing file. Useful for adding new methods, tests, or log entries.")
  public Map<String, Object> appendToFile(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "File path relative to repository root") String filePath,
      @ToolParam(description = "Content to append to the file") String content) {

    if (repoDir == null || filePath == null || content == null) {
      return Map.of("error", "repoDir, filePath, and content are required");
    }

    try {
      Path fullPath = pathResolver.resolve(repoDir, filePath);

      if (!pathResolver.exists(fullPath)) {
        return Map.of("error", "File not found: " + filePath);
      }

      Files.writeString(fullPath, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);

      return Map.of(
          "success", true,
          "path", filePath,
          "appendedBytes", content.length(),
          "message", "Content appended successfully"
      );
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    } catch (IOException e) {
      return Map.of("error", "Failed to append: " + e.getMessage());
    }
  }

  /**
   * List directory contents.
   */
  @Tool(description = "List files and directories in a path. Use this to explore the project structure.")
  public Map<String, Object> listDirectory(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "Path relative to repository root (empty for root)") String dirPath) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    try {
      Path fullPath = pathResolver.resolve(repoDir, dirPath);
      File dir = fullPath.toFile();

      if (!dir.exists()) {
        return Map.of("error", "Directory not found: " + (dirPath == null ? "/" : dirPath));
      }
      if (!dir.isDirectory()) {
        return Map.of("error", "Not a directory: " + dirPath);
      }

      File[] files = dir.listFiles();
      if (files == null) {
        return Map.of("error", "Cannot list directory");
      }

      List<Map<String, Object>> entries = new ArrayList<>();
      for (File file : files) {
        entries.add(Map.of(
            "name", file.getName(),
            "type", file.isDirectory() ? "directory" : "file",
            "size", file.isFile() ? file.length() : 0
        ));
      }

      String targetPath = (dirPath == null || dirPath.isBlank()) ? "/" : dirPath;
      return Map.of(
          "path", targetPath,
          "entries", entries,
          "count", entries.size()
      );
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    }
  }

  /**
   * Search for files by pattern.
   */
  @Tool(description = "Search for files matching a pattern (e.g., '*.java', '*Test.java'). Returns matching file paths.")
  public Map<String, Object> searchFiles(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "File name pattern with wildcards (e.g., '*.java', '*Test*')") String pattern,
      @ToolParam(description = "Maximum results to return (default 50)") Integer limit) {

    if (repoDir == null || pattern == null) {
      return Map.of("error", "repoDir and pattern are required");
    }

    int maxResults = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;

    try {
      Path repoPath = pathResolver.resolveRepo(repoDir);

      if (!pathResolver.exists(repoPath)) {
        return Map.of("error", "Repository not found: " + repoDir);
      }

      // Convert glob pattern to regex
      String regex = pattern
          .replace(".", "\\.")
          .replace("*", ".*")
          .replace("?", ".");

      try (Stream<Path> walk = Files.walk(repoPath)) {
        List<String> matches = walk
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().matches(regex))
            .limit(maxResults)
            .map(p -> repoPath.relativize(p).toString())
            .toList();

        return Map.of(
            "pattern", pattern,
            "matches", matches,
            "count", matches.size(),
            "truncated", matches.size() >= maxResults
        );
      }
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    } catch (IOException e) {
      return Map.of("error", "Search failed: " + e.getMessage());
    }
  }

  /**
   * Delete a file.
   */
  @Tool(description = "Delete a file from the workspace. Use with caution.")
  public Map<String, Object> deleteFile(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "File path relative to repository root") String filePath) {

    if (repoDir == null || filePath == null) {
      return Map.of("error", "repoDir and filePath are required");
    }

    try {
      Path fullPath = pathResolver.resolve(repoDir, filePath);
      File file = fullPath.toFile();

      if (!file.exists()) {
        return Map.of("error", "File not found: " + filePath);
      }
      if (!file.isFile()) {
        return Map.of("error", "Not a file (use deleteDirectory for directories): " + filePath);
      }

      if (file.delete()) {
        return Map.of(
            "success", true,
            "path", filePath,
            "message", "File deleted successfully"
        );
      } else {
        return Map.of("error", "Failed to delete file");
      }
    } catch (SecurityException e) {
      return Map.of("error", e.getMessage());
    }
  }
}
