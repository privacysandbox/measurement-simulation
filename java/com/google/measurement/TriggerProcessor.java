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

import com.google.measurement.Trigger.Status;
import com.google.measurement.util.Filter;
import java.net.URI;
import java.util.Iterator;
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
    builder.setRegistrant(URI.create((String) jsonObject.get("registrant")));
    builder.setDestinationType(destinationType);
    builder.setTriggerTime((Long) jsonObject.get("timestamp"));

    builder.setEnrollmentId((String) jsonObject.get("enrollment_id"));
    builder.setStatus(Status.PENDING);
    return builder.build();
  }

  private static boolean parseTrigger(Trigger.Builder builder, JSONObject jsonObject) {
    String eventTriggerData = new JSONArray().toString();
    if (jsonObject.containsKey("event_trigger_data")) {
      Optional<String> validEventTriggerData =
          getValidEventTriggerData((JSONArray) jsonObject.get("event_trigger_data"));
      if (!validEventTriggerData.isPresent()) {
        return false;
      }
      eventTriggerData = validEventTriggerData.get();
    }
    builder.setEventTriggers(eventTriggerData);

    if (jsonObject.containsKey("aggregatable_trigger_data")) {
      JSONArray aggregatableTriggerData = (JSONArray) jsonObject.get("aggregatable_trigger_data");
      Optional<String> validAggregatableTrigerData =
          getValidAggregatableTriggerData(aggregatableTriggerData);
      if (!validAggregatableTrigerData.isPresent()) {
        return false;
      }
      builder.setAggregateTriggerData(validAggregatableTrigerData.get());
    }

    if (jsonObject.containsKey("aggregatable_values")) {
      JSONObject aggregatableValues = (JSONObject) jsonObject.get("aggregatable_values");
      if (!isValidAggregateValues(aggregatableValues)) {
        return false;
      }
      builder.setAggregateValues(aggregatableValues.toString());
    }

    if (jsonObject.containsKey("aggregatable_deduplication_keys")) {
      Optional<String> validAggregatableDeduplicationKeysString =
          getValidAggregateDuplicationKeysString(
              (JSONArray) jsonObject.get("aggregatable_deduplication_keys"));
      if (!validAggregatableDeduplicationKeysString.isPresent()) {
        logger.info("parseTrigger: aggregate deduplication keys are invalid.");
        return false;
      }
      builder.setAggregateDeduplicationKeys(validAggregatableDeduplicationKeysString.get());
    }
    if (jsonObject.containsKey("filters")) {
      JSONArray filters = Filter.maybeWrapFilters(jsonObject, "filters");
      if (!FetcherUtil.areValidAttributionFilters(filters)) {
        logger.info("parseTrigger: filters are invalid.");
        return false;
      }
      builder.setFilters(filters.toString());
    }
    if (jsonObject.containsKey("not_filters")) {
      JSONArray notFilters = Filter.maybeWrapFilters(jsonObject, "not_filters");
      if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
        logger.info("parseTrigger: not_filters are invalid.");
        return false;
      }
      builder.setNotFilters(notFilters.toString());
    }

    ApiChoice apiChoice = ApiChoice.OS;
    if (jsonObject.containsKey("api_choice")) {
      apiChoice = ApiChoice.valueOf((String) jsonObject.get("api_choice"));
    }
    builder.setApiChoice(apiChoice);

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
        JSONArray filters = Filter.maybeWrapFilters(eventTriggerDatum, "filters");
        if (!FetcherUtil.areValidAttributionFilters(filters)) {
          logger.info("getValidEventTriggerData: filters are invalid.");
          return Optional.empty();
        }
        validEventTriggerDatum.put("filters", filters);
      }
      if (eventTriggerDatum.containsKey("not_filters")) {
        JSONArray notFilters = Filter.maybeWrapFilters(eventTriggerDatum, "not_filters");
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

  private static Optional<String> getValidAggregatableTriggerData(
      JSONArray aggregatableTriggerDataArr) {
    if (aggregatableTriggerDataArr.size() > SystemHealthParams.MAX_AGGREGATABLE_TRIGGER_DATA) {
      logger.info(
          String.format(
              "Aggregate trigger data list has more entries than permitted. %d",
              aggregatableTriggerDataArr.size()));
      return Optional.empty();
    }
    JSONArray validAggregateTriggerData = new JSONArray();
    for (int i = 0; i < aggregatableTriggerDataArr.size(); i++) {
      JSONObject aggregatableTriggerData = (JSONObject) aggregatableTriggerDataArr.get(i);
      String keyPiece = (String) aggregatableTriggerData.get("key_piece");
      if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
        logger.info(String.format("Aggregate trigger data key_piece is invalid. %s", keyPiece));
        return Optional.empty();
      }
      JSONArray sourceKeys = (JSONArray) aggregatableTriggerData.get("source_keys");
      if (sourceKeys == null
          || sourceKeys.size() > SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
        logger.info(
            "Aggregate trigger data source-keys list failed to parse or has more entries than"
                + " permitted.");
        return Optional.empty();
      }
      for (int j = 0; j < sourceKeys.size(); j++) {
        String key = (String) sourceKeys.get(j);
        if (!FetcherUtil.isValidAggregateKeyId(key)) {
          logger.info(String.format("Aggregate trigger data source-key is invalid. %s", key));
          return Optional.empty();
        }
      }
      if (aggregatableTriggerData.containsKey("filters")) {
        JSONArray filters = Filter.maybeWrapFilters(aggregatableTriggerData, "filters");
        if (!FetcherUtil.areValidAttributionFilters(filters)) {
          logger.info("Aggregate trigger data filters are invalid.");
          return Optional.empty();
        }
        aggregatableTriggerData.put("filters", filters);
      }
      if (aggregatableTriggerData.containsKey("not_filters")) {
        JSONArray notFilters = Filter.maybeWrapFilters(aggregatableTriggerData, "not_filters");
        if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
          logger.info("Aggregate trigger data not_filters are invalid.");
          return Optional.empty();
        }
        aggregatableTriggerData.put("not_filters", notFilters);
      }
      validAggregateTriggerData.add(aggregatableTriggerData);
    }
    return Optional.of(validAggregateTriggerData.toString());
  }

  private static boolean isValidAggregateValues(JSONObject aggregateValues) {
    if (aggregateValues.size() > SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
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

  private static Optional<String> getValidAggregateDuplicationKeysString(
      JSONArray aggregateDeduplicationKeys) {
    JSONArray validAggregateDeduplicationKeys = new JSONArray();
    if (aggregateDeduplicationKeys.size()
        > SystemHealthParams.MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION) {
      logger.info(
          String.format(
              "Aggregate deduplication keys have more keys than permitted. %s",
              aggregateDeduplicationKeys.size()));
      return Optional.empty();
    }
    for (int i = 0; i < aggregateDeduplicationKeys.size(); i++) {
      JSONObject aggregateDedupKey = new JSONObject();
      JSONObject deduplication_key = (JSONObject) aggregateDeduplicationKeys.get(i);
      String deduplicationKey = (String) deduplication_key.get("deduplication_key");
      if (!FetcherUtil.isValidAggregateDeduplicationKey(deduplicationKey)) {
        return Optional.empty();
      }
      aggregateDedupKey.put("deduplication_key", deduplicationKey);
      if (deduplication_key.containsKey("filters")) {
        JSONArray filters = Filter.maybeWrapFilters(deduplication_key, "filters");
        if (!FetcherUtil.areValidAttributionFilters(filters)) {
          logger.info("Aggregate deduplication key: " + i + " contains invalid filters.");
          return Optional.empty();
        }
        aggregateDedupKey.put("filters", filters);
      }
      if (deduplication_key.containsKey("not_filters")) {
        JSONArray notFilters = Filter.maybeWrapFilters(deduplication_key, "not_filters");
        if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
          logger.info("Aggregate deduplication key: " + i + " contains invalid not_filters.");
          return Optional.empty();
        }
        aggregateDedupKey.put("not_filters", notFilters);
      }
      validAggregateDeduplicationKeys.add(aggregateDedupKey);
    }
    return Optional.of(validAggregateDeduplicationKeys.toString());
  }
}
