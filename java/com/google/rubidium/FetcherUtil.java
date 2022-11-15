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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class FetcherUtil {
  public static boolean areValidAttributionFilters(JSONObject filterData) {
    if (filterData == null || filterData.size() > SystemHealthParams.MAX_ATTRIBUTION_FILTERS) {
      return false;
    }

    for (String key : (Iterable<String>) filterData.keySet()) {
      if (key.getBytes().length > SystemHealthParams.MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING) {
        return false;
      }
      JSONArray values = (JSONArray) filterData.get(key);
      if (values == null || values.size() > SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER) {
        return false;
      }
      for (int i = 0; i < values.size(); ++i) {
        String value = (String) values.get(i);
        if (value == null
            || value.getBytes().length
                > SystemHealthParams.MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean isValidAggregateKeyId(String id) {
    return id != null
        && id.getBytes().length <= SystemHealthParams.MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID;
  }

  public static boolean isValidAggregateKeyPiece(String keyPiece) {
    if (keyPiece == null) {
      return false;
    }
    int len = keyPiece.getBytes().length;
    return (keyPiece.startsWith("0x") || keyPiece.startsWith("0X")) && 2 < len && len < 35;
  }
}
