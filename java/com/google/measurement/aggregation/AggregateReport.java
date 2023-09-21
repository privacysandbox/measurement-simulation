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

package com.google.measurement.aggregation;

import com.google.measurement.Constants;
import com.google.measurement.util.UnsignedLong;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/** Class that contains all the real data needed after aggregation, it is not encrypted. */
public class AggregateReport {
  static final String OPERATION = "operation";
  static final String HISTOGRAM = "histogram";
  static final String DATA = "data";

  private String mId;
  private URI mPublisher;
  private URI mAttributionDestination;
  private long mSourceRegistrationTime;
  private long mScheduledReportTime; // triggerTime + random([10min, 1hour])
  private String mEnrollmentId;
  private String mDebugCleartextPayload;
  private AggregateAttributionData mAggregateAttributionData;
  private Status mStatus;
  private DebugReportStatus mDebugReportStatus;

  private String mApiVersion;
  private UnsignedLong mSourceDebugKey;
  private UnsignedLong mTriggerDebugKey;
  private String mSourceId;
  private String mTriggerId;
  private UnsignedLong mDedupKey;
  private URI mRegistrationOrigin;

  public enum Status {
    PENDING,
    DELIVERED,
    MARKED_TO_DELETE
  }

  public enum DebugReportStatus {
    NONE,
    PENDING,
    DELIVERED
  }

