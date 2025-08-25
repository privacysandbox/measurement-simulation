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

package com.google.measurement.client;

import com.google.measurement.client.IntDef;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Uri;

import com.google.measurement.client.Flags;
import com.google.measurement.client.WebAddresses;
import com.google.measurement.client.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.client.aggregation.AggregatableValuesConfig;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.aggregation.AggregateDeduplicationKey;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.aggregation.AggregateTriggerData;
import com.google.measurement.client.util.Filter;
import com.google.measurement.client.util.Filter.FilterContract;
import com.google.measurement.client.util.JsonUtil;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.util.Validation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** POJO for Trigger. */
public class Trigger {

  private String mId;
  private Uri mAttributionDestination;
  @EventSurfaceType private int mDestinationType;
  private String mEnrollmentId;
  private long mTriggerTime;
  private @NonNull String mEventTriggers;
  @Status private int mStatus;
  private Uri mRegistrant;
  private String mAggregateTriggerData;
  private String mAggregateValuesString;
  private String mAggregateDeduplicationKeys;
  private boolean mIsDebugReporting;
  private Optional<AggregatableAttributionTrigger> mAggregatableAttributionTrigger;
  private String mFilters;
  private String mNotFilters;
  @Nullable private UnsignedLong mDebugKey;
  private boolean mAdIdPermission;
  private boolean mArDebugPermission;
  @Nullable private String mAttributionConfig;
  @Nullable private String mAdtechKeyMapping;
  @Nullable private String mDebugJoinKey;
  @Nullable private String mPlatformAdId;
  @Nullable private String mDebugAdId;
  private Uri mRegistrationOrigin;
  @Nullable private Uri mAggregationCoordinatorOrigin;
  private SourceRegistrationTimeConfig mAggregatableSourceRegistrationTimeConfig;
  @Nullable private String mTriggerContextId;
  @Nullable private String mAttributionScopesString;
  @Nullable private Integer mAggregatableFilteringIdMaxBytes;
  @Nullable private String mAggregateDebugReportingString;
  @Nullable private AggregateDebugReporting mAggregateDebugReporting;

