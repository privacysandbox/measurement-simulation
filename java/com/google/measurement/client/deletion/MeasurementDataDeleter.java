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

package com.google.measurement.client.deletion;

import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;

import com.google.measurement.client.DeletionParam;
import com.google.measurement.client.DeletionRequest;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Pair;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.Flags;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.WipeoutStatus;
import com.google.measurement.client.aggregation.AggregateHistogramContribution;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.stats.MeasurementWipeoutStats;
import com.google.measurement.client.VisibleForTesting;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Facilitates deletion of measurement data from the database, for e.g. deletion of sources,
 * triggers, reports, attributions.
 */
public class MeasurementDataDeleter {
  static final String ANDROID_APP_SCHEME = "android-app";
  private static final int AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT = 0;
  private final DatastoreManager mDatastoreManager;
  private final Flags mFlags;
  private final AdServicesLogger mLogger;

  public MeasurementDataDeleter(DatastoreManager datastoreManager, Flags flags) {
    this(datastoreManager, flags, AdServicesLoggerImpl.getInstance());
  }

  @VisibleForTesting
  public MeasurementDataDeleter(
      DatastoreManager datastoreManager, Flags flags, AdServicesLogger logger) {
    mDatastoreManager = datastoreManager;
    mFlags = flags;
    mLogger = logger;
  }

  /**
   * Deletes all measurement data owned by a registrant and optionally providing an origin uri
   * and/or a range of dates.
   *
   * @param deletionParam contains registrant, time range, sites to consider for deletion
   * @return true if deletion was successful, false otherwise
   */
  public boolean delete(@NonNull DeletionParam deletionParam) {
    boolean result = mDatastoreManager.runInTransaction((dao) -> delete(dao, deletionParam));
    if (result) {
      // Log wipeout event triggered by request (from the delete registrations API)
      WipeoutStatus wipeoutStatus = new WipeoutStatus();
      wipeoutStatus.setWipeoutType(WipeoutStatus.WipeoutType.DELETE_REGISTRATIONS_API);
      logWipeoutStats(wipeoutStatus, getRegistrant(deletionParam.getAppPackageName()).toString());
    }
    return result;
  }

  /**
   * Deletes all measurement data for a given package name that has been uninstalled.
   *
   * @param packageName including android-app:// scheme
   * @return true if deletion deleted any record
   */
  public boolean deleteAppUninstalledData(@NonNull Uri packageName, long eventTime) {
    // Using MATCH_BEHAVIOR_PRESERVE with empty origins and domains to preserve nothing.
    // In other words, to delete all data that only matches the provided app package name.
    final DeletionParam deletionParam =
        new DeletionParam.Builder(
                /* originUris= */ Collections.emptyList(),
                /* domainUris= */ Collections.emptyList(),
                /* start= */ Instant.MIN,
                /* end= */ Instant.MAX,
                /* appPackageName= */ packageName.getHost(),
                /* sdkPackageName= */ "")
            .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
            .build();

    Optional<Boolean> result =
        mDatastoreManager.runInTransactionWithResult(
            (dao) -> {
              if (!mFlags.getMeasurementEnableReinstallReattribution()) {
                dao.undoInstallAttribution(packageName);
              }
              if (mFlags.getMeasurementEnableMinReportLifespanForUninstall()) {
                return deleteUninstall(dao, deletionParam, eventTime);
              }
              return delete(dao, deletionParam);
            });
    return result.orElse(false);
  }

  /** Returns true if any record were deleted. */
  private boolean delete(@NonNull IMeasurementDao dao, @NonNull DeletionParam deletionParam)
      throws DatastoreException {
    List<String> sourceIds =
        dao.fetchMatchingSources(
            getRegistrant(deletionParam.getAppPackageName()),
            deletionParam.getStart(),
            deletionParam.getEnd(),
            deletionParam.getOriginUris(),
            deletionParam.getDomainUris(),
            deletionParam.getMatchBehavior());
    Set<String> triggerIds =
        dao.fetchMatchingTriggers(
            getRegistrant(deletionParam.getAppPackageName()),
            deletionParam.getStart(),
            deletionParam.getEnd(),
            deletionParam.getOriginUris(),
            deletionParam.getDomainUris(),
            deletionParam.getMatchBehavior());
    return deleteInternal(dao, deletionParam, sourceIds, triggerIds);
  }

