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

package com.google.rubidium.aggregation;

import com.google.rubidium.util.Util;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Properties;

public class AggregationArgs implements Serializable {

  // Location on disk of batch.avro input file
  public String inputDataAvroFile;

  // Location on disk of domain.avro input file
  public static String domainAvroFile;

  // Directory on disk to output results to
  public String outputDirectory;

  // Epsilon value for noise. Must be > 0, and <= 64
  public static int epsilon;

  // Whether to ignore noising and thresholding
  public static boolean noNoising;

  // Whether to format the output as json
  public static boolean jsonOutput;

  // Whether to use the domain file or not. False = use domain, True = skip domain
  public static boolean skipDomain;

  // File format for the domain generation file. TEXT_FILE or AVRO. Does not seem to apply to batch
  public static String domainFileFormat;

  // Time limit to run aggregation for before error is thrown. Default 5 minutes
  public static long timeoutMinutes;

  static {
    initialize();
  }

  public static void initialize() {
    Properties props = new Properties();
    try {
      Util.loadProperties(props, "./config/AggregationArgs.properties");
    } catch (IOException e) {
      throw new RuntimeException(
          "Error with reading file config/AggregationArgs.properties."
              + " Please ensure that it exists and is properly formatted");
    }

    epsilon = validateEpsilon(props, "epsilon");
    noNoising = validateBoolean(props, "noNoising");
    jsonOutput = validateBoolean(props, "jsonOutput");
    skipDomain = validateBoolean(props, "skipDomain");
    domainFileFormat = validateDomainFileFormat(props, "domainFileFormat");
    timeoutMinutes = validateInt(props, "timeoutMinutes");
  }

  private static String validateDomainFileFormat(Properties props, String key) {
    String domainFileFormat = props.getProperty(validateKey(props, key));
    if (!domainFileFormat.equals("TEXT_FILE") && !domainFileFormat.equals("AVRO")) {
      throw new IllegalArgumentException(
          "Property domainFileFormat in "
              + "config/AggregationArgs.properties must be either TEXT_FILE or AVRO");
    }

    return domainFileFormat;
  }

  private static boolean validateBoolean(Properties props, String key) {
    String value = props.getProperty(validateKey(props, key));
    if ("true".equals(value) || "false".equals(value)) {
      return Boolean.parseBoolean(value);
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Property %s in config/AggregationArgs.properties must be either true or false",
              key));
    }
  }

  private static int validateEpsilon(Properties props, String key) {
    int epsilon = validateInt(props, key);
    if (epsilon < 0 || epsilon > 64) {
      throw new IllegalArgumentException(
          "epsilon value in config/AggregationArgs.properties must"
              + " be an integer between 0 and 64");
    }

    return epsilon;
  }

  private static int validateInt(Properties props, String key) {
    key = validateKey(props, key);
    int integer = 0;
    try {
      integer = Integer.parseInt(props.getProperty(key));
    } catch (NumberFormatException e) {
      String err =
          String.format("%s with value %s not a valid integer", key, props.getProperty(key));
      throw new IllegalArgumentException(err);
    }

    return integer;
  }

  private static String validateKey(Properties props, String key) {
    if (!props.containsKey(key)) {
      String err = String.format("%s missing from AggregationArgs.properties", key);
      throw new IllegalArgumentException(err);
    }
    return key;
  }

  public String[] toStringArgs() {
    ArrayList<String> argList = new ArrayList<>();
    if (inputDataAvroFile != null) {
      argList.add("--input_data_avro_file");
      argList.add(inputDataAvroFile);
    }
    if (domainAvroFile != null) {
      argList.add("--domain_avro_file");
      argList.add(domainAvroFile);
    }
    if (outputDirectory != null) {
      argList.add("--output_directory");
      argList.add(outputDirectory);
    }

    if (domainFileFormat != null) {
      argList.add("--domain_file_format");
      argList.add(domainFileFormat);
    }

    if (epsilon != 0) {
      argList.add("--epsilon");
      argList.add(String.valueOf(epsilon));
    }

    if (noNoising) {
      argList.add("--no_noising");
    }

    if (jsonOutput) {
      argList.add("--json_output");
    }

    if (skipDomain) {
      argList.add("--skip_domain");
    }

    return argList.toArray(new String[] {});
  }
}
