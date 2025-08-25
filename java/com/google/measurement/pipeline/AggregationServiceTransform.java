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

import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * A PTransform for taking a PCollection of groups of strings and executing the Aggregation Service
 * LocalTestingTool on each of the groups. Before invoking the LocalTestingTool, the string values
 * need to be converted to Avro format and written to disk, as that is what the LocalTestingTool
 * expects. So this transform executes two JARs: AggregatableReportConverter and LocalTestingTool.
 */
public class AggregationServiceTransform
    extends PTransform<PCollection<KV<String, String>>, PCollection<KV<String, String>>> {
  private final String aggregationResultOutputPrefix;
  private final String aggregatableReportOutputPrefix;
  private final String[] localTestingToolArguments;
  private final String[] aggregatableReportConverterArguments;

  public AggregationServiceTransform(
      String localTestingToolJarPath,
      String aggregatableReportConverterJarPath,
      String aggregatableReportOutputPrefix,
      String aggregationResultOutputPrefix) {
    this.aggregatableReportOutputPrefix = aggregatableReportOutputPrefix;
    this.aggregationResultOutputPrefix = aggregationResultOutputPrefix;

    aggregatableReportConverterArguments =
        new String[] {
          // Path to the AggregatableReportConverter JAR. This JAR needs to be present on the worker
          // machine that executes this transform.
          aggregatableReportConverterJarPath
        };

    localTestingToolArguments =
        new String[] {
          // Path to the LocalTestingTool JAR. This JAR needs to be present on the worker machine
          // that executes this transform.
          localTestingToolJarPath,
          // Skip domain flag for LocalTestingTool. Will be applied to all invocations. Configures
          // LocalTestingTool to not expect a domain file. Note, for the actual Aggregate Service, a
          // domain file is necessary. We exclude it here to simplify implementation. A more
          // accurate simulation will require supplying the domain file.
          "--skip_domain",
          // Write output as JSON. Will be applied to all invocations. The default is avro, however,
          // JSON is more convenient for readability. Note, actual Aggregation Service outputs to
          // avro format.
          "--json_output"
        };
  }

  @Override
  public PCollection<KV<String, String>> expand(PCollection<KV<String, String>> input) {
    return input.apply(
        "ExecuteLocalTestingToolJar",
        ParDo.of(
            new AggregationServiceDoFn(
                localTestingToolArguments,
                aggregatableReportConverterArguments,
                aggregatableReportOutputPrefix,
                aggregationResultOutputPrefix)));
  }
}
