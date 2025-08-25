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

package com.google.measurement.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.avro.generic.GenericRecord;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AggregatableReportConverterMain {

  public static final String OUTPUT_PATH_FLAG_NAME = "--output_path";

  public static void main(String[] args) throws JSONException, IOException {
    if (args.length != 2 || !Objects.equals(args[0], OUTPUT_PATH_FLAG_NAME)) {
      System.err.println("Incorrect usage: " + OUTPUT_PATH_FLAG_NAME + " is a required flag");
      return;
    }

    String outputDirectory = args[1];
    AggregatableReportConverter converter = new AggregatableReportConverter();
    JSONArray aggregatableReportJsonArray = getJsonArray();
    List<GenericRecord> records = new ArrayList<>();
    for (int i = 0; i < aggregatableReportJsonArray.length(); i++) {
      JSONObject aggregatableReportJsonObject = aggregatableReportJsonArray.getJSONObject(i);
      records.add(converter.convertJsonToAvro(aggregatableReportJsonObject));
    }

    converter.writeAvro(Path.of(outputDirectory), records);
  }

  private static JSONArray getJsonArray() throws JSONException, IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;
    StringBuilder stringBuilder = new StringBuilder();
    while ((line = reader.readLine()) != null) {
      stringBuilder.append(line);
    }
    return new JSONArray(stringBuilder.toString());
  }
}
