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

import com.google.rubidium.aggregation.AggregatableAttributionTrigger;
import com.google.rubidium.aggregation.AggregateFilterData;
import com.google.rubidium.aggregation.AggregateTriggerData;
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
  @Nullable private AggregatableAttributionTrigger mAggregatableAttributionTrigger;
  @Nullable private String mFilters;
  @Nullable private Long mDebugKey;

  public enum Status {
    PENDING,
    IGNORED,
    ATTRIBUTED
  }

  private Trigger() {
    mStatus = Status.PENDING;
    // Making this default explicit since it anyway occur on an uninitialised int field.
    mDestinationType = EventSurfaceType.APP;
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
        && Objects.equals(mRegistrant, trigger.mRegistrant)
        && Objects.equals(mAggregateTriggerData, trigger.mAggregateTriggerData)
        && Objects.equals(mAggregateValues, trigger.mAggregateValues)
        && Objects.equals(mAggregatableAttributionTrigger, trigger.mAggregatableAttributionTrigger)
        && Objects.equals(mFilters, trigger.mFilters);
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
        mDebugKey);
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
   * Conversion type purchase = 2 at a 9 bit offset, i.e. 2 << 9. // A 9 bit offset is needed
   * because there are 511 possible campaigns, which // will take up 9 bits in the resulting key.
   * "key_piece": "0x400", // Apply this key piece to: "source_keys": ["campaignCounts"] }, { //
   * Purchase category shirts = 21 at a 7 bit offset, i.e. 21 << 7. // A 7 bit offset is needed
   * because there are ~100 regions for the geo key, // which will take up 7 bits of space in the
   * resulting key. "key_piece": "0xA80", // Apply this key piece to: "source_keys": ["geoValue",
   * "nonMatchingKeyIdsAreIgnored"] } ]
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
   * Returns the AggregatableAttributionTrigger object, which is constructed using the aggregate
   * trigger data string and aggregate values string in Trigger.
   */
  public AggregatableAttributionTrigger getAggregatableAttributionTrigger() {
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

  /** Debug key of {@link Trigger}. */
  public @Nullable Long getDebugKey() {
    return mDebugKey;
  }

  /**
   * Generates AggregatableAttributionTrigger from aggregate trigger data string and aggregate
   * values string in Trigger.
   */
  public Optional<AggregatableAttributionTrigger> parseAggregateTrigger()
      throws ParseException, NumberFormatException {
    if (this.mAggregateTriggerData == null || this.mAggregateValues == null) {
      return Optional.empty();
    }
    JSONParser parser = new JSONParser();
    JSONArray jsonArray = (JSONArray) parser.parse(mAggregateTriggerData);
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      JSONObject jsonObject = (JSONObject) jsonArray.get(i);
      String hexString = (String) jsonObject.get("key_piece");
      if (hexString.startsWith("0x")) {
        hexString = hexString.substring(2);
      }
      // Do not process trigger if a key exceeds 128 bits.
      if (hexString.length() > 32) {
        return Optional.empty();
      }
      BigInteger bigInteger = new BigInteger(hexString, 16);
      JSONArray sourceKeys = (JSONArray) jsonObject.get("source_keys");
      HashSet<String> sourceKeySet = new HashSet<>();
      for (int j = 0; j < sourceKeys.size(); j++) {
        sourceKeySet.add((String) sourceKeys.get(j));
      }
      AggregateTriggerData.Builder builder =
          new AggregateTriggerData.Builder().setKey(bigInteger).setSourceKeys(sourceKeySet);
      if (jsonObject.containsKey("filters") && jsonObject.get("filters") != null) {
        AggregateFilterData filters =
            new AggregateFilterData.Builder()
                .buildAggregateFilterData((JSONObject) jsonObject.get("filters"))
                .build();
        builder.setFilter(filters);
      }
      if (jsonObject.containsKey("not_filters") && jsonObject.get("not_filters") != null) {
        AggregateFilterData notFilters =
            new AggregateFilterData.Builder()
                .buildAggregateFilterData((JSONObject) jsonObject.get("not_filters"))
                .build();
        builder.setNotFilter(notFilters);
      }
      triggerDataList.add(builder.build());
    }
    JSONObject values = (JSONObject) parser.parse(mAggregateValues);
    Map<String, Integer> valueMap = new HashMap<>();
    for (Object key : values.keySet()) {
      valueMap.put((String) key, Math.toIntExact((Long) values.get(key)));
    }
    return Optional.of(
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(valueMap)
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
    JSONArray jsonArray = (JSONArray) parser.parse(mEventTriggers);
    List<EventTrigger> eventTriggers = new ArrayList<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      EventTrigger.Builder eventTriggerBuilder = new EventTrigger.Builder();
      JSONObject eventTriggersJsonString = (JSONObject) jsonArray.get(i);
      if (eventTriggersJsonString.get(EventTriggerContract.TRIGGER_DATA) != null) {
        eventTriggerBuilder.setTriggerData(
            (Long) eventTriggersJsonString.get(EventTriggerContract.TRIGGER_DATA));
      }
      if (eventTriggersJsonString.get(EventTriggerContract.PRIORITY) != null) {
        eventTriggerBuilder.setTriggerPriority(
            (Long) eventTriggersJsonString.get(EventTriggerContract.PRIORITY));
      }
      if (eventTriggersJsonString.get(EventTriggerContract.DEDUPLICATION_KEY) != null) {
        eventTriggerBuilder.setDedupKey(
            (Long) eventTriggersJsonString.get(EventTriggerContract.DEDUPLICATION_KEY));
      }
      if (eventTriggersJsonString.get(EventTriggerContract.FILTERS) != null) {
        AggregateFilterData filters =
            new AggregateFilterData.Builder()
                .buildAggregateFilterData(
                    (JSONObject) eventTriggersJsonString.get(EventTriggerContract.FILTERS))
                .build();
        eventTriggerBuilder.setFilter(filters);
      }
      if (eventTriggersJsonString.get(EventTriggerContract.NOT_FILTERS) != null) {
        AggregateFilterData notFilters =
            new AggregateFilterData.Builder()
                .buildAggregateFilterData(
                    (JSONObject) eventTriggersJsonString.get(EventTriggerContract.NOT_FILTERS))
                .build();
        eventTriggerBuilder.setNotFilter(notFilters);
      }
      eventTriggers.add(eventTriggerBuilder.build());
    }
    return eventTriggers;
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

    /** See {@link Trigger#getEnrollmentId()} ()}. */
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
    public Builder setAggregateTriggerData(@Nullable String aggregateTriggerData) {
      mBuilding.mAggregateTriggerData = aggregateTriggerData;
      return this;
    }

    /** See {@link Trigger#getAggregateValues()} */
    public Builder setAggregateValues(@Nullable String aggregateValues) {
      mBuilding.mAggregateValues = aggregateValues;
      return this;
    }

    /** See {@link Trigger#getFilters()} */
    public Builder setFilters(@Nullable String filters) {
      mBuilding.mFilters = filters;
      return this;
    }

    /** See {@link Trigger#getDebugKey()} ()} */
    public Builder setDebugKey(@Nullable Long debugKey) {
      mBuilding.mDebugKey = debugKey;
      return this;
    }

    /** See {@link Trigger#getAggregatableAttributionTrigger()} */
    public Builder setAggregatableAttributionTrigger(
        @Nullable AggregatableAttributionTrigger aggregatableAttributionTrigger) {
      mBuilding.mAggregatableAttributionTrigger = aggregatableAttributionTrigger;
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
