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

import com.google.measurement.noising.SourceNoiseHandler;
import com.google.measurement.util.Debug;
import com.google.measurement.util.UnsignedLong;
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;

public class EventReport implements Serializable {
  private String mId;
  private UnsignedLong mSourceEventId;
  private long mReportTime;
  private long mTriggerTime;
  private long mTriggerPriority;
  private List<URI> mAttributionDestinations;
  private String mEnrollmentId;
  private UnsignedLong mTriggerData;
  private UnsignedLong mTriggerDedupKey;
  private double mRandomizedTriggerRate;
  private Status mStatus;
  private DebugReportStatus mDebugReportStatus;

  private Source.SourceType mSourceType;
  private UnsignedLong mSourceDebugKey;
  private UnsignedLong mTriggerDebugKey;
  private String mSourceId;
  private String mTriggerId;
  private URI mRegistrationOrigin;
  private long mTriggerValue;
  private Pair<Long, Long> mTriggerSummaryBucket;

  public enum Status {
    PENDING,
    DELIVERED
  }

  public enum DebugReportStatus {
    NONE,
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
        && mDebugReportStatus == eventReport.mDebugReportStatus
        && mReportTime == eventReport.mReportTime
        && Objects.equals(mAttributionDestinations, eventReport.mAttributionDestinations)
        && Objects.equals(mEnrollmentId, eventReport.mEnrollmentId)
        && mTriggerTime == eventReport.mTriggerTime
        && Objects.equals(mTriggerData, eventReport.mTriggerData)
        && Objects.equals(mSourceEventId, eventReport.mSourceEventId)
        && mTriggerPriority == eventReport.mTriggerPriority
        && Objects.equals(mTriggerDedupKey, eventReport.mTriggerDedupKey)
        && mSourceType == eventReport.mSourceType
        && mRandomizedTriggerRate == eventReport.mRandomizedTriggerRate
        && Objects.equals(mSourceDebugKey, eventReport.mSourceDebugKey)
        && Objects.equals(mTriggerDebugKey, eventReport.mTriggerDebugKey)
        && Objects.equals(mSourceId, eventReport.mSourceId)
        && Objects.equals(mTriggerId, eventReport.mTriggerId)
        && Objects.equals(mRegistrationOrigin, eventReport.mRegistrationOrigin)
        && Objects.equals(mTriggerValue, eventReport.mTriggerValue)
        && Objects.equals(mTriggerSummaryBucket, eventReport.mTriggerSummaryBucket);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mStatus,
        mDebugReportStatus,
        mReportTime,
        mAttributionDestinations,
        mEnrollmentId,
        mTriggerTime,
        mTriggerData,
        mSourceEventId,
        mTriggerPriority,
        mTriggerDedupKey,
        mSourceType,
        mRandomizedTriggerRate,
        mSourceDebugKey,
        mTriggerDebugKey,
        mSourceId,
        mTriggerId,
        mRegistrationOrigin,
        mTriggerValue,
        mTriggerSummaryBucket);
  }

  /** Unique identifier for the report. */
  public String getId() {
    return mId;
  }

  /** Identifier of the associated {@link Source} event. */
  public UnsignedLong getSourceEventId() {
    return mSourceEventId;
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

  /** AttributionDestinations of the {@link Source} and {@link Trigger}. */
  public List<URI> getAttributionDestinations() {
    return mAttributionDestinations;
  }

  /** Ad Tech enrollment ID. */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** Metadata for the report. */
  public UnsignedLong getTriggerData() {
    return mTriggerData;
  }

  /** Deduplication key of the associated {@link Trigger} */
  public UnsignedLong getTriggerDedupKey() {
    return mTriggerDedupKey;
  }

  /** Current {@link Status} of the report. */
  public Status getStatus() {
    return mStatus;
  }

  /** Current {@link DebugReportStatus} of the report. */
  public DebugReportStatus getDebugReportStatus() {
    return mDebugReportStatus;
  }

  /** SourceType of the event's source. */
  public Source.SourceType getSourceType() {
    return mSourceType;
  }

  /** Randomized trigger rate for noising */
  public double getRandomizedTriggerRate() {
    return mRandomizedTriggerRate;
  }

  /** Source Debug Key */
  public UnsignedLong getSourceDebugKey() {
    return mSourceDebugKey;
  }

  /** Trigger Debug Key */
  public UnsignedLong getTriggerDebugKey() {
    return mTriggerDebugKey;
  }

  /** Source ID */
  public String getSourceId() {
    return mSourceId;
  }

  /** Trigger ID */
  public String getTriggerId() {
    return mTriggerId;
  }

  /** Returns registration origin used to register the source and trigger */
  public URI getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Trigger summary bucket */
  public Pair<Long, Long> getTriggerSummaryBucket() {
    return mTriggerSummaryBucket;
  }

  /** See {@link EventReport#getTriggerSummaryBucket()} */
  public void setTriggerSummaryBucket(Pair<Long, Long> summaryBucket) {
    mTriggerSummaryBucket = summaryBucket;
  }

  /**
   * Update the summary bucket in the event report
   *
   * @param summaryBucket the summary bucket
   */
  public void updateSummaryBucket(Pair<Long, Long> summaryBucket) {
    mTriggerSummaryBucket = summaryBucket;
  }

  /** Builder for {@link EventReport} */
  public static final class Builder {
    private final EventReport mBuilding;

    public Builder() {
      mBuilding = new EventReport();
    }

    /** See {@link EventReport#getId()} */
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link EventReport#getSourceEventId()} */
    public Builder setSourceEventId(UnsignedLong sourceEventId) {
      mBuilding.mSourceEventId = sourceEventId;
      return this;
    }

    /** See {@link EventReport#getEnrollmentId()} */
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link EventReport#getAttributionDestinations()} */
    public Builder setAttributionDestinations(List<URI> attributionDestinations) {
      mBuilding.mAttributionDestinations = attributionDestinations;
      return this;
    }

    /** See {@link EventReport#getTriggerTime()} */
    public Builder setTriggerTime(long triggerTime) {
      mBuilding.mTriggerTime = triggerTime;
      return this;
    }

    /** See {@link EventReport#getTriggerData()} */
    public Builder setTriggerData(UnsignedLong triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    /** See {@link EventReport#getTriggerPriority()} */
    public Builder setTriggerPriority(long triggerPriority) {
      mBuilding.mTriggerPriority = triggerPriority;
      return this;
    }

    /** See {@link EventReport#getTriggerPriority()} */
    public Builder setTriggerValue(long triggerValue) {
      mBuilding.mTriggerValue = triggerValue;
      return this;
    }

    /** See {@link EventReport#getTriggerDedupKey()} */
    public Builder setTriggerDedupKey(UnsignedLong triggerDedupKey) {
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

    /** See {@link EventReport#getDebugReportStatus()} */
    public Builder setDebugReportStatus(DebugReportStatus debugReportStatus) {
      mBuilding.mDebugReportStatus = debugReportStatus;
      return this;
    }

    /** See {@link EventReport#getSourceType()} */
    public Builder setSourceType(Source.SourceType sourceType) {
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link EventReport#getRandomizedTriggerRate()} */
    public Builder setRandomizedTriggerRate(double randomizedTriggerRate) {
      mBuilding.mRandomizedTriggerRate = randomizedTriggerRate;
      return this;
    }

    /** See {@link EventReport#getSourceDebugKey()} */
    public Builder setSourceDebugKey(UnsignedLong sourceDebugKey) {
      mBuilding.mSourceDebugKey = sourceDebugKey;
      return this;
    }

    /** See {@link EventReport#getTriggerDebugKey()} */
    public Builder setTriggerDebugKey(UnsignedLong triggerDebugKey) {
      mBuilding.mTriggerDebugKey = triggerDebugKey;
      return this;
    }

    /** See {@link EventReport#getSourceId()} */
    public Builder setSourceId(String sourceId) {
      mBuilding.mSourceId = sourceId;
      return this;
    }

    /** See {@link EventReport#getTriggerId()} */
    public Builder setTriggerId(String triggerId) {
      mBuilding.mTriggerId = triggerId;
      return this;
    }

    /** See {@link Source#getRegistrationOrigin()} */
    public Builder setRegistrationOrigin(URI registrationOrigin) {
      mBuilding.mRegistrationOrigin = registrationOrigin;
      return this;
    }

    /**
     * set the summary bucket from input DB text encode summary bucket
     *
     * @param summaryBucket the string encoded summary bucket
     * @return builder
     */
    public Builder setTriggerSummaryBucket(String summaryBucket) {
      if (summaryBucket == null || summaryBucket.isEmpty()) {
        mBuilding.mTriggerSummaryBucket = null;
        return this;
      }
      String[] numbers = summaryBucket.split(",");

      Long firstNumber = Long.parseLong(numbers[0].trim());
      Long secondNumber = Long.parseLong(numbers[1].trim());
      mBuilding.mTriggerSummaryBucket = new Pair<>(firstNumber, secondNumber);

      return this;
    }

    /** See {@link EventReport#getTriggerSummaryBucket()} */
    public Builder setTriggerSummaryBucket(Pair<Long, Long> summaryBucket) {
      mBuilding.mTriggerSummaryBucket = summaryBucket;
      return this;
    }

    /** Populates fields using {@link Source}, {@link Trigger} and {@link EventTrigger}. */
    public Builder populateFromSourceAndTrigger(
        Source source,
        Trigger trigger,
        EventTrigger eventTrigger,
        Map<UnsignedLong, UnsignedLong> debugKeyPair,
        EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
        SourceNoiseHandler sourceNoiseHandler,
        List<URI> eventReportDestinations) {
      mBuilding.mId = UUID.randomUUID().toString();
      mBuilding.mTriggerDedupKey = eventTrigger.getDedupKey();
      // truncate trigger data to 3-bit or 1-bit based on {@link Source.SourceType}
      mBuilding.mTriggerTime = trigger.getTriggerTime();
      mBuilding.mSourceEventId = source.getEventId();
      mBuilding.mEnrollmentId = source.getEnrollmentId();
      mBuilding.mStatus = Status.PENDING;
      mBuilding.mAttributionDestinations = eventReportDestinations;
      mBuilding.mSourceType = source.getSourceType();
      mBuilding.mDebugReportStatus = DebugReportStatus.NONE;
      if (Debug.isAttributionDebugReportPermitted(
          source, trigger, mBuilding.mSourceDebugKey, mBuilding.mTriggerDebugKey)) {
        mBuilding.mDebugReportStatus = DebugReportStatus.PENDING;
      }
      mBuilding.mSourceId = source.getId();
      mBuilding.mTriggerId = trigger.getId();
      mBuilding.mRegistrationOrigin = trigger.getRegistrationOrigin();
      if (source.getTriggerSpecs() != null && !source.getTriggerSpecs().isEmpty()) {
        // The source is using flexible event API
        source.buildFlexibleEventReportApi();
        mBuilding.mTriggerPriority =
            source
                .getFlexEventReportSpec()
                .getHighestPriorityOfAttributedAndIncomingTriggers(
                    eventTrigger.getTriggerData(), eventTrigger.getTriggerPriority());
        mBuilding.mTriggerData = eventTrigger.getTriggerData();
        mBuilding.mReportTime =
            ReportSpecUtil.getFlexEventReportingTime(
                source.getFlexEventReportSpec(),
                source.getEventTime(),
                trigger.getTriggerTime(),
                eventTrigger.getTriggerData());
        mBuilding.mRandomizedTriggerRate = source.getFlexEventReportSpec().getFlipProbability();
        mBuilding.mTriggerValue = eventTrigger.getTriggerValue();
      } else {
        mBuilding.mTriggerPriority = eventTrigger.getTriggerPriority();
        // truncate trigger data to 3-bit or 1-bit based on {@link Source.SourceType}
        mBuilding.mTriggerData = getTruncatedTriggerData(source, eventTrigger);
        mBuilding.mReportTime =
            eventReportWindowCalcDelegate.getReportingTime(
                source, trigger.getTriggerTime(), trigger.getDestinationType());
        mBuilding.mRandomizedTriggerRate =
            sourceNoiseHandler.getRandomAttributionProbability(source);
      }
      return this;
    }

    private UnsignedLong getTruncatedTriggerData(Source source, EventTrigger eventTrigger) {
      UnsignedLong triggerData = eventTrigger.getTriggerData();
      return triggerData.mod(source.getTriggerDataCardinality());
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
    payload.put("attribution_destination", serializeAttributionDestinations());
    payload.put(
        "scheduled_report_time", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(mReportTime)));
    payload.put("source_event_id", mSourceEventId);
    payload.put("trigger_data", mTriggerData);
    payload.put("report_id", mId);
    payload.put("source_type", mSourceType.toString());
    payload.put("randomized_trigger_rate", mRandomizedTriggerRate);
    if (mTriggerSummaryBucket != null) {
      payload.put("trigger_summary_bucket", mTriggerSummaryBucket.encodeAsList());
    }
    return payload;
  }

  private Object serializeAttributionDestinations() {
    if (mAttributionDestinations.size() == 1) {
      return mAttributionDestinations.get(0).toString();
    } else {
      List<String> destinations =
          mAttributionDestinations.stream()
              .map(URI::toString)
              .sorted()
              .collect(Collectors.toList());
      return destinations;
    }
  }

  /**
   * In flexible event API, the trigger priority might change so see a setter here
   *
   * @param priority the new priority
   */
  public void setTriggerPriority(long priority) {
    mTriggerPriority = priority;
  }

  /** Trigger Value */
  public long getTriggerValue() {
    return mTriggerValue;
  }
}
