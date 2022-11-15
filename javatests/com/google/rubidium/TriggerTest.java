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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.rubidium.aggregation.AggregatableAttributionTrigger;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class TriggerTest {
  private static final String TOP_LEVEL_FILTERS_JSON_STRING =
      "{\n"
          + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
          + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
          + "}\n";
  private static final String TOP_LEVEL_FILTERS_JSON_STRING_X =
      "{\n"
          + "  \"key_1x\": [\"value_1\", \"value_2\"],\n"
          + "  \"key_2x\": [\"value_1\", \"value_2\"]\n"
          + "}\n";

  @Test
  public void testEqualsPass() {
    assertEquals(new Trigger.Builder().build(), new Trigger.Builder().build());
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
    assertEquals(
        new Trigger.Builder()
            .setEnrollmentId("https://example.com")
            .setAttributionDestination(URI.create("https://example.com/aD"))
            .setId("1")
            .setEventTriggers("data 1 L, priority 3 L, dedup key 6L")
            .setTriggerTime(5L)
            .setStatus(Trigger.Status.PENDING)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateTriggerData(aggregateTriggerDatas.toString())
            .setAggregateValues(values.toString())
            .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .build(),
        new Trigger.Builder()
            .setEnrollmentId("https://example.com")
            .setAttributionDestination(URI.create("https://example.com/aD"))
            .setId("1")
            .setEventTriggers("data 1 L, priority 3 L, dedup key 6L")
            .setTriggerTime(5L)
            .setStatus(Trigger.Status.PENDING)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateTriggerData(aggregateTriggerDatas.toString())
            .setAggregateValues(values.toString())
            .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
            .build());
  }

  @Test
  public void testEqualsFail() {
    assertNotEquals(
        new Trigger.Builder().setAttributionDestination(URI.create(("1"))).build(),
        new Trigger.Builder().setAttributionDestination(URI.create(("2"))).build());
    assertNotEquals(
        new Trigger.Builder().setEnrollmentId(("1")).build(),
        new Trigger.Builder().setEnrollmentId(("2")).build());
    assertNotEquals(
        new Trigger.Builder().setEventTriggers("priority 1L").build(),
        new Trigger.Builder().setEventTriggers("priority 2L").build());
    assertNotEquals(
        new Trigger.Builder().setTriggerTime(1L).build(),
        new Trigger.Builder().setTriggerTime(2L).build());
    assertNotEquals(
        new Trigger.Builder().setEventTriggers("data 1L").build(),
        new Trigger.Builder().setEventTriggers("data 2L").build());
    assertNotEquals(
        new Trigger.Builder().setStatus(Trigger.Status.PENDING).build(),
        new Trigger.Builder().setStatus(Trigger.Status.IGNORED).build());
    assertNotEquals(
        new Trigger.Builder().setEventTriggers("dedup key 1L").build(),
        new Trigger.Builder().setEventTriggers("dedup key 2L").build());
    assertNotEquals(
        new Trigger.Builder().setRegistrant(URI.create(("android-app://com.example.abc"))).build(),
        new Trigger.Builder().setRegistrant(URI.create(("android-app://com.example.xyz"))).build());
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
        new Trigger.Builder().setAggregateTriggerData(aggregateTriggerDataList1.toString()).build(),
        new Trigger.Builder()
            .setAggregateTriggerData(aggregateTriggerDataList2.toString())
            .build());
    JSONObject values1 = new JSONObject();
    values1.put("campaignCounts", 32768);
    JSONObject values2 = new JSONObject();
    values2.put("geoValue", 1664);
    assertNotEquals(
        new Trigger.Builder().setAggregateValues(values1.toString()).build(),
        new Trigger.Builder().setAggregateValues(values2.toString()).build());
    assertNotEquals(
        new Trigger.Builder().setFilters(TOP_LEVEL_FILTERS_JSON_STRING).build(),
        new Trigger.Builder().setFilters(TOP_LEVEL_FILTERS_JSON_STRING_X).build());
  }

  @Test
  public void testParseAggregateTrigger() throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    JSONArray sourcekeysArray1 = new JSONArray();
    sourcekeysArray1.addAll(Arrays.asList("campaignCounts"));
    JSONArray sourcekeysArray2 = new JSONArray();
    sourcekeysArray2.addAll(Arrays.asList("geoValue", "noMatch"));
    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", sourcekeysArray1);
    jsonObject1.put("filters", createFilterJSONObject());
    jsonObject1.put("not_filters", createFilterJSONObject());
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", sourcekeysArray2);
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    JSONObject values = new JSONObject();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    Trigger trigger =
        new Trigger.Builder()
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues(values.toString())
            .build();
    Optional<AggregatableAttributionTrigger> aggregatableAttributionTrigger =
        trigger.parseAggregateTrigger();
    assertTrue(aggregatableAttributionTrigger.isPresent());
    AggregatableAttributionTrigger aggregateTrigger = aggregatableAttributionTrigger.get();
    assertEquals(aggregateTrigger.getTriggerData().size(), 2);
    assertEquals(aggregateTrigger.getTriggerData().get(0).getSourceKeys().size(), 1);
    assertEquals(aggregateTrigger.getTriggerData().get(0).getKey().longValue(), 1024L);
    assertTrue(aggregateTrigger.getTriggerData().get(0).getSourceKeys().contains("campaignCounts"));
    assertTrue(aggregateTrigger.getTriggerData().get(0).getFilter().isPresent());
    assertEquals(
        aggregateTrigger.getTriggerData().get(0).getFilter().get().getAttributionFilterMap().size(),
        2);
    assertTrue(aggregateTrigger.getTriggerData().get(0).getNotFilter().isPresent());
    assertEquals(
        aggregateTrigger
            .getTriggerData()
            .get(0)
            .getNotFilter()
            .get()
            .getAttributionFilterMap()
            .size(),
        2);
    assertEquals(aggregateTrigger.getTriggerData().get(1).getKey().longValue(), 2688L);
    assertEquals(aggregateTrigger.getTriggerData().get(1).getSourceKeys().size(), 2);
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("geoValue"));
    assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("noMatch"));
    assertEquals(aggregateTrigger.getValues().size(), 2);
    assertEquals(aggregateTrigger.getValues().get("campaignCounts").intValue(), 32768);
    assertEquals(aggregateTrigger.getValues().get("geoValue").intValue(), 1664);
  }

  private JSONObject createFilterJSONObject() throws ParseException {
    JSONObject filterData = new JSONObject();
    JSONArray conversionsubdomainArray = new JSONArray();
    conversionsubdomainArray.addAll(Arrays.asList("electronics.megastore"));
    JSONArray productArray = new JSONArray();
    productArray.addAll(Arrays.asList("1234", "2345"));
    filterData.put("conversion_subdomain", conversionsubdomainArray);
    filterData.put("product", productArray);
    return filterData;
  }
}
