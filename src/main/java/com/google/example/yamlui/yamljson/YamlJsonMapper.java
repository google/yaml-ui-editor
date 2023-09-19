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

package com.google.example.yamlui.yamljson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/** Converts between JSON and YAML. */
@Service
public class YamlJsonMapper {

  private final ObjectMapper objectMapper;
  private final YAMLMapper yamlMapper;

  YamlJsonMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.yamlMapper =
        YAMLMapper.builder()
            .disable(YAMLParser.Feature.EMPTY_STRING_AS_NULL)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();
  }

  /** Converts JSON to YAML. */
  public byte[] toYaml(byte[] json) {
    try {
      JsonNode jsonNode = objectMapper.readTree(json);
      return yamlMapper.writeValueAsBytes(jsonNode);
    } catch (IOException e) {
      throw new com.google.example.yamlui.yamljson.YamlJsonMapperException(
          "Could not convert JSON to YAML: \n" + new String(json, StandardCharsets.UTF_8), e);
    }
  }

  /** Converts YAML to JSON. */
  public byte[] toJson(byte[] yaml) {
    try {
      JsonNode jsonNode = yamlMapper.readTree(yaml);
      return objectMapper.writeValueAsBytes(jsonNode);
    } catch (IOException e) {
      throw new com.google.example.yamlui.yamljson.YamlJsonMapperException(
          "Could not convert YAML to JSON: \n" + new String(yaml, StandardCharsets.UTF_8), e);
    }
  }
}
