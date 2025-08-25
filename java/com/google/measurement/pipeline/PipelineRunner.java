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
import java.nio.file.Paths;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.GroupByKey;

public final class PipelineRunner {
  public static void main(String[] args) throws IOException {
    var options = PipelineOptionsFactory.fromArgs(args).withValidation().as(PipelineOptions.class);
    var pipeline = Pipeline.create(options);

    RunfilePaths runfilePaths = new RunfilePaths(options);

    String inputPath;
    if (options.getInputDirectory() != null && !options.getInputDirectory().trim().isEmpty()) {
      inputPath = Paths.get(options.getInputDirectory()).resolve("*.json").toString();
    } else {
      inputPath = runfilePaths.getTestdataPath();
    }

    var clientSimulationOutput =
        pipeline
            .apply("IngestClientSimulationInput", new FileInputTransform(inputPath))
            .apply(
                "ExecuteClientSimulation",
                new JarExecutorTransform(new String[] {runfilePaths.getMockRunnerJarPath()}));

    clientSimulationOutput
        .apply("ExtractEventReports", new ExtractJsonFieldTransform("event_reports"))
        .apply(
            "WriteEventReports", new FileOutputTransform(options.getEventReportsOutputDirectory()));

    clientSimulationOutput
        .apply("ExtractAggregatableReports", new ExtractJsonFieldTransform("aggregate_reports"))
        .apply("KeyByDestinationAndDay", new KeyByDestinationAndDayTransform())
        .apply("GroupByDestinationAndDay", GroupByKey.create())
        .apply("GroupValuesToJsonArray", new GroupToJsonArrayTransform())
        .apply(
            "ExecuteAggregationService",
            new AggregationServiceTransform(
                runfilePaths.getLocalTestingToolJarPath(),
                runfilePaths.getAggregatableReportConverterJarPath(),
                options.getAggregatableReportsOutputDirectory(),
                options.getAggregationResultsDirectory()));

    pipeline.run().waitUntilFinish();
  }

  private static class RunfilePaths {
    private static final String LOCAL_TESTING_TOOL_VERSION = "1.0.2";
    private Runfiles runfiles = null;
    private String runfilesDirectory = null;

    private RunfilePaths(PipelineOptions opts) {
      if (opts.getRunfilesDirectory() == null) {
        try {
          runfiles = Runfiles.create();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        runfilesDirectory = opts.getRunfilesDirectory();
      }
    }

    public String getTestdataPath() {
      return runfiles.rlocation("_main/java/com/google/measurement/pipeline/testdata/*.json");
    }

    public String getMockRunnerJarPath() {
      String name = "MockRunner_deploy.jar";

      return runfiles == null
          ? runfilesDirectory + "/" + name
          : runfiles.rlocation("_main/" + name);
    }

    public String getAggregatableReportConverterJarPath() {
      String name = "AggregatableReportConverter_deploy.jar";

      return runfiles == null
          ? runfilesDirectory + "/" + name
          : runfiles.rlocation("_main/" + name);
    }

    public String getLocalTestingToolJarPath() {
      String suffix = "lib/LocalTestingTool_" + LOCAL_TESTING_TOOL_VERSION + ".jar";

      return runfiles == null
          ? runfilesDirectory + "/" + suffix
          : runfiles.rlocation("_main/" + suffix);
    }
  }
}
