/*
 * Copyright 2022 Google LLC
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

package com.google.measurement.util;

import java.net.URI;

public final class Validation {

  public static void validateNonNull(Object... objects) throws IllegalArgumentException {
    for (Object o : objects) {
      if (o == null) {
        throw new IllegalArgumentException("Received null values");
      }
    }
  }

  public static void validateURI(URI uri) throws IllegalArgumentException {
    if (uri == null || uri.getScheme() == null) {
      throw new IllegalArgumentException("URI with no scheme is not valid");
    }
  }
}
