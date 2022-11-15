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

package com.google.rubidium;

import com.google.rubidium.aggregation.AggregateFilterData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Filtering utilities for measurement. */
public class FilterUtil {
  /**
   * Checks whether source filter and trigger filter are matched. When a key is only present in
   * source or trigger, ignore that key. When a key is present both in source and trigger, the key
   * matches if the intersection of values is not empty.
   *
   * @param sourceFilter the filter_data field in attribution source.
   * @param triggerFilter the AttributionTriggerData in attribution trigger.
   * @param isFilter true for filters, false for not_filters.
   * @return return true when all keys in source filter and trigger filter are matched.
   */
  public static boolean isFilterMatch(
      AggregateFilterData sourceFilter, AggregateFilterData triggerFilter, boolean isFilter) {
    for (String key : triggerFilter.getAttributionFilterMap().keySet()) {
      if (!sourceFilter.getAttributionFilterMap().containsKey(key)) {
        continue;
      }
      // Finds the intersection of two value lists.
      List<String> sourceValues = sourceFilter.getAttributionFilterMap().get(key);
      List<String> triggerValues = triggerFilter.getAttributionFilterMap().get(key);
      Set<String> common = new HashSet<>(sourceValues);
      common.retainAll(triggerValues);
      // For filters, return false when one key doesn't have intersection.
      if (isFilter && common.size() == 0) {
        return false;
      }
      // For not_filters, return false when one key has intersection.
      if (!isFilter && common.size() != 0) {
        return false;
      }
    }
    return true;
  }
}
