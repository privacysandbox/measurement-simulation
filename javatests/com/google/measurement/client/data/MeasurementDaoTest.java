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

package com.google.measurement.client.data;

import static com.google.measurement.client.data.MeasurementTables.ALL_MSMT_TABLES;
import static com.google.measurement.client.data.MeasurementTables.AsyncRegistrationContract;
import static com.google.measurement.client.data.MeasurementTables.AttributionContract;
import static com.google.measurement.client.data.MeasurementTables.EventReportContract;
import static com.google.measurement.client.data.MeasurementTables.MSMT_TABLE_PREFIX;
import static com.google.measurement.client.data.MeasurementTables.SourceContract;
import static com.google.measurement.client.data.MeasurementTables.TriggerContract;
import static com.google.measurement.client.data.MeasurementTables.XnaIgnoredSourcesContract;
import static com.google.measurement.client.Flags.MEASUREMENT_DB_SIZE_LIMIT;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.client.Flags.MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW;
import static com.google.measurement.client.Flags.MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS;
import static com.google.measurement.client.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
import static com.google.measurement.client.SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS;
import static com.google.measurement.client.SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import static java.util.concurrent.TimeUnit.DAYS;

import com.google.common.truth.Truth;
import com.google.measurement.client.stats.AdServicesErrorLogger;
import com.google.measurement.client.ApplicationProvider;
import com.google.measurement.client.AttributedTrigger;
import com.google.measurement.client.Attribution;
import com.google.measurement.client.Context;
import com.google.measurement.client.DeletionRequest;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.EventTrigger;
import com.google.measurement.client.FakeFlagsFactory;
import com.google.measurement.client.Flags;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Pair;
import com.google.measurement.client.Source;
import com.google.measurement.client.SourceFixture;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.TriggerFixture;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebUtil;
import com.google.measurement.client.data.MeasurementTables.AggregatableDebugReportBudgetTrackerContract;
import com.google.measurement.client.data.MeasurementTables.AppReportHistoryContract;
import com.google.measurement.client.data.MeasurementTables.DebugReportContract;
import com.google.measurement.client.registration.AsyncRegistrationFixture;
import com.google.measurement.client.registration.AsyncRegistrationFixture.ValidAsyncRegistrationParams;
import com.google.measurement.client.KeyValueData.DataType;
import com.google.measurement.client.data.MeasurementTables.KeyValueDataContract;
import com.google.measurement.client.aggregation.AggregateDebugReportRecord;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.aggregation.AggregateReportFixture;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.registration.AsyncRegistration;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.DebugReport;
import com.google.measurement.client.reporting.EventReportFixture;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.util.UnsignedLong;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MeasurementDaoTest {
  protected static final Context sContext = ApplicationProvider.getApplicationContext();
  private static final Uri APP_TWO_SOURCES = Uri.parse("android-app://com.example1.two-sources");
  private static final Uri APP_ONE_SOURCE = Uri.parse("android-app://com.example2.one-source");
  private static final String DEFAULT_ENROLLMENT_ID = "enrollment-id";
  private static final String ENROLLMENT_ID1 = "enrollment-id1";
  private static final Uri APP_TWO_PUBLISHER =
      Uri.parse("android-app://com.publisher2.two-sources");
  private static final Uri APP_ONE_PUBLISHER = Uri.parse("android-app://com.publisher1.one-source");
  private static final Uri APP_NO_PUBLISHER = Uri.parse("android-app://com.publisher3.no-sources");
  private static final Uri APP_BROWSER = Uri.parse("android-app://com.example1.browser");
  private static final Uri WEB_ONE_DESTINATION = WebUtil.validUri("https://example1.test");
  private static final Uri WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN =
      WebUtil.validUri("https://store.example1.test");
  private static final Uri WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2 =
      WebUtil.validUri("https://foo.example1.test");
  private static final Uri WEB_TWO_DESTINATION = WebUtil.validUri("https://example2.test");
  private static final Uri WEB_THREE_DESTINATION = WebUtil.validUri("https://example3.test");
  private static final Uri WEB_TWO_DESTINATION_WITH_PATH =
      WebUtil.validUri("https://www.example2.test/ad/foo");
  private static final Uri APP_ONE_DESTINATION =
      Uri.parse("android-app://com.example1.one-trigger");
  private static final Uri APP_TWO_DESTINATION =
      Uri.parse("android-app://com.example1.two-triggers");
  private static final Uri APP_THREE_DESTINATION =
      Uri.parse("android-app://com.example1.three-triggers");
  private static final Uri APP_THREE_DESTINATION_PATH1 =
      Uri.parse("android-app://com.example1.three-triggers/path1");
  private static final Uri APP_THREE_DESTINATION_PATH2 =
      Uri.parse("android-app://com.example1.three-triggers/path2");
  private static final Uri APP_NO_TRIGGERS = Uri.parse("android-app://com.example1.no-triggers");
  private static final Uri INSTALLED_PACKAGE = Uri.parse("android-app://com.example.installed");
  private static final Uri WEB_PUBLISHER_ONE = WebUtil.validUri("https://not.example.test");
  private static final Uri WEB_PUBLISHER_TWO = WebUtil.validUri("https://notexample.test");
  // Differs from WEB_PUBLISHER_ONE by scheme.
  private static final Uri WEB_PUBLISHER_THREE = WebUtil.validUri("http://not.example.test");
  private static final Uri APP_DESTINATION = Uri.parse("android-app://com.destination.example");
  private static final Uri REGISTRATION_ORIGIN = WebUtil.validUri("https://subdomain.example.test");

  private static final Uri REGISTRANT = Uri.parse("android-app://com.example.abc");
  private static final Uri INSTALLED_REGISTRANT = Uri.parse("android-app://installed-registrant");
  private static final Uri NOT_INSTALLED_REGISTRANT =
      Uri.parse("android-app://not-installed-registrant");

  private static final long INSERTION_TIME = 1617297798;
  private static final long COOLDOWN_WINDOW = TimeUnit.HOURS.toMillis(2);
  private static final long ATTRIBUTION_SCOPE_LIMIT = 3L;
  private static final long MAX_EVENT_STATES = 1000L;
  private static final String REGISTRATION_ID2 = "R2";

  // Fake ID count for initializing triggers.
  private int mValueId = 1;
  private Flags mFlags;
  private DatastoreManager mDatastoreManager;
  public static final Uri REGISTRATION_ORIGIN_2 =
      WebUtil.validUri("https://subdomain_2.example.test");
  public static final Uri REGISTRATION_ORIGIN_3 =
      WebUtil.validUri("https://subdomain_3.example.test");
  public static final Uri REGISTRATION_ORIGIN_4 =
      WebUtil.validUri("https://subdomain_4.example.test");
  public static final Uri REGISTRATION_ORIGIN_5 =
      WebUtil.validUri("https://subdomain_5.example.test");

  public static final Uri TOP_LEVEL_REGISTRANT_1 = Uri.parse("android-app://com.example1.sample");
  public static final Uri TOP_LEVEL_REGISTRANT_2 = Uri.parse("android-app://com.example2.sample");

  @Before
  public void before() {
    mFlags = FakeFlagsFactory.getFlagsForTest();
    mDatastoreManager =
        new SQLDatastoreManager(
            MeasurementDbHelper.getInstance(), Mockito.mock(AdServicesErrorLogger.class));
  }

  @After
  public void cleanup() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    for (String table : ALL_MSMT_TABLES) {
      db.delete(table, null, null);
    }
  }

  @Test
  public void testInsertSource() {
    Source validSource =
        SourceFixture.getValidSourceBuilder()
            .setEventReportWindows("{'start_time': 1, 'end_times': ['3600', '7200']}")
            .setStatus(Source.Status.MARKED_TO_DELETE)
            .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
            .setTriggerData(Set.of(new UnsignedLong(23L), new UnsignedLong(1L)))
            .build();
    mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

    String sourceId = getFirstSourceIdFromDatastore();
    Source source =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();

    assertNotNull(source);
    assertNotNull(source.getId());
    assertNull(source.getAppDestinations());
    assertNull(source.getWebDestinations());
    assertEquals(validSource.getEnrollmentId(), source.getEnrollmentId());
    assertEquals(validSource.getRegistrant(), source.getRegistrant());
    assertEquals(validSource.getEventTime(), source.getEventTime());
    assertEquals(validSource.getExpiryTime(), source.getExpiryTime());
    assertEquals(validSource.getStatus(), source.getStatus());
    assertEquals(validSource.getEventReportWindow(), source.getEventReportWindow());
    assertEquals(validSource.getAggregatableReportWindow(), source.getAggregatableReportWindow());
    assertEquals(validSource.getPriority(), source.getPriority());
    assertEquals(validSource.getSourceType(), source.getSourceType());
    assertEquals(validSource.getInstallAttributionWindow(), source.getInstallAttributionWindow());
    assertEquals(validSource.getInstallCooldownWindow(), source.getInstallCooldownWindow());
    assertEquals(validSource.getAttributionMode(), source.getAttributionMode());
    assertEquals(
        validSource.getReinstallReattributionWindow(), source.getReinstallReattributionWindow());
    assertEquals(validSource.getAggregateSource(), source.getAggregateSource());
    assertEquals(validSource.getFilterDataString(), source.getFilterDataString());
    assertEquals(validSource.getSharedFilterDataKeys(), source.getSharedFilterDataKeys());
    assertEquals(validSource.getAggregateContributions(), source.getAggregateContributions());
    assertEquals(validSource.isDebugReporting(), source.isDebugReporting());
    assertEquals(validSource.getSharedAggregationKeys(), source.getSharedAggregationKeys());
    assertEquals(validSource.getRegistrationId(), source.getRegistrationId());
    assertEquals(validSource.getInstallTime(), source.getInstallTime());
    assertEquals(validSource.getPlatformAdId(), source.getPlatformAdId());
    assertEquals(validSource.getDebugAdId(), source.getDebugAdId());
    assertEquals(validSource.getRegistrationOrigin(), source.getRegistrationOrigin());
    assertEquals(
        validSource.hasCoarseEventReportDestinations(), source.hasCoarseEventReportDestinations());
    assertEquals(validSource.getTriggerDataMatching(), source.getTriggerDataMatching());
    assertEquals(validSource.getTriggerData(), source.getTriggerData());
    assertEquals(validSource.getEventReportWindows(), source.getEventReportWindows());
    assertEquals(SourceFixture.ValidSourceParams.SHARED_DEBUG_KEY, source.getSharedDebugKey());
    assertEquals(0L, source.getDestinationLimitPriority());

    // Assert destinations were inserted into the source destination table.

    Pair<List<Uri>, List<Uri>> destinations =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceDestinations(source.getId()))
            .get();
    assertTrue(
        ImmutableMultiset.copyOf(validSource.getAppDestinations())
            .equals(ImmutableMultiset.copyOf(destinations.first)));
    assertTrue(
        ImmutableMultiset.copyOf(validSource.getWebDestinations())
            .equals(ImmutableMultiset.copyOf(destinations.second)));
  }

  @Test
  public void testInsertSource_flexibleEventReport_equal() throws JSONException {
    Source validSource = SourceFixture.getValidSourceWithFlexEventReport();
    mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

    String sourceId = getFirstSourceIdFromDatastore();
    Source source =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();
    source.buildTriggerSpecs();

    assertNotNull(source);
    assertNotNull(source.getId());
    assertNull(source.getAppDestinations());
    assertNull(source.getWebDestinations());
    assertEquals(validSource.getEnrollmentId(), source.getEnrollmentId());
    assertEquals(validSource.getRegistrant(), source.getRegistrant());
    assertEquals(validSource.getEventTime(), source.getEventTime());
    assertEquals(validSource.getExpiryTime(), source.getExpiryTime());
    assertEquals(validSource.getEventReportWindow(), source.getEventReportWindow());
    assertEquals(validSource.getAggregatableReportWindow(), source.getAggregatableReportWindow());
    assertEquals(validSource.getPriority(), source.getPriority());
    assertEquals(validSource.getSourceType(), source.getSourceType());
    assertEquals(validSource.getInstallAttributionWindow(), source.getInstallAttributionWindow());
    assertEquals(validSource.getInstallCooldownWindow(), source.getInstallCooldownWindow());
    assertEquals(validSource.getAttributionMode(), source.getAttributionMode());
    assertEquals(validSource.getAggregateSource(), source.getAggregateSource());
    assertEquals(validSource.getFilterDataString(), source.getFilterDataString());
    assertEquals(validSource.getAggregateContributions(), source.getAggregateContributions());
    assertEquals(validSource.isDebugReporting(), source.isDebugReporting());
    assertEquals(validSource.getSharedAggregationKeys(), source.getSharedAggregationKeys());
    assertEquals(validSource.getRegistrationId(), source.getRegistrationId());
    assertEquals(validSource.getInstallTime(), source.getInstallTime());
    assertEquals(validSource.getPlatformAdId(), source.getPlatformAdId());
    assertEquals(validSource.getDebugAdId(), source.getDebugAdId());
    assertEquals(validSource.getRegistrationOrigin(), source.getRegistrationOrigin());
    assertEquals(
        validSource.getTriggerSpecs().getMaxReports(), source.getMaxEventLevelReports().intValue());
    assertEquals(validSource.getTriggerSpecs().encodeToJson(), source.getTriggerSpecsString());
    assertNull(source.getEventAttributionStatus());
    assertEquals(
        validSource.getTriggerSpecs().encodePrivacyParametersToJsonString(),
        source.getPrivacyParameters());
    assertEquals(validSource.getTriggerSpecs(), source.getTriggerSpecs());

    // Assert destinations were inserted into the source destination table.
    Pair<List<Uri>, List<Uri>> destinations =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceDestinations(source.getId()))
            .get();
    assertTrue(
        ImmutableMultiset.copyOf(validSource.getAppDestinations())
            .equals(ImmutableMultiset.copyOf(destinations.first)));
    assertTrue(
        ImmutableMultiset.copyOf(validSource.getWebDestinations())
            .equals(ImmutableMultiset.copyOf(destinations.second)));
  }

  @Test
  public void testInsertSource_reachedDbSizeLimitOnEdgeCase_doNotInsert() {
    insertSourceReachingDbSizeLimit(/* dbSize= */ 100L, /* dbSizeMaxLimit= */ 100L);
  }

  @Test
  public void testInsertSource_reachedDbSizeLimitUpperEdgeCase_doNotInsert() {
    insertSourceReachingDbSizeLimit(/* dbSize= */ 101L, /* dbSizeMaxLimit= */ 100L);
  }

  @Test
  public void testInsertSource_attributionScopeEnabled_success() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source validSource =
          insertSourceForAttributionScope(
              List.of("1", "2", "3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              List.of(APP_ONE_DESTINATION));
      Source source =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSource(validSource.getId()))
              .get();
      assertThat(source.getAttributionScopeLimit())
          .isEqualTo(validSource.getAttributionScopeLimit());
      assertThat(source.getMaxEventStates()).isEqualTo(validSource.getMaxEventStates());
      List<String> attributionScopes =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSourceAttributionScopes(source.getId()))
              .get();
      assertThat(attributionScopes).containsExactlyElementsIn(validSource.getAttributionScopes());
    }
  }

  @Test
  public void testInsertSource_attributionScopeDisabled_doesNotInsertAttributionScopeRelatedData() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(false).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source validSource =
          insertSourceForAttributionScope(
              List.of("1", "2", "3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              List.of(APP_ONE_DESTINATION));
      Source source =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSource(validSource.getId()))
              .get();

      assertThat(source.getAttributionScopeLimit()).isEqualTo(null);
      assertThat(source.getMaxEventStates()).isEqualTo(null);
      List<String> attributionScopes =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSourceAttributionScopes(source.getId()))
              .get();
      assertThat(attributionScopes).isEmpty();
    }
  }

  @Test
  public void testInsertSource_aggregateDebugReportingEnabled_success() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAggregateDebugReporting();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source validSource =
          SourceFixture.getValidSourceBuilder()
              .setEventReportWindows("{'start_time': 1, 'end_times': ['3600', '7200']}")
              .setStatus(Source.Status.MARKED_TO_DELETE)
              .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
              .setTriggerData(Set.of(new UnsignedLong(23L), new UnsignedLong(1L)))
              .build();
      mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

      String sourceId = getFirstSourceIdFromDatastore();
      Source source =
          mDatastoreManager
              .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
              .get();

      assertThat(source).isNotNull();
      assertThat(source.getId()).isNotNull();
      assertThat(source.getAppDestinations()).isNull();
      assertThat(source.getWebDestinations()).isNull();
      assertThat(validSource.getEnrollmentId()).isEqualTo(source.getEnrollmentId());
      assertThat(validSource.getRegistrant()).isEqualTo(source.getRegistrant());
      assertThat(validSource.getEventTime()).isEqualTo(source.getEventTime());
      assertThat(validSource.getExpiryTime()).isEqualTo(source.getExpiryTime());
      assertThat(validSource.getStatus()).isEqualTo(source.getStatus());
      assertThat(validSource.getEventReportWindow()).isEqualTo(source.getEventReportWindow());
      assertThat(validSource.getAggregatableReportWindow())
          .isEqualTo(source.getAggregatableReportWindow());
      assertThat(validSource.getPriority()).isEqualTo(source.getPriority());
      assertThat(validSource.getSourceType()).isEqualTo(source.getSourceType());
      assertThat(validSource.getInstallAttributionWindow())
          .isEqualTo(source.getInstallAttributionWindow());
      assertThat(validSource.getInstallCooldownWindow())
          .isEqualTo(source.getInstallCooldownWindow());
      assertThat(validSource.getAttributionMode()).isEqualTo(source.getAttributionMode());
      assertThat(validSource.getReinstallReattributionWindow())
          .isEqualTo(source.getReinstallReattributionWindow());
      assertThat(validSource.getAggregateSource()).isEqualTo(source.getAggregateSource());
      assertThat(validSource.getFilterDataString()).isEqualTo(source.getFilterDataString());
      assertThat(validSource.getSharedFilterDataKeys()).isEqualTo(source.getSharedFilterDataKeys());
      assertThat(validSource.getAggregateContributions())
          .isEqualTo(source.getAggregateContributions());
      assertThat(validSource.isDebugReporting()).isEqualTo(source.isDebugReporting());
      assertThat(validSource.getSharedAggregationKeys())
          .isEqualTo(source.getSharedAggregationKeys());
      assertThat(validSource.getRegistrationId()).isEqualTo(source.getRegistrationId());
      assertThat(validSource.getInstallTime()).isEqualTo(source.getInstallTime());
      assertThat(validSource.getPlatformAdId()).isEqualTo(source.getPlatformAdId());
      assertThat(validSource.getDebugAdId()).isEqualTo(source.getDebugAdId());
      assertThat(validSource.getRegistrationOrigin()).isEqualTo(source.getRegistrationOrigin());
      assertThat(validSource.hasCoarseEventReportDestinations())
          .isEqualTo(source.hasCoarseEventReportDestinations());
      assertThat(validSource.getTriggerDataMatching()).isEqualTo(source.getTriggerDataMatching());
      assertThat(validSource.getTriggerData()).isEqualTo(source.getTriggerData());
      assertThat(validSource.getEventReportWindows()).isEqualTo(source.getEventReportWindows());
      assertThat(SourceFixture.ValidSourceParams.SHARED_DEBUG_KEY)
          .isEqualTo(source.getSharedDebugKey());
      assertThat(source.getDestinationLimitPriority()).isEqualTo(0L);
      assertThat(validSource.getAggregateDebugReportingString())
          .isEqualTo(source.getAggregateDebugReportingString());
      assertThat(validSource.getAggregateDebugReportContributions())
          .isEqualTo(source.getAggregateDebugReportContributions());

      // Assert destinations were inserted into the source destination table.

      Pair<List<Uri>, List<Uri>> destinations =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSourceDestinations(source.getId()))
              .get();
      assertThat(
              ImmutableMultiset.copyOf(validSource.getAppDestinations())
                  .equals(ImmutableMultiset.copyOf(destinations.first)))
          .isTrue();
      assertThat(
              ImmutableMultiset.copyOf(validSource.getWebDestinations())
                  .equals(ImmutableMultiset.copyOf(destinations.second)))
          .isTrue();
    }
  }

  @Test
  public void testInsertSource_aggregateDebugReportingDisabled_relatedDataNotInserted() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(false).when(mFlags).getMeasurementEnableAggregateDebugReporting();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source validSource =
          SourceFixture.getValidSourceBuilder()
              .setEventReportWindows("{'start_time': 1, 'end_times': ['3600', '7200']}")
              .setStatus(Source.Status.MARKED_TO_DELETE)
              .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
              .setTriggerData(Set.of(new UnsignedLong(23L), new UnsignedLong(1L)))
              .build();
      mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

      String sourceId = getFirstSourceIdFromDatastore();
      Source source =
          mDatastoreManager
              .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
              .get();

      assertThat(source).isNotNull();
      assertThat(source.getId()).isNotNull();
      assertThat(source.getAppDestinations()).isNull();
      assertThat(source.getWebDestinations()).isNull();
      assertThat(validSource.getEnrollmentId()).isEqualTo(source.getEnrollmentId());
      assertThat(validSource.getRegistrant()).isEqualTo(source.getRegistrant());
      assertThat(validSource.getEventTime()).isEqualTo(source.getEventTime());
      assertThat(validSource.getExpiryTime()).isEqualTo(source.getExpiryTime());
      assertThat(validSource.getStatus()).isEqualTo(source.getStatus());
      assertThat(validSource.getEventReportWindow()).isEqualTo(source.getEventReportWindow());
      assertThat(validSource.getAggregatableReportWindow())
          .isEqualTo(source.getAggregatableReportWindow());
      assertThat(validSource.getPriority()).isEqualTo(source.getPriority());
      assertThat(validSource.getSourceType()).isEqualTo(source.getSourceType());
      assertThat(validSource.getInstallAttributionWindow())
          .isEqualTo(source.getInstallAttributionWindow());
      assertThat(validSource.getInstallCooldownWindow())
          .isEqualTo(source.getInstallCooldownWindow());
      assertThat(validSource.getAttributionMode()).isEqualTo(source.getAttributionMode());
      assertThat(validSource.getReinstallReattributionWindow())
          .isEqualTo(source.getReinstallReattributionWindow());
      assertThat(validSource.getAggregateSource()).isEqualTo(source.getAggregateSource());
      assertThat(validSource.getFilterDataString()).isEqualTo(source.getFilterDataString());
      assertThat(validSource.getSharedFilterDataKeys()).isEqualTo(source.getSharedFilterDataKeys());
      assertThat(validSource.getAggregateContributions())
          .isEqualTo(source.getAggregateContributions());
      assertThat(validSource.isDebugReporting()).isEqualTo(source.isDebugReporting());
      assertThat(validSource.getSharedAggregationKeys())
          .isEqualTo(source.getSharedAggregationKeys());
      assertThat(validSource.getRegistrationId()).isEqualTo(source.getRegistrationId());
      assertThat(validSource.getInstallTime()).isEqualTo(source.getInstallTime());
      assertThat(validSource.getPlatformAdId()).isEqualTo(source.getPlatformAdId());
      assertThat(validSource.getDebugAdId()).isEqualTo(source.getDebugAdId());
      assertThat(validSource.getRegistrationOrigin()).isEqualTo(source.getRegistrationOrigin());
      assertThat(validSource.hasCoarseEventReportDestinations())
          .isEqualTo(source.hasCoarseEventReportDestinations());
      assertThat(validSource.getTriggerDataMatching()).isEqualTo(source.getTriggerDataMatching());
      assertThat(validSource.getTriggerData()).isEqualTo(source.getTriggerData());
      assertThat(validSource.getEventReportWindows()).isEqualTo(source.getEventReportWindows());
      assertThat(SourceFixture.ValidSourceParams.SHARED_DEBUG_KEY)
          .isEqualTo(source.getSharedDebugKey());
      assertThat(source.getDestinationLimitPriority()).isEqualTo(0L);
      assertThat(source.getAggregateDebugReportingString()).isEqualTo(null);
      assertThat(source.getAggregateDebugReportContributions()).isEqualTo(0L);

      // Assert destinations were inserted into the source destination table.

      Pair<List<Uri>, List<Uri>> destinations =
          mDatastoreManager
              .runInTransactionWithResult(
                  measurementDao -> measurementDao.getSourceDestinations(source.getId()))
              .get();
      assertThat(
              ImmutableMultiset.copyOf(validSource.getAppDestinations())
                  .equals(ImmutableMultiset.copyOf(destinations.first)))
          .isTrue();
      assertThat(
              ImmutableMultiset.copyOf(validSource.getWebDestinations())
                  .equals(ImmutableMultiset.copyOf(destinations.second)))
          .isTrue();
    }
  }

  private void insertSourceReachingDbSizeLimit(long dbSize, long dbSizeMaxLimit) {
    final Source validSource = SourceFixture.getValidSource();

    // Mocking that the DB file has a size of 100 bytes
    final MeasurementDbHelper spyMeasurementDbHelper = spy(MeasurementDbHelper.getInstance());
    try (MockedStatic mockedStaticDbHelper = mockStatic(MeasurementDbHelper.class);
        MockedStatic mockedStaticFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedStaticDbHelper
          .when(() -> MeasurementDbHelper.getInstance())
          .thenReturn(spyMeasurementDbHelper);
      mockedStaticDbHelper.when(spyMeasurementDbHelper::getDbFileSize).thenReturn(dbSize);
      mDatastoreManager =
          new SQLDatastoreManager(
              spyMeasurementDbHelper, Mockito.mock(AdServicesErrorLogger.class));
      // Mocking that the flags return a max limit size of 100 bytes
      Flags mockFlags = Mockito.mock(Flags.class);
      mockedStaticFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);
      doReturn(dbSizeMaxLimit).when(mockFlags).getMeasurementDbSizeLimit();

      mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

      try (Cursor sourceCursor =
          MeasurementDbHelper.getInstance()
              .getReadableDatabase()
              .query(SourceContract.TABLE, null, null, null, null, null, null)) {
        assertFalse(sourceCursor.moveToNext());
      }
    }
  }

  @Test
  public void testInsertTrigger() {
    Trigger validTrigger = TriggerFixture.getValidTrigger();
    mDatastoreManager.runInTransaction((dao) -> dao.insertTrigger(validTrigger));

    try (Cursor triggerCursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(TriggerContract.TABLE, null, null, null, null, null, null)) {
      assertTrue(triggerCursor.moveToNext());
      Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(triggerCursor);
      assertNotNull(trigger);
      assertNotNull(trigger.getId());
      assertEquals(validTrigger.getAttributionDestination(), trigger.getAttributionDestination());
      assertEquals(validTrigger.getDestinationType(), trigger.getDestinationType());
      assertEquals(validTrigger.getEnrollmentId(), trigger.getEnrollmentId());
      assertEquals(validTrigger.getRegistrant(), trigger.getRegistrant());
      assertEquals(validTrigger.getTriggerTime(), trigger.getTriggerTime());
      assertEquals(validTrigger.getEventTriggers(), trigger.getEventTriggers());
      assertEquals(validTrigger.getAttributionConfig(), trigger.getAttributionConfig());
      assertEquals(validTrigger.getAdtechKeyMapping(), trigger.getAdtechKeyMapping());
      assertEquals(validTrigger.getPlatformAdId(), trigger.getPlatformAdId());
      assertEquals(validTrigger.getDebugAdId(), trigger.getDebugAdId());
      assertEquals(validTrigger.getRegistrationOrigin(), trigger.getRegistrationOrigin());
      assertEquals(
          validTrigger.getAggregationCoordinatorOrigin(),
          trigger.getAggregationCoordinatorOrigin());
      assertEquals(
          validTrigger.getAggregatableSourceRegistrationTimeConfig(),
          trigger.getAggregatableSourceRegistrationTimeConfig());
      assertEquals(validTrigger.getTriggerContextId(), trigger.getTriggerContextId());
      assertEquals(validTrigger.getAttributionScopesString(), trigger.getAttributionScopesString());
      assertEquals(
          validTrigger.getAggregatableFilteringIdMaxBytes(),
          trigger.getAggregatableFilteringIdMaxBytes());
      assertThat(validTrigger.getAggregateDebugReportingString())
          .isEqualTo(trigger.getAggregateDebugReportingString());
    }
  }

  @Test
  public void testInsertTrigger_reachedDbSizeLimitOnEdgeCase_doNotInsert() {
    insertTriggerReachingDbSizeLimit(/* dbSize= */ 100L, /* dbSizeMaxLimit= */ 100L);
  }

  @Test
  public void testInsertTrigger_reachedDbSizeLimitUpperEdgeCase_doNotInsert() {
    insertTriggerReachingDbSizeLimit(/* dbSize= */ 101L, /* dbSizeMaxLimit= */ 100L);
  }

  private void insertTriggerReachingDbSizeLimit(long dbSize, long dbSizeMaxLimit) {
    final Trigger validTrigger = TriggerFixture.getValidTrigger();

    // Mocking that the DB file has a size of 100 bytes
    final MeasurementDbHelper spyMeasurementDbHelper = spy(MeasurementDbHelper.getInstance());
    try (MockedStatic mockedStaticDbHelper = mockStatic(MeasurementDbHelper.class);
        MockedStatic mockedStaticFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedStaticDbHelper
          .when(() -> MeasurementDbHelper.getInstance())
          .thenReturn(spyMeasurementDbHelper);
      mockedStaticDbHelper.when(spyMeasurementDbHelper::getDbFileSize).thenReturn(dbSize);
      mDatastoreManager =
          new SQLDatastoreManager(
              spyMeasurementDbHelper, Mockito.mock(AdServicesErrorLogger.class));
      // Mocking that the flags return a max limit size of 100 bytes
      Flags mockFlags = Mockito.mock(Flags.class);
      mockedStaticFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);
      doReturn(dbSizeMaxLimit).when(mockFlags).getMeasurementDbSizeLimit();

      mDatastoreManager.runInTransaction((dao) -> dao.insertTrigger(validTrigger));

      try (Cursor sourceCursor =
          MeasurementDbHelper.getInstance()
              .getReadableDatabase()
              .query(TriggerContract.TABLE, null, null, null, null, null, null)) {
        assertFalse(sourceCursor.moveToNext());
      }
    }
  }

  @Test
  public void testGetNumSourcesPerPublisher_publisherTypeApp() {
    setupSourceAndTriggerData();
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              2, measurementDao.getNumSourcesPerPublisher(APP_TWO_PUBLISHER, EventSurfaceType.APP));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              1, measurementDao.getNumSourcesPerPublisher(APP_ONE_PUBLISHER, EventSurfaceType.APP));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              0, measurementDao.getNumSourcesPerPublisher(APP_NO_PUBLISHER, EventSurfaceType.APP));
        });
  }

  @Test
  public void testGetNumSourcesPerPublisher_publisherTypeWeb() {
    setupSourceDataForPublisherTypeWeb();
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              1, measurementDao.getNumSourcesPerPublisher(WEB_PUBLISHER_ONE, EventSurfaceType.WEB));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              2, measurementDao.getNumSourcesPerPublisher(WEB_PUBLISHER_TWO, EventSurfaceType.WEB));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              1,
              measurementDao.getNumSourcesPerPublisher(WEB_PUBLISHER_THREE, EventSurfaceType.WEB));
        });
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withDataInSource() {
    // Setup records in source storage
    final List<Uri> appsInstalled =
        List.of(buildRegistrant("foo"), buildRegistrant("bar"), buildRegistrant("baz"));
    final List<Uri> appsNotInstalled = List.of(buildRegistrant("qux"), buildRegistrant("quux"));
    insertSourceForPackageName(
        Stream.concat(appsInstalled.stream(), appsNotInstalled.stream()).toArray(Uri[]::new));

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertEquals(2, uninstalledAppNames.get().size());
    Set<Uri> result = new HashSet<>(uninstalledAppNames.get());
    assertTrue(result.contains(buildRegistrant("qux")));
    assertTrue(result.contains(buildRegistrant("quux")));
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withDataInTrigger() {
    // Setup records in trigger storage
    final List<Uri> appsInstalled =
        List.of(buildRegistrant("foo"), buildRegistrant("bar"), buildRegistrant("baz"));
    final List<Uri> appsNotInstalled = List.of(buildRegistrant("qux"), buildRegistrant("quux"));
    insertTriggerForPackageName(
        Stream.concat(appsInstalled.stream(), appsNotInstalled.stream()).toArray(Uri[]::new));

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertEquals(2, uninstalledAppNames.get().size());
    Set<Uri> result = new HashSet<>(uninstalledAppNames.get());
    assertTrue(result.contains(buildRegistrant("qux")));
    assertTrue(result.contains(buildRegistrant("quux")));
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withDataInTriggerAndSource() {
    // Setup records in source and trigger storage
    final List<Uri> appsInstalled =
        List.of(buildRegistrant("foo"), buildRegistrant("bar"), buildRegistrant("baz"));
    final List<Uri> appsNotInstalled = List.of(buildRegistrant("qux"), buildRegistrant("quux"));
    final Uri[] appNames =
        Stream.concat(appsInstalled.stream(), appsNotInstalled.stream()).toArray(Uri[]::new);
    insertSourceForPackageName(appNames);
    insertTriggerForPackageName(appNames);

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertEquals(2, uninstalledAppNames.get().size());
    Set<Uri> result = new HashSet<>(uninstalledAppNames.get());
    assertTrue(result.contains(buildRegistrant("qux")));
    assertTrue(result.contains(buildRegistrant("quux")));
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withDataInAsync() {
    // Setup records in source storage
    final List<Uri> appsInstalled =
        List.of(buildRegistrant("foo"), buildRegistrant("bar"), buildRegistrant("baz"));
    final List<Uri> appsNotInstalled = List.of(buildRegistrant("qux"), buildRegistrant("quux"));
    insertAsyncRecordForPackageName(
        Stream.concat(appsInstalled.stream(), appsNotInstalled.stream()).toArray(Uri[]::new));

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertEquals(2, uninstalledAppNames.get().size());
    Set<Uri> result = new HashSet<>(uninstalledAppNames.get());
    assertTrue(result.contains(buildRegistrant("qux")));
    assertTrue(result.contains(buildRegistrant("quux")));
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withAnyDataAndNoAppsUninstalled() {
    // Setup records in source storage (any storage), all these apps are still installed
    final List<Uri> appsInstalled =
        List.of(
            buildRegistrant("foo"),
            buildRegistrant("bar"),
            buildRegistrant("baz"),
            buildRegistrant("qux"),
            buildRegistrant("quux"));
    insertSourceForPackageName(appsInstalled.stream().toArray(Uri[]::new));

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertTrue(uninstalledAppNames.get().isEmpty());
    assertEquals(0, uninstalledAppNames.get().size());
  }

  @Test
  public void testGetUninstalledAppNamesContainingData_withNoData() {
    // Setup records, these apps don't have source nor trigger data
    final List<Uri> appsInstalled =
        List.of(
            buildRegistrant("foo"),
            buildRegistrant("bar"),
            buildRegistrant("baz"),
            buildRegistrant("qux"),
            buildRegistrant("quux"));

    // Execution
    final Optional<List<Uri>> uninstalledAppNames =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getUninstalledAppNamesHavingMeasurementData(appsInstalled));

    // Validation, apps not installed should be returned
    assertNotNull(uninstalledAppNames);
    assertTrue(uninstalledAppNames.isPresent());
    assertTrue(uninstalledAppNames.get().isEmpty());
    assertEquals(0, uninstalledAppNames.get().size());
  }

  @Test
  public void testCountDistinctReportingOriginPerPublisherXDestinationInAttribution_atWindow() {
    Uri sourceSite = Uri.parse("android-app://publisher.app");
    Uri appDestination = Uri.parse("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentReportingOrigins(
            4, appDestination, 5000000001L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution);
    }
    Uri excludedRegistrationOrigin = WebUtil.validUri("https://subdomain0.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                  sourceSite,
                  appDestination,
                  excludedRegistrationOrigin,
                  5000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestInAttribution_beyondWindow() {
    Uri sourceSite = Uri.parse("android-app://publisher.app");
    Uri appDestination = Uri.parse("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentReportingOrigins(
            4, appDestination, 5000000000L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution);
    }
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain.example0.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                  sourceSite, appDestination, excludedReportingOrigin, 5000000000L, 6000000000L));
        });
  }

  @Test
  public void testInsertDebugReport() {
    DebugReport debugReport = createDebugReport();
    mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(DebugReportContract.TABLE, null, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      DebugReport report = SqliteObjectMapper.constructDebugReportFromCursor(cursor);
      assertNotNull(report);
      assertNotNull(report.getId());
      assertEquals(debugReport.getType(), report.getType());
      assertEquals(debugReport.getBody().toString(), report.getBody().toString());
      assertEquals(debugReport.getEnrollmentId(), report.getEnrollmentId());
      assertEquals(debugReport.getRegistrationOrigin(), report.getRegistrationOrigin());
      assertEquals(debugReport.getReferenceId(), report.getReferenceId());
      assertEquals(debugReport.getInsertionTime(), report.getInsertionTime());
      assertEquals(debugReport.getRegistrant(), report.getRegistrant());
    }
  }

  @Test
  public void singleAppTrigger_triggersPerDestination_returnsOne() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_ONE_DESTINATION, APP_ONE_DESTINATION));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao ->
            assertThat(
                    measurementDao.getNumTriggersPerDestination(
                        APP_ONE_DESTINATION, EventSurfaceType.APP))
                .isEqualTo(1));
  }

  @Test
  public void multipleAppTriggers_similarUris_triggersPerDestination() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
    triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao ->
            assertThat(
                    measurementDao.getNumTriggersPerDestination(
                        APP_TWO_DESTINATION, EventSurfaceType.APP))
                .isEqualTo(2));
  }

  @Test
  public void noAppTriggers_triggersPerDestination_returnsNone() {
    mDatastoreManager.runInTransaction(
        measurementDao ->
            assertThat(
                    measurementDao.getNumTriggersPerDestination(
                        APP_NO_TRIGGERS, EventSurfaceType.APP))
                .isEqualTo(0));
  }

  @Test
  public void multipleAppTriggers_differentPaths_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION));
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH1));
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH2));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      APP_THREE_DESTINATION, EventSurfaceType.APP))
              .isEqualTo(3);
          // Try the same thing, but use the app uri with path to find number of triggers.
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      APP_THREE_DESTINATION_PATH1, EventSurfaceType.APP))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      APP_THREE_DESTINATION_PATH2, EventSurfaceType.APP))
              .isEqualTo(3);
          Uri unseenAppThreePath = Uri.parse("android-app://com.example1.three-triggers/path3");
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      unseenAppThreePath, EventSurfaceType.APP))
              .isEqualTo(3);
        });
  }

  @Test
  public void singleWebTrigger_triggersPerDestination_returnsOne() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_ONE_DESTINATION, EventSurfaceType.WEB))
              .isEqualTo(1);
        });
  }

  @Test
  public void webTriggerMultipleSubDomains_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN));
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_ONE_DESTINATION, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WebUtil.validUri("https://new-subdomain.example1.test"),
                      EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WebUtil.validUri("https://example1.test"), EventSurfaceType.WEB))
              .isEqualTo(3);
        });
  }

  @Test
  public void webTriggerWithoutSubdomains_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    Uri webDestinationWithoutSubdomain = WebUtil.validUri("https://example1.test");
    Uri webDestinationWithoutSubdomainPath1 = WebUtil.validUri("https://example1.test/path1");
    Uri webDestinationWithoutSubdomainPath2 = WebUtil.validUri("https://example1.test/path2");
    Uri webDestinationWithoutSubdomainPath3 = WebUtil.validUri("https://example1.test/path3");
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomain));
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath1));
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath2));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      webDestinationWithoutSubdomain, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      webDestinationWithoutSubdomainPath1, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      webDestinationWithoutSubdomainPath2, EventSurfaceType.WEB))
              .isEqualTo(3);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      webDestinationWithoutSubdomainPath3, EventSurfaceType.WEB))
              .isEqualTo(3);
        });
  }

  @Test
  public void webTriggerDifferentPaths_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_TWO_DESTINATION));
    triggerList.add(createWebTrigger(WEB_TWO_DESTINATION_WITH_PATH));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_TWO_DESTINATION, EventSurfaceType.WEB))
              .isEqualTo(2);
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      WEB_TWO_DESTINATION_WITH_PATH, EventSurfaceType.WEB))
              .isEqualTo(2);
        });
  }

  @Test
  public void noMathingWebTriggers_triggersPerDestination_returnsZero() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    addTriggersToDatabase(triggerList);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          Uri differentScheme = WebUtil.validUri("http://www.example1.test");
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      differentScheme, EventSurfaceType.WEB))
              .isEqualTo(0);

          Uri notMatchingUrl2 = WebUtil.validUri("https://www.not-example1.test");
          assertThat(
                  measurementDao.getNumTriggersPerDestination(
                      notMatchingUrl2, EventSurfaceType.WEB))
              .isEqualTo(0);

          Uri notMatchingUrl = WebUtil.validUri("https://www.not-example-1.test");
          assertThat(
                  measurementDao.getNumTriggersPerDestination(notMatchingUrl, EventSurfaceType.WEB))
              .isEqualTo(0);
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInAttribution_appDest() {
    Uri sourceSite = Uri.parse("android-app://publisher.app");
    Uri webDestination = WebUtil.validUri("https://web-destination.test");
    Uri appDestination = Uri.parse("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations1 =
        getAttributionsWithDifferentReportingOrigins(
            4, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithAppDestinations2 =
        getAttributionsWithDifferentReportingOrigins(
            2, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations =
        getAttributionsWithDifferentReportingOrigins(
            2, webDestination, 5500000000L, sourceSite, registrant);
    List<Attribution> attributionsOutOfWindow =
        getAttributionsWithDifferentReportingOrigins(
            10, appDestination, 50000000000L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations1) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsWithAppDestinations2) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsWithWebDestinations) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsOutOfWindow) {
      insertAttribution(attribution);
    }
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain0.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                  sourceSite, appDestination, excludedReportingOrigin, 4000000000L, 6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInAttribution_webDest() {
    Uri sourceSite = Uri.parse("android-app://publisher.app");
    Uri webDestination = WebUtil.validUri("https://web-destination.test");
    Uri appDestination = Uri.parse("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentReportingOrigins(
            2, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations1 =
        getAttributionsWithDifferentReportingOrigins(
            4, webDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations2 =
        getAttributionsWithDifferentReportingOrigins(
            2, webDestination, 5500000000L, sourceSite, registrant);
    List<Attribution> attributionsOutOfWindow =
        getAttributionsWithDifferentReportingOrigins(
            10, webDestination, 50000000000L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsWithWebDestinations1) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsWithWebDestinations2) {
      insertAttribution(attribution);
    }
    for (Attribution attribution : attributionsOutOfWindow) {
      insertAttribution(attribution);
    }
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain3.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
                  sourceSite, webDestination, excludedReportingOrigin, 4000000000L, 6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_atWindow() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000001L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.APP,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_expiredSource() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000001L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> expiredSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            4500000001L,
            6000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : expiredSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.APP,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_beyondWindow() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.APP,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_appPublisher() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(9),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.APP,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
  }

  // (Testing countDistinctDestinationsPerPublisherInActiveSource)
  @Test
  public void testCountDistinctDestinations_appPublisher_differentEnrollment() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.APP,
                  "unmatched-enrollment-id",
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.APP,
                  "unmatched-enrollment-id",
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.APP,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_webPublisher_exactMatch() {
    Uri publisher = WebUtil.validUri("https://publisher.test");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(9),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_webPublisher_doesNotMatchDomainAsSuffix() {
    Uri publisher = WebUtil.validUri("https://publisher.test");
    Uri publisherAsSuffix = WebUtil.validUri("https://prefix-publisher.test");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            8,
            true,
            true,
            4500000000L,
            publisherAsSuffix,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisherAsSuffix,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> markedAsDeletedSources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.MARKED_TO_DELETE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : ignoredSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : markedAsDeletedSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(4),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao ->
            assertEquals(
                Integer.valueOf(4),
                measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                    publisher,
                    EventSurfaceType.WEB,
                    excludedDestinations,
                    EventSurfaceType.WEB,
                    4000000000L,
                    6000000000L)));
  }

  @Test
  public void testCountDistinctDestinations_webPublisher_doesNotMatchDifferentScheme() {
    Uri publisher = WebUtil.validUri("https://publisher.test");
    Uri publisherWithDifferentScheme = WebUtil.validUri("http://publisher.test");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisherWithDifferentScheme,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(9),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(5),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctDestinations_webPublisher_multipleDestinations() {
    Uri publisher = WebUtil.validUri("https://publisher.test");
    // One source with multiple destinations
    Source activeSourceWithAppAndWebDestinations =
        getSourceWithDifferentDestinations(
            3,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            1,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    insertSource(activeSourceWithAppAndWebDestinations);
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    List<Uri> excludedDestinations =
        List.of(
            WebUtil.validUri("https://web-destination-1.test"),
            WebUtil.validUri("https://web-destination-2.test"));
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(2),
              measurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(8),
              measurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                  publisher,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  6000000000L));
        });
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(2),
              measurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                  publisher,
                  EventSurfaceType.WEB,
                  excludedDestinations,
                  EventSurfaceType.WEB,
                  4000000000L,
                  6000000000L));
        });
  }

  // Tests countSourcesPerPublisherXEnrollmentExcludingRegistrationOriginSinceTime
  @Test
  public void testCountSourcesExclRegOrigin_forSameOrigin_returnsZero() {
    // Positive case. For same registration origin we always pass the 1 origin
    // per site limit and return 0
    Uri appPublisher = Uri.parse("android-app://publisher.app");
    List<Source> sourcesMoreThanOneDayOld =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - DAYS.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);

    List<Source> sourcesRecent =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);

    for (Source source : sourcesMoreThanOneDayOld) {
      insertSource(source);
    }
    for (Source source : sourcesRecent) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  REGISTRATION_ORIGIN,
                  appPublisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  @Test
  public void testCountSourcesExclRegOrigin_forDifferentAppPublisher_returnsZero() {
    // Positive case. For different publisher we always pass the 1 origin
    // per site limit and return 0
    Uri appPublisher = Uri.parse("android-app://publisher.app");
    Uri appPublisher2 = Uri.parse("android-app://publisher2.app");
    List<Source> sources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : sources) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  REGISTRATION_ORIGIN_2,
                  appPublisher2,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  @Test
  public void testCountSourcesExclRegOrigin_forDifferentWebPublisher_returnsZero() {
    // Positive case. For different publisher we always pass the 1 origin
    // per site limit and return 0
    Uri publisher = WebUtil.validUri("https://publisher.test");
    Uri publisher2 = WebUtil.validUri("https://publisher2.test");
    List<Source> sources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2),
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : sources) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  REGISTRATION_ORIGIN_2,
                  publisher2,
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  @Test
  public void testCountSourcesExclRegOrigin_forDifferentEnrollment_returnsZero() {
    // Positive case. For different enrollment (aka reporting site)
    // we always pass the 1 origin per site limit and return 0
    String differentEnrollment = "new-enrollment";
    Uri differentSite = WebUtil.validUri("https://subdomain.different-site.test");
    Uri appPublisher = Uri.parse("android-app://publisher.app");
    List<Source> sources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : sources) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  differentSite,
                  appPublisher,
                  EventSurfaceType.APP,
                  differentEnrollment,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  @Test
  public void testCountSourcesExclRegOrigin_forDifferentOriginMoreThanTimeWindow_returnsZero() {
    // Positive case. For different origin with same enrollment
    // more than time window of 1 day we always pass the 1 origin per site
    // limit and return 0
    Uri appPublisher = Uri.parse("android-app://publisher.app");
    List<Source> sources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - DAYS.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : sources) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  REGISTRATION_ORIGIN_2,
                  appPublisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  // Tests countSourcesPerPublisherXEnrollmentExcludingRegistrationOriginSinceTime
  @Test
  public void testCountSources_forDifferentOriginWithinTimeWindow_returnsNumOfSources() {
    // Negative case. For different origin with same enrollment
    // we always fail the 1 origin per site limit and return 1
    Uri appPublisher = Uri.parse("android-app://publisher.app");
    List<Source> sources =
        getSourcesWithDifferentDestinations(
            5,
            true,
            true,
            System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2),
            appPublisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    for (Source source : sources) {
      insertSource(source);
    }
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(1),
              measurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                  REGISTRATION_ORIGIN_2,
                  appPublisher,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  System.currentTimeMillis(),
                  MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW));
        });
  }

  @Test
  public void testCountDistinctRegistrationOriginPerPublisherXDestinationInSource_atWindow() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations, 4500000001L, publisher, Source.Status.ACTIVE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    Uri excludedRegistrationOrigin = WebUtil.validUri("https://subdomain1.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(1),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.APP,
                  appDestinations,
                  excludedRegistrationOrigin,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInSource_beyondWindow() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain1.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(0),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.APP,
                  appDestinations,
                  excludedReportingOrigin,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInSource_expiredSource() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations, 4500000001L, publisher, Source.Status.ACTIVE);
    List<Source> expiredSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            4,
            appDestinations,
            webDestinations,
            4500000001L,
            6000000000L,
            publisher,
            Source.Status.ACTIVE);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : expiredSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain1.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.APP,
                  appDestinations,
                  excludedReportingOrigin,
                  4500000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInSource_appDestination() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, null, webDestinations, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentRegistrationOrigins(
            10, appDestinations, webDestinations, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentRegistrationOrigins(
            3, appDestinations, webDestinations, 5000000000L, publisher, Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain1.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(2),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.APP,
                  appDestinations,
                  excludedReportingOrigin,
                  4000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testCountDistinctReportingOriginsPerPublisherXDestinationInSource_webDestination() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, null, webDestinations, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentRegistrationOrigins(
            10, appDestinations, webDestinations, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentRegistrationOrigins(
            3, appDestinations, webDestinations, 5000000000L, publisher, Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    String excludedEnrollmentId = "enrollment-id-22";
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain22.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(3),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.WEB,
                  webDestinations,
                  excludedReportingOrigin,
                  4000000000L,
                  6000000000L));
        });
  }

  // countDistinctEnrollmentsPerPublisherXDestinationInSource
  @Test
  public void countDistinctReportingOriginsPerPublisher_webDestination_multipleDestinations() {
    Uri publisher = Uri.parse("android-app://publisher.app");
    List<Uri> webDestinations1 = List.of(WebUtil.validUri("https://web-destination-1.test"));
    List<Uri> webDestinations2 =
        List.of(
            WebUtil.validUri("https://web-destination-1.test"),
            WebUtil.validUri("https://web-destination-2.test"));
    List<Uri> appDestinations = List.of(Uri.parse("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            3, appDestinations, webDestinations1, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentRegistrationOrigins(
            2, null, webDestinations2, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentRegistrationOrigins(
            10, appDestinations, webDestinations2, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentRegistrationOrigins(
            2, appDestinations, webDestinations1, 5000000000L, publisher, Source.Status.IGNORED);
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source);
    }
    for (Source source : ignoredSources) {
      insertSource(source);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    Uri excludedReportingOrigin = WebUtil.validUri("https://subdomain1.example.test");
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              Integer.valueOf(2),
              measurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                  publisher,
                  EventSurfaceType.WEB,
                  webDestinations2,
                  excludedReportingOrigin,
                  4000000000L,
                  6000000000L));
        });
  }

  @Test
  public void testInstallAttribution_selectHighestPriority() {
    long currentTimestamp = System.currentTimeMillis();

    insertSource(
        createSourceForIATest("IA1", currentTimestamp, 100, -1, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA1");
    insertSource(
        createSourceForIATest("IA2", currentTimestamp, 50, -1, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA2");
    // Should select id IA1 because it has higher priority
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertTrue(getInstallAttributionStatus("IA1", db));
    assertFalse(getInstallAttributionStatus("IA2", db));
    removeSources(Arrays.asList("IA1", "IA2"), db);
  }

  @Test
  public void testInstallAttribution_selectLatest() {
    long currentTimestamp = System.currentTimeMillis();
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA1");
    insertSource(
        createSourceForIATest("IA2", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA2");
    // Should select id=IA2 as it is latest
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertFalse(getInstallAttributionStatus("IA1", db));
    assertTrue(getInstallAttributionStatus("IA2", db));

    removeSources(Arrays.asList("IA1", "IA2"), db);
  }

  @Test
  public void installAttribution_noCooldownWindow_ignoredToBeMarked() {
    long currentTimestamp = System.currentTimeMillis();
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(0)
            .build(),
        "IA1");
    // Should select id=IA2 as it is latest
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertFalse(getInstallAttributionStatus("IA1", db));

    removeSources(Arrays.asList("IA1", "IA2"), db);
  }

  @Test
  public void testInstallAttribution_ignoreNewerSources() {
    long currentTimestamp = System.currentTimeMillis();
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA1");
    insertSource(
        createSourceForIATest("IA2", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA2");
    // Should select id=IA1 as it is the only valid choice.
    // id=IA2 is newer than the evenTimestamp of install event.
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(
                  INSTALLED_PACKAGE, currentTimestamp - DAYS.toMillis(7));
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertTrue(getInstallAttributionStatus("IA1", db));
    assertFalse(getInstallAttributionStatus("IA2", db));
    removeSources(Arrays.asList("IA1", "IA2"), db);
  }

  @Test
  public void testInstallAttribution_noValidSource() {
    long currentTimestamp = System.currentTimeMillis();
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, 10, 10, true, DEFAULT_ENROLLMENT_ID).build(),
        "IA1");
    insertSource(
        createSourceForIATest("IA2", currentTimestamp, 10, 11, true, DEFAULT_ENROLLMENT_ID).build(),
        "IA2");
    // Should not update any sources.
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp)));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertFalse(getInstallAttributionStatus("IA1", db));
    assertFalse(getInstallAttributionStatus("IA2", db));
    removeSources(Arrays.asList("IA1", "IA2"), db);
  }

  @Test
  public void installAttribution_install_installTimeEqualsEventTime() {
    long currentTimestamp = System.currentTimeMillis();
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA1");
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(
                  INSTALLED_PACKAGE, currentTimestamp - DAYS.toMillis(7));
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertEquals(
        currentTimestamp - DAYS.toMillis(7),
        getInstallAttributionInstallTime("IA1", db).longValue());
    removeSources(Arrays.asList("IA1"), db);
  }

  @Test
  public void testInstallAttribution_reinstallReattributionEnabled_skipsAttributionForReinstall() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableReinstallReattribution();
      long currentTimestamp = System.currentTimeMillis();
      long reinstallWindow = DAYS.toMillis(50);
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA1",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setReinstallReattributionWindow(reinstallWindow)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setInstallAttributed(true)
              .build(),
          /* sourceId= */ "IA1");

      insertSource(
          createSourceForIATest(
                  /* id= */ "IA2",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setReinstallReattributionWindow(reinstallWindow)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          /* sourceId= */ "IA2");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA3",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setReinstallReattributionWindow(reinstallWindow)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          /* sourceId= */ "IA3");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA4",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setReinstallReattributionWindow(reinstallWindow)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          /* sourceId= */ "IA4");
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.insertOrUpdateAppReportHistory(
                    INSTALLED_PACKAGE, REGISTRATION_ORIGIN, currentTimestamp);
              }));

      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
              }));
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA1", db));
      assertFalse(getInstallAttributionStatus(/* sourceDbId= */ "IA2", db));
      assertFalse(getInstallAttributionStatus(/* sourceDbId= */ "IA3", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA4", db));

      removeSources(Arrays.asList("IA1", "IA2", "IA3", "IA4"), db);
    }
  }

  @Test
  public void testInstallAttribution_reinstallReattributionDisabled_doesNotSkipReinstall() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(false).when(mFlags).getMeasurementEnableReinstallReattribution();
      long currentTimestamp = System.currentTimeMillis();
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA1",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setInstallAttributed(true)
              .build(),
          "IA1");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA2",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          "IA2");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA3",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          "IA3");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA4",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .build(),
          "IA4");
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.insertOrUpdateAppReportHistory(
                    INSTALLED_PACKAGE, REGISTRATION_ORIGIN, currentTimestamp);
              }));
      // Should select id=IA2 as it is latest
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
              }));
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA1", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA2", db));
      assertFalse(getInstallAttributionStatus(/* sourceDbId= */ "IA3", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA4", db));

      removeSources(Arrays.asList("IA1", "IA2", "IA3", "IA4"), db);
    }
  }

  @Test
  public void testInstallAttribution_reinstallReattributionEnabledNoWindow_doesNotSkipReinstall() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableReinstallReattribution();
      long currentTimestamp = System.currentTimeMillis();
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA1",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .setInstallAttributed(true)
              .build(),
          "IA1");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA2",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .build(),
          "IA2");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA3",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .build(),
          "IA3");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA4",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  ENROLLMENT_ID1,
                  REGISTRATION_ORIGIN_2)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .build(),
          "IA4");
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.insertOrUpdateAppReportHistory(
                    INSTALLED_PACKAGE, REGISTRATION_ORIGIN, currentTimestamp);
              }));
      // Should select id=IA2 as it is latest
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
              }));
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA1", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA2", db));
      assertFalse(getInstallAttributionStatus(/* sourceDbId= */ "IA3", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA4", db));

      removeSources(Arrays.asList("IA1", "IA2", "IA3", "IA4"), db);
    }
  }

  @Test
  public void testInstallAttribution_reinstallReattributionEnabledNoReinstall_doesNotSkip() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableReinstallReattribution();
      long currentTimestamp = System.currentTimeMillis();
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA1",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 10,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .build(),
          "IA1");
      insertSource(
          createSourceForIATest(
                  /* id= */ "IA2",
                  currentTimestamp,
                  /* priority= */ -1,
                  /* eventTimePastDays= */ 5,
                  /* expiredIAWindow= */ false,
                  DEFAULT_ENROLLMENT_ID)
              .setInstallCooldownWindow(COOLDOWN_WINDOW)
              .setReinstallReattributionWindow(0L)
              .build(),
          "IA2");

      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.insertOrUpdateAppReportHistory(
                    INSTALLED_PACKAGE, REGISTRATION_ORIGIN, currentTimestamp);
              }));
      // Should select id=IA2 as it is latest
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
              }));
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      assertFalse(getInstallAttributionStatus(/* sourceDbId= */ "IA1", db));
      assertTrue(getInstallAttributionStatus(/* sourceDbId= */ "IA2", db));

      removeSources(Arrays.asList("IA1", "IA2"), db);
    }
  }

  @Test
  public void doInstallAttribution_noValidSourceStatus_IgnoresSources() {
    long currentTimestamp = System.currentTimeMillis();
    Source source =
        createSourceForIATest("IA1", currentTimestamp, 100, -1, false, DEFAULT_ENROLLMENT_ID)
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build();

    // Execution
    // Active source should get install attributed
    source.setStatus(Source.Status.ACTIVE);
    insertSource(source, source.getId());
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp)));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertTrue(getInstallAttributionStatus("IA1", db));
    removeSources(Collections.singletonList("IA1"), db);

    // Active source should not get install attributed
    source.setStatus(Source.Status.IGNORED);
    insertSource(source, source.getId());
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp)));
    assertFalse(getInstallAttributionStatus("IA1", db));
    removeSources(Collections.singletonList("IA1"), db);

    // MARKED_TO_DELETE source should not get install attributed
    source.setStatus(Source.Status.MARKED_TO_DELETE);
    insertSource(source, source.getId());
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp)));
    assertFalse(getInstallAttributionStatus("IA1", db));
    removeSources(Collections.singletonList("IA1"), db);
  }

  @Test
  public void doInstallAttribution_withSourcesAcrossEnrollments_marksOneInstallFromEachRegOrigin() {
    long currentTimestamp = System.currentTimeMillis();

    // Enrollment1: Choose IA2 because that's newer and still occurred before install
    insertSource(
        createSourceForIATest("IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID + "_1")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example1.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA1");
    insertSource(
        createSourceForIATest("IA2", currentTimestamp, -1, 9, false, DEFAULT_ENROLLMENT_ID + "_1")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example1.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA2");

    // Enrollment2: Choose IA4 because IA3's install attribution window has expired
    insertSource(
        createSourceForIATest("IA3", currentTimestamp, -1, 10, true, DEFAULT_ENROLLMENT_ID + "_2")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example2.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA3");
    insertSource(
        createSourceForIATest("IA4", currentTimestamp, -1, 9, false, DEFAULT_ENROLLMENT_ID + "_2")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example2.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA4");

    // Enrollment3: Choose IA5 because IA6 was registered after install event
    insertSource(
        createSourceForIATest("IA5", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID + "_3")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example3.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA5");
    insertSource(
        createSourceForIATest("IA6", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID + "_3")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example3.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA6");

    // Enrollment4: Choose IA8 due to higher priority
    insertSource(
        createSourceForIATest("IA7", currentTimestamp, 5, 10, false, DEFAULT_ENROLLMENT_ID + "_4")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example4.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA7");
    insertSource(
        createSourceForIATest("IA8", currentTimestamp, 10, 10, false, DEFAULT_ENROLLMENT_ID + "_4")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example4.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA8");

    // Enrollment5: Choose none because both sources are ineligible
    // Expired install attribution window
    insertSource(
        createSourceForIATest("IA9", currentTimestamp, 5, 31, true, DEFAULT_ENROLLMENT_ID + "_5")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example5.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA9");
    // Registered after install attribution
    insertSource(
        createSourceForIATest("IA10", currentTimestamp, 10, 3, false, DEFAULT_ENROLLMENT_ID + "_5")
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example5.test"))
            .setInstallCooldownWindow(COOLDOWN_WINDOW)
            .build(),
        "IA10");

    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> {
              measurementDao.doInstallAttribution(
                  INSTALLED_PACKAGE, currentTimestamp - DAYS.toMillis(7));
            }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertTrue(getInstallAttributionStatus("IA2", db));
    assertTrue(getInstallAttributionStatus("IA4", db));
    assertTrue(getInstallAttributionStatus("IA5", db));
    assertTrue(getInstallAttributionStatus("IA8", db));

    assertFalse(getInstallAttributionStatus("IA1", db));
    assertFalse(getInstallAttributionStatus("IA3", db));
    assertFalse(getInstallAttributionStatus("IA6", db));
    assertFalse(getInstallAttributionStatus("IA7", db));
    assertFalse(getInstallAttributionStatus("IA9", db));
    assertFalse(getInstallAttributionStatus("IA10", db));

    removeSources(
        Arrays.asList("IA1", "IA2", "IA3", "IA4", "IA5", "IA6", "IA7", "IA8", "IA8", "IA10"), db);
  }

  @Test
  public void deleteFlexEventReportsAndAttributions_success() throws JSONException {
    // Setup - Creates the following -
    // source - S1, S2
    // trigger - T1, T2, T3
    // event reports - (E13, E23) (E11_1, E11_2, E11_3) (E21) (E22_1, E22_2)
    // attributions
    //    (ATT11_00 (aggregate scope), ATT11_01 (aggregate scope))
    //    (ATT11_1, ATT11_2, ATT11_3) (ATT21) (ATT22_1, ATT22_2)
    prepareDataForFlexEventReportAndAttributionDeletion();

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    // Assert attributions present
    assertNotNull(getAttribution("ATT11_1", db));
    assertNotNull(getAttribution("ATT11_2", db));
    assertNotNull(getAttribution("ATT11_3", db));
    assertNotNull(getAttribution("ATT21", db));
    assertNotNull(getAttribution("ATT22_1", db));
    assertNotNull(getAttribution("ATT22_2", db));

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          // Assert sources and triggers present
          assertNotNull(measurementDao.getSource("S1"));
          assertNotNull(measurementDao.getSource("S2"));
          assertNotNull(measurementDao.getTrigger("T1"));
          assertNotNull(measurementDao.getTrigger("T2"));

          // Validate presence of unmatched event reports
          measurementDao.getEventReport("E13");
          measurementDao.getEventReport("E23");

          // Event report group 1
          // Validate event reports present
          EventReport e111 = measurementDao.getEventReport("E11_1");
          EventReport e112 = measurementDao.getEventReport("E11_2");

          // Deletion
          measurementDao.deleteFlexEventReportsAndAttributions(List.of(e111, e112));

          // Validate event report deletion
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E11_1");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E11_2");
              });
          assertNotNull(measurementDao.getEventReport("E11_3"));

          // Event report group 2
          // Validate event reports present
          EventReport e21 = measurementDao.getEventReport("E21");

          // Deletion
          measurementDao.deleteFlexEventReportsAndAttributions(List.of(e21));

          // Validate event report deletion
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E21");
              });

          // Event report group 3
          // Validate event reports present (retrieval doesn't throw)
          measurementDao.getEventReport("E22_1");
          EventReport e222 = measurementDao.getEventReport("E22_2");

          // Deletion
          measurementDao.deleteFlexEventReportsAndAttributions(List.of(e222));

          // Validate event report deletion
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E22_2");
              });
          assertNotNull(measurementDao.getEventReport("E22_1"));

          // Validate sources and triggers present
          assertNotNull(measurementDao.getSource("S1"));
          assertNotNull(measurementDao.getSource("S2"));
          assertNotNull(measurementDao.getTrigger("T1"));
          assertNotNull(measurementDao.getTrigger("T2"));

          // Validate presence of unmatched event reports
          measurementDao.getEventReport("E13");
          measurementDao.getEventReport("E23");
        });

    // Validate attribution deletion
    assertNotNull(getAttribution("ATT11_00", db));
    assertNotNull(getAttribution("ATT11_01", db));
    assertNull(getAttribution("ATT11_1", db));
    assertNull(getAttribution("ATT11_2", db));
    assertNotNull(getAttribution("ATT11_3", db));
    assertNull(getAttribution("ATT21", db));
    // Attribution deletion order within the group associated with an event report is generally
    // by insertion order, although it's not guaranteed. We deleted event report E22_2 but the
    // first limited associated attribution returned is ATT22_1.
    assertNull(getAttribution("ATT22_1", db));
    assertNotNull(getAttribution("ATT22_2", db));
  }

  @Test
  public void deleteSources_providedIds_deletesMatchingSourcesAndRelatedData()
      throws JSONException {
    // Setup - Creates the following -
    // source - S1, S2, S3, S4
    // trigger - T1, T2, T3, T4
    // event reports - E11, E12, E21, E22, E23, E33, E44
    // aggregate reports - AR11, AR12, AR21, AR34
    // attributions - ATT11, ATT12, ATT21, ATT22, ATT33, ATT44
    prepareDataForSourceAndTriggerDeletion();

    // Execution
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          measurementDao.deleteSources(List.of("S1", "S2"));

          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getSource("S1");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getSource("S2");
              });

          assertNotNull(measurementDao.getSource("S3"));
          assertNotNull(measurementDao.getSource("S4"));
          assertNotNull(measurementDao.getTrigger("T1"));
          assertNotNull(measurementDao.getTrigger("T2"));
          assertNotNull(measurementDao.getTrigger("T3"));
          assertNotNull(measurementDao.getTrigger("T4"));

          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E11");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E12");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E21");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E22");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E23");
              });
          assertNotNull(measurementDao.getEventReport("E33"));
          assertNotNull(measurementDao.getEventReport("E44"));

          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getAggregateReport("AR11");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getAggregateReport("AR12");
              });
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getAggregateReport("AR21");
              });
          assertNotNull(measurementDao.getAggregateReport("AR34"));
        });

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertEquals(2, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
  }

  @Test
  public void deleteSource_providedId_deletesMatchingXnaIgnoredSource() {
    // Setup
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    Source s1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("S1")
            .setEnrollmentId("1")
            .build();

    ContentValues sourceValues = new ContentValues();
    sourceValues.put(SourceContract.ID, s1.getId());
    sourceValues.put(SourceContract.EVENT_ID, s1.getEventId().getValue());

    ContentValues xnaIgnoredSourceValues = new ContentValues();
    xnaIgnoredSourceValues.put(XnaIgnoredSourcesContract.SOURCE_ID, s1.getId());
    xnaIgnoredSourceValues.put(XnaIgnoredSourcesContract.ENROLLMENT_ID, s1.getEnrollmentId());

    // Execution
    db.insert(SourceContract.TABLE, null, sourceValues);
    db.insert(XnaIgnoredSourcesContract.TABLE, null, xnaIgnoredSourceValues);

    // Assertion
    assertEquals(1, DatabaseUtils.queryNumEntries(db, SourceContract.TABLE));
    assertEquals(1, DatabaseUtils.queryNumEntries(db, XnaIgnoredSourcesContract.TABLE));

    // Execution
    removeSources(Collections.singletonList(s1.getId()), db);

    // Assertion
    assertEquals(0, DatabaseUtils.queryNumEntries(db, SourceContract.TABLE));
    assertEquals(0, DatabaseUtils.queryNumEntries(db, XnaIgnoredSourcesContract.TABLE));
  }

  @Test
  public void deleteTriggers_providedIds_deletesMatchingTriggersAndRelatedData()
      throws JSONException {
    // Setup - Creates the following -
    // source - S1, S2, S3, S4
    // trigger - T1, T2, T3, T4
    // event reports - E11, E12, E21, E22, E23, E33, E44
    // aggregate reports - AR11, AR12, AR21, AR34
    // attributions - ATT11, ATT12, ATT21, ATT22, ATT33, ATT44
    prepareDataForSourceAndTriggerDeletion();

    // Execution
    mDatastoreManager.runInTransaction(
        measurementDao -> {
          measurementDao.deleteTriggers(List.of("T1", "T2"));

          assertNotNull(measurementDao.getSource("S1"));
          assertNotNull(measurementDao.getSource("S2"));
          assertNotNull(measurementDao.getSource("S3"));
          assertNotNull(measurementDao.getSource("S4"));
          assertThrows(DatastoreException.class, () -> measurementDao.getTrigger("T1"));
          assertThrows(DatastoreException.class, () -> measurementDao.getTrigger("T2"));
          assertNotNull(measurementDao.getTrigger("T3"));
          assertNotNull(measurementDao.getTrigger("T4"));

          assertThrows(DatastoreException.class, () -> measurementDao.getEventReport("E11"));
          assertThrows(DatastoreException.class, () -> measurementDao.getEventReport("E12"));
          assertThrows(DatastoreException.class, () -> measurementDao.getEventReport("E21"));
          assertThrows(DatastoreException.class, () -> measurementDao.getEventReport("E22"));
          assertNotNull(measurementDao.getEventReport("E23"));
          assertNotNull(measurementDao.getEventReport("E33"));
          assertNotNull(measurementDao.getEventReport("E44"));

          assertThrows(DatastoreException.class, () -> measurementDao.getAggregateReport("AR11"));
          assertThrows(DatastoreException.class, () -> measurementDao.getAggregateReport("AR12"));
          assertThrows(DatastoreException.class, () -> measurementDao.getAggregateReport("AR21"));

          assertNotNull(measurementDao.getAggregateReport("AR34"));
        });

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    assertEquals(2, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
  }

  // Setup - Creates the following -
  // source - S1, S2
  // trigger - T1, T2
  // event reports - E11_1, E11_2, E11_3, E21, E22_1, E22_2
  // attributions - ATT11_1, ATT11_2, ATT11_3, ATT21, ATT22_1, ATT22_2
  private void prepareDataForFlexEventReportAndAttributionDeletion() throws JSONException {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Source s1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("S1")
            .build();
    Source s2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setId("S2")
            .build();
    Trigger t1 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T1")
            .build();
    Trigger t2 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T2")
            .build();
    Trigger t3 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T2")
            .build();
    EventReport e111 = createEventReportForSourceAndTrigger("E11_1", s1, t1);
    EventReport e112 = createEventReportForSourceAndTrigger("E11_2", s1, t1);
    EventReport e113 = createEventReportForSourceAndTrigger("E11_3", s1, t1);
    EventReport e21 = createEventReportForSourceAndTrigger("E21", s2, t1);
    EventReport e221 = createEventReportForSourceAndTrigger("E22_1", s2, t2);
    EventReport e222 = createEventReportForSourceAndTrigger("E22_2", s2, t2);
    EventReport e13 = createEventReportForSourceAndTrigger("E13", s1, t3);
    EventReport e23 = createEventReportForSourceAndTrigger("E23", s2, t3);
    Attribution att1100 =
        createAttribution("ATT11_00", Attribution.Scope.AGGREGATE, s1.getId(), t1.getId());
    Attribution att1101 =
        createAttribution("ATT11_01", Attribution.Scope.AGGREGATE, s1.getId(), t1.getId());
    Attribution att111 = createAttribution("ATT11_1", s1.getId(), t1.getId());
    Attribution att112 = createAttribution("ATT11_2", s1.getId(), t1.getId());
    Attribution att113 = createAttribution("ATT11_3", s1.getId(), t1.getId());
    Attribution att21 = createAttribution("ATT21", s2.getId(), t1.getId());
    Attribution att221 = createAttribution("ATT22_1", s2.getId(), t2.getId());
    Attribution att222 = createAttribution("ATT22_2", s2.getId(), t2.getId());

    insertSource(s1, s1.getId());
    insertSource(s2, s2.getId());
    AbstractDbIntegrationTest.insertToDb(t1, db);
    AbstractDbIntegrationTest.insertToDb(t2, db);
    AbstractDbIntegrationTest.insertToDb(e111, db);
    AbstractDbIntegrationTest.insertToDb(e112, db);
    AbstractDbIntegrationTest.insertToDb(e113, db);
    AbstractDbIntegrationTest.insertToDb(e21, db);
    AbstractDbIntegrationTest.insertToDb(e221, db);
    AbstractDbIntegrationTest.insertToDb(e222, db);
    AbstractDbIntegrationTest.insertToDb(e13, db);
    AbstractDbIntegrationTest.insertToDb(e23, db);
    AbstractDbIntegrationTest.insertToDb(att1100, db);
    AbstractDbIntegrationTest.insertToDb(att1101, db);
    AbstractDbIntegrationTest.insertToDb(att111, db);
    AbstractDbIntegrationTest.insertToDb(att112, db);
    AbstractDbIntegrationTest.insertToDb(att113, db);
    AbstractDbIntegrationTest.insertToDb(att21, db);
    AbstractDbIntegrationTest.insertToDb(att221, db);
    AbstractDbIntegrationTest.insertToDb(att222, db);
  }

  // Setup - Creates the following -
  // source - S1, S2, S3, S4
  // trigger - T1, T2, T3, T4
  // event reports - E11, E12, E21, E22, E23, E33, E44
  // aggregate reports - AR11, AR12, AR21, AR34
  // attributions - ATT11, ATT12, ATT21, ATT22, ATT33, ATT44
  private void prepareDataForSourceAndTriggerDeletion() throws JSONException {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Source s1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("S1")
            .build(); // deleted
    Source s2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setId("S2")
            .build(); // deleted
    Source s3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(3L))
            .setId("S3")
            .build();
    Source s4 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(4L))
            .setId("S4")
            .build();
    Trigger t1 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T1")
            .build();
    Trigger t2 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T2")
            .build();
    Trigger t3 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T3")
            .build();
    Trigger t4 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T4")
            .build();
    EventReport e11 = createEventReportForSourceAndTrigger("E11", s1, t1);
    EventReport e12 = createEventReportForSourceAndTrigger("E12", s1, t2);
    EventReport e21 = createEventReportForSourceAndTrigger("E21", s2, t1);
    EventReport e22 = createEventReportForSourceAndTrigger("E22", s2, t2);
    EventReport e23 = createEventReportForSourceAndTrigger("E23", s2, t3);
    EventReport e33 = createEventReportForSourceAndTrigger("E33", s3, t3);
    EventReport e44 = createEventReportForSourceAndTrigger("E44", s4, t4);
    AggregateReport ar11 = createAggregateReportForSourceAndTrigger("AR11", s1, t1);
    AggregateReport ar12 = createAggregateReportForSourceAndTrigger("AR12", s1, t2);
    AggregateReport ar21 = createAggregateReportForSourceAndTrigger("AR21", s2, t1);
    AggregateReport ar34 = createAggregateReportForSourceAndTrigger("AR34", s3, t4);
    Attribution att11 = createAttribution("ATT11", s1.getId(), t1.getId()); // deleted
    Attribution att12 = createAttribution("ATT12", s1.getId(), t2.getId()); // deleted
    Attribution att21 = createAttribution("ATT21", s2.getId(), t1.getId()); // deleted
    Attribution att22 = createAttribution("ATT22", s2.getId(), t2.getId()); // deleted
    Attribution att33 = createAttribution("ATT33", s3.getId(), t3.getId());
    Attribution att44 = createAttribution("ATT44", s4.getId(), t4.getId());

    insertSource(s1, s1.getId());
    insertSource(s2, s2.getId());
    insertSource(s3, s3.getId());
    insertSource(s4, s4.getId());

    AbstractDbIntegrationTest.insertToDb(t1, db);
    AbstractDbIntegrationTest.insertToDb(t2, db);
    AbstractDbIntegrationTest.insertToDb(t3, db);
    AbstractDbIntegrationTest.insertToDb(t4, db);

    AbstractDbIntegrationTest.insertToDb(e11, db);
    AbstractDbIntegrationTest.insertToDb(e12, db);
    AbstractDbIntegrationTest.insertToDb(e21, db);
    AbstractDbIntegrationTest.insertToDb(e22, db);
    AbstractDbIntegrationTest.insertToDb(e23, db);
    AbstractDbIntegrationTest.insertToDb(e33, db);
    AbstractDbIntegrationTest.insertToDb(e44, db);

    AbstractDbIntegrationTest.insertToDb(ar11, db);
    AbstractDbIntegrationTest.insertToDb(ar12, db);
    AbstractDbIntegrationTest.insertToDb(ar21, db);
    AbstractDbIntegrationTest.insertToDb(ar34, db);

    AbstractDbIntegrationTest.insertToDb(att11, db);
    AbstractDbIntegrationTest.insertToDb(att12, db);
    AbstractDbIntegrationTest.insertToDb(att21, db);
    AbstractDbIntegrationTest.insertToDb(att22, db);
    AbstractDbIntegrationTest.insertToDb(att33, db);
    AbstractDbIntegrationTest.insertToDb(att44, db);
  }

  @Test
  public void testUndoInstallAttribution_noMarkedSource() {
    long currentTimestamp = System.currentTimeMillis();
    Source source =
        createSourceForIATest("IA1", currentTimestamp, 10, 10, false, DEFAULT_ENROLLMENT_ID)
            .build();
    source.setInstallAttributed(true);
    insertSource(source, source.getId());
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> measurementDao.undoInstallAttribution(INSTALLED_PACKAGE)));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    // Should set installAttributed = false for id=IA1
    assertFalse(getInstallAttributionStatus("IA1", db));
  }

  @Test
  public void undoInstallAttribution_uninstall_nullInstallTime() {
    long currentTimestamp = System.currentTimeMillis();
    Source source =
        createSourceForIATest("IA1", currentTimestamp, 10, 10, false, DEFAULT_ENROLLMENT_ID)
            .build();
    source.setInstallAttributed(true);
    insertSource(source, source.getId());
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> measurementDao.undoInstallAttribution(INSTALLED_PACKAGE)));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    // Should set installTime = null for id=IA1
    assertNull(getInstallAttributionInstallTime("IA1", db));
  }

  @Test
  public void getSourceDestinations_returnsExpected() {
    // Insert two sources with some intersection of destinations
    // and assert all destination queries work.

    // First source
    List<Uri> webDestinations1 =
        List.of(
            Uri.parse("https://first-place.test"),
            Uri.parse("https://second-place.test"),
            Uri.parse("https://third-place.test"));
    List<Uri> appDestinations1 = List.of(Uri.parse("android-app://test.first-place"));
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("1")
            .setAppDestinations(appDestinations1)
            .setWebDestinations(webDestinations1)
            .build();
    insertSource(source1, source1.getId());

    // Second source
    List<Uri> webDestinations2 =
        List.of(
            Uri.parse("https://not-first-place.test"),
            Uri.parse("https://not-second-place.test"),
            Uri.parse("https://third-place.test"));
    List<Uri> appDestinations2 = List.of(Uri.parse("android-app://test.not-first-place"));
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("2")
            .setAppDestinations(appDestinations2)
            .setWebDestinations(webDestinations2)
            .build();
    insertSource(source2, source2.getId());

    Pair<List<Uri>, List<Uri>> result1 =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceDestinations(source1.getId()))
            .get();
    // Assert first app destinations
    assertTrue(
        ImmutableMultiset.copyOf(source1.getAppDestinations())
            .equals(ImmutableMultiset.copyOf(result1.first)));
    // Assert first web destinations
    assertTrue(
        ImmutableMultiset.copyOf(source1.getWebDestinations())
            .equals(ImmutableMultiset.copyOf(result1.second)));

    Pair<List<Uri>, List<Uri>> result2 =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceDestinations(source2.getId()))
            .get();
    // Assert second app destinations
    assertTrue(
        ImmutableMultiset.copyOf(source2.getAppDestinations())
            .equals(ImmutableMultiset.copyOf(result2.first)));
    // Assert second web destinations
    assertTrue(
        ImmutableMultiset.copyOf(source2.getWebDestinations())
            .equals(ImmutableMultiset.copyOf(result2.second)));
  }

  @Test
  public void getNumAggregateReportsPerSource_returnsExpected() {
    List<Source> sources =
        Arrays.asList(
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(1L))
                .setId("source1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(2L))
                .setId("source2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(3L))
                .setId("source3")
                .build());
    List<AggregateReport> reports =
        Arrays.asList(
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-1.test"),
                1,
                "source1",
                AggregateReportFixture.ValidAggregateReportParams.API),
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-1.test"),
                2,
                "source1",
                AggregateReportFixture.ValidAggregateReportParams.API),
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-2.test"),
                3,
                "source2",
                AggregateReportFixture.ValidAggregateReportParams.API),
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-1.test"),
                4,
                "source3",
                AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API),
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-1.test"),
                5,
                "source3",
                AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API),
            generateMockAggregateReport(
                WebUtil.validUrl("https://destination-2.test"),
                6,
                "source3",
                AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    sources.forEach(source -> insertSource(source, source.getId()));
    Consumer<AggregateReport> aggregateReportConsumer =
        aggregateReport -> {
          ContentValues values = new ContentValues();
          values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
          values.put(MeasurementTables.AggregateReport.SOURCE_ID, aggregateReport.getSourceId());
          values.put(
              MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
              aggregateReport.getAttributionDestination().toString());
          values.put(MeasurementTables.AggregateReport.API, aggregateReport.getApi());
          db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
        };
    reports.forEach(aggregateReportConsumer);

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source1", AggregateReportFixture.ValidAggregateReportParams.API))
              .isEqualTo(2);
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source2", AggregateReportFixture.ValidAggregateReportParams.API))
              .isEqualTo(1);
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source3", AggregateReportFixture.ValidAggregateReportParams.API))
              .isEqualTo(0);
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source1", AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API))
              .isEqualTo(0);
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source2", AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API))
              .isEqualTo(0);
          assertThat(
                  measurementDao.countNumAggregateReportsPerSource(
                      "source3", AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API))
              .isEqualTo(3);
        });
  }

  @Test
  public void getNumAggregateReportsPerDestination_returnsExpected() {
    List<AggregateReport> reportsWithPlainDestination =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-1.test"), 1));
    List<AggregateReport> reportsWithPlainAndSubDomainDestination =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-2.test"), 2),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-2.test"), 3));
    List<AggregateReport> reportsWithPlainAndPathDestination =
        Arrays.asList(
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-3.test"), 4),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-3.test/abcd"), 5));
    List<AggregateReport> reportsWithAll3Types =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-4.test"), 6),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-4.test"), 7),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-4.test/abcd"), 8));
    List<AggregateReport> reportsWithAndroidAppDestination =
        Arrays.asList(generateMockAggregateReport("android-app://destination-5.app", 9));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    Stream.of(
            reportsWithPlainDestination,
            reportsWithPlainAndSubDomainDestination,
            reportsWithPlainAndPathDestination,
            reportsWithAll3Types,
            reportsWithAndroidAppDestination)
        .flatMap(Collection::stream)
        .forEach(
            aggregateReport -> {
              ContentValues values = new ContentValues();
              values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
              values.put(
                  MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                  aggregateReport.getAttributionDestination().toString());
              values.put(
                  MeasurementTables.AggregateReport.IS_FAKE_REPORT, aggregateReport.isFakeReport());
              db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
            });

    List<String> attributionDestinations1 = createWebDestinationVariants(1);
    List<String> attributionDestinations2 = createWebDestinationVariants(2);
    List<String> attributionDestinations3 = createWebDestinationVariants(3);
    List<String> attributionDestinations4 = createWebDestinationVariants(4);
    List<String> attributionDestinations5 = createAppDestinationVariants(5);

    // expected query return values for attribution destination variants
    List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
    List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
    List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
    assertAggregateReportCount(
        attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts);
    assertAggregateReportCount(
        attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts);
    assertAggregateReportCount(
        attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts);
    assertAggregateReportCount(
        attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts);
    assertAggregateReportCount(
        attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts);
  }

  @Test
  public void getAggregateReportById_fakeReport() {
    AggregateReport ar11 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("11")
            .setIsFakeReport(true)
            .build();
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AbstractDbIntegrationTest.insertToDb(ar11, db);

    Optional<AggregateReport> resOpt =
        mDatastoreManager.runInTransactionWithResult((dao) -> dao.getAggregateReport("11"));
    assertTrue(resOpt.isPresent());
    AggregateReport res = resOpt.get();
    assertTrue(res.isFakeReport());
  }

  @Test
  public void getNumEventReportsPerDestination_returnsExpected() {
    List<EventReport> reportsWithPlainDestination =
        Arrays.asList(generateMockEventReport(WebUtil.validUrl("https://destination-1.test"), 1));
    List<EventReport> reportsWithPlainAndSubDomainDestination =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://destination-2.test"), 2),
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-2.test"), 3));
    List<EventReport> reportsWithPlainAndPathDestination =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-3.test"), 4),
            generateMockEventReport(
                WebUtil.validUrl("https://subdomain.destination-3.test/abcd"), 5));
    List<EventReport> reportsWithAll3Types =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://destination-4.test"), 6),
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-4.test"), 7),
            generateMockEventReport(
                WebUtil.validUrl("https://subdomain.destination-4.test/abcd"), 8));
    List<EventReport> reportsWithAndroidAppDestination =
        Arrays.asList(generateMockEventReport("android-app://destination-5.app", 9));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    Stream.of(
            reportsWithPlainDestination,
            reportsWithPlainAndSubDomainDestination,
            reportsWithPlainAndPathDestination,
            reportsWithAll3Types,
            reportsWithAndroidAppDestination)
        .flatMap(Collection::stream)
        .forEach(
            eventReport -> {
              ContentValues values = new ContentValues();
              values.put(EventReportContract.ID, eventReport.getId());
              values.put(
                  EventReportContract.ATTRIBUTION_DESTINATION,
                  eventReport.getAttributionDestinations().get(0).toString());
              db.insert(EventReportContract.TABLE, null, values);
            });

    List<String> attributionDestinations1 = createWebDestinationVariants(1);
    List<String> attributionDestinations2 = createWebDestinationVariants(2);
    List<String> attributionDestinations3 = createWebDestinationVariants(3);
    List<String> attributionDestinations4 = createWebDestinationVariants(4);
    List<String> attributionDestinations5 = createAppDestinationVariants(5);

    // expected query return values for attribution destination variants
    List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
    List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
    List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
    assertEventReportCount(
        attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts);
    assertEventReportCount(
        attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts);
    assertEventReportCount(
        attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts);
    assertEventReportCount(
        attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts);
    assertEventReportCount(
        attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts);
  }

  @Test
  public void testGetSourceEventReports() {
    List<Source> sourceList =
        Arrays.asList(
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("1")
                .setEventId(new UnsignedLong(3L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("2")
                .setEventId(new UnsignedLong(4L))
                .setEnrollmentId("1")
                .build(),
            // Should always be ignored
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("3")
                .setEventId(new UnsignedLong(4L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("15")
                .setEventId(new UnsignedLong(15L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("16")
                .setEventId(new UnsignedLong(16L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("20")
                .setEventId(new UnsignedLong(20L))
                .setEnrollmentId("2")
                .build());

    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder().setId("101").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("102").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("201").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("202").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("1001").setEnrollmentId("2").build());

    // Should match with source 1
    List<EventReport> reportList1 = new ArrayList<>();
    reportList1.add(
        new EventReport.Builder()
            .setId("1")
            .setSourceEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(0).getAppDestinations())
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList1.add(
        new EventReport.Builder()
            .setId("7")
            .setSourceEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());

    // Should match with source 2
    List<EventReport> reportList2 = new ArrayList<>();
    reportList2.add(
        new EventReport.Builder()
            .setId("3")
            .setSourceEventId(new UnsignedLong(4L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("201")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList2.add(
        new EventReport.Builder()
            .setId("8")
            .setSourceEventId(new UnsignedLong(4L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("202")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());

    List<EventReport> reportList3 = new ArrayList<>();
    // Should not match with any source
    reportList3.add(
        new EventReport.Builder()
            .setId("2")
            .setSourceEventId(new UnsignedLong(5L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("15")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("4")
            .setSourceEventId(new UnsignedLong(6L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("16")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .setTriggerValue(100L)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("5")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("15")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .setTriggerValue(120L)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("6")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("20")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .setTriggerValue(200L)
            .build());

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    sourceList.forEach(source -> insertSource(source, source.getId()));
    triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

    Stream.of(reportList1, reportList2, reportList3)
        .flatMap(Collection::stream)
        .forEach(
            (eventReport -> {
              mDatastoreManager.runInTransaction((dao) -> dao.insertEventReport(eventReport));
            }));

    assertEquals(
        reportList1,
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceEventReports(sourceList.get(0)))
            .orElseThrow());

    assertEquals(
        reportList2,
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceEventReports(sourceList.get(1)))
            .orElseThrow());
  }

  @Test
  public void getSourceEventReports_sourcesWithSameEventId_haveSeparateEventReportsMatch() {
    List<Source> sourceList =
        Arrays.asList(
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("1")
                .setEventId(new UnsignedLong(1L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("2")
                .setEventId(new UnsignedLong(1L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("3")
                .setEventId(new UnsignedLong(2L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setId("4")
                .setEventId(new UnsignedLong(2L))
                .setEnrollmentId("2")
                .build());

    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder().setId("101").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("102").setEnrollmentId("2").build());

    // Should match with source 1
    List<EventReport> reportList1 = new ArrayList<>();
    reportList1.add(
        new EventReport.Builder()
            .setId("1")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(0).getAppDestinations())
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList1.add(
        new EventReport.Builder()
            .setId("2")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());

    // Should match with source 2
    List<EventReport> reportList2 = new ArrayList<>();
    reportList2.add(
        new EventReport.Builder()
            .setId("3")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList2.add(
        new EventReport.Builder()
            .setId("4")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());

    // Match with source3
    List<EventReport> reportList3 = new ArrayList<>();
    reportList3.add(
        new EventReport.Builder()
            .setId("5")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("2")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("3")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("6")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("2")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("3")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    sourceList.forEach(source -> insertSource(source, source.getId()));
    triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

    Stream.of(reportList1, reportList2, reportList3)
        .flatMap(Collection::stream)
        .forEach(
            (eventReport -> {
              mDatastoreManager.runInTransaction((dao) -> dao.insertEventReport(eventReport));
            }));

    assertEquals(
        reportList1,
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceEventReports(sourceList.get(0)))
            .orElseThrow());

    assertEquals(
        reportList2,
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceEventReports(sourceList.get(1)))
            .orElseThrow());

    assertEquals(
        reportList3,
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getSourceEventReports(sourceList.get(2)))
            .orElseThrow());
  }

  @Test
  public void testUpdateSourceStatus() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);

    List<Source> sourceList = new ArrayList<>();
    sourceList.add(SourceFixture.getMinimalValidSourceBuilder().setId("1").build());
    sourceList.add(SourceFixture.getMinimalValidSourceBuilder().setId("2").build());
    sourceList.add(SourceFixture.getMinimalValidSourceBuilder().setId("3").build());
    sourceList.forEach(
        source -> {
          ContentValues values = new ContentValues();
          values.put(SourceContract.ID, source.getId());
          values.put(SourceContract.STATUS, 1);
          db.insert(SourceContract.TABLE, null, values);
        });

    // Multiple Elements
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.updateSourceStatus(List.of("1", "2", "3"), Source.Status.IGNORED)));

    // Single Element
    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.updateSourceStatus(List.of("1", "2"), Source.Status.IGNORED)));
  }

  @Test
  public void updateSourceAttributedTriggers_baseline_equal() throws JSONException {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);

    List<Source> sourceList = new ArrayList<>();
    sourceList.add(
        SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum().setId("1").build());
    sourceList.add(
        SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum().setId("2").build());
    sourceList.add(
        SourceFixture.getValidFullSourceBuilderWithFlexEventReportValueSum().setId("3").build());
    mDatastoreManager.runInTransaction(
        (dao) -> {
          for (Source source : sourceList) {
            dao.insertSource(source);
          }
        });
    List<EventReport> eventReportList = new ArrayList<>();
    eventReportList.add(
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(3L)
            .setTriggerValue(5L)
            .setReportTime(10000L)
            .build());
    Source originalSource = sourceList.get(0);
    insertAttributedTrigger(originalSource.getTriggerSpecs(), eventReportList.get(0));
    Optional<Source> newSource =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao -> measurementDao.getSource(originalSource.getId()));
    assertTrue(newSource.isPresent());

    assertNotEquals(newSource.get(), originalSource);
    newSource.get().buildTriggerSpecs();
    assertEquals(0, newSource.get().getTriggerSpecs().getAttributedTriggers().size());

    mDatastoreManager.runInTransaction(
        measurementDao ->
            measurementDao.updateSourceAttributedTriggers(
                originalSource.getId(), originalSource.attributedTriggersToJsonFlexApi()));

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          assertEquals(
              originalSource.attributedTriggersToJsonFlexApi(),
              measurementDao.getSource(originalSource.getId()).getEventAttributionStatus());
        });
  }

  @Test
  public void testGetMatchingActiveSources() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    String enrollmentId = "enrollment-id";
    Uri appDestination = Uri.parse("android-app://com.example.abc");
    Uri webDestination = WebUtil.validUri("https://example.test");
    Uri webDestinationWithSubdomain = WebUtil.validUri("https://xyz.example.test");
    Source sApp1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("2")
            .setEventTime(10)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("3")
            .setEventTime(20)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp4 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("4")
            .setEventTime(30)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb5 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("5")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb6 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("6")
            .setEventTime(10)
            .setExpiryTime(50)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb7 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("7")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();

    List<Source> sources = Arrays.asList(sApp1, sApp2, sApp3, sApp4, sWeb5, sWeb6, sAppWeb7);
    sources.forEach(source -> insertInDb(db, source));

    Function<Trigger, List<Source>> runFunc =
        trigger -> {
          List<Source> result =
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getMatchingActiveSources(trigger))
                  .orElseThrow();
          result.sort(Comparator.comparing(Source::getId));
          return result;
        };

    // Trigger Time > sApp1's eventTime and < sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time < sApp3's eventTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
    // Expected: Match with sApp1, sApp2, sAppWeb7
    Trigger trigger1MatchSource1And2 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
    assertEquals(3, result1.size());
    assertEquals(sApp1.getId(), result1.get(0).getId());
    assertEquals(sApp2.getId(), result1.get(1).getId());
    assertEquals(sAppWeb7.getId(), result1.get(2).getId());

    // Trigger Time > sApp1's eventTime and = sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time = sApp3's eventTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's eventTime and = sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3
    Trigger trigger2MatchSource127 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(20)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();

    List<Source> result2 = runFunc.apply(trigger2MatchSource127);
    assertEquals(2, result2.size());
    assertEquals(sApp2.getId(), result2.get(0).getId());
    assertEquals(sApp3.getId(), result2.get(1).getId());

    // Trigger Time > sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3
    Trigger trigger3MatchSource237 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();

    List<Source> result3 = runFunc.apply(trigger3MatchSource237);
    assertEquals(2, result3.size());
    assertEquals(sApp2.getId(), result3.get(0).getId());
    assertEquals(sApp3.getId(), result3.get(1).getId());

    // Trigger Time > sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
    // Trigger Time > sApp4's eventTime and < sApp4's expiryTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3 and sApp4
    Trigger trigger4MatchSource1And2And3 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(31)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();

    List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
    assertEquals(3, result4.size());
    assertEquals(sApp2.getId(), result4.get(0).getId());
    assertEquals(sApp3.getId(), result4.get(1).getId());
    assertEquals(sApp4.getId(), result4.get(2).getId());

    // sApp1, sApp2, sApp3, sApp4 don't have web destination
    // Trigger Time > sWeb5's eventTime and < sApp5's expiryTime
    // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
    // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
    // Expected: Match with sApp5, sApp6, sAppWeb7
    Trigger trigger5MatchSource567 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestination)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    List<Source> result5 = runFunc.apply(trigger5MatchSource567);
    assertEquals(3, result1.size());
    assertEquals(sWeb5.getId(), result5.get(0).getId());
    assertEquals(sWeb6.getId(), result5.get(1).getId());
    assertEquals(sAppWeb7.getId(), result5.get(2).getId());

    // sApp1, sApp2, sApp3, sApp4 don't have web destination
    // Trigger Time > sWeb5's expiryTime
    // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
    // Trigger Time > sWeb7's expiryTime
    // Expected: Match with sApp6 only
    Trigger trigger6MatchSource67 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestinationWithSubdomain)
            .setDestinationType(EventSurfaceType.WEB)
            .build();

    List<Source> result6 = runFunc.apply(trigger6MatchSource67);
    assertEquals(1, result6.size());
    assertEquals(sWeb6.getId(), result6.get(0).getId());

    // Trigger with different subdomain than source
    // Expected: No Match found
    Trigger triggerDifferentRegistrationOrigin =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain-different.example.test"))
            .build();

    List<Source> result7 = runFunc.apply(triggerDifferentRegistrationOrigin);
    assertTrue(result7.isEmpty());

    // Trigger with different domain than source
    // Expected: No Match found
    Trigger triggerDifferentDomainOrigin =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example-different.test"))
            .build();

    List<Source> result8 = runFunc.apply(triggerDifferentDomainOrigin);
    assertTrue(result8.isEmpty());

    // Trigger with different port than source
    // Expected: No Match found
    Trigger triggerDifferentPort =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test:8083"))
            .build();

    List<Source> result9 = runFunc.apply(triggerDifferentPort);
    assertTrue(result9.isEmpty());

    // Enrollment id for trigger and source not same
    // Registration Origin for trigger and source same
    // Expected: Match with sApp1, sApp2, sAppWeb7
    Trigger triggerDifferentEnrollmentSameRegistration =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId("different-enrollment-id")
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result10 = runFunc.apply(triggerDifferentEnrollmentSameRegistration);
    assertEquals(3, result10.size());
    assertEquals(sApp1.getId(), result10.get(0).getId());
    assertEquals(sApp2.getId(), result10.get(1).getId());
    assertEquals(sAppWeb7.getId(), result10.get(2).getId());
  }

  @Test
  public void testGetMatchingActiveSources_multipleDestinations() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    String enrollmentId = "enrollment-id";
    Uri webDestination1 = WebUtil.validUri("https://example.test");
    Uri webDestination1WithSubdomain = WebUtil.validUri("https://xyz.example.test");
    Uri webDestination2 = WebUtil.validUri("https://example2.test");
    Source sWeb1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination1))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("2")
            .setEventTime(10)
            .setExpiryTime(50)
            .setWebDestinations(List.of(webDestination1, webDestination2))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("3")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination1))
            .setEnrollmentId(enrollmentId)
            .build();

    List<Source> sources = Arrays.asList(sWeb1, sWeb2, sAppWeb3);
    sources.forEach(source -> insertInDb(db, source));

    Function<Trigger, List<Source>> getMatchingSources =
        trigger -> {
          List<Source> result =
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getMatchingActiveSources(trigger))
                  .orElseThrow();
          result.sort(Comparator.comparing(Source::getId));
          return result;
        };

    Trigger triggerMatchSourceWeb2 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestination1WithSubdomain)
            .setDestinationType(EventSurfaceType.WEB)
            .build();

    List<Source> result = getMatchingSources.apply(triggerMatchSourceWeb2);
    assertEquals(1, result.size());
    assertEquals(sWeb2.getId(), result.get(0).getId());
  }

  @Test
  public void testGetMatchingActiveSources_attributionScopeEnabled_populateScopes() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      // S0: attribution scopes -> [], destinations -> [D1, D2]
      Source source0 =
          insertSourceForAttributionScope(
              /* attributionScopes= */ null,
              /* attributionScopeLimit= */ null,
              /* maxEventStates= */ null,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION),
              List.of(APP_ONE_DESTINATION));
      // S1: attribution scopes -> ["1", "2"], destinations -> [D1]
      Source source1 =
          insertSourceForAttributionScope(
              List.of("1", "2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 1,
              null,
              List.of(APP_ONE_DESTINATION));
      // S2: attribution scopes -> ["2", "3"], destinations -> [D2]
      Source source2 =
          insertSourceForAttributionScope(
              List.of("2", "3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_ONE_DESTINATION),
              null);

      Trigger trigger0 =
          TriggerFixture.getValidTriggerBuilder()
              .setTriggerTime(SOURCE_EVENT_TIME + 3)
              .setAttributionDestination(APP_ONE_DESTINATION)
              .setDestinationType(EventSurfaceType.APP)
              .build();
      List<Source> matchingSources0 = getMatchingSources(trigger0);
      assertThat(matchingSources0.size()).isEqualTo(2);
      List<String> matchingSourceIds0 =
          matchingSources0.stream().map(Source::getId).collect(Collectors.toList());
      List<List<String>> matchingSourceAttributionScopes0 =
          matchingSources0.stream().map(Source::getAttributionScopes).collect(Collectors.toList());
      assertThat(matchingSourceIds0).containsExactly(source0.getId(), source1.getId());
      // Source attribution scopes won't be populated if trigger doesn't have attribution scope.
      assertThat(matchingSourceAttributionScopes0).containsExactly(null, null);

      Trigger trigger1 =
          TriggerFixture.getValidTriggerBuilder()
              .setTriggerTime(SOURCE_EVENT_TIME + 4)
              .setAttributionScopesString("1")
              .setAttributionDestination(APP_ONE_DESTINATION)
              .setDestinationType(EventSurfaceType.APP)
              .build();
      List<Source> matchingSources1 = getMatchingSources(trigger1);
      List<String> matchingSourceIds1 =
          matchingSources1.stream().map(Source::getId).collect(Collectors.toList());
      List<List<String>> matchingSourceAttributionScopes1 =
          matchingSources1.stream().map(Source::getAttributionScopes).collect(Collectors.toList());
      assertThat(matchingSourceIds1).containsExactly(source0.getId(), source1.getId());
      assertThat(matchingSourceAttributionScopes1)
          .containsExactly(source0.getAttributionScopes(), source1.getAttributionScopes());

      Trigger trigger2 =
          TriggerFixture.getValidTriggerBuilder()
              .setTriggerTime(SOURCE_EVENT_TIME + 5)
              .setAttributionScopesString("2")
              .setAttributionDestination(WEB_ONE_DESTINATION)
              .setDestinationType(EventSurfaceType.WEB)
              .build();
      List<Source> matchingSources2 = getMatchingSources(trigger2);
      List<String> matchingSourceIds2 =
          matchingSources2.stream().map(Source::getId).collect(Collectors.toList());
      List<List<String>> matchingSourceAttributionScopes2 =
          matchingSources2.stream().map(Source::getAttributionScopes).collect(Collectors.toList());
      assertThat(matchingSourceIds2).containsExactly(source0.getId(), source2.getId());
      assertThat(matchingSourceAttributionScopes2)
          .containsExactly(source0.getAttributionScopes(), source2.getAttributionScopes());
    }
  }

  @Test
  public void testGetAttributionScopesForRegistration() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      insertSourceForAttributionScope(
          List.of("1"),
          ATTRIBUTION_SCOPE_LIMIT,
          MAX_EVENT_STATES,
          SOURCE_EVENT_TIME,
          List.of(WEB_ONE_DESTINATION),
          List.of(APP_ONE_DESTINATION),
          SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
          SourceFixture.ValidSourceParams.REGISTRATION_ID,
          Source.SourceType.NAVIGATION,
          Source.Status.ACTIVE);
      insertSourceForAttributionScope(
          List.of("2"),
          ATTRIBUTION_SCOPE_LIMIT,
          MAX_EVENT_STATES,
          SOURCE_EVENT_TIME,
          List.of(WEB_ONE_DESTINATION),
          List.of(APP_ONE_DESTINATION),
          SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
          SourceFixture.ValidSourceParams.REGISTRATION_ID,
          Source.SourceType.NAVIGATION,
          Source.Status.ACTIVE);
      insertSourceForAttributionScope(
          List.of("3"),
          ATTRIBUTION_SCOPE_LIMIT,
          MAX_EVENT_STATES,
          SOURCE_EVENT_TIME,
          List.of(WEB_ONE_DESTINATION),
          List.of(APP_ONE_DESTINATION),
          REGISTRATION_ORIGIN_2,
          REGISTRATION_ID2,
          Source.SourceType.NAVIGATION,
          Source.Status.ACTIVE);
      // Ignored source, attribution scopes ignored.
      insertSourceForAttributionScope(
          List.of("4"),
          ATTRIBUTION_SCOPE_LIMIT,
          MAX_EVENT_STATES,
          SOURCE_EVENT_TIME,
          List.of(WEB_ONE_DESTINATION),
          List.of(APP_ONE_DESTINATION),
          SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
          SourceFixture.ValidSourceParams.REGISTRATION_ID,
          Source.SourceType.NAVIGATION,
          Source.Status.IGNORED);

      // Execution
      mDatastoreManager.runInTransaction(
          (dao) -> {
            assertThat(
                    dao.getAttributionScopesForRegistration(
                            SourceFixture.ValidSourceParams.REGISTRATION_ID,
                            SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN.toString())
                        .get())
                .containsExactly("1", "2");
            assertThat(
                    dao.getAttributionScopesForRegistration(
                            REGISTRATION_ID2, REGISTRATION_ORIGIN_2.toString())
                        .get())
                .containsExactly("3");
            assertThat(
                    dao.getAttributionScopesForRegistration(
                            SourceFixture.ValidSourceParams.REGISTRATION_ID,
                            SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN.toString())
                        .get())
                .containsExactly("1", "2");
            assertThat(
                    dao.getAttributionScopesForRegistration(
                            REGISTRATION_ID2, REGISTRATION_ORIGIN_2.toString())
                        .get())
                .containsExactly("3");
            assertThat(
                    dao.getAttributionScopesForRegistration(
                            SourceFixture.ValidSourceParams.REGISTRATION_ID,
                            REGISTRATION_ORIGIN_2.toString())
                        .isEmpty())
                .isTrue();
          });
    }
  }

  @Test
  public void testGetMatchingActiveDelayedSources() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    String enrollmentId = "enrollment-id";
    Uri appDestination = Uri.parse("android-app://com.example.abc");
    Uri webDestination = WebUtil.validUri("https://example.test");
    Source sApp1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("2")
            .setEventTime(140)
            .setExpiryTime(200)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("3")
            .setEventTime(20)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp4 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("4")
            .setEventTime(16)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb5 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("5")
            .setEventTime(13)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb6 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("6")
            .setEventTime(14)
            .setExpiryTime(50)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb7 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("7")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb8 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("8")
            .setEventTime(15)
            .setExpiryTime(25)
            .setAppDestinations(List.of(appDestination))
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();

    List<Source> sources =
        Arrays.asList(sApp1, sApp2, sApp3, sApp4, sWeb5, sWeb6, sAppWeb7, sAppWeb8);
    sources.forEach(source -> insertInDb(db, source));

    Function<Trigger, Optional<Source>> runFunc =
        trigger -> {
          Optional<Source> result =
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao ->
                          measurementDao.getNearestDelayedMatchingActiveSource(trigger))
                  .orElseThrow();
          return result;
        };

    // sApp1's eventTime <= Trigger Time
    // Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW > sApp2's eventTime
    // Trigger Time < sApp3's eventTime <= Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW
    // Trigger Time < sApp4's eventTime <= Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW
    // sWeb5 and sWeb6 don't have app destination
    // sAppWeb7's eventTime <= Trigger Time
    // Trigger Time < sAppWeb8's eventTime <= Trigger Time +
    // MAX_DELAYED_SOURCE_REGISTRATION_WINDOW
    // Expected: Match with sAppWeb8
    Trigger trigger1MatchSource8 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    Optional<Source> result1 = runFunc.apply(trigger1MatchSource8);
    assertEquals(sAppWeb8.getId(), result1.get().getId());

    // sApp1's eventTime <= Trigger Time
    // Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW > sApp2's eventTime
    // Trigger Time < sApp3's eventTime <= Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW
    // Trigger Time < sApp4's eventTime <= Trigger Time + MAX_DELAYED_SOURCE_REGISTRATION_WINDOW
    // sWeb5 and sWeb6 don't have app destination
    // sAppWeb7's eventTime <= Trigger Time
    // sAppWeb8's eventTime <= Trigger Time
    // Expected: Match with sApp4
    Trigger trigger2MatchSource4 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(15)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    Optional<Source> result2 = runFunc.apply(trigger2MatchSource4);
    assertEquals(sApp4.getId(), result2.get().getId());

    // sApp1's eventTime <= Trigger Time
    // sApp2's eventTime <= Trigger Time
    // sApp3's eventTime <= Trigger Time
    // sApp4's eventTime <= Trigger Time
    // sWeb5 and sWeb6 don't have app destination
    // sAppWeb7's eventTime <= Trigger Time
    // sAppWeb8's eventTime <= Trigger Time
    // Expected: no match
    Trigger trigger3NoMatchingSource =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(150)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    Optional<Source> result3 = runFunc.apply(trigger3NoMatchingSource);
    assertFalse(result3.isPresent());
  }

  @Test
  public void testInsertAggregateEncryptionKey() {
    String keyId = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
    String publicKey = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
    long expiry = 1653620135831L;
    Uri aggregationOrigin = WebUtil.validUri("https://a.test");

    mDatastoreManager.runInTransaction(
        (dao) ->
            dao.insertAggregateEncryptionKey(
                new AggregateEncryptionKey.Builder()
                    .setKeyId(keyId)
                    .setPublicKey(publicKey)
                    .setExpiry(expiry)
                    .setAggregationCoordinatorOrigin(aggregationOrigin)
                    .build()));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                null,
                null,
                null,
                null,
                null,
                null)) {
      assertTrue(cursor.moveToNext());
      AggregateEncryptionKey aggregateEncryptionKey =
          SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor);
      assertNotNull(aggregateEncryptionKey);
      assertNotNull(aggregateEncryptionKey.getId());
      assertEquals(keyId, aggregateEncryptionKey.getKeyId());
      assertEquals(publicKey, aggregateEncryptionKey.getPublicKey());
      assertEquals(expiry, aggregateEncryptionKey.getExpiry());
      assertEquals(aggregationOrigin, aggregateEncryptionKey.getAggregationCoordinatorOrigin());
    }
  }

  @Test
  public void testInsertAggregateReport() {
    AggregateReport validAggregateReport = AggregateReportFixture.getValidAggregateReport();
    mDatastoreManager.runInTransaction((dao) -> dao.insertAggregateReport(validAggregateReport));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(MeasurementTables.AggregateReport.TABLE, null, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      AggregateReport aggregateReport = SqliteObjectMapper.constructAggregateReport(cursor);
      assertNotNull(aggregateReport);
      assertNotNull(aggregateReport.getId());
      assertEquals(validAggregateReport, aggregateReport);
    }
  }

  @Test
  public void testInsertAggregateReport_withNullSourceRegistrationTime() {
    AggregateReport.Builder builder = AggregateReportFixture.getValidAggregateReportBuilder();
    builder.setSourceRegistrationTime(null);
    AggregateReport aggregateReportWithNullSourceRegistrationTime = builder.build();
    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAggregateReport(aggregateReportWithNullSourceRegistrationTime));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(MeasurementTables.AggregateReport.TABLE, null, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      AggregateReport aggregateReport = SqliteObjectMapper.constructAggregateReport(cursor);
      assertNotNull(aggregateReport);
      assertNotNull(aggregateReport.getId());
      assertEquals(aggregateReportWithNullSourceRegistrationTime, aggregateReport);
    }
  }

  @Test
  public void testInsertAggregateReport_withTriggerTime() {
    AggregateReport.Builder builder = AggregateReportFixture.getValidAggregateReportBuilder();
    builder.setTriggerTime(1L);
    AggregateReport aggregateReportWithTriggerTime = builder.build();
    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAggregateReport(aggregateReportWithTriggerTime));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(MeasurementTables.AggregateReport.TABLE, null, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      AggregateReport aggregateReport = SqliteObjectMapper.constructAggregateReport(cursor);
      assertNotNull(aggregateReport);
      assertNotNull(aggregateReport.getId());
      assertNotNull(aggregateReport.getTriggerTime());
      assertEquals(aggregateReportWithTriggerTime, aggregateReport);
    }
  }

  @Test
  public void testDeleteAllMeasurementDataWithEmptyList() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    Source source = SourceFixture.getMinimalValidSourceBuilder().setId("S1").build();
    ContentValues sourceValue = new ContentValues();
    sourceValue.put("_id", source.getId());
    db.insert(SourceContract.TABLE, null, sourceValue);

    Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
    ContentValues triggerValue = new ContentValues();
    triggerValue.put("_id", trigger.getId());
    db.insert(TriggerContract.TABLE, null, triggerValue);

    EventReport eventReport = new EventReport.Builder().setId("E1").build();
    ContentValues eventReportValue = new ContentValues();
    eventReportValue.put("_id", eventReport.getId());
    db.insert(EventReportContract.TABLE, null, eventReportValue);

    AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
    ContentValues aggregateReportValue = new ContentValues();
    aggregateReportValue.put("_id", aggregateReport.getId());
    db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

    ContentValues rateLimitValue = new ContentValues();
    rateLimitValue.put(AttributionContract.ID, "ARL1");
    rateLimitValue.put(AttributionContract.SOURCE_SITE, "sourceSite");
    rateLimitValue.put(AttributionContract.SOURCE_ORIGIN, "sourceOrigin");
    rateLimitValue.put(AttributionContract.DESTINATION_SITE, "destinationSite");
    rateLimitValue.put(AttributionContract.TRIGGER_TIME, 5L);
    rateLimitValue.put(AttributionContract.REGISTRANT, "registrant");
    rateLimitValue.put(AttributionContract.ENROLLMENT_ID, "enrollmentId");

    db.insert(AttributionContract.TABLE, null, rateLimitValue);

    AggregateEncryptionKey key =
        new AggregateEncryptionKey.Builder()
            .setId("K1")
            .setKeyId("keyId")
            .setPublicKey("publicKey")
            .setExpiry(1)
            .setAggregationCoordinatorOrigin(Uri.parse("https://1.test"))
            .build();
    ContentValues keyValues = new ContentValues();
    keyValues.put("_id", key.getId());

    mDatastoreManager.runInTransaction(
        (dao) -> dao.deleteAllMeasurementData(Collections.emptyList()));

    for (String table : ALL_MSMT_TABLES) {
      assertThat(
              db.query(
                      /* table */ table,
                      /* columns */ null,
                      /* selection */ null,
                      /* selectionArgs */ null,
                      /* groupBy */ null,
                      /* having */ null,
                      /* orderedBy */ null)
                  .getCount())
          .isEqualTo(0);
    }
  }

  @Test
  public void testDeleteAllMeasurementDataWithNonEmptyList() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    Source source = SourceFixture.getMinimalValidSourceBuilder().setId("S1").build();
    ContentValues sourceValue = new ContentValues();
    sourceValue.put("_id", source.getId());
    db.insert(SourceContract.TABLE, null, sourceValue);

    Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
    ContentValues triggerValue = new ContentValues();
    triggerValue.put("_id", trigger.getId());
    db.insert(TriggerContract.TABLE, null, triggerValue);

    EventReport eventReport = new EventReport.Builder().setId("E1").build();
    ContentValues eventReportValue = new ContentValues();
    eventReportValue.put("_id", eventReport.getId());
    db.insert(EventReportContract.TABLE, null, eventReportValue);

    AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
    ContentValues aggregateReportValue = new ContentValues();
    aggregateReportValue.put("_id", aggregateReport.getId());
    db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

    ContentValues rateLimitValue = new ContentValues();
    rateLimitValue.put(AttributionContract.ID, "ARL1");
    rateLimitValue.put(AttributionContract.SOURCE_SITE, "sourceSite");
    rateLimitValue.put(AttributionContract.SOURCE_ORIGIN, "sourceOrigin");
    rateLimitValue.put(AttributionContract.DESTINATION_SITE, "destinationSite");
    rateLimitValue.put(AttributionContract.TRIGGER_TIME, 5L);
    rateLimitValue.put(AttributionContract.REGISTRANT, "registrant");
    rateLimitValue.put(AttributionContract.ENROLLMENT_ID, "enrollmentId");
    db.insert(AttributionContract.TABLE, null, rateLimitValue);

    AggregateEncryptionKey key =
        new AggregateEncryptionKey.Builder()
            .setId("K1")
            .setKeyId("keyId")
            .setPublicKey("publicKey")
            .setExpiry(1)
            .setAggregationCoordinatorOrigin(Uri.parse("https://1.test"))
            .build();
    ContentValues keyValues = new ContentValues();
    keyValues.put("_id", key.getId());

    List<String> excludedTables = List.of(SourceContract.TABLE);

    mDatastoreManager.runInTransaction((dao) -> dao.deleteAllMeasurementData(excludedTables));

    for (String table : ALL_MSMT_TABLES) {
      if (!excludedTables.contains(table)) {
        assertThat(
                db.query(
                        /* table */ table,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderedBy */ null)
                    .getCount())
            .isEqualTo(0);
      } else {
        assertThat(
                db.query(
                        /* table */ table,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderedBy */ null)
                    .getCount())
            .isNotEqualTo(0);
      }
    }
  }

  /** Test that the variable ALL_MSMT_TABLES actually has all the measurement related tables. */
  @Test
  public void testAllMsmtTables() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Cursor cursor =
        db.query(
            "sqlite_master",
            /* columns */ null,
            /* selection */ "type = ? AND name like ?",
            /* selectionArgs*/ new String[] {"table", MSMT_TABLE_PREFIX + "%"},
            /* groupBy */ null,
            /* having */ null,
            /* orderBy */ null);

    List<String> tableNames = new ArrayList<>();
    while (cursor.moveToNext()) {
      String tableName = cursor.getString(cursor.getColumnIndex("name"));
      tableNames.add(tableName);
    }
    assertThat(tableNames.size()).isEqualTo(ALL_MSMT_TABLES.length);
    for (String tableName : tableNames) {
      assertThat(ALL_MSMT_TABLES).asList().contains(tableName);
    }
  }

  @Test
  public void insertAttributionRateLimit() {
    // Setup
    Source source = SourceFixture.getValidSource();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
            .build();
    Attribution attribution =
        new Attribution.Builder()
            .setScope(Attribution.Scope.AGGREGATE)
            .setEnrollmentId(source.getEnrollmentId())
            .setDestinationOrigin(source.getWebDestinations().get(0).toString())
            .setDestinationSite(source.getAppDestinations().get(0).toString())
            .setSourceOrigin(source.getPublisher().toString())
            .setSourceSite(source.getPublisher().toString())
            .setRegistrant(source.getRegistrant().toString())
            .setTriggerTime(trigger.getTriggerTime())
            .setRegistrationOrigin(trigger.getRegistrationOrigin())
            .setReportId(UUID.randomUUID().toString())
            .build();

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.insertAttribution(attribution);
        });

    // Assertion
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(AttributionContract.TABLE, null, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      assertEquals(
          attribution.getScope(), cursor.getInt(cursor.getColumnIndex(AttributionContract.SCOPE)));
      assertEquals(
          attribution.getEnrollmentId(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.ENROLLMENT_ID)));
      assertEquals(
          attribution.getDestinationOrigin(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.DESTINATION_ORIGIN)));
      assertEquals(
          attribution.getDestinationSite(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.DESTINATION_SITE)));
      assertEquals(
          attribution.getSourceOrigin(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.SOURCE_ORIGIN)));
      assertEquals(
          attribution.getSourceSite(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.SOURCE_SITE)));
      assertEquals(
          attribution.getRegistrant(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.REGISTRANT)));
      assertEquals(
          attribution.getTriggerTime(),
          cursor.getLong(cursor.getColumnIndex(AttributionContract.TRIGGER_TIME)));
      assertEquals(
          attribution.getRegistrationOrigin(),
          Uri.parse(
              cursor.getString(cursor.getColumnIndex(AttributionContract.REGISTRATION_ORIGIN))));
      assertEquals(
          attribution.getReportId(),
          cursor.getString(cursor.getColumnIndex(AttributionContract.REPORT_ID)));
    }
  }

  @Test
  public void getAttributionsPerRateLimitWindow_atTimeWindowScoped_countsAttribution() {
    // Setup
    Source source = SourceFixture.getValidSource();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
            .build();

    Attribution eventAttribution =
        getAttributionBuilder(source, trigger).setScope(Attribution.Scope.EVENT).build();

    Attribution aggregateAttribution =
        getAttributionBuilder(source, trigger).setScope(Attribution.Scope.AGGREGATE).build();

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.insertAttribution(eventAttribution);
          dao.insertAttribution(aggregateAttribution);
        });

    // Assertion
    AtomicLong eventAttributionsCount = new AtomicLong();
    AtomicLong aggregateAttributionsCount = new AtomicLong();
    mDatastoreManager.runInTransaction(
        (dao) -> {
          eventAttributionsCount.set(
              dao.getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger));
          aggregateAttributionsCount.set(
              dao.getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger));
        });

    assertEquals(1L, eventAttributionsCount.get());
    assertEquals(1L, aggregateAttributionsCount.get());
  }

  @Test
  public void getAttributionsPerRateLimitWindow_beyondTimeWindowScoped_countsAttribution() {
    // Setup
    Source source = SourceFixture.getValidSource();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
            .build();

    Attribution eventAttribution =
        getAttributionBuilder(source, trigger)
            .setTriggerTime(trigger.getTriggerTime() - MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS)
            .setScope(Attribution.Scope.EVENT)
            .build();

    Attribution aggregateAttribution =
        getAttributionBuilder(source, trigger)
            .setTriggerTime(trigger.getTriggerTime() - MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS)
            .setScope(Attribution.Scope.AGGREGATE)
            .build();

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.insertAttribution(eventAttribution);
          dao.insertAttribution(aggregateAttribution);
        });

    // Assertion
    AtomicLong eventAttributionsCount = new AtomicLong();
    AtomicLong aggregateAttributionsCount = new AtomicLong();
    mDatastoreManager.runInTransaction(
        (dao) -> {
          eventAttributionsCount.set(
              dao.getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger));
          aggregateAttributionsCount.set(
              dao.getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger));
        });

    assertEquals(0L, eventAttributionsCount.get());
    assertEquals(0L, aggregateAttributionsCount.get());
  }

  @Test
  public void testTransactionRollbackForRuntimeException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            mDatastoreManager.runInTransaction(
                (dao) -> {
                  dao.insertSource(SourceFixture.getValidSource());
                  // build() call throws IllegalArgumentException
                  Trigger trigger = new Trigger.Builder().build();
                  dao.insertTrigger(trigger);
                }));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    // There should be no insertions
    assertEquals(0, db.query(SourceContract.TABLE, null, null, null, null, null, null).getCount());
    assertEquals(0, db.query(TriggerContract.TABLE, null, null, null, null, null, null).getCount());
  }

  @Test
  public void testDeleteEventReportAndAttribution() throws JSONException {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Source s1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("S1")
            .build();
    Trigger t1 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T1")
            .build();
    Trigger t2 =
        TriggerFixture.getValidTriggerBuilder()
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setId("T2")
            .build();
    EventReport e11 = createEventReportForSourceAndTrigger("E11", s1, t1);
    EventReport e12 = createEventReportForSourceAndTrigger("E12", s1, t2);

    Attribution aggregateAttribution11 =
        createAttribution(
            "ATT11_aggregate", Attribution.Scope.AGGREGATE, s1.getId(), t1.getId(), "E11");
    Attribution aggregateAttribution12 =
        createAttribution(
            "ATT12_aggregate", Attribution.Scope.AGGREGATE, s1.getId(), t2.getId(), "E12");
    Attribution eventAttribution11 =
        createAttribution("ATT11_event", Attribution.Scope.EVENT, s1.getId(), t1.getId(), "E11");
    Attribution eventAttribution12 =
        createAttribution("ATT12_event", Attribution.Scope.EVENT, s1.getId(), t2.getId(), "E12");

    insertSource(s1, s1.getId());
    AbstractDbIntegrationTest.insertToDb(t1, db);
    AbstractDbIntegrationTest.insertToDb(t2, db);
    AbstractDbIntegrationTest.insertToDb(e11, db);
    AbstractDbIntegrationTest.insertToDb(e12, db);
    AbstractDbIntegrationTest.insertToDb(aggregateAttribution11, db);
    AbstractDbIntegrationTest.insertToDb(aggregateAttribution12, db);
    AbstractDbIntegrationTest.insertToDb(eventAttribution11, db);
    AbstractDbIntegrationTest.insertToDb(eventAttribution12, db);

    // Assert attributions present
    assertNotNull(getAttribution("ATT11_aggregate", db));
    assertNotNull(getAttribution("ATT12_aggregate", db));
    assertNotNull(getAttribution("ATT11_event", db));
    assertNotNull(getAttribution("ATT12_event", db));

    mDatastoreManager.runInTransaction(
        measurementDao -> {
          // Assert sources and triggers present
          assertNotNull(measurementDao.getSource("S1"));
          assertNotNull(measurementDao.getTrigger("T1"));
          assertNotNull(measurementDao.getTrigger("T2"));

          // Validate presence of event reports
          measurementDao.getEventReport("E11");
          measurementDao.getEventReport("E12");

          // Deletion
          measurementDao.deleteEventReportAndAttribution(e11);

          // Validate event report deletion
          assertThrows(
              DatastoreException.class,
              () -> {
                measurementDao.getEventReport("E11");
              });
          assertNotNull(measurementDao.getEventReport("E12"));

          // Validate sources and triggers present
          assertNotNull(measurementDao.getSource("S1"));
          assertNotNull(measurementDao.getTrigger("T1"));
          assertNotNull(measurementDao.getTrigger("T2"));
        });

    // Validate attribution deletion
    assertNotNull(getAttribution("ATT11_aggregate", db));
    assertNotNull(getAttribution("ATT12_aggregate", db));
    assertNull(getAttribution("ATT11_event", db));
    assertNotNull(getAttribution("ATT12_event", db));
  }

  @Test
  public void testDeleteDebugReport() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    DebugReport debugReport = createDebugReport();

    ContentValues values = new ContentValues();
    values.put(DebugReportContract.ID, debugReport.getId());
    values.put(DebugReportContract.TYPE, debugReport.getType());
    values.put(DebugReportContract.BODY, debugReport.getBody().toString());
    values.put(DebugReportContract.ENROLLMENT_ID, debugReport.getEnrollmentId());
    values.put(
        DebugReportContract.REGISTRATION_ORIGIN, debugReport.getRegistrationOrigin().toString());
    db.insert(DebugReportContract.TABLE, null, values);

    long count = DatabaseUtils.queryNumEntries(db, DebugReportContract.TABLE, /* selection */ null);
    assertEquals(1, count);

    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao -> measurementDao.deleteDebugReport(debugReport.getId())));

    count = DatabaseUtils.queryNumEntries(db, DebugReportContract.TABLE, /* selection */ null);
    assertEquals(0, count);
  }

  @Test
  public void testDeleteDebugReports_byRegistrantAndRange() {
    final String registrantMatching = "foo";
    final long insertionTimeWithinRange = 1701206853050L;
    final long insertionTimeNotWithinRange = 1701206853000L;
    DebugReport debugReportMatchingRegistrantAndWithinRange =
        createDebugReport(
            /* id= */ "1", buildRegistrant(registrantMatching), insertionTimeWithinRange);

    DebugReport debugReportNotMatchingRegistrantAndWithinRange =
        createDebugReport(/* id= */ "2", buildRegistrant("bar"), insertionTimeWithinRange);

    DebugReport debugReportMatchingRegistrantAndNotWithinRange =
        createDebugReport(
            /* id= */ "3", buildRegistrant(registrantMatching), insertionTimeNotWithinRange);

    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.insertDebugReport(debugReportMatchingRegistrantAndWithinRange);
          dao.insertDebugReport(debugReportNotMatchingRegistrantAndWithinRange);
          dao.insertDebugReport(debugReportMatchingRegistrantAndNotWithinRange);
        });

    mDatastoreManager.runInTransaction(
        (dao) ->
            dao.deleteDebugReports(
                buildRegistrant(registrantMatching),
                /* start= */ Instant.ofEpochMilli(insertionTimeWithinRange - 1),
                /* end= */ Instant.ofEpochMilli(insertionTimeWithinRange + 1)));

    Set<String> ids = new HashSet<>();
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(DebugReportContract.TABLE, null, null, null, null, null, null)) {
      while (cursor.moveToNext()) {
        ids.add(cursor.getString(cursor.getColumnIndexOrThrow(DebugReportContract.ID)));
      }
    }
    assertEquals(2, ids.size());
    assertTrue(ids.contains("2"));
    assertTrue(ids.contains("3"));
  }

  @Test
  public void testGetDebugReportIds() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    DebugReport debugReport = createDebugReport();

    ContentValues values = new ContentValues();
    values.put(DebugReportContract.ID, debugReport.getId());
    values.put(DebugReportContract.TYPE, debugReport.getType());
    values.put(DebugReportContract.BODY, debugReport.getBody().toString());
    values.put(DebugReportContract.ENROLLMENT_ID, debugReport.getEnrollmentId());
    values.put(
        DebugReportContract.REGISTRATION_ORIGIN, debugReport.getRegistrationOrigin().toString());
    db.insert(DebugReportContract.TABLE, null, values);

    long count = DatabaseUtils.queryNumEntries(db, DebugReportContract.TABLE, /* selection */ null);
    assertEquals(1, count);

    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                assertEquals(List.of(debugReport.getId()), measurementDao.getDebugReportIds())));
  }

  @Test
  public void testGetDebugReportIdsWithRetryLimit() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      DebugReport debugReport = createDebugReport();

      ContentValues values = new ContentValues();
      values.put(DebugReportContract.ID, debugReport.getId());
      values.put(DebugReportContract.TYPE, debugReport.getType());
      values.put(DebugReportContract.BODY, debugReport.getBody().toString());
      values.put(DebugReportContract.ENROLLMENT_ID, debugReport.getEnrollmentId());
      values.put(
          DebugReportContract.REGISTRATION_ORIGIN, debugReport.getRegistrationOrigin().toString());
      db.insert(DebugReportContract.TABLE, null, values);

      long count =
          DatabaseUtils.queryNumEntries(db, DebugReportContract.TABLE, /* selection */ null);
      assertEquals(1, count);

      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao ->
                  assertEquals(List.of(debugReport.getId()), measurementDao.getDebugReportIds())));
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao -> {
                // Adds records to KeyValueData table for Retry Count.
                measurementDao.incrementAndGetReportingRetryCount(
                    debugReport.getId(), DataType.DEBUG_REPORT_RETRY_COUNT);
                assertTrue(measurementDao.getDebugReportIds().isEmpty());
              }));
    }
  }

  @Test
  public void testDeleteExpiredRecordsForAsyncRegistrations() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    List<AsyncRegistration> asyncRegistrationList = new ArrayList<>();
    int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;

    // Will be deleted by request time
    asyncRegistrationList.add(
        new AsyncRegistration.Builder()
            .setId("1")
            .setOsDestination(Uri.parse("android-app://installed-app-destination"))
            .setRegistrant(INSTALLED_REGISTRANT)
            .setTopOrigin(INSTALLED_REGISTRANT)
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(1)
            .setRetryCount(retryLimit - 1L)
            .setRegistrationId(UUID.randomUUID().toString())
            .build());

    // Will be deleted by either request time or retry limit
    asyncRegistrationList.add(
        new AsyncRegistration.Builder()
            .setId("2")
            .setOsDestination(Uri.parse("android-app://installed-app-destination"))
            .setRegistrant(INSTALLED_REGISTRANT)
            .setTopOrigin(INSTALLED_REGISTRANT)
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(1)
            .setRetryCount(retryLimit)
            .setRegistrationId(UUID.randomUUID().toString())
            .build());

    // Will not be deleted
    asyncRegistrationList.add(
        new AsyncRegistration.Builder()
            .setId("3")
            .setOsDestination(Uri.parse("android-app://not-installed-app-destination"))
            .setRegistrant(INSTALLED_REGISTRANT)
            .setTopOrigin(INSTALLED_REGISTRANT)
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(Long.MAX_VALUE)
            .setRetryCount(retryLimit - 1L)
            .setRegistrationId(UUID.randomUUID().toString())
            .build());

    // Will be deleted due to retry limit
    asyncRegistrationList.add(
        new AsyncRegistration.Builder()
            .setId("4")
            .setOsDestination(Uri.parse("android-app://not-installed-app-destination"))
            .setRegistrant(INSTALLED_REGISTRANT)
            .setTopOrigin(INSTALLED_REGISTRANT)
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(Long.MAX_VALUE)
            .setRetryCount(retryLimit)
            .setRegistrationId(UUID.randomUUID().toString())
            .build());

    asyncRegistrationList.forEach(
        asyncRegistration -> {
          ContentValues values = new ContentValues();
          values.put(AsyncRegistrationContract.ID, asyncRegistration.getId());
          values.put(
              AsyncRegistrationContract.REGISTRANT, asyncRegistration.getRegistrant().toString());
          values.put(
              AsyncRegistrationContract.TOP_ORIGIN, asyncRegistration.getTopOrigin().toString());
          values.put(
              AsyncRegistrationContract.OS_DESTINATION,
              asyncRegistration.getOsDestination().toString());
          values.put(
              AsyncRegistrationContract.AD_ID_PERMISSION, asyncRegistration.getDebugKeyAllowed());
          values.put(AsyncRegistrationContract.TYPE, asyncRegistration.getType().toString());
          values.put(AsyncRegistrationContract.REQUEST_TIME, asyncRegistration.getRequestTime());
          values.put(AsyncRegistrationContract.RETRY_COUNT, asyncRegistration.getRetryCount());
          values.put(
              AsyncRegistrationContract.REGISTRATION_ID, asyncRegistration.getRegistrationId());
          db.insert(AsyncRegistrationContract.TABLE, /* nullColumnHack */ null, values);
        });

    long count =
        DatabaseUtils.queryNumEntries(db, AsyncRegistrationContract.TABLE, /* selection */ null);
    assertEquals(4, count);

    long earliestValidInsertion = System.currentTimeMillis() - 2;

    assertTrue(
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.deleteExpiredRecords(
                    earliestValidInsertion,
                    retryLimit,
                    /* earliestValidAppReportInsertion */ null,
                    /* earliestValidAggregateDebugReportInsertion */ 0)));

    count =
        DatabaseUtils.queryNumEntries(db, AsyncRegistrationContract.TABLE, /* selection */ null);
    assertEquals(1, count);

    Cursor cursor =
        db.query(
            AsyncRegistrationContract.TABLE,
            /* columns */ null,
            /* selection */ null,
            /* selectionArgs */ null,
            /* groupBy */ null,
            /* having */ null,
            /* orderBy */ null);

    Set<String> ids = new HashSet<>(Arrays.asList("3"));
    List<AsyncRegistration> asyncRegistrations = new ArrayList<>();
    while (cursor.moveToNext()) {
      AsyncRegistration asyncRegistration = SqliteObjectMapper.constructAsyncRegistration(cursor);
      asyncRegistrations.add(asyncRegistration);
    }
    for (AsyncRegistration asyncRegistration : asyncRegistrations) {
      assertTrue(ids.contains(asyncRegistration.getId()));
    }
  }

  @Test
  public void deleteExpiredRecords_registrationRedirectCount() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    List<Pair<String, String>> regIdCounts =
        List.of(
            new Pair<>("reg1", "1"),
            new Pair<>("reg2", "2"),
            new Pair<>("reg3", "3"),
            new Pair<>("reg4", "4"));
    for (Pair<String, String> regIdCount : regIdCounts) {
      ContentValues contentValues = new ContentValues();
      contentValues.put(
          KeyValueDataContract.DATA_TYPE,
          KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString());
      contentValues.put(KeyValueDataContract.KEY, regIdCount.first);
      contentValues.put(KeyValueDataContract.VALUE, regIdCount.second);
      db.insert(KeyValueDataContract.TABLE, null, contentValues);
    }
    AsyncRegistration asyncRegistration1 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setRegistrationId("reg1")
            .setRequestTime(System.currentTimeMillis() + 60000) // Avoid deletion
            .build();
    AsyncRegistration asyncRegistration2 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setRegistrationId("reg2")
            .setRequestTime(System.currentTimeMillis() + 60000) // Avoid deletion
            .build();
    List<AsyncRegistration> asyncRegistrations = List.of(asyncRegistration1, asyncRegistration2);
    asyncRegistrations.forEach(
        asyncRegistration ->
            mDatastoreManager.runInTransaction(
                dao -> dao.insertAsyncRegistration(asyncRegistration)));

    long earliestValidInsertion = System.currentTimeMillis() - 60000;
    int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;
    assertTrue(
        mDatastoreManager.runInTransaction(
            (dao) ->
                dao.deleteExpiredRecords(
                    earliestValidInsertion,
                    retryLimit,
                    /* earliestValidAggregateDebugReportInsertion */ null,
                    /* earliestValidAggregateDebugReportInsertion */ 0)));

    Cursor cursor =
        db.query(
            KeyValueDataContract.TABLE, null, null, null, null, null, KeyValueDataContract.KEY);
    assertEquals(2, cursor.getCount());
    cursor.moveToNext();
    assertEquals(
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString(),
        cursor.getString(cursor.getColumnIndex(KeyValueDataContract.DATA_TYPE)));
    assertEquals("reg1", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.KEY)));
    assertEquals("1", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.VALUE)));
    cursor.moveToNext();
    assertEquals(
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString(),
        cursor.getString(cursor.getColumnIndex(KeyValueDataContract.DATA_TYPE)));
    assertEquals("reg2", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.KEY)));
    assertEquals("2", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.VALUE)));
    cursor.close();
  }

  @Test
  public void deleteExpiredRecords_skipDeliveredEventReportsOutsideWindow() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues sourceValid = new ContentValues();
    sourceValid.put(SourceContract.ID, "s1");
    sourceValid.put(SourceContract.EVENT_TIME, System.currentTimeMillis());

    ContentValues sourceExpired = new ContentValues();
    sourceExpired.put(SourceContract.ID, "s2");
    sourceExpired.put(SourceContract.EVENT_TIME, System.currentTimeMillis() - DAYS.toMillis(20));

    ContentValues triggerValid = new ContentValues();
    triggerValid.put(TriggerContract.ID, "t1");
    triggerValid.put(TriggerContract.TRIGGER_TIME, System.currentTimeMillis());

    ContentValues triggerExpired = new ContentValues();
    triggerExpired.put(TriggerContract.ID, "t2");
    triggerExpired.put(
        TriggerContract.TRIGGER_TIME, System.currentTimeMillis() - DAYS.toMillis(20));

    db.insert(SourceContract.TABLE, null, sourceValid);
    db.insert(SourceContract.TABLE, null, sourceExpired);
    db.insert(TriggerContract.TABLE, null, triggerValid);
    db.insert(TriggerContract.TABLE, null, triggerExpired);

    ContentValues eventReport_NotDelivered_WithinWindow = new ContentValues();
    eventReport_NotDelivered_WithinWindow.put(EventReportContract.ID, "e1");
    eventReport_NotDelivered_WithinWindow.put(
        EventReportContract.REPORT_TIME, System.currentTimeMillis());
    eventReport_NotDelivered_WithinWindow.put(
        EventReportContract.STATUS, EventReport.Status.PENDING);
    eventReport_NotDelivered_WithinWindow.put(
        EventReportContract.SOURCE_ID, sourceValid.getAsString(SourceContract.ID));
    eventReport_NotDelivered_WithinWindow.put(
        EventReportContract.TRIGGER_ID, triggerValid.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_NotDelivered_WithinWindow);

    ContentValues eventReport_Delivered_WithinWindow = new ContentValues();
    eventReport_Delivered_WithinWindow.put(EventReportContract.ID, "e2");
    eventReport_Delivered_WithinWindow.put(
        EventReportContract.REPORT_TIME, System.currentTimeMillis());
    eventReport_Delivered_WithinWindow.put(
        EventReportContract.STATUS, EventReport.Status.DELIVERED);
    eventReport_Delivered_WithinWindow.put(
        EventReportContract.SOURCE_ID, sourceValid.getAsString(SourceContract.ID));
    eventReport_Delivered_WithinWindow.put(
        EventReportContract.TRIGGER_ID, triggerValid.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_Delivered_WithinWindow);

    ContentValues eventReport_Delivered_OutsideWindow = new ContentValues();
    eventReport_Delivered_OutsideWindow.put(EventReportContract.ID, "e3");
    eventReport_Delivered_OutsideWindow.put(
        EventReportContract.REPORT_TIME, System.currentTimeMillis() - DAYS.toMillis(20));
    eventReport_Delivered_OutsideWindow.put(
        EventReportContract.STATUS, EventReport.Status.DELIVERED);
    eventReport_Delivered_OutsideWindow.put(
        EventReportContract.SOURCE_ID, sourceValid.getAsString(SourceContract.ID));
    eventReport_Delivered_OutsideWindow.put(
        EventReportContract.TRIGGER_ID, triggerValid.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_Delivered_OutsideWindow);

    ContentValues eventReport_NotDelivered_OutsideWindow = new ContentValues();
    eventReport_NotDelivered_OutsideWindow.put(EventReportContract.ID, "e4");
    eventReport_NotDelivered_OutsideWindow.put(
        EventReportContract.REPORT_TIME, System.currentTimeMillis() - DAYS.toMillis(20));
    eventReport_NotDelivered_OutsideWindow.put(
        EventReportContract.STATUS, EventReport.Status.PENDING);
    eventReport_NotDelivered_OutsideWindow.put(
        EventReportContract.SOURCE_ID, sourceValid.getAsString(SourceContract.ID));
    eventReport_NotDelivered_OutsideWindow.put(
        EventReportContract.TRIGGER_ID, triggerValid.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_NotDelivered_OutsideWindow);

    ContentValues eventReport_expiredSource = new ContentValues();
    eventReport_expiredSource.put(EventReportContract.ID, "e5");
    eventReport_expiredSource.put(EventReportContract.REPORT_TIME, System.currentTimeMillis());
    eventReport_expiredSource.put(EventReportContract.STATUS, EventReport.Status.PENDING);
    eventReport_expiredSource.put(
        EventReportContract.SOURCE_ID, sourceExpired.getAsString(SourceContract.ID));
    eventReport_expiredSource.put(
        EventReportContract.TRIGGER_ID, triggerValid.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_expiredSource);

    ContentValues eventReport_expiredTrigger = new ContentValues();
    eventReport_expiredTrigger.put(EventReportContract.ID, "e6");
    eventReport_expiredTrigger.put(EventReportContract.REPORT_TIME, System.currentTimeMillis());
    eventReport_expiredTrigger.put(EventReportContract.STATUS, EventReport.Status.PENDING);
    eventReport_expiredTrigger.put(
        EventReportContract.SOURCE_ID, sourceValid.getAsString(SourceContract.ID));
    eventReport_expiredTrigger.put(
        EventReportContract.TRIGGER_ID, triggerExpired.getAsString(TriggerContract.ID));
    db.insert(EventReportContract.TABLE, null, eventReport_expiredTrigger);

    long earliestValidInsertion = System.currentTimeMillis() - DAYS.toMillis(10);
    int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;
    mDatastoreManager.runInTransaction(
        measurementDao ->
            measurementDao.deleteExpiredRecords(
                earliestValidInsertion,
                retryLimit,
                /* earliestValidAppReportInsertion */ null,
                /* earliestValidAggregateDebugReportInsertion */ 0));

    List<ContentValues> deletedReports =
        List.of(eventReport_expiredSource, eventReport_expiredTrigger);

    List<ContentValues> notDeletedReports =
        List.of(
            eventReport_Delivered_OutsideWindow,
            eventReport_Delivered_WithinWindow,
            eventReport_NotDelivered_OutsideWindow,
            eventReport_NotDelivered_WithinWindow);

    assertEquals(
        notDeletedReports.size(),
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT("
                + EventReportContract.ID
                + ") FROM "
                + EventReportContract.TABLE
                + " WHERE "
                + EventReportContract.ID
                + " IN ("
                + notDeletedReports.stream()
                    .map(
                        (eR) -> {
                          return DatabaseUtils.sqlEscapeString(
                              eR.getAsString(EventReportContract.ID));
                        })
                    .collect(Collectors.joining(","))
                + ")",
            null));

    assertEquals(
        0,
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT("
                + EventReportContract.ID
                + ") FROM "
                + EventReportContract.TABLE
                + " WHERE "
                + EventReportContract.ID
                + " IN ("
                + deletedReports.stream()
                    .map(
                        (eR) -> {
                          return DatabaseUtils.sqlEscapeString(
                              eR.getAsString(EventReportContract.ID));
                        })
                    .collect(Collectors.joining(","))
                + ")",
            null));
  }

  @Test
  public void deleteExpiredRecords_VerboseDebugReportsWhileLimitingRetries() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getReadableDatabase();

      DebugReport debugReport1 =
          new DebugReport.Builder()
              .setId("reportId1")
              .setType("trigger-event-deduplicated")
              .setBody(
                  " {\n"
                      + "      \"attribution_destination\":"
                      + " \"https://destination.example\",\n"
                      + "      \"source_event_id\": \"45623\"\n"
                      + "    }")
              .setEnrollmentId("1")
              .setRegistrationOrigin(REGISTRATION_ORIGIN)
              .build();

      DebugReport debugReport2 =
          new DebugReport.Builder()
              .setId("reportId2")
              .setType("trigger-event-deduplicated")
              .setBody(
                  " {\n"
                      + "      \"attribution_destination\":"
                      + " \"https://destination.example\",\n"
                      + "      \"source_event_id\": \"45623\"\n"
                      + "    }")
              .setEnrollmentId("1")
              .setRegistrationOrigin(REGISTRATION_ORIGIN)
              .build();

      mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport1));
      mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport2));

      mDatastoreManager.runInTransaction(
          dao ->
              dao.deleteExpiredRecords(
                  /* earliestValidInsertion */ 0,
                  /* registrationRetryLimit */ 0,
                  /* earliestValidAppReportInsertion */ null,
                  /* earliestValidAggregateDebugReportInsertion */ 0));
      assertEquals(
          2,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT(" + DebugReportContract.ID + ") FROM " + DebugReportContract.TABLE,
              null));
      // Increment Attempt Record 1
      mDatastoreManager.runInTransaction(
          (dao) ->
              dao.incrementAndGetReportingRetryCount(
                  debugReport1.getId(), DataType.DEBUG_REPORT_RETRY_COUNT));
      // Delete Expired (Record 1)
      mDatastoreManager.runInTransaction(
          dao ->
              dao.deleteExpiredRecords(
                  /* earliestValidInsertion */ 0,
                  /* registrationRetryLimit */ 0,
                  /* earliestValidAppReportInsertion */ null,
                  /* earliestValidAggregateDebugReportInsertion */ 0));

      // Assert Record 2 remains.
      assertEquals(
          1,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + DebugReportContract.ID
                  + ") FROM "
                  + DebugReportContract.TABLE
                  + " WHERE "
                  + DebugReportContract.ID
                  + " = ?",
              new String[] {debugReport2.getId()}));

      // Assert Record 1 Removed
      assertEquals(
          0,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + DebugReportContract.ID
                  + ") FROM "
                  + DebugReportContract.TABLE
                  + " WHERE "
                  + DebugReportContract.ID
                  + " = ?",
              new String[] {debugReport1.getId()}));
    }
  }

  @Test
  public void deleteExpiredRecords_VerboseDebugReportsWhileNotLimitingRetries() {
    // Mocking that the retry Limiting Disable, but has limit number,
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(false).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getReadableDatabase();

      DebugReport debugReport1 =
          new DebugReport.Builder()
              .setId("reportId1")
              .setType("trigger-event-deduplicated")
              .setBody(
                  " {\n"
                      + "      \"attribution_destination\":"
                      + " \"https://destination.example\",\n"
                      + "      \"source_event_id\": \"45623\"\n"
                      + "    }")
              .setEnrollmentId("1")
              .setRegistrationOrigin(REGISTRATION_ORIGIN)
              .setInsertionTime(System.currentTimeMillis() + 60000L)
              .build();

      DebugReport debugReport2 =
          new DebugReport.Builder()
              .setId("reportId2")
              .setType("trigger-event-deduplicated")
              .setBody(
                  " {\n"
                      + "      \"attribution_destination\":"
                      + " \"https://destination.example\",\n"
                      + "      \"source_event_id\": \"45623\"\n"
                      + "    }")
              .setEnrollmentId("1")
              .setRegistrationOrigin(REGISTRATION_ORIGIN)
              .setInsertionTime(System.currentTimeMillis() - 60000L)
              .build();
      // Insert
      mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport1));
      mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport2));

      // Increment Attempt
      mDatastoreManager.runInTransaction(
          (dao) ->
              dao.incrementAndGetReportingRetryCount(
                  debugReport1.getId(), DataType.DEBUG_REPORT_RETRY_COUNT));
      // Delete Expired
      long earliestValidInsertion = System.currentTimeMillis();

      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao ->
                  measurementDao.deleteExpiredRecords(
                      earliestValidInsertion,
                      /* earliestValidAppReportInsertion */ 0,
                      /* earliestValidAppReportInsertion */ null,
                      /* earliestValidAggregateDebugReportInsertion */ 0)));

      // Assert Record 1 remains because not expired and Retry Limiting Off.
      assertEquals(
          1,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + DebugReportContract.ID
                  + ") FROM "
                  + DebugReportContract.TABLE
                  + " WHERE "
                  + DebugReportContract.ID
                  + " = ?",
              new String[] {debugReport1.getId()}));

      // Assert Record 2 Removed because expired.
      assertEquals(
          0,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + DebugReportContract.ID
                  + ") FROM "
                  + DebugReportContract.TABLE
                  + " WHERE "
                  + DebugReportContract.ID
                  + " = ?",
              new String[] {debugReport2.getId()}));
    }
  }

  @Test
  public void deleteExpiredRecords_RetryKeyValueData() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    // Non-stale join record
    DebugReport debugReport = createDebugReport();
    mDatastoreManager.runInTransaction((dao) -> dao.insertDebugReport(debugReport));

    // Should Remain
    ContentValues nonStaleValues = new ContentValues();
    nonStaleValues.put(
        KeyValueDataContract.DATA_TYPE, DataType.DEBUG_REPORT_RETRY_COUNT.toString());
    nonStaleValues.put(KeyValueDataContract.KEY, debugReport.getId());
    nonStaleValues.put(KeyValueDataContract.VALUE, "1");
    db.insert(KeyValueDataContract.TABLE, null, nonStaleValues);

    // Should Delete
    ContentValues staleValues = new ContentValues();
    staleValues.put(KeyValueDataContract.DATA_TYPE, DataType.DEBUG_REPORT_RETRY_COUNT.toString());
    staleValues.put(KeyValueDataContract.KEY, "stale-key");
    staleValues.put(KeyValueDataContract.VALUE, "1");
    db.insert(KeyValueDataContract.TABLE, null, staleValues);

    mDatastoreManager.runInTransaction(
        dao ->
            dao.deleteExpiredRecords(
                /* earliestValidInsertion */ 0,
                /* registrationRetryLimit */ 0,
                /* earliestValidAppReportInsertion */ null,
                /* earliestValidAggregateDebugReportInsertion */ 0));

    // Assert Non-Stale record remains.
    assertEquals(
        1,
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT("
                + KeyValueDataContract.KEY
                + ") FROM "
                + KeyValueDataContract.TABLE
                + " WHERE "
                + KeyValueDataContract.KEY
                + " = ?",
            new String[] {nonStaleValues.getAsString(KeyValueDataContract.KEY)}));

    // Assert Stale Record Removed
    assertEquals(
        0,
        DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT("
                + KeyValueDataContract.KEY
                + ") FROM "
                + KeyValueDataContract.TABLE
                + " WHERE "
                + KeyValueDataContract.KEY
                + " = ?",
            new String[] {staleValues.getAsString(KeyValueDataContract.KEY)}));
  }

  @Test
  public void deleteExpiredRecords_reinstallAttributionEnabled_deletesExpiredAppInstallHistory() {
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(true).when(mockFlags).getMeasurementEnableReinstallReattribution();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getReadableDatabase();
      long now = System.currentTimeMillis();
      mDatastoreManager.runInTransaction(
          (dao) ->
              dao.insertOrUpdateAppReportHistory(
                  INSTALLED_PACKAGE,
                  REGISTRATION_ORIGIN,
                  /* lastReportDeliveredTimestamp= */ now - TimeUnit.DAYS.toMillis(1)));
      mDatastoreManager.runInTransaction(
          (dao) ->
              dao.insertOrUpdateAppReportHistory(
                  INSTALLED_PACKAGE,
                  REGISTRATION_ORIGIN_2,
                  /* lastReportDeliveredTimestamp= */ now - TimeUnit.DAYS.toMillis(3)));

      long earliestValidInsertion = now - TimeUnit.DAYS.toMillis(2);
      assertTrue(
          mDatastoreManager.runInTransaction(
              measurementDao ->
                  measurementDao.deleteExpiredRecords(
                      earliestValidInsertion,
                      /* registrationRetryLimit= */ 0,
                      earliestValidInsertion,
                      /* earliestValidAggregateDebugReportInsertion */ 0)));
      // Assert Record 1 remains because not expired and Retry Limiting Off.
      assertEquals(
          1,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + AppReportHistoryContract.REGISTRATION_ORIGIN
                  + ") FROM "
                  + AppReportHistoryContract.TABLE
                  + " WHERE "
                  + AppReportHistoryContract.REGISTRATION_ORIGIN
                  + " = ?",
              new String[] {REGISTRATION_ORIGIN.toString()}));

      // Assert Record 2 Removed because expired.
      assertEquals(
          0,
          DatabaseUtils.longForQuery(
              db,
              "SELECT COUNT("
                  + AppReportHistoryContract.REGISTRATION_ORIGIN
                  + ") FROM "
                  + AppReportHistoryContract.TABLE
                  + " WHERE "
                  + AppReportHistoryContract.REGISTRATION_ORIGIN
                  + " = ?",
              new String[] {REGISTRATION_ORIGIN_2.toString()}));
    }
  }

  @Test
  public void deleteExpiredRecords_withAdrBudgetTrackerRecords_deletesOlderRecords() {
    // Setup
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(true).when(mockFlags).getMeasurementEnableAggregateDebugReporting();
      long currentTime = System.currentTimeMillis();

      // record1 - 6 hour old report record
      AggregateDebugReportRecord record1 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.abc"),
                  APP_ONE_PUBLISHER,
                  REGISTRATION_ORIGIN,
                  1)
              .build();
      // record2 - 1 day 1 millisecond old report record
      AggregateDebugReportRecord record2 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - DAYS.toMillis(1) - 1,
                  Uri.parse("android-app://com.example.def"),
                  APP_TWO_PUBLISHER,
                  REGISTRATION_ORIGIN_2,
                  10)
              .build();
      // record3 - 10 days old report record
      AggregateDebugReportRecord record3 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - DAYS.toMillis(10),
                  Uri.parse("android-app://com.example.ghi"),
                  APP_ONE_PUBLISHER,
                  REGISTRATION_ORIGIN_3,
                  100)
              .build();
      // record4 - 1 minute old report record
      AggregateDebugReportRecord record4 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.MINUTES.toMillis(1),
                  Uri.parse("android-app://com.example.abc"),
                  APP_TWO_SOURCES,
                  REGISTRATION_ORIGIN_4,
                  1000)
              .build();
      List<AggregateDebugReportRecord> records = Arrays.asList(record1, record2, record3, record4);
      records.forEach(
          record ->
              mDatastoreManager.runInTransaction(
                  dao -> dao.insertAggregateDebugReportRecord(record)));

      // Execution - delete records older than 1 day - should retain record1 & record4
      mDatastoreManager.runInTransaction(
          dao ->
              dao.deleteExpiredRecords(
                  /* earliestValidInsertion */ 0L,
                  /* registrationRetryLimit */ 0,
                  /* earliestValidAppReportInsertion */ null,
                  currentTime - DAYS.toMillis(1)));

      // Assertion
      assertThat(
              DatabaseUtils.queryNumEntries(
                  MeasurementDbHelper.getInstance().getReadableDatabase(),
                  AggregatableDebugReportBudgetTrackerContract.TABLE))
          .isEqualTo(2);
      try (Cursor recordCursor =
          MeasurementDbHelper.getInstance()
              .getReadableDatabase()
              .query(
                  AggregatableDebugReportBudgetTrackerContract.TABLE,
                  new String[] {AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN},
                  null,
                  null,
                  null,
                  null,
                  AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)) {
        assertThat(recordCursor.getCount()).isEqualTo(2);
        assertThat(recordCursor.moveToNext()).isTrue();
        assertThat(
                recordCursor.getString(
                    recordCursor.getColumnIndex(
                        AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)))
            .isEqualTo(REGISTRATION_ORIGIN.toString());
        assertThat(recordCursor.moveToNext()).isTrue();
        assertThat(
                recordCursor.getString(
                    recordCursor.getColumnIndex(
                        AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)))
            .isEqualTo(REGISTRATION_ORIGIN_4.toString());
      }
    }
  }

  @Test
  public void deleteExpiredRecords_withAdrRecords_deletesRecordsDueToSourceTriggerDeletion() {
    // Setup
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(true).when(mockFlags).getMeasurementEnableAggregateDebugReporting();
      long currentTime = System.currentTimeMillis();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

      // 30 days old
      Source s1 =
          SourceFixture.getValidSourceBuilder()
              .setId("S1")
              .setEventTime(currentTime - DAYS.toMillis(30))
              .build();
      Trigger t1 =
          TriggerFixture.getValidTriggerBuilder()
              .setId("T1")
              .setTriggerTime(currentTime - DAYS.toMillis(30))
              .build();
      AbstractDbIntegrationTest.insertToDb(s1, db);
      AbstractDbIntegrationTest.insertToDb(t1, db);

      // 1 day old
      Source s2 =
          SourceFixture.getValidSourceBuilder()
              .setId("S2")
              .setEventTime(currentTime - DAYS.toMillis(1))
              .build();
      Trigger t2 =
          TriggerFixture.getValidTriggerBuilder()
              .setId("T2")
              .setTriggerTime(currentTime - DAYS.toMillis(1))
              .build();
      AbstractDbIntegrationTest.insertToDb(s2, db);
      AbstractDbIntegrationTest.insertToDb(t2, db);

      // deleted - record1 - 6 hour old report record but source and trigger are 30 days old
      AggregateDebugReportRecord record1 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.abc"),
                  APP_ONE_PUBLISHER,
                  REGISTRATION_ORIGIN,
                  1)
              .setSourceId(s1.getId())
              .setTriggerId(t1.getId())
              .build();
      // deleted - record2 - 6 hour old report record but source is 30 days old
      AggregateDebugReportRecord record2 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.def"),
                  APP_TWO_PUBLISHER,
                  REGISTRATION_ORIGIN_2,
                  10)
              .setSourceId(s1.getId())
              .setTriggerId(null)
              .build();
      // deleted - record3 - 6 hour old report record but trigger is 30 days old
      AggregateDebugReportRecord record3 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.ghi"),
                  APP_ONE_PUBLISHER,
                  REGISTRATION_ORIGIN_3,
                  100)
              .setSourceId(null)
              .setTriggerId(t1.getId())
              .build();
      // retained - record4 - 6 hour old report record and source is 1 day old
      AggregateDebugReportRecord record4 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.jkl"),
                  APP_TWO_SOURCES,
                  REGISTRATION_ORIGIN_4,
                  1000)
              .setSourceId(s2.getId())
              .setTriggerId(null)
              .build();
      // retained - record4 - 6 hour old report record and trigger is 1 day old
      AggregateDebugReportRecord record5 =
          new AggregateDebugReportRecord.Builder(
                  currentTime - TimeUnit.HOURS.toMillis(6),
                  Uri.parse("android-app://com.example.mno"),
                  APP_TWO_SOURCES,
                  REGISTRATION_ORIGIN_5,
                  1000)
              .setSourceId(null)
              .setTriggerId(t2.getId())
              .build();
      List<AggregateDebugReportRecord> records =
          Arrays.asList(record1, record2, record3, record4, record5);
      records.forEach(
          record ->
              mDatastoreManager.runInTransaction(
                  dao -> dao.insertAggregateDebugReportRecord(record)));

      // Execution - delete records older than 10 days - should retain record4 & record5
      mDatastoreManager.runInTransaction(
          dao ->
              dao.deleteExpiredRecords(
                  /* earliestValidInsertion */ currentTime - DAYS.toMillis(10),
                  /* registrationRetryLimit */ 0,
                  /* earliestValidAppReportInsertion */ null,
                  /* earliestValidAggregateDebugReportInsertion */ 0));

      // Assertion
      assertThat(
              DatabaseUtils.queryNumEntries(
                  MeasurementDbHelper.getInstance().getReadableDatabase(),
                  AggregatableDebugReportBudgetTrackerContract.TABLE))
          .isEqualTo(2);
      try (Cursor recordCursor =
          MeasurementDbHelper.getInstance()
              .getReadableDatabase()
              .query(
                  AggregatableDebugReportBudgetTrackerContract.TABLE,
                  new String[] {AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN},
                  null,
                  null,
                  null,
                  null,
                  AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)) {
        assertThat(recordCursor.getCount()).isEqualTo(2);
        assertThat(recordCursor.moveToNext()).isTrue();
        assertThat(
                recordCursor.getString(
                    recordCursor.getColumnIndex(
                        AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)))
            .isEqualTo(REGISTRATION_ORIGIN_4.toString());
        assertThat(recordCursor.moveToNext()).isTrue();
        assertThat(
                recordCursor.getString(
                    recordCursor.getColumnIndex(
                        AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)))
            .isEqualTo(REGISTRATION_ORIGIN_5.toString());
      }
    }
  }

  @Test
  public void getRegistrationRedirectCount_keyMissing() {
    Optional<KeyValueData> optKeyValueData =
        mDatastoreManager.runInTransactionWithResult(
            (dao) ->
                dao.getKeyValueData("missing_random_id", DataType.REGISTRATION_REDIRECT_COUNT));
    assertTrue(optKeyValueData.isPresent());
    KeyValueData keyValueData = optKeyValueData.get();
    assertEquals(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT, keyValueData.getDataType());
    assertEquals("missing_random_id", keyValueData.getKey());
    assertNull(keyValueData.getValue());
    assertEquals(1, keyValueData.getRegistrationRedirectCount());
  }

  @Test
  public void getRegistrationRedirectCount_keyExists() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(
        KeyValueDataContract.DATA_TYPE,
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString());
    contentValues.put(KeyValueDataContract.KEY, "random_id");
    contentValues.put(KeyValueDataContract.VALUE, "2");
    db.insert(KeyValueDataContract.TABLE, null, contentValues);
    Optional<KeyValueData> optKeyValueData =
        mDatastoreManager.runInTransactionWithResult(
            (dao) ->
                dao.getKeyValueData(
                    "random_id", KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT));
    assertTrue(optKeyValueData.isPresent());
    KeyValueData keyValueData = optKeyValueData.get();
    assertEquals(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT, keyValueData.getDataType());
    assertEquals("random_id", keyValueData.getKey());
    assertEquals("2", keyValueData.getValue());
    assertEquals(2, keyValueData.getRegistrationRedirectCount());
  }

  @Test
  public void updateRegistrationRedirectCount_keyMissing() {
    KeyValueData keyValueData =
        new KeyValueData.Builder()
            .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
            .setKey("key_1")
            .setValue("4")
            .build();
    mDatastoreManager.runInTransaction((dao) -> dao.insertOrUpdateKeyValueData(keyValueData));
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Cursor cursor = db.query(KeyValueDataContract.TABLE, null, null, null, null, null, null);
    assertEquals(1, cursor.getCount());
    cursor.moveToNext();
    assertEquals(
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString(),
        cursor.getString(cursor.getColumnIndex(KeyValueDataContract.DATA_TYPE)));
    assertEquals("key_1", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.KEY)));
    assertEquals("4", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.VALUE)));
    cursor.close();
  }

  @Test
  public void updateRegistrationRedirectCount_keyExists() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(
        KeyValueDataContract.DATA_TYPE,
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString());
    contentValues.put(KeyValueDataContract.KEY, "key_1");
    contentValues.put(KeyValueDataContract.VALUE, "2");
    db.insert(KeyValueDataContract.TABLE, null, contentValues);

    KeyValueData keyValueData =
        new KeyValueData.Builder()
            .setDataType(KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT)
            .setKey("key_1")
            .setValue("4")
            .build();
    mDatastoreManager.runInTransaction((dao) -> dao.insertOrUpdateKeyValueData(keyValueData));

    Cursor cursor = db.query(KeyValueDataContract.TABLE, null, null, null, null, null, null);
    assertEquals(1, cursor.getCount());
    cursor.moveToNext();
    assertEquals(
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString(),
        cursor.getString(cursor.getColumnIndex(KeyValueDataContract.DATA_TYPE)));
    assertEquals("key_1", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.KEY)));
    assertEquals("4", cursor.getString(cursor.getColumnIndex(KeyValueDataContract.VALUE)));
    cursor.close();
  }

  @Test
  public void keyValueDataTable_PrimaryKeyConstraint() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues contentValues1 = new ContentValues();
    contentValues1.put(
        KeyValueDataContract.DATA_TYPE,
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString());
    contentValues1.put(KeyValueDataContract.KEY, "key_1");
    contentValues1.put(KeyValueDataContract.VALUE, "2");

    assertNotEquals(-1, db.insert(KeyValueDataContract.TABLE, null, contentValues1));

    // Should fail because we are using <DataType, Key> as primary key
    assertEquals(-1, db.insert(KeyValueDataContract.TABLE, null, contentValues1));

    ContentValues contentValues2 = new ContentValues();
    contentValues2.put(
        KeyValueDataContract.DATA_TYPE,
        KeyValueData.DataType.REGISTRATION_REDIRECT_COUNT.toString());
    contentValues2.put(KeyValueDataContract.KEY, "key_2");
    contentValues2.put(KeyValueDataContract.VALUE, "2");

    assertNotEquals(-1, db.insert(KeyValueDataContract.TABLE, null, contentValues2));
  }

  @Test
  public void
      fetchSourceIdsForLowestPriorityDest_appDestEmptyExclusions_delLowPriorityDestination() {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableSourceDestinationLimitPriority();
      long baseEventTime = System.currentTimeMillis();
      long commonExpiryTime = baseEventTime + DAYS.toMillis(30);
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
              .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
              .setEventTime(baseEventTime)
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(10)
              .build(),
          "s11");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
              .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(1))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(20)
              .build(),
          "s21");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app3")))
              .setWebDestinations(List.of(Uri.parse("https://web3.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(2))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(30)
              .build(),
          "s31");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
              .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(3))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(10)
              .build(),
          "s12");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
              .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(4))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(20)
              .build(),
          "s22");

      // Execute
      // com.example.app1 has the least priority of all as 10
      mDatastoreManager.runInTransaction(
          (dao) -> {
            Pair<Long, List<String>> destinationPriorityAndSourcesTodelete =
                dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                    SourceFixture.ValidSourceParams.PUBLISHER,
                    EventSurfaceType.APP,
                    SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                    Collections.emptyList(),
                    EventSurfaceType.APP,
                    baseEventTime + DAYS.toMillis(10) // request time
                    );

            assertEquals(10L, (long) destinationPriorityAndSourcesTodelete.first);
            assertEquals(
                Sets.newSet("s11", "s12"),
                new HashSet<>(destinationPriorityAndSourcesTodelete.second));
          });
    }
  }

  @Test
  public void fetchSourceIdsForLowPriorityDest_webDestEmptyExclusions_delLowPriorityDestinations() {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableSourceDestinationLimitPriority();
      long baseEventTime = System.currentTimeMillis();
      long commonExpiryTime = baseEventTime + DAYS.toMillis(30);
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
              .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
              .setEventTime(baseEventTime)
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(10)
              .build(),
          "s11");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
              .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(1))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(20)
              .build(),
          "s21");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app3")))
              .setWebDestinations(List.of(Uri.parse("https://web3.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(2))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(30)
              .build(),
          "s31");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
              .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(3))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(40)
              .build(),
          "s12");
      insertSource(
          createSourceBuilder()
              .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
              .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
              .setEventTime(baseEventTime + DAYS.toMillis(4))
              .setExpiryTime(commonExpiryTime)
              .setDestinationLimitPriority(20)
              .build(),
          "s22");

      // Execute
      // web1.example.com has priority 10 with s11 and priority 40 with s12, the higher one will
      // be considered, i.e. 40. web2.example.com" has priority as 20 through both s21 and s22,
      // its associated sources will be deleted instead.
      mDatastoreManager.runInTransaction(
          (dao) -> {
            Pair<Long, List<String>> destinationPriorityAndSourcesToDelete =
                dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                    SourceFixture.ValidSourceParams.PUBLISHER,
                    EventSurfaceType.APP,
                    SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                    Collections.emptyList(),
                    EventSurfaceType.WEB,
                    baseEventTime + DAYS.toMillis(10) // request time
                    );

            assertEquals(20L, (long) destinationPriorityAndSourcesToDelete.first);
            assertEquals(
                Sets.newSet("s21", "s22"),
                new HashSet<>(destinationPriorityAndSourcesToDelete.second));
          });
    }
  }

  @Test
  public void fetchSourceIdsForLowPriorityDest_appDestEmptyExclusions_delLruDestinations() {
    // Setup
    long baseEventTime = System.currentTimeMillis();
    insert5SourcesForLruDestDeletion(baseEventTime);

    // Execute
    // com.example.app3 would be the least recently used destination, as 1 & 2 are used
    // afterwards
    mDatastoreManager.runInTransaction(
        (dao) -> {
          Pair<Long, List<String>> destinationPriorityAndSourcesToDelete =
              dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                  SourceFixture.ValidSourceParams.PUBLISHER,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  Collections.emptyList(),
                  EventSurfaceType.APP,
                  baseEventTime + DAYS.toMillis(10) // request time
                  );

          assertEquals(0, (long) destinationPriorityAndSourcesToDelete.first);
          assertEquals(List.of("s31"), destinationPriorityAndSourcesToDelete.second);
        });
  }

  @Test
  public void fetchSourceIdsForLowPriorityDest_appDestWebPubEmptyExclusions_delLowPrioDestSource() {
    // Setup
    long baseEventTime = System.currentTimeMillis();
    long commonExpiryTime = baseEventTime + DAYS.toMillis(30);
    insertSource(
        createSourceBuilder()
            .setPublisher(Uri.parse("https://web.example.com"))
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
            .setEventTime(baseEventTime)
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s11");
    insertSource(
        createSourceBuilder()
            .setPublisher(Uri.parse("https://web.example.com"))
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(1))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s21");
    insertSource(
        createSourceBuilder()
            .setPublisher(Uri.parse("https://web.example.com"))
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app3")))
            .setWebDestinations(List.of(Uri.parse("https://web3.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(2))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s31");
    insertSource(
        createSourceBuilder()
            .setPublisher(Uri.parse("https://web.example.com"))
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(3))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s12");
    insertSource(
        createSourceBuilder()
            .setPublisher(Uri.parse("https://web.example.com"))
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(4))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s22");

    // Execute
    // com.example.app3 would be the least recently used destination, as 1 & 2 are used
    // afterwards
    mDatastoreManager.runInTransaction(
        (dao) -> {
          Pair<Long, List<String>> destinationPriorityAndSourcesToDelete =
              dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                  Uri.parse("https://web.example.com"),
                  EventSurfaceType.WEB,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  Collections.emptyList(),
                  EventSurfaceType.APP,
                  baseEventTime + DAYS.toMillis(10) // request time
                  );

          assertEquals(0L, (long) destinationPriorityAndSourcesToDelete.first);
          assertEquals(List.of("s31"), destinationPriorityAndSourcesToDelete.second);
        });
  }

  @Test
  public void
      fetchSourceIdsForLowPriorityDest_diffEnrollments_delOldDestSourceForChosenEnrollment() {
    // Setup
    long baseEventTime = System.currentTimeMillis();
    long commonExpiryTime = baseEventTime + DAYS.toMillis(30);
    insertSource(
        createSourceBuilder()
            .setEnrollmentId("enrollment1")
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setEventTime(baseEventTime)
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s11");
    insertSource(
        createSourceBuilder()
            .setEnrollmentId("enrollment1")
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setEventTime(baseEventTime + DAYS.toMillis(1))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s21");
    insertSource(
        createSourceBuilder()
            .setEnrollmentId("enrollment1")
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app3")))
            .setEventTime(baseEventTime + DAYS.toMillis(2))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s31");
    insertSource(
        createSourceBuilder()
            .setEnrollmentId("enrollment2")
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setEventTime(baseEventTime + DAYS.toMillis(3))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s12");
    insertSource(
        createSourceBuilder()
            .setEnrollmentId("enrollment2")
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setEventTime(baseEventTime + DAYS.toMillis(4))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s22");

    // Execute
    // com.example.app1 would be the least recently used destination for enrollment2, that will
    // be deleted
    mDatastoreManager.runInTransaction(
        (dao) -> {
          Pair<Long, List<String>> destinationPriorityAndSourcesToDelete =
              dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                  SourceFixture.ValidSourceParams.PUBLISHER,
                  EventSurfaceType.APP,
                  "enrollment2",
                  Collections.emptyList(),
                  EventSurfaceType.APP,
                  baseEventTime + DAYS.toMillis(10) // request time
                  );

          assertEquals(0L, (long) destinationPriorityAndSourcesToDelete.first);
          assertEquals(List.of("s12"), destinationPriorityAndSourcesToDelete.second);
        });
  }

  @Test
  public void fetchSourceIdsForLowPriorityDest_appDestExcludeLruSource_deletes2ndLruDestSources() {
    // Setup
    long baseEventTime = System.currentTimeMillis();
    insert5SourcesForLruDestDeletion(System.currentTimeMillis());

    // Execute
    // com.example.app1 would be the second least recently used destination, as 2 is used
    // afterwards and 3 is ignored to be deleted.
    mDatastoreManager.runInTransaction(
        (dao) -> {
          Pair<Long, List<String>> destinationPriorityAndSourcesToDelete =
              dao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                  SourceFixture.ValidSourceParams.PUBLISHER,
                  EventSurfaceType.APP,
                  SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                  List.of(Uri.parse("android-app://com.example.app3")),
                  EventSurfaceType.APP,
                  baseEventTime + DAYS.toMillis(10) // request time
                  );

          assertEquals(0L, (long) destinationPriorityAndSourcesToDelete.first);
          assertEquals(List.of("s11", "s12"), destinationPriorityAndSourcesToDelete.second);
        });
  }

  private void insert5SourcesForLruDestDeletion(long baseEventTime) {
    long commonExpiryTime = baseEventTime + DAYS.toMillis(30);
    insertSource(
        createSourceBuilder()
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
            .setEventTime(baseEventTime)
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s11");
    insertSource(
        createSourceBuilder()
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(1))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s21");
    insertSource(
        createSourceBuilder()
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app3")))
            .setWebDestinations(List.of(Uri.parse("https://web3.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(2))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s31");
    insertSource(
        createSourceBuilder()
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app1")))
            .setWebDestinations(List.of(Uri.parse("https://web1.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(3))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s12");
    insertSource(
        createSourceBuilder()
            .setAppDestinations(List.of(Uri.parse("android-app://com.example.app2")))
            .setWebDestinations(List.of(Uri.parse("https://web2.example.com")))
            .setEventTime(baseEventTime + DAYS.toMillis(4))
            .setExpiryTime(commonExpiryTime)
            .build(),
        "s22");
  }

  @Test
  public void deletePendingAggregateReportsAndAttributionsForSources_success() {
    // Setup
    long baseTime = System.currentTimeMillis();
    // Sources
    insertSource(SourceFixture.getValidSource(), "S1");
    insertSource(SourceFixture.getValidSource(), "S2");
    insertSource(SourceFixture.getValidSource(), "S3");
    insertSource(SourceFixture.getValidSource(), "S4");

    mDatastoreManager.runInTransaction(
        (dao) -> {
          // Aggregate reports
          // Should get deleted
          AggregateReport agg1 =
              AggregateReportFixture.getValidAggregateReportBuilder()
                  .setId("Agg1")
                  .setSourceId("S1")
                  .setScheduledReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  .setStatus(AggregateReport.Status.PENDING)
                  .build();
          dao.insertAggregateReport(agg1);
          dao.insertAttribution(
              createAttribution("Att1", Attribution.Scope.AGGREGATE, "S1", null, agg1.getId()));

          // Should not get deleted because S2 is not provided
          AggregateReport agg2 =
              AggregateReportFixture.getValidAggregateReportBuilder()
                  .setId("Agg2")
                  .setSourceId("S2")
                  .setScheduledReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  .setStatus(AggregateReport.Status.PENDING)
                  .build();
          dao.insertAggregateReport(agg2);
          dao.insertAttribution(
              createAttribution("Att2", Attribution.Scope.AGGREGATE, "S2", null, agg2.getId()));

          // Infeasible case, but it should not get deleted because its status is
          // DELIVERED
          AggregateReport agg3 =
              AggregateReportFixture.getValidAggregateReportBuilder()
                  .setId("Agg3")
                  .setSourceId("S3")
                  .setScheduledReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  .setStatus(AggregateReport.Status.DELIVERED)
                  .build();
          dao.insertAggregateReport(agg3);
          dao.insertAttribution(
              createAttribution("Att3", Attribution.Scope.AGGREGATE, "S3", null, agg3.getId()));

          // Execution
          dao.deletePendingAggregateReportsAndAttributionsForSources(List.of("S1", "S3"));

          // Assertion
          assertThrows(DatastoreException.class, () -> dao.getAggregateReport("Agg1"));
          assertEquals(agg2, dao.getAggregateReport("Agg2"));
          assertEquals(agg3, dao.getAggregateReport("Agg3"));
        });

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    assertEquals(2, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
    Set<String> reportIds = new HashSet<>();
    try (Cursor cursor =
        db.rawQuery(
            "SELECT " + AttributionContract.REPORT_ID + " FROM " + AttributionContract.TABLE,
            null)) {
      while (cursor.moveToNext()) {
        reportIds.add(cursor.getString(0));
      }
    }
    assertEquals(Set.of("Agg2", "Agg3"), reportIds);
  }

  @Test
  public void fetchMatchingSourcesUninstall_outsideReportLifetime_deleteSources()
      throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      // All trigger times more than 24 hours ago.
      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime - DAYS.toMillis(1) - TimeUnit.HOURS.toMillis(1))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - DAYS.toMillis(2))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(0), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(0), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualSources =
                dao.fetchMatchingSourcesUninstall(
                    SourceFixture.ValidSourceParams.REGISTRANT, currentTime);
            // Source is deleted
            Truth.assertThat(actualSources.first.size()).isEqualTo(1);
            Truth.assertThat(actualSources.second.size()).isEqualTo(0);
          });
    }
  }

  @Test
  public void fetchMatchingSourcesUninstall_withinReportLifetime_ignoreSources()
      throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      // All trigger times more than 24 hours ago except trigger1.
      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime)
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(0), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(0), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualSources =
                dao.fetchMatchingSourcesUninstall(
                    SourceFixture.ValidSourceParams.REGISTRANT, currentTime);
            // Source is ignored
            Truth.assertThat(actualSources.first.size()).isEqualTo(0);
            Truth.assertThat(actualSources.second.size()).isEqualTo(1);
          });
    }
  }

  @Test
  public void fetchMatchingSourcesUninstall_deleteAndIgnoreSources() throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build(),
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source2")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build(),
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source3")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      // All trigger times more than 24 hours ago except trigger1.
      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime)
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - DAYS.toMillis(2))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(1), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(2), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualSources =
                dao.fetchMatchingSourcesUninstall(
                    SourceFixture.ValidSourceParams.REGISTRANT, currentTime);
            // Source is ignored
            Truth.assertThat(actualSources.first.size()).isEqualTo(2);
            Truth.assertThat(actualSources.second.size()).isEqualTo(1);
          });
    }
  }

  @Test
  public void fetchMatchingTriggersUninstall_outsideReportLifetime_deleteTriggers()
      throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime - DAYS.toMillis(1) - TimeUnit.HOURS.toMillis(1))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - DAYS.toMillis(2))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(0), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(0), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualTriggers =
                dao.fetchMatchingTriggersUninstall(
                    TriggerFixture.ValidTriggerParams.REGISTRANT, currentTime);
            // Triggers are deleted
            Truth.assertThat(actualTriggers.first.size()).isEqualTo(3);
            Truth.assertThat(actualTriggers.second.size()).isEqualTo(0);
          });
    }
  }

  @Test
  public void fetchMatchingTriggersUninstall_withinReportLifetime_ignoreTriggers()
      throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(23))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(1))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - TimeUnit.HOURS.toMillis(12))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(0), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(0), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualTriggers =
                dao.fetchMatchingTriggersUninstall(
                    TriggerFixture.ValidTriggerParams.REGISTRANT, currentTime);
            // Triggers are ignored
            Truth.assertThat(actualTriggers.first.size()).isEqualTo(0);
            Truth.assertThat(actualTriggers.second.size()).isEqualTo(3);
          });
    }
  }

  @Test
  public void fetchMatchingTriggersUninstall_deleteAndIgnoreTriggers() throws JSONException {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableMinReportLifespanForUninstall();
      doReturn(TimeUnit.DAYS.toSeconds(1))
          .when(mFlags)
          .getMeasurementMinReportLifespanForUninstallSeconds();

      long currentTime = System.currentTimeMillis();
      long baseEventTime = currentTime - DAYS.toMillis(3);
      long expiryTime = baseEventTime + DAYS.toMillis(30);

      List<Source> sources =
          Arrays.asList(
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source1")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build(),
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source2")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build(),
              SourceFixture.getMinimalValidSourceBuilder()
                  .setEventId(new UnsignedLong(1L))
                  .setId("source3")
                  .setEventTime(baseEventTime)
                  .setExpiryTime(expiryTime)
                  .build());

      List<Trigger> triggers =
          Arrays.asList(
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger1")
                  .setTriggerTime(currentTime)
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger2")
                  .setTriggerTime(currentTime - DAYS.toMillis(2))
                  .build(),
              TriggerFixture.getValidTriggerBuilder()
                  .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                  .setId("trigger3")
                  .setTriggerTime(currentTime - DAYS.toMillis(3))
                  .build());

      EventReport report0 =
          createEventReportForSourceAndTriggerForUninstall(
              "report0", sources.get(0), triggers.get(0));
      EventReport report1 =
          createEventReportForSourceAndTriggerForUninstall(
              "report1", sources.get(1), triggers.get(1));

      AggregateReport aggregateReport0 =
          createAggregateReportForSourceAndTrigger("areport0", sources.get(2), triggers.get(2));

      SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
      sources.forEach(source -> insertSource(source, source.getId()));
      triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(report0);
            dao.insertEventReport(report1);
            dao.insertAggregateReport(aggregateReport0);
          });

      // Execution
      mDatastoreManager.runInTransaction(
          dao -> {
            Pair<List<String>, List<String>> actualTriggers =
                dao.fetchMatchingTriggersUninstall(
                    TriggerFixture.ValidTriggerParams.REGISTRANT, currentTime);
            // Triggers are deleted
            Truth.assertThat(actualTriggers.first.size()).isEqualTo(2);
            Truth.assertThat(actualTriggers.second.size()).isEqualTo(1);
          });
    }
  }

  @Test
  public void deletePendingFakeEventReportsForSources_success() {
    // Setup
    long baseTime = SOURCE_EVENT_TIME;
    // Sources
    insertSource(SourceFixture.getValidSource(), "S1");
    insertSource(SourceFixture.getValidSource(), "S2");
    insertSource(SourceFixture.getValidSource(), "S3");
    insertSource(SourceFixture.getValidSource(), "S4");

    mDatastoreManager.runInTransaction(
        (dao) -> {
          // Event reports
          // Should get deleted
          EventReport eventReport1 =
              EventReportFixture.getBaseEventReportBuild()
                  .setId("Event1")
                  .setSourceId("S1")
                  .setReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  // trigger time > source event time => fake report
                  .setTriggerTime(baseTime + TimeUnit.HOURS.toMillis(1) + 1L)
                  .setStatus(EventReport.Status.PENDING)
                  .setTriggerId(null)
                  .build();
          dao.insertEventReport(eventReport1);
          dao.insertAttribution(
              createAttribution("Att1", Attribution.Scope.EVENT, "S1", null, eventReport1.getId()));

          // Should not get deleted because S2 is not provided
          EventReport eventReport2 =
              EventReportFixture.getBaseEventReportBuild()
                  .setId("Event2")
                  .setSourceId("S2")
                  .setReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  // trigger time > source event time => fake report
                  .setTriggerTime(
                      baseTime + TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(1))
                  .setStatus(EventReport.Status.PENDING)
                  .setTriggerId(null)
                  .build();
          dao.insertEventReport(eventReport2);
          dao.insertAttribution(
              createAttribution("Att2", Attribution.Scope.EVENT, "S2", null, eventReport2.getId()));

          // Should not get deleted because it's a real report
          EventReport eventReport3 =
              EventReportFixture.getBaseEventReportBuild()
                  .setId("Event3")
                  .setSourceId("S1")
                  .setReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  // trigger time < source event time => real report
                  .setTriggerTime(baseTime - 1L)
                  .setStatus(EventReport.Status.PENDING)
                  .setTriggerId(null)
                  .build();
          dao.insertEventReport(eventReport3);
          dao.insertAttribution(
              createAttribution("Att3", Attribution.Scope.EVENT, "S1", null, eventReport3.getId()));

          // Infeasible case, but it should not get deleted because its status is
          // DELIVERED
          EventReport eventReport4 =
              EventReportFixture.getBaseEventReportBuild()
                  .setId("Event4")
                  .setSourceId("S3")
                  .setReportTime(baseTime + TimeUnit.HOURS.toMillis(1))
                  // trigger time > source event time => fake report
                  .setTriggerTime(
                      baseTime + TimeUnit.HOURS.toMillis(1) + TimeUnit.SECONDS.toMillis(1))
                  .setStatus(EventReport.Status.DELIVERED)
                  .setTriggerId(null)
                  .build();
          dao.insertEventReport(eventReport4);
          dao.insertAttribution(
              createAttribution("Att4", Attribution.Scope.EVENT, "S3", null, eventReport4.getId()));

          // Execution
          dao.deleteFutureFakeEventReportsForSources(List.of("S1", "S3"), baseTime);

          // Assertion
          assertThrows(DatastoreException.class, () -> dao.getEventReport("Event1"));
          assertEquals(eventReport2, dao.getEventReport("Event2"));
          assertEquals(eventReport3, dao.getEventReport("Event3"));
          assertEquals(eventReport4, dao.getEventReport("Event4"));
        });

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    assertEquals(4, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
    Set<String> reportIds = new HashSet<>();
    try (Cursor cursor =
        db.rawQuery(
            "SELECT " + AttributionContract.REPORT_ID + " FROM " + AttributionContract.TABLE,
            null)) {
      while (cursor.moveToNext()) {
        reportIds.add(cursor.getString(0));
      }
    }
    assertEquals(Set.of("Event1", "Event2", "Event3", "Event4"), reportIds);
  }

  private static Source getSourceWithDifferentDestinations(
      int numDestinations,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      Uri publisher,
      String enrollmentId,
      @Source.Status int sourceStatus) {
    List<Uri> appDestinations = null;
    List<Uri> webDestinations = null;
    if (hasAppDestinations) {
      appDestinations = new ArrayList<>();
      appDestinations.add(Uri.parse("android-app://com.app-destination"));
    }
    if (hasWebDestinations) {
      webDestinations = new ArrayList<>();
      for (int i = 0; i < numDestinations; i++) {
        webDestinations.add(Uri.parse("https://web-destination-" + String.valueOf(i) + ".com"));
      }
    }
    long expiryTime =
        eventTime
            + TimeUnit.SECONDS.toMillis(
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return new Source.Builder()
        .setEventId(new UnsignedLong(0L))
        .setEventTime(eventTime)
        .setExpiryTime(expiryTime)
        .setPublisher(publisher)
        .setAppDestinations(appDestinations)
        .setWebDestinations(webDestinations)
        .setEnrollmentId(enrollmentId)
        .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
        .setStatus(sourceStatus)
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .build();
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      Uri publisher,
      String enrollmentId,
      @Source.Status int sourceStatus,
      Uri registrationOrigin) {
    long expiryTime =
        eventTime
            + TimeUnit.SECONDS.toMillis(
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentDestinations(
        numSources,
        hasAppDestinations,
        hasWebDestinations,
        eventTime,
        expiryTime,
        publisher,
        enrollmentId,
        sourceStatus,
        registrationOrigin);
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      Uri publisher,
      String enrollmentId,
      @Source.Status int sourceStatus) {
    long expiryTime =
        eventTime
            + TimeUnit.SECONDS.toMillis(
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentDestinations(
        numSources,
        hasAppDestinations,
        hasWebDestinations,
        eventTime,
        expiryTime,
        publisher,
        enrollmentId,
        sourceStatus,
        REGISTRATION_ORIGIN);
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      long expiryTime,
      Uri publisher,
      String enrollmentId,
      @Source.Status int sourceStatus,
      Uri registrationOrigin) {
    List<Source> sources = new ArrayList<>();
    for (int i = 0; i < numSources; i++) {
      Source.Builder sourceBuilder =
          new Source.Builder()
              .setEventId(new UnsignedLong(0L))
              .setEventTime(eventTime)
              .setExpiryTime(expiryTime)
              .setPublisher(publisher)
              .setEnrollmentId(enrollmentId)
              .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
              .setStatus(sourceStatus)
              .setRegistrationOrigin(registrationOrigin);
      if (hasAppDestinations) {
        sourceBuilder.setAppDestinations(
            List.of(Uri.parse("android-app://app-destination-" + String.valueOf(i))));
      }
      if (hasWebDestinations) {
        sourceBuilder.setWebDestinations(
            List.of(Uri.parse("https://web-destination-" + String.valueOf(i) + ".com")));
      }
      sources.add(sourceBuilder.build());
    }
    return sources;
  }

  private static List<Source> getSourcesWithDifferentRegistrationOrigins(
      int numSources,
      List<Uri> appDestinations,
      List<Uri> webDestinations,
      long eventTime,
      Uri publisher,
      @Source.Status int sourceStatus) {
    long expiryTime =
        eventTime
            + TimeUnit.SECONDS.toMillis(
                MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentRegistrationOrigins(
        numSources,
        appDestinations,
        webDestinations,
        eventTime,
        expiryTime,
        publisher,
        sourceStatus);
  }

  private static List<Source> getSourcesWithDifferentRegistrationOrigins(
      int numSources,
      List<Uri> appDestinations,
      List<Uri> webDestinations,
      long eventTime,
      long expiryTime,
      Uri publisher,
      @Source.Status int sourceStatus) {
    List<Source> sources = new ArrayList<>();
    for (int i = 0; i < numSources; i++) {
      Source.Builder sourceBuilder =
          new Source.Builder()
              .setEventId(new UnsignedLong(0L))
              .setEventTime(eventTime)
              .setExpiryTime(expiryTime)
              .setPublisher(publisher)
              .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
              .setStatus(sourceStatus)
              .setAppDestinations(getNullableUriList(appDestinations))
              .setWebDestinations(getNullableUriList(webDestinations))
              .setEnrollmentId("enrollment-id")
              .setRegistrationOrigin(WebUtil.validUri("https://subdomain" + i + ".example.test"));
      sources.add(sourceBuilder.build());
    }
    return sources;
  }

  private static List<Attribution> getAttributionsWithDifferentReportingOrigins(
      int numAttributions,
      Uri destinationSite,
      long triggerTime,
      Uri sourceSite,
      String registrant) {
    List<Attribution> attributions = new ArrayList<>();
    for (int i = 0; i < numAttributions; i++) {
      Attribution.Builder attributionBuilder =
          new Attribution.Builder()
              .setTriggerTime(triggerTime)
              .setSourceSite(sourceSite.toString())
              .setSourceOrigin(sourceSite.toString())
              .setDestinationSite(destinationSite.toString())
              .setDestinationOrigin(destinationSite.toString())
              .setEnrollmentId("enrollment-id")
              .setRegistrationOrigin(WebUtil.validUri("https://subdomain" + i + ".example.test"))
              .setRegistrant(registrant);
      attributions.add(attributionBuilder.build());
    }
    return attributions;
  }

  private static void insertAttribution(Attribution attribution) {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AttributionContract.ID, UUID.randomUUID().toString());
    values.put(AttributionContract.SOURCE_SITE, attribution.getSourceSite());
    values.put(AttributionContract.DESTINATION_SITE, attribution.getDestinationSite());
    values.put(AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
    values.put(AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
    values.put(AttributionContract.SOURCE_ID, attribution.getSourceId());
    values.put(AttributionContract.TRIGGER_ID, attribution.getTriggerId());
    values.put(
        AttributionContract.REGISTRATION_ORIGIN, attribution.getRegistrationOrigin().toString());
    long row = db.insert("msmt_attribution", null, values);
    assertNotEquals("Attribution insertion failed", -1, row);
  }

  private static Attribution createAttribution(
      String attributionId, String sourceId, String triggerId) {
    return createAttribution(attributionId, Attribution.Scope.EVENT, sourceId, triggerId);
  }

  private static Attribution createAttribution(
      String attributionId, @Attribution.Scope int scope, String sourceId, String triggerId) {
    return createAttribution(attributionId, scope, sourceId, triggerId, null);
  }

  private static Attribution createAttribution(
      String attributionId,
      @Attribution.Scope int scope,
      String sourceId,
      String triggerId,
      String reportId) {
    return new Attribution.Builder()
        .setId(attributionId)
        .setScope(scope)
        .setTriggerTime(0L)
        .setSourceSite("android-app://source.app")
        .setSourceOrigin("android-app://source.app")
        .setDestinationSite("android-app://destination.app")
        .setDestinationOrigin("android-app://destination.app")
        .setEnrollmentId("enrollment-id-")
        .setRegistrant("android-app://registrant.app")
        .setSourceId(sourceId)
        .setTriggerId(triggerId)
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .setReportId(reportId)
        .build();
  }

  private static void insertSource(Source source) {
    insertSource(source, UUID.randomUUID().toString());
  }

  // This is needed because MeasurementDao::insertSource inserts a default value for status.
  private static void insertSource(Source source, String sourceId) {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(SourceContract.ID, sourceId);
    if (source.getEventId() != null) {
      values.put(SourceContract.EVENT_ID, source.getEventId().getValue());
    }
    values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
    values.put(SourceContract.PUBLISHER_TYPE, source.getPublisherType());
    values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
    values.put(SourceContract.EVENT_TIME, source.getEventTime());
    values.put(SourceContract.EXPIRY_TIME, source.getExpiryTime());
    values.put(SourceContract.PRIORITY, source.getPriority());
    values.put(SourceContract.STATUS, source.getStatus());
    values.put(SourceContract.SOURCE_TYPE, source.getSourceType().toString());
    values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
    values.put(SourceContract.INSTALL_ATTRIBUTION_WINDOW, source.getInstallAttributionWindow());
    values.put(SourceContract.INSTALL_COOLDOWN_WINDOW, source.getInstallCooldownWindow());
    values.put(SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
    values.put(SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
    values.put(SourceContract.FILTER_DATA, source.getFilterDataString());
    values.put(SourceContract.SHARED_FILTER_DATA_KEYS, source.getSharedFilterDataKeys());
    values.put(SourceContract.AGGREGATE_CONTRIBUTIONS, source.getAggregateContributions());
    values.put(SourceContract.DEBUG_REPORTING, source.isDebugReporting());
    values.put(SourceContract.INSTALL_TIME, source.getInstallTime());
    values.put(SourceContract.REGISTRATION_ID, source.getRegistrationId());
    values.put(SourceContract.SHARED_AGGREGATION_KEYS, source.getSharedAggregationKeys());
    values.put(SourceContract.REGISTRATION_ORIGIN, source.getRegistrationOrigin().toString());
    values.put(SourceContract.DESTINATION_LIMIT_PRIORITY, source.getDestinationLimitPriority());
    values.put(SourceContract.IS_INSTALL_ATTRIBUTED, source.isInstallAttributed());
    values.put(
        SourceContract.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS,
        source.getAggregateDebugReportContributions());
    values.put(
        SourceContract.REINSTALL_REATTRIBUTION_WINDOW, source.getReinstallReattributionWindow());
    long row = db.insert(SourceContract.TABLE, null, values);
    assertNotEquals("Source insertion failed", -1, row);

    maybeInsertSourceDestinations(db, source, sourceId);
  }

  private static String getNullableUriString(List<Uri> uriList) {
    return Optional.ofNullable(uriList).map(uris -> uris.get(0).toString()).orElse(null);
  }

  /** Test that the AsyncRegistration is inserted correctly. */
  @Test
  public void testInsertAsyncRegistration() {
    AsyncRegistration validAsyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
    String validAsyncRegistrationId = validAsyncRegistration.getId();

    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAsyncRegistration(validAsyncRegistration));

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AsyncRegistrationContract.TABLE,
                null,
                AsyncRegistrationContract.ID + " = ? ",
                new String[] {validAsyncRegistrationId},
                null,
                null,
                null)) {

      assertTrue(cursor.moveToNext());
      AsyncRegistration asyncRegistration = SqliteObjectMapper.constructAsyncRegistration(cursor);
      assertNotNull(asyncRegistration);
      assertNotNull(asyncRegistration.getId());
      assertEquals(asyncRegistration.getId(), validAsyncRegistration.getId());
      assertNotNull(asyncRegistration.getRegistrationUri());
      assertNotNull(asyncRegistration.getTopOrigin());
      assertEquals(asyncRegistration.getTopOrigin(), validAsyncRegistration.getTopOrigin());
      assertNotNull(asyncRegistration.getRegistrant());
      assertEquals(asyncRegistration.getRegistrant(), validAsyncRegistration.getRegistrant());
      assertNotNull(asyncRegistration.getSourceType());
      assertEquals(asyncRegistration.getSourceType(), validAsyncRegistration.getSourceType());
      assertNotNull(asyncRegistration.getDebugKeyAllowed());
      assertEquals(
          asyncRegistration.getDebugKeyAllowed(), validAsyncRegistration.getDebugKeyAllowed());
      assertNotNull(asyncRegistration.getRetryCount());
      assertEquals(asyncRegistration.getRetryCount(), validAsyncRegistration.getRetryCount());
      assertNotNull(asyncRegistration.getRequestTime());
      assertEquals(asyncRegistration.getRequestTime(), validAsyncRegistration.getRequestTime());
      assertNotNull(asyncRegistration.getOsDestination());
      assertEquals(asyncRegistration.getOsDestination(), validAsyncRegistration.getOsDestination());
      assertNotNull(asyncRegistration.getRegistrationUri());
      assertEquals(
          asyncRegistration.getRegistrationUri(), validAsyncRegistration.getRegistrationUri());
      assertNotNull(asyncRegistration.getDebugKeyAllowed());
      assertEquals(
          asyncRegistration.getDebugKeyAllowed(), validAsyncRegistration.getDebugKeyAllowed());
      assertEquals(asyncRegistration.getPlatformAdId(), validAsyncRegistration.getPlatformAdId());
      assertEquals(asyncRegistration.getPostBody(), validAsyncRegistration.getPostBody());
      assertEquals(
          asyncRegistration.getRedirectBehavior(), validAsyncRegistration.getRedirectBehavior());
    }
  }

  /** Test that records in AsyncRegistration queue are fetched properly. */
  @Test
  public void testFetchNextQueuedAsyncRegistration_validRetryLimit() {
    AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
    String asyncRegistrationId = asyncRegistration.getId();

    mDatastoreManager.runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
    mDatastoreManager.runInTransaction(
        (dao) -> {
          AsyncRegistration fetchedAsyncRegistration =
              dao.fetchNextQueuedAsyncRegistration((short) 1, new HashSet<>());
          assertNotNull(fetchedAsyncRegistration);
          assertEquals(fetchedAsyncRegistration.getId(), asyncRegistrationId);
          fetchedAsyncRegistration.incrementRetryCount();
          dao.updateRetryCount(fetchedAsyncRegistration);
        });

    mDatastoreManager.runInTransaction(
        (dao) -> {
          AsyncRegistration fetchedAsyncRegistration =
              dao.fetchNextQueuedAsyncRegistration((short) 1, new HashSet<>());
          assertNull(fetchedAsyncRegistration);
        });
  }

  /** Test that records in AsyncRegistration queue are fetched properly. */
  @Test
  public void testFetchNextQueuedAsyncRegistration_excludeByOrigin() {
    Uri origin1 = Uri.parse("https://adtech1.test");
    Uri origin2 = Uri.parse("https://adtech2.test");
    Uri regUri1 = origin1.buildUpon().appendPath("/hello").build();
    Uri regUri2 = origin2;
    AsyncRegistration asyncRegistration1 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setRegistrationUri(regUri1)
            .build();
    AsyncRegistration asyncRegistration2 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setRegistrationUri(regUri2)
            .build();

    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.insertAsyncRegistration(asyncRegistration1);
          dao.insertAsyncRegistration(asyncRegistration2);
        });
    // Should fetch none
    Set<Uri> excludedOrigins1 = Set.of(origin1, origin2);
    Optional<AsyncRegistration> optAsyncRegistration =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.fetchNextQueuedAsyncRegistration((short) 4, excludedOrigins1));
    assertTrue(optAsyncRegistration.isEmpty());

    // Should fetch only origin1
    Set<Uri> excludedOrigins2 = Set.of(origin2);
    optAsyncRegistration =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.fetchNextQueuedAsyncRegistration((short) 4, excludedOrigins2));
    assertTrue(optAsyncRegistration.isPresent());
    assertEquals(regUri1, optAsyncRegistration.get().getRegistrationUri());

    // Should fetch only origin2
    Set<Uri> excludedOrigins3 = Set.of(origin1);
    optAsyncRegistration =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.fetchNextQueuedAsyncRegistration((short) 4, excludedOrigins3));
    assertTrue(optAsyncRegistration.isPresent());
    assertEquals(regUri2, optAsyncRegistration.get().getRegistrationUri());
  }

  /** Test that AsyncRegistration is deleted correctly. */
  @Test
  public void testDeleteAsyncRegistration() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
    String asyncRegistrationID = asyncRegistration.getId();

    mDatastoreManager.runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AsyncRegistrationContract.TABLE,
                null,
                AsyncRegistrationContract.ID + " = ? ",
                new String[] {asyncRegistration.getId().toString()},
                null,
                null,
                null)) {
      assertTrue(cursor.moveToNext());
      AsyncRegistration updateAsyncRegistration =
          SqliteObjectMapper.constructAsyncRegistration(cursor);
      assertNotNull(updateAsyncRegistration);
    }
    mDatastoreManager.runInTransaction(
        (dao) -> dao.deleteAsyncRegistration(asyncRegistration.getId()));

    db.query(
        /* table */ AsyncRegistrationContract.TABLE,
        /* columns */ null,
        /* selection */ AsyncRegistrationContract.ID + " = ? ",
        /* selectionArgs */ new String[] {asyncRegistrationID.toString()},
        /* groupBy */ null,
        /* having */ null,
        /* orderedBy */ null);

    assertThat(
            db.query(
                    /* table */ AsyncRegistrationContract.TABLE,
                    /* columns */ null,
                    /* selection */ AsyncRegistrationContract.ID + " = ? ",
                    /* selectionArgs */ new String[] {asyncRegistrationID.toString()},
                    /* groupBy */ null,
                    /* having */ null,
                    /* orderedBy */ null)
                .getCount())
        .isEqualTo(0);
  }

  @Test
  public void testDeleteAsyncRegistration_missingRecord() {
    mDatastoreManager.runInTransaction(
        (dao) ->
            assertThrows(
                "Async Registration already deleted",
                DatastoreException.class,
                () -> dao.deleteAsyncRegistration("missingAsyncRegId")));
  }

  @Test
  public void deleteAsyncRegistrations_success() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AsyncRegistration ar1 =
        new AsyncRegistration.Builder()
            .setId("1")
            .setRegistrant(Uri.parse("android-app://installed-registrant1"))
            .setTopOrigin(Uri.parse("android-app://installed-registrant1"))
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(1)
            .setRegistrationId(ValidAsyncRegistrationParams.REGISTRATION_ID)
            .build();

    AsyncRegistration ar2 =
        new AsyncRegistration.Builder()
            .setId("2")
            .setRegistrant(Uri.parse("android-app://installed-registrant2"))
            .setTopOrigin(Uri.parse("android-app://installed-registrant2"))
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(Long.MAX_VALUE)
            .setRegistrationId(ValidAsyncRegistrationParams.REGISTRATION_ID)
            .build();

    AsyncRegistration ar3 =
        new AsyncRegistration.Builder()
            .setId("3")
            .setRegistrant(Uri.parse("android-app://installed-registrant3"))
            .setTopOrigin(Uri.parse("android-app://installed-registrant3"))
            .setAdIdPermission(false)
            .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
            .setRequestTime(Long.MAX_VALUE)
            .setRegistrationId(ValidAsyncRegistrationParams.REGISTRATION_ID)
            .build();

    List<AsyncRegistration> asyncRegistrationList = List.of(ar1, ar2, ar3);
    asyncRegistrationList.forEach(
        asyncRegistration -> {
          ContentValues values = new ContentValues();
          values.put(AsyncRegistrationContract.ID, asyncRegistration.getId());
          values.put(AsyncRegistrationContract.REQUEST_TIME, asyncRegistration.getRequestTime());
          values.put(
              AsyncRegistrationContract.REGISTRANT, asyncRegistration.getRegistrant().toString());
          values.put(
              AsyncRegistrationContract.TOP_ORIGIN, asyncRegistration.getTopOrigin().toString());
          values.put(
              AsyncRegistrationContract.REGISTRATION_ID, asyncRegistration.getRegistrationId());
          db.insert(AsyncRegistrationContract.TABLE, /* nullColumnHack */ null, values);
        });

    mDatastoreManager.runInTransaction((dao) -> dao.deleteAsyncRegistrations(List.of("1", "3")));

    assertThat(
            db.query(
                    /* table */ AsyncRegistrationContract.TABLE,
                    /* columns */ null,
                    /* selection */ null,
                    /* selectionArgs */ null,
                    /* groupBy */ null,
                    /* having */ null,
                    /* orderedBy */ null)
                .getCount())
        .isEqualTo(1);

    assertThat(
            db.query(
                    /* table */ AsyncRegistrationContract.TABLE,
                    /* columns */ null,
                    /* selection */ AsyncRegistrationContract.ID + " = ? ",
                    /* selectionArgs */ new String[] {"2"},
                    /* groupBy */ null,
                    /* having */ null,
                    /* orderedBy */ null)
                .getCount())
        .isEqualTo(1);
  }

  /** Test that retry count in AsyncRegistration is updated correctly. */
  @Test
  public void testUpdateAsyncRegistrationRetryCount() {
    AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
    String asyncRegistrationId = asyncRegistration.getId();
    long originalRetryCount = asyncRegistration.getRetryCount();

    mDatastoreManager.runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
    mDatastoreManager.runInTransaction(
        (dao) -> {
          asyncRegistration.incrementRetryCount();
          dao.updateRetryCount(asyncRegistration);
        });

    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AsyncRegistrationContract.TABLE,
                null,
                AsyncRegistrationContract.ID + " = ? ",
                new String[] {asyncRegistrationId},
                null,
                null,
                null)) {
      assertTrue(cursor.moveToNext());
      AsyncRegistration updateAsyncRegistration =
          SqliteObjectMapper.constructAsyncRegistration(cursor);
      assertNotNull(updateAsyncRegistration);
      assertTrue(updateAsyncRegistration.getRetryCount() == originalRetryCount + 1);
    }
  }

  @Test
  public void getSource_fetchesMatchingSourceFromDb() {
    // Setup - insert 2 sources with different IDs
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    String sourceId1 = "source1";
    Source source1WithoutDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId1)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .build();
    Source source1WithDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId1)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .build();
    insertInDb(db, source1WithDestinations);
    String sourceId2 = "source2";
    Source source2WithoutDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId2)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .build();
    Source source2WithDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId2)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .build();
    insertInDb(db, source2WithDestinations);

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> {
          assertEquals(source1WithoutDestinations, dao.getSource(sourceId1));
          assertEquals(source2WithoutDestinations, dao.getSource(sourceId2));
        });
  }

  @Test
  public void getSourceRegistrant_fetchesMatchingSourceFromDb() {
    // Setup - insert 2 sources with different IDs
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    String sourceId1 = "source1";
    String registrant1 = "android-app://registrant.app1";
    Source source1WithDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId1)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrant(Uri.parse(registrant1))
            .build();
    insertInDb(db, source1WithDestinations);

    String sourceId2 = "source2";
    String registrant2 = "android-app://registrant.app1";
    Source source2WithDestinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId(sourceId2)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrant(Uri.parse(registrant2))
            .build();
    insertInDb(db, source2WithDestinations);

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> {
          assertEquals(registrant1, dao.getSourceRegistrant(sourceId1));
          assertEquals(registrant2, dao.getSourceRegistrant(sourceId2));
        });
  }

  @Test
  public void getSource_nonExistingInDb_throwsException() {
    // Setup - insert 2 sources with different IDs
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
      doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      String sourceId1 = "source1";
      Source source1WithDestinations =
          SourceFixture.getMinimalValidSourceBuilder()
              .setId(sourceId1)
              .setAppDestinations(null)
              .setWebDestinations(null)
              .build();
      insertInDb(db, source1WithDestinations);

      // Execution
      try {
        mDatastoreManager.runInTransaction(
            (dao) -> {
              dao.getSource("random_source_id");
            });
        fail();
      } catch (IllegalStateException e) {
        Throwable cause = e.getCause();
        assertEquals(DatastoreException.class, cause.getClass());
        assertEquals("Source retrieval failed. Id: random_source_id", cause.getMessage());
      }
    }
  }

  @Test
  public void getSource_nonExistingInDbNoSampling_swallowException() {
    // Setup - insert 2 sources with different IDs
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
      doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      String sourceId1 = "source1";
      Source source1WithDestinations =
          SourceFixture.getMinimalValidSourceBuilder()
              .setId(sourceId1)
              .setAppDestinations(null)
              .setWebDestinations(null)
              .build();
      insertInDb(db, source1WithDestinations);

      // Execution
      assertFalse(
          mDatastoreManager.runInTransaction(
              (dao) -> {
                dao.getSource("random_source_id");
              }));
    }
  }

  @Test
  public void getSource_nonExistingInDbThrowingDisabled_swallowException() {
    // Setup - insert 2 sources with different IDs
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(false).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
      doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      String sourceId1 = "source1";
      Source source1WithDestinations =
          SourceFixture.getMinimalValidSourceBuilder()
              .setId(sourceId1)
              .setAppDestinations(null)
              .setWebDestinations(null)
              .build();
      insertInDb(db, source1WithDestinations);

      // Execution
      assertFalse(
          mDatastoreManager.runInTransaction(
              (dao) -> {
                dao.getSource("random_source_id");
              }));
    }
  }

  @Test
  public void fetchMatchingAggregateReports_returnsMatchingReports() {
    // setup - create reports for 3*3 combinations of source and trigger
    List<Source> sources =
        Arrays.asList(
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(1L))
                .setId("source1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(2L))
                .setId("source2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(3L))
                .setId("source3")
                .build());
    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder().setId("trigger1").build(),
            TriggerFixture.getValidTriggerBuilder().setId("trigger2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("trigger3").build());
    List<AggregateReport> reports =
        ImmutableList.of(
            createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(0)),
            createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(1)),
            createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(2)),
            createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(0)),
            createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(1)),
            createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(2)),
            createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(0)),
            createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(1)),
            createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(2)));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertSource(source, source.getId()));
    triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));
    reports.forEach(
        report -> mDatastoreManager.runInTransaction((dao) -> dao.insertAggregateReport(report)));

    mDatastoreManager.runInTransaction(
        (dao) -> {
          // Execution
          List<AggregateReport> aggregateReports =
              dao.fetchMatchingAggregateReports(
                  Arrays.asList(sources.get(1).getId(), "nonMatchingSource"),
                  Arrays.asList(triggers.get(2).getId(), "nonMatchingTrigger"));
          assertEquals(5, aggregateReports.size());

          aggregateReports =
              dao.fetchMatchingAggregateReports(
                  Arrays.asList(sources.get(0).getId(), sources.get(1).getId()),
                  Collections.emptyList());
          assertEquals(6, aggregateReports.size());

          aggregateReports =
              dao.fetchMatchingAggregateReports(
                  Collections.emptyList(),
                  Arrays.asList(triggers.get(0).getId(), triggers.get(2).getId()));
          assertEquals(6, aggregateReports.size());

          aggregateReports =
              dao.fetchMatchingAggregateReports(
                  Arrays.asList(
                      sources.get(0).getId(), sources.get(1).getId(), sources.get(2).getId()),
                  Arrays.asList(
                      triggers.get(0).getId(), triggers.get(1).getId(), triggers.get(2).getId()));
          assertEquals(9, aggregateReports.size());
        });
  }

  @Test
  public void fetchMatchingEventReports_returnsMatchingReports() throws JSONException {
    // setup - create reports for 3*3 combinations of source and trigger
    List<Source> sources =
        Arrays.asList(
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(1L))
                .setId("source1")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(2L))
                .setId("source2")
                .build(),
            SourceFixture.getMinimalValidSourceBuilder()
                .setEventId(new UnsignedLong(3L))
                .setId("source3")
                .build());
    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder()
                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                .setId("trigger1")
                .build(),
            TriggerFixture.getValidTriggerBuilder()
                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                .setId("trigger2")
                .build(),
            TriggerFixture.getValidTriggerBuilder()
                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                .setId("trigger3")
                .build());
    List<EventReport> reports =
        ImmutableList.of(
            createEventReportForSourceAndTrigger(sources.get(0), triggers.get(0)),
            createEventReportForSourceAndTrigger(sources.get(0), triggers.get(1)),
            createEventReportForSourceAndTrigger(sources.get(0), triggers.get(2)),
            createEventReportForSourceAndTrigger(sources.get(1), triggers.get(0)),
            createEventReportForSourceAndTrigger(sources.get(1), triggers.get(1)),
            createEventReportForSourceAndTrigger(sources.get(1), triggers.get(2)),
            createEventReportForSourceAndTrigger(sources.get(2), triggers.get(0)),
            createEventReportForSourceAndTrigger(sources.get(2), triggers.get(1)),
            createEventReportForSourceAndTrigger(sources.get(2), triggers.get(2)));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertSource(source, source.getId()));
    triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));
    reports.forEach(
        report -> mDatastoreManager.runInTransaction((dao) -> dao.insertEventReport(report)));

    mDatastoreManager.runInTransaction(
        (dao) -> {
          // Execution
          List<EventReport> eventReports =
              dao.fetchMatchingEventReports(
                  Arrays.asList(sources.get(1).getId(), "nonMatchingSource"),
                  Arrays.asList(triggers.get(2).getId(), "nonMatchingTrigger"));
          assertEquals(5, eventReports.size());

          eventReports =
              dao.fetchMatchingEventReports(
                  Arrays.asList(sources.get(0).getId(), sources.get(1).getId()),
                  Collections.emptyList());
          assertEquals(6, eventReports.size());

          eventReports =
              dao.fetchMatchingEventReports(
                  Collections.emptyList(),
                  Arrays.asList(triggers.get(0).getId(), triggers.get(2).getId()));
          assertEquals(6, eventReports.size());

          eventReports =
              dao.fetchMatchingEventReports(
                  Arrays.asList(
                      sources.get(0).getId(), sources.get(1).getId(), sources.get(2).getId()),
                  Arrays.asList(
                      triggers.get(0).getId(), triggers.get(1).getId(), triggers.get(2).getId()));
          assertEquals(9, eventReports.size());
        });
  }

  @Test
  public void fetchMatchingSources_bringsMatchingSources() {
    // Setup
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source1")
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source2")
            .build();
    Source source3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(3L))
            .setPublisher(WebUtil.validUri("https://subdomain2.site1.test"))
            .setEventTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source3")
            .build();
    Source source4 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(4L))
            .setPublisher(WebUtil.validUri("https://subdomain2.site2.test"))
            .setEventTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source4")
            .build();
    Source source5 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(5L))
            .setPublisher(WebUtil.validUri("https://subdomain2.site1.test"))
            .setEventTime(20000)
            .setRegistrant(Uri.parse("android-app://com.registrant2"))
            .setId("source5")
            .build();
    List<Source> sources = List.of(source1, source2, source3, source4, source5);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertInDb(db, source));

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          // --- DELETE behaviour ---
          // Delete Nothing
          // No matches
          List<String> actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(0, actualSources.size());

          // 1 & 2 match registrant1 and "https://subdomain1.site1.test" publisher
          // origin
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualSources.size());

          // Only 2 matches registrant1 and "https://subdomain1.site1.test"
          // publisher origin within
          // the range
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(1, actualSources.size());

          // 1,2 & 3 matches registrant1 and "https://site1.test" publisher origin
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(3, actualSources.size());

          // 3 matches origin and 4 matches domain URI
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualSources.size());

          // --- PRESERVE (anti-match exception registrant) behaviour ---
          // Preserve Nothing
          // 1,2,3 & 4 are match registrant1
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(4, actualSources.size());

          // 3 & 4 match registrant1 and don't match
          // "https://subdomain1.site1.test" publisher origin
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualSources.size());

          // 3 & 4 match registrant1, in range and don't match
          // "https://subdomain1.site1.test"
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualSources.size());

          // Only 4 matches registrant1, in range and don't match
          // "https://site1.test"
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualSources.size());

          // only 2 is registrant1 based, in range and does not match either
          // site2.test or subdomain2.site1.test
          actualSources =
              dao.fetchMatchingSources(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualSources.size());
        });
  }

  @Test
  public void fetchFlexSourceIdsFor_bringsMatchingSources_expectedSourceReturned()
      throws JSONException {
    // Setup
    TriggerSpecs testTriggerSpecs = SourceFixture.getValidTriggerSpecsCountBased();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source1")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source1.buildTriggerSpecs();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source2")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source2.buildTriggerSpecs();

    List<TriggerSpecs> triggerSpecsList =
        List.of(source1.getTriggerSpecs(), source2.getTriggerSpecs());

    for (TriggerSpecs triggerSpecs : triggerSpecsList) {
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("123456")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("234567")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("345678")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
    }

    Source source3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source3")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source3.buildTriggerSpecs();

    insertAttributedTrigger(
        source3.getTriggerSpecs(),
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("123456")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build());

    List<Source> sources = List.of(source1, source2, source3);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertInDb(db, source));

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(Collections.singletonList("123456"));
          Set<String> expected = Set.of("source1", "source2", "source3");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(Collections.singletonList("234567"));
          Set<String> expected = Set.of("source1", "source2");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(Collections.singletonList("23456"));
          assertEquals(Collections.emptySet(), actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(new ArrayList<>());
          assertEquals(Collections.emptySet(), actualSources);
        });
  }

  @Test
  public void fetchFlexSourceIdsFor_moreThanSqliteMaxCompoundSelect_expectedSourceReturned()
      throws JSONException {
    // Setup
    TriggerSpecs testTriggerSpecs = SourceFixture.getValidTriggerSpecsCountBased();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source1")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source1.buildTriggerSpecs();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source2")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source2.buildTriggerSpecs();

    List<TriggerSpecs> triggerSpecsList =
        List.of(source1.getTriggerSpecs(), source2.getTriggerSpecs());

    for (TriggerSpecs triggerSpecs : triggerSpecsList) {
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("123456")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("234567")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("345678")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
    }

    Source source3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source3")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source3.buildTriggerSpecs();

    insertAttributedTrigger(
        source3.getTriggerSpecs(),
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("123456")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build());

    List<Source> sources = List.of(source1, source2, source3);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertInDb(db, source));

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(getRandomIdsWith(1000, "123456"));
          Set<String> expected = Set.of("source1", "source2", "source3");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(getRandomIdsWith(1000, "234567"));
          Set<String> expected = Set.of("source1", "source2");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(getRandomIdsWith(1000, "23456"));
          assertEquals(Collections.emptySet(), actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(new ArrayList<>());
          assertEquals(Collections.emptySet(), actualSources);
        });
  }

  @Test
  public void fetchFlexSourceIdsFor_emptyInputSourceId_noSourceReturned() throws JSONException {
    // Setup
    TriggerSpecs testTriggerSpecs = SourceFixture.getValidTriggerSpecsCountBased();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source1")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source1.buildTriggerSpecs();

    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source2")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source2.buildTriggerSpecs();

    List<TriggerSpecs> triggerSpecsList =
        List.of(source1.getTriggerSpecs(), source2.getTriggerSpecs());

    for (TriggerSpecs triggerSpecs : triggerSpecsList) {
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("123456")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("234567")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
      insertAttributedTrigger(
          triggerSpecs,
          EventReportFixture.getBaseEventReportBuild()
              .setTriggerId("345678")
              .setTriggerData(new UnsignedLong(2L))
              .setTriggerPriority(1L)
              .setTriggerValue(1L)
              .build());
    }

    Source source3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source3")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source3.buildTriggerSpecs();

    insertAttributedTrigger(
        source3.getTriggerSpecs(),
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("123456")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build());

    List<Source> sources = List.of(source1, source2, source3);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    sources.forEach(source -> insertInDb(db, source));

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(Collections.singletonList("123456"));
          Set<String> expected = Set.of("source1", "source2", "source3");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(Collections.singletonList("234567"));
          Set<String> expected = Set.of("source1", "source2");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(Collections.singletonList("23456"));
          assertEquals(Collections.emptySet(), actualSources);
        });
  }

  @Test
  public void fetchFlexSourceIdsFor_doesNotMatchV1Sources() throws JSONException {
    // Setup
    EventReport eventReport1 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("123456")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build();
    EventReport eventReport2 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("234567")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build();
    EventReport eventReport3 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerId("345678")
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(1L)
            .setTriggerValue(1L)
            .build();

    TriggerSpecs testTriggerSpecs = SourceFixture.getValidTriggerSpecsCountBased();

    // Flex source
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source1")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source1.buildTriggerSpecs();

    insertAttributedTrigger(source1.getTriggerSpecs(), eventReport1);
    insertAttributedTrigger(source1.getTriggerSpecs(), eventReport2);
    insertAttributedTrigger(source1.getTriggerSpecs(), eventReport3);

    // Non-flex (V1) source
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source2")
            .build();
    source2.buildAttributedTriggers();

    insertAttributedTrigger(source2.getAttributedTriggers(), eventReport1);
    insertAttributedTrigger(source2.getAttributedTriggers(), eventReport2);
    insertAttributedTrigger(source2.getAttributedTriggers(), eventReport3);

    // Flex source
    Source source3 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(2L))
            .setPublisher(WebUtil.validUri("https://subdomain1.site1.test"))
            .setEventTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("source3")
            .setTriggerSpecsString(testTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(testTriggerSpecs.getMaxReports())
            .setPrivacyParameters(testTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    source3.buildTriggerSpecs();

    insertAttributedTrigger(source3.getTriggerSpecs(), eventReport1);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    List.of(source1, source2, source3).forEach(source -> insertInDb(db, source));

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(Collections.singletonList("123456"));
          Set<String> expected = Set.of("source1", "source3");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources =
              dao.fetchFlexSourceIdsFor(new ArrayList<>(Collections.singletonList("234567")));
          Set<String> expected = Set.of("source1");
          assertEquals(expected, actualSources);
        });

    mDatastoreManager.runInTransaction(
        dao -> {
          Set<String> actualSources = dao.fetchFlexSourceIdsFor(Collections.singletonList("23456"));
          assertEquals(Collections.emptySet(), actualSources);
        });
  }

  @Test
  public void fetchMatchingTriggers_bringsMatchingTriggers() {
    // Setup
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WebUtil.validUri("https://subdomain1.site1.test"))
            .setTriggerTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("trigger1")
            .build();
    Trigger trigger2 =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WebUtil.validUri("https://subdomain1.site1.test"))
            .setTriggerTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("trigger2")
            .build();
    Trigger trigger3 =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WebUtil.validUri("https://subdomain2.site1.test"))
            .setTriggerTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("trigger3")
            .build();
    Trigger trigger4 =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WebUtil.validUri("https://subdomain2.site2.test"))
            .setTriggerTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("trigger4")
            .build();
    Trigger trigger5 =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(WebUtil.validUri("https://subdomain2.site1.test"))
            .setTriggerTime(20000)
            .setRegistrant(Uri.parse("android-app://com.registrant2"))
            .setId("trigger5")
            .build();
    List<Trigger> triggers = List.of(trigger1, trigger2, trigger3, trigger4, trigger5);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    triggers.forEach(
        trigger -> {
          ContentValues values = new ContentValues();
          values.put(TriggerContract.ID, trigger.getId());
          values.put(
              TriggerContract.ATTRIBUTION_DESTINATION,
              trigger.getAttributionDestination().toString());
          values.put(TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
          values.put(TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
          values.put(TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
          values.put(TriggerContract.STATUS, trigger.getStatus());
          db.insert(TriggerContract.TABLE, /* nullColumnHack */ null, values);
        });

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          // --- DELETE behaviour ---
          // Delete Nothing
          // No Matches
          Set<String> actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(0, actualTriggers.size());

          // 1 & 2 match registrant1 and "https://subdomain1.site1.test"
          // destination origin
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualTriggers.size());

          // Only 2 matches registrant1 and "https://subdomain1.site1.test"
          // destination origin within the range
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(1, actualTriggers.size());

          // 1,2 & 3 matches registrant1 and "https://site1.test" destination
          // origin
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(3, actualTriggers.size());

          // 3 matches origin and 4 matches domain URI
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualTriggers.size());

          // --- PRESERVE (anti-match exception registrant) behaviour ---
          // Preserve Nothing
          // 1,2,3 & 4 are match registrant1
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(4, actualTriggers.size());

          // 3 & 4 match registrant1 and don't match
          // "https://subdomain1.site1.test" destination origin
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualTriggers.size());

          // 3 & 4 match registrant1, in range and don't match
          // "https://subdomain1.site1.test"
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualTriggers.size());

          // Only 4 matches registrant1, in range and don't match
          // "https://site1.test"
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualTriggers.size());

          // only 2 is registrant1 based, in range and does not match either
          // site2.test or subdomain2.site1.test
          actualTriggers =
              dao.fetchMatchingTriggers(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualTriggers.size());
        });
  }

  @Test
  public void fetchMatchingAsyncRegistrations_bringsMatchingAsyncRegistrations() {
    // Setup
    AsyncRegistration asyncRegistration1 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setTopOrigin(WebUtil.validUri("https://subdomain1.site1.test"))
            .setRequestTime(5000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("asyncRegistration1")
            .build();
    AsyncRegistration asyncRegistration2 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setTopOrigin(WebUtil.validUri("https://subdomain1.site1.test"))
            .setRequestTime(10000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("asyncRegistration2")
            .build();
    AsyncRegistration asyncRegistration3 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setTopOrigin(WebUtil.validUri("https://subdomain2.site1.test"))
            .setRequestTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("asyncRegistration3")
            .build();
    AsyncRegistration asyncRegistration4 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setTopOrigin(WebUtil.validUri("https://subdomain2.site2.test"))
            .setRequestTime(15000)
            .setRegistrant(Uri.parse("android-app://com.registrant1"))
            .setId("asyncRegistration4")
            .build();
    AsyncRegistration asyncRegistration5 =
        AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
            .setTopOrigin(WebUtil.validUri("https://subdomain2.site1.test"))
            .setRequestTime(20000)
            .setRegistrant(Uri.parse("android-app://com.registrant2"))
            .setId("asyncRegistration5")
            .build();
    List<AsyncRegistration> asyncRegistrations =
        List.of(
            asyncRegistration1,
            asyncRegistration2,
            asyncRegistration3,
            asyncRegistration4,
            asyncRegistration5);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    asyncRegistrations.forEach(
        asyncRegistration -> {
          ContentValues values = new ContentValues();
          values.put(AsyncRegistrationContract.ID, asyncRegistration.getId());
          values.put(
              AsyncRegistrationContract.TOP_ORIGIN, asyncRegistration.getTopOrigin().toString());
          values.put(AsyncRegistrationContract.REQUEST_TIME, asyncRegistration.getRequestTime());
          values.put(
              AsyncRegistrationContract.REGISTRANT, asyncRegistration.getRegistrant().toString());
          values.put(AsyncRegistrationContract.REGISTRATION_ID, UUID.randomUUID().toString());
          db.insert(AsyncRegistrationContract.TABLE, /* nullColumnHack */ null, values);
        });

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          // --- DELETE behaviour ---
          // Delete Nothing
          // No Matches
          List<String> actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(0, actualAsyncRegistrations.size());

          // 1 & 2 match registrant1 and "https://subdomain1.site1.test" top-
          // origin origin
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualAsyncRegistrations.size());

          // Only 2 matches registrant1 and "https://subdomain1.site1.test"
          // top-origin origin within the range
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(1, actualAsyncRegistrations.size());

          // 1,2 & 3 matches registrant1 and "https://site1.test" top-origin
          // origin
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(3, actualAsyncRegistrations.size());

          // 3 matches origin and 4 matches domain URI
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_DELETE);
          assertEquals(2, actualAsyncRegistrations.size());

          // --- PRESERVE (anti-match exception registrant) behaviour ---
          // Preserve Nothing
          // 1,2,3 & 4 are match registrant1
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(4, actualAsyncRegistrations.size());

          // 3 & 4 match registrant1 and don't match
          // "https://subdomain1.site1.test" top-origin origin
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualAsyncRegistrations.size());

          // 3 & 4 match registrant1, in range and don't match
          // "https://subdomain1.site1.test"
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(8000),
                  Instant.ofEpochMilli(50000),
                  List.of(WebUtil.validUri("https://subdomain1.site1.test")),
                  List.of(),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(2, actualAsyncRegistrations.size());

          // Only 4 matches registrant1, in range and don't match
          // "https://site1.test"
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(0),
                  Instant.ofEpochMilli(50000),
                  List.of(),
                  List.of(WebUtil.validUri("https://site1.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualAsyncRegistrations.size());

          // only 2 is registrant1 based, in range and does not match either
          // site2.test or subdomain2.site1.test
          actualAsyncRegistrations =
              dao.fetchMatchingAsyncRegistrations(
                  Uri.parse("android-app://com.registrant1"),
                  Instant.ofEpochMilli(10000),
                  Instant.ofEpochMilli(20000),
                  List.of(WebUtil.validUri("https://subdomain2.site1.test")),
                  List.of(WebUtil.validUri("https://site2.test")),
                  DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
          assertEquals(1, actualAsyncRegistrations.size());
        });
  }

  @Test
  public void testUpdateSourceAggregateReportDedupKeys_updatesKeysInList() {
    Source validSource = SourceFixture.getValidSource();
    assertTrue(validSource.getAggregateReportDedupKeys().equals(new ArrayList<UnsignedLong>()));
    mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

    String sourceId = getFirstSourceIdFromDatastore();
    Source source =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();

    source.getAggregateReportDedupKeys().add(new UnsignedLong(10L));
    mDatastoreManager.runInTransaction((dao) -> dao.updateSourceAggregateReportDedupKeys(source));

    Source sourceAfterUpdate =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();

    assertTrue(sourceAfterUpdate.getAggregateReportDedupKeys().size() == 1);
    assertTrue(sourceAfterUpdate.getAggregateReportDedupKeys().get(0).getValue() == 10L);
  }

  @Test
  public void testUpdateSourceAggregateReportDedupKeys_acceptsUnsignedLongValue() {
    Source validSource = SourceFixture.getValidSource();
    assertTrue(validSource.getAggregateReportDedupKeys().equals(new ArrayList<UnsignedLong>()));
    mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

    String sourceId = getFirstSourceIdFromDatastore();
    Source source =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();

    UnsignedLong dedupKeyValue = new UnsignedLong("17293822569102704640");
    source.getAggregateReportDedupKeys().add(dedupKeyValue);
    mDatastoreManager.runInTransaction((dao) -> dao.updateSourceAggregateReportDedupKeys(source));

    Source sourceAfterUpdate =
        mDatastoreManager
            .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
            .get();

    assertEquals(1, sourceAfterUpdate.getAggregateReportDedupKeys().size());
    assertEquals(dedupKeyValue, sourceAfterUpdate.getAggregateReportDedupKeys().get(0));
  }

  @Test
  public void testPersistAndRetrieveSource_handlesPreExistingNegativeValues() {
    // Setup
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Source validSource = SourceFixture.getValidSource();
    ContentValues values = new ContentValues();
    values.put(SourceContract.ID, validSource.getId());
    values.put(SourceContract.EVENT_ID, validSource.getEventId().toString());
    values.put(SourceContract.ENROLLMENT_ID, validSource.getEnrollmentId());
    values.put(SourceContract.REGISTRANT, validSource.getRegistrant().toString());
    values.put(SourceContract.PUBLISHER, validSource.getPublisher().toString());
    values.put(SourceContract.REGISTRATION_ORIGIN, validSource.getRegistrationOrigin().toString());
    // Existing invalid values
    Long val1 = -123L;
    Long val2 = Long.MIN_VALUE;
    values.put(SourceContract.AGGREGATE_REPORT_DEDUP_KEYS, val1.toString() + "," + val2.toString());
    db.insert(SourceContract.TABLE, /* nullColumnHack */ null, values);
    maybeInsertSourceDestinations(db, validSource, validSource.getId());

    // Execution
    Optional<Source> source =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao -> measurementDao.getSource(validSource.getId()));
    assertTrue(source.isPresent());
    List<UnsignedLong> aggReportDedupKeys = source.get().getAggregateReportDedupKeys();
    assertEquals(2, aggReportDedupKeys.size());
    assertEquals(val1, (Long) aggReportDedupKeys.get(0).getValue());
    assertEquals(val2, (Long) aggReportDedupKeys.get(1).getValue());
  }

  @Test
  public void fetchTriggerMatchingSourcesForXna_filtersSourcesCorrectly() {
    // Setup
    Uri matchingDestination = APP_ONE_DESTINATION;
    Uri nonMatchingDestination = APP_TWO_DESTINATION;
    String mmpMatchingEnrollmentId = "mmp1";
    String mmpNonMatchingEnrollmentId = "mmpx";
    String san1MatchingEnrollmentId = "san1EnrollmentId";
    String san2MatchingEnrollmentId = "san2EnrollmentId";
    String san3MatchingEnrollmentId = "san3EnrollmentId";
    String san4NonMatchingEnrollmentId = "san4EnrollmentId";
    String san5MatchingEnrollmentId = "san5EnrollmentId";

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAttributionDestination(matchingDestination)
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
            .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
            .setAggregateTriggerData(TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
            .setAggregateValuesString(TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES_STRING)
            .setFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
            .setNotFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
            .build();
    Source s1MmpMatchingWithDestinations =
        createSourceBuilder()
            .setId("s1")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .build();
    Source s1MmpMatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s1")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(s1MmpMatchingWithDestinations.getRegistrationId())
            .build();
    Source s2MmpDiffDestination =
        createSourceBuilder()
            .setId("s2")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(List.of(nonMatchingDestination))
            .build();
    Source s3MmpExpired =
        createSourceBuilder()
            .setId("s3")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(List.of(nonMatchingDestination))
            // expired before trigger time
            .setExpiryTime(trigger.getTriggerTime() - DAYS.toMillis(1))
            .build();
    Source s4NonMatchingMmp =
        createSourceBuilder()
            .setId("s4")
            .setEnrollmentId(mmpNonMatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .build();
    Source s5MmpMatchingWithDestinations =
        createSourceBuilder()
            .setId("s5")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .build();
    Source s5MmpMatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s5")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(s5MmpMatchingWithDestinations.getRegistrationId())
            .build();
    Source s6San1MatchingWithDestinations =
        createSourceBuilder()
            .setId("s6")
            .setEnrollmentId(san1MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s6San1MatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s6")
            .setEnrollmentId(san1MatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(s6San1MatchingWithDestinations.getRegistrationId())
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s7San1DiffDestination =
        createSourceBuilder()
            .setId("s7")
            .setEnrollmentId(san1MatchingEnrollmentId)
            .setAppDestinations(List.of(nonMatchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s8San2MatchingWithDestinations =
        createSourceBuilder()
            .setId("s8")
            .setEnrollmentId(san2MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s8San2MatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s8")
            .setEnrollmentId(san2MatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(s8San2MatchingWithDestinations.getRegistrationId())
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s9San3XnaIgnored =
        createSourceBuilder()
            .setId("s9")
            .setEnrollmentId(san3MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s10San3MatchingWithDestinations =
        createSourceBuilder()
            .setId("s10")
            .setEnrollmentId(san3MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s10San3MatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s10")
            .setEnrollmentId(san3MatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(s10San3MatchingWithDestinations.getRegistrationId())
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s11San4EnrollmentNonMatching =
        createSourceBuilder()
            .setId("s11")
            .setEnrollmentId(san4NonMatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
            .build();
    Source s12San1NullSharedAggregationKeys =
        createSourceBuilder()
            .setId("s12")
            .setEnrollmentId(san1MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setSharedAggregationKeys(null)
            .build();
    Source s13San1Expired =
        createSourceBuilder()
            .setId("s13")
            .setEnrollmentId(san1MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            // expired before trigger time
            .setExpiryTime(trigger.getTriggerTime() - DAYS.toMillis(1))
            .build();
    String registrationIdForTriggerAndOtherRegistration = UUID.randomUUID().toString();
    Source s14San5RegIdClasesWithMmp =
        createSourceBuilder()
            .setId("s14")
            .setEnrollmentId(san5MatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setRegistrationId(registrationIdForTriggerAndOtherRegistration)
            .build();
    Source s15MmpMatchingWithDestinations =
        createSourceBuilder()
            .setId("s15")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(List.of(matchingDestination))
            .setRegistrationId(registrationIdForTriggerAndOtherRegistration)
            .build();
    Source s15MmpMatchingWithoutDestinations =
        createSourceBuilder()
            .setId("s15")
            .setEnrollmentId(mmpMatchingEnrollmentId)
            .setAppDestinations(null)
            .setWebDestinations(null)
            .setRegistrationId(registrationIdForTriggerAndOtherRegistration)
            .build();
    List<Source> sources =
        Arrays.asList(
            s1MmpMatchingWithDestinations,
            s2MmpDiffDestination,
            s3MmpExpired,
            s4NonMatchingMmp,
            s5MmpMatchingWithDestinations,
            s6San1MatchingWithDestinations,
            s7San1DiffDestination,
            s8San2MatchingWithDestinations,
            s9San3XnaIgnored,
            s10San3MatchingWithDestinations,
            s11San4EnrollmentNonMatching,
            s12San1NullSharedAggregationKeys,
            s13San1Expired,
            s14San5RegIdClasesWithMmp,
            s15MmpMatchingWithDestinations);
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    // Insert all sources to the DB
    sources.forEach(source -> insertSource(source, source.getId()));

    // Insert XNA ignored sources
    ContentValues values = new ContentValues();
    values.put(XnaIgnoredSourcesContract.SOURCE_ID, s9San3XnaIgnored.getId());
    values.put(XnaIgnoredSourcesContract.ENROLLMENT_ID, mmpMatchingEnrollmentId);
    long row = db.insert(XnaIgnoredSourcesContract.TABLE, null, values);
    assertEquals(1, row);

    List<Source> expectedMatchingSources =
        Arrays.asList(
            s1MmpMatchingWithoutDestinations,
            s5MmpMatchingWithoutDestinations,
            s6San1MatchingWithoutDestinations,
            s8San2MatchingWithoutDestinations,
            s10San3MatchingWithoutDestinations,
            s15MmpMatchingWithoutDestinations);
    Comparator<Source> sortingComparator = Comparator.comparing(Source::getId);
    expectedMatchingSources.sort(sortingComparator);

    // Execution
    mDatastoreManager.runInTransaction(
        dao -> {
          List<String> matchingSanEnrollmentIds =
              Arrays.asList(
                  san1MatchingEnrollmentId,
                  san2MatchingEnrollmentId,
                  san3MatchingEnrollmentId,
                  san5MatchingEnrollmentId);
          List<Source> actualMatchingSources =
              dao.fetchTriggerMatchingSourcesForXna(trigger, matchingSanEnrollmentIds);
          actualMatchingSources.sort(sortingComparator);
          // Assertion
          assertEquals(expectedMatchingSources, actualMatchingSources);
        });
  }

  @Test
  public void insertIgnoredSourceForEnrollment_success() {
    // Setup
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    // Need to insert sources before, to honor the foreign key constraint
    insertSource(createSourceBuilder().setId("s1").build(), "s1");
    insertSource(createSourceBuilder().setId("s2").build(), "s2");

    Pair<String, String> entry11 = new Pair<>("s1", "e1");
    Pair<String, String> entry21 = new Pair<>("s2", "e1");
    Pair<String, String> entry22 = new Pair<>("s2", "e2");

    mDatastoreManager.runInTransaction(
        dao -> {
          // Execution
          dao.insertIgnoredSourceForEnrollment(entry11.first, entry11.second);
          dao.insertIgnoredSourceForEnrollment(entry21.first, entry21.second);
          dao.insertIgnoredSourceForEnrollment(entry22.first, entry22.second);

          // Assertion
          queryAndAssertSourceEntries(db, "e1", Arrays.asList("s1", "s2"));
          queryAndAssertSourceEntries(db, "e2", Collections.singletonList("s2"));
        });
  }

  @Test
  public void countDistinctDebugAdIdsUsedByEnrollment_oneTriggerAndSource() {
    // Setup
    Source.Builder sourceBuilder =
        new Source.Builder()
            .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
            .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
            .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
            .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    Source s1 =
        sourceBuilder
            .setId("s1")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s1));

    Trigger.Builder triggerBuilder =
        new Trigger.Builder()
            .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE);
    Trigger t1 =
        triggerBuilder
            .setId("t1")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t1));

    // Assertion
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(1, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-1"))));
  }

  @Test
  public void countDistinctDebugAdIdsUsedByEnrollment_nullValuesPresent() {
    // Setup
    Source.Builder sourceBuilder =
        new Source.Builder()
            .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
            .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
            .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
            .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    // Source with debug AdId present
    Source s1 =
        sourceBuilder
            .setId("s1")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s1));
    // Source with no debug AdId
    Source s2 =
        sourceBuilder
            .setId("s2")
            .setPublisherType(EventSurfaceType.WEB)
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s2));

    Trigger.Builder triggerBuilder =
        new Trigger.Builder()
            .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE);
    // Trigger with debug AdId present
    Trigger t1 =
        triggerBuilder
            .setId("t1")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t1));
    // Trigger with no debug AdId
    Trigger t2 =
        triggerBuilder
            .setId("t2")
            .setDestinationType(EventSurfaceType.WEB)
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t2));

    // Assertion
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(1, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-1"))));
  }

  @Test
  public void countDistinctDebugAdIdsUsedByEnrollment_multipleSourcesAndTriggers() {
    // Setup
    Source.Builder sourceBuilder =
        new Source.Builder()
            .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
            .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
            .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
            .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    // Multiple sources with same AdId
    Source s1 =
        sourceBuilder
            .setId("s1")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s1));
    Source s2 =
        sourceBuilder
            .setId("s2")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s2));
    Source s3 =
        sourceBuilder
            .setId("s3")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s3));

    Trigger.Builder triggerBuilder =
        new Trigger.Builder()
            .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE);
    // Multiple triggers with same AdId
    Trigger t1 =
        triggerBuilder
            .setId("t1")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t1));
    Trigger t2 =
        triggerBuilder
            .setId("t2")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t2));
    Trigger t3 =
        triggerBuilder
            .setId("t3")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t3));

    // Assertion
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(1, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-1"))));
  }

  @Test
  public void countDistinctDebugAdIdsUsedByEnrollment_multipleAdIdsPresent() {
    // Setup
    Source.Builder sourceBuilder =
        new Source.Builder()
            .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
            .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
            .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
            .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    // Multiple sources with different AdIds but the same enrollmentId
    Source s1 =
        sourceBuilder
            .setId("s1")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s1));
    Source s2 =
        sourceBuilder
            .setId("s2")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-2")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s2));
    Source s3 =
        sourceBuilder
            .setId("s3")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-3")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s3));

    Trigger.Builder triggerBuilder =
        new Trigger.Builder()
            .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE);
    // Multiple triggers with different AdIds but the same enrollmentId
    Trigger t1 =
        triggerBuilder
            .setId("t1")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-4")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t1));
    Trigger t2 =
        triggerBuilder
            .setId("t2")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-5")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t2));
    Trigger t3 =
        triggerBuilder
            .setId("t3")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-6")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t3));

    // Assertion
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(6, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-1"))));
  }

  @Test
  public void countDistinctDebugAdIdsUsedByEnrollment_multipleEnrollmentIdsPresent() {
    // Setup
    Source.Builder sourceBuilder =
        new Source.Builder()
            .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
            .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
            .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
            .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    // Multiple sources with different AdIds and differing enrollmentIds
    Source s1 =
        sourceBuilder
            .setId("s1")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-1")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s1));
    Source s2 =
        sourceBuilder
            .setId("s2")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-2")
            .setEnrollmentId("enrollment-id-2")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s2));
    Source s3 =
        sourceBuilder
            .setId("s3")
            .setPublisherType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-3")
            .setEnrollmentId("enrollment-id-2")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertSource(s3));

    Trigger.Builder triggerBuilder =
        new Trigger.Builder()
            .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
            .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE);
    // Multiple triggers with different AdIds and differing enrollmentIds
    Trigger t1 =
        triggerBuilder
            .setId("t1")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-4")
            .setEnrollmentId("enrollment-id-1")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t1));
    Trigger t2 =
        triggerBuilder
            .setId("t2")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-5")
            .setEnrollmentId("enrollment-id-2")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t2));
    Trigger t3 =
        triggerBuilder
            .setId("t3")
            .setDestinationType(EventSurfaceType.WEB)
            .setDebugAdId("debug-ad-id-6")
            .setEnrollmentId("enrollment-id-2")
            .build();
    mDatastoreManager.runInTransaction(dao -> dao.insertTrigger(t3));

    // Assertion
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(2, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-1"))));
    assertTrue(
        mDatastoreManager.runInTransaction(
            dao ->
                assertEquals(4, dao.countDistinctDebugAdIdsUsedByEnrollment("enrollment-id-2"))));
  }

  @Test
  public void getPendingAggregateReportIdsByCoordinatorInWindow() {
    AggregateReport ar11 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("11")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
            .build();
    AggregateReport ar12 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("12")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
            .build();
    AggregateReport ar21 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("21")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url2.test"))
            .build();
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AbstractDbIntegrationTest.insertToDb(ar11, db);
    AbstractDbIntegrationTest.insertToDb(ar12, db);
    AbstractDbIntegrationTest.insertToDb(ar21, db);

    Optional<Map<String, List<String>>> resOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) ->
                dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME,
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME
                        + DAYS.toMillis(30)));
    assertTrue(resOpt.isPresent());
    Map<String, List<String>> res = resOpt.get();
    assertEquals(2, res.size());

    // URL 1
    List<String> url1Ids = res.get("https://url1.test");
    url1Ids.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals(2, url1Ids.size());
    assertEquals("11", url1Ids.get(0));
    assertEquals("12", url1Ids.get(1));
    // URL 2
    List<String> url2Ids = res.get("https://url2.test");
    url2Ids.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals(1, url2Ids.size());
    assertEquals("21", url2Ids.get(0));
  }

  @Test
  public void getPendingAggregateReportIdsByCoordinatorInWindowWithRetryLimit() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      AggregateReport ar11 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("11")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
              .build();
      AggregateReport ar12 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("12")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
              .build();
      AggregateReport ar21 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("21")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url2.test"))
              .build();
      AggregateReport ar31 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("31")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url3.test"))
              .build();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      AbstractDbIntegrationTest.insertToDb(ar11, db);
      AbstractDbIntegrationTest.insertToDb(ar12, db);
      AbstractDbIntegrationTest.insertToDb(ar21, db);
      AbstractDbIntegrationTest.insertToDb(ar31, db);

      Optional<Map<String, List<String>>> resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> {
                return dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME,
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME
                        + DAYS.toMillis(30));
              });
      assertTrue(resOpt.isPresent());
      Map<String, List<String>> res = resOpt.get();
      assertEquals(3, res.size());

      // URL 1
      List<String> url1Ids = res.get("https://url1.test");
      url1Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals(2, url1Ids.size());
      assertEquals("11", url1Ids.get(0));
      assertEquals("12", url1Ids.get(1));
      // URL 2
      List<String> url2Ids = res.get("https://url2.test");
      url2Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals(1, url2Ids.size());
      assertEquals("21", url2Ids.get(0));

      resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> {
                // Adds records to KeyValueData table for Retry Count.
                dao.incrementAndGetReportingRetryCount(
                    ar31.getId(), DataType.AGGREGATE_REPORT_RETRY_COUNT);
                return dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME,
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME
                        + DAYS.toMillis(30));
              });
      res = resOpt.get();

      assertEquals(2, res.size());

      // URL 1
      url1Ids = res.get("https://url1.test");
      url1Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals(2, url1Ids.size());
      assertEquals("11", url1Ids.get(0));
      assertEquals("12", url1Ids.get(1));
      // URL 2
      url2Ids = res.get("https://url2.test");
      url2Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals(1, url2Ids.size());
      assertEquals("21", url2Ids.get(0));
    }
  }

  @Test
  public void getPendingAggregateDebugReportIdsByCoordinator() {
    AggregateReport ar11 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("11")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
            .build();
    AggregateReport ar12 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("12")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
            .build();
    AggregateReport ar21 =
        AggregateReportFixture.getValidAggregateReportBuilder()
            .setId("21")
            .setAggregationCoordinatorOrigin(Uri.parse("https://url2.test"))
            .build();
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AbstractDbIntegrationTest.insertToDb(ar11, db);
    AbstractDbIntegrationTest.insertToDb(ar12, db);
    AbstractDbIntegrationTest.insertToDb(ar21, db);

    Optional<Map<String, List<String>>> resOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) ->
                dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME,
                    AggregateReportFixture.ValidAggregateReportParams.TRIGGER_TIME
                        + DAYS.toMillis(30)));
    assertTrue(resOpt.isPresent());
    Map<String, List<String>> res = resOpt.get();
    assertEquals(2, res.size());

    // URL 1
    List<String> url1Ids = res.get("https://url1.test");
    assertEquals(2, url1Ids.size());
    url1Ids.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals("11", url1Ids.get(0));
    assertEquals("12", url1Ids.get(1));
    // URL 2
    List<String> url2Ids = res.get("https://url2.test");
    url2Ids.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals(1, url2Ids.size());
    assertEquals("21", url2Ids.get(0));
  }

  @Test
  public void getPendingAggregateDebugReportIdsByCoordinatorWithRetryLimit() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      AggregateReport ar11 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("11")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
              .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
              .build();
      AggregateReport ar12 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("12")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url1.test"))
              .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
              .build();
      AggregateReport ar21 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("21")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url2.test"))
              .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
              .build();
      AggregateReport ar31 =
          AggregateReportFixture.getValidAggregateReportBuilder()
              .setId("31")
              .setAggregationCoordinatorOrigin(Uri.parse("https://url3.test"))
              .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
              .build();
      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
      AbstractDbIntegrationTest.insertToDb(ar11, db);
      AbstractDbIntegrationTest.insertToDb(ar12, db);
      AbstractDbIntegrationTest.insertToDb(ar21, db);
      AbstractDbIntegrationTest.insertToDb(ar31, db);

      Optional<Map<String, List<String>>> resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> dao.getPendingAggregateDebugReportIdsByCoordinator());
      assertTrue(resOpt.isPresent());
      Map<String, List<String>> res = resOpt.get();
      assertEquals(3, res.size());

      resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> {
                // Adds records to KeyValueData table for Retry Count.
                dao.incrementAndGetReportingRetryCount(
                    ar31.getId(), DataType.AGGREGATE_REPORT_RETRY_COUNT);
                return dao.getPendingAggregateDebugReportIdsByCoordinator();
              });
      assertTrue(resOpt.isPresent());
      res = resOpt.get();
      assertEquals(2, res.size());

      // URL 1
      List<String> url1Ids = res.get("https://url1.test");
      assertEquals(2, url1Ids.size());
      url1Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals("11", url1Ids.get(0));
      assertEquals("12", url1Ids.get(1));
      // URL 2
      List<String> url2Ids = res.get("https://url2.test");
      url2Ids.sort(String.CASE_INSENSITIVE_ORDER);
      assertEquals(1, url2Ids.size());
      assertEquals("21", url2Ids.get(0));
    }
  }

  @Test
  public void getPendingEventReportIdsInWindowWithRetry() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

      EventReport er1 = generateMockEventReport(WebUtil.validUrl("https://destination-1.test"), 1);
      EventReport er2 = generateMockEventReport(WebUtil.validUrl("https://destination-2.test"), 2);
      List.of(er1, er2)
          .forEach(
              eventReport -> {
                ContentValues values = new ContentValues();
                values.put(EventReportContract.ID, eventReport.getId());
                values.put(
                    EventReportContract.ATTRIBUTION_DESTINATION,
                    eventReport.getAttributionDestinations().get(0).toString());
                values.put(
                    EventReportContract.REPORT_TIME,
                    EventReportFixture.ValidEventReportParams.TRIGGER_TIME + DAYS.toMillis(15));
                values.put(EventReportContract.STATUS, EventReport.Status.PENDING);
                db.insert(EventReportContract.TABLE, null, values);
              });
      Optional<List<String>> resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) ->
                  dao.getPendingEventReportIdsInWindow(
                      EventReportFixture.ValidEventReportParams.TRIGGER_TIME,
                      EventReportFixture.ValidEventReportParams.TRIGGER_TIME + DAYS.toMillis(30)));
      assertTrue(resOpt.isPresent());
      List<String> res = resOpt.get();
      assertEquals(2, res.size());
      assertTrue(res.containsAll(List.of("1", "2")));
      resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> {
                // Adds records to KeyValueData table for Retry Count.
                dao.incrementAndGetReportingRetryCount("1", DataType.EVENT_REPORT_RETRY_COUNT);
                return dao.getPendingEventReportIdsInWindow(
                    EventReportFixture.ValidEventReportParams.TRIGGER_TIME,
                    EventReportFixture.ValidEventReportParams.TRIGGER_TIME + DAYS.toMillis(30));
              });
      res = resOpt.get();
      assertEquals(1, res.size());
      assertEquals(List.of("2"), res);
    }
  }

  @Test
  public void getPendingEventDebugReportIdsWithRetryLimit() {
    // Mocking that the flags return a Max Retry of 1
    Flags mockFlags = Mockito.mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mockFlags);

      doReturn(1).when(mockFlags).getMeasurementReportingRetryLimit();
      doReturn(true).when(mockFlags).getMeasurementReportingRetryLimitEnabled();

      SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

      EventReport er1 = generateMockEventReport(WebUtil.validUrl("https://destination-1.test"), 1);
      EventReport er2 = generateMockEventReport(WebUtil.validUrl("https://destination-2.test"), 2);
      List.of(er1, er2)
          .forEach(
              eventReport -> {
                ContentValues values = new ContentValues();
                values.put(EventReportContract.ID, eventReport.getId());
                values.put(
                    EventReportContract.ATTRIBUTION_DESTINATION,
                    eventReport.getAttributionDestinations().get(0).toString());
                values.put(
                    EventReportContract.DEBUG_REPORT_STATUS, EventReport.DebugReportStatus.PENDING);
                db.insert(EventReportContract.TABLE, null, values);
              });

      Optional<List<String>> resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> dao.getPendingDebugEventReportIds());
      assertTrue(resOpt.isPresent());
      List<String> res = resOpt.get();
      assertEquals(2, res.size());
      assertTrue(res.containsAll(List.of("1", "2")));
      resOpt =
          mDatastoreManager.runInTransactionWithResult(
              (dao) -> {
                // Adds records to KeyValueData table for Retry Count.
                dao.incrementAndGetReportingRetryCount(
                    "1", DataType.DEBUG_EVENT_REPORT_RETRY_COUNT);
                return dao.getPendingDebugEventReportIds();
              });
      res = resOpt.get();
      assertEquals(1, res.size());
      assertEquals(List.of("2"), res);
    }
  }

  @Test
  public void getNonExpiredAggregateEncryptionKeys() {
    AggregateEncryptionKey ek11 =
        new AggregateEncryptionKey.Builder()
            .setId("11")
            .setKeyId("11")
            .setPublicKey("11")
            .setExpiry(11)
            .setAggregationCoordinatorOrigin(Uri.parse("https://1coordinator.test"))
            .build();
    // ek12 will not be fetched because expiry (5) < 10
    AggregateEncryptionKey ek12 =
        new AggregateEncryptionKey.Builder()
            .setId("12")
            .setKeyId("12")
            .setPublicKey("12")
            .setExpiry(5)
            .setAggregationCoordinatorOrigin(Uri.parse("https://1coordinator.test"))
            .build();

    AggregateEncryptionKey ek21 =
        new AggregateEncryptionKey.Builder()
            .setId("21")
            .setKeyId("21")
            .setPublicKey("21")
            .setExpiry(10)
            .setAggregationCoordinatorOrigin(Uri.parse("https://2coordinator.test"))
            .build();
    AggregateEncryptionKey ek22 =
        new AggregateEncryptionKey.Builder()
            .setId("22")
            .setKeyId("22")
            .setPublicKey("22")
            .setExpiry(15)
            .setAggregationCoordinatorOrigin(Uri.parse("https://2coordinator.test"))
            .build();

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    AbstractDbIntegrationTest.insertToDb(ek11, db);
    AbstractDbIntegrationTest.insertToDb(ek12, db);
    AbstractDbIntegrationTest.insertToDb(ek21, db);
    AbstractDbIntegrationTest.insertToDb(ek22, db);

    List<AggregateEncryptionKey> res1 =
        mDatastoreManager
            .runInTransactionWithResult(
                (dao) -> {
                  return dao.getNonExpiredAggregateEncryptionKeys(
                      Uri.parse("https://1coordinator.test"), 10);
                })
            .orElseThrow();
    // ek12 will not be fetched because expiry (5) < 10
    assertEquals(1, res1.size());
    assertEquals(ek11, res1.get(0));

    List<AggregateEncryptionKey> res2 =
        mDatastoreManager
            .runInTransactionWithResult(
                (dao) -> {
                  return dao.getNonExpiredAggregateEncryptionKeys(
                      Uri.parse("https://2coordinator.test"), 10);
                })
            .orElseThrow();
    res1.sort((x, y) -> String.CASE_INSENSITIVE_ORDER.compare(x.getId(), y.getId()));
    assertEquals(2, res2.size());
    assertEquals(ek21, res2.get(0));
    assertEquals(ek22, res2.get(1));
  }

  @Test
  public void incrementReportRetryIncrements() {
    final String eventId = "TestIdEvent";
    final String aggregateId = "TestIdAggregate";
    final String debugId = "TestIdDebug";

    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.incrementAndGetReportingRetryCount(eventId, DataType.EVENT_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(eventId, DataType.DEBUG_EVENT_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(
              aggregateId, DataType.AGGREGATE_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(
              aggregateId, DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(debugId, DataType.DEBUG_REPORT_RETRY_COUNT);
        });
    Optional<KeyValueData> eventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.EVENT_REPORT_RETRY_COUNT));
    Optional<KeyValueData> debugEventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.DEBUG_EVENT_REPORT_RETRY_COUNT));
    Optional<KeyValueData> aggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.AGGREGATE_REPORT_RETRY_COUNT));
    Optional<KeyValueData> debugAggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT));
    Optional<KeyValueData> debugCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(debugId, DataType.DEBUG_REPORT_RETRY_COUNT));

    assertTrue(eventCount.isPresent());
    assertEquals(1, (eventCount.get().getReportRetryCount()));
    assertTrue(debugEventCount.isPresent());
    assertEquals(1, (debugEventCount.get().getReportRetryCount()));
    assertTrue(aggregateCount.isPresent());
    assertEquals(1, (aggregateCount.get().getReportRetryCount()));
    assertTrue(debugAggregateCount.isPresent());
    assertEquals(1, (debugAggregateCount.get().getReportRetryCount()));
    assertTrue(debugCount.isPresent());
    assertEquals(1, (debugCount.get().getReportRetryCount()));

    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.incrementAndGetReportingRetryCount(eventId, DataType.EVENT_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(
              aggregateId, DataType.AGGREGATE_REPORT_RETRY_COUNT);
        });
    eventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.EVENT_REPORT_RETRY_COUNT));
    aggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.AGGREGATE_REPORT_RETRY_COUNT));
    debugEventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.DEBUG_EVENT_REPORT_RETRY_COUNT));
    debugAggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT));
    debugCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(debugId, DataType.DEBUG_REPORT_RETRY_COUNT));

    assertTrue(eventCount.isPresent());
    assertEquals(2, (eventCount.get().getReportRetryCount()));
    assertTrue(debugEventCount.isPresent());
    assertEquals(1, (debugEventCount.get().getReportRetryCount()));
    assertTrue(aggregateCount.isPresent());
    assertEquals(2, (aggregateCount.get().getReportRetryCount()));
    assertTrue(debugAggregateCount.isPresent());
    assertEquals(1, (debugAggregateCount.get().getReportRetryCount()));
    assertTrue(debugCount.isPresent());
    assertEquals(1, (debugCount.get().getReportRetryCount()));

    mDatastoreManager.runInTransaction(
        (dao) -> {
          dao.incrementAndGetReportingRetryCount(eventId, DataType.DEBUG_EVENT_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(
              aggregateId, DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT);
          dao.incrementAndGetReportingRetryCount(debugId, DataType.DEBUG_REPORT_RETRY_COUNT);
        });
    eventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.EVENT_REPORT_RETRY_COUNT));
    aggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.AGGREGATE_REPORT_RETRY_COUNT));
    debugEventCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(eventId, DataType.DEBUG_EVENT_REPORT_RETRY_COUNT));
    debugAggregateCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(aggregateId, DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT));
    debugCount =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getKeyValueData(debugId, DataType.DEBUG_REPORT_RETRY_COUNT));

    assertTrue(eventCount.isPresent());
    assertEquals(2, (eventCount.get().getReportRetryCount()));
    assertTrue(debugEventCount.isPresent());
    assertEquals(2, (debugEventCount.get().getReportRetryCount()));
    assertTrue(aggregateCount.isPresent());
    assertEquals(2, (aggregateCount.get().getReportRetryCount()));
    assertTrue(debugAggregateCount.isPresent());
    assertEquals(2, (debugAggregateCount.get().getReportRetryCount()));
    assertTrue(debugCount.isPresent());
    assertEquals(2, (debugCount.get().getReportRetryCount()));
  }

  @Test
  public void countNavigationSourcesPerReportingOriginQuery() {
    final String registrationId1 = "registrationId1";
    final String registrationId2 = "registrationId2";
    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setRegistrationId(registrationId1)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build();
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setRegistrationId(registrationId1)
            .setSourceType(Source.SourceType.EVENT)
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build();
    Source source3 =
        SourceFixture.getValidSourceBuilder()
            .setRegistrationId(registrationId1)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build();
    Source source4 =
        SourceFixture.getValidSourceBuilder()
            .setRegistrationId(registrationId2)
            .setSourceType(Source.SourceType.EVENT)
            .setRegistrationOrigin(REGISTRATION_ORIGIN_2)
            .build();
    Source source5 =
        SourceFixture.getValidSourceBuilder()
            .setRegistrationId(registrationId2)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(REGISTRATION_ORIGIN_2)
            .build();
    Arrays.asList(source1, source2, source3, source4, source5).stream()
        .forEach(source -> insertSource(source));
    assertThat(
            mDatastoreManager.runInTransactionWithResult(
                (dao) ->
                    dao.countNavigationSourcesPerReportingOrigin(
                        REGISTRATION_ORIGIN, registrationId1)))
        .isEqualTo(Optional.of(2L));
    assertThat(
            mDatastoreManager.runInTransactionWithResult(
                (dao) ->
                    dao.countNavigationSourcesPerReportingOrigin(
                        REGISTRATION_ORIGIN_2, registrationId2)))
        .isEqualTo(Optional.of(1L));
    assertThat(
            mDatastoreManager.runInTransactionWithResult(
                (dao) ->
                    dao.countNavigationSourcesPerReportingOrigin(
                        REGISTRATION_ORIGIN, registrationId2)))
        .isEqualTo(Optional.of(0L));
  }

  private void verifySourceStatus(@NonNull Source source, @Source.Status int status) {
    assertThat(
            mDatastoreManager
                .runInTransactionWithResult(
                    measurementDao -> measurementDao.getSource(source.getId()))
                .get()
                .getStatus())
        .isEqualTo(status);
  }

  @Test
  public void
      testUpdateSourcesForAttributionScope_diffMaxViewStates_ignoresSourcesDeletesReports() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source source1 =
          insertSourceForAttributionScope(
              List.of("1"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION),
              null);
      EventReport pastFakeEventReport =
          new EventReport.Builder()
              .setId("1")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      EventReport fakeEventReport1 =
          new EventReport.Builder()
              .setId("2")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      // Deleted fake event report for comparison.
      EventReport deletedFakeEventReport1 =
          new EventReport.Builder()
              .setId("3")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.MARKED_TO_DELETE)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(pastFakeEventReport);
            dao.insertEventReport(fakeEventReport1);
            dao.updateSourcesForAttributionScope(source1);
          });
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, fakeEventReport1);

      Source source2 =
          insertSourceForAttributionScope(
              List.of("2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES + 1,
              SOURCE_EVENT_TIME + 1,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source2));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, deletedFakeEventReport1);

      Source source3 =
          insertSourceForAttributionScope(
              List.of("3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source3));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source4 =
          insertSourceForAttributionScope(
              List.of("5"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 3,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source4));
      Arrays.asList(source1, source2).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source5 =
          insertSourceForAttributionScope(
              List.of("4"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES + 1,
              SOURCE_EVENT_TIME + 4,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source5));
      Arrays.asList(source1, source2, source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source5).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // Sources for different reporting origin with different max event states.
      Source source6 =
          insertSourceForAttributionScope(
              List.of("4"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 5,
              List.of(WEB_ONE_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source6));
      Arrays.asList(source1, source2, source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source5, source6).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // Sources for different reporting origin with the same max event states.
      Source source7 =
          insertSourceForAttributionScope(
              List.of("4"),
              ATTRIBUTION_SCOPE_LIMIT + 1,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 5,
              List.of(WEB_TWO_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source7));
      Arrays.asList(source1, source2, source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source5, source6, source7).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source8 =
          insertSourceForAttributionScope(
              List.of("4"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES + 1,
              SOURCE_EVENT_TIME + 5,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source8));
      Arrays.asList(source1, source2, source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source5, source6, source7, source8).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
    }
  }

  @Test
  public void testUpdateSourcesForAttributionScope_smallerLimit_ignoresSourcesDeletesReports() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();
      Source source1 =
          insertSourceForAttributionScope(
              List.of("1"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION),
              null);
      EventReport pastFakeEventReport =
          new EventReport.Builder()
              .setId("1")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      EventReport fakeEventReport1 =
          new EventReport.Builder()
              .setId("2")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      // Delete fake event report for comparison.
      EventReport deletedFakeEventReport1 =
          new EventReport.Builder()
              .setId("3")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.MARKED_TO_DELETE)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(pastFakeEventReport);
            dao.insertEventReport(fakeEventReport1);
            dao.updateSourcesForAttributionScope(source1);
          });
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, fakeEventReport1);

      Source source2 =
          insertSourceForAttributionScope(
              List.of("2"),
              /* attributionScopeLimit= */ 8L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 1,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source2));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, deletedFakeEventReport1);

      Source source3 =
          insertSourceForAttributionScope(
              List.of("3"),
              /* attributionScopeLimit= */ 4L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source3));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source4 =
          insertSourceForAttributionScope(
              List.of("3"),
              /* attributionScopeLimit= */ 7L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 3,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source4));
      Arrays.asList(source1, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source5 =
          insertSourceForAttributionScope(
              List.of("3"),
              /* attributionScopeLimit= */ 4L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source5));
      Arrays.asList(source1, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source4, source5).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source6 =
          insertSourceForAttributionScope(
              List.of("3"),
              /* attributionScopeLimit= */ 6L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source6));
      Arrays.asList(source1, source3, source5).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source4, source6).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // Sources for different reporting origin with different max event states.
      Source source7 =
          insertSourceForAttributionScope(
              List.of("4"),
              /* attributionScopeLimit= */ 6L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 3,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source7));
      Arrays.asList(source1, source3, source5).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source4, source6, source7).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // Sources for different reporting origin with the same max event states.
      Source source8 =
          insertSourceForAttributionScope(
              List.of("4"),
              /* attributionScopeLimit= */ 4L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 3,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source8));
      Arrays.asList(source1, source3, source5).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source4, source6, source7, source8).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      Source source9 =
          insertSourceForAttributionScope(
              List.of("4"),
              /* attributionScopeLimit= */ 5L,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 4,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source9));
      Arrays.stream(new Source[] {source1, source3, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source4, source6, source7, source8, source9})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
    }
  }

  @Test
  public void
      testUpdateSourcesForAttributionScope_scopedSource_ignoresNonScopedAndDeletesReports() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      Source source1 =
          insertSourceForAttributionScope(
              null, null, null, SOURCE_EVENT_TIME, List.of(WEB_ONE_DESTINATION), null);
      EventReport pastFakeEventReport =
          new EventReport.Builder()
              .setId("1")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      EventReport fakeEventReport1 =
          new EventReport.Builder()
              .setId("2")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.PENDING)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      // Deleted fake event report for comparison.
      EventReport deletedFakeEventReport1 =
          new EventReport.Builder()
              .setId("3")
              .setSourceId(source1.getId())
              .setSourceEventId(source1.getEventId())
              .setReportTime(SOURCE_EVENT_TIME + 1000)
              .setAttributionDestinations(List.of(WEB_ONE_DESTINATION))
              .setTriggerTime(SOURCE_EVENT_TIME + 1000)
              .setSourceType(source1.getSourceType())
              .setStatus(EventReport.Status.MARKED_TO_DELETE)
              .setRegistrationOrigin(source1.getRegistrationOrigin())
              .build();
      mDatastoreManager.runInTransaction(
          (dao) -> {
            dao.insertEventReport(pastFakeEventReport);
            dao.insertEventReport(fakeEventReport1);
            dao.updateSourcesForAttributionScope(source1);
          });
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, fakeEventReport1);

      Source source2 =
          insertSourceForAttributionScope(
              List.of("2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 1,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source2));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      assertThat(
              mDatastoreManager
                  .runInTransactionWithResult(
                      measurementDao -> measurementDao.getSourceEventReports(source1))
                  .get())
          .containsExactly(pastFakeEventReport, deletedFakeEventReport1);

      Source source3 =
          insertSourceForAttributionScope(
              List.of("3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source3));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.asList(source2, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
    }
  }

  @Test
  public void testUpdateSourcesForAttributionScope_newNonScopedSource_removesScopes() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();
      Consumer<? super Source> verifyAttributionScopeEmptyFn =
          source -> {
            assertThat(
                    mDatastoreManager
                        .runInTransactionWithResult(
                            measurementDao ->
                                measurementDao.getSourceAttributionScopes(source.getId()))
                        .get())
                .isEmpty();
            Source savedSource =
                mDatastoreManager
                    .runInTransactionWithResult(
                        measurementDao -> measurementDao.getSource(source.getId()))
                    .get();
            assertThat(savedSource.getAttributionScopeLimit()).isNull();
            assertThat(savedSource.getMaxEventStates()).isNull();
          };

      Consumer<? super Source> verifyAttributionScopeUnchangedFn =
          source -> {
            assertThat(
                    mDatastoreManager
                        .runInTransactionWithResult(
                            measurementDao ->
                                measurementDao.getSourceAttributionScopes(source.getId()))
                        .get())
                .containsExactlyElementsIn(source.getAttributionScopes());
            Source savedSource =
                mDatastoreManager
                    .runInTransactionWithResult(
                        measurementDao -> measurementDao.getSource(source.getId()))
                    .get();
            assertThat(savedSource.getAttributionScopeLimit())
                .isEqualTo(savedSource.getAttributionScopeLimit());
            assertThat(savedSource.getMaxEventStates()).isEqualTo(savedSource.getMaxEventStates());
          };

      Source source1 =
          insertSourceForAttributionScope(
              List.of("1"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source1));
      Arrays.asList(source1).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      Arrays.asList(source1).stream().forEach(verifyAttributionScopeUnchangedFn);

      Source source2 =
          insertSourceForAttributionScope(
              List.of("2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 1,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source2));
      Arrays.asList(source1, source2).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      Arrays.asList(source1, source2).stream().forEach(verifyAttributionScopeUnchangedFn);

      Source source3 =
          insertSourceForAttributionScope(
              null, null, null, SOURCE_EVENT_TIME + 2, List.of(WEB_TWO_DESTINATION), null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source3));
      Arrays.asList(source1, source2, source3).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      // Source3 is for a different destination, attribution scopes should not be cleared.
      Arrays.asList(source1, source2).stream().forEach(verifyAttributionScopeUnchangedFn);

      Source source4 =
          insertSourceForAttributionScope(
              null, null, null, SOURCE_EVENT_TIME + 3, List.of(WEB_ONE_DESTINATION), null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source4));
      Arrays.asList(source1, source2, source3, source4).stream()
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
      // Source4 is the same destination, clear attribution scopes for older sources.
      Arrays.asList(source1, source2).stream().forEach(verifyAttributionScopeEmptyFn);
    }
  }

  @Test
  public void testUpdateSourcesForAttributionScope_scopesNotSelected_ignoreSources() {
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAttributionScope();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();
      // Below are the sources registered with attribution scopes and destinations.
      // For each destination, two sources are registered, and only one's scopes are to be
      // deleted.
      // For registration R1:
      // S1: attribution scopes -> [0, ""], destinations -> [D1]
      // S2: attribution scopes -> [3, 4, 5], destinations -> [D1]
      // S3: attribution scopes -> [0, 1], destinations -> [D2]
      // S4: attribution scopes -> [1, 3], destinations -> [D2]
      // S5: attribution scopes -> [1, 2], destinations -> [D3]
      // S6: attribution scopes -> [2, 3, 4], destinations -> [D3]
      // S7: attribution scopes -> [2], destinations -> [D4]
      // S8: attribution scopes -> [1, 2], destinations -> [D4], shares same timestamp as S7.
      // S12: attribution scopes -> [3, 4], destinations -> [D1, D2, D3, D4]
      // For registration R2 to test interplay cross reporting origin:
      // If the reporting origin were R1, the attribution scopes for S9, S10, and S11 would
      // have been removed.
      // S9: attribution scopes -> [0], destinations -> [D1]
      // S10: attribution scopes -> [0, 1], destinations -> [D2]
      // S11: attribution scopes -> [1, 2], destinations -> [D3]
      // The selected attribution scopes for each destination are:
      // D1: [3, 4, 5] => Scope for S1 to be removed.
      // D2: [1, 3, 4] => Scope for S3 to be removed.
      // D3: [2, 3, 4] => Scope for S5 to be removed.
      // D4: [2, 3, 4] => Scope for S8 to be removed. S7 and S8 share the same timestamp; the
      // attribution scope with the higher value will be selected.

      // S1: attribution scopes -> [0], destinations -> [D1]
      Source source1 =
          insertSourceForAttributionScope(
              List.of("0", ""),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              null,
              List.of(APP_ONE_DESTINATION));
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source1));
      Arrays.stream(new Source[] {source1})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S2: attribution scopes -> [3, 4, 5], destinations -> [D1]
      Source source2 =
          insertSourceForAttributionScope(
              List.of("3", "4", "5"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 1,
              null,
              List.of(APP_ONE_DESTINATION));
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source2));
      Arrays.stream(new Source[] {source1})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S3: attribution scopes -> [0, 1], destinations -> [D2]
      Source source3 =
          insertSourceForAttributionScope(
              List.of("0", "1"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source3));
      Arrays.stream(new Source[] {source1})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S4: attribution scopes -> [1, 3], destinations -> [D2]
      Source source4 =
          insertSourceForAttributionScope(
              List.of("1", "3"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 3,
              List.of(WEB_ONE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source4));
      Arrays.stream(new Source[] {source1})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S5: attribution scopes -> [1, 2], destinations -> [D3]
      Source source5 =
          insertSourceForAttributionScope(
              List.of("1", "2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 4,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source5));
      Arrays.stream(new Source[] {source1})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S6: attribution scopes -> [2, 3, 4], destinations -> [D3]
      Source source6 =
          insertSourceForAttributionScope(
              List.of("2", "3", "4"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 5,
              List.of(WEB_TWO_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source6));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4, source6})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S7: attribution scopes -> [2], destinations -> [D4]
      Source source7 =
          insertSourceForAttributionScope(
              List.of("2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 6,
              List.of(WEB_THREE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source7));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4, source6, source7})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S8: attribution scopes -> [1, 2], destinations -> [D4], shares same timestamp as S7.
      Source source8 =
          insertSourceForAttributionScope(
              List.of("1", "2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 6,
              List.of(WEB_THREE_DESTINATION),
              null);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source8));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4, source6, source7, source8})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S9: attribution scopes -> [0], destinations -> [D1], reporting origin -> R2
      Source source9 =
          insertSourceForAttributionScope(
              List.of("0"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME,
              null,
              List.of(APP_ONE_DESTINATION),
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source9));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(new Source[] {source2, source3, source4, source6, source7, source8, source9})
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S10: attribution scopes -> [0, 1], destinations -> [D2], reporting origin -> R2
      Source source10 =
          insertSourceForAttributionScope(
              List.of("0", "1"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 2,
              List.of(WEB_ONE_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source10));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(
              new Source[] {
                source2, source3, source4, source6, source7, source8, source9, source10
              })
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S11: attribution scopes -> [1, 2], destinations -> [D3], reporting origin -> R2
      Source source11 =
          insertSourceForAttributionScope(
              List.of("1", "2"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 4,
              List.of(WEB_TWO_DESTINATION),
              null,
              REGISTRATION_ORIGIN_2);
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source11));
      Arrays.stream(new Source[] {source1, source5})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(
              new Source[] {
                source2, source3, source4, source6, source7, source8, source9, source10, source11
              })
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));

      // S12: attribution scopes -> [3, 4], destinations -> [D1, D2, D3, D4]
      Source source12 =
          insertSourceForAttributionScope(
              List.of("3", "4"),
              ATTRIBUTION_SCOPE_LIMIT,
              MAX_EVENT_STATES,
              SOURCE_EVENT_TIME + 8,
              List.of(WEB_ONE_DESTINATION, WEB_TWO_DESTINATION, WEB_THREE_DESTINATION),
              List.of(APP_ONE_DESTINATION));
      mDatastoreManager.runInTransaction((dao) -> dao.updateSourcesForAttributionScope(source12));
      Arrays.stream(new Source[] {source1, source3, source5, source8})
          .forEach(source -> verifySourceStatus(source, Source.Status.IGNORED));
      Arrays.stream(
              new Source[] {
                source2, source4, source6, source7, source9, source10, source11, source12
              })
          .forEach(source -> verifySourceStatus(source, Source.Status.ACTIVE));
    }
  }

  @Test
  public void getLatestReportTimeInBatchWindow_singleAggregateReport_returnsSingleReportTime() {
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("source1")
            .build();

    long scheduledReportTime = 1L;
    AggregateReport report =
        generateMockAggregateReport(
            WebUtil.validUrl("https://destination-1.test"), 1, "source1", scheduledReportTime);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    getAggregateReportConsumer(db).accept(report);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(scheduledReportTime, result.longValue());
  }

  @Test
  public void testGetLatestReportTimeInBatchWindow_singleEventReport_returnsSingleReportTime() {
    long reportTime = 1L;
    EventReport report =
        generateMockEventReport(WebUtil.validUrl("https://destination-1.test"), 1, reportTime);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    getEventReportConsumer(db).accept(report);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(reportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_twoAggregateReport_bothInBatchWindow_returnsTwoReportTimes() {
    String sourceId = "source1";
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId(sourceId)
            .build();

    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS - 1L;

    String destination = WebUtil.validUrl("https://destination-1.test");
    List<AggregateReport> reports =
        Arrays.asList(
            generateMockAggregateReport(destination, 1, sourceId, firstScheduledReportTime),
            generateMockAggregateReport(destination, 2, sourceId, secondScheduledReportTime));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    reports.forEach(getAggregateReportConsumer(db));

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(secondScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_twoAggReport_oneAfterBatchWindow_returnOneReportTime() {
    String sourceId = "source1";
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId(sourceId)
            .build();

    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1L;

    String destination = WebUtil.validUrl("https://destination-1.test");
    List<AggregateReport> reports =
        Arrays.asList(
            generateMockAggregateReport(destination, 1, sourceId, firstScheduledReportTime),
            generateMockAggregateReport(destination, 2, sourceId, secondScheduledReportTime));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    reports.forEach(getAggregateReportConsumer(db));

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    // secondScheduledReportTime should not be returned as it was outside the batch window.
    assertEquals(firstScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_twoEventReport_bothInBatchWindow_returnSecondReportTime() {
    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS - 1L;

    String destination = WebUtil.validUrl("https://destination-1.test");
    List<EventReport> reports =
        Arrays.asList(
            generateMockEventReport(destination, 1, firstScheduledReportTime),
            generateMockEventReport(destination, 2, secondScheduledReportTime));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);

    Consumer<EventReport> eventReportConsumer = getEventReportConsumer(db);
    reports.forEach(eventReportConsumer);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(secondScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_twoEventReport_oneAfterBatchWindow_returnFirstReportTime() {
    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1L;

    String destination = WebUtil.validUrl("https://destination-1.test");
    List<EventReport> reports =
        Arrays.asList(
            generateMockEventReport(destination, 1, firstScheduledReportTime),
            generateMockEventReport(destination, 2, secondScheduledReportTime));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);

    Consumer<EventReport> eventReportConsumer = getEventReportConsumer(db);
    reports.forEach(eventReportConsumer);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    // secondScheduledReportTime should not be returned as it was outside the batch window.
    assertEquals(firstScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_oneAggReport_oneEventReport_bothInBatchWindow_returnSecondReportTime() {
    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS - 1L;

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId("source1")
            .build();

    String destination = WebUtil.validUrl("https://destination-1.test");
    AggregateReport aggregateReport =
        generateMockAggregateReport(destination, 1, "source1", firstScheduledReportTime);
    EventReport eventReport = generateMockEventReport(destination, 1, secondScheduledReportTime);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    getEventReportConsumer(db).accept(eventReport);
    getAggregateReportConsumer(db).accept(aggregateReport);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(secondScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_oneAggReport_oneEventReport_oneAfterBatchWindow_returnFirstReportTime() {
    long firstScheduledReportTime = 1L;
    long secondScheduledReportTime =
        firstScheduledReportTime + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1L;

    String sourceId = "source1";
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId(sourceId)
            .build();

    String destination = WebUtil.validUrl("https://destination-1.test");
    AggregateReport aggregateReport =
        generateMockAggregateReport(destination, 1, sourceId, firstScheduledReportTime);
    EventReport eventReport = generateMockEventReport(destination, 1, secondScheduledReportTime);

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    getEventReportConsumer(db).accept(eventReport);
    getAggregateReportConsumer(db).accept(aggregateReport);

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(firstScheduledReportTime, result.longValue());
  }

  @Test
  public void
      testGetLatestReportTimeInBatchWindow_manyAggReport_manyEventReport_returnLatestReportTime() {
    String sourceId = "source1";
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventId(new UnsignedLong(1L))
            .setId(sourceId)
            .build();
    List<EventReport> eventReports = new ArrayList<>();
    List<AggregateReport> aggregateReports = new ArrayList<>();
    int firstReportTime = 1;
    String destination = WebUtil.validUrl("https://destination-1.test");
    for (int i = firstReportTime; i < MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION; i++) {
      eventReports.add(generateMockEventReport(destination, /* id= */ i, /* reportTime= */ i));
    }

    for (int i = firstReportTime; i < MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE; i++) {
      aggregateReports.add(
          generateMockAggregateReport(destination, /* id= */ i, sourceId, /* reportTime= */ i));
    }

    // Add one more aggregate report that is scheduled at the very edge of the batch window.
    // The report time for this report should be returned as the report time.
    long lastReportTime = MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
    int lastId = MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE;
    aggregateReports.add(
        generateMockAggregateReport(destination, lastId, sourceId, lastReportTime));

    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    eventReports.forEach(getEventReportConsumer(db));
    aggregateReports.forEach(getAggregateReportConsumer(db));

    Long result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao ->
                    measurementDao.getLatestReportTimeInBatchWindow(
                        mFlags.getMeasurementReportingJobServiceBatchWindowMillis()))
            .orElseThrow();

    assertEquals(lastReportTime, result.longValue());
  }

  @Test
  public void testGetLatestReportTimeInBatchWindow_noReports_returnNull() {
    Optional<Long> results =
        mDatastoreManager.runInTransactionWithResult(
            measurementDao ->
                measurementDao.getLatestReportTimeInBatchWindow(
                    mFlags.getMeasurementReportingJobServiceBatchWindowMillis()));

    assertTrue(results.isEmpty());
  }

  @Test
  public void testInsertSource_withDestinationLimitPriorityEnabled_fetchesTheSetValue() {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableSourceDestinationLimitPriority();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();

      // Execution
      Source validSource =
          SourceFixture.getValidSourceBuilder()
              .setDestinationLimitPriority(
                  SourceFixture.ValidSourceParams.DESTINATION_LIMIT_PRIORITY)
              .build();
      mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));

      // Assertion
      String sourceId = getFirstSourceIdFromDatastore();
      Source source =
          mDatastoreManager
              .runInTransactionWithResult(measurementDao -> measurementDao.getSource(sourceId))
              .orElseThrow(() -> new IllegalStateException("Source is null"));
      assertEquals(
          SourceFixture.ValidSourceParams.DESTINATION_LIMIT_PRIORITY,
          source.getDestinationLimitPriority());
    }
  }

  @Test
  public void testInsertAggregateDebugReportRecords_sourceAndTriggerIdPresent_succeeds() {
    // insert source & trigger to honor the foreign key constraint
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S1")
            .setEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .build();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder().setId("T1").setEnrollmentId("2").build();
    AggregateDebugReportRecord validAggregateDebugReportRecord =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 1000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test1.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination.test"),
                /* contributions= */ 9)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();

    // Execution
    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    AbstractDbIntegrationTest.insertToDb(trigger, db);
    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAggregateDebugReportRecord(validAggregateDebugReportRecord));

    // Assertion
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AggregatableDebugReportBudgetTrackerContract.TABLE,
                null,
                null,
                null,
                null,
                null,
                null)) {
      assertThat(cursor.moveToNext()).isTrue();
      assertThat(validAggregateDebugReportRecord.getReportGenerationTime())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REPORT_GENERATION_TIME)));
      assertThat(validAggregateDebugReportRecord.getTopLevelRegistrant().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.TOP_LEVEL_REGISTRANT)));
      assertThat(validAggregateDebugReportRecord.getRegistrantApp().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRANT_APP)));
      assertThat(validAggregateDebugReportRecord.getRegistrationOrigin().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)));
      assertThat(validAggregateDebugReportRecord.getSourceId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.SOURCE_ID)));
      assertThat(validAggregateDebugReportRecord.getTriggerId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.TRIGGER_ID)));
      assertThat(validAggregateDebugReportRecord.getContributions())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.CONTRIBUTIONS)));
    }
  }

  @Test
  public void testInsertAggregateDebugReportRecords_nullTriggerId_succeeds() {
    // insert source & trigger to honor the foreign key constraint
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S1")
            .setEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .build();
    AggregateDebugReportRecord validAggregateDebugReportRecord =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 1000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test1.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination.test"),
                /* contributions= */ 9)
            .setSourceId("S1")
            .build();

    // Execution
    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAggregateDebugReportRecord(validAggregateDebugReportRecord));

    // Assertion
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AggregatableDebugReportBudgetTrackerContract.TABLE,
                null,
                null,
                null,
                null,
                null,
                null)) {
      assertThat(cursor.moveToNext()).isTrue();
      assertThat(validAggregateDebugReportRecord.getReportGenerationTime())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REPORT_GENERATION_TIME)));
      assertThat(validAggregateDebugReportRecord.getTopLevelRegistrant().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.TOP_LEVEL_REGISTRANT)));
      assertThat(validAggregateDebugReportRecord.getRegistrantApp().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRANT_APP)));
      assertThat(validAggregateDebugReportRecord.getRegistrationOrigin().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)));
      assertThat(validAggregateDebugReportRecord.getSourceId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.SOURCE_ID)));
      assertThat(validAggregateDebugReportRecord.getTriggerId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.TRIGGER_ID)));
      assertThat(validAggregateDebugReportRecord.getContributions())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.CONTRIBUTIONS)));
    }
  }

  @Test
  public void testInsertAggregateDebugReportRecords_nullSourceAndTriggerId_succeeds() {
    AggregateDebugReportRecord validAggregateDebugReportRecord =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 1000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test1.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination.test"),
                /* contributions= */ 9)
            .build();

    // Execution
    mDatastoreManager.runInTransaction(
        (dao) -> dao.insertAggregateDebugReportRecord(validAggregateDebugReportRecord));

    // Assertion
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(
                AggregatableDebugReportBudgetTrackerContract.TABLE,
                null,
                null,
                null,
                null,
                null,
                null)) {
      assertThat(cursor.moveToNext()).isTrue();
      assertThat(validAggregateDebugReportRecord.getReportGenerationTime())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REPORT_GENERATION_TIME)));
      assertThat(validAggregateDebugReportRecord.getTopLevelRegistrant().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.TOP_LEVEL_REGISTRANT)));
      assertThat(validAggregateDebugReportRecord.getRegistrantApp().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRANT_APP)));
      assertThat(validAggregateDebugReportRecord.getRegistrationOrigin().toString())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.REGISTRATION_ORIGIN)));
      assertThat(validAggregateDebugReportRecord.getSourceId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.SOURCE_ID)));
      assertThat(validAggregateDebugReportRecord.getTriggerId())
          .isEqualTo(
              cursor.getString(
                  cursor.getColumnIndex(AggregatableDebugReportBudgetTrackerContract.TRIGGER_ID)));
      assertThat(validAggregateDebugReportRecord.getContributions())
          .isEqualTo(
              cursor.getInt(
                  cursor.getColumnIndex(
                      AggregatableDebugReportBudgetTrackerContract.CONTRIBUTIONS)));
    }
  }

  @Test
  public void testGetTotalAggregateDebugReportBudget() {
    // insert source & trigger to honor the foreign key constraint
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S1")
            .setEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .build();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder().setId("T1").setEnrollmentId("2").build();
    SQLiteDatabase db = MeasurementDbHelper.getInstance().getWritableDatabase();
    Objects.requireNonNull(db);
    insertSource(source, source.getId());
    AbstractDbIntegrationTest.insertToDb(trigger, db);

    // test case 1 (publisher query): outside time window + same publisher
    AggregateDebugReportRecord validAggregateDebugReportRecord1 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 1000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination0.test"),
                /* contributions= */ 9)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();

    // test case 2 (publisher query): inside time window + same publisher + different origin
    AggregateDebugReportRecord validAggregateDebugReportRecord2 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 2000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination1.test"),
                /* contributions= */ 16)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();
    AggregateDebugReportRecord validAggregateDebugReportRecord3 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 3000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination2.test"),
                /* contributions= */ 25)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();
    AggregateDebugReportRecord validAggregateDebugReportRecord4 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 4000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_1,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination3.test"),
                /* contributions= */ 36)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();

    // test case 3 (publisher + origin query): inside time window + same publisher + same origin
    AggregateDebugReportRecord validAggregateDebugReportRecord5 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 5000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_2,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination4.test"),
                /* contributions= */ 49)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();
    AggregateDebugReportRecord validAggregateDebugReportRecord6 =
        new AggregateDebugReportRecord.Builder(
                /* reportGenerationTime= */ 6000L,
                /* topLevelRegistrant= */ TOP_LEVEL_REGISTRANT_2,
                /* registrantApp= */ Uri.parse("com.test.myapp"),
                /* registrationOrigin= */ Uri.parse("https://destination4.test"),
                /* contributions= */ 64)
            .setSourceId("S1")
            .setTriggerId("T1")
            .build();
    List<AggregateDebugReportRecord> validAggregateDebugReportRecords =
        Arrays.asList(
            validAggregateDebugReportRecord1,
            validAggregateDebugReportRecord2,
            validAggregateDebugReportRecord3,
            validAggregateDebugReportRecord4,
            validAggregateDebugReportRecord5,
            validAggregateDebugReportRecord6);

    for (AggregateDebugReportRecord validAggregateDebugReportRecord :
        validAggregateDebugReportRecords) {
      mDatastoreManager.runInTransaction(
          (dao) -> dao.insertAggregateDebugReportRecord(validAggregateDebugReportRecord));
    }

    // test case 1
    int budget1 =
        mDatastoreManager
            .runInTransactionWithResult(
                (dao) ->
                    dao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        /* publisher= */ TOP_LEVEL_REGISTRANT_1,
                        /* publisherType= */ EventSurfaceType.APP,
                        /* windowStartTime= */ 1000L))
            .get();
    assertThat(budget1).isEqualTo((16 + 25 + 36));

    // test case 2
    int budget2 =
        mDatastoreManager
            .runInTransactionWithResult(
                (dao) ->
                    dao.sumAggregateDebugReportBudgetXPublisherXWindow(
                        /* publisher= */ TOP_LEVEL_REGISTRANT_1,
                        /* publisherType= */ EventSurfaceType.APP,
                        /* windowStartTime= */ 1001L))
            .get();
    assertThat(budget2).isEqualTo((16 + 25 + 36));

    // test case 3
    int budget3 =
        mDatastoreManager
            .runInTransactionWithResult(
                (dao) ->
                    dao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                        /* publisher= */ TOP_LEVEL_REGISTRANT_2,
                        /* publisherType= */ EventSurfaceType.APP,
                        /* origin= */ Uri.parse("https://destination4.test"),
                        /* windowStartTime= */ 1001L))
            .get();
    assertThat(budget3).isEqualTo((49 + 64));
  }

  @Test
  public void updateSourceAggregateDebugContributions_updateFromPreValue_success() {
    // Setup
    mFlags = mock(Flags.class);
    try (MockedStatic mockedFlagsFactory = mockStatic(FlagsFactory.class)) {
      mockedFlagsFactory.when(FlagsFactory::getFlags).thenReturn(mFlags);

      doReturn(true).when(mFlags).getMeasurementEnableAggregateDebugReporting();
      doReturn(MEASUREMENT_DB_SIZE_LIMIT).when(mFlags).getMeasurementDbSizeLimit();
      int initialContributions = 1024;
      Source source =
          SourceFixture.getValidSourceBuilder()
              .setAggregateDebugReportContributions(initialContributions)
              .build();
      insertSource(source, source.getId());

      // Verify persisted value
      assertThat(getFirstSourceFromDb().getAggregateDebugReportContributions())
          .isEqualTo(initialContributions);

      // Execution
      // Update the value
      int expectedUpdatedContributions = 65536;
      source.setAggregateDebugContributions(expectedUpdatedContributions);
      mDatastoreManager.runInTransaction(
          measurementDao -> measurementDao.updateSourceAggregateDebugContributions(source));

      // Verification
      assertThat(getFirstSourceFromDb().getAggregateDebugReportContributions())
          .isEqualTo(expectedUpdatedContributions);
    }
  }

  private Source getFirstSourceFromDb() {
    return mDatastoreManager
        .runInTransactionWithResult(
            measurementDao ->
                measurementDao.getSource(
                    getFirstIdFromDatastore(SourceContract.TABLE, SourceContract.ID)))
        .orElseThrow();
  }

  private static Consumer<AggregateReport> getAggregateReportConsumer(SQLiteDatabase db) {
    Consumer<AggregateReport> aggregateReportConsumer =
        aggregateReport -> {
          ContentValues values = new ContentValues();
          values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
          values.put(MeasurementTables.AggregateReport.SOURCE_ID, aggregateReport.getSourceId());
          values.put(
              MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
              aggregateReport.getAttributionDestination().toString());
          values.put(
              MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
              aggregateReport.getScheduledReportTime());
          values.put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus());
          db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
        };
    return aggregateReportConsumer;
  }

  private static Consumer<EventReport> getEventReportConsumer(SQLiteDatabase db) {
    Consumer<EventReport> eventReportConsumer =
        eventReport -> {
          ContentValues values = new ContentValues();
          values.put(EventReportContract.ID, eventReport.getId());
          values.put(
              EventReportContract.ATTRIBUTION_DESTINATION,
              eventReport.getAttributionDestinations().get(0).toString());
          values.put(EventReportContract.REPORT_TIME, eventReport.getReportTime());
          values.put(EventReportContract.STATUS, EventReport.Status.PENDING);

          db.insert(EventReportContract.TABLE, null, values);
        };
    return eventReportConsumer;
  }

  private void insertInDb(SQLiteDatabase db, Source source) {
    ContentValues values = new ContentValues();
    values.put(SourceContract.ID, source.getId());
    values.put(SourceContract.STATUS, Source.Status.ACTIVE);
    values.put(SourceContract.EVENT_TIME, source.getEventTime());
    values.put(SourceContract.EXPIRY_TIME, source.getExpiryTime());
    values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
    values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
    values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
    values.put(SourceContract.REGISTRATION_ORIGIN, source.getRegistrationOrigin().toString());
    if (source.getAttributedTriggers() != null) {
      values.put(SourceContract.EVENT_ATTRIBUTION_STATUS, source.attributedTriggersToJson());
    }
    if (source.getTriggerSpecs() != null) {
      values.put(SourceContract.TRIGGER_SPECS, source.getTriggerSpecs().encodeToJson());
    }
    db.insert(SourceContract.TABLE, null, values);

    // Insert source destinations
    if (source.getAppDestinations() != null) {
      for (Uri appDestination : source.getAppDestinations()) {
        ContentValues destinationValues = new ContentValues();
        destinationValues.put(MeasurementTables.SourceDestination.SOURCE_ID, source.getId());
        destinationValues.put(
            MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.APP);
        destinationValues.put(
            MeasurementTables.SourceDestination.DESTINATION, appDestination.toString());
        db.insert(MeasurementTables.SourceDestination.TABLE, null, destinationValues);
      }
    }

    if (source.getWebDestinations() != null) {
      for (Uri webDestination : source.getWebDestinations()) {
        ContentValues destinationValues = new ContentValues();
        destinationValues.put(MeasurementTables.SourceDestination.SOURCE_ID, source.getId());
        destinationValues.put(
            MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.WEB);
        destinationValues.put(
            MeasurementTables.SourceDestination.DESTINATION, webDestination.toString());
        db.insert(MeasurementTables.SourceDestination.TABLE, null, destinationValues);
      }
    }
  }

  private void queryAndAssertSourceEntries(
      SQLiteDatabase db, String enrollmentId, List<String> expectedSourceIds) {
    try (Cursor cursor =
        db.query(
            XnaIgnoredSourcesContract.TABLE,
            new String[] {XnaIgnoredSourcesContract.SOURCE_ID},
            XnaIgnoredSourcesContract.ENROLLMENT_ID + " = ?",
            new String[] {enrollmentId},
            null,
            null,
            null)) {
      assertEquals(expectedSourceIds.size(), cursor.getCount());
      for (int i = 0; i < expectedSourceIds.size() && cursor.moveToNext(); i++) {
        assertEquals(expectedSourceIds.get(i), cursor.getString(0));
      }
    }
  }

  private Source.Builder createSourceBuilder() {
    return new Source.Builder()
        .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
        .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
        .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
        .setEventTime(SOURCE_EVENT_TIME)
        .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
        .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
        .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
        .setInstallAttributionWindow(SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
        .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
        .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
        .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
        .setFilterDataString(SourceFixture.ValidSourceParams.buildFilterDataString())
        .setSharedFilterDataKeys(SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS)
        .setIsDebugReporting(true)
        .setRegistrationId(UUID.randomUUID().toString())
        .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
        .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
        .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
  }

  private AggregateReport createAggregateReportForSourceAndTrigger(Source source, Trigger trigger) {
    return createAggregateReportForSourceAndTrigger(UUID.randomUUID().toString(), source, trigger);
  }

  private EventReport createEventReportForSourceAndTrigger(Source source, Trigger trigger)
      throws JSONException {
    return createEventReportForSourceAndTrigger(UUID.randomUUID().toString(), source, trigger);
  }

  private AggregateReport createAggregateReportForSourceAndTrigger(
      String reportId, Source source, Trigger trigger) {
    return AggregateReportFixture.getValidAggregateReportBuilder()
        .setId(reportId)
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .setTriggerTime(trigger.getTriggerTime())
        .build();
  }

  private EventReport createEventReportForSourceAndTrigger(
      String reportId, Source source, Trigger trigger) throws JSONException {
    EventTrigger eventTrigger =
        trigger.parseEventTriggers(FakeFlagsFactory.getFlagsForTest()).get(0);
    return new EventReport.Builder()
        .populateFromSourceAndTrigger(
            source,
            trigger,
            eventTrigger.getTriggerData(),
            eventTrigger,
            new Pair<>(null, null),
            new EventReportWindowCalcDelegate(mFlags),
            new SourceNoiseHandler(mFlags),
            source.getAttributionDestinations(trigger.getDestinationType()))
        .setId(reportId)
        .setSourceEventId(source.getEventId())
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .build();
  }

  private EventReport createEventReportForSourceAndTriggerForUninstall(
      String reportId, Source source, Trigger trigger) throws JSONException {
    EventTrigger eventTrigger =
        trigger.parseEventTriggers(FakeFlagsFactory.getFlagsForTest()).get(0);
    return new EventReport.Builder()
        .setTriggerTime(trigger.getTriggerTime())
        .setSourceEventId(source.getEventId())
        .setEnrollmentId(source.getEnrollmentId())
        .setStatus(EventReport.Status.PENDING)
        .setSourceType(source.getSourceType())
        .setDebugReportStatus(EventReport.DebugReportStatus.NONE)
        .setRegistrationOrigin(trigger.getRegistrationOrigin())
        .setTriggerPriority(eventTrigger.getTriggerPriority())
        .setTriggerData(eventTrigger.getTriggerData())
        .setId(reportId)
        .setAttributionDestinations(source.getAttributionDestinations(trigger.getDestinationType()))
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .build();
  }

  private DebugReport createDebugReport() {
    return createDebugReport(UUID.randomUUID().toString(), REGISTRANT, INSERTION_TIME);
  }

  private DebugReport createDebugReport(String id, Uri registrant, long insertionTime) {
    return new DebugReport.Builder()
        .setId(id)
        .setType("trigger-event-deduplicated")
        .setBody(
            " {\n"
                + "      \"attribution_destination\":"
                + " \"https://destination.example\",\n"
                + "      \"source_event_id\": \"45623\"\n"
                + "    }")
        .setEnrollmentId("1")
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .setRegistrant(registrant)
        .setInsertionTime(insertionTime)
        .build();
  }

  private DebugReport buildDebugReportWithInstalledRegistrant(String id) {
    return new DebugReport.Builder()
        .setId(id)
        .setType("trigger-event-deduplicated")
        .setBody(
            " {\n"
                + "      \"attribution_destination\":"
                + " \"https://destination.example\",\n"
                + "      \"source_event_id\": \"45623\"\n"
                + "    }")
        .setEnrollmentId("1")
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .setRegistrant(INSTALLED_REGISTRANT)
        .setInsertionTime(INSERTION_TIME)
        .build();
  }

  private DebugReport buildDebugReportWithNotInstalledRegistrant(String id) {
    return new DebugReport.Builder()
        .setId(id)
        .setType("trigger-event-deduplicated")
        .setBody(
            " {\n"
                + "      \"attribution_destination\":"
                + " \"https://destination.example\",\n"
                + "      \"source_event_id\": \"45623\"\n"
                + "    }")
        .setEnrollmentId("1")
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .setRegistrant(NOT_INSTALLED_REGISTRANT)
        .setInsertionTime(INSERTION_TIME)
        .build();
  }

  private Uri buildRegistrant(String appName) {
    return Uri.parse("android-app://" + appName);
  }

  private void insertAsyncRecordForPackageName(Uri... registrants) {
    for (Uri registrant : registrants) {
      AsyncRegistration validRecord =
          AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
              .setRegistrant(registrant)
              .build();

      mDatastoreManager.runInTransaction((dao) -> dao.insertAsyncRegistration(validRecord));
    }
  }

  private void insertSourceForPackageName(Uri... registrants) {
    for (Uri registrant : registrants) {
      Source validSource = SourceFixture.getValidSourceBuilder().setRegistrant(registrant).build();

      mDatastoreManager.runInTransaction((dao) -> dao.insertSource(validSource));
    }
  }

  private Source insertSourceForAttributionScope(
      List<String> attributionScopes,
      Long attributionScopeLimit,
      Long maxEventStates,
      long eventTime,
      List<Uri> webDestinations,
      List<Uri> appDestinations) {
    return insertSourceForAttributionScope(
        attributionScopes,
        attributionScopeLimit,
        maxEventStates,
        eventTime,
        webDestinations,
        appDestinations,
        SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
  }

  private Source insertSourceForAttributionScope(
      List<String> attributionScopes,
      Long attributionScopeLimit,
      Long maxEventStates,
      long eventTime,
      List<Uri> webDestinations,
      List<Uri> appDestinations,
      @NonNull Uri reportingOrigin) {
    return insertSourceForAttributionScope(
        attributionScopes,
        attributionScopeLimit,
        maxEventStates,
        eventTime,
        webDestinations,
        appDestinations,
        reportingOrigin,
        SourceFixture.ValidSourceParams.REGISTRATION_ID,
        Source.SourceType.EVENT,
        Source.Status.ACTIVE);
  }

  private Source insertSourceForAttributionScope(
      List<String> attributionScopes,
      Long attributionScopeLimit,
      Long maxEventStates,
      long eventTime,
      List<Uri> webDestinations,
      List<Uri> appDestinations,
      @NonNull Uri reportingOrigin,
      String registrationId,
      Source.SourceType sourceType,
      @Source.Status int status) {
    Source validSource =
        SourceFixture.getValidSourceBuilder()
            .setEventTime(eventTime)
            .setAttributionScopeLimit(attributionScopeLimit)
            .setMaxEventStates(maxEventStates)
            .setWebDestinations(webDestinations)
            .setAppDestinations(appDestinations)
            .setAttributionScopes(attributionScopes)
            .setRegistrationOrigin(reportingOrigin)
            .setSourceType(sourceType)
            .setStatus(status)
            .setRegistrationId(registrationId)
            .build();
    AtomicReference<String> insertedSourceId = new AtomicReference<>();
    mDatastoreManager.runInTransaction(
        (dao) -> {
          insertedSourceId.set(dao.insertSource(validSource));
          Source insertedSource = dao.getSource(insertedSourceId.get());
          boolean attributionScopeEnabled = mFlags.getMeasurementEnableAttributionScope();
          assertThat(insertedSource.getMaxEventStates())
              .isEqualTo(attributionScopeEnabled ? maxEventStates : null);
          assertThat(insertedSource.getAttributionScopeLimit())
              .isEqualTo(attributionScopeEnabled ? attributionScopeLimit : null);
          assertThat(dao.getSourceAttributionScopes(insertedSourceId.get()))
              .containsExactlyElementsIn(
                  (!attributionScopeEnabled || attributionScopes == null)
                      ? List.of()
                      : attributionScopes);
        });
    return Source.Builder.from(validSource).setId(insertedSourceId.get()).build();
  }

  private void insertTriggerForPackageName(Uri... registrants) {
    for (Uri registrant : registrants) {
      Trigger validTrigger =
          TriggerFixture.getValidTriggerBuilder().setRegistrant(registrant).build();

      mDatastoreManager.runInTransaction((dao) -> dao.insertTrigger(validTrigger));
    }
  }

  private void setupSourceAndTriggerData() {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();
    List<Source> sourcesList = new ArrayList<>();
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S1")
            .setRegistrant(APP_TWO_SOURCES)
            .setPublisher(APP_TWO_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S2")
            .setRegistrant(APP_TWO_SOURCES)
            .setPublisher(APP_TWO_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S3")
            .setRegistrant(APP_ONE_SOURCE)
            .setPublisher(APP_ONE_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S4")
            .setRegistrant(APP_ONE_SOURCE)
            .setPublisher(APP_ONE_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .setStatus(Source.Status.MARKED_TO_DELETE)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S5")
            .setRegistrant(APP_TWO_SOURCES)
            .setPublisher(APP_TWO_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .setStatus(Source.Status.MARKED_TO_DELETE)
            .build());
    sourcesList.forEach(source -> insertSource(source, source.getId()));
    List<Trigger> triggersList = new ArrayList<>();
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T1")
            .setRegistrant(APP_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T2")
            .setRegistrant(APP_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T3")
            .setRegistrant(APP_ONE_DESTINATION)
            .build());

    // Add web triggers.
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T4")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T5")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T7")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T8")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T9")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_TWO_DESTINATION_WITH_PATH)
            .build());

    for (Trigger trigger : triggersList) {
      ContentValues values = new ContentValues();
      values.put("_id", trigger.getId());
      values.put("registrant", trigger.getRegistrant().toString());
      values.put("attribution_destination", trigger.getAttributionDestination().toString());
      long row = db.insert("msmt_trigger", null, values);
      assertNotEquals("Trigger insertion failed", -1, row);
    }
  }

  private Trigger createWebTrigger(Uri attributionDestination) {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("ID" + mValueId++)
        .setAttributionDestination(attributionDestination)
        .setRegistrant(APP_BROWSER)
        .build();
  }

  private Trigger createAppTrigger(Uri registrant, Uri destination) {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("ID" + mValueId++)
        .setAttributionDestination(destination)
        .setRegistrant(registrant)
        .build();
  }

  private void addTriggersToDatabase(List<Trigger> triggersList) {
    SQLiteDatabase db = MeasurementDbHelper.getInstance().safeGetWritableDatabase();

    for (Trigger trigger : triggersList) {
      ContentValues values = new ContentValues();
      values.put("_id", trigger.getId());
      values.put("registrant", trigger.getRegistrant().toString());
      values.put("attribution_destination", trigger.getAttributionDestination().toString());
      long row = db.insert("msmt_trigger", null, values);
      assertNotEquals("Trigger insertion failed", -1, row);
    }
  }

  private void setupSourceDataForPublisherTypeWeb() {
    List<Source> sourcesList = new ArrayList<>();
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("W1")
            .setPublisher(WEB_PUBLISHER_ONE)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("W21")
            .setPublisher(WEB_PUBLISHER_TWO)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("W22")
            .setPublisher(WEB_PUBLISHER_TWO)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("W23")
            .setPublisher(WEB_PUBLISHER_TWO)
            .setPublisherType(EventSurfaceType.WEB)
            .setStatus(Source.Status.MARKED_TO_DELETE)
            .build());
    sourcesList.add(
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S3")
            .setPublisher(WEB_PUBLISHER_THREE)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.forEach(source -> insertSource(source, source.getId()));
  }

  private Source.Builder createSourceForIATest(
      String id,
      long currentTime,
      long priority,
      int eventTimePastDays,
      boolean expiredIAWindow,
      String enrollmentId) {
    return createSourceForIATest(
        id,
        currentTime,
        priority,
        eventTimePastDays,
        expiredIAWindow,
        enrollmentId,
        REGISTRATION_ORIGIN);
  }

  private Source.Builder createSourceForIATest(
      String id,
      long currentTime,
      long priority,
      int eventTimePastDays,
      boolean expiredIAWindow,
      String enrollmentId,
      Uri registrationOrigin) {
    return new Source.Builder()
        .setId(id)
        .setPublisher(Uri.parse("android-app://com.example.sample"))
        .setRegistrant(Uri.parse("android-app://com.example.sample"))
        .setEnrollmentId(enrollmentId)
        .setExpiryTime(currentTime + DAYS.toMillis(30))
        .setInstallAttributionWindow(DAYS.toMillis(expiredIAWindow ? 0 : 30))
        .setAppDestinations(List.of(INSTALLED_PACKAGE))
        .setEventTime(currentTime - DAYS.toMillis(eventTimePastDays == -1 ? 10 : eventTimePastDays))
        .setPriority(priority == -1 ? 100 : priority)
        .setRegistrationOrigin(registrationOrigin);
  }

  private AggregateReport generateMockAggregateReport(String attributionDestination, int id) {
    return new AggregateReport.Builder()
        .setId(String.valueOf(id))
        .setAttributionDestination(Uri.parse(attributionDestination))
        .build();
  }

  private AggregateReport generateMockAggregateReport(
      String attributionDestination, int id, String sourceId, String api) {
    return new AggregateReport.Builder()
        .setId(String.valueOf(id))
        .setSourceId(sourceId)
        .setAttributionDestination(Uri.parse(attributionDestination))
        .setApi(api)
        .build();
  }

  private AggregateReport generateMockAggregateReport(
      String attributionDestination, int id, String sourceId, long reportTime) {
    return new AggregateReport.Builder()
        .setId(String.valueOf(id))
        .setSourceId(sourceId)
        .setAttributionDestination(Uri.parse(attributionDestination))
        .setScheduledReportTime(reportTime)
        .build();
  }

  private EventReport generateMockEventReport(String attributionDestination, int id) {
    return new EventReport.Builder()
        .setId(String.valueOf(id))
        .setAttributionDestinations(List.of(Uri.parse(attributionDestination)))
        .build();
  }

  private EventReport generateMockEventReport(
      String attributionDestination, int id, long reportTime) {
    return new EventReport.Builder()
        .setId(String.valueOf(id))
        .setAttributionDestinations(List.of(Uri.parse(attributionDestination)))
        .setReportTime(reportTime)
        .build();
  }

  private void assertAggregateReportCount(
      List<String> attributionDestinations, int destinationType, List<Integer> expectedCounts) {
    IntStream.range(0, attributionDestinations.size())
        .forEach(
            i -> {
              DatastoreManager.ThrowingCheckedFunction<Integer> aggregateReportCountPerDestination =
                  measurementDao ->
                      measurementDao.getNumAggregateReportsPerDestination(
                          Uri.parse(attributionDestinations.get(i)), destinationType);
              assertEquals(
                  expectedCounts.get(i),
                  mDatastoreManager
                      .runInTransactionWithResult(aggregateReportCountPerDestination)
                      .orElseThrow());
            });
  }

  private void assertEventReportCount(
      List<String> attributionDestinations, int destinationType, List<Integer> expectedCounts) {
    IntStream.range(0, attributionDestinations.size())
        .forEach(
            i -> {
              DatastoreManager.ThrowingCheckedFunction<Integer> numEventReportsPerDestination =
                  measurementDao ->
                      measurementDao.getNumEventReportsPerDestination(
                          Uri.parse(attributionDestinations.get(i)), destinationType);
              assertEquals(
                  expectedCounts.get(i),
                  mDatastoreManager
                      .runInTransactionWithResult(numEventReportsPerDestination)
                      .orElseThrow());
            });
  }

  private List<String> createAppDestinationVariants(int destinationNum) {
    return Arrays.asList(
        "android-app://subdomain.destination-" + destinationNum + ".app/abcd",
        "android-app://subdomain.destination-" + destinationNum + ".app",
        "android-app://destination-" + destinationNum + ".app/abcd",
        "android-app://destination-" + destinationNum + ".app",
        "android-app://destination-" + destinationNum + ".ap");
  }

  private List<String> createWebDestinationVariants(int destinationNum) {
    return Arrays.asList(
        "https://subdomain.destination-" + destinationNum + ".com/abcd",
        "https://subdomain.destination-" + destinationNum + ".com",
        "https://destination-" + destinationNum + ".com/abcd",
        "https://destination-" + destinationNum + ".com",
        "https://destination-" + destinationNum + ".co");
  }

  private boolean getInstallAttributionStatus(String sourceDbId, SQLiteDatabase db) {
    Cursor cursor =
        db.query(
            SourceContract.TABLE,
            new String[] {SourceContract.IS_INSTALL_ATTRIBUTED},
            SourceContract.ID + " = ? ",
            new String[] {sourceDbId},
            null,
            null,
            null,
            null);
    assertTrue(cursor.moveToFirst());
    return cursor.getInt(0) == 1;
  }

  private Long getInstallAttributionInstallTime(String sourceDbId, SQLiteDatabase db) {
    Cursor cursor =
        db.query(
            SourceContract.TABLE,
            new String[] {SourceContract.INSTALL_TIME},
            SourceContract.ID + " = ? ",
            new String[] {sourceDbId},
            null,
            null,
            null,
            null);
    assertTrue(cursor.moveToFirst());
    if (!cursor.isNull(0)) {
      return cursor.getLong(0);
    }
    return null;
  }

  private void removeSources(List<String> dbIds, SQLiteDatabase db) {
    db.delete(
        SourceContract.TABLE,
        SourceContract.ID + " IN ( ? )",
        new String[] {String.join(",", dbIds)});
  }

  private static void maybeInsertSourceDestinations(
      SQLiteDatabase db, Source source, String sourceId) {
    if (source.getAppDestinations() != null) {
      for (Uri appDestination : source.getAppDestinations()) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceDestination.SOURCE_ID, sourceId);
        values.put(MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.APP);
        values.put(MeasurementTables.SourceDestination.DESTINATION, appDestination.toString());
        long row = db.insert(MeasurementTables.SourceDestination.TABLE, null, values);
        assertNotEquals("Source app destination insertion failed", -1, row);
      }
    }
    if (source.getWebDestinations() != null) {
      for (Uri webDestination : source.getWebDestinations()) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceDestination.SOURCE_ID, sourceId);
        values.put(MeasurementTables.SourceDestination.DESTINATION_TYPE, EventSurfaceType.WEB);
        values.put(MeasurementTables.SourceDestination.DESTINATION, webDestination.toString());
        long row = db.insert(MeasurementTables.SourceDestination.TABLE, null, values);
        assertNotEquals("Source web destination insertion failed", -1, row);
      }
    }
  }

  private static Attribution.Builder getAttributionBuilder(Source source, Trigger trigger) {
    return new Attribution.Builder()
        .setEnrollmentId(source.getEnrollmentId())
        .setDestinationOrigin(source.getWebDestinations().get(0).toString())
        .setDestinationSite(source.getAppDestinations().get(0).toString())
        .setSourceOrigin(source.getPublisher().toString())
        .setSourceSite(source.getPublisher().toString())
        .setRegistrant(source.getRegistrant().toString())
        .setTriggerTime(trigger.getTriggerTime() - MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS + 1)
        .setRegistrationOrigin(trigger.getRegistrationOrigin());
  }

  /** Create {@link Attribution} object from SQLite datastore. */
  private static Attribution constructAttributionFromCursor(Cursor cursor) {
    Attribution.Builder builder = new Attribution.Builder();
    int index = cursor.getColumnIndex(AttributionContract.ID);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setId(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.SCOPE);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setScope(cursor.getInt(index));
    }
    index = cursor.getColumnIndex(AttributionContract.SOURCE_SITE);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setSourceSite(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.SOURCE_ORIGIN);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setSourceOrigin(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.DESTINATION_SITE);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setDestinationSite(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.DESTINATION_ORIGIN);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setDestinationOrigin(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.ENROLLMENT_ID);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setEnrollmentId(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.TRIGGER_TIME);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setTriggerTime(cursor.getLong(index));
    }
    index = cursor.getColumnIndex(AttributionContract.REGISTRANT);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setRegistrant(cursor.getString(index));
    }
    index = cursor.getColumnIndex(AttributionContract.REGISTRATION_ORIGIN);
    if (index > -1 && !cursor.isNull(index)) {
      builder.setRegistrationOrigin(Uri.parse(cursor.getString(index)));
    }
    return builder.build();
  }

  private Attribution getAttribution(String attributionId, SQLiteDatabase db) {
    try (Cursor cursor =
        db.query(
            AttributionContract.TABLE,
            /* columns= */ null,
            AttributionContract.ID + " = ? ",
            new String[] {attributionId},
            /* groupBy= */ null,
            /* having= */ null,
            /* orderBy= */ null,
            /* limit= */ null)) {
      if (cursor.getCount() == 0) {
        return null;
      }
      cursor.moveToNext();
      return constructAttributionFromCursor(cursor);
    }
  }

  private static void insertAttributedTrigger(TriggerSpecs triggerSpecs, EventReport eventReport) {
    triggerSpecs
        .getAttributedTriggers()
        .add(
            new AttributedTrigger(
                eventReport.getTriggerId(),
                eventReport.getTriggerPriority(),
                eventReport.getTriggerData(),
                eventReport.getTriggerValue(),
                eventReport.getTriggerTime(),
                eventReport.getTriggerDedupKey(),
                eventReport.getTriggerDebugKey(),
                false));
  }

  private static void insertAttributedTrigger(
      List<AttributedTrigger> attributedTriggers, EventReport eventReport) {
    attributedTriggers.add(
        new AttributedTrigger(
            eventReport.getTriggerId(),
            eventReport.getTriggerData(),
            eventReport.getTriggerDedupKey()));
  }

  private static List<String> getRandomIdsWith(int numIds, String idToInclude) {
    List<String> result = new ArrayList<>();
    result.add(idToInclude);
    for (int i = 0; i < numIds; i++) {
      result.add(UUID.randomUUID().toString());
    }
    return result;
  }

  private static String getFirstSourceIdFromDatastore() {
    return getFirstIdFromDatastore(SourceContract.TABLE, SourceContract.ID);
  }

  private static String getFirstIdFromDatastore(String tableName, String idColumn) {
    try (Cursor cursor =
        MeasurementDbHelper.getInstance()
            .getReadableDatabase()
            .query(tableName, new String[] {idColumn}, null, null, null, null, null)) {
      assertTrue(cursor.moveToNext());
      return cursor.getString(cursor.getColumnIndex(idColumn));
    }
  }

  private static List<Uri> getNullableUriList(List<Uri> uris) {
    return uris == null ? null : uris;
  }

  private List<String> getMatchingSourceIds(Trigger trigger) {
    List<Source> result =
        mDatastoreManager
            .runInTransactionWithResult(
                measurementDao -> measurementDao.getMatchingActiveSources(trigger))
            .orElseThrow();
    return result.stream().map(Source::getId).collect(Collectors.toList());
  }

  private List<Source> getMatchingSources(Trigger trigger) {
    return mDatastoreManager
        .runInTransactionWithResult(
            measurementDao -> measurementDao.getMatchingActiveSources(trigger))
        .orElseThrow();
  }
}
