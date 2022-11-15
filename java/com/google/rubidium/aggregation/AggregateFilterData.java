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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** POJO for AggregatableAttributionFilterData. */
public class AggregateFilterData {
  private Map<String, List<String>> mAttributionFilterMap;

  AggregateFilterData() {
    mAttributionFilterMap = new HashMap<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateFilterData)) {
      return false;
    }
    AggregateFilterData attributionFilterData = (AggregateFilterData) obj;
    return Objects.equals(mAttributionFilterMap, attributionFilterData.mAttributionFilterMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mAttributionFilterMap);
  }

  /** Returns the attribution filter map. */
  public Map<String, List<String>> getAttributionFilterMap() {
    return mAttributionFilterMap;
  }

  /** Builder for {@link AggregateFilterData}. */
  public static final class Builder {
    private final AggregateFilterData mBuilding;

    public Builder() {
      mBuilding = new AggregateFilterData();
    }

    /** See {@link AggregateFilterData#getAttributionFilterMap()}. */
    public Builder setAttributionFilterMap(Map<String, List<String>> attributionFilterMap) {
      mBuilding.mAttributionFilterMap = attributionFilterMap;
      return this;
    }

    /** Builds AggregateFilterData from JSONObject. */
    public Builder buildAggregateFilterData(JSONObject jsonObject) {
      Map<String, List<String>> aggregateFilterData = new HashMap<>();
      for (Object objkey : jsonObject.keySet()) {
        String key = (String) objkey;
        JSONArray jsonArray = (JSONArray) jsonObject.get(key);
        List<String> filterDataList = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
          filterDataList.add((String) jsonArray.get(i));
        }
        aggregateFilterData.put(key, filterDataList);
      }
      mBuilding.mAttributionFilterMap = aggregateFilterData;
      return this;
    }

    /** Build the {@link AggregateFilterData}. */
    public AggregateFilterData build() {
      return mBuilding;
    }
  }
}
