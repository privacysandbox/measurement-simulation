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

import static org.junit.Assert.assertEquals;

import java.net.URI;
import org.junit.Test;

public class BaseUriExtractorTest {
  @Test
  public void testGetBaseUri() {
    assertEquals(
        BaseUriExtractor.getBaseUri(URI.create("https://www.example.com/abc")).toString(),
        "https://www.example.com");
    assertEquals(
        BaseUriExtractor.getBaseUri(URI.create("android-app://com.example.sample")).toString(),
        "android-app://com.example.sample");
    assertEquals(
        BaseUriExtractor.getBaseUri(URI.create("https://www.example.com:8080/abc")).toString(),
        "https://www.example.com:8080");
  }
}
