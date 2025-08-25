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

import static com.google.measurement.client.util.Time.roundDownToDay;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR;
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
import com.google.measurement.client.CryptoException;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.VisibleForTesting;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.aggregation.AggregateEncryptionKeyManager;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.reporting.ReportingStatus.FailureStatus;
import com.google.measurement.client.reporting.ReportingStatus.UploadStatus;
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

/** Class for handling aggregate reporting. */
public class AggregateReportingJobHandler {
  private static final int MAX_HTTP_SUCCESS_CODE = 299;

  /** {@link Uri} where attribution-success aggregate reports are sent with a delay. */
  @VisibleForTesting
  public static final String AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
      ".well-known/attribution-reporting/report-aggregate-attribution";

  /**
   * {@link Uri} where debug attribution-success aggregate reports are sent, which are essentially a
   * copy of regular trigger attribution aggregate reports, but sent immediately.
   */
  @VisibleForTesting
  public static final String DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
      ".well-known/attribution-reporting/debug/report-aggregate-attribution";

  /**
   * {@link Uri} where registration/attribution error related aggregate debug reports are sent that
   * consume from source level L1 budget.
   */
  @VisibleForTesting
  public static final String AGGREGATE_DEBUG_REPORT_URI_PATH =
      ".well-known/attribution-reporting/debug/report-aggregate-debug";

  /** "0" is the convention for indicating an excluded source registration time. */
  public static final String EXCLUDED_SOURCE_REGISTRATION_TIME = "0";

  private final DatastoreManager mDatastoreManager;
  private final AggregateEncryptionKeyManager mAggregateEncryptionKeyManager;
  private boolean mIsDebugInstance;
  private final Flags mFlags;
  private final ReportingStatus.ReportType mReportType;
  private final ReportingStatus.UploadMethod mUploadMethod;
  private final AdServicesLogger mLogger;

  private final Context mContext;
  private final AndroidTimeSource mTimeSource;

  AggregateReportingJobHandler(
      DatastoreManager datastoreManager,
      AggregateEncryptionKeyManager aggregateEncryptionKeyManager,
      Flags flags,
      AdServicesLogger logger,
      ReportingStatus.ReportType reportType,
      ReportingStatus.UploadMethod uploadMethod,
      Context context,
      AndroidTimeSource timeSource) {
    mDatastoreManager = datastoreManager;
    mAggregateEncryptionKeyManager = aggregateEncryptionKeyManager;
    mFlags = flags;
    mLogger = logger;
    mReportType = reportType;
    mUploadMethod = uploadMethod;
    mContext = context;
    mTimeSource = timeSource;
  }

  @VisibleForTesting
  AggregateReportingJobHandler(
      DatastoreManager datastoreManager,
      AggregateEncryptionKeyManager aggregateEncryptionKeyManager,
      Flags flags,
      AdServicesLogger logger,
      Context context,
      AndroidTimeSource timeSource) {
    this(
        datastoreManager,
        aggregateEncryptionKeyManager,
        flags,
        logger,
        ReportingStatus.ReportType.UNKNOWN,
        ReportingStatus.UploadMethod.UNKNOWN,
        context,
        timeSource);
  }

