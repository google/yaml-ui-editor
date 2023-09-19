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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.example.yamlui.schema.SchemaRepository;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Enables server-side validation of configs against the corresponding JSON Schema. */
@Service
public class ConfigValidator {

  private final SchemaRepository schemaRepository;
  private final JsonSchemaFactory jsonSchemaFactory;
  private final ObjectMapper objectMapper;
  private final Map<String, JsonSchema> schemasByType;

  @Autowired
  ConfigValidator(SchemaRepository schemaRepository) {
    this.schemaRepository = schemaRepository;
    // github.com/json-editor/json-editor supports spec V3 and V4
    this.jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
    this.objectMapper = new ObjectMapper();
    this.schemasByType = new ConcurrentHashMap<>();
  }

  /**
   * Parses all available JSON Schemas and keeps them in a map keyed by type.
   *
   * <p>This avoids having to parse the JSON Schema for each validation. JsonSchema instances are
   * thread-safe.
   *
   * <p>Runs on application startup and when a user initiates a sync from the UI.
   */
  @PostConstruct
  public void loadJsonSchemas() {
    Map<String, JsonSchema> jsonSchemasByType =
        schemaRepository.getSchemaTypes().stream()
            .collect(Collectors.toMap(type -> type, this::loadJsonSchema));
    schemasByType.putAll(jsonSchemasByType);
  }

  /** Loads a JSON Schema from the repository and parses it. */
  JsonSchema loadJsonSchema(String type) {
    byte[] schemaAsJsonBytes = schemaRepository.loadSchema(type);
    return jsonSchemaFactory.getSchema(new String(schemaAsJsonBytes, StandardCharsets.UTF_8));
  }

  /**
   * Validates the config (as JSON) using the JSON Schema specified by <code>type</code>.
   *
   * @return a set of validation messages. If validation was successful, returns an empty set.
   */
  Set<String> validateConfig(String type, byte[] configAsJsonBytes) {
    try {
      JsonSchema jsonSchema = schemasByType.get(type);
      JsonNode config = objectMapper.readTree(configAsJsonBytes);
      Set<ValidationMessage> messages = jsonSchema.validate(config);
      return messages.stream()
          .map(ValidationMessage::getMessage)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new ConfigException("Could not validate config of type " + type, e);
    }
  }
}
