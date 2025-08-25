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

package com.google.measurement.client;

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
import com.google.measurement.client.attribution.AttributionJobHandler;
import com.google.measurement.client.attribution.AttributionJobHandlerWrapper;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.AggregateReportingJobHandlerWrapper;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.reporting.EventReportingJobHandlerWrapper;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MockRunner extends E2EAbstractMockTest {
  private MockRunner(
      Collection<Action> actions,
      ReportObjects expectedOutput,
      ParamsProvider paramsProvider,
      String name,
      Map<String, String> phFlagsMap)
      throws RemoteException, IOException {
    super(actions, expectedOutput, paramsProvider, name, phFlagsMap);
    mAttributionHelper =
        new AttributionJobHandlerWrapper(
            new AttributionJobHandler(
                mDatastoreManager,
                mFlags,
                new DebugReportApi(
                    ApplicationProvider.getApplicationContext(),
                    mFlags,
                    new EventReportWindowCalcDelegate(mFlags),
                    new SourceNoiseHandler(mFlags)),
                new EventReportWindowCalcDelegate(mFlags),
                new SourceNoiseHandler(mFlags),
                AdServicesLoggerImpl.getInstance(),
                new XnaSourceCreator(mFlags),
                new AggregateDebugReportApi(mFlags)));

    mMeasurementImpl =
        TestObjectProvider.getMeasurementImpl(
            mDatastoreManager, mClickVerifier, mMeasurementDataDeleter, mMockContentResolver);

    mAsyncRegistrationQueueRunner =
        TestObjectProvider.getAsyncRegistrationQueueRunner(
            mSourceNoiseHandler,
            mDatastoreManager,
            mAsyncSourceFetcher,
            mAsyncTriggerFetcher,
            mDebugReportApi,
            mAggregateDebugReportApi,
            mFlags);
  }

  public MockRunner() throws RemoteException, IOException {
    this(null, null, null, null, new HashMap<>());
  }

  public void run(String[] args) throws Exception {
    JSONObject jsonObject = getJsonObject(getInputFilePath(args));
    JSONObject input = jsonObject.getJSONObject(TestFormatJsonMapping.TEST_INPUT_KEY);
    InteropTestReader interopTestReader = new InteropTestReader(jsonObject.toString());
    List<Action> actions = getActions(input, interopTestReader);
    processActions(actions);
    sendOutput();
  }

  private String getInputFilePath(String[] args) {
    // flag is not mandatory.
    if (args == null || args.length == 0) {
      return null;
    }

    if (args.length != 2 || !Objects.equals(args[0], "--input_path")) {
      System.err.println(
          "Incorrect usage: only accepted flag is --input_path. Flag must have a value."
              + "Resorting to STDIN");
      return null;
    }

    return args[1];
  }

  @Override
  void processAction(AggregateReportingJob reportingJob) throws IOException, JSONException {
    long maxAggregateReportUploadRetryWindowMs =
        Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;

    Object[] aggregateCaptures =
        AggregateReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
            mDatastoreManager,
            reportingJob.mTimestamp - maxAggregateReportUploadRetryWindowMs,
            reportingJob.mTimestamp,
            false,
            mFlags);

    mActualOutput.mAggregateReportObjects.addAll((List<JSONObject>) aggregateCaptures[2]);
  }

  @Override
  void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
    prepareRegistrationServer(triggerRegistration);
    mMeasurementImpl.register(
        triggerRegistration.mRegistrationRequest,
        triggerRegistration.mAdIdPermission,
        triggerRegistration.mTimestamp);
    mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

    // To test interactions with deletion of expired records, run event reporting and deletion
    // before performing attribution.
    processAction(
        new EventReportingJob(triggerRegistration.mTimestamp - TimeUnit.MINUTES.toMillis(1)));
    long earliestValidInsertion =
        triggerRegistration.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
    runDeleteExpiredRecordsJob(earliestValidInsertion);

    mAttributionHelper.performPendingAttributions();
    // Attribution can happen up to an hour after registration call, due to AsyncRegistration
    processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
    processActualDebugReportApiJob(triggerRegistration.mTimestamp);
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

  private static JSONObject getJsonObject(String filePath)
      throws JSONException, FileNotFoundException {
    InputStream inputStream;
    if (filePath == null || filePath.isEmpty()) {
      inputStream = System.in;
    } else {
      File file = new File(filePath);
      inputStream = new FileInputStream(file);
    }

    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    String line;
    StringBuilder stringBuilder = new StringBuilder();
    try {
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return new JSONObject(preprocessTestJson(stringBuilder.toString()));
  }

  private static List<Action> getActions(JSONObject input, InteropTestReader interopTestReader)
      throws JSONException {
    List<Action> actions = new ArrayList<>();
    actions.addAll(createSourceBasedActions(input, interopTestReader));
    actions.addAll(createTriggerBasedActions(input, interopTestReader));
    actions.addAll(createInstallActions(input));
    actions.addAll(createUninstallActions(input));

    actions.sort(Comparator.comparing(Action::getComparable));

    return actions;
  }

  private void sendOutput() throws JSONException {
    PrintWriter writer = new PrintWriter(System.out, true);
    JSONObject jsonOutput = new JSONObject();
    jsonOutput.put("aggregate_reports", new JSONArray(mActualOutput.mAggregateReportObjects));
    jsonOutput.put("event_reports", new JSONArray(mActualOutput.mEventReportObjects));
    jsonOutput.put("debug_event_reports", new JSONArray(mActualOutput.mDebugEventReportObjects));
    jsonOutput.put(
        "debug_aggregate_reports", new JSONArray(mActualOutput.mDebugAggregateReportObjects));
    jsonOutput.put("debug_reports", new JSONArray(mActualOutput.mDebugReportObjects));

    writer.println(jsonOutput);
  }

  private void processActions(List<Action> actions) throws IOException, JSONException {
    for (Action action : actions) {
      if (action instanceof RegisterSource) {
        processAction((RegisterSource) action);
      } else if (action instanceof RegisterTrigger) {
        processAction((RegisterTrigger) action);
      } else if (action instanceof RegisterWebSource) {
        processAction((RegisterWebSource) action);
      } else if (action instanceof RegisterWebTrigger) {
        processAction((RegisterWebTrigger) action);
      } else if (action instanceof RegisterListSources) {
        processAction((RegisterListSources) action);
      } else if (action instanceof EventReportingJob) {
        processAction((EventReportingJob) action);
      } else if (action instanceof AggregateReportingJob) {
        processAction((AggregateReportingJob) action);
      } else if (action instanceof InstallApp) {
        processAction((InstallApp) action);
      } else if (action instanceof UninstallApp) {
        processAction((UninstallApp) action);
      }
    }
  }
}
