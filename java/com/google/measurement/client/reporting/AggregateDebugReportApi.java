/*
 * Copyright (C) 2024 Google LLC
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

import static com.google.measurement.client.util.Applications.ANDROID_APP_SCHEME;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import com.google.measurement.client.Uri;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.ErrorLogUtil;
import com.google.measurement.client.Flags;
import com.google.measurement.client.WebAddresses;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.aggregation.AggregateDebugReportData;
import com.google.measurement.client.aggregation.AggregateDebugReportRecord;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.aggregation.AggregateHistogramContribution;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.util.BaseUriExtractor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.json.JSONException;

/**
 * Generates and schedules aggregate debug reports in the supported ad-tech side erroneous cases.
 */
public class AggregateDebugReportApi {
  public static final String AGGREGATE_DEBUG_REPORT_API = "attribution-reporting-debug";
  // TODO(b/364768862): Bump this to 1.0 based on flexible contribution filtering flag
  private static final String PRE_FLEXIBLE_CONTRIBUTION_FILTERING_API_VERSION = "0.1";
  private static final String POST_FLEXIBLE_CONTRIBUTION_FILTERING_API_VERSION = "1.0";
  private final Flags mFlags;

  public AggregateDebugReportApi(Flags flags) {
    mFlags = flags;
  }

  /**
   * Schedule debug reports for all source registration errors related, i.e. "source-*" debug
   * reports.
   */
  public void scheduleSourceRegistrationDebugReport(
      Source source, Set<DebugReportApi.Type> types, IMeasurementDao measurementDao) {
    if (!mFlags.getMeasurementEnableAggregateDebugReporting()
        || source.getAggregateDebugReportingString() == null) {
      LoggerFactory.getMeasurementLogger()
          .d(
              "Aggregate debug reporting on source disabled; "
                  + "flag=%s; "
                  + "aggregatable_debug_reporting available=%s",
              mFlags.getMeasurementEnableAggregateDebugReporting(),
              source.getAggregateDebugReportingString() != null);
      return;
    }

    try {
      AggregateDebugReporting sourceAdr = source.getAggregateDebugReportingObject();
      List<AggregateDebugReportData> debugDataList =
          Optional.ofNullable(sourceAdr)
              .map(AggregateDebugReporting::getAggregateDebugReportDataList)
              .orElse(null);

      if (debugDataList == null) {
        return;
      }

      List<AggregateHistogramContribution> contributions =
          types.stream()
              .map(type -> getFirstMatchingAggregateReportData(debugDataList, type).orElse(null))
              .filter(Objects::nonNull)
              .map(debugData -> createContributions(debugData, sourceAdr.getKeyPiece()))
              .collect(Collectors.toList());

      if (contributions.isEmpty()) {
        // Both Source and trigger have opted-in but the debug data didn't match
        LoggerFactory.getMeasurementLogger().d("Debug report type data not opted-in for ADR");
        measurementDao.insertAggregateReport(generateNullAggregateReport(source));
        return;
      }

      int sumNewContributions = sumContributions(contributions);
      if (sumNewContributions + source.getAggregateDebugReportContributions()
          > sourceAdr.getBudget()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report of type=%s because it "
                    + "exceeds source budget",
                types);
        measurementDao.insertAggregateReport(generateNullAggregateReport(source));
        return;
      }

      Optional<Uri> baseOrigin = extractBaseUri(source.getRegistrationOrigin());
      Optional<Uri> basePublisher = extractBaseUri(source.getPublisher());

      if (baseOrigin.isEmpty() || basePublisher.isEmpty()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report of type=%s; "
                    + "Invalid origin or top level site",
                types);
        return;
      }

