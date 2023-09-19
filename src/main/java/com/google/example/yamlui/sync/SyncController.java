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

package com.google.example.yamlui.sync;

import com.google.example.yamlui.config.ConfigException;
import com.google.example.yamlui.config.ConfigValidator;
import com.google.example.yamlui.git.GitClient;
import com.google.example.yamlui.git.GitException;
import com.google.example.yamlui.schema.SchemaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Provides an endpoint that enables users to trigger a sync (<code>git pull</code>) from the remote
 * Git repository.
 *
 * <p>This is useful in situations where changes can be pushed to the remote Git repository from
 * other clients. For instance, in a situation where:
 *
 * <ol>
 *   <li>developers can push config directly to the Git repository, and
 *   <li>operators can push config to the Git repository using this application.
 * </ol>
 */
@RestController
public class SyncController {

  private static final Logger LOG = LoggerFactory.getLogger(SyncController.class);

  private final GitClient gitClient;
  private final ConfigValidator configValidator;

  @Autowired
  SyncController(GitClient gitClient, ConfigValidator configValidator) {
    this.gitClient = gitClient;
    this.configValidator = configValidator;
  }

  @GetMapping("/sync")
  RedirectView sync(WebRequest request, RedirectAttributes redirectAttributes) {
    LOG.info("Pulling latest from config repo");
    try {
      gitClient.pull();
    } catch (GitException e) {
      LOG.error("Problem pulling from config repo", e);
      redirectAttributes.addAttribute("error", "pull");
    }
    LOG.info("Reloading JSON schemas");
    try {
      configValidator.loadJsonSchemas();
    } catch (ConfigException | SchemaException e) {
      LOG.error("Could not load JSON schemas for server-side validation", e);
      redirectAttributes.addAttribute("error", "schemas");
    }
    return new RedirectView(getRedirectUri(request));
  }

  String getRedirectUri(WebRequest request) {
    String referrer = request.getHeader("referer");
    if (referrer == null) {
      referrer = "";
    }
    UriComponents uriComponents =
        UriComponentsBuilder.fromHttpUrl(referrer)
            .replaceQueryParam("error", "")
            .replaceQueryParam("result", "sync_success")
            .build();
    return uriComponents.getPath() + "?" + uriComponents.getQuery();
  }
}
