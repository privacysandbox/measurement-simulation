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

import java.util.Arrays;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Rule;
import org.junit.Test;

public class DataProcessorTest {
  @Rule public final transient TestPipeline p = TestPipeline.create();

  @Test
  public void BuildUserToSourceMapTest() {
    String[] cmdArgs =
        new String[] {
          "--sourceStartDate=2022-01-15",
          "--sourceEndDate=2022-01-15",
          "--attributionSourceFileName=attribution_source.json",
          "--inputDirectory=testdata"
        };
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);

    String sourceData1 =
        "{\"user_id\": \"U1\", \"source_event_id\": 1, \"source_type\": \"EVENT\", \"publisher\":"
            + " \"https://www.example1.com/s1\", \"web_destination\":"
            + " \"https://www.example2.com/d1\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"event_time\": 1642218050000, \"expiry\":"
            + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
            + " \"dedup_keys\": [], \"install_attribution_window\": 100,"
            + " \"post_install_exclusivity_window\": 101, \"filter_data\": {\"type\": [\"1\","
            + " \"2\", \"3\", \"4\"], \"ctid\":  [\"id\"]}, \"aggregation_keys\": [{\"id\":"
            + " \"myId\", \"key_piece\": \"0xFFFFFFFFFFFFFF\" }]}";

    String sourceData2 =
        "{\"user_id\": \"U1\", \"source_event_id\": 2, \"source_type\": \"EVENT\", \"publisher\":"
            + " \"https://www.example1.com/s2\", \"web_destination\":"
            + " \"https://www.example2.com/d2\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"event_time\": 1642235602000, \"expiry\":"
            + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
            + " \"dedup_keys\": [], \"install_attribution_window\": 100,"
            + " \"post_install_exclusivity_window\": 101, \"filter_data\": {\"type\": [\"7\","
            + " \"8\", \"9\", \"10\"], \"ctid\": [\"id\"]}, \"aggregation_keys\": [{\"id\":"
            + " \"campaignCounts\", \"key_piece\": \"0x159\"}, {\"id\": \"geoValue\","
            + " \"key_piece\": \"0x5\"}]}";

    String sourceData3 =
        "{\"user_id\": \"U2\", \"source_event_id\": 3, \"source_type\": \"NAVIGATION\","
            + " \"publisher\": \"https://www.example1.com/s3\", \"web_destination\":"
            + " \"https://www.example2.com/d3\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"event_time\": 1642249235000, \"expiry\":"
            + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
            + " \"dedup_keys\": [], \"install_attribution_window\": 100,"
            + " \"post_install_exclusivity_window\": 101, \"filter_data\": {\"type\": [\"1\","
            + " \"2\", \"3\", \"4\"], \"ctid\":  [\"id\"]}, \"aggregation_keys\": [{\"id\":"
            + " \"myId3\", \"key_piece\": \"0xFFFFFFFFFFFFFFFFFFFFFF\"}]}";

    JSONParser parser = new JSONParser();
    try {
      Source source1 = SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(sourceData1));
      Source source2 = SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(sourceData2));
      Source source3 = SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(sourceData3));
      PCollection<KV<String, Source>> userToAdtechSourceData =
          DataProcessor.buildUserToSourceMap(p, options);

      PAssert.that(userToAdtechSourceData)
          .containsInAnyOrder(
              Arrays.asList(KV.of("U1", source1), KV.of("U1", source2), KV.of("U2", source3)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  @Test
  public void BuildUserToTriggerMapTest() {
    String[] cmdArgs =
        new String[] {
          "--triggerStartDate=2022-01-15",
          "--triggerEndDate=2022-01-15",
          "--triggerFileName=trigger.json",
          "--inputDirectory=testdata"
        };
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);

    String triggerData1 =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d1\","
            + " \"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"trigger_time\": 1642271444000, \"event_trigger_data\": [{\"trigger_data\": 1000,"
            + " \"priority\": 100, \"deduplication_key\": 1}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": {\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\": [\"value_1\", \"value_2\"]} }],"
            + " \"aggregatable_values\": {\"campaignCounts\": 32768,\"geoValue\": 1664},"
            + " \"filters\": \"{\\\"key_1\\\": [\\\"value_1\\\", \\\"value_2\\\"], \\\"key_2\\\":"
            + " [\\\"value_1\\\", \\\"value_2\\\"]}\"}";
    String triggerData2 =
        "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d3\","
            + " \"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"trigger_time\": 1642273950000, \"event_trigger_data\": [{\"trigger_data\": 1000,"
            + " \"priority\": 100, \"deduplication_key\": 1}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"not_filters\": {\"key_1x\":"
            + " [\"value_1\", \"value_2\"], \"key_2x\": [\"value_1\", \"value_2\"]} }],"
            + " \"aggregatable_values\": {\"campaignCounts\": 32768,\"geoValue\": 1664},"
            + " \"filters\": \"{\\\"key_1\\\": [\\\"value_1\\\", \\\"value_2\\\"], \\\"key_2\\\":"
            + " [\\\"value_1\\\", \\\"value_2\\\"]}\"}";
    String triggerData3 =
        "{\"user_id\": \"U2\", \"attribution_destination\": \"https://www.example2.com/d3\","
            + " \"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\","
            + " \"trigger_time\": 1642288930000, \"event_trigger_data\": [{\"trigger_data\": 1000,"
            + " \"priority\": 100, \"deduplication_key\": 1}], \"registrant\":"
            + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
            + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": {\"key_1\":"
            + " [\"value_1\", \"value_2\"], \"key_2\": [\"value_1\", \"value_2\"]} }],"
            + " \"aggregatable_values\": {\"campaignCounts\": 32768,\"geoValue\": 1664},"
            + " \"filters\": \"{\\\"key_1\\\": [\\\"value_1\\\", \\\"value_2\\\"], \\\"key_2\\\":"
            + " [\\\"value_1\\\", \\\"value_2\\\"]}\"}";
    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);

    JSONParser parser = new JSONParser();
    try {
      Trigger trigger1 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(triggerData1));
      Trigger trigger2 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(triggerData2));
      Trigger trigger3 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(triggerData3));

      PAssert.that(userToAdtechTriggerData)
          .containsInAnyOrder(
              Arrays.asList(KV.of("U1", trigger1), KV.of("U1", trigger2), KV.of("U2", trigger3)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }
}
