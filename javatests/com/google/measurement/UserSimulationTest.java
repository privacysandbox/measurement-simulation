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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.measurement.util.UnsignedLong;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserSimulationTest {
  @Mock IMeasurementDAO measurementDAO;

  @Test
  public void exportReportTest() throws IOException, ParseException {
    // No pending triggers means no calls to AttributionJobHandler.performAttribution();
    when(measurementDAO.getPendingTriggers()).thenReturn(List.of());
    when(measurementDAO.getAllEventReports()).thenReturn(List.of(createEventReport()));

    // Create temp dir and pass that into UserSimulation
    Path tempdir = Files.createTempDirectory("AttributionJobHandlerTest");
    UserSimulation userSimulation = new UserSimulation("U1", tempdir.toString());
    userSimulation.runSimulation(measurementDAO, null);

    // Check if folder/file is created in tempdir
    Path reportPath = tempdir.resolve("U1/event_reports.json");
    assertTrue(Files.exists(reportPath));

    // Check if created report matches what it should be
    String reportContents = Files.readString(reportPath);
    String correctReportContents =
        "{\"report_id\":\"1\",\"scheduled_report_time\":\"2\",\"source_event_id\":21,\"source_type\":\"NAVIGATION\",\"randomized_trigger_rate\":0.0,\"attribution_destination\":\"http:\\/\\/bar.com\",\"trigger_data\":8}\n";

    JsonElement reportJson = JsonParser.parseString(reportContents);
    JsonElement expectedJson = JsonParser.parseString(correctReportContents);

    assertEquals(expectedJson, reportJson);

    // Confirm a second call deletes previous contents
    userSimulation.runSimulation(measurementDAO, null);
    reportContents = Files.readString(reportPath);

    reportJson = JsonParser.parseString(reportContents);
    expectedJson = JsonParser.parseString(correctReportContents);
    assertEquals(expectedJson, reportJson);

    // Check if multiple reports are handled correctly
    when(measurementDAO.getAllEventReports())
        .thenReturn(List.of(createEventReport(), createEventReport()));
    userSimulation.runSimulation(measurementDAO, null);
    reportContents = Files.readString(reportPath);

    JsonArray reportArray = new JsonArray();
    String[] reports = reportContents.split("\n");
    for (String report : reports) {
      reportArray.add(JsonParser.parseString(report));
    }

    JsonArray expectedArray = new JsonArray();
    expectedArray.add(JsonParser.parseString(correctReportContents));
    expectedArray.add(JsonParser.parseString(correctReportContents));

    assertEquals(expectedArray, reportArray);
  }

  private EventReport createEventReport() {
    return new EventReport.Builder()
        .setId("1")
        .setSourceEventId(new UnsignedLong(21L))
        .setEnrollmentId("http://foo.com")
        .setAttributionDestinations(Arrays.asList(URI.create("http://bar.com")))
        .setTriggerTime(1000L)
        .setTriggerData(new UnsignedLong(8L))
        .setTriggerPriority(2L)
        .setTriggerDedupKey(new UnsignedLong(3L))
        .setReportTime(2000L)
        .setStatus(EventReport.Status.PENDING)
        .setSourceType(Source.SourceType.NAVIGATION)
        .build();
  }
}
