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
import java.util.TreeMap;
import org.junit.Test;

/** Unit tests for {@link AggregatableAttributionSource} */
public final class AggregatableAttributionSourceTest {

  @Test
  public void testCreation() throws Exception {
    TreeMap<String, BigInteger> aggregatableSource = new TreeMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(159L));
    aggregatableSource.put("geoValue", BigInteger.valueOf(5L));
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .build();
    assertEquals(2, attributionSource.getAggregatableSource().size());
    assertEquals(159L, attributionSource.getAggregatableSource().get("campaignCounts").longValue());
    assertEquals(5L, attributionSource.getAggregatableSource().get("geoValue").longValue());
  }

  @Test
  public void testDefaults() throws Exception {
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder().build();
    assertEquals(0, attributionSource.getAggregatableSource().size());
  }
}
