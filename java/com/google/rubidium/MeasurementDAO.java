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

import static com.google.rubidium.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_WINDOW_MS;

import com.google.rubidium.Trigger.Status;
import com.google.rubidium.aggregation.AggregateReport;
import com.google.rubidium.util.BaseUriExtractor;
import com.google.rubidium.util.Web;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Data Access Object for Measurement PPAPI module */
public class MeasurementDAO implements IMeasurementDAO {
  private static final Logger logger = Logger.getLogger(MeasurementDAO.class.getName());
  private final DatastoreManager mDatastoreManager;

  public MeasurementDAO(List<Source> sources, List<Trigger> triggers) {
    mDatastoreManager = new DatastoreManager(sources, triggers);
  }

  /**
   * @return List of Ids of Triggers whose status is Pending. This is used instead of
   *     getPendingTriggerIds() in AdServices
   */
  @Override
  public List<Trigger> getPendingTriggers() {
    return mDatastoreManager.getTriggers().stream()
        .filter((t) -> t.getStatus().equals(Status.PENDING)) // Filter only Pending triggers
        .collect(Collectors.toCollection(ArrayList::new)); // Collect ids into a List
  }

  @Override
  public List<Source> getMatchingActiveSources(Trigger trigger) {
    Optional<Map<String, String>> destinationColumnAndValue = getDestinationColumnAndValue(trigger);
    if (!destinationColumnAndValue.isPresent()) {
      logger.info(
          String.format(
              "getMatchingActiveSources: unable to obtain destination column and value: %s",
              trigger.getAttributionDestination().toString()));
      return new ArrayList();
    }

    String sourceDestinationColumn = destinationColumnAndValue.get().keySet().iterator().next();
    String triggerDestinationValue = destinationColumnAndValue.get().get(sourceDestinationColumn);

    return mDatastoreManager.getSources().stream()
        .filter(
            s ->
                (sourceDestinationColumn.equals("app_destination")
                    ? s.getAppDestination().toString().equals(triggerDestinationValue)
                    : s.getWebDestination().toString().equals(triggerDestinationValue)))
        .filter(s -> s.getEnrollmentId().equals(trigger.getEnrollmentId()))
        .filter(s -> s.getEventTime() < trigger.getTriggerTime())
        .filter(s -> s.getExpiryTime() >= trigger.getTriggerTime())
        .filter(s -> !s.getStatus().equals(Source.Status.IGNORED))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<EventReport> getSourceEventReports(Source source) {
    return mDatastoreManager.getEventReports().stream()
        .filter(r -> r.getSourceId() == source.getEventId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime) {
    return mDatastoreManager.getEventReports().stream()
        .filter(e -> e.getReportTime() >= windowStartTime)
        .filter(e -> e.getReportTime() <= windowEndTime)
        .filter(e -> e.getStatus() == EventReport.Status.PENDING)
        .map(e -> e.getId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  /**
   * Return Event Reports where the Event Report has a matching Source (on EventReport's Source ID
   * == Source's EventID), and the EventReport's status is PENDING and Source's registrant == the
   * appName parameter
   */
  public List<String> getPendingEventReportIdsForGivenApp(String appName) {
    return mDatastoreManager.getEventReports().stream()
        .filter(
            e ->
                mDatastoreManager.getSources().stream()
                    .anyMatch(
                        s ->
                            s.getEventId() == e.getSourceId() // Source and EventReport Id match
                                && s.getRegistrant()
                                    .toString()
                                    .equals(appName)) // Source's registrant == appName
            )
        .filter(e -> e.getStatus() == EventReport.Status.PENDING) // EventReport's status == Pending
        .map(e -> e.getId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public long getAttributionsPerRateLimitWindow(Source source, Trigger trigger) {
    Optional<URI> publisherBaseUri =
        extractBaseUri(source.getPublisher(), source.getPublisherType());
    Optional<URI> destinationBaseUri =
        extractBaseUri(trigger.getAttributionDestination(), trigger.getDestinationType());
    if (!publisherBaseUri.isPresent() || !destinationBaseUri.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "getAttributionsPerRateLimitWindow: extractBaseUri "
                  + "failed. Publisher: %s; Attribution destination: %s",
              source.getPublisher().toString(), trigger.getAttributionDestination().toString()));
    }
    String publisherTopPrivateDomain = publisherBaseUri.get().toString();
    String triggerDestinationTopPrivateDomain = destinationBaseUri.get().toString();

    return mDatastoreManager.getAttributions().stream()
        .filter(attribution -> attribution.getSourceSite().equals(publisherTopPrivateDomain))
        .filter(
            attribution ->
                attribution.getDestinationSite().equals(triggerDestinationTopPrivateDomain))
        .filter(attribution -> attribution.getEnrollmentId().equals(trigger.getEnrollmentId()))
        .filter(
            attribution ->
                attribution.getTriggerTime()
                    >= (trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS))
        .filter(attribution -> attribution.getTriggerTime() <= trigger.getTriggerTime())
        .count();
  }

  public long getNumSourcesPerRegistrant(URI registrant) {
    return mDatastoreManager.getSources().stream()
        .filter(s -> s.getRegistrant().equals(registrant))
        .count();
  }

  public long getNumTriggersPerRegistrant(URI registrant) {
    return mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getRegistrant().equals(registrant))
        .count();
  }

  @Override
  public void doInstallAttribution(URI uri, long eventTimestamp) {
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getAppDestination().equals(uri))
        .filter(s -> s.getEventTime() <= eventTimestamp)
        .filter((s -> s.getExpiryTime() > eventTimestamp))
        .filter((s -> s.getEventTime() + s.getInstallAttributionWindow() >= eventTimestamp))
        .sorted(
            Comparator.comparing(Source::getPriority)
                .thenComparing(Source::getEventTime)
                .reversed()) // Descending order
        .findFirst()
        .ifPresent(s -> s.setInstallAttributed(true));
  }

  @Override
  public void undoInstallAttribution(URI uri) {
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getAppDestination().equals(uri))
        .forEach(s -> s.setInstallAttributed(false));
  }

  @Override
  /**
   * Find Sources whose registrant field matches the parameter String uri, then delete any
   * EventReports whose sourceId matches the Source's id and vice versa with the Source's eventId.
   *
   * <p>Also delete: EventReports whose attributionDestination == uri Source whose registrant ==
   * uri, OR (status == IGNORED AND attributionDestination == uri) Trigger whose registrant == uri
   * AttributionRateLimit whose sourceSite == uri OR destinationSite == uri
   */
  public void deleteAppRecords(String uri) {
    final List<EventReport> reportsToRemove = new ArrayList<>();
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getRegistrant().toString().equals(uri))
        .forEach(
            s -> {
              mDatastoreManager.getEventReports().stream()
                  .filter(e -> e.getSourceId() == s.getEventId()) // matches with source
                  .forEach(e -> reportsToRemove.add(e));
            });
    mDatastoreManager.getEventReports().removeAll(reportsToRemove);

    reportsToRemove.clear();
    mDatastoreManager.getEventReports().stream()
        .filter(e -> e.getAttributionDestination().toString().equals(uri))
        .forEach(e -> reportsToRemove.add(e));
    mDatastoreManager.getEventReports().removeAll(reportsToRemove);

    final List<Source> sourcesToRemove = new ArrayList<>();
    mDatastoreManager.getSources().stream()
        .filter(
            s ->
                s.getRegistrant().toString().equals(uri)
                    || (s.getStatus() == Source.Status.IGNORED
                        && s.getAppDestination().toString().equals(uri)))
        .forEach(s -> sourcesToRemove.add(s));
    mDatastoreManager.getSources().removeAll(sourcesToRemove);

    final List<Trigger> triggersToRemove = new ArrayList<>();
    mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getRegistrant().toString().equals(uri))
        .forEach(t -> triggersToRemove.add(t));
    mDatastoreManager.getTriggers().removeAll(triggersToRemove);

