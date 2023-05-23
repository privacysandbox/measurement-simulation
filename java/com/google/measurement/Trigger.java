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

import com.google.measurement.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.aggregation.AggregateDeduplicationKey;
import com.google.measurement.aggregation.AggregateTriggerData;
import com.google.measurement.util.Filter;
import com.google.measurement.util.UnsignedLong;
import com.google.measurement.util.Util;
import com.google.measurement.util.Web;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.avro.reflect.Nullable;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@DefaultCoder(AvroCoder.class)
public class Trigger implements Serializable {
  private String mId;
  private URI mAttributionDestination;
  private EventSurfaceType mDestinationType;
  private String mEnrollmentId;
  private long mTriggerTime;
  private String mEventTriggers;
  private Status mStatus;
  private URI mRegistrant;
  @Nullable private String mAggregateTriggerData;
  @Nullable private String mAggregateValues;
  @Nullable private String mAggregateDeduplicationKeys;
  private boolean mIsDebugReporting;
  @Nullable private Optional<AggregatableAttributionTrigger> mAggregatableAttributionTrigger;
  @Nullable private String mFilters;
  @Nullable private String mNotFilters;
  @Nullable private UnsignedLong mDebugKey;
  private boolean mAdIdPermission;
  private boolean mArDebugPermission;
  @Nullable private String mAttributionConfig;
  @Nullable private String mAdtechKeyMapping;
  private ApiChoice mApiChoice;

