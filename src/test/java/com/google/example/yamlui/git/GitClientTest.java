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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.jupiter.api.Test;

class GitClientTest {

  @Test
  void pullMergesChangesInDifferentFiles() throws Exception {
    // Create the remote Git repo.
    final Path gitRemoteRepoDir = createBareRepo();

    // Create clone1 from remote repo (empty).
    GitClient gitClient1 = cloneRepo(gitRemoteRepoDir);
    final Path clone1Dir = gitClient1.getLocalPath();

    // Create test1 file in clone1 and push to remote.
    String test1Filename = "test1.txt";
    Files.write(clone1Dir.resolve(test1Filename), "foo\nbar\n".getBytes(StandardCharsets.UTF_8));
    gitClient1.add(test1Filename);
    final String commitId0 =
        gitClient1.commit(
            "Add " + test1Filename + " with foo-bar", "Client One", "client1@example.com");
    // Not pulling here since the remote repo is empty (no commits yet).
    gitClient1.push();

    // Create clone2 from remote repo (has one commit).
    GitClient gitClient2 = cloneRepo(gitRemoteRepoDir);
    final Path clone2Dir = gitClient2.getLocalPath();

    // Modify test1 file in clone1, pull, and push to remote.
    Files.write(clone1Dir.resolve(test1Filename), "qux\nquux\n".getBytes(StandardCharsets.UTF_8));
    gitClient1.add(test1Filename);
    final String commitId1 =
        gitClient1.commit(
            "Modify " + test1Filename + " with qux-quux", "Client One", "client1@example.com");
    gitClient1.pull();
    gitClient1.push();

    // Create test2 file in clone2 and push to remote.
    // This should result in a merge commit.
    String test2Filename = "test2.txt";
    Files.write(clone2Dir.resolve(test2Filename), "frob\nbaz\n".getBytes(StandardCharsets.UTF_8));
    gitClient2.add(test2Filename);
    final String commitId2 =
        gitClient2.commit(
            "Add " + test2Filename + " with frob-baz", "Client Two", "client2@example.com");
    gitClient2.pull();
    gitClient2.push();

    // Verify the expected commits exist in the log:
    // Two commits from client1, one from client2, and the merge commit.
    Iterable<RevCommit> revCommitsIter = new Git(gitClient2.getRepo()).log().call();
    List<RevCommit> revCommits =
        StreamSupport.stream(revCommitsIter.spliterator(), false).collect(Collectors.toList());
    assertThat(revCommits, hasSize(4));
    // commit log is in reverse order of commits
    // revCommits.get(0) is the merge commit
    assertThat(revCommits.get(1).getName(), equalTo(commitId2));
    assertThat(revCommits.get(2).getName(), equalTo(commitId1));
    assertThat(revCommits.get(3).getName(), equalTo(commitId0));

    // Verify the expected commits exist in the private reflog of clone2.
    Collection<ReflogEntry> reflogEntriesCollection = new Git(gitClient2.getRepo()).reflog().call();
    List<ReflogEntry> reflogEntries = new ArrayList<>(reflogEntriesCollection);
    assertThat(reflogEntries, hasSize(3));
  }

  @Test
  void pullDiscardsChangesOnMergeConflict() throws Exception {
    // Create the remote Git repo.
    final Path gitRemoteRepoDir = createBareRepo();

    // Create clone1 from remote repo (empty).
    GitClient gitClient1 = cloneRepo(gitRemoteRepoDir);
    final Path clone1Dir = gitClient1.getLocalPath();

    // Create test file in clone1 and push to remote.
    String testFilename = "test.txt";
    Files.write(clone1Dir.resolve(testFilename), "foo\nbar\n".getBytes(StandardCharsets.UTF_8));
    gitClient1.add(testFilename);
    final String commitId0 =
        gitClient1.commit(
            "Add " + testFilename + " with foo-bar", "Client One", "client1@example.com");
    // Not pulling here since the remote repo is empty (no commits yet).
    gitClient1.push();

    // Create clone2 from remote repo (contains test file).
    GitClient gitClient2 = cloneRepo(gitRemoteRepoDir);
    final Path clone2Dir = gitClient2.getLocalPath();

    // Change test file in clone1 and push to remote.
    Files.write(clone1Dir.resolve(testFilename), "frob\nbar\n".getBytes(StandardCharsets.UTF_8));
    gitClient1.add(testFilename);
    final String commitId1 =
        gitClient1.commit(
            "Update " + testFilename + " with frob-bar", "Client One", "client1@example.com");
    gitClient1.pull();
    gitClient1.push();

    // Change test file in clone2 and push to remote.
    // This should result in an unsuccessful merge, and local changes should be discarded with a
    // `git reset`.
    Files.write(clone2Dir.resolve(testFilename), "foo\nbaz\n".getBytes(StandardCharsets.UTF_8));
    gitClient2.add(testFilename);
    final String commitId2 =
        gitClient2.commit(
            "Update " + testFilename + " with foo-baz", "Client Two", "client2@example.com");
    gitClient2.pull(); // should result in the change being discarded (reset)
    gitClient2.push();

    // The change from clone2 should have been discarded due to conflict with change from clone1.
    String fileContents = Files.readString(clone2Dir.resolve(testFilename));
    assertThat(fileContents, equalTo("frob\nbar\n"));

    // Verify the expected commits exist in the log: two commits from clone1
    Iterable<RevCommit> revCommitsIter = new Git(gitClient2.getRepo()).log().call();
    List<RevCommit> revCommits =
        StreamSupport.stream(revCommitsIter.spliterator(), false).collect(Collectors.toList());
    assertThat(revCommits, hasSize(2));
    // commit log is in reverse order of commits
    assertThat(revCommits.get(0).getName(), equalTo(commitId1));
    assertThat(revCommits.get(1).getName(), equalTo(commitId0));

    // Verify the expected commits exist in the private reflog of clone2.
    Collection<ReflogEntry> reflogEntriesCollection = new Git(gitClient2.getRepo()).reflog().call();
    List<ReflogEntry> reflogEntries = new ArrayList<>(reflogEntriesCollection);
    assertThat(reflogEntries, hasSize(3));
    // reflog will contain commits as seen from gitClient2
    assertThat(reflogEntries.get(0).getNewId().getName(), equalTo(commitId1));
    assertThat(
        reflogEntries.get(1).getNewId().getName(),
        equalTo(commitId2)); // the commit with that was reset
    assertThat(reflogEntries.get(2).getNewId().getName(), equalTo(commitId0));
  }

  private Path createBareRepo() throws IOException, GitAPIException {
    Path gitRemoteRepoDir = Files.createTempDirectory("gitclienttest-remote-");
    gitRemoteRepoDir.toFile().deleteOnExit();
    try (Git ignored =
        Git.init()
            .setBare(true)
            .setGitDir(gitRemoteRepoDir.resolve(".git").toFile())
            .setInitialBranch("main")
            .call()) {
      // do nothing
    }
    return gitRemoteRepoDir;
  }

  private GitClient cloneRepo(Path gitRemoteRepoDir) throws IOException {
    Path cloneDir = Files.createTempDirectory("gitclienttest-clone-");
    cloneDir.toFile().deleteOnExit(); // comment out this line to diagnose test failures
    GitClient gitClient =
        new GitClient(
            CredentialsProvider.getDefault(),
            gitRemoteRepoDir.toString(),
            "origin",
            "main",
            10,
            cloneDir);
    gitClient.ensureCloneAndPull();
    return gitClient;
  }
}
