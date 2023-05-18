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
import com.google.measurement.aggregation.AggregateTriggerData;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.simple.JSONArray;

public final class TriggerFixture {

  private TriggerFixture() {}

  // Assume the field values in this Trigger.Builder have no relation to the field values in
  // {@link ValidTriggerParams}
  public static Trigger.Builder getValidTriggerBuilder() {
    return new Trigger.Builder()
        .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
        .setEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
        .setRegistrant(ValidTriggerParams.REGISTRANT);
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
        .setAggregateValues(ValidTriggerParams.AGGREGATE_VALUES)
        .setFilters(ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
        .setNotFilters(ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
        .setAttributionConfig(ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
        .setAdtechBitMapping(ValidTriggerParams.X_NETWORK_KEY_MAPPING)
        .build();
  }

  public static class ValidTriggerParams {
    public static final Long TRIGGER_TIME = 8640000000L;
    public static final URI ATTRIBUTION_DESTINATION = URI.create("android-app://com.destination");
    public static final URI REGISTRANT = URI.create("android-app://com.registrant");
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
            + "  \"trigger_data\": 5,\n"
            + "  \"priority\": 123,\n"
            + "  \"filters\": [{\n"
            + "    \"source_type\": [\"navigation\"],\n"
            + "    \"key_1\": [\"value_1\"] \n"
            + "   }]\n"
            + "},\n"
            + "{\n"
            + "  \"trigger_data\": 0,\n"
            + "  \"priority\": 124,\n"
            + "  \"deduplication_key\": 101,\n"
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
    public static final String AGGREGATE_VALUES =
        "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";
    public static final Long DEBUG_KEY = new Long(27836L);

    public static final AttributionConfig ATTRIBUTION_CONFIG =
        new AttributionConfig.Builder()
            .setExpiry(604800L)
            .setSourceAdtech("AdTech1-Ads")
            .setSourcePriorityRange(Map.of(100L, 1000L))
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
                Collections.singletonList(
                    new FilterMap.Builder()
                        .setAttributionFilterMap(
                            Map.of("campaign_type", Collections.singletonList("install")))
                        .build()))
            .build();

    public static final String ATTRIBUTION_CONFIGS_STRING = buildAttributionConfigsString();
    public static final String X_NETWORK_KEY_MAPPING =
        "{" + "\"AdTechA-enrollment_id\": \"0x1\"," + "\"AdTechB-enrollment_id\": \"0x2\"" + "}";

    public static final AggregatableAttributionTrigger buildAggregatableAttributionTrigger() {
      final FilterMap filter =
          new FilterMap.Builder()
              .setAttributionFilterMap(
                  Map.of(
                      "product",
                      List.of("1234", "4321"),
                      "conversion_subdomain",
                      List.of("electronics.megastore")))
              .build();
      return new AggregatableAttributionTrigger.Builder()
          .setValues(Map.of("x", 1))
          .setTriggerData(
              List.of(
                  new AggregateTriggerData.Builder()
                      .setKey(BigInteger.ONE)
                      .setSourceKeys(new HashSet(Set.of("sourceKey")))
                      .setFilterSet(List.of(filter))
                      .build()))
          .build();
    }

    public static final String buildAttributionConfigsString() {
      JSONArray configStrings = new JSONArray();
      configStrings.add(
          Collections.singletonList(ValidTriggerParams.ATTRIBUTION_CONFIG.serializeAsJson()));
      return configStrings.toString();
    }
  }
}
