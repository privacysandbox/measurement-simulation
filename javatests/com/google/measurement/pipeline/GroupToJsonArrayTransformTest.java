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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.JSONArray;
import org.junit.Rule;
import org.junit.Test;

public class GroupToJsonArrayTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testGroupToJsonArrayTransform() {
    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("key1", "{\"value1a\":\"\"}"),
            KV.of("key2", "{\"value2a\":\"\"}"),
            KV.of("key1", "{\"value1b\":\"\"}"),
            KV.of("key2", "{\"value2b\":\"\"}"),
            KV.of("key1", "{\"value1c\":\"\"}"));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    PCollection<KV<String, Iterable<String>>> groupedData = input.apply(GroupByKey.create());

    PCollection<KV<String, String>> output = groupedData.apply(new GroupToJsonArrayTransform());

    PAssert.that(output)
        .satisfies(
            actual -> {
              // Use a set for asserting to make it order invariant.
              Map<String, Set<String>> expectedOutput =
                  Map.of(
                      "key1",
                      Set.of("{\"value1a\":\"\"}", "{\"value1b\":\"\"}", "{\"value1c\":\"\"}"),
                      "key2",
                      Set.of("{\"value2a\":\"\"}", "{\"value2b\":\"\"}"));

              for (KV<String, String> kv : actual) {
                assertTrue(expectedOutput.containsKey(kv.getKey()));
                try {
                  JSONArray actualArray = new JSONArray(kv.getValue());
                  Set<String> actualValues = new HashSet<>();
                  for (int i = 0; i < actualArray.length(); i++) {
                    actualValues.add(actualArray.get(i).toString());
                  }

                  assertEquals(expectedOutput.get(kv.getKey()), actualValues);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }

              return null;
            });
    // Run the pipeline
    pipeline.run();
  }
}