  private boolean deleteUninstall(
      @NonNull IMeasurementDao dao, @NonNull DeletionParam deletionParam, long eventTime)
      throws DatastoreException {
    Pair<List<String>, List<String>> sourceIdsUninstall =
        dao.fetchMatchingSourcesUninstall(
            getRegistrant(deletionParam.getAppPackageName()), eventTime);

    Pair<List<String>, List<String>> triggerIdsUninstall =
        dao.fetchMatchingTriggersUninstall(
            getRegistrant(deletionParam.getAppPackageName()), eventTime);

    dao.updateSourceStatus(sourceIdsUninstall.second, Source.Status.MARKED_TO_DELETE);
    dao.updateTriggerStatus(triggerIdsUninstall.second, Trigger.Status.MARKED_TO_DELETE);

    deleteInternal(dao, deletionParam, sourceIdsUninstall.first, triggerIdsUninstall.first);
    return true;
  }

  private boolean deleteInternal(
      @NonNull IMeasurementDao dao,
      @NonNull DeletionParam deletionParam,
      Collection<String> sourceIds,
      Collection<String> triggerIds)
      throws DatastoreException {
    List<String> asyncRegistrationIds =
        dao.fetchMatchingAsyncRegistrations(
            getRegistrant(deletionParam.getAppPackageName()),
            deletionParam.getStart(),
            deletionParam.getEnd(),
            deletionParam.getOriginUris(),
            deletionParam.getDomainUris(),
            deletionParam.getMatchBehavior());

    int debugReportsDeletedCount =
        dao.deleteDebugReports(
            getRegistrant(deletionParam.getAppPackageName()),
            deletionParam.getStart(),
            deletionParam.getEnd());

    final boolean containsRecordsToBeDeleted =
        !sourceIds.isEmpty() || !triggerIds.isEmpty() || !asyncRegistrationIds.isEmpty();
    if (!containsRecordsToBeDeleted) {
      return debugReportsDeletedCount > 0;
    }

    // Reset aggregate contributions and dedup keys on sources for triggers to be
    // deleted.
    List<AggregateReport> aggregateReports =
        dao.fetchMatchingAggregateReports(sourceIds, triggerIds);
    resetAggregateContributions(dao, aggregateReports);
    resetAggregateReportDedupKeys(dao, aggregateReports);
    List<EventReport> eventReports;
    if (mFlags.getMeasurementFlexibleEventReportingApiEnabled()) {
      /*
       Because some triggers may not be stored in the event report table in
       the flexible event report API, we must extract additional related
       triggers from the source table.
      */
      Set<String> extendedSourceIds = dao.fetchFlexSourceIdsFor(triggerIds);

      // IMeasurementDao::fetchFlexSourceIdsFor fetches only
      // sources that have trigger specs (flex API), which means we can examine
      // only their attributed trigger list.
      for (String sourceId : extendedSourceIds) {
        Source source = dao.getSource(sourceId);
        try {
          source.buildAttributedTriggers();
          triggerIds.addAll(source.getAttributedTriggerIds());
          // Delete all attributed triggers for the source.
          dao.updateSourceAttributedTriggers(sourceId, new JSONArray().toString());
        } catch (JSONException error) {
          LoggerFactory.getMeasurementLogger()
              .e(
                  error,
                  "MeasurementDataDeleter::delete unable to build attributed "
                      + "triggers. Source ID: %s",
                  sourceId);
        }
      }

      extendedSourceIds.addAll(sourceIds);

      eventReports = dao.fetchMatchingEventReports(extendedSourceIds, triggerIds);
    } else {
      eventReports = dao.fetchMatchingEventReports(sourceIds, triggerIds);
    }

    resetDedupKeys(dao, eventReports);

    dao.deleteAsyncRegistrations(asyncRegistrationIds);

    // Delete sources and triggers, that'll take care of deleting related reports
    // and attributions
    if (deletionParam.getDeletionMode() == DeletionRequest.DELETION_MODE_ALL) {
      dao.deleteSources(sourceIds);
      dao.deleteTriggers(triggerIds);
      return true;
    }

    // Mark reports for deletion for DELETION_MODE_EXCLUDE_INTERNAL_DATA
    for (EventReport eventReport : eventReports) {
      dao.markEventReportStatus(eventReport.getId(), EventReport.Status.MARKED_TO_DELETE);
    }

    for (AggregateReport aggregateReport : aggregateReports) {
      dao.markAggregateReportStatus(
          aggregateReport.getId(), AggregateReport.Status.MARKED_TO_DELETE);
    }

    // Finally mark sources and triggers for deletion
    dao.updateSourceStatus(sourceIds, Source.Status.MARKED_TO_DELETE);
    dao.updateTriggerStatus(triggerIds, Trigger.Status.MARKED_TO_DELETE);
    return true;
  }

