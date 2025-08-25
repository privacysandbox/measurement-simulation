/*
 * Copyright (C) 2022 Google LLC
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
package com.google.measurement.client.registration;

import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.measurement.client.Uri;
import com.google.measurement.client.WebUtil;
import com.google.measurement.client.FakeFlagsFactory;
import com.google.measurement.client.Flags;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.aggregation.AggregateDebugReportData;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.MeasurementRegistrationResponseStats;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

/** Unit tests for {@link FetcherUtil} */
@RunWith(MockitoJUnitRunner.Silent.class)
public final class FetcherUtilTest {
  private static final String LONG_FILTER_STRING = "12345678901234567890123456";
  private static final Uri REGISTRATION_URI = WebUtil.validUri("https://foo.test");
  private static final Uri REGISTRANT_URI = WebUtil.validUri("https://bar.test");
  private static final String KEY = "key";
  public static final int UNKNOWN_SOURCE_TYPE = 0;
  public static final int UNKNOWN_REGISTRATION_SURFACE_TYPE = 0;
  public static final int APP_REGISTRATION_SURFACE_TYPE = 2;
  public static final int UNKNOWN_STATUS = 0;
  public static final int UNKNOWN_REGISTRATION_FAILURE_TYPE = 0;
  private static final String ENROLLMENT_ID = "enrollment_id";
  private MockedStatic<FlagsFactory> fakeFlagsFactoryMockedStatic = mockStatic(FlagsFactory.class);

  @Mock Flags mFlags;
  @Mock AdServicesLogger mLogger;

