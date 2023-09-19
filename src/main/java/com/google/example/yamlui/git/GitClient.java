// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.example.yamlui.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushConfig.PushDefault;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provides the Git operations required for this application.
 *
 * <p>References: <a href="https://wiki.eclipse.org/JGit/User_Guide#API">JGit low level API</a> <a
 * href="https://wiki.eclipse.org/JGit/User_Guide#Porcelain_API">JGit porcelain API</a>.
 */
@Service
public class GitClient {

  private static final Logger LOG = LoggerFactory.getLogger(GitClient.class);

  // Fallback author name and email for Git commits, to use if unspecified.
  static final String DEFAULT_AUTHOR_NAME = "Console UI";
  static final String DEFAULT_AUTHOR_EMAIL = "console@example.com";

  // credentialsProvider provides credentials for authenticating operations on the remote Git
  // repository.
  private final CredentialsProvider credentialsProvider;

  // repoUrl is the URL of the remote Git repository
  private final String repoUrl;

  // remote is the name of the remote Git repository.
  // Defaults to `origin`.
  private final String remote;

  // branch is the name of the branch that this Git client operates on.
  // This Git client assumes that the local and remote branches have the same name.
  // Defaults to `main`.
  private final String branch;

  // timeoutSeconds is used for pull, push, and clone commands.
  // Defaults to `30` seconds.
  private final int timeoutSeconds;

  // rootPath is the local filesystem path where the Git repository is cloned
  private final Path localPath;

  // repo represents the repository for the local clone of the Git repository
  private final Repository repo;

  @Autowired
  GitClient(
      CredentialsProvider credentialsProvider,
      @Value("${git.repository.url}") String repoUrl,
      @Value("${git.repository.remote:origin}") String remote,
      @Value("${git.repository.branch:main}") String branch,
      @Value("${git.repository.timeout:30}") int timeoutSeconds,
      @Qualifier("localPath") Path localPath)
      throws IOException {
    this.credentialsProvider = credentialsProvider;
    this.repoUrl = repoUrl;
    this.remote = remote;
    this.branch = branch;
    this.timeoutSeconds = timeoutSeconds;
    this.localPath = localPath;
    this.repo =
        new FileRepositoryBuilder()
            .setGitDir(this.localPath.resolve(".git").toFile())
            .readEnvironment() // scan environment GIT_* variables
            .build();
  }

  /** Returns the path to the local clone of the Git repository. */
  public Path getLocalPath() {
    return localPath;
  }

  @PostConstruct
  void ensureCloneAndPull() {
    if (Files.isDirectory(localPath.resolve(".git"))) {
      LOG.info("Repo already cloned to {}, pulling from {}/{}", localPath, remote, branch);
      pull();
      return;
    }
    LOG.info("No local Git repo found in {}, cloning from {}", localPath, repoUrl);
    cloneRepo();
  }

  void cloneRepo() {
    try (Git ignored =
        Git.cloneRepository()
            .setBare(false)
            .setBranch(branch)
            .setCredentialsProvider(credentialsProvider)
            .setDirectory(localPath.toFile())
            .setRemote(remote)
            .setTimeout(timeoutSeconds)
            .setURI(repoUrl)
            .call()) {
      // empty
    } catch (GitAPIException e) {
      throw new GitException("Could not clone repository from " + repoUrl + " to " + localPath, e);
    }
  }

  // Breaking encapsulation for testing.
  Repository getRepo() {
    return repo;
  }

  /**
   * Returns the latest commit ID (SHA-1) for a file path from the local clone.
   *
   * <p>Used to detect updates that were not based on the last change.
   *
   * <p>Returns empty string if no commits exist for the file path.
   */
  public String getLatestCommitIdForFilePath(Path path) {
    try (Git git = new Git(repo)) {
      Iterator<RevCommit> revCommits =
          git.log().addPath(path.toString()).setMaxCount(1).call().iterator();
      if (!revCommits.hasNext()) {
        LOG.info("No commits for file path {}", path);
        return "";
      }
      return revCommits.next().getName();
    } catch (GitAPIException e) {
      throw new GitException("Could not get latest commit ID for file " + path, e);
    }
  }

