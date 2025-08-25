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

package com.google.measurement.client;

import static com.google.measurement.client.ResultCode.RESULT_OK;
import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.google.measurement.client.actions.Action;
import com.google.measurement.client.actions.AggregateReportingJob;
import com.google.measurement.client.actions.EventReportingJob;
import com.google.measurement.client.actions.InstallApp;
import com.google.measurement.client.actions.RegisterListSources;
import com.google.measurement.client.actions.RegisterSource;
import com.google.measurement.client.actions.RegisterTrigger;
import com.google.measurement.client.actions.RegisterWebSource;
import com.google.measurement.client.actions.RegisterWebTrigger;
import com.google.measurement.client.actions.ReportObjects;
import com.google.measurement.client.actions.UninstallApp;
import com.google.measurement.client.actions.UriConfig;
import com.google.measurement.client.aggregation.AggregateCryptoFixture;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.attribution.AttributionJobHandlerWrapper;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.DbTestUtil;
import com.google.measurement.client.data.EnrollmentDao;
import com.google.measurement.client.data.EnrollmentData;
import com.google.measurement.client.data.SQLDatastoreManager;
import com.google.measurement.client.deletion.MeasurementDataDeleter;
import com.google.measurement.client.noising.ImpressionNoiseUtil;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.ondevicepersonalization.NoOdpDelegationWrapper;
import com.google.measurement.client.registration.AsyncRegistrationContentProvider;
import com.google.measurement.client.registration.AsyncRegistrationQueueRunner;
import com.google.measurement.client.registration.AsyncSourceFetcher;
import com.google.measurement.client.registration.AsyncTriggerFetcher;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.AggregateReportingJobHandlerWrapper;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.DebugReportingJobHandlerWrapper;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.reporting.EventReportingJobHandlerWrapper;
import com.google.measurement.client.stats.AdServicesErrorLogger;
import com.google.measurement.client.stats.NoOpLoggerImpl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * <p>Consider @RunWith(Parameterized.class)
 */
public abstract class E2EAbstractMockTest extends E2EAbstractTest {

  // Class extensions may choose to disable or enable added noise.
  AttributionJobHandlerWrapper mAttributionHelper;
  MeasurementImpl mMeasurementImpl;
  ClickVerifier mClickVerifier;
  MeasurementDataDeleter mMeasurementDataDeleter;
  Flags mFlags;
  AsyncRegistrationQueueRunner mAsyncRegistrationQueueRunner;
  SourceNoiseHandler mSourceNoiseHandler;
  ImpressionNoiseUtil mImpressionNoiseUtil;
  AsyncSourceFetcher mAsyncSourceFetcher;
  AsyncTriggerFetcher mAsyncTriggerFetcher;
  AdServicesErrorLogger mErrorLogger;

  EnrollmentDao mEnrollmentDao;
  DatastoreManager mDatastoreManager;

  ContentResolver mMockContentResolver;
  ContentProviderClient mMockContentProviderClient;
  private final Set<String> mSeenUris = new HashSet<>();
  private final Map<String, String> mUriToEnrollmentId = new HashMap<>();
  protected DebugReportApi mDebugReportApi;
  protected AggregateDebugReportApi mAggregateDebugReportApi;

  private static Map<String, String> sPhFlags =
      Map.ofEntries(
          entry(
              FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG,
              AGGREGATE_REPORT_DELAY + ",0"),
          entry(
              FlagsConstants.KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
              WebUtil.validUrl("https://coordinator.test")),
          entry(
              FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
              WebUtil.validUrl("https://coordinator.test")),
          entry(
              FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME,
              "0"),
          entry(
              FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME,
              "0"));
  private HttpsURLConnection mUrlConnection;

