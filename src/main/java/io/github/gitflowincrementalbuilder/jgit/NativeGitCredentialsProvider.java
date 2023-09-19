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

package io.github.gitflowincrementalbuilder.jgit;

import java.nio.file.Path;
import java.util.Map;

/**
 * Delegate authentication to system git, using any configured credential helpers.
 *
 * <p>Added this class because the parent class has default visibility.
 */
public class NativeGitCredentialsProvider extends HttpDelegatingCredentialsProvider {

  public NativeGitCredentialsProvider(
      Path projectDir, Map<String, String> additionalNativeGitEnvironment) {
    super(projectDir, additionalNativeGitEnvironment);
  }

  @Override
  public boolean isInteractive() {
    return false;
  }
}
