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

package com.google.rubidium.adtech;

import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.rubidium.aggregation.AggregationArgs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalAggregationRunnerTest {

  private static final List<Integer> EXPECTED_OUTPUT = Arrays.asList(0, 32768, 0);

  @Rule public final TemporaryFolder testWorkingDir = new TemporaryFolder();
  private Path workingDir;
  private Path outputDir;
  private ObjectMapper objectMapper = new ObjectMapper();
  private AggregationArgs args = new AggregationArgs();

  @Before
  public void setup() {
    workingDir = testWorkingDir.getRoot().toPath();
    outputDir = workingDir.resolve("output");
    AggregationArgs.initialize();
  }

  @Test
  public void aggregator_blank_success() {
    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    AggregationArgs.domainAvroFile = "testdata/domain.avro";
    LocalAggregationRunner.runAggregator(args);
  }

  @Test
  public void aggregator_bad_params_failure() {
    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    AggregationArgs.domainAvroFile = "testdata/domain.avro";
    AggregationArgs.epsilon = 100;

    Assert.assertThrows(
        ParameterException.class,
        () -> {
          LocalAggregationRunner.runAggregator(args);
        });
  }

  @Test
  public void aggregator_output_correct() throws IOException {

    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    AggregationArgs.domainAvroFile = "testdata/domain.avro";
    AggregationArgs.noNoising = true;
    AggregationArgs.jsonOutput = true;

    LocalAggregationRunner.runAggregator(args);

    Path outputJson = outputDir.resolve("output.json");
    JsonNode output = objectMapper.readTree(Files.newInputStream(outputJson));

    List<Integer> outputValues = new ArrayList<>();
    List<Object> buckets = objectMapper.convertValue(output, new TypeReference<ArrayList>() {});
    for (Object bucketData : buckets) {
      HashMap<Object, Object> bucketDataMap = (HashMap) bucketData;
      Integer bucketValue = (Integer) bucketDataMap.get("value");
      outputValues.add(bucketValue);
    }
    Assert.assertTrue(outputValues.containsAll(EXPECTED_OUTPUT));
    Assert.assertTrue(EXPECTED_OUTPUT.containsAll(outputValues));
  }
}
