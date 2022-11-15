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

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;

@DefaultCoder(AvroCoder.class)
class GenerateAggregatableReport extends DoFn<JSONObject, Boolean> {
  private Set<JSONObject> expectedAggregatableReports;
  private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
  private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);

  GenerateAggregatableReport(Set<JSONObject> expectedAggregatableReports) {
    this.expectedAggregatableReports = expectedAggregatableReports;
  }

  @ProcessElement
  public void processElement(ProcessContext c) {
    JSONObject element = c.element();
    JSONObject updatedElement = getAggregateReportObject(element);
    c.output(checkAggregatableReport(updatedElement));
  }

  private boolean checkAggregatableReport(JSONObject actualReport) {
    for (JSONObject expectedReportObj : this.expectedAggregatableReports) {
      if (isMatch(actualReport, expectedReportObj)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMatch(JSONObject actualReport, JSONObject expectedReport) {
    long actualReportTime = (Long) actualReport.get("report_time");
    long expectedReportTime = (Long) expectedReport.get("report_time");
    if (actualReportTime < (expectedReportTime + MIN_TIME_MS)
        || actualReportTime > (expectedReportTime + MAX_TIME_MS)) {
      return false;
    }

    if (!actualReport.get("report_url").equals(expectedReport.get("report_url"))) {
      return false;
    }

    String expectedDestination =
        (String) ((JSONObject) expectedReport.get("payload")).get("attribution_destination");
    if (!actualReport.get("attribution_destination").equals(expectedDestination)) {
      return false;
    }

    JSONArray actualHistograms =
        (JSONArray) ((JSONObject) actualReport.get("payload")).get("histograms");
    JSONArray expectedHistograms =
        (JSONArray) ((JSONObject) expectedReport.get("payload")).get("histograms");
    if (actualHistograms.size() != expectedHistograms.size()) {
      return false;
    }
    for (Object histogramObj : expectedHistograms) {
      JSONObject histogram = (JSONObject) histogramObj;
      if (!actualHistograms.toString().contains(histogram.toString())) {
        return false;
      }
    }
    return true;
  }

  private JSONObject getAggregateReportObject(JSONObject element) {
    JSONObject result = new JSONObject();
    JSONParser parser = new JSONParser();
    try {
      JSONObject sharedInfo = (JSONObject) parser.parse((String) element.get("shared_info"));
      result.put("report_time", (Long) sharedInfo.get("scheduled_report_time"));
      result.put("report_url", sharedInfo.get("reporting_origin"));
      result.put("attribution_destination", sharedInfo.get("attribution_destination"));
      result.put("payload", getAggregatablePayloadForTest(element));
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return result;
  }

  private static JSONObject getAggregatablePayloadForTest(JSONObject data) {
    JSONArray payloads = (JSONArray) data.get("aggregation_service_payloads");
    String payload = (String) ((JSONObject) payloads.get(0)).get("debug_cleartext_payload");
    final byte[] encoded = Base64.getDecoder().decode(payload);
    JSONObject aggregateJson = new JSONObject();
    aggregateJson.put("histograms", getAggregateHistograms(encoded));
    return aggregateJson;
  }

  private static JSONArray getAggregateHistograms(byte[] encodedCborPayload) {
    JSONArray result = new JSONArray();
    try {
      final List<DataItem> dataItems =
          new CborDecoder(new ByteArrayInputStream(encodedCborPayload)).decode();
      final co.nstant.in.cbor.model.Map payload = (co.nstant.in.cbor.model.Map) dataItems.get(0);
      final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
      for (DataItem i : payloadArray.getDataItems()) {
        co.nstant.in.cbor.model.Map m = (co.nstant.in.cbor.model.Map) i;
        JSONObject res = new JSONObject();
        res.put(
            "key",
            new BigInteger(((ByteString) m.get(new UnicodeString("bucket"))).getBytes())
                .toString());
        res.put(
            "value", new BigInteger(((ByteString) m.get(new UnicodeString("value"))).getBytes()));
        result.add(res);
      }
    } catch (CborException e) {
      e.printStackTrace();
    }
    return result;
  }
}

public final class RunE2ETest {
  @Test
  public void runE2ETests() throws Exception {
    File e2eDirectory = new File("e2e_tests");
    for (File e2eTestFile : e2eDirectory.listFiles()) {
      System.out.println("Running test for " + e2eTestFile.getName());
      testJsonFile(e2eTestFile);
    }
  }

  private void testJsonFile(File inFile) throws Exception {
    // Parse input file for expected reports
    JSONParser parser = new JSONParser();
    JSONObject fileData = (JSONObject) parser.parse(new FileReader(inFile));
    JSONObject inputs = (JSONObject) fileData.get("input");
    JSONArray sources = (JSONArray) inputs.get("sources");
    JSONArray triggers = (JSONArray) inputs.get("triggers");

    JSONObject outputs = (JSONObject) fileData.get("output");
    JSONArray eventLevelResults = (JSONArray) outputs.get("event_level_results");
    Set<JSONObject> expectedEventReports = new HashSet();
    for (Object eventLevelResult : eventLevelResults) {
      JSONObject payload = (JSONObject) ((JSONObject) eventLevelResult).get("payload");
      expectedEventReports.add(payload);
    }

    JSONArray aggregatableResults = (JSONArray) outputs.get("aggregatable_results");
    Set<JSONObject> expectedAggregatableReports = new HashSet();
    for (Object aggregatableResultObj : aggregatableResults) {
      // We are not comparing the source site in our outputs
      JSONObject aggregatableResult = (JSONObject) aggregatableResultObj;
      JSONObject payload = (JSONObject) aggregatableResult.get("payload");
      payload.remove("source_site");
      aggregatableResult.put("payload", payload);
      expectedAggregatableReports.add(aggregatableResult);
    }

    // Simulate Attribution Reporting API for a single user/device
    List<KV<String, Source>> sourceObjs = new ArrayList();
    List<KV<String, Trigger>> triggerObjs = new ArrayList();
    for (Object source : sources) {
      sourceObjs.add(KV.of("User1", SourceProcessor.buildSourceFromJson((JSONObject) source)));
    }
    for (Object trigger : triggers) {
      triggerObjs.add(KV.of("User1", TriggerProcessor.buildTriggerFromJson((JSONObject) trigger)));
    }
    SimulationRunner simulationRunner = new SimulationRunner();
    Pipeline p = Pipeline.create();
    PCollection<KV<String, Source>> userToAdtechSourceData = p.apply(Create.of(sourceObjs));
    PCollection<KV<String, Trigger>> userToAdtechTriggerData = p.apply(Create.of(triggerObjs));
    PCollection<KV<String, CoGbkResult>> joinedData =
        simulationRunner.joinSourceAndTriggerData(userToAdtechSourceData, userToAdtechTriggerData);

    Path tempDir = Files.createTempDirectory("AttributionJobHandlerTest");
    PCollection<JSONObject> aggregatableReports =
        simulationRunner.runUserSimulationInParallel(joinedData, tempDir.toString());

    // Match the output aggregatable reports
    PCollection<Boolean> matchingAggregatableReports =
        aggregatableReports.apply(
            ParDo.of(new GenerateAggregatableReport(expectedAggregatableReports)));
    List<Boolean> boolOutputs =
        new ArrayList<>(Collections.nCopies(expectedAggregatableReports.size(), true));
    PAssert.that(matchingAggregatableReports).containsInAnyOrder(boolOutputs);

    p.run().waitUntilFinish();

    // Match event level reports
    // Read event reports from the simulation library output
    Set<JSONObject> simLibEventReports = new HashSet();
    try {
      FileReader fileReader = new FileReader(tempDir.toString() + "/User1/event_reports.json");
      BufferedReader reader = new BufferedReader(fileReader);
      String line = reader.readLine();
      while (line != null) {
        JSONObject eventReport = (JSONObject) parser.parse(line);
        eventReport.remove("report_id");
        Long sourceEventId = (Long) eventReport.get("source_event_id");
        eventReport.put("source_event_id", sourceEventId.toString());
        Long triggerData = (Long) eventReport.get("trigger_data");
        eventReport.put("trigger_data", triggerData.toString());
        simLibEventReports.add(eventReport);
        line = reader.readLine();
      }
      reader.close();
      Assert.assertEquals(expectedEventReports.size(), simLibEventReports.size());
      Assert.assertEquals(expectedEventReports, simLibEventReports);
    } catch (FileNotFoundException e) {
      Assert.assertEquals(expectedEventReports.size(), 0);
    }
  }
}
