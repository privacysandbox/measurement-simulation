/*
 * Copyright 2025 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.measurement.client.MockRunner;
import com.google.measurement.client.data.DbTestUtil;
import com.google.measurement.client.data.SQLiteDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class MockRunnerTest {

  @After
  public void cleanup() {
    SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().getWritableDatabase();
    emptyTables(db);
  }

  @Test
  public void runnerMainTest() throws Exception {
    String input = getInput();
    String output = postprocessTestOutput(simulateRunner(input, new String[] {}));
    assertOutput(output);
  }

  @Test
  public void runnerMainTest_inputFromFile() throws Exception {
    Path tempDir = Files.createTempDirectory("tempfs-test");
    tempDir.toFile().deleteOnExit();

    // Create test files in the temporary directory
    Path testFile1 = Files.createFile(tempDir.resolve("input.json"));
    Files.writeString(testFile1, getInput());
    String[] args = new String[] {"--input_path", testFile1.toString()};

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);
    PrintStream sysOutBackup = System.out;
    System.setOut(printStream);

    // Execute
    MockRunner runner = new MockRunner();
    runner.run(args);

    String output = postprocessTestOutput(outputStream.toString(StandardCharsets.UTF_8));
    System.setOut(sysOutBackup);

    assertOutput(output);
  }

  private void assertOutput(String output) throws JSONException {
    JSONObject jsonOutput = new JSONObject(output);
    JSONArray eventReports = (JSONArray) jsonOutput.get("event_reports");
    assertThat(eventReports.length()).isEqualTo(1);
    assertEventReport(eventReports);
    assertThat(((JSONArray) jsonOutput.get("debug_event_reports")).length()).isEqualTo(0);
    assertThat(((JSONArray) jsonOutput.get("aggregate_reports")).length()).isEqualTo(0);
    assertThat(((JSONArray) jsonOutput.get("debug_aggregate_reports")).length()).isEqualTo(0);
    assertThat(((JSONArray) jsonOutput.get("debug_reports")).length()).isEqualTo(0);
  }

  private static void assertEventReport(JSONArray eventReports) throws JSONException {
    JSONObject eventReport = eventReports.getJSONObject(0);
    assertThat(eventReport.get("report_url")).isEqualTo("https://www.reporter.test");
    JSONObject payload = eventReport.getJSONObject("payload");
    assertThat(payload.get("attribution_destination")).isEqualTo("android-app://example.2d1.test");
    assertThat(payload.get("source_event_id")).isEqualTo("1");
    assertThat(payload.get("trigger_data")).isEqualTo("0");
    assertThat(payload.get("source_type")).isEqualTo("event");
  }

  static String postprocessTestOutput(String json) {
    return json.replaceAll("\\.com(?=[\"\\/,])", ".test");
  }

  private static String simulateRunner(String input, String[] args) throws Exception {
    // Simulate input stream
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);

    // Mock System.in
    // TODO (399678367): Create a wrapper for IO streams
    InputStream sysInBackup = System.in; // Backup System.in
    PrintStream sysOutBackup = System.out;

    System.setIn(inputStream);
    System.setOut(printStream);

    // Execute
    MockRunner runner = new MockRunner();
    runner.run(args);

    String output = outputStream.toString(StandardCharsets.UTF_8);
    // Restore System.in and System.out
    System.setIn(sysInBackup);
    System.setOut(sysOutBackup);

    return output;
  }

  private static String getInput() {
    String input =
        """
        {
          "input": {
            "sources": [
              {
                "registration_request": {
                  "source_type": "event",
                  "registrant": "example.1s1.test"
                },
                "responses": [
                  {
                    "url": "https://www.reporter.test",
                    "response": {
                      "Attribution-Reporting-Register-Source": {
                        "source_event_id": "1",
                        "destination": "android-app://example.2d1.test",
                        "priority": "100",
                        "expiry": "2592000"
                      },
                      "Location": null,
                      "Attribution-Reporting-Redirect": null
                    }
                  }
                ],
                "timestamp": "800000000001"
              }
            ],
            "triggers": [
              {
                "registration_request": {
                  "registrant": "example.2d1.test"
                },
                "responses": [
                  {
                    "url": "https://www.reporter.test",
                    "response": {
                      "Attribution-Reporting-Register-Trigger": {
                        "event_trigger_data": [
                          {
                            "trigger_data": "2",
                            "priority": "101"
                          }
                        ]
                      },
                      "Location": null,
                      "Attribution-Reporting-Redirect": null
                    }
                  }
                ],
                "timestamp": "800000600001"
              }
            ]
          }
        }
        """;

    return input;
  }

  private static void emptyTables(SQLiteDatabase db) {
    db.delete("msmt_source", null, null);
    db.delete("msmt_trigger", null, null);
    db.delete("msmt_event_report", null, null);
    db.delete("msmt_attribution", null, null);
    db.delete("msmt_aggregate_report", null, null);
    db.delete("msmt_async_registration_contract", null, null);
  }
}
