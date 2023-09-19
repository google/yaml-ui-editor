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

package com.google.example.yamlui.schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Reads JSON Schemas as byte arrays from the file system. */
@Repository
public class SchemaRepository {

  private final Path schemaPath;

  @Autowired
  public SchemaRepository(
      @Qualifier("localPath") Path localPath,
      @Value("${git.repository.paths.schemas:schemas}") String path) {
    this.schemaPath = localPath.resolve(path);
  }

  /**
   * Retrieves the bytes of a JSON Schema from the file system.
   *
   * @param type must match the schema file name, exluding the <code>.json</code> suffix.
   */
  public byte[] loadSchema(String type) {
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }
    Path path = schemaPath.resolve(type + ".json");
    if (!Files.isRegularFile(path)) {
      throw new SchemaException("Schema for type " + type + " not found on path " + path);
    }
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new SchemaException("Could not read schema of type " + type + " from path " + path, e);
    }
  }

  /** Returns the names of the available JSON Schemas, sorted alphabetically. */
  public List<String> getSchemaTypes() {
    try (Stream<Path> stream = Files.list(schemaPath)) {
      return stream
          .map(path -> path.getFileName().toString())
          .filter(path -> path.endsWith(".json"))
          .map(FilenameUtils::removeExtension)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new SchemaException("Could not get list of schema types", e);
    }
  }
}