  @IntDef(value = {Status.PENDING, Status.IGNORED, Status.ATTRIBUTED, Status.MARKED_TO_DELETE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Status {
    int PENDING = 0;
    int IGNORED = 1;
    int ATTRIBUTED = 2;
    int MARKED_TO_DELETE = 3;
  }

  public enum SourceRegistrationTimeConfig {
    INCLUDE,
    EXCLUDE
  }

  private Trigger() {
    mStatus = Status.PENDING;
    // Making this default explicit since it anyway occur on an uninitialised int field.
    mDestinationType = EventSurfaceType.APP;
    mIsDebugReporting = false;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Trigger)) {
      return false;
    }
    Trigger trigger = (Trigger) obj;
    return Objects.equals(mId, trigger.getId())
        && Objects.equals(mAttributionDestination, trigger.mAttributionDestination)
        && mDestinationType == trigger.mDestinationType
        && Objects.equals(mEnrollmentId, trigger.mEnrollmentId)
        && mTriggerTime == trigger.mTriggerTime
        && Objects.equals(mDebugKey, trigger.mDebugKey)
        && Objects.equals(mEventTriggers, trigger.mEventTriggers)
        && mStatus == trigger.mStatus
        && mIsDebugReporting == trigger.mIsDebugReporting
        && mAdIdPermission == trigger.mAdIdPermission
        && mArDebugPermission == trigger.mArDebugPermission
        && mAggregatableSourceRegistrationTimeConfig
            == trigger.mAggregatableSourceRegistrationTimeConfig
        && Objects.equals(mRegistrant, trigger.mRegistrant)
        && Objects.equals(mAggregateTriggerData, trigger.mAggregateTriggerData)
        && Objects.equals(mAggregateValuesString, trigger.mAggregateValuesString)
        && Objects.equals(mAggregatableAttributionTrigger, trigger.mAggregatableAttributionTrigger)
        && Objects.equals(mFilters, trigger.mFilters)
        && Objects.equals(mNotFilters, trigger.mNotFilters)
        && Objects.equals(mAttributionConfig, trigger.mAttributionConfig)
        && Objects.equals(mAdtechKeyMapping, trigger.mAdtechKeyMapping)
        && Objects.equals(mAggregateDeduplicationKeys, trigger.mAggregateDeduplicationKeys)
        && Objects.equals(mDebugJoinKey, trigger.mDebugJoinKey)
        && Objects.equals(mPlatformAdId, trigger.mPlatformAdId)
        && Objects.equals(mDebugAdId, trigger.mDebugAdId)
        && Objects.equals(mRegistrationOrigin, trigger.mRegistrationOrigin)
        && Objects.equals(mTriggerContextId, trigger.mTriggerContextId)
        && Objects.equals(mAttributionScopesString, trigger.mAttributionScopesString)
        && Objects.equals(
            mAggregatableFilteringIdMaxBytes, trigger.mAggregatableFilteringIdMaxBytes)
        && Objects.equals(mAggregateDebugReportingString, trigger.mAggregateDebugReportingString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mId,
        mAttributionDestination,
        mDestinationType,
        mEnrollmentId,
        mTriggerTime,
        mEventTriggers,
        mStatus,
        mAggregateTriggerData,
        mAggregateValuesString,
        mAggregatableAttributionTrigger,
        mFilters,
        mNotFilters,
        mDebugKey,
        mAdIdPermission,
        mArDebugPermission,
        mAttributionConfig,
        mAdtechKeyMapping,
        mAggregateDeduplicationKeys,
        mDebugJoinKey,
        mPlatformAdId,
        mDebugAdId,
        mRegistrationOrigin,
        mAggregatableSourceRegistrationTimeConfig,
        mTriggerContextId,
        mAttributionScopesString,
        mAggregatableFilteringIdMaxBytes,
        mAggregateDebugReportingString);
  }

  /** Unique identifier for the {@link Trigger}. */
  public String getId() {
    return mId;
  }

  /** Destination where {@link Trigger} occurred. */
  public Uri getAttributionDestination() {
    return mAttributionDestination;
  }

  /** Destination type of the {@link Trigger}. */
  @EventSurfaceType
  public int getDestinationType() {
    return mDestinationType;
  }

  /** AdTech enrollment ID. */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** Time when the event occurred. */
  public long getTriggerTime() {
    return mTriggerTime;
  }

  /** Event triggers containing priority, de-dup key, trigger data and event-level filters info. */
  public String getEventTriggers() {
    return mEventTriggers;
  }

  /** Current state of the {@link Trigger}. */
  @Status
  public int getStatus() {
    return mStatus;
  }

  /** Set the status. */
  public void setStatus(@Status int status) {
    mStatus = status;
  }

  /** Registrant of this trigger, primarily an App. */
  public Uri getRegistrant() {
    return mRegistrant;
  }

  /**
   * Returns aggregate trigger data string used for aggregation. aggregate trigger data json is a
   * JSONArray. example: [ // Each dict independently adds pieces to multiple source keys. { //
   * Conversion type purchase = 2 at a 9 bit key_offset, i.e. 2 << 9. // A 9 bit key_offset is
   * needed because there are 511 possible campaigns, which // will take up 9 bits in the resulting
   * key. "key_piece": "0x400", // Apply this key piece to: "source_keys": ["campaignCounts"] }, {
   * // Purchase category shirts = 21 at a 7 bit key_offset, i.e. 21 << 7. // A 7 bit key_offset is
   * needed because there are ~100 regions for the geo key, // which will take up 7 bits of space in
   * the resulting key. "key_piece": "0xA80", // Apply this key piece to: "source_keys":
   * ["geoValue", "nonMatchingKeyIdsAreIgnored"] } ]
   */
  public String getAggregateTriggerData() {
    return mAggregateTriggerData;
  }

