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

package com.google.measurement.client.reporting;

import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;

import com.google.measurement.client.AndroidTimeSource;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Context;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Pair;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.ErrorLogUtil;
import com.google.measurement.client.Flags;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.VisibleForTesting;
import com.google.measurement.client.reporting.ReportingStatus.UploadStatus;
import com.google.measurement.client.reporting.ReportingStatus.FailureStatus;
import com.google.measurement.client.util.Applications;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/** Class for handling event level reporting. */
public class EventReportingJobHandler {
  private static final int MAX_HTTP_SUCCESS_CODE = 299;
  private final DatastoreManager mDatastoreManager;
  private boolean mIsDebugInstance;
  private final Flags mFlags;
  private final AdServicesLogger mLogger;
  private ReportingStatus.ReportType mReportType;
  private ReportingStatus.UploadMethod mUploadMethod;
  private final AndroidTimeSource mTimeSource;

  private Context mContext;

  @VisibleForTesting
  EventReportingJobHandler(
      DatastoreManager datastoreManager,
      Flags flags,
      Context context,
      AndroidTimeSource timeSource) {
    this(datastoreManager, flags, AdServicesLoggerImpl.getInstance(), context, timeSource);
  }

  EventReportingJobHandler(
      DatastoreManager datastoreManager,
      Flags flags,
      AdServicesLogger logger,
      ReportingStatus.ReportType reportType,
      ReportingStatus.UploadMethod uploadMethod,
      Context context,
      AndroidTimeSource timeSource) {
    mDatastoreManager = datastoreManager;
    mFlags = flags;
    mLogger = logger;
    mReportType = reportType;
    mUploadMethod = uploadMethod;
    mContext = context;
    mTimeSource = timeSource;
  }

  @VisibleForTesting
  EventReportingJobHandler(
      DatastoreManager datastoreManager,
      Flags flags,
      AdServicesLogger logger,
      Context context,
      AndroidTimeSource timeSource) {
    mDatastoreManager = datastoreManager;
    mFlags = flags;
    mLogger = logger;
    mContext = context;
    mTimeSource = timeSource;
  }

