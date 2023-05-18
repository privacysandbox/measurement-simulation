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

import com.google.measurement.Source.Status;
import com.google.measurement.aggregation.AggregateReport;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public interface IMeasurementDAO {

  /** Returns list of all pending {@link Trigger}s. */
  List<Trigger> getPendingTriggers();

  Trigger getTrigger(String triggerId);

  void insertSource(Source source);

  void insertTrigger(Trigger trigger);

  /**
   * Fetches the count of aggregate reports for the provided destination.
   *
   * @param attributionDestination URI for the destination
   * @param destinationType DestinationType App/Web
   * @return number of aggregate reports in the database attributed to the provided destination
   */
  int getNumAggregateReportsPerDestination(
      URI attributionDestination, EventSurfaceType destinationType);

  /**
   * Fetches the count of event reports for the provided destination.
   *
   * @param attributionDestination URI for the destination
   * @param destinationType DestinationType App/Web
   * @return number of event reports in the database attributed to the provided destination
   */
  int getNumEventReportsPerDestination(
      URI attributionDestination, EventSurfaceType destinationType);

  /**
   * Gets the number of sources associated to a publisher.
   *
   * @param publisherURI URI for the publisher
   * @param publisherType PublisherType App/Web
   * @return Number of sources registered for the given publisher
   */
  long getNumSourcesPerPublisher(URI publisherURI, EventSurfaceType publisherType);

  /** Gets the number of triggers a registrant has registered. */
  long getNumTriggersPerRegistrant(URI registrant);

  /** Gets the number of triggers associated to a destination. */
  long getNumTriggersPerDestination(URI destination, EventSurfaceType destinationType);

  /**
   * Gets the count of distinct IDs of enrollments in the Attributions list in a time window from
   * windowStartTime (exclusive) to windowEndTime (inclusive) with matching publisher and
   * destination, excluding a given enrollment ID.
   */
  Integer countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
      URI sourceSite,
      URI destinationSite,
      String excludedEnrollmentId,
      long windowStartTime,
      long windowEndTime);

  /**
   * Gets the count of distinct URIs of destinations in the Sources list in a time window from
   * windowStartTime (exclusive) to windowEndTime (inclusive) with matching publisher, enrollment,
   * and ACTIVE status, excluding given destinations.
   */
  Integer countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
      URI publisher,
      EventSurfaceType publisherType,
      String enrollmentId,
      List<URI> excludedDestinations,
      EventSurfaceType destinationType,
      long windowStartTime,
      long windowEndTime);

  /**
   * Gets the count of distinct IDs of enrollments in the Sources list in a time window with
   * matching publisher and destination, excluding a given enrollment ID.
   */
  Integer countDistinctEnrollmentsPerPublisherXDestinationInSource(
      URI publisher,
      EventSurfaceType publisherType,
      URI destination,
      String enrollmentId,
      long windowStartTime,
      long windowEndTime);

  /**
   * Queries and returns the list of matching {@link Source} for the provided {@link Trigger}.
   *
   * @return list of active matching sources
   */
  List<Source> getMatchingActiveSources(Trigger trigger);

  /**
   * Updates the {@link Source.Status} value for the provided list of {@link Source}
   *
   * @param sources list of sources.
   * @param status value to be set
   */
  void updateSourceStatus(List<Source> sources, Status status);

  /**
   * Update the set of Event dedup keys contained in the provided {@link Source}
   *
   * @param source the {@link Source} object.
   */
  void updateSourceEventReportDedupKeys(Source source);

  /**
   * Update the set of Aggregate dedup keys contained in the provided {@link Source}
   *
   * @param source the {@link Source} object.
   */
  void updateSourceAggregateReportDedupKeys(Source source);

  /**
   * Updates the value of aggregate contributions for the corresponding {@link Source}
   *
   * @param source the {@link Source} object.
   */
  void updateSourceAggregateContributions(Source source);

  /**
   * Returns list of all the reports associated with the {@link Source}.
   *
   * @param source for querying reports
   * @return list of relevant eventReports
   */
  List<EventReport> getSourceEventReports(Source source);

  void insertEventReport(EventReport report);

  void deleteEventReport(EventReport report);

  /**
   * Returns list of IDs of all event reports that have a scheduled reporting time in the given
   * window from windowStartTime (inclusive) to windowEndTime (inclusive).
   */
  List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime);

  /** Returns list of IDs of all debug event reports. */
  List<String> getPendingDebugEventReportIds();

  /** Returns list of IDs of all pending event reports for a given app right away. */
  List<String> getPendingEventReportIdsForGivenApp(String appName);

  /**
   * Find the number of entries for a rate limit window using the {@link Source} and {@link
   * Trigger}. Rate-Limit Window: (Source Site, Destination Site, Window) from triggerTime.
   *
   * @return the number of entries for the window.
   */
  long getAttributionsPerRateLimitWindow(Source source, Trigger trigger);

  void insertAttribution(Attribution attribution);

  void deleteAppRecords(String uri);

  void deleteExpiredRecords();

  void doInstallAttribution(URI uri, long eventTimestamp);

  void undoInstallAttribution(URI uri);

  void insertAggregateReport(AggregateReport aggregateReport);

  /**
   * Returns list of IDs of all aggregate reports that have a scheduled reporting time in the given
   * window from windowStartTime (inclusive) to windowEndTime (inclusive).
   */
  List<String> getPendingAggregateReportIdsInWindow(long windowStartTime, long windowEndTime);

  /** Returns list of IDs of all pending aggregate reports for a given app right away. */
  List<String> getPendingAggregateReportIdsForGivenApp(String appName);

  void deleteMeasurementData(String registrant, String origin, Instant start, Instant end);

  List<AggregateReport> getAllAggregateReports();

  List<EventReport> getAllEventReports();

  boolean canStoreSource(Source source);

  boolean canStoreTrigger(Trigger trigger);
}
