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

import com.google.measurement.util.UnsignedLong;
import java.io.Serializable;
import java.util.Objects;
import org.json.simple.JSONObject;

/** POJO for attributed trigger. */
public class AttributedTrigger implements Serializable {
  private final String mTriggerId;
  private final long mPriority;
  private final UnsignedLong mTriggerData;
  private final long mValue;
  private final long mTriggerTime;
  private final UnsignedLong mDedupKey;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AttributedTrigger)) {
      return false;
    }
    AttributedTrigger t = (AttributedTrigger) obj;

    return mTriggerId.equals(t.mTriggerId)
        && mPriority == t.mPriority
        && Objects.equals(mTriggerData, t.mTriggerData)
        && mValue == t.mValue
        && mTriggerTime == t.mTriggerTime
        && Objects.equals(mDedupKey, t.mDedupKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mTriggerId, mPriority, mTriggerData, mValue, mTriggerTime, mDedupKey);
  }

  public AttributedTrigger(JSONObject json) {
    mTriggerId = (String) json.get(JsonKeys.TRIGGER_ID);
    if (json.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY)) {
      mPriority = (Long) json.get(ReportSpecUtil.FlexEventReportJsonKeys.PRIORITY);
    } else {
      mPriority = 0L;
    }
    if (json.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA)) {
      mTriggerData =
          new UnsignedLong(
              String.valueOf(json.get(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA)));
    } else {
      mTriggerData = null;
    }
    if (json.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.VALUE)) {
      mValue = (Long) json.get(ReportSpecUtil.FlexEventReportJsonKeys.VALUE);
    } else {
      mValue = 0L;
    }
    if (json.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME)) {
      mTriggerTime = (Long) json.get(ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_TIME);
    } else {
      mTriggerTime = 0L;
    }
    if (json.containsKey(JsonKeys.DEDUP_KEY)) {
      mDedupKey = new UnsignedLong(String.valueOf(json.get(JsonKeys.DEDUP_KEY)));
    } else {
      mDedupKey = null;
    }
  }

  public AttributedTrigger(
      String triggerId,
      long priority,
      UnsignedLong triggerData,
      long value,
      long triggerTime,
      UnsignedLong dedupKey) {
    mTriggerId = triggerId;
    mPriority = priority;
    mTriggerData = triggerData;
    mValue = value;
    mTriggerTime = triggerTime;
    mDedupKey = dedupKey;
  }

  public static AttributedTrigger copy(AttributedTrigger copyFrom) {
    return new AttributedTrigger(
        copyFrom.getTriggerId(),
        copyFrom.getPriority(),
        new UnsignedLong(copyFrom.getTriggerData().getValue()),
        copyFrom.getValue(),
        copyFrom.getTriggerTime(),
        new UnsignedLong(copyFrom.getDedupKey().getValue()));
  }

  public String getTriggerId() {
    return mTriggerId;
  }

  public long getPriority() {
    return mPriority;
  }

  public UnsignedLong getTriggerData() {
    return mTriggerData;
  }

  public long getValue() {
    return mValue;
  }

  public long getTriggerTime() {
    return mTriggerTime;
  }

  public UnsignedLong getDedupKey() {
    return mDedupKey;
  }

  private interface JsonKeys {
    String TRIGGER_ID = "trigger_id";
    String DEDUP_KEY = "dedup_key";
  }
}
