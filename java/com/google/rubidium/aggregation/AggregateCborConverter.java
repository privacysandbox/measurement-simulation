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

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

public class AggregateCborConverter {

  public static String encode(AggregateReport aggregateReport) throws Exception {
    // Extract Histogram
    final List<AggregateHistogramContribution> contributions =
        aggregateReport.getAggregateAttributionData().getContributions();
    if (contributions.isEmpty()) {
      throw new Exception("No contributions found");
    }
    try {
      // Encode with Cbor
      final byte[] payloadCborEncoded = encodeWithCbor(contributions);
      // Encode with Base 64
      return Base64.getEncoder().encodeToString(payloadCborEncoded);
      //            return encodeWithBase64(payloadCborEncoded);
    } catch (Exception e) {
      throw new Exception("Encoding error", e);
    }
  }

  static byte[] encodeWithCbor(List<AggregateHistogramContribution> contributions)
      throws Exception {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final CborBuilder cborBuilder = new CborBuilder();

    final Map payloadMap = new Map();
    final Array dataArray = new Array();

    for (AggregateHistogramContribution contribution : contributions) {
      final byte[] value = ByteBuffer.allocate(4).putInt((int) contribution.getValue()).array();
      final byte[] bucket = new byte[16];
      final byte[] src = contribution.getKey().toByteArray();
      final int length = Math.min(src.length, 16);
      final int position = bucket.length - length;
      System.arraycopy(src, /* srcPos= */ 0, bucket, position, length);

      final Map dataMap = new Map();
      dataMap.put(new UnicodeString("bucket"), new ByteString(bucket));
      dataMap.put(new UnicodeString("value"), new ByteString(value));
      dataArray.add(dataMap);
    }
    payloadMap.put(new UnicodeString("operation"), new UnicodeString("histogram"));
    payloadMap.put(new UnicodeString("data"), dataArray);

    new CborEncoder(outputStream).encode(cborBuilder.add(payloadMap).build());
    return outputStream.toByteArray();
  }
}
