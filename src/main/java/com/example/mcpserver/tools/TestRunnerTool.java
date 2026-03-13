package com.example.mcpserver.tools;

import com.example.mcpserver.util.CommandExecutor;
import com.example.mcpserver.util.WorkspacePathResolver;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Test Runner Tool - Exposes test execution capabilities to AI agents.
 * <p>
 * This tool allows an LLM to run tests, compile projects, and get test results.
 */
@Component
public class TestRunnerTool {

  private final WorkspacePathResolver pathResolver;
  private final CommandExecutor commandExecutor;

  public TestRunnerTool(WorkspacePathResolver pathResolver, CommandExecutor commandExecutor) {
    this.pathResolver = pathResolver;
    this.commandExecutor = commandExecutor;
  }

  /**
   * Run all tests in a project.
   */
  @Tool(description = "Run all tests in a Maven or Gradle project. Returns test results including passed, failed, and error counts.")
  public Map<String, Object> runAllTests(
      @ToolParam(description = "Repository directory name in workspace") String repoDir) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();
    if (!repoPath.exists()) {
      return Map.of("error", "Repository not found: " + repoDir);
    }

    String command = detectBuildCommand(repoPath, null);
    if (command == null) {
      return Map.of("error", "No Maven or Gradle build file found");
    }

