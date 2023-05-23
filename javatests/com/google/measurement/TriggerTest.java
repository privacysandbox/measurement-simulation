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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.measurement.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.aggregation.AggregateTriggerData;
import com.google.measurement.util.UnsignedLong;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class TriggerTest {
  private static final String TOP_LEVEL_FILTERS_JSON_STRING =
      "[{\n"
          + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
          + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
          + "}]\n";
  private static final String TOP_LEVEL_FILTERS_JSON_STRING_X =
      "[{\n"
          + "  \"key_1x\": [\"value_1\", \"value_2\"],\n"
          + "  \"key_2x\": [\"value_1\", \"value_2\"]\n"
          + "}]\n";
  private static final String EVENT_TRIGGERS =
      "[\n"
          + "{\n"
          + "  \"trigger_data\": 1,\n"
          + "  \"priority\": 345678,\n"
          + "  \"deduplication_key\": 2345678,\n"
          + "  \"filters\": [{\n"
          + "    \"source_type\": [\"navigation\"],\n"
          + "    \"key_1\": [\"value_1\"] \n"
          + "   }]\n"
          + "}"
          + "]\n";
  private static final UnsignedLong DEBUG_KEY = new UnsignedLong(2367372L);
  private static final URI APP_DESTINATION = URI.create("android-app://com.android.app");
  private static final URI APP_DESTINATION_WITH_PATH =
      URI.create("android-app://com.android.app/with/path");
  private static final URI WEB_DESTINATION = WebUtil.validUri("https://example.test");
  private static final URI WEB_DESTINATION_WITH_PATH =
      WebUtil.validUri("https://example.test/with/path");
  private static final URI WEB_DESTINATION_WITH_SUBDOMAIN =
      WebUtil.validUri("https://subdomain.example.test");
  private static final URI WEB_DESTINATION_WITH_SUBDOMAIN_PATH_QUERY_FRAGMENT =
      WebUtil.validUri("https://subdomain.example.test/with/path?query=0#fragment");
  private static final URI WEB_DESTINATION_INVALID = URI.create("https://example.notatld");

  @Test
  public void testEqualsPass() throws ParseException {
    assertEquals(
        TriggerFixture.getValidTriggerBuilder().build(),
        TriggerFixture.getValidTriggerBuilder().build());
    JSONArray aggregateTriggerDatas = new JSONArray();
    JSONObject aggregateTriggerData1 = new JSONObject();
    aggregateTriggerData1.put("key_piece", "0x400");
    aggregateTriggerData1.put("source_keys", Arrays.asList("campaignCounts"));
    JSONObject aggregateTriggerData2 = new JSONObject();
    aggregateTriggerData2.put("key_piece", "0xA80");
    aggregateTriggerData2.put("source_keys", Arrays.asList("geoValue", "nonMatchingKey"));
    aggregateTriggerDatas.add(aggregateTriggerData1);
    aggregateTriggerDatas.add(aggregateTriggerData2);
    JSONObject values = new JSONObject();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    JSONObject adtechBitMapping = new JSONObject();
    adtechBitMapping.put("AdTechA-enrollment_id", "0x1");
    String debugJoinKey = "SAMPLE_DEBUG_JOIN_KEY";
    assertEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setEnrollmentId("enrollment-id")
            .setAttributionDestination(URI.create("https://example.test/aD"))
            .setDestinationType(EventSurfaceType.WEB)
            .setId("1")
            .setEventTriggers(EVENT_TRIGGERS)
            .setTriggerTime(5L)
            .setIsDebugReporting(true)
            .setAdIdPermission(true)
            .setArDebugPermission(true)
            .setStatus(Trigger.Status.PENDING)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateTriggerData(aggregateTriggerDatas.toString())
            .setAggregateValues(values.toString())
            .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .setDebugKey(DEBUG_KEY)
            .setAggregatableAttributionTrigger(
                TriggerFixture.getValidTrigger().getAggregatableAttributionTrigger().orElse(null))
            .setAttributionConfig(createAttributionConfigJSONArray().toString())
            .setAdtechBitMapping(adtechBitMapping.toString())
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setEnrollmentId("enrollment-id")
            .setAttributionDestination(URI.create("https://example.test/aD"))
            .setDestinationType(EventSurfaceType.WEB)
            .setId("1")
            .setEventTriggers(EVENT_TRIGGERS)
            .setTriggerTime(5L)
            .setIsDebugReporting(true)
            .setAdIdPermission(true)
            .setArDebugPermission(true)
            .setStatus(Trigger.Status.PENDING)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateTriggerData(aggregateTriggerDatas.toString())
            .setAggregateValues(values.toString())
            .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .setDebugKey(DEBUG_KEY)
            .setAggregatableAttributionTrigger(
                TriggerFixture.getValidTrigger().getAggregatableAttributionTrigger().orElse(null))
            .setAttributionConfig(createAttributionConfigJSONArray().toString())
            .setAdtechBitMapping(adtechBitMapping.toString())
            .build());
  }

  @Test
  public void testEqualsFail() {
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(URI.create("https://1.test"))
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(URI.create("https://2.test"))
            .build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setDestinationType(EventSurfaceType.APP).build(),
        TriggerFixture.getValidTriggerBuilder().setDestinationType(EventSurfaceType.WEB).build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setEnrollmentId("enrollment-id-1").build(),
        TriggerFixture.getValidTriggerBuilder().setEnrollmentId("enrollment-id-2").build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setEventTriggers("a").build(),
        TriggerFixture.getValidTriggerBuilder().setEventTriggers("b").build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setTriggerTime(1L).build(),
        TriggerFixture.getValidTriggerBuilder().setTriggerTime(2L).build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setStatus(Trigger.Status.PENDING).build(),
        TriggerFixture.getValidTriggerBuilder().setStatus(Trigger.Status.IGNORED).build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setRegistrant(URI.create("android-app://com.example.xyz"))
            .build());
    JSONArray aggregateTriggerDataList1 = new JSONArray();
    JSONObject aggregateTriggerData1 = new JSONObject();
    aggregateTriggerData1.put("key_piece", "0x400");
    aggregateTriggerData1.put("source_keys", Arrays.asList("campaignCounts"));
    aggregateTriggerDataList1.add(aggregateTriggerData1);
    JSONArray aggregateTriggerDataList2 = new JSONArray();
    JSONObject aggregateTriggerData2 = new JSONObject();
    aggregateTriggerData2.put("key_piece", "0xA80");
    aggregateTriggerData2.put("source_keys", Arrays.asList("geoValue", "nonMatchingKey"));
    aggregateTriggerDataList2.add(aggregateTriggerData2);
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(aggregateTriggerDataList1.toString())
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(aggregateTriggerDataList2.toString())
            .build());
    JSONObject values1 = new JSONObject();
    values1.put("campaignCounts", 32768);
    JSONObject values2 = new JSONObject();
    values2.put("geoValue", 1664);
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setAggregateValues(values1.toString()).build(),
        TriggerFixture.getValidTriggerBuilder().setAggregateValues(values2.toString()).build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder().setFilters(TOP_LEVEL_FILTERS_JSON_STRING).build(),
        TriggerFixture.getValidTriggerBuilder()
            .setFilters(TOP_LEVEL_FILTERS_JSON_STRING_X)
            .build());
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING_X)
            .build());
    JSONArray attributionConfigList1 = new JSONArray();
    JSONObject attributionConfig1 = new JSONObject();
    attributionConfig1.put("source_network", "AdTech1-Ads");
    JSONObject sourcePriorityRange1 = new JSONObject();
    sourcePriorityRange1.put("start", 100);
    sourcePriorityRange1.put("end", 1000);
    attributionConfig1.put("source_priority_range", sourcePriorityRange1);
    JSONObject sourceFilters1 = new JSONObject();
    sourceFilters1.put("campaign_type", Arrays.asList("install"));
    sourceFilters1.put("source_type", Arrays.asList("navigation"));
    attributionConfig1.put("source_filters", sourceFilters1);
    attributionConfig1.put("priority", "99");
    attributionConfig1.put("expiry", "604800");
    JSONObject filterData1 = new JSONObject();
    filterData1.put("campaign_type", Arrays.asList("install"));
    attributionConfig1.put("filter_data", filterData1);
    attributionConfigList1.add(attributionConfig1);
    JSONArray attributionConfigList2 = new JSONArray();
    JSONObject attributionConfig2 = new JSONObject();
    attributionConfig2.put("source_network", "AdTech2-Ads");
    JSONObject sourcePriorityRange2 = new JSONObject();
    sourcePriorityRange2.put("start", 100);
    sourcePriorityRange2.put("end", 1000);
    attributionConfig1.put("source_priority_range", sourcePriorityRange2);
    JSONObject sourceFilters2 = new JSONObject();
    sourceFilters2.put("campaign_type", Arrays.asList("install"));
    sourceFilters2.put("source_type", Arrays.asList("navigation"));
    attributionConfig2.put("source_filters", sourceFilters2);
    attributionConfig2.put("priority", "99");
    attributionConfig2.put("expiry", "604800");
    JSONObject filterData2 = new JSONObject();
    filterData1.put("campaign_type", Arrays.asList("install"));
    attributionConfig2.put("filter_data", filterData2);
    attributionConfigList2.add(attributionConfig2);
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionConfig(attributionConfigList1.toString())
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionConfig(attributionConfigList2.toString())
            .build());
    JSONObject adtechBitMapping1 = new JSONObject();
    adtechBitMapping1.put("AdTechA-enrollment_id", "0x1");
    JSONObject adtechBitMapping2 = new JSONObject();
    adtechBitMapping2.put("AdTechA-enrollment_id", "0x2");
    assertNotEquals(
        TriggerFixture.getValidTriggerBuilder()
            .setAdtechBitMapping(adtechBitMapping1.toString())
            .build(),
        TriggerFixture.getValidTriggerBuilder()
            .setAdtechBitMapping(adtechBitMapping2.toString())
            .build());
  }

  @Test
  public void testHashCode_equals() {
    final Trigger trigger1 = TriggerFixture.getValidTriggerBuilder().build();
    final Trigger trigger2 = TriggerFixture.getValidTriggerBuilder().build();
    final Set<Trigger> triggerSet1 = Set.of(trigger1);
    final Set<Trigger> triggerSet2 = Set.of(trigger2);
    assertEquals(trigger1.hashCode(), trigger2.hashCode());
    assertEquals(trigger1, trigger2);
    assertEquals(triggerSet1, triggerSet2);
  }

  @Test
  public void testAggregatableAttributionTrigger() throws Exception {
    final Map<String, Integer> values = Map.of("foo", 93);
    final List<AggregateTriggerData> triggerData =
        List.of(new AggregateTriggerData.Builder().build());
    final AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setValues(values)
            .setTriggerData(triggerData)
            .build();
    final Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregatableAttributionTrigger(attributionTrigger)
            .build();
    Optional<AggregatableAttributionTrigger> aggregatableAttributionTrigger =
        trigger.getAggregatableAttributionTrigger();
    assertTrue(aggregatableAttributionTrigger.isPresent());
    assertNotNull(aggregatableAttributionTrigger.get().getTriggerData());
    assertEquals(values, aggregatableAttributionTrigger.get().getValues());
    assertEquals(triggerData, aggregatableAttributionTrigger.get().getTriggerData());
  }

  @Test
  public void testParseAggregateTrigger() throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    JSONArray sourceKeys1 = new JSONArray();
    sourceKeys1.add("campaignCounts");

    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", sourceKeys1);
    jsonObject1.put("filters", createFilterJSONArray());
    jsonObject1.put("not_filters", createFilterJSONArray());
    jsonObject1.put("x_network_data", createXNetworkDataJSONObject());
    JSONObject jsonObject2 = new JSONObject();
    JSONArray sourceKeys2 = new JSONArray();
    sourceKeys2.add("geoValue");
    sourceKeys2.add("noMatch");
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", sourceKeys2);
    jsonObject1.put("x_network_data", createXNetworkDataJSONObject());
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    JSONObject values = new JSONObject();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    JSONArray aggregateDedupKeys = new JSONArray();
    JSONObject dedupKeyJsonObject1 = new JSONObject();
    dedupKeyJsonObject1.put("deduplication_key", "10");
    dedupKeyJsonObject1.put("filters", createFilterJSONArray());
    dedupKeyJsonObject1.put("not_filters", createFilterJSONArray());
    JSONObject dedupKeyJsonObject2 = new JSONObject();
    dedupKeyJsonObject2.put("deduplication_key", "11");
    dedupKeyJsonObject2.put("filters", createFilterJSONArray());
    dedupKeyJsonObject2.put("not_filters", createFilterJSONArray());
    aggregateDedupKeys.add(dedupKeyJsonObject1);
    aggregateDedupKeys.add(dedupKeyJsonObject2);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues(values.toString())
            .setAggregateDeduplicationKeys(aggregateDedupKeys.toString())
            .build();
    Optional<AggregatableAttributionTrigger> aggregatableAttributionTrigger =
        trigger.getAggregatableAttributionTrigger();
    assertTrue(aggregatableAttributionTrigger.isPresent());
    AggregatableAttributionTrigger aggregateTrigger = aggregatableAttributionTrigger.get();
    assertEquals(2, aggregateTrigger.getTriggerData().size());
    assertEquals(1, aggregateTrigger.getTriggerData().get(0).getSourceKeys().size());
    assertEquals(1024, aggregateTrigger.getTriggerData().get(0).getKey().intValue());
    assertTrue(aggregateTrigger.getTriggerData().get(0).getSourceKeys().contains("campaignCounts"));
    assertTrue(aggregateTrigger.getTriggerData().get(0).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(aggregateTrigger.getTriggerData().get(0).getNotFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertEquals(
        10L,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getXNetworkData()
            .orElse(null)
            .getKeyOffset()
            .orElse(null)
            .getValue()
            .longValue());
    assertEquals(2688, aggregateTrigger.getTriggerData().get(1).getKey().intValue());
    assertEquals(2, aggregateTrigger.getTriggerData().get(1).getSourceKeys().size());
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("geoValue"));
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("noMatch"));
    assertEquals(2, aggregateTrigger.getValues().size());
    assertEquals(32768, aggregateTrigger.getValues().get("campaignCounts").intValue());
    assertEquals(1664, aggregateTrigger.getValues().get("geoValue").intValue());
    assertEquals(
        10L,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getXNetworkData()
            .orElse(null)
            .getKeyOffset()
            .orElse(null)
            .getValue()
            .longValue());
    assertTrue(aggregateTrigger.getAggregateDeduplicationKeys().isPresent());
    assertEquals(2, aggregateTrigger.getAggregateDeduplicationKeys().get().size());
    assertEquals(
        new UnsignedLong(10L),
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(0).getDeduplicationKey());
    assertTrue(
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(0).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getNotFilterSet()
            .isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(1).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getNotFilterSet()
            .isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
  }

  @Test
  public void testParseAggregateTrigger_withNumericStrings() throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    JSONArray sourceKeys1 = new JSONArray();
    sourceKeys1.add("campaignCounts");

    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", sourceKeys1);
    jsonObject1.put("filters", createFilterJSONArray());
    jsonObject1.put("not_filters", createFilterJSONArray());
    jsonObject1.put("x_network_data", createXNetworkDataJSONObject());
    JSONObject jsonObject2 = new JSONObject();
    JSONArray sourceKeys2 = new JSONArray();
    sourceKeys2.add("geoValue");
    sourceKeys2.add("noMatch");
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", sourceKeys2);
    jsonObject1.put("x_network_data", createXNetworkDataJSONObject());
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    JSONObject values = new JSONObject();
    values.put("campaignCounts", "32768");
    values.put("geoValue", "1664");
    JSONArray aggregateDedupKeys = new JSONArray();
    JSONObject dedupKeyJsonObject1 = new JSONObject();
    dedupKeyJsonObject1.put("deduplication_key", "10");
    dedupKeyJsonObject1.put("filters", createFilterJSONArray());
    dedupKeyJsonObject1.put("not_filters", createFilterJSONArray());
    JSONObject dedupKeyJsonObject2 = new JSONObject();
    dedupKeyJsonObject2.put("deduplication_key", "11");
    dedupKeyJsonObject2.put("filters", createFilterJSONArray());
    dedupKeyJsonObject2.put("not_filters", createFilterJSONArray());
    aggregateDedupKeys.add(dedupKeyJsonObject1);
    aggregateDedupKeys.add(dedupKeyJsonObject2);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues(values.toString())
            .setAggregateDeduplicationKeys(aggregateDedupKeys.toString())
            .build();
    Optional<AggregatableAttributionTrigger> aggregatableAttributionTrigger =
        trigger.getAggregatableAttributionTrigger();
    assertTrue(aggregatableAttributionTrigger.isPresent());
    AggregatableAttributionTrigger aggregateTrigger = aggregatableAttributionTrigger.get();
    assertEquals(2, aggregateTrigger.getTriggerData().size());
    assertEquals(1, aggregateTrigger.getTriggerData().get(0).getSourceKeys().size());
    assertEquals(1024, aggregateTrigger.getTriggerData().get(0).getKey().intValue());
    assertTrue(aggregateTrigger.getTriggerData().get(0).getSourceKeys().contains("campaignCounts"));
    assertTrue(aggregateTrigger.getTriggerData().get(0).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(aggregateTrigger.getTriggerData().get(0).getNotFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertEquals(
        10L,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getXNetworkData()
            .orElse(null)
            .getKeyOffset()
            .orElse(null)
            .getValue()
            .longValue());
    assertEquals(2688, aggregateTrigger.getTriggerData().get(1).getKey().intValue());
    assertEquals(2, aggregateTrigger.getTriggerData().get(1).getSourceKeys().size());
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("geoValue"));
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("noMatch"));
    assertEquals(2, aggregateTrigger.getValues().size());
    assertEquals(32768, aggregateTrigger.getValues().get("campaignCounts").intValue());
    assertEquals(1664, aggregateTrigger.getValues().get("geoValue").intValue());
    assertEquals(
        10L,
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getXNetworkData()
            .orElse(null)
            .getKeyOffset()
            .orElse(null)
            .getValue()
            .longValue());
    assertTrue(aggregateTrigger.getAggregateDeduplicationKeys().isPresent());
    assertEquals(2, aggregateTrigger.getAggregateDeduplicationKeys().get().size());
    assertEquals(
        new UnsignedLong(10L),
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(0).getDeduplicationKey());
    assertTrue(
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(0).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getNotFilterSet()
            .isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(0)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger.getAggregateDeduplicationKeys().get().get(1).getFilterSet().isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
    assertTrue(
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getNotFilterSet()
            .isPresent());
    assertEquals(
        2,
        aggregateTrigger
            .getAggregateDeduplicationKeys()
            .get()
            .get(1)
            .getNotFilterSet()
            .get()
            .get(0)
            .getAttributionFilterMap()
            .size());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_appDestination() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(APP_DESTINATION)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    assertEquals(APP_DESTINATION, trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_webDestination() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WEB_DESTINATION)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithSubdomain() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WEB_DESTINATION_WITH_SUBDOMAIN)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithPath() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WEB_DESTINATION_WITH_PATH)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithSubdomainPathQueryFrag() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WEB_DESTINATION_WITH_SUBDOMAIN_PATH_QUERY_FRAGMENT)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void testGetAttributionDestinationBaseUri_invalidWebDestination() {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WEB_DESTINATION_INVALID)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    assertNull(trigger.getAttributionDestinationBaseUri());
  }

  @Test
  public void parseEventTriggers() throws ParseException {
    // setup
    JSONParser parser = new JSONParser();
    JSONObject filtersMap1 =
        (JSONObject)
            parser.parse(
                "{\n"
                    + "    \"filter_key_1\": [\"filter_value_1\"], \n"
                    + "    \"filter_key_2\": [\"filter_value_2\"] \n"
                    + "   }");
    JSONObject notFiltersMap1 =
        (JSONObject)
            parser.parse(
                "{\n"
                    + "    \"not_filter_key_1\": [\"not_filter_value_1\", "
                    + "\"not_filter_value_2\"]"
                    + "   }");
    JSONObject notFiltersMap2 =
        (JSONObject) parser.parse("{\n" + "    \"key_1\": [\"value_1_x\"] \n" + "   }");
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"filter_key_1\": [\"filter_value_1\"], \n"
                    + "    \"filter_key_2\": [\"filter_value_2\"] \n"
                    + "   }],\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"not_filter_key_1\": [\"not_filter_value_1\", "
                    + "\"not_filter_value_2\"]"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"key_1\": [\"value_1_x\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    EventTrigger eventTrigger1 =
        new EventTrigger.Builder(new UnsignedLong(2L))
            .setTriggerPriority(2L)
            .setDedupKey(new UnsignedLong(2L))
            .setFilterSet(List.of(new FilterMap.Builder().buildFilterData(filtersMap1).build()))
            .setNotFilterSet(
                List.of(new FilterMap.Builder().buildFilterData(notFiltersMap1).build()))
            .build();
    EventTrigger eventTrigger2 =
        new EventTrigger.Builder(new UnsignedLong(3L))
            .setTriggerPriority(3L)
            .setDedupKey(new UnsignedLong(3L))
            .setNotFilterSet(
                List.of(new FilterMap.Builder().buildFilterData(notFiltersMap2).build()))
            .build();
    // Action
    List<EventTrigger> actualEventTriggers = trigger.parseEventTriggers();
    // Assertion
    assertEquals(Arrays.asList(eventTrigger1, eventTrigger2), actualEventTriggers);
  }

  @Test
  public void parseAdtechBitMapping_nonEmpty_parseSuccess() throws ParseException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setAdtechBitMapping(
                "{\"AdTechA-enrollment_id\": \"0x1\"," + "\"AdTechB-enrollment_id\": \"0x2\"}")
            .build();
    Map<Object, BigInteger> adtechMapping = trigger.parseAdtechKeyMapping();
    assertEquals(2, adtechMapping.size());
    BigInteger adtechBit1 = new BigInteger("1", 16);
    BigInteger adtechBit2 = new BigInteger("2", 16);
    assertEquals(adtechBit1, adtechMapping.get("AdTechA-enrollment_id"));
    assertEquals(adtechBit2, adtechMapping.get("AdTechB-enrollment_id"));
  }

  private JSONArray createFilterJSONArray() {
    JSONObject filterMap = new JSONObject();
    JSONArray subDomain = new JSONArray();
    subDomain.add("electronics.megastore");
    JSONArray product = new JSONArray();
    product.add("1234");
    product.add("2345");
    filterMap.put("conversion_subdomain", subDomain);
    filterMap.put("product", product);
    JSONArray filterSet = new JSONArray();
    filterSet.add(filterMap);
    return filterSet;
  }

  private JSONObject createXNetworkDataJSONObject() {
    JSONObject xNetworkData = new JSONObject();
    xNetworkData.put("key_offset", 10L);
    return xNetworkData;
  }

  private JSONArray createAttributionConfigJSONArray() {
    JSONArray attributionConfigList = new JSONArray();
    List<String> adtechIds = new ArrayList<>(Arrays.asList("AdTech1-Ads", "AdTech2-Ads"));
    for (String adtechId : adtechIds) {
      JSONObject attributionConfig = new JSONObject();
      attributionConfig.put("source_network", adtechId);
      JSONObject sourcePriorityRange = new JSONObject();
      sourcePriorityRange.put("start", 100L);
      sourcePriorityRange.put("end", 1000L);
      attributionConfig.put("source_priority_range", sourcePriorityRange);
      JSONObject sourceFilters = new JSONObject();

      JSONArray campaignType = new JSONArray();
      campaignType.add("install");
      sourceFilters.put("campaign_type", campaignType);

      JSONArray sourceType = new JSONArray();
      sourceType.add("navigation");
      sourceFilters.put("source_type", sourceType);
      JSONArray sourceFilterSet = new JSONArray();
      sourceFilterSet.add(sourceFilters);
      attributionConfig.put("source_filters", sourceFilterSet);

      JSONObject sourceNotFilters = new JSONObject();
      JSONArray campaignType2 = new JSONArray();
      campaignType2.add("product");
      sourceNotFilters.put("campaign_type", campaignType2);
      JSONArray sourceNotFilterSet = new JSONArray();
      sourceNotFilterSet.add(sourceNotFilters);
      attributionConfig.put("source_not_filters", sourceNotFilterSet);
      attributionConfig.put("source_expiry_override", 600000L);
      attributionConfig.put("priority", 99L);
      attributionConfig.put("expiry", 604800L);
      JSONObject filterData = new JSONObject();

      filterData.put("campaign_type", campaignType);
      JSONArray filterDataSet = new JSONArray();
      filterDataSet.add(filterData);
      attributionConfig.put("filter_data", filterDataSet);
      attributionConfig.put("post_install_exclusivity_window", 100000L);
      attributionConfigList.add(attributionConfig);
    }
    return attributionConfigList;
  }
}