  protected E2EAbstractMockTest(
      Collection<Action> actions,
      ReportObjects expectedOutput,
      ParamsProvider paramsProvider,
      String name,
      Map<String, String> phFlagsMap)
      throws RemoteException, IOException {
    super(
        actions,
        expectedOutput,
        name,
        ((Supplier<Map<String, String>>)
                () -> {
                  for (String key : sPhFlags.keySet()) {
                    phFlagsMap.putIfAbsent(key, sPhFlags.get(key));
                  }
                  return phFlagsMap;
                })
            .get());
    mClickVerifier = mock(ClickVerifier.class);
    mFlags = spy(FlagsFactory.getFlags());
    doReturn(false).when(mFlags).getEnrollmentEnableLimitedLogging();
    mErrorLogger = mock(AdServicesErrorLogger.class);
    mDatastoreManager =
        new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
    mMeasurementDataDeleter = spy(new MeasurementDataDeleter(mDatastoreManager, mFlags));

    mEnrollmentDao =
        new EnrollmentDao(
            ApplicationProvider.getApplicationContext(),
            DbTestUtil.getSharedDbHelperForTest(),
            mFlags,
            /* enable seed */ true,
            new NoOpLoggerImpl(),
            EnrollmentUtil.getInstance());
    mDebugReportApi =
        new DebugReportApi(
            ApplicationProvider.getApplicationContext(),
            mFlags,
            new EventReportWindowCalcDelegate(mFlags),
            new SourceNoiseHandler(mFlags));
    mAggregateDebugReportApi = new AggregateDebugReportApi(mFlags);

    mImpressionNoiseUtil = spy(new ImpressionNoiseUtil());

    mSourceNoiseHandler =
        spy(
            new SourceNoiseHandler(
                mFlags, new EventReportWindowCalcDelegate(mFlags), mImpressionNoiseUtil));

    mAsyncSourceFetcher =
        spy(
            new AsyncSourceFetcher(
                sContext, mEnrollmentDao, mFlags, mDatastoreManager, mDebugReportApi));
    mAsyncTriggerFetcher =
        spy(
            new AsyncTriggerFetcher(
                sContext,
                mEnrollmentDao,
                mFlags,
                new NoOdpDelegationWrapper(),
                mDatastoreManager,
                mDebugReportApi));
    mMockContentResolver = mock(ContentResolver.class);
    mMockContentProviderClient = mock(ContentProviderClient.class);
    mUrlConnection = mock(HttpsURLConnection.class);

    Uri triggerUri = TriggerContentProvider.getTriggerUri();
    Uri asyncRegistrationTriggerUri = AsyncRegistrationContentProvider.getTriggerUri();
    when(mUrlConnection.getResponseCode()).thenReturn(200);
    when(mMockContentResolver.acquireContentProviderClient(triggerUri))
        .thenReturn(mMockContentProviderClient);
    when(mMockContentResolver.acquireContentProviderClient(asyncRegistrationTriggerUri))
        .thenReturn(mMockContentProviderClient);
    when(mMockContentProviderClient.insert(eq(triggerUri), any())).thenReturn(triggerUri);
    when(mMockContentProviderClient.insert(eq(asyncRegistrationTriggerUri), any()))
        .thenReturn(asyncRegistrationTriggerUri);
    when(mClickVerifier.isInputEventVerifiable(any(), anyLong(), anyString())).thenReturn(true);
  }

  void prepareEventReportNoising(UriConfig uriConfig) {
    List<int[]> fakeReportConfigs = uriConfig.getFakeReportConfigs();
    Mockito.doReturn(fakeReportConfigs)
        .when(mImpressionNoiseUtil)
        .getReportConfigsForSequenceIndex(any(), anyLong());
    Mockito.doReturn(fakeReportConfigs)
        .when(mImpressionNoiseUtil)
        .selectFlexEventReportRandomStateAndGenerateReportConfigs(any(), anyInt(), any());
    Mockito.doReturn(fakeReportConfigs == null ? 2.0D : 0.0D)
        .when(mSourceNoiseHandler)
        .getRandomDouble(any());
  }

