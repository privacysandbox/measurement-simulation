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

import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * PTransform for taking a PCollection of simulation outputs keyed by the device ids they came from,
 * and selecting a specific JSON object from that output.
 */
public class ExtractJsonFieldTransform
    extends PTransform<PCollection<KV<String, String>>, PCollection<KV<String, String>>> {

  private final String fieldName;

  public ExtractJsonFieldTransform(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  public PCollection<KV<String, String>> expand(PCollection<KV<String, String>> input) {
    return input.apply("ExtractJsonField", MapElements.via(new ExtractJsonFieldDoFn(fieldName)));
  }

  private static class ExtractJsonFieldDoFn
      extends SimpleFunction<KV<String, String>, KV<String, String>> {
    private final String fieldName;

    public ExtractJsonFieldDoFn(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public KV<String, String> apply(KV<String, String> input) {
      String filename = input.getKey();
      String json = input.getValue();

      String extractedJson = "";
      try {
        JSONObject jsonObject = new JSONObject(json);
        extractedJson = jsonObject.getString(fieldName);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }

      return KV.of(filename, extractedJson);
    }
  }
}
