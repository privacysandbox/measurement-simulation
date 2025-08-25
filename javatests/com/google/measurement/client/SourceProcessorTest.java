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
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.measurement.client.Source.AttributionMode;
import com.google.measurement.client.Source.SourceType;
import com.google.measurement.client.Source.Status;
import com.google.measurement.client.util.UnsignedLong;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class SourceProcessorTest {

  @Test
  public void testBuildingFromJson() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"source_event_id\": 1, \"source_type\": \"EVENT\", \"publisher\":"
            + " \"https://www.example1.com/s1\", \"web_destination\":"
            + " \"https://www.example2.com/d1\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"timestamp\": 1642218050000, \"expiry\":"
            + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
            + " \"dedup_keys\": [], \"attributionMode\": \"TRUTHFULLY\","
            + " \"install_attribution_window\": 1728000, \"post_install_exclusivity_window\": 101,"
            + " \"filter_data\": {\"type\":  [\"1\"], \"ctid\":  [\"id\"]}, \"aggregation_keys\":"
            + " {\"myId\": \"0x1\"}, \"api_choice\": \"WEB\"}\n";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Source source = SourceProcessor.buildSourceFromJson(jsonObject);
    assertSource(source);
  }

  @Test
  public void testBuildingFromJson_withFlexEvent() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"source_event_id\": \"1\", \"source_type\": \"NAVIGATION\","
            + " \"publisher\": \"https://www.example1.com/s1\", \"web_destination\":"
            + " \"https://www.example2.com/d1\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"timestamp\": \"1642218050000\", \"expiry\":"
            + " \"1647645724\", \"priority\": \"100\",         \"trigger_specs\": [\n"
            + "          {\n"
            + "            \"trigger_data\": [\n"
            + "              3,\n"
            + "              4,\n"
            + "              5,\n"
            + "              6,\n"
            + "              7\n"
            + "            ],\n"
            + "            \"event_report_windows\": {\n"
            + "              \"start_time\": 0,\n"
            + "              \"end_times\": [\n"
            + "                172300\n"
            + "              ]\n"
            + "            },\n"
            + "            \"summary_window_operator\": \"count\",\n"
            + "            \"summary_buckets\": [\n"
            + "              1,\n"
            + "              2,\n"
            + "              3\n"
            + "            ]\n"
            + "          }\n"
            + "        ],"
            + " \"max_event_level_reports\": 2 \n"
            + " \"registrant\":"
            + " \"https://www.example3.com/e1\", \"dedup_keys\": [], \"attributionMode\":"
            + " \"TRUTHFULLY\", \"install_attribution_window\": \"1728000\","
            + " \"post_install_exclusivity_window\": \"101\", \"filter_data\": {\"type\":  [\"1\"],"
            + " \"ctid\":  [\"id\"]}, \"aggregation_keys\": {\"myId\": \"0x1\"}, \"api_choice\":"
            + " \"WEB\"}\n";

    JSONObject jsonObject = (JSONObject) ((new JSONParser()).parse(json));
    Source source = SourceProcessor.buildSourceFromJson(jsonObject);
    assertEquals((Long) 1L, source.getEventId().getValue());
    assertEquals(SourceType.NAVIGATION, source.getSourceType());
    assertEquals("https://www.example1.com", source.getPublisher().toString());
    assertEquals("https://example2.com", source.getWebDestinations().get(0).toString());
    assertEquals("https://www.example3.com/r1", source.getEnrollmentId());
    assertEquals("https://www.example3.com/e1", source.getRegistrant().toString());
    assertEquals(Status.ACTIVE, source.getStatus());
    assertEquals(1642218050000L, source.getEventTime());
    assertEquals(1644810050000L, source.getExpiryTime());
    assertEquals(100, source.getPriority());
    assertEquals(AttributionMode.TRUTHFULLY, source.getAttributionMode());
    assertEquals(1728000000L, source.getInstallAttributionWindow());
    assertEquals(101000, source.getInstallCooldownWindow());
    assertEquals("{\"ctid\":[\"id\"],\"type\":[\"1\"]}", source.getFilterDataString());
    assertEquals("{\"myId\":\"0x1\"}", source.getAggregateSource());
    assertNotNull(source.getFlexEventReportSpec());
    assertEquals(
        172300000L,
        source
            .getFlexEventReportSpec()
            .getTriggerSpecs()[0]
            .getEventReportWindowsEnd()
            .get(0)
            .longValue());
    assertEquals(
        new UnsignedLong(3L),
        source.getFlexEventReportSpec().getTriggerSpecs()[0].getTriggerData().get(0));
    assertEquals(2, source.getMaxEventLevelReports().intValue());
  }

  @Test
  public void testBuildingFromJson_withNumericStrings() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"source_event_id\": \"1\", \"source_type\": \"EVENT\","
            + " \"publisher\": \"https://www.example1.com/s1\", \"web_destination\":"
            + " \"https://www.example2.com/d1\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"timestamp\": \"1642218050000\", \"expiry\":"
            + " \"1647645724\", \"priority\": \"100\", \"registrant\":"
            + " \"https://www.example3.com/e1\", \"dedup_keys\": [], \"attributionMode\":"
            + " \"TRUTHFULLY\", \"install_attribution_window\": \"1728000\","
            + " \"post_install_exclusivity_window\": \"101\", \"filter_data\": {\"type\":  [\"1\"],"
            + " \"ctid\":  [\"id\"]}, \"aggregation_keys\": {\"myId\": \"0x1\"}, \"api_choice\":"
            + " \"WEB\"}\n";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Source source = SourceProcessor.buildSourceFromJson(jsonObject);
    assertSource(source);
  }

  private void assertSource(Source source) {
    assertEquals((Long) 1L, source.getEventId().getValue());
    assertEquals(SourceType.EVENT, source.getSourceType());
    assertEquals("https://www.example1.com", source.getPublisher().toString());
    assertEquals("https://example2.com", source.getWebDestinations().get(0).toString());
    assertEquals("https://www.example3.com/r1", source.getEnrollmentId());
    assertEquals("https://www.example3.com/e1", source.getRegistrant().toString());
    assertEquals(Status.ACTIVE, source.getStatus());
    assertEquals(1642218050000L, source.getEventTime());
    assertEquals(1644810050000L, source.getExpiryTime());
    assertEquals(100, source.getPriority());
    assertEquals(AttributionMode.TRUTHFULLY, source.getAttributionMode());
    assertEquals(1728000000L, source.getInstallAttributionWindow());
    assertEquals(101000, source.getInstallCooldownWindow());

    JsonElement reportJson = JsonParser.parseString(source.getFilterDataString());
    JsonElement expectedJson = JsonParser.parseString("{\"ctid\":[\"id\"],\"type\":[\"1\"]}");
    assertEquals(expectedJson, reportJson);

    assertEquals("{\"myId\":\"0x1\"}", source.getAggregateSource());
  }
}
