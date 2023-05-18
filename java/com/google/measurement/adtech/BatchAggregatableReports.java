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

import com.google.measurement.util.Util;
import java.io.Serializable;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class BatchAggregatableReports implements Serializable {

  private static PCollection<KV<String, JSONObject>> createDailyBatches(
      PCollection<JSONObject> aggregatePayloads) {
    // Group the aggregatable reports by advertiser and for each advertiser, create batches for a
    // 24-hour time window. This code is referenced in documentation under the example directory.
    // Check that documentation after any changes here.
    return aggregatePayloads.apply(
        MapElements.via(
            new SimpleFunction<JSONObject, KV<String, JSONObject>>() {
              @Override
              public KV<String, JSONObject> apply(JSONObject input) {
                String sharedInfoStr = (String) input.get("shared_info");
                JSONParser parser = new JSONParser();
                try {
                  JSONObject sharedInfo = (JSONObject) parser.parse(sharedInfoStr);
                  String advertiser = (String) sharedInfo.get("attribution_destination");
                  long formattedDate =
                      Util.roundDownToDay((long) sharedInfo.get("scheduled_report_time"));
                  String groupByKey = advertiser + "_" + formattedDate;
                  return KV.of(groupByKey, input);
                } catch (ParseException e) {
                  e.printStackTrace();
                  return null;
                }
              }
            }));
  }

  public static PCollection<KV<String, Iterable<JSONObject>>> generateBatches(
      PCollection<JSONObject> aggregatablePayloadPCollection) {
    PCollection<KV<String, JSONObject>> collectionReports =
        createDailyBatches(aggregatablePayloadPCollection);
    // Group the data based on the grouping key and batch strategy
    PCollection<KV<String, Iterable<JSONObject>>> groupedReports =
        collectionReports.apply(GroupByKey.<String, JSONObject>create());
    // Get individual batch based on the keys
    return groupedReports;
  }

  public static void generateAggregateReports(
      PCollection<JSONObject> aggregatablePayloadPCollection, String outputDirectory) {
    PCollection<KV<String, Iterable<JSONObject>>> batchedReports =
        generateBatches(aggregatablePayloadPCollection);

    // Write each batch to avro file and call Aggregation service for each of them in parallel.
    batchedReports.apply(ParDo.of(new ProcessBatch(outputDirectory)));
  }
}
