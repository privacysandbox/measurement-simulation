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

package com.google.measurement.client.aggregation;

import com.google.measurement.client.FilterMap;
import java.math.BigInteger;
import java.util.Objects;
import java.util.TreeMap;

/** POJO for AggregatableAttributionSource. */
public class AggregatableAttributionSource {
  private TreeMap<String, BigInteger> mAggregatableSource;
  private FilterMap mFilterMap;

  private AggregatableAttributionSource() {
    mAggregatableSource = new TreeMap<>();
    mFilterMap = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregatableAttributionSource)) {
      return false;
    }
    AggregatableAttributionSource attributionSource = (AggregatableAttributionSource) obj;
    return Objects.equals(mAggregatableSource, attributionSource.mAggregatableSource)
        && Objects.equals(mFilterMap, attributionSource.mFilterMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mAggregatableSource);
  }

  /**
   * Returns aggregatable_source map with key represents the name field in JSON, value represents
   * the id field in JSON.
   */
  public TreeMap<String, BigInteger> getAggregatableSource() {
    return mAggregatableSource;
  }

  /** Returns aggregate filter data which represents a map in JSONObject. */
  public FilterMap getFilterMap() {
    return mFilterMap;
  }

  /** Builder for {@link AggregatableAttributionSource}. */
  public static final class Builder {
    private final AggregatableAttributionSource mBuilding;

    public Builder() {
      mBuilding = new AggregatableAttributionSource();
    }

    /** See {@link AggregatableAttributionSource#getAggregatableSource()}. */
    public Builder setAggregatableSource(TreeMap<String, BigInteger> aggregatableSource) {
      mBuilding.mAggregatableSource = aggregatableSource;
      return this;
    }

    /** See {@link AggregatableAttributionSource#getFilterMap()}. */
    public Builder setFilterMap(FilterMap filterMap) {
      mBuilding.mFilterMap = filterMap;
      return this;
    }

    /** Build the {@link AggregatableAttributionSource}. */
    public AggregatableAttributionSource build() {
      return mBuilding;
    }
  }
}
