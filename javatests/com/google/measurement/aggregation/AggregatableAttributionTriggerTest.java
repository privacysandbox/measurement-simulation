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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;

/** Unit tests for {@link AggregatableAttributionTrigger} */
public final class AggregatableAttributionTriggerTest {

  @Test
  public void testCreation() throws Exception {
    AggregateTriggerData attributionTriggerData1 =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(159L))
            .setSourceKeys(new HashSet<>(Arrays.asList("campCounts", "campGeoCounts")))
            .build();
    AggregateTriggerData attributionTriggerData2 =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(5L))
            .setSourceKeys(
                new HashSet<>(Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
            .build();
    Map<String, Integer> values = new HashMap<>();
    values.put("campCounts", 1);
    values.put("campGeoCounts", 100);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
            .setValues(values)
            .build();
    assertEquals(2, attributionTrigger.getTriggerData().size());
    assertEquals(159L, attributionTrigger.getTriggerData().get(0).getKey().longValue());
    assertEquals(2, attributionTrigger.getTriggerData().get(0).getSourceKeys().size());
    assertEquals(5L, attributionTrigger.getTriggerData().get(1).getKey().longValue());
    assertEquals(3, attributionTrigger.getTriggerData().get(1).getSourceKeys().size());
    assertEquals(1, attributionTrigger.getValues().get("campCounts").intValue());
    assertEquals(100, attributionTrigger.getValues().get("campGeoCounts").intValue());
  }

  @Test
  public void testDefaults() throws Exception {
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder().build();
    assertEquals(0, attributionTrigger.getTriggerData().size());
    assertEquals(0, attributionTrigger.getValues().size());
  }
}
