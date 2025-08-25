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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileOutputTransformTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testFileOutputTransform() throws IOException {
    File outputDir = temporaryFolder.newFolder();
    String outputDirPath = outputDir.getAbsolutePath();

    List<KV<String, String>> inputData =
        Arrays.asList(
            KV.of("key1", "This is file 1 content."), KV.of("key2", "This is file 2 content."));

    PCollection<KV<String, String>> input = pipeline.apply(Create.of(inputData));

    input.apply(new FileOutputTransform(outputDirPath));

    pipeline.run().waitUntilFinish();

    Path file1Path = Path.of(outputDirPath, "key1.json");
    Path file2Path = Path.of(outputDirPath, "key2.json");

    assertEquals("This is file 1 content.\n", Files.readString(file1Path));
    assertEquals("This is file 2 content.\n", Files.readString(file2Path));
  }
}
