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

import static com.google.measurement.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_WINDOW_MS;

import com.google.measurement.Trigger.Status;
import com.google.measurement.aggregation.AggregateReport;
import com.google.measurement.util.BaseUriExtractor;
import com.google.measurement.util.Web;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Data Access Object for Measurement PPAPI module */
public class MeasurementDAO implements IMeasurementDAO {
  private static final Logger logger = Logger.getLogger(MeasurementDAO.class.getName());
  private final DatastoreManager mDatastoreManager;

  public MeasurementDAO() {
    mDatastoreManager = new DatastoreManager();
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
  public int getNumAggregateReportsPerDestination(
      URI attributionDestination, EventSurfaceType destinationType) {
    return getNumAggregateReportsPerDestinationInternal(attributionDestination, destinationType);
  }

  @Override
  public void insertSource(Source source) {
    this.mDatastoreManager.insertSource(source);
  }

  @Override
  public void insertTrigger(Trigger trigger) {
    this.mDatastoreManager.insertTrigger(trigger);
  }

  @Override
  public int getNumEventReportsPerDestination(
      URI attributionDestination, EventSurfaceType destinationType) {
    return getNumEventReportsPerDestinationInternal(attributionDestination, destinationType);
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
                    ? s.getAppDestinations() != null
                        && s.getAppDestinations().contains(URI.create(triggerDestinationValue))
                    : s.getWebDestinations() != null
                        && s.getWebDestinations().contains(URI.create(triggerDestinationValue))))
        .filter(s -> s.getEnrollmentId().equals(trigger.getEnrollmentId()))
        .filter(s -> s.getEventTime() <= trigger.getTriggerTime())
        .filter(s -> s.getExpiryTime() > trigger.getTriggerTime())
        .filter(s -> s.getStatus().equals(Source.Status.ACTIVE))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<EventReport> getSourceEventReports(Source source) {
    return mDatastoreManager.getEventReports().stream()
        .filter(r -> r.getSourceId() == source.getId())
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime) {
    return mDatastoreManager.getEventReports().stream()
        .filter(e -> e.getReportTime() >= windowStartTime)
        .filter(e -> e.getReportTime() <= windowEndTime)
        .filter(e -> e.getStatus() == EventReport.Status.PENDING)
        .map(EventReport::getId)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<String> getPendingDebugEventReportIds() {
    return mDatastoreManager.getEventReports().stream()
        .filter(e -> e.getDebugReportStatus() == EventReport.DebugReportStatus.PENDING)
        .map(EventReport::getId)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public Trigger getTrigger(String triggerId) {
    return mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getId().equals(triggerId))
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<String> getPendingEventReportIdsForGivenApp(String appName) {
    return mDatastoreManager.getEventReports().stream()
        .filter(
            e ->
                mDatastoreManager.getSources().stream()
                    .anyMatch(
                        s ->
                            Objects.equals(s.getId(), e.getSourceId())
                                // Source and EventReport Id match
                                && s.getRegistrant()
                                    .toString()
                                    .equals(appName)) // Source's registrant == appName
            )
        .filter(e -> e.getStatus() == EventReport.Status.PENDING) // EventReport's status == Pending
        .map(EventReport::getId)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public long getAttributionsPerRateLimitWindow(Source source, Trigger trigger) {
    Optional<URI> publisherBaseURI =
        extractBaseURI(source.getPublisher(), source.getPublisherType());
    Optional<URI> destinationBaseURI =
        extractBaseURI(trigger.getAttributionDestination(), trigger.getDestinationType());
    if (!publisherBaseURI.isPresent() || !destinationBaseURI.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "getAttributionsPerRateLimitWindow: extractBaseURI "
                  + "failed. Publisher: %s; Attribution destination: %s",
              source.getPublisher().toString(), trigger.getAttributionDestination().toString()));
    }
    String publisherTopPrivateDomain = publisherBaseURI.get().toString();
    String triggerDestinationTopPrivateDomain = destinationBaseURI.get().toString();

    return mDatastoreManager.getAttributions().stream()
        .filter(attribution -> attribution.getSourceSite().equals(publisherTopPrivateDomain))
        .filter(
            attribution ->
                attribution.getDestinationSite().equals(triggerDestinationTopPrivateDomain))
        .filter(attribution -> attribution.getEnrollmentId().equals(trigger.getEnrollmentId()))
        .filter(
            attribution ->
                attribution.getTriggerTime()
                    > (trigger.getTriggerTime() - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS))
        .filter(attribution -> attribution.getTriggerTime() <= trigger.getTriggerTime())
        .count();
  }

  public long getNumSourcesPerPublisher(URI publisher, EventSurfaceType publisherType) {
    if (publisherType == EventSurfaceType.APP) {
      return mDatastoreManager.getSources().stream()
          .filter(s -> s.getPublisher().equals(publisher))
          .count();
    } else {
      return mDatastoreManager.getSources().stream()
          .filter(
              s ->
                  (s.getPublisher().equals(publisher)
                      || s.getPublisher()
                          .toString()
                          .matches(publisher.getScheme() + "://%." + publisher.getAuthority())))
          .count();
    }
  }

  public long getNumTriggersPerRegistrant(URI registrant) {
    return mDatastoreManager.getTriggers().stream()
        .filter(t -> t.getRegistrant().equals(registrant))
        .count();
  }

  @Override
  public long getNumTriggersPerDestination(URI destination, EventSurfaceType destinationType) {
    Optional<URI> destinationBaseURIOptional = extractBaseURI(destination, destinationType);

    if (destinationBaseURIOptional.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "getNumTriggersPerDestination: Unable to extract base URI from %s",
              destination.toString()));
    }

