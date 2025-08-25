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

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A PTransform for taking a PCollection of groups of json AggregatableReports keyed by (attribution
 * destination x schedule report time) and consolidating the groups into JSON arrays, which is what
 * is required by the AggregatableReportConverter tool.
 */
public class GroupToJsonArrayTransform
    extends PTransform<PCollection<KV<String, Iterable<String>>>, PCollection<KV<String, String>>> {

  @Override
  public PCollection<KV<String, String>> expand(PCollection<KV<String, Iterable<String>>> input) {
    return input.apply("GroupToJsonArrayDoFn", ParDo.of(new GroupToJsonArrayFn()));
  }

  private static class GroupToJsonArrayFn
      extends DoFn<KV<String, Iterable<String>>, KV<String, String>> {
    @ProcessElement
    public void processElement(
        @Element KV<String, Iterable<String>> input, OutputReceiver<KV<String, String>> out)
        throws JSONException {
      Iterable<String> values = input.getValue();

      JSONArray jsonArray = new JSONArray();
      for (String value : values) {
        jsonArray.put(new JSONObject(value));
      }

      out.output(KV.of(input.getKey(), jsonArray.toString()));
    }
  }
}
