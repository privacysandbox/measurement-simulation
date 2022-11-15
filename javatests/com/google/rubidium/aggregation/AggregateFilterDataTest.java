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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Unit tests for {@link AggregateFilterData} */
public final class AggregateFilterDataTest {
  @Test
  public void testCreation() throws Exception {
    Map<String, List<String>> attributionFilterMap = new HashMap<>();
    attributionFilterMap.put("type", Arrays.asList("1", "2", "3", "4"));
    attributionFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData attributionFilterData =
        new AggregateFilterData.Builder().setAttributionFilterMap(attributionFilterMap).build();
    assertEquals(attributionFilterData.getAttributionFilterMap().size(), 2);
    assertEquals(attributionFilterData.getAttributionFilterMap().get("type").size(), 4);
    assertEquals(attributionFilterData.getAttributionFilterMap().get("ctid").size(), 1);
  }

  @Test
  public void testDefaults() throws Exception {
    AggregateFilterData data = new AggregateFilterData.Builder().build();
    assertEquals(data.getAttributionFilterMap().size(), 0);
  }
}
