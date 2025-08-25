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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.values.KV;

public class AggregationServiceDoFn extends JarExecutorDoFn {
  private final String[] localTestingToolArguments;
  private final String[] aggregatableReportConverterArguments;
  private final String aggregatableReportConverterOutputPrefix;
  private final String aggregationResultOutputPrefix;
  private final String aggregatableReportsFileName = "aggregatable_reports.avro";

  public AggregationServiceDoFn(
      String[] localTestingToolArguments,
      String[] aggregatableReportConverterArguments,
      String aggregatableReportOutputPrefix,
      String aggregationResultOutputPrefix) {
    super(aggregatableReportConverterArguments);
    this.localTestingToolArguments = localTestingToolArguments;
    this.aggregatableReportConverterArguments = aggregatableReportConverterArguments;
    this.aggregatableReportConverterOutputPrefix = aggregatableReportOutputPrefix;
    this.aggregationResultOutputPrefix = aggregationResultOutputPrefix;
  }

  @ProcessElement
  public void processElement(
      @Element KV<String, String> input, OutputReceiver<KV<String, String>> out)
      throws IOException, InterruptedException {
    Path aggReportPath = invokeAggregatableReportConverter(input);
    invokeLocalTestingTool(input, out, aggReportPath);
  }

  private Path invokeAggregatableReportConverter(KV<String, String> input)
      throws IOException, InterruptedException {
    Path aggReportDirectory =
        Paths.get(aggregatableReportConverterOutputPrefix).resolve(input.getKey());
    Files.createDirectories(aggReportDirectory);
    Path aggReportPath = aggReportDirectory.resolve(aggregatableReportsFileName);
    List<String> batchSpecificArguments;
    batchSpecificArguments = new ArrayList<>(Arrays.asList(aggregatableReportConverterArguments));
    batchSpecificArguments.add("--output_path");
    batchSpecificArguments.add(aggReportPath.toString());
    this.arguments = batchSpecificArguments.toArray(new String[0]);
    super.processElement(input, null);
    return aggReportPath;
  }

  private void invokeLocalTestingTool(
      KV<String, String> input, OutputReceiver<KV<String, String>> out, Path aggReportPath)
      throws IOException, InterruptedException {
    Path outputPath = Paths.get(aggregationResultOutputPrefix).resolve(input.getKey());
    Files.createDirectories(outputPath);

    List<String> batchSpecificArguments = new ArrayList<>(Arrays.asList(localTestingToolArguments));

    batchSpecificArguments.add("--input_data_avro_file");
    batchSpecificArguments.add(aggReportPath.toString());

    batchSpecificArguments.add("--output_directory");
    batchSpecificArguments.add(outputPath.toString());

    this.arguments = batchSpecificArguments.toArray(new String[0]);
    super.processElement(input, out);
  }
}
