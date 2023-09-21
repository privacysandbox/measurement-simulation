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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.options.PipelineOptions.CheckEnabled;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DataProcessorTest {
  @Rule public final transient TestPipeline p = TestPipeline.create();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();
  private final String inputDataYear = "2022";
  private final String inputDataMonth = "01";
  private final String inputDataDay = "15";
  private final String inputDate = String.join("-", inputDataYear, inputDataMonth, inputDataDay);
  private File sourceFile;
  private File triggerFile;
  private File extensionFile;

  @Before
  public void setUp() throws Exception {
    File testdata = tempFolder.newFolder(inputDataYear, inputDataMonth, inputDataDay);
    extensionFile = new File(testdata, "extension.json");
    sourceFile = new File(testdata, "attribution_source.json");
    triggerFile = new File(testdata, "trigger.json");
  }

  @Test
  public void buildSourceMapWithMultipleUsersTest() throws IOException {
    String user2SourceData = getSourceData().replace("U1", "U2");
    String sourceData =
        String.join("\n", getSourceData(), getSourceDataWithFlexEventAPI(), user2SourceData);
    Files.write(sourceFile.toPath(), sourceData.getBytes());

    String[] cmdArgs = getSourceCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    JSONParser parser = new JSONParser();
    try {
      Source source1 =
          SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(getSourceData()));
      Source source2 =
          SourceProcessor.buildSourceFromJson(
              (JSONObject) parser.parse(getSourceDataWithFlexEventAPI()));
      Source source3 =
          SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(user2SourceData));
      PCollection<KV<String, Source>> userToAdtechSourceData =
          DataProcessor.buildUserToSourceMap(p, options);
      userToAdtechSourceData = DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.OS);

      PAssert.that(userToAdtechSourceData)
          .containsInAnyOrder(
              Arrays.asList(KV.of("U1", source1), KV.of("U1", source2), KV.of("U2", source3)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  @Test
  public void buildUserToSourceMapByApiChoiceTest() throws IOException {
    String webSourceData =
        getSourceData().replace("\"api_choice\": \"OS\"", "\"api_choice\": \"WEB\"");
    String sourceData = String.join("\n", getSourceData(), webSourceData);
    Files.write(sourceFile.toPath(), sourceData.getBytes());

    String[] cmdArgs = getSourceCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    JSONParser parser = new JSONParser();
    try {
      Source source1 =
          SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(getSourceData()));
      Source source2 =
          SourceProcessor.buildSourceFromJson((JSONObject) parser.parse(webSourceData));
      PCollection<KV<String, Source>> userToAdtechSourceData =
          DataProcessor.buildUserToSourceMap(p, options);
      PCollection<KV<String, Source>> userToAdtechSourceDataForOs =
          DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.OS);
      PCollection<KV<String, Source>> userToAdtechSourceDataForWeb =
          DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.WEB);

      PAssert.that(userToAdtechSourceDataForOs).containsInAnyOrder(List.of(KV.of("U1", source1)));
      PAssert.that(userToAdtechSourceDataForWeb).containsInAnyOrder(List.of(KV.of("U1", source2)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  @Test
  public void ignoreAppDestinationsOnWebTest() throws IOException {
    String webSourceData =
        getSourceData()
            .replace("\"api_choice\": \"OS\"", "\"api_choice\": \"WEB\"")
            .replace(
                "\"web_destination\": \"https://www.example2.com/d1\"",
                "\"destination\": \"android-app://com.advertiser.example\"");
    String sourceData = String.join("\n", webSourceData);
    Files.write(sourceFile.toPath(), sourceData.getBytes());

    String[] cmdArgs = getSourceCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Source>> userToAdtechSourceData =
        DataProcessor.buildUserToSourceMap(p, options);
    userToAdtechSourceData = DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.WEB);
    PAssert.that(userToAdtechSourceData).empty();
    p.run().waitUntilFinish();
  }

  @Test
  public void noWebSourcesTest() throws IOException {
    Files.write(sourceFile.toPath(), getSourceData().getBytes());

    String[] cmdArgs = getSourceCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Source>> userToAdtechSourceData =
        DataProcessor.buildUserToSourceMap(p, options);
    userToAdtechSourceData = DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.WEB);
    PAssert.that(userToAdtechSourceData).empty();
    p.run().waitUntilFinish();
  }

  @Test
  public void noOsSourcesTest() throws IOException {
    Files.write(sourceFile.toPath(), getSourceData().replace("OS", "WEB").getBytes());

    String[] cmdArgs = getSourceCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Source>> userToAdtechSourceData =
        DataProcessor.buildUserToSourceMap(p, options);
    userToAdtechSourceData = DataProcessor.filterSourceMap(userToAdtechSourceData, ApiChoice.OS);
    PAssert.that(userToAdtechSourceData).empty();
    p.run().waitUntilFinish();
  }

  @Test
  public void buildTriggerMapWithMultipleUsersTest() throws IOException {
    String user2TriggerData = getTriggerData().replace("U1", "U2");
    String triggerData = String.join("\n", getTriggerData(), getTriggerData(), user2TriggerData);
    Files.write(triggerFile.toPath(), triggerData.getBytes());
    String[] cmdArgs = getTriggerCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);

    userToAdtechTriggerData = DataProcessor.filterTriggerMap(userToAdtechTriggerData, ApiChoice.OS);

    JSONParser parser = new JSONParser();
    try {
      Trigger trigger1 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(getTriggerData()));
      Trigger trigger2 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(getTriggerData()));
      Trigger trigger3 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(user2TriggerData));

      PAssert.that(userToAdtechTriggerData)
          .containsInAnyOrder(
              Arrays.asList(KV.of("U1", trigger1), KV.of("U1", trigger2), KV.of("U2", trigger3)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  @Test
  public void buildUserToTriggerMapByApiChoiceTest() throws IOException {
    String webTriggerData =
        getTriggerData().replace("\"api_choice\": \"OS\"", "\"api_choice\": \"WEB\"");
    String triggerData = String.join("\n", getTriggerData(), webTriggerData);
    Files.write(triggerFile.toPath(), triggerData.getBytes());
    String[] cmdArgs = getTriggerCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);
    PCollection<KV<String, Trigger>> userToAdtechTriggerDataForOs =
        DataProcessor.filterTriggerMap(userToAdtechTriggerData, ApiChoice.OS);
    PCollection<KV<String, Trigger>> userToAdtechTriggerDataForWeb =
        DataProcessor.filterTriggerMap(userToAdtechTriggerData, ApiChoice.WEB);

    JSONParser parser = new JSONParser();
    try {
      Trigger trigger1 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(getTriggerData()));
      Trigger trigger2 =
          TriggerProcessor.buildTriggerFromJson((JSONObject) parser.parse(webTriggerData));

      PAssert.that(userToAdtechTriggerDataForOs).containsInAnyOrder(List.of(KV.of("U1", trigger1)));
      PAssert.that(userToAdtechTriggerDataForWeb)
          .containsInAnyOrder(List.of(KV.of("U1", trigger2)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  @Test
  public void noWebTriggersTest() throws IOException {
    Files.write(triggerFile.toPath(), getTriggerData().getBytes());
    String[] cmdArgs = getTriggerCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);

    userToAdtechTriggerData =
        DataProcessor.filterTriggerMap(userToAdtechTriggerData, ApiChoice.WEB);

    PAssert.that(userToAdtechTriggerData).empty();
    p.run().waitUntilFinish();
  }

  @Test
  public void noOsTriggersTest() throws IOException {
    Files.write(triggerFile.toPath(), getTriggerData().replace("OS", "WEB").getBytes());
    String[] cmdArgs = getTriggerCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);

    userToAdtechTriggerData = DataProcessor.filterTriggerMap(userToAdtechTriggerData, ApiChoice.OS);

    PAssert.that(userToAdtechTriggerData).empty();
    p.run().waitUntilFinish();
  }

  @Test
  public void buildUserToExtensionEventMapTest() throws IOException {
    String extensionEventData =
        "{\"user_id\": \"U1\", \"uri\": \"android-app://example2.d1.test\","
            + " \"timestamp\": \"1642218050000\", \"action\": \"install\"}";
    Files.write(extensionFile.toPath(), extensionEventData.getBytes());

    String[] cmdArgs = getExtensionCmdArgs();
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(cmdArgs).withValidation().as(SimulationConfig.class);
    p.getOptions().setStableUniqueNames(CheckEnabled.OFF);

    JSONParser parser = new JSONParser();
    try {
      ExtensionEvent extensionEvent =
          ExtensionEvent.buildExtensionEventFromJson((JSONObject) parser.parse(extensionEventData));
      PCollection<KV<String, ExtensionEvent>> userToExtensionEventMap =
          DataProcessor.buildUserToExtensionEventMap(p, options);

      PAssert.that(userToExtensionEventMap)
          .containsInAnyOrder(List.of(KV.of("U1", extensionEvent)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    p.run().waitUntilFinish();
  }

  private String getSourceData() {
    return "{\"user_id\": \"U1\", \"source_event_id\": 1, \"source_type\": \"EVENT\","
        + " \"publisher\": \"https://www.example1.com/s1\", \"web_destination\":"
        + " \"https://www.example2.com/d1\", \"enrollment_id\":"
        + " \"https://www.example3.com/r1\", \"timestamp\": 1642218050000, \"expiry\":"
        + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
        + " \"dedup_keys\": [], \"install_attribution_window\": 100,"
        + " \"post_install_exclusivity_window\": 101, \"filter_data\": {\"type\": [\"1\","
        + " \"2\", \"3\", \"4\"], \"ctid\":  [\"id\"]}, \"aggregation_keys\": {\"myId\":"
        + " \"0xFFFFFFFFFFFFFF\"}, \"api_choice\": \"OS\"}";
  }

  private String getSourceDataWithFlexEventAPI() {
    return "{\"user_id\": \"U1\", \"source_event_id\": \"1\", \"source_type\": \"NAVIGATION\","
        + " \"publisher\": \"https://www.example1.com/s1\", \"web_destination\":"
        + " \"https://www.example2.com/d1\", \"enrollment_id\":"
        + " \"https://www.example3.com/r1\", \"timestamp\": \"1642218050000\", \"expiry\":"
        + " \"1647645724\", \"priority\": \"100\", \"trigger_specs\": [{ "
        + "\"trigger_data\": [3,4,5,6, 7], \"event_report_windows\": {"
        + "\"start_time\": 0,\"end_times\": [172300]}, \"summary_window_operator\": "
        + "\"count\",\"summary_buckets\": [1,2,3]}],"
        + " \"max_event_level_reports\": 2 "
        + " \"registrant\":"
        + " \"https://www.example3.com/e1\", \"dedup_keys\": [], \"attributionMode\":"
        + " \"TRUTHFULLY\", \"install_attribution_window\": \"1728000\","
        + " \"post_install_exclusivity_window\": \"101\", \"filter_data\": {\"type\":  [\"1\"],"
        + " \"ctid\":  [\"id\"]}, \"aggregation_keys\": {\"myId\": \"0x1\"}, \"api_choice\":"
        + " \"OS\"}";
  }

  private String getTriggerData() {
    return "{\"user_id\": \"U1\", \"attribution_destination\": \"https://www.example2.com/d1\","
        + " \"destination_type\": \"WEB\", \"enrollment_id\": \"https://www.example3.com/r1\","
        + " \"timestamp\": 1642271444000, \"event_trigger_data\": [{\"trigger_data\": 1000,"
        + " \"priority\": 100, \"deduplication_key\": 1}], \"registrant\":"
        + " \"http://example1.com/4\", \"aggregatable_trigger_data\": [{\"key_piece\":"
        + " \"0x400\", \"source_keys\": [\"campaignCounts\"], \"filters\": [{\"key_1\":"
        + " [\"value_1\", \"value_2\"], \"key_2\": [\"value_1\", \"value_2\"]}] }],"
        + " \"aggregatable_values\": {\"campaignCounts\": 32768,\"geoValue\": 1664},"
        + " \"filters\": {\"key_1\": [\"value_1\", \"value_2\"], \"key_2\":"
        + " [\"value_1\", \"value_2\"]}, \"api_choice\": \"OS\"}";
  }

  private String[] getSourceCmdArgs() {
    return new String[] {
      "--sourceStartDate=" + inputDate,
      "--sourceEndDate=" + inputDate,
      "--attributionSourceFileName=attribution_source.json",
      "--inputDirectory=" + tempFolder.getRoot().getAbsolutePath(),
    };
  }

  private String[] getTriggerCmdArgs() {
    return new String[] {
      "--triggerStartDate=" + inputDate,
      "--triggerEndDate=" + inputDate,
      "--triggerFileName=trigger.json",
      "--inputDirectory=" + tempFolder.getRoot().getAbsolutePath(),
    };
  }

  private String[] getExtensionCmdArgs() {
    return new String[] {
      "--extensionEventStartDate=" + inputDate,
      "--extensionEventEndDate=" + inputDate,
      "--extensionEventFileName=extension.json",
      "--inputDirectory=" + tempFolder.getRoot().getAbsolutePath(),
    };
  }
}
