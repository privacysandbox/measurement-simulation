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

package com.google.measurement.pipeline;

import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

public class KeyByDestinationAndDayTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testKeyByDestinationAndDayTransform() throws JSONException {
    long timestamp1 = 1700000000000L; // 11/14/2023 22:13:20
    long timestamp2 = 1700006280000L; // 11/14/2023 23:58:00
    JSONObject aggReport1 = createAggReport("advertiser1", timestamp1);
    JSONObject aggReport2 = createAggReport("advertiser2", timestamp1);
    JSONObject aggReport3 = createAggReport("advertiser1", timestamp2);
    JSONObject aggReport4 = createAggReport("advertiser2", timestamp2);

    JSONArray user1Reports = new JSONArray();
    user1Reports.put(aggReport1);
    user1Reports.put(aggReport2);

    JSONArray user2Reports = new JSONArray();
    user2Reports.put(aggReport3);
    user2Reports.put(aggReport4);

    List<KV<String, String>> inputData =
        Arrays.asList(KV.of("U1", user1Reports.toString()), KV.of("U2", user2Reports.toString()));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    PCollection<KV<String, String>> output = input.apply(new KeyByDestinationAndDayTransform());

    long expectedTimestamp = 1699920000000L; // 11/14/2023 00:00:00

    List<KV<String, String>> expectedOutput =
        Arrays.asList(
            KV.of("advertiser1_" + expectedTimestamp, aggReport1.toString()),
            KV.of("advertiser1_" + expectedTimestamp, aggReport3.toString()),
            KV.of("advertiser2_" + expectedTimestamp, aggReport2.toString()),
            KV.of("advertiser2_" + expectedTimestamp, aggReport4.toString()));

    PAssert.that(output).containsInAnyOrder(expectedOutput);

    pipeline.run();
  }

  private JSONObject createAggReport(String attributionDestination, long scheduledReportTime)
      throws JSONException {
    JSONObject sharedInfo = new JSONObject();
    sharedInfo.put("attribution_destination", attributionDestination);
    sharedInfo.put("scheduled_report_time", scheduledReportTime);

    JSONObject aggReport = new JSONObject();
    aggReport.put("shared_info", sharedInfo);
    return aggReport;
  }
}
