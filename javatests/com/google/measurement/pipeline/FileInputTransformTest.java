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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;

public class FileInputTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testFileInputTransform() throws IOException {
    Path tempDir = Files.createTempDirectory("tempfs-test");
    tempDir.toFile().deleteOnExit(); // Ensure directory is deleted after test

    // Create test files in the temporary directory
    Path testFile1 = Files.createFile(tempDir.resolve("file1.txt"));
    Files.writeString(testFile1, "This is file 1 content.");
    Path testFile2 = Files.createFile(tempDir.resolve("file2.txt"));
    Files.writeString(testFile2, "This is file 2 content.");

    // Define the input file pattern (relative to the temporary directory)
    String inputFilePattern = tempDir.resolve("*.txt").toString();

    // Apply the FileInputTransform
    PCollection<KV<String, String>> output =
        pipeline.apply(new FileInputTransform(inputFilePattern));

    // Define the expected output (use the actual filenames in the temporary directory)
    List<KV<String, String>> expectedOutput =
        Arrays.asList(
            KV.of("file1", "This is file 1 content."), KV.of("file2", "This is file 2 content."));

    // Assert that the output matches the expected output
    PAssert.that(output).containsInAnyOrder(expectedOutput);

    // Run the pipeline
    pipeline.run();
  }
}
