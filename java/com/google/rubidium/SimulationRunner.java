/*
 * Copyright 2022 Google LLC
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

package com.google.rubidium;

import com.google.rubidium.adtech.BatchAggregatableReports;
import com.google.rubidium.util.Util;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;
import org.json.simple.JSONObject;

public class SimulationRunner {

  private static TupleTag<Source> sourceTag = new TupleTag<>();
  private static TupleTag<Trigger> triggerTag = new TupleTag<>();

  protected PCollection<KV<String, CoGbkResult>> joinSourceAndTriggerData(
      PCollection<KV<String, Source>> userToAdtechSourceData,
      PCollection<KV<String, Trigger>> userToAdtechTriggerData) {
    // Join the 2 datasets to group Source and Trigger data for each user
    return DataProcessor.joinSourceAndTriggerData(
        userToAdtechSourceData, userToAdtechTriggerData, sourceTag, triggerTag);
  }

  protected PCollection<JSONObject> runUserSimulationInParallel(
      PCollection<KV<String, CoGbkResult>> joinedData, String outputDirectory) {
    // Simulate attribution reporting API for each user id in parallel.
    PCollection<List<JSONObject>> aggregatableReportList =
        joinedData.apply(
            ParDo.of(new RunSimulationPerUser(sourceTag, triggerTag, outputDirectory)));

    return aggregatableReportList.apply(Flatten.iterables());
  }

  protected void generateAggregateReports(
      PCollection<JSONObject> aggregatableReports, String domainAvroFile, String outputDirectory) {
    // Generate individual batches based on the keys, write them to avro files and call Aggregation
    // service.
    BatchAggregatableReports.generateAggregateReports(
        aggregatableReports, domainAvroFile, outputDirectory);
  }

  public boolean run(String[] args) {
    System.out.println("Simulating Attribution Reporting API...");
    // Create beam pipeline to read and process data
    SimulationConfig options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(SimulationConfig.class);
    Util.validateFilenames(options.getAttributionSourceFileName(), options.getTriggerFileName());
    Pipeline p = Pipeline.create(options);

    // Group input data by individual user ids
    PCollection<KV<String, Source>> userToAdtechSourceData =
        DataProcessor.buildUserToSourceMap(p, options);
    PCollection<KV<String, Trigger>> userToAdtechTriggerData =
        DataProcessor.buildUserToTriggerMap(p, options);

    PCollection<KV<String, CoGbkResult>> joinedData =
        joinSourceAndTriggerData(userToAdtechSourceData, userToAdtechTriggerData);

    PCollection<JSONObject> aggregatableReports =
        runUserSimulationInParallel(joinedData, options.getOutputDirectory());

    generateAggregateReports(
        aggregatableReports, options.getDomainAvroFile(), options.getOutputDirectory());

    p.run().waitUntilFinish();

    System.out.println("Attribution Reporting API Simulation ended...");
    return true;
  }

  // Entrypoint for python code. Is not referenced from anywhere in Java.
  public boolean run(ArrayList<String> args) {
    return this.run(args.toArray(new String[0]));
  }

  public static void main(String[] args) {
    SimulationRunner runner = new SimulationRunner();
    runner.run(args);
    System.exit(0);
  }
}
