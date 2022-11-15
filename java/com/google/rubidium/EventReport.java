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

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.json.simple.JSONObject;

public class EventReport implements Serializable {
  private String mId;
  private long mSourceId;
  private long mReportTime;
  private long mTriggerTime;
  private long mTriggerPriority;
  private URI mAttributionDestination;
  private String mEnrollmentId;
  private long mTriggerData;
  private Long mTriggerDedupKey;
  private double mRandomizedTriggerRate;
  private Status mStatus;
  private Source.SourceType mSourceType;

  public enum Status {
    PENDING,
    DELIVERED
  }

  private EventReport() {
    mTriggerDedupKey = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof EventReport)) {
      return false;
    }
    EventReport eventReport = (EventReport) obj;
    return mStatus == eventReport.mStatus
        && mReportTime == eventReport.mReportTime
        && Objects.equals(mAttributionDestination, eventReport.mAttributionDestination)
        && Objects.equals(mEnrollmentId, eventReport.mEnrollmentId)
        && mTriggerTime == eventReport.mTriggerTime
        && mTriggerData == eventReport.mTriggerData
        && mSourceId == eventReport.mSourceId
        && mTriggerPriority == eventReport.mTriggerPriority
        && Objects.equals(mTriggerDedupKey, eventReport.mTriggerDedupKey)
        && mSourceType == eventReport.mSourceType
        && mRandomizedTriggerRate == eventReport.mRandomizedTriggerRate;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mStatus,
        mReportTime,
        mAttributionDestination,
        mEnrollmentId,
        mTriggerTime,
        mTriggerData,
        mSourceId,
        mTriggerPriority,
        mTriggerDedupKey,
        mSourceType,
        mRandomizedTriggerRate);
  }

  /** Unique identifier for the report. */
  public String getId() {
    return mId;
  }

  /** Identifier of the associated {@link Source} event. */
  public long getSourceId() {
    return mSourceId;
  }

  /** Scheduled time for the report to be sent. */
  public long getReportTime() {
    return mReportTime;
  }

  /** TriggerTime of the associated {@link Trigger}. */
  public long getTriggerTime() {
    return mTriggerTime;
  }

  /** Priority of the associated {@link Trigger}. */
  public long getTriggerPriority() {
    return mTriggerPriority;
  }

  /** AttributionDestination of the {@link Source} and {@link Trigger}. */
  public URI getAttributionDestination() {
    return mAttributionDestination;
  }

  /** Ad Tech enrollment id. */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** Metadata for the report. */
  public long getTriggerData() {
    return mTriggerData;
  }

  /** Deduplication key of the associated {@link Trigger} */
  public Long getTriggerDedupKey() {
    return mTriggerDedupKey;
  }

  /** Current {@link Status} of the report. */
  public Status getStatus() {
    return mStatus;
  }

  /** SourceType of the event's source. */
  public Source.SourceType getSourceType() {
    return mSourceType;
  }

  /** Randomized trigger rate for noising */
  public double getRandomizedTriggerRate() {
    return mRandomizedTriggerRate;
  }

  /** Builder for {@link EventReport} */
  public static final class Builder {
    private final EventReport mBuilding;

    public Builder() {
      mBuilding = new EventReport();
      mBuilding.mId = UUID.randomUUID().toString();
    }

    /** See {@link EventReport#getId()} */
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link EventReport#getSourceId()} */
    public Builder setSourceId(long sourceId) {
      mBuilding.mSourceId = sourceId;
      return this;
    }

    /** See {@link EventReport#getEnrollmentId()} ()} ()} */
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link EventReport#getAttributionDestination()} */
    public Builder setAttributionDestination(URI attributionDestination) {
      mBuilding.mAttributionDestination = attributionDestination;
      return this;
    }

    /** See {@link EventReport#getTriggerTime()} */
    public Builder setTriggerTime(long triggerTime) {
      mBuilding.mTriggerTime = triggerTime;
      return this;
    }

    /** See {@link EventReport#getTriggerData()} */
    public Builder setTriggerData(long triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    /** See {@link EventReport#getTriggerPriority()} */
    public Builder setTriggerPriority(long triggerPriority) {
      mBuilding.mTriggerPriority = triggerPriority;
      return this;
    }

    /** See {@link EventReport#getTriggerDedupKey()} */
    public Builder setTriggerDedupKey(Long triggerDedupKey) {
      mBuilding.mTriggerDedupKey = triggerDedupKey;
      return this;
    }

    /** See {@link EventReport#getReportTime()} */
    public Builder setReportTime(long reportTime) {
      mBuilding.mReportTime = reportTime;
      return this;
    }

    /** See {@link EventReport#getStatus()} */
    public Builder setStatus(Status status) {
      mBuilding.mStatus = status;
      return this;
    }

    /** See {@link EventReport#getSourceType()} */
    public Builder setSourceType(Source.SourceType sourceType) {
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link EventReport#getRandomizedTriggerRate()} ()} */
    public Builder setRandomizedTriggerRate(double randomizedTriggerRate) {
      mBuilding.mRandomizedTriggerRate = randomizedTriggerRate;
      return this;
    }

    /** Populates fields using {@link Source}, {@link Trigger} and {@link EventTrigger}. */
    public Builder populateFromSourceAndTrigger(
        Source source, Trigger trigger, EventTrigger eventTrigger) {
      mBuilding.mTriggerPriority = eventTrigger.getTriggerPriority();
      mBuilding.mTriggerDedupKey = eventTrigger.getDedupKey();
      // truncate trigger data to 3-bit or 1-bit based on {@link Source.SourceType}
      mBuilding.mTriggerData = getTruncatedTriggerData(source, eventTrigger);
      mBuilding.mTriggerTime = trigger.getTriggerTime();
      mBuilding.mSourceId = source.getEventId();
      mBuilding.mEnrollmentId = source.getEnrollmentId();
      mBuilding.mStatus = Status.PENDING;
      mBuilding.mAttributionDestination = trigger.getAttributionDestination();
      mBuilding.mReportTime =
          source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType());
      mBuilding.mSourceType = source.getSourceType();
      mBuilding.mRandomizedTriggerRate = source.getRandomAttributionProbability();
      return this;
    }

    private long getTruncatedTriggerData(Source source, EventTrigger eventTrigger) {
      return eventTrigger.getTriggerData() % source.getTriggerDataCardinality();
    }

    /** Build the {@link EventReport}. */
    public EventReport build() {
      return mBuilding;
    }
  }

  /**
   * Create JSON Payload of this EventReport based on EventReportPayload from Measurement API
   *
   * @return JSON Representation of unencrypted EventReport.
   */
  public JSONObject toJsonObject() {
    JSONObject payload = new JSONObject();
    payload.put("attribution_destination", mAttributionDestination.toString());
    payload.put("source_event_id", mSourceId);
    payload.put("trigger_data", mTriggerData);
    payload.put("report_id", mId);
    payload.put("source_type", mSourceType.toString());
    payload.put("randomized_trigger_rate", mRandomizedTriggerRate);
    return payload;
  }
}