  private AggregateReport() {
    mId = null;
    mPublisher = null;
    mAttributionDestination = null;
    mSourceRegistrationTime = 0L;
    mScheduledReportTime = 0L;
    mEnrollmentId = null;
    mDebugCleartextPayload = null;
    mAggregateAttributionData = null;
    mStatus = AggregateReport.Status.PENDING;
    mDebugReportStatus = AggregateReport.DebugReportStatus.NONE;
    mSourceDebugKey = null;
    mTriggerDebugKey = null;
    mDedupKey = null;
    mRegistrationOrigin = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateReport)) {
      return false;
    }
    AggregateReport aggregateReport = (AggregateReport) obj;
    return Objects.equals(mPublisher, aggregateReport.mPublisher)
        && Objects.equals(mAttributionDestination, aggregateReport.mAttributionDestination)
        && mSourceRegistrationTime == aggregateReport.mSourceRegistrationTime
        && mScheduledReportTime == aggregateReport.mScheduledReportTime
        && Objects.equals(mEnrollmentId, aggregateReport.mEnrollmentId)
        && Objects.equals(mDebugCleartextPayload, aggregateReport.mDebugCleartextPayload)
        && Objects.equals(mAggregateAttributionData, aggregateReport.mAggregateAttributionData)
        && mStatus == aggregateReport.mStatus
        && mDebugReportStatus == aggregateReport.mDebugReportStatus
        && Objects.equals(mApiVersion, aggregateReport.mApiVersion)
        && Objects.equals(mSourceDebugKey, aggregateReport.mSourceDebugKey)
        && Objects.equals(mTriggerDebugKey, aggregateReport.mTriggerDebugKey)
        && Objects.equals(mSourceId, aggregateReport.mSourceId)
        && Objects.equals(mTriggerId, aggregateReport.mTriggerId)
        && Objects.equals(mDedupKey, aggregateReport.mDedupKey)
        && Objects.equals(mRegistrationOrigin, aggregateReport.mRegistrationOrigin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mId,
        mPublisher,
        mAttributionDestination,
        mSourceRegistrationTime,
        mScheduledReportTime,
        mEnrollmentId,
        mDebugCleartextPayload,
        mAggregateAttributionData,
        mStatus,
        mDebugReportStatus,
        mSourceDebugKey,
        mTriggerDebugKey,
        mSourceId,
        mTriggerId,
        mDedupKey,
        mRegistrationOrigin);
  }

  /** Unique identifier for the {@link AggregateReport}. */
  public String getId() {
    return mId;
  }

  /** URI for publisher of this source, primarily an App. */
  public URI getPublisher() {
    return mPublisher;
  }

  /** URI for attribution destination of source. */
  public URI getAttributionDestination() {
    return mAttributionDestination;
  }

  /** Source registration time. */
  public long getSourceRegistrationTime() {
    return mSourceRegistrationTime;
  }

  /** Scheduled report time for aggregate report. */
  public long getScheduledReportTime() {
    return mScheduledReportTime;
  }

  /** Ad-tech enrollment ID. */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** Unencrypted aggregate payload string, convert from List of AggregateHistogramContribution. */
  public String getDebugCleartextPayload() {
    return mDebugCleartextPayload;
  }

  /** Source Debug Key */
  public UnsignedLong getSourceDebugKey() {
    return mSourceDebugKey;
  }

  /** Trigger Debug Key */
  public UnsignedLong getTriggerDebugKey() {
    return mTriggerDebugKey;
  }

  /** Contains the data specific to the aggregate report. */
  public AggregateAttributionData getAggregateAttributionData() {
    return mAggregateAttributionData;
  }

  /** Current {@link Status} of the report. */
  public Status getStatus() {
    return mStatus;
  }

  /** Current {@link DebugReportStatus} of the report. */
  public DebugReportStatus getDebugReportStatus() {
    return mDebugReportStatus;
  }

  /** Debug Key */
  public UnsignedLong getDebugKey() {
    return mDedupKey;
  }

  /** Registration URI */
  public URI getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Api version when the report was issued. */
  public String getApiVersion() {
    return mApiVersion;
  }

  /**
   * Generates String for debugCleartextPayload. JSON for format : { "operation": "histogram",
   * "data": [{ "bucket": 1369, "value": 32768 }, { "bucket": 3461, "value": 1664 }] }
   */
  public static String generateDebugPayload(List<AggregateHistogramContribution> contributions)
      throws ParseException {
    JSONArray jsonArray = new JSONArray();
    for (AggregateHistogramContribution contribution : contributions) {
      jsonArray.add(contribution.toJSONObject());
    }
    JSONObject debugPayload = new JSONObject();
    debugPayload.put(OPERATION, HISTOGRAM);
    debugPayload.put(DATA, jsonArray);
    return debugPayload.toString();
  }

  /**
   * It deserializes the debug cleartext payload into {@link AggregateHistogramContribution}s.
   *
   * @return list of {@link AggregateHistogramContribution}s
   */
  public List<AggregateHistogramContribution> extractAggregateHistogramContributions() {
    try {
      ArrayList<AggregateHistogramContribution> aggregateHistogramContributions = new ArrayList<>();
      JSONParser parser = new JSONParser();
      JSONObject debugCleartextPayload = (JSONObject) parser.parse(mDebugCleartextPayload);
      JSONArray contributionsArray = (JSONArray) debugCleartextPayload.get(DATA);
      for (int i = 0; i < contributionsArray.size(); i++) {
        AggregateHistogramContribution aggregateHistogramContribution =
            new AggregateHistogramContribution.Builder()
                .fromJsonObject((JSONObject) contributionsArray.get(i));
        aggregateHistogramContributions.add(aggregateHistogramContribution);
      }
      return aggregateHistogramContributions;
    } catch (ParseException e) {
      return Collections.emptyList();
    }
  }

  /** {@link Source} ID */
  public String getSourceId() {
    return mSourceId;
  }

  /** {@link Trigger} ID */
  public String getTriggerId() {
    return mTriggerId;
  }

  /** Builder for {@link AggregateReport}. */
  public static final class Builder {
    private final AggregateReport mAttributionReport;

    public Builder() {
      mAttributionReport = new AggregateReport();
    }

    /** See {@link AggregateReport#getId()}. */
    public Builder setId(String id) {
      mAttributionReport.mId = id;
      return this;
    }

    /** See {@link AggregateReport#getPublisher()}. */
    public Builder setPublisher(URI publisher) {
      mAttributionReport.mPublisher = publisher;
      return this;
    }

    /** See {@link AggregateReport#getAttributionDestination()}. */
    public Builder setAttributionDestination(URI attributionDestination) {
      mAttributionReport.mAttributionDestination = attributionDestination;
      return this;
    }

    /** See {@link AggregateReport#getSourceRegistrationTime()}. */
    public Builder setSourceRegistrationTime(long sourceRegistrationTime) {
      mAttributionReport.mSourceRegistrationTime = sourceRegistrationTime;
      return this;
    }

    /** See {@link AggregateReport#getScheduledReportTime()}. */
    public Builder setScheduledReportTime(long scheduledReportTime) {
      mAttributionReport.mScheduledReportTime = scheduledReportTime;
      return this;
    }

    /** See {@link AggregateReport#getEnrollmentId()}. */
    public Builder setEnrollmentId(String enrollmentId) {
      mAttributionReport.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link AggregateReport#getDebugCleartextPayload()}. */
    public Builder setDebugCleartextPayload(String debugCleartextPayload) {
      mAttributionReport.mDebugCleartextPayload = debugCleartextPayload;
      return this;
    }

    /** See {@link AggregateReport#getAggregateAttributionData()}. */
    public Builder setAggregateAttributionData(AggregateAttributionData aggregateAttributionData) {
      mAttributionReport.mAggregateAttributionData = aggregateAttributionData;
      return this;
    }

    /** See {@link AggregateReport#getStatus()} */
    public Builder setStatus(Status status) {
      mAttributionReport.mStatus = status;
      return this;
    }

    /** See {@link AggregateReport#getDebugReportStatus()} */
    public Builder setDebugReportStatus(DebugReportStatus debugReportStatus) {
      mAttributionReport.mDebugReportStatus = debugReportStatus;
      return this;
    }

    /** See {@link AggregateReport#getApiVersion()} */
    public Builder setApiVersion(String version) {
      mAttributionReport.mApiVersion = version;
      return this;
    }

    /** See {@link AggregateReport#getSourceDebugKey()} */
    public Builder setSourceDebugKey(UnsignedLong sourceDebugKey) {
      mAttributionReport.mSourceDebugKey = sourceDebugKey;
      return this;
    }

    /** See {@link AggregateReport#getTriggerDebugKey()} */
    public Builder setTriggerDebugKey(UnsignedLong triggerDebugKey) {
      mAttributionReport.mTriggerDebugKey = triggerDebugKey;
      return this;
    }

    /** See {@link AggregateReport#getSourceId()} */
    public AggregateReport.Builder setSourceId(String sourceId) {
      mAttributionReport.mSourceId = sourceId;
      return this;
    }

    /** See {@link AggregateReport#getTriggerId()} */
    public AggregateReport.Builder setTriggerId(String triggerId) {
      mAttributionReport.mTriggerId = triggerId;
      return this;
    }

    /** See {@link AggregateReport#getDebugKey()} */
    public Builder setDedupKey(UnsignedLong dedupKey) {
      mAttributionReport.mDedupKey = dedupKey;
      return this;
    }

    /** See {@link AggregateReport#getRegistrationOrigin()} */
    public Builder setRegistrationOrigin(URI registrationOrigin) {
      mAttributionReport.mRegistrationOrigin = registrationOrigin;
      return this;
    }

    /** Build the {@link AggregateReport}. */
    public AggregateReport build() {
      return mAttributionReport;
    }
  }

  /** Generate the JSON serialization of the aggregate report. */
  public JSONObject toJson() throws Exception {
    JSONObject aggregateBodyJson = new JSONObject();
    final JSONObject sharedInfo = sharedInfoToJson();
    aggregateBodyJson.put("shared_info", sharedInfo.toString());
    aggregateBodyJson.put("aggregation_service_payloads", aggregationServicePayloadsToJson());
    if (mSourceDebugKey != null) {
      aggregateBodyJson.put("source_debug_key", mSourceDebugKey.toString());
    }
    if (mTriggerDebugKey != null) {
      aggregateBodyJson.put("trigger_debug_key", mTriggerDebugKey.toString());
    }
    return aggregateBodyJson;
  }

  /** Generate the JSON serialization of the shared_info field of the aggregate report. */
  JSONObject sharedInfoToJson() {
    JSONObject sharedInfoJson = new JSONObject();
    sharedInfoJson.put("api", Constants.getSharedInfoAPIKey());
    sharedInfoJson.put("attribution_destination", mAttributionDestination.toString());
    sharedInfoJson.put("report_id", mId);
    sharedInfoJson.put("reporting_origin", mEnrollmentId);
    sharedInfoJson.put("scheduled_report_time", mScheduledReportTime);
    sharedInfoJson.put("source_registration_time", mSourceRegistrationTime);
    sharedInfoJson.put("version", mApiVersion);
    return sharedInfoJson;
  }

  /** Generate the JSON array serialization of the aggregation service payloads field. */
  JSONArray aggregationServicePayloadsToJson() throws Exception {
    JSONArray aggregationServicePayloadsJson = new JSONArray();
    final JSONObject aggregationServicePayload = new JSONObject();
    aggregationServicePayload.put("debug_cleartext_payload", AggregateCborConverter.encode(this));
    aggregationServicePayloadsJson.add(aggregationServicePayload);
    return aggregationServicePayloadsJson;
  }
}
