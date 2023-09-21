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

import com.google.measurement.Source.AttributionMode;
import com.google.measurement.Source.SourceType;
import com.google.measurement.aggregation.AggregatableAttributionSource;
import com.google.measurement.util.UnsignedLong;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public final class SourceFixture {

  private SourceFixture() {}

  // Assume the field values in this Source.Builder have no relation to the field values in
  // {@link ValidSourceParams}
  public static Source.Builder getValidSourceBuilder() {
    return new Source.Builder()
        .setPublisher(ValidSourceParams.PUBLISHER)
        .setAppDestinations(ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setEnrollmentId(ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(ValidSourceParams.REGISTRANT)
        .setRegistrationOrigin(ValidSourceParams.REGISTRATION_ORIGIN);
  }

  // Assume the field values in this Source.Builder have no relation to the field values in
  // {@link ValidSourceParams}
  public static Source.Builder getMinimalValidSourceBuilder() {
    return new Source.Builder()
        .setPublisher(ValidSourceParams.PUBLISHER)
        .setAppDestinations(ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setEnrollmentId(ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(ValidSourceParams.REGISTRANT)
        .setRegistrationOrigin(ValidSourceParams.REGISTRATION_ORIGIN);
  }

  // Assume the field values in this Source have no relation to the field values in
  // {@link ValidSourceParams}
  public static Source getValidSource() {
    return new Source.Builder()
        .setId(UUID.randomUUID().toString())
        .setEventId(ValidSourceParams.SOURCE_EVENT_ID)
        .setPublisher(ValidSourceParams.PUBLISHER)
        .setAppDestinations(ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setWebDestinations(ValidSourceParams.WEB_DESTINATIONS)
        .setEnrollmentId(ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(ValidSourceParams.REGISTRANT)
        .setEventTime(ValidSourceParams.SOURCE_EVENT_TIME)
        .setExpiryTime(ValidSourceParams.EXPIRY_TIME)
        .setEventReportWindow(ValidSourceParams.EXPIRY_TIME)
        .setAggregatableReportWindow(ValidSourceParams.EXPIRY_TIME)
        .setPriority(ValidSourceParams.PRIORITY)
        .setSourceType(ValidSourceParams.SOURCE_TYPE)
        .setInstallAttributionWindow(ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
        .setInstallCooldownWindow(ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
        .setAttributionMode(ValidSourceParams.ATTRIBUTION_MODE)
        .setAggregateSource(ValidSourceParams.buildAggregateSource())
        .setFilterData(ValidSourceParams.buildFilterData())
        .setIsDebugReporting(true)
        .setRegistrationId(ValidSourceParams.REGISTRATION_ID)
        .setSharedAggregationKeys(ValidSourceParams.SHARED_AGGREGATE_KEYS)
        .setInstallTime(ValidSourceParams.INSTALL_TIME)
        .setPlatformAdId(ValidSourceParams.PLATFORM_AD_ID)
        .setDebugAdId(ValidSourceParams.DEBUG_AD_ID)
        .setRegistrationOrigin(ValidSourceParams.REGISTRATION_ORIGIN)
        .build();
  }

  public static class ValidSourceParams {
    public static final Long EXPIRY_TIME = 8640000010L;
    public static final Long PRIORITY = 100L;
    public static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(1L);
    public static final Long SOURCE_EVENT_TIME = 8640000000L;
    public static final List<URI> ATTRIBUTION_DESTINATIONS =
        List.of(URI.create("android-app://com.destination"));
    public static List<URI> WEB_DESTINATIONS = List.of(URI.create("https://destination.com"));
    public static final URI PUBLISHER = URI.create("android-app://com.publisher");
    public static final URI REGISTRANT = URI.create("android-app://com.registrant");
    public static final String ENROLLMENT_ID = "enrollment-id";
    public static final SourceType SOURCE_TYPE = Source.SourceType.EVENT;
    public static final Long INSTALL_ATTRIBUTION_WINDOW = 841839879274L;
    public static final Long INSTALL_COOLDOWN_WINDOW = 8418398274L;
    public static final UnsignedLong DEBUG_KEY = new UnsignedLong(7834690L);
    public static final AttributionMode ATTRIBUTION_MODE = Source.AttributionMode.TRUTHFULLY;
    public static final int AGGREGATE_CONTRIBUTIONS = 0;
    public static final String REGISTRATION_ID = "R1";
    public static final String SHARED_AGGREGATE_KEYS = "[\"key1\"]";
    public static final Long INSTALL_TIME = 100L;
    public static final String PLATFORM_AD_ID = "test-platform-ad-id";
    public static final String DEBUG_AD_ID = "test-debug-ad-id";
    public static final URI REGISTRATION_ORIGIN =
        WebUtil.validUri("https://subdomain.example.test");

    public static final String buildAggregateSource() {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put("campaignCounts", "0x456");
      jsonObject.put("geoValue", "0x159");
      return jsonObject.toString();
    }

    public static final String buildFilterData() {
      JSONObject filterMap = new JSONObject();
      JSONArray subDomain = new JSONArray();
      subDomain.add("electronics.megastore");

      JSONArray product = new JSONArray();
      product.add("1234");
      product.add("2345");
      filterMap.put("conversion_subdomain", subDomain);
      filterMap.put("product", product);
      return filterMap.toString();
    }

    public static final AggregatableAttributionSource buildAggregatableAttributionSource() {
      TreeMap<String, BigInteger> aggregateSourceMap = new TreeMap<>();
      aggregateSourceMap.put("5", new BigInteger("345"));
      return new AggregatableAttributionSource.Builder()
          .setAggregatableSource(aggregateSourceMap)
          .setFilterMap(
              new FilterMap.Builder()
                  .setAttributionFilterMap(
                      Map.of(
                          "product", List.of("1234", "4321"),
                          "conversion_subdomain", List.of("electronics.megastore")))
                  .build())
          .build();
    }
  }

  public static ReportSpec getValidReportSpecCountBased() {
    String triggerSpecsString =
        "[{\"trigger_data\": [1, 2],"
            + "\"event_report_windows\": { "
            + "\"start_time\": \"0\", "
            + String.format(
                "\"end_times\": [%s, %s]}, ", TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7))
            + "\"summary_window_operator\": \"count\", "
            + "\"summary_buckets\": [1, 2]}]";
    return new ReportSpec(triggerSpecsString, 3, getValidSource());
  }

  public static ReportSpec getValidReportSpecCountBasedWithFewerState() {
    String triggerSpecsString =
        "[{\"trigger_data\": [1],"
            + "\"event_report_windows\": { "
            + "\"start_time\": \"0\", "
            + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toMillis(2))
            + "\"summary_window_operator\": \"count\", "
            + "\"summary_buckets\": [1]}]";
    return new ReportSpec(triggerSpecsString, 1, getValidSource());
  }

  public static ReportSpec getValidReportSpecValueSum() {
    return new ReportSpec(getTriggerSpecValueSumEncodedJSONValidBaseline(), 3, getValidSource());
  }

  public static Source getValidSourceWithFlexEventReport() {

    return getValidSourceBuilder()
        .setAttributedTriggers(new ArrayList<>())
        .setFlexEventReportSpec(getValidReportSpecCountBased())
        .setMaxEventLevelReports(getValidReportSpecCountBased().getMaxReports())
        .build();
  }

  public static Source getValidSourceWithFlexEventReportWithFewerState() {
    return getValidSourceBuilder()
        .setAttributedTriggers(new ArrayList<>())
        .setFlexEventReportSpec(getValidReportSpecCountBasedWithFewerState())
        .setMaxEventLevelReports(getValidReportSpecCountBasedWithFewerState().getMaxReports())
        .build();
  }

  public static Source.Builder getValidFullSourceBuilderWithFlexEventReportValueSum() {
    return getValidSourceBuilder()
        .setAttributedTriggers(new ArrayList<>())
        .setFlexEventReportSpec(getValidReportSpecValueSum());
  }

  public static Source.Builder getValidSourceBuilderWithFlexEventReportValueSum() {
    ReportSpec reportSpec = getValidReportSpecValueSum();
    return getMinimalValidSourceBuilder()
        .setId(UUID.randomUUID().toString())
        .setFlexEventReportSpec(reportSpec)
        .setMaxEventLevelReports(reportSpec.getMaxReports())
        .setAttributedTriggers(null);
  }

  public static Source.Builder getValidSourceBuilderWithFlexEventReport() {
    ReportSpec reportSpec = getValidReportSpecCountBased();
    return getMinimalValidSourceBuilder()
        .setId(UUID.randomUUID().toString())
        .setFlexEventReportSpec(reportSpec)
        .setMaxEventLevelReports(reportSpec.getMaxReports())
        .setAttributedTriggers(null);
  }

  public static String getTriggerSpecCountEncodedJSONValidBaseline() {
    return "[{\"trigger_data\": [1, 2, 3],"
        + "\"event_report_windows\": { "
        + "\"start_time\": \"0\", "
        + String.format(
            "\"end_times\": [%s, %s, %s]}, ",
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30))
        + "\"summary_window_operator\": \"count\", "
        + "\"summary_buckets\": [1, 2, 3, 4]}]";
  }

  public static String getTriggerSpecValueSumEncodedJSONValidBaseline() {
    return "[{\"trigger_data\": [1, 2],"
        + "\"event_report_windows\": { "
        + "\"start_time\": \"0\", "
        + String.format(
            "\"end_times\": [%s, %s]}, ", TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7))
        + "\"summary_window_operator\": \"value_sum\", "
        + "\"summary_buckets\": [10, 100]}]";
  }

  public static String getTriggerSpecValueCountJSONTwoTriggerSpecs() {
    return "[{\"trigger_data\": [1, 2, 3],"
        + "\"event_report_windows\": { "
        + "\"start_time\": \"0\", "
        + String.format(
            "\"end_times\": [%s, %s, %s]}, ",
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30))
        + "\"summary_window_operator\": \"count\", "
        + "\"summary_buckets\": [1, 2, 3, 4]}, "
        + "{\"trigger_data\": [4, 5, 6, 7],"
        + "\"event_report_windows\": { "
        + "\"start_time\": \"0\", "
        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toMillis(3))
        + "\"summary_window_operator\": \"count\", "
        + "\"summary_buckets\": [1,5,7]} "
        + "]";
  }
}
