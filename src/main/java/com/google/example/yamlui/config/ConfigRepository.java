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

package com.google.example.yamlui.config;

import com.google.example.yamlui.git.GitClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Repository;

@Repository
class ConfigRepository {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigRepository.class);

  private final GitClient gitClient;
  private final Path configPath;
  private final String yamlExtension;

  @Autowired
  ConfigRepository(
      GitClient gitClient,
      @Value("${git.repository.paths.config:config}") String configPath,
      @Value("${git.yaml.extension:yaml}") String yamlExtension) {
    this.gitClient = gitClient;
    this.configPath = Path.of(configPath);
    this.yamlExtension = yamlExtension;
  }

  Config loadConfig(String type) {
    gitClient.pull();
    Path path = getFullPathForConfigType(type);
    if (!Files.isRegularFile(path)) {
      throw new ConfigException("config " + type + " not found on path " + path);
    }
    Path repoPath = getRepoPathForConfigType(type);
    String commitId = gitClient.getLatestCommitIdForFilePath(repoPath);
    try {
      byte[] configBytes = Files.readAllBytes(path);
      return new Config(type, commitId, configBytes);
    } catch (IOException e) {
      throw new ConfigException("Could not read config of type " + type + " from path " + path, e);
    }
  }

  void saveConfig(Config config, String type, User user) {
    Path repoPath = getRepoPathForConfigType(type);
    String commitId = gitClient.getLatestCommitIdForFilePath(repoPath);
    if (!"".equals(commitId) && !commitId.equals(config.version())) {
      throw new ConcurrentModificationException(
          "Incoming change for config "
              + repoPath
              + " is based on commit ID "
              + config.version()
              + " but most recent commit ID is "
              + commitId);
    }
    Path path = getFullPathForConfigType(type);
    try {
      Files.write(path, config.bytes());
    } catch (IOException e) {
      throw new ConfigException(
          "Could not write config of type " + type + " to file at path " + path, e);
    }
    LOG.info("Pushing changes to config repo");
    gitClient.add(getRepoPathForConfigType(type).toString());
    gitClient.commit("Update " + type + " configuration", getName(user), getEmail(user));
    boolean mergeSuccessful = gitClient.pull();
    gitClient.push();
    if (!mergeSuccessful) {
      throw new ConfigException(
          "Conflicting edits of config type " + type + " detected, discarding changes.");
    }
  }

  private Path getFullPathForConfigType(String type) {
    return gitClient.getLocalPath().resolve(configPath).resolve(type + "." + yamlExtension);
  }

  private Path getRepoPathForConfigType(String type) {
    return configPath.resolve(type + "." + yamlExtension);
  }

  /**
   * getName() turns a username into an author name to be used in an author block of a Git commit.
   * Definitely only for demo purposes!
   */
  private String getName(User user) {
    if (user == null || user.getUsername() == null || user.getUsername().equals("")) {
      return "Console UI";
    }
    String username = user.getUsername();
    return username.substring(0, 1).toUpperCase(Locale.ROOT) + username.substring(1);
  }

  /**
   * getName() turns a username into an email address to be used in an author block of a Git commit.
   * Definitely only for demo purposes!
   */
  private String getEmail(User user) {
    if (user == null || user.getUsername() == null || user.getUsername().equals("")) {
      return "user@example.com";
    }
    return user.getUsername() + "@example.com";
  }
}