  /** Equivalent to <code>git add</code>. */
  public void add(String filepattern) {
    try (Git git = new Git(repo)) {
      git.add().addFilepattern(filepattern).call();
    } catch (GitAPIException e) {
      throw new GitException("Could not add files for pattern " + filepattern, e);
    }
  }

  /**
   * Equivalent to <code>git commit</code>, using the default author name and email configured in
   * this class.
   *
   * @return the commit SHA.
   */
  public String commit(String message) {
    return commit(message, DEFAULT_AUTHOR_NAME, DEFAULT_AUTHOR_EMAIL);
  }

  /**
   * Equivalent to <code>git commit</code>.
   *
   * @return the commit SHA.
   */
  public String commit(String message, String name, String email) {
    try (Git git = new Git(repo)) {
      RevCommit revCommit = git.commit().setAuthor(name, email).setMessage(message).call();
      return revCommit.getId().getName();
    } catch (GitAPIException e) {
      throw new GitException("Could not commit", e);
    }
  }

  /**
   * Performs a <code>git pull</code> operation. If there are merge conflicts, the local changes are
   * discarded.
   *
   * <p>Specifically, this method performs the equivalent of:
   *
   * <pre>
   * git pull --ff --no-rebase --strategy recursive --strategy-option conflict origin main
   * </pre>
   *
   * <p>If the pull command results in conflicts, this method does the equivalent of:
   *
   * <pre>
   * git reset --hard remotes/origin/main
   * </pre>
   *
   * @returns true the merge was successful. If false, local changes have been discarded.
   */
  public boolean pull() {
    try (Git git = new Git(repo)) {
      PullResult pullResult =
          git.pull()
              .setContentMergeStrategy(ContentMergeStrategy.CONFLICT)
              .setCredentialsProvider(credentialsProvider)
              .setFastForward(FastForwardMode.FF)
              .setRebase(false)
              .setRemote(remote)
              .setRemoteBranchName(branch)
              .setStrategy(MergeStrategy.RECURSIVE)
              .setTimeout(timeoutSeconds)
              .call();
      if (!pullResult.isSuccessful()) {
        MergeResult mergeResult = pullResult.getMergeResult();
        HashSet<String> conflictingFiles = new HashSet<>();
        if (mergeResult != null && mergeResult.getConflicts() != null) {
          conflictingFiles.addAll(mergeResult.getConflicts().keySet());
        }
        LOG.warn(
            "Git pull resulted in merge conflict(s). "
                + "Discarding local changes in the following files: {}",
            conflictingFiles);
        git.reset().setMode(ResetType.HARD).setRef("remotes/" + remote + "/" + branch).call();
      }
      return pullResult.isSuccessful();
    } catch (RefNotAdvertisedException e) {
      LOG.warn("Could not pull, possibly due to the remote repo being empty (no commits)", e);
      return false;
    } catch (GitAPIException e) {
      throw new GitException("Could not pull", e);
    }
  }

  /** Equivalent to <code>git push</code>. */
  public void push() {
    try (Git git = new Git(repo)) {
      Iterable<PushResult> pushResults =
          git.push()
              .setAtomic(true)
              .setCredentialsProvider(credentialsProvider)
              .setForce(false)
              .setPushDefault(PushDefault.CURRENT)
              .setTimeout(timeoutSeconds)
              .call();
      Map<String, Status> results =
          StreamSupport.stream(pushResults.spliterator(), false)
              .map(pushResult -> pushResult.getRemoteUpdate("refs/heads/" + branch))
              .collect(
                  Collectors.toUnmodifiableMap(
                      remoteRefUpdate -> remoteRefUpdate.getNewObjectId().getName(),
                      RemoteRefUpdate::getStatus));
      for (Status status : results.values()) {
        if (status != Status.OK && status != Status.UP_TO_DATE) {
          throw new GitException("Problems pushing to remote: " + results);
        }
      }
    } catch (GitAPIException e) {
      throw new GitException("Could not push", e);
    }
  }
}
