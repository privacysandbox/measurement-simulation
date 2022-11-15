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

import com.google.protobuf.ListValue;
import com.google.rubidium.InputData.AggregatableTriggerData;
import com.google.rubidium.InputData.TriggerData;
import com.google.rubidium.Trigger.Status;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class TriggerProcessor {
  private static final Logger logger = Logger.getLogger(TriggerProcessor.class.getName());

  /**
   * Build Trigger object from the input json object
   *
   * @param jsonObject Json representation of the Trigger object
   * @return Trigger object
   * @throws ParseException if parsing fails
   */
  public static Trigger buildTriggerFromJson(JSONObject jsonObject) throws Exception {
    Trigger.Builder builder = new Trigger.Builder();
    boolean isValid = parseTrigger(builder, jsonObject);
    if (!isValid) {
      throw new Exception("Unable to parse Trigger data.");
    }

    builder.setId(UUID.randomUUID().toString());
    EventSurfaceType destinationType = EventSurfaceType.APP;
    if (jsonObject.containsKey("destination_type")) {
      String dstType = (String) jsonObject.get("destination_type");
      if (dstType.equals("WEB")) {
        destinationType = EventSurfaceType.WEB;
      }
    }

    builder.setAttributionDestination(
        URI.create((String) jsonObject.get("attribution_destination")));
    builder.setDestinationType(destinationType);
    builder.setEnrollmentId((String) jsonObject.get("enrollment_id"));
    builder.setStatus(Status.PENDING);
    builder.setTriggerTime((Long) jsonObject.get("trigger_time"));
    builder.setRegistrant(URI.create((String) jsonObject.get("registrant")));
    return builder.build();
  }

  private static boolean parseTrigger(Trigger.Builder builder, JSONObject jsonObject) {
    if (jsonObject.containsKey("event_trigger_data")) {
      Optional<String> validEventTriggerData =
          getValidEventTriggerData((JSONArray) jsonObject.get("event_trigger_data"));
      if (!validEventTriggerData.isPresent()) {
        return false;
      }
      builder.setEventTriggers(validEventTriggerData.get());
    }
    if (jsonObject.containsKey("aggregatable_trigger_data")) {
      JSONArray aggregateTriggerData = (JSONArray) jsonObject.get("aggregatable_trigger_data");
      if (!isValidAggregateTriggerData(aggregateTriggerData)) {
        return false;
      }
      builder.setAggregateTriggerData(aggregateTriggerData.toString());
    }
    if (jsonObject.containsKey("aggregatable_values")) {
      JSONObject aggregatableValues = (JSONObject) jsonObject.get("aggregatable_values");
      if (!isValidAggregateValues(aggregatableValues)) {
        return false;
      }
      builder.setAggregateValues(aggregatableValues.toString());
    }
    if (jsonObject.containsKey("filters")) {
      builder.setFilters((String) jsonObject.get("filters"));
    }
    return true;
  }

  private static Optional<String> getValidEventTriggerData(JSONArray eventTriggerDataArr) {
    if (eventTriggerDataArr.size() > SystemHealthParams.MAX_ATTRIBUTION_EVENT_TRIGGER_DATA) {
      logger.info(
          String.format(
              "Event trigger data list has more entries than permitted. %d",
              eventTriggerDataArr.size()));
      return Optional.empty();
    }
    JSONArray validEventTriggerData = new JSONArray();
    for (int i = 0; i < eventTriggerDataArr.size(); ++i) {
      JSONObject validEventTriggerDatum = new JSONObject();
      JSONObject eventTriggerDatum = (JSONObject) eventTriggerDataArr.get(i);
      // Treat invalid trigger data, priority and deduplication key as if they were not
      // set.
      if (eventTriggerDatum.containsKey("trigger_data")) {
        try {
          validEventTriggerDatum.put("trigger_data", (long) eventTriggerDatum.get("trigger_data"));
        } catch (NumberFormatException e) {
          logger.info(
              String.format(
                  "getValidEventTriggerData: parsing trigger_data failed with error: %s.",
                  e.getMessage()));
        }
      }
      if (eventTriggerDatum.containsKey("priority")) {
        try {
          validEventTriggerDatum.put("priority", (long) eventTriggerDatum.get("priority"));
        } catch (NumberFormatException e) {
          logger.info(
              String.format(
                  "getValidEventTriggerData: parsing priority failed with error: %s.",
                  e.getMessage()));
        }
      }
      if (eventTriggerDatum.containsKey("deduplication_key")) {
        try {
          validEventTriggerDatum.put(
              "deduplication_key", (long) eventTriggerDatum.get("deduplication_key"));
        } catch (NumberFormatException e) {
          logger.info(
              String.format(
                  "getValidEventTriggerData: parsing deduplication_key failed with error: %s.",
                  e.getMessage()));
        }
      }
      if (eventTriggerDatum.containsKey("filters")) {
        JSONObject filters = (JSONObject) eventTriggerDatum.get("filters");
        if (!FetcherUtil.areValidAttributionFilters(filters)) {
          logger.info("getValidEventTriggerData: filters are invalid.");
          return Optional.empty();
        }
        validEventTriggerDatum.put("filters", filters);
      }
      if (eventTriggerDatum.containsKey("not_filters")) {
        JSONObject notFilters = (JSONObject) eventTriggerDatum.get("not_filters");
        if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
          logger.info("getValidEventTriggerData: not-filters are invalid.");
          return Optional.empty();
        }
        validEventTriggerDatum.put("not_filters", notFilters);
      }
      validEventTriggerData.add(validEventTriggerDatum);
    }
    return Optional.of(validEventTriggerData.toString());
  }

  private static boolean isValidAggregateTriggerData(JSONArray aggregateTriggerDataArr) {
    if (aggregateTriggerDataArr.size() > SystemHealthParams.MAX_AGGREGATABLE_TRIGGER_DATA) {
      logger.info(
          String.format(
              "Aggregate trigger data list has more entries than permitted. %d",
              aggregateTriggerDataArr.size()));
      return false;
    }
    for (int i = 0; i < aggregateTriggerDataArr.size(); i++) {
      JSONObject aggregateTriggerData = (JSONObject) aggregateTriggerDataArr.get(i);
      String keyPiece = (String) aggregateTriggerData.get("key_piece");
      if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
        logger.info(String.format("Aggregate trigger data key_piece is invalid. %s", keyPiece));
        return false;
      }
      JSONArray sourceKeys = (JSONArray) aggregateTriggerData.get("source_keys");
      if (sourceKeys == null
          || sourceKeys.size() > PrivacyParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
        logger.info(
            "Aggregate trigger data source-keys list failed to parse or has more entries than"
                + " permitted.");
        return false;
      }
      for (int j = 0; j < sourceKeys.size(); j++) {
        String key = (String) sourceKeys.get(j);
        if (!FetcherUtil.isValidAggregateKeyId(key)) {
          logger.info(String.format("Aggregate trigger data source-key is invalid. %s", key));
          return false;
        }
      }
      if (aggregateTriggerData.containsKey("filters")
          && !FetcherUtil.areValidAttributionFilters(
              (JSONObject) aggregateTriggerData.get("filters"))) {
        logger.info("Aggregate trigger data filters are invalid.");
        return false;
      }
      if (aggregateTriggerData.containsKey("not_filters")
          && !FetcherUtil.areValidAttributionFilters(
              (JSONObject) aggregateTriggerData.get("not_filters"))) {
        logger.info("Aggregate trigger data not_filters are invalid.");
        return false;
      }
    }
    return true;
  }

  private static boolean isValidAggregateValues(JSONObject aggregateValues) {
    if (aggregateValues.size() > PrivacyParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
      logger.info(
          String.format(
              "Aggregate values have more keys than permitted. %s", aggregateValues.size()));
      return false;
    }
    Iterator<String> ids = aggregateValues.keySet().iterator();
    while (ids.hasNext()) {
      String id = ids.next();
      if (!FetcherUtil.isValidAggregateKeyId(id)) {
        logger.info(String.format("Aggregate values key ID is invalid. %s", id));
        return false;
      }
    }
    return true;
  }

  /**
   * Build Trigger object from a proto record.
   *
   * @param triggerProto protobuf representation of Trigger object
   * @return Trigger object
   */
  public static Trigger buildTriggerFromProto(InputData.Trigger triggerProto) throws Exception {
    Trigger.Builder builder = new Trigger.Builder();
    List<InputData.TriggerData> eventTriggerDataList = triggerProto.getEventTriggerDataList();
    if (!eventTriggerDataList.isEmpty()) {
      JSONArray eventTriggerData = new JSONArray();
      for (TriggerData data : eventTriggerDataList) {
        JSONObject dataObj = new JSONObject();
        dataObj.put("trigger_data", data.getTriggerData());
        dataObj.put("priority", data.getPriority());
        dataObj.put("deduplication_key", data.getDeduplicationKey());
        eventTriggerData.add(dataObj);
      }
      Optional<String> validEventTriggerData = getValidEventTriggerData(eventTriggerData);
      if (!validEventTriggerData.isPresent()) {
        throw new Exception("Unable to parse Trigger data.");
      }
      builder.setEventTriggers(validEventTriggerData.get());
    }

    List<AggregatableTriggerData> aggregatableTriggerDataList =
        triggerProto.getAggregatableTriggerDataList();
    if (!aggregatableTriggerDataList.isEmpty()) {
      JSONArray aggregatableTriggerData = new JSONArray();
      for (AggregatableTriggerData data : aggregatableTriggerDataList) {
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", data.getKeyPiece());
        JSONArray sourceKeys = new JSONArray();
        for (int i = 0; i < data.getSourceKeysCount(); ++i) {
          sourceKeys.add(data.getSourceKeys(i));
        }
        if (!sourceKeys.isEmpty()) {
          dataObj.put("source_keys", sourceKeys);
        }

        JSONObject filtersDataObj = new JSONObject();
        for (Entry<String, ListValue> filtersData : data.getFiltersMap().entrySet()) {
          JSONArray filtersDataList = new JSONArray();
          filtersData.getValue().getValuesList().stream()
              .forEach(elem -> filtersDataList.add(elem.getStringValue()));
          filtersDataObj.put(filtersData.getKey(), filtersDataList);
        }
        if (!filtersDataObj.isEmpty()) {
          dataObj.put("filters", filtersDataObj);
        }

        JSONObject notFiltersDataObj = new JSONObject();
        for (Entry<String, ListValue> notFiltersData : data.getNotFiltersMap().entrySet()) {
          JSONArray notFiltersDataList = new JSONArray();
          notFiltersData.getValue().getValuesList().stream()
              .forEach(elem -> notFiltersDataList.add(elem.getStringValue()));
          notFiltersDataObj.put(notFiltersData.getKey(), notFiltersDataList);
        }
        if (!notFiltersDataObj.isEmpty()) {
          dataObj.put("not_filters", notFiltersDataObj);
        }

        aggregatableTriggerData.add(dataObj);
      }
      if (!isValidAggregateTriggerData(aggregatableTriggerData)) {
        throw new Exception("Unable to parse Trigger data.");
      }
      builder.setAggregateTriggerData(aggregatableTriggerData.toString());
    }

    Map<String, Long> aggregateValuesMap = triggerProto.getAggregateValuesMap();
    if (!aggregateValuesMap.isEmpty()) {
      JSONObject aggregateValues = new JSONObject(aggregateValuesMap);
      if (!isValidAggregateValues(aggregateValues)) {
        throw new Exception("Unable to parse Trigger data.");
      }
      builder.setAggregateValues(aggregateValues.toString());
    }

    if (!triggerProto.getFilters().isEmpty()) {
      builder.setFilters(triggerProto.getFilters());
    }

    builder.setId(UUID.randomUUID().toString());
    EventSurfaceType destinationType =
        Enum.valueOf(EventSurfaceType.class, triggerProto.getDestinationType().toString());
    builder.setDestinationType(destinationType);
    builder.setAttributionDestination(URI.create(triggerProto.getAttributionDestination()));
    builder.setEnrollmentId(triggerProto.getEnrollmentId());
    builder.setStatus(Status.PENDING);
    builder.setTriggerTime(triggerProto.getTriggerTime());
    builder.setRegistrant(URI.create(triggerProto.getRegistrant()));

    return builder.build();
  }
}
