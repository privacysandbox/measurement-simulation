/*
 * Copyright (C) 2022 Google LLC
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

package com.google.measurement.client.reporting;

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Flags;
import com.google.measurement.client.aggregation.AggregateCryptoConverter;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.VisibleForTesting;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Class for constructing the report body of an aggregate report. */
public class AggregateReportBody {
  private String mAttributionDestination;
  private String mSourceRegistrationTime;
  private String mScheduledReportTime;
  private String mApiVersion;
  private String mApi;
  private String mReportId;
  private String mReportingOrigin;
  private String mDebugCleartextPayload;
  @Nullable private UnsignedLong mSourceDebugKey;
  @Nullable private UnsignedLong mTriggerDebugKey;
  private String mDebugMode;

  private Uri mAggregationCoordinatorOrigin;
  @Nullable private String mTriggerContextId;

  @VisibleForTesting
  interface PayloadBodyKeys {
    String SHARED_INFO = "shared_info";
    String AGGREGATION_SERVICE_PAYLOADS = "aggregation_service_payloads";
    String SOURCE_DEBUG_KEY = "source_debug_key";
    String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
    String TRIGGER_CONTEXT_ID = "trigger_context_id";
  }

  private interface AggregationServicePayloadKeys {
    String PAYLOAD = "payload";
    String KEY_ID = "key_id";
    String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
  }

  @VisibleForTesting
  interface SharedInfoKeys {
    String API_NAME = "api";
    String ATTRIBUTION_DESTINATION = "attribution_destination";
    String REPORT_ID = "report_id";
    String REPORTING_ORIGIN = "reporting_origin";
    String SCHEDULED_REPORT_TIME = "scheduled_report_time";
    String SOURCE_REGISTRATION_TIME = "source_registration_time";
    String API_VERSION = "version";
    String DEBUG_MODE = "debug_mode";
  }

  private AggregateReportBody() {}

  private AggregateReportBody(AggregateReportBody other) {
    mAttributionDestination = other.mAttributionDestination;
    mSourceRegistrationTime = other.mSourceRegistrationTime;
    mScheduledReportTime = other.mScheduledReportTime;
    mApiVersion = other.mApiVersion;
    mApi = other.mApi;
    mReportId = other.mReportId;
    mReportingOrigin = other.mReportingOrigin;
    mDebugCleartextPayload = other.mDebugCleartextPayload;
    mSourceDebugKey = other.mSourceDebugKey;
    mTriggerDebugKey = other.mTriggerDebugKey;
    mDebugMode = other.mDebugMode;
    mAggregationCoordinatorOrigin = other.mAggregationCoordinatorOrigin;
    mTriggerContextId = other.mTriggerContextId;
  }

  /** Generate the JSON serialization of the aggregate report. */
  public JSONObject toJson(AggregateEncryptionKey key, Flags flags) throws JSONException {
    JSONObject aggregateBodyJson = new JSONObject();

    final String sharedInfo = sharedInfoToJson().toString();
    aggregateBodyJson.put(PayloadBodyKeys.SHARED_INFO, sharedInfo);
    aggregateBodyJson.put(
        PayloadBodyKeys.AGGREGATION_SERVICE_PAYLOADS,
        aggregationServicePayloadsToJson(sharedInfo, key));

    if (mSourceDebugKey != null) {
      aggregateBodyJson.put(PayloadBodyKeys.SOURCE_DEBUG_KEY, mSourceDebugKey.toString());
    }
    if (mTriggerDebugKey != null) {
      aggregateBodyJson.put(PayloadBodyKeys.TRIGGER_DEBUG_KEY, mTriggerDebugKey.toString());
    }
    if (flags.getMeasurementAggregationCoordinatorOriginEnabled()) {
      aggregateBodyJson.put(
          PayloadBodyKeys.AGGREGATION_COORDINATOR_ORIGIN, mAggregationCoordinatorOrigin.toString());
    }
    if (flags.getMeasurementEnableTriggerContextId() && mTriggerContextId != null) {
      aggregateBodyJson.put(PayloadBodyKeys.TRIGGER_CONTEXT_ID, mTriggerContextId);
    }

    return aggregateBodyJson;
  }

  /** Generate the JSON serialization of the shared_info field of the aggregate report. */
  @VisibleForTesting
  JSONObject sharedInfoToJson() throws JSONException {
    JSONObject sharedInfoJson = new JSONObject();

    sharedInfoJson.put(SharedInfoKeys.API_NAME, mApi);
    sharedInfoJson.put(SharedInfoKeys.ATTRIBUTION_DESTINATION, mAttributionDestination);
    sharedInfoJson.put(SharedInfoKeys.REPORT_ID, mReportId);
    sharedInfoJson.put(SharedInfoKeys.REPORTING_ORIGIN, mReportingOrigin);
    sharedInfoJson.put(SharedInfoKeys.SCHEDULED_REPORT_TIME, mScheduledReportTime);

    String sourceRegistrationTime = mSourceRegistrationTime;
    sharedInfoJson.put(SharedInfoKeys.SOURCE_REGISTRATION_TIME, sourceRegistrationTime);
    sharedInfoJson.put(SharedInfoKeys.API_VERSION, mApiVersion);

    if (mDebugMode != null) {
      sharedInfoJson.put(SharedInfoKeys.DEBUG_MODE, mDebugMode);
    }

    return sharedInfoJson;
  }

