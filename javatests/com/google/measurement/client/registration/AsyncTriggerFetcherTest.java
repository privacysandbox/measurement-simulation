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

import static com.google.measurement.client.Flags.MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES;
import static com.google.measurement.client.Flags.MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE;
import static com.google.measurement.client.Flags.MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.measurement.client.stats.AdServicesErrorLogger;
import com.google.measurement.client.AdServicesExtendedMockitoTestCase;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.AttributionConfig;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.DbTestUtil;
import com.google.measurement.client.data.EnrollmentDao;
import com.google.measurement.client.ExpectErrorLogUtilCall;
import com.google.measurement.client.FakeFlagsFactory;
import com.google.measurement.client.FilterMap;
import com.google.measurement.client.Flags;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.stats.MeasurementRegistrationResponseStats;
import com.google.measurement.client.Pair;
import com.google.measurement.client.data.SQLDatastoreManager;
import com.google.measurement.client.SdkLevel;
import com.google.measurement.client.SetErrorLogUtilDefaultParams;
import com.google.measurement.client.Source.SourceType;
import com.google.measurement.client.SourceFixture;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebTriggerParams;
import com.google.measurement.client.WebUtil;
import com.google.measurement.client.aggregation.AggregateDebugReportData;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import com.google.measurement.client.ondevicepersonalization.NoOdpDelegationWrapper;
import com.google.measurement.client.ondevicepersonalization.OdpDelegationWrapperImpl;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.util.Enrollment;
import com.google.measurement.client.util.UnsignedLong;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
/** Unit tests for {@link AsyncTriggerFetcher} */
@RunWith(Parameterized.class)
public final class AsyncTriggerFetcherTest extends AdServicesExtendedMockitoTestCase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String ANDROID_APP_SCHEME = "android-app";
  private static final String ANDROID_APP_SCHEME_URI_PREFIX = ANDROID_APP_SCHEME + "://";
  private static final String SDK_PACKAGE_NAME = "sdk.package.name";
  private static final String TRIGGER_URI = WebUtil.validUrl("https://subdomain.foo.test");
  private static final String ENROLLMENT_ID = "enrollment-id";
  private static final String TOP_ORIGIN = WebUtil.validUrl("https://baz.test");
  private static final long TRIGGER_DATA = 7;
  private static final long PRIORITY = 1;
  private static final String LONG_FILTER_STRING = "12345678901234567890123456";
  private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
  private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
  private static final long DEDUP_KEY = 100;
  private static final UnsignedLong DEBUG_KEY = new UnsignedLong(34787843L);
  private static final String DEBUG_JOIN_KEY = "SAMPLE_DEBUG_JOIN_KEY";
  private static final String DEFAULT_REDIRECT = WebUtil.validUrl("https://subdomain.bar.test");
  private static final String EVENT_TRIGGERS_1 =
      "[\n"
          + "{\n"
          + "  \"trigger_data\": \""
          + TRIGGER_DATA
          + "\",\n"
          + "  \"priority\": \""
          + PRIORITY
          + "\",\n"
          + "  \"deduplication_key\": \""
          + DEDUP_KEY
          + "\",\n"
          + "  \"filters\": [{\n"
          + "    \"source_type\": [\"navigation\"],\n"
          + "    \"key_1\": [\"value_1\"] \n"
          + "   }]\n"
          + "}"
          + "]\n";
  private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
      "[{\"deduplication_key\": \""
          + DEDUP_KEY
          + "\",\n"
          + "\"filters\": {\n"
          + "  \"category_1\": [\"filter\"],\n"
          + "  \"category_2\": [\"filter\"] \n"
          + " },"
          + "\"not_filters\": {\n"
          + "  \"category_1\": [\"filter\"],\n"
          + "  \"category_2\": [\"filter\"] \n"
          + "}}"
          + "]";
  private static final String LIST_TYPE_REDIRECT_URI =
      WebUtil.validUrl("https://subdomain.bar.test");
  private static final String LOCATION_TYPE_REDIRECT_URI = WebUtil.validUrl("https://example.test");
  private static final String LOCATION_TYPE_REDIRECT_WELLKNOWN_URI =
      WebUtil.validUrl(
          LOCATION_TYPE_REDIRECT_URI
              + "/"
              + AsyncRedirects.WELL_KNOWN_PATH_SEGMENT
              + "?"
              + AsyncRedirects.WELL_KNOWN_QUERY_PARAM
              + "="
              + Uri.encode(LOCATION_TYPE_REDIRECT_URI));
  private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://subdomain.foo.test");
  private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
      new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
  private static final String DEFAULT_EVENT_TRIGGER_DATA = "[]";

  private static final int UNKNOWN_SOURCE_TYPE = 0;
  private static final int UNKNOWN_REGISTRATION_SURFACE_TYPE = 0;
  private static final int APP_REGISTRATION_SURFACE_TYPE = 2;
  private static final int UNKNOWN_STATUS = 0;
  private static final int SUCCESS_STATUS = 1;
  private static final int UNKNOWN_REGISTRATION_FAILURE_TYPE = 0;
  private static final String PLATFORM_AD_ID_VALUE = "SAMPLE_PLATFORM_AD_ID_VALUE";
  private static final String DEBUG_AD_ID_VALUE = "SAMPLE_DEBUG_AD_ID_VALUE";
  private static final String HEADER_ODP_REGISTER_TRIGGER = "Odp-Register-Trigger";
  private static final String ODP_PACKAGE_NAME = "com.adtech1";
  private static final String ODP_CLASS_NAME = "com.adtech1.AdTechIsolatedService";
  private static final String ODP_CERT_DIGEST = "AABBCCDD";
  private static final String ODP_EVENT_DATA = "123";
  private static final int mMaxAggregateDeduplicationKeysPerRegistration =
      Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;

  private AsyncTriggerFetcher mFetcher;
  private DatastoreManager mDatastoreManager;

  @Mock private HttpsURLConnection mUrlConnection;
  @Mock private HttpsURLConnection mUrlConnection1;
  @Mock private EnrollmentDao mEnrollmentDao;
  @Mock private AdServicesLogger mLogger;
  @Mock private AdServicesErrorLogger mErrorLogger;
  @Mock private DebugReportApi mDebugReportApi;

  // Parameterised setup to run all the tests once with ARA parsing V1 flag on, and once with the
  // flag off.
  private final Boolean mEnableUpdateTriggerHeaderSizeLimit;

  private MockedStatic<Enrollment> mEnrollmentMockedStatic;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> getConfig() throws IOException, JSONException {
    return List.of(
        new Object[] {"enabled_update_trigger_header_limit", Boolean.TRUE},
        new Object[] {"disabled_update_trigger_header_limit", Boolean.FALSE});
  }

  public AsyncTriggerFetcherTest(String name, Boolean enableUpdateTriggerHeaderSizeLimit) {
    mEnableUpdateTriggerHeaderSizeLimit = enableUpdateTriggerHeaderSizeLimit;
  }

  @Before
  public void setup() {
    mocker.mockGetFlags(FakeFlagsFactory.getFlagsForTest());
    mDatastoreManager =
        Mockito.spy(
            new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger));
    mFetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mMockFlags,
                mock(NoOdpDelegationWrapper.class),
                mDatastoreManager,
                mDebugReportApi));
    // For convenience, return the same enrollment-ID since we're using many arbitrary
    // registration URIs and not yet enforcing uniqueness of enrollment.
    mEnrollmentMockedStatic = mockStatic(Enrollment.class);
    mEnrollmentMockedStatic
        .when(() -> Enrollment.getValidEnrollmentId(any(), anyString(), any(), any(), any()))
        .thenReturn(Optional.of(ENROLLMENT_ID));
    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(false);
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(false);
    when(mMockFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
        .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
    when(mMockFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("");
    when(mMockFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration())
        .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
    when(mMockFlags.getMeasurementMaxAggregateDeduplicationKeysPerRegistration())
        .thenReturn(mMaxAggregateDeduplicationKeysPerRegistration);
    when(mMockFlags.getMeasurementMaxAttributionFilters())
        .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
    when(mMockFlags.getMeasurementMaxFilterMapsPerFilterSet())
        .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET);
    when(mMockFlags.getMeasurementMaxValuesPerAttributionFilter())
        .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
    when(mMockFlags.getMeasurementMaxSumOfAggregateValuesPerSource())
        .thenReturn(MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE);
    when(mMockFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
        .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    when(mMockFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds())
        .thenReturn(MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    when(mMockFlags.getMeasurementEnableUpdateTriggerHeaderLimit())
        .thenReturn(mEnableUpdateTriggerHeaderSizeLimit);
    when(mMockFlags.getMaxResponseBasedRegistrationPayloadSizeBytes())
        .thenReturn(MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
    when(mMockFlags.getMaxTriggerRegistrationHeaderSizeBytes())
        .thenReturn(MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
    when(mMockFlags.getMeasurementMaxLengthOfTriggerContextId())
        .thenReturn(MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID);
    when(mMockFlags.getMeasurementMaxAttributionScopeLength()).thenReturn(10);
    when(mMockFlags.getMeasurementMaxAttributionScopesPerSource()).thenReturn(10);
    when(mMockFlags.getMeasurementEnableDebugReport())
        .thenReturn(Flags.MEASUREMENT_ENABLE_DEBUG_REPORT);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport())
        .thenReturn(Flags.MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT);
  }

  @After
  public void cleanup() {
    mEnrollmentMockedStatic.close();
    flagsFactoryMockedStatic.close();
  }

  @Test
  public void testBasicTriggerRequest() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    MeasurementRegistrationResponseStats expectedStats =
        new MeasurementRegistrationResponseStats.Builder(
                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                223,
                UNKNOWN_SOURCE_TYPE,
                APP_REGISTRATION_SURFACE_TYPE,
                SUCCESS_STATUS,
                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                0,
                "",
                0,
                false,
                false,
                0,
                false,
                false)
            .setAdTechDomain(null)
            .build();
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    FetcherUtil.emitHeaderMetrics(
        MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
        mLogger,
        asyncRegistration,
        asyncFetchStatus,
        ENROLLMENT_ID);
    verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats), eq(ENROLLMENT_ID));
  }

  @Test
  public void testBasicTriggerRequest_withAggregateDeduplicationKey() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    MeasurementRegistrationResponseStats expectedStats =
        new MeasurementRegistrationResponseStats.Builder(
                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                436,
                UNKNOWN_SOURCE_TYPE,
                APP_REGISTRATION_SURFACE_TYPE,
                SUCCESS_STATUS,
                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                0,
                "",
                0,
                false,
                false,
                0,
                false,
                false)
            .setAdTechDomain(null)
            .build();
    String wrappedFilters =
        "[{\n" + "  \"category_1\": [\"filter\"],\n" + "  \"category_2\": [\"filter\"] \n" + " }]";
    String wrappedNotFilters =
        "[{\n" + "  \"category_1\": [\"filter\"],\n" + "  \"category_2\": [\"filter\"] \n" + "}]";
    String expectedAggregateDedupKeys =
        "[{\"deduplication_key\": \""
            + DEDUP_KEY
            + "\",\n"
            + "\"filters\": "
            + wrappedFilters
            + ","
            + "\"not_filters\":"
            + wrappedNotFilters
            + "}"
            + "]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + AGGREGATE_DEDUPLICATION_KEYS_1
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(
        new JSONArray(expectedAggregateDedupKeys).toString(),
        result.getAggregateDeduplicationKeys());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    FetcherUtil.emitHeaderMetrics(
        MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES,
        mLogger,
        asyncRegistration,
        asyncFetchStatus,
        ENROLLMENT_ID);
    verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats), eq(ENROLLMENT_ID));
  }

  @Test
  public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAString_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String wrappedFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String wrappedNotFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String aggregateDedupKeys =
        "[{\"deduplication_key\":756,"
            + "\"filters\":"
            + wrappedFilters
            + ","
            + "\"not_filters\":"
            + wrappedNotFilters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + aggregateDedupKeys
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregateDeduplicationKey_dedupKeyNegative_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String wrappedFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String wrappedNotFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String aggregateDedupKeys =
        "[{\"deduplication_key\":\"-756\","
            + "\"filters\":"
            + wrappedFilters
            + ","
            + "\"not_filters\":"
            + wrappedNotFilters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + aggregateDedupKeys
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregateDeduplicationKey_dedupKeyTooLarge_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String wrappedFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String wrappedNotFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String aggregateDedupKeys =
        "[{\"deduplication_key\":\"18446744073709551616\","
            + "\"filters\":"
            + wrappedFilters
            + ","
            + "\"not_filters\":"
            + wrappedNotFilters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + aggregateDedupKeys
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregateDeduplicationKey_dedupKeyNotAnInt_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String wrappedFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String wrappedNotFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String aggregateDedupKeys =
        "[{\"deduplication_key\":\"756a\","
            + "\"filters\":"
            + wrappedFilters
            + ","
            + "\"not_filters\":"
            + wrappedNotFilters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + aggregateDedupKeys
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregateDeduplicationKey_dedupKeyMissing_succeeds() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String wrappedFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String wrappedNotFilters =
        "[{" + "\"category_1\":[\"filter\"]," + "\"category_2\":[\"filter\"]" + "}]";
    String aggregateDedupKeys =
        "[{\"filters\":" + wrappedFilters + "," + "\"not_filters\":" + wrappedNotFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + aggregateDedupKeys
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(aggregateDedupKeys, result.getAggregateDeduplicationKeys());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  // Tests for redirect types

  @Test
  public void testRedirectType_bothRedirectHeaderTypes_choosesListType() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate both 'list' and 'location' type headers
    headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(2, asyncRedirects.getRedirects().size());
    assertEquals(
        LIST_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
            .get(0)
            .getUri()
            .toString());
    assertEquals(
        LOCATION_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
            .get(0)
            .getUri()
            .toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectHeaderType_choosesLocationType() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate only 'location' type header
    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectType_acceptsListType() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate both 'list' and 'location' type headers
    headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(2, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
            .get(0)
            .getUri()
            .toString());
    assertEquals(
        LIST_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
            .get(0)
            .getUri()
            .toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectHeaderType_wellKnownPath() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate only 'location' type header
    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
    headers.put(
        AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
        List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_WELLKNOWN_URI,
        asyncRedirects.getRedirects().get(0).getUri().toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectType_wellKnownPath_acceptsList() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
    headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));
    headers.put(
        AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
        List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(2, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_WELLKNOWN_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LOCATION)
            .get(0)
            .getUri()
            .toString());
    assertEquals(
        LIST_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
            .get(0)
            .getUri()
            .toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testWellKnownPathHeader_noop() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate behavior config header, but no redirects.
    headers.put(
        AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
        List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(0, asyncRedirects.getRedirects().size());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testWellKnownPathHeader_acceptsListTypeWithNoLocationType() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headers = getDefaultHeaders();

    // Populate behavior config header, but no Location type redirect.
    headers.put(
        AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG,
        List.of(AsyncRedirects.REDIRECT_302_TO_WELL_KNOWN));
    headers.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(LIST_TYPE_REDIRECT_URI));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(1, asyncRedirects.getRedirects().size());

    assertEquals(
        LIST_TYPE_REDIRECT_URI,
        asyncRedirects
            .getRedirectsByType(AsyncRegistration.RedirectType.LIST)
            .get(0)
            .getUri()
            .toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectHeaderType_wellKnownHeaderMisconfigured()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
    headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, List.of("BAD"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testRedirectType_locationRedirectHeaderType_wellKnownHeaderNull() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(302);
    Map<String, List<String>> headers = getDefaultHeaders();

    headers.put(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY, List.of(LOCATION_TYPE_REDIRECT_URI));
    headers.put(AsyncRedirects.HEADER_ATTRIBUTION_REPORTING_REDIRECT_CONFIG, null);

    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertTriggerRegistration(asyncRegistration, result);

    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(
        LOCATION_TYPE_REDIRECT_URI, asyncRedirects.getRedirects().get(0).getUri().toString());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  // End tests for redirect types

  @Test
  public void fetchTrigger_eventTriggerDataNull_eventTriggerDataEqualsEmptyArray()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "null";
    String expectedResult = DEFAULT_EVENT_TRIGGER_DATA;
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(expectedResult, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_tooManyFilterMaps() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder eventTriggers = new StringBuilder("[{\"trigger_data\":\"2\",\"filters\":[");
    for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
      eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    }
    eventTriggers.append("}]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filterMapIncludesReservedPrefix_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggers =
        "[{\"trigger_data\":\"2\",\"filters\":["
            + "{\"_something_reserved\":[\"val1\", \"val2\"]}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_eventTriggerDataMissing_eventTriggerDataEqualsEmptyArray()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String expectedResult = DEFAULT_EVENT_TRIGGER_DATA;
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"debug_key\": \"" + DEBUG_KEY + "\"}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(expectedResult, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_eventTriggerDataEmptyObjects_emptyObjectsPopulated() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\"},{},{}]";
    String expectedResult =
        "[{\"trigger_data\":\"2\"},{\"trigger_data\":\"0\"},{\"trigger_data\":\"0\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(expectedResult, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_tooManyNotFilterMaps() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder eventTriggers = new StringBuilder("[{\"trigger_data\":\"2\",\"not_filters\":[");
    int i;
    for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
      eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
    }
    eventTriggers.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    eventTriggers.append("}]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilterMapIncludesReservedPrefix_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggers =
        "[{\"trigger_data\":\"2\",\"not_filters\":["
            + "{\"_something_reserved\":[\"val1\", \"val2\"]}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggers + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_triggerDataNotAString_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":2,\"priority\":\"101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_triggerDataNegative_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"-2\",\"priority\":\"101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_triggerDataTooLarge_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"18446744073709551616\",\"priority\":\"101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_triggerDataNotAnInt_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"101z\",\"priority\":\"101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_triggerData_uses64thBit() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"18446744073709551615\",\"priority\":\"101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerData, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerDataWithValue_success() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":156}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerData, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerDataWithValueTooSmall_fail() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":0}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerDataWithValueTooLarge_fail() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    long tooLarge = TriggerSpecs.MAX_BUCKET_THRESHOLD + 1L;
    String eventTriggerData =
        "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":" + tooLarge + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerDataWithValueAsString_fail() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"14\",\"priority\":\"101\",\"value\":\"123\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_priority_negative() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"-101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerData, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_priorityNotAString_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":74}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_priorityTooLarge_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"18446744073709551615\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_priorityNotAnInt_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"a101\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_deduplicationKeyNotAString_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"deduplication_key\":34}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_deduplicationKeyNegative_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"deduplication_key\":\"-34\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_deduplicationKeyTooLarge_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\","
            + "\"deduplication_key\":\"18446744073709551616\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_deduplicationKeyNotAnInt_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"deduplication_key\":\"145l\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_deduplicationKey_uses64thBit() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\","
            + "\"deduplication_key\":\"18446744073709551615\"}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerData, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_wrapsFilters() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData = "[{\"trigger_data\":\"2\",\"filters\":{\"id\":[\"val1\",\"val2\"]}}]";
    String eventTriggerDataWithWrappedFilters =
        "[{\"trigger_data\":\"2\",\"filters\":[{\"id\":[\"val1\",\"val2\"]}]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerDataWithWrappedFilters, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filters_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filters_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filters_tooManyValues() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    filters.append("]}");
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_filters_valueTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\",\"filters\":" + filters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_wrapsNotFilters() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"not_filters\":{\"id\":[\"val1\",\"val2\"]}}]";
    String eventTriggerDataWithWrappedNotFilters =
        "[{\"trigger_data\":\"2\",\"not_filters\":[{\"id\":[\"val1\",\"val2\"]}]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(eventTriggerDataWithWrappedNotFilters, result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilters_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters = "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"not_filters\":" + notFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilters_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters = new StringBuilder("{");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    notFilters.append("}");
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"not_filters\":" + notFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters =
        "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"not_filters\":" + notFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilters_tooManyValues() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    notFilters.append("]}");
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"not_filters\":" + notFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
  }

  @Test
  public void testTriggerRequest_eventTriggerData_notFilters_valueTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters =
        "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    String eventTriggerData =
        "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"not_filters\":" + notFilters + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_wrapsFilters() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}";
    String wrappedFilters = "[{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(wrappedFilters, result.getFilters());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_tooManyFilterMaps() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("[");
    for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
      filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    }
    filters.append("]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filterMapIncludesReservedPrefix_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "[{\"_something_reserved\":[\"val1\", \"val2\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersMissingDefaultToNull_success()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString()).isNull();
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonObject_success() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValues = "{\"campaignCounts\":32768}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + validAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    assertThat(asyncFetchStatus.getEntityStatus()).isEqualTo(AsyncFetchStatus.EntityStatus.SUCCESS);
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(validAggregatableValues).toString());
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayEmptyValues_success()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValuesArr = "[{\"values\": {}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + validAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    assertThat(asyncFetchStatus.getEntityStatus()).isEqualTo(AsyncFetchStatus.EntityStatus.SUCCESS);
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONArray(validAggregatableValuesArr).toString());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isTrue();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersInvalidType_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    String invalidAggregatableValues = "{" + "\"aggregatable_values\":\"12.0\"" + "}";
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of("Attribution-Reporting-Register-Trigger", List.of(invalidAggregatableValues)));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
    assertThat(asyncFetchStatus.getEntityStatus())
        .isEqualTo(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArray_success() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValuesArr =
        "["
            + "{"
            + "\"values\": {\"campaignCounts\": 32768, \"geoValue\": 1664}, "
            + "\"filters\":"
            + " {\"category\": [\"filter_1\", \"filter_2\"]}"
            + "},"
            + "{"
            + "\"values\": {\"campaignCounts\": 65535},"
            + "\"filters\": {"
            + " \"category\": [\"filter_1\"]},"
            + "\"not_filters\": {"
            + " \"category\": [\"filter_2\"]}"
            + "}"
            + "]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + validAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
    Trigger result = fetch.get();
    assertEquals(
        new JSONArray(validAggregatableValuesArr).toString(), result.getAggregateValuesString());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isTrue();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayMissingValues_fail()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValuesArr = "[{}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
    assertThat(asyncFetchStatus.getEntityStatus())
        .isEqualTo(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayValuesTooLarge_fail()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValuesArr =
        "[{\"values\": {\"campaignCounts\": 65537, \"geoValue\": 1664}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayFiltersInvalidJson_fail()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidFilters = "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
    String invalidAggregatableValuesArr =
        "[{\"values\": {\"campaignCounts\": 32768, \"geoValue\": 1664}, \"filters\":"
            + invalidFilters
            + "}]";

    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayTooManyFilterMaps_fail()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    filters.append("]}");
    String invalidAggregatableValuesArr =
        "[{\"values\": {\"campaignCounts\": 32768, \"geoValue\": 1664}, \"filters\":"
            + filters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_aggregateValueFiltersJsonArrayTooManyNotFilterMaps_fail()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder invalidNotFilters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    invalidNotFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    invalidNotFilters.append("]}");
    String invalidAggregatableValuesArr =
        "[{\"values\": {\"campaignCounts\": 32768, \"geoValue\": 1664}, \"not_filters\":"
            + invalidNotFilters
            + "}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertThat(asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringJsonObject_success() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768, \"filtering_id" + "\":\"1\"},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"aggregatable_values\":"
                        + validAggregatableValues
                        + ","
                        + "\"aggregatable_filtering_id_max_bytes\":"
                        + 2
                        + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(validAggregatableValues).toString());
    assertThat(result.getAggregatableFilteringIdMaxBytes()).isEqualTo(2);
  }

  @Test
  public void testTriggerRequest_flexibleFilteringValueTooLarge_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues =
        "{\"campaignCounts\": {\"value\": 65537, \"filtering_id" + "\":\"1\"},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\":" + invalidAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringInvalidValueType_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues = "{\"campaignCounts\":\"1664\"}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringTooSmallFilterIdMaxBytes_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_filtering_id_max_bytes\":" + 0 + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringTooLargeFilterIdMaxBytes_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_filtering_id_max_bytes\":" + 9 + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringInvalidFilterIdMaxBytesType_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_filtering_id_max_bytes\":" + "2" + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringFilteringIdMaxBytesDefaultToOne_success()
      throws Exception {
    // filtering_id is valid because default maxBytes is 1
    // 255 is within range of 0-255 inclusive
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768, \"filtering_id" + "\":\"255\"},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + validAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    assertThat(asyncFetchStatus.getEntityStatus()).isEqualTo(AsyncFetchStatus.EntityStatus.SUCCESS);
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(validAggregatableValues).toString());
  }

  @Test
  public void testTriggerRequest_flexibleFilteringJsonObjectFilteringIdWithinRange_passes()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768, \"filtering_id"
            + "\":\"65535\"},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"aggregatable_values\":"
                        + validAggregatableValues
                        + ","
                        + "\"aggregatable_filtering_id_max_bytes\":"
                        + 2
                        + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    assertThat(asyncFetchStatus.getEntityStatus()).isEqualTo(AsyncFetchStatus.EntityStatus.SUCCESS);
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(validAggregatableValues).toString());
    assertThat(result.getAggregatableFilteringIdMaxBytes()).isEqualTo(2);
  }

  @Test
  public void testTriggerRequest_flexibleFilteringJsonObjectFilteringIdExceedsDefaultRange_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768," + " \"filtering_id\":\"256\"},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\":" + invalidAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringJsonObjectInvalidFilteringIdInvalidType_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768," + " \"filtering_id\":20},\"geoValue\":1664}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\":" + invalidAggregatableValues + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringWithoutAggregateValueFilters_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValuesArr =
        "[{\"values\":{\"campaignCounts\":{\"value\":32768, \"filtering_id\":\"123\"},"
            + " \"geoValue\":1664}, \"filters\":{\"category\":[\"filter_1\"]},"
            + " \"not_filters\":{\"category\":[\"filter_2\"]}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"aggregatable_values\":"
                        + validAggregatableValuesArr
                        + ","
                        + "\"aggregatable_filtering_id_max_bytes\":"
                        + 2
                        + "}")));
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringWithAggregateValueFilters_success()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String validAggregatableValuesArr =
        "[{\"values\":{\"campaignCounts\":{\"value\":32768, \"filtering_id\":\"123\"},"
            + " \"geoValue\":1664}, \"filters\":{\"category\":[\"filter_1\"]},"
            + " \"not_filters\":{\"category\":[\"filter_2\"]}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"aggregatable_values\":"
                        + validAggregatableValuesArr
                        + ","
                        + "\"aggregatable_filtering_id_max_bytes\":"
                        + 2
                        + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isTrue();
    assertThat(asyncFetchStatus.getEntityStatus()).isEqualTo(AsyncFetchStatus.EntityStatus.SUCCESS);
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONArray(validAggregatableValuesArr).toString());
    assertThat(result.getAggregatableFilteringIdMaxBytes()).isEqualTo(2);
  }

  @Test
  public void testTriggerRequest_flexibleFilteringJsonArrayValueNotIntOrJsonObject_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValuesArr = "[{\"values\":{\"campaignCounts\":\"123\"}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void
      testTriggerRequest_flexibleFilteringFilteringIdExceedsDefaultFilteringIdMaxBytes_fails()
          throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    String invalidAggregatableValuesArr =
        "[{\"values\":{\"campaignCounts\":{\"value\":32768, \"filtering_id\":\"257\"},"
            + " \"geoValue\":1664}, \"filters\":{\"category\":[\"filter_1\"]},"
            + " \"not_filters\":{\"category\":[\"filter_2\"]}}]";
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\":" + invalidAggregatableValuesArr + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_flexibleFilteringFilteringIdExceedsFilteringIdMaxBytes_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    String invalidAggregatableValuesArr =
        "[{\"values\":{\"campaignCounts\":{\"value\":32768, \"filtering_id\":\"65537\"},"
            + " \"geoValue\":1664}, \"filters\":{\"category\":[\"filter_1\"]},"
            + " \"not_filters\":{\"category\":[\"filter_2\"]}}]";
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"aggregatable_values\":"
                        + invalidAggregatableValuesArr
                        + ","
                        + "\"aggregatable_filtering_id_max_bytes\":"
                        + 2
                        + "}")));
    when(mMockFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMockFlags.getMeasurementDefaultFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
    when(mMockFlags.getMeasurementMaxFilteringIdMaxBytes())
        .thenReturn(Flags.MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
  }

  @Test
  public void testTriggerRequest_filterMapIncludesNullValue_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "[{\"something\":[\"val1\", null]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filterMapIncludesNumericValue_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "[{\"something\":[\"val1\", 347]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filters_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_tooManyEntries() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    int maxAggregateDeduplicationKeysPerRegistration =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder tooManyEntries = new StringBuilder("[");
    tooManyEntries.append(
        IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration + 1)
            .mapToObj(i -> "{\"deduplication_key\": \"" + i + "\"}")
            .collect(Collectors.joining(",")));
    tooManyEntries.append("]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + tooManyEntries
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_missingDeduplicationKey()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder deduplicationKeys = new StringBuilder("[");
    int maxAggregateDeduplicationKeysPerRegistration =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
    deduplicationKeys.append(
        IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
            .mapToObj(i -> "{\"deduplication_key\":}")
            .collect(Collectors.joining(",")));
    deduplicationKeys.append("]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + deduplicationKeys
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    int maxAggregateDeduplicationKeysPerRegistration =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    StringBuilder notFilters = new StringBuilder("{");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS)
            .mapToObj(i -> "\"not-filter-string-" + i + "\": [\"not-filter-value\"]")
            .collect(Collectors.joining(",")));
    notFilters.append("}");
    StringBuilder deduplicationKeys = new StringBuilder("[");
    deduplicationKeys.append(
        IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
            .mapToObj(
                i ->
                    "{\"deduplication_key\":"
                        + "\""
                        + i
                        + "\""
                        + ",\"filters\" :"
                        + filters
                        + ","
                        + "\"not_filters\" :\""
                        + notFilters
                        + "}")
            .collect(Collectors.joining(",")));
    deduplicationKeys.append("]");

    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ",\"aggregatable_deduplication_keys\":"
                        + deduplicationKeys
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_tooManyNotFilters() throws Exception {
    int maxAggregateDeduplicationKeysPerRegistration =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    StringBuilder notFilters = new StringBuilder("{");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"not-filter-string-" + i + "\": [\"not-filter-value\"]")
            .collect(Collectors.joining(",")));
    notFilters.append("}");
    StringBuilder deduplicationKeys = new StringBuilder("[");
    deduplicationKeys.append(
        IntStream.range(0, maxAggregateDeduplicationKeysPerRegistration)
            .mapToObj(
                i ->
                    "{\"deduplication_key\":"
                        + "\""
                        + i
                        + "\""
                        + ",\"filters\" :"
                        + filters
                        + ","
                        + "\"not_filters\" :\""
                        + notFilters
                        + "}")
            .collect(Collectors.joining(",")));
    deduplicationKeys.append("]");

    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + deduplicationKeys
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_deduplicationKeyIsEmpty()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":{}"
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_AggregateDeduplicationKeys_deduplicationKeysNotPresent()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ","
                        + "\"aggregatable_deduplication_keys\":"
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filters_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filters_tooManyValues() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    filters.append("]}");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_filters_valueTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_positiveLookbackWindow_acceptsTrigger() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": 123}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_invalidLookbackWindowValidJSON_rejectsTrigger() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": \"123\"}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_zeroLookbackWindow_rejectsTrigger() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": 0}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void fetchTrigger_negativeLookbackWindow_rejectsTrigger() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\"], \"ctid\":[\"id\"], \"_lookback_window\": -123}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\":" + filters + "}")));
    when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_wrapsNotFilters() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters = "{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}";
    String wrappedNotFilters = "[{\"product\":[\"1234\",\"2345\"],\"cid\":[\"id\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(wrappedNotFilters, result.getNotFilters());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_tooManyNotFilterMaps() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters = new StringBuilder("[");
    for (int i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET + 1; i++) {
      notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    }
    notFilters.append("]");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilterMapIncludesReservedPrefix_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "[{\"_something_reserved\":[\"val1\", \"val2\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilters_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters = "{\"product\":[\"1234\",\"2345\"], \"\"\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilters_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters = new StringBuilder("{");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    notFilters.append("}");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters =
        "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilters_tooManyValues() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    notFilters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    notFilters.append("]}");
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequest_notFilters_valueTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters =
        "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"not_filters\":" + notFilters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  @ExpectErrorLogUtilCall(errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID)
  public void testBasicTriggerRequest_skipTriggerWhenNotEnrolled_processRedirects()
      throws IOException {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    mEnrollmentMockedStatic
        .when(() -> Enrollment.getValidEnrollmentId(any(), anyString(), any(), any(), any()))
        .thenReturn(Optional.empty());
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                List.of(DEFAULT_REDIRECT),
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    verify(mFetcher, times(1)).openUrl(any());
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(DEFAULT_REDIRECT, asyncRedirects.getRedirects().get(0).getUri().toString());
    assertEquals(
        AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
  }

  @Test
  public void testBasicTriggerRequestWithDebugKey() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequest_debugKeyNegative_setToNull() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_key\":\"-376\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertNull(result.getDebugKey());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequest_debugKeyNotAString_setToNull() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_key\":376}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertNull(result.getDebugKey());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequest_debugKey_tooLarge() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":"
                + EVENT_TRIGGERS_1
                + ",\"debug_key\":\"18446744073709551616\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertNull(result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequest_debugKey_notAnInt() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_key\":\"65g43\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertNull(result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequest_debugKey_uses64thBit() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":"
                + EVENT_TRIGGERS_1
                + ",\"debug_key\":\"18446744073709551615\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(new UnsignedLong(-1L), result.getDebugKey());
  }

  @Test
  public void registerTrigger_nonHttpsUrl_rejectsTrigger() throws Exception {
    RegistrationRequest request =
        RegistrationRequestFixture.getInvalidRegistrationRequest(
            RegistrationRequest.REGISTER_TRIGGER,
            WebUtil.validUri("http://foo.test"),
            sContext.getPackageName(),
            SDK_PACKAGE_NAME);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
  }

  @Test
  public void testBadTriggerUrl() throws Exception {
    AsyncRegistration registration =
        createAsyncRegistration(
            UUID.randomUUID().toString(),
            WebUtil.validUri("bad-schema://foo.test"),
            null,
            null,
            Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
            null,
            Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
            AsyncRegistration.RegistrationType.APP_SOURCE,
            null,
            System.currentTimeMillis(),
            0,
            false,
            false,
            null);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch = mFetcher.fetchTrigger(registration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.INVALID_URL, asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
  }

  @Test
  public void testBadTriggerConnection() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doThrow(new IOException("Bad internet things")).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.ResponseStatus.NETWORK_ERROR, asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, never()).setRequestMethod("POST");
  }

  @Test
  public void testBadRequestReturnFailure() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(400);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE, asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_eventTriggerDataNoFields_triggerDataEqualsZero() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\": [{}]}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
    when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);
    Map<String, List<String>> headersFirstRequest = new HashMap<>();
    headersFirstRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    headersFirstRequest.put(AsyncRedirects.REDIRECT_LIST_HEADER_KEY, List.of(DEFAULT_REDIRECT));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
  }

  @Test
  public void testMissingRegistrationHeaderButWithRedirect_noHeaderErrorDebugReport()
      throws Exception {
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                List.of(DEFAULT_REDIRECT),
                "Attribution-Reporting-Info",
                List.of("report-header-errors")))
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(DEFAULT_REDIRECT, asyncRedirects.getRedirects().get(0).getUri().toString());

    assertEquals(AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mDebugReportApi, never())
        .scheduleHeaderErrorReport(
            any(),
            any(),
            any(),
            eq("Attribution-Reporting-Register-Trigger"),
            eq(ENROLLMENT_ID),
            any(),
            any());
  }

  @Test
  public void testInvalidRegistrationHeaderWithRedirect_sendHeaderErrorDebugReport()
      throws Exception {
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                List.of(DEFAULT_REDIRECT),
                "Attribution-Reporting-Info",
                List.of("report-header-errors"),
                "Attribution-Reporting-Register-Trigger",
                List.of()))
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(1, asyncRedirects.getRedirects().size());
    assertEquals(DEFAULT_REDIRECT, asyncRedirects.getRedirects().get(0).getUri().toString());

    assertEquals(AsyncFetchStatus.EntityStatus.HEADER_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());

    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mDebugReportApi, times(1))
        .scheduleHeaderErrorReport(
            any(),
            any(),
            any(),
            eq("Attribution-Reporting-Register-Trigger"),
            eq(ENROLLMENT_ID),
            any(),
            any());
  }

  @Test
  public void testBasicTriggerRequestWithAggregateTriggerData() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
            + "\"x_network_data\":{\"key_offset\":\"20\"}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(aggregateTriggerData, result.getAggregateTriggerData());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void basicTriggerRequest_withInvalidOffsetInAggregateTriggerData_throwsParsingError()
      throws IOException {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
            + "\"x_network_data\":{\"key_offset\":\"INVALID_VALUE\"}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void basicTriggerRequest_withNoOffsetInAggregateTriggerData_consideredValid()
      throws IOException {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"],"
            + "\"x_network_data\":{}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(aggregateTriggerData, result.getAggregateTriggerData());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithAggregateTriggerData_wrapsFilters() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    String aggregateTriggerDataWithWrappedFilters =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(aggregateTriggerDataWithWrappedFilters, result.getAggregateTriggerData());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithAggregateTriggerData_tooManyFilterMaps() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("[");
    int i;
    for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
      filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
    }
    filters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    filters.append("]");
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + filters
            + "},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_trigger_data\":" + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequestWithAggregateTriggerData_filterMapIncludesReservedPrefix_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters =
        "[{\"key-1\":[\"val1\", \"val2\"]}," + "{\"_reserved_prefix\":[\"val1\", \"val2\"]}]";
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + filters
            + "},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_trigger_data\":" + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testBasicTriggerRequestWithAggregateTriggerData_tooManyNotFilterMaps()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder notFilters = new StringBuilder("[");
    int i;
    for (i = 0; i < Flags.DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET; i++) {
      notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]},");
    }
    notFilters.append("{\"key-" + i + "\":[\"val1" + i + "\", \"val2" + i + "\"]}");
    notFilters.append("]");
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\":"
            + notFilters
            + "},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_trigger_data\":" + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequestWithAggregateTriggerData_notFilterMapIncludesReservedPrefix_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String notFilters =
        "[{\"key-1\":[\"val1\", \"val2\"]}," + "{\"_reserved_prefix\":[\"val1\", \"val2\"]}]";
    String aggregateTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\":"
            + notFilters
            + "},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_trigger_data\":" + aggregateTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testBasicTriggerRequestWithAggregateValues() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    Trigger result = fetch.get();
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(aggregatableValues).toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithAggregateValues_valueWithTrailingZeros_success()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\": 1664.0}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(new JSONObject(aggregatableValues).toString(), result.getAggregateValuesString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithAggregateTriggerData_rejectsTooManyValueKeys()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    StringBuilder tooManyKeys = new StringBuilder("{");
    int i = 0;
    for (; i < 50; i++) {
      tooManyKeys.append(String.format("\"key-%s\": 12345,", i));
    }
    tooManyKeys.append(String.format("\"key-%s\": 12345}", i));
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\": " + tooManyKeys + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(fetch.isPresent()).isFalse();
    assertThat(asyncFetchStatus.getEntityStatus())
        .isEqualTo(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void basicTriggerRequest_triggerContextIdSet_IncludeSourceRegistrationTime_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mMockFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"trigger_context_id\": \"test_context_id\""
                        + ", \"aggregatable_source_registration_time\": \""
                        + Trigger.SourceRegistrationTimeConfig.INCLUDE.name()
                        + "\""
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequest_triggerContextIdNotAString_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mMockFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"trigger_context_id\": 42" + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequest_triggerContextIdExceedsMaxLength_fails() throws Exception {
    String triggerContextId = "a".repeat(MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID + 1);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mMockFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"trigger_context_id\": "
                        + "\""
                        + triggerContextId
                        + "\", "
                        + "\"aggregatable_source_registration_time\": \""
                        + Trigger.SourceRegistrationTimeConfig.EXCLUDE.name()
                        + "\""
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequest_triggerContextIdSet_succeeds() throws Exception {
    String triggerContextId = "test_context_id";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mMockFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"trigger_context_id\": "
                        + "\""
                        + triggerContextId
                        + "\""
                        + ", \"aggregatable_source_registration_time\": \""
                        + Trigger.SourceRegistrationTimeConfig.EXCLUDE.name()
                        + "\""
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(triggerContextId, result.getTriggerContextId());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void basicTriggerRequest_triggerContextIdSet_defaultSourceRegistrationTimeConfig_succeeds()
      throws Exception {
    String triggerContextId = "test_context_id";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mMockFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"trigger_context_id\": " + "\"" + triggerContextId + "\"" + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(triggerContextId, result.getTriggerContextId());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_withReportingFilters_success() throws IOException, JSONException {
    // Setup
    String filters =
        "[{\n"
            + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
            + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
            + "}]";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"filters\": " + filters + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(new JSONArray(filters).toString(), result.getFilters());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_basic_success() throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_withExtendedHeaders_success() throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(
            Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
    String filters =
        "[{\n"
            + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
            + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
            + "}]";
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\": "
                        + EVENT_TRIGGERS_1
                        + ", \"filters\": "
                        + filters
                        + ", \"aggregatable_values\": "
                        + aggregatableValues
                        + ", \"aggregatable_trigger_data\": "
                        + aggregatableTriggerData
                        + "}")));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    Trigger result = fetch.get();
    assertThat(result.getEventTriggers()).isEqualTo(new JSONArray(EVENT_TRIGGERS_1).toString());
    assertThat(result.getAggregateTriggerData())
        .isEqualTo((new JSONArray(aggregatableTriggerData).toString()));
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(aggregatableValues).toString());
    assertThat(result.getFilters()).isEqualTo(new JSONArray(filters).toString());
    assertThat(result.getRegistrationOrigin().toString()).isEqualTo(TRIGGER_URI);
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_withRedirects_ignoresRedirects() throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(
            Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\": " + EVENT_TRIGGERS_1 + "}"),
                AsyncRedirects.REDIRECT_LIST_HEADER_KEY,
                List.of(LIST_TYPE_REDIRECT_URI),
                AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY,
                List.of(LOCATION_TYPE_REDIRECT_URI)));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(0, asyncRedirects.getRedirects().size());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    verify(mUrlConnection).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":\"campaignCounts\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_missingPrefix()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_tooLong()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\""
            + LONG_AGGREGATE_KEY_PIECE
            + "\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_sourceKeysMissing_setsDefault()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\"}]";
    // (Wraps filters)
    String expectedResult =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\":"
            + "[{\"conversion_subdomain\":[\"electronics.megastore\"]}],"
            + "\"not_filters\":[{\"product\":[\"1\"]}]},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(expectedResult, result.getAggregateTriggerData());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_sourceKeysNotAnArray_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":{\"campaignCounts\": true},"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_sourceKeys_tooManyKeys() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder tooManyKeys = new StringBuilder("[");
    tooManyKeys.append(
        IntStream.range(0, mMockFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration() + 1)
            .mapToObj(i -> "aggregate-key-" + i)
            .collect(Collectors.joining(",")));
    tooManyKeys.append("]");
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\": "
            + tooManyKeys
            + ","
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_sourceKeys_keyIdNotAString_fails()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\",35],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_sourceKeys_invalidKeyId()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\", \""
            + LONG_AGGREGATE_KEY_ID
            + "\"],"
            + "\"filters\":"
            + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_filters_tooManyFilters() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\": "
            + filters
            + ","
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_filters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\": "
            + filters
            + ","
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_filters_tooManyValues() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    filters.append("]}");
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\": "
            + filters
            + ","
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_filters_valueTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"filters\": "
            + filters
            + ","
            + "\"not_filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyFilters()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters = new StringBuilder("{");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS + 1)
            .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
            .collect(Collectors.joining(",")));
    filters.append("}");
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\": "
            + filters
            + ","
            + "\"filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_notFilters_keyTooLong() throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\": "
            + filters
            + ","
            + "\"filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyValues()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    StringBuilder filters =
        new StringBuilder(
            "{" + "\"filter-string-1\": [\"filter-value-1\"]," + "\"filter-string-2\": [");
    filters.append(
        IntStream.range(0, Flags.DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
            .mapToObj(i -> "\"filter-value-" + i + "\"")
            .collect(Collectors.joining(",")));
    filters.append("]}");
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\": "
            + filters
            + ","
            + "\"filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregateTriggerData_notFilters_valueTooLong()
      throws Exception {
    Assume.assumeFalse(mEnableUpdateTriggerHeaderSizeLimit);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String filters = "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
    String aggregatableTriggerData =
        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
            + "\"not_filters\": "
            + filters
            + ","
            + "\"filters\":{\"product\":[\"1\"]}},"
            + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_trigger_data\": " + aggregatableTriggerData + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testBasicTriggerRequestWithDebugReportingHeader() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_reporting\":\"true\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertTrue(result.isDebugReporting());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithInvalidDebugReportingHeader() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_reporting\":\"invalid\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertFalse(result.isDebugReporting());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithNullDebugReportingHeader() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_reporting\":\"null\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertFalse(result.isDebugReporting());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithNoQuotesDebugReportingHeader() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + ",\"debug_reporting\":invalid}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertFalse(result.isDebugReporting());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithEmptyDebugReportingHeader() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertFalse(result.isDebugReporting());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void testBasicTriggerRequestWithAggregatableValues() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertThat(fetch.isPresent()).isTrue();
    Trigger result = fetch.get();
    assertThat(result.getEnrollmentId()).isEqualTo(ENROLLMENT_ID);
    assertThat(result.getAggregateValuesString())
        .isEqualTo(new JSONObject(aggregatableValues).toString());
    assertThat(result.getRegistrationOrigin().toString()).isEqualTo(TRIGGER_URI);
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void triggerRequest_aggregatableValueJsonObject_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues =
        "{\"campaignCounts\": {\"value\": 32768, \"filtering_id\": \"1\"}}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + invalidAggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
  }

  @Test
  public void triggerRequest_aggregatableValueJsonArrayNotAccepted_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues =
        "[{\"values\":{\"campaignCounts\":{\"value\":32768, \"filtering_id\":\"123\"},"
            + " \"geoValue\":1664}, \"filters\":{\"category\":[\"filter_1\"]},"
            + " \"not_filters\":{\"category\":[\"filter_2\"]}}]";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + invalidAggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
  }

  @Test
  public void triggerRequest_aggregatableValueLessThanOne_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":0}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregatableValueNotAnInt_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":\"1a2\"}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregatableValueWithFractional_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String invalidAggregatableValues = "{\"campaignCounts\":32768.01,\"geoValue\":1644}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + invalidAggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregatableValueLongValue_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    Long longValue = ((long) Integer.MAX_VALUE) + 1L;
    String aggregatableValues =
        "{\"campaignCounts\":32768,\"geoValue\":" + longValue.toString() + "}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void triggerRequest_aggregatableValueTooLarge_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    Integer tooLarge = MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE + 1;
    String aggregatableValues =
        "{\"campaignCounts\":32768,\"geoValue\":" + tooLarge.toString() + "}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregatableValues_invalidJson() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues = "{\"campaignCounts\":32768\"geoValue\":1644}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregatableValues_tooManyKeys() throws Exception {
    StringBuilder tooManyKeys = new StringBuilder("{");
    tooManyKeys.append(
        IntStream.range(0, mMockFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration() + 1)
            .mapToObj(i -> String.format("\"key-%s\": 12345,", i))
            .collect(Collectors.joining(",")));
    tooManyKeys.append("}");
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"aggregatable_values\": " + tooManyKeys + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void testTriggerRequestWithAggregatableValues_invalidKeyId() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    String aggregatableValues =
        "{\"campaignCounts\":32768, \"" + LONG_AGGREGATE_KEY_ID + "\":1644}";
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"aggregatable_values\": " + aggregatableValues + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            appTriggerRegistrationRequest(request), asyncFetchStatus, asyncRedirects);
    assertFalse(fetch.isPresent());
    verify(mUrlConnection, times(1)).setRequestMethod("POST");
    verify(mFetcher, times(1)).openUrl(any());
  }

  @Test
  public void basicTriggerRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
      throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    MeasurementRegistrationResponseStats expectedStats =
        new MeasurementRegistrationResponseStats.Builder(
                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                223,
                UNKNOWN_SOURCE_TYPE,
                APP_REGISTRATION_SURFACE_TYPE,
                SUCCESS_STATUS,
                UNKNOWN_REGISTRATION_FAILURE_TYPE,
                0,
                "",
                0,
                false,
                false,
                0,
                false,
                false)
            .setAdTechDomain(WebUtil.validUrl("https://foo.test"))
            .build();
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setRetryCount(0);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    FetcherUtil.emitHeaderMetrics(5L, mLogger, asyncRegistration, asyncFetchStatus, ENROLLMENT_ID);
    verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats), eq(ENROLLMENT_ID));
  }

  @Test
  public void basicTriggerRequest_headersMoreThanMaxResponseSize_logsSizeExceededError()
      throws Exception {
    // This test is only valid when this flag is enabled.
    Assume.assumeTrue(mEnableUpdateTriggerHeaderSizeLimit);
    Map<String, List<String>> headers = getDefaultHeaders();
    long headerSize = FetcherUtil.calculateHeadersCharactersLength(headers);
    when(mMockFlags.getMaxTriggerRegistrationHeaderSizeBytes()).thenReturn(headerSize - 1);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setRetryCount(0);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    assertEquals(
        AsyncFetchStatus.ResponseStatus.HEADER_SIZE_LIMIT_EXCEEDED,
        asyncFetchStatus.getResponseStatus());
    assertFalse(fetch.isPresent());
  }

  @Test
  public void basicTriggerRequest_headersWithinMaxResponseSize_success() throws Exception {
    // This test is only valid when this flag is enabled.
    Assume.assumeTrue(mEnableUpdateTriggerHeaderSizeLimit);
    Map<String, List<String>> headers = getDefaultHeaders();
    long headerSize = FetcherUtil.calculateHeadersCharactersLength(headers);
    when(mMockFlags.getMaxTriggerRegistrationHeaderSizeBytes()).thenReturn(headerSize);

    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields()).thenReturn(headers);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setRetryCount(0);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
  }

  @Test
  public void triggerRequest_appRegWithValidAttributionConfig_parsesCorrectly() throws Exception {
    // Setup
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    String originalAttributionConfigString =
        "[{\n"
            + "\"source_network\": \"AdTech1-Ads\",\n"
            + "\"source_priority_range\": {\n"
            + "\"start\": 100,\n"
            + "\"end\": 1000\n"
            + "},\n"
            + "\"source_filters\": {\n"
            + "\"campaign_type\": [\"install\"]"
            + "},\n"
            + "\"source_not_filters\": {\n"
            + "\"product\": [\"prod1\"]"
            + "},\n"
            + "\"priority\": \"99\",\n"
            + "\"expiry\": \"604800\",\n"
            + "\"source_expiry_override\": \"1209600\",\n"
            + "\"post_install_exclusivity_window\": \"5000\",\n"
            + "\"filter_data\": {\n"
            + "\"campaign_type\": [\"install\"]\n"
            + "}\n"
            + "},"
            + "{\n"
            + "\"source_network\": \"AdTech2-Ads\"}"
            + "]";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"attribution_config\":"
                        + originalAttributionConfigString
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    List<FilterMap> sourceFilters =
        Collections.singletonList(
            new FilterMap.Builder()
                .setAttributionFilterMap(
                    Map.of("campaign_type", Collections.singletonList("install")))
                .build());
    List<FilterMap> sourceNotFilters =
        Collections.singletonList(
            new FilterMap.Builder()
                .setAttributionFilterMap(Map.of("product", Collections.singletonList("prod1")))
                .build());
    FilterMap filterData =
        new FilterMap.Builder()
            .setAttributionFilterMap(Map.of("campaign_type", Collections.singletonList("install")))
            .build();
    AttributionConfig attributionConfig1 =
        new AttributionConfig.Builder()
            .setSourceAdtech("AdTech1-Ads")
            .setSourcePriorityRange(new Pair<>(100L, 1000L))
            .setSourceFilters(sourceFilters)
            .setSourceNotFilters(sourceNotFilters)
            .setPriority(99L)
            .setExpiry(604800L)
            .setSourceExpiryOverride(1209600L)
            .setPostInstallExclusivityWindow(5000L)
            .setFilterData(filterData)
            .build();
    AttributionConfig attributionConfig2 =
        new AttributionConfig.Builder().setSourceAdtech("AdTech2-Ads").build();

    JSONArray expectedAttributionConfigJsonArray =
        new JSONArray(
            Arrays.asList(
                attributionConfig1.serializeAsJson(mMockFlags),
                attributionConfig2.serializeAsJson(mMockFlags)));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(expectedAttributionConfigJsonArray.toString(), result.getAttributionConfig());
  }

  @Test
  public void triggerRequest_allowListedWebRegWithValidAttributionConfig_parsesCorrectly()
      throws Exception {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(List.of(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList())
        .thenReturn(sContext.getPackageName() + ",some_other_package");
    String originalAttributionConfigString =
        "[{\n"
            + "\"source_network\": \"AdTech1-Ads\",\n"
            + "\"source_priority_range\": {\n"
            + "\"start\": 100,\n"
            + "\"end\": 1000\n"
            + "},\n"
            + "\"source_filters\": {\n"
            + "\"campaign_type\": [\"install\"]"
            + "},\n"
            + "\"source_not_filters\": {\n"
            + "\"product\": [\"prod1\"]"
            + "},\n"
            + "\"priority\": \"99\",\n"
            + "\"expiry\": \"604800\",\n"
            + "\"source_expiry_override\": \"1209600\",\n"
            + "\"post_install_exclusivity_window\": \"5000\",\n"
            + "\"filter_data\": {\n"
            + "\"campaign_type\": [\"install\"]\n"
            + "}\n"
            + "},"
            + "{\n"
            + "\"source_network\": \"AdTech2-Ads\"}"
            + "]";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"attribution_config\":"
                        + originalAttributionConfigString
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    List<FilterMap> sourceFilters =
        Collections.singletonList(
            new FilterMap.Builder()
                .setAttributionFilterMap(
                    Map.of("campaign_type", Collections.singletonList("install")))
                .build());
    List<FilterMap> sourceNotFilters =
        Collections.singletonList(
            new FilterMap.Builder()
                .setAttributionFilterMap(Map.of("product", Collections.singletonList("prod1")))
                .build());
    FilterMap filterData =
        new FilterMap.Builder()
            .setAttributionFilterMap(Map.of("campaign_type", Collections.singletonList("install")))
            .build();
    AttributionConfig attributionConfig1 =
        new AttributionConfig.Builder()
            .setSourceAdtech("AdTech1-Ads")
            .setSourcePriorityRange(new Pair<>(100L, 1000L))
            .setSourceFilters(sourceFilters)
            .setSourceNotFilters(sourceNotFilters)
            .setPriority(99L)
            .setExpiry(604800L)
            .setSourceExpiryOverride(1209600L)
            .setPostInstallExclusivityWindow(5000L)
            .setFilterData(filterData)
            .build();
    AttributionConfig attributionConfig2 =
        new AttributionConfig.Builder().setSourceAdtech("AdTech2-Ads").build();

    JSONArray expectedAttributionConfigJsonArray =
        new JSONArray(
            Arrays.asList(
                attributionConfig1.serializeAsJson(mMockFlags),
                attributionConfig2.serializeAsJson(mMockFlags)));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = webTriggerRegistrationRequest(request, true);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(expectedAttributionConfigJsonArray.toString(), result.getAttributionConfig());
  }

  @Test
  public void triggerRequest_disallowListedWebRegWithValidAttributionConfig_doesntParse()
      throws Exception {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(List.of(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // Doesn't have the app package
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("some_other_package");
    String originalAttributionConfigString =
        "[{\n"
            + "\"source_network\": \"AdTech1-Ads\",\n"
            + "\"source_priority_range\": {\n"
            + "\"start\": 100,\n"
            + "\"end\": 1000\n"
            + "},\n"
            + "\"source_filters\": {\n"
            + "\"campaign_type\": [\"install\"]"
            + "},\n"
            + "\"source_not_filters\": {\n"
            + "\"product\": [\"prod1\"]"
            + "},\n"
            + "\"priority\": \"99\",\n"
            + "\"expiry\": \"604800\",\n"
            + "\"source_expiry_override\": \"680\",\n"
            + "\"post_install_exclusivity_window\": \"5000\",\n"
            + "\"filter_data\": {\n"
            + "\"campaign_type\": [\"install\"]\n"
            + "}\n"
            + "},"
            + "{\n"
            + "\"source_network\": \"AdTech2-Ads\"}"
            + "]";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"attribution_config\":"
                        + originalAttributionConfigString
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = webTriggerRegistrationRequest(request, true);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertNull(result.getAttributionConfig());
  }

  @Test
  public void triggerRequest_attributionConfigThrowingJsonException_dropsTheTriggerCompletely()
      throws Exception {
    // Setup
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"attribution_config\": INVALID_JSON_ARRAY"
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
  }

  @Test
  public void testBadTriggerJson_sendHeaderErrorDebugReport() throws Exception {
    // Setup
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);
    String headerWithJsonError = "{[[aaa[[[[}}}";
    String headerName = "Attribution-Reporting-Register-Trigger";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // Opt-in header error debug report by adding header "Attribution-Reporting-Info";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                headerName,
                List.of(headerWithJsonError),
                "Attribution-Reporting-Info",
                List.of("report-header-errors")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mDebugReportApi, times(1))
        .scheduleHeaderErrorReport(
            any(), any(), any(), eq(headerName), eq(ENROLLMENT_ID), eq(headerWithJsonError), any());
  }

  @Test
  public void testBadTriggerJson_headerErrorReportFlagDisabled_doNotSend() throws Exception {
    // Setup
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(false);
    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);
    String headerWithJsonError = "{[[aaa[[[[}}}";
    String headerName = "Attribution-Reporting-Register-Trigger";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // Opt-in header error debug report by adding header "Attribution-Reporting-Info";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                headerName,
                List.of(headerWithJsonError),
                "Attribution-Reporting-Info",
                List.of("report-header-errors")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mDebugReportApi, never())
        .scheduleHeaderErrorReport(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  public void testBadTriggerJson_notOptInHeaderErrorDebugReport_doNotSend() throws Exception {
    // Setup
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    String headerWithJsonError = "{[[aaa[[[[}}}";
    String headerName = "Attribution-Reporting-Register-Trigger";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // Didn't opt-in header error debug report by adding header "Attribution-Reporting-Info";
    when(mUrlConnection.getHeaderFields())
        .thenReturn(Map.of(headerName, List.of(headerWithJsonError)));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mDebugReportApi, never())
        .scheduleHeaderErrorReport(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  public void testBadTriggerJson_invalidOptInHeaderErrorDebugReport_doNotSend() throws Exception {
    // Setup
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    String headerWithJsonError = "{[[aaa[[[[}}}";
    String headerName = "Attribution-Reporting-Register-Trigger";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // Invalid opt-in string for header error debug report.
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                headerName,
                List.of(headerWithJsonError),
                "Attribution-Reporting-Info",
                List.of("invalid-string")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.PARSING_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mDebugReportApi, never())
        .scheduleHeaderErrorReport(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  public void testAttributionInfoHeaderOnly_doNotSendHeaderErrorDebugReport() throws Exception {
    // Setup
    when(mMockFlags.getMeasurementEnableDebugReport()).thenReturn(true);
    when(mMockFlags.getMeasurementEnableHeaderErrorDebugReport()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    // The header fields only contains attribution info header and have no registration header
    // or redirect header.
    when(mUrlConnection.getHeaderFields())
        .thenReturn(Map.of("Attribution-Reporting-Info", List.of("report-header-errors")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, new AsyncRedirects());

    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mDebugReportApi, never())
        .scheduleHeaderErrorReport(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  public void triggerRequest_withValidAdtechBitMapping_storesCorrectly() throws Exception {
    // Setup
    String validAdTechBitMapping = "{\"AdTechA-Ads\":\"0x1\",\"AdtechB-Ads\":\"0x2\"}";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"x_network_key_mapping\":"
                        + validAdTechBitMapping
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(validAdTechBitMapping, result.getAdtechKeyMapping());
  }

  @Test
  public void triggerRequest_withInvalidAdtechBitMapping_dropsBitMapping() throws Exception {
    // Setup
    String invalidAdTechBitMapping =
        "{"
            // Values don't start with 0x -- invalid
            + "\"AdTechA-Ads\": 1234,"
            + "\"AdTechB-Ads\": 2"
            + "}";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"x_network_key_mapping\":"
                        + invalidAdTechBitMapping
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertNull(result.getAdtechKeyMapping());
  }

  @Test
  public void triggerRequest_xnaDisabled_nullXNAFields() throws Exception {
    // Setup
    String validAdTechBitMapping = "{\"AdTechA-Ads\":\"0x1\",\"AdTechB-Ads\":\"0x2\"}";
    String validAttributionConfig =
        "[{\n"
            + "\"source_network\": \"AdTech1-Ads\",\n"
            + "\"source_priority_range\": {\n"
            + "\"start\": 100,\n"
            + "\"end\": 1000\n"
            + "},\n"
            + "\"source_filters\": {\n"
            + "\"campaign_type\": [\"install\"]"
            + "},\n"
            + "\"source_not_filters\": {\n"
            + "\"product\": [\"prod1\"]"
            + "},\n"
            + "\"priority\": \"99\",\n"
            + "\"expiry\": \"604800\",\n"
            + "\"source_expiry_override\": \"680\",\n"
            + "\"post_install_exclusivity_window\": \"5000\",\n"
            + "\"filter_data\": {\n"
            + "\"campaign_type\": [\"install\"]\n"
            + "}\n"
            + "},"
            + "{\n"
            + "\"source_network\": \"AdTech2-Ads\"}"
            + "]";
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{"
                        + "\"event_trigger_data\":"
                        + EVENT_TRIGGERS_1
                        + ", \"adtech_bit_mapping\":"
                        + validAdTechBitMapping
                        + ", \"attribution_config\":"
                        + validAttributionConfig
                        + "}")));
    doReturn(5000L).when(mMockFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    when(mMockFlags.getMeasurementEnableXNA()).thenReturn(false);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertNull(result.getAdtechKeyMapping());
    assertNull(result.getAttributionConfig());
  }

  @Test
  public void fetchWebTriggers_withoutSourceRegistrationTimeConfig_defaultToExclude()
      throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{" + "\"event_trigger_data\": " + EVENT_TRIGGERS_1 + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        result.getAggregatableSourceRegistrationTimeConfig());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_withSourceRegistrationTimeConfig_excludeValue_getsParsed()
      throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"aggregatable_source_registration_time\": \""
                + Trigger.SourceRegistrationTimeConfig.EXCLUDE.name()
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        result.getAggregatableSourceRegistrationTimeConfig());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_withSourceRegistrationTimeConfig_includeValue_getsParsed()
      throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"aggregatable_source_registration_time\": \""
                + Trigger.SourceRegistrationTimeConfig.INCLUDE.name()
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        result.getAggregatableSourceRegistrationTimeConfig());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTrigger_withInvalidSourceRegistrationTime_returnsEmpty() throws IOException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    when(mMockFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"aggregatable_source_registration_time\": \""
                + "INVALID"
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    assertTrue(fetch.isEmpty());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
  }

  @Test
  public void fetchWebTriggers_withDebugJoinKey_getsParsed() throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + ", \"debug_join_key\": \""
                + DEBUG_JOIN_KEY
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTriggers_withDebugJoinKeyEnrollmentNotAllowlisted_joinKeyDropped()
      throws IOException, JSONException {
    // Setup
    when(mMockFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
        .thenReturn("some_random_enrollment1,some_random_enrollment2");
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + ", \"debug_join_key\": \""
                + DEBUG_JOIN_KEY
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(REGISTRATION_URI_1, result.getRegistrationOrigin());
    assertNull(result.getDebugJoinKey());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_basicWithDebugJoinKey_getsParsed() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":"
                + EVENT_TRIGGERS_1
                + ",\"debug_key\":\""
                + DEBUG_KEY
                + "\" ,\"debug_join_key\":\""
                + DEBUG_JOIN_KEY
                + "\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_basicWithDebugJoinKeyEnrollmentNotInAllowlist_joinKeyDropped()
      throws Exception {
    when(mMockFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":"
                + EVENT_TRIGGERS_1
                + ",\"debug_key\":\""
                + DEBUG_KEY
                + "\" ,\"debug_join_key\":\""
                + DEBUG_JOIN_KEY
                + "\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    assertNull(result.getDebugJoinKey());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_setsRegistrationOriginWithoutPath_forRegistrationURIWithPath()
      throws Exception {
    String uri = WebUtil.validUrl("https://test1.example.test/path1");
    RegistrationRequest request = buildRequest(uri);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        WebUtil.validUrl("https://test1.example.test"), result.getRegistrationOrigin().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_setsRegistrationOriginWithPort_forRegistrationURIWithPort()
      throws Exception {
    String uri = WebUtil.validUrl("https://test1.example.test:8081");
    RegistrationRequest request = buildRequest(uri);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        WebUtil.validUrl("https://test1.example.test:8081"),
        result.getRegistrationOrigin().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_appRegistrationWithAdId_encodedAdIdAddedToTrigger() throws Exception {
    RegistrationRequest request = buildRequestWithAdId(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);

    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{\"event_trigger_data\":"
                + EVENT_TRIGGERS_1
                + ",\"debug_key\":\""
                + DEBUG_KEY
                + "\"}"));

    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequestWithAdId(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
    assertEquals(PLATFORM_AD_ID_VALUE, result.getPlatformAdId());
  }

  @Test
  public void fetchWebTrigger_withDebugAdIdValue_getsParsed() throws IOException, JSONException {
    // Setup
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + ", \"debug_ad_id\": \""
                + DEBUG_AD_ID_VALUE
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_AD_ID_VALUE, result.getDebugAdId());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTrigger_withDebugAdIdValue_enrollmentBlockListed_doesNotGetParsed()
      throws IOException, JSONException {
    // Setup
    when(mMockFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist())
        .thenReturn(ENROLLMENT_ID);
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + ", \"debug_ad_id\": \""
                + DEBUG_AD_ID_VALUE
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertNull(result.getDebugAdId());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchWebTrigger_withDebugAdIdValue_blockListMatchesAll_doesNotGetParsed()
      throws IOException, JSONException {
    // Setup
    when(mMockFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist()).thenReturn("*");
    WebTriggerRegistrationRequest request =
        buildWebTriggerRegistrationRequest(Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
    doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
    when(mUrlConnection1.getResponseCode()).thenReturn(200);
    when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("");
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + ", \"debug_ad_id\": \""
                + DEBUG_AD_ID_VALUE
                + "\""
                + "}"));
    when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(
            webTriggerRegistrationRequest(request, true), asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertNull(result.getDebugAdId());
    verify(mUrlConnection1).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrue() throws Exception {
    String uri = WebUtil.validUrl("https://test1.example.test:8081");
    RegistrationRequest request = buildRequest(uri);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    doReturn(true).when(mMockFlags).isDisableMeasurementEnrollmentCheck();
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        WebUtil.validUrl("https://test1.example.test:8081"),
        result.getRegistrationOrigin().toString());
    assertEquals("https://example.com", result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForLocalhost()
      throws Exception {
    String uri = WebUtil.validUrl("https://localhost:8081");
    RegistrationRequest request = buildRequest(uri);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    doReturn(true).when(mMockFlags).isDisableMeasurementEnrollmentCheck();
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        WebUtil.validUrl("https://localhost:8081"), result.getRegistrationOrigin().toString());
    assertEquals("https://localhost", result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_setsSiteEnrollmentId_whenDisableEnrollmentFlagIsTrueForIP()
      throws Exception {
    String uri = WebUtil.validUrl("https://127.0.0.1:8081");
    RegistrationRequest request = buildRequest(uri);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(uri));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    doReturn(true).when(mMockFlags).isDisableMeasurementEnrollmentCheck();
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of(
            "{"
                + "\"event_trigger_data\": "
                + EVENT_TRIGGERS_1
                + ", \"debug_key\": \""
                + DEBUG_KEY
                + "\""
                + "}"));
    when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        WebUtil.validUrl("https://127.0.0.1:8081"), result.getRegistrationOrigin().toString());
    assertEquals("https://127.0.0.1", result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(DEBUG_KEY, result.getDebugKey());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_aggregatorCoordinatorOriginAllowed_pass() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + " [{}],"
                        + "'aggregation_coordinator_origin': "
                        + "'https://cloud.coordination.test'"
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
    assertEquals(
        Uri.parse("https://cloud.coordination.test"), result.getAggregationCoordinatorOrigin());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_aggregatorCoordinatorOriginDisallowed_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + " [{}],"
                        + "'aggregation_coordinator_origin': "
                        + "'https://invalid.cloud.coordination.test'"
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_nullCloudCoordinatorOrigin() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "\"event_trigger_data\": " + "[{}]" + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(AsyncFetchStatus.EntityStatus.SUCCESS, asyncFetchStatus.getEntityStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    assertEquals("[{\"trigger_data\":\"0\"}]", result.getEventTriggers());
    assertNull(result.getAggregationCoordinatorOrigin());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_emptyCloudCoordinatorOrigin_fails() throws Exception {
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"event_trigger_data\":"
                        + " [{}],"
                        + "'aggregation_coordinator_origin': "
                        + "'https://invalid.cloud.coordination.test'"
                        + "}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertEquals(
        AsyncFetchStatus.EntityStatus.VALIDATION_ERROR, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void getOdpWrapper_sdkVersionBelowT_failed() {
    try (MockedStatic<SdkLevel> sdkLevelMockedStatic = mockStatic(SdkLevel.class)) {
      sdkLevelMockedStatic.when(SdkLevel::isAtLeastT).thenReturn(false);
      when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
      when(mMockFlags.getMeasurementEnableOdpWebTriggerRegistration()).thenReturn(true);
      doReturn(sContext.getPackageManager()).when(mMockContext).getPackageManager();
      AsyncTriggerFetcher fetcher = new AsyncTriggerFetcher(mMockContext);
      assertTrue(fetcher.getOdpWrapper() instanceof NoOdpDelegationWrapper);
    }
  }

  @Test
  public void getOdpWrapper_odpFlagDisabled_failed() {
    try (MockedStatic<SdkLevel> sdkLevelMockedStatic = mockStatic(SdkLevel.class)) {
      sdkLevelMockedStatic.when(SdkLevel::isAtLeastT).thenReturn(true);
      when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
      when(mMockFlags.getMeasurementEnableOdpWebTriggerRegistration()).thenReturn(false);
      doReturn(sContext.getPackageManager()).when(mMockContext).getPackageManager();
      AsyncTriggerFetcher fetcher = new AsyncTriggerFetcher(mMockContext);
      assertTrue(fetcher.getOdpWrapper() instanceof NoOdpDelegationWrapper);
    }
  }

  @Test
  public void getOdpWrapper_odpUnavailable_returnsNoOdpWrapper() {
    try (MockedStatic<SdkLevel> sdkLevelMockedStatic = mockStatic(SdkLevel.class)) {
      sdkLevelMockedStatic.when(SdkLevel::isAtLeastT).thenReturn(true);
      when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
      when(mMockFlags.getMeasurementEnableOdpWebTriggerRegistration()).thenReturn(true);
      doReturn(sContext.getPackageManager()).when(mMockContext).getPackageManager();
      doReturn(null)
          .when(mMockContext)
          .getSystemService(OnDevicePersonalizationSystemEventManager.class);
      AsyncTriggerFetcher fetcher = new AsyncTriggerFetcher(mMockContext);
      assertTrue(fetcher.getOdpWrapper() instanceof NoOdpDelegationWrapper);
    }
  }

  @Test
  public void getOdpWrapper_odpAvailable_success() {
    Assume.assumeTrue(SdkLevel.isAtLeastT());
    OnDevicePersonalizationSystemEventManager odpManager =
        mock(OnDevicePersonalizationSystemEventManager.class);
    when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
    when(mMockFlags.getMeasurementEnableOdpWebTriggerRegistration()).thenReturn(true);
    doReturn(sContext.getPackageManager()).when(mMockContext).getPackageManager();
    doReturn(odpManager)
        .when(mMockContext)
        .getSystemService(OnDevicePersonalizationSystemEventManager.class);
    AsyncTriggerFetcher fetcher = new AsyncTriggerFetcher(mMockContext);
    assertTrue(fetcher.getOdpWrapper() instanceof OdpDelegationWrapperImpl);
  }

  @Test
  public void fetchTrigger_odpHeaderPresent_reportingHeaderPresent_forwardOdpTrigger()
      throws Exception {
    OdpDelegationWrapperImpl odpDelegationWrapperImplMock = mock(OdpDelegationWrapperImpl.class);
    AsyncTriggerFetcher fetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mMockFlags,
                odpDelegationWrapperImplMock,
                mDatastoreManager,
                mDebugReportApi));

    RegistrationRequest request = buildRequest(TRIGGER_URI);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    List odpHeaderValue =
        List.of(
            "{"
                + "\"service\":\""
                + ODP_PACKAGE_NAME
                + "/"
                + ODP_CLASS_NAME
                + "\","
                + "\"certDigest\":\""
                + ODP_CERT_DIGEST
                + "\","
                + "\"data\":\""
                + ODP_EVENT_DATA
                + "\""
                + "}");
    headersRequest.put(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue);
    doReturn(mUrlConnection).when(fetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields()).thenReturn(Collections.unmodifiableMap(headersRequest));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        fetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    verify(odpDelegationWrapperImplMock, times(1))
        .registerOdpTrigger(
            eq(asyncRegistration),
            eq(Map.of(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue)),
            eq(true));
  }

  @Test
  public void fetchTrigger_odpHeaderPresent_reportingHeaderMissing_forwardOdpTrigger()
      throws Exception {
    OdpDelegationWrapperImpl odpDelegationWrapperImplMock = mock(OdpDelegationWrapperImpl.class);
    AsyncTriggerFetcher fetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mMockFlags,
                odpDelegationWrapperImplMock,
                mDatastoreManager,
                mDebugReportApi));

    RegistrationRequest request = buildRequest(TRIGGER_URI);
    Map<String, List<String>> headersRequest = new HashMap<>();
    List odpHeaderValue =
        List.of(
            "{"
                + "\"service\":\""
                + ODP_PACKAGE_NAME
                + "/"
                + ODP_CLASS_NAME
                + "\","
                + "\"certDigest\":\""
                + ODP_CERT_DIGEST
                + "\","
                + "\"data\":\""
                + ODP_EVENT_DATA
                + "\""
                + "}");
    headersRequest.put(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue);
    doReturn(mUrlConnection).when(fetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields()).thenReturn(Collections.unmodifiableMap(headersRequest));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        fetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.EntityStatus.HEADER_MISSING, asyncFetchStatus.getEntityStatus());
    assertFalse(fetch.isPresent());
    verify(mUrlConnection).setRequestMethod("POST");
    verify(odpDelegationWrapperImplMock, times(1))
        .registerOdpTrigger(
            eq(asyncRegistration),
            eq(Map.of(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue)),
            eq(true));
  }

  @Test
  public void fetchTrigger_odpHeaderMissing_reportingHeaderPresent_odpTriggerNotForwarded()
      throws Exception {
    OdpDelegationWrapperImpl odpDelegationWrapperImplMock = mock(OdpDelegationWrapperImpl.class);
    AsyncTriggerFetcher fetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mMockFlags,
                odpDelegationWrapperImplMock,
                mDatastoreManager,
                mDebugReportApi));

    RegistrationRequest request = buildRequest(TRIGGER_URI);
    Map<String, List<String>> headersRequest = new HashMap<>();
    headersRequest.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    doReturn(mUrlConnection).when(fetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields()).thenReturn(Collections.unmodifiableMap(headersRequest));

    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
    // Execution
    Optional<Trigger> fetch =
        fetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    verify(odpDelegationWrapperImplMock, never()).registerOdpTrigger(any(), any(), anyBoolean());
  }

  @Test
  public void fetchTrigger_odpHeaderSizeIgnored_forwardOdpTrigger() throws Exception {
    Map<String, List<String>> reportingHeaderOnly = new HashMap<>();
    reportingHeaderOnly.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    when(mMockFlags.getMaxTriggerRegistrationHeaderSizeBytes())
        .thenReturn(FetcherUtil.calculateHeadersCharactersLength(reportingHeaderOnly));
    when(mMockFlags.getMeasurementEnableUpdateTriggerHeaderLimit()).thenReturn(true);

    Map<String, List<String>> reportingAndOdpHeaders = new HashMap<>();
    reportingAndOdpHeaders.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    List odpHeaderValue =
        List.of(
            "{"
                + "\"service\":\""
                + ODP_PACKAGE_NAME
                + "/"
                + ODP_CLASS_NAME
                + "\","
                + "\"certDigest\":\""
                + ODP_CERT_DIGEST
                + "\","
                + "\"data\":\""
                + ODP_EVENT_DATA
                + "\""
                + "}");
    reportingAndOdpHeaders.put(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue);

    OdpDelegationWrapperImpl odpDelegationWrapperImplMock = mock(OdpDelegationWrapperImpl.class);
    AsyncTriggerFetcher fetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mMockFlags,
                odpDelegationWrapperImplMock,
                mDatastoreManager,
                mDebugReportApi));
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(fetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(Collections.unmodifiableMap(reportingAndOdpHeaders));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    asyncFetchStatus.setRegistrationDelay(0L);
    asyncFetchStatus.setRetryCount(0);
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        fetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);

    // Assertion
    assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getResponseStatus());
    assertTrue(fetch.isPresent());
    Trigger result = fetch.get();
    assertEquals(
        asyncRegistration.getTopOrigin().toString(), result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(TRIGGER_URI, result.getRegistrationOrigin().toString());
    verify(mUrlConnection).setRequestMethod("POST");
    verify(odpDelegationWrapperImplMock, times(1))
        .registerOdpTrigger(
            any(), eq(Map.of(HEADER_ODP_REGISTER_TRIGGER, odpHeaderValue)), eq(true));
  }

  @Test
  public void fetchTrigger_attributionScopeEnabled_parsesAttributionScopeFields() throws Exception {
    when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"attribution_scopes\": [\"5\", \"6\"]}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAttributionScopes()).containsExactly("5", "6");
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_attributionScopeNotAString_fails() throws Exception {
    when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"attribution_scopes\": [1]}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isFalse();
    assertThat(asyncFetchStatus.getEntityStatus())
        .isEqualTo(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_attributionScopeEmpty_pass() throws Exception {
    when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of("{\"attribution_scopes\": [\"\"]}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAttributionScopes()).containsExactly("");
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_zeroAttributionScopes_pass() throws Exception {
    when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger", List.of("{\"attribution_scopes\": []}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Execution
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAttributionScopes()).isEmpty();
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_attributionScopeDisabled_skipsAttributionScopeFields() throws Exception {
    when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(false);
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger", List.of("{\"attribution_scopes\": 5}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAttributionScopes()).isNull();
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void
      fetchTrigger_aggregateDebugReportingDisabled_ignoreValidAggregateDebugReportingFields()
          throws Exception {
    when(mMockFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(false);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"aggregatable_debug_reporting\":{"
                        + "\"budget\":1024,"
                        + "\"key_piece\":\"0x1\","
                        + "\"debug_data\":["
                        + "{\"types\":["
                        + "\"trigger-event-deduplicated\","
                        + "\"trigger-event-low-priority\"],"
                        + "\"key_piece\":\"0x123\","
                        + "\"value\": 123},"
                        + "{\"types\":["
                        + "\"trigger-event-no-matching-configurations\"],"
                        + "\"key_piece\":\"0x789\","
                        + "\"value\":789}],"
                        + "\"aggregation_coordinator_origin\":"
                        + " \"https://cloud.coordination.test\"}}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAggregateDebugReportingString()).isNull();
    verify(mUrlConnection).setRequestMethod("POST");
  }

  @Test
  public void fetchTrigger_adrHasInvalidDebugData_acceptsTrigger() throws Exception {
    when(mMockFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"aggregatable_debug_reporting\":{"
                        + "\"budget\":1024,"
                        + "\"key_piece\":\"0x1\","
                        + "\"debug_data\":["
                        + "{\"types\":["
                        + "\"trigger-event-deduplicated\","
                        + "\"invalid-report-type\"],"
                        + "\"key_piece\":\"0x123\","
                        + "\"value\": 123},"
                        + "{\"types\":["
                        + "\"trigger-event-no-matching-configurations\"],"
                        + "\"key_piece\":\"0x789\","
                        + "\"value\":789}],"
                        + "\"aggregation_coordinator_origin\":"
                        + " \"https://cloud.coordination.test\"}}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    Set<String> dataTypes1 = new HashSet<>();
    dataTypes1.add(DebugReportApi.Type.TRIGGER_EVENT_DEDUPLICATED.getValue());
    AggregateDebugReportData debugData1 =
        new AggregateDebugReportData.Builder(dataTypes1, new BigInteger("123", 16), 123).build();

    Set<String> dataTypes2 = new HashSet<>();
    dataTypes2.add(DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS.getValue());
    AggregateDebugReportData debugData2 =
        new AggregateDebugReportData.Builder(dataTypes2, new BigInteger("789", 16), 789).build();

    AggregateDebugReporting adrObject =
        new AggregateDebugReporting.Builder(
                // Budget field is ignored for trigger
                0,
                new BigInteger("1", 16),
                new ArrayList<>(List.of(debugData1, debugData2)),
                Uri.parse("https://cloud.coordination.test"))
            .build();

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    verify(mUrlConnection).setRequestMethod("POST");
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    assertThat(fetch.get().getAggregateDebugReportingObject()).isEqualTo(adrObject);
  }

  @Test
  public void fetchTrigger_aggregateDebugReportingEnabled_parseValidAggregateDebugReportingFields()
      throws Exception {
    when(mMockFlags.getMeasurementEnableAggregateDebugReporting()).thenReturn(true);
    when(mMockFlags.getMeasurementAggregationCoordinatorOriginList())
        .thenReturn("https://cloud.coordination.test");
    RegistrationRequest request = buildRequest(TRIGGER_URI);
    doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mUrlConnection.getHeaderFields())
        .thenReturn(
            Map.of(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                    "{\"aggregatable_debug_reporting\":{"
                        + "\"budget\":1024,"
                        + "\"key_piece\":\"0x1\","
                        + "\"debug_data\":["
                        + "{\"types\":["
                        + "\"trigger-event-deduplicated\","
                        + "\"trigger-event-low-priority\"],"
                        + "\"key_piece\":\"0x123\","
                        + "\"value\": 123},"
                        + "{\"types\":["
                        + "\"trigger-event-no-matching-configurations\"],"
                        + "\"key_piece\":\"0x789\","
                        + "\"value\":789}],"
                        + "\"aggregation_coordinator_origin\":"
                        + " \"https://cloud.coordination.test\"}}")));
    AsyncRedirects asyncRedirects = new AsyncRedirects();
    AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
    AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);

    // Execution
    Optional<Trigger> fetch =
        mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, asyncRedirects);
    // Assertion
    assertThat(asyncFetchStatus.getResponseStatus())
        .isEqualTo(AsyncFetchStatus.ResponseStatus.SUCCESS);
    assertThat(fetch.isPresent()).isTrue();
    AggregateDebugReporting aggregateDebugReporting =
        new AggregateDebugReporting.Builder(
                new JSONObject(fetch.get().getAggregateDebugReportingString()))
            .build();
    assertThat(aggregateDebugReporting.getKeyPiece()).isEqualTo(new BigInteger("1", 16));
    // Budget field is ignored for trigger
    assertThat(aggregateDebugReporting.getBudget()).isEqualTo(0);
    assertThat(aggregateDebugReporting.getAggregationCoordinatorOrigin())
        .isEqualTo(Uri.parse("https://cloud.coordination.test"));
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(0).getKeyPiece())
        .isEqualTo(new BigInteger("123", 16));
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(0).getValue())
        .isEqualTo(123);
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(0).getReportType())
        .isEqualTo(
            new HashSet<>(
                Arrays.asList("trigger-event-deduplicated", "trigger-event-low-priority")));
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(1).getKeyPiece())
        .isEqualTo(new BigInteger("789", 16));
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(1).getValue())
        .isEqualTo(789);
    assertThat(aggregateDebugReporting.getAggregateDebugReportDataList().get(1).getReportType())
        .isEqualTo(new HashSet<>(Arrays.asList("trigger-event-no-matching-configurations")));
    verify(mUrlConnection).setRequestMethod("POST");
  }

  private RegistrationRequest buildRequest(String triggerUri) {
    return new RegistrationRequest.Builder(
            RegistrationRequest.REGISTER_TRIGGER,
            Uri.parse(triggerUri),
            sContext.getPackageName(),
            SDK_PACKAGE_NAME)
        .build();
  }

  private RegistrationRequest buildRequestWithAdId(String triggerUri) {
    return new RegistrationRequest.Builder(
            RegistrationRequest.REGISTER_TRIGGER,
            Uri.parse(triggerUri),
            sContext.getPackageName(),
            SDK_PACKAGE_NAME)
        .setAdIdPermissionGranted(true)
        .setAdIdValue(PLATFORM_AD_ID_VALUE)
        .build();
  }

  private WebTriggerRegistrationRequest buildWebTriggerRegistrationRequest(
      List<WebTriggerParams> triggerParams, String topOrigin) {
    return new WebTriggerRegistrationRequest.Builder(triggerParams, Uri.parse(topOrigin)).build();
  }

  private static AsyncRegistration appTriggerRegistrationRequest(
      RegistrationRequest registrationRequest) {
    return createAsyncRegistration(
        UUID.randomUUID().toString(),
        registrationRequest.getRegistrationUri(),
        null,
        null,
        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
        null,
        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
        registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
            ? AsyncRegistration.RegistrationType.APP_SOURCE
            : AsyncRegistration.RegistrationType.APP_TRIGGER,
        null,
        System.currentTimeMillis(),
        0,
        false,
        false,
        null);
  }

  private static AsyncRegistration appTriggerRegistrationRequestWithAdId(
      RegistrationRequest registrationRequest) {
    return createAsyncRegistration(
        UUID.randomUUID().toString(),
        registrationRequest.getRegistrationUri(),
        null,
        null,
        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
        null,
        Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
        registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
            ? AsyncRegistration.RegistrationType.APP_SOURCE
            : AsyncRegistration.RegistrationType.APP_TRIGGER,
        null,
        System.currentTimeMillis(),
        0,
        false,
        true,
        PLATFORM_AD_ID_VALUE);
  }

  private static AsyncRegistration webTriggerRegistrationRequest(
      WebTriggerRegistrationRequest webTriggerRegistrationRequest, boolean arDebugPermission) {
    if (webTriggerRegistrationRequest.getTriggerParams().size() > 0) {
      WebTriggerParams webTriggerParams = webTriggerRegistrationRequest.getTriggerParams().get(0);
      return createAsyncRegistration(
          UUID.randomUUID().toString(),
          webTriggerParams.getRegistrationUri(),
          null,
          null,
          Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
          null,
          webTriggerRegistrationRequest.getDestination(),
          AsyncRegistration.RegistrationType.WEB_TRIGGER,
          null,
          System.currentTimeMillis(),
          0,
          arDebugPermission,
          false,
          null);
    }
    return null;
  }

  private static AsyncRegistration createAsyncRegistration(
      String iD,
      Uri registrationUri,
      Uri webDestination,
      Uri osDestination,
      Uri registrant,
      Uri verifiedDestination,
      Uri topOrigin,
      AsyncRegistration.RegistrationType registrationType,
      SourceType sourceType,
      long mRequestTime,
      long mRetryCount,
      boolean debugKeyAllowed,
      boolean adIdPermission,
      String adIdValue) {
    return new AsyncRegistration.Builder()
        .setId(iD)
        .setRegistrationUri(registrationUri)
        .setWebDestination(webDestination)
        .setOsDestination(osDestination)
        .setRegistrant(registrant)
        .setVerifiedDestination(verifiedDestination)
        .setTopOrigin(topOrigin)
        .setType(registrationType)
        .setSourceType(sourceType)
        .setRequestTime(mRequestTime)
        .setRetryCount(mRetryCount)
        .setDebugKeyAllowed(debugKeyAllowed)
        .setRegistrationId(UUID.randomUUID().toString())
        .setAdIdPermission(adIdPermission)
        .setPlatformAdId(adIdValue)
        .build();
  }

  private static Map<String, List<String>> getDefaultHeaders() {
    Map<String, List<String>> headers = new HashMap<>();
    headers.put(
        "Attribution-Reporting-Register-Trigger",
        List.of("{\"event_trigger_data\":" + EVENT_TRIGGERS_1 + "}"));
    return headers;
  }

  private static void assertTriggerRegistration(AsyncRegistration asyncRegistration, Trigger result)
      throws JSONException {
    assertEquals(
        asyncRegistration.getRegistrant().toString(),
        result.getAttributionDestination().toString());
    assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
    assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
    assertEquals(asyncRegistration.getRegistrationUri(), result.getRegistrationOrigin());
  }
}
