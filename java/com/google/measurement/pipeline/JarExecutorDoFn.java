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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;

public class JarExecutorDoFn extends DoFn<KV<String, String>, KV<String, String>> {
  protected String[] arguments;

  public JarExecutorDoFn(String[] arguments) {
    this.arguments = arguments;
  }

  @ProcessElement
  public void processElement(
      @Element KV<String, String> input, OutputReceiver<KV<String, String>> out)
      throws IOException, InterruptedException {
    String key = input.getKey();
    String content = input.getValue();

    List<String> command = new ArrayList<>();
    command.add("java");
    command.add("-jar");
    command.addAll(Arrays.asList(this.arguments));

    Process process = new ProcessBuilder(command).start();

    OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
    writer.write(content);
    writer.close();

    StringBuilder outputBuilder = new StringBuilder();
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
      outputBuilder.append(line).append("\n");
    }

    BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    String errline;
    while ((errline = errReader.readLine()) != null) {
      // In a real pipeline this should be written to a file instead.
      System.err.println(errline);
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      // Handle errors
      throw new RuntimeException("JAR execution failed.");
    }

    if (out != null) {
      out.output(KV.of(key, outputBuilder.toString()));
    }
  }
}