  void prepareAggregateReportNoising(UriConfig uriConfig) {
    mAttributionHelper.prepareAggregateReportNoising(uriConfig);
  }

  @Override
  void prepareRegistrationServer(RegisterSource sourceRegistration) throws IOException {
    for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
      UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
      if (uriConfig.shouldEnroll()) {
        updateEnrollment(uri);
      }
      HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
      when(urlConnection.getResponseCode()).thenReturn(200);
      Answer<Map<String, List<String>>> headerFieldsMockAnswer =
          invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
      Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
      Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
      prepareEventReportNoising(uriConfig);
    }
  }

  @Override
  void prepareRegistrationServer(RegisterListSources sourceRegistration) throws IOException {
    for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
      UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
      if (uriConfig.shouldEnroll()) {
        updateEnrollment(uri);
      }
      HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
      when(urlConnection.getResponseCode()).thenReturn(200);
      Answer<Map<String, List<String>>> headerFieldsMockAnswer =
          invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
      Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
      Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
      prepareEventReportNoising(uriConfig);
    }
  }

  @Override
  void prepareRegistrationServer(RegisterTrigger triggerRegistration) throws IOException {
    for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
      UriConfig uriConfig = getNextUriConfig(triggerRegistration.mUriConfigsMap.get(uri));
      if (uriConfig.shouldEnroll()) {
        updateEnrollment(uri);
      }
      HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
      when(urlConnection.getResponseCode()).thenReturn(200);
      // Answer<Map<String, List<String>>> headerFieldsMockAnswer =
      //     invocation -> getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
      // Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
      Mockito.doReturn(getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri))
          .when(urlConnection)
          .getHeaderFields();
      Mockito.doReturn(urlConnection).when(mAsyncTriggerFetcher).openUrl(new URL(uri));
      prepareAggregateReportNoising(uriConfig);
    }
  }

  @Override
  void prepareRegistrationServer(RegisterWebSource sourceRegistration) throws IOException {
    for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
      UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
      if (uriConfig.shouldEnroll()) {
        updateEnrollment(uri);
      }
      HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
      when(urlConnection.getResponseCode()).thenReturn(200);
      Answer<Map<String, List<String>>> headerFieldsMockAnswer =
          invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
      Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
      Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
      prepareEventReportNoising(uriConfig);
    }
  }

  @Override
  void prepareRegistrationServer(RegisterWebTrigger triggerRegistration) throws IOException {
    for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
      UriConfig uriConfig = getNextUriConfig(triggerRegistration.mUriConfigsMap.get(uri));
      if (uriConfig.shouldEnroll()) {
        updateEnrollment(uri);
      }
      HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
      when(urlConnection.getResponseCode()).thenReturn(200);
      Answer<Map<String, List<String>>> headerFieldsMockAnswer =
          invocation -> getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
      Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
      Mockito.doReturn(urlConnection).when(mAsyncTriggerFetcher).openUrl(new URL(uri));
      prepareAggregateReportNoising(uriConfig);
    }
  }

  @Override
  void processAction(RegisterSource sourceRegistration) throws IOException, JSONException {
    prepareRegistrationServer(sourceRegistration);
    Assert.assertEquals(
        "MeasurementImpl.register source failed",
        RESULT_OK,
        mMeasurementImpl.register(
            sourceRegistration.mRegistrationRequest,
            sourceRegistration.mAdIdPermission,
            sourceRegistration.mTimestamp));
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
    processActualDebugReportApiJob(sourceRegistration.mTimestamp);
    processActualDebugReportJob(sourceRegistration.mTimestamp, 0L);
  }

  @Override
  void processAction(RegisterWebSource sourceRegistration) throws IOException, JSONException {
    prepareRegistrationServer(sourceRegistration);
    Assert.assertEquals(
        "MeasurementImpl.registerWebSource failed",
        RESULT_OK,
        mMeasurementImpl.registerWebSource(
            sourceRegistration.mRegistrationRequest,
            sourceRegistration.mAdIdPermission,
            sourceRegistration.mTimestamp));
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
    processActualDebugReportApiJob(sourceRegistration.mTimestamp);
    processActualDebugReportJob(sourceRegistration.mTimestamp, 0L);
  }

  @Override
  void processAction(RegisterListSources sourceRegistration) throws IOException, JSONException {
    prepareRegistrationServer(sourceRegistration);
    Assert.assertEquals(
        "MeasurementImpl.registerWebSource failed",
        RESULT_OK,
        mMeasurementImpl.registerSources(
            sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
    processActualDebugReportApiJob(sourceRegistration.mTimestamp);
  }

  @Override
  void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
    prepareRegistrationServer(triggerRegistration);
    Assert.assertEquals(
        "MeasurementImpl.register trigger failed",
        RESULT_OK,
        mMeasurementImpl.register(
            triggerRegistration.mRegistrationRequest,
            triggerRegistration.mAdIdPermission,
            triggerRegistration.mTimestamp));
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

    // To test interactions with deletion of expired records, run event reporting and deletion
    // before performing attribution.
    processAction(
        new EventReportingJob(triggerRegistration.mTimestamp - TimeUnit.MINUTES.toMillis(1)));
    long earliestValidInsertion =
        triggerRegistration.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    runDeleteExpiredRecordsJob(earliestValidInsertion);

    Assert.assertTrue(
        "AttributionJobHandler.performPendingAttributions returned false",
        mAttributionHelper.performPendingAttributions());
    // Attribution can happen up to an hour after registration call, due to AsyncRegistration
    processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
    processActualDebugReportApiJob(triggerRegistration.mTimestamp);
  }

  @Override
  void processAction(RegisterWebTrigger triggerRegistration) throws IOException, JSONException {
    prepareRegistrationServer(triggerRegistration);
    Assert.assertEquals(
        "MeasurementImpl.registerWebTrigger failed",
        RESULT_OK,
        mMeasurementImpl.registerWebTrigger(
            triggerRegistration.mRegistrationRequest,
            triggerRegistration.mAdIdPermission,
            triggerRegistration.mTimestamp));
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
    Assert.assertTrue(
        "AttributionJobHandler.performPendingAttributions returned false",
        mAttributionHelper.performPendingAttributions());
    // Attribution can happen up to an hour after registration call, due to AsyncRegistration
    processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
    processActualDebugReportApiJob(triggerRegistration.mTimestamp);
  }

  // Triggers debug reports to be sent
  void processActualDebugReportJob(long timestamp, long delay) throws IOException, JSONException {
    long maxAggregateReportUploadRetryWindowMs =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
    long reportTime = timestamp + delay;
    Object[] eventCaptures =
        EventReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
            mDatastoreManager,
            reportTime - Flags.DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
            reportTime,
            true,
            mFlags,
            ApplicationProvider.getApplicationContext());

    processActualDebugEventReports(
        timestamp,
        (List<EventReport>) eventCaptures[0],
        (List<Uri>) eventCaptures[1],
        (List<JSONObject>) eventCaptures[2]);

    Object[] aggregateCaptures =
        AggregateReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
            mDatastoreManager,
            reportTime - maxAggregateReportUploadRetryWindowMs,
            reportTime,
            true,
            mFlags);

    processActualDebugAggregateReports(
        (List<AggregateReport>) aggregateCaptures[0],
        (List<Uri>) aggregateCaptures[1],
        (List<JSONObject>) aggregateCaptures[2]);
  }

  // Process actual additional debug reports.
  protected void processActualDebugReportApiJob(long timestamp) throws IOException, JSONException {
    Object[] reportCaptures =
        DebugReportingJobHandlerWrapper.spyPerformScheduledPendingReports(
            mDatastoreManager, sContext);

    processActualDebugReports(
        timestamp, (List<Uri>) reportCaptures[1], (List<JSONObject>) reportCaptures[2]);
  }

  @Override
  void processAction(InstallApp installApp) {
    Assert.assertTrue(
        "measurementDao.doInstallAttribution failed",
        mDatastoreManager.runInTransaction(
            measurementDao ->
                measurementDao.doInstallAttribution(installApp.mUri, installApp.mTimestamp)));
  }

  @Override
  void processAction(UninstallApp uninstallApp) {
    Assert.assertTrue(
        "measurementDao.undoInstallAttribution failed",
        mMeasurementImpl.deletePackageRecords(uninstallApp.mUri, uninstallApp.mTimestamp));
  }

  @Override
  void processAction(EventReportingJob reportingJob) throws IOException, JSONException {
    long earliestValidInsertion = reportingJob.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    runDeleteExpiredRecordsJob(earliestValidInsertion);

    Object[] eventCaptures =
        EventReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
            mDatastoreManager,
            reportingJob.mTimestamp
                - Flags.DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
            reportingJob.mTimestamp,
            false,
            mFlags,
            ApplicationProvider.getApplicationContext());

    processActualEventReports(
        (List<EventReport>) eventCaptures[0],
        (List<Uri>) eventCaptures[1],
        (List<JSONObject>) eventCaptures[2]);
  }

  @Override
  void processAction(AggregateReportingJob reportingJob) throws IOException, JSONException {
    long maxAggregateReportUploadRetryWindowMs =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
    long earliestValidInsertion = reportingJob.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    runDeleteExpiredRecordsJob(earliestValidInsertion);

    Object[] aggregateCaptures =
        AggregateReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
            mDatastoreManager,
            reportingJob.mTimestamp - maxAggregateReportUploadRetryWindowMs,
            reportingJob.mTimestamp,
            false,
            mFlags);

    processActualAggregateReports(
        (List<AggregateReport>) aggregateCaptures[0],
        (List<Uri>) aggregateCaptures[1],
        (List<JSONObject>) aggregateCaptures[2]);
  }

  // Class extensions may need different processing to prepare for result evaluation.
  void processActualEventReports(
      List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads)
      throws JSONException {
    List<JSONObject> eventReportObjects =
        getActualEventReportObjects(eventReports, destinations, payloads);
    mActualOutput.mEventReportObjects.addAll(eventReportObjects);
  }

  void processActualDebugEventReports(
      long triggerTime,
      List<EventReport> eventReports,
      List<Uri> destinations,
      List<JSONObject> payloads)
      throws JSONException {
    List<JSONObject> eventReportObjects =
        getActualEventReportObjects(eventReports, destinations, payloads);
    for (JSONObject obj : eventReportObjects) {
      obj.put(TestFormatJsonMapping.REPORT_TIME_KEY, String.valueOf(triggerTime));
    }
    mActualOutput.mDebugEventReportObjects.addAll(eventReportObjects);
  }

  void processActualDebugReports(
      long timestamp, List<Uri> destinations, List<JSONObject> payloads) {
    List<JSONObject> debugReportObjects =
        getActualDebugReportObjects(timestamp, destinations, payloads);
    mActualOutput.mDebugReportObjects.addAll(debugReportObjects);
  }

  private List<JSONObject> getActualEventReportObjects(
      List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads) {
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < destinations.size(); i++) {
      Map<String, Object> map = new HashMap<>();
      map.put(TestFormatJsonMapping.REPORT_TIME_KEY, eventReports.get(i).getReportTime());
      map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
      map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
      result.add(new JSONObject(map));
    }
    return result;
  }

  private List<JSONObject> getActualDebugReportObjects(
      long timestamp, List<Uri> destinations, List<JSONObject> payloads) {
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < destinations.size(); i++) {
      Map<String, Object> map = new HashMap<>();
      map.put(TestFormatJsonMapping.REPORT_TIME_KEY, String.valueOf(timestamp));
      map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
      map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
      result.add(new JSONObject(map));
    }
    return result;
  }

  // Class extensions may need different processing to prepare for result evaluation.
  void processActualAggregateReports(
      List<AggregateReport> aggregateReports, List<Uri> destinations, List<JSONObject> payloads)
      throws JSONException {
    List<JSONObject> aggregateReportObjects =
        getActualAggregateReportObjects(aggregateReports, destinations, payloads, 0L);
    mActualOutput.mAggregateReportObjects.addAll(aggregateReportObjects);
  }

  void processActualDebugAggregateReports(
      List<AggregateReport> aggregateReports, List<Uri> destinations, List<JSONObject> payloads)
      throws JSONException {
    List<JSONObject> aggregateReportObjects =
        getActualAggregateReportObjects(
            aggregateReports, destinations, payloads, TimeUnit.HOURS.toMillis(1));
    mActualOutput.mDebugAggregateReportObjects.addAll(aggregateReportObjects);
  }

  private List<JSONObject> getActualAggregateReportObjects(
      List<AggregateReport> aggregateReports,
      List<Uri> destinations,
      List<JSONObject> payloads,
      long reportDelay)
      throws JSONException {
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < destinations.size(); i++) {
      JSONObject sharedInfo = new JSONObject(payloads.get(i).getString("shared_info"));
      result.add(
          new JSONObject()
              .put(
                  TestFormatJsonMapping.REPORT_TIME_KEY,
                  String.valueOf(
                      aggregateReports.get(i).getApi().equals("attribution-reporting-debug")
                          ? aggregateReports.get(i).getScheduledReportTime()
                          : aggregateReports.get(i).getScheduledReportTime()
                              // Debug aggregate reports have the same
                              // scheduled
                              // report time as regular aggregate
                              // reports but are
                              // sent without delay.
                              - reportDelay))
              .put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString())
              .put(
                  TestFormatJsonMapping.PAYLOAD_KEY,
                  getActualAggregatablePayloadForTest(sharedInfo, payloads.get(i))));
    }
    return result;
  }

  private static JSONObject getActualAggregatablePayloadForTest(
      JSONObject sharedInfo, JSONObject data) throws JSONException {
    String payload =
        data.getJSONArray("aggregation_service_payloads").getJSONObject(0).getString("payload");

    final byte[] decryptedPayload =
        HpkeJni.decrypt(
            decode(AggregateCryptoFixture.getPrivateKeyBase64()),
            decode(payload),
            (AggregateCryptoFixture.getSharedInfoPrefix() + sharedInfo.toString()).getBytes());

    String sourceDebugKey = data.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY);
    String triggerDebugKey = data.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY);

    JSONObject sharedInfoJson = new JSONObject();
    for (String key : sAggregateReportSharedInfoKeys) {
      if (sharedInfo.has(key)) {
        sharedInfoJson.put(key, sharedInfo.getString(key));
      }
    }

    JSONObject aggregateJson =
        new JSONObject()
            .put(AggregateReportPayloadKeys.SHARED_INFO, sharedInfoJson)
            .put(
                AggregateReportPayloadKeys.AGGREGATION_COORDINATOR_ORIGIN,
                data.optString("aggregation_coordinator_origin", ""))
            .put(
                AggregateReportPayloadKeys.HISTOGRAMS,
                getActualAggregateHistograms(decryptedPayload));
    if (!sourceDebugKey.isEmpty()) {
      aggregateJson.put(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, sourceDebugKey);
    }
    if (!triggerDebugKey.isEmpty()) {
      aggregateJson.put(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, triggerDebugKey);
    }
    if (!data.isNull(AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID)) {
      aggregateJson.put(
          AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID,
          data.optString(AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID));
    }

    return aggregateJson;
  }

  private static JSONArray getActualAggregateHistograms(byte[] encodedCborPayload)
      throws JSONException {
    List<JSONObject> result = new ArrayList<>();

    try {
      final List<DataItem> dataItems =
          new CborDecoder(new ByteArrayInputStream(encodedCborPayload)).decode();
      final co.nstant.in.cbor.model.Map payload = (co.nstant.in.cbor.model.Map) dataItems.get(0);
      final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
      for (DataItem i : payloadArray.getDataItems()) {
        co.nstant.in.cbor.model.Map m = (co.nstant.in.cbor.model.Map) i;
        Object value =
            "0x"
                + new BigInteger(1, ((ByteString) m.get(new UnicodeString("bucket"))).getBytes())
                    .toString(16);
        result.add(
            new JSONObject()
                .put(AggregateHistogramKeys.BUCKET, value)
                .put(
                    AggregateHistogramKeys.VALUE,
                    new BigInteger(1, ((ByteString) m.get(new UnicodeString("value"))).getBytes())
                        .intValue()));
      }
    } catch (CborException e) {
      throw new JSONException(e);
    }

    return new JSONArray(result);
  }

  protected static Map<String, List<String>> getNextResponse(
      Map<String, List<Map<String, List<String>>>> uriToResponseHeadersMap, String uri) {
    List<Map<String, List<String>>> responseList = uriToResponseHeadersMap.get(uri);
    return responseList.remove(0);
  }

  protected static UriConfig getNextUriConfig(List<UriConfig> uriConfigs) {
    return uriConfigs.remove(0);
  }

  protected void runDeleteExpiredRecordsJob(long earliestValidInsertion) {
    int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;
    mDatastoreManager.runInTransaction(
        dao -> dao.deleteExpiredRecords(earliestValidInsertion, retryLimit, null, 0));
  }

  void updateEnrollment(String uri) {
    if (mSeenUris.contains(uri)) {
      return;
    }
    mSeenUris.add(uri);
    String enrollmentId = getEnrollmentId(uri);
    Set<String> attributionRegistrationUrls;
    EnrollmentData enrollmentData = mEnrollmentDao.getEnrollmentData(enrollmentId);
    if (enrollmentData != null) {
      mEnrollmentDao.delete(enrollmentId);
      attributionRegistrationUrls =
          new HashSet<>(enrollmentData.getAttributionSourceRegistrationUrl());
      attributionRegistrationUrls.addAll(enrollmentData.getAttributionTriggerRegistrationUrl());
      attributionRegistrationUrls.add(uri);
    } else {
      attributionRegistrationUrls = Set.of(uri);
    }
    Uri registrationUri = Uri.parse(uri);
    String reportingUrl = registrationUri.getScheme() + "://" + registrationUri.getAuthority();
    insertEnrollment(enrollmentId, reportingUrl, new ArrayList<>(attributionRegistrationUrls));
  }

  private void insertEnrollment(
      String enrollmentId, String reportingUrl, List<String> attributionRegistrationUrls) {
    EnrollmentData enrollmentData =
        new EnrollmentData.Builder()
            .setEnrollmentId(enrollmentId)
            .setAttributionSourceRegistrationUrl(attributionRegistrationUrls)
            .setAttributionTriggerRegistrationUrl(attributionRegistrationUrls)
            .setAttributionReportingUrl(List.of(reportingUrl))
            .build();
    Assert.assertTrue(mEnrollmentDao.insert(enrollmentData));
  }

  private String getEnrollmentId(String uri) {
    Optional<Uri> domainAndScheme = WebAddresses.topPrivateDomainAndScheme(Uri.parse(uri));
    String authority = domainAndScheme.get().getAuthority();
    return mUriToEnrollmentId.computeIfAbsent(authority, k -> "enrollment-id-" + authority);
  }

  private static byte[] decode(String value) {
    return Base64.getDecoder().decode(value.getBytes());
  }
}
