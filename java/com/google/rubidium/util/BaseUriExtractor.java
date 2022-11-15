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

package com.google.rubidium.util;

import java.net.URI;
import java.net.URISyntaxException;

/** Class to extract URI. */
public class BaseUriExtractor {
  /**
   * Returns the base URI of the given URI. For example: "https://www.example.com/abc" ->
   * "https://www.example.com" "android-app://com.example.sample" ->
   * "android-app://com.example.sample" "https://www.example.com:8080/abc" ->
   * "https://www.example.com:8080"
   */
  public static URI getBaseUri(URI uri) {
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException(String.format("URI should be absolute. Input: %s", uri));
    }
    try {
      return new URI(uri.getScheme() + "://" + parseAuthority(uri.toString()));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return null;
  }

  private BaseUriExtractor() {}

  private static String parseAuthority(String uriString) {
    int schemeSeparator = uriString.indexOf(':');
    int length = uriString.length();
    // If "//" follows the scheme separator, we have an authority.
    if (length > schemeSeparator + 2
        && uriString.charAt(schemeSeparator + 1) == '/'
        && uriString.charAt(schemeSeparator + 2) == '/') {
      // We have an authority.
      // Look for the start of the path, query, or fragment, or the
      // end of the string.
      int end = schemeSeparator + 3;
      LOOP:
      while (end < length) {
        switch (uriString.charAt(end)) {
          case '/': // Start of path
          case '?': // Start of query
          case '#': // Start of fragment
            break LOOP;
        }
        end++;
      }
      return uriString.substring(schemeSeparator + 3, end);
    } else {
      return null;
    }
  }
}
