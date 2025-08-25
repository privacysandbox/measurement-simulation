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

import static com.google.common.truth.Truth.assertThat;
import static com.google.measurement.client.PrivacyParams.AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE;
import static com.google.measurement.client.PrivacyParams.AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import com.google.measurement.client.aggregation.AggregateAttributionData;
import com.google.measurement.client.aggregation.AggregateCborConverter;
import com.google.measurement.client.aggregation.AggregateHistogramContribution;
import com.google.measurement.client.aggregation.AggregateReport;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AggregatableReportConverterTest {
  @Test
  public void convertJsonToAvro() throws Exception {
    String sharedInfo = getSharedInfo();
    byte expectedBucket = 1;
    byte expectedValue = 1;
    AggregateReport aggregateReport =
        getAggregateReport(String.valueOf(expectedBucket), expectedValue);
    JSONObject payloadJson = getPayloadJson(aggregateReport);
    JSONObject aggregatableReportJson = getAggregatableReportJson(sharedInfo, payloadJson);

    AggregatableReportConverter converter = new AggregatableReportConverter();
    GenericRecord avroRecord = converter.convertJsonToAvro(aggregatableReportJson);
    Map decodedPayload = getDecodedPayload(avroRecord);

    Array dataArray = (Array) decodedPayload.get(new UnicodeString("data"));
    assertThat(dataArray.getDataItems().size()).isEqualTo(1);

    co.nstant.in.cbor.model.Map contribution =
        (co.nstant.in.cbor.model.Map) dataArray.getDataItems().get(0);

    assertContribution(contribution, "value", AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE, expectedValue);
    assertContribution(
        contribution, "bucket", AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE, expectedBucket);
    assertThat(decodedPayload.get(new UnicodeString("operation")))
        .isEqualTo(new UnicodeString("histogram"));
    assertThat(sharedInfo).isEqualTo(avroRecord.get("shared_info"));
  }

  private static void assertContribution(Map contribution, String key, int size, byte expected) {
    ByteString actualByteString = (ByteString) contribution.get(new UnicodeString(key));
    byte[] expectedByteArray = new byte[size];
    expectedByteArray[size - 1] = expected;
    assertThat(actualByteString.getBytes()).isEqualTo(expectedByteArray);
  }

  private static Map getDecodedPayload(GenericRecord avroRecord) throws CborException {
    ByteBuffer byteBuffer = (ByteBuffer) avroRecord.get("payload");
    byte[] payloadBytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(payloadBytes);
    final List<DataItem> dataItems =
        new CborDecoder(new ByteArrayInputStream(payloadBytes)).decode();
    return (Map) dataItems.get(0);
  }

  private static JSONObject getAggregatableReportJson(String sharedInfo, JSONObject payloadJson)
      throws JSONException {
    JSONArray aggregationServicePayloads = new JSONArray();
    aggregationServicePayloads.put(payloadJson);
    JSONObject aggregatableReport = new JSONObject();
    aggregatableReport.put("shared_info", sharedInfo);
    aggregatableReport.put("aggregation_service_payloads", aggregationServicePayloads);
    return aggregatableReport;
  }

  private static JSONObject getPayloadJson(AggregateReport aggregateReport) throws Exception {
    String payloadString = AggregateCborConverter.encode(aggregateReport);
    JSONObject payloadJson = new JSONObject();
    payloadJson.put("payload", payloadString);
    payloadJson.put("key_id", "random_key");
    return payloadJson;
  }

  private static AggregateReport getAggregateReport(String bucket, int value) {
    AggregateReport.Builder builder = new AggregateReport.Builder();
    AggregateAttributionData.Builder dataBuilder = new AggregateAttributionData.Builder();
    AggregateHistogramContribution.Builder contributionBuilder =
        new AggregateHistogramContribution.Builder();
    dataBuilder.setContributions(
        List.of(contributionBuilder.setKey(new BigInteger(bucket)).setValue(value).build()));
    builder.setAggregateAttributionData(dataBuilder.build());
    return builder.build();
  }

  private static String getSharedInfo() {
    String sharedInfo =
        """
        {"api":"attribution-reporting",
        "attribution_destination":"android-app://com.example.test",
        "report_id":"d61cb6ff-cf6f-44cb-b3a3-6d498d6d581f",
        "reporting_origin":"https://www.reporter.test",
        "scheduled_report_time":"800000989",
        "source_registration_time":"799977600",
        "version":"1.0"}\
        """;
    return sharedInfo;
  }
}
