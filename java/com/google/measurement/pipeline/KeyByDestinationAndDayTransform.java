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

import java.util.concurrent.TimeUnit;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A PTransform for taking a PCollection of groups of AggregatableReports keyed by the device ids
 * that they came from and rekeying those groups to a composite key built from the attribution
 * destination and unix timestamp of the scheduled report time rounded down to the day.
 *
 * <p>This PTransform is one example of how ad-techs can group their Aggregatable Reports before
 * sending them to the Aggregation Service. Other methods can have different granularity for
 * grouping, e.g. all reports for a given week or destination prefix.
 */
public class KeyByDestinationAndDayTransform
    extends PTransform<PCollection<KV<String, String>>, PCollection<KV<String, String>>> {

  @Override
  public PCollection<KV<String, String>> expand(PCollection<KV<String, String>> input) {
    return input.apply("KeyByDestinationAndDayDoFn", ParDo.of(new KeyByDestinationAndDayDoFn()));
  }

  private static class KeyByDestinationAndDayDoFn
      extends DoFn<KV<String, String>, KV<String, String>> {
    @ProcessElement
    public void processElement(
        @Element KV<String, String> input, OutputReceiver<KV<String, String>> out)
        throws JSONException {
      JSONArray aggReports = new JSONArray(input.getValue());

      for (int i = 0; i < aggReports.length(); i++) {
        JSONObject aggReport = aggReports.getJSONObject(i);
        JSONObject sharedInfo = new JSONObject(aggReport.get("shared_info").toString());
        String advertiser =
            sharedInfo.getString("attribution_destination").replaceFirst("^android-app://", "");
        long dateTimestamp = roundDownToDay(sharedInfo.getLong("scheduled_report_time"));
        String key = advertiser + "_" + dateTimestamp;
        out.output(KV.of(key, aggReport.toString()));
      }
    }
  }

  private static long roundDownToDay(long timestamp) {
    return Math.floorDiv(timestamp, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
  }
}
