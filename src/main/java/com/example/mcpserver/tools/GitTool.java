package com.example.mcpserver.tools;

import com.example.mcpserver.util.WorkspacePathResolver;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Git Tool - Exposes Git operations to AI agents.
 * <p>
 * This tool allows an LLM to clone repositories, create branches,
 * stage, commit, push changes, and view git status/history.
 */
@Component
public class GitTool {

  private final String githubToken;
  private final WorkspacePathResolver pathResolver;

  public GitTool(
      @Value("${github.token:}") String githubToken,
      WorkspacePathResolver pathResolver) {
    this.githubToken = githubToken;
    this.pathResolver = pathResolver;
  }

  /**
   * Clone a repository.
   */
  @Tool(description = "Clone a GitHub repository to the local workspace. Returns the local path where the repo was cloned.")
  public Map<String, Object> cloneRepository(
      @ToolParam(description = "Repository URL (e.g., https://github.com/owner/repo.git)") String repoUrl,
      @ToolParam(description = "Local directory name (optional, defaults to repo name)") String localDir) {

    if (repoUrl == null || repoUrl.isBlank()) {
      return Map.of("error", "Repository URL is required");
    }

    String dirName = localDir;
    if (dirName == null || dirName.isBlank()) {
      dirName = repoUrl.substring(repoUrl.lastIndexOf('/') + 1).replace(".git", "");
    }

    File targetDir = pathResolver.resolveRepo(dirName).toFile();

    if (targetDir.exists()) {
      return Map.of("error", "Directory already exists: " + targetDir.getAbsolutePath(),
          "suggestion", "Use a different localDir name or delete the existing directory");
    }

    try {
      Git.cloneRepository()
          .setURI(repoUrl)
          .setDirectory(targetDir)
          .setCredentialsProvider(getCredentials())
          .call()
          .close();

      return Map.of(
          "success", true,
          "path", targetDir.getAbsolutePath(),
          "message", "Repository cloned successfully"
      );
    } catch (GitAPIException e) {
      return Map.of("error", "Failed to clone: " + e.getMessage());
    }
  }

  /**
   * Create and checkout a new branch.
   */
  @Tool(description = "Create a new branch and switch to it. Use this before making changes for an issue.")
  public Map<String, Object> createBranch(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "New branch name (e.g., 'feature/issue-123')") String branchName) {

    if (repoDir == null || branchName == null) {
      return Map.of("error", "repoDir and branchName are required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();

    try (Git git = Git.open(repoPath)) {
      git.checkout()
          .setCreateBranch(true)
          .setName(branchName)
          .call();

      return Map.of(
          "success", true,
          "branch", branchName,
          "message", "Created and switched to branch: " + branchName
      );
    } catch (IOException | GitAPIException e) {
      return Map.of("error", "Failed to create branch: " + e.getMessage());
    }
  }

  /**
   * Get current git status.
   */
  @Tool(description = "Get the current git status showing modified, added, and untracked files.")
  public Map<String, Object> getStatus(
      @ToolParam(description = "Repository directory name in workspace") String repoDir) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();

    try (Git git = Git.open(repoPath)) {
      Status status = git.status().call();
      String branch = git.getRepository().getBranch();

      return Map.of(
          "branch", branch,
          "modified", status.getModified(),
          "added", status.getAdded(),
          "removed", status.getRemoved(),
          "untracked", status.getUntracked(),
          "isClean", status.isClean()
      );
    } catch (IOException | GitAPIException e) {
      return Map.of("error", "Failed to get status: " + e.getMessage());
    }
  }

  /**
   * Stage, commit, and push changes.
   */
  @Tool(description = "Stage all changes, create a commit with the given message, and push to remote. Use after making file changes.")
  public Map<String, Object> commitAndPush(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "Commit message describing the changes") String message) {

    if (repoDir == null || message == null) {
      return Map.of("error", "repoDir and message are required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();

    try (Git git = Git.open(repoPath)) {
      git.add().addFilepattern(".").call();
      git.add().addFilepattern(".").setUpdate(true).call();

      Status status = git.status().call();
      if (status.isClean()) {
        return Map.of("warning", "No changes to commit", "status", "clean");
      }

      RevCommit commit = git.commit()
          .setMessage(message)
          .call();

      String branch = git.getRepository().getBranch();
      git.push()
          .setCredentialsProvider(getCredentials())
          .setRemote("origin")
          .add(branch)
          .call();

      return Map.of(
          "success", true,
          "commitId", commit.getName().substring(0, 7),
          "message", commit.getShortMessage(),
          "branch", branch,
          "pushed", true
      );
    } catch (IOException | GitAPIException e) {
      return Map.of("error", "Failed to commit/push: " + e.getMessage());
    }
  }

  /**
   * List branches in a repository.
   */
  @Tool(description = "List all branches in the repository (both local and remote).")
  public Map<String, Object> listBranches(
      @ToolParam(description = "Repository directory name in workspace") String repoDir) {

    if (repoDir == null) {
      return Map.of("error", "repoDir is required");
    }

    File repoPath = pathResolver.resolveRepo(repoDir).toFile();

    try (Git git = Git.open(repoPath)) {
      List<String> localBranches = git.branchList().call().stream()
          .map(Ref::getName)
          .map(name -> name.replace("refs/heads/", ""))
          .toList();

      String currentBranch = git.getRepository().getBranch();

      return Map.of(
          "currentBranch", currentBranch,
          "localBranches", localBranches
      );
    } catch (IOException | GitAPIException e) {
      return Map.of("error", "Failed to list branches: " + e.getMessage());
    }
  }

  /**
   * Get recent commit history.
   */
  @Tool(description = "Get recent commit history for the current branch.")
  public List<Map<String, Object>> getCommitHistory(
      @ToolParam(description = "Repository directory name in workspace") String repoDir,
      @ToolParam(description = "Maximum number of commits to return (default 10)") Integer limit) {

    if (repoDir == null) {
      return List.of(Map.of("error", "repoDir is required"));
    }

    int maxCommits = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;
    File repoPath = pathResolver.resolveRepo(repoDir).toFile();

    try (Git git = Git.open(repoPath)) {
      Iterable<RevCommit> commits = git.log().setMaxCount(maxCommits).call();

      return StreamSupport.stream(commits.spliterator(), false)
          .map(commit -> Map.<String, Object>of(
              "id", commit.getName().substring(0, 7),
              "message", commit.getShortMessage(),
              "author", commit.getAuthorIdent().getName(),
              "date", commit.getAuthorIdent().getWhen().toString()
          ))
          .toList();
    } catch (IOException | GitAPIException e) {
      return List.of(Map.of("error", "Failed to get history: " + e.getMessage()));
    }
  }

  /**
   * Get the workspace path.
   */
  @Tool(description = "Get the absolute path to the workspace directory where repositories are cloned.")
  public Map<String, Object> getWorkspacePath() {
    File workspace = pathResolver.getWorkspaceRootFile();
    String[] dirs = workspace.list((dir, name) -> new File(dir, name).isDirectory());
    return Map.of(
        "path", workspace.getAbsolutePath(),
        "exists", workspace.exists(),
        "repositories", workspace.exists() && dirs != null ? List.of(dirs) : List.of()
    );
  }

  private CredentialsProvider getCredentials() {
    if (githubToken != null && !githubToken.isBlank()) {
      return new UsernamePasswordCredentialsProvider(githubToken, "");
    }
    return null;
  }
}
