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

/** POJO for AggregatableAttributionTrigger. */
public class AggregatableAttributionTrigger {
  private List<AggregateTriggerData> mTriggerData;
  private Map<String, Integer> mValues;

  private AggregatableAttributionTrigger() {
    mTriggerData = new ArrayList<>();
    mValues = new HashMap<>();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregatableAttributionTrigger)) {
      return false;
    }
    AggregatableAttributionTrigger attributionTrigger = (AggregatableAttributionTrigger) obj;
    return Objects.equals(mTriggerData, attributionTrigger.mTriggerData)
        && Objects.equals(mValues, attributionTrigger.mValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mTriggerData, mValues);
  }

  /**
   * Returns all trigger_data which define individual conversion data that we want to add to the
   * conversion side aggregation key.
   */
  public List<AggregateTriggerData> getTriggerData() {
    return mTriggerData;
  }

  /** Returns the value map which contains the value for each aggregatable_source. */
  public Map<String, Integer> getValues() {
    return mValues;
  }

  /** Builder for {@link AggregatableAttributionTrigger}. */
  public static final class Builder {
    private final AggregatableAttributionTrigger mBuilding;

    public Builder() {
      mBuilding = new AggregatableAttributionTrigger();
    }

    /** See {@link AggregatableAttributionTrigger#getTriggerData()}. */
    public Builder setTriggerData(List<AggregateTriggerData> triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    /** See {@link AggregatableAttributionTrigger#getValues()}. */
    public Builder setValues(Map<String, Integer> values) {
      mBuilding.mValues = values;
      return this;
    }

    /** Build the {@link AggregatableAttributionTrigger}. */
    public AggregatableAttributionTrigger build() {
      return mBuilding;
    }
  }
}