  /**
   * Returns aggregate value string used for aggregation. Aggregate value can be either JSONObject
   * or JSONArray. example: {"campaignCounts": 32768, "geoValue": 1664} example: [{ "values":
   * {"campaignCounts" :32768, "geoValue": 1664}, "filters": {"category": ["filter_1", "filter_2"]},
   * "not_filters": {"category": ["filter_3", "filter_4"]} }]
   */
  public String getAggregateValuesString() {
    return mAggregateValuesString;
  }

  /**
   * Returns a list of aggregate deduplication keys. aggregate deduplication key is a JSONObject.
   * example: { "deduplication_key": "32768", "filters": [ {type: [filter_1, filter_2]} ],
   * "not_filters": [ {type: [not_filter_1, not_filter_2]} ] }
   */
  public String getAggregateDeduplicationKeys() {
    return mAggregateDeduplicationKeys;
  }

  /**
   * Returns the AggregatableAttributionTrigger object, which is constructed using the aggregate
   * trigger data string and aggregate values string in Trigger.
   */
  public Optional<AggregatableAttributionTrigger> getAggregatableAttributionTrigger(Flags flags)
      throws JSONException {
    if (mAggregatableAttributionTrigger != null) {
      return mAggregatableAttributionTrigger;
    }

    mAggregatableAttributionTrigger = parseAggregateTrigger(flags);
    return mAggregatableAttributionTrigger;
  }

  /**
   * Returns top level filters. The value is in json format.
   *
   * <p>Will be used for deciding if the trigger can be attributed to the source. If the source
   * fails the filtering against these filters then no reports(event/aggregate) are generated.
   * example: { "key1" : ["value11", "value12"], "key2" : ["value21", "value22"] }
   */
  public String getFilters() {
    return mFilters;
  }

  /** Is Ad Tech Opt-in to Debug Reporting {@link Trigger}. */
  public boolean isDebugReporting() {
    return mIsDebugReporting;
  }

  /** Is Ad ID Permission Enabled. */
  public boolean hasAdIdPermission() {
    return mAdIdPermission;
  }

  /** Is Ar Debug Permission Enabled. */
  public boolean hasArDebugPermission() {
    return mArDebugPermission;
  }

  /** Returns top level not-filters. The value is in json format. */
  public String getNotFilters() {
    return mNotFilters;
  }

  /** Debug key of {@link Trigger}. */
  @Nullable
  public UnsignedLong getDebugKey() {
    return mDebugKey;
  }

  /**
   * Returns field attribution config JSONArray as String. example: [{ "source_network":
   * "AdTech1-Ads", "source_priority_range": { “start”: 100, “end”: 1000 }, "source_filters": {
   * "campaign_type": ["install"], "source_type": ["navigation"], }, "priority": "99", "expiry":
   * "604800", "filter_data":{ "campaign_type": ["install"], } }]
   */
  @Nullable
  public String getAttributionConfig() {
    return mAttributionConfig;
  }

  /**
   * Returns adtech bit mapping JSONObject as String. example: "x_network_key_mapping": {
   * "AdTechA-enrollment_id": "0x1", "AdTechB-enrollment_id": "0x2", }
   */
  @Nullable
  public String getAdtechKeyMapping() {
    return mAdtechKeyMapping;
  }

  /**
   * Returns join key that should be matched with source's join key at the time of generating
   * reports.
   */
  @Nullable
  public String getDebugJoinKey() {
    return mDebugJoinKey;
  }

  /**
   * Returns actual platform AdID from getAdId() on app trigger registration, to be matched with a
   * web source's {@link Trigger#getDebugAdId()} value at the time of generating reports.
   */
  @Nullable
  public String getPlatformAdId() {
    return mPlatformAdId;
  }

