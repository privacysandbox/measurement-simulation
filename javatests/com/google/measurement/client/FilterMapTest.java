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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.simple.JSONObject;
import org.junit.Test;

/** Unit tests for {@link com.google.measurement.client.FilterMap} */
public final class FilterMapTest {

  @Test
  public void testCreation() throws Exception {
    com.google.measurement.client.FilterMap attributionFilterMap = createExample();
    assertEquals(2, attributionFilterMap.getAttributionFilterMap().size());
    assertEquals(4, attributionFilterMap.getAttributionFilterMap().get("type").size());
    assertEquals(1, attributionFilterMap.getAttributionFilterMap().get("ctid").size());
  }

  @Test
  public void testDefaults() throws Exception {
    com.google.measurement.client.FilterMap data =
        new com.google.measurement.client.FilterMap.Builder().build();
    assertEquals(0, data.getAttributionFilterMap().size());
  }

  @Test
  public void testHashCode_equals() throws Exception {
    final com.google.measurement.client.FilterMap data1 = createExample();
    final com.google.measurement.client.FilterMap data2 = createExample();
    final Set<com.google.measurement.client.FilterMap> dataSet1 = Set.of(data1);
    final Set<com.google.measurement.client.FilterMap> dataSet2 = Set.of(data2);
    assertEquals(data1.hashCode(), data2.hashCode());
    assertEquals(data1, data2);
    assertEquals(dataSet1, dataSet2);
  }

  @Test
  public void testHashCode_notEquals() throws Exception {
    final com.google.measurement.client.FilterMap data1 = createExample();
    Map<String, List<String>> attributionFilterMap = new HashMap<>();
    attributionFilterMap.put("type", Arrays.asList("2", "3", "4"));
    attributionFilterMap.put("ctid", Collections.singletonList("id"));
    final com.google.measurement.client.FilterMap data2 =
        new com.google.measurement.client.FilterMap.Builder()
            .setAttributionFilterMap(attributionFilterMap)
            .build();
    final Set<com.google.measurement.client.FilterMap> dataSet1 = Set.of(data1);
    final Set<com.google.measurement.client.FilterMap> dataSet2 = Set.of(data2);
    assertNotEquals(data1.hashCode(), data2.hashCode());
    assertNotEquals(data1, data2);
    assertNotEquals(dataSet1, dataSet2);
  }

  @Test
  public void serializeAsJson_success() {
    // Setup
    com.google.measurement.client.FilterMap expected = createExample();
    // Execution
    JSONObject jsonObject = expected.serializeAsJson();
    com.google.measurement.client.FilterMap actual =
        new com.google.measurement.client.FilterMap.Builder().buildFilterData(jsonObject).build();
    // Assertion
    assertEquals(expected, actual);
  }

  private com.google.measurement.client.FilterMap createExample() {
    Map<String, List<String>> attributionFilterMap = new HashMap<>();
    attributionFilterMap.put("type", Arrays.asList("1", "2", "3", "4"));
    attributionFilterMap.put("ctid", Arrays.asList("id"));
    return new FilterMap.Builder().setAttributionFilterMap(attributionFilterMap).build();
  }
}
