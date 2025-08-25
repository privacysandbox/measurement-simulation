/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.runfiles.Runfiles;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PipelineRunnerTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testPipelineRunner() throws Exception {
    // Create temporary directories for pipeline I/O.
    File inputDir = folder.newFolder("input");
    File eventReportsOutputDir = folder.newFolder("event_reports");
    File aggregatableReportsOutputDir = folder.newFolder("aggregatable_reports");
    File aggregationResultsDir = folder.newFolder("aggregation_results");

    // Copy test data to the temporary input directory.
    Runfiles runfiles = Runfiles.create();
    String testDataPath =
        runfiles.rlocation("_main/java/com/google/measurement/pipeline/testdata/U1.json");
    Files.copy(Paths.get(testDataPath), new File(inputDir, "U1.json").toPath());
    String testDataPath2 =
        runfiles.rlocation("_main/java/com/google/measurement/pipeline/testdata/U2.json");
    Files.copy(Paths.get(testDataPath2), new File(inputDir, "U2.json").toPath());

    // Run the pipeline.
    PipelineRunner.main(
        new String[] {
          "--inputDirectory=" + inputDir.getAbsolutePath(),
          "--eventReportsOutputDirectory=" + eventReportsOutputDir.getAbsolutePath(),
          "--aggregatableReportsOutputDirectory=" + aggregatableReportsOutputDir.getAbsolutePath(),
          "--aggregationResultsDirectory=" + aggregationResultsDir.getAbsolutePath(),
        });

    // Verify that the output directories are not empty.
    List<String> eventReports =
        Arrays.stream(eventReportsOutputDir.list())
            .filter(s -> !s.startsWith(".temp-beam"))
            .collect(Collectors.toList());
    assertThat(eventReports).containsExactly("U1.json", "U2.json");
    assertThat(aggregatableReportsOutputDir.list()).isNotEmpty();
    assertThat(aggregationResultsDir.list()).isNotEmpty();
  }
}