  /**
   * Returns SHA256 hash of AdID from registration response on web registration concatenated with
   * enrollment ID, to be matched with an app source's {@link Source#getPlatformAdId()} value at the
   * time of generating reports.
   */
  @Nullable
  public String getDebugAdId() {
    return mDebugAdId;
  }

  /** Returns registration origin used to register the source */
  public Uri getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Returns coordinator origin for aggregatable reports */
  @Nullable
  public Uri getAggregationCoordinatorOrigin() {
    return mAggregationCoordinatorOrigin;
  }

  /**
   * Return {@link SourceRegistrationTimeConfig#EXCLUDE} if the {@link AggregateReport} should not
   * include the attributed {@link Source} registration time during attribution reporting. Returns
   * {@link SourceRegistrationTimeConfig#INCLUDE} otherwise.
   */
  public SourceRegistrationTimeConfig getAggregatableSourceRegistrationTimeConfig() {
    return mAggregatableSourceRegistrationTimeConfig;
  }

  /** Returns the context id */
  @Nullable
  public String getTriggerContextId() {
    return mTriggerContextId;
  }

  /** Returns the aggregatable filtering id max bytes. */
  @Nullable
  public Integer getAggregatableFilteringIdMaxBytes() {
    return mAggregatableFilteringIdMaxBytes;
  }

