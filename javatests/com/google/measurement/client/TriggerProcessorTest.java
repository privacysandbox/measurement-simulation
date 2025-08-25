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

package com.google.measurement.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.measurement.client.Trigger.Status;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class TriggerProcessorTest {

  @Test
  public void testBuildingFromJson() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d1\", "
            + "\"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\", "
            + "\"timestamp\": 1642271444000, \"event_trigger_data\": [{\"trigger_data\": 1000, "
            + "\"priority\": 100, \"deduplication_key\": 1}], \"registrant\": "
            + "\"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\": "
            + "\"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": [{\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\":[\"value_1\", \"value_2\"]}] }], "
            + "\"aggregatable_values\": {\"campaignCounts\":32768,\"geoValue\":1664}, "
            + "\"filters\":{\"key_1\":[\"value_1\",\"value_2\"]}}";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
    String destination = "https://www.example2.com/d1";
    assertTrigger(trigger, destination);
  }

  @Test
  public void testBuildingFromJson_withNumericStrings() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d1\","
            + " \"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"timestamp\": \"1642271444000\", \"event_trigger_data\": [{\"trigger_data\":"
            + " \"1000\", \"priority\": \"100\", \"deduplication_key\": \"1\"}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": [{\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\":[\"value_1\", \"value_2\"]}] }],"
            + " \"aggregatable_values\": {\"campaignCounts\":32768,\"geoValue\":1664}, "
            + "\"filters\":{\"key_1\":[\"value_1\",\"value_2\"]}}";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
    String destination = "https://www.example2.com/d1";
    assertTrigger(trigger, destination);
  }

  @Test
  public void testBuildingFromJson_withAppDestination() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"android-app://com.test.app\","
            + " \"destination_type\": \"APP\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"timestamp\": \"1642271444000\", \"event_trigger_data\": [{\"trigger_data\":"
            + " \"1000\", \"priority\": \"100\", \"deduplication_key\": \"1\"}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": [{\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\":[\"value_1\", \"value_2\"]}] }],"
            + " \"aggregatable_values\": {\"campaignCounts\":32768,\"geoValue\":1664}, "
            + "\"filters\":{\"key_1\":[\"value_1\",\"value_2\"]}}";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
    String destination = "android-app://com.test.app";
    assertTrigger(trigger, destination);
  }

  @Test
  public void testBuildingFromJson_withAppDestinationMissingScheme() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"com.test.app\","
            + " \"destination_type\": \"APP\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"timestamp\": \"1642271444000\", \"event_trigger_data\": [{\"trigger_data\":"
            + " \"1000\", \"priority\": \"100\", \"deduplication_key\": \"1\"}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": [{\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\":[\"value_1\", \"value_2\"]}] }],"
            + " \"aggregatable_values\": {\"campaignCounts\":32768,\"geoValue\":1664}, "
            + "\"filters\":{\"key_1\":[\"value_1\",\"value_2\"]}}";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
    String destination = "android-app://com.test.app";
    assertTrigger(trigger, destination);
  }

  private void assertTrigger(Trigger trigger, String destination) {
    assertEquals(destination, trigger.getAttributionDestination().toString());
    assertEquals("https://www.example3.com/r1", trigger.getEnrollmentId());
    assertEquals(
        "[{\"deduplication_key\":1,\"priority\":100,\"trigger_data\":1000}]",
        trigger.getEventTriggers());
    assertEquals(1642271444000L, trigger.getTriggerTime());
    assertEquals(Status.PENDING, trigger.getStatus());
    assertEquals("http://example1.com/4", trigger.getRegistrant().toString());
    assertTrue(trigger.getAggregateTriggerData().contains("\"key_piece\":\"0x400\""));
    assertTrue(trigger.getAggregateTriggerData().contains("\"source_keys\":[\"campaignCounts\"]"));
    assertTrue(trigger.getAggregateTriggerData().contains("\"key_1\":[\"value_1\",\"value_2\"]"));
    assertTrue(trigger.getAggregateTriggerData().contains("\"key_2\":[\"value_1\",\"value_2\"]"));
    assertEquals("{\"geoValue\":1664,\"campaignCounts\":32768}", trigger.getAggregateValues());
    assertEquals("[{\"key_1\":[\"value_1\",\"value_2\"]}]", trigger.getFilters());
  }
}
