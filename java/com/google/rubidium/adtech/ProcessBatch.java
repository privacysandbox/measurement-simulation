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

import com.google.common.collect.Lists;
import com.google.rubidium.aggregation.AggregationArgs;
import com.google.rubidium.util.Util;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.transforms.DoFn;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

@DefaultCoder(AvroCoder.class)
class ProcessBatch extends DoFn<Iterable<JSONObject>, Void> {

  private final String domainAvroFile;
  private final String outputDirectory;

  ProcessBatch(final String domainAvroFile, final String outputDirectory) {
    this.domainAvroFile = domainAvroFile;
    this.outputDirectory = outputDirectory;
  }

  private GenericRecord generateGenericRecord(JSONArray payload, String sharedInfo, Schema schema)
      throws IOException {
    final GenericRecord record = new GenericData.Record(schema);
    String inputPayload = (String) ((JSONObject) payload.get(0)).get("debug_cleartext_payload");
    byte[] payloadBytes = Base64.getDecoder().decode(inputPayload);
    record.put("payload", ByteBuffer.wrap(payloadBytes));
    // No encryption key as all reports are in plain-text
    record.put("key_id", "no_key");
    record.put("shared_info", sharedInfo);
    return record;
  }

  private Optional<Path> writeToAvroFile(
      List<JSONObject> aggregatableReportPayloadList, String randomFileName) {
    Path avroFilePath = null;
    try {
      if (aggregatableReportPayloadList.isEmpty()) {
        return Optional.empty();
      }
      final Schema schema = Schema.parse(new File("reports.avsc"));
      final String fileName = randomFileName + ".avro";
      Path inputBatches = Paths.get(outputDirectory, "input_batches");
      Files.createDirectories(inputBatches);
      avroFilePath = inputBatches.resolve(fileName);
      Files.deleteIfExists(avroFilePath); // Empty file if one already exists

      final DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
      DataFileWriter<GenericRecord> fileWriter = new DataFileWriter<>(writer);
      fileWriter.create(schema, new File(avroFilePath.toString()));
      for (JSONObject aggregatableReportPayload : aggregatableReportPayloadList) {
        String sharedInfo = (String) aggregatableReportPayload.get("shared_info");
        JSONArray payloads =
            (JSONArray) aggregatableReportPayload.get("aggregation_service_payloads");
        fileWriter.append(generateGenericRecord(payloads, sharedInfo, schema));
      }
      fileWriter.close();
      return Optional.of(avroFilePath);
    } catch (IOException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  @ProcessElement
  public void processElement(ProcessContext c) {
    Iterable<JSONObject> element = c.element();
    List<JSONObject> batchList = Lists.newArrayList(element);
    String randomFilePath = Util.generateRandomString();
    Optional<Path> avroFilePath = writeToAvroFile(batchList, randomFilePath);

    // Call Local Aggregation service
    if (avroFilePath.isPresent()) {
      AggregationArgs.domainAvroFile = domainAvroFile;
      AggregationArgs args = new AggregationArgs();
      args.inputDataAvroFile = avroFilePath.get().toString();
      args.outputDirectory = Paths.get(outputDirectory, randomFilePath).toString();
      LocalAggregationRunner.runAggregator(args);
    }
  }
}
