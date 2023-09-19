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

import com.google.example.yamlui.git.GitException;
import com.google.example.yamlui.yamljson.YamlJsonMapper;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class ConfigController {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigController.class);

  private final ConfigRepository configRepository;
  private final ConfigValidator configValidator;
  private final YamlJsonMapper yamlJsonMapper;
  private final boolean validate;

  @Autowired
  ConfigController(
      ConfigRepository configRepository,
      ConfigValidator configValidator,
      YamlJsonMapper yamlJsonMapper,
      @Value("${validation.server}") boolean validate) {
    this.configRepository = configRepository;
    this.configValidator = configValidator;
    this.yamlJsonMapper = yamlJsonMapper;
    this.validate = validate;
  }

  /**
   * loadConfig() reads the YAML config file of the specified type from the repository, converts it
   * to JSON, and sends the JSON in the HTTP response body.
   */
  @GetMapping(value = "/config/{type}.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  ResponseEntity<byte[]> loadConfig(@PathVariable("type") String type) {
    LOG.info("Loading {} config", type);
    try {
      Config config = configRepository.loadConfig(type);
      byte[] configAsYaml = config.bytes();
      byte[] configAsJson = yamlJsonMapper.toJson(configAsYaml);
      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setETag("\"" + config.version() + "\"");
      return ResponseEntity.ok().headers(responseHeaders).body(configAsJson);
    } catch (ConfigException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "config " + type + " not found");
    }
  }

  /**
   * saveConfig() accepts config as JSON, validates it, converts it to YAML and stores it in the
   * repository. It sends back an HTTP response with a JSON payload that includes a `location`
   * field. The client JavaScript code redirects the browser to this URL. This method does _not_ set
   * the `Location` HTTP response header because the `fetch()` function on the client would treat
   * that as a redirect to be followed by the AJAX call.
   */
  @RequestMapping(
      value = "/config/{type}.json",
      method = {RequestMethod.POST, RequestMethod.PUT},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  Map<String, String> saveConfig(
      @RequestHeader(HttpHeaders.ETAG) String etag,
      @RequestBody byte[] requestBody,
      @PathVariable("type") String type,
      @AuthenticationPrincipal User user,
      WebRequest request,
      RedirectAttributes redirectAttributes) {
    String version = etag == null ? "" : etag.replaceAll("\"", "");
    LOG.info("Saving {} config, base version {}", type, version);
    byte[] configAsJsonBytes = requestBody;
    if (validate) {
      Set<String> validationMessages = configValidator.validateConfig(type, configAsJsonBytes);
      if (validationMessages != null && validationMessages.size() > 0) {
        LOG.warn("Validation errors for config of type {}: {}", type, validationMessages);
        return Collections.singletonMap(
            "location", getRedirectUri(request, type, "error", "Validation failed"));
      }
    }
    byte[] configAsYamlBytes = yamlJsonMapper.toYaml(configAsJsonBytes);
    Config config = new Config(type, version, configAsYamlBytes);
    try {
      configRepository.saveConfig(config, type, user);
      return Collections.singletonMap(
          "location", getRedirectUri(request, type, "result", "save_success"));
    } catch (ConfigException | GitException e) {
      LOG.error("Could not save changes to config repository", e);
      return Collections.singletonMap(
          "location", getRedirectUri(request, type, "error", "Save failed"));
    } catch (ConcurrentModificationException e) {
      LOG.warn("Concurrent modification of config " + type + ": " + e.getMessage());
      return Collections.singletonMap(
          "location",
          getRedirectUri(request, type, "error", "Concurrent modification, please try again."));
    }
  }

  private String getRedirectUri(WebRequest request, String type, String outcome, String message) {
    String referrer = request.getHeader("Referer");
    if (referrer == null || referrer.equals("")) {
      referrer = request.getContextPath() + "?type=" + type;
    }
    UriComponents uriComponents =
        UriComponentsBuilder.fromHttpUrl(referrer)
            .replaceQueryParam("error", "")
            .replaceQueryParam("result", "")
            .replaceQueryParam(outcome, message)
            .build();
    return uriComponents.getPath() + "?" + uriComponents.getQuery();
  }
}
