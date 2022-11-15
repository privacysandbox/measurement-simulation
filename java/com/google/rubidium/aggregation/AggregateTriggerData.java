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

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** POJO for AggregateTriggerData. */
public class AggregateTriggerData {
  private BigInteger mKey;
  private HashSet<String> mSourceKeys;
  private Optional<AggregateFilterData> mFilter;
  private Optional<AggregateFilterData> mNotFilter;

  private AggregateTriggerData() {
    mKey = null;
    mSourceKeys = new HashSet<>();
    mFilter = Optional.empty();
    mNotFilter = Optional.empty();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateTriggerData)) {
      return false;
    }
    AggregateTriggerData attributionTriggerData = (AggregateTriggerData) obj;
    return Objects.equals(mKey, attributionTriggerData.mKey)
        && Objects.equals(mSourceKeys, attributionTriggerData.mSourceKeys)
        && Objects.equals(mFilter, attributionTriggerData.mFilter)
        && Objects.equals(mNotFilter, attributionTriggerData.mNotFilter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mKey, mSourceKeys);
  }

  /** Returns trigger_data's key which will be used to generate the aggregate key. */
  public BigInteger getKey() {
    return mKey;
  }

  /** Returns the source_key set which represent which source this dimension applies to. */
  public Set<String> getSourceKeys() {
    return mSourceKeys;
  }

  /**
   * Returns the filter which controls when aggregate trigger data is used based on impression side
   * information.
   */
  public Optional<AggregateFilterData> getFilter() {
    return mFilter;
  }

  /** Returns the not_filter, reverse of filter. */
  public Optional<AggregateFilterData> getNotFilter() {
    return mNotFilter;
  }

  /** Builder for {@link AggregateTriggerData}. */
  public static final class Builder {
    private final AggregateTriggerData mBuilding;

    public Builder() {
      mBuilding = new AggregateTriggerData();
    }

    /** See {@link AggregateTriggerData#getKey()}. */
    public Builder setKey(BigInteger key) {
      mBuilding.mKey = key;
      return this;
    }

    /** See {@link AggregateTriggerData#getSourceKeys()}. */
    public Builder setSourceKeys(HashSet<String> sourceKeys) {
      mBuilding.mSourceKeys = sourceKeys;
      return this;
    }

    /** See {@link AggregateTriggerData#getFilter()}. */
    public Builder setFilter(AggregateFilterData filter) {
      mBuilding.mFilter = Optional.of(filter);
      return this;
    }

    /** See {@link AggregateTriggerData#getNotFilter()} */
    public Builder setNotFilter(AggregateFilterData notFilter) {
      mBuilding.mNotFilter = Optional.of(notFilter);
      return this;
    }

    /** Build the {@link AggregateTriggerData} */
    public AggregateTriggerData build() {
      return mBuilding;
    }
  }
}
