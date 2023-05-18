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

import com.google.measurement.aggregation.AggregatableAttributionSource;
import com.google.measurement.aggregation.AggregatableAttributionTrigger;
import com.google.measurement.aggregation.AggregateAttributionData;
import com.google.measurement.aggregation.AggregateDeduplicationKey;
import com.google.measurement.aggregation.AggregateHistogramContribution;
import com.google.measurement.aggregation.AggregatePayloadGenerator;
import com.google.measurement.aggregation.AggregateReport;
import com.google.measurement.util.BaseUriExtractor;
import com.google.measurement.util.Filter;
import com.google.measurement.util.Util;
import com.google.measurement.util.Web;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
  private static final String API_VERSION = "0.1";

  AttributionJobHandler(IMeasurementDAO measurementDAO) {
    this.measurementDAO = measurementDAO;
  }

  synchronized void performPendingAttributions() throws ParseException {
    List<Trigger> pendingTriggers = measurementDAO.getPendingTriggers();
    for (int i = 0; i < pendingTriggers.size(); i++) {
      performAttribution(pendingTriggers.get(i));
    }
  }

  /**
   * Perform attribution for {@code trigger}.
   *
   * @param trigger to perform attribution for {@link Trigger}
   * @throws ParseException if JSON parsing fails
   */
  private void performAttribution(Trigger trigger) throws ParseException {
    if (trigger.getStatus() != Trigger.Status.PENDING) {
      return;
    }

    Optional<Map<Source, List<Source>>> sourceOpt =
        selectSourceToAttribute(trigger, measurementDAO);
    if (sourceOpt.isEmpty()) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    Source source = sourceOpt.get().keySet().iterator().next();
    List<Source> remainingMatchingSources = sourceOpt.get().get(source);

    if (!doTopLevelFiltersMatch(source, trigger)) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    if (!hasAttributionQuota(source, trigger, measurementDAO)
        || !isEnrollmentWithinPrivacyBounds(source, trigger, measurementDAO)) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }

    boolean aggregateReportGenerated =
        maybeGenerateAggregateReport(source, trigger, measurementDAO);

    boolean eventReportGenerated = maybeGenerateEventReport(source, trigger, measurementDAO);

    if (eventReportGenerated || aggregateReportGenerated) {
      ignoreCompetingSources(measurementDAO, remainingMatchingSources, trigger.getEnrollmentId());
      attributeTriggerAndInsertAttribution(trigger, source, measurementDAO);
    } else {
      ignoreTrigger(trigger, measurementDAO);
    }
  }

  private boolean maybeGenerateAggregateReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) throws ParseException {
    if (trigger.getTriggerTime() > source.getAggregatableReportWindow()) {
      return false;
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
      return false;
    }

    try {
      Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
          maybeGetAggregateDeduplicationKey(source, trigger);
      if (aggregateDeduplicationKey.isPresent()
          && source
              .getAggregateReportDedupKeys()
              .contains(aggregateDeduplicationKey.get().getDeduplicationKey())) {
        return false;
      }

      Optional<List<AggregateHistogramContribution>> contributions =
          AggregatePayloadGenerator.generateAttributionReport(source, trigger);
      if (!contributions.isPresent()) {
        return false;
      }

      OptionalInt newAggregateContributions =
          validateAndGetUpdatedAggregateContributions(contributions.get(), source);
      if (!newAggregateContributions.isPresent()) {
        logger.info(
            String.format(
                "Aggregate contributions exceeded bound. Source ID: %s ; Trigger ID: %s ",
                source.getId(), trigger.getId()));
        return false;
      }

      source.setAggregateContributions(newAggregateContributions.getAsInt());

      long randomTime =
          (long)
              ((Math.random() * (AGGREGATE_MAX_REPORT_DELAY - AGGREGATE_MIN_REPORT_DELAY))
                  + AGGREGATE_MIN_REPORT_DELAY);

      AggregateReport aggregateReport =
          new AggregateReport.Builder()
              .setId(UUID.randomUUID().toString())
              .setPublisher(source.getRegistrant())
              .setAttributionDestination(trigger.getAttributionDestination())
              .setSourceRegistrationTime(Util.roundDownToDay(source.getEventTime()))
              .setScheduledReportTime(trigger.getTriggerTime() + randomTime)
              .setEnrollmentId(source.getEnrollmentId())
              .setDebugCleartextPayload(AggregateReport.generateDebugPayload(contributions.get()))
              .setAggregateAttributionData(
                  new AggregateAttributionData.Builder()
                      .setContributions(contributions.get())
                      .build())
              .setStatus(AggregateReport.Status.PENDING)
              .setApiVersion(API_VERSION)
              .setSourceId(source.getId())
              .setTriggerId(trigger.getId())
              .build();

      finalizeAggregateReportCreation(
          source, aggregateDeduplicationKey, aggregateReport, measurementDAO);
      return true;
    } catch (ParseException e) {
      logger.severe("ParseException when parsing aggregate fields in AttributionJobHandler");
      return false;
    }
  }

  private Optional<Map<Source, List<Source>>> selectSourceToAttribute(
      Trigger trigger, IMeasurementDAO measurementDao) {
    List<Source> matchingSources = measurementDao.getMatchingActiveSources(trigger);

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
      IMeasurementDAO measurementDao,
      List<Source> remainingMatchingSources,
      String triggerEnrollmentId) {

    if (!remainingMatchingSources.isEmpty()) {
      List<Source> ignoredOriginalSourceIds = new ArrayList<>();
      for (Source source : remainingMatchingSources) {
        source.setStatus(Source.Status.IGNORED);
        ignoredOriginalSourceIds.add(source);
      }
      measurementDao.updateSourceStatus(ignoredOriginalSourceIds, Source.Status.IGNORED);
    }
  }

  private boolean maybeGenerateEventReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDao) {

    if (trigger.getTriggerTime() > source.getEventReportWindow()) {
      return false;
    }
    int numReports =
        measurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType());
    if (numReports >= SystemHealthParams.MAX_EVENT_REPORTS_PER_DESTINATION) {
      logger.info(
          String.format(
              "Event reports for destination %1$s exceeds system health limit of %2$d.",
              trigger.getAttributionDestination(),
              SystemHealthParams.MAX_EVENT_REPORTS_PER_DESTINATION));
      return false;
    }

    // Do not generate event reports for source which have attributionMode != Truthfully.
    if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
      return false;
    }

    Optional<EventTrigger> matchingEventTrigger = findFirstMatchingEventTrigger(source, trigger);
    if (!matchingEventTrigger.isPresent()) {
      return false;
    }
    EventTrigger eventTrigger = matchingEventTrigger.get();
    // Check if deduplication key clashes with existing reports.
    if (eventTrigger.getDedupKey() != null
        && source.getEventReportDedupKeys().contains(eventTrigger.getDedupKey())) {
      return false;
    }

    EventReport newEventReport =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(source, trigger, eventTrigger)
            .build();
    if (!provisionEventReportQuota(source, trigger, newEventReport, measurementDao)) {
      return false;
    }
    finalizeEventReportCreation(source, eventTrigger, newEventReport, measurementDao);
    return true;
  }

  private boolean provisionEventReportQuota(
      Source source, Trigger trigger, EventReport newEventReport, IMeasurementDAO measurementDao) {
    List<EventReport> sourceEventReports = measurementDao.getSourceEventReports(source);
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
      return false;
    }
    EventReport lowestPriorityEventReport = relevantEventReports.get(0);
    if (lowestPriorityEventReport.getTriggerPriority() >= newEventReport.getTriggerPriority()) {
      return false;
    }
    if (lowestPriorityEventReport.getTriggerDedupKey() != null) {
      source.getEventReportDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
    }
    measurementDao.deleteEventReport(lowestPriorityEventReport);
    return true;
  }

  private void finalizeEventReportCreation(
      Source source,
      EventTrigger eventTrigger,
      EventReport eventReport,
      IMeasurementDAO measurementDao) {
    if (eventTrigger.getDedupKey() != null) {
      source.getEventReportDedupKeys().add(eventTrigger.getDedupKey());
    }
    measurementDao.updateSourceEventReportDedupKeys(source);
    measurementDao.insertEventReport(eventReport);
  }

  private void finalizeAggregateReportCreation(
      Source source,
      Optional<AggregateDeduplicationKey> aggregateDeduplicationKey,
      AggregateReport aggregateReport,
      IMeasurementDAO measurementDao) {
    if (aggregateDeduplicationKey.isPresent()) {
      source
          .getAggregateReportDedupKeys()
          .add(aggregateDeduplicationKey.get().getDeduplicationKey());
    }
    if (source.getParentId() == null) {
      // Only update aggregate contributions for an original source, not for a derived
      // source
      measurementDao.updateSourceAggregateContributions(source);
      measurementDao.updateSourceAggregateReportDedupKeys(source);
    }
    measurementDao.insertAggregateReport(aggregateReport);
  }

  private void attributeTriggerAndInsertAttribution(
      Trigger trigger, Source source, IMeasurementDAO measurementDao) {
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    measurementDao.insertAttribution(createAttribution(source, trigger));
  }

  private void ignoreTrigger(Trigger trigger, IMeasurementDAO measurementDao) {
    trigger.setStatus(Trigger.Status.IGNORED);
  }

  private boolean hasAttributionQuota(
      Source source, Trigger trigger, IMeasurementDAO measurementDao) {
    long attributionCount = measurementDao.getAttributionsPerRateLimitWindow(source, trigger);
    return attributionCount < PrivacyParams.MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
  }

  private boolean isWithinReportLimit(
      Source source, int existingReportCount, EventSurfaceType destinationType) {
    return source.getMaxReportCount(destinationType) > existingReportCount;
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
      return Filter.isFilterMatch(sourceFilters, triggerFilterSet, true)
          && Filter.isFilterMatch(sourceFilters, triggerNotFilterSet, false);
    } catch (ParseException e) {
      return false;
    }
  }

  private Optional<EventTrigger> findFirstMatchingEventTrigger(Source source, Trigger trigger) {
    try {
      FilterMap sourceFiltersData = source.getFilterData();
      List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
      return eventTriggers.stream()
          .filter(eventTrigger -> doEventLevelFiltersMatch(sourceFiltersData, eventTrigger))
          .findFirst();
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

  private static OptionalInt validateAndGetUpdatedAggregateContributions(
      List<AggregateHistogramContribution> contributions, Source source) {
    int newAggregateContributions = source.getAggregateContributions();
    for (AggregateHistogramContribution contribution : contributions) {
      try {
        newAggregateContributions =
            Math.addExact(newAggregateContributions, contribution.getValue());
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

  private static boolean isEnrollmentWithinPrivacyBounds(
      Source source, Trigger trigger, IMeasurementDAO measurementDao) {
    Optional<Map<URI, URI>> publisherAndDestination =
        getPublisherAndDestinationTopPrivateDomains(source, trigger);
    if (publisherAndDestination.isPresent()) {
      URI key = publisherAndDestination.get().keySet().iterator().next();
      int count =
          measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
              key,
              publisherAndDestination.get().get(key),
              trigger.getEnrollmentId(),
              trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS,
              trigger.getTriggerTime());
      return count
          < PrivacyParams.MAX_DISTINCT_ENROLLMENTS_PER_PUBLISHER_X_DESTINATION_IN_ATTRIBUTION;
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

  private static Optional<URI> getTopPrivateDomain(URI URI, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(URI))
        : Web.topPrivateDomainAndScheme(URI);
  }
}
