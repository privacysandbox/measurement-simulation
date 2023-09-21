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

import static com.google.measurement.util.BaseUriExtractor.getBaseUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.google.measurement.noising.SourceNoiseHandler;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

@DefaultCoder(AvroCoder.class)
class GenerateAggregatableReport extends DoFn<JSONObject, Boolean> {
  private List<JSONObject> expectedAggregatableReports;

  GenerateAggregatableReport(List<JSONObject> expectedAggregatableReports) {
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
    long expectedReportTime = Long.parseUnsignedLong((String) expectedReport.get("report_time"));
    if (actualReportTime < (expectedReportTime - PrivacyParams.AGGREGATE_MAX_REPORT_DELAY)
        || actualReportTime >= (expectedReportTime + PrivacyParams.AGGREGATE_MAX_REPORT_DELAY)) {
      return false;
    }

    String expectedBaseURI =
        getBaseUri(URI.create((String) expectedReport.get("report_url"))).toString();
    String actualReportBaseURI =
        getBaseUri(URI.create((String) actualReport.get("report_url"))).toString();
    if (!actualReportBaseURI.equals(expectedBaseURI)) {
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
            new BigInteger(1, ((ByteString) m.get(new UnicodeString("bucket"))).getBytes())
                .toString(10));
        res.put(
            "value",
            new BigInteger(1, ((ByteString) m.get(new UnicodeString("value"))).getBytes())
                .intValue());
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
    // 1. Run Measurement e2e tests
    File e2eDirectory = new File("e2e_tests/msmt_e2e_tests");
    for (File e2eTestFile : e2eDirectory.listFiles()) {
      System.out.println("Running test for " + e2eTestFile.getName());
      testJsonFile(e2eTestFile);
    }

    // 2. Run Measurement e2e noise tests
    e2eDirectory = new File("e2e_tests/msmt_e2e_noise_tests");
    for (File e2eTestFile : e2eDirectory.listFiles()) {
      System.out.println("Running test for " + e2eTestFile.getName());
      testJsonFile(e2eTestFile);
    }

    // 3. Run Measurement interop tests
    e2eDirectory = new File("e2e_tests/msmt_interop_tests");
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

    JSONArray installs = new JSONArray();
    if (inputs.containsKey("installs")) {
      installs = (JSONArray) inputs.get("installs");
    }
    JSONArray uninstalls = new JSONArray();
    if (inputs.containsKey("uninstalls")) {
      uninstalls = (JSONArray) inputs.get("uninstalls");
    }

    JSONObject outputs = (JSONObject) fileData.get("output");
    List<JSONObject> expectedEventReports = getExpectedEventReports(outputs);
    List<JSONObject> expectedAggregatableReports = getExpectedAggregatableReports(outputs);

    // Simulate Attribution Reporting API for a single user/device
    List<Source> sourceObjs = new ArrayList<>();
    List<Trigger> triggerObjs = new ArrayList<>();
    List<ExtensionEvent> extensionEventObjs = new ArrayList<>();
    for (Object source : sources) {
      try {
        sourceObjs.add(SourceProcessor.buildSourceFromJson((JSONObject) source));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    for (Object trigger : triggers) {
      triggerObjs.add(TriggerProcessor.buildTriggerFromJson((JSONObject) trigger));
    }
    for (Object install : installs) {
      JSONObject eventInstall = (JSONObject) install;
      eventInstall.put("action", "install");
      extensionEventObjs.add(ExtensionEvent.buildExtensionEventFromJson((JSONObject) install));
    }
    for (Object uninstall : uninstalls) {
      JSONObject eventUnInstall = (JSONObject) uninstall;
      eventUnInstall.put("action", "uninstall");
      extensionEventObjs.add(ExtensionEvent.buildExtensionEventFromJson((JSONObject) uninstall));
    }

    Pipeline p = Pipeline.create();

    Path tempDir = Files.createTempDirectory("E2ETest");

    SourceNoiseHandler sourceNoiseHandler = getSourceNoiseHandler();

    UserSimulation userSimulation =
        new UserSimulation("User1", tempDir.toString(), sourceNoiseHandler);

    PCollection<JSONObject> aggregatableReports =
        getAggregatableReports(sourceObjs, triggerObjs, extensionEventObjs, p, userSimulation);

    // Match the output aggregatable reports
    PCollection<Boolean> matchingAggregatableReports =
        aggregatableReports.apply(
            ParDo.of(new GenerateAggregatableReport(expectedAggregatableReports)));
    List<Boolean> boolOutputs =
        new ArrayList<>(Collections.nCopies(expectedAggregatableReports.size(), true));
    PAssert.that(matchingAggregatableReports).containsInAnyOrder(boolOutputs);

    p.run().waitUntilFinish();

    assertEventReports(parser, expectedEventReports, tempDir);
  }

  // Match event level reports
  // Read event reports from the simulation library output
  private static void assertEventReports(
      JSONParser parser, List<JSONObject> expectedEventReports, Path tempDir)
      throws IOException, ParseException {
    List<JSONObject> simLibEventReports = new ArrayList<>();
    try {
      FileReader fileReader = new FileReader(tempDir + "/User1/event_reports.json");
      BufferedReader reader = new BufferedReader(fileReader);
      String line = reader.readLine();
      while (line != null) {
        JSONObject eventReport = (JSONObject) parser.parse(line);
        eventReport.remove("report_id");
        eventReport.put("source_event_id", eventReport.get("source_event_id").toString());
        Long triggerData = (Long) eventReport.get("trigger_data");
        eventReport.put("trigger_data", triggerData.toString());
        eventReport.put("source_type", ((String) eventReport.get("source_type")).toLowerCase());
        if (eventReport.containsKey("trigger_summary_bucket")) {
          eventReport.put(
              "trigger_summary_bucket",
              ((String) eventReport.get("trigger_summary_bucket")).toLowerCase());
        }
        simLibEventReports.add(eventReport);
        line = reader.readLine();
      }
      reader.close();
      assertEquals(expectedEventReports.size(), simLibEventReports.size());
      assertThat(simLibEventReports, containsInAnyOrder(expectedEventReports.toArray()));
    } catch (FileNotFoundException e) {
      assertEquals(expectedEventReports.size(), 0);
    }
  }

  private static PCollection<JSONObject> getAggregatableReports(
      List<Source> sourceObjs,
      List<Trigger> triggerObjs,
      List<ExtensionEvent> extensionEventObjs,
      Pipeline p,
      UserSimulation userSimulation)
      throws ParseException {
    List<JSONObject> aggregatableReportsList =
        userSimulation.runSimulation(sourceObjs, triggerObjs, extensionEventObjs);
    PCollection<JSONObject> aggregatableReports;
    if (aggregatableReportsList == null || aggregatableReportsList.isEmpty()) {
      aggregatableReports = p.apply(Create.empty(TypeDescriptor.of(JSONObject.class)));
    } else {
      aggregatableReports = p.apply(Create.of(aggregatableReportsList));
    }
    return aggregatableReports;
  }

  /*
   * Mock the source noise handler so that all tests run deterministically.
   */
  private static SourceNoiseHandler getSourceNoiseHandler() {
    SourceNoiseHandler sourceNoiseHandler = spy(new SourceNoiseHandler(new Flags()));

    doReturn(Collections.emptyList())
        .when(sourceNoiseHandler)
        .assignAttributionModeAndGenerateFakeReports(any(Source.class));
    return sourceNoiseHandler;
  }

  private static List<JSONObject> getExpectedAggregatableReports(JSONObject outputs) {
    JSONArray aggregatableResults =
        (outputs.containsKey("aggregatable_results"))
            ? (JSONArray) outputs.get("aggregatable_results")
            : new JSONArray();
    List<JSONObject> expectedAggregatableReports = new ArrayList<>();
    for (Object aggregatableResultObj : aggregatableResults) {
      // We are not comparing the source site in our outputs
      JSONObject aggregatableResult = (JSONObject) aggregatableResultObj;
      JSONObject payload = (JSONObject) aggregatableResult.get("payload");
      payload.remove("source_site");
      aggregatableResult.put("payload", payload);
      expectedAggregatableReports.add(aggregatableResult);
    }
    return expectedAggregatableReports;
  }

  private static List<JSONObject> getExpectedEventReports(JSONObject outputs) {
    JSONArray eventLevelResults =
        (outputs.containsKey("event_level_results"))
            ? (JSONArray) outputs.get("event_level_results")
            : new JSONArray();
    List<JSONObject> expectedEventReports = new ArrayList<>();
    for (Object eventLevelResult : eventLevelResults) {
      JSONObject report = (JSONObject) ((JSONObject) eventLevelResult).get("payload");
      if (report.containsKey("source_debug_key")) {
        report.remove("source_debug_key");
      }
      if (report.containsKey("trigger_debug_key")) {
        report.remove("trigger_debug_key");
      }
      expectedEventReports.add(report);
    }
    return expectedEventReports;
  }
}