    final List<Attribution> arlToRemove = new ArrayList<>();
    mDatastoreManager.getAttributions().stream()
        .filter(a -> a.getSourceSite().equals(uri) || a.getDestinationSite().equals(uri))
        .forEach(a -> arlToRemove.add(a));
    mDatastoreManager.getAttributions().removeAll(arlToRemove);
  }

  @Override
  public int countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
      URI sourceSite,
      URI destinationSite,
      String excludedEnrollmentId,
      long windowStartTime,
      long windowEndTime) {
    return (int)
        mDatastoreManager.getAttributions().stream()
            .filter(a -> a.getSourceSite().equals(sourceSite.toString()))
            .filter(a -> a.getDestinationSite().equals(destinationSite.toString()))
            .filter(a -> !a.getEnrollmentId().equals(excludedEnrollmentId))
            .filter(a -> a.getTriggerTime() < windowEndTime)
            .filter(a -> a.getTriggerTime() >= windowStartTime)
            .distinct()
            .count();
  }

  @Override
  public void deleteExpiredRecords() {
    long earliestValidInsertion = System.currentTimeMillis() - MEASUREMENT_DELETE_EXPIRED_WINDOW_MS;

    List<Source> sourcesToRemove = new ArrayList<>();
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getEventTime() < earliestValidInsertion)
        .forEach(s -> sourcesToRemove.add(s));
    mDatastoreManager.getSources().removeAll(sourcesToRemove);

    List<Trigger> triggersToRemove = new ArrayList<>();
    mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getTriggerTime() < earliestValidInsertion)
        .forEach(t -> triggersToRemove.add(t));
    mDatastoreManager.getTriggers().removeAll(triggersToRemove);

    List<EventReport> eventReportsToRemove = new ArrayList<>();
    mDatastoreManager.getEventReports().stream()
        .filter(
            e ->
                e.getStatus() == EventReport.Status.DELIVERED
                    || e.getReportTime() < earliestValidInsertion)
        .forEach(e -> eventReportsToRemove.add(e));
    mDatastoreManager.getEventReports().removeAll(eventReportsToRemove);

    List<Attribution> attributionRateLimitsToRemove = new ArrayList<>();
    mDatastoreManager.getAttributions().stream()
        .filter(a -> a.getTriggerTime() < earliestValidInsertion)
        .forEach(a -> attributionRateLimitsToRemove.add(a));
    mDatastoreManager.getAttributions().removeAll(attributionRateLimitsToRemove);
  }

  @Override
  public void deleteMeasurementData(String registrant, String origin, Instant start, Instant end) {
    Objects.requireNonNull(registrant);
    validateOptionalRange(start, end);

    if (origin == null && start == null) { // Delete by registrant
      deleteMeasurementDataByRegistrant(registrant);
    } else if (start == null) { // Delete by Registrant and URI
      deleteMeasurementDataByRegistrantAndUri(registrant, origin);
    } else if (origin == null) { // Delete by Registrant and time Range
      deleteMeasurementDataByRegistrantAndTimeRange(registrant, start, end);
    } else { // Delete by Registrant, URI, and time Range
      deleteMeasurementDataByAllVars(registrant, origin, start, end);
    }
  }

  private void deleteMeasurementDataByRegistrant(String registrant) {
    mDatastoreManager.removeAttributionRateLimitsByFilter(
        a -> a.getRegistrant().equals(registrant));
    mDatastoreManager.removeEventReportsByFilter(
        e -> mDatastoreManager.getEventReportsByRegistrant(registrant).contains(e));
    mDatastoreManager.removeTriggersByFilter(t -> t.getRegistrant().toString().equals(registrant));
    mDatastoreManager.removeSourcesByFilter(s -> s.getRegistrant().toString().equals(registrant));
  }

  private void deleteMeasurementDataByRegistrantAndUri(String registrant, String origin) {
    mDatastoreManager.removeAttributionRateLimitsByFilter(
        a -> a.getRegistrant().equals(registrant),
        a -> a.getSourceSite().equals(origin) || a.getDestinationSite().equals(origin));
    mDatastoreManager.removeEventReportsByFilter(
        e ->
            mDatastoreManager.getSources().stream()
                .anyMatch(
                    s ->
                        e.getSourceId() == s.getEventId()
                            && s.getRegistrant().toString().equals(registrant)
                            && ((s.getPublisher().toString().equals(origin))
                                || (e.getAttributionDestination().toString().equals(origin)))));
    mDatastoreManager.removeTriggersByFilter(
        t -> t.getRegistrant().toString().equals(registrant),
        t -> t.getAttributionDestination().toString().equals(origin));
    mDatastoreManager.removeSourcesByFilter(
        s -> s.getRegistrant().toString().equals(registrant),
        s -> s.getPublisher().toString().equals(origin));
  }

  private void deleteMeasurementDataByRegistrantAndTimeRange(
      String registrant, Instant start, Instant end) {
    mDatastoreManager.removeAttributionRateLimitsByFilter(
        a -> a.getRegistrant().equals(registrant),
        a -> a.getTriggerTime() >= start.toEpochMilli(),
        a -> a.getTriggerTime() <= end.toEpochMilli());
    mDatastoreManager.removeEventReportsByFilter(
        e ->
            mDatastoreManager.getSources().stream()
                .anyMatch(
                    s ->
                        e.getSourceId() == s.getEventId()
                            && s.getRegistrant().toString().equals(registrant)
                            && ((s.getEventTime() >= start.toEpochMilli()
                                    && s.getEventTime() <= end.toEpochMilli())
                                || (e.getTriggerTime() >= start.toEpochMilli()
                                    && e.getTriggerTime() <= end.toEpochMilli()))));
    mDatastoreManager.removeTriggersByFilter(
        t -> t.getRegistrant().toString().equals(registrant),
        t -> t.getTriggerTime() >= start.toEpochMilli(),
        t -> t.getTriggerTime() <= end.toEpochMilli());
    mDatastoreManager.removeSourcesByFilter(
        s -> s.getRegistrant().toString().equals(registrant),
        s -> s.getEventTime() >= start.toEpochMilli(),
        s -> s.getEventTime() <= end.toEpochMilli());
  }

  private void deleteMeasurementDataByAllVars(
      String registrant, String origin, Instant start, Instant end) {
    mDatastoreManager.removeAttributionRateLimitsByFilter(
        a -> a.getRegistrant().equals(registrant),
        a -> a.getSourceSite().equals(origin) || a.getDestinationSite().equals(origin),
        a -> a.getTriggerTime() >= start.toEpochMilli(),
        a -> a.getTriggerTime() <= end.toEpochMilli());
    mDatastoreManager.removeEventReportsByFilter(
        e ->
            mDatastoreManager.getSources().stream()
                .anyMatch(
                    s ->
                        e.getSourceId() == s.getEventId()
                            && s.getRegistrant().toString().equals(registrant)
                            && ((s.getPublisher().toString().equals(origin)
                                    && s.getEventTime() >= start.toEpochMilli()
                                    && s.getEventTime() <= end.toEpochMilli())
                                || (e.getAttributionDestination().toString().equals(origin)
                                    && e.getTriggerTime() >= start.toEpochMilli()
                                    && e.getTriggerTime() <= end.toEpochMilli()))));
    mDatastoreManager.removeTriggersByFilter(
        t -> t.getRegistrant().toString().equals(registrant),
        t -> t.getAttributionDestination().toString().equals(origin),
        t -> t.getTriggerTime() >= start.toEpochMilli(),
        t -> t.getTriggerTime() <= end.toEpochMilli());
    mDatastoreManager.removeSourcesByFilter(
        s -> s.getRegistrant().toString().equals(registrant),
        s -> s.getPublisher().toString().equals(origin),
        s -> s.getEventTime() >= start.toEpochMilli(),
        s -> s.getEventTime() <= end.toEpochMilli());
  }

  private void validateOptionalRange(Instant start, Instant end) {
    if (start == null ^ end == null)
      throw new IllegalArgumentException(
          "invalid range, both start and end dates must be provided if providing any");
    if (start != null && start.isAfter(end))
      throw new IllegalArgumentException(
          "invalid range, start date must be equal or before end date");
  }

  @Override
  public List<String> getPendingAggregateReportIdsInWindow(
      long windowStartTime, long windowEndTime) {
    return mDatastoreManager.getAggregateReports().stream()
        .filter(c -> c.getScheduledReportTime() >= windowStartTime)
        .filter(c -> c.getScheduledReportTime() <= windowEndTime)
        .filter(c -> c.getStatus() == AggregateReport.Status.PENDING)
        .map(c -> c.getId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<String> getPendingAggregateReportIdsForGivenApp(String appName) {
    return mDatastoreManager.getAggregateReports().stream()
        .filter(c -> c.getPublisher().toString().equals(appName))
        .filter(c -> c.getStatus() == AggregateReport.Status.PENDING)
        .map(c -> c.getId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static Optional<Map<String, String>> getDestinationColumnAndValue(Trigger trigger) {
    if (trigger.getDestinationType() == EventSurfaceType.APP) {
      return Optional.of(Map.of("app_destination", trigger.getAttributionDestination().toString()));
    } else {
      Optional<URI> topPrivateDomainAndScheme =
          Web.topPrivateDomainAndScheme(trigger.getAttributionDestination());
      return topPrivateDomainAndScheme.map(uri -> Map.of("web_destination", uri.toString()));
    }
  }

  private static Optional<URI> extractBaseUri(URI uri, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(uri))
        : Web.topPrivateDomainAndScheme(uri);
  }

  @Override
  public Trigger getTrigger(String triggerId) {
    return mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getId().equals(triggerId))
        .findFirst()
        .orElse(null);
  }

  @Override
  public void deleteEventReport(EventReport report) {
    mDatastoreManager.removeEventReport(report);
  }

  @Override
  public void insertEventReport(EventReport report) {
    mDatastoreManager.insertEventReport(report);
  }

  @Override
  public void updateSourceStatus(List<Source> sources, Source.Status status) {
    mDatastoreManager.updateSourceStatus(sources, status);
  }

  @Override
  public void updateSourceAggregateContributions(Source source) {
    mDatastoreManager.updateSourceAggregateContributions(source);
  }

  @Override
  public void updateSourceDedupKeys(Source source) {
    mDatastoreManager.updateSourceDedupKeys(source);
  }

  @Override
  public void insertAttribution(Attribution attribution) {
    mDatastoreManager.insertAttribution(attribution);
  }

  @Override
  public List<AggregateReport> getAllAggregateReports() {
    return mDatastoreManager.getAggregateReports();
  }

  @Override
  public void insertAggregateReport(AggregateReport aggregateReport) {
    mDatastoreManager.getAggregateReports().add(aggregateReport);
  }

  @Override
  public List<EventReport> getAllEventReports() {
    return mDatastoreManager.getEventReports();
  }
}
