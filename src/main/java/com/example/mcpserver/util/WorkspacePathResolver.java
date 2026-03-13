package com.example.mcpserver.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for resolving and validating workspace paths.
 * Ensures all file operations stay within the configured workspace boundary.
 */
@Component
public class WorkspacePathResolver {

  private final Path workspaceRoot;

  public WorkspacePathResolver(@Value("${mcp.workspace.path:./workspace}") String workspacePath) {
    this.workspaceRoot = Path.of(workspacePath).toAbsolutePath().normalize();
    // Ensure workspace directory exists
    new File(workspacePath).mkdirs();
  }

  /**
   * Resolves a path within a repository directory and validates it's within the workspace.
   */
  public Path resolve(String repoDir, String filePath) {
    String targetPath = (filePath == null || filePath.isBlank()) ? "" : filePath;
    Path fullPath = workspaceRoot.resolve(repoDir).resolve(targetPath).normalize();
    validateWithinWorkspace(fullPath);
    return fullPath;
  }

  /**
   * Resolves a repository root path and validates it's within the workspace.
   */
  public Path resolveRepo(String repoDir) {
    Path repoPath = workspaceRoot.resolve(repoDir).normalize();
    validateWithinWorkspace(repoPath);
    return repoPath;
  }

  /**
   * Gets the workspace root path.
   */
  public Path getWorkspaceRoot() {
    return workspaceRoot;
  }

  /**
   * Gets the workspace root as a File.
   */
  public File getWorkspaceRootFile() {
    return workspaceRoot.toFile();
  }

  /**
   * Checks if a path exists.
   */
  public boolean exists(Path path) {
    return Files.exists(path);
  }

  /**
   * Checks if a path is a directory.
   */
  public boolean isDirectory(Path path) {
    return Files.isDirectory(path);
  }

  /**
   * Checks if a path is a regular file.
   */
  public boolean isFile(Path path) {
    return Files.isRegularFile(path);
  }

  private void validateWithinWorkspace(Path path) {
    if (!path.startsWith(workspaceRoot)) {
      throw new SecurityException("Access denied: path outside workspace");
    }
  }
}