    URI destinationBaseURI = destinationBaseURIOptional.get();

    if (destinationType == EventSurfaceType.APP) {
      return mDatastoreManager.getTriggers().stream()
          .filter(
              t ->
                  (t.getAttributionDestination().equals(destinationBaseURI)
                      || t.getAttributionDestination()
                          .toString()
                          .matches(destinationBaseURI.toString() + "/.*")))
          .count();
    } else {
      String schemeSubDomainMatcher =
          destination.getScheme() + "://.*." + destinationBaseURI.getAuthority();
      String schemeDomainMatcher =
          destination.getScheme() + "://" + destinationBaseURI.getAuthority();
      String domainAndPathMatcher = schemeDomainMatcher + "/.*";
      String subDomainAndPathMatcher = schemeSubDomainMatcher + "/.*";

      return mDatastoreManager.getTriggers().stream()
          .filter(
              t ->
                  (t.getAttributionDestination().toString().equals(schemeDomainMatcher)
                          || t.getAttributionDestination()
                              .toString()
                              .matches(schemeSubDomainMatcher)
                          || t.getAttributionDestination().toString().matches(domainAndPathMatcher))
                      || t.getAttributionDestination().toString().matches(subDomainAndPathMatcher))
          .count();
    }
  }

  @Override
  public Integer countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
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
            .filter(a -> a.getTriggerTime() <= windowEndTime)
            .filter(a -> a.getTriggerTime() > windowStartTime)
            .map(Attribution::getEnrollmentId)
            .distinct()
            .count();
  }

  @Override
  public Integer countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
      URI publisher,
      EventSurfaceType publisherType,
      String enrollmentId,
      List<URI> excludedDestinations,
      EventSurfaceType destinationType,
      long windowStartTime,
      long windowEndTime) {
    Stream<Source> sources =
        mDatastoreManager.getSources().stream()
            .filter(
                s ->
                    (publisherType == EventSurfaceType.APP
                        ? s.getPublisher().equals(publisher)
                        : (s.getPublisher().equals(publisher)
                            || s.getPublisher()
                                .toString()
                                .matches(
                                    publisher.getScheme() + "://.*." + publisher.getAuthority()))))
            .filter(s -> s.getEnrollmentId().equals(enrollmentId))
            .filter(s -> s.getStatus() == Source.Status.ACTIVE)
            .filter(
                s ->
                    (destinationType == EventSurfaceType.APP
                        ? !dataUriExists(s.getAppDestinations(), excludedDestinations)
                        : !dataUriExists(s.getWebDestinations(), excludedDestinations)))
            .filter(s -> s.getEventTime() > windowStartTime)
            .filter(s -> s.getEventTime() <= windowEndTime)
            .filter(s -> s.getExpiryTime() > windowEndTime);

    Stream<URI> destinations;
    if (destinationType == EventSurfaceType.APP) {
      destinations =
          sources
              .filter(s -> s.getAppDestinations() != null)
              .map(Source::getAppDestinations)
              .flatMap(dest -> dest.stream());
    } else {
      destinations =
          sources
              .filter(s -> s.getWebDestinations() != null)
              .map(Source::getWebDestinations)
              .flatMap(dest -> dest.stream());
    }

    return (int) destinations.distinct().count();
  }

  private boolean dataUriExists(List<URI> dataURIs, List<URI> domainURIs) {
    if (dataURIs == null || dataURIs.isEmpty()) {
      return false;
    }
    HashSet<String> domainURIsStr =
        new HashSet(domainURIs.stream().map(URI::toString).collect(Collectors.toSet()));
    for (URI dataURI : dataURIs) {
      if (domainURIsStr.contains(dataURI.toString())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Integer countDistinctEnrollmentsPerPublisherXDestinationInSource(
      URI publisher,
      EventSurfaceType publisherType,
      List<URI> destinations,
      String excludedEnrollmentId,
      long windowStartTime,
      long windowEndTime) {

    return (int)
        mDatastoreManager.getSources().stream()
            .filter(
                s ->
                    (publisherType == EventSurfaceType.APP
                        ? s.getPublisher().equals(publisher)
                        : (s.getPublisher().equals(publisher)
                            || s.getPublisher()
                                .toString()
                                .matches(
                                    publisher.getScheme() + "://%." + publisher.getAuthority()))))
            .filter(
                s ->
                    (dataUriExists(s.getAppDestinations(), destinations)
                        || dataUriExists(s.getWebDestinations(), destinations)))
            .filter(s -> (!s.getEnrollmentId().equals(excludedEnrollmentId)))
            .filter(s -> s.getEventTime() > windowStartTime)
            .filter(s -> s.getEventTime() <= windowEndTime)
            .filter(s -> s.getExpiryTime() > windowEndTime)
            .map(Source::getEnrollmentId)
            .distinct()
            .count();
  }

  @Override
  public long countDistinctDebugAdIdsUsedByEnrollment(String enrollmentId) {
    return Stream.concat(
            mDatastoreManager.getSources().stream()
                .filter(s -> s.getDebugAdId() != null)
                .filter(s -> s.getEnrollmentId().equals(enrollmentId))
                .filter(s -> s.getPublisherType() == EventSurfaceType.WEB)
                .map(Source::getDebugAdId),
            mDatastoreManager.getTriggers().stream()
                .filter(t -> t.getDebugAdId() != null)
                .filter(t -> t.getEnrollmentId().equals(enrollmentId))
                .filter(t -> t.getDestinationType() == EventSurfaceType.WEB)
                .map(Trigger::getDebugAdId))
        .distinct()
        .count();
  }

  @Override
  public void deleteAppRecords(String uri) {
    final List<EventReport> reportsToRemove = new ArrayList<>();
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getRegistrant().toString().equals(uri))
        .forEach(
            s -> {
              mDatastoreManager.getEventReports().stream()
                  .filter(e -> e.getSourceId().equals(s.getId())) // matches with source
                  .forEach(e -> reportsToRemove.add(e));
            });
    mDatastoreManager.getEventReports().removeAll(reportsToRemove);

    reportsToRemove.clear();
    mDatastoreManager.getEventReports().stream()
        .filter(e -> e.getAttributionDestinations().get(0).toString().equals(uri))
        .forEach(e -> reportsToRemove.add(e));
    mDatastoreManager.getEventReports().removeAll(reportsToRemove);

    final List<Source> sourcesToRemove = new ArrayList<>();
    mDatastoreManager.getSources().stream()
        .filter(
            s ->
                s.getRegistrant().toString().equals(uri)
                    || (s.getStatus() == Source.Status.IGNORED
                        && s.getAppDestinations().get(0).toString().equals(uri)))
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

  private int getNumAggregateReportsPerDestinationInternal(
      URI attributionDestination, EventSurfaceType destinationType) {
    Optional<URI> destinationBaseURI = extractBaseURI(attributionDestination, destinationType);
    if (!destinationBaseURI.isPresent()) {
      throw new IllegalStateException("extractBaseURI failed for destination.");
    }
    // Example: https://destination.com
    String noSubdomainOrPostfixMatch =
        destinationBaseURI.get().getScheme() + "://" + destinationBaseURI.get().getHost();

    // Example: https://subdomain.destination.com/path
    String subdomainAndPostfixMatch =
        destinationBaseURI.get().getScheme()
            + "://.*."
            + destinationBaseURI.get().getHost()
            + "/.*";
    // Example: https://subdomain.destination.com
    String subdomainMatch =
        destinationBaseURI.get().getScheme() + "://.*." + destinationBaseURI.get().getHost();
    // Example: https://destination.com/path
    String postfixMatch =
        destinationBaseURI.get().getScheme() + "://" + destinationBaseURI.get().getHost() + "/.*";
    if (destinationType == EventSurfaceType.WEB) {
      return (int)
          mDatastoreManager.getAggregateReports().stream()
              .filter(
                  r ->
                      (r.getAttributionDestination().toString().equals(noSubdomainOrPostfixMatch)
                          || r.getAttributionDestination()
                              .toString()
                              .matches(subdomainAndPostfixMatch)
                          || r.getAttributionDestination().toString().matches(subdomainMatch)
                          || r.getAttributionDestination().toString().matches(postfixMatch)))
              .count();
    } else {
      return (int)
          mDatastoreManager.getAggregateReports().stream()
              .filter(
                  r ->
                      (r.getAttributionDestination().toString().equals(noSubdomainOrPostfixMatch)
                          || r.getAttributionDestination().toString().matches(postfixMatch)))
              .count();
    }
  }

  private int getNumEventReportsPerDestinationInternal(
      URI attributionDestination, EventSurfaceType destinationType) {
    Optional<URI> destinationBaseURI = extractBaseURI(attributionDestination, destinationType);
    if (!destinationBaseURI.isPresent()) {
      throw new IllegalStateException("extractBaseURI failed for destination.");
    }
    // Example: https://destination.com
    String noSubdomainOrPostfixMatch =
        destinationBaseURI.get().getScheme() + "://" + destinationBaseURI.get().getHost();

    // Example: https://subdomain.destination.com/path
    String subdomainAndPostfixMatch =
        destinationBaseURI.get().getScheme()
            + "://.*."
            + destinationBaseURI.get().getHost()
            + "/.*";
    // Example: https://subdomain.destination.com
    String subdomainMatch =
        destinationBaseURI.get().getScheme() + "://.*." + destinationBaseURI.get().getHost();
    // Example: https://destination.com/path
    String postfixMatch =
        destinationBaseURI.get().getScheme() + "://" + destinationBaseURI.get().getHost() + "/.*";
    if (destinationType == EventSurfaceType.WEB) {
      return (int)
          mDatastoreManager.getEventReports().stream()
              .filter(
                  r ->
                      (r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .equals(noSubdomainOrPostfixMatch)
                          || r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .matches(subdomainAndPostfixMatch)
                          || r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .matches(subdomainMatch)
                          || r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .matches(postfixMatch)))
              .count();
    } else {
      return (int)
          mDatastoreManager.getEventReports().stream()
              .filter(
                  r ->
                      (r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .equals(noSubdomainOrPostfixMatch)
                          || r.getAttributionDestinations()
                              .get(0)
                              .toString()
                              .matches(postfixMatch)))
              .count();
    }
  }

  @Override
  public void doInstallAttribution(URI uri, long eventTimestamp) {
    Optional<Source> sourceOpt =
        mDatastoreManager.getSources().stream()
            .filter(s -> s.getAppDestinations().get(0).equals(uri))
            .filter(s -> s.getEventTime() <= eventTimestamp)
            .filter((s -> s.getExpiryTime() > eventTimestamp))
            .filter((s -> s.getEventTime() + s.getInstallAttributionWindow() >= eventTimestamp))
            .filter(s -> s.getStatus().equals(Source.Status.ACTIVE))
            .sorted(
                Comparator.comparing(Source::getPriority, Comparator.reverseOrder())
                    .thenComparing(Source::getEventTime, Comparator.reverseOrder()))
            .findFirst();

    if (sourceOpt.isPresent()) {
      sourceOpt.get().setInstallAttributed(true);
      sourceOpt.get().setInstallTime(eventTimestamp);
    }
  }

  @Override
  public void undoInstallAttribution(URI uri) {
    mDatastoreManager.getSources().stream()
        .filter(s -> s.getAppDestinations().get(0).equals(uri))
        .forEach(s -> updateSourceInstallData(s, false, null));
  }

  private void updateSourceInstallData(Source source, boolean installAttributed, Long installTime) {
    source.setInstallAttributed(installAttributed);
    source.setInstallTime(installTime);
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
    validateOptionalRange(start, end);

    if (origin == null && start == null) { // Delete by registrant
      deleteMeasurementDataByRegistrant(registrant);
    } else if (start == null) { // Delete by Registrant and URI
      deleteMeasurementDataByRegistrantAndURI(registrant, origin);
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

  private void deleteMeasurementDataByRegistrantAndURI(String registrant, String origin) {
    mDatastoreManager.removeAttributionRateLimitsByFilter(
        a -> a.getRegistrant().equals(registrant),
        a -> a.getSourceSite().equals(origin) || a.getDestinationSite().equals(origin));
    mDatastoreManager.removeEventReportsByFilter(
        e ->
            mDatastoreManager.getSources().stream()
                .anyMatch(
                    s ->
                        e.getSourceId().equals(s.getId())
                            && s.getRegistrant().toString().equals(registrant)
                            && ((s.getPublisher().toString().equals(origin))
                                || (e.getAttributionDestinations()
                                    .get(0)
                                    .toString()
                                    .equals(origin)))));
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
                        e.getSourceId().equals(s.getId())
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
                        e.getSourceId().equals(s.getId())
                            && s.getRegistrant().toString().equals(registrant)
                            && ((s.getPublisher().toString().equals(origin)
                                    && s.getEventTime() >= start.toEpochMilli()
                                    && s.getEventTime() <= end.toEpochMilli())
                                || (e.getAttributionDestinations().get(0).toString().equals(origin)
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
        .map(AggregateReport::getId)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<String> getPendingAggregateReportIdsForGivenApp(String appName) {
    return mDatastoreManager.getAggregateReports().stream()
        .filter(c -> c.getPublisher().toString().equals(appName))
        .filter(c -> c.getStatus() == AggregateReport.Status.PENDING)
        .map(AggregateReport::getId)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private Optional<Map<String, String>> getDestinationColumnAndValue(Trigger trigger) {
    if (trigger.getDestinationType() == EventSurfaceType.APP) {
      return Optional.of(Map.of("app_destination", trigger.getAttributionDestination().toString()));
    } else {
      Optional<URI> topPrivateDomainAndScheme =
          Web.topPrivateDomainAndScheme(trigger.getAttributionDestination());
      return topPrivateDomainAndScheme.map(URI -> Map.of("web_destination", URI.toString()));
    }
  }

  private Optional<URI> extractBaseURI(URI uri, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(uri))
        : Web.topPrivateDomainAndScheme(uri);
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
  public void updateSourceEventReportDedupKeys(Source source) {
    mDatastoreManager.updateSourceEventReportDedupKeys(source);
  }

  @Override
  public void updateSourceAttributedTriggers(Source source) {
    mDatastoreManager.updateSourceEventReportDedupKeys(source);
  }

  @Override
  public void updateSourceAggregateReportDedupKeys(Source source) {
    mDatastoreManager.updateSourceAggregateReportDedupKeys(source);
  }

  @Override
  public void updateSourceAggregateContributions(Source source) {
    mDatastoreManager.updateSourceAggregateContributions(source);
  }

  @Override
  public void updateEventReportSummaryBucket(String eventReportId, Pair<Long, Long> summaryBucket) {
    mDatastoreManager.updateEventReportSummaryBucket(eventReportId, summaryBucket);
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
  public List<DebugReport> getAllDebugReports() {
    return mDatastoreManager.getDebugReports();
  }

  @Override
  public void insertAggregateReport(AggregateReport aggregateReport) {
    mDatastoreManager.getAggregateReports().add(aggregateReport);
  }

  @Override
  public void insertDebugReport(DebugReport debugReport) {
    mDatastoreManager.getDebugReports().add(debugReport);
  }

  @Override
  public List<EventReport> getAllEventReports() {
    return mDatastoreManager.getEventReports();
  }

  @Override
  public boolean canStoreSource(Source source) {
    Optional<URI> publisher =
        this.getTopLevelPublisher(source.getPublisher(), source.getPublisherType());
    if (publisher.isEmpty()) {
      logger.info(
          String.format("getTopLevelPublisher failed: %s", source.getPublisher().toString()));
      return false;
    }

    long numSourcesPerPublisher =
        this.getNumSourcesPerPublisher(
            BaseUriExtractor.getBaseUri(publisher.get()), source.getPublisherType());

    if (numSourcesPerPublisher >= SystemHealthParams.MAX_SOURCES_PER_PUBLISHER) {
      logger.info(
          String.format(
              "Max limit of %d sources for publisher - %s reached.",
              SystemHealthParams.MAX_SOURCES_PER_PUBLISHER, source.getPublisher().toString()));
      return false;
    }

    return true;
  }

  @Override
  public boolean canStoreTrigger(Trigger trigger) {
    long triggersPerDestination =
        this.getNumTriggersPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType());
    return triggersPerDestination < SystemHealthParams.MAX_TRIGGER_REGISTERS_PER_DESTINATION;
  }

  private Optional<URI> getTopLevelPublisher(URI topOrigin, EventSurfaceType publisherType) {
    return publisherType == EventSurfaceType.APP
        ? Optional.of(topOrigin)
        : Web.topPrivateDomainAndScheme(topOrigin);
  }
}
