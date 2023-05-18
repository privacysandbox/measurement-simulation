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

import com.google.measurement.FilterMap;
import com.google.measurement.util.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Unit tests for {@link AggregateDeduplicationKey} */
public class AggregateDeduplicationKeyTest {
  private static final UnsignedLong DEDUP_KEY = new UnsignedLong(10L);
  private static List<FilterMap> sFilterSet = new ArrayList<>();

  private AggregateDeduplicationKey createExample() {
    Map<String, List<String>> filter = new HashMap<>();
    List<String> filterValues = new ArrayList<>();
    filterValues.add("abc");
    filterValues.add("xyz");
    filter.put("filter1", filterValues);
    sFilterSet.add(new FilterMap.Builder().setAttributionFilterMap(filter).build());
    return new AggregateDeduplicationKey.Builder(DEDUP_KEY).setFilterSet(sFilterSet).build();
  }

  void verifyExample(AggregateDeduplicationKey aggregateDeduplicationKey) {
    assertEquals(DEDUP_KEY, aggregateDeduplicationKey.getDeduplicationKey());
    assertEquals(sFilterSet, aggregateDeduplicationKey.getFilterSet().get());
  }

  @Test
  public void testCreation() {
    verifyExample(createExample());
  }
}
