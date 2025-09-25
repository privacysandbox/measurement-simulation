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
import java.io.InputStreamReader;

/**
 * Utility class for testing the JarExecutorTransform. A simple jar that echoes content from STDIN.
 */
public final class Echo {
  public static void main(String[] args) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line); // Echo the line back to stdout
      }
      for (String arg : args) {
        System.out.println(arg);
      }

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }
  }
}