  /**
   * Set isDebugInstance
   *
   * @param isDebugInstance indicates a debug aggregate report
   * @return the instance of AggregateReportingJobHandler
   */
  public AggregateReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
    mIsDebugInstance = isDebugInstance;
    return this;
  }

  /**
   * Finds all aggregate reports within the given window that have a status {@link
   * AggregateReport.Status#PENDING} or {@link AggregateReport.DebugReportStatus#PENDING} based on
   * mIsDebugReport and attempts to upload them individually.
   *
   * @param windowStartTime Start time of the search window
   * @param windowEndTime End time of the search window
   * @return always return true to signal to JobScheduler that the task is done.
   */
  synchronized boolean performScheduledPendingReportsInWindow(
      long windowStartTime, long windowEndTime) {
    Optional<Map<String, List<String>>> pendingAggregateReportsInWindowOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> {
              if (mIsDebugInstance) {
                return dao.getPendingAggregateDebugReportIdsByCoordinator();
              } else {
                return dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                    windowStartTime, windowEndTime);
              }
            });
    if (pendingAggregateReportsInWindowOpt.isEmpty()) {
      // Failure during aggregate report retrieval
      return true;
    }

    Map<String, List<String>> idsByCoordinator = pendingAggregateReportsInWindowOpt.get();

    for (Map.Entry<String, List<String>> entrySet : idsByCoordinator.entrySet()) {
      String coordinator = entrySet.getKey();
      List<String> reportIds = entrySet.getValue();
      List<AggregateEncryptionKey> keys =
          mAggregateEncryptionKeyManager.getAggregateEncryptionKeys(
              Uri.parse(coordinator), reportIds.size());

      if (keys.size() == reportIds.size()) {
        for (int i = 0; i < reportIds.size(); i++) {
          // If the job service's requirements specified at runtime are no longer met, the
          // job service will interrupt this thread.  If the thread has been interrupted,
          // it will exit early.
          if (Thread.currentThread().isInterrupted()) {
            LoggerFactory.getMeasurementLogger()
                .d(
                    "AggregateReportingJobHandler"
                        + " performScheduledPendingReports thread interrupted,"
                        + " exiting early.");
            return true;
          }

          ReportingStatus reportingStatus = new ReportingStatus();
          reportingStatus.setReportType(mReportType);
          reportingStatus.setUploadMethod(mUploadMethod);
          final String aggregateReportId = reportIds.get(i);
          performReport(aggregateReportId, keys.get(i), reportingStatus);

          if (reportingStatus.getUploadStatus() == UploadStatus.FAILURE) {
            mDatastoreManager.runInTransaction(
                (dao) -> {
                  int retryCount =
                      dao.incrementAndGetReportingRetryCount(
                          aggregateReportId,
                          mIsDebugInstance
                              ? KeyValueData.DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT
                              : KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT);
                  reportingStatus.setRetryCount(retryCount);
                });
          }
        }
      } else {
        LoggerFactory.getMeasurementLogger()
            .w("The number of keys do not align with the number of reports");
      }
    }

    return true;
  }

  private String getAppPackageName(AggregateReport report) {
    if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
      return "";
    }
    if (report.getSourceId() == null) {
      LoggerFactory.getMeasurementLogger().d("SourceId is null on aggregate report.");
      return "";
    }
    Optional<String> sourceRegistrant =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getSourceRegistrant(report.getSourceId()));
    if (sourceRegistrant.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Source registrant not found");
      return "";
    }
    return sourceRegistrant.get();
  }

  /**
   * Perform aggregate reporting by finding the relevant {@link AggregateReport} and making an HTTP
   * POST request to the specified report to URL with the report data as a JSON in the body.
   *
   * @param aggregateReportId for the datastore id of the {@link AggregateReport}
   * @param key used for encrypting report payload
   */
  synchronized void performReport(
      String aggregateReportId, AggregateEncryptionKey key, ReportingStatus reportingStatus) {
    String enrollmentId = null;
    Optional<AggregateReport> aggregateReportOpt =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> dao.getAggregateReport(aggregateReportId));
    if (aggregateReportOpt.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Aggregate report not found");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_FOUND, enrollmentId);
      return;
    }
    AggregateReport aggregateReport = aggregateReportOpt.get();
    enrollmentId = aggregateReport.getEnrollmentId();
    reportingStatus.setReportingDelay(
        mTimeSource.currentTimeMillis() - aggregateReport.getScheduledReportTime());
    reportingStatus.setSourceRegistrant(getAppPackageName(aggregateReport));
    if (mIsDebugInstance
        && aggregateReport.getDebugReportStatus() != AggregateReport.DebugReportStatus.PENDING) {
      LoggerFactory.getMeasurementLogger().d("Debugging status is not pending");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_PENDING, enrollmentId);
      return;
    }

    if (!mIsDebugInstance && aggregateReport.getStatus() != AggregateReport.Status.PENDING) {
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.REPORT_NOT_PENDING, enrollmentId);
      return;
    }

    // Aggregate Report on device for more than minimum lifespan
    if (mFlags.getMeasurementEnableMinReportLifespanForUninstall()
        && aggregateReportCreatedBeforeLifespan(aggregateReport.getTriggerTime())
        && (!anyPublisherAppInstalled(aggregateReport)
            || !anyTriggerDestinationAppInstalled(aggregateReport))) {
      mDatastoreManager.runInTransaction(dao -> dao.deleteAggregateReport(aggregateReport));
      setAndLogReportingStatus(
          reportingStatus,
          UploadStatus.FAILURE,
          FailureStatus.APP_UNINSTALLED_OR_OUTSIDE_WINDOW,
          enrollmentId);
      return;
    }

    try {
      Uri reportingOrigin = aggregateReport.getRegistrationOrigin();
      JSONObject aggregateReportJsonBody =
          createReportJsonPayload(aggregateReport, reportingOrigin, key);
      Boolean triggerDebugHeaderAvailable = null;
      if (mFlags.getMeasurementEnableTriggerDebugSignal()) {
        Optional<Boolean> hasTriggerDebugSignal =
            mDatastoreManager.runInTransactionWithResult(
                (dao) -> getTriggerDebugAvailability(aggregateReport, dao));
        triggerDebugHeaderAvailable = hasTriggerDebugSignal.orElse(null);
      }

      int returnCode =
          makeHttpPostRequest(
              reportingOrigin,
              aggregateReportJsonBody,
              triggerDebugHeaderAvailable,
              aggregateReport.getApi());

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
                  dao.markAggregateDebugReportDelivered(aggregateReportId);
                } else {
                  dao.markAggregateReportStatus(
                      aggregateReportId, AggregateReport.Status.DELIVERED);
                }
                if (mFlags.getMeasurementEnableReinstallReattribution()) {
                  updateAppReportHistory(aggregateReport, dao);
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
          .d(e, "Network error occurred when attempting to deliver aggregate report.");
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.NETWORK, enrollmentId);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .d(e, "Serialization error occurred at aggregate report delivery.");
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.SERIALIZATION_ERROR, enrollmentId);
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
        // Unrecoverable state - delete the report.
        mDatastoreManager.runInTransaction(
            dao ->
                dao.markAggregateReportStatus(
                    aggregateReportId, AggregateReport.Status.MARKED_TO_DELETE));
      }

      if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        // JSONException is unexpected.
        throw new IllegalStateException(
            "Serialization error occurred at aggregate report delivery", e);
      }
    } catch (CryptoException e) {
      LoggerFactory.getMeasurementLogger().e(e, e.toString());
      setAndLogReportingStatus(
          reportingStatus, UploadStatus.FAILURE, FailureStatus.ENCRYPTION_ERROR, enrollmentId);
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
      if (mFlags.getMeasurementEnableReportingJobsThrowCryptoException()
          && ThreadLocalRandom.current().nextFloat()
              < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
        throw e;
      }
    } catch (Exception e) {
      LoggerFactory.getMeasurementLogger().e(e, e.toString());
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

  private void updateAppReportHistory(AggregateReport aggregateReport, IMeasurementDao dao)
      throws DatastoreException {
    Pair<List<Uri>, List<Uri>> destinations =
        dao.getSourceDestinations(aggregateReport.getSourceId());
    List<Uri> appDestinations = destinations.first;
    if (appDestinations.isEmpty()
        || !appDestinations.get(0).equals(aggregateReport.getAttributionDestination())
        || aggregateReport.isFakeReport()) {
      return;
    }
    dao.insertOrUpdateAppReportHistory(
        appDestinations.get(0),
        aggregateReport.getRegistrationOrigin(),
        aggregateReport.getScheduledReportTime());
  }

  private boolean aggregateReportCreatedBeforeLifespan(long triggerTime) {
    return triggerTime
            + TimeUnit.SECONDS.toMillis(mFlags.getMeasurementMinReportLifespanForUninstallSeconds())
        < mTimeSource.currentTimeMillis();
  }

  private boolean anyTriggerDestinationAppInstalled(AggregateReport aggregateReport) {
    return Applications.anyAppsInstalled(
        mContext, List.of(aggregateReport.getAttributionDestination()));
  }

  private boolean anyPublisherAppInstalled(AggregateReport aggregateReport) {
    return Applications.anyAppsInstalled(mContext, List.of(aggregateReport.getPublisher()));
  }

  /** Creates the JSON payload for the POST request from the AggregateReport. */
  @VisibleForTesting
  JSONObject createReportJsonPayload(
      AggregateReport aggregateReport, Uri reportingOrigin, AggregateEncryptionKey key)
      throws JSONException {
    return new AggregateReportBody.Builder()
        .setReportId(aggregateReport.getId())
        .setAttributionDestination(aggregateReport.getAttributionDestination().toString())
        .setSourceRegistrationTime(getSourceRegistrationTimeStr(aggregateReport))
        .setScheduledReportTime(
            String.valueOf(
                TimeUnit.MILLISECONDS.toSeconds(aggregateReport.getScheduledReportTime())))
        .setApiVersion(aggregateReport.getApiVersion())
        .setApi(aggregateReport.getApi())
        .setReportingOrigin(reportingOrigin.toString())
        .setDebugCleartextPayload(aggregateReport.getDebugCleartextPayload())
        .setSourceDebugKey(aggregateReport.getSourceDebugKey())
        .setTriggerDebugKey(aggregateReport.getTriggerDebugKey())
        .setAggregationCoordinatorOrigin(aggregateReport.getAggregationCoordinatorOrigin())
        .setDebugMode(
            aggregateReport.getSourceDebugKey() != null
                    && aggregateReport.getTriggerDebugKey() != null
                ? "enabled"
                : null)
        .setTriggerContextId(aggregateReport.getTriggerContextId())
        .build()
        .toJson(key, mFlags);
  }

  private String getSourceRegistrationTimeStr(AggregateReport aggregateReport) {
    if (aggregateReport.getSourceRegistrationTime() == null) {
      if (AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API.equals(aggregateReport.getApi())) {
        return null;
      }
      if (mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled()) {
        // A null source registration time implies the source registration time was not set.
        // We normally include this in the JSON serialization anyway, but when the feature
        // flag for making source registration time optional is enabled, send a value
        // indicating exclusion.
        return EXCLUDED_SOURCE_REGISTRATION_TIME;
      }
      return null;
    }

    return String.valueOf(
        TimeUnit.MILLISECONDS.toSeconds(
            roundDownToDay(aggregateReport.getSourceRegistrationTime())));
  }

  /** Makes the POST request to the reporting URL. */
  @VisibleForTesting
  public int makeHttpPostRequest(
      Uri adTechDomain,
      JSONObject aggregateReportBody,
      @Nullable Boolean hasTriggerDebug,
      String api)
      throws IOException {
    AggregateReportSender aggregateReportSender =
        new AggregateReportSender(mContext, getReportUriPath(api));
    Map<String, String> headers =
        hasTriggerDebug == null
            ? null
            : Map.of("Trigger-Debugging-Available", hasTriggerDebug.toString());
    return aggregateReportSender.sendReportWithHeaders(adTechDomain, aggregateReportBody, headers);
  }

  @VisibleForTesting
  String getReportUriPath(String api) {
    if (!mIsDebugInstance) {
      return AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
    }

    if (AggregateDebugReportApi.AGGREGATE_DEBUG_REPORT_API.equals(api)) {
      return AGGREGATE_DEBUG_REPORT_URI_PATH;
    }

    return DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH;
  }

  @Nullable
  private Boolean getTriggerDebugAvailability(AggregateReport aggregateReport, IMeasurementDao dao)
      throws DatastoreException {
    Trigger trigger = dao.getTrigger(aggregateReport.getTriggerId());
    // Only set the header when the conversion happens on web.
    if (trigger.getDestinationType() == EventSurfaceType.APP) {
      return null;
    }
    return trigger.hasArDebugPermission();
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
