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
import com.google.protobuf.Value;
import com.google.rubidium.InputData.DestinationType;
import com.google.rubidium.Trigger.Status;
import com.google.rubidium.aggregation.AggregatableAttributionTrigger;
import com.google.rubidium.aggregation.AggregateFilterData;
import com.google.rubidium.aggregation.AggregateTriggerData;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Test;

public class TriggerProcessorTest {
  @Test
  public void testBuildingFromJson() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d1\", "
            + "\"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\", "
            + "\"trigger_time\": 1642271444000, \"event_trigger_data\": [{\"trigger_data\": 1000, "
            + "\"priority\": 100, \"deduplication_key\": 1}], \"registrant\": "
            + "\"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\": "
            + "\"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": {\"key_1\":"
            + "[\"value_1\",\"value_2\"],\"key_2\":[\"value_1\",\"value_2\"]} }], "
            + "\"aggregatable_values\": {\"campaignCounts\":32768,\"geoValue\":1664}, "
            + "\"filters\": \"{\\\"key_1\\\": [\\\"value_1\\\", \\\"value_2\\\"]}\"}";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
    assertTrigger(trigger);
  }

  @Test
  public void testBuildingFromProto() throws Exception {
    ListValue listValue =
        ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("value_1").build())
            .addValues(Value.newBuilder().setStringValue("value_2").build())
            .build();

    Map<String, ListValue> attributionFilterMap =
        new HashMap<String, ListValue>() {
          {
            put("key_1", listValue);
            put("key_2", listValue);
          }
        };

    InputData.AggregatableTriggerData aggregateTriggerData =
        InputData.AggregatableTriggerData.newBuilder()
            .setKeyPiece("0x400")
            .addSourceKeys("campaignCounts")
            .putAllFilters(attributionFilterMap)
            .build();

    Map<String, Long> values =
        new HashMap() {
          {
            put("campaignCounts", 32768);
            put("geoValue", 1664);
          }
        };

    InputData.TriggerData triggerData =
        InputData.TriggerData.newBuilder()
            .setTriggerData(1000)
            .setPriority(100)
            .setDeduplicationKey(1)
            .build();

    InputData.Trigger protoTrigger =
        InputData.Trigger.newBuilder()
            .setAttributionDestination("https://www.example2.com/d1")
            .setEnrollmentId("https://www.example3.com/r1")
            .setDestinationType(DestinationType.WEB)
            .setTriggerTime(1642271444000L)
            .setRegistrant("http://example1.com/4")
            .addEventTriggerData(0, triggerData)
            .addAggregatableTriggerData(0, aggregateTriggerData)
            .putAllAggregateValues(values)
            .setFilters("{\"key_1\": [\"value_1\", \"value_2\"]}")
            .build();

    Trigger trigger = TriggerProcessor.buildTriggerFromProto(protoTrigger);
    assertTrigger(trigger);
  }

  private void assertTrigger(Trigger trigger) {
    Map<String, List<String>> attributionFilterMap =
        new HashMap<String, List<String>>() {
          {
            put("key_1", Arrays.asList("value_1", "value_2"));
            put("key_2", Arrays.asList("value_1", "value_2"));
          }
        };

    AggregateFilterData aggregateFilterData =
        new AggregateFilterData.Builder().setAttributionFilterMap(attributionFilterMap).build();

    AggregateTriggerData aggregateTriggerData =
        new AggregateTriggerData.Builder()
            .setFilter(aggregateFilterData)
            .setSourceKeys(
                new HashSet<String>() {
                  {
                    add("campaignCounts");
                  }
                })
            .setKey(new BigInteger("400", 16))
            .build();

    Map<String, Integer> values =
        new HashMap<String, Integer>() {
          {
            put("campaignCounts", 32768);
            put("geoValue", 1664);
          }
        };

    AggregatableAttributionTrigger aggAttributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(Arrays.asList(aggregateTriggerData))
            .setValues(values)
            .build();

    Assert.assertEquals(
        trigger.getAttributionDestination().toString(), "https://www.example2.com/d1");
    Assert.assertEquals(trigger.getEnrollmentId(), "https://www.example3.com/r1");
    Assert.assertEquals(
        trigger.getEventTriggers(),
        "[{\"deduplication_key\":1,\"priority\":100,\"trigger_data\":1000}]");
    Assert.assertEquals(trigger.getTriggerTime(), 1642271444000L);
    Assert.assertEquals(trigger.getStatus(), Status.PENDING);
    Assert.assertEquals(trigger.getRegistrant().toString(), "http://example1.com/4");
    Assert.assertTrue(trigger.getAggregateTriggerData().contains("\"key_piece\":\"0x400\""));
    Assert.assertTrue(
        trigger.getAggregateTriggerData().contains("\"source_keys\":[\"campaignCounts\"]"));
    Assert.assertTrue(
        trigger.getAggregateTriggerData().contains("\"key_1\":[\"value_1\",\"value_2\"]"));
    Assert.assertTrue(
        trigger.getAggregateTriggerData().contains("\"key_2\":[\"value_1\",\"value_2\"]"));
    Assert.assertEquals(
        trigger.getAggregateValues(), "{\"geoValue\":1664,\"campaignCounts\":32768}");
    Assert.assertEquals(trigger.getFilters(), "{\"key_1\": [\"value_1\", \"value_2\"]}");
  }
}
