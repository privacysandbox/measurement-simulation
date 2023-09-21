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

import static com.google.measurement.FetcherUtil.areValidAttributionFilters;
import static com.google.measurement.util.BaseUriExtractor.getBaseUri;
import static com.google.measurement.util.Web.topPrivateDomainAndScheme;

import com.google.measurement.Source.AttributionMode;
import com.google.measurement.Source.SourceType;
import com.google.measurement.Source.Status;
import com.google.measurement.util.UnsignedLong;
import com.google.measurement.util.Util;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class SourceProcessor {
  private static final Logger logger = Logger.getLogger(SourceProcessor.class.getName());
  private static final long ONE_DAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(1);
  private static final String mDefaultAndroidAppScheme = "android-app";
  private static final String mDefaultAndroidAppURIPrefix = mDefaultAndroidAppScheme + "://";

  /**
   * Build Source object from the input json object
   *
   * @param jsonObject Json representation of the Source object
   * @return Source object
   * @throws ParseException if parsing fails
   */
  public static Source buildSourceFromJson(JSONObject jsonObject) throws Exception {
    Source.Builder builder = new Source.Builder();
    builder.setPublisher(getBaseUri(URI.create((String) jsonObject.get("publisher"))));
    builder.setEnrollmentId((String) jsonObject.get("enrollment_id"));
    builder.setRegistrant(URI.create((String) jsonObject.get("registrant")));
    builder.setSourceType(Enum.valueOf(SourceType.class, (String) jsonObject.get("source_type")));
    builder.setAttributionMode(AttributionMode.TRUTHFULLY);
    builder.setEventTime(Util.parseJsonLong(jsonObject, "timestamp"));
    if (jsonObject.containsKey("has_ad_id_permission")) {
      builder.setAdIdPermission((boolean) jsonObject.get("has_ad_id_permission"));
    }

    if (jsonObject.containsKey("has_ar_debug_permission")) {
      builder.setArDebugPermission((boolean) jsonObject.get("has_ar_debug_permission"));
    }

    if (jsonObject.containsKey("publisher_type")) {
      builder.setPublisherType(
          Enum.valueOf(EventSurfaceType.class, (String) jsonObject.get("publisher_type")));
    }

    boolean isValid = parseSource(builder, jsonObject);
    if (!isValid) {
      throw new Exception("Unable to parse Source data.");
    }

    if (jsonObject.containsKey("aggregation_keys")) {
      JSONObject aggregationKeys = (JSONObject) jsonObject.get("aggregation_keys");
      if (!areValidAggregationKeys(aggregationKeys)) {
        throw new Exception("Unable to parse Source data.");
      }
      builder.setAggregateSource(aggregationKeys.toString());
    }

    if (jsonObject.containsKey("debug_key")) {
      builder.setDebugKey(Util.parseJsonUnsignedLong(jsonObject, "debug_key"));
    }

    if (jsonObject.containsKey("shared_aggregation_keys")) {
      JSONArray sharedAggregationKeys = (JSONArray) jsonObject.get("shared_aggregation_keys");
      builder.setSharedAggregationKeys(sharedAggregationKeys.toString());
    }
    if (jsonObject.containsKey("max_event_level_reports")) {
      builder.setMaxEventLevelReports(Util.parseJsonInt(jsonObject, "max_event_level_reports"));
    }
    builder.setStatus(Status.ACTIVE);
    if (jsonObject.containsKey("trigger_specs")) {
      String triggerSpecString = ((JSONArray) jsonObject.get("trigger_specs")).toJSONString();

      if (!isTriggerSpecArrayValid(triggerSpecString, PrivacyParams.EXPIRY)) {
        throw new Exception("Unable to parse Source data: Invalid Trigger Spec format");
      }
      String updatedTriggerSpec =
          populateTriggerSpecDefaults(
              triggerSpecString,
              null,
              PrivacyParams.EXPIRY,
              Enum.valueOf(SourceType.class, (String) jsonObject.get("source_type")));

      builder.setTriggerSpecs(updatedTriggerSpec);

      // This step must be after source type is set and max event level report
      builder.buildInitialFlexEventReportSpec();
      if (!builder.hasValidInformationGain()) {
        throw new Exception("Unable to parse Source data: Information gain exceeds limit");
      }
    }
    return builder.build();
  }

  private static boolean isTriggerSpecArrayValid(String triggerSpecString, long expiry) {
    JSONArray triggerSpecArray = new JSONArray();
    JSONParser parser = new JSONParser();
    try {
      triggerSpecArray = (JSONArray) parser.parse(triggerSpecString);
      Set<UnsignedLong> triggerDataSet = new HashSet<>();
      for (int i = 0; i < triggerSpecArray.size(); i++) {
        if (!isTriggerSpecValid((JSONObject) triggerSpecArray.get(i), expiry, triggerDataSet)) {
          return false;
        }
      }
      // Check cardinality of trigger_data across the whole trigger spec array
      if (triggerDataSet.size() > PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY) {
        return false;
      }
    } catch (ParseException e) {
      logger.info("Trigger Spec parsing failed");
      return false;
    }
    return true;
  }

  private static boolean isTriggerSpecValid(
      JSONObject triggerSpec, long expiry, Set<UnsignedLong> triggerDataSet) {
    List<UnsignedLong> triggerDataList =
        TriggerSpec.getTriggerDataArrayFromJSON(
            triggerSpec, ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA);
    if (triggerDataList.isEmpty()
        || triggerDataList.size() > PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY) {
      return false;
    }
    // Check exclusivity of trigger_data across the whole trigger spec array
    for (UnsignedLong triggerData : triggerDataList) {
      if (!triggerDataSet.add(triggerData)) {
        return false;
      }
    }

    if (triggerSpec.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)
        && !isEventReportWindowsValid(
            (JSONObject)
                triggerSpec.get(ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS),
            expiry)) {
      return false;
    }

    TriggerSpec.SummaryOperatorType summaryWindowOperator = TriggerSpec.SummaryOperatorType.COUNT;
    if (triggerSpec.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
      try {
        String op =
            (String)
                triggerSpec.get(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR);

        summaryWindowOperator = TriggerSpec.SummaryOperatorType.valueOf(op.toUpperCase());
      } catch (IllegalArgumentException e) {
        // If a summary window operator is not in the pre-defined list, it will throw to
        // exception.
        logger.info("Summary Operator parsing failed");
        return false;
      }
    }
    List<Long> summaryBuckets = null;
    if (triggerSpec.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
      summaryBuckets =
          TriggerSpec.getLongArrayFromJSON(
              triggerSpec, ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS);
    }
    if ((summaryBuckets == null || summaryBuckets.isEmpty())
        && summaryWindowOperator != TriggerSpec.SummaryOperatorType.COUNT) {
      return false;
    }

    if (summaryBuckets != null && !TriggerSpec.isStrictIncreasing(summaryBuckets)) {
      return false;
    }

    return true;
  }

  private static boolean parseSource(Source.Builder builder, JSONObject jsonObject)
      throws Exception {
    String appDst = (String) jsonObject.get("destination");
    Object webDst = jsonObject.get("web_destination");
    if (appDst == null && webDst == null) {
      throw new Exception("Provide at least destination or web_destination.");
    }
    if (!jsonObject.containsKey("source_event_id")) {
      throw new Exception("Provide source_event_id.");
    }

    builder.setId(UUID.randomUUID().toString());
    builder.setEventId(Util.parseJsonUnsignedLong(jsonObject, "source_event_id"));

    long timestamp = Util.parseJsonLong(jsonObject, "timestamp");

    long expiry = PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
    if (jsonObject.containsKey("expiry")) {
      expiry = Util.parseJsonLong(jsonObject, "expiry");
      expiry =
          extractValidNumberInRange(
              expiry,
              PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
              PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);

      SourceType sourceType =
          Enum.valueOf(SourceType.class, (String) jsonObject.get("source_type"));
      if (sourceType == SourceType.EVENT) {
        expiry = roundSecondsToWholeDays(expiry);
      }
    }
    builder.setExpiryTime(timestamp + TimeUnit.SECONDS.toMillis(expiry));

    long eventReportWindow;
    if (jsonObject.containsKey("event_report_window")) {
      eventReportWindow =
          Math.min(
              expiry,
              extractValidNumberInRange(
                  Util.parseJsonLong(jsonObject, "event_report_window"),
                  PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                  PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
    } else {
      eventReportWindow = expiry;
    }
    builder.setEventReportWindow(
        Long.valueOf(timestamp + TimeUnit.SECONDS.toMillis(eventReportWindow)));

    long aggregatableReportWindow;
    if (jsonObject.containsKey("aggregatable_report_window")) {
      aggregatableReportWindow =
          Math.min(
              expiry,
              extractValidNumberInRange(
                  Util.parseJsonLong(jsonObject, "aggregatable_report_window"),
                  PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                  PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
    } else {
      aggregatableReportWindow = expiry;
    }
    builder.setAggregatableReportWindow(
        timestamp + TimeUnit.SECONDS.toMillis(aggregatableReportWindow));

    if (jsonObject.containsKey("priority")) {
      builder.setPriority(Util.parseJsonLong(jsonObject, "priority"));
    }

    long inputInstallAttributionWindow = PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
    if (jsonObject.containsKey("install_attribution_window")) {
      inputInstallAttributionWindow = Util.parseJsonLong(jsonObject, "install_attribution_window");
    }
    long installAttributionWindow =
        extractValidNumberInRange(
            inputInstallAttributionWindow,
            PrivacyParams.MIN_INSTALL_ATTRIBUTION_WINDOW,
            PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW);
    builder.setInstallAttributionWindow(TimeUnit.SECONDS.toMillis(installAttributionWindow));

    long inputPostInstallExclusivityWindow = PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
    if (jsonObject.containsKey("post_install_exclusivity_window")) {
      inputPostInstallExclusivityWindow =
          Util.parseJsonLong(jsonObject, "post_install_exclusivity_window");
    }
    long installCooldownWindow =
        extractValidNumberInRange(
            inputPostInstallExclusivityWindow,
            PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
            PrivacyParams.MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
    builder.setInstallCooldownWindow(TimeUnit.SECONDS.toMillis(installCooldownWindow));

    if (jsonObject.containsKey("filter_data")) {
      JSONObject filterData = (JSONObject) jsonObject.get("filter_data");
      if (!areValidAttributionFilters(filterData)) {
        logger.info("Source filter_data is invalid.");
        return false;
      }
      builder.setFilterData(filterData.toString());
    }

    if (appDst != null) {
      URI appUri = URI.create(appDst);
      if (appUri.getScheme() == null) {
        logger.info("App destination is missing app scheme, adding.");
        appUri = URI.create(mDefaultAndroidAppURIPrefix + appUri);
      }
      if (!mDefaultAndroidAppScheme.equals(appUri.getScheme())) {
        logger.severe(
            String.format(
                "Invalid scheme for app destination: %s; dropping the source.",
                appUri.getScheme()));
        return false;
      }
      List<URI> appUris = new ArrayList<>();
      appUris.add(getBaseUri(appUri));
      builder.setAppDestinations(appUris);
    }

    if (webDst != null) {
      Set<URI> destinationSet = new HashSet();
      JSONArray webDestinations = new JSONArray();
      if (webDst instanceof String) {
        webDestinations.add(webDst);
      } else {
        webDestinations.addAll((List<String>) webDst);
      }

      if (webDestinations.size()
          > PrivacyParams.MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION) {
        logger.info("Source registration exceeded the number of allowed destinations.");
        return false;
      }

      for (int i = 0; i < webDestinations.size(); ++i) {
        URI destination = URI.create((String) webDestinations.get(i));
        Optional<URI> topPrivateDomainAndScheme = topPrivateDomainAndScheme(destination);
        if (!topPrivateDomainAndScheme.isPresent()) {
          logger.info("Unable to extract top private domain and scheme from web destination.");
          return false;
        } else {
          destinationSet.add(topPrivateDomainAndScheme.get());
        }
      }
      List<URI> webUris = new ArrayList<>();
      webUris.addAll(destinationSet);
      builder.setWebDestinations(webUris);
    }

    ApiChoice apiChoice = ApiChoice.OS;
    if (jsonObject.containsKey("api_choice")) {
      apiChoice = ApiChoice.valueOf((String) jsonObject.get("api_choice"));
    }
    builder.setApiChoice(apiChoice);

    return true;
  }

  private static long roundSecondsToWholeDays(long seconds) {
    long remainder = seconds % ONE_DAY_IN_SECONDS;
    boolean roundUp = remainder >= ONE_DAY_IN_SECONDS / 2L;
    return seconds - remainder + (roundUp ? ONE_DAY_IN_SECONDS : 0);
  }

  private static boolean areValidAggregationKeys(JSONObject aggregationKeys) {
    if (aggregationKeys.size() > SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
      logger.info(
          String.format(
              "Aggregation-keys have more entries than permitted: %d", aggregationKeys.size()));
      return false;
    }

    for (String id : new ArrayList<String>(aggregationKeys.keySet())) {
      if (!FetcherUtil.isValidAggregateKeyId(id)) {
        logger.info(String.format("Aggregate source data key_id is invalid. %s", id));
        return false;
      }
      String keyPiece = (String) aggregationKeys.get(id);
      if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
        logger.info(String.format("Aggregate source data key-piece is invalid. %s", keyPiece));
        return false;
      }
    }
    return true;
  }

  private static long extractValidNumberInRange(long value, long lowerLimit, long upperLimit) {
    if (value < lowerLimit) {
      return lowerLimit;
    }
    if (value > upperLimit) {
      return upperLimit;
    }
    return value;
  }

  private static boolean isEventReportWindowsValid(JSONObject jsonReportWindows, long expiry) {
    long startTime = 0;
    if (jsonReportWindows.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME)) {
      startTime = (Long) jsonReportWindows.get(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME);
    }
    if (startTime < 0 || startTime > expiry) {
      return false;
    }
    List<Long> windowsEnd =
        TriggerSpec.getLongArrayFromJSON(
            jsonReportWindows, ReportSpecUtil.FlexEventReportJsonKeys.END_TIMES);
    if (windowsEnd.isEmpty()
        || windowsEnd.size()
            > PrivacyParams.MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS) {
      return false;
    }

    if (startTime > windowsEnd.get(0) || windowsEnd.get(windowsEnd.size() - 1) > expiry) {
      return false;
    }

    if (!TriggerSpec.isStrictIncreasing(windowsEnd)) {
      return false;
    }
    return true;
  }

  private static String populateTriggerSpecDefaults(
      String triggerSpecString,
      String eventReportWindows,
      long expiry,
      Source.SourceType sourceType) {
    List<Pair<Long, Long>> parsedEventReportWindows =
        Source.getOrDefaultEventReportWindows(eventReportWindows, sourceType, expiry);
    long defaultStart = parsedEventReportWindows.get(0).first;
    List<Long> defaultEnds =
        parsedEventReportWindows.stream().map((x) -> x.second).collect(Collectors.toList());
    JSONParser parser = new JSONParser();
    try {
      JSONArray triggerSpecJson = (JSONArray) parser.parse(triggerSpecString);
      TriggerSpec[] triggerSpecs = new TriggerSpec[triggerSpecJson.size()];
      for (int i = 0; i < triggerSpecJson.size(); i++) {
        triggerSpecs[i] =
            new TriggerSpec.Builder((JSONObject) triggerSpecJson.get(i), defaultStart, defaultEnds)
                .build();
      }
      return ReportSpec.encodeTriggerSpecsToJson(triggerSpecs);
    } catch (ParseException e) {
      return "";
    }
  }
}
