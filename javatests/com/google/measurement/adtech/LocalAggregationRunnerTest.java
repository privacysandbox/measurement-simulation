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

package com.google.measurement.adtech;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.aggregate.adtech.worker.LocalRunner;
import com.google.common.util.concurrent.ServiceManager;
import com.google.measurement.aggregation.AggregationArgs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class LocalAggregationRunnerTest {
  private static final List<Integer> EXPECTED_OUTPUT = Arrays.asList(0, 32768, 0);
  @Rule public final TemporaryFolder testWorkingDir = new TemporaryFolder();
  private Path workingDir;
  private Path outputDir;
  private ObjectMapper objectMapper = new ObjectMapper();
  private AggregationArgs args = new AggregationArgs();
  private final String TESTKEY = "testKey";
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  @Before
  public void setup() {
    System.setErr(new PrintStream(err));
    workingDir = testWorkingDir.getRoot().toPath();
    outputDir = workingDir.resolve("output");
    AggregationArgs.initialize();
  }

  @Test
  public void aggregator_blank_success() {
    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    args.domainAvroFile = "testdata/domain.avro";
    LocalAggregationRunner.runAggregator(args, TESTKEY);
  }

  @Test
  public void aggregator_bad_params_failure() {
    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    args.domainAvroFile = "testdata/domain.avro";
    AggregationArgs.epsilon = 100;

    ParameterException e =
        Assert.assertThrows(
            ParameterException.class,
            () -> {
              LocalAggregationRunner.runAggregator(args, TESTKEY);
            });

    Assert.assertEquals(
        "Parameter --epsilon should be a number > 0 and <= 64 (found 100)", e.getMessage());
  }

  @Test
  public void aggregator_ioException() {
    try (MockedStatic<LocalRunner> mocked = Mockito.mockStatic(LocalRunner.class)) {
      mocked
          .when(
              () -> {
                LocalRunner.internalMain(any(String[].class));
              })
          .thenThrow(IOException.class);

      LocalAggregationRunner.runAggregator(args, TESTKEY);

      String expectedErr = "IO Exception in Aggregation API for batch: " + TESTKEY;
      Assert.assertTrue(err.toString().contains(expectedErr));
    }
  }

  @Test
  public void aggregator_timeoutException() throws TimeoutException {
    ServiceManager mockedServiceManager = mock(ServiceManager.class);
    doThrow(new TimeoutException()).when(mockedServiceManager).awaitStopped(any(Duration.class));
    try (MockedStatic<LocalRunner> mocked = Mockito.mockStatic(LocalRunner.class)) {
      mocked
          .when(
              () -> {
                LocalRunner.internalMain(any(String[].class));
              })
          .thenReturn(mockedServiceManager);

      LocalAggregationRunner.runAggregator(args, TESTKEY);

      String expectedErr =
          "Aggregation API timed out for batch: "
              + TESTKEY
              + ". Consider increasing the aggregation duration via --timeoutMinutes. Default value"
              + " is 5 minutes.";
      Assert.assertTrue(err.toString().contains(expectedErr));
    }
  }

  @Test
  public void aggregator_output_correct() throws IOException {
    args.inputDataAvroFile = "testdata/batch.avro";
    args.outputDirectory = outputDir.toString();
    args.domainAvroFile = "testdata/domain.avro";
    AggregationArgs.noNoising = true;
    AggregationArgs.jsonOutput = true;

    LocalAggregationRunner.runAggregator(args, TESTKEY);

    Path outputJson = outputDir.resolve("output.json");
    JsonNode output = objectMapper.readTree(Files.newInputStream(outputJson));

    List<Integer> outputValues = new ArrayList<>();
    List<Object> buckets = objectMapper.convertValue(output, new TypeReference<ArrayList>() {});
    for (Object bucketData : buckets) {
      HashMap<Object, Object> bucketDataMap = (HashMap) bucketData;
      Integer bucketValue = (Integer) bucketDataMap.get("metric");
      outputValues.add(bucketValue);
    }
    assertTrue(outputValues.containsAll(EXPECTED_OUTPUT));
    assertTrue(EXPECTED_OUTPUT.containsAll(outputValues));
  }
}
