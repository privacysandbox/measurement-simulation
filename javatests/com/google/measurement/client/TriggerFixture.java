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

import com.google.measurement.client.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.client.aggregation.AggregateTriggerData;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.FakeFlagsFactory;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;

public final class TriggerFixture {
  private TriggerFixture() {}

  // Assume the field values in this Trigger.Builder have no relation to the field values in
  // {@link ValidTriggerParams}
  public static Trigger.Builder getValidTriggerBuilder() {
    return new Trigger.Builder()
        .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
        .setEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
        .setRegistrant(ValidTriggerParams.REGISTRANT)
        .setAggregatableSourceRegistrationTimeConfig(
            ValidTriggerParams.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
        .setRegistrationOrigin(ValidTriggerParams.REGISTRATION_ORIGIN);
  }

  // Assume the field values in this Trigger have no relation to the field values in
  // {@link ValidTriggerParams}
  public static Trigger getValidTrigger() {
    return new Trigger.Builder()
        .setId(UUID.randomUUID().toString())
        .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
        .setEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
        .setRegistrant(ValidTriggerParams.REGISTRANT)
        .setTriggerTime(ValidTriggerParams.TRIGGER_TIME)
        .setEventTriggers(ValidTriggerParams.EVENT_TRIGGERS)
        .setAggregateTriggerData(ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
        .setAggregateValuesString(ValidTriggerParams.AGGREGATE_VALUES_STRING)
        .setFilters(ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
        .setNotFilters(ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
        .setAttributionConfig(ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
        .setAdtechBitMapping(ValidTriggerParams.X_NETWORK_KEY_MAPPING)
        .setRegistrationOrigin(ValidTriggerParams.REGISTRATION_ORIGIN)
        .setAggregationCoordinatorOrigin(ValidTriggerParams.AGGREGATION_COORDINATOR_ORIGIN)
        .setAggregatableSourceRegistrationTimeConfig(
            ValidTriggerParams.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
        .setTriggerContextId(ValidTriggerParams.TRIGGER_CONTEXT_ID)
        .setAttributionScopesString(ValidTriggerParams.ATTRIBUTION_SCOPES)
        .setAggregatableFilteringIdMaxBytes(ValidTriggerParams.AGGREGATABLE_FILTERING_ID_MAX_BYTES)
        .setAggregateDebugReportingString(ValidTriggerParams.AGGREGATE_DEBUG_REPORT)
        .build();
  }

  public static class ValidTriggerParams {
    public static final Long TRIGGER_TIME = 8640000000L;
    public static final Uri ATTRIBUTION_DESTINATION = Uri.parse("android-app://com.destination");
    public static final Uri REGISTRANT = Uri.parse("android-app://com.registrant");
    public static final String ENROLLMENT_ID = "enrollment-id";
    public static final String TOP_LEVEL_FILTERS_JSON_STRING =
        "[{\n"
            + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
            + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
            + "}]\n";

    public static final String TOP_LEVEL_NOT_FILTERS_JSON_STRING =
        "[{\"geo\": [], \"source_type\": [\"event\"]}]";

    public static final String EVENT_TRIGGERS =
        "[\n"
            + "{\n"
            + "  \"trigger_data\": \"5\",\n"
            + "  \"priority\": \"123\",\n"
            + "  \"filters\": [{\n"
            + "    \"source_type\": [\"navigation\"],\n"
            + "    \"key_1\": [\"value_1\"] \n"
            + "   }]\n"
            + "},\n"
            + "{\n"
            + "  \"trigger_data\": \"0\",\n"
            + "  \"priority\": \"124\",\n"
            + "  \"deduplication_key\": \"101\",\n"
            + "  \"filters\": [{\n"
            + "     \"source_type\": [\"event\"]\n"
            + "   }]\n"
            + "}\n"
            + "]\n";

    public static final String AGGREGATE_TRIGGER_DATA =
        "["
            + "{"
            + "\"key_piece\":\"0xA80\","
            + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
            + "}"
            + "]";

    public static final String AGGREGATE_VALUES_STRING =
        "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";

    public static final UnsignedLong DEBUG_KEY = new UnsignedLong(27836L);

    public static final AttributionConfig ATTRIBUTION_CONFIG =
        new AttributionConfig.Builder()
            .setExpiry(604800L)
            .setSourceAdtech("AdTech1-Ads")
            .setSourcePriorityRange(new Pair<>(100L, 1000L))
            .setSourceFilters(
                Collections.singletonList(
                    new FilterMap.Builder()
                        .setAttributionFilterMap(
                            Map.of(
                                "campaign_type",
                                Collections.singletonList("install"),
                                "source_type",
                                Collections.singletonList("navigation")))
                        .build()))
            .setPriority(99L)
            .setExpiry(604800L)
            .setFilterData(
                new FilterMap.Builder()
                    .setAttributionFilterMap(
                        Map.of("campaign_type", Collections.singletonList("install")))
                    .build())
            .build();

    public static final String ATTRIBUTION_CONFIGS_STRING =
        new JSONArray(
                Collections.singletonList(
                    ATTRIBUTION_CONFIG.serializeAsJson(FakeFlagsFactory.getFlagsForTest())))
            .toString();

    public static final String X_NETWORK_KEY_MAPPING =
        "{" + "\"AdTechA-enrollment_id\": \"0x1\"," + "\"AdTechB-enrollment_id\": \"0x2\"" + "}";
    public static final Uri REGISTRATION_ORIGIN =
        WebUtil.validUri("https://subdomain.example.test");

    public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
        WebUtil.validUri("https://coordinator.example.test");

    public static final Trigger.SourceRegistrationTimeConfig
        AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG = Trigger.SourceRegistrationTimeConfig.INCLUDE;

    public static final String PLATFORM_AD_ID = "test-platform-ad-id";
    public static final String TRIGGER_CONTEXT_ID = "test-trigger-context-id";
    public static final String ATTRIBUTION_SCOPES = "[\"1\"]";
    public static final int AGGREGATABLE_FILTERING_ID_MAX_BYTES = 1;
    public static final String AGGREGATE_DEBUG_REPORT =
        "{\"key_piece\":\"0x222\","
            + "\"debug_data\":["
            + "{"
            + "\"types\": [\"trigger-aggregate-insufficient-budget\", "
            + "\"trigger-aggregate-deduplicated\"],"
            + "\"key_piece\": \"0x333\","
            + "\"value\": 333"
            + "},"
            + "{"
            + "\"types\": [\"trigger-aggregate-report-window-passed\", "
            + "\"trigger-event-low-priority\"],"
            + "\"key_piece\": \"0x444\","
            + "\"value\": 444"
            + "},"
            + "{"
            + "\"types\": [\"unspecified\"],"
            + "\"key_piece\": \"0x555\","
            + "\"value\": 555"
            + "}"
            + "],"
            + "\"aggregation_coordinator_origin\":\"https://aws.example\"}";
  }
}
