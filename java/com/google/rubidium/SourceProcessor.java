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

import static com.google.rubidium.FetcherUtil.areValidAttributionFilters;
import static com.google.rubidium.util.BaseUriExtractor.getBaseUri;
import static com.google.rubidium.util.Web.topPrivateDomainAndScheme;

import com.google.protobuf.ListValue;
import com.google.rubidium.Source.AttributionMode;
import com.google.rubidium.Source.SourceType;
import com.google.rubidium.Source.Status;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class SourceProcessor {
  private static final Logger logger = Logger.getLogger(SourceProcessor.class.getName());
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
    JSONArray dedupKeys = (JSONArray) jsonObject.get("dedup_keys");
    List<Long> dedupKeysAsList = new ArrayList<>();
    if (dedupKeys != null) dedupKeys.forEach(key -> dedupKeysAsList.add((Long) key));

    Source.Builder builder = new Source.Builder();
    builder.setEventTime((Long) jsonObject.get("event_time"));
    boolean isValid = parseSource(builder, jsonObject);
    if (!isValid) {
      throw new Exception("Unable to parse Source data.");
    }

    if (jsonObject.containsKey("aggregation_keys")) {
      JSONArray aggregationKeys = (JSONArray) jsonObject.get("aggregation_keys");
      if (!areValidAggregationKeys(aggregationKeys)) {
        throw new Exception("Unable to parse Source data.");
      }
      builder.setAggregateSource(aggregationKeys.toString());
    }

    builder.setSourceType(Enum.valueOf(SourceType.class, (String) jsonObject.get("source_type")));
    builder.setAttributionMode(AttributionMode.TRUTHFULLY);
    builder.setEnrollmentId((String) jsonObject.get("enrollment_id"));
    builder.setDedupKeys(dedupKeysAsList);
    builder.setStatus(Status.ACTIVE);
    builder.setPublisher(URI.create((String) jsonObject.get("publisher")));
    builder.setRegistrant(URI.create((String) jsonObject.get("registrant")));
    return builder.build();
  }

  private static boolean parseSource(Source.Builder builder, JSONObject jsonObject)
      throws Exception {
    String appDst = (String) jsonObject.get("destination");
    String webDst = (String) jsonObject.get("web_destination");
    if (appDst == null && webDst == null) {
      throw new Exception("Provide at least destination or web_destination.");
    }
    if (!jsonObject.containsKey("source_event_id")) {
      throw new Exception("Provide source_event_id.");
    }
    builder.setId(UUID.randomUUID().toString());
    builder.setEventId((long) jsonObject.get("source_event_id"));
    long expiry = PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
    if (jsonObject.containsKey("expiry")) {
      expiry = (Long) jsonObject.get("expiry");
    }
    long expiryTime =
        extractValidNumberInRange(
            expiry,
            PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
            PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    builder.setExpiryTime(
        (long) jsonObject.get("event_time") + TimeUnit.SECONDS.toMillis(expiryTime));

    if (jsonObject.containsKey("priority")) {
      builder.setPriority((Long) jsonObject.get("priority"));
    }

    long inputInstallAttributionWindow = PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
    if (jsonObject.containsKey("install_attribution_window")) {
      inputInstallAttributionWindow = (Long) jsonObject.get("install_attribution_window");
    }
    long installAttributionWindow =
        extractValidNumberInRange(
            inputInstallAttributionWindow,
            PrivacyParams.MIN_INSTALL_ATTRIBUTION_WINDOW,
            PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW);
    builder.setInstallAttributionWindow(TimeUnit.SECONDS.toMillis(installAttributionWindow));

    long inputPostInstallExclusivityWindow = PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
    if (jsonObject.containsKey("post_install_exclusivity_window")) {
      inputPostInstallExclusivityWindow = (Long) jsonObject.get("post_install_exclusivity_window");
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
      builder.setAggregateFilterData(filterData.toString());
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
      builder.setAppDestination(getBaseUri(appUri));
    }

    if (webDst != null) {
      URI webUri = URI.create(webDst);
      Optional<URI> topPrivateDomainAndScheme = topPrivateDomainAndScheme(webUri);
      if (!topPrivateDomainAndScheme.isPresent()) {
        logger.info("Unable to extract top private domain and scheme from web destination.");
        return false;
      } else {
        builder.setWebDestination(topPrivateDomainAndScheme.get());
      }
    }
    return true;
  }

  private static boolean areValidAggregationKeys(JSONArray aggregationKeys) {
    if (aggregationKeys.size() > SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
      logger.info(
          String.format(
              "Aggregation-keys have more entries than permitted: %d", aggregationKeys.size()));
      return false;
    }
    for (int i = 0; i < aggregationKeys.size(); ++i) {
      JSONObject keyObj = (JSONObject) aggregationKeys.get(i);
      if (keyObj == null) {
        logger.info("Aggregation key failed to parse.");
        return false;
      }
      String id = (String) keyObj.get("id");
      if (!FetcherUtil.isValidAggregateKeyId(id)) {
        logger.info(String.format("Aggregate source data key_id is invalid. %s", id));
        return false;
      }

      String keyPiece = (String) keyObj.get("key_piece");
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

  /**
   * Build Source object from a proto record.
   *
   * @param sourceProto protobuf representation of Source object
   * @return Source object
   */
  public static Source buildSourceFromProto(InputData.AttributionSource sourceProto)
      throws Exception {
    String appDst = sourceProto.getDestination();
    String webDst = sourceProto.getWebDestination();
    if (appDst.equals("") && webDst.equals("")) {
      throw new Exception("Provide at least destination or web_destination.");
    }

    if (sourceProto.getSourceEventId() <= 0) {
      throw new Exception("Provide source_event_id.");
    }

    Source.Builder builder = new Source.Builder();
    builder.setId(UUID.randomUUID().toString());
    builder.setEventId(sourceProto.getSourceEventId());
    builder.setEventTime(sourceProto.getEventTime());

    long expiry = PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
    if (sourceProto.getExpiry() > 0) {
      expiry = sourceProto.getExpiry();
    }
    long expiryTime =
        extractValidNumberInRange(
            expiry,
            PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
            PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    builder.setExpiryTime(sourceProto.getEventTime() + TimeUnit.SECONDS.toMillis(expiryTime));

    if (sourceProto.getPriority() > 0) {
      builder.setPriority(sourceProto.getPriority());
    }

    long inputInstallAttributionWindow = PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
    if (sourceProto.getInstallAttributionWindow() > 0) {
      inputInstallAttributionWindow = sourceProto.getInstallAttributionWindow();
    }
    long installAttributionWindow =
        extractValidNumberInRange(
            inputInstallAttributionWindow,
            PrivacyParams.MIN_INSTALL_ATTRIBUTION_WINDOW,
            PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW);
    builder.setInstallAttributionWindow(TimeUnit.SECONDS.toMillis(installAttributionWindow));

    long inputPostInstallExclusivityWindow = PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
    if (sourceProto.getPostInstallExclusivityWindow() > 0) {
      inputPostInstallExclusivityWindow = sourceProto.getPostInstallExclusivityWindow();
    }
    long installCooldownWindow =
        extractValidNumberInRange(
            inputPostInstallExclusivityWindow,
            PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
            PrivacyParams.MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
    builder.setInstallCooldownWindow(TimeUnit.SECONDS.toMillis(installCooldownWindow));

    Map<String, ListValue> filterData = sourceProto.getFilterDataMap();
    if (!filterData.isEmpty()) {
      JSONObject filterDataObj = new JSONObject();
      for (Entry<String, ListValue> data : filterData.entrySet()) {
        JSONArray dataList = new JSONArray();
        data.getValue().getValuesList().stream()
            .forEach(elem -> dataList.add(elem.getStringValue()));
        filterDataObj.put(data.getKey(), dataList);
      }
      if (!areValidAttributionFilters(filterDataObj)) {
        logger.info("Source filter_data is invalid.");
        throw new Exception("Unable to parse Source data.");
      }
      builder.setAggregateFilterData(filterDataObj.toString());
    }

    if (!appDst.isEmpty()) {
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
        throw new Exception("Unable to parse Source data.");
      }
      builder.setAppDestination(getBaseUri(appUri));
    }

    if (!webDst.isEmpty()) {
      URI webUri = URI.create(webDst);
      Optional<URI> topPrivateDomainAndScheme = topPrivateDomainAndScheme(webUri);
      if (!topPrivateDomainAndScheme.isPresent()) {
        logger.info("Unable to extract top private domain and scheme from web destination.");
        throw new Exception("Unable to parse Source data.");
      } else {
        builder.setWebDestination(topPrivateDomainAndScheme.get());
      }
    }

    List<InputData.AggregationKey> aggregationKeysList = sourceProto.getAggregationKeysList();
    if (aggregationKeysList.size() > 0) {
      JSONArray aggregationKeys = new JSONArray();
      aggregationKeysList.stream()
          .forEach(key -> aggregationKeys.add(new JSONObject(key.getAggregationKeyMapMap())));
      if (!areValidAggregationKeys(aggregationKeys)) {
        throw new Exception("Unable to parse Source data.");
      }
      builder.setAggregateSource(aggregationKeys.toString());
    }

    builder.setSourceType(SourceType.valueOf(sourceProto.getSourceType().toString()));
    builder.setAttributionMode(AttributionMode.TRUTHFULLY);
    builder.setEnrollmentId(sourceProto.getEnrollmentId());
    builder.setDedupKeys(sourceProto.getDedupKeysList());
    builder.setStatus(Status.ACTIVE);
    builder.setPublisher(URI.create(sourceProto.getPublisher()));
    builder.setRegistrant(URI.create(sourceProto.getRegistrant()));
    return builder.build();
  }
}
