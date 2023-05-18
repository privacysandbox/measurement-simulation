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

import com.google.aggregate.adtech.worker.LocalRunner;
import com.google.common.util.concurrent.ServiceManager;
import com.google.measurement.aggregation.AggregationArgs;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/** Takes LocalRunner parameters and runs the local runner on them. */
public final class LocalAggregationRunner {

  public static void runAggregator(AggregationArgs args, String batchKey) {
    // Run Aggregation API on generated reports
    try {
      ServiceManager serviceManager = LocalRunner.internalMain(args.toStringArgs());
      serviceManager.awaitStopped(Duration.ofMinutes(AggregationArgs.timeoutMinutes));
    } catch (IOException e) {
      System.err.println("IO Exception in Aggregation API for batch: " + batchKey);
      e.printStackTrace();
    } catch (TimeoutException e) {
      System.err.println(
          "Aggregation API timed out for batch: "
              + batchKey
              + ". Consider increasing the "
              + "aggregation duration via --timeoutMinutes. Default value is 5 minutes.");
    }
  }
}
