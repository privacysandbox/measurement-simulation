/*
 * Copyright (C) 2022 Google LLC
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

package com.google.measurement.client.util;

import static org.junit.Assert.assertEquals;

import com.google.measurement.client.Uri;
import org.junit.Test;

/** Unit tests for {@link BaseUriExtractor} */
public class BaseUriExtractorTest {

  @Test
  public void testGetBaseUri() {
    assertEquals(
        BaseUriExtractor.getBaseUri(Uri.parse("https://www.example.test/abc")).toString(),
        "https://www.example.test");
    assertEquals(
        BaseUriExtractor.getBaseUri(Uri.parse("android-app://com.example.sample")).toString(),
        "android-app://com.example.sample");
    assertEquals(
        BaseUriExtractor.getBaseUri(Uri.parse("https://www.example.test:8080/abc")).toString(),
        "https://www.example.test:8080");
  }
}
