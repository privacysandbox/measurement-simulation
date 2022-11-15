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
import java.util.Objects;
import java.util.Optional;

/** Event trigger containing trigger data, priority, de-deup key and filters info. */
public class EventTrigger {
  private Long mTriggerData;
  private long mTriggerPriority;
  private Long mDedupKey;
  private Optional<AggregateFilterData> mFilter;
  private Optional<AggregateFilterData> mNotFilter;

  private EventTrigger() {
    mFilter = Optional.empty();
    mNotFilter = Optional.empty();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof EventTrigger)) {
      return false;
    }
    EventTrigger eventTrigger = (EventTrigger) obj;
    return Objects.equals(mTriggerData, eventTrigger.mTriggerData)
        && mTriggerPriority == eventTrigger.mTriggerPriority
        && Objects.equals(mDedupKey, eventTrigger.mDedupKey)
        && Objects.equals(mFilter, eventTrigger.mFilter)
        && Objects.equals(mNotFilter, eventTrigger.mNotFilter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mTriggerData, mTriggerPriority, mDedupKey, mFilter, mNotFilter);
  }

  /** Returns trigger_data for the event. */
  public Long getTriggerData() {
    return mTriggerData;
  }

  /** Trigger priority. */
  public long getTriggerPriority() {
    return mTriggerPriority;
  }

  /** De-deuplication key.. */
  public Long getDedupKey() {
    return mDedupKey;
  }

  /** Filters that should match with source's. */
  public Optional<AggregateFilterData> getFilterData() {
    return mFilter;
  }

  /** Filters that should not match with source's. */
  public Optional<AggregateFilterData> getNotFilterData() {
    return mNotFilter;
  }

  public static final class Builder {
    private final EventTrigger mBuilding;

    public Builder() {
      mBuilding = new EventTrigger();
    }

    /** See {@link EventTrigger#getTriggerData()}. */
    public EventTrigger.Builder setTriggerData(Long triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    /** See {@link EventTrigger#getTriggerPriority()}. */
    public EventTrigger.Builder setTriggerPriority(Long triggerPriority) {
      mBuilding.mTriggerPriority = triggerPriority;
      return this;
    }

    /** See {@link EventTrigger#getDedupKey()}. */
    public EventTrigger.Builder setDedupKey(Long dedupKey) {
      mBuilding.mDedupKey = dedupKey;
      return this;
    }

    /** See {@link EventTrigger#getFilterData()}. */
    public EventTrigger.Builder setFilter(AggregateFilterData filterData) {
      mBuilding.mFilter = Optional.ofNullable(filterData);
      return this;
    }

    /** See {@link EventTrigger#getNotFilterData()} ()}. */
    public EventTrigger.Builder setNotFilter(AggregateFilterData notFilterData) {
      mBuilding.mNotFilter = Optional.ofNullable(notFilterData);
      return this;
    }

    /** Build the {@link EventTrigger}. */
    public EventTrigger build() {
      return mBuilding;
    }
  }
}
