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

package com.google.rubidium.aggregation;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Unit tests for {@link AggregatableAttributionSource} */
public final class AggregatableAttributionSourceTest {
  @Test
  public void testCreation() throws Exception {
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(159L));
    aggregatableSource.put("geoValue", BigInteger.valueOf(5L));
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .build();
    assertEquals(attributionSource.getAggregatableSource().size(), 2);
    assertEquals(attributionSource.getAggregatableSource().get("campaignCounts").longValue(), 159L);
    assertEquals(attributionSource.getAggregatableSource().get("geoValue").longValue(), 5L);
  }

  @Test
  public void testDefaults() throws Exception {
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder().build();
    assertEquals(attributionSource.getAggregatableSource().size(), 0);
  }
}