  @VisibleForTesting
  void resetAggregateContributions(
      @NonNull IMeasurementDao dao, @NonNull List<AggregateReport> aggregateReports)
      throws DatastoreException {
    for (AggregateReport report : aggregateReports) {
      if (report.getSourceId() == null) {
        LoggerFactory.getMeasurementLogger().d("SourceId is null on event report.");
        return;
      }

      Source source = dao.getSource(report.getSourceId());
      int aggregateHistogramContributionsSum =
          report.extractAggregateHistogramContributions().stream()
              .mapToInt(AggregateHistogramContribution::getValue)
              .sum();

      int newAggregateContributionsSum =
          Math.max(
              (source.getAggregateContributions() - aggregateHistogramContributionsSum),
              AGGREGATE_CONTRIBUTIONS_VALUE_MINIMUM_LIMIT);

      source.setAggregateContributions(newAggregateContributionsSum);

      // Update in the DB
      dao.updateSourceAggregateContributions(source);
    }
  }

  @VisibleForTesting
  void resetDedupKeys(@NonNull IMeasurementDao dao, @NonNull List<EventReport> eventReports)
      throws DatastoreException {
    for (EventReport report : eventReports) {
      if (report.getSourceId() == null) {
        LoggerFactory.getMeasurementLogger()
            .d("resetDedupKeys: SourceId on the event report is null.");
        continue;
      }

      Source source = dao.getSource(report.getSourceId());
      UnsignedLong dedupKey = report.getTriggerDedupKey();

      // Event reports for flex API do not have trigger dedup key populated. Otherwise,
      // it may or may not be.
      if (dedupKey == null) {
        return;
      }

      if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
        try {
          source.buildAttributedTriggers();
          source
              .getAttributedTriggers()
              .removeIf(
                  attributedTrigger ->
                      dedupKey.equals(attributedTrigger.getDedupKey())
                          && Objects.equals(
                              report.getTriggerId(), attributedTrigger.getTriggerId()));
          dao.updateSourceAttributedTriggers(source.getId(), source.attributedTriggersToJson());
        } catch (JSONException e) {
          LoggerFactory.getMeasurementLogger()
              .e(e, "resetDedupKeys: failed to build attributed triggers.");
        }
      } else {
        source.getEventReportDedupKeys().remove(dedupKey);
        dao.updateSourceEventReportDedupKeys(source);
      }
    }
  }

  void resetAggregateReportDedupKeys(
      @NonNull IMeasurementDao dao, @NonNull List<AggregateReport> aggregateReports)
      throws DatastoreException {
    for (AggregateReport report : aggregateReports) {
      if (report.getSourceId() == null) {
        LoggerFactory.getMeasurementLogger().d("SourceId on the aggregate report is null.");
        continue;
      }

      Source source = dao.getSource(report.getSourceId());
      if (report.getDedupKey() == null) {
        continue;
      }
      source.getAggregateReportDedupKeys().remove(report.getDedupKey());
      dao.updateSourceAggregateReportDedupKeys(source);
    }
  }

  private Uri getRegistrant(String packageName) {
    return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
  }

  private void logWipeoutStats(WipeoutStatus wipeoutStatus, String sourceRegistrant) {
    mLogger.logMeasurementWipeoutStats(
        new MeasurementWipeoutStats.Builder()
            .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
            .setWipeoutType(wipeoutStatus.getWipeoutType().getValue())
            .setSourceRegistrant(sourceRegistrant)
            .build());
  }
}
