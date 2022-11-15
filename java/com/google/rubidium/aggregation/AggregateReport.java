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

import com.google.rubidium.Constants;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/** Class that contains all the real data needed after aggregation, it is not encrypted. */
public class AggregateReport {
  private String mId;
  private URI mPublisher;
  private URI mAttributionDestination;
  private long mSourceRegistrationTime;
  private long mScheduledReportTime; // triggerTime + random([10min, 1hour])
  private String mAdTechDomain;
  private String mEnrollmentId;
  private String mDebugCleartextPayload;
  private AggregateAttributionData mAggregateAttributionData;
  private Status mStatus;
  private String mApiVersion;

  public enum Status {
    PENDING,
    DELIVERED
  }

  private AggregateReport() {
    mId = null;
    mPublisher = null;
    mAttributionDestination = null;
    mSourceRegistrationTime = 0L;
    mScheduledReportTime = 0L;
    mAdTechDomain = null;
    mEnrollmentId = null;
    mDebugCleartextPayload = null;
    mAggregateAttributionData = null;
    mStatus = AggregateReport.Status.PENDING;
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
        && Objects.equals(mAdTechDomain, aggregateReport.mAdTechDomain)
        && Objects.equals(mEnrollmentId, aggregateReport.mEnrollmentId)
        && Objects.equals(mDebugCleartextPayload, aggregateReport.mDebugCleartextPayload)
        && Objects.equals(mAggregateAttributionData, aggregateReport.mAggregateAttributionData)
        && mStatus == aggregateReport.mStatus
        && Objects.equals(mApiVersion, aggregateReport.mApiVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mId,
        mPublisher,
        mAttributionDestination,
        mSourceRegistrationTime,
        mScheduledReportTime,
        mAdTechDomain,
        mEnrollmentId,
        mDebugCleartextPayload,
        mAggregateAttributionData,
        mStatus);
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

  /** String for report_to of source. */
  public String getAdTechDomain() {
    return mAdTechDomain;
  }

  /** Ad-tech enrollment ID. */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** Unencrypted aggregate payload string, convert from List of AggregateHistogramContribution. */
  public String getDebugCleartextPayload() {
    return mDebugCleartextPayload;
  }

  /** Contains the data specific to the aggregate report. */
  public AggregateAttributionData getAggregateAttributionData() {
    return mAggregateAttributionData;
  }

  /** Current {@link Status} of the report. */
  public Status getStatus() {
    return mStatus;
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
    debugPayload.put("operation", "histogram");
    debugPayload.put("data", jsonArray);
    return debugPayload.toString();
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

    /** See {@link AggregateReport#getAdTechDomain()}. */
    public Builder setAdTechDomain(String adTechDomain) {
      mAttributionReport.mAdTechDomain = adTechDomain;
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

    /** See {@link AggregateReport#getApiVersion()} */
    public Builder setApiVersion(String version) {
      mAttributionReport.mApiVersion = version;
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
    //        aggregationServicePayload.put(AggregationServicePayloadKeys.PAYLOAD,
    // encryptedPayload);
    //      aggregationServicePayload.put(AggregationServicePayloadKeys.KEY_ID, key.getKeyId());
    aggregationServicePayload.put("debug_cleartext_payload", AggregateCborConverter.encode(this));
    aggregationServicePayloadsJson.add(aggregationServicePayload);
    return aggregationServicePayloadsJson;
  }
}
