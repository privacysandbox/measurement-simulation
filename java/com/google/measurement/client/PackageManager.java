/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.measurement.client;

public class PackageManager {

  /**
   * Used only for testing purposes. If this method is not explicitly mocked, it will throw a
   * NameNotFoundException because SimLib does not actually have access to Android's PackageManager.
   */
  public ApplicationInfo getApplicationInfo(String host, int i) throws NameNotFoundException {
    throw new NameNotFoundException();
  }

  /**
   * This exception is thrown when a given package, application, or component name cannot be found.
   */
  public static class NameNotFoundException extends Exception {
    public NameNotFoundException() {}

    public NameNotFoundException(String name) {
      super(name);
    }
  }
}
