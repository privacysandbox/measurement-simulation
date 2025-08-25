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

package com.google.measurement.client.attribution;

import static com.google.measurement.client.Flags.MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION;
import static com.google.measurement.client.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.client.util.Time.roundDownToDay;
import static com.google.measurement.client.reporting.DebugReportApi.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.measurement.client.AdServicesConfig;
import com.google.measurement.client.ApplicationProvider;
import com.google.measurement.client.Attribution;
import com.google.measurement.client.AttributionConfig;
import com.google.measurement.client.Context;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.Flags;
import com.google.measurement.client.ITransaction;
import com.google.measurement.client.LogUtil;
import com.google.measurement.client.Pair;
import com.google.measurement.client.PrivacyParams;
import com.google.measurement.client.Source;
import com.google.measurement.client.SourceFixture;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.TriggerFixture;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebUtil;
import com.google.measurement.client.XnaSourceCreator;
import com.google.measurement.client.aggregation.AggregateAttributionData;
import com.google.measurement.client.aggregation.AggregateHistogramContribution;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.stats.AdServicesErrorLogger;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.stats.MeasurementAttributionStats;
import com.google.measurement.client.util.UnsignedLong;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit test for {@link AttributionJobHandler} */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AttributionJobHandlerTest {
  public static final String TEST_TRIGGER_CONTEXT_ID = "test_trigger_context_id";
  private static final String AGGREGATE_REPORT_DELAY_DELIMITER = ",";
  private static final String API = "attribution-reporting";
  private static final long SOURCE_TIME = 1690000000000L;
  private static final long TRIGGER_TIME = 1690000001000L;
  private static final long EXPIRY_TIME = 1692592000000L;
  private static final long LOOKBACK_WINDOW = 1000L;
  private static final Context sContext = ApplicationProvider.getApplicationContext();
  private static final Uri APP_DESTINATION = Uri.parse("android-app://com.example.app");
  private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web.example.test");
  private static final Uri PUBLISHER = Uri.parse("android-app://publisher.app");
  private static final Uri REGISTRATION_URI = WebUtil.validUri("https://subdomain.example.test");
  private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
  private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
  private static final String TRIGGER_ID = "triggerId1";
  private static final String EVENT_TRIGGERS =
      "["
          + "{"
          + "  \"trigger_data\": \"5\","
          + "  \"priority\": \"123\","
          + "  \"deduplication_key\": \"2\","
          + "  \"filters\": [{"
          + "    \"source_type\": [\"event\"]"
          + "   }]"
          + "}"
          + "]";

  private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
      "[{\"deduplication_key\": \"" + 10 + "\"" + " }" + "]";

  private static Trigger createAPendingTriggerEventScopeOnly() {
    return TriggerFixture.getValidTriggerBuilder()
        .setId(TRIGGER_ID)
        .setTriggerTime(TRIGGER_TIME)
        .setStatus(Trigger.Status.PENDING)
        .setEventTriggers(EVENT_TRIGGERS)
        .build();
  }

  DatastoreManager mDatastoreManager;

  AttributionJobHandler mHandler;

  EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

  SourceNoiseHandler mSourceNoiseHandler;

  @Mock IMeasurementDao mMeasurementDao;

  @Mock ITransaction mTransaction;

  @Mock Flags mFlags;

  @Mock AdServicesLogger mLogger;
  @Mock AdServicesErrorLogger mErrorLogger;
  @Mock DebugReportApi mDebugReportApi;
  @Mock AggregateDebugReportApi mAdrApi;

  class FakeDatastoreManager extends DatastoreManager {

    FakeDatastoreManager() {
      super(AttributionJobHandlerTest.this.mErrorLogger);
    }

    @Override
    public ITransaction createNewTransaction() {
      return mTransaction;
    }

    @Override
    public IMeasurementDao getMeasurementDao() {
      return mMeasurementDao;
    }

    @Override
    protected int getDataStoreVersion() {
      return 0;
    }
  }

  @Before
  public void before() {
    mDatastoreManager = new FakeDatastoreManager();
    mEventReportWindowCalcDelegate = spy(new EventReportWindowCalcDelegate(mFlags));
    mSourceNoiseHandler = spy(new SourceNoiseHandler(mFlags));
    mLogger = spy(AdServicesLoggerImpl.getInstance());
    mHandler =
        new AttributionJobHandler(
            mDatastoreManager,
            mFlags,
            mDebugReportApi,
            mEventReportWindowCalcDelegate,
            mSourceNoiseHandler,
            mLogger,
            new XnaSourceCreator(mFlags),
            mAdrApi);
    when(mFlags.getMeasurementEnableXNA()).thenReturn(false);
    when(mFlags.getMeasurementMaxEventAttributionPerRateLimitWindow())
        .thenReturn(Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    when(mFlags.getMeasurementMaxAggregateAttributionPerRateLimitWindow())
        .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    when(mFlags.getMeasurementMaxDistinctReportingOriginsInAttribution())
        .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION);
    when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(true);
    when(mFlags.getMeasurementMaxAttributionsPerInvocation())
        .thenReturn(Flags.DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
    when(mFlags.getMeasurementMaxEventReportsPerDestination())
        .thenReturn(Flags.MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
    when(mFlags.getMeasurementMaxAggregateReportsPerDestination())
        .thenReturn(MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
    when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(0f);
    when(mFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
        .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
    when(mFlags.getMeasurementEventReportsVtcEarlyReportingWindows())
        .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
    when(mFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
        .thenReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    when(mFlags.getMeasurementAggregateReportDelayConfig())
        .thenReturn(Flags.MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG);
    when(mFlags.getMeasurementMaxLengthOfTriggerContextId())
        .thenReturn(Flags.MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID);
    when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
        .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    when(mFlags.getMeasurementMaxReportStatesPerSourceRegistration())
        .thenReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
    when(mFlags.getMeasurementEnableV1SourceTriggerData())
        .thenReturn(Flags.MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA);
    when(mFlags.getMeasurementMaxAggregateReportsPerSource())
        .thenReturn(Flags.MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE);
  }

  @Test
  public void shouldIgnoreNonPendingTrigger() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.IGNORED)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao).getTrigger(trigger.getId());
    verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldIgnoreIfNoSourcesFound() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    verify(mDebugReportApi)
        .scheduleTriggerNoMatchingSourceDebugReport(
            trigger, mMeasurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
    verify(mAdrApi).scheduleTriggerNoMatchingSourceDebugReport(trigger, mMeasurementDao);
  }

  @Test
  public void shouldRejectBasedOnDedupKey() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"5\","
                    + "  \"priority\": \"123\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"event\"],"
                    + "    \"key_1\": [\"value_1\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .build();
    String attributionStatus =
        getAttributionStatus(
            List.of("triggerId2", "triggerId3"), List.of("1", "2"), List.of("1", "2"));
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventAttributionStatus(attributionStatus)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldRejectBasedOnDedupKey_dedupAlignFlagOff() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"5\","
                    + "  \"priority\": \"123\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"event\"],"
                    + "    \"key_1\": [\"value_1\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(Arrays.asList(new UnsignedLong(1L), new UnsignedLong(2L)))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow() throws DatastoreException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[{\"trigger_data\":\"1\"," + "\"filters\":[{\"source_type\": [\"event\"]}]" + "}]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime - 1)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow_secondTrigger()
      throws DatastoreException, JSONException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\":\"1\"}]")
            .setTriggerTime(triggerTime)
            .build();
    Trigger trigger2 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId2")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\":\"0\"}]")
            .setTriggerTime(triggerTime + 1L)
            .build();
    List<Trigger> triggers = new ArrayList<>();
    triggers.add(trigger1);
    triggers.add(trigger2);
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .setEventReportWindow(triggerTime + 1L)
            .build();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);

    assertEquals(
        AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
        mHandler.performPendingAttributions());
    // Verify trigger status updates.
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger1.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger2.getId())), eq(Trigger.Status.IGNORED));
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao, times(1)).insertEventReport(reportArg.capture());
    List<EventReport> newReportArgs = reportArg.getAllValues();
    assertEquals(1, newReportArgs.size());
    assertEquals(
        newReportArgs.get(0).getTriggerData(),
        triggers.get(0).parseEventTriggers(mFlags).get(0).getTriggerData());
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow_prioritisedSource()
      throws DatastoreException {
    String eventTriggers =
        "[{\"trigger_data\": \"5\","
            + "\"priority\": \"123\","
            + "\"filters\":[{\"key_1\":[\"value_1\"]}]"
            + "}]";
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .setTriggerTime(triggerTime)
            .build();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime + 1L)
            .build();
    // Second source has higher priority but the event report window ends before trigger time
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime + 1000)
            .setEventReportWindow(triggerTime - 1)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    assertEquals(1, matchingSourceList.size());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded_eventScopeOnly() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger);
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded_aggregateScopeOnly()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn(5L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger);
    verify(mMeasurementDao, never())
        .getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded_aggregateAndEventScope_eventLimited()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateAndEventTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger);
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, times(1)).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded_aggregateAndEventScope_aggregateLimited()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateAndEventTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger);
    verify(mMeasurementDao, times(1))
        .getAttributionsPerRateLimitWindow(Attribution.Scope.AGGREGATE, source, trigger);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, times(1)).insertEventReport(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotAddIfAdTechPrivacyBoundExceeded() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
            any(), any(), any(), anyLong(), anyLong()))
        .thenReturn(10);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .countDistinctReportingOriginsPerPublisherXDestInAttribution(
            any(), any(), any(), anyLong(), anyLong());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performPendingAttributions_vtcWithConfiguredReportsCount_attributeUptoConfigLimit()
      throws DatastoreException {
    // Setup
    doReturn(3).when(mFlags).getMeasurementVtcConfigurableMaxEventReportsCount();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    doReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()))
        .when(mMeasurementDao)
        .getSourceDestinations(source.getId());
    // 2 event reports already present for the source
    doReturn(Arrays.asList(mock(EventReport.class), mock(EventReport.class)))
        .when(mMeasurementDao)
        .getSourceEventReports(source);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    doReturn(trigger).when(mMeasurementDao).getTrigger(trigger.getId());
    doReturn(matchingSourceList).when(mMeasurementDao).getMatchingActiveSources(trigger);
    doReturn(Collections.singletonList(trigger.getId()))
        .when(mMeasurementDao)
        .getPendingTriggerIds();

    // Execution
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    // Assertion
    verify(mMeasurementDao, times(1)).insertEventReport(any());
  }

  @Test
  public void shouldIgnoreForMaxReportsPerSource() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport2 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport3 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotReplaceHighPriorityReports() throws DatastoreException {
    String eventTriggers =
        "["
            + "{"
            + "  \"trigger_data\": \"5\","
            + "  \"priority\": \"100\","
            + "  \"deduplication_key\": \"2\""
            + "}"
            + "]";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setEventTriggers(eventTriggers)
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(200L)
            .build();
    EventReport eventReport2 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport3 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldDoSimpleAttribution() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(any());

    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldDoSimpleAttribution_topLevelTriggerDataExactMatching()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableTriggerDataMatching()).thenReturn(true);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
            .setTriggerData(Set.of(new UnsignedLong(23L), new UnsignedLong(27L)))
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId(TRIGGER_ID)
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\": \"27\", \"deduplication_key\": \"2\"}]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("27"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(any());

    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    assertEquals(new UnsignedLong(27L), eventReport.getTriggerData());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldDoSimpleAttribution_topLevelTriggerDataModulusMatching()
      throws DatastoreException {
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setTriggerDataMatching(Source.TriggerDataMatching.MODULUS)
            .setTriggerData(
                Set.of(new UnsignedLong(0L), new UnsignedLong(1L), new UnsignedLong(2L)))
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId(TRIGGER_ID)
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\": \"4\", \"deduplication_key\": \"4\"}]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("4"), List.of("4"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(any());

    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    assertEquals(new UnsignedLong(1L), eventReport.getTriggerData());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldIgnoreIfNoSourcesFound_includeSourceRegistrationTime_dontTriggerNullReports()
      throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId(TRIGGER_ID)
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());

    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }

  @Test
  public void shouldIgnoreIfNoSourcesFound_excludeSourceRegistrationTime_triggerNullReports()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId(TRIGGER_ID)
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());

    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
        trigger, measurementAttributionStats);
    assertEquals(1, measurementAttributionStats.getNullAggregateReportCount());
  }

  @Test
  public void performPendingAttributions_generatesAggregateReport_excludeSourceRegistrationTime()
      throws JSONException, DatastoreException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
            .build();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(null)
            .setApi(API)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
  }

  @Test
  public void
      performPendingAttributions_generatesAggregateReport_excludeSourceRegistrationTime_includeContextId()
          throws JSONException, DatastoreException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
            .setTriggerContextId("test_context_id")
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
            .build();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(null)
            .setTriggerContextId(trigger.getTriggerContextId())
            .setApi(API)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mFlags.getMeasurementEnableTriggerContextId()).thenReturn(true);

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    AggregateReport actualReport = aggregateReportCaptor.getValue();
    assertAggregateReportsEqual(expectedAggregateReport, actualReport);
    assertEquals(trigger.getTriggerTime(), actualReport.getScheduledReportTime());
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_includeSourceRegistrationTime()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.INCLUDE)
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilder(trigger, source).build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttribution_excludeSourceRegistrationTime_noNullReports()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAggregatableSourceRegistrationTimeConfig(
                Trigger.SourceRegistrationTimeConfig.EXCLUDE)
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            // Because the trigger's SourceRegistrationTimeConfig is EXCLUDE, we should
            // get a null source registration time on the aggregate report
            .setSourceRegistrationTime(null)
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setIsFakeReport(false)
            .setApi(API)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled())
        .thenReturn(true);
    // A rate of 1 would guarantee a null report was generated if we were not checking the
    // SourceRegistrationTimeConfig on the trigger, but because we are checking, there
    // should be no null reports.
    when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);

    verify(mMeasurementDao, times(1)).insertAggregateReport(aggregateReportCaptor.capture());
    AggregateReport actualAggregateReport = aggregateReportCaptor.getValue();

    assertAggregateReportsEqual(expectedAggregateReport, actualAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports() throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilder(trigger, source).build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttributionWithAggregateReport_bumpedApiVersion()
      throws JSONException, DatastoreException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("1.0")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(SOURCE_TIME)
            .setApi(API)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_bumpedApiVersion()
      throws DatastoreException, JSONException {
    when(mFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilderV1(trigger, source).build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  private void captureAndAssertNullAggregateReportsWithRealReport(
      Trigger trigger, AggregateReport expectedAggregateReport) throws DatastoreException {
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    // There is a chance to create a null report for each day between 0 and max expiry,
    // except for the day that the source actually occurred.
    long invocations =
        MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
            / TimeUnit.DAYS.toSeconds(1);
    invocations++; // The real report will also be inserted.
    verify(mMeasurementDao, times((int) invocations))
        .insertAggregateReport(aggregateReportCaptor.capture());
    List<AggregateReport> reports = aggregateReportCaptor.getAllValues();

    List<AggregateReport> fakeReports =
        reports.stream()
            .filter(AggregateReport::isFakeReport)
            .sorted(Comparator.comparing(AggregateReport::getSourceRegistrationTime).reversed())
            .collect(Collectors.toList());

    for (int i = 0; i < fakeReports.size(); i++) {
      // i + 1 because the first attempt at creating a null report is skipped since the
      // fakeSourceTime would be equal to the actual source registration time.
      assertNullAggregateReport(
          fakeReports.get(i),
          trigger,
          Long.valueOf(trigger.getTriggerTime() - (i + 1) * TimeUnit.DAYS.toMillis(1)));
    }

    AggregateReport trueReport = reports.stream().filter(r -> !r.isFakeReport()).findFirst().get();
    assertAggregateReportsEqual(expectedAggregateReport, trueReport);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(30, measurementAttributionStats.getNullAggregateReportCount());
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_triggerOnLastPossibleDay()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    // Add one day because attribution is eligible from 0 to max days, inclusive.
    long maxDays =
        (MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
                + TimeUnit.DAYS.toSeconds(1))
            * TimeUnit.SECONDS.toMillis(1);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(roundDownToDay(SOURCE_TIME) + maxDays - 1)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(roundDownToDay(SOURCE_TIME) + maxDays)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .build();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(SOURCE_TIME)
            .setIsFakeReport(false)
            .setApi(API)
            .build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    // There is a chance to create a null report for each day between 0 and max expiry
    long invocations =
        MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS
            / TimeUnit.DAYS.toSeconds(1);
    invocations++; // The real report will also be inserted.
    verify(mMeasurementDao, times((int) invocations))
        .insertAggregateReport(aggregateReportCaptor.capture());
    List<AggregateReport> reports = aggregateReportCaptor.getAllValues();

    List<AggregateReport> fakeReports =
        reports.stream()
            .filter(AggregateReport::isFakeReport)
            .sorted(Comparator.comparing(AggregateReport::getSourceRegistrationTime).reversed())
            .collect(Collectors.toList());

    for (int i = 0; i < fakeReports.size(); i++) {
      assertNullAggregateReport(
          fakeReports.get(i),
          trigger,
          Long.valueOf(trigger.getTriggerTime() - i * TimeUnit.DAYS.toMillis(1)));
    }

    AggregateReport trueReport = reports.stream().filter(r -> !r.isFakeReport()).findFirst().get();
    assertAggregateReportsEqual(expectedAggregateReport, trueReport);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(30, measurementAttributionStats.getNullAggregateReportCount());
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_debugKey_noPermission()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilder(trigger, source).setTriggerDebugKey(null).build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_debugKey_hasArDebugPermission()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();
    Uri webDestination = WebUtil.validUri("https://example.com");
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .setArDebugPermission(true)
            .setDestinationType(EventSurfaceType.WEB)
            .setAttributionDestination(webDestination)
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .setPublisherType(EventSurfaceType.WEB)
            .setPublisher(WebUtil.validUri("https://web.publisher.test"))
            .setWebDestinations(List.of(webDestination))
            .build();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilder(trigger, source)
            .setAttributionDestination(webDestination)
            .build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttributionWithNullReports_debugKey_hasAdIdPermission()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .setAdIdPermission(true)
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        getExpectedAggregateReportBuilder(trigger, source).build();

    setupTestForNullAggregateReport(trigger, source);

    mHandler.performPendingAttributions();

    captureAndAssertNullAggregateReportsWithRealReport(trigger, expectedAggregateReport);
  }

  @Test
  public void shouldDoSimpleAttribution_dedupAlignFlagOff() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        sourceArg.getValue().getEventReportDedupKeys(),
        Collections.singletonList(new UnsignedLong(2L)));
    verify(mMeasurementDao).insertEventReport(any());

    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldIgnoreLowPrioritySourceWhileAttribution() throws DatastoreException {
    String eventTriggers =
        "["
            + "{"
            + "  \"trigger_data\": \"5\","
            + "  \"priority\": \"123\","
            + "  \"deduplication_key\": \"2\","
            + "  \"filters\": [{"
            + "    \"key_1\": [\"value_1\"] "
            + "   }]"
            + "}"
            + "]";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .setTriggerTime(3)
            .build();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(1L)
            .setExpiryTime(30)
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(2L)
            .setExpiryTime(30)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));

    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao)
        .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source2.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_sourceDeactivationAfterFilteringFlagOn_ignoresWithoutAttribution()
      throws DatastoreException {
    String eventTriggers =
        "[{"
            + "  \"trigger_data\": \"5\","
            + "  \"priority\": \"123\","
            + "  \"deduplication_key\": \"2\","
            + "  \"filters\": [{"
            + "    \"key_1\": [\"value_1\"] "
            + "   }]"
            + "}]";
    // Missing top level filters match anything. Competing sources are ignored directly after.
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .setTriggerTime(3)
            .build();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(1L)
            .setExpiryTime(30)
            // Filters match the trigger's event triggers but the Source is not matched
            // since it has lower priority.
            .setFilterDataString("{\"key_1\":[\"1234\",\"value_1\"]}")
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(2L)
            .setExpiryTime(30)
            // Filters do not match the trigger's event triggers so a report is not
            // generated.
            .setFilterDataString("{\"key_1\":[\"no_match\"]}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));

    when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(true);

    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao)
        .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_sourceDeactivationAfterFilteringFlagOff_doesNotIgnoreWithoutAttr()
      throws DatastoreException {
    String eventTriggers =
        "[{"
            + "  \"trigger_data\": \"5\","
            + "  \"priority\": \"123\","
            + "  \"deduplication_key\": \"2\","
            + "  \"filters\": [{"
            + "    \"key_1\": [\"value_1\"] "
            + "   }]"
            + "}]";
    // Missing top level filters match anything.
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .setTriggerTime(3)
            .build();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(1L)
            .setExpiryTime(30)
            // Filters match but the Source is not matched since it has lower priority.
            .setFilterDataString("{\"key_1\":[\"1234\",\"value_1\"]}")
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(2L)
            .setExpiryTime(30)
            // Filters do not match, attribution flow returns and does not ignore
            // competing sources.
            .setFilterDataString("{\"key_1\":[\"1234\",\"no_match\"]}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));

    when(mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()).thenReturn(false);

    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    // Attribution did not occur and competing sources are not ignored.
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    assertEquals(1, matchingSourceList.size());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldReplaceLowPriorityReportWhileAttribution() throws DatastoreException {
    String eventTriggers =
        "["
            + "{"
            + "  \"trigger_data\": \"5\","
            + "  \"priority\": \"200\","
            + "  \"deduplication_key\": \"2\""
            + "}"
            + "]";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setEventTriggers(eventTriggers)
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>())
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAppDestinations(List.of(APP_DESTINATION))
            .setPublisherType(EventSurfaceType.APP)
            .setPublisher(PUBLISHER)
            .build();
    doReturn(5L)
        .when(mEventReportWindowCalcDelegate)
        .getReportingTime(any(Source.class), anyLong(), anyInt());
    doReturn(EventReportWindowCalcDelegate.MomentPlacement.WITHIN)
        .when(mEventReportWindowCalcDelegate)
        .fallsWithinWindow(any(Source.class), any(Trigger.class), any(UnsignedLong.class));
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(100L)
            .setReportTime(5L)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .build();
    EventReport eventReport2 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.DELIVERED)
            .setReportTime(5L)
            .setAttributionDestinations(source.getAppDestinations())
            .build();
    EventReport eventReport3 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.DELIVERED)
            .setReportTime(5L)
            .setAttributionDestinations(source.getAppDestinations())
            .build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(List.of(APP_DESTINATION), List.of()));
    mHandler.performPendingAttributions();
    // One call to getAttributionsPerRateLimitWindow for aggregate attribution.
    verify(mMeasurementDao, times(1)).getAttributionsPerRateLimitWindow(anyInt(), any(), any());
    verify(mMeasurementDao).deleteEventReportAndAttribution(eventReport1);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldRollbackOnFailure() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    // Failure
    doThrow(new DatastoreException("Simulating failure"))
        .when(mMeasurementDao)
        .insertEventReport(any(EventReport.class));
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao).getTrigger(anyString());
    verify(mMeasurementDao).getMatchingActiveSources(any());
    verify(mMeasurementDao, times(2)).getAttributionsPerRateLimitWindow(anyInt(), any(), any());
    verify(mMeasurementDao, times(1)).insertEventReport(any());
    verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction).rollback();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldRollbackOnFailure_dedupAlignFlagOff() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    // Failure
    when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);
    doThrow(new DatastoreException("Simulating failure"))
        .when(mMeasurementDao)
        .updateSourceEventReportDedupKeys(any());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao).getTrigger(anyString());
    verify(mMeasurementDao).getMatchingActiveSources(any());
    verify(mMeasurementDao, times(2)).getAttributionsPerRateLimitWindow(anyInt(), any(), any());
    verify(mMeasurementDao, never()).updateTriggerStatus(any(), anyInt());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction).rollback();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldPerformMultipleAttributions() throws DatastoreException, JSONException {
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .build();
    Trigger trigger2 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId2")
            .setTriggerTime(TRIGGER_TIME + 1000L)
            .setStatus(Trigger.Status.PENDING)
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"5\","
                    + "  \"priority\": \"123\","
                    + "  \"deduplication_key\": \"2\""
                    + "}"
                    + "]")
            .build();
    List<Trigger> triggers = new ArrayList<>();
    triggers.add(trigger1);
    triggers.add(trigger2);
    List<Source> matchingSourceList1 = new ArrayList<>();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    matchingSourceList1.add(source1);
    List<Source> matchingSourceList2 = new ArrayList<>();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME + 500L)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    matchingSourceList2.add(source2);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
    when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));

    assertEquals(
        AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
        mHandler.performPendingAttributions());
    // Verify trigger status updates.
    verify(mMeasurementDao, times(2)).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
    // Verify source dedup key updates.
    String expectedAttributionStatus1 =
        getAttributionStatus(List.of(trigger1.getId()), List.of("5"), List.of("1"));
    String expectedAttributionStatus2 =
        getAttributionStatus(List.of(trigger2.getId()), List.of("5"), List.of("2"));
    ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
    verify(mMeasurementDao, times(2))
        .updateSourceAttributedTriggers(sourceIdArg.capture(), attributionStatusArg.capture());
    List<String> sourceIds = sourceIdArg.getAllValues();
    List<String> attributionStatuses = attributionStatusArg.getAllValues();
    assertEquals(source1.getId(), sourceIds.get(0));
    assertEquals(expectedAttributionStatus1, attributionStatuses.get(0));
    assertEquals(source2.getId(), sourceIds.get(1));
    assertEquals(expectedAttributionStatus2, attributionStatuses.get(1));
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao, times(2)).insertEventReport(reportArg.capture());
    List<EventReport> newReportArgs = reportArg.getAllValues();
    boolean flexEventReportFlag = true;
    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    for (int i = 0; i < newReportArgs.size(); i++) {
      assertEquals(
          newReportArgs.get(i).getTriggerDedupKey(),
          triggers.get(i).parseEventTriggers(mFlags).get(0).getDedupKey());
      assertEquals(
          newReportArgs.get(i).getRegistrationOrigin(), triggers.get(i).getRegistrationOrigin());
    }
  }

  @Test
  public void shouldAttributedToInstallAttributedSource() throws DatastoreException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("trigger1")
            .setStatus(Trigger.Status.PENDING)
            .setTriggerTime(triggerTime)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"5\","
                    + "  \"priority\": \"123\","
                    + "  \"deduplication_key\": \"2\""
                    + "}"
                    + "]")
            .build();
    // Lower priority and older priority source.
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setInstallAttributed(true)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(10))
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(28))
            .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setEventId(new UnsignedLong(2L))
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source2);
    matchingSourceList.add(source1);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao)
        .updateSourceStatus(eq(List.of(source2.getId())), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    assertEquals(source2.getEventId(), matchingSourceList.get(0).getEventId());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source1.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
  }

  @Test
  public void shouldNotAttributeToOldInstallAttributedSource() throws DatastoreException {
    // Setup
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(10);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("trigger1")
            .setStatus(Trigger.Status.PENDING)
            .setTriggerTime(triggerTime)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"5\","
                    + "  \"priority\": \"123\","
                    + "  \"deduplication_key\": \"2\""
                    + "}"
                    + "]")
            .build();
    // Lower Priority. Install cooldown Window passed.
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setInstallAttributed(true)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(3))
            .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    Source source2 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("source2")
            .setEventId(new UnsignedLong(2L))
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source2);
    matchingSourceList.add(source1);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source2.getId()))
        .thenReturn(Pair.create(source2.getAppDestinations(), source2.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);

    // Assertion
    verify(mMeasurementDao)
        .updateSourceStatus(eq(List.of(source1.getId())), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    assertEquals(source1.getEventId(), matchingSourceList.get(0).getEventId());
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    ArgumentCaptor<String> sourceIdArg = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> attributionStatusArg = ArgumentCaptor.forClass(String.class);
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(sourceIdArg.capture(), attributionStatusArg.capture());
    assertEquals(source2.getId(), sourceIdArg.getValue());
    assertEquals(expectedAttributionStatus, attributionStatusArg.getValue());
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
  }

  @Test
  public void shouldNotGenerateReportForAttributionModeFalsely() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.FALSELY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void shouldNotGenerateReportForAttributionModeNever() throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.NEVER)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performPendingAttributions_GeneratesEventReport_WithReportingOriginOfTrigger()
      throws DatastoreException {
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .build();
    List<Source> matchingSourceList1 = new ArrayList<>();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
            .build();
    matchingSourceList1.add(source1);
    when(mMeasurementDao.getPendingTriggerIds()).thenReturn(Arrays.asList(trigger1.getId()));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));

    assertEquals(
        AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
        mHandler.performPendingAttributions());
    // Verify trigger status updates.
    verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(
        WebUtil.validUri("https://trigger.example.test"), eventReport.getRegistrationOrigin());
  }

  @Test
  public void performPendingAttributions_GeneratesEventReport_WithCoarseDestinations()
      throws DatastoreException {
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .build();
    List<Source> matchingSourceList1 = new ArrayList<>();
    Source source1 =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
            .setAppDestinations(Collections.singletonList(APP_DESTINATION))
            .setWebDestinations(Collections.singletonList(WEB_DESTINATION))
            .setCoarseEventReportDestinations(true)
            .build();
    matchingSourceList1.add(source1);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger1.getId()));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source1.getId()))
        .thenReturn(Pair.create(source1.getAppDestinations(), source1.getWebDestinations()));
    when(mFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(true);

    assertEquals(
        AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED,
        mHandler.performPendingAttributions());
    // Verify trigger status updates.
    verify(mMeasurementDao).updateTriggerStatus(any(), eq(Trigger.Status.ATTRIBUTED));
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(
        WebUtil.validUri("https://trigger.example.test"), eventReport.getRegistrationOrigin());
    List<Uri> reportDestinations = eventReport.getAttributionDestinations();
    assertEquals(2, reportDestinations.size());
    assertEquals(APP_DESTINATION, reportDestinations.get(0));
    assertEquals(WEB_DESTINATION, reportDestinations.get(1));
  }

  @Test
  public void shouldObserveFlagOverriddenAggregateReportDelay()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();

    long reportMinDelay = TimeUnit.MINUTES.toMillis(61);
    long reportDelaySpan = TimeUnit.MINUTES.toMillis(10);
    when(mFlags.getMeasurementAggregateReportDelayConfig())
        .thenReturn(String.valueOf(reportMinDelay) + "," + String.valueOf(reportDelaySpan));

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    // Assert expected aggregate report time range
    long lowerBound = TRIGGER_TIME + reportMinDelay;
    // Add slightly more delay to upper bound to account for execution.
    long upperBound = TRIGGER_TIME + reportMinDelay + reportDelaySpan + 1000L;
    AggregateReport capturedReport = aggregateReportCaptor.getValue();
    assertTrue(
        capturedReport.getScheduledReportTime() > lowerBound
            && capturedReport.getScheduledReportTime() < upperBound);
  }

  @Test
  public void shouldObserveDefaultAggregateReportDelay() throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    // Assert expected aggregate report time range
    long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
    // Add slightly more delay to upper bound to account for execution.
    long upperBound =
        TRIGGER_TIME
            + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
            + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN
            + 1000L;
    AggregateReport capturedReport = aggregateReportCaptor.getValue();
    assertTrue(
        capturedReport.getScheduledReportTime() > lowerBound
            && capturedReport.getScheduledReportTime() < upperBound);
  }

  @Test
  public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideIsNull()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();

    when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn(null);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    // Assert expected aggregate report time range
    long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
    // Add slightly more delay to upper bound to account for execution.
    long upperBound =
        TRIGGER_TIME
            + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
            + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN
            + 1000L;
    AggregateReport capturedReport = aggregateReportCaptor.getValue();
    assertTrue(
        capturedReport.getScheduledReportTime() > lowerBound
            && capturedReport.getScheduledReportTime() < upperBound);
  }

  @Test
  public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideSizeIsInvalid()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();

    when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("12");

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    // Assert expected aggregate report time range
    long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
    // Add slightly more delay to upper bound to account for execution.
    long upperBound =
        TRIGGER_TIME
            + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
            + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN
            + 1000L;
    AggregateReport capturedReport = aggregateReportCaptor.getValue();
    assertTrue(
        capturedReport.getScheduledReportTime() > lowerBound
            && capturedReport.getScheduledReportTime() < upperBound);
  }

  @Test
  public void shouldObserveDefaultAggregateReportDelayWhenFlagOverrideValueIsInvalid()
      throws DatastoreException, JSONException {
    Trigger trigger = getAggregateTrigger();
    Source source = getAggregateSource();

    when(mFlags.getMeasurementAggregateReportDelayConfig()).thenReturn("1200u0000r,3600000");

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    // Assert expected aggregate report time range
    long lowerBound = TRIGGER_TIME + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
    // Add slightly more delay to upper bound to account for execution.
    long upperBound =
        TRIGGER_TIME
            + PrivacyParams.AGGREGATE_REPORT_MIN_DELAY
            + PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN
            + 1000L;
    AggregateReport capturedReport = aggregateReportCaptor.getValue();
    assertTrue(
        capturedReport.getScheduledReportTime() > lowerBound
            && capturedReport.getScheduledReportTime() < upperBound);
  }

  @Test
  public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReport()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Source source = getAggregateSource();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(SOURCE_TIME)
            .setApi(API)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
  }

  @Test
  public void shouldDoSimpleAttribution_IncrementStatsEventReportCount() throws DatastoreException {
    Trigger trigger = createAPendingTriggerEventScopeOnly();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("2"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(any());

    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(0, measurementAttributionStats.getAggregateReportCount());
    assertEquals(0, measurementAttributionStats.getAggregateDebugReportCount());
    assertEquals(1, measurementAttributionStats.getEventReportCount());
    assertEquals(0, measurementAttributionStats.getEventDebugReportCount());
  }

  @Test
  public void shouldDoSimpleAttribution_IncrementStatsAggregateAndEventReportCount()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source = getAggregateSource();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao).insertEventReport(any());

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(1, measurementAttributionStats.getAggregateReportCount());
    assertEquals(0, measurementAttributionStats.getAggregateDebugReportCount());
    assertEquals(1, measurementAttributionStats.getEventReportCount());
    assertEquals(0, measurementAttributionStats.getEventDebugReportCount());
  }

  @Test
  public void performPendingAttributions_GeneratesAggregateReport_WithReportingOriginOfTrigger()
      throws JSONException, DatastoreException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .setRegistrationOrigin(WebUtil.validUri("https://source.example.test"))
            .build();

    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(WebUtil.validUri("https://trigger.example.test"))
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(SOURCE_TIME)
            .setApi(API)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    mHandler.performPendingAttributions();

    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
  }

  @Test
  public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReportWithDedupKey()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAggregateDeduplicationKeys(AGGREGATE_DEDUPLICATION_KEYS_1)
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceId1")
            .setEventTime(SOURCE_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .build();
    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .setSourceRegistrationTime(SOURCE_TIME)
            .setApi(API)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    mHandler.performPendingAttributions();
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());

    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    assertEquals(sourceArg.getValue().getAggregateContributions(), 32768 + 1644);
    ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateReportDedupKeys(sourceCaptor.capture());

    assertEquals(sourceCaptor.getValue().getAggregateReportDedupKeys().size(), 1);
    assertEquals(
        sourceCaptor.getValue().getAggregateReportDedupKeys().get(0), new UnsignedLong(10L));
  }

  @Test
  public void shouldNotGenerateAggregateReportWhenExceedingAggregateContributionsLimit()
      throws DatastoreException, JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setEventTriggers(EVENT_TRIGGERS)
            .build();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
            .setAggregateContributions(65536 - 32768 - 1644 + 1)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, never()).updateSourceAggregateContributions(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }

  @Test
  public void performAttributions_noRecords_returnSuccess() throws DatastoreException {
    // Setup
    when(mMeasurementDao.getPendingTriggerIds()).thenReturn(Collections.emptyList());

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertions
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
  }

  @Test
  public void performAttributions_triggerFilterSet_commonKeysDontIntersect_ignoreTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"]}, {"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],"
                    + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerFilters_commonKeysDontIntersect_ignoreTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],"
                    + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerNotFilterSet_commonKeysIntersect_ignoreTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setNotFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"]}, {"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2_x\"],"
                    + "  \"key_2\": [\"value_1_x\", \"value_2\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerNotFilters_commonKeysIntersect_ignoreTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setNotFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2_x\"],"
                    + "  \"key_2\": [\"value_1_x\", \"value_2\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerNotFilterSet_commonKeysDontIntersect_attributeTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setNotFilters(
                "[{"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {"
                    + "  \"key_2\": [\"value_21\", \"value_22_x\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerNotFiltersWithCommonKeysDontIntersect_attributeTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setNotFilters(
                "[{"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22_x\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_topLevelFilterSetMatch_attributeTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerSourceFiltersWithCommonKeysIntersect_attributeTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .setDebugKey(SOURCE_DEBUG_KEY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_sourceKey()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .setDebugKey(SOURCE_DEBUG_KEY)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_triggerKey()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_triggerSourceFiltersWithNoCommonKeys_attributeTrigger()
      throws DatastoreException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAggregatableReportWindow(TRIGGER_TIME + 1L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1x\": [\"value_11_x\", \"value_12_x\"],"
                    + "  \"key_2x\": [\"value_21_x\", \"value_22_x\"]"
                    + "}")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("5"), List.of("1"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(reportArg.capture());
    EventReport eventReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_eventLevelFilters_filterSet_attributeFirstMatchingTrigger()
      throws DatastoreException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"key_1\": [\"unmatched\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"ignored\": [\"ignored\"]}, {"
                    + "    \"key_1\": [\"unmatched\"]}, {"
                    + "    \"key_1\": [\"matched\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"matched\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setId("sourceId")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(triggerTime)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_eventLevelFilters_attributeFirstMatchingTrigger()
      throws DatastoreException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"key_1\": [\"value_1_x\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"key_1\": [\"value_1\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setId("sourceId")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(triggerTime)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_filterSet_eventLevelNotFilters_attributeFirstMatchingTrigger()
      throws DatastoreException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"not_filters\": [{"
                    + "    \"key_1\": [\"value_1\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"not_filters\": [{"
                    + "    \"key_1\": [\"value_1\"]}, {"
                    + "    \"key_2\": [\"value_2\"]}, {"
                    + "    \"key_1\": [\"matches_when_negated\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setId("sourceId")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(triggerTime)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_eventLevelNotFilters_attributeFirstMatchingTrigger()
      throws DatastoreException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"not_filters\": [{"
                    + "    \"key_1\": [\"value_1\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"not_filters\": [{"
                    + "    \"key_1\": [\"value_1_x\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setId("sourceId")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(triggerTime)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_eventLevelFiltersWithSourceType_attributeFirstMatchingTrigger()
      throws DatastoreException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"event\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"navigation\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setId("sourceId")
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_invalidAggregateValues_generateEventReportOnly()
      throws JSONException, DatastoreException {
    // Setup
    JSONArray triggerData = getAggregateTriggerData();
    String invalidAggregatableValuesWithFlagOff =
        "[{\"values\":{\"campaignCounts\":32768, \"geoValue\":1664}},{\"values\":{\"a\":1,"
            + " \"b\":2, \"c\":3}}]";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setAggregateTriggerData(triggerData.toString())
            .setAggregateValuesString(invalidAggregatableValuesWithFlagOff)
            .build();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao, times(1)).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_filterSet_eventLevelFiltersFailToMatch_aggregateReportOnly()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"product\": [\"value_11\"]}, {"
                    + "    \"key_1\": [\"value_11\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"product\": [\"value_21\"]}, {"
                    + "    \"key_1\": [\"value_21\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{\"product\":[\"1234\", \"2345\"]," + "\"key_1\": [\"value_1_y\", \"value_2_y\"]}")
            .setId("sourceId")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    // Verify aggregate report registration origin.
    ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
    AggregateReport aggregateReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_eventLevelFiltersFailToMatch_generateAggregateReportOnly()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"key_1\": [\"value_11\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"key_1\": [\"value_21\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{\"product\":[\"1234\",\"2345\"], \"key_1\": " + "[\"value_1_y\", \"value_2_y\"]}")
            .setId("sourceId")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_aggregateReportsExceedsLimitPerDestination_insertsOnlyEventReport()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1024;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mDebugReportApi)
        .scheduleTriggerDebugReport(
            any(), any(), eq("1024"), any(), eq(Type.TRIGGER_AGGREGATE_STORAGE_LIMIT));
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(Collections.singletonList(Type.TRIGGER_AGGREGATE_STORAGE_LIMIT)),
            eq(mMeasurementDao));
    verify(mMeasurementDao).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_aggregateReportsExceedsLimitPerSource_insertsOnlyEventReport()
      throws DatastoreException, JSONException {
    when(mFlags.getMeasurementMaxAggregateReportsPerSource()).thenReturn(20);
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 10;
    int numEventReportPerDestination = 10;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.countNumAggregateReportsPerSource(
            eq(source.getId()), eq(AttributionJobHandler.API)))
        .thenReturn(21);

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mDebugReportApi)
        .scheduleTriggerDebugReport(
            any(), any(), eq("20"), any(), eq(Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS));
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(Collections.singletonList(Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS)),
            eq(mMeasurementDao));
    verify(mMeasurementDao).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_separateReportsForAttributionRateLimitDisabled_sameReportType()
      throws DatastoreException, JSONException {
    // Setup
    when(mFlags.getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit())
        .thenReturn(false);

    Trigger trigger = getAggregateAndEventTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mDebugReportApi)
        .scheduleTriggerDebugReport(
            any(), any(), any(), any(), eq(Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT));
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(Collections.singletonList(Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT)),
            eq(mMeasurementDao));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_separateReportsForAttributionRateLimitEnabled_eventReport()
      throws DatastoreException, JSONException {
    // Setup
    when(mFlags.getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit())
        .thenReturn(true);

    Trigger trigger = getAggregateAndEventTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mDebugReportApi)
        .scheduleTriggerDebugReport(
            any(),
            any(),
            any(),
            any(),
            eq(Type.TRIGGER_EVENT_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT));
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(
                Collections.singletonList(
                    Type.TRIGGER_EVENT_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT)),
            eq(mMeasurementDao));

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_separateReportsForAttributionRateLimitEnabled_aggregateReport()
      throws DatastoreException, JSONException {
    // Setup
    when(mFlags.getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit())
        .thenReturn(true);

    Trigger trigger = getAggregateAndEventTrigger();
    Source source = getAggregateSource();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger))
        .thenReturn((long) Flags.MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.EVENT, source, trigger))
        .thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    Type reportType = Type.TRIGGER_AGGREGATE_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT;
    verify(mDebugReportApi).scheduleTriggerDebugReport(any(), any(), any(), any(), eq(reportType));
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(Collections.singletonList(reportType)),
            eq(mMeasurementDao));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_eventReportsExceedsLimit_insertsOnlyAggregateReport()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1024;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(Collections.singletonList(Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED)),
            eq(mMeasurementDao));
  }

  @Test
  public void performAttribution_bothReportsExceedLimit_doesNotInsertReport()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("S1")
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1024;
    int numEventReportPerDestination = 1024;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    doReturn(new Pair<>(Collections.singletonList(APP_DESTINATION), null))
        .when(mMeasurementDao)
        .getSourceDestinations(anyString());
    doReturn(EventReportWindowCalcDelegate.MomentPlacement.WITHIN)
        .when(mEventReportWindowCalcDelegate)
        .fallsWithinWindow(any(Source.class), any(Trigger.class), any(UnsignedLong.class));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mAdrApi)
        .scheduleTriggerAttributionErrorWithSourceDebugReport(
            eq(source),
            eq(trigger),
            eq(
                Arrays.asList(
                    Type.TRIGGER_AGGREGATE_STORAGE_LIMIT, Type.TRIGGER_EVENT_STORAGE_LIMIT)),
            eq(mMeasurementDao));
  }

  @Test
  public void performAttribution_aggregateAndEventReportsExceedsLimit_noReportInsertion()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1024;
    int numEventReportPerDestination = 1024;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_aggregateAndEventReportsDoNotExceedsLimit_ReportInsertion()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setExpiryTime(triggerTime + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    // Verify event report registration origin.
    ArgumentCaptor<EventReport> eventReportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao).insertEventReport(eventReportArg.capture());
    ArgumentCaptor<AggregateReport> aggReportArg = ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggReportArg.capture());
    assertNotNull(aggReportArg.getValue().getId());

    EventReport eventReport = eventReportArg.getValue();
    assertNotNull(eventReport.getId());
    assertEquals(REGISTRATION_URI, eventReport.getRegistrationOrigin());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    Set<String> expectedAttributionReportIds =
        Set.of(eventReport.getId(), aggReportArg.getValue().getId());

    ArgumentCaptor<Attribution> attributionArg = ArgumentCaptor.forClass(Attribution.class);
    verify(mMeasurementDao, times(2)).insertAttribution(attributionArg.capture());
    List<Attribution> attributions = attributionArg.getAllValues();
    assertEquals(2, attributions.size());
    Set<String> actualAttributionReportIds =
        Set.of(attributions.get(0).getReportId(), attributions.get(1).getReportId());
    assertEquals(expectedAttributionReportIds, actualAttributionReportIds);
  }

  @Test
  public void performAttributions_withXnaConfig_originalSourceWinsAndOtherIgnored()
      throws DatastoreException {
    // Setup
    String adtechEnrollment = "AdTech1-Ads";
    AttributionConfig attributionConfig =
        new AttributionConfig.Builder()
            .setSourceAdtech(adtechEnrollment)
            .setSourcePriorityRange(new Pair<>(1L, 1000L))
            .setSourceFilters(null)
            .setPriority(1L)
            .setExpiry(604800L)
            .setFilterData(null)
            .build();
    Trigger trigger =
        getXnaTriggerBuilder()
            .setFilters(null)
            .setNotFilters(null)
            .setAttributionConfig(
                new JSONArray(Collections.singletonList(attributionConfig.serializeAsJson(mFlags)))
                    .toString())
            .build();

    String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
    Source xnaSource =
        createXnaSourceBuilder()
            .setEnrollmentId(adtechEnrollment)
            // Priority changes to 1 for derived source
            .setPriority(100L)
            .setAggregateSource(aggregatableSource)
            .setFilterDataString(null)
            .setSharedAggregationKeys(
                new JSONArray(Arrays.asList("campaignCounts", "geoValue")).toString())
            .build();
    // winner due to install attribution and higher priority
    Source triggerEnrollmentSource1 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .build();

    Source triggerEnrollmentSource2 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .setInstallAttributed(false)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(xnaSource);
    matchingSourceList.add(triggerEnrollmentSource1);
    matchingSourceList.add(triggerEnrollmentSource2);
    when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
        .thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(triggerEnrollmentSource1.getId()))
        .thenReturn(
            Pair.create(
                triggerEnrollmentSource1.getAppDestinations(),
                triggerEnrollmentSource1.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao).insertEventReport(any());
    verify(mMeasurementDao, times(2)).insertAttribution(any());
    verify(mMeasurementDao)
        .insertIgnoredSourceForEnrollment(xnaSource.getId(), trigger.getEnrollmentId());
    verify(mMeasurementDao)
        .updateSourceStatus(
            eq(Collections.singletonList(triggerEnrollmentSource2.getId())),
            eq(Source.Status.IGNORED));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_withXnaConfig_derivedSourceWinsAndOtherIgnored()
      throws DatastoreException {
    // Setup
    String adtechEnrollment = "AdTech1-Ads";
    AttributionConfig attributionConfig =
        new AttributionConfig.Builder()
            .setExpiry(604800L)
            .setSourceAdtech(adtechEnrollment)
            .setSourcePriorityRange(new Pair<>(1L, 1000L))
            .setSourceFilters(null)
            .setPriority(50L)
            .setExpiry(604800L)
            .setFilterData(null)
            .build();
    Trigger trigger =
        getXnaTriggerBuilder()
            .setFilters(null)
            .setNotFilters(null)
            .setAttributionConfig(
                new JSONArray(Collections.singletonList(attributionConfig.serializeAsJson(mFlags)))
                    .toString())
            .build();

    String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
    // Its derived source will be winner due to install attribution and higher priority
    Source xnaSource =
        createXnaSourceBuilder()
            .setEnrollmentId(adtechEnrollment)
            // Priority changes to 50 for derived source
            .setPriority(1L)
            .setAggregateSource(aggregatableSource)
            .setFilterDataString(null)
            .setSharedAggregationKeys(
                new JSONArray(Arrays.asList("campaignCounts", "geoValue")).toString())
            .build();
    Source triggerEnrollmentSource1 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .build();

    Source triggerEnrollmentSource2 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .setInstallAttributed(false)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(xnaSource);
    matchingSourceList.add(triggerEnrollmentSource1);
    matchingSourceList.add(triggerEnrollmentSource2);
    when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
        .thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    // Verify aggregate report registration origin.
    ArgumentCaptor<AggregateReport> reportArg = ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(reportArg.capture());
    AggregateReport aggregateReport = reportArg.getValue();
    assertEquals(REGISTRATION_URI, aggregateReport.getRegistrationOrigin());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao).insertAttribution(any());
    verify(mMeasurementDao)
        .updateSourceStatus(
            eq(Arrays.asList(triggerEnrollmentSource1.getId(), triggerEnrollmentSource2.getId())),
            eq(Source.Status.IGNORED));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_xnaDisabled_derivedSourceIgnored() throws DatastoreException {
    // Setup
    String adtechEnrollment = "AdTech1-Ads";
    AttributionConfig attributionConfig =
        new AttributionConfig.Builder()
            .setExpiry(604800L)
            .setSourceAdtech(adtechEnrollment)
            .setSourcePriorityRange(new Pair<>(1L, 1000L))
            .setSourceFilters(null)
            .setPriority(50L)
            .setExpiry(604800L)
            .setFilterData(null)
            .build();
    Trigger trigger =
        getXnaTriggerBuilder()
            .setFilters(null)
            .setNotFilters(null)
            .setAttributionConfig(
                new JSONArray(Collections.singletonList(attributionConfig.serializeAsJson(mFlags)))
                    .toString())
            .build();

    String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
    // Its derived source will be winner due to install attribution and higher priority
    Source xnaSource =
        createXnaSourceBuilder()
            .setEnrollmentId(adtechEnrollment)
            // Priority changes to 50 for derived source
            .setPriority(1L)
            .setAggregateSource(aggregatableSource)
            .setFilterDataString(null)
            .setSharedAggregationKeys(
                new JSONArray(Arrays.asList("campaignCounts", "geoValue")).toString())
            .build();
    Source triggerEnrollmentSource1 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .build();

    Source triggerEnrollmentSource2 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .setInstallAttributed(false)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(xnaSource);
    matchingSourceList.add(triggerEnrollmentSource1);
    matchingSourceList.add(triggerEnrollmentSource2);
    when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
        .thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).insertAttribution(any());
    verify(mMeasurementDao, never())
        .updateSourceStatus(
            eq(Arrays.asList(triggerEnrollmentSource1.getId(), triggerEnrollmentSource2.getId())),
            eq(Source.Status.IGNORED));
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_flexEventReport_oneTriggerGenerateTwoReports()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"1\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"1000\","
                    + "  \"deduplication_key\": \"1\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    TriggerSpecs triggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .setTriggerSpecsString(triggerSpecs.encodeToJson())
            .setMaxEventLevelReports(triggerSpecs.getMaxReports())
            .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, times(2)).insertEventReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    assertEquals(1, source.getTriggerSpecs().getAttributedTriggers().size());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  /**
   * The triggerData in the trigger didn't match any one of the trigger data in the source
   * registration. No report generated
   */
  public void performAttribution_flexEventReport_triggerDataMismatch()
      throws DatastoreException, JSONException {
    // Setup
    long triggerTime = 234324L;
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"6\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"1000\","
                    + "  \"deduplication_key\": \"1\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(triggerTime)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    TriggerSpecs triggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(triggerTime + 1L)
            .setAggregatableReportWindow(triggerTime + 1L)
            .setTriggerSpecsString(triggerSpecs.encodeToJson())
            .setMaxEventLevelReports(triggerSpecs.getMaxReports())
            .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
            .build();
    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, never()).getSourceEventReports(any());
    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  /**
   * Status before attribution: 1 trigger attributed and 1 report generated; Incoming trigger
   * status: 2 new reports should be generated; Result: 2 reports written into DB and no competition
   * condition; debug reports and trigger debug keys populated only when all trigger contributors
   * have debug keys.
   */
  public void performAttribution_flexEventReportTwoNonCompetingTriggersAllDebugReports()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    UnsignedLong triggerData1 = new UnsignedLong(1L);
    UnsignedLong triggerData2 = new UnsignedLong(2L);
    UnsignedLong sourceDebugKey = new UnsignedLong(777L);
    UnsignedLong debugKey1 = new UnsignedLong(4L);
    UnsignedLong debugKey2 = new UnsignedLong(55L);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setDestinationType(EventSurfaceType.APP)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \""
                    + triggerData2
                    + "\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"123\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAdIdPermission(true)
            .setDebugKey(debugKey2)
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    final EventReport currentEventReport1 =
        new EventReport.Builder()
            .setId("100")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("android-app://com.ignored")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(3))
            .setStatus(EventReport.Status.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + 3000L)
            .setTriggerData(triggerData1)
            .setTriggerPriority(123L)
            .setTriggerValue(30)
            .setTriggerSummaryBucket(firstBucket)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerDebugKey(debugKey1)
            .build();

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);

    existingAttributes.put(triggerRecord1);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(baseTime)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(2))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(2))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .setAdIdPermission(true)
            .setDebugKey(sourceDebugKey)
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(new ArrayList<>(Collections.singletonList(currentEventReport1)));
    int numAggregateReportPerDestination = 10;
    int numEventReportPerDestination = 10;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    ArgumentCaptor<EventReport> insertedReportsCaptor = ArgumentCaptor.forClass(EventReport.class);
    ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
    verify(mMeasurementDao, times(1))
        .deleteFlexEventReportsAndAttributions(deletedReportsCaptor.capture());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    assertEquals(1, deletedReportsCaptor.getValue().size());
    List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
    assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
    assertEquals(firstBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
    assertEquals(secondBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
    assertEquals(triggerData1, insertedEventReports.get(0).getTriggerData());
    assertEquals(triggerData2, insertedEventReports.get(1).getTriggerData());
    assertEquals(triggerData2, insertedEventReports.get(2).getTriggerData());
    assertEquals(List.of(debugKey1), insertedEventReports.get(0).getTriggerDebugKeys());
    assertEquals(List.of(debugKey2), insertedEventReports.get(1).getTriggerDebugKeys());
    assertEquals(List.of(debugKey2), insertedEventReports.get(2).getTriggerDebugKeys());
    assertNull(insertedEventReports.get(0).getSourceDebugKey());
    assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
    assertEquals(sourceDebugKey, insertedEventReports.get(2).getSourceDebugKey());
    assertEquals(
        EventReport.DebugReportStatus.NONE, insertedEventReports.get(0).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.PENDING, insertedEventReports.get(1).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.PENDING, insertedEventReports.get(2).getDebugReportStatus());
    long reportTime = TimeUnit.DAYS.toMillis(2);
    assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  /*
   * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
   * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
   * trigger has higher priority and one previous report should be deleted; debug reports and
   * trigger debug keys populated only when all trigger contributors have debug keys.
   */
  public void performAttribution_flexEventReportSecondTriggerHigherPriorityAllDebugReports()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    UnsignedLong triggerData1 = new UnsignedLong(1L);
    UnsignedLong triggerData2 = new UnsignedLong(2L);
    UnsignedLong sourceDebugKey = new UnsignedLong(777L);
    UnsignedLong debugKey1 = new UnsignedLong(4L);
    UnsignedLong debugKey2 = new UnsignedLong(55L);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \""
                    + triggerData2
                    + "\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"1234\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 4800000)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAdIdPermission(true)
            .setDebugKey(debugKey2)
            .build();

    final EventReport.Builder eventReportBuilder =
        new EventReport.Builder()
            .setId("100")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setTriggerData(triggerData1)
            .setTriggerPriority(121L)
            .setTriggerValue(101)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerDebugKey(debugKey1);

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

    existingAttributes.put(triggerRecord1);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(
                new ArrayList<>(Collections.singletonList(new UnsignedLong(3L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .setAdIdPermission(true)
            .setDebugKey(sourceDebugKey)
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(
            new ArrayList<>(
                Arrays.asList(
                    eventReportBuilder.setTriggerSummaryBucket(firstBucket).build(),
                    eventReportBuilder.setTriggerSummaryBucket(secondBucket).build())));
    int numAggregateReportPerDestination = 0;
    int numEventReportPerDestination = 2;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    ArgumentCaptor<EventReport> insertedReportsCaptor = ArgumentCaptor.forClass(EventReport.class);
    ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
    verify(mMeasurementDao, times(1))
        .deleteFlexEventReportsAndAttributions(deletedReportsCaptor.capture());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
    assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
    assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
    assertEquals(firstBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
    assertEquals(triggerData2, insertedEventReports.get(0).getTriggerData());
    assertEquals(triggerData2, insertedEventReports.get(1).getTriggerData());
    assertEquals(triggerData1, insertedEventReports.get(2).getTriggerData());
    assertEquals(List.of(debugKey2), insertedEventReports.get(0).getTriggerDebugKeys());
    assertEquals(List.of(debugKey2), insertedEventReports.get(1).getTriggerDebugKeys());
    assertEquals(List.of(debugKey1), insertedEventReports.get(2).getTriggerDebugKeys());
    assertEquals(sourceDebugKey, insertedEventReports.get(0).getSourceDebugKey());
    assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
    assertNull(insertedEventReports.get(2).getSourceDebugKey());
    assertEquals(
        EventReport.DebugReportStatus.PENDING, insertedEventReports.get(0).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.PENDING, insertedEventReports.get(1).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.NONE, insertedEventReports.get(2).getDebugReportStatus());
    assertEquals(2, deletedReportsCaptor.getValue().size());
    long reportTime = TimeUnit.DAYS.toMillis(2);
    assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  /*
   * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
   * Incoming trigger status: 2 reports should be generated for triggerData 2 Result: incoming
   * trigger has lower priority, no previous report should be deleted and only 1 new report
   * inserted into DB; debug reports and trigger debug keys populated only when all trigger
   * contributors have debug keys.
   */
  public void performAttribution_flexEventReportSecondTriggerLowerPrioritySomeDebugReports()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    UnsignedLong triggerData1 = new UnsignedLong(1L);
    UnsignedLong triggerData2 = new UnsignedLong(2L);
    UnsignedLong sourceDebugKey = new UnsignedLong(777L);
    UnsignedLong debugKey2 = new UnsignedLong(55L);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \""
                    + triggerData2
                    + "\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"1\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAdIdPermission(true)
            .setDebugKey(debugKey2)
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    final EventReport.Builder eventReportBuilder =
        new EventReport.Builder()
            .setId("100")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + 3000)
            .setTriggerData(triggerData1)
            .setTriggerPriority(124L)
            .setSourceDebugKey(sourceDebugKey)
            .setTriggerValue(103)
            .setTriggerDedupKey(new UnsignedLong(3L));

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

    existingAttributes.put(triggerRecord1);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .setAdIdPermission(true)
            .setDebugKey(sourceDebugKey)
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(
            new ArrayList<>(
                Arrays.asList(
                    eventReportBuilder.setTriggerSummaryBucket(firstBucket).build(),
                    eventReportBuilder.setTriggerSummaryBucket(secondBucket).build())));
    int numAggregateReportPerDestination = 24;
    int numEventReportPerDestination = 35;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    ArgumentCaptor<EventReport> insertedReportsCaptor = ArgumentCaptor.forClass(EventReport.class);
    ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(mMeasurementDao, times(3)).insertEventReport(insertedReportsCaptor.capture());
    verify(mMeasurementDao, times(1))
        .deleteFlexEventReportsAndAttributions(deletedReportsCaptor.capture());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    assertEquals(2, deletedReportsCaptor.getValue().size());
    List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
    assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
    assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
    assertEquals(firstBucket, insertedEventReports.get(2).getTriggerSummaryBucket());
    assertEquals(triggerData1, insertedEventReports.get(0).getTriggerData());
    assertEquals(triggerData1, insertedEventReports.get(1).getTriggerData());
    assertEquals(triggerData2, insertedEventReports.get(2).getTriggerData());
    assertEquals(Collections.emptyList(), insertedEventReports.get(0).getTriggerDebugKeys());
    assertEquals(Collections.emptyList(), insertedEventReports.get(1).getTriggerDebugKeys());
    assertEquals(List.of(debugKey2), insertedEventReports.get(2).getTriggerDebugKeys());
    assertEquals(sourceDebugKey, insertedEventReports.get(0).getSourceDebugKey());
    assertEquals(sourceDebugKey, insertedEventReports.get(1).getSourceDebugKey());
    assertEquals(sourceDebugKey, insertedEventReports.get(2).getSourceDebugKey());
    assertEquals(
        EventReport.DebugReportStatus.NONE, insertedEventReports.get(0).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.NONE, insertedEventReports.get(1).getDebugReportStatus());
    assertEquals(
        EventReport.DebugReportStatus.PENDING, insertedEventReports.get(2).getDebugReportStatus());
    long reportTime = TimeUnit.DAYS.toMillis(2);
    assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(2).getReportTime() - baseTime);
    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    assertEquals(2, source.getTriggerSpecs().getAttributedTriggers().size());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  /*
   * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
   * Incoming trigger is prior to window start, attribution exits before altering event reports.
   */
  public void performAttribution_flexEventReportSecondTriggerWindowNotStarted_reportsUnaltered()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    UnsignedLong triggerData1 = new UnsignedLong(1L);
    UnsignedLong triggerData2 = new UnsignedLong(2L);
    UnsignedLong sourceDebugKey = new UnsignedLong(777L);
    UnsignedLong debugKey2 = new UnsignedLong(55L);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \""
                    + triggerData2
                    + "\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"1\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            // Trigger time is one hour before trigger specs start time
            .setTriggerTime(baseTime + TimeUnit.HOURS.toMillis(4))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAdIdPermission(true)
            .setDebugKey(debugKey2)
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    final EventReport.Builder eventReportBuilder =
        new EventReport.Builder()
            .setId("100")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + 3000)
            .setTriggerData(triggerData1)
            .setTriggerPriority(124L)
            .setSourceDebugKey(sourceDebugKey)
            .setTriggerValue(103)
            .setTriggerDedupKey(new UnsignedLong(3L));

    TriggerSpecs templateTriggerSpecs =
        SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

    existingAttributes.put(triggerRecord1);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource(null)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(null)
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .setAdIdPermission(true)
            .setDebugKey(sourceDebugKey)
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(
            new ArrayList<>(
                Arrays.asList(
                    eventReportBuilder.setTriggerSummaryBucket(firstBucket).build(),
                    eventReportBuilder.setTriggerSummaryBucket(secondBucket).build())));
    int numAggregateReportPerDestination = 24;
    int numEventReportPerDestination = 35;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));

    verify(mMeasurementDao, never()).getSourceEventReports(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).deleteFlexEventReportsAndAttributions(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  /*
   * Status before attribution: 1 trigger attributed and 2 report generated with triggerData 1;
   * Incoming trigger is prior to window start, attribution exits before altering event reports.
   */
  public void performAttribution_flexEventReportSecondTriggerWindowPassed_reportsUnaltered()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    UnsignedLong triggerData1 = new UnsignedLong(1L);
    UnsignedLong triggerData2 = new UnsignedLong(2L);
    UnsignedLong sourceDebugKey = new UnsignedLong(777L);
    UnsignedLong debugKey2 = new UnsignedLong(55L);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \""
                    + triggerData2
                    + "\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"1\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            // Trigger time is one day after trigger specs window ends
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(8))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAdIdPermission(true)
            .setDebugKey(debugKey2)
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    final EventReport.Builder eventReportBuilder =
        new EventReport.Builder()
            .setId("100")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + 3000)
            .setTriggerData(triggerData1)
            .setTriggerPriority(124L)
            .setSourceDebugKey(sourceDebugKey)
            .setTriggerValue(103)
            .setTriggerDedupKey(new UnsignedLong(3L));

    TriggerSpecs templateTriggerSpecs =
        SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(eventReportBuilder.build());

    existingAttributes.put(triggerRecord1);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>(Collections.singleton(new UnsignedLong(3L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource(null)
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(null)
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .setAdIdPermission(true)
            .setDebugKey(sourceDebugKey)
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(
            new ArrayList<>(
                Arrays.asList(
                    eventReportBuilder.setTriggerSummaryBucket(firstBucket).build(),
                    eventReportBuilder.setTriggerSummaryBucket(secondBucket).build())));
    int numAggregateReportPerDestination = 24;
    int numEventReportPerDestination = 35;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));

    verify(mMeasurementDao, never()).getSourceEventReports(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).deleteFlexEventReportsAndAttributions(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  /**
   * Status before attribution: 2 trigger attributed and 2 report generated with triggerData 1 and
   * 2, respectively; Incoming trigger status: 2 reports should be generated for triggerData 1
   * Result: incoming trigger has higher priority so previous report with triggerData 2 is deleted.
   */
  public void performAttribution_flexEventReport_insertThirdTriggerPriorityReset()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"1\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"111\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    Pair<Long, Long> firstBucket = Pair.create(10L, 99L);
    Pair<Long, Long> secondBucket = Pair.create(100L, TriggerSpecs.MAX_BUCKET_THRESHOLD);

    final EventReport currentEventReport1 =
        new EventReport.Builder()
            .setId("100")
            .setTriggerId("01234")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(50)
            .setTriggerValue(20)
            .setTriggerSummaryBucket(firstBucket)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .build();
    final EventReport currentEventReport2 =
        new EventReport.Builder()
            .setId("101")
            .setTriggerId("12345")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(60)
            .setTriggerValue(30)
            .setTriggerSummaryBucket(firstBucket)
            .setTriggerDedupKey(new UnsignedLong(1233L))
            .build();

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);

    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);
    JSONObject triggerRecord2 = generateTriggerJsonFromEventReport(currentEventReport2);

    existingAttributes.put(triggerRecord1);
    existingAttributes.put(triggerRecord2);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(
                new ArrayList<>(Arrays.asList(new UnsignedLong(3L), new UnsignedLong(1233L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
    int numAggregateReportPerDestination = 3;
    int numEventReportPerDestination = 3;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    ArgumentCaptor<EventReport> insertedReportsCaptor = ArgumentCaptor.forClass(EventReport.class);
    ArgumentCaptor<List<EventReport>> deletedReportsCaptor =
        ArgumentCaptor.forClass((Class) List.class);
    verify(mMeasurementDao, times(2)).insertEventReport(insertedReportsCaptor.capture());
    verify(mMeasurementDao, times(1))
        .deleteFlexEventReportsAndAttributions(deletedReportsCaptor.capture());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
    assertEquals(2, deletedReportsCaptor.getValue().size());
    List<EventReport> insertedEventReports = insertedReportsCaptor.getAllValues();
    assertEquals(firstBucket, insertedEventReports.get(0).getTriggerSummaryBucket());
    assertEquals(secondBucket, insertedEventReports.get(1).getTriggerSummaryBucket());
    assertEquals(new UnsignedLong(1L), insertedEventReports.get(0).getTriggerData());
    assertEquals(new UnsignedLong(1L), insertedEventReports.get(1).getTriggerData());
    long reportTime = TimeUnit.DAYS.toMillis(2);
    assertEquals(reportTime, insertedEventReports.get(0).getReportTime() - baseTime);
    assertEquals(reportTime, insertedEventReports.get(1).getReportTime() - baseTime);

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    assertEquals(3, source.getTriggerSpecs().getAttributedTriggers().size());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  public void performAttribution_flexEventReport_notReportDueToDedup()
      throws DatastoreException, JSONException {

    // Setup
    long baseTime = System.currentTimeMillis();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"1\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"105\","
                    + "  \"deduplication_key\": \"111\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    final EventReport currentEventReport1 =
        new EventReport.Builder()
            .setId("100")
            .setTriggerId("01234")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(50)
            .setTriggerValue(20)
            .setTriggerDedupKey(new UnsignedLong(111L))
            .build();
    final EventReport currentEventReport2 =
        new EventReport.Builder()
            .setId("101")
            .setTriggerId("12345")
            .setSourceEventId(new UnsignedLong(22L))
            .setEnrollmentId("another-enrollment-id")
            .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
            .setReportTime(baseTime + TimeUnit.DAYS.toMillis(2))
            .setStatus(EventReport.Status.PENDING)
            .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRegistrationOrigin(WebUtil.validUri("https://adtech2.test"))
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1) + 1000)
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(60)
            .setTriggerValue(30)
            .setTriggerDedupKey(new UnsignedLong(123L))
            .build();

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = generateTriggerJsonFromEventReport(currentEventReport1);
    JSONObject triggerRecord2 = generateTriggerJsonFromEventReport(currentEventReport2);

    existingAttributes.put(triggerRecord1);
    existingAttributes.put(triggerRecord2);

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(
                new ArrayList<>(Arrays.asList(new UnsignedLong(111L), new UnsignedLong(123L))))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any()))
        .thenReturn(new ArrayList<>(Arrays.asList(currentEventReport1, currentEventReport2)));
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, never()).getSourceEventReports(any());

    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).deleteEventReportAndAttribution(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(anyString(), anyString());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  public void performAttribution_flexEventReport_dedupKeyInserted()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"1\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"1\","
                    + "  \"deduplication_key\": \"111\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
    JSONArray existingAttributes = new JSONArray();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>())
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).deleteEventReportAndAttribution(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  public void performAttribution_flexEventReport_dedupKeyInserted_dedupAlignFlagOff()
      throws DatastoreException, JSONException {
    // Setup
    long baseTime = System.currentTimeMillis();
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"1\","
                    + "  \"priority\": \"123\","
                    + "  \"value\": \"1\","
                    + "  \"deduplication_key\": \"111\""
                    + "}"
                    + "]")
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}]")
            .setTriggerTime(baseTime + TimeUnit.DAYS.toMillis(1))
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();

    TriggerSpecs templateTriggerSpecs = SourceFixture.getValidTriggerSpecsValueSum(2);
    JSONArray existingAttributes = new JSONArray();

    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setEventReportDedupKeys(new ArrayList<>())
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterDataString(
                "{"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]"
                    + "}")
            .setEventTime(baseTime)
            .setEventReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setAggregatableReportWindow(baseTime + TimeUnit.DAYS.toMillis(3))
            .setTriggerSpecsString(templateTriggerSpecs.encodeToJson())
            .setMaxEventLevelReports(templateTriggerSpecs.getMaxReports())
            .setEventAttributionStatus(existingAttributes.toString())
            .setPrivacyParameters(templateTriggerSpecs.encodePrivacyParametersToJsonString())
            .build();

    when(mFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 5;
    int numEventReportPerDestination = 4;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()).thenReturn(false);

    // Execution
    mHandler.performPendingAttributions();

    // Assertion
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).fetchMatchingEventReports(any(), any());
    verify(mMeasurementDao, times(1)).getSourceEventReports(any());
    verify(mMeasurementDao, times(1)).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).deleteEventReportAndAttribution(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();

    verify(mMeasurementDao, times(1))
        .updateSourceAttributedTriggers(
            eq(source.getId()), eq(source.attributedTriggersToJsonFlexApi()));
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());
  }

  @Test
  public void performAttributions_withXnaConfig_derivedSourceWinsAndIsLogged()
      throws DatastoreException {
    // Setup
    String adtechEnrollment = "AdTech1-Ads";
    AttributionConfig attributionConfig =
        new AttributionConfig.Builder()
            .setExpiry(604800L)
            .setSourceAdtech(adtechEnrollment)
            .setSourcePriorityRange(new Pair<>(1L, 1000L))
            .setSourceFilters(null)
            .setPriority(50L)
            .setExpiry(604800L)
            .setFilterData(null)
            .build();
    Trigger trigger =
        getXnaTriggerBuilder()
            .setFilters(null)
            .setNotFilters(null)
            .setAttributionConfig(
                new JSONArray(Collections.singletonList(attributionConfig.serializeAsJson(mFlags)))
                    .toString())
            .build();

    String aggregatableSource = SourceFixture.ValidSourceParams.buildAggregateSource();
    // Its derived source will be winner due to install attribution and higher priority
    Source xnaSource =
        createXnaSourceBuilder()
            .setEnrollmentId(adtechEnrollment)
            // Priority changes to 50 for derived source
            .setPriority(1L)
            .setAggregateSource(aggregatableSource)
            .setFilterDataString(null)
            .setSharedAggregationKeys(
                new JSONArray(Arrays.asList("campaignCounts", "geoValue")).toString())
            .build();
    Source triggerEnrollmentSource1 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .build();

    Source triggerEnrollmentSource2 =
        createXnaSourceBuilder()
            .setEnrollmentId(trigger.getEnrollmentId())
            .setPriority(2L)
            .setFilterDataString(null)
            .setAggregateSource(aggregatableSource)
            .setInstallAttributed(false)
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(xnaSource);
    matchingSourceList.add(triggerEnrollmentSource1);
    matchingSourceList.add(triggerEnrollmentSource2);
    when(mMeasurementDao.fetchTriggerMatchingSourcesForXna(any(), any()))
        .thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertTrue(measurementAttributionStats.isSourceDerived());
  }

  @Test
  public void performAttributions_invalidTriggerId_triggerNotFoundLogged()
      throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(getEventTriggers())
            .setNotFilters(
                "[{"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {"
                    + "  \"key_2\": [\"value_21\", \"value_22_x\"]"
                    + "}]")
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenThrow(DatastoreException.class);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.FAILURE, result);
    ArgumentCaptor<MeasurementAttributionStats> statsArg =
        ArgumentCaptor.forClass(MeasurementAttributionStats.class);
    verify(mLogger).logMeasurementAttributionStats(statsArg.capture(), eq(null));
    MeasurementAttributionStats measurementAttributionStats = statsArg.getValue();
    assertEquals(
        measurementAttributionStats.getSourceType(),
        AttributionStatus.SourceType.UNKNOWN.getValue());
    assertEquals(
        measurementAttributionStats.getSurfaceType(),
        AttributionStatus.AttributionSurface.UNKNOWN.getValue());
    assertEquals(
        measurementAttributionStats.getResult(),
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue());
    assertEquals(
        measurementAttributionStats.getFailureType(),
        AttributionStatus.FailureType.TRIGGER_NOT_FOUND.getValue());
  }

  @Test
  public void performAttributions_topLevelFiltersDontMatch_topLevelFilterMatchFailureLogged()
      throws DatastoreException {
    Trigger trigger = TriggerFixture.getValidTrigger();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{" + "  \"key_1\": [\"no_match\"]," + "  \"key_2\": [\"no_match\"]" + "}")
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.SourceType.VIEW.getValue(), measurementAttributionStats.getSourceType());
    assertEquals(
        AttributionStatus.AttributionSurface.APP_APP.getValue(),
        measurementAttributionStats.getSurfaceType());
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE.getValue(),
        measurementAttributionStats.getFailureType());
  }

  @Test
  public void performAttributions_withinLookbackWindow_attributeTrigger()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"event\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"navigation\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(TRIGGER_TIME)
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    // Set Lookback window to be greater than duration from
                    // source to trigger time.
                    + "  \"_lookback_window\": 1000"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString("{" + "  \"key_1\": [\"value_11\", \"value_12\"]" + "}")
            .setId("sourceId")
            .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW - 1))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(TRIGGER_TIME + TimeUnit.DAYS.toMillis(28))
            .setEventReportWindow(TRIGGER_TIME + 1)
            .setAggregatableReportWindow(TRIGGER_TIME + 1)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerTime(trigger.getTriggerTime())
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.ATTRIBUTED));
    String expectedAttributionStatus =
        getAttributionStatus(List.of(trigger.getId()), List.of("3"), List.of("3"));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(source.getId()), eq(expectedAttributionStatus));
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_outsideLookbackWindow_noAttributionTrigger()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "["
                    + "{"
                    + "  \"trigger_data\": \"2\","
                    + "  \"priority\": \"2\","
                    + "  \"deduplication_key\": \"2\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"event\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "},"
                    + "{"
                    + "  \"trigger_data\": \"3\","
                    + "  \"priority\": \"3\","
                    + "  \"deduplication_key\": \"3\","
                    + "  \"filters\": [{"
                    + "    \"source_type\": [\"navigation\"], "
                    + "    \"dummy_key\": [\"dummy_value\"] "
                    + "   }]"
                    + "}"
                    + "]")
            .setTriggerTime(TRIGGER_TIME)
            .setFilters(
                "[{"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],"
                    // Set Lookback window to be smaller than duration from
                    // source to trigger time.
                    + "  \"_lookback_window\": 1000"
                    + "}]")
            .build();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString("{" + "  \"key_1\": [\"value_11\", \"value_12\"]" + "}")
            .setId("sourceId")
            .setEventTime(TRIGGER_TIME - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW + 1))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventReportWindow(TRIGGER_TIME + 1)
            .setAggregatableReportWindow(TRIGGER_TIME + 1)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(2L)
            .setTriggerDedupKey(new UnsignedLong(2L))
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerTime(trigger.getTriggerTime())
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRandomizedTriggerRate(mSourceNoiseHandler.getRandomizedTriggerRate(source))
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setRegistrationOrigin(REGISTRATION_URI)
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(any(), any());
    verify(mMeasurementDao, never()).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_noSourceEmptyAggTriggerDataEmptyValues_dontSendNullAggReport()
      throws DatastoreException {
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData("[]", "{}");
  }

  @Test
  public void
      performAttributions_noSourceEmptyAggTriggerDataEmptyAggValuesWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData("[]", "{}");
  }

  @Test
  public void
      performAttributions_noSourceEmptyAggTriggerDataEmptyAggValuesJsonArrayWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData("[]", "[]");
  }

  @Test
  public void performAttributions_noSourceEmptyAggTriggerDataNoValues_dontSendNullAggReport()
      throws DatastoreException {
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData("[]", null);
  }

  @Test
  public void
      performAttributions_noSourceEmptyAggTriggerDataNoAggValuesWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData("[]", null);
  }

  @Test
  public void performAttributions_noSourceNoAggTriggerDataEmptyValues_dontSendNullAggReport()
      throws DatastoreException {
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData(null, "{}");
  }

  @Test
  public void
      performAttributions_noSourceNoAggTriggerDataEmptyAggValuesWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData(null, "{}");
  }

  @Test
  public void
      performAttributions_noSourceNoAggTriggerDataEmptyAggValuesJsonArrayWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData(null, "[]");
  }

  @Test
  public void performAttributions_noSourceNoAggTriggerDataNoValues_dontSendNullAggReport()
      throws DatastoreException {
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData(null, null);
  }

  @Test
  public void
      performAttributions_noSourceNoAggTriggerDataNoAggValuesWithFiltering_dontSendNullAggReport()
          throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    testNullAggregateReports_NullOrEmptyTriggerAggregatableData(null, null);
  }

  // The following tests exercise null aggregate report logic for unattributed triggers. Each
  // path to nonattribution is repeated based on the combination of source registration time and
  // trigger context id, hence we parameterize those for each test method.
  @Test
  public void performAttributions_noSource_includeSourceRegistrationTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReport_noSource(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_noSource_exclSourceRegistrationTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReport_noSource(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_noSource_exclSourceRegTime_triggerContextId_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReport_noSource(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_topLevelFiltersDontMatch_inclSourceRegTime_sendNullAggReports()
      throws DatastoreException, JSONException {
    testNullAggregateReports_topLevelFiltersDontMatch(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_topLevelFiltersDontMatch_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_topLevelFiltersDontMatch(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_topLevelFiltersDontMatch_exclSourceRegTime_contextIdSet_sendNullAggReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_topLevelFiltersDontMatch(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_triggerTimePastAggReportWindow_inclSourceRegTime_sendNullAggregateReports()
          throws DatastoreException, JSONException {
    testNullAggregateReports_triggerTimePastAggReportWindow(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_triggerTimePastAggReportWindow_exclSourceRegTime_sendNullAggregateReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_triggerTimePastAggReportWindow(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_triggerTimePastAggReportWindow_exclSourceRegTime_contextIdSet_sendNullAggReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_triggerTimePastAggReportWindow(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_numReportsPerDestExceedMax_inclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_numReportsPerDestExceedsMax(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_numReportsPerDestExceedMax_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_numReportsPerDestExceedsMax(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_numReportsPerDestExceedMax_exclSourceRegTime_contextId_sendNullAggReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_numReportsPerDestExceedsMax(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggDeduped_inclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggDeduped(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggDeduped_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggDeduped(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggDeduped_exclSourceRegTime_contextId_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggDeduped(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggNoContrib_inclSourceRegTime_sendNullAggregateReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateNoContributions(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggNoContrib_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateNoContributions(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggNoContrib_contextIdSet_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateNoContributions(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_blockedByRateLimit_inclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_blockedByMaxDistinctReportingOriginsRateLimit(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_blockedByRateLimit_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_blockedByMaxDistinctReportingOriginsRateLimit(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_blockedByRateLimit_exclSourceRegTime_contextIdSet_sendNullAggReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_blockedByMaxDistinctReportingOriginsRateLimit(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggInsuffBudget_inclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateInsufficientBudget(
        Trigger.SourceRegistrationTimeConfig.INCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void performAttributions_triggerAggInsuffBudget_exclSourceRegTime_sendNullAggReport()
      throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateInsufficientBudget(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ null,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        1.0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  public void
      performAttributions_triggerAggInsuffBudget_exclSourceRegTime_contextIdSet_sendNullAggReport()
          throws DatastoreException, JSONException {
    testNullAggregateReports_triggerAggregateInsufficientBudget(
        Trigger.SourceRegistrationTimeConfig.EXCLUDE,
        /* triggerContextId= */ TEST_TRIGGER_CONTEXT_ID,
        () -> mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime(),
        0f,
        (trigger) -> {
          try {
            captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
                trigger, getMeasurementAttributionStats());
          } catch (DatastoreException e) {
            throw new RuntimeException(e);
          }
        });
  }

  public static Trigger.Builder getXnaTriggerBuilder() {
    return new Trigger.Builder()
        .setId(UUID.randomUUID().toString())
        .setAttributionDestination(TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION)
        .setEnrollmentId(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID)
        .setRegistrant(TriggerFixture.ValidTriggerParams.REGISTRANT)
        .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
        .setAggregateTriggerData(TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
        .setAggregateValuesString(TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES_STRING)
        .setFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
        .setNotFilters(TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
        .setAttributionConfig(TriggerFixture.ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
        .setAdtechBitMapping(TriggerFixture.ValidTriggerParams.X_NETWORK_KEY_MAPPING)
        .setRegistrationOrigin(TriggerFixture.ValidTriggerParams.REGISTRATION_ORIGIN)
        .setAggregatableSourceRegistrationTimeConfig(
            TriggerFixture.ValidTriggerParams.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG);
  }

  @Test
  public void performAttributions_attributionScopeEnabled_attributesSourceWithSameAttributionScope()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    Source olderSourceWithMatchingAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("olderSourceWithMatchingAttributionScopeId")
            .setEventTime(SOURCE_TIME - 2)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMatchingAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMatchingAttributionScopeId")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMatchingAttributionScopeLowerPriority =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMatchingAttributionScopeLowerPriorityId")
            .setEventTime(SOURCE_TIME + 1)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(99)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithoutAttributionScope =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceWithoutAttributionScopeId")
            .setEventTime(SOURCE_TIME + 2)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(101)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMismatchAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMismatchAttributionScopeId")
            .setEventTime(SOURCE_TIME + 3)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("4", "5", "6"))
            .setPriority(102)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Trigger triggerWithAttributionScope =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAttributionScopesString("[\"1\"]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(triggerWithAttributionScope.getId()));
    when(mMeasurementDao.getTrigger(triggerWithAttributionScope.getId()))
        .thenReturn(triggerWithAttributionScope);
    when(mMeasurementDao.getMatchingActiveSources(triggerWithAttributionScope))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    sourceWithMatchingAttributionScope,
                    sourceWithoutAttributionScope,
                    sourceWithMismatchAttributionScope,
                    olderSourceWithMatchingAttributionScope,
                    sourceWithMatchingAttributionScopeLowerPriority)));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(any()))
        .thenReturn(
            Pair.create(
                sourceWithMatchingAttributionScope.getAppDestinations(),
                sourceWithMatchingAttributionScope.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(List.of(triggerWithAttributionScope.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(sourceWithMatchingAttributionScope.getId()), any());
    verify(mMeasurementDao)
        .updateSourceStatus(
            eq(
                List.of(
                    olderSourceWithMatchingAttributionScope.getId(),
                    sourceWithMatchingAttributionScopeLowerPriority.getId(),
                    sourceWithMismatchAttributionScope.getId(),
                    sourceWithoutAttributionScope.getId())),
            eq(Source.Status.IGNORED));

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_noAttributionScopeMatch_attributesToNone()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    Source sourceWithoutAttributionScope =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceWithoutAttributionScopeId")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(101)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMismatchAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMismatchAttributionScopeId")
            .setEventTime(SOURCE_TIME + 1)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("4", "5", "6"))
            .setPriority(102)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Trigger triggerWithAttributionScope =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAttributionScopesString("[\"1\"]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(triggerWithAttributionScope.getId()));
    when(mMeasurementDao.getTrigger(triggerWithAttributionScope.getId()))
        .thenReturn(triggerWithAttributionScope);
    when(mMeasurementDao.getMatchingActiveSources(triggerWithAttributionScope))
        .thenReturn(
            new ArrayList<>(
                List.of(sourceWithoutAttributionScope, sourceWithMismatchAttributionScope)));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(any()))
        .thenReturn(
            Pair.create(
                sourceWithMismatchAttributionScope.getAppDestinations(),
                sourceWithMismatchAttributionScope.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(List.of(triggerWithAttributionScope.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).updateSourceAttributedTriggers(any(), any());
    verify(mMeasurementDao, never()).updateSourceStatus(any(), anyInt());

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_multipleTriggerAttributionScopes_selectsAllMatchingScopes()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    Source olderSourceWithMatchingAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("olderSourceWithMatchingAttributionScopeId")
            .setEventTime(SOURCE_TIME - 1)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMatchingAttributionScope1 =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMatchingAttributionScope1Id")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("1"))
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMatchingAttributionScope2 =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMatchingAttributionScope2Id")
            .setEventTime(SOURCE_TIME + 1)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("2"))
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMatchingAttributionScopeLowerPriority =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMatchingAttributionScopeLowerPriorityId")
            .setEventTime(SOURCE_TIME + 2)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(99)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithoutAttributionScope =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceWithoutAttributionScopeId")
            .setEventTime(SOURCE_TIME + 3)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(101)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Source sourceWithMismatchAttributionScope =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("sourceWithMismatchAttributionScopeId")
            .setEventTime(SOURCE_TIME + 4)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("4", "5", "6"))
            .setPriority(102)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Trigger triggerWithAttributionScope =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAttributionScopesString("[\"1\", \"2\"]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(triggerWithAttributionScope.getId()));
    when(mMeasurementDao.getTrigger(triggerWithAttributionScope.getId()))
        .thenReturn(triggerWithAttributionScope);
    when(mMeasurementDao.getMatchingActiveSources(triggerWithAttributionScope))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    sourceWithMatchingAttributionScope1,
                    sourceWithMatchingAttributionScope2,
                    sourceWithoutAttributionScope,
                    sourceWithMismatchAttributionScope,
                    olderSourceWithMatchingAttributionScope,
                    sourceWithMatchingAttributionScopeLowerPriority)));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(any()))
        .thenReturn(
            Pair.create(
                sourceWithMatchingAttributionScope1.getAppDestinations(),
                sourceWithMatchingAttributionScope1.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(List.of(triggerWithAttributionScope.getId())), eq(Trigger.Status.ATTRIBUTED));
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(sourceWithMatchingAttributionScope2.getId()), any());
    verify(mMeasurementDao)
        .updateSourceStatus(
            eq(
                List.of(
                    sourceWithMatchingAttributionScope1.getId(),
                    olderSourceWithMatchingAttributionScope.getId(),
                    sourceWithMatchingAttributionScopeLowerPriority.getId(),
                    sourceWithMismatchAttributionScope.getId(),
                    sourceWithoutAttributionScope.getId())),
            eq(Source.Status.IGNORED));

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttributions_emptyTriggerScopes_ignoresSourceScopes()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
    // Source with scope and higher priority, the scopes are ignored at attribution time.
    // The source should be attributed due to higher priority.
    Source olderSourceWithScopeAndHigherPriority =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("olderSourceWithScopeAndHigherPriority")
            .setEventTime(SOURCE_TIME)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(102)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    // Source with scope and lower priority, the scopes are ignored at attribution time.
    Source newerSourceWithScopeAndLowerPriority =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setId("newerSourceWithScopeAndLowerPriority")
            .setEventTime(SOURCE_TIME + 1)
            .setExpiryTime(EXPIRY_TIME)
            .setAttributionScopes(List.of("1"))
            .setPriority(101)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    // Most recent source without scopes.
    Source sourceWithoutAttributionScope =
        SourceFixture.getMinimalValidSourceBuilder()
            .setId("sourceWithoutAttributionScope")
            .setEventTime(SOURCE_TIME + 2)
            .setExpiryTime(EXPIRY_TIME)
            .setPriority(100)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();

    Trigger triggerWithAttributionScope =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setTriggerTime(TRIGGER_TIME)
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(EVENT_TRIGGERS)
            .setAttributionScopesString("[]")
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(triggerWithAttributionScope.getId()));
    when(mMeasurementDao.getTrigger(triggerWithAttributionScope.getId()))
        .thenReturn(triggerWithAttributionScope);
    when(mMeasurementDao.getMatchingActiveSources(triggerWithAttributionScope))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    olderSourceWithScopeAndHigherPriority,
                    newerSourceWithScopeAndLowerPriority,
                    sourceWithoutAttributionScope)));
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    when(mMeasurementDao.getSourceDestinations(any()))
        .thenReturn(
            Pair.create(
                olderSourceWithScopeAndHigherPriority.getAppDestinations(),
                olderSourceWithScopeAndHigherPriority.getWebDestinations()));

    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(List.of(triggerWithAttributionScope.getId())), eq(Trigger.Status.ATTRIBUTED));
    // The source scopes should be ignored as the trigger has empty attribution scopes.
    // All sources will be selected for attribution, and
    // olderSourceWithScopeAndHigherPriority is attributed due to higher priority.
    verify(mMeasurementDao)
        .updateSourceAttributedTriggers(eq(olderSourceWithScopeAndHigherPriority.getId()), any());
    verify(mMeasurementDao)
        .updateSourceStatus(
            eq(
                List.of(
                    newerSourceWithScopeAndLowerPriority.getId(),
                    sourceWithoutAttributionScope.getId())),
            eq(Source.Status.IGNORED));

    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  @Test
  public void performAttribution_triggerNoAggrDataNoEventTriggerData_returnAndIgnoreTrigger()
      throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(null)
            .setAggregateValuesString(null)
            .setEventTriggers("[]")
            .setId(UUID.randomUUID().toString())
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.TRIGGER_IGNORED.getValue(),
        measurementAttributionStats.getFailureType());

    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
  }

  @Test
  public void performAttribution_triggerHasValidAggregateValueConfigs_success()
      throws DatastoreException {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    String validAggregatableValuesString =
        "["
            + "{"
            + "\"values\": {\"campaignCounts\": 32768, \"geoValue\": 1664}, "
            + "\"filters\":"
            + " {\"product\": [\"1234\"]}"
            + "}"
            + "]";
    Source validSource = SourceFixture.getValidSource();
    Trigger validTrigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(null)
            .setAggregateValuesString(validAggregatableValuesString)
            .setEventTriggers(EVENT_TRIGGERS)
            .setId(UUID.randomUUID().toString())
            .build();
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(validTrigger.getId()));
    when(mMeasurementDao.getTrigger(validTrigger.getId())).thenReturn(validTrigger);
    when(mMeasurementDao.getMatchingActiveSources(validTrigger))
        .thenReturn(new ArrayList<>(List.of(validSource)));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .updateTriggerStatus(eq(List.of(validTrigger.getId())), eq(Trigger.Status.ATTRIBUTED));
  }

  private void testNullAggregateReport_noSource(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId).build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.NO_MATCHING_SOURCE.getValue(),
        measurementAttributionStats.getFailureType());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_topLevelFiltersDontMatch(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setFilters("[{" + "  \"key_1\": [\"match\"]," + "  \"key_2\": [\"match\"]" + "}]")
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterDataString(
                "{" + "  \"key_1\": [\"no_match\"]," + "  \"key_2\": [\"no_match\"]" + "}")
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.SourceType.VIEW.getValue(), measurementAttributionStats.getSourceType());
    assertEquals(
        AttributionStatus.AttributionSurface.APP_APP.getValue(),
        measurementAttributionStats.getSurfaceType());
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE.getValue(),
        measurementAttributionStats.getFailureType());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_triggerTimePastAggReportWindow(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setTriggerTime(3600001L)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregatableReportWindow(3600000L)
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_numReportsPerDestExceedsMax(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    long triggerTime = 3600000L;
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setTriggerTime(triggerTime)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregatableReportWindow(triggerTime + 1) // ensure trigger time is before window
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    int excessiveReportCount = MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION + 1;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(excessiveReportCount);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_triggerAggDeduped(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    long triggerTime = 3600000L;
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setAggregateDeduplicationKeys(
                "[{\"deduplication_key\": \"0\"," + "\"filters\": [{\"x\": [\"y\"]}]}]")
            .setTriggerTime(triggerTime)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\": \"0x159\", \"geoValue\": \"0x5\"}")
            .setFilterDataString("{\"x\": [\"y\", \"z\"]}")
            // Ensure key matches that of trigger's aggregate deduplication key
            .setAggregateReportDedupKeys(List.of(new UnsignedLong(0L)))
            .setAggregatableReportWindow(triggerTime + 1) // ensure trigger time is before window
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_triggerAggregateNoContributions(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    long triggerTime = 3600000L;
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setTriggerTime(triggerTime)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"noMatch\": \"0x159\", \"stillNoMatch\": \"0x5\"}")
            .setAggregatableReportWindow(triggerTime + 1) // ensure trigger time's before window
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_triggerAggregateInsufficientBudget(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    int excessiveAggregateValue = Flags.MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE + 1;
    String aggregateValuesStr =
        String.format(
            Locale.ENGLISH,
            "{\"campaignCounts\":32768," + "\"geoValue\":%d}",
            excessiveAggregateValue);
    long triggerTime = 3600000L;
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setAggregateValuesString(aggregateValuesStr)
            .setTriggerTime(triggerTime)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\": \"0x159\", \"geoValue\": \"0x5\"}")
            .setAggregatableReportWindow(triggerTime + 1) // ensure trigger time is before window
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());

    assertionFunc.accept(trigger);
  }

  private void testNullAggregateReports_NullOrEmptyTriggerAggregatableData(
      String aggregateTriggerData, String aggregateValues) throws DatastoreException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setAggregateTriggerData(aggregateTriggerData)
            .setAggregateValuesString(aggregateValues)
            .setEventTriggers(EVENT_TRIGGERS)
            .setId(UUID.randomUUID().toString())
            .build();

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    // Despite a 100% null rate for inclusion and exclusion of source registration time, there
    // should still be no null aggregate report since there is no aggregatable data.
    when(mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()).thenReturn(1.0f);
    when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);
    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.NO_MATCHING_SOURCE.getValue(),
        measurementAttributionStats.getFailureType());

    verify(mMeasurementDao)
        .updateTriggerStatus(
            eq(Collections.singletonList(trigger.getId())), eq(Trigger.Status.IGNORED));
    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mTransaction, times(2)).begin();
    verify(mTransaction, times(2)).end();
  }

  private void testNullAggregateReports_blockedByMaxDistinctReportingOriginsRateLimit(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig,
      String triggerContextId,
      Supplier<Float> flagSupplier,
      float flagValue,
      Consumer<Trigger> assertionFunc)
      throws DatastoreException, JSONException {
    long triggerTime = 3600000L;
    Trigger trigger =
        getTriggerBuilderForNullAggReports(sourceRegistrationTimeConfig, triggerContextId)
            .setTriggerTime(triggerTime)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    Source source =
        SourceFixture.getMinimalValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregatableReportWindow(triggerTime + 1) // ensure trigger time's before window
            .build();
    matchingSourceList.add(source);

    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementMaxDistinctReportingOriginsInAttribution()).thenReturn(0);
    when(flagSupplier.get()).thenReturn(flagValue);

    // Execution
    AttributionJobHandler.ProcessingResult result = mHandler.performPendingAttributions();

    // Assertion
    assertEquals(AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED, result);

    MeasurementAttributionStats measurementAttributionStats = getMeasurementAttributionStats();
    assertEquals(
        AttributionStatus.AttributionResult.NOT_ATTRIBUTED.getValue(),
        measurementAttributionStats.getResult());
    assertEquals(
        AttributionStatus.FailureType.RATE_LIMIT_EXCEEDED.getValue(),
        measurementAttributionStats.getFailureType());

    assertionFunc.accept(trigger);
  }

  /**
   * Return a trigger builder meant for testing null aggregate reports. The specific aggregate
   * trigger data and aggregate values are not important, they just need to be present to invoke
   * null aggregate report logic.
   */
  private static Trigger.Builder getTriggerBuilderForNullAggReports(
      Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig, String triggerContextId)
      throws JSONException {
    return TriggerFixture.getValidTriggerBuilder()
        .setId(UUID.randomUUID().toString())
        .setEventTriggers(getEventTriggers())
        .setAggregateTriggerData(getAggregateTriggerData().toString())
        .setAggregateValuesString(getAggregateValues())
        .setAggregatableSourceRegistrationTimeConfig(sourceRegistrationTimeConfig)
        .setTriggerContextId(triggerContextId);
  }

  private static String getAggregateValues() {
    return "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";
  }

  private static String getEventTriggers() {
    return "["
        + "{"
        + "  \"trigger_data\": \"5\","
        + "  \"priority\": \"123\","
        + "  \"deduplication_key\": \"1\""
        + "}"
        + "]";
  }

  private void captureAndAssertNullAggregateReportsIncludingSourceRegistrationTime(
      Trigger trigger, MeasurementAttributionStats measurementAttributionStats)
      throws DatastoreException {
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    // +1 to account for day of trigger
    long invocations =
        TimeUnit.SECONDS.toDays(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
            + 1L;
    verify(mMeasurementDao, times((int) invocations))
        .insertAggregateReport(aggregateReportCaptor.capture());
    List<AggregateReport> reports = aggregateReportCaptor.getAllValues();
    reports.sort(Comparator.comparing(AggregateReport::getSourceRegistrationTime).reversed());

    for (int i = 0; i < reports.size(); i++) {
      assertNullAggregateReport(
          reports.get(i), trigger, trigger.getTriggerTime() - TimeUnit.DAYS.toMillis(i));
    }
    assertEquals(31, measurementAttributionStats.getNullAggregateReportCount());
  }

  private void captureAndAssertNullAggregateReportExcludingSourceRegistrationTime(
      Trigger trigger, MeasurementAttributionStats measurementAttributionStats)
      throws DatastoreException {
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao, times(1)).insertAggregateReport(aggregateReportCaptor.capture());
    AggregateReport report = aggregateReportCaptor.getValue();
    assertNullAggregateReport(report, trigger, null);
    assertEquals(1, measurementAttributionStats.getNullAggregateReportCount());
  }

  private MeasurementAttributionStats getMeasurementAttributionStats() {
    ArgumentCaptor<MeasurementAttributionStats> statsArg =
        ArgumentCaptor.forClass(MeasurementAttributionStats.class);
    verify(mLogger)
        .logMeasurementAttributionStats(
            statsArg.capture(), eq(TriggerFixture.ValidTriggerParams.ENROLLMENT_ID));
    MeasurementAttributionStats measurementAttributionStats = statsArg.getValue();
    return measurementAttributionStats;
  }

  private Source.Builder createXnaSourceBuilder() {
    return new Source.Builder()
        .setId(UUID.randomUUID().toString())
        .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
        .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
        .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
        .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
        .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
        .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
        .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
        .setInstallAttributionWindow(SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
        .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
        .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
        .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
        .setFilterDataString(buildMatchingFilterData())
        .setIsDebugReporting(true)
        .setRegistrationId(SourceFixture.ValidSourceParams.REGISTRATION_ID)
        .setSharedAggregationKeys(SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS)
        .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
        .setAggregatableReportWindow(SourceFixture.ValidSourceParams.EXPIRY_TIME)
        .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN)
        .setInstallAttributed(true);
  }

  private String buildMatchingFilterData() {
    try {
      JSONObject filterMap = new JSONObject();
      filterMap.put(
          "conversion_subdomain",
          new JSONArray(Collections.singletonList("electronics.megastore")));
      return filterMap.toString();
    } catch (JSONException e) {
      LogUtil.e("JSONException when building aggregate filter data.");
    }
    return null;
  }

  private JSONArray buildAggregateTriggerData() throws JSONException {
    JSONArray triggerData = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
    jsonObject1.put("filters", createFilterJSONArray());
    jsonObject1.put("not_filters", createFilterJSONArray());
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
    triggerData.put(jsonObject1);
    triggerData.put(jsonObject2);
    return triggerData;
  }

  private JSONArray createFilterJSONArray() throws JSONException {
    JSONObject filterMap = new JSONObject();
    filterMap.put("conversion_subdomain", new JSONArray(Arrays.asList("electronics.megastore")));
    filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));
    JSONArray filterSet = new JSONArray();
    filterSet.put(filterMap);
    return filterSet;
  }

  private void assertNullAggregateReport(
      AggregateReport report, Trigger trigger, Long expectedSourceRegistrationTime) {
    assertEquals(trigger.getRegistrationOrigin(), report.getRegistrationOrigin());
    assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
    String[] split =
        MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG.split(AGGREGATE_REPORT_DELAY_DELIMITER);
    final long minDelay = Long.parseLong(split[0].trim());
    final long delaySpan = Long.parseLong(split[1].trim());
    long lowerBound = trigger.getTriggerTime() + minDelay;

    // Add slightly more delay to upper bound to account for execution.
    long upperBound = trigger.getTriggerTime() + minDelay + delaySpan + 1000L;

    if (trigger.getTriggerContextId() == null) {
      assertTrue(
          report.getScheduledReportTime() > lowerBound
              && report.getScheduledReportTime() < upperBound);
    } else {
      assertEquals(trigger.getTriggerTime(), report.getScheduledReportTime());
    }

    assertNull(report.getSourceDebugKey());
    if ((trigger.getDestinationType() == EventSurfaceType.APP && trigger.hasAdIdPermission())
        || (trigger.getDestinationType() == EventSurfaceType.WEB
            && trigger.hasArDebugPermission())) {
      assertEquals(trigger.getDebugKey(), report.getTriggerDebugKey());
    } else {
      assertNull(report.getTriggerDebugKey());
    }

    if (trigger.getAggregationCoordinatorOrigin() == null) {
      assertEquals(
          Uri.parse(AdServicesConfig.getMeasurementDefaultAggregationCoordinatorOrigin()),
          report.getAggregationCoordinatorOrigin());
    } else {
      assertEquals(
          trigger.getAggregationCoordinatorOrigin(), report.getAggregationCoordinatorOrigin());
    }
    assertTrue(report.isFakeReport());
    assertEquals(trigger.getId(), report.getTriggerId());
    assertEquals(expectedSourceRegistrationTime, report.getSourceRegistrationTime());
  }

  private void setupTestForNullAggregateReport(Trigger trigger, Source source)
      throws DatastoreException {
    when(mMeasurementDao.getPendingTriggerIds())
        .thenReturn(Collections.singletonList(trigger.getId()));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(anyInt(), any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceDestinations(source.getId()))
        .thenReturn(Pair.create(source.getAppDestinations(), source.getWebDestinations()));
    when(mFlags.getMeasurementNullAggregateReportEnabled()).thenReturn(true);
    when(mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime()).thenReturn(1.0f);
  }

  private void assertAggregateReportsEqual(
      AggregateReport expectedReport, AggregateReport actualReport) {
    // Avoids checking report time because there is randomization
    assertEquals(expectedReport.getApiVersion(), actualReport.getApiVersion());
    assertEquals(
        expectedReport.getAttributionDestination(), actualReport.getAttributionDestination());
    assertEquals(
        expectedReport.getDebugCleartextPayload(), actualReport.getDebugCleartextPayload());
    assertEquals(expectedReport.getEnrollmentId(), actualReport.getEnrollmentId());
    assertEquals(expectedReport.getPublisher(), actualReport.getPublisher());
    assertEquals(expectedReport.getSourceId(), actualReport.getSourceId());
    assertEquals(expectedReport.getTriggerId(), actualReport.getTriggerId());
    assertEquals(
        expectedReport.getAggregateAttributionData(), actualReport.getAggregateAttributionData());
    assertEquals(expectedReport.getSourceDebugKey(), actualReport.getSourceDebugKey());
    assertEquals(expectedReport.getTriggerDebugKey(), actualReport.getTriggerDebugKey());
    assertEquals(expectedReport.getRegistrationOrigin(), actualReport.getRegistrationOrigin());
    assertEquals(
        expectedReport.getSourceRegistrationTime(), actualReport.getSourceRegistrationTime());
    assertEquals(expectedReport.isFakeReport(), actualReport.isFakeReport());
    assertEquals(expectedReport.getTriggerContextId(), actualReport.getTriggerContextId());
    assertEquals(expectedReport.getApi(), actualReport.getApi());
  }

  private static AggregateReport.Builder getExpectedAggregateReportBuilder(
      Trigger trigger, Source source) {
    return new AggregateReport.Builder()
        .setApiVersion("0.1")
        .setAttributionDestination(trigger.getAttributionDestination())
        .setDebugCleartextPayload(
            "{\"operation\":\"histogram\","
                + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                + "{\"bucket\":\"2693\",\"value\":1644}]}")
        .setEnrollmentId(source.getEnrollmentId())
        .setPublisher(source.getRegistrant())
        .setSourceId(source.getId())
        .setTriggerDebugKey(trigger.getDebugKey())
        .setSourceDebugKey(source.getDebugKey())
        .setTriggerId(trigger.getId())
        .setRegistrationOrigin(REGISTRATION_URI)
        .setSourceRegistrationTime(source.getEventTime())
        .setAggregateAttributionData(
            new AggregateAttributionData.Builder()
                .setContributions(
                    Arrays.asList(
                        new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("1369"))
                            .setValue(32768)
                            .build(),
                        new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("2693"))
                            .setValue(1644)
                            .build()))
                .build())
        .setIsFakeReport(false)
        .setApi(API);
  }

  private static AggregateReport.Builder getExpectedAggregateReportBuilderV1(
      Trigger trigger, Source source) {
    return new AggregateReport.Builder()
        .setApiVersion("1.0")
        .setAttributionDestination(trigger.getAttributionDestination())
        .setDebugCleartextPayload(
            "{\"operation\":\"histogram\","
                + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                + "{\"bucket\":\"2693\",\"value\":1644}]}")
        .setEnrollmentId(source.getEnrollmentId())
        .setPublisher(source.getRegistrant())
        .setSourceId(source.getId())
        .setTriggerDebugKey(trigger.getDebugKey())
        .setSourceDebugKey(source.getDebugKey())
        .setTriggerId(trigger.getId())
        .setRegistrationOrigin(REGISTRATION_URI)
        .setSourceRegistrationTime(source.getEventTime())
        .setAggregateAttributionData(
            new AggregateAttributionData.Builder()
                .setContributions(
                    Arrays.asList(
                        new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("1369"))
                            .setValue(32768)
                            .build(),
                        new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("2693"))
                            .setValue(1644)
                            .build()))
                .build())
        .setIsFakeReport(false)
        .setApi(API);
  }

  private static JSONArray getAggregateTriggerData() throws JSONException {
    JSONArray triggerData = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
    triggerData.put(jsonObject1);
    triggerData.put(jsonObject2);
    return triggerData;
  }

  private static Trigger.Builder getTriggerBuilder() throws JSONException {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("triggerId1")
        .setTriggerTime(TRIGGER_TIME)
        .setStatus(Trigger.Status.PENDING)
        .setEventTriggers(new JSONArray().toString());
  }

  private static Trigger.Builder getAggregateTriggerBuilder() throws JSONException {
    JSONArray triggerData = getAggregateTriggerData();

    return getTriggerBuilder()
        .setAggregateTriggerData(triggerData.toString())
        .setAggregateValuesString("{\"campaignCounts\":32768,\"geoValue\":1644}");
  }

  private static Trigger getAggregateTrigger() throws JSONException {
    return getAggregateTriggerBuilder().build();
  }

  private static Trigger getAggregateAndEventTrigger() throws JSONException {
    return getAggregateTriggerBuilder().setEventTriggers(EVENT_TRIGGERS).build();
  }

  private static Source getAggregateSource() {
    return SourceFixture.getMinimalValidSourceBuilder()
        .setId("sourceId1")
        .setEventTime(SOURCE_TIME)
        .setExpiryTime(EXPIRY_TIME)
        .setAggregatableReportWindow(TRIGGER_TIME + 1L)
        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
        .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
        .setFilterDataString("{\"product\":[\"1234\",\"2345\"]}")
        .build();
  }

  private static JSONObject generateTriggerJsonFromEventReport(EventReport eventReport)
      throws JSONException {
    JSONObject triggerRecord = new JSONObject();
    triggerRecord.put("trigger_id", eventReport.getId());
    triggerRecord.put("value", eventReport.getTriggerValue());
    triggerRecord.put("priority", eventReport.getTriggerPriority());
    triggerRecord.put("trigger_time", eventReport.getTriggerTime());
    triggerRecord.put("trigger_data", eventReport.getTriggerData());
    triggerRecord.put("dedup_key", eventReport.getTriggerDedupKey());
    if (eventReport.getTriggerDebugKey() != null) {
      triggerRecord.put("debug_key", eventReport.getTriggerDebugKey());
    }
    triggerRecord.put("has_source_debug_key", eventReport.getSourceDebugKey() != null);
    return triggerRecord;
  }

  private static String getAttributionStatus(
      List<String> triggerIds, List<String> triggerData, List<String> dedupKeys) {
    try {
      JSONArray attributionStatus = new JSONArray();
      for (int i = 0; i < triggerIds.size(); i++) {
        attributionStatus.put(
            new JSONObject()
                .put("trigger_id", triggerIds.get(i))
                .put("trigger_data", triggerData.get(i))
                .put("dedup_key", dedupKeys.get(i)));
      }
      return attributionStatus.toString();
    } catch (JSONException ignored) {
      return null;
    }
  }
}
