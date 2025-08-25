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

package com.google.measurement.client;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class ExtensionEventTest {

  @Test
  public void testBuildExtensionEventFromJson() throws ParseException {
    String extensionEventData =
        "{\"user_id\": \"U1\", \"uri\": \"android-app://example2.d1.test\","
            + " \"timestamp\": \"1642218050000\", \"action\": \"install\"}";

    JSONParser parser = new JSONParser();
    com.google.measurement.client.ExtensionEvent event =
        ExtensionEvent.buildExtensionEventFromJson((JSONObject) parser.parse(extensionEventData));
    assertTrue(event.getAction().equals("install"));
    assertTrue(event.getUri().toString().equals("android-app://example2.d1.test"));
    assertSame(0, Long.compare(1642218050000L, event.getTimestamp()));
  }
}