      if (!isWithinRateLimits(
          baseOrigin.get(),
          basePublisher.get(),
          source.getPublisherType(),
          measurementDao,
          (source.getEventTime() - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
          sumNewContributions)) {
        LoggerFactory.getMeasurementLogger()
            .d("Not generating aggregate debug report of type=%s ;rate limit" + " exceeded", types);
        measurementDao.insertAggregateReport(generateNullAggregateReport(source));
        return;
      }

      LoggerFactory.getMeasurementLogger().d("Generating debug report type=%s", types);

      // If the source is persisted in the DB, only then the resultant ADR should have the
      // source ID for FKey constraint and per source reports consideration. Also, update
      // the contributions in the DB if the source registration was successful.
      String sourceId = null;
      if (types.contains(DebugReportApi.Type.SOURCE_SUCCESS)
          || types.contains(DebugReportApi.Type.SOURCE_NOISED)) {
        source.setAggregateDebugContributions(
            sumNewContributions + source.getAggregateDebugReportContributions());
        measurementDao.updateSourceAggregateDebugContributions(source);
        sourceId = source.getId();
      }

      AggregateReport aggregateReport = createAggregateReport(source, sourceId, contributions);
      measurementDao.insertAggregateReport(aggregateReport);

      measurementDao.insertAggregateDebugReportRecord(
          createAggregateDebugReportRecord(
              aggregateReport,
              sumNewContributions,
              source.getRegistrant(),
              basePublisher.get(),
              baseOrigin.get()));
    } catch (JSONException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    } catch (DatastoreException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }
  }

  /**
   * Schedule debug reports for all trigger attribution errors related, i.e. "trigger-*", debug
   * reports, except {@link DebugReportApi.Type#TRIGGER_NO_MATCHING_SOURCE}.
   */
  public void scheduleTriggerAttributionErrorWithSourceDebugReport(
      Source source,
      Trigger trigger,
      List<DebugReportApi.Type> types,
      IMeasurementDao measurementDao) {
    if (!mFlags.getMeasurementEnableAggregateDebugReporting()
        || source.getAggregateDebugReportingString() == null) {
      LoggerFactory.getMeasurementLogger()
          .d(
              "Aggregate debug reporting on source disabled; "
                  + "flag=%s; "
                  + "source_aggregatable_debug_reporting available=%s;"
                  + "trigger_aggregatable debug_reporting available=%s",
              mFlags.getMeasurementEnableAggregateDebugReporting(),
              source.getAggregateDebugReportingString() != null,
              trigger.getAggregateDebugReportingString() != null);
      return;
    }

    try {
      AggregateDebugReporting triggerAdr = trigger.getAggregateDebugReportingObject();
      List<AggregateDebugReportData> triggerDebugDataList =
          Optional.ofNullable(triggerAdr)
              .map(AggregateDebugReporting::getAggregateDebugReportDataList)
              .orElse(null);
      if (triggerDebugDataList == null) {
        return;
      }

      AggregateDebugReporting sourceAdr = source.getAggregateDebugReportingObject();
      if (sourceAdr == null) {
        LoggerFactory.getMeasurementLogger()
            .d("Source side aggregate debug reporting is not available.");
        measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
        return;
      }

      List<AggregateHistogramContribution> contributions =
          types.stream()
              .map(
                  type ->
                      getFirstMatchingAggregateReportData(triggerDebugDataList, type).orElse(null))
              .filter(Objects::nonNull)
              .map(
                  debugData ->
                      createContributions(
                          debugData, sourceAdr.getKeyPiece().or(triggerAdr.getKeyPiece())))
              .collect(Collectors.toList());

      if (contributions.isEmpty()) {
        // Both Source and trigger have opted-in but the debug data didn't match
        LoggerFactory.getMeasurementLogger().d("Debug report type data not opted-in for ADR");
        measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
        return;
      }

      int sumNewContributions = sumContributions(contributions);
      if (sumNewContributions + source.getAggregateDebugReportContributions()
          > sourceAdr.getBudget()) {
        LoggerFactory.getMeasurementLogger()
            .d("Not generating aggregate debug report %s because it exceeds source" + " budget");
        measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
        return;
      }

      if (measurementDao.countNumAggregateReportsPerSource(
              source.getId(), AGGREGATE_DEBUG_REPORT_API)
          >= mFlags.getMeasurementMaxAdrCountPerSource()) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report %s because it exceeds"
                    + " maximum number of reports per source ");
        measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
        return;
      }

      Optional<Uri> baseOrigin = extractBaseUri(trigger.getRegistrationOrigin());
      Uri baseTopLevelSite = trigger.getAttributionDestinationBaseUri();

      if (baseOrigin.isEmpty() || baseTopLevelSite == null) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report of types=%s; "
                    + "Invalid origin or top level site",
                types);
        return;
      }

      if (!isWithinRateLimits(
          baseOrigin.get(),
          baseTopLevelSite,
          trigger.getDestinationType(),
          measurementDao,
          (trigger.getTriggerTime() - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
          sumNewContributions)) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report of types=%s ;rate limit" + " exceeded",
                types);
        measurementDao.insertAggregateReport(generateNullAggregateReport(source, trigger));
        return;
      }

      LoggerFactory.getMeasurementLogger().d("Generating debug report types=%s", types);
      AggregateReport aggregateReport = createAggregateReport(source, trigger, contributions);
      measurementDao.insertAggregateReport(aggregateReport);
      measurementDao.insertAggregateDebugReportRecord(
          createAggregateDebugReportRecord(
              aggregateReport,
              sumNewContributions,
              trigger.getRegistrant(),
              baseTopLevelSite,
              baseOrigin.get()));

      source.setAggregateDebugContributions(
          sumNewContributions + source.getAggregateDebugReportContributions());
      measurementDao.updateSourceAggregateDebugContributions(source);
    } catch (JSONException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    } catch (DatastoreException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }
  }

  /**
   * Create aggregate debug report for {@link DebugReportApi.Type#TRIGGER_NO_MATCHING_SOURCE} case.
   * It's different from {@link #scheduleTriggerAttributionErrorWithSourceDebugReport} because
   * source isn't available hence contribution budget doesn't apply.
   */
  public void scheduleTriggerNoMatchingSourceDebugReport(
      Trigger trigger, IMeasurementDao measurementDao) {
    if (!mFlags.getMeasurementEnableAggregateDebugReporting()
        || trigger.getAggregateDebugReportingString() == null) {
      LoggerFactory.getMeasurementLogger()
          .d(
              "Aggregate debug reporting on source disabled; "
                  + "flag=%s; "
                  + "aggregatable_debug_reporting available=%s",
              mFlags.getMeasurementEnableAggregateDebugReporting(),
              trigger.getAggregateDebugReportingString() != null);
      return;
    }

    try {
      if (trigger.getAggregateDebugReportingObject() == null
          || trigger.getAggregateDebugReportingObject().getAggregateDebugReportDataList() == null) {
        return;
      }
      DebugReportApi.Type type = DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE;
      Optional<AggregateDebugReportData> firstMatchingAggregateReportData =
          getFirstMatchingAggregateReportData(
              trigger.getAggregateDebugReportingObject().getAggregateDebugReportDataList(), type);
      if (firstMatchingAggregateReportData.isEmpty()) {
        LoggerFactory.getMeasurementLogger()
            .d("No matching debug data to generate aggregate debug report.");
        measurementDao.insertAggregateReport(generateNullAggregateReport(trigger));
        return;
      }

      AggregateDebugReportData errorDebugReportingData = firstMatchingAggregateReportData.get();

      Optional<Uri> baseOrigin = extractBaseUri(trigger.getRegistrationOrigin());
      Uri baseTopLevelSite = trigger.getAttributionDestinationBaseUri();
      if (baseOrigin.isEmpty() || baseTopLevelSite == null) {
        LoggerFactory.getMeasurementLogger()
            .d(
                "Not generating aggregate debug report of type=%s; "
                    + "Invalid origin or top level site",
                type);
        return;
      }

      if (!isWithinRateLimits(
          baseOrigin.get(),
          baseTopLevelSite,
          trigger.getDestinationType(),
          measurementDao,
          (trigger.getTriggerTime() - mFlags.getMeasurementAdrBudgetWindowLengthMillis()),
          errorDebugReportingData.getValue())) {
        LoggerFactory.getMeasurementLogger()
            .d("Not generating aggregate debug report of type=%s ;rate limit" + " exceeded", type);
        measurementDao.insertAggregateReport(generateNullAggregateReport(trigger));
        return;
      }

      AggregateHistogramContribution contributions =
          createContributions(
              errorDebugReportingData, trigger.getAggregateDebugReportingObject().getKeyPiece());

      LoggerFactory.getMeasurementLogger().d("Generating debug report type=%s", type);
      AggregateReport aggregateReport = createAggregateReport(trigger, contributions);
      measurementDao.insertAggregateReport(aggregateReport);
      measurementDao.insertAggregateDebugReportRecord(
          createAggregateDebugReportRecord(
              aggregateReport,
              contributions.getValue(),
              trigger.getRegistrant(),
              baseTopLevelSite,
              baseOrigin.get()));
    } catch (JSONException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    } catch (DatastoreException e) {
      // This isn't expected as at this point all data is valid.
      ErrorLogUtil.e(
          e,
          AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE,
          AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
    }
  }

  private Uri getTriggerOrDefaultCoordinatorOrigin(
      AggregateDebugReporting aggregateDebugReportingObject) {
    return Optional.ofNullable(aggregateDebugReportingObject.getAggregationCoordinatorOrigin())
        .orElse(Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()));
  }

  private AggregateReport createAggregateReport(
      Trigger trigger, AggregateHistogramContribution contributions) throws JSONException {
    Uri coordinatorOrigin =
        getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject());
    return new AggregateReport.Builder()
        .setId(UUID.randomUUID().toString())
        .setAttributionDestination(trigger.getAttributionDestination())
        .setPublisher(trigger.getAttributionDestination())
        .setScheduledReportTime(trigger.getTriggerTime())
        .setEnrollmentId(trigger.getEnrollmentId())
        .setDebugCleartextPayload(
            AggregateReport.generateDebugPayload(
                getPaddedContributions(Collections.singletonList(contributions))))
        // We don't want to deliver regular aggregate reports
        .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
        .setApiVersion(getApiVersion())
        .setSourceId(null)
        .setTriggerId(trigger.getId())
        .setRegistrationOrigin(trigger.getRegistrationOrigin())
        .setApi(AGGREGATE_DEBUG_REPORT_API)
        .setAggregationCoordinatorOrigin(coordinatorOrigin)
        .build();
  }

  private AggregateReport createAggregateReport(
      Source source, String sourceId, List<AggregateHistogramContribution> contributions)
      throws JSONException {
    return new AggregateReport.Builder()
        .setId(UUID.randomUUID().toString())
        .setPublisher(source.getPublisher())
        .setAttributionDestination(getSourceDestinationToReport(source))
        .setScheduledReportTime(source.getEventTime())
        .setEnrollmentId(source.getEnrollmentId())
        .setDebugCleartextPayload(
            AggregateReport.generateDebugPayload(getPaddedContributions(contributions)))
        // We don't want to deliver regular aggregate reports for ADRs
        .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
        .setApiVersion(getApiVersion())
        .setSourceId(sourceId)
        .setTriggerId(null)
        .setRegistrationOrigin(source.getRegistrationOrigin())
        .setApi(AGGREGATE_DEBUG_REPORT_API)
        .setAggregationCoordinatorOrigin(
            Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
        .build();
  }

  private AggregateReport createAggregateReport(
      Source source, Trigger trigger, List<AggregateHistogramContribution> contributions)
      throws JSONException {
    Uri coordinatorOrigin =
        getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject());
    return new AggregateReport.Builder()
        .setId(UUID.randomUUID().toString())
        .setPublisher(source.getPublisher())
        .setAttributionDestination(trigger.getAttributionDestination())
        .setScheduledReportTime(trigger.getTriggerTime())
        .setEnrollmentId(source.getEnrollmentId())
        .setDebugCleartextPayload(
            AggregateReport.generateDebugPayload(getPaddedContributions(contributions)))
        // We don't want to deliver regular aggregate reports
        .setStatus(AggregateReport.Status.MARKED_TO_DELETE)
        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
        .setApiVersion(getApiVersion())
        // As source/trigger registration might have failed
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .setRegistrationOrigin(trigger.getRegistrationOrigin())
        .setApi(AGGREGATE_DEBUG_REPORT_API)
        .setAggregationCoordinatorOrigin(coordinatorOrigin)
        .build();
  }

  private boolean isWithinRateLimits(
      Uri origin,
      Uri topLevelSite,
      int topLevelSiteType,
      IMeasurementDao measurementDao,
      long windowStartTime,
      int newContributions)
      throws DatastoreException {
    // Per origin per topLevelSite limits
    if ((measurementDao.sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
                topLevelSite, topLevelSiteType, origin, windowStartTime)
            + newContributions)
        > mFlags.getMeasurementAdrBudgetOriginXPublisherXWindow()) {
      return false;
    }

    // Per topLevelSite limits
    if ((measurementDao.sumAggregateDebugReportBudgetXPublisherXWindow(
                topLevelSite, topLevelSiteType, windowStartTime)
            + newContributions)
        > mFlags.getMeasurementAdrBudgetPublisherXWindow()) {
      return false;
    }

    return true;
  }

  private Optional<AggregateDebugReportData> getFirstMatchingAggregateReportData(
      Collection<AggregateDebugReportData> aggregateDebugReportDataList,
      DebugReportApi.Type reportType) {
    if (aggregateDebugReportDataList == null) {
      return Optional.empty();
    }
    return aggregateDebugReportDataList.stream()
        .filter(
            data ->
                data.getReportType().contains(reportType.getValue())
                    || data.getReportType().contains(DebugReportApi.Type.UNSPECIFIED.getValue()))
        .findFirst();
  }

  private Uri getSourceDestinationToReport(Source source) {
    return (source.getAppDestinations() == null || source.getAppDestinations().isEmpty())
        ? Collections.min(source.getWebDestinations())
        : Collections.min(source.getAppDestinations());
  }

  private static AggregateHistogramContribution createContributions(
      AggregateDebugReportData errorDebugReportingData, BigInteger keyPiece) {
    return new AggregateHistogramContribution.Builder()
        .setKey(keyPiece.or(errorDebugReportingData.getKeyPiece()))
        .setValue(errorDebugReportingData.getValue())
        .build();
  }

  private static int sumContributions(List<AggregateHistogramContribution> contributions) {
    return contributions.stream().mapToInt(AggregateHistogramContribution::getValue).sum();
  }

  private static AggregateDebugReportRecord createAggregateDebugReportRecord(
      AggregateReport aggregateReport,
      int contributionValue,
      Uri registrantApp,
      Uri topLevelSite,
      Uri origin) {
    return new AggregateDebugReportRecord.Builder(
            aggregateReport.getScheduledReportTime(),
            topLevelSite,
            registrantApp,
            origin,
            contributionValue)
        .setSourceId(aggregateReport.getSourceId())
        .setTriggerId(aggregateReport.getTriggerId())
        .build();
  }

  private AggregateReport generateNullAggregateReport(Source source, Trigger trigger)
      throws JSONException {
    return generateBaseNullReportBuilder()
        .setRegistrationOrigin(trigger.getRegistrationOrigin())
        .setAttributionDestination(trigger.getAttributionDestination())
        .setScheduledReportTime(trigger.getTriggerTime())
        .setTriggerId(trigger.getId())
        .setAggregationCoordinatorOrigin(
            getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject()))
        .setPublisher(source.getPublisher())
        .build();
  }

  private AggregateReport generateNullAggregateReport(Source source) throws JSONException {
    return generateBaseNullReportBuilder()
        .setPublisher(source.getPublisher())
        .setRegistrationOrigin(source.getRegistrationOrigin())
        .setAttributionDestination(getSourceDestinationToReport(source))
        .setScheduledReportTime(source.getEventTime())
        // We don't want null report to be counted as this source driven ADR
        .setSourceId(null)
        .setAggregationCoordinatorOrigin(
            Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
        .build();
  }

  private AggregateReport generateNullAggregateReport(Trigger trigger) throws JSONException {
    return generateBaseNullReportBuilder()
        .setRegistrationOrigin(trigger.getRegistrationOrigin())
        .setAttributionDestination(trigger.getAttributionDestination())
        .setScheduledReportTime(trigger.getTriggerTime())
        .setTriggerId(trigger.getId())
        .setAggregationCoordinatorOrigin(
            getTriggerOrDefaultCoordinatorOrigin(trigger.getAggregateDebugReportingObject()))
        .build();
  }

  private AggregateReport.Builder generateBaseNullReportBuilder() throws JSONException {
    String debugPayload =
        AggregateReport.generateDebugPayload(
            getPaddedContributions(Collections.singletonList(createPaddingContribution())));
    return new AggregateReport.Builder()
        .setId(UUID.randomUUID().toString())
        .setApiVersion(getApiVersion())
        // exclude by default
        .setSourceRegistrationTime(null)
        .setDebugCleartextPayload(debugPayload)
        .setIsFakeReport(true)
        .setTriggerContextId(null)
        .setApi(AGGREGATE_DEBUG_REPORT_API)
        .setAggregationCoordinatorOrigin(
            Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
        .setDebugReportStatus(AggregateReport.DebugReportStatus.PENDING)
        .setStatus(AggregateReport.Status.MARKED_TO_DELETE);
  }

  private List<AggregateHistogramContribution> getPaddedContributions(
      List<AggregateHistogramContribution> contributions) {
    List<AggregateHistogramContribution> paddedContributions = new ArrayList<>(contributions);
    if (mFlags.getMeasurementEnableAggregatableReportPayloadPadding()) {
      IntStream.range(
              contributions.size(), mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration())
          .forEach(i -> paddedContributions.add(createPaddingContribution()));
    }
    return paddedContributions;
  }

  private AggregateHistogramContribution createPaddingContribution() {
    return new AggregateHistogramContribution.Builder().setPaddingContribution().build();
  }

  private static Optional<Uri> extractBaseUri(Uri uri) {
    if (uri.getScheme().equals(ANDROID_APP_SCHEME)) {
      return Optional.of(BaseUriExtractor.getBaseUri(uri));
    }
    return WebAddresses.topPrivateDomainAndScheme(uri);
  }

  private String getApiVersion() {
    if (mFlags.getMeasurementEnableFlexibleContributionFiltering()) {
      return POST_FLEXIBLE_CONTRIBUTION_FILTERING_API_VERSION;
    }
    return PRE_FLEXIBLE_CONTRIBUTION_FILTERING_API_VERSION;
  }
}
