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

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Transforms for converting rows from input files to Trigger or Source objects. */
public class InputFileProcessor {

  /**
   * Transform to create a KV pair of userIds and Source objects. Creates the Source object from a
   * JSON row.
   */
  public static class AttributionSourceJsonMapperDoFn extends DoFn<String, KV<String, Source>> {
    @ProcessElement
    public void processElement(@Element String input, OutputReceiver<KV<String, Source>> out) {
      JSONParser parser = new JSONParser();
      try {
        Object obj = parser.parse(input);
        JSONObject jsonObject = (JSONObject) obj;
        Source source = SourceProcessor.buildSourceFromJson(jsonObject);
        String userId = (String) jsonObject.get("user_id");
        out.output(KV.of(userId, source));
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException(
            String.format("Failed to parse the source input: %s", input));
      }
    }
  }

  /**
   * Transform to create a KV pair of userIds and Trigger objects. Creates the Trigger object from a
   * JSON row.
   */
  public static class TriggerJsonMapperDoFn extends DoFn<String, KV<String, Trigger>> {
    @ProcessElement
    public void processElement(@Element String input, OutputReceiver<KV<String, Trigger>> out) {
      JSONParser parser = new JSONParser();
      try {
        Object obj = parser.parse(input);
        JSONObject jsonObject = (JSONObject) obj;
        Trigger trigger = TriggerProcessor.buildTriggerFromJson(jsonObject);
        String userId = (String) jsonObject.get("user_id");
        out.output(KV.of(userId, trigger));
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException(
            String.format("Failed to parse the trigger input: %s", input));
      }
    }
  }
}
