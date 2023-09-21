/*
 * Copyright 2022 Google LLC
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

package com.google.measurement;

import static com.google.measurement.PrivacyParams.AGGREGATE_MAX_REPORT_DELAY;
import static com.google.measurement.PrivacyParams.AGGREGATE_MIN_REPORT_DELAY;

import com.google.common.collect.ImmutableList;
import com.google.measurement.DebugReportApi.Type;
import com.google.measurement.aggregation.AggregatableAttributionSource;
import com.google.measurement.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.aggregation.AggregateAttributionData;
import com.google.measurement.aggregation.AggregateDeduplicationKey;
import com.google.measurement.aggregation.AggregateHistogramContribution;
import com.google.measurement.aggregation.AggregatePayloadGenerator;
import com.google.measurement.aggregation.AggregateReport;
import com.google.measurement.noising.SourceNoiseHandler;
import com.google.measurement.util.BaseUriExtractor;
import com.google.measurement.util.Debug;
import com.google.measurement.util.Filter;
import com.google.measurement.util.UnsignedLong;
import com.google.measurement.util.Util;
import com.google.measurement.util.Web;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AttributionJobHandler {
  private static final Logger logger = Logger.getLogger(AttributionJobHandler.class.getName());
  private final IMeasurementDAO measurementDAO;
  private final Flags mFlags;
  private final DebugReportApi mDebugReportApi;
  private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
  private final SourceNoiseHandler mSourceNoiseHandler;
  private List<Object> combinedData;
  private static final String API_VERSION = "0.1";

  private enum TriggeringStatus {
    DROPPED,
    ATTRIBUTED
  }

  AttributionJobHandler(IMeasurementDAO measurementDAO, List<Object> combinedData) {
    this(
        measurementDAO,
        combinedData,
        new Flags(),
        new DebugReportApi(new Flags()),
        new EventReportWindowCalcDelegate(new Flags()),
        new SourceNoiseHandler(new Flags()));
  }

  AttributionJobHandler(
      IMeasurementDAO measurementDAO,
      List<Object> combinedData,
      Flags flags,
      DebugReportApi debugReportApi,
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      SourceNoiseHandler sourceNoiseHandler) {
    this.measurementDAO = measurementDAO;
    this.mFlags = flags;
    this.mDebugReportApi = debugReportApi;
    this.mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    this.mSourceNoiseHandler = sourceNoiseHandler;
    this.combinedData = combinedData;
  }

  synchronized void performPendingAttributions() {
    if (this.combinedData == null) {
      this.combinedData = new ArrayList<>();
      this.combinedData.addAll(measurementDAO.getPendingTriggers());
    }

    for (Object combinedDatum : this.combinedData) {
      if (combinedDatum instanceof Trigger) {
        performAttribution((Trigger) combinedDatum);
      } else {
        // This is an (un)install event
        ExtensionEvent event = (ExtensionEvent) combinedDatum;
        if (event.getAction().equals("install")) {
          measurementDAO.doInstallAttribution(event.getUri(), event.getTimestamp());
        } else {
          measurementDAO.undoInstallAttribution(event.getUri());
          measurementDAO.deleteAppRecords(event.getUri().toString());
        }
      }
    }
  }

  /**
   * Perform attribution for {@code trigger}.
   *
   * @param trigger to perform attribution for {@link Trigger}
   */
  private void performAttribution(Trigger trigger) {
    if (trigger.getStatus() != Trigger.Status.PENDING) {
      return;
    }

    Optional<Map<Source, List<Source>>> sourceOpt =
        selectSourceToAttribute(trigger, measurementDAO);

    if (sourceOpt.isEmpty()) {
      mDebugReportApi.scheduleTriggerNoMatchingSourceDebugReport(
          trigger, measurementDAO, Type.TRIGGER_NO_MATCHING_SOURCE);
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    Source source = sourceOpt.get().keySet().iterator().next();
    List<Source> remainingMatchingSources = sourceOpt.get().get(source);

    if (!doTopLevelFiltersMatch(source, trigger)) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    if (shouldAttributionBeBlockedByRateLimits(source, trigger, measurementDAO)) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    TriggeringStatus aggregateTriggeringStatus =
        maybeGenerateAggregateReport(source, trigger, measurementDAO);

    TriggeringStatus eventTriggeringStatus =
        maybeGenerateEventReport(source, trigger, measurementDAO);

    if (eventTriggeringStatus == TriggeringStatus.ATTRIBUTED
        || aggregateTriggeringStatus == TriggeringStatus.ATTRIBUTED) {
      ignoreCompetingSources(measurementDAO, remainingMatchingSources, trigger.getEnrollmentId());
      attributeTriggerAndInsertAttribution(trigger, source, measurementDAO);
    } else {
      ignoreTrigger(trigger, measurementDAO);
    }
  }

  private boolean shouldAttributionBeBlockedByRateLimits(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    if (!hasAttributionQuota(source, trigger, measurementDAO)
        || !isEnrollmentWithinPrivacyBounds(source, trigger, measurementDAO)) {
      logger.info(
          String.format(
              "Attribution blocked by rate limits. Source ID: %s ; Trigger ID: %s ",
              source.getId(), trigger.getId()));
      return true;
    }
    return false;
  }

  private TriggeringStatus maybeGenerateAggregateReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    if (trigger.getTriggerTime() > source.getAggregatableReportWindow()) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, null, measurementDAO, Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED);
      return TriggeringStatus.DROPPED;
    }

    int numReports =
        measurementDAO.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType());

    if (numReports >= SystemHealthParams.MAX_AGGREGATE_REPORTS_PER_DESTINATION) {
      logger.info(
          String.format(
              "Aggregate reports for destination %1$s exceeds system health limit of %2$d.",
              trigger.getAttributionDestination(),
              SystemHealthParams.MAX_AGGREGATE_REPORTS_PER_DESTINATION));
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          String.valueOf(numReports),
          measurementDAO,
          Type.TRIGGER_AGGREGATE_STORAGE_LIMIT);
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
            measurementDAO,
            Type.TRIGGER_AGGREGATE_DEDUPLICATED);
        return TriggeringStatus.DROPPED;
      }

      Optional<List<AggregateHistogramContribution>> contributions =
          AggregatePayloadGenerator.generateAttributionReport(source, trigger);
      if (!contributions.isPresent()) {
        if (source.getAggregatableAttributionSource().isPresent()
            && trigger.getAggregatableAttributionTrigger().isPresent()) {
          mDebugReportApi.scheduleTriggerDebugReport(
              source,
              trigger,
              /* limit= */ null,
              measurementDAO,
              Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS);
        }
        return TriggeringStatus.DROPPED;
      }

      OptionalInt newAggregateContributions =
          validateAndGetUpdatedAggregateContributions(
              contributions.get(), source, trigger, measurementDAO);
      if (!newAggregateContributions.isPresent()) {
        logger.info(
            String.format(
                "Aggregate contributions exceeded bound. Source ID: %s ; Trigger ID: %s ",
                source.getId(), trigger.getId()));
        return TriggeringStatus.DROPPED;
      }

      source.setAggregateContributions(newAggregateContributions.getAsInt());

      long randomTime =
          (long)
              ((Math.random() * (AGGREGATE_MAX_REPORT_DELAY - AGGREGATE_MIN_REPORT_DELAY))
                  + AGGREGATE_MIN_REPORT_DELAY);

      UnsignedLong sourceDebugKey = null;
      UnsignedLong triggerDebugKey = null;
      AggregateReport.DebugReportStatus debugReportStatus = AggregateReport.DebugReportStatus.NONE;
      if (Debug.isAttributionDebugReportPermitted(
          source, trigger, sourceDebugKey, triggerDebugKey)) {
        debugReportStatus = AggregateReport.DebugReportStatus.PENDING;
      }

      AggregateReport.Builder aggregateReportBuilder =
          new AggregateReport.Builder()
              .setId(UUID.randomUUID().toString())
              .setPublisher(source.getRegistrant())
              .setAttributionDestination(trigger.getAttributionDestinationBaseUri())
              .setSourceRegistrationTime(Util.roundDownToDay(source.getEventTime()))
              .setScheduledReportTime(trigger.getTriggerTime() + randomTime)
              .setEnrollmentId(source.getEnrollmentId())
              .setDebugCleartextPayload(AggregateReport.generateDebugPayload(contributions.get()))
              .setAggregateAttributionData(
                  new AggregateAttributionData.Builder()
                      .setContributions(contributions.get())
                      .build())
              .setStatus(AggregateReport.Status.PENDING)
              .setDebugReportStatus(debugReportStatus)
              .setApiVersion(API_VERSION)
              .setSourceDebugKey(sourceDebugKey)
              .setTriggerDebugKey(triggerDebugKey)
              .setSourceId(source.getId())
              .setTriggerId(trigger.getId())
              .setRegistrationOrigin(trigger.getRegistrationOrigin());

      if (aggregateDeduplicationKeyOptional.isPresent()) {
        aggregateReportBuilder.setDedupKey(
            aggregateDeduplicationKeyOptional.get().getDeduplicationKey().get());
      }
      AggregateReport aggregateReport = aggregateReportBuilder.build();

      finalizeAggregateReportCreation(
          source, aggregateDeduplicationKeyOptional, aggregateReport, measurementDAO);
      return TriggeringStatus.ATTRIBUTED;
    } catch (ParseException e) {
      logger.severe("ParseException when parsing aggregate fields in AttributionJobHandler");
      return TriggeringStatus.DROPPED;
    }
  }

  private Optional<Map<Source, List<Source>>> selectSourceToAttribute(
      Trigger trigger, IMeasurementDAO measurementDAO) {
    List<Source> matchingSources = measurementDAO.getMatchingActiveSources(trigger);

    if (matchingSources.isEmpty()) {
      return Optional.empty();
    }

    // Sort based on isInstallAttributed, Priority and Event Time.
    // Is a valid install-attributed source.
    Function<Source, Boolean> installAttributionComparator =
        (Source source) ->
            source.isInstallAttributed() && isWithinInstallCooldownWindow(source, trigger);

    matchingSources.sort(
        Comparator.comparing(installAttributionComparator, Comparator.reverseOrder())
            .thenComparing(Source::getPriority, Comparator.reverseOrder())
            .thenComparing(Source::getEventTime, Comparator.reverseOrder()));

    Source selectedSource = matchingSources.remove(0);

    return Optional.of(Map.of(selectedSource, matchingSources));
  }

  private Optional<AggregateDeduplicationKey> maybeGetAggregateDeduplicationKey(
      Source source, Trigger trigger) throws ParseException {
    Optional<AggregateDeduplicationKey> dedupKey;
    Optional<AggregatableAttributionSource> optionalAggregateAttributionSource =
        source.getAggregatableAttributionSource();
    Optional<AggregatableAttributionTrigger> optionalAggregateAttributionTrigger =
        trigger.getAggregatableAttributionTrigger();
    if (!optionalAggregateAttributionSource.isPresent()
        || !optionalAggregateAttributionTrigger.isPresent()) {
      return Optional.empty();
    }
    AggregatableAttributionSource aggregateAttributionSource =
        optionalAggregateAttributionSource.get();
    AggregatableAttributionTrigger aggregateAttributionTrigger =
        optionalAggregateAttributionTrigger.get();
    dedupKey =
        aggregateAttributionTrigger.maybeExtractDedupKey(aggregateAttributionSource.getFilterMap());
    return dedupKey;
  }

  private void ignoreCompetingSources(
      IMeasurementDAO measurementDAO,
      List<Source> remainingMatchingSources,
      String triggerEnrollmentId) {

    if (!remainingMatchingSources.isEmpty()) {
      List<Source> ignoredOriginalSourceIds = new ArrayList<>();
      for (Source source : remainingMatchingSources) {
        source.setStatus(Source.Status.IGNORED);
        ignoredOriginalSourceIds.add(source);
      }
      measurementDAO.updateSourceStatus(ignoredOriginalSourceIds, Source.Status.IGNORED);
    }
  }

  private TriggeringStatus maybeGenerateEventReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, null, measurementDAO, Type.TRIGGER_EVENT_NOISE);
      return TriggeringStatus.DROPPED;
    }

    if (trigger.getTriggerTime() > source.getEventReportWindow()) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, null, measurementDAO, Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED);
      return TriggeringStatus.DROPPED;
    }

    Optional<EventTrigger> matchingEventTrigger =
        findFirstMatchingEventTrigger(source, trigger, measurementDAO);
    if (!matchingEventTrigger.isPresent()) {
      return TriggeringStatus.DROPPED;
    }
    EventTrigger eventTrigger = matchingEventTrigger.get();
    // Check if deduplication key clashes with existing reports.
    if (eventTrigger.getDedupKey() != null
        && source.getEventReportDedupKeys().contains(eventTrigger.getDedupKey())) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source, trigger, /* limit= */ null, measurementDAO, Type.TRIGGER_EVENT_DEDUPLICATED);
      return TriggeringStatus.DROPPED;
    }

    int numReports =
        measurementDAO.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType());
    if (numReports >= SystemHealthParams.MAX_EVENT_REPORTS_PER_DESTINATION) {
      logger.info(
          String.format(
              "Event reports for destination %1$s exceeds system health limit of %2$d.",
              trigger.getAttributionDestination(),
              SystemHealthParams.MAX_EVENT_REPORTS_PER_DESTINATION));
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          String.valueOf(numReports),
          measurementDAO,
          Type.TRIGGER_EVENT_STORAGE_LIMIT);
      return TriggeringStatus.DROPPED;
    }

    Map<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(measurementDAO).getDebugKeys(source, trigger);

    EventReport newEventReport =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(
                source,
                trigger,
                eventTrigger,
                debugKeyPair,
                mEventReportWindowCalcDelegate,
                mSourceNoiseHandler,
                getEventReportDestinations(source, trigger.getDestinationType()))
            .build();

    if (source.getTriggerSpecs() == null || source.getTriggerSpecs().isEmpty()) {
      if (!provisionEventReportQuota(source, trigger, newEventReport, measurementDAO)) {
        return TriggeringStatus.DROPPED;
      }
      finalizeEventReportCreation(source, eventTrigger, newEventReport, measurementDAO);
    } else {
      if (!provisionEventReportFlexEventApiQuota(
          source, newEventReport, measurementDAO, eventTrigger)) {
        return TriggeringStatus.DROPPED;
      }
    }
    return TriggeringStatus.ATTRIBUTED;
  }

  private boolean provisionEventReportFlexEventApiQuota(
      Source source,
      EventReport newEventReport,
      IMeasurementDAO measurementDao,
      EventTrigger eventTrigger) {
    ReportSpec reportSpec = source.getFlexEventReportSpec();
    if (!reportSpec.containsTriggerData(newEventReport.getTriggerData())) {
      return false;
    }

    // for flexible event API.
    int bucketIncrements = ReportSpecUtil.countBucketIncrements(reportSpec, newEventReport);
    if (bucketIncrements == 0) {
      // the new proposed report doesn't cause bucket increments so no new report
      // generated
      reportSpec.insertAttributedTrigger(newEventReport);
      // Flex API already inserts the attributed trigger and does not need an explicit action
      // for that.
      if (eventTrigger.getDedupKey() != null) {
        source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
        measurementDao.updateSourceEventReportDedupKeys(source);
      }
    } else {
      List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);
      if (sourceEventReports.size() + bucketIncrements <= reportSpec.getMaxReports()) {
        // there are enough quota to generate all report for this trigger. No competing
        // condition
        reportSpec.insertAttributedTrigger(newEventReport);

        finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
            source,
            eventTrigger,
            newEventReport,
            measurementDao,
            bucketIncrements,
            getReportCountForTriggerData(newEventReport.getTriggerData(), sourceEventReports));
      } else {
        // competing condition: more event report candidate than allowed quota
        Pair<List<EventReport>, Integer> tmp =
            ReportSpecUtil.processIncomingReport(
                reportSpec, bucketIncrements, newEventReport, sourceEventReports);
        List<EventReport> toBeDeletedReports = tmp.first;
        int numOfNewReportGenerated = tmp.second;
        for (EventReport report : toBeDeletedReports) {
          measurementDao.deleteEventReport(report);
          if (report.getTriggerDedupKey() != null) {
            source.getEventReportDedupKeys().remove(report.getTriggerDedupKey());
          }
        }
        // create a filtered list to remove new event report and deleted event report
        List<EventReport> nonDeletedReports =
            sourceEventReports.stream()
                .filter(obj -> !toBeDeletedReports.contains(obj))
                .collect(Collectors.toList());
        finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
            source,
            eventTrigger,
            newEventReport,
            measurementDao,
            numOfNewReportGenerated,
            getReportCountForTriggerData(newEventReport.getTriggerData(), nonDeletedReports));
        resetSummaryBucketForEventReportsRelatedToDeletion(
            source, nonDeletedReports, toBeDeletedReports, measurementDAO);
      }
    }
    measurementDao.updateSourceAttributedTriggers(source);
    return true;
  }

  private void resetSummaryBucketForEventReportsRelatedToDeletion(
      Source source,
      List<EventReport> currentEventReports,
      List<EventReport> deletedReports,
      IMeasurementDAO measurementDao) {
    Set<UnsignedLong> processedTriggerData = new HashSet<>();
    List<EventReport> orderedEventReports =
        currentEventReports.stream()
            .sorted(Comparator.comparingLong(EventReport::getTriggerTime).reversed())
            .collect(Collectors.toList());

    for (EventReport deletedReport : deletedReports) {
      UnsignedLong triggerData = deletedReport.getTriggerData();
      if (processedTriggerData.contains(triggerData)) {
        continue;
      }
      processedTriggerData.add(triggerData);
      int count = 0;
      List<Long> summaryBuckets =
          ReportSpecUtil.getSummaryBucketsForTriggerData(
              source.getFlexEventReportSpec(), triggerData);
      for (EventReport currentReport : orderedEventReports) {
        Pair<Long, Long> newSummaryBucket =
            ReportSpecUtil.getSummaryBucketFromIndex(count, summaryBuckets);
        count++;
        if (!newSummaryBucket.equals(currentReport.getTriggerSummaryBucket())) {
          // only the new bucket different with original one, we need to update DB
          currentReport.updateSummaryBucket(newSummaryBucket);
          measurementDao.updateEventReportSummaryBucket(
              currentReport.getId(), currentReport.getTriggerSummaryBucket());
        }
      }
    }
  }

  private int getReportCountForTriggerData(
      UnsignedLong triggerData, List<EventReport> currentReports) {
    int count = 0;
    for (EventReport report : currentReports) {
      if (report.getTriggerData().equals(triggerData)) {
        count++;
      }
    }
    return count;
  }

  private void finalizeMultipleEventReportCreationWithTriggerSummaryBucket(
      Source source,
      EventTrigger eventTrigger,
      EventReport eventReport,
      IMeasurementDAO measurementDao,
      int numNewReport,
      int numCurrentReportWithSameTriggerData) {
    // Flex API already inserts the attributed trigger and does not need an explicit action for
    // that.
    if (eventTrigger.getDedupKey() != null) {
      source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
    }
    measurementDao.updateSourceEventReportDedupKeys(source);
    List<Long> summaryBuckets =
        ReportSpecUtil.getSummaryBucketsForTriggerData(
            source.getFlexEventReportSpec(), eventTrigger.getTriggerData());
    for (int i = 0; i < numNewReport; i++) {
      eventReport.updateSummaryBucket(
          ReportSpecUtil.getSummaryBucketFromIndex(
              numCurrentReportWithSameTriggerData++, summaryBuckets));
      measurementDao.insertEventReport(eventReport);
    }
  }

  private List<URI> getEventReportDestinations(Source source, EventSurfaceType destinationType) {
    ImmutableList.Builder<URI> destinations = new ImmutableList.Builder<>();
    if (mFlags.getMeasurementEnableCoarseEventReportDestinations()
        && source.getCoarseEventReportDestinations()) {
      Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
      Optional.ofNullable(source.getWebDestinations()).ifPresent(destinations::addAll);
    } else {
      destinations.addAll(source.getAttributionDestinations(destinationType));
    }
    return destinations.build();
  }

  private boolean provisionEventReportQuota(
      Source source, Trigger trigger, EventReport newEventReport, IMeasurementDAO measurementDAO) {
    List<EventReport> sourceEventReports = measurementDAO.getSourceEventReports(source);
    if (isWithinReportLimit(source, sourceEventReports.size(), trigger.getDestinationType())) {
      return true;
    }
    List<EventReport> relevantEventReports =
        sourceEventReports.stream()
            .filter((r) -> r.getStatus() == EventReport.Status.PENDING)
            .filter((r) -> r.getReportTime() == newEventReport.getReportTime())
            .sorted(
                Comparator.comparingLong(EventReport::getTriggerPriority)
                    .thenComparing(EventReport::getTriggerTime, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    if (relevantEventReports.isEmpty()) {
      UnsignedLong triggerData = newEventReport.getTriggerData();
      mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
          source, trigger, triggerData, measurementDAO, Type.TRIGGER_EVENT_EXCESSIVE_REPORTS);
      return false;
    }

    EventReport lowestPriorityEventReport = relevantEventReports.get(0);
    if (lowestPriorityEventReport.getTriggerPriority() >= newEventReport.getTriggerPriority()) {
      UnsignedLong triggerData = newEventReport.getTriggerData();
      mDebugReportApi.scheduleTriggerDebugReportWithAllFields(
          source, trigger, triggerData, measurementDAO, Type.TRIGGER_EVENT_LOW_PRIORITY);
      return false;
    }

    if (lowestPriorityEventReport.getTriggerDedupKey() != null) {
      source.getEventReportDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
    }

    measurementDAO.deleteEventReport(lowestPriorityEventReport);
    return true;
  }

  private void finalizeEventReportCreation(
      Source source,
      EventTrigger eventTrigger,
      EventReport eventReport,
      IMeasurementDAO measurementDAO) {
    if (eventTrigger.getDedupKey() != null) {
      source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
    }
    measurementDAO.updateSourceEventReportDedupKeys(source);
    measurementDAO.insertEventReport(eventReport);
  }

  private void finalizeAggregateReportCreation(
      Source source,
      Optional<AggregateDeduplicationKey> aggregateDeduplicationKey,
      AggregateReport aggregateReport,
      IMeasurementDAO measurementDAO) {
    if (aggregateDeduplicationKey.isPresent()) {
      source
          .getAggregateReportDedupKeys()
          .add(aggregateDeduplicationKey.get().getDeduplicationKey().get());
    }
    if (source.getParentId() == null) {
      // Only update aggregate contributions for an original source, not for a derived
      // source
      measurementDAO.updateSourceAggregateContributions(source);
      measurementDAO.updateSourceAggregateReportDedupKeys(source);
    }
    measurementDAO.insertAggregateReport(aggregateReport);
  }

  private void attributeTriggerAndInsertAttribution(
      Trigger trigger, Source source, IMeasurementDAO measurementDAO) {
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    measurementDAO.insertAttribution(createAttribution(source, trigger));
  }

  private void ignoreTrigger(Trigger trigger, IMeasurementDAO measurementDAO) {
    trigger.setStatus(Trigger.Status.IGNORED);
  }

  private boolean hasAttributionQuota(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    long attributionCount = measurementDAO.getAttributionsPerRateLimitWindow(source, trigger);
    if (attributionCount >= mFlags.getMeasurementMaxAttributionPerRateLimitWindow()) {
      mDebugReportApi.scheduleTriggerDebugReport(
          source,
          trigger,
          String.valueOf(attributionCount),
          measurementDAO,
          Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT);
    }
    return attributionCount < mFlags.getMeasurementMaxAttributionPerRateLimitWindow();
  }

  private boolean isWithinReportLimit(
      Source source, int existingReportCount, EventSurfaceType destinationType) {
    return mEventReportWindowCalcDelegate.getMaxReportCount(
            source, hasAppInstallAttributionOccurred(source, destinationType))
        > existingReportCount;
  }

  private static boolean hasAppInstallAttributionOccurred(
      Source source, EventSurfaceType destinationType) {
    return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
  }

  private boolean isWithinInstallCooldownWindow(Source source, Trigger trigger) {
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
  private boolean doTopLevelFiltersMatch(Source source, Trigger trigger) {
    try {
      FilterMap sourceFilters = source.getFilterData();
      List<FilterMap> triggerFilterSet = extractFilterSet(trigger.getFilters());
      List<FilterMap> triggerNotFilterSet = extractFilterSet(trigger.getNotFilters());
      boolean isFilterMatch =
          Filter.isFilterMatch(sourceFilters, triggerFilterSet, true)
              && Filter.isFilterMatch(sourceFilters, triggerNotFilterSet, false);
      if (!isFilterMatch
          && !sourceFilters.getAttributionFilterMap().isEmpty()
          && (!triggerFilterSet.isEmpty() || !triggerNotFilterSet.isEmpty())) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            /* limit= */ null,
            measurementDAO,
            Type.TRIGGER_NO_MATCHING_FILTER_DATA);
      }
      return isFilterMatch;
    } catch (ParseException e) {
      return false;
    }
  }

  private Optional<EventTrigger> findFirstMatchingEventTrigger(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    try {
      FilterMap sourceFiltersData = source.getFilterData();
      List<EventTrigger> eventTriggers = trigger.parseEventTriggers(true);
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
            measurementDAO,
            Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS);
      }
      return matchingEventTrigger;
    } catch (ParseException e) {
      // If JSON is malformed, we shall consider as not matched.
      logger.severe("Malformed JSON string: " + e.getMessage());
      return Optional.empty();
    }
  }

  private boolean doEventLevelFiltersMatch(FilterMap sourceFilter, EventTrigger eventTrigger) {
    if (eventTrigger.getFilterSet().isPresent()
        && !Filter.isFilterMatch(sourceFilter, eventTrigger.getFilterSet().get(), true)) {
      return false;
    }
    if (eventTrigger.getNotFilterSet().isPresent()
        && !Filter.isFilterMatch(sourceFilter, eventTrigger.getNotFilterSet().get(), false)) {
      return false;
    }
    return true;
  }

  private static List<FilterMap> extractFilterSet(String str) throws ParseException {
    String json = (str == null || str.isEmpty()) ? "[]" : str;
    List<FilterMap> filterSet = new ArrayList<>();
    JSONParser parser = new JSONParser();
    JSONArray filters = (JSONArray) parser.parse(json);

    for (int i = 0; i < filters.size(); i++) {
      FilterMap filterMap =
          new FilterMap.Builder().buildFilterData((JSONObject) filters.get(i)).build();
      filterSet.add(filterMap);
    }
    return filterSet;
  }

  private OptionalInt validateAndGetUpdatedAggregateContributions(
      List<AggregateHistogramContribution> contributions,
      Source source,
      Trigger trigger,
      IMeasurementDAO measurementDAO) {
    int newAggregateContributions = source.getAggregateContributions();
    for (AggregateHistogramContribution contribution : contributions) {
      try {
        newAggregateContributions =
            Math.addExact(newAggregateContributions, contribution.getValue());
        if (newAggregateContributions >= PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
          // When histogram value is >= 65536 (aggregatable_budget_per_source),
          // generate verbose debug report, record the actual histogram value.
          mDebugReportApi.scheduleTriggerDebugReport(
              source,
              trigger,
              String.valueOf(PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE),
              measurementDAO,
              Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET);
        }
        if (newAggregateContributions > PrivacyParams.MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE) {
          return OptionalInt.empty();
        }
      } catch (ArithmeticException e) {
        logger.severe("Error adding aggregate contribution values.");
        return OptionalInt.empty();
      }
    }
    return OptionalInt.of(newAggregateContributions);
  }

  private boolean isEnrollmentWithinPrivacyBounds(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    Optional<Map<URI, URI>> publisherAndDestination =
        getPublisherAndDestinationTopPrivateDomains(source, trigger);
    if (publisherAndDestination.isPresent()) {
      URI key = publisherAndDestination.get().keySet().iterator().next();
      Integer count =
          measurementDAO.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
              key,
              publisherAndDestination.get().get(key),
              trigger.getEnrollmentId(),
              trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
              trigger.getTriggerTime());

      if (count >= mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution()) {
        mDebugReportApi.scheduleTriggerDebugReport(
            source,
            trigger,
            String.valueOf(count),
            measurementDAO,
            Type.TRIGGER_REPORTING_ORIGIN_LIMIT);
      }
      return count < mFlags.getMeasurementMaxDistinctEnrollmentsInAttribution();
    } else {
      logger.info(
          String.format(
              "isEnrollmentWithinPrivacyBounds: "
                  + "getPublisherAndDestinationTopPrivateDomains failed. %s %s",
              source.getPublisher(), trigger.getAttributionDestination()));
      return true;
    }
  }

  private static Optional<Map<URI, URI>> getPublisherAndDestinationTopPrivateDomains(
      Source source, Trigger trigger) {
    URI attributionDestination = trigger.getAttributionDestination();
    Optional<URI> triggerDestinationTopPrivateDomain =
        trigger.getDestinationType() == EventSurfaceType.APP
            ? Optional.of(BaseUriExtractor.getBaseUri(attributionDestination))
            : Web.topPrivateDomainAndScheme(attributionDestination);
    URI publisher = source.getPublisher();
    Optional<URI> publisherTopPrivateDomain =
        source.getPublisherType() == EventSurfaceType.APP
            ? Optional.of(publisher)
            : Web.topPrivateDomainAndScheme(publisher);
    if (!triggerDestinationTopPrivateDomain.isPresent() || !publisherTopPrivateDomain.isPresent()) {
      return Optional.empty();
    } else {
      return Optional.of(
          Map.of(publisherTopPrivateDomain.get(), triggerDestinationTopPrivateDomain.get()));
    }
  }

  public Attribution createAttribution(Source source, Trigger trigger) {
    Optional<URI> publisherTopPrivateDomain =
        getTopPrivateDomain(source.getPublisher(), source.getPublisherType());
    URI destination = trigger.getAttributionDestination();
    Optional<URI> destinationTopPrivateDomain =
        getTopPrivateDomain(destination, trigger.getDestinationType());
    if (!publisherTopPrivateDomain.isPresent() || !destinationTopPrivateDomain.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "createAttribution: getSourceAndTriggerTopPrivateDomains failed. Publisher: %s; "
                  + "Attribution destination: %s",
              source.getPublisher(), destination));
    }

    return new Attribution.Builder()
        .setSourceSite(publisherTopPrivateDomain.get().toString())
        .setSourceOrigin(source.getPublisher().toString())
        .setDestinationSite(destinationTopPrivateDomain.get().toString())
        .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
        .setEnrollmentId(trigger.getEnrollmentId())
        .setTriggerTime(trigger.getTriggerTime())
        .setRegistrant(trigger.getRegistrant().toString())
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .build();
  }

  private static Optional<URI> getTopPrivateDomain(URI uri, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(uri))
        : Web.topPrivateDomainAndScheme(uri);
  }
}