  /**
   * Set isDebugInstance
   *
   * @param isDebugInstance indicates a debug event report
   * @return the instance of EventReportingJobHandler
   */
  public EventReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
    mIsDebugInstance = isDebugInstance;
    return this;
  }

  /**
   * Finds all reports within the given window that have a status {@link EventReport.Status#PENDING}
   * or {@link EventReport.DebugReportStatus#PENDING} based on mIsDebugReport and attempts to upload
   * them individually.
   *
   * @param windowStartTime Start time of the search window
   * @param windowEndTime End time of the search window
   * @return always return true to signal to JobScheduler that the task is done.
   */
  synchronized boolean performScheduledPendingReportsInWindow(
      long windowStartTime, long windowEndTime) {
    Optional<List<String>> pendingEventReportsInWindowOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> {
              if (mIsDebugInstance) {
                return dao.getPendingDebugEventReportIds();
              } else {
                return dao.getPendingEventReportIdsInWindow(windowStartTime, windowEndTime);
              }
            });
    if (!pendingEventReportsInWindowOpt.isPresent()) {
      // Failure during event report retrieval
      return true;
    }

    List<String> pendingEventReportIdsInWindow = pendingEventReportsInWindowOpt.get();
    for (String eventReportId : pendingEventReportIdsInWindow) {

      // If the job service's requirements specified at runtime are no longer met, the job
      // service will interrupt this thread.  If the thread has been interrupted, it will exit
      // early.
      if (Thread.currentThread().isInterrupted()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "EventReportingJobHandler performScheduledPendingReports "
                    + "thread interrupted, exiting early.");
        return true;
      }

      // TODO: Use result to track rate of success vs retry vs failure
      ReportingStatus reportingStatus = new ReportingStatus();
      if (mReportType != null) {
        reportingStatus.setReportType(mReportType);
      }
      if (mUploadMethod != null) {
        reportingStatus.setUploadMethod(mUploadMethod);
      }
      performReport(eventReportId, reportingStatus);

      if (reportingStatus.getUploadStatus() == UploadStatus.FAILURE) {
        mDatastoreManager.runInTransaction(
            (dao) -> {
              int retryCount =
                  dao.incrementAndGetReportingRetryCount(
                      eventReportId,
                      mIsDebugInstance
                          ? KeyValueData.DataType.DEBUG_EVENT_REPORT_RETRY_COUNT
                          : KeyValueData.DataType.EVENT_REPORT_RETRY_COUNT);
              reportingStatus.setRetryCount(retryCount);
            });
      }
    }
    return true;
  }

  private String getAppPackageName(EventReport eventReport) {
    if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
      return "";
    }
    if (eventReport.getSourceId() == null) {
      LoggerFactory.getMeasurementLogger().d("SourceId is null on event report.");
      return "";
    }
    Optional<String> sourceRegistrant =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getSourceRegistrant(eventReport.getSourceId()));
    if (!sourceRegistrant.isPresent()) {
      LoggerFactory.getMeasurementLogger().d("Source registrant not found");
      return "";
    }
    return sourceRegistrant.get();
  }

  /**
   * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST request
   * to the specified report to URL with the report data as a JSON in the body.
   *
   * @param eventReportId for the datastore id of the {@link EventReport}
   */
  synchronized void performReport(String eventReportId, ReportingStatus reportingStatus) {
    String enrollmentId = null;
    Optional<EventReport> eventReportOpt =
        mDatastoreManager.runInTransactionWithResult((dao) -> dao.getEventReport(eventReportId));
    if (eventReportOpt.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Event report not found");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_FOUND, enrollmentId);
      return;
    }
    EventReport eventReport = eventReportOpt.get();
    enrollmentId = eventReport.getEnrollmentId();
    reportingStatus.setReportingDelay(
        mTimeSource.currentTimeMillis() - eventReport.getReportTime());
    reportingStatus.setSourceRegistrant(getAppPackageName(eventReport));
    if (mIsDebugInstance
        && eventReport.getDebugReportStatus() != EventReport.DebugReportStatus.PENDING) {
      LoggerFactory.getMeasurementLogger().d("debugging status is not pending");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_PENDING, enrollmentId);
      return;
    }

    if (!mIsDebugInstance && eventReport.getStatus() != EventReport.Status.PENDING) {
      LoggerFactory.getMeasurementLogger().d("event report status is not pending");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_PENDING, enrollmentId);
      return;
    }

    // Event Report on device for more than minimum lifespan and source/trigger app is
    // uninstalled then we skip sending the report.
    if (mFlags.getMeasurementEnableMinReportLifespanForUninstall()
        && eventReportCreatedBeforeLifespan(eventReport.getTriggerTime())
        && (!anyPublisherAppInstalled(eventReport)
            || !anyTriggerDestinationAppInstalled(eventReport))) {
      mDatastoreManager.runInTransaction(dao -> dao.deleteEventReport(eventReport));
      setAndLogReportingStatus(
          reportingStatus,
          UploadStatus.FAILURE,
          FailureStatus.APP_UNINSTALLED_OR_OUTSIDE_WINDOW,
          enrollmentId);
      return;
    }

    try {
      Uri reportingOrigin = eventReport.getRegistrationOrigin();
      JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
      Boolean triggerDebuggingAvailable = null;
      if (mFlags.getMeasurementEnableTriggerDebugSignal()) {
        Optional<Boolean> hasTriggerDebug =
            mDatastoreManager.runInTransactionWithResult(
                dao -> getTriggerDebugAvailability(eventReport, dao));
        if (hasTriggerDebug.isPresent()) {
          triggerDebuggingAvailable = hasTriggerDebug.get();
        }
      }
      int returnCode =
          makeHttpPostRequest(reportingOrigin, eventReportJsonPayload, triggerDebuggingAvailable);

      // Code outside [200, 299] is a failure according to HTTP protocol.
      if (returnCode < HttpURLConnection.HTTP_OK || returnCode > MAX_HTTP_SUCCESS_CODE) {
        setAndLogReportingStatus(
            reportingStatus,
            UploadStatus.FAILURE,
            FailureStatus.UNSUCCESSFUL_HTTP_RESPONSE_CODE,
            enrollmentId);
        return;
      }

      boolean success =
          mDatastoreManager.runInTransaction(
              (dao) -> {
                if (mIsDebugInstance) {
                  dao.markEventDebugReportDelivered(eventReportId);
                } else {
                  dao.markEventReportStatus(eventReportId, EventReport.Status.DELIVERED);
                }
                if (mFlags.getMeasurementEnableReinstallReattribution()) {
                  updateAppReportHistory(eventReport, dao);
                }
              });
      if (!success) {
        setAndLogReportingStatus(
            reportingStatus, UploadStatus.FAILURE, FailureStatus.DATASTORE, enrollmentId);
        return;
      }

      setAndLogReportingStatus(
          reportingStatus, UploadStatus.SUCCESS, FailureStatus.UNKNOWN, enrollmentId);

    } catch (IOException e) {
      LoggerFactory.getMeasurementLogger()
          .d(e, "Network error occurred when attempting to deliver event report.");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.NETWORK, enrollmentId);
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .d(e, "Serialization error occurred at event report delivery.");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.SERIALIZATION_ERROR, enrollmentId);
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
        // Unrecoverable state - delete the report.
        mDatastoreManager.runInTransaction(
            dao -> dao.markEventReportStatus(eventReportId, EventReport.Status.MARKED_TO_DELETE));
      }

      if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        // JSONException is unexpected.
        throw new IllegalStateException("Serialization error occurred at event report delivery", e);
      }
    } catch (Exception e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Unexpected exception occurred when attempting to deliver event report.");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.UNKNOWN, enrollmentId);
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        throw e;
      }
    }
  }

  private void updateAppReportHistory(EventReport eventReport, IMeasurementDao dao)
      throws DatastoreException {
    Pair<List<Uri>, List<Uri>> destinations = dao.getSourceDestinations(eventReport.getSourceId());
    List<Uri> appDestinations = destinations.first;
    List<Uri> webDestinations = destinations.second;
    if (appDestinations.isEmpty()
        || !eventReport.getAttributionDestinations().contains(appDestinations.get(0))) {
      return;
    }
    if (!webDestinations.isEmpty()
        && dao.getSource(eventReport.getSourceId()).hasCoarseEventReportDestinations()) {
      return;
    }
    dao.insertOrUpdateAppReportHistory(
        appDestinations.get(0), eventReport.getRegistrationOrigin(), eventReport.getReportTime());
  }

  /** Creates the JSON payload for the POST request from the EventReport. */
  @VisibleForTesting
  JSONObject createReportJsonPayload(EventReport eventReport) throws JSONException {
    return new EventReportPayload.Builder()
        .setReportId(eventReport.getId())
        .setSourceEventId(eventReport.getSourceEventId())
        .setAttributionDestination(eventReport.getAttributionDestinations())
        .setScheduledReportTime(
            String.valueOf(TimeUnit.MILLISECONDS.toSeconds(eventReport.getReportTime())))
        .setTriggerData(eventReport.getTriggerData())
        .setSourceType(eventReport.getSourceType().getValue())
        .setRandomizedTriggerRate(eventReport.getRandomizedTriggerRate())
        .setSourceDebugKey(eventReport.getSourceDebugKey())
        .setTriggerDebugKey(eventReport.getTriggerDebugKey())
        .setTriggerDebugKeys(eventReport.getTriggerDebugKeys())
        .setTriggerSummaryBucket(eventReport.getTriggerSummaryBucket())
        .build()
        .toJson();
  }

  /** Makes the POST request to the reporting URL. */
  @VisibleForTesting
  public int makeHttpPostRequest(
      Uri adTechDomain, JSONObject eventReportPayload, @Nullable Boolean hasTriggerDebug)
      throws IOException {
    EventReportSender eventReportSender = new EventReportSender(mIsDebugInstance, mContext);
    Map<String, String> headers =
        hasTriggerDebug == null
            ? null
            : Map.of("Trigger-Debugging-Available", hasTriggerDebug.toString());
    return eventReportSender.sendReportWithHeaders(adTechDomain, eventReportPayload, headers);
  }

  @Nullable
  private Boolean getTriggerDebugAvailability(EventReport eventReport, IMeasurementDao dao)
      throws DatastoreException {
    Source source = dao.getSource(eventReport.getSourceId());
    // Only get the data when web destinations are available.
    if (!source.hasWebDestinations()) {
      return null;
    }

    String triggerId = eventReport.getTriggerId();
    // TriggerId is null for fake event reports. Randomize the value returned for fake reports
    // with given probability which is adjusted based on the 3PCD progress.
    if (triggerId == null) {
      return ThreadLocalRandom.current().nextFloat()
          < mFlags.getMeasurementTriggerDebugSignalProbabilityForFakeReports();
    }

    Trigger trigger = dao.getTrigger(triggerId);
    boolean hasTriggerArDebug = trigger.hasArDebugPermission();
    boolean hasAdId = trigger.hasAdIdPermission();
    // When coarse_event_report_destinations = true and both app and web destinations are
    // available in source, check either adId or arDebug permission to avoid leaking the
    // destination type.
    if (source.hasAppDestinations() && source.hasCoarseEventReportDestinations()) {
      if (!mFlags.getMeasurementEnableEventTriggerDebugSignalForCoarseDestination()) {
        return null;
      }
      return hasAdId || hasTriggerArDebug;
    }
    // When coarse_event_report_destinations = false or only web destinations are available,
    // check arDebug value.
    return hasTriggerArDebug;
  }

  private boolean eventReportCreatedBeforeLifespan(long triggerTime) {
    return triggerTime
            + TimeUnit.SECONDS.toMillis(mFlags.getMeasurementMinReportLifespanForUninstallSeconds())
        < mTimeSource.currentTimeMillis();
  }

  private boolean anyTriggerDestinationAppInstalled(EventReport eventReport) {
    return Applications.anyAppsInstalled(mContext, eventReport.getAttributionDestinations());
  }

  private boolean anyPublisherAppInstalled(EventReport eventReport) {
    Optional<Source> sourceOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getSource(eventReport.getSourceId()));
    return sourceOpt.isPresent()
        && Applications.anyAppsInstalled(mContext, List.of(sourceOpt.get().getPublisher()));
  }

  private void setAndLogReportingStatus(
      ReportingStatus reportingStatus,
      ReportingStatus.UploadStatus uploadStatus,
      ReportingStatus.FailureStatus failureStatus,
      String enrollmentId) {
    reportingStatus.setFailureStatus(failureStatus);
    reportingStatus.setUploadStatus(uploadStatus);
    logReportingStats(reportingStatus, enrollmentId);
  }

  private void logReportingStats(ReportingStatus reportingStatus, String enrollmentId) {
    mLogger.logMeasurementReports(
        new MeasurementReportsStats.Builder()
            .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
            .setType(reportingStatus.getReportType().getValue())
            .setResultCode(reportingStatus.getUploadStatus().getValue())
            .setFailureType(reportingStatus.getFailureStatus().getValue())
            .setUploadMethod(reportingStatus.getUploadMethod().getValue())
            .setReportingDelay(reportingStatus.getReportingDelay())
            .setSourceRegistrant(reportingStatus.getSourceRegistrant())
            .setRetryCount(reportingStatus.getRetryCount())
            .build(),
        enrollmentId);
  }
}