  @Before
  public void setup() {
    fakeFlagsFactoryMockedStatic
        .when(FlagsFactory::getFlags)
        .thenReturn(FakeFlagsFactory.getFlagsForTest());
    when(mFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
  }

  @After
  public void cleanup() {
    fakeFlagsFactoryMockedStatic.close();
  }

  @Test
  public void testIsSuccess() {
    assertTrue(FetcherUtil.isSuccess(200));
    assertTrue(FetcherUtil.isSuccess(201));
    assertTrue(FetcherUtil.isSuccess(202));
    assertTrue(FetcherUtil.isSuccess(204));
    assertFalse(FetcherUtil.isSuccess(404));
    assertFalse(FetcherUtil.isSuccess(500));
    assertFalse(FetcherUtil.isSuccess(0));
  }

  @Test
  public void testIsRedirect() {
    assertTrue(FetcherUtil.isRedirect(301));
    assertTrue(FetcherUtil.isRedirect(302));
    assertTrue(FetcherUtil.isRedirect(303));
    assertTrue(FetcherUtil.isRedirect(307));
    assertTrue(FetcherUtil.isRedirect(308));
    assertFalse(FetcherUtil.isRedirect(200));
    assertFalse(FetcherUtil.isRedirect(404));
    assertFalse(FetcherUtil.isRedirect(500));
    assertFalse(FetcherUtil.isRedirect(0));
  }

  @Test
  public void parseRedirects_noRedirectHeaders_returnsEmpty() {
    Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
        FetcherUtil.parseRedirects(Map.of());
    assertEquals(2, redirectMap.size());
    assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LIST).isEmpty());
    assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LOCATION).isEmpty());
  }

  @Test
  public void parseRedirects_bothHeaderTypes() {
    Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
        FetcherUtil.parseRedirects(
            Map.of(
                "Attribution-Reporting-Redirect", List.of("foo.test", "bar.test"),
                "Location", List.of("baz.test")));
    assertEquals(2, redirectMap.size());
    // Verify List Redirects
    List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LIST);
    assertEquals(2, redirects.size());
    assertEquals(Uri.parse("foo.test"), redirects.get(0));
    assertEquals(Uri.parse("bar.test"), redirects.get(1));
    // Verify Location Redirect
    redirects = redirectMap.get(AsyncRegistration.RedirectType.LOCATION);
    assertEquals(1, redirects.size());
    assertEquals(Uri.parse("baz.test"), redirects.get(0));
  }

  @Test
  public void parseRedirects_locationHeaderOnly() {
    Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
        FetcherUtil.parseRedirects(Map.of("Location", List.of("baz.test")));
    assertEquals(2, redirectMap.size());
    List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LOCATION);
    assertEquals(1, redirects.size());
    assertEquals(Uri.parse("baz.test"), redirects.get(0));
    assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LIST).isEmpty());
  }

  @Test
  public void parseRedirects_lsitHeaderOnly() {
    Map<AsyncRegistration.RedirectType, List<Uri>> redirectMap =
        FetcherUtil.parseRedirects(
            Map.of("Attribution-Reporting-Redirect", List.of("foo.test", "bar.test")));
    assertEquals(2, redirectMap.size());
    // Verify List Redirects
    List<Uri> redirects = redirectMap.get(AsyncRegistration.RedirectType.LIST);
    assertEquals(2, redirects.size());
    assertEquals(Uri.parse("foo.test"), redirects.get(0));
    assertEquals(Uri.parse("bar.test"), redirects.get(1));
    assertTrue(redirectMap.get(AsyncRegistration.RedirectType.LOCATION).isEmpty());
  }

  @Test
  public void extractUnsignedLong_maxValue_success() throws JSONException {
    String unsignedLongString = "18446744073709551615";
    JSONObject obj = new JSONObject().put(KEY, unsignedLongString);
    assertEquals(
        Optional.of(new UnsignedLong(unsignedLongString)),
        FetcherUtil.extractUnsignedLong(obj, KEY));
  }

  @Test
  public void extractUnsignedLong_negative_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "-123");
    assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
  }

  @Test
  public void extractUnsignedLong_notAString_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, 123);
    assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
  }

  @Test
  public void extractUnsignedLong_tooLarge_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "18446744073709551616");
    assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
  }

  @Test
  public void extractUnsignedLong_notAnInt_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "123p");
    assertEquals(Optional.empty(), FetcherUtil.extractUnsignedLong(obj, KEY));
  }

  @Test
  public void is64BitInteger_various() {
    assertTrue(FetcherUtil.is64BitInteger(Integer.valueOf(64)));
    assertTrue(FetcherUtil.is64BitInteger(Integer.valueOf(-664)));
    assertTrue(FetcherUtil.is64BitInteger(Integer.MAX_VALUE));
    assertTrue(FetcherUtil.is64BitInteger(Long.valueOf(-140737488355328L)));
    assertTrue(FetcherUtil.is64BitInteger(Long.MAX_VALUE));
    assertFalse(FetcherUtil.is64BitInteger(Double.valueOf(45.33D)));
    assertFalse(FetcherUtil.is64BitInteger(Float.valueOf(-456.335F)));
    assertFalse(FetcherUtil.is64BitInteger("4567"));
    assertFalse(FetcherUtil.is64BitInteger(new JSONObject()));
  }

  @Test
  public void extractString_various() {
    assertThat(FetcherUtil.extractString(Integer.valueOf(64), 10).isEmpty()).isTrue();
    assertThat(FetcherUtil.extractString(Long.valueOf(64L), 10).isEmpty()).isTrue();
    assertThat(FetcherUtil.extractString("", 10).isPresent()).isTrue();
    assertThat(FetcherUtil.extractString("a", 10).isPresent()).isTrue();
    assertThat(FetcherUtil.extractString("abcd", 3).isEmpty()).isTrue();
  }

  @Test
  public void extractStringArray_stringArray_passes() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, new JSONArray("[\"1\", \"2\"]"));
    Optional<List<String>> result = FetcherUtil.extractStringArray(obj, KEY, 5, 10);
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).containsExactly("1", "2");
  }

  @Test
  public void extractStringArray_sizeTooBig_fails() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, new JSONArray("[\"1\", \"2\"]"));
    Optional<List<String>> result = FetcherUtil.extractStringArray(obj, KEY, 1, 10);
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void extractStringArray_stringTooLong_fails() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, new JSONArray("[\"1\", \"2345\"]"));
    Optional<List<String>> result = FetcherUtil.extractStringArray(obj, KEY, 5, 1);
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void extractLongString_maxValue_success() throws JSONException {
    String longString = "9223372036854775807";
    JSONObject obj = new JSONObject().put(KEY, longString);
    assertEquals(Optional.of(Long.parseLong(longString)), FetcherUtil.extractLongString(obj, KEY));
  }

  @Test
  public void extractLongString_negative_success() throws JSONException {
    String longString = "-935";
    JSONObject obj = new JSONObject().put(KEY, longString);
    assertEquals(Optional.of(Long.parseLong(longString)), FetcherUtil.extractLongString(obj, KEY));
  }

  @Test
  public void extractLongString_notAString_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, 123);
    assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
  }

  @Test
  public void extractLongString_tooLarge_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "9223372036854775808");
    assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
  }

  @Test
  public void extractLongString_notAnInt_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "123p");
    assertEquals(Optional.empty(), FetcherUtil.extractLongString(obj, KEY));
  }

  @Test
  public void extractLong_numericMaxValue_success() throws JSONException {
    long longMax = 9223372036854775807L;
    JSONObject obj = new JSONObject().put(KEY, longMax);
    assertEquals(Optional.of(Long.valueOf(longMax)), FetcherUtil.extractLong(obj, KEY));
  }

  @Test
  public void extractLong_numericNegative_success() throws JSONException {
    long longNegative = -935L;
    JSONObject obj = new JSONObject().put(KEY, longNegative);
    assertEquals(Optional.of(Long.valueOf(longNegative)), FetcherUtil.extractLong(obj, KEY));
  }

  @Test
  public void extractLong_numericNotANumber_returnsEmpty() throws JSONException {
    JSONObject obj = new JSONObject().put(KEY, "123");
    assertEquals(Optional.empty(), FetcherUtil.extractLong(obj, KEY));
  }

  @Test
  public void extractIntegralValue_posIntegralNumber_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 123);
    assertWithMessage("extractValueOfValidPositiveIntegralNumber")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.of(new BigDecimal(123)));
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidPositiveIntegralNumber")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.of(new BigDecimal(123)));
  }

  @Test
  public void extractIntegralValue_negativeIntegralNumber_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -123);
    assertWithMessage("extractValueOfValidNegativeIntegralNumber")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.of(new BigDecimal(-123)));
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidNegativeIntegralNumber")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.of(new BigDecimal(-123)));
  }

  @Test
  public void extractIntegralValue_zeroInput_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 0);
    assertWithMessage("extractValueOfZero")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.of(new BigDecimal(0)));
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfZero")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.of(new BigDecimal(0)));
  }

  @Test
  public void extractIntegralValue_posNumWithDecimalZero_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 12.0);
    assertWithMessage("extractValueOfValidPosNumberWithDecimalOfZero")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY).get().compareTo(new BigDecimal(12.0)))
        .isEqualTo(0);
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidPosNumberWithDecimalOfZero")
        .that(FetcherUtil.extractIntegralValue(obj).get().compareTo(new BigDecimal(12.0)))
        .isEqualTo(0);
  }

  @Test
  public void extractIntegralValue_negNumWithDecimalZero_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -12.0);
    assertWithMessage("extractValueOfValidNegNumberWithDecimalOfZero")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY).get().compareTo(new BigDecimal(-12.0)))
        .isEqualTo(0);
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidNegNumberWithDecimalOfZero")
        .that(FetcherUtil.extractIntegralValue(obj).get().compareTo(new BigDecimal(-12.0)))
        .isEqualTo(0);
  }

  @Test
  public void extractIntegralValue_posNumWithDecimalNonZero_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 3.4);
    assertWithMessage("extractValueOfValidPosNumberWithDecimalNonZero")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.empty());
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidPosNumberWithDecimalNonZero")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralValue_negNumWithDecimalNonZero_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -5.6);
    assertWithMessage("extractValueOfValidNegNumberWithDecimalNonZero")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.empty());
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfValidNegNumberWithDecimalNonZero")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralValue_posIntegralSciNotation_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 1.23e3);
    assertWithMessage("extractValueOfPosSciNotation")
        .that(
            FetcherUtil.extractIntegralValue(jsonObj, KEY).get().compareTo(new BigDecimal(1230.0)))
        .isEqualTo(0);
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfPosSciNotation")
        .that(FetcherUtil.extractIntegralValue(obj).get().compareTo(new BigDecimal(1230.0)))
        .isEqualTo(0);
  }

  @Test
  public void extractIntegralValue_negIntegralSciNotation_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -3.456e3);
    assertWithMessage("extractValueOfNegSciNotation")
        .that(
            FetcherUtil.extractIntegralValue(jsonObj, KEY).get().compareTo(new BigDecimal(-3456.0)))
        .isEqualTo(0);
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfNegSciNotation")
        .that(FetcherUtil.extractIntegralValue(obj).get().compareTo(new BigDecimal(-3456.0)))
        .isEqualTo(0);
  }

  @Test
  public void extractIntegralValue_posNonIntegralSciNotation_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 1.23e1);
    assertWithMessage("extractValueOfPosNonIntegralSciNotation")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.empty());
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfPosNonIntegralSciNotation")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralValue_negNonIntegralSciNotation_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -0.345e2);
    assertWithMessage("extractValueOfNegNonIntegralSciNotation")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.empty());
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfNegNonIntegralSciNotation")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralValue_nonNumericInput_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, "78");
    assertWithMessage("extractValueOfNonNumericInput")
        .that(FetcherUtil.extractIntegralValue(jsonObj, KEY))
        .isEqualTo(Optional.empty());
    Object obj = jsonObj.get(KEY);
    assertWithMessage("extractValueOfNonNumericInput")
        .that(FetcherUtil.extractIntegralValue(obj))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralInt_posIntegralNumber_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 2147483647);
    assertWithMessage("extractIntegralInt(2147483647)")
        .that(FetcherUtil.extractIntegralInt(jsonObj, KEY))
        .isEqualTo(Optional.of(Integer.valueOf(2147483647)));
  }

  @Test
  public void extractIntegralInt_negativeIntegralNumber_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -2147483648);
    assertWithMessage("extractIntegralInt(-2147483648)")
        .that(FetcherUtil.extractIntegralInt(jsonObj, KEY))
        .isEqualTo(Optional.of(Integer.valueOf(-2147483648)));
  }

  @Test
  public void extractIntegralInt_zeroInput_success() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 0);
    assertWithMessage("extractIntegralInt(0)")
        .that(FetcherUtil.extractIntegralInt(jsonObj, KEY))
        .isEqualTo(Optional.of(Integer.valueOf(0)));
  }

  @Test
  public void extractIntegralInt_inputAboveMaxInt_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, 2147483648L);
    assertWithMessage("extractIntegralInt(2147483648)")
        .that(FetcherUtil.extractIntegralInt(jsonObj, KEY))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void extractIntegralInt_inputBelowMinInt_fails() throws Exception {
    JSONObject jsonObj = new JSONObject().put(KEY, -2147483649L);
    assertWithMessage("extractIntegralInt(-2147483649)")
        .that(FetcherUtil.extractIntegralInt(jsonObj, KEY))
        .isEqualTo(Optional.empty());
  }

  @Test
  public void testIsValidAggregateKeyId_valid() {
    assertTrue(FetcherUtil.isValidAggregateKeyId("abcd"));
  }

  @Test
  public void testIsValidAggregateKeyId_null_returnsFalse() {
    assertFalse(FetcherUtil.isValidAggregateKeyId(null));
  }

  @Test
  public void testIsValidAggregateKeyId_empty_returnsFalse() {
    assertFalse(FetcherUtil.isValidAggregateKeyId(""));
  }

  @Test
  public void testIsValidAggregateKeyId_tooLong() {
    StringBuilder keyId = new StringBuilder("");
    for (int i = 0;
        i < Flags.DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID + 1;
        i++) {
      keyId.append("a");
    }
    assertFalse(FetcherUtil.isValidAggregateKeyId(keyId.toString()));
  }

  @Test
  public void testIsValidAggregateKeyPiece_valid() {
    assertTrue(FetcherUtil.isValidAggregateKeyPiece("0x15A", mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_validWithUpperCasePrefix() {
    assertTrue(FetcherUtil.isValidAggregateKeyPiece("0X15A", mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_null() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece(null, mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_emptyString() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece("", mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_missingPrefix() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece("1234", mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_tooShort() {
    assertFalse(FetcherUtil.isValidAggregateKeyPiece("0x", mFlags));
  }

  @Test
  public void testIsValidAggregateKeyPiece_tooLong() {
    StringBuilder keyPiece = new StringBuilder("0x");
    for (int i = 0; i < 33; i++) {
      keyPiece.append("1");
    }
    assertFalse(FetcherUtil.isValidAggregateKeyPiece(keyPiece.toString(), mFlags));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_valid() throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_removeSizeConstraints_valid() throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_valid() throws JSONException {
    String json =
        "{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}";
    JSONObject filters = new JSONObject(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowAllowedAndValid_returnsTrue()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void areValidAttributionFilters_validlLookbackWindowA_removeSizeConstraints_returnsTrue()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowZeroDecimal_returnsTrue() throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 12.0"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowZeroDecimalRetTrue")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isTrue();
  }

  @Test
  public void
      areValidAttributionFilters_lookbackWindowZeroDecimal_removeSizeConstraints_returnsTrue()
          throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 12.0"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowZeroDecimalRetTrue_noConstraints")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ false))
        .isTrue();
  }

  @Test
  public void areValidAttributionFilters_negativeLookbackWindowDecimal_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": -12.0"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("negativeLookbackWindow_withDecimalRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowNonZeroDecimal_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 12.3"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowNonZeroDecimalRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_negativeLookbackWindowNonZeroDecimal_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": -12.3"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("negativeLookbackWindowNonZeroDecimalRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowSciNotation_returnsTrue() throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 1.23e2"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowScientificNotationRetTrue")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isTrue();
  }

  @Test
  public void
      areValidAttributionFilters_lookbackWindowSciNotation_removeSizeConstraints_returnsTrue()
          throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 1.23e2"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowScientificNotationRetTrue_noConstraints")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ false))
        .isTrue();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowSciNotationAndNonZeroDecimal_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 1.234e2"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowScientificNotation_nonZeroDecimalRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowNegativeSciNotation_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": -1.23e2"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowNegativeScientificNotationRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowAsStr_returnsFalse() throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": \"123\""
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("stringLookbackWindowRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowAsStr_removeSizeConstraints_returnsFalse()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": \"123\""
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("StringLookbackWindowNoConstraintsRetFalse")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ false))
        .isFalse();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowGreaterThanLongMaxValue_returnsTrue()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 9223372036854775808"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowGreaterThanLongMAXVAL_retTrue")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ true))
        .isTrue();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowLongMax_removeSizeConstraints_returnsTrue()
      throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 9223372036854775808"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertWithMessage("lookbackWindowGreaterThanLongMAXVAL_noConstraintsRetTrue")
        .that(
            FetcherUtil.areValidAttributionFilters(
                filters,
                mFlags,
                /* canIncludeLookbackWindow= */ true,
                /* shouldCheckFilterSize= */ false))
        .isTrue();
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowAllowedButInvalid_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": abcd"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void areValidAttributionFilters_invalidLookbackWindow_removeSizeConstraints_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": abcd"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void areValidAttributionFilters_lookbackWindowDisallowed_returnsFalse() throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void
      areValidAttributionFilters_lookbackWindowDisallowed_removeSizeConstraints_returnsFalse()
          throws Exception {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void areValidAttributionFilters_zeroLookbackWindow_returnsFalse() throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 0"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void areValidAttributionFilters_zeroLookbackWindow_removeSizeConstraints_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": 0"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void areValidAttributionFilters_negativeLookbackWindow_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": -123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void areValidAttributionFilters_negativeLookbackWindow_removeSizeConstraints_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"],"
            + "\"_lookback_window\": -123"
            + "}]";
    JSONArray filters = new JSONArray(json);
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ true,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_null() throws JSONException {
    JSONObject nullFilterMap = null;
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            nullFilterMap,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMapNull_removeSizeConstraints_returnFalse()
      throws JSONException {
    JSONObject nullFilterMap = null;
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            nullFilterMap,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_tooManyFilters() throws JSONException {
    StringBuilder json = new StringBuilder("[{");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    json.append("}]");
    JSONArray filters = new JSONArray(json.toString());
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_tooManyFilters_removeSizeConstraints()
      throws JSONException {
    StringBuilder json = new StringBuilder("[{");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    json.append("}]");
    JSONArray filters = new JSONArray(json.toString());
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_tooManyFilters() throws JSONException {
    StringBuilder json = new StringBuilder("{");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    json.append("}");
    JSONObject filters = new JSONObject(json.toString());
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_tooManyFilters_removeSizeConstraints()
      throws JSONException {
    StringBuilder json = new StringBuilder("{");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    json.append("}");
    JSONObject filters = new JSONObject(json.toString());
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_keyTooLong() throws JSONException {
    String json =
        "[{"
            + "\""
            + LONG_FILTER_STRING
            + "\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_longKeyWithoutSizeConstraints_returnsTrue()
      throws JSONException {
    String json =
        "[{"
            + "\""
            + LONG_FILTER_STRING
            + "\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_keyTooLong() throws JSONException {
    String json =
        "{"
            + "\""
            + LONG_FILTER_STRING
            + "\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}";
    JSONObject filters = new JSONObject(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_longKeyWithoutSizeConstraints_returnsTrue()
      throws JSONException {
    String json =
        "{"
            + "\""
            + LONG_FILTER_STRING
            + "\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}";
    JSONObject filters = new JSONObject(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_tooManyFilterMapsPerFilterSet() throws JSONException {
    StringBuilder json = new StringBuilder("[");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1)
            .mapToObj(
                i ->
                    "{\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-"
                        + i
                        + "\"]}")
            .collect(Collectors.joining(",")));
    json.append("]");
    JSONArray filters = new JSONArray(json.toString());
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_tooManyFilterMapsPerFilterSet_removeConstraints()
      throws JSONException {
    StringBuilder json = new StringBuilder("[");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1)
            .mapToObj(
                i ->
                    "{\"filter-string-1\": [\"filter-value-1\"],"
                        + "\"filter-string-2\": [\"filter-value-"
                        + i
                        + "\"]}")
            .collect(Collectors.joining(",")));
    json.append("]");
    JSONArray filters = new JSONArray(json.toString());
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_tooManyValues() throws JSONException {
    StringBuilder json =
        new StringBuilder(
            "[{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    json.append("]}]");
    JSONArray filters = new JSONArray(json.toString());
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_removeMaxValueLimit() throws JSONException {
    StringBuilder json =
        new StringBuilder(
            "[{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    json.append("]}]");
    JSONArray filters = new JSONArray(json.toString());
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_tooManyValues() throws JSONException {
    StringBuilder json =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    json.append("]}");
    JSONObject filters = new JSONObject(json.toString());
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_removeMaxValueLimit() throws JSONException {
    StringBuilder json =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    json.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    json.append("]}");
    JSONObject filters = new JSONObject(json.toString());
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_valueTooLong() throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \""
            + LONG_FILTER_STRING
            + "\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterSet_removeValueLengthLimit()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \""
            + LONG_FILTER_STRING
            + "\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_valueTooLong() throws JSONException {
    String json =
        "{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \""
            + LONG_FILTER_STRING
            + "\"]"
            + "}";
    JSONObject filters = new JSONObject(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterMap_removeValueLengthLimit()
      throws JSONException {
    String json =
        "{"
            + "\"filter-string-1\": [\"filter-value-1\"],"
            + "\"filter-string-2\": [\"filter-value-2\", \""
            + LONG_FILTER_STRING
            + "\"]"
            + "}";
    JSONObject filters = new JSONObject(json);
    assertTrue(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ false));
  }

  @Test
  public void testAreValidAttributionFilters_filterSetIncludesNullValue_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\", null],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void testAreValidAttributionFilters_filterSetIncludesNumericValue_returnsFalse()
      throws JSONException {
    String json =
        "[{"
            + "\"filter-string-1\": [\"filter-value-1\", 37],"
            + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
            + "}]";
    JSONArray filters = new JSONArray(json);
    assertFalse(
        FetcherUtil.areValidAttributionFilters(
            filters,
            mFlags,
            /* canIncludeLookbackWindow= */ false,
            /* shouldCheckFilterSize= */ true));
  }

  @Test
  public void emitHeaderMetrics_headersSizeLessThanMaxAllowed_doesNotLogAdTechDomain() {
    // Setup
    int registrationType = 1;
    long maxAllowedHeadersSize = 30;
    Map<String, List<String>> headersMap = createHeadersMap();
    int headersMapSize = 28;

    // Execution
    AsyncRegistration asyncRegistration =
        new AsyncRegistration.Builder()
            .setRegistrationId(UUID.randomUUID().toString())
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRegistrationUri(REGISTRATION_URI)
            .setRegistrant(REGISTRANT_URI)
            .build();

    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

    FetcherUtil.emitHeaderMetrics(
        maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus, ENROLLMENT_ID);

    // Verify
    verify(mLogger)
        .logMeasurementRegistrationsResponseSize(
            ArgumentMatchers.eq(
                new MeasurementRegistrationResponseStats.Builder(
                        AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                        registrationType,
                        headersMapSize,
                        UNKNOWN_SOURCE_TYPE,
                        APP_REGISTRATION_SURFACE_TYPE,
                        UNKNOWN_STATUS,
                        UNKNOWN_REGISTRATION_FAILURE_TYPE,
                        0,
                        REGISTRANT_URI.toString(),
                        0,
                        false,
                        false,
                        0,
                        false,
                        false)
                    .setAdTechDomain(null)
                    .build()),
            eq(ENROLLMENT_ID));
  }

  @Test
  public void emitHeaderMetrics_headersSizeExceedsMaxAllowed_logsAdTechDomain() {
    // Setup
    int registrationType = 1;
    long maxAllowedHeadersSize = 25;
    Map<String, List<String>> headersMap = createHeadersMap();
    int headersMapSize = 28;

    // Execution
    AsyncRegistration asyncRegistration =
        new AsyncRegistration.Builder()
            .setRegistrationId(UUID.randomUUID().toString())
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRegistrationUri(REGISTRATION_URI)
            .setRegistrant(REGISTRANT_URI)
            .build();

    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

    FetcherUtil.emitHeaderMetrics(
        maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus, ENROLLMENT_ID);

    // Verify
    verify(mLogger)
        .logMeasurementRegistrationsResponseSize(
            ArgumentMatchers.eq(
                new MeasurementRegistrationResponseStats.Builder(
                        AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                        registrationType,
                        headersMapSize,
                        UNKNOWN_SOURCE_TYPE,
                        APP_REGISTRATION_SURFACE_TYPE,
                        UNKNOWN_STATUS,
                        UNKNOWN_REGISTRATION_FAILURE_TYPE,
                        0,
                        REGISTRANT_URI.toString(),
                        0,
                        false,
                        false,
                        0,
                        false,
                        false)
                    .setAdTechDomain(REGISTRATION_URI.toString())
                    .build()),
            eq(ENROLLMENT_ID));
  }

  @Test
  public void emitHeaderMetrics_headersWithNullValues_success() {
    // Setup
    int registrationType = 1;
    long maxAllowedHeadersSize = 25;

    Map<String, List<String>> headersMap = new HashMap<>();
    headersMap.put("key1", Arrays.asList("val11", "val12"));
    headersMap.put("key2", null);
    int headersMapSize = 18;

    // Execution
    AsyncRegistration asyncRegistration =
        new AsyncRegistration.Builder()
            .setRegistrationId(UUID.randomUUID().toString())
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRegistrationUri(REGISTRATION_URI)
            .setRegistrant(REGISTRANT_URI)
            .build();

    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headersMap));

    FetcherUtil.emitHeaderMetrics(
        maxAllowedHeadersSize, mLogger, asyncRegistration, asyncFetchStatus, ENROLLMENT_ID);

    // Verify
    verify(mLogger)
        .logMeasurementRegistrationsResponseSize(
            ArgumentMatchers.eq(
                new MeasurementRegistrationResponseStats.Builder(
                        AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                        registrationType,
                        headersMapSize,
                        UNKNOWN_SOURCE_TYPE,
                        APP_REGISTRATION_SURFACE_TYPE,
                        UNKNOWN_STATUS,
                        UNKNOWN_REGISTRATION_FAILURE_TYPE,
                        0,
                        REGISTRANT_URI.toString(),
                        0,
                        false,
                        false,
                        0,
                        false,
                        false)
                    .setAdTechDomain(null)
                    .build()),
            eq(ENROLLMENT_ID));
  }

  @Test
  public void isValidAggregateDeduplicationKey_negativeValue() {
    assertFalse(FetcherUtil.isValidAggregateDeduplicationKey("-1"));
  }

  @Test
  public void isValidAggregateDeduplicationKey_nonNumericalValue() {
    assertFalse(FetcherUtil.isValidAggregateDeduplicationKey("w"));
  }

  @Test
  public void isValidAggregateDeduplicationKey_success() {
    assertTrue(FetcherUtil.isValidAggregateDeduplicationKey("18446744073709551615"));
    assertTrue(FetcherUtil.isValidAggregateDeduplicationKey("0"));
  }

  @Test
  public void isValidAggregateDeduplicationKey_nullValue_returnsFalse() {
    assertFalse(FetcherUtil.isValidAggregateDeduplicationKey(null));
  }

  @Test
  public void isValidAggregateDeduplicationKey_empty_returnsFalse() {
    assertFalse(FetcherUtil.isValidAggregateDeduplicationKey(""));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_nullValue_returnsFalse() {
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(/* attributionInfoHeader= */ null, mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_noMatchingKey_returnsFalse() {
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("preferred-platform=web"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_matchingKeyWrongFormat_returnsFalse() {
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("report-header-errors=os"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_matchingKeyEnabled_returnsTrue() {
    assertTrue(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("report-header-errors"), mFlags));
    assertTrue(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("report-header-errors=?1"), mFlags));
    assertTrue(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            List.of("preferred-platform=web, report-header-errors;"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_debugReportDisabled_returnsFalse() {
    when(mFlags.getMeasurementEnableDebugReport()).thenReturn(false);
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("report-header-errors"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_headerErrorReportDisabled_returnsFalse() {
    when(mFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(false);
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(List.of("report-header-errors"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_matchingKeyDisabled_returnsFalse() {
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            List.of("preferred-platform=web, report-header-errors=?0;"), mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_multipleKey_returnsLast() {
    assertTrue(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            List.of("preferred-platform=web," + " report-header-errors=?0;report-header-errors"),
            mFlags));
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            List.of("report-header-errors;report-header-errors=?0," + " preferred-platform=os;"),
            mFlags));
  }

  @Test
  public void isHeaderErrorDebugReportEnabled_multipleHeaders_returnsLast() {
    assertTrue(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            Arrays.asList("report-header-errors=?0", "report-header-errors"), mFlags));
    assertFalse(
        FetcherUtil.isHeaderErrorDebugReportEnabled(
            Arrays.asList("report-header-errors", "report-header-errors=?0"), mFlags));
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyObject_returnDefaultValue()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    JSONObject obj = new JSONObject();
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("0", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(0);
    assertThat(aggregateDebugReporting.getAggregationCoordinatorOrigin()).isNull();
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList()).isNull();
  }

  @Test
  public void getValidAggregateDebugReportingString_missingKeyPiece_returnDefaultValue()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    JSONObject obj = new JSONObject();
    obj.put("budget", 65536);
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("0", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyKeyPiece_returnDefaultValue()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "");
    obj.put("budget", 65536);
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("0", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
  }

  @Test
  public void getValidAggregateDebugReportingString_invalidKeyPiece_fails() throws JSONException {
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "1x3");
    obj.put("budget", 65536);
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyOrigin_fails() throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "");
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_originNotInAllowList_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination1.test");
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_originInAllowList_succeeds()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
    assertThat(Uri.parse("https://cloud.coordination.test"))
        .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyDebugData_succeeds() throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    obj.put("debug_data", new JSONArray());
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
    assertThat(Uri.parse("https://cloud.coordination.test"))
        .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().isEmpty()).isTrue();
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyDebugDataObject_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONArray emptyData = new JSONArray();
    emptyData.put(new JSONObject());
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    obj.put("debug_data", emptyData);
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_emptyDebugDataKeyPiece_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "");
    dataObj2.put("value", 65536);
    dataObj2.put("type", new JSONArray(Arrays.asList("source-max-event-states-limit")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_invalidDebugDataKeyPiece_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "1x3");
    dataObj2.put("value", 65536);
    dataObj2.put("type", new JSONArray(Arrays.asList("source-max-event-states-limit")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_valueExceedsLowerLimit_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 0);
    dataObj2.put("type", new JSONArray(Arrays.asList("source-max-event-states-limit")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_valueExceedsUpperLimit_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65537);
    dataObj2.put("type", new JSONArray(Arrays.asList("source-max-event-states-limit")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_invalidType_fails() throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65536);
    dataObj2.put(
        "type",
        new JSONArray(Arrays.asList("source-max-event-states-limit", "invalid-report-type")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_duplicateTypesInTheSameObject_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65536);
    dataObj2.put(
        "type",
        new JSONArray(
            Arrays.asList(
                "source-max-event-states-limit",
                "source-storage-limit",
                "source-max-event-states-limit")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_duplicateTypesInDifferentObjects_fails()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj1.put("type", new JSONArray(Arrays.asList("source-noised")));
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65536);
    dataObj2.put(
        "type", new JSONArray(Arrays.asList("source-max-event-states-limit", "source-noised")));
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    assertThat(FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).isPresent())
        .isFalse();
  }

  @Test
  public void getValidAggregateDebugReportingString_defaultReportType_succeeds()
      throws JSONException {
    // Setup
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65536);
    JSONArray types1 = new JSONArray(Arrays.asList("source-noised"));
    JSONArray types2 = new JSONArray(Arrays.asList("source-max-event-states-limit", "unspecified"));
    dataObj1.put("types", types1);
    dataObj2.put("types", types2);
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));

    // Execution
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();

    // Assertion
    assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
    assertThat(Uri.parse("https://cloud.coordination.test"))
        .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
    AggregateDebugReportData expectedDebugData1 =
        new AggregateDebugReportData.Builder(
                /* reportType= */ new HashSet<>(Arrays.asList("source-noised")),
                /* keyPiece= */ new BigInteger("3", 16),
                /* value= */ 65536)
            .build();
    AggregateDebugReportData expectedDebugData2 =
        new AggregateDebugReportData.Builder(
                /* reportType= */ new HashSet<>(
                    Arrays.asList("source-max-event-states-limit", "unspecified")),
                /* keyPiece= */ new BigInteger("3", 16),
                /* value= */ 65536)
            .build();

    assertThat(expectedDebugData1)
        .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList().get(0));
    assertThat(expectedDebugData2)
        .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList().get(1));
  }

  @Test
  public void getValidAggregateDebugReportingString_noDuplicatesTypes_succeeds()
      throws JSONException {
    when(mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()).thenReturn(65536);
    when(mFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    JSONObject obj = new JSONObject();
    obj.put("key_piece", "0x3");
    obj.put("budget", 65536);
    obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
    JSONObject dataObj1 = new JSONObject();
    JSONObject dataObj2 = new JSONObject();
    dataObj1.put("key_piece", "0x3");
    dataObj1.put("value", 65536);
    dataObj2.put("key_piece", "0x3");
    dataObj2.put("value", 65536);
    JSONArray types1 = new JSONArray(Arrays.asList("source-noised"));
    JSONArray types2 =
        new JSONArray(
            Arrays.asList("source-max-event-states-limit", "source-scopes-channel-capacity-limit"));
    dataObj1.put("types", types1);
    dataObj2.put("types", types2);
    obj.put("debug_data", new JSONArray(Arrays.asList(dataObj1, dataObj2)));
    String aggregateDebugReportingString =
        FetcherUtil.getValidAggregateDebugReportingWithBudget(obj, mFlags).get();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(new JSONObject(aggregateDebugReportingString)).build();
    assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
    assertThat(Uri.parse("https://cloud.coordination.test"))
        .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
    AggregateDebugReportData validDebugData1 =
        new AggregateDebugReportData.Builder(
                /* reportType= */ new HashSet<>(Arrays.asList("source-noised")),
                /* keyPiece= */ new BigInteger("3", 16),
                /* value= */ 65536)
            .build();
    AggregateDebugReportData validDebugData2 =
        new AggregateDebugReportData.Builder(
                /* reportType= */ new HashSet<>(
                    Arrays.asList(
                        "source-max-event-states-limit", "source-scopes-channel-capacity-limit")),
                /* keyPiece= */ new BigInteger("3", 16),
                /* value= */ 65536)
            .build();
    assertThat(Arrays.asList(validDebugData1, validDebugData2))
        .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
  }

  private Map<String, List<String>> createHeadersMap() {
    return new ImmutableMap.Builder<String, List<String>>()
        .put("key1", Arrays.asList("val11", "val12"))
        .put("key2", Arrays.asList("val21", "val22"))
        .build();
  }
}
