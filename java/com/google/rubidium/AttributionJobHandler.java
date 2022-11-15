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

package com.google.rubidium;

import com.google.rubidium.aggregation.AggregatableAttributionSource;
import com.google.rubidium.aggregation.AggregatableAttributionTrigger;
import com.google.rubidium.aggregation.AggregateAttributionData;
import com.google.rubidium.aggregation.AggregateFilterData;
import com.google.rubidium.aggregation.AggregateHistogramContribution;
import com.google.rubidium.aggregation.AggregatePayloadGenerator;
import com.google.rubidium.aggregation.AggregateReport;
import com.google.rubidium.util.BaseUriExtractor;
import com.google.rubidium.util.Filter;
import com.google.rubidium.util.Web;
import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AttributionJobHandler {
  private static final Logger logger = Logger.getLogger(AttributionJobHandler.class.getName());
  private final IMeasurementDAO measurementDAO;
  private static final String API_VERSION = "0.1";
  private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
  private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);

  AttributionJobHandler(IMeasurementDAO measurementDAO) {
    this.measurementDAO = measurementDAO;
  }

  synchronized void performPendingAttributions() {
    List<Trigger> pendingTriggers = measurementDAO.getPendingTriggers();
    for (int i = 0; i < pendingTriggers.size(); i++) {
      performAttribution(pendingTriggers.get(i));
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

    Optional<Source> sourceOpt = getMatchingSource(trigger, measurementDAO);
    if (!sourceOpt.isPresent()) {
      ignoreTrigger(trigger, measurementDAO);
      return;
    }
    Source source = sourceOpt.get();

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
      attributeTriggerAndInsertAttribution(trigger, source, measurementDAO);
    } else {
      ignoreTrigger(trigger, measurementDAO);
    }
  }

  private boolean maybeGenerateAggregateReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDAO) {
    try {
      Optional<AggregatableAttributionSource> aggregateAttributionSource =
          source.parseAggregateSource();
      Optional<AggregatableAttributionTrigger> aggregateAttributionTrigger =
          trigger.parseAggregateTrigger();

      if (aggregateAttributionSource.isPresent() && aggregateAttributionTrigger.isPresent()) {
        Optional<List<AggregateHistogramContribution>> contributions =
            AggregatePayloadGenerator.generateAttributionReport(
                aggregateAttributionSource.get(), aggregateAttributionTrigger.get());
        if (contributions.isPresent()) {

          OptionalInt newAggregateContributions =
              validateAndGetUpdatedAggregateContributions(contributions.get(), source);
          if (newAggregateContributions.isPresent()) {
            source.setAggregateContributions(newAggregateContributions.getAsInt());
          } else {
            logger.info(
                String.format(
                    "Aggregate contributions exceeded bound. " + "Source ID: %s ; Trigger ID: %s ",
                    source.getId(), trigger.getId()));
            return false;
          }
          long randomTime = (long) ((Math.random() * (MAX_TIME_MS - MIN_TIME_MS)) + MIN_TIME_MS);

          AggregateReport aggregateReport =
              new AggregateReport.Builder()
                  .setId(UUID.randomUUID().toString())
                  .setPublisher(source.getRegistrant())
                  .setAttributionDestination(trigger.getAttributionDestination())
                  .setSourceRegistrationTime(roundDownToDay(source.getEventTime()))
                  .setScheduledReportTime(trigger.getTriggerTime() + randomTime)
                  .setEnrollmentId(source.getEnrollmentId())
                  .setDebugCleartextPayload(
                      AggregateReport.generateDebugPayload(contributions.get()))
                  .setAggregateAttributionData(
                      new AggregateAttributionData.Builder()
                          .setContributions(contributions.get())
                          .build())
                  .setStatus(AggregateReport.Status.PENDING)
                  .setApiVersion(API_VERSION)
                  .build();

          measurementDAO.updateSourceAggregateContributions(source);
          measurementDAO.insertAggregateReport(aggregateReport);
          return true;
        }
      }
    } catch (ParseException e) {
      logger.severe("ParseException when parsing aggregate fields in AttributionJobHandler");
      return false;
    }
    return false;
  }

  private Optional<Source> getMatchingSource(Trigger trigger, IMeasurementDAO measurementDao) {
    List<Source> matchingSources = measurementDao.getMatchingActiveSources(trigger);

    if (matchingSources.isEmpty()) {
      return Optional.empty();
    }

    matchingSources.sort(
        Comparator.comparing(
                (Source source) ->
                    // Is a valid install-attributed source.
                    source.isInstallAttributed() && isWithinInstallCooldownWindow(source, trigger),
                Comparator.reverseOrder())
            .thenComparing(Source::getPriority, Comparator.reverseOrder())
            .thenComparing(Source::getEventTime, Comparator.reverseOrder()));

    Source selectedSource = matchingSources.get(0);
    matchingSources.remove(0);
    if (!matchingSources.isEmpty()) {
      matchingSources.forEach((s) -> s.setStatus(Source.Status.IGNORED));
      measurementDao.updateSourceStatus(matchingSources, Source.Status.IGNORED);
    }
    return Optional.of(selectedSource);
  }

  private boolean maybeGenerateEventReport(
      Source source, Trigger trigger, IMeasurementDAO measurementDao) {
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
        && source.getDedupKeys().contains(eventTrigger.getDedupKey())) {
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
      source.getDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
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
      source.getDedupKeys().add(eventTrigger.getDedupKey());
    }
    measurementDao.updateSourceDedupKeys(source);
    measurementDao.insertEventReport(eventReport);
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
    String triggerFilters = trigger.getFilters();
    String sourceFilters = source.getAggregateFilterData();
    if (triggerFilters == null
        || sourceFilters == null
        || triggerFilters.isEmpty()
        || sourceFilters.isEmpty()) {
      // Nothing to match
      return true;
    }

    try {
      AggregateFilterData sourceFiltersData = extractFilterMap(sourceFilters);
      AggregateFilterData triggerFiltersData = extractFilterMap(triggerFilters);
      return FilterUtil.isFilterMatch(sourceFiltersData, triggerFiltersData, true);
    } catch (ParseException e) {
      return false;
    }
  }

  private Optional<EventTrigger> findFirstMatchingEventTrigger(Source source, Trigger trigger) {
    try {
      String sourceFilters = source.getAggregateFilterData();
      AggregateFilterData sourceFiltersData;
      if (sourceFilters == null || sourceFilters.isEmpty()) {
        // Initialize an empty map to add source_type to it later
        sourceFiltersData = new AggregateFilterData.Builder().build();
      } else {
        sourceFiltersData = extractFilterMap(sourceFilters);
      }
      // Add source type
      appendToAggregateFilterData(
          sourceFiltersData,
          "source_type",
          Collections.singletonList(source.getSourceType().getValue()));
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

  private boolean doEventLevelFiltersMatch(
      AggregateFilterData sourceFiltersData, EventTrigger eventTrigger) {
    if (eventTrigger.getFilterData().isPresent()
        && !Filter.isFilterMatch(sourceFiltersData, eventTrigger.getFilterData().get(), true)) {
      return false;
    }
    if (eventTrigger.getNotFilterData().isPresent()
        && !Filter.isFilterMatch(sourceFiltersData, eventTrigger.getNotFilterData().get(), false)) {
      return false;
    }
    return true;
  }

  private AggregateFilterData extractFilterMap(String object) throws ParseException {
    JSONParser parser = new JSONParser();
    JSONObject sourceFilterObject = (JSONObject) parser.parse(object);
    return new AggregateFilterData.Builder().buildAggregateFilterData(sourceFilterObject).build();
  }

  private void appendToAggregateFilterData(
      AggregateFilterData filterData, String key, List<String> value) {
    Map<String, List<String>> attributeFilterMap = filterData.getAttributionFilterMap();
    attributeFilterMap.put(key, value);
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

  private static long roundDownToDay(long timestamp) {
    return Math.floorDiv(timestamp, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
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
      return count < PrivacyParams.MAX_DISTINCT_AD_TECHS_PER_PUBLISHER_X_DESTINATION_IN_ATTRIBUTION;
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
    Optional<URI> publisherBaseURI =
        extractBaseURI(source.getPublisher(), source.getPublisherType());
    URI destination = trigger.getAttributionDestination();
    Optional<URI> destinationBaseURI = extractBaseURI(destination, trigger.getDestinationType());
    if (!publisherBaseURI.isPresent() || !destinationBaseURI.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "createAttribution: extractBaseURI failed. Publisher: %s; "
                  + "Attribution destination: %s",
              source.getPublisher(), destination));
    }
    String publisherTopPrivateDomain = publisherBaseURI.get().toString();
    String triggerDestinationTopPrivateDomain = destinationBaseURI.get().toString();
    return new Attribution.Builder()
        .setSourceSite(publisherTopPrivateDomain)
        .setSourceOrigin(BaseUriExtractor.getBaseUri(source.getPublisher()).toString())
        .setDestinationSite(triggerDestinationTopPrivateDomain)
        .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
        .setEnrollmentId(trigger.getEnrollmentId())
        .setTriggerTime(trigger.getTriggerTime())
        .setRegistrant(trigger.getRegistrant().toString())
        .build();
  }

  private static Optional<URI> extractBaseURI(URI URI, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(URI))
        : Web.topPrivateDomainAndScheme(URI);
  }
}
