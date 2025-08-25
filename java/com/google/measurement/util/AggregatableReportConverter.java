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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class AggregatableReportConverter {
  private static final Schema sSchema;

  static {
    try {
      sSchema = Schema.parse(AggregatableReportConverter.class.getResourceAsStream("reports.avsc"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public AggregatableReportConverter() {}

  public GenericRecord convertJsonToAvro(JSONObject aggregatableReportJson) throws JSONException {
    final GenericRecord record = new GenericData.Record(sSchema);
    JSONArray aggServicePayloadJsonArray =
        aggregatableReportJson.getJSONArray(JsonKeys.AGGREGATION_SERVICE_PAYLOADS);
    String sharedInfo = aggregatableReportJson.getString(JsonKeys.SHARED_INFO);
    JSONObject payloadJson = aggServicePayloadJsonArray.getJSONObject(0);
    String payload = payloadJson.getString(JsonKeys.PAYLOAD);
    byte[] payloadBytes = Base64.getDecoder().decode(payload);
    record.put(AvroKeys.PAYLOAD, ByteBuffer.wrap(payloadBytes));
    record.put(AvroKeys.KEY_ID, payloadJson.getString(JsonKeys.KEY_ID));
    record.put(AvroKeys.SHARED_INFO, sharedInfo);
    return record;
  }

  public void writeAvro(Path outputPath, List<GenericRecord> records) throws IOException {
    final DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(sSchema);
    DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(writer);
    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
      fileWriter.create(sSchema, out);
      for (GenericRecord record : records) {
        fileWriter.append(record);
      }
      fileWriter.close();
    }
  }

  private interface JsonKeys {
    String AGGREGATION_SERVICE_PAYLOADS = "aggregation_service_payloads";
    String SHARED_INFO = "shared_info";
    String PAYLOAD = "payload";
    String KEY_ID = "key_id";
  }

  private interface AvroKeys {
    String SHARED_INFO = "shared_info";
    String PAYLOAD = "payload";
    String KEY_ID = "key_id";
  }
}