  /**
   * Generates AggregatableAttributionTrigger from aggregate trigger data string and aggregate
   * values string in Trigger.
   */
  private Optional<AggregatableAttributionTrigger> parseAggregateTrigger(Flags flags)
      throws JSONException, NumberFormatException {
    if (mAggregateValuesString == null) {
      return Optional.empty();
    }
    JSONArray triggerDataArray =
        mAggregateTriggerData == null ? new JSONArray() : new JSONArray(mAggregateTriggerData);
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    Filter filter = new Filter(flags);
    for (int i = 0; i < triggerDataArray.length(); i++) {
      JSONObject triggerDatum = triggerDataArray.getJSONObject(i);
      // Remove "0x" prefix.
      String hexString = triggerDatum.getString("key_piece").substring(2);
      BigInteger bigInteger = new BigInteger(hexString, 16);
      JSONArray sourceKeys = triggerDatum.getJSONArray("source_keys");
      Set<String> sourceKeySet = new HashSet<>();
      for (int j = 0; j < sourceKeys.length(); j++) {
        sourceKeySet.add(sourceKeys.getString(j));
      }
      AggregateTriggerData.Builder builder =
          new AggregateTriggerData.Builder().setKey(bigInteger).setSourceKeys(sourceKeySet);
      if (triggerDatum.has(FilterContract.FILTERS)
          && !triggerDatum.isNull(FilterContract.FILTERS)) {
        List<FilterMap> filterSet =
            filter.deserializeFilterSet(triggerDatum.getJSONArray(FilterContract.FILTERS));
        builder.setFilterSet(filterSet);
      }
      if (triggerDatum.has(FilterContract.NOT_FILTERS)
          && !triggerDatum.isNull(FilterContract.NOT_FILTERS)) {
        List<FilterMap> notFilterSet =
            filter.deserializeFilterSet(triggerDatum.getJSONArray("not_filters"));
        builder.setNotFilterSet(notFilterSet);
      }
      if (!triggerDatum.isNull("x_network_data")) {
        JSONObject xNetworkDataJson = triggerDatum.getJSONObject("x_network_data");
        XNetworkData xNetworkData = new XNetworkData.Builder(xNetworkDataJson).build();
        builder.setXNetworkData(xNetworkData);
      }
      triggerDataList.add(builder.build());
    }
    List<AggregateDeduplicationKey> dedupKeyList = new ArrayList<>();
    if (getAggregateDeduplicationKeys() != null) {
      JSONArray dedupKeyObjects = new JSONArray(getAggregateDeduplicationKeys());
      for (int i = 0; i < dedupKeyObjects.length(); i++) {
        JSONObject dedupKeyObject = dedupKeyObjects.getJSONObject(i);
        AggregateDeduplicationKey.Builder builder = new AggregateDeduplicationKey.Builder();
        if (dedupKeyObject.has("deduplication_key")
            && !dedupKeyObject.isNull("deduplication_key")) {
          builder.setDeduplicationKey(
              new UnsignedLong(dedupKeyObject.getString("deduplication_key")));
        }
        if (dedupKeyObject.has(FilterContract.FILTERS)
            && !dedupKeyObject.isNull(FilterContract.FILTERS)) {
          List<FilterMap> filterSet =
              filter.deserializeFilterSet(dedupKeyObject.getJSONArray(FilterContract.FILTERS));
          builder.setFilterSet(filterSet);
        }
        if (dedupKeyObject.has(FilterContract.NOT_FILTERS)
            && !dedupKeyObject.isNull(FilterContract.NOT_FILTERS)) {
          List<FilterMap> notFilterSet =
              filter.deserializeFilterSet(dedupKeyObject.getJSONArray(FilterContract.NOT_FILTERS));
          builder.setNotFilterSet(notFilterSet);
        }
        dedupKeyList.add(builder.build());
      }
    }
    AggregatableAttributionTrigger.Builder aggregatableAttributionTriggerBuilder =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setAggregateDeduplicationKeys(dedupKeyList);
    Optional<JSONArray> maybeAggregateValuesArr =
        JsonUtil.maybeGetJsonArray(mAggregateValuesString);
    if (maybeAggregateValuesArr.isPresent()) {
      if (!flags.getMeasurementEnableAggregateValueFilters()) {
        return Optional.empty();
      }
      List<AggregatableValuesConfig> aggregatableValuesConfigList = new ArrayList<>();
      for (int i = 0; i < maybeAggregateValuesArr.get().length(); i++) {
        JSONObject valuesObj = maybeAggregateValuesArr.get().getJSONObject(i);
        AggregatableValuesConfig aggregatableValuesConfig =
            new AggregatableValuesConfig.Builder(valuesObj, flags).build();
        aggregatableValuesConfigList.add(aggregatableValuesConfig);
      }
      aggregatableAttributionTriggerBuilder.setValueConfigs(aggregatableValuesConfigList);
    } else {
      // Default case: Convert value from integer to AggregatableKeyValue.
      AggregatableValuesConfig aggregatableValuesConfig =
          new AggregatableValuesConfig.Builder(new JSONObject(mAggregateValuesString)).build();
      aggregatableAttributionTriggerBuilder.setValueConfigs(List.of(aggregatableValuesConfig));
    }
    return Optional.of(aggregatableAttributionTriggerBuilder.build());
  }

  /**
   * Parses the json array under {@link #mEventTriggers} to form a list of {@link EventTrigger}s.
   *
   * @return list of {@link EventTrigger}s
   * @throws JSONException if JSON parsing fails
   */
  public List<EventTrigger> parseEventTriggers(Flags flags) throws JSONException {
    JSONArray jsonArray = new JSONArray(mEventTriggers);
    List<EventTrigger> eventTriggers = new ArrayList<>();
    boolean readValue = flags.getMeasurementFlexibleEventReportingApiEnabled();
    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject eventTrigger = jsonArray.getJSONObject(i);

      EventTrigger.Builder eventTriggerBuilder =
          new EventTrigger.Builder(
              new UnsignedLong(eventTrigger.getString(EventTriggerContract.TRIGGER_DATA)));

      if (!eventTrigger.isNull(EventTriggerContract.PRIORITY)) {
        eventTriggerBuilder.setTriggerPriority(eventTrigger.getLong(EventTriggerContract.PRIORITY));
      }

      if (readValue && !eventTrigger.isNull(EventTriggerContract.VALUE)) {
        eventTriggerBuilder.setTriggerValue(eventTrigger.getLong(EventTriggerContract.VALUE));
      } else {
        eventTriggerBuilder.setTriggerValue(1L);
      }

      if (!eventTrigger.isNull(EventTriggerContract.DEDUPLICATION_KEY)) {
        eventTriggerBuilder.setDedupKey(
            new UnsignedLong(eventTrigger.getString(EventTriggerContract.DEDUPLICATION_KEY)));
      }

      if (!eventTrigger.isNull(FilterContract.FILTERS)) {
        List<FilterMap> filterSet =
            new Filter(flags)
                .deserializeFilterSet(eventTrigger.getJSONArray(FilterContract.FILTERS));
        eventTriggerBuilder.setFilterSet(filterSet);
      }

      if (!eventTrigger.isNull(FilterContract.NOT_FILTERS)) {
        List<FilterMap> notFilterSet =
            new Filter(flags)
                .deserializeFilterSet(eventTrigger.getJSONArray(FilterContract.NOT_FILTERS));
        eventTriggerBuilder.setNotFilterSet(notFilterSet);
      }
      eventTriggers.add(eventTriggerBuilder.build());
    }

