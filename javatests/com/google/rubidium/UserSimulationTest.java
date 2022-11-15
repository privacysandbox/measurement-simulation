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

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserSimulationTest {

  @Mock IMeasurementDAO measurementDAO;

  @Test
  public void exportReportTest() throws IOException {
    // No pending triggers means no calls to AttributionJobHandler.performAttribution();
    when(measurementDAO.getPendingTriggers()).thenReturn(List.of());
    when(measurementDAO.getAllEventReports()).thenReturn(List.of(createEventReport()));

    // Create temp dir and pass that into UserSimulation
    Path tempdir = Files.createTempDirectory("AttributionJobHandlerTest");
    UserSimulation userSimulation = new UserSimulation("U1", tempdir.toString());
    userSimulation.runSimulation(measurementDAO);

    // Check if folder/file is created in tempdir
    Path reportPath = tempdir.resolve("U1/event_reports.json");
    Assert.assertTrue(Files.exists(reportPath));

    // Check if created report matches what it should be
    String reportContents = Files.readString(reportPath);
    String correctReportContents =
        "{\"report_id\":\"1\",\"source_event_id\":21,\"source_type\":\"NAVIGATION\","
            + "\"randomized_trigger_rate\":0.0,\"attribution_destination\":\"http:\\/\\/bar.com\","
            + "\"trigger_data\":8}\n";

    Assert.assertEquals(reportContents, correctReportContents);

    // Confirm a second call deletes previous contents
    userSimulation.runSimulation(measurementDAO);
    reportContents = Files.readString(reportPath);
    Assert.assertEquals(reportContents, correctReportContents);

    // Check if multiple reports are handled correctly
    when(measurementDAO.getAllEventReports())
        .thenReturn(List.of(createEventReport(), createEventReport()));
    userSimulation.runSimulation(measurementDAO);
    reportContents = Files.readString(reportPath);
    correctReportContents = correctReportContents + correctReportContents;
    Assert.assertEquals(reportContents, correctReportContents);
  }

  private EventReport createEventReport() {
    return new EventReport.Builder()
        .setId("1")
        .setSourceId(21)
        .setEnrollmentId("http://foo.com")
        .setAttributionDestination(URI.create("http://bar.com"))
        .setTriggerTime(1000L)
        .setTriggerData(8L)
        .setTriggerPriority(2L)
        .setTriggerDedupKey(3L)
        .setReportTime(2000L)
        .setStatus(EventReport.Status.PENDING)
        .setSourceType(Source.SourceType.NAVIGATION)
        .build();
  }
}
