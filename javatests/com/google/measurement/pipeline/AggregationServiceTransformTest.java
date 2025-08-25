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

import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;

public class AggregationServiceTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testJarExecutorTransform_withFlags() throws IOException {
    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("key1", "This is file 1 content."), KV.of("key2", "This is file 2 content."));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    Path tempDir = Files.createTempDirectory("tempfs-test");
    tempDir.toFile().deleteOnExit();
    String aggregationResultOutputPrefix = tempDir.toString();
    String aggregatableReportOutputPrefix = "/tmp/input";

    PCollection<KV<String, String>> output =
        input.apply(
            new AggregationServiceTransform(
                getJarPath(),
                getJarPath(),
                aggregatableReportOutputPrefix,
                aggregationResultOutputPrefix));

    String key1ExpectedOutput =
        "This is file 1 content.\n"
            + "--skip_domain\n"
            + "--json_output\n"
            + "--input_data_avro_file\n"
            + "/tmp/input/key1/aggregatable_reports.avro\n"
            + "--output_directory\n"
            + aggregationResultOutputPrefix
            + "/key1\n";

    String key2ExpectedOutput =
        "This is file 2 content.\n"
            + "--skip_domain\n"
            + "--json_output\n"
            + "--input_data_avro_file\n"
            + "/tmp/input/key2/aggregatable_reports.avro\n"
            + "--output_directory\n"
            + aggregationResultOutputPrefix
            + "/key2\n";

    List<KV<String, String>> expectedOutput =
        Arrays.asList(KV.of("key1", key1ExpectedOutput), KV.of("key2", key2ExpectedOutput));

    PAssert.that(output).containsInAnyOrder(expectedOutput);

    pipeline.run();
  }

  private String getJarPath() {
    try {
      Runfiles runfiles = Runfiles.create();
      return runfiles.rlocation("_main/Echo_deploy.jar");
    } catch (Exception e) {
      throw new RuntimeException("Failed to locate test JAR", e);
    }
  }
}
