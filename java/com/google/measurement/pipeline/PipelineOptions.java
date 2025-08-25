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

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.StreamingOptions;

public interface PipelineOptions extends StreamingOptions {
  @Description("Directory to PipelineRunner runfiles.")
  String getRunfilesDirectory();

  void setRunfilesDirectory(String value);

  @Description(
      "Directory to input files. Must be an absolute path to a directory. All json files will be"
          + " ingested from that directory. The names of the files will indicate the user-id to"
          + " the simulation.")
  String getInputDirectory();

  void setInputDirectory(String value);

  @Description(
      "Directory to write event reports to. Must be an absolute path to a directory. All event"
          + " reports for a user will be written to a single file. The filename will be the user"
          + " id.")
  @Default.String("/tmp/event_reports/")
  String getEventReportsOutputDirectory();

  void setEventReportsOutputDirectory(String value);

  @Description(
      "Directory to write aggregatable reports to. Must be an absolute path to a directory. The"
          + " format will be avro. These files will be used as input to the Aggregation Service"
          + " LocalTestingTool.")
  @Default.String("/tmp/aggregatable_reports/")
  String getAggregatableReportsOutputDirectory();

  void setAggregatableReportsOutputDirectory(String value);

  @Description("Directory to write aggregation results to. Must be an absolute path to a directory")
  @Default.String("/tmp/aggregation_results/")
  String getAggregationResultsDirectory();

  void setAggregationResultsDirectory(String value);
}
