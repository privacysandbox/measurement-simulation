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

/** A Transform for executing arbitrary JARs. */
public class JarExecutorTransform
    extends PTransform<PCollection<KV<String, String>>, PCollection<KV<String, String>>> {

  private final String[] arguments;

  /**
   * Create a transform for executing a JAR. The path to the JAR will always be the first element in
   * the arguments parameter. Each element thereafter will be flags specified to the JAR. For flags
   * that have values, add an additional element. For example,
   *
   * <p>["path/to/jar", "--flag_name", "flag_value", "--valueless_flag"]
   *
   * <p>The value in the KV pair will be sent to STDIN of the Java process executing the JAR.
   *
   * @param arguments
   */
  public JarExecutorTransform(String[] arguments) {
    this.arguments = arguments;
  }

  @Override
  public PCollection<KV<String, String>> expand(PCollection<KV<String, String>> input) {
    return input.apply("ExecuteJAR", ParDo.of(new JarExecutorDoFn(arguments)));
  }
}
