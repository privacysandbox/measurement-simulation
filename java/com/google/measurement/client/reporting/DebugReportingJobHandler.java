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

import com.google.measurement.client.Context;
import com.google.measurement.client.Uri;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.ErrorLogUtil;
import com.google.measurement.client.Flags;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.VisibleForTesting;
import com.google.measurement.client.reporting.ReportingStatus.FailureStatus;
import com.google.measurement.client.reporting.ReportingStatus.UploadStatus;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONArray;
import org.json.JSONException;

/** Class for handling debug reporting. */
public class DebugReportingJobHandler {

  private static final int MAX_HTTP_SUCCESS_CODE = 299;
  private final DatastoreManager mDatastoreManager;
  private final Flags mFlags;
  private ReportingStatus.UploadMethod mUploadMethod;
  private AdServicesLogger mLogger;

  private Context mContext;

  @VisibleForTesting
  DebugReportingJobHandler(
      DatastoreManager datastoreManager, Flags flags, AdServicesLogger logger, Context context) {
    this(datastoreManager, flags, logger, ReportingStatus.UploadMethod.UNKNOWN, context);
  }

  DebugReportingJobHandler(
      DatastoreManager datastoreManager,
      Flags flags,
      AdServicesLogger logger,
      ReportingStatus.UploadMethod uploadMethod,
      Context context) {
    mDatastoreManager = datastoreManager;
    mFlags = flags;
    mLogger = logger;
    mUploadMethod = uploadMethod;
    mContext = context;
  }

  /** Finds all debug reports and attempts to upload them individually. */
  void performScheduledPendingReports() {
    Optional<List<String>> pendingDebugReports =
        mDatastoreManager.runInTransactionWithResult(IMeasurementDao::getDebugReportIds);
    if (!pendingDebugReports.isPresent()) {
      LoggerFactory.getMeasurementLogger().d("Pending Debug Reports not found");
      return;
    }

    List<String> pendingDebugReportIdsInWindow = pendingDebugReports.get();
    for (String debugReportId : pendingDebugReportIdsInWindow) {
      // If the job service's requirements specified at runtime are no longer met, the job
      // service will interrupt this thread.  If the thread has been interrupted, it will exit
      // early.
      if (Thread.currentThread().isInterrupted()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "DebugReportingJobHandler performScheduledPendingReports "
                    + "thread interrupted, exiting early.");
        return;
      }

      ReportingStatus reportingStatus = new ReportingStatus();
      if (mUploadMethod != null) {
        reportingStatus.setUploadMethod(mUploadMethod);
      }
      performReport(debugReportId, reportingStatus);
      if (reportingStatus.getUploadStatus() == ReportingStatus.UploadStatus.FAILURE) {
        mDatastoreManager.runInTransaction(
            (dao) -> {
              int retryCount =
                  dao.incrementAndGetReportingRetryCount(
                      debugReportId, KeyValueData.DataType.DEBUG_REPORT_RETRY_COUNT);
              reportingStatus.setRetryCount(retryCount);
            });
      }
    }
  }

  /**
   * Perform reporting by finding the relevant {@link DebugReport} and making an HTTP POST request
   * to the specified report to URL with the report data as a JSON in the body.
   *
   * @param debugReportId for the datastore id of the {@link DebugReport}
   */
  void performReport(String debugReportId, ReportingStatus reportingStatus) {
    String enrollmentId = null;
    Optional<DebugReport> debugReportOpt =
        mDatastoreManager.runInTransactionWithResult((dao) -> dao.getDebugReport(debugReportId));

    if (debugReportOpt.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Reading Scheduled Debug Report failed");
      reportingStatus.setReportType(ReportingStatus.ReportType.VERBOSE_DEBUG_UNKNOWN);
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_FOUND, enrollmentId);
      return;
    }

    DebugReport debugReport = debugReportOpt.get();
    enrollmentId = debugReport.getEnrollmentId();
    reportingStatus.setReportingDelay(System.currentTimeMillis() - debugReport.getInsertionTime());
    reportingStatus.setReportType(debugReport.getType());
    reportingStatus.setSourceRegistrant(getAppPackageName(debugReport));

    try {
      Uri reportingOrigin = debugReport.getRegistrationOrigin();
      JSONArray debugReportJsonPayload = createReportJsonPayload(debugReport);
      int returnCode = makeHttpPostRequest(reportingOrigin, debugReportJsonPayload);

      // Code outside [200, 299] is a failure according to HTTP protocol.
      if (returnCode < HttpURLConnection.HTTP_OK || returnCode > MAX_HTTP_SUCCESS_CODE) {
        LoggerFactory.getMeasurementLogger().d("Sending debug report failed with http error");
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
                dao.deleteDebugReport(debugReport.getId());
              });
      if (!success) {
        LoggerFactory.getMeasurementLogger().d("Deleting debug report failed");
        setAndLogReportingStatus(
            reportingStatus, UploadStatus.FAILURE, FailureStatus.DATASTORE, enrollmentId);
        return;
      }
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.SUCCESS, FailureStatus.UNKNOWN, enrollmentId);

    } catch (IOException e) {
      LoggerFactory.getMeasurementLogger()
          .d(e, "Network error occurred when attempting to deliver debug report.");
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.NETWORK, enrollmentId);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .d(e, "Serialization error occurred at debug report delivery.");
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.SERIALIZATION_ERROR, enrollmentId);
      if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
        // Unrecoverable state - delete the report.
        mDatastoreManager.runInTransaction(dao -> dao.deleteDebugReport(debugReportId));
      }
      if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        // JSONException is unexpected.
        throw new IllegalStateException("Serialization error occurred at event report delivery", e);
      }
    } catch (Exception e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Unexpected exception occurred when attempting to deliver debug report.");
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.UNKNOWN, enrollmentId);
      if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        throw e;
      }
    }
  }

  /** Creates the JSON payload for the POST request from the DebugReport. */
  @VisibleForTesting
  JSONArray createReportJsonPayload(DebugReport debugReport) throws JSONException {
    JSONArray debugReportJsonPayload = new JSONArray();
    debugReportJsonPayload.put(debugReport.toPayloadJson());
    return debugReportJsonPayload;
  }

  /** Makes the POST request to the reporting URL. */
  @VisibleForTesting
  public int makeHttpPostRequest(Uri adTechDomain, JSONArray debugReportPayload)
      throws IOException {
    DebugReportSender debugReportSender = new DebugReportSender(mContext);
    return debugReportSender.sendReport(adTechDomain, debugReportPayload);
  }

  private String getAppPackageName(DebugReport debugReport) {
    if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
      return "";
    }
    Uri sourceRegistrant = debugReport.getRegistrant();
    if (sourceRegistrant == null) {
      LoggerFactory.getMeasurementLogger().d("Source registrant is null on debug report");
      return "";
    }
    return sourceRegistrant.toString();
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