    return executeTests(repoPath, command);
  }

  /**
   * Run a specific test class.
   */
  @Tool(description = "Run a specific test class. Provide the fully qualified class name (e.g., 'com.example.UserServiceTest').")
  public Map<String, Object> runTestClass(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "Fully qualified test class name") String testClass) {

    if (repoDir == null || testClass == null) {
      return Map.of("error", "repoDir and testClass are required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();
    if (!repoPath.exists()) {
      return Map.of("error", "Repository not found: " + repoDir);
    }

    String command = detectBuildCommand(repoPath, testClass);
    if (command == null) {
      return Map.of("error", "No Maven or Gradle build file found");
    }

    return executeTests(repoPath, command);
  }

  /**
   * Run a specific test method.
   */
  @Tool(description = "Run a specific test method. Provide class name and method name.")
  public Map<String, Object> runTestMethod(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "Fully qualified test class name") String testClass,
      @ToolParam(description = "Test method name") String testMethod) {

    if (repoDir == null || testClass == null || testMethod == null) {
      return Map.of("error", "repoDir, testClass, and testMethod are required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();
    if (!repoPath.exists()) {
      return Map.of("error", "Repository not found: " + repoDir);
    }

    String testFilter = testClass + "#" + testMethod;
    String command = detectBuildCommand(repoPath, testFilter);
    if (command == null) {
      return Map.of("error", "No Maven or Gradle build file found");
    }

    return executeTests(repoPath, command);
  }

  /**
   * Compile the project without running tests.
   */
  @Tool(description = "Compile the project to check for compilation errors. Does not run tests.")
  public Map<String, Object> compileProject(
      @ToolParam(description = "Repository directory name in workspace") String repoDir) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();
    if (!repoPath.exists()) {
      return Map.of("error", "Repository not found: " + repoDir);
    }

    String command;
    if (new File(repoPath, "pom.xml").exists()) {
      command = "./mvnw compile -q";
    } else if (new File(repoPath, "build.gradle").exists() ||
        new File(repoPath, "build.gradle.kts").exists()) {
      command = "./gradlew compileJava compileTestJava -q";
    } else {
      return Map.of("error", "No Maven or Gradle build file found");
    }

    return commandExecutor.execute(repoPath, command, "Compilation");
  }

  /**
   * Check test coverage (if configured).
   */
  @Tool(description = "Run tests with code coverage reporting. Returns coverage percentage if JaCoCo is configured.")
  public Map<String, Object> runTestsWithCoverage(
      @ToolParam(description = "Repository directory name in workspace") String repoDir) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();
    if (!repoPath.exists()) {
      return Map.of("error", "Repository not found: " + repoDir);
    }

    String command;
    if (new File(repoPath, "pom.xml").exists()) {
      command = "./mvnw clean verify jacoco:report";
    } else if (new File(repoPath, "build.gradle").exists() ||
        new File(repoPath, "build.gradle.kts").exists()) {
      command = "./gradlew clean test jacocoTestReport";
    } else {
      return Map.of("error", "No Maven or Gradle build file found");
    }

    Map<String, Object> result = commandExecutor.execute(repoPath, command, "Test with Coverage");

    // Try to parse coverage from JaCoCo report
    File coverageReport = new File(repoPath, "target/site/jacoco/index.html");
    if (!coverageReport.exists()) {
      coverageReport = new File(repoPath, "build/reports/jacoco/test/html/index.html");
    }

    if (coverageReport.exists()) {
      result = new HashMap<>(result);
      result.put("coverageReportPath", coverageReport.getAbsolutePath());
      result.put("hint", "JaCoCo report generated. Parse the HTML for detailed coverage metrics.");
    }

    return result;
  }

  private String detectBuildCommand(File repoPath, String testFilter) {
    if (new File(repoPath, "pom.xml").exists()) {
      if (testFilter != null) {
        return "./mvnw test -Dtest=" + testFilter;
      }
      return "./mvnw test";
    } else if (new File(repoPath, "build.gradle").exists() ||
        new File(repoPath, "build.gradle.kts").exists()) {
      if (testFilter != null) {
        return "./gradlew test --tests \"" + testFilter + "\"";
      }
      return "./gradlew test";
    }
    return null;
  }

  private Map<String, Object> executeTests(File repoPath, String command) {
    Map<String, Object> result = commandExecutor.execute(repoPath, command, "Tests");

    String output = (String) result.get("output");
    if (output != null) {
      result = new HashMap<>(result);
      result.putAll(parseTestResults(output));
    }

    return result;
  }

  private Map<String, Object> parseTestResults(String output) {
    Map<String, Object> results = new HashMap<>();

    // Maven Surefire format: Tests run: X, Failures: Y, Errors: Z, Skipped: W
    Pattern mavenPattern = Pattern.compile(
        "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
    Matcher mavenMatcher = mavenPattern.matcher(output);

    int totalRun = 0, totalFailed = 0, totalErrors = 0, totalSkipped = 0;

    while (mavenMatcher.find()) {
      totalRun += Integer.parseInt(mavenMatcher.group(1));
      totalFailed += Integer.parseInt(mavenMatcher.group(2));
      totalErrors += Integer.parseInt(mavenMatcher.group(3));
      totalSkipped += Integer.parseInt(mavenMatcher.group(4));
    }

    if (totalRun > 0) {
      results.put("testsRun", totalRun);
      results.put("testsFailed", totalFailed);
      results.put("testsErrors", totalErrors);
      results.put("testsSkipped", totalSkipped);
      results.put("testsPassed", totalRun - totalFailed - totalErrors);
    }

    // Gradle format: X tests completed, Y failed
    Pattern gradlePattern = Pattern.compile("(\\d+) tests completed, (\\d+) failed");
    Matcher gradleMatcher = gradlePattern.matcher(output);

    if (gradleMatcher.find() && totalRun == 0) {
      totalRun = Integer.parseInt(gradleMatcher.group(1));
      totalFailed = Integer.parseInt(gradleMatcher.group(2));
      results.put("testsRun", totalRun);
      results.put("testsFailed", totalFailed);
      results.put("testsPassed", totalRun - totalFailed);
    }

    // Extract failed test names
    List<String> failedTests = new ArrayList<>();
    Pattern failedTestPattern = Pattern.compile("(?:FAILED|FAILURE).*?([\\w.]+)(?:#(\\w+))?");
    Matcher failedMatcher = failedTestPattern.matcher(output);
    while (failedMatcher.find()) {
      String testName = failedMatcher.group(1);
      if (failedMatcher.group(2) != null) {
        testName += "#" + failedMatcher.group(2);
      }
      failedTests.add(testName);
    }
    if (!failedTests.isEmpty()) {
      results.put("failedTestNames", failedTests);
    }

    return results;
  }
}
