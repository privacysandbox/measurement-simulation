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

package com.google.measurement.util;

import com.google.measurement.FilterMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** Filtering utilities for measurement. */
public class Filter {
  private Filter() {}

  /**
   * Checks whether source filter and trigger filter are matched. When a key is only present in
   * source or trigger, ignore that key. When a key is present both in source and trigger, the key
   * matches if the intersection of values is not empty.
   *
   * @param sourceFilter the {@code FilterMap} in attribution source.
   * @param triggerFilters a list of {@code FilterMap}, the trigger filter set.
   * @param isFilter true for filters, false for not_filters.
   * @return return true when all keys shared by source filter matches with at least one trigger
   *     filter in triggerFilters.
   */
  public static boolean isFilterMatch(
      FilterMap sourceFilter, List<FilterMap> triggerFilters, boolean isFilter) {
    if (sourceFilter.getAttributionFilterMap().isEmpty() || triggerFilters.isEmpty()) {
      return true;
    }
    for (FilterMap filterMap : triggerFilters) {
      if (isFilterMatch(sourceFilter, filterMap, isFilter)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isFilterMatch(
      FilterMap sourceFilter, FilterMap triggerFilter, boolean isFilter) {
    for (String key : triggerFilter.getAttributionFilterMap().keySet()) {
      if (!sourceFilter.getAttributionFilterMap().containsKey(key)) {
        continue;
      }
      // Finds the intersection of two value lists.
      List<String> sourceValues = sourceFilter.getAttributionFilterMap().get(key);
      List<String> triggerValues = triggerFilter.getAttributionFilterMap().get(key);
      if (!matchFilterValues(sourceValues, triggerValues, isFilter)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchFilterValues(
      List<String> sourceValues, List<String> triggerValues, boolean isFilter) {
    if (triggerValues.isEmpty()) {
      return isFilter ? sourceValues.isEmpty() : !sourceValues.isEmpty();
    }
    Set<String> intersection = new HashSet<>(sourceValues);
    intersection.retainAll(triggerValues);
    return isFilter ? !intersection.isEmpty() : intersection.isEmpty();
  }

  /**
   * Deserializes the provided {@link JSONArray} of filters into filter set.
   *
   * @param filters serialized filter set
   * @return deserialized filter set
   */
  public static List<FilterMap> deserializeFilterSet(JSONArray filters) {
    List<FilterMap> filterSet = new ArrayList<>();
    for (int i = 0; i < filters.size(); i++) {
      FilterMap filterMap =
          new FilterMap.Builder().buildFilterData((JSONObject) filters.get(i)).build();
      filterSet.add(filterMap);
    }
    return filterSet;
  }

  /**
   * Builds {@link JSONArray} out of the list of {@link FilterMap} provided by serializing it
   * recursively.
   *
   * @param filterMaps to be serialized
   * @return serialized filter maps
   */
  public static JSONArray serializeFilterSet(List<FilterMap> filterMaps) {
    JSONArray serializedFilterMaps = new JSONArray();
    for (FilterMap sourceFilter : filterMaps) {
      serializedFilterMaps.add(sourceFilter.serializeAsJson());
    }
    return serializedFilterMaps;
  }

  /**
   * Filters can be available in either {@link JSONObject} format or {@link JSONArray} format. For
   * consistency across the board, this method wraps the {@link JSONObject} into {@link JSONArray}.
   *
   * @param json json where to look for the filter object
   * @param key key with which the filter object is associated
   * @return wrapped {@link JSONArray}
   */
  public static JSONArray maybeWrapFilters(JSONObject json, String key) {
    Object maybeFilterMap = json.get(key);
    if (maybeFilterMap instanceof JSONObject) {
      JSONArray filterSet = new JSONArray();
      filterSet.add(maybeFilterMap);
      return filterSet;
    }
    return (JSONArray) json.get(key);
  }
}
