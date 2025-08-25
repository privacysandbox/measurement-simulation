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
import org.junit.Rule;
import org.junit.Test;

public class ExtractJsonFieldTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testExtractJsonFieldTransform() {
    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("key1", "{\"name\":\"value1\", \"age\":30}"),
            KV.of("key2", "{\"name\":\"value2\", \"city\":\"New York\"}"));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    PCollection<KV<String, String>> output = input.apply(new ExtractJsonFieldTransform("name"));

    List<KV<String, String>> expectedOutput =
        Arrays.asList(KV.of("key1", "value1"), KV.of("key2", "value2"));

    PAssert.that(output).containsInAnyOrder(expectedOutput);

    pipeline.run();
  }
}
