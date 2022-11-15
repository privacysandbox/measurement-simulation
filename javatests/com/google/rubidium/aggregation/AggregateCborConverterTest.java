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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class AggregateCborConverterTest {
  private static final int AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE = 16;
  private static final int AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE = 4;

  @Test
  public void testEncode_successfully() throws Exception {
    String result = AggregateCborConverter.encode(getDefaultPayload());
    assertNotNull(result);
    assertEncodedPayload(result);
  }

  @Test
  public void testEncode_emptyHistogram() {
    try {
      AggregateCborConverter.encode(getPayloadWithNoContributions());
    } catch (Exception e) {
      // succeed
      assertEquals(e.getMessage(), "No contributions found");
    }
  }

  @Test
  public void testEncodeWithCbor_successfully() throws Exception {
    final String encodedStr = AggregateCborConverter.encode(getDefaultPayload());
    final byte[] encoded = Base64.getDecoder().decode(encodedStr);
    final List<DataItem> dataItems = new CborDecoder(new ByteArrayInputStream(encoded)).decode();

    final Map payload = (Map) dataItems.get(0);
    final Array payloadArray = (Array) payload.get(new UnicodeString("data"));

    assertEquals(2, payloadArray.getDataItems().size());
    assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());
    assertTrue(
        payloadArray.getDataItems().stream()
            .anyMatch(i -> isFound((Map) i, "bucket", "1") && isFound((Map) i, "value", "2")));

    assertTrue(
        payloadArray.getDataItems().stream()
            .anyMatch(i -> isFound((Map) i, "bucket", "3") && isFound((Map) i, "value", "4")));
  }

  @Test
  public void testEncodeWithCbor_differentSizesShouldMatchUpperBound() throws Exception {
    final List<AggregateHistogramContribution> contributions = new ArrayList<>();
    final AggregateHistogramContribution firstContribution =
        new AggregateHistogramContribution.Builder()
            .setKey(new BigInteger("1"))
            .setValue(1)
            .build();
    final AggregateHistogramContribution secondContribution =
        new AggregateHistogramContribution.Builder()
            .setKey(new BigInteger("1329227995784915872903807060280344576"))
            .setValue(Integer.MAX_VALUE)
            .build();
    contributions.add(firstContribution);
    contributions.add(secondContribution);

    AggregateReport report =
        new AggregateReport.Builder()
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder().setContributions(contributions).build())
            .build();
    final String encodedStr = AggregateCborConverter.encode(report);
    final byte[] encoded = Base64.getDecoder().decode(encodedStr);
    final List<DataItem> dataItems = new CborDecoder(new ByteArrayInputStream(encoded)).decode();

    final Map payload = (Map) dataItems.get(0);
    final Array payloadArray = (Array) payload.get(new UnicodeString("data"));

    assertEquals(2, payloadArray.getDataItems().size());
    assertEquals(
        AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE,
        getBytesLength((Map) payloadArray.getDataItems().get(0), "bucket"));
    assertEquals(
        getBytesLength((Map) payloadArray.getDataItems().get(0), "bucket"),
        getBytesLength((Map) payloadArray.getDataItems().get(1), "bucket"));

    assertEquals(
        AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE,
        getBytesLength((Map) payloadArray.getDataItems().get(0), "value"));
    assertEquals(
        getBytesLength((Map) payloadArray.getDataItems().get(0), "value"),
        getBytesLength((Map) payloadArray.getDataItems().get(1), "value"));
  }

  private void assertEncodedPayload(String encodedPayloadBase64) throws Exception {
    final byte[] cborEncoded = Base64.getDecoder().decode(encodedPayloadBase64);
    assertNotNull(cborEncoded);

    final List<DataItem> dataItems =
        new CborDecoder(new ByteArrayInputStream(cborEncoded)).decode();

    final Map payload = (Map) dataItems.get(0);
    assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());

    final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
    assertEquals(2, payloadArray.getDataItems().size());
    assertTrue(
        payloadArray.getDataItems().stream()
            .anyMatch(i -> isFound((Map) i, "bucket", "1") && isFound((Map) i, "value", "2")));

    assertTrue(
        payloadArray.getDataItems().stream()
            .anyMatch(i -> isFound((Map) i, "bucket", "3") && isFound((Map) i, "value", "4")));
  }

  private boolean isFound(Map map, String name, String value) {
    return new BigInteger(value)
        .equals(new BigInteger(((ByteString) map.get(new UnicodeString(name))).getBytes()));
  }

  private int getBytesLength(Map map, String keyName) {
    return ((ByteString) map.get(new UnicodeString(keyName))).getBytes().length;
  }

  private AggregateReport getDefaultPayload() throws ParseException {
    AggregateHistogramContribution contribution1 =
        new AggregateHistogramContribution.Builder()
            .setKey(BigInteger.valueOf(1))
            .setValue(2)
            .build();
    AggregateHistogramContribution contribution2 =
        new AggregateHistogramContribution.Builder()
            .setKey(BigInteger.valueOf(3))
            .setValue(4)
            .build();
    List<AggregateHistogramContribution> contributions = new ArrayList<>();
    contributions.add(contribution1);
    contributions.add(contribution2);
    return new AggregateReport.Builder()
        .setAggregateAttributionData(
            new AggregateAttributionData.Builder().setContributions(contributions).build())
        .build();
  }

  private AggregateReport getPayloadWithNoContributions() throws ParseException {
    return new AggregateReport.Builder()
        .setAggregateAttributionData(
            new AggregateAttributionData.Builder().setContributions(new ArrayList<>()).build())
        .build();
  }
}
