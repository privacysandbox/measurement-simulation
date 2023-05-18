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

package com.google.measurement;

import static com.google.measurement.SystemHealthParams.MAX_ATTRIBUTION_FILTERS;
import static com.google.measurement.SystemHealthParams.MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID;
import static com.google.measurement.SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public final class FetcherUtilTest {
  private static final String LONG_FILTER_STRING = "12345678901234567890123456";

  @Test
  public void testIsValidAggregateKeyId_valid() {
    assertTrue(FetcherUtil.isValidAggregateKeyId("abcd"));
  }

  @Test
  public void testIsValidAggregateKeyId_null() {
    assertFalse(FetcherUtil.isValidAggregateKeyId(null));
  }

  @Test
  public void testIsValidAggregateKeyId_tooLong() {
    StringBuilder keyId = new StringBuilder("");
    for (int i = 0; i < MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID + 1; i++) {
      keyId.append("a");
    }
    assertFalse(FetcherUtil.isValidAggregateKeyId(keyId.toString()));
  }

  @Test
  public void testIsValidAggregateKeyPiece_valid() {
    assertTrue(FetcherUtil.isValidAggregateKeyPiece("0x15A"));
  }

  @Test
  public void testIsValidAggregateKeyPiece_validWithUpperCasePrefix() {
    assertTrue(FetcherUtil.isValidAggregateKeyPiece("0X15A"));
  }

  @Test
  public void testIsValidAggregateKeyPiece_null() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece(null));
  }

  @Test
  public void testIsValidAggregateKeyPiece_missingPrefix() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece("1234"));
  }

  @Test
  public void testIsValidAggregateKeyPiece_tooShort() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece("0x"));
  }

  @Test
  public void testIsValidAggregateKeyPiece_tooLong() {
    StringBuilder keyPiece = new StringBuilder("0x");
    for (int i = 0; i < 33; i++) {
      keyPiece.append("1");
    }
    assertFalse(FetcherUtil.isValidAggregateKeyPiece(keyPiece.toString()));
  }

  @Test
  public void testAreValidAttributionFilters_valid() throws ParseException {
    String json =
        "{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}";
    JSONParser parser = new JSONParser();
    JSONObject filters = (JSONObject) parser.parse(json);
    assertTrue(FetcherUtil.areValidAttributionFilters(filters));
  }

  @Test
  public void testAreValidAttributionFilters_tooManyFilters() throws ParseException {
    StringBuilder json = new StringBuilder("{");
    json.append(
        IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    json.append("}");
    JSONParser parser = new JSONParser();
    JSONObject filters = (JSONObject) parser.parse(json.toString());
    assertFalse(FetcherUtil.areValidAttributionFilters(filters));
  }

  @Test
  public void testAreValidAttributionFilters_keyTooLong() throws ParseException {
    String json =
        "{"
            + "\""
            + LONG_FILTER_STRING
            + "\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}";
    JSONParser parser = new JSONParser();
    JSONObject filters = (JSONObject) parser.parse(json);
    assertFalse(FetcherUtil.areValidAttributionFilters(filters));
  }

  @Test
  public void testAreValidAttributionFilters_tooManyValues() throws ParseException {
    StringBuilder json =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    json.append(
        IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    json.append("]}");
    JSONParser parser = new JSONParser();
    JSONObject filters = (JSONObject) parser.parse(json.toString());
    assertFalse(FetcherUtil.areValidAttributionFilters(filters));
  }

  @Test
  public void testAreValidAttributionFilters_valueTooLong() throws ParseException {
    String json =
        "{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \""
            + LONG_FILTER_STRING
            + "\"]"
            + "}";
    JSONParser parser = new JSONParser();
    JSONObject filters = (JSONObject) parser.parse(json);
    assertFalse(FetcherUtil.areValidAttributionFilters(filters));
  }
}
