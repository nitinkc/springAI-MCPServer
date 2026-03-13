package com.example.mcpserver.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for executing shell commands with timeout support.
 */
@Component
public class CommandExecutor {

  private final long defaultTimeoutMinutes;

  public CommandExecutor(@Value("${mcp.command.timeout-minutes:10}") long defaultTimeoutMinutes) {
    this.defaultTimeoutMinutes = defaultTimeoutMinutes;
  }

  /**
   * Execute a shell command in the specified directory.
   */
  public Map<String, Object> execute(File workingDir, String command, String taskName) {
    return execute(workingDir, command, taskName, defaultTimeoutMinutes);
  }

  /**
   * Execute a shell command with custom timeout.
   */
  public Map<String, Object> execute(File workingDir, String command, String taskName, long timeoutMinutes) {
    try {
      ProcessBuilder pb = new ProcessBuilder();
      pb.directory(workingDir);
      pb.redirectErrorStream(true);

      // Use shell to execute command
      if (System.getProperty("os.name").toLowerCase().contains("windows")) {
        pb.command("cmd", "/c", command);
      } else {
        pb.command("sh", "-c", command);
      }

      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
      int exitCode = completed ? process.exitValue() : -1;

      if (!completed) {
        process.destroyForcibly();
        return Map.of(
            "error", taskName + " timed out after " + timeoutMinutes + " minutes",
            "output", output.toString()
        );
      }

      return Map.of(
          "success", exitCode == 0,
          "exitCode", exitCode,
          "output", output.toString(),
          "command", command
      );
    } catch (Exception e) {
      return Map.of("error", "Failed to execute " + taskName + ": " + e.getMessage());
    }
  }
}

