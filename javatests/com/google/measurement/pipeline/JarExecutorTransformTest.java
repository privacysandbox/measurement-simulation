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
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;

public class JarExecutorTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testJarExecutorTransform_noFlags() {
    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("file1.txt", "This is file 1 content."),
            KV.of("file2.txt", "This is file 2 content."));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    String[] arguments = new String[] {getJarPath()};

    PCollection<KV<String, String>> output = input.apply(new JarExecutorTransform(arguments));

    List<KV<String, String>> expectedOutput =
        Arrays.asList(
            KV.of("file1.txt", "This is file 1 content.\n"),
            KV.of("file2.txt", "This is file 2 content.\n"));

    PAssert.that(output).containsInAnyOrder(expectedOutput);

    pipeline.run();
  }

  @Test
  public void testJarExecutorTransform_withFlags() {
    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("file1.txt", "This is file 1 content."),
            KV.of("file2.txt", "This is file 2 content."));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    String[] arguments = new String[] {getJarPath(), "--flag1", "value1", "--flag2", "value2"};

    PCollection<KV<String, String>> output = input.apply(new JarExecutorTransform(arguments));

    String expectedFlags = "--flag1\nvalue1\n--flag2\nvalue2\n";
    List<KV<String, String>> expectedOutput =
        Arrays.asList(
            KV.of("file1.txt", "This is file 1 content.\n" + expectedFlags),
            KV.of("file2.txt", "This is file 2 content.\n" + expectedFlags));

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
