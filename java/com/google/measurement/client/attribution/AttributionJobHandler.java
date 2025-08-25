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

import static com.google.measurement.client.PrivacyParams.AGGREGATE_REPORT_DELAY_SPAN;
import static com.google.measurement.client.PrivacyParams.AGGREGATE_REPORT_MIN_DELAY;
import static com.google.measurement.client.util.Time.roundDownToDay;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;

import com.google.measurement.client.DelayedSourceRegistrationStatus;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Pair;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.AdServicesConfig;
import com.google.measurement.client.Flags;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.WebAddresses;
import com.google.measurement.client.AttributedTrigger;
import com.google.measurement.client.Attribution;
import com.google.measurement.client.AttributionConfig;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.EventTrigger;
import com.google.measurement.client.FilterMap;
import com.google.measurement.client.PrivacyParams;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.TriggerSpec;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.XnaSourceCreator;
import com.google.measurement.client.aggregation.AggregatableAttributionSource;
import com.google.measurement.client.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.aggregation.AggregatableValuesConfig;
import com.google.measurement.client.aggregation.AggregateAttributionData;
import com.google.measurement.client.aggregation.AggregateDeduplicationKey;
import com.google.measurement.client.aggregation.AggregateHistogramContribution;
import com.google.measurement.client.aggregation.AggregatePayloadGenerator;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.attribution.AttributionStatus.AttributionResult;
import com.google.measurement.client.attribution.AttributionStatus.FailureType;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.DebugKeyAccessor;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate.MomentPlacement;
import com.google.measurement.client.util.BaseUriExtractor;
import com.google.measurement.client.util.Filter;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.stats.MeasurementAttributionStats;
import com.google.measurement.client.stats.MeasurementDelayedSourceRegistrationStats;
import com.google.measurement.client.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AttributionJobHandler {

  @VisibleForTesting public static final String API = "attribution-reporting";
  private static final String API_VERSION = "0.1";
  private static final String AGGREGATE_REPORT_DELAY_DELIMITER = ",";
  private final DatastoreManager mDatastoreManager;
  private final DebugReportApi mDebugReportApi;
  private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
  private final SourceNoiseHandler mSourceNoiseHandler;
  private final AdServicesLogger mLogger;
  private final XnaSourceCreator mXnaSourceCreator;
  private final AggregateDebugReportApi mAdrApi;
  private final Flags mFlags;
  private final Filter mFilter;

  private enum TriggeringStatus {
    DROPPED,
    ATTRIBUTED
  }

  private enum ProvisionEventReportQuotaResult {
    ADD,
    REPLACE,
    DENIED
  }

  public enum ProcessingResult {
    FAILURE,
    SUCCESS_WITH_PENDING_RECORDS,
    SUCCESS_ALL_RECORDS_PROCESSED
  }

  AttributionJobHandler(
      DatastoreManager datastoreManager,
      DebugReportApi debugReportApi,
      AggregateDebugReportApi adrApi) {
    this(
        datastoreManager,
        FlagsFactory.getFlags(),
        debugReportApi,
        new EventReportWindowCalcDelegate(FlagsFactory.getFlags()),
        new SourceNoiseHandler(FlagsFactory.getFlags()),
        AdServicesLoggerImpl.getInstance(),
        new XnaSourceCreator(FlagsFactory.getFlags()),
        adrApi);
  }

  public AttributionJobHandler(
      DatastoreManager datastoreManager,
      Flags flags,
      DebugReportApi debugReportApi,
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      SourceNoiseHandler sourceNoiseHandler,
      AdServicesLogger logger,
      XnaSourceCreator xnaSourceCreator,
      AggregateDebugReportApi adrApi) {
    mDatastoreManager = datastoreManager;
    mFlags = flags;
    mDebugReportApi = debugReportApi;
    mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    mSourceNoiseHandler = sourceNoiseHandler;
    mLogger = logger;
    mXnaSourceCreator = xnaSourceCreator;
    mAdrApi = adrApi;
    mFilter = new Filter(mFlags);
  }

  /**
   * Perform attribution by finding relevant {@link Source} and generates {@link EventReport}.
   *
   * @return false if there are datastore failures or pending {@link Trigger} left, true otherwise
   */
  public ProcessingResult performPendingAttributions() {
    Optional<List<String>> pendingTriggersOpt =
        mDatastoreManager.runInTransactionWithResult(IMeasurementDao::getPendingTriggerIds);
    if (!pendingTriggersOpt.isPresent()) {
      // Failure during trigger retrieval
      // Reschedule for retry
      return ProcessingResult.FAILURE;
    }
    List<String> pendingTriggers = pendingTriggersOpt.get();
    final int numRecordsToProcess =
        Math.min(pendingTriggers.size(), mFlags.getMeasurementMaxAttributionsPerInvocation());
    for (int i = 0; i < numRecordsToProcess; i++) {
      AttributionStatus attributionStatus = new AttributionStatus();
      boolean success = performAttribution(pendingTriggers.get(i), attributionStatus);
      if (!success) {
        // Failure during trigger attribution
        // Reschedule for retry
        return ProcessingResult.FAILURE;
      }
    }

    // Reschedule if there are unprocessed pending triggers.
    return pendingTriggers.size() > numRecordsToProcess
        ? ProcessingResult.SUCCESS_WITH_PENDING_RECORDS
        : ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED;
  }

  /**
   * Perform attribution for {@code triggerId}.
   *
   * @param triggerId datastore id of the {@link Trigger}
   * @return success
   */
  private boolean performAttribution(String triggerId, AttributionStatus attributionStatus) {
    return mDatastoreManager.runInTransaction(
        measurementDao -> {
          String enrollmentId = null;
          Trigger trigger;
          try {
            trigger = measurementDao.getTrigger(triggerId);
            enrollmentId = trigger.getEnrollmentId();
          } catch (DatastoreException e) {
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                FailureType.TRIGGER_NOT_FOUND,
                enrollmentId);
            throw e;
          }
          attributionStatus.setAttributionDelay(
              System.currentTimeMillis() - trigger.getTriggerTime());

          if (trigger.getStatus() != Trigger.Status.PENDING) {
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                getFailureTypeFromTriggerStatus(trigger.getStatus()),
                enrollmentId);
            return;
          }

          try {
            if (!getTriggerHasAggregatableData(trigger)
                && trigger.parseEventTriggers(mFlags).isEmpty()) {
              ignoreTrigger(trigger, measurementDao);
              setAndLogAttributionStatus(
                  attributionStatus,
                  AttributionResult.NOT_ATTRIBUTED,
                  FailureType.TRIGGER_IGNORED,
                  enrollmentId);
              return;
            }
          } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                .e(
                    e,
                    "JSONException when trigger attribution for"
                        + " trigger with ID: "
                        + trigger.getId());
          }

          Optional<Pair<Source, List<Source>>> sourceOpt =
              selectSourceToAttribute(trigger, measurementDao, attributionStatus);

          // Log competing source that did not win attribution because of delay
          Optional<Source> matchingDelayedSource =
              measurementDao.getNearestDelayedMatchingActiveSource(trigger);
          if (matchingDelayedSource.isPresent()) {
            logDelayedSourceRegistrationStats(matchingDelayedSource.get(), trigger);
          }

          if (sourceOpt.isEmpty()) {
            mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
                trigger, measurementDao, DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE);
            mAdrApi.scheduleTriggerNoMatchingSourceDebugReport(trigger, measurementDao);
            generateNullAggregateReportForNonAttributedTrigger(
                measurementDao, trigger, attributionStatus);
            ignoreTrigger(trigger, measurementDao);
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                FailureType.NO_MATCHING_SOURCE,
                enrollmentId);
            return;
          }

          Source source = sourceOpt.get().first;

          // If the source is a flex source, build trigger specs to populate privacy
          // parameters that may be used in debug and regular reporting.
          if (mFlags.getMeasurementFlexibleEventReportingApiEnabled()
              && source.getTriggerSpecsString() != null
              && !source.getTriggerSpecsString().isEmpty()) {
            try {
              source.buildTriggerSpecs();
            } catch (JSONException e) {
              LoggerFactory.getMeasurementLogger()
                  .e(
                      e,
                      "AttributionJobHandler::performAttribution cannot build" + " trigger specs");
              ignoreTrigger(trigger, measurementDao);
              // TODO(b/361166071): Add a new failure type.
              setAndLogAttributionStatus(
                  attributionStatus,
                  AttributionResult.NOT_ATTRIBUTED,
                  FailureType.UNKNOWN,
                  enrollmentId);
              return;
            }
          }

          List<Source> remainingMatchingSources = sourceOpt.get().second;

          attributionStatus.setSourceType(source.getSourceType());
          attributionStatus.setSurfaceTypeFromSourceAndTrigger(source, trigger);
          attributionStatus.setSourceRegistrant(source.getRegistrant().toString());

          if (source.isInstallAttributed()) {
            attributionStatus.setInstallAttribution(true);
          }

          if (!doTopLevelFiltersMatch(source, trigger, measurementDao)) {
            generateNullAggregateReportForNonAttributedTrigger(
                measurementDao, trigger, attributionStatus);
            ignoreTrigger(trigger, measurementDao);
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                FailureType.TOP_LEVEL_FILTER_MATCH_FAILURE,
                enrollmentId);
            return;
          }

          if (mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()) {
            ignoreCompetingSources(
                measurementDao, remainingMatchingSources, trigger.getEnrollmentId());
          }

          if (shouldAttributionBeBlockedByRateLimits(source, trigger, measurementDao)) {
            generateNullAggregateReportForNonAttributedTrigger(
                measurementDao, trigger, attributionStatus);
            ignoreTrigger(trigger, measurementDao);
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                FailureType.RATE_LIMIT_EXCEEDED,
                enrollmentId);
            return;
          }

          List<DebugReportApi.Type> adrTypes = new ArrayList<>();
          TriggeringStatus aggregateTriggeringStatus =
              maybeGenerateAggregateReport(
                  source, trigger, measurementDao, attributionStatus, adrTypes);

          TriggeringStatus eventTriggeringStatus =
              maybeGenerateEventReport(
                  source, trigger, measurementDao, attributionStatus, adrTypes);

          if (aggregateTriggeringStatus == TriggeringStatus.DROPPED) {
            generateNullAggregateReportForNonAttributedTrigger(
                measurementDao, trigger, attributionStatus);
          }

          boolean isEventTriggeringStatusAttributed =
              eventTriggeringStatus == TriggeringStatus.ATTRIBUTED;
          boolean isAggregateTriggeringStatusAttributed =
              aggregateTriggeringStatus == TriggeringStatus.ATTRIBUTED;
          if (isEventTriggeringStatusAttributed || isAggregateTriggeringStatusAttributed) {
            if (!mFlags.getMeasurementEnableSourceDeactivationAfterFiltering()) {
              ignoreCompetingSources(
                  measurementDao, remainingMatchingSources, trigger.getEnrollmentId());
            }
            attributeTrigger(trigger, measurementDao);
            setAndLogAttributionStatus(
                attributionStatus,
                getAttributionResultFromGeneratedReports(
                    isAggregateTriggeringStatusAttributed, isEventTriggeringStatusAttributed),
                FailureType.UNKNOWN,
                enrollmentId);
          } else {
            ignoreTrigger(trigger, measurementDao);
            // TODO (b/309323690) Consider logging implications for scoped attribution
            // rate limit.
            setAndLogAttributionStatus(
                attributionStatus,
                AttributionResult.NOT_ATTRIBUTED,
                FailureType.NO_REPORTS_GENERATED,
                enrollmentId);
          }
          // Create a combined ADR if multiple errors occurred (event & aggregate
          // report related). Generates a null report if no error has occrred.
          mAdrApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
              source, trigger, adrTypes, measurementDao);
        });
  }

  private boolean getTriggerHasAggregatableData(Trigger trigger) throws JSONException {
    Optional<AggregatableAttributionTrigger> aggregatableAttributionTriggerOpt =
        trigger.getAggregatableAttributionTrigger(mFlags);
    if (aggregatableAttributionTriggerOpt.isEmpty()) {
      return false;
    }

    AggregatableAttributionTrigger aggregatableAttributionTrigger =
        aggregatableAttributionTriggerOpt.get();
    for (AggregatableValuesConfig aggValuesConfig :
        aggregatableAttributionTrigger.getValueConfigs()) {
      if (!aggValuesConfig.getValues().isEmpty()) {
        return true;
      }
    }
    return !aggregatableAttributionTrigger.getTriggerData().isEmpty();
  }

  private boolean shouldAttributionBeBlockedByRateLimits(
      Source source, Trigger trigger, IMeasurementDao measurementDao) throws DatastoreException {
    if (!isReportingOriginWithinPrivacyBounds(source, trigger, measurementDao)) {
      LoggerFactory.getMeasurementLogger()
          .d(
              "Attribution blocked by rate limits. Source ID: %s ; Trigger ID: %s ",
              source.getId(), trigger.getId());
      return true;
    }
    return false;
  }

  private TriggeringStatus maybeGenerateAggregateReport(
      Source source,
      Trigger trigger,
      IMeasurementDao measurementDao,
      AttributionStatus attributionStatus,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException {
    long attributionCount =
        measurementDao.getAttributionsPerRateLimitWindow(
            Attribution.Scope.AGGREGATE, source, trigger);
    if (!hasAttributionQuota(
        attributionCount,
        Attribution.Scope.AGGREGATE,
        source,
        trigger,
        measurementDao,
        adrTypesToGenerate)) {
      LoggerFactory.getMeasurementLogger()
          .d(
              "Attribution blocked by aggregate rate limits. Source ID: %s ; " + "Trigger ID: %s ",
              source.getId(), trigger.getId());
      return TriggeringStatus.DROPPED;
    }

    if (trigger.getTriggerTime() >= source.getAggregatableReportWindow()) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          null,
          measurementDao,
          DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
      return TriggeringStatus.DROPPED;
    }

    int numReportsPerDestination =
        measurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType());

    if (numReportsPerDestination >= mFlags.getMeasurementMaxAggregateReportsPerDestination()) {
      LoggerFactory.getMeasurementLogger()
          .d(
              String.format(
                  Locale.ENGLISH,
                  "Aggregate reports for destination %1$s exceeds system health"
                      + " limit of %2$d.",
                  trigger.getAttributionDestination(),
                  mFlags.getMeasurementMaxAggregateReportsPerDestination()));
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          String.valueOf(mFlags.getMeasurementMaxAggregateReportsPerDestination()),
          measurementDao,
          DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
      return TriggeringStatus.DROPPED;
    }

    if (measurementDao.countNumAggregateReportsPerSource(source.getId(), API)
        >= mFlags.getMeasurementMaxAggregateReportsPerSource()) {
      LoggerFactory.getMeasurementLogger()
          .d(
              String.format(
                  Locale.ENGLISH,
                  "Aggregate reports for source %1$s exceeds system" + " health limit of %2$d.",
                  source.getId(),
                  mFlags.getMeasurementMaxAggregateReportsPerSource()));
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          String.valueOf(mFlags.getMeasurementMaxAggregateReportsPerSource()),
          measurementDao,
          DebugReportApi.Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS);
      return TriggeringStatus.DROPPED;
    }

    try {
      Optional<AggregateDeduplicationKey> aggregateDeduplicationKeyOptional =
          maybeGetAggregateDeduplicationKey(source, trigger);
      if (aggregateDeduplicationKeyOptional.isPresent()
          && source
              .getAggregateReportDedupKeys()
              .contains(aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get())) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            /* limit= */ null,
            measurementDao,
            DebugReportApi.Type.TRIGGER_AGGREGATE_DEDUPLICATED);
        adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_DEDUPLICATED);
        return TriggeringStatus.DROPPED;
      }
      Optional<List<AggregateHistogramContribution>> contributions =
          new AggregatePayloadGenerator(mFlags).generateAttributionReport(source, trigger);
      if (!contributions.isPresent()) {
        if (source.getAggregatableAttributionSource(trigger, mFlags).isPresent()
            && trigger.getAggregatableAttributionTrigger(mFlags).isPresent()) {
          mDebugReportApi.scheduleTriggerDebugReport(
              source,
              trigger,
              /* limit= */ null,
              measurementDao,
              DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
          adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        }
        return TriggeringStatus.DROPPED;
      }
      OptionalInt newAggregateContributions =
          validateAndGetUpdatedAggregateContributions(
              contributions.get(), source, trigger, measurementDao, adrTypesToGenerate);
      if (!newAggregateContributions.isPresent()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Aggregate contributions exceeded bound. Source ID: %s ; " + "Trigger ID: %s ",
                source.getId(), trigger.getId());
        return TriggeringStatus.DROPPED;
      }

      source.setAggregateContributions(newAggregateContributions.getAsInt());
      long scheduledReportTime = trigger.getTriggerTime();
      if (trigger.getTriggerContextId() == null) {
        scheduledReportTime += getAggregateReportDelay();
      }
      Pair<UnsignedLong, UnsignedLong> debugKeyPair =
          new DebugKeyAccessor(measurementDao).getDebugKeys(source, trigger);
      UnsignedLong sourceDebugKey = debugKeyPair.first;
      UnsignedLong triggerDebugKey = debugKeyPair.second;

      int debugReportStatus = AggregateReport.DebugReportStatus.NONE;
      if (sourceDebugKey != null && triggerDebugKey != null) {
        debugReportStatus = AggregateReport.DebugReportStatus.PENDING;
      }
      AggregateReport.Builder aggregateReportBuilder =
          new AggregateReport.Builder()
              .setId(UUID.randomUUID().toString())
              // TODO: b/254855494 unused field, incorrect value; cleanup
              .setPublisher(source.getRegistrant())
              .setAttributionDestination(trigger.getAttributionDestinationBaseUri())
              .setSourceRegistrationTime(getSourceRegistrationTime(source, trigger))
              .setScheduledReportTime(scheduledReportTime)
              .setEnrollmentId(trigger.getEnrollmentId())
              .setDebugCleartextPayload(AggregateReport.generateDebugPayload(contributions.get()))
              .setAggregateAttributionData(
                  new AggregateAttributionData.Builder()
                      .setContributions(contributions.get())
                      .build())
              .setStatus(AggregateReport.Status.PENDING)
              .setDebugReportStatus(debugReportStatus)
              .setApiVersion(getApiVersion())
              .setSourceDebugKey(sourceDebugKey)
              .setTriggerDebugKey(triggerDebugKey)
              .setSourceId(source.getId())
              .setTriggerId(trigger.getId())
              .setRegistrationOrigin(trigger.getRegistrationOrigin())
              .setTriggerContextId(trigger.getTriggerContextId())
              .setTriggerTime(trigger.getTriggerTime())
              .setApi(API);
      if (mFlags.getMeasurementEnableMinReportLifespanForUninstall()) {
        aggregateReportBuilder.setTriggerTime(trigger.getTriggerTime());
      }
      if (trigger.getAggregationCoordinatorOrigin() != null) {
        aggregateReportBuilder.setAggregationCoordinatorOrigin(
            trigger.getAggregationCoordinatorOrigin());
      } else {
        aggregateReportBuilder.setAggregationCoordinatorOrigin(
            Uri.parse(AdServicesConfig.getMeasurementDefaultAggregationCoordinatorOrigin()));
      }

      if (aggregateDeduplicationKeyOptional.isPresent()) {
        aggregateReportBuilder.setDedupKey(
            aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get());
      }

      AggregateReport aggregateReport = aggregateReportBuilder.build();

      if (mFlags.getMeasurementNullAggregateReportEnabled()
          && Trigger.SourceRegistrationTimeConfig.INCLUDE.equals(
              trigger.getAggregatableSourceRegistrationTimeConfig())) {
        generateNullAggregateReportsIncludingSourceRegistrationTime(
            trigger, aggregateReport, measurementDao, attributionStatus);
      }

      finalizeAggregateReportCreation(
          source, aggregateDeduplicationKeyOptional, aggregateReport, measurementDao);
      incrementAggregateReportCountBy(attributionStatus, 1);
      if (aggregateReport.getDebugReportStatus() == AggregateReport.DebugReportStatus.PENDING) {
        incrementAggregateDebugReportCountBy(attributionStatus, 1);
      }
      insertAttribution(
          measurementDao, source, trigger, Attribution.Scope.AGGREGATE, aggregateReport.getId());
      return TriggeringStatus.ATTRIBUTED;
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(
              e,
              "AttributionJobHandler::maybeGenerateAggregateReport JSONException when"
                  + " parse aggregate fields.");
      return TriggeringStatus.DROPPED;
    }
  }

  private String getApiVersion() {
    if (mFlags.getMeasurementEnableFlexibleContributionFiltering()) {
      return "1.0";
    }
    return API_VERSION;
  }

  @Nullable
  private Long getSourceRegistrationTime(Source source, Trigger trigger) {
    if (mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled()
        && Trigger.SourceRegistrationTimeConfig.EXCLUDE.equals(
            trigger.getAggregatableSourceRegistrationTimeConfig())) {
      // A null source registration time implies source registration time should be excluded
      // from the report.
      return null;
    }
    return source.getEventTime();
  }

  private void generateNullAggregateReportForNonAttributedTrigger(
      IMeasurementDao measurementDao, Trigger trigger, AttributionStatus attributionStatus)
      throws DatastoreException {
    try {
      if (!mFlags.getMeasurementNullAggregateReportEnabled()
          || !getTriggerHasAggregatableData(trigger)) {
        return;
      }
      if (Trigger.SourceRegistrationTimeConfig.EXCLUDE.equals(
          trigger.getAggregatableSourceRegistrationTimeConfig())) {
        generateNullAggregateReportExcludingSourceRegistrationTime(
            measurementDao, trigger, attributionStatus);
      } else {
        generateNullAggregateReportsIncludingSourceRegistrationTime(
            trigger, null, measurementDao, attributionStatus);
      }
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(
              e,
              "JSONException when building null aggregate report for"
                  + " trigger with ID: "
                  + trigger.getId());
    }
  }

  @VisibleForTesting
  public double getRandom() {
    return Math.random();
  }

  private void generateNullAggregateReportExcludingSourceRegistrationTime(
      IMeasurementDao measurementDao, Trigger trigger, AttributionStatus attributionStatus)
      throws DatastoreException, JSONException {
    float nullRate =
        trigger.getTriggerContextId() == null
            ? mFlags.getMeasurementNullAggReportRateExclSourceRegistrationTime()
            : 1.0F;
    if (getRandom() < nullRate) {
      AggregateReport nullReport =
          // Although the WICG spec states the trigger time should be used here, we
          // pass null because the source_registration_time is intended for exclusion
          // anyway.
          getNullAggregateReport(trigger, /* sourceTime= */ null);
      measurementDao.insertAggregateReport(nullReport);
      incrementNullAggregateReportCountBy(attributionStatus, 1);
    }
  }

  @VisibleForTesting
  public List<Long> getNullAggregatableReportsDays(long maxSourceExpiry, float nullRate) {
    List<Long> nullAggregatableReportsDays = new ArrayList<>();
    long totalDays = TimeUnit.MILLISECONDS.toDays(maxSourceExpiry);
    for (long dayCount = 0L; dayCount <= totalDays; dayCount += 1L) {
      if (Math.random() < nullRate) {
        nullAggregatableReportsDays.add(dayCount);
      }
    }
    return nullAggregatableReportsDays;
  }

  private void generateNullAggregateReportsIncludingSourceRegistrationTime(
      Trigger trigger,
      @Nullable AggregateReport aggregateReport,
      IMeasurementDao measurementDao,
      AttributionStatus attributionStatus)
      throws DatastoreException, JSONException {
    List<Long> nullAggregatableReportsDays =
        getNullAggregatableReportsDays(
            getRoundedMaxSourceExpiry(),
            mFlags.getMeasurementNullAggReportRateInclSourceRegistrationTime());
    Long roundedAttributedSourceTime =
        aggregateReport == null
            ? null
            : roundDownToDay(aggregateReport.getSourceRegistrationTime());
    for (Long dayCount : nullAggregatableReportsDays) {
      long fakeSourceTime = trigger.getTriggerTime() - TimeUnit.DAYS.toMillis(dayCount);
      if (Objects.equals(roundDownToDay(fakeSourceTime), roundedAttributedSourceTime)) {
        continue;
      }
      AggregateReport nullReport = getNullAggregateReport(trigger, fakeSourceTime);
      measurementDao.insertAggregateReport(nullReport);
      incrementNullAggregateReportCountBy(attributionStatus, 1);
    }
  }

  private long getRoundedMaxSourceExpiry() {
    return roundDownToDay(
        TimeUnit.SECONDS.toMillis(
            mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
  }

  private AggregateReport getNullAggregateReport(Trigger trigger, @Nullable Long sourceTime)
      throws JSONException {
    AggregateReport.Builder nullReportBuilder =
        new AggregateReport.Builder()
            .getNullAggregateReportBuilder(
                trigger, sourceTime, getAggregateReportDelay(), getApiVersion(), API);

    if (mFlags.getMeasurementEnableAggregatableReportPayloadPadding()) {
      AggregateHistogramContribution paddingContribution =
          new AggregateHistogramContribution.Builder().setPaddingContribution().build();
      List<AggregateHistogramContribution> contributions = new ArrayList<>();
      contributions.add(paddingContribution);
      AggregatePayloadGenerator generator = new AggregatePayloadGenerator(mFlags);
      generator.padContributions(contributions, paddingContribution);
      nullReportBuilder.setDebugCleartextPayload(
          AggregateReport.generateDebugPayload(contributions));
    }

    return nullReportBuilder.build();
  }

  private Optional<Pair<Source, List<Source>>> selectSourceToAttribute(
      Trigger trigger, IMeasurementDao measurementDao, AttributionStatus attributionStatus)
      throws DatastoreException {
    List<Source> matchingSources;
    if (!mFlags.getMeasurementEnableXNA() || trigger.getAttributionConfig() == null) {
      matchingSources = measurementDao.getMatchingActiveSources(trigger);
    } else {
      // XNA attribution is possible
      Set<String> enrollmentIds = extractEnrollmentIds(trigger.getAttributionConfig());
      List<Source> allSources =
          measurementDao.fetchTriggerMatchingSourcesForXna(trigger, enrollmentIds);
      List<Source> triggerEnrollmentMatchingSources = new ArrayList<>();
      List<Source> otherEnrollmentBasedSources = new ArrayList<>();
      for (Source source : allSources) {
        if (Objects.equals(source.getEnrollmentId(), trigger.getEnrollmentId())) {
          triggerEnrollmentMatchingSources.add(source);
        } else {
          otherEnrollmentBasedSources.add(source);
        }
      }
      List<Source> derivedSources =
          mXnaSourceCreator.generateDerivedSources(trigger, otherEnrollmentBasedSources);
      matchingSources = new ArrayList<>();
      matchingSources.addAll(triggerEnrollmentMatchingSources);
      matchingSources.addAll(derivedSources);
    }

    if (matchingSources.isEmpty()) {
      return Optional.empty();
    }

    // Sort based on isInstallAttributed, Priority and Event Time.
    // Is a valid install-attributed source.
    Function<Source, Boolean> installAttributionComparator =
        (Source source) ->
            source.isInstallAttributed() && isWithinInstallCooldownWindow(source, trigger);
    Comparator<Source> comparator =
        Comparator.comparing(installAttributionComparator, Comparator.reverseOrder());
    if (mFlags.getMeasurementEnableAttributionScope()) {
      List<String> triggerAttributionScopes;
      try {
        triggerAttributionScopes = trigger.getAttributionScopes();
      } catch (JSONException e) {
        throw new IllegalArgumentException("Failed to parse trigger attribution scopes.");
      }
      if (triggerAttributionScopes != null && !triggerAttributionScopes.isEmpty()) {
        Set<String> triggerAttributionScopesSet = new HashSet<>(triggerAttributionScopes);
        // Filter based on whether any of the trigger attribution scope is in the source
        // attribution scopes.
        Function<Source, Boolean> attributionScopeFilter =
            (Source source) -> {
              if (source.getAttributionScopes() == null) {
                return false;
              }
              Set<String> sourceAttributionScopes = new HashSet<>(source.getAttributionScopes());
              sourceAttributionScopes.retainAll(triggerAttributionScopesSet);
              return !sourceAttributionScopes.isEmpty();
            };
        if (!matchingSources.stream().anyMatch(attributionScopeFilter::apply)) {
          return Optional.empty();
        }
        comparator =
            Comparator.comparing(attributionScopeFilter, Comparator.reverseOrder())
                .thenComparing(installAttributionComparator, Comparator.reverseOrder());
      }
    }
    comparator =
        comparator
            .thenComparing(Source::getPriority, Comparator.reverseOrder())
            .thenComparing(Source::getEventTime, Comparator.reverseOrder());
    matchingSources.sort(comparator);

    Source selectedSource = matchingSources.remove(0);

    if (selectedSource.getParentId() != null) {
      attributionStatus.setSourceDerived(true);
    }

    return Optional.of(Pair.create(selectedSource, matchingSources));
  }

  private Set<String> extractEnrollmentIds(String attributionConfigsString) {
    Set<String> enrollmentIds = new HashSet<>();
    try {
      JSONArray attributionConfigsJsonArray = new JSONArray(attributionConfigsString);
      for (int i = 0; i < attributionConfigsJsonArray.length(); i++) {
        JSONObject attributionConfigJson = attributionConfigsJsonArray.getJSONObject(i);
        // It can't be null, has already been validated at fetcher
        enrollmentIds.add(
            attributionConfigJson.getString(
                AttributionConfig.AttributionConfigContract.SOURCE_NETWORK));
      }
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger().d(e, "Failed to parse attribution configs.");
    }
    return enrollmentIds;
  }

  private Optional<AggregateDeduplicationKey> maybeGetAggregateDeduplicationKey(
      Source source, Trigger trigger) {
    try {
      Optional<AggregateDeduplicationKey> dedupKey;
      Optional<AggregatableAttributionSource> optionalAggregateAttributionSource =
          source.getAggregatableAttributionSource(trigger, mFlags);
      Optional<AggregatableAttributionTrigger> optionalAggregateAttributionTrigger =
          trigger.getAggregatableAttributionTrigger(mFlags);
      if (!optionalAggregateAttributionSource.isPresent()
          || !optionalAggregateAttributionTrigger.isPresent()) {
        return Optional.empty();
      }
      AggregatableAttributionSource aggregateAttributionSource =
          optionalAggregateAttributionSource.get();
      AggregatableAttributionTrigger aggregateAttributionTrigger =
          optionalAggregateAttributionTrigger.get();
      dedupKey =
          aggregateAttributionTrigger.maybeExtractDedupKey(
              aggregateAttributionSource.getFilterMap(), mFlags);
      return dedupKey;
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(
              e,
              "AttributionJobHandler::maybeGetAggregateDeduplicationKey JSONException"
                  + " when parse aggregate dedup key fields in"
                  + " AttributionJobHandler.");
      return Optional.empty();
    }
  }

  private void ignoreCompetingSources(
      IMeasurementDao measurementDao,
      List<Source> remainingMatchingSources,
      String triggerEnrollmentId)
      throws DatastoreException {
    if (!remainingMatchingSources.isEmpty()) {
      List<String> ignoredOriginalSourceIds = new ArrayList<>();
      for (Source source : remainingMatchingSources) {
        source.setStatus(Source.Status.IGNORED);

        if (source.getParentId() == null) {
          // Original source
          ignoredOriginalSourceIds.add(source.getId());
        } else {
          // Derived source (XNA)
          measurementDao.insertIgnoredSourceForEnrollment(
              source.getParentId(), triggerEnrollmentId);
        }
      }
      measurementDao.updateSourceStatus(ignoredOriginalSourceIds, Source.Status.IGNORED);
    }
  }

  private TriggeringStatus maybeGenerateEventReport(
      Source source,
      Trigger trigger,
      IMeasurementDao measurementDao,
      AttributionStatus attributionStatus,
      List<DebugReportApi.Type> adrTypes)
      throws DatastoreException {
    if (source.getParentId() != null) {
      LoggerFactory.getMeasurementLogger()
          .d("Event report generation skipped because it's a derived source.");
      return TriggeringStatus.DROPPED;
    }

    // TODO: Handle attribution rate limit consideration for non-truthful cases.
    if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, null, measurementDao, DebugReportApi.Type.TRIGGER_EVENT_NOISE);
      adrTypes.add(DebugReportApi.Type.TRIGGER_EVENT_NOISE);
      return TriggeringStatus.DROPPED;
    }

    Optional<EventTrigger> matchingEventTrigger =
        findFirstMatchingEventTrigger(source, trigger, measurementDao, adrTypes);
    if (!matchingEventTrigger.isPresent()) {
      return TriggeringStatus.DROPPED;
    }

    EventTrigger eventTrigger = matchingEventTrigger.get();
    // Check if deduplication key clashes with existing reports.
    if (eventTrigger.getDedupKey() != null) {
      boolean alreadyAttributed;
      if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
        try {
          source.buildAttributedTriggers();
          alreadyAttributed = hasDeduplicationKey(source, eventTrigger.getDedupKey());
        } catch (JSONException e) {
          LoggerFactory.getMeasurementLogger()
              .e(e, "maybeGenerateEventReport: failed to build attributed triggers.");
          return TriggeringStatus.DROPPED;
        }
      } else {
        alreadyAttributed = source.getEventReportDedupKeys().contains(eventTrigger.getDedupKey());
      }
      if (alreadyAttributed) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            /* limit= */ null,
            measurementDao,
            DebugReportApi.Type.TRIGGER_EVENT_DEDUPLICATED);
        adrTypes.add(DebugReportApi.Type.TRIGGER_EVENT_DEDUPLICATED);
        return TriggeringStatus.DROPPED;
      }
    }

    Optional<UnsignedLong> maybeEffectiveTriggerData =
        getMatchingEffectiveTriggerData(eventTrigger, source);

    if (maybeEffectiveTriggerData.isEmpty()) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          null,
          measurementDao,
          DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
      adrTypes.add(DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA);
      return TriggeringStatus.DROPPED;
    }

    if (!isTriggerFallsWithinWindow(
        source, trigger, maybeEffectiveTriggerData.get(), measurementDao, adrTypes)) {
      return TriggeringStatus.DROPPED;
    }

    Pair<List<Uri>, List<Uri>> destinations = measurementDao.getSourceDestinations(source.getId());
    source.setAppDestinations(destinations.first);
    source.setWebDestinations(destinations.second);

    Pair<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(measurementDao).getDebugKeys(source, trigger);

    if (source.getTriggerSpecs() == null) {
      EventReport newEventReport =
          new EventReport.Builder()
              .populateFromSourceAndTrigger(
                  source,
                  trigger,
                  maybeEffectiveTriggerData.get(),
                  eventTrigger,
                  debugKeyPair,
                  mEventReportWindowCalcDelegate,
                  mSourceNoiseHandler,
                  getEventReportDestinations(source, trigger.getDestinationType()))
              .build();
      ProvisionEventReportQuotaResult provisionEventReportQuota =
          provisionEventReportQuota(source, trigger, newEventReport, measurementDao, adrTypes);
      if (provisionEventReportQuota == ProvisionEventReportQuotaResult.DENIED) {
        return TriggeringStatus.DROPPED;
      }
      if (provisionEventReportQuota == ProvisionEventReportQuotaResult.ADD) {
        long attributionCount =
            measurementDao.getAttributionsPerRateLimitWindow(
                Attribution.Scope.EVENT, source, trigger);
        if (!hasAttributionQuota(
            attributionCount, Attribution.Scope.EVENT, source, trigger, measurementDao, adrTypes)) {
          LoggerFactory.getMeasurementLogger()
              .d(
                  "Attribution blocked by event rate limits. Source ID: %s ; " + "Trigger ID: %s ",
                  source.getId(), trigger.getId());
          return TriggeringStatus.DROPPED;
        }
      }
      int numReports =
          measurementDao.getNumEventReportsPerDestination(
              trigger.getAttributionDestination(), trigger.getDestinationType());
      if (numReports >= mFlags.getMeasurementMaxEventReportsPerDestination()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                String.format(
                    Locale.ENGLISH,
                    "Event reports for destination %1$s exceeds system health" + " limit of %2$d.",
                    trigger.getAttributionDestination(),
                    mFlags.getMeasurementMaxEventReportsPerDestination()));
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            String.valueOf(mFlags.getMeasurementMaxEventReportsPerDestination()),
            measurementDao,
            DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        adrTypes.add(DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT);
        return TriggeringStatus.DROPPED;
      }
      if (mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
        finalizeEventReportCreation(source, eventTrigger, trigger, newEventReport, measurementDao);
      } else {
        finalizeEventReportCreation(source, eventTrigger, newEventReport, measurementDao);
      }
      incrementEventReportCountBy(attributionStatus, 1);
      if (newEventReport.getDebugReportStatus() == EventReport.DebugReportStatus.PENDING) {
        incrementEventDebugReportCountBy(attributionStatus, 1);
      }
      insertAttribution(
          measurementDao, source, trigger, Attribution.Scope.EVENT, newEventReport.getId());
      // The source is using flexible event API
    } else if (!generateFlexEventReports(
        source,
        trigger,
        eventTrigger,
        maybeEffectiveTriggerData.get(),
        debugKeyPair,
        measurementDao)) {
      return TriggeringStatus.DROPPED;
    }
    return TriggeringStatus.ATTRIBUTED;
  }

  private long restoreTriggerContributionsAndProvisionFlexEventReportQuota(
      Source source,
      Trigger trigger,
      long attributionCount,
      Map<UnsignedLong, Integer> triggerDataToBucketIndexMap,
      IMeasurementDao measurementDao)
      throws DatastoreException {

    List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);

    List<EventReport> reportsToDelete = new ArrayList<>();

    source
        .getTriggerSpecs()
        .prepareFlexAttribution(
            sourceEventReports,
            trigger.getTriggerTime(),
            reportsToDelete,
            triggerDataToBucketIndexMap);

    int numEarlierScheduledReports = sourceEventReports.size() - reportsToDelete.size();
    int maxEventReports = source.getTriggerSpecs().getMaxReports();

    // Completed reports already covered the allotted quota.
    if (numEarlierScheduledReports == maxEventReports) {
      return 0;
    }

    // Delete pending reports and associated attribution rate-limit records. We will recreate an
    // updated sequence below.
    measurementDao.deleteFlexEventReportsAndAttributions(reportsToDelete);

    // Each report deleted has an associated attribution rate-limit record deleted
    long remainingAttributions =
        (long) mFlags.getMeasurementMaxEventAttributionPerRateLimitWindow()
            - attributionCount
            + reportsToDelete.size();

    // Return the smaller of remaining attributions per rate limit or remaining report quota.
    return Math.min(remainingAttributions, (long) (maxEventReports - numEarlierScheduledReports));
  }

  private boolean generateFlexEventReports(
      Source source,
      Trigger trigger,
      EventTrigger eventTrigger,
      UnsignedLong effectiveTriggerData,
      Pair<UnsignedLong, UnsignedLong> debugKeyPair,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    if (source.getTriggerDataCardinality() == 0) {
      return false;
    }

    TriggerSpecs triggerSpecs = source.getTriggerSpecs();

    // Store the current bucket index for each trigger data
    Map<UnsignedLong, Integer> triggerDataToBucketIndexMap = new HashMap<>();

    long attributionCount =
        measurementDao.getAttributionsPerRateLimitWindow(Attribution.Scope.EVENT, source, trigger);

    long remainingReportQuota =
        restoreTriggerContributionsAndProvisionFlexEventReportQuota(
            source, trigger, attributionCount, triggerDataToBucketIndexMap, measurementDao);

    if (remainingReportQuota == 0L) {
      return false;
    }

    List<AttributedTrigger> attributedTriggers = source.getAttributedTriggers();

    long triggerValue =
        triggerSpecs.getSummaryOperatorType(effectiveTriggerData)
                == TriggerSpec.SummaryOperatorType.COUNT
            ? 1L
            : eventTrigger.getTriggerValue();

    attributedTriggers.add(
        new AttributedTrigger(
            trigger.getId(),
            eventTrigger.getTriggerPriority(),
            // effectiveTriggerData is used in reporting and matching trigger spec; the original
            // trigger datastore record contains the provided trigger data.
            effectiveTriggerData,
            triggerValue,
            trigger.getTriggerTime(),
            eventTrigger.getDedupKey(),
            debugKeyPair.second,
            debugKeyPair.first != null));

    attributedTriggers.sort(
        Comparator.comparingLong(AttributedTrigger::getPriority)
            .reversed()
            .thenComparing(AttributedTrigger::getTriggerTime));

    // Store for each trigger data any amount already covered for the current bucket.
    Map<UnsignedLong, Long> triggerDataToBucketAmountMap = new HashMap<>();
    Map<UnsignedLong, List<AttributedTrigger>> triggerDataToContributingTriggersMap =
        new HashMap<>();

    for (AttributedTrigger attributedTrigger : attributedTriggers) {
      // Flex API already inserts the attributed trigger and does not need an explicit action
      // for that.
      if (attributedTrigger.getDedupKey() != null
          && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
        source.getEventReportDedupKeys().add(attributedTrigger.getDedupKey());
        measurementDao.updateSourceEventReportDedupKeys(source);
      }

      remainingReportQuota -=
          updateFlexAttributionStateAndGetNumReports(
              source,
              trigger,
              attributedTrigger,
              remainingReportQuota,
              triggerDataToBucketIndexMap,
              triggerDataToBucketAmountMap,
              triggerDataToContributingTriggersMap,
              measurementDao);

      if (remainingReportQuota == 0) {
        break;
      }
    }

    measurementDao.updateSourceAttributedTriggers(
        source.getId(), source.attributedTriggersToJsonFlexApi());

    // TODO (b/307786346): represent actual report count.
    return true;
  }

  private long updateFlexAttributionStateAndGetNumReports(
      Source source,
      Trigger trigger,
      AttributedTrigger attributedTrigger,
      long remainingReportQuota,
      Map<UnsignedLong, Integer> triggerDataToBucketIndexMap,
      Map<UnsignedLong, Long> triggerDataToBucketAmountMap,
      Map<UnsignedLong, List<AttributedTrigger>> triggerDataToContributingTriggersMap,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    TriggerSpecs triggerSpecs = source.getTriggerSpecs();
    UnsignedLong triggerData = attributedTrigger.getTriggerData();

    triggerDataToBucketIndexMap.putIfAbsent(triggerData, 0);

    int bucketIndex = triggerDataToBucketIndexMap.get(triggerData);
    List<Long> buckets = triggerSpecs.getSummaryBucketsForTriggerData(triggerData);

    // Once we've generated a report for the last bucket, subsequent triggers cannot
    // generate more reports.
    if (bucketIndex == buckets.size()) {
      return 0;
    }

    triggerDataToBucketAmountMap.putIfAbsent(triggerData, 0L);
    triggerDataToContributingTriggersMap.putIfAbsent(triggerData, new ArrayList<>());

    List<AttributedTrigger> contributingTriggers =
        triggerDataToContributingTriggersMap.get(triggerData);
    if (attributedTrigger.remainingValue() > 0L) {
      contributingTriggers.add(attributedTrigger);
    }

    long prevBucket = bucketIndex == 0 ? 0L : buckets.get(bucketIndex - 1);
    long numReportsCreated = 0L;

    for (int i = bucketIndex; i < buckets.size(); i++) {
      long bucket = buckets.get(i);
      long bucketSize = bucket - prevBucket;
      long bucketAmount = triggerDataToBucketAmountMap.get(triggerData);

      if (attributedTrigger.remainingValue() >= bucketSize - bucketAmount) {
        finalizeEventReportAndAttributionCreationForFlex(
            source,
            trigger,
            attributedTrigger,
            contributingTriggers,
            TriggerSpecs.getSummaryBucketFromIndex(i, buckets),
            measurementDao);
        numReportsCreated += 1L;

        if (remainingReportQuota - numReportsCreated == 0L) {
          return numReportsCreated;
        }

        attributedTrigger.addContribution(bucketSize - bucketAmount);
        triggerDataToBucketIndexMap.put(triggerData, i + 1);
        triggerDataToBucketAmountMap.put(triggerData, 0L);
        contributingTriggers.clear();
        if (attributedTrigger.remainingValue() > 0L) {
          contributingTriggers.add(attributedTrigger);
        }
      } else {
        triggerDataToBucketIndexMap.put(triggerData, i);
        long diff = attributedTrigger.remainingValue();
        triggerDataToBucketAmountMap.merge(triggerData, diff, (oldValue, value) -> oldValue + diff);
        attributedTrigger.addContribution(diff);
        break;
      }
      prevBucket = bucket;
    }

    return numReportsCreated;
  }

  private List<Uri> getEventReportDestinations(@NonNull Source source, int destinationType) {
    ImmutableList.Builder<Uri> destinations = new ImmutableList.Builder<>();
    if (mFlags.getMeasurementEnableCoarseEventReportDestinations()
        && source.hasCoarseEventReportDestinations()) {
      Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
      Optional.ofNullable(source.getWebDestinations()).ifPresent(destinations::addAll);
    } else {
      destinations.addAll(source.getAttributionDestinations(destinationType));
    }
    return destinations.build();
  }

  private ProvisionEventReportQuotaResult provisionEventReportQuota(
      Source source,
      Trigger trigger,
      EventReport newEventReport,
      IMeasurementDao measurementDao,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException {
    List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);

    if (isWithinReportLimit(source, sourceEventReports.size(), trigger.getDestinationType())) {
      return ProvisionEventReportQuotaResult.ADD;
    }

    List<EventReport> relevantEventReports =
        sourceEventReports.stream()
            .filter(
                (r) ->
                    r.getStatus() == EventReport.Status.PENDING
                        && r.getReportTime() == newEventReport.getReportTime())
            .sorted(
                Comparator.comparingLong(EventReport::getTriggerPriority)
                    .thenComparing(EventReport::getTriggerTime, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    if (relevantEventReports.isEmpty()) {
      UnsignedLong triggerData = newEventReport.getTriggerData();
      mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
          source,
          trigger,
          triggerData,
          measurementDao,
          DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
      return ProvisionEventReportQuotaResult.DENIED;
    }

    EventReport lowestPriorityEventReport = relevantEventReports.get(0);
    if (lowestPriorityEventReport.getTriggerPriority() >= newEventReport.getTriggerPriority()) {
      UnsignedLong triggerData = newEventReport.getTriggerData();
      mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
          source,
          trigger,
          triggerData,
          measurementDao,
          DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY);
      return ProvisionEventReportQuotaResult.DENIED;
    }

    if (lowestPriorityEventReport.getTriggerDedupKey() != null
        && !mFlags.getMeasurementEnableAraDeduplicationAlignmentV1()) {
      source.getEventReportDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
    }

    measurementDao.deleteEventReportAndAttribution(lowestPriorityEventReport);
    return ProvisionEventReportQuotaResult.REPLACE;
  }

  private static void finalizeEventReportCreation(
      Source source,
      EventTrigger eventTrigger,
      EventReport eventReport,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    if (eventTrigger.getDedupKey() != null) {
      source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
    }
    measurementDao.updateSourceEventReportDedupKeys(source);

    measurementDao.insertEventReport(eventReport);
  }

  private static void finalizeEventReportCreation(
      Source source,
      EventTrigger eventTrigger,
      Trigger trigger,
      EventReport eventReport,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    if (eventTrigger.getDedupKey() != null) {
      source
          .getAttributedTriggers()
          .add(
              new AttributedTrigger(
                  trigger.getId(), eventTrigger.getTriggerData(), eventTrigger.getDedupKey()));
      measurementDao.updateSourceAttributedTriggers(
          source.getId(), source.attributedTriggersToJson());
    }

    measurementDao.insertEventReport(eventReport);
  }

  private void finalizeEventReportAndAttributionCreationForFlex(
      Source source,
      Trigger trigger,
      AttributedTrigger attributedTrigger,
      List<AttributedTrigger> contributingTriggers,
      Pair<Long, Long> triggerSummaryBucket,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    long reportTime =
        mEventReportWindowCalcDelegate.getFlexEventReportingTime(
            source.getTriggerSpecs(),
            source.getEventTime(),
            // We can make an assertion that any report generated for any trigger data can only
            // be associated with the next report window after trigger time that's configured
            // for that trigger data: (1) if report time were to be before trigger time, that
            // would mean attributed triggers all with an earlier time filled a bucket during
            // the current iteration, which is disputed by counting all of those buckets before,
            // and (2) if report time is to be after trigger time, it necessarily will be the
            // next report window configured for the current trigger data, regardless of how
            // much earlier were the actual attributed triggers counted towards the bucket.
            trigger.getTriggerTime(),
            attributedTrigger.getTriggerData());
    Pair<UnsignedLong, List<UnsignedLong>> debugKeys =
        getDebugKeysForFlex(contributingTriggers, source);
    EventReport eventReport =
        new EventReport.Builder()
            .getForFlex(
                source,
                trigger,
                attributedTrigger,
                reportTime,
                triggerSummaryBucket,
                debugKeys.first,
                debugKeys.second,
                mSourceNoiseHandler.getRandomizedTriggerRate(source),
                getEventReportDestinations(source, trigger.getDestinationType()))
            .build();
    measurementDao.insertEventReport(eventReport);
    insertAttribution(
        measurementDao, source, trigger, Attribution.Scope.EVENT, eventReport.getId());
  }

  private static void finalizeAggregateReportCreation(
      Source source,
      Optional<AggregateDeduplicationKey> aggregateDeduplicationKeyOptional,
      AggregateReport aggregateReport,
      IMeasurementDao measurementDao)
      throws DatastoreException {
    if (aggregateDeduplicationKeyOptional.isPresent()) {
      source
          .getAggregateReportDedupKeys()
          .add(aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get());
    }

    if (source.getParentId() == null) {
      // Only update aggregate contributions for an original source, not for a derived
      // source
      measurementDao.updateSourceAggregateContributions(source);
      measurementDao.updateSourceAggregateReportDedupKeys(source);
    }
    measurementDao.insertAggregateReport(aggregateReport);
  }

  private static void ignoreTrigger(Trigger trigger, IMeasurementDao measurementDao)
      throws DatastoreException {
    trigger.setStatus(Trigger.Status.IGNORED);
    measurementDao.updateTriggerStatus(
        Collections.singletonList(trigger.getId()), Trigger.Status.IGNORED);
  }

  private static void attributeTrigger(Trigger trigger, IMeasurementDao measurementDao)
      throws DatastoreException {
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    measurementDao.updateTriggerStatus(
        Collections.singletonList(trigger.getId()), Trigger.Status.ATTRIBUTED);
  }

  private static void insertAttribution(
      Source source, Trigger trigger, IMeasurementDao measurementDao) throws DatastoreException {
    measurementDao.insertAttribution(createAttributionBuilder(source, trigger).build());
  }

  private static void insertAttribution(
      IMeasurementDao measurementDao,
      Source source,
      Trigger trigger,
      @Attribution.Scope int scope,
      String reportId)
      throws DatastoreException {
    measurementDao.insertAttribution(
        createAttributionBuilder(source, trigger).setScope(scope).setReportId(reportId).build());
  }

  private boolean hasAttributionQuota(
      long attributionCount,
      @Attribution.Scope int scope,
      Source source,
      Trigger trigger,
      IMeasurementDao measurementDao,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException {
    int limit =
        scope == Attribution.Scope.EVENT
            ? mFlags.getMeasurementMaxEventAttributionPerRateLimitWindow()
            : mFlags.getMeasurementMaxAggregateAttributionPerRateLimitWindow();
    boolean isWithinLimit = attributionCount < limit;
    if (!isWithinLimit) {
      DebugReportApi.Type reportType =
          scope == Attribution.Scope.EVENT
              ? DebugReportApi.Type.TRIGGER_EVENT_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT
              : DebugReportApi.Type.TRIGGER_AGGREGATE_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT;
      reportType =
          mFlags.getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit()
              ? reportType
              : DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT;
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, String.valueOf(limit), measurementDao, reportType);
      adrTypesToGenerate.add(reportType);
    }
    return isWithinLimit;
  }

  private boolean isWithinReportLimit(
      Source source, int existingReportCount, @EventSurfaceType int destinationType) {
    return mEventReportWindowCalcDelegate.getMaxReportCount(source, destinationType)
        > existingReportCount;
  }

  private static boolean isWithinInstallCooldownWindow(Source source, Trigger trigger) {
    return trigger.getTriggerTime() < (source.getEventTime() + source.getInstallCooldownWindow());
  }

  /**
   * The logic works as following - 1. If source OR trigger filters are empty, we call it a match
   * since there is no restriction. 2. If source and trigger filters have no common keys, it's a
   * match. 3. All common keys between source and trigger filters should have intersection between
   * their list of values.
   *
   * @return true for a match, false otherwise
   */
  private boolean doTopLevelFiltersMatch(
      @NonNull Source source, @NonNull Trigger trigger, IMeasurementDao measurementDao)
      throws DatastoreException {
    try {
      FilterMap sourceFilters = source.getFilterData(trigger, mFlags);
      List<FilterMap> triggerFilterSet = extractFilterSet(trigger.getFilters());
      List<FilterMap> triggerNotFilterSet = extractFilterSet(trigger.getNotFilters());
      boolean isFilterMatch =
          mFilter.isFilterMatch(sourceFilters, triggerFilterSet, true)
              && mFilter.isFilterMatch(sourceFilters, triggerNotFilterSet, false);
      if (!isFilterMatch
          && !sourceFilters.isEmpty(mFlags)
          && (!triggerFilterSet.isEmpty() || !triggerNotFilterSet.isEmpty())) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            /* limit= */ null,
            measurementDao,
            DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA);
        mAdrApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
            source,
            trigger,
            Collections.singletonList(DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA),
            measurementDao);
      }
      return isFilterMatch;
    } catch (JSONException e) {
      // If JSON is malformed, we shall consider as not matched.
      LoggerFactory.getMeasurementLogger()
          .e(e, "AttributionJobHandler::doTopLevelFiltersMatch: JSON parse failed.");
      return false;
    }
  }

  private Optional<EventTrigger> findFirstMatchingEventTrigger(
      Source source,
      Trigger trigger,
      IMeasurementDao measurementDao,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException {
    try {
      FilterMap sourceFiltersData = source.getFilterData(trigger, mFlags);
      List<EventTrigger> eventTriggers = trigger.parseEventTriggers(mFlags);
      Optional<EventTrigger> matchingEventTrigger =
          eventTriggers.stream()
              .filter(eventTrigger -> doEventLevelFiltersMatch(sourceFiltersData, eventTrigger))
              .findFirst();
      // trigger-no-matching-configurations verbose debug report is generated when event
      // trigger "filters/not_filters" field doesn't match source "filter_data" field. It
      // won't be generated when trigger doesn't have event_trigger_data field.
      if (!matchingEventTrigger.isPresent() && !eventTriggers.isEmpty()) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            /* limit= */ null,
            measurementDao,
            DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
        adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
      }
      return matchingEventTrigger;
    } catch (JSONException e) {
      // If JSON is malformed, we shall consider as not matched.
      LoggerFactory.getMeasurementLogger()
          .e(
              e,
              "AttributionJobHandler::findFirstMatchingEventTrigger: Malformed JSON" + " string.");
      return Optional.empty();
    }
  }

  private boolean doEventLevelFiltersMatch(FilterMap sourceFiltersData, EventTrigger eventTrigger) {
    if (eventTrigger.getFilterSet().isPresent()
        && !mFilter.isFilterMatch(sourceFiltersData, eventTrigger.getFilterSet().get(), true)) {
      return false;
    }

    if (eventTrigger.getNotFilterSet().isPresent()
        && !mFilter.isFilterMatch(sourceFiltersData, eventTrigger.getNotFilterSet().get(), false)) {
      return false;
    }

    return true;
  }

  private List<FilterMap> extractFilterSet(String str) throws JSONException {
    return mFlags.getMeasurementEnableLookbackWindowFilter()
        ? extractFilterSetV2(str)
        : extractFilterSetV1(str);
  }

  private List<FilterMap> extractFilterSetV1(String str) throws JSONException {
    String json = (str == null || str.isEmpty()) ? "[]" : str;
    List<FilterMap> filterSet = new ArrayList<>();
    JSONArray filters = new JSONArray(json);
    for (int i = 0; i < filters.length(); i++) {
      FilterMap filterMap =
          new FilterMap.Builder().buildFilterData(filters.getJSONObject(i)).build();
      filterSet.add(filterMap);
    }
    return filterSet;
  }

  private List<FilterMap> extractFilterSetV2(String str) throws JSONException {
    String json = (str == null || str.isEmpty()) ? "[]" : str;
    JSONArray filters = new JSONArray(json);
    return mFilter.deserializeFilterSet(filters);
  }

  private OptionalInt validateAndGetUpdatedAggregateContributions(
      List<AggregateHistogramContribution> contributions,
      Source source,
      Trigger trigger,
      IMeasurementDao measurementDao,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException, JSONException {
    int newAggregateContributions = source.getAggregateContributions();
    int adrAllocatedBudget =
        Optional.ofNullable(source.getAggregateDebugReportingObject())
            .map(AggregateDebugReporting::getBudget)
            .orElse(0);
    for (AggregateHistogramContribution contribution : contributions) {
      try {
        newAggregateContributions =
            Math.addExact(newAggregateContributions, contribution.getValue());
        if (newAggregateContributions + adrAllocatedBudget
            > PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
          // When histogram value is >= 65536 (aggregatable_budget_per_source),
          // generate verbose debug report, record the actual histogram value.
          mDebugReportApi.scheduleTriggerDebugReport(
              source,
              trigger,
              String.valueOf(PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE),
              measurementDao,
              DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
          adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
          return OptionalInt.empty();
        }
      } catch (ArithmeticException e) {
        LoggerFactory.getMeasurementLogger()
            .e(
                e,
                "AttributionJobHandler::validateAndGetUpdatedAggregateContributions"
                    + " Error adding aggregate contribution values.");
        return OptionalInt.empty();
      }
    }
    return OptionalInt.of(newAggregateContributions);
  }

  private boolean isReportingOriginWithinPrivacyBounds(
      Source source, Trigger trigger, IMeasurementDao measurementDao) throws DatastoreException {
    Optional<Pair<Uri, Uri>> publisherAndDestination =
        getPublisherAndDestinationTopPrivateDomains(source, trigger);
    if (publisherAndDestination.isPresent()) {
      Integer count =
          measurementDao.countDistinctReportingOriginsPerPublisherXDestInAttribution(
              publisherAndDestination.get().first,
              publisherAndDestination.get().second,
              trigger.getRegistrationOrigin(),
              trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
              trigger.getTriggerTime());
      if (count >= mFlags.getMeasurementMaxDistinctReportingOriginsInAttribution()) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            String.valueOf(mFlags.getMeasurementMaxDistinctReportingOriginsInAttribution()),
            measurementDao,
            DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
        mAdrApi.scheduleTriggerAttributionErrorWithSourceDebugReport(
            source,
            trigger,
            Collections.singletonList(DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT),
            measurementDao);
      }

      return count < mFlags.getMeasurementMaxDistinctReportingOriginsInAttribution();
    } else {
      LoggerFactory.getMeasurementLogger()
          .d(
              "isReportingOriginWithinPrivacyBounds:"
                  + " getPublisherAndDestinationTopPrivateDomains failed. %s %s",
              source.getPublisher(), trigger.getAttributionDestination());
      return true;
    }
  }

  private Optional<UnsignedLong> getMatchingEffectiveTriggerData(
      EventTrigger eventTrigger, Source source) {
    UnsignedLong triggerData = eventTrigger.getTriggerData();

    // Flex source
    if (source.getTriggerSpecs() != null) {
      if (mFlags.getMeasurementEnableTriggerDataMatching()
          && source.getTriggerDataMatching() == Source.TriggerDataMatching.MODULUS) {
        if (source.getTriggerDataCardinality() == 0) {
          return Optional.empty();
        }
        // Modify trigger data value mod total trigger spec cardinality.
        triggerData = triggerData.mod(source.getTriggerDataCardinality());
      }
      if (!source.getTriggerSpecs().containsTriggerData(triggerData)) {
        return Optional.empty();
      }
      return Optional.of(triggerData);
    }

    // V1 source
    if (mFlags.getMeasurementEnableTriggerDataMatching()
        && source.getTriggerDataMatching() == Source.TriggerDataMatching.EXACT) {
      if (source.getTriggerData() != null) {
        return source.getTriggerData().contains(triggerData)
            ? Optional.of(triggerData)
            : Optional.empty();
      } else {
        UnsignedLong triggerDataCardinalityBound =
            new UnsignedLong(((long) source.getTriggerDataCardinality()) - 1L);
        if (triggerData.compareTo(triggerDataCardinalityBound) > 0) {
          return Optional.empty();
        }
      }
    }

    // V1 source with trigger data matching type modulus (default)
    if (source.getTriggerDataCardinality() == 0) {
      return Optional.empty();
    }

    return Optional.of(triggerData.mod(source.getTriggerDataCardinality()));
  }

  private static Optional<Pair<Uri, Uri>> getPublisherAndDestinationTopPrivateDomains(
      Source source, Trigger trigger) {
    Uri attributionDestination = trigger.getAttributionDestination();
    Optional<Uri> triggerDestinationTopPrivateDomain =
        trigger.getDestinationType() == EventSurfaceType.APP
            ? Optional.of(BaseUriExtractor.getBaseUri(attributionDestination))
            : WebAddresses.topPrivateDomainAndScheme(attributionDestination);
    Uri publisher = source.getPublisher();
    Optional<Uri> publisherTopPrivateDomain =
        source.getPublisherType() == EventSurfaceType.APP
            ? Optional.of(publisher)
            : WebAddresses.topPrivateDomainAndScheme(publisher);
    if (!triggerDestinationTopPrivateDomain.isPresent() || !publisherTopPrivateDomain.isPresent()) {
      return Optional.empty();
    } else {
      return Optional.of(
          Pair.create(publisherTopPrivateDomain.get(), triggerDestinationTopPrivateDomain.get()));
    }
  }

  public static Attribution.Builder createAttributionBuilder(
      @NonNull Source source, @NonNull Trigger trigger) {
    Optional<Uri> publisherTopPrivateDomain =
        getTopPrivateDomain(source.getPublisher(), source.getPublisherType());
    Uri destination = trigger.getAttributionDestination();
    Optional<Uri> destinationTopPrivateDomain =
        getTopPrivateDomain(destination, trigger.getDestinationType());

    if (!publisherTopPrivateDomain.isPresent() || !destinationTopPrivateDomain.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "insertAttributionRateLimit: "
                  + "getSourceAndDestinationTopPrivateDomains"
                  + " failed. Publisher: %s; Attribution destination: %s",
              source.getPublisher(), destination));
    }

    return new Attribution.Builder()
        .setSourceSite(publisherTopPrivateDomain.get().toString())
        .setSourceOrigin(source.getPublisher().toString())
        .setDestinationSite(destinationTopPrivateDomain.get().toString())
        .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
        .setEnrollmentId(trigger.getEnrollmentId())
        // TODO: b/276638412 rename to Attribution::setSourceTime
        .setTriggerTime(source.getEventTime())
        .setRegistrant(trigger.getRegistrant().toString())
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .setRegistrationOrigin(trigger.getRegistrationOrigin());
  }

  private static Optional<Uri> getTopPrivateDomain(
      Uri uri, @EventSurfaceType int eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(uri))
        : WebAddresses.topPrivateDomainAndScheme(uri);
  }

  private static Pair<UnsignedLong, List<UnsignedLong>> getDebugKeysForFlex(
      List<AttributedTrigger> contributingTriggers, Source source) {
    List<UnsignedLong> triggerDebugKeys = new ArrayList<>();
    // To provide a source debug key in the event report, the source debug key must have been
    // populated for each evaluation for source and trigger for all triggers contributing to the
    // bucket.
    boolean allBucketContributorsHadNonNullSourceDebugKeys = true;
    for (AttributedTrigger trigger : contributingTriggers) {
      // Only add a debug key to the result if the invariant is maintained. Otherwise, the
      // invariant has been broken, but conclude the iteration to process source debug-key.
      if (trigger.getDebugKey() != null) {
        triggerDebugKeys.add(trigger.getDebugKey());
      }
      // Update the value of the boolean for source debug key as a series of AND
      // operations that must all be true.
      allBucketContributorsHadNonNullSourceDebugKeys &= trigger.hasSourceDebugKey();
    }
    // We are allowed to access the actual source debug key value if the invariant has been
    // maintained.
    UnsignedLong sourceDebugKey =
        allBucketContributorsHadNonNullSourceDebugKeys ? source.getDebugKey() : null;
    // All triggers must have debug keys for the report to include any.
    if (contributingTriggers.size() == triggerDebugKeys.size()) {
      return Pair.create(sourceDebugKey, triggerDebugKeys);
    } else {
      return Pair.create(sourceDebugKey, Collections.emptyList());
    }
  }

  private static boolean hasDeduplicationKey(
      @NonNull Source source, @NonNull UnsignedLong dedupKey) {
    for (AttributedTrigger attributedTrigger : source.getAttributedTriggers()) {
      if (dedupKey.equals(attributedTrigger.getDedupKey())) {
        return true;
      }
    }
    return false;
  }

  private AttributionResult getAttributionResultFromGeneratedReports(
      boolean aggregateReportGenerated, boolean eventReportGenerated) {
    if (aggregateReportGenerated && eventReportGenerated) {
      return AttributionResult.AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS;
    }
    if (aggregateReportGenerated) {
      return AttributionResult.AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS;
    }
    if (eventReportGenerated) {
      return AttributionResult.EVENT_REPORT_GENERATED_SUCCESS_STATUS;
    }
    return AttributionResult.UNKNOWN;
  }

  private FailureType getFailureTypeFromTriggerStatus(int triggerStatus) {
    return switch (triggerStatus) {
      case Trigger.Status.IGNORED -> FailureType.TRIGGER_IGNORED;
      case Trigger.Status.ATTRIBUTED -> FailureType.TRIGGER_ALREADY_ATTRIBUTED;
      case Trigger.Status.MARKED_TO_DELETE -> FailureType.TRIGGER_MARKED_FOR_DELETION;
      default -> FailureType.UNKNOWN;
    };
  }

  private void setAndLogAttributionStatus(
      AttributionStatus attributionStatus,
      AttributionResult attributionResult,
      FailureType failureType,
      @Nullable String enrollmentId) {
    attributionStatus.setAttributionResult(attributionResult);
    attributionStatus.setFailureType(failureType);
    logAttributionStats(attributionStatus, enrollmentId);
  }

  private void logAttributionStats(
      AttributionStatus attributionStatus, @Nullable String enrollmentId) {
    mLogger.logMeasurementAttributionStats(
        new MeasurementAttributionStats.Builder()
            .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
            .setSourceType(attributionStatus.getSourceType().getValue())
            .setSurfaceType(attributionStatus.getAttributionSurface().getValue())
            .setResult(attributionStatus.getAttributionResult().getValue())
            .setFailureType(attributionStatus.getFailureType().getValue())
            .setSourceDerived(attributionStatus.isSourceDerived())
            .setInstallAttribution(attributionStatus.isInstallAttribution())
            .setAttributionDelay(attributionStatus.getAttributionDelay())
            .setSourceRegistrant(attributionStatus.getSourceRegistrant())
            .setAggregateReportCount(attributionStatus.getAggregateReportCount())
            .setAggregateDebugReportCount(attributionStatus.getAggregateDebugReportCount())
            .setEventReportCount(attributionStatus.getEventReportCount())
            .setEventDebugReportCount(attributionStatus.getEventDebugReportCount())
            .setNullAggregateReportCount(attributionStatus.getNullAggregateReportCount())
            .build(),
        enrollmentId);
  }

  private void logDelayedSourceRegistrationStats(Source source, Trigger trigger) {
    DelayedSourceRegistrationStatus delayedSourceRegistrationStatus =
        new DelayedSourceRegistrationStatus();
    delayedSourceRegistrationStatus.setRegistrationDelay(
        source.getEventTime() - trigger.getTriggerTime());

    mLogger.logMeasurementDelayedSourceRegistrationStats(
        new MeasurementDelayedSourceRegistrationStats.Builder()
            .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
            .setRegistrationStatus(delayedSourceRegistrationStatus.UNKNOWN)
            .setRegistrationDelay(delayedSourceRegistrationStatus.getRegistrationDelay())
            .setRegistrant(source.getRegistrant().toString())
            .build());
  }

  private long getAggregateReportDelay() {
    long reportDelayFromDefaults =
        (long) (Math.random() * AGGREGATE_REPORT_DELAY_SPAN + AGGREGATE_REPORT_MIN_DELAY);

    String aggregateReportDelayString = mFlags.getMeasurementAggregateReportDelayConfig();

    if (aggregateReportDelayString == null) {
      LoggerFactory.getMeasurementLogger().d("Invalid configurable aggregate report delay: null");
      return reportDelayFromDefaults;
    }

    String[] split = aggregateReportDelayString.split(AGGREGATE_REPORT_DELAY_DELIMITER);

    if (split.length != 2) {
      LoggerFactory.getMeasurementLogger()
          .d("Invalid configurable aggregate report delay: length is not two");
      return reportDelayFromDefaults;
    }

    try {
      final long minDelay = Long.parseLong(split[0].trim());
      final long delaySpan = Long.parseLong(split[1].trim());
      return (long) (Math.random() * delaySpan + minDelay);
    } catch (NumberFormatException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Configurable aggregate report delay parsing failed.");
      return reportDelayFromDefaults;
    }
  }

  private void incrementEventReportCountBy(AttributionStatus attributionStatus, int count) {
    attributionStatus.setEventReportCount(attributionStatus.getEventReportCount() + count);
  }

  private void incrementEventDebugReportCountBy(AttributionStatus attributionStatus, int count) {
    attributionStatus.setEventDebugReportCount(
        attributionStatus.getEventDebugReportCount() + count);
  }

  private void incrementAggregateReportCountBy(AttributionStatus attributionStatus, int count) {
    attributionStatus.setAggregateReportCount(attributionStatus.getAggregateReportCount() + count);
  }

  private void incrementAggregateDebugReportCountBy(
      AttributionStatus attributionStatus, int count) {
    attributionStatus.setAggregateDebugReportCount(
        attributionStatus.getAggregateDebugReportCount() + count);
  }

  private void incrementNullAggregateReportCountBy(AttributionStatus attributionStatus, int count) {
    attributionStatus.setNullAggregateReportCount(
        attributionStatus.getNullAggregateReportCount() + count);
  }

  private boolean isTriggerFallsWithinWindow(
      Source source,
      Trigger trigger,
      UnsignedLong effectiveTriggerData,
      IMeasurementDao measurementDao,
      List<DebugReportApi.Type> adrTypesToGenerate)
      throws DatastoreException {
    MomentPlacement momentPlacement =
        mEventReportWindowCalcDelegate.fallsWithinWindow(source, trigger, effectiveTriggerData);
    if (momentPlacement == MomentPlacement.BEFORE) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          null,
          measurementDao,
          DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED);
      return false;
    }
    if (momentPlacement == MomentPlacement.AFTER) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          null,
          measurementDao,
          DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
      adrTypesToGenerate.add(DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
      return false;
    }
    return true;
  }
}