  /** Generate the JSON array serialization of the aggregation service payloads field. */
  @VisibleForTesting
  JSONArray aggregationServicePayloadsToJson(String sharedInfo, AggregateEncryptionKey key)
      throws JSONException {
    JSONArray aggregationServicePayloadsJson = new JSONArray();

    final String encryptedPayload =
        AggregateCryptoConverter.encrypt(key.getPublicKey(), mDebugCleartextPayload, sharedInfo);

    final JSONObject aggregationServicePayload = new JSONObject();
    aggregationServicePayload.put(AggregationServicePayloadKeys.PAYLOAD, encryptedPayload);
    aggregationServicePayload.put(AggregationServicePayloadKeys.KEY_ID, key.getKeyId());

    if (mSourceDebugKey != null && mTriggerDebugKey != null) {
      aggregationServicePayload.put(
          AggregationServicePayloadKeys.DEBUG_CLEARTEXT_PAYLOAD,
          AggregateCryptoConverter.encode(mDebugCleartextPayload));
    }
    aggregationServicePayloadsJson.put(aggregationServicePayload);

    return aggregationServicePayloadsJson;
  }

  /** Builder class for AggregateReportBody. */
  public static final class Builder {
    private AggregateReportBody mBuilding;

    public Builder() {
      mBuilding = new AggregateReportBody();
    }

    /** The attribution destination set on the source. */
    public @NonNull Builder setAttributionDestination(@NonNull String attributionDestination) {
      mBuilding.mAttributionDestination = attributionDestination;
      return this;
    }

    /** The registration time of the source. */
    public @NonNull Builder setSourceRegistrationTime(@NonNull String sourceRegistrationTime) {
      mBuilding.mSourceRegistrationTime = sourceRegistrationTime;
      return this;
    }

    /** The initial scheduled report time for the report. */
    public @NonNull Builder setScheduledReportTime(@NonNull String scheduledReportTime) {
      mBuilding.mScheduledReportTime = scheduledReportTime;
      return this;
    }

    /** The version of the API used to generate the aggregate report. */
    public @NonNull Builder setApiVersion(@NonNull String version) {
      mBuilding.mApiVersion = version;
      return this;
    }

    /**
     * The API name, e.g. "attribution-reporting", "attribution-reporting-debug", used to generate
     * the aggregate report.
     */
    public @NonNull Builder setApi(@NonNull String api) {
      mBuilding.mApi = api;
      return this;
    }

    /** The ad tech domain for the report. */
    public @NonNull Builder setReportingOrigin(@NonNull String reportingOrigin) {
      mBuilding.mReportingOrigin = reportingOrigin;
      return this;
    }

    /** The unique id for this report. */
    public @NonNull Builder setReportId(@NonNull String reportId) {
      mBuilding.mReportId = reportId;
      return this;
    }

    /** The cleartext payload for debug. */
    public @NonNull Builder setDebugCleartextPayload(@NonNull String debugCleartextPayload) {
      mBuilding.mDebugCleartextPayload = debugCleartextPayload;
      return this;
    }

    /** Source debug key */
    public @NonNull Builder setSourceDebugKey(@Nullable UnsignedLong sourceDebugKey) {
      mBuilding.mSourceDebugKey = sourceDebugKey;
      return this;
    }

    /** Trigger debug key */
    public @NonNull Builder setTriggerDebugKey(@Nullable UnsignedLong triggerDebugKey) {
      mBuilding.mTriggerDebugKey = triggerDebugKey;
      return this;
    }

    /** Debug mode */
    public Builder setDebugMode(String debugMode) {
      mBuilding.mDebugMode = debugMode;
      return this;
    }

    /** Origin of aggregation coordinator used for this report. */
    public Builder setAggregationCoordinatorOrigin(Uri aggregationCoordinatorOrigin) {
      mBuilding.mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
      return this;
    }

    /** Trigger context id */
    public Builder setTriggerContextId(@Nullable String triggerContextId) {
      mBuilding.mTriggerContextId = triggerContextId;
      return this;
    }

    /** Build the AggregateReportBody. */
    public @NonNull AggregateReportBody build() {
      return new AggregateReportBody(mBuilding);
    }
  }
}
