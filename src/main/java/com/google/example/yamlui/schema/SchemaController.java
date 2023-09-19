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

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class SchemaController {

  private final SchemaRepository schemaRepository;

  @Autowired
  SchemaController(SchemaRepository schemaRepository) {
    this.schemaRepository = schemaRepository;
  }

  @GetMapping(value = "/schema/{type}.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  byte[] loadSchema(@PathVariable("type") String type) {
    try {
      return schemaRepository.loadSchema(type);
    } catch (SchemaException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "schema " + type + " not available");
    }
  }

  @GetMapping(value = "/schemas", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  List<String> listSchemas() {
    return schemaRepository.getSchemaTypes();
  }
}
