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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.rubidium.aggregation.AggregateFilterData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class FilterTest {

  @Test
  public void testIsFilterMatchReturnTrue() {
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    Map<String, List<String>> triggerFilterMap = new HashMap<>();
    triggerFilterMap.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
    triggerFilterMap.put("id", Arrays.asList("1", "2"));
    AggregateFilterData triggerFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
    assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
  }

  @Test
  public void testIsFilterMatchReturnFalse() {
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    Map<String, List<String>> triggerFilterMap = new HashMap<>();
    triggerFilterMap.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap.put("product", Arrays.asList("1", "2")); // doesn't match.
    triggerFilterMap.put("id", Arrays.asList("1", "2"));
    AggregateFilterData triggerFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
    assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
  }

  @Test
  public void testIsNotFilterMatchReturnTrue() {
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    Map<String, List<String>> triggerFilterMap = new HashMap<>();
    triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
    triggerFilterMap.put("product", Arrays.asList("1", "2")); // doesn't match.
    triggerFilterMap.put("id", Arrays.asList("1", "2"));
    AggregateFilterData triggerFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
    assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
  }

  @Test
  public void testIsNotFilterMatchReturnFalse() {
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    Map<String, List<String>> triggerFilterMap = new HashMap<>();
    triggerFilterMap.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
    triggerFilterMap.put("id", Arrays.asList("1", "2"));
    AggregateFilterData triggerFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
    assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
  }
}
