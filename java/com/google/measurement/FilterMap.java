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

package com.google.measurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** POJO for FilterMap. */
public class FilterMap {
  private Map<String, List<String>> mAttributionFilterMap;

  private FilterMap() {
    mAttributionFilterMap = new HashMap<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FilterMap)) {
      return false;
    }
    FilterMap attributionFilterMap = (FilterMap) obj;
    return Objects.equals(mAttributionFilterMap, attributionFilterMap.mAttributionFilterMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mAttributionFilterMap);
  }

  /** Returns the attribution filter map. */
  public Map<String, List<String>> getAttributionFilterMap() {
    return mAttributionFilterMap;
  }

  /**
   * Serializes the object into a {@link JSONObject}.
   *
   * @return serialized {@link JSONObject}.
   */
  public JSONObject serializeAsJson() {
    if (mAttributionFilterMap == null) {
      return null;
    }
    try {
      JSONObject result = new JSONObject();
      for (String key : mAttributionFilterMap.keySet()) {
        JSONArray data = new JSONArray();
        for (String val : mAttributionFilterMap.get(key)) {
          data.add(val);
        }
        result.put(key, data);
      }
      return result;
    } catch (Exception e) {
      return null;
    }
  }

  /** Builder for {@link FilterMap}. */
  public static final class Builder {
    private final FilterMap mBuilding;

    public Builder() {
      mBuilding = new FilterMap();
    }

    /** See {@link FilterMap#getAttributionFilterMap()}. */
    public Builder setAttributionFilterMap(Map<String, List<String>> attributionFilterMap) {
      mBuilding.mAttributionFilterMap = attributionFilterMap;
      return this;
    }

    /** Builds FilterMap from JSONObject. */
    public Builder buildFilterData(JSONObject jsonObject) {
      Map<String, List<String>> filterMap = new HashMap<>();
      for (Object key : jsonObject.keySet()) {
        List<String> filterMapList = new ArrayList<>();
        Object data = jsonObject.get(key);
        if (data instanceof JSONArray) {
          JSONArray jsonArray = (JSONArray) data;
          for (int i = 0; i < jsonArray.size(); i++) {
            filterMapList.add((String) jsonArray.get(i));
          }
        } else if (data instanceof List) {
          List<String> vals = (List) data;
          for (String val : vals) {
            filterMapList.add(val);
          }
        }
        filterMap.put((String) key, filterMapList);
      }
      mBuilding.mAttributionFilterMap = filterMap;
      return this;
    }

    /** Build the {@link FilterMap}. */
    public FilterMap build() {
      return mBuilding;
    }
  }
}