    return eventTriggers;
  }

  /**
   * Parses the json object under {@link #mAdtechKeyMapping} to create a mapping of adtechs to their
   * bits.
   *
   * @return mapping of String to BigInteger
   * @throws JSONException if JSON parsing fails
   * @throws NumberFormatException if BigInteger parsing fails
   */
  @Nullable
  public Map<String, BigInteger> parseAdtechKeyMapping()
      throws JSONException, NumberFormatException {
    if (mAdtechKeyMapping == null) {
      return null;
    }
    Map<String, BigInteger> adtechBitMapping = new HashMap<>();
    JSONObject jsonObject = new JSONObject(mAdtechKeyMapping);
    Iterator<String> keys = jsonObject.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      // Remove "0x" prefix.
      String hexString = jsonObject.getString(key).substring(2);
      BigInteger bigInteger = new BigInteger(hexString, 16);
      adtechBitMapping.put(key, bigInteger);
    }
    return adtechBitMapping;
  }

  /**
   * Returns a {@code Uri} with scheme and (1) public suffix + 1 in case of a web destination, or
   * (2) the Android package name in case of an app destination. Returns null if extracting the
   * public suffix + 1 fails.
   */
  @Nullable
  public Uri getAttributionDestinationBaseUri() {
    if (mDestinationType == EventSurfaceType.APP) {
      return mAttributionDestination;
    } else {
      Optional<Uri> uri = WebAddresses.topPrivateDomainAndScheme(mAttributionDestination);
      return uri.orElse(null);
    }
  }

  /** Returns attribution scope string for the trigger. */
  @Nullable
  public String getAttributionScopesString() {
    return mAttributionScopesString;
  }

  /** Returns attribution scopes for the trigger. */
  @Nullable
  public List<String> getAttributionScopes() throws JSONException {
    if (mAttributionScopesString == null) {
      return null;
    }
    JSONArray jsonArray = new JSONArray(mAttributionScopesString);
    List<String> attributionScopes = new ArrayList<>();
    for (int i = 0; i < jsonArray.length(); ++i) {
      attributionScopes.add(jsonArray.getString(i));
    }
    return attributionScopes;
  }

  /** Returns the aggregate debug reporting object as a string */
  @Nullable
  public String getAggregateDebugReportingString() {
    return mAggregateDebugReportingString;
  }

  /** Returns the aggregate debug reporting object as a string */
  @Nullable
  public AggregateDebugReporting getAggregateDebugReportingObject() throws JSONException {
    if (mAggregateDebugReportingString == null) {
      return null;
    }
    if (mAggregateDebugReporting == null) {
      mAggregateDebugReporting =
          new AggregateDebugReporting.Builder(new JSONObject(mAggregateDebugReportingString))
              .build();
    }
    return mAggregateDebugReporting;
  }

  /** Builder for {@link Trigger}. */
  public static final class Builder {

    private final Trigger mBuilding;

    public Builder() {
      mBuilding = new Trigger();
    }

    /** See {@link Trigger#getId()}. */
    @NonNull
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link Trigger#getAttributionDestination()}. */
    @NonNull
    public Builder setAttributionDestination(Uri attributionDestination) {
      Validation.validateUri(attributionDestination);
      mBuilding.mAttributionDestination = attributionDestination;
      return this;
    }

    /** See {@link Trigger#getDestinationType()}. */
    @NonNull
    public Builder setDestinationType(@EventSurfaceType int destinationType) {
      mBuilding.mDestinationType = destinationType;
      return this;
    }

    /** See {@link Trigger#getEnrollmentId()}. */
    @NonNull
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link Trigger#getStatus()}. */
    @NonNull
    public Builder setStatus(@Status int status) {
      mBuilding.mStatus = status;
      return this;
    }

    /** See {@link Trigger#getTriggerTime()}. */
    @NonNull
    public Builder setTriggerTime(long triggerTime) {
      mBuilding.mTriggerTime = triggerTime;
      return this;
    }

    /** See {@link Trigger#getEventTriggers()}. */
    @NonNull
    public Builder setEventTriggers(@NonNull String eventTriggers) {
      Validation.validateNonNull(eventTriggers);
      mBuilding.mEventTriggers = eventTriggers;
      return this;
    }

    /** See {@link Trigger#getRegistrant()} */
    @NonNull
    public Builder setRegistrant(@NonNull Uri registrant) {
      Validation.validateUri(registrant);
      mBuilding.mRegistrant = registrant;
      return this;
    }

    /** See {@link Trigger#getAggregateTriggerData()}. */
    @NonNull
    public Builder setAggregateTriggerData(@Nullable String aggregateTriggerData) {
      mBuilding.mAggregateTriggerData = aggregateTriggerData;
      return this;
    }

    /** See {@link Trigger#getAggregateValuesString()} */
    @NonNull
    public Builder setAggregateValuesString(@Nullable String aggregateValuesString) {
      mBuilding.mAggregateValuesString = aggregateValuesString;
      return this;
    }

    /** See {@link Trigger#getAggregateDeduplicationKeys()} */
    @NonNull
    public Builder setAggregateDeduplicationKeys(@NonNull String aggregateDeduplicationKeys) {
      mBuilding.mAggregateDeduplicationKeys = aggregateDeduplicationKeys;
      return this;
    }

    /** See {@link Trigger#getFilters()} */
    @NonNull
    public Builder setFilters(@Nullable String filters) {
      mBuilding.mFilters = filters;
      return this;
    }

    /** See {@link Trigger#isDebugReporting()} */
    public Builder setIsDebugReporting(boolean isDebugReporting) {
      mBuilding.mIsDebugReporting = isDebugReporting;
      return this;
    }

    /** See {@link Trigger#hasAdIdPermission()} */
    public Builder setAdIdPermission(boolean adIdPermission) {
      mBuilding.mAdIdPermission = adIdPermission;
      return this;
    }

    /** See {@link Trigger#hasArDebugPermission()} */
    public Builder setArDebugPermission(boolean arDebugPermission) {
      mBuilding.mArDebugPermission = arDebugPermission;
      return this;
    }

    /** See {@link Trigger#getNotFilters()} */
    @NonNull
    public Builder setNotFilters(@Nullable String notFilters) {
      mBuilding.mNotFilters = notFilters;
      return this;
    }

    /** See {@link Trigger#getDebugKey()} */
    public Builder setDebugKey(@Nullable UnsignedLong debugKey) {
      mBuilding.mDebugKey = debugKey;
      return this;
    }

    /** See {@link Trigger#getAttributionConfig()} */
    public Builder setAttributionConfig(@Nullable String attributionConfig) {
      mBuilding.mAttributionConfig = attributionConfig;
      return this;
    }

    /** See {@link Trigger#getAdtechKeyMapping()} */
    public Builder setAdtechBitMapping(@Nullable String adtechBitMapping) {
      mBuilding.mAdtechKeyMapping = adtechBitMapping;
      return this;
    }

    /** See {@link Trigger#getAggregatableAttributionTrigger()} */
    @NonNull
    public Builder setAggregatableAttributionTrigger(
        @Nullable AggregatableAttributionTrigger aggregatableAttributionTrigger) {
      mBuilding.mAggregatableAttributionTrigger =
          Optional.ofNullable(aggregatableAttributionTrigger);
      return this;
    }

    /** See {@link Trigger#getDebugJoinKey()} */
    @NonNull
    public Builder setDebugJoinKey(@Nullable String debugJoinKey) {
      mBuilding.mDebugJoinKey = debugJoinKey;
      return this;
    }

    /** See {@link Trigger#getPlatformAdId()} */
    @NonNull
    public Builder setPlatformAdId(@Nullable String platformAdId) {
      mBuilding.mPlatformAdId = platformAdId;
      return this;
    }

    /** See {@link Trigger#getDebugAdId()} */
    @NonNull
    public Builder setDebugAdId(@Nullable String debugAdId) {
      mBuilding.mDebugAdId = debugAdId;
      return this;
    }

    /** See {@link Trigger#getRegistrationOrigin()} */
    @NonNull
    public Builder setRegistrationOrigin(Uri registrationOrigin) {
      mBuilding.mRegistrationOrigin = registrationOrigin;
      return this;
    }

    /** See {@link Trigger#getAggregationCoordinatorOrigin()} */
    public Builder setAggregationCoordinatorOrigin(Uri aggregationCoordinatorOrigin) {
      mBuilding.mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
      return this;
    }

    /** See {@link Trigger#getAggregatableSourceRegistrationTimeConfig()}. */
    @NonNull
    public Builder setAggregatableSourceRegistrationTimeConfig(
        SourceRegistrationTimeConfig config) {
      mBuilding.mAggregatableSourceRegistrationTimeConfig = config;
      return this;
    }

    /** See {@link Trigger#getTriggerContextId()}. */
    public Builder setTriggerContextId(@Nullable String triggerContextId) {
      mBuilding.mTriggerContextId = triggerContextId;
      return this;
    }

    /** See {@link Trigger#getAttributionScopesString()}. */
    @NonNull
    public Builder setAttributionScopesString(@Nullable String attributionScopesString) {
      mBuilding.mAttributionScopesString = attributionScopesString;
      return this;
    }

    /** See {@link Trigger#getAggregatableFilteringIdMaxBytes()} */
    public Builder setAggregatableFilteringIdMaxBytes(
        @Nullable Integer aggregatableFilteringIdMaxBytes) {
      mBuilding.mAggregatableFilteringIdMaxBytes = aggregatableFilteringIdMaxBytes;
      return this;
    }

    /** See {@link Trigger#getAggregateDebugReportingString()}. */
    @NonNull
    public Builder setAggregateDebugReportingString(
        @Nullable String aggregateDebugReportingString) {
      mBuilding.mAggregateDebugReportingString = aggregateDebugReportingString;
      return this;
    }

    /** Build the {@link Trigger}. */
    @NonNull
    public Trigger build() {
      Validation.validateNonNull(
          mBuilding.mAttributionDestination,
          mBuilding.mEnrollmentId,
          mBuilding.mRegistrant,
          mBuilding.mRegistrationOrigin,
          mBuilding.mAggregatableSourceRegistrationTimeConfig);

      return mBuilding;
    }
  }

  /** Event trigger field keys. */
  public interface EventTriggerContract {
    String TRIGGER_DATA = "trigger_data";
    String PRIORITY = "priority";
    String VALUE = "value";
    String DEDUPLICATION_KEY = "deduplication_key";
  }
}
