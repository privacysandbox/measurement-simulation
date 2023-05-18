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

package com.google.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.measurement.FilterMap;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Unit tests for {@link AggregateTriggerData} */
public final class AggregateTriggerDataTest {

  @Test
  public void testCreation() throws Exception {
    Map<String, List<String>> attributionFilterMap = new HashMap<>();
    attributionFilterMap.put("ctid", Arrays.asList("1", "2"));
    FilterMap filterData =
        new FilterMap.Builder().setAttributionFilterMap(attributionFilterMap).build();
    AggregateTriggerData attributionTriggerData =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(5L))
            .setSourceKeys(
                new HashSet<>(Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
            .setFilterSet(Arrays.asList(filterData))
            .build();
    assertEquals(5L, attributionTriggerData.getKey().longValue());
    assertEquals(3, attributionTriggerData.getSourceKeys().size());
    assertTrue(attributionTriggerData.getFilterSet().isPresent());
    FilterMap data = attributionTriggerData.getFilterSet().get().get(0);
    assertEquals(2, data.getAttributionFilterMap().get("ctid").size());
  }

  @Test
  public void testDefaults() throws Exception {
    AggregateTriggerData attributionTriggerData = new AggregateTriggerData.Builder().build();
    assertNull(attributionTriggerData.getKey());
    assertEquals(0, attributionTriggerData.getSourceKeys().size());
    assertFalse(attributionTriggerData.getFilterSet().isPresent());
    assertFalse(attributionTriggerData.getNotFilterSet().isPresent());
  }
}
