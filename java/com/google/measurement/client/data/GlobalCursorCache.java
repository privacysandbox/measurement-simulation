/*
 * Copyright 2025 Google LLC
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

package com.google.measurement.client.data;

import java.util.HashMap;
import java.util.Map;

public class GlobalCursorCache {
  private Map<String, Map<String, Integer>> mCache;

  public GlobalCursorCache() {
    mCache = new HashMap<>();
  }

  public Map<String, Integer> getOrCreateColumnIndexCache(String sql) {
    if (mCache.get(sql) == null) {
      mCache.put(sql, new HashMap<>());
    }

    return mCache.get(sql);
  }
}