  public enum Status {
    PENDING,
    IGNORED,
    ATTRIBUTED,
    MARKED_TO_DELETE
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
    return Objects.equals(mAttributionDestination, trigger.mAttributionDestination)
        && mDestinationType == trigger.mDestinationType
        && Objects.equals(mEnrollmentId, trigger.mEnrollmentId)
        && mTriggerTime == trigger.mTriggerTime
        && Objects.equals(mDebugKey, trigger.mDebugKey)
        && Objects.equals(mEventTriggers, trigger.mEventTriggers)
        && mStatus == trigger.mStatus
        && mIsDebugReporting == trigger.mIsDebugReporting
        && mAdIdPermission == trigger.mAdIdPermission
        && mArDebugPermission == trigger.mArDebugPermission
        && Objects.equals(mRegistrant, trigger.mRegistrant)
        && Objects.equals(mAggregateTriggerData, trigger.mAggregateTriggerData)
        && Objects.equals(mAggregateValues, trigger.mAggregateValues)
        && Objects.equals(mAggregatableAttributionTrigger, trigger.mAggregatableAttributionTrigger)
        && Objects.equals(mFilters, trigger.mFilters)
        && Objects.equals(mNotFilters, trigger.mNotFilters)
        && Objects.equals(mAttributionConfig, trigger.mAttributionConfig)
        && Objects.equals(mAdtechKeyMapping, trigger.mAdtechKeyMapping)
        && Objects.equals(mAggregateDeduplicationKeys, trigger.mAggregateDeduplicationKeys)
        && mApiChoice == trigger.mApiChoice;
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
        mAggregateValues,
        mAggregatableAttributionTrigger,
        mFilters,
        mNotFilters,
        mDebugKey,
        mAdIdPermission,
        mArDebugPermission,
        mAttributionConfig,
        mAdtechKeyMapping,
        mAggregateDeduplicationKeys);
  }

  /** Unique identifier for the {@link Trigger}. */
  public String getId() {
    return mId;
  }

  /** Destination where {@link Trigger} occurred. */
  public URI getAttributionDestination() {
    return mAttributionDestination;
  }

  /** Destination type of the {@link Trigger}. */
  public EventSurfaceType getDestinationType() {
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
  public Status getStatus() {
    return mStatus;
  }

  /** Set the status. */
  public void setStatus(Status status) {
    mStatus = status;
  }

  /** Registrant of this trigger, primarily an App. */
  public URI getRegistrant() {
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
   * Returns aggregate value string used for aggregation. aggregate value json is a JSONObject.
   * example: { "campaignCounts": 32768, "geoValue": 1664 }
   */
  public String getAggregateValues() {
    return mAggregateValues;
  }

  /**
   * Returns a string which represents a list of Aggregate Deduplication keys. Aggregate
   * Deduplication key is a JSONObject. Example: { "deduplication_key": "32768", "filters": [ {type:
   * [filter_1, filter_2]} ], "not_filters": [ {type: [not_filter_1, not_filter_2]} ] }
   */
  public String getAggregateDeduplicationKeys() {
    return mAggregateDeduplicationKeys;
  }

  /**
   * Returns the AggregatableAttributionTrigger object, which is constructed using the aggregate
   * trigger data string and aggregate values string in Trigger.
   */
  public Optional<AggregatableAttributionTrigger> getAggregatableAttributionTrigger()
      throws ParseException {
    if (mAggregatableAttributionTrigger != null) {
      return mAggregatableAttributionTrigger;
    }
    mAggregatableAttributionTrigger = parseAggregateTrigger();
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

  public ApiChoice getApiChoice() {
    return mApiChoice;
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
   * Returns Ad Tech bit mapping JSONObject as String. example: "x_network_key_mapping": {
   * "AdTechA-enrollment_id": "0x1", "AdTechB-enrollment_id": "0x2", }
   */
  @Nullable
  public String getAdtechKeyMapping() {
    return mAdtechKeyMapping;
  }

  /**
   * Generates AggregatableAttributionTrigger from aggregate trigger data string and aggregate
   * values string in Trigger.
   */
  private Optional<AggregatableAttributionTrigger> parseAggregateTrigger()
      throws ParseException, NumberFormatException {
    if (this.mAggregateValues == null) {
      return Optional.empty();
    }
    JSONParser parser = new JSONParser();
    JSONArray jsonArray =
        (this.mAggregateTriggerData == null)
            ? new JSONArray()
            : (JSONArray) parser.parse(this.mAggregateTriggerData);
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      JSONObject jsonObject = (JSONObject) jsonArray.get(i);
      // Remove "0x" prefix.
      String hexString = ((String) jsonObject.get("key_piece")).substring(2);
      BigInteger bigInteger = new BigInteger(hexString, 16);
      JSONArray sourceKeys = (JSONArray) jsonObject.get("source_keys");
      HashSet<String> sourceKeySet = new HashSet<>();
      for (int j = 0; j < sourceKeys.size(); j++) {
        sourceKeySet.add((String) sourceKeys.get(j));
      }
      AggregateTriggerData.Builder builder =
          new AggregateTriggerData.Builder().setKey(bigInteger).setSourceKeys(sourceKeySet);
      if (jsonObject.containsKey("filters")) {
        List<FilterMap> filterSet =
            Filter.deserializeFilterSet((JSONArray) jsonObject.get("filters"));
        builder.setFilterSet(filterSet);
      }
      if (jsonObject.containsKey("not_filters")) {
        List<FilterMap> notFilterSet =
            Filter.deserializeFilterSet((JSONArray) jsonObject.get("not_filters"));
        builder.setNotFilterSet(notFilterSet);
      }
      if (jsonObject.containsKey("x_network_data")) {
        JSONObject xNetworkDataJson = (JSONObject) jsonObject.get("x_network_data");
        XNetworkData xNetworkData = new XNetworkData.Builder(xNetworkDataJson).build();
        builder.setXNetworkData(xNetworkData);
      }
      triggerDataList.add(builder.build());
    }
    JSONObject values = (JSONObject) parser.parse(this.mAggregateValues);
    Map<String, Integer> valueMap = new HashMap<>();
    for (Object key : values.keySet()) {
      valueMap.put((String) key, Util.parseJsonInt(values, (String) key));
    }
    List<AggregateDeduplicationKey> dedupKeyList = new ArrayList<>();
    if (this.getAggregateDeduplicationKeys() != null) {
      JSONArray dedupKeyObjects = (JSONArray) parser.parse(this.getAggregateDeduplicationKeys());
      for (int i = 0; i < dedupKeyObjects.size(); i++) {
        JSONObject dedupKeyObject = (JSONObject) dedupKeyObjects.get(i);
        UnsignedLong dedupKey = Util.parseJsonUnsignedLong(dedupKeyObject, "deduplication_key");
        AggregateDeduplicationKey.Builder builder = new AggregateDeduplicationKey.Builder(dedupKey);
        if (dedupKeyObject.containsKey("filters")) {
          List<FilterMap> filterSet =
              Filter.deserializeFilterSet((JSONArray) dedupKeyObject.get("filters"));
          builder.setFilterSet(filterSet);
        }
        if (dedupKeyObject.containsKey("not_filters")) {
          List<FilterMap> notFilterSet =
              Filter.deserializeFilterSet((JSONArray) dedupKeyObject.get("not_filters"));
          builder.setNotFilterSet(notFilterSet);
        }
        dedupKeyList.add(builder.build());
      }
    }
    return Optional.of(
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(valueMap)
            .setAggregateDeduplicationKeys(dedupKeyList)
            .build());
  }

  /**
   * Parses the json array under {@link #mEventTriggers} to form a list of {@link EventTrigger}s.
   *
   * @return list of {@link EventTrigger}s
   * @throws ParseException if JSON parsing fails
   */
  public List<EventTrigger> parseEventTriggers() throws ParseException {
    JSONParser parser = new JSONParser();
    JSONArray jsonArray = (JSONArray) parser.parse(this.mEventTriggers);
    List<EventTrigger> eventTriggers = new ArrayList<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      JSONObject eventTrigger = (JSONObject) jsonArray.get(i);
      EventTrigger.Builder eventTriggerBuilder =
          new EventTrigger.Builder(
              Util.parseJsonUnsignedLong(eventTrigger, EventTriggerContract.TRIGGER_DATA));
      if (eventTrigger.containsKey(EventTriggerContract.PRIORITY)) {
        eventTriggerBuilder.setTriggerPriority(
            Util.parseJsonLong(eventTrigger, EventTriggerContract.PRIORITY));
      }
      if (eventTrigger.containsKey(EventTriggerContract.DEDUPLICATION_KEY)) {
        eventTriggerBuilder.setDedupKey(
            Util.parseJsonUnsignedLong(eventTrigger, EventTriggerContract.DEDUPLICATION_KEY));
      }
      if (eventTrigger.containsKey(EventTriggerContract.FILTERS)) {
        List<FilterMap> filterSet =
            Filter.deserializeFilterSet((JSONArray) eventTrigger.get(EventTriggerContract.FILTERS));
        eventTriggerBuilder.setFilterSet(filterSet);
      }
      if (eventTrigger.containsKey(EventTriggerContract.NOT_FILTERS)) {
        List<FilterMap> notFilterSet =
            Filter.deserializeFilterSet(
                (JSONArray) eventTrigger.get(EventTriggerContract.NOT_FILTERS));
        eventTriggerBuilder.setNotFilterSet(notFilterSet);
      }
      eventTriggers.add(eventTriggerBuilder.build());
    }
    return eventTriggers;
  }

  /**
   * Parses the json object under {@link #mAdtechKeyMapping} to create a mapping of Ad Techs to
   * their bits.
   *
   * @return mapping of String to BigInteger
   * @throws ParseException if JSON parsing fails
   * @throws NumberFormatException if BigInteger parsing fails
   */
  @Nullable
  public Map<Object, BigInteger> parseAdtechKeyMapping()
      throws ParseException, NumberFormatException {
    if (mAdtechKeyMapping == null) {
      return null;
    }
    Map<Object, BigInteger> adtechBitMapping = new HashMap<>();
    JSONParser parser = new JSONParser();
    JSONObject jsonObject = (JSONObject) parser.parse(mAdtechKeyMapping);
    for (Object key : jsonObject.keySet()) {
      // Remove "0x" prefix.
      String hexString = ((String) jsonObject.get(key)).substring(2);
      BigInteger bigInteger = new BigInteger(hexString, 16);
      adtechBitMapping.put(key, bigInteger);
    }
    return adtechBitMapping;
  }

  /**
   * Returns a {@code URI} with scheme and (1) public suffix + 1 in case of a web destination, or
   * (2) the Android package name in case of an app destination. Returns null if extracting the
   * public suffix + 1 fails.
   */
  @Nullable
  public URI getAttributionDestinationBaseUri() {
    if (mDestinationType == EventSurfaceType.APP) {
      return mAttributionDestination;
    } else {
      Optional<URI> uri = Web.topPrivateDomainAndScheme(mAttributionDestination);
      return uri.orElse(null);
    }
  }

  /** Builder for {@link Trigger}. */
  public static final class Builder {
    private final Trigger mBuilding;

    public Builder() {
      mBuilding = new Trigger();
    }

    /** See {@link Trigger#getId()}. */
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link Trigger#getAttributionDestination()}. */
    public Builder setAttributionDestination(URI attributionDestination) {
      mBuilding.mAttributionDestination = attributionDestination;
      return this;
    }

    /** See {@link Trigger#getDestinationType()}. */
    public Builder setDestinationType(EventSurfaceType destinationType) {
      mBuilding.mDestinationType = destinationType;
      return this;
    }

    /** See {@link Trigger#getEnrollmentId()}. */
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link Trigger#getStatus()}. */
    public Builder setStatus(Status status) {
      mBuilding.mStatus = status;
      return this;
    }

    /** See {@link Trigger#getTriggerTime()}. */
    public Builder setTriggerTime(long triggerTime) {
      mBuilding.mTriggerTime = triggerTime;
      return this;
    }

    /** See {@link Trigger#getEventTriggers()}. */
    public Builder setEventTriggers(String eventTriggers) {
      mBuilding.mEventTriggers = eventTriggers;
      return this;
    }

    /** See {@link Trigger#getRegistrant()} */
    public Builder setRegistrant(URI registrant) {
      mBuilding.mRegistrant = registrant;
      return this;
    }

    /** See {@link Trigger#getAggregateTriggerData()}. */
    public Builder setAggregateTriggerData(String aggregateTriggerData) {
      mBuilding.mAggregateTriggerData = aggregateTriggerData;
      return this;
    }

    /** See {@link Trigger#getAggregateValues()} */
    public Builder setAggregateValues(String aggregateValues) {
      mBuilding.mAggregateValues = aggregateValues;
      return this;
    }

    /** See {@link Trigger#getAggregateDeduplicationKeys()} */
    public Builder setAggregateDeduplicationKeys(String aggregateDeduplicationKeys) {
      mBuilding.mAggregateDeduplicationKeys = aggregateDeduplicationKeys;
      return this;
    }

    /** See {@link Trigger#getFilters()} */
    public Builder setFilters(String filters) {
      mBuilding.mFilters = filters;
      return this;
    }

    /** See {@link Trigger#isDebugReporting()} */
    public Trigger.Builder setIsDebugReporting(boolean isDebugReporting) {
      mBuilding.mIsDebugReporting = isDebugReporting;
      return this;
    }

    /** See {@link Trigger#hasAdIdPermission()} */
    public Trigger.Builder setAdIdPermission(boolean adIdPermission) {
      mBuilding.mAdIdPermission = adIdPermission;
      return this;
    }

    /** See {@link Trigger#hasArDebugPermission()} */
    public Trigger.Builder setArDebugPermission(boolean arDebugPermission) {
      mBuilding.mArDebugPermission = arDebugPermission;
      return this;
    }

    /** See {@link Trigger#getNotFilters()} */
    public Builder setNotFilters(String notFilters) {
      mBuilding.mNotFilters = notFilters;
      return this;
    }

    /** See {@link Trigger#getDebugKey()} */
    public Builder setDebugKey(UnsignedLong debugKey) {
      mBuilding.mDebugKey = debugKey;
      return this;
    }

    /** See {@link Trigger#getAttributionConfig()} */
    public Builder setAttributionConfig(String attributionConfig) {
      mBuilding.mAttributionConfig = attributionConfig;
      return this;
    }

    /** See {@link Trigger#getAdtechKeyMapping()} */
    public Builder setAdtechBitMapping(String adtechBitMapping) {
      mBuilding.mAdtechKeyMapping = adtechBitMapping;
      return this;
    }

    /** See {@link Trigger#getAggregatableAttributionTrigger()} */
    public Builder setAggregatableAttributionTrigger(
        @Nullable AggregatableAttributionTrigger aggregatableAttributionTrigger) {
      mBuilding.mAggregatableAttributionTrigger =
          Optional.ofNullable(aggregatableAttributionTrigger);
      return this;
    }

    public Builder setApiChoice(ApiChoice apiChoice) {
      mBuilding.mApiChoice = apiChoice;
      return this;
    }

    /** Build the {@link Trigger}. */
    public Trigger build() {
      return mBuilding;
    }
  }

  /** Event trigger field keys. */
  public interface EventTriggerContract {
    String TRIGGER_DATA = "trigger_data";
    String PRIORITY = "priority";
    String DEDUPLICATION_KEY = "deduplication_key";
    String FILTERS = "filters";
    String NOT_FILTERS = "not_filters";
  }
}
