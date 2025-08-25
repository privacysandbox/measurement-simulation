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

package com.google.measurement.client.data;

import com.google.measurement.client.Attribution;
import com.google.measurement.client.DeletionRequest.MatchBehavior;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.ITransaction;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.KeyValueData.DataType;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Pair;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.Uri;
import com.google.measurement.client.aggregation.AggregateDebugReportRecord;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.MeasurementTables;
import com.google.measurement.client.registration.AsyncRegistration;
import com.google.measurement.client.reporting.DebugReport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Interface for Measurement related data access operations. */
public interface IMeasurementDao {
  /** Set the transaction. */
  void setTransaction(ITransaction transaction);

  /** Add an entry to the Trigger datastore. */
  void insertTrigger(Trigger trigger) throws DatastoreException;

  /** Returns list of ids for all pending {@link Trigger}. */
  List<String> getPendingTriggerIds() throws DatastoreException;

  /**
   * Queries and returns the {@link Source}.
   *
   * @param sourceId ID of the requested Source
   * @return the requested Source
   */
  Source getSource(@NonNull String sourceId) throws DatastoreException;

  /**
   * Queries and returns the {@link Source}'s destinations.
   *
   * @param sourceId ID of the requested Source
   * @return a Pair of lists of app destination and web destination Uris
   */
  Pair<List<Uri>, List<Uri>> getSourceDestinations(@NonNull String sourceId)
      throws DatastoreException;

  /**
   * Queries and returns the {@link Source}'s attribution scopes.
   *
   * @param sourceId ID of the requested Source.
   * @return a list of attribution scopes.
   */
  List<String> getSourceAttributionScopes(@NonNull String sourceId) throws DatastoreException;

  /**
   * Queries and returns the {@link Source}'s attribution scopes for a given source registration and
   * reporting origin.
   *
   * @param registrationId ID of the registration.
   * @param registrationOrigin source registration origin.
   * @return an optional list of attribution scopes, empty if no source is found for the provided
   *     registration ID and reporting origin
   */
  Optional<Set<String>> getAttributionScopesForRegistration(
      @NonNull String registrationId, @NonNull String registrationOrigin) throws DatastoreException;

  /**
   * Updates existing sources based on the following criteria for attribution scope:
   *
   * <ol>
   *   <li>Deactivates sources with {@link Source#getMaxEventStates()} different from {@code
   *       pendingSource}.
   *   <li>Deactivates sources with {@link Source#getAttributionScopeLimit()} smaller than {@code
   *       pendingSource}.
   *   <li>Removes attribution scopes for existing sources not selected as the latest k attribution
   *       scopes, where k = {@code pendingSource#getAttributionScopeLimit()}.
   * </ol>
   *
   * @param pendingSource The pending source to compare against existing sources.
   * @throws DatastoreException If an error occurs while processing the data in the datastore.
   */
  void updateSourcesForAttributionScope(@NonNull Source pendingSource) throws DatastoreException;

  /**
   * Queries and returns the {@link Source}.
   *
   * @param sourceId ID of the requested Source
   * @return the source registrant from requested Source
   */
  String getSourceRegistrant(@NonNull String sourceId) throws DatastoreException;

  /**
   * Queries and returns the {@link Trigger}.
   *
   * @param triggerId Id of the request Trigger
   * @return the requested Trigger
   */
  Trigger getTrigger(String triggerId) throws DatastoreException;

  /**
   * Fetches the count of aggregate reports for the provided destination.
   *
   * @param attributionDestination Uri for the destination
   * @param destinationType DestinationType App/Web
   * @return number of aggregate reports in the database attributed to the provided destination
   */
  int getNumAggregateReportsPerDestination(
      @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
      throws DatastoreException;

  /**
   * Fetches the count of aggregate reports for the provided source id.
   *
   * @param sourceId source id
   * @param api aggregate report API
   * @return number of aggregate reports in the database attributed to the provided source id and
   *     with provided api value.
   */
  int countNumAggregateReportsPerSource(String sourceId, String api) throws DatastoreException;

  /**
   * Fetches the count of event reports for the provided destination.
   *
   * @param attributionDestination Uri for the destination
   * @param destinationType DestinationType App/Web
   * @return number of event reports in the database attributed to the provided destination
   */
  int getNumEventReportsPerDestination(
      @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
      throws DatastoreException;

  /**
   * Gets the number of sources associated to a publisher.
   *
   * @param publisherUri Uri for the publisher
   * @param publisherType PublisherType App/Web
   * @return Number of sources registered for the given publisher
   */
  long getNumSourcesPerPublisher(Uri publisherUri, @EventSurfaceType int publisherType)
      throws DatastoreException;

  /** Gets the number of triggers associated to a destination. */
  long getNumTriggersPerDestination(Uri destination, @EventSurfaceType int destinationType)
      throws DatastoreException;

  /**
   * Returns a list of app names that have measurement data. These app names are not part of the
   * installedApps list.
   *
   * @param installedApps app names with their android-app:// scheme
   * @return list of app names with their android-app:// scheme
   * @throws DatastoreException if transaction is not active
   */
  List<Uri> getUninstalledAppNamesHavingMeasurementData(List<Uri> installedApps)
      throws DatastoreException;

  /**
   * Gets the count of distinct reporting origins in the Attribution table in a time window with
   * matching publisher and destination, excluding a given reporting origin.
   */
  Integer countDistinctReportingOriginsPerPublisherXDestInAttribution(
      Uri sourceSite,
      Uri destination,
      Uri excludedReportingOrigin,
      long windowStartTime,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Gets the count of distinct Uris of destinations in the Source table in a time window with
   * matching publisher, enrollment, and unexpired; excluding given destinations.
   */
  Integer countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
      Uri publisher,
      @EventSurfaceType int publisherType,
      String enrollmentId,
      List<Uri> excludedDestinations,
      @EventSurfaceType int destinationType,
      long windowStartTime,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Gets the count of distinct Uris of destinations in the Source table with matching publisher,
   * enrollment, and unexpired; excluding given destinations.
   */
  Integer countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
      Uri publisher,
      @EventSurfaceType int publisherType,
      String enrollmentId,
      List<Uri> excludedDestinations,
      @EventSurfaceType int destinationType,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Gets the count of distinct Uris of destinations in the Source table in a time window with
   * matching publisher; excluding given destinations.
   */
  Integer countDistinctDestinationsPerPublisherPerRateLimitWindow(
      Uri publisher,
      @EventSurfaceType int publisherType,
      List<Uri> excludedDestinations,
      @EventSurfaceType int destinationType,
      long windowStartTime,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Gets the count of distinct reporting origins in the source table in a time period before event
   * time with matching publisher, enrollment; excluding the given registration origin.
   */
  Integer countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
      Uri registrationOrigin,
      Uri publisher,
      @EventSurfaceType int publisherType,
      String enrollmentId,
      long eventTime,
      long timePeriodInMs)
      throws DatastoreException;

  /**
   * Gets the count of distinct IDs of enrollments in the Source table in a time window with
   * matching publisher and destination; excluding a given reporting origin.
   */
  Integer countDistinctReportingOriginsPerPublisherXDestinationInSource(
      Uri publisher,
      @EventSurfaceType int publisherType,
      List<Uri> destinations,
      Uri excludedReportingOrigin,
      long windowStartTime,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Updates the {@link Trigger.Status} value for the provided {@link Trigger}.
   *
   * @param triggerIds trigger to update
   * @param status status to apply
   * @throws DatastoreException database transaction related issues
   */
  void updateTriggerStatus(@NonNull Collection<String> triggerIds, @Trigger.Status int status)
      throws DatastoreException;

  /**
   * Add an entry to the Source datastore and returns the source ID.
   *
   * @param source Source data to be inserted.
   * @return source ID, if source ID is null, the record was not saved.
   */
  String insertSource(Source source) throws DatastoreException;

  /**
   * Queries and returns the list of matching {@link Source} for the provided {@link Trigger}.
   *
   * @return list of active matching sources; Null in case of SQL failure
   */
  List<Source> getMatchingActiveSources(Trigger trigger) throws DatastoreException;

  /**
   * Queries and returns the most recent matching delayed {@link Source} (Optional) for the provided
   * {@link Trigger}.
   */
  Optional<Source> getNearestDelayedMatchingActiveSource(@NonNull Trigger trigger)
      throws DatastoreException;

  /**
   * Updates the {@link Source.Status} value for the provided list of {@link Source}
   *
   * @param sourceIds list of sources.
   * @param status value to be set
   */
  void updateSourceStatus(@NonNull Collection<String> sourceIds, @Source.Status int status)
      throws DatastoreException;

  /**
   * @param sourceId the source ID
   * @param attributionStatus the source's JSON-encoded attributed triggers
   * @throws DatastoreException throws DatastoreException
   */
  void updateSourceAttributedTriggers(String sourceId, String attributionStatus)
      throws DatastoreException;

  /**
   * Update the value of {@link Source.Status} for the corresponding {@link Source}
   *
   * @param source the {@link Source} object.
   */
  void updateSourceEventReportDedupKeys(@NonNull Source source) throws DatastoreException;

  /**
   * Update the set of Aggregate dedup keys contained in the provided {@link Source}
   *
   * @param source the {@link Source} object.
   */
  void updateSourceAggregateReportDedupKeys(@NonNull Source source) throws DatastoreException;

  /**
   * Updates the value of aggregate contributions for the corresponding {@link Source}.
   *
   * @param source the {@link Source} object.
   */
  void updateSourceAggregateContributions(@NonNull Source source) throws DatastoreException;

  /**
   * Updates the value of aggregate debug contributions for the corresponding {@link Source}.
   *
   * @param source the {@link Source} object.
   */
  void updateSourceAggregateDebugContributions(@NonNull Source source) throws DatastoreException;

  /**
   * Returns list of all the reports associated with the {@link Source}.
   *
   * @param source for querying reports
   * @return list of relevant eventReports
   */
  List<EventReport> getSourceEventReports(Source source) throws DatastoreException;

  /**
   * Queries and returns the {@link EventReport}.
   *
   * @param eventReportId Id of the request Event Report
   * @return the requested Event Report; Null in case of SQL failure
   */
  @Nullable
  EventReport getEventReport(String eventReportId) throws DatastoreException;

  /**
   * Queries and returns the {@link AggregateReport}
   *
   * @param aggregateReportId Id of the request Aggregate Report
   * @return the request Aggregate Report; Null in case of SQL failure
   */
  @Nullable
  AggregateReport getAggregateReport(String aggregateReportId) throws DatastoreException;

  /**
   * Queries and returns the {@link DebugReport}
   *
   * @param debugReportId of the request Debug Report
   * @return the request Debug Report; Null in case of SQL failure
   */
  @Nullable
  DebugReport getDebugReport(String debugReportId) throws DatastoreException;

  /**
   * Change the status of an event report to DELIVERED
   *
   * @param eventReportId the id of the event report to be updated
   * @param status status of the event report
   */
  void markEventReportStatus(String eventReportId, @EventReport.Status int status)
      throws DatastoreException;

  /**
   * Change the summary bucket of the event report
   *
   * @param eventReportId the id of the event report to be updated
   * @param summaryBucket the new summary bucket of the report
   * @throws DatastoreException
   */
  void updateEventReportSummaryBucket(@NonNull String eventReportId, @NonNull String summaryBucket)
      throws DatastoreException;
  ;

  /**
   * Change the status of an event debug report to DELIVERED
   *
   * @param eventReportId the id of the event report to be updated
   */
  void markEventDebugReportDelivered(String eventReportId) throws DatastoreException;

  /**
   * Change the status of an aggregate report to DELIVERED
   *
   * @param aggregateReportId the id of the event report to be updated
   * @param status new status to set
   */
  void markAggregateReportStatus(String aggregateReportId, @AggregateReport.Status int status)
      throws DatastoreException;

  /**
   * Change the status of an aggregate debug report to DELIVERED
   *
   * @param aggregateReportId the id of the event report to be updated
   */
  void markAggregateDebugReportDelivered(String aggregateReportId) throws DatastoreException;

  /** Saves the {@link EventReport} to the datastore. */
  void insertEventReport(EventReport eventReport) throws DatastoreException;

  /** Deletes the {@link EventReport} and associated {@link Attribution} from the datastore. */
  void deleteEventReportAndAttribution(EventReport eventReport) throws DatastoreException;

  /** Deletes the {@link DebugReport} from the datastore. */
  void deleteDebugReport(String debugReportId) throws DatastoreException;

  /**
   * Deletes the {@link DebugReport} from the datastore based on parameters.
   *
   * @param registrant
   * @param start
   * @param end
   * @return number of debug records deleted
   * @throws DatastoreException
   */
  int deleteDebugReports(@NonNull Uri registrant, @NonNull Instant start, @NonNull Instant end)
      throws DatastoreException;

  /** Returns list of all event reports that have a scheduled reporting time in the given window. */
  List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
      throws DatastoreException;

  /** Returns list of all debug event reports. */
  List<String> getPendingDebugEventReportIds() throws DatastoreException;

  /** Returns list of all pending event reports for a given app right away. */
  List<String> getPendingEventReportIdsForGivenApp(Uri appName) throws DatastoreException;

  /**
   * Find the number of entries for a rate limit window, scoped to event- or aggregate-level using
   * the {@link Source} and {@link Trigger}. Rate-Limit Window: (Scope, Source Site, Destination
   * Site, Window) from triggerTime.
   *
   * @return the number of entries for the window.
   */
  long getAttributionsPerRateLimitWindow(
      @Attribution.Scope int scope, @NonNull Source source, @NonNull Trigger trigger)
      throws DatastoreException;

  /** Add an entry in Attribution datastore. */
  void insertAttribution(@NonNull Attribution attribution) throws DatastoreException;

  /** Deletes all expired records in measurement tables. */
  void deleteExpiredRecords(
      long earliestValidInsertion,
      int registrationRetryLimit,
      @Nullable Long earliestValidAppReportInsertion,
      long earliestValidAggregateDebugReportInsertion)
      throws DatastoreException;

  /**
   * Mark relevant source as install attributed.
   *
   * @param uri package identifier
   * @param eventTimestamp timestamp of installation event
   */
  void doInstallAttribution(Uri uri, long eventTimestamp) throws DatastoreException;

  /**
   * Undo any install attributed source events.
   *
   * @param uri package identifier
   */
  void undoInstallAttribution(Uri uri) throws DatastoreException;

  /** Save aggregate encryption key to the datastore. */
  void insertAggregateEncryptionKey(AggregateEncryptionKey aggregateEncryptionKey)
      throws DatastoreException;

  /**
   * Retrieve all aggregate encryption keys from the datastore whose expiry time is greater than or
   * equal to {@code expiry}.
   */
  List<AggregateEncryptionKey> getNonExpiredAggregateEncryptionKeys(Uri coordinator, long expiry)
      throws DatastoreException;

  /** Remove aggregate encryption keys from the datastore older than {@code expiry}. */
  void deleteExpiredAggregateEncryptionKeys(long expiry) throws DatastoreException;

  /** Delete Event Report from datastore. */
  void deleteEventReport(EventReport eventReport) throws DatastoreException;

  /** Delete Aggregate Report from datastore. */
  void deleteAggregateReport(AggregateReport aggregateReport) throws DatastoreException;

  /** Save unencrypted aggregate payload to the datastore. */
  void insertAggregateReport(AggregateReport payload) throws DatastoreException;

  /** Save debug report payload to the datastore. */
  void insertDebugReport(DebugReport payload) throws DatastoreException;

  /**
   * Returns a map of coordinator to pending aggregate reports that have a scheduled reporting time
   * in the given window.
   */
  Map<String, List<String>> getPendingAggregateReportIdsByCoordinatorInWindow(
      long windowStartTime, long windowEndTime) throws DatastoreException;

  /** Returns a map of coordinator to pending aggregate debug reports */
  Map<String, List<String>> getPendingAggregateDebugReportIdsByCoordinator()
      throws DatastoreException;

  /** Returns list of all debug reports. */
  List<String> getDebugReportIds() throws DatastoreException;

  /** Returns list of all pending aggregate reports for a given app right away. */
  List<String> getPendingAggregateReportIdsForGivenApp(Uri appName) throws DatastoreException;

  /**
   * Delete all data generated by Measurement API, except for tables in the exclusion list.
   *
   * @param tablesToExclude a {@link List} of tables that won't be deleted. An empty list will
   *     delete every table.
   */
  void deleteAllMeasurementData(List<String> tablesToExclude) throws DatastoreException;

  /**
   * Delete records from source table that match provided source IDs.
   *
   * @param sourceIds source IDs to match
   * @throws DatastoreException database transaction issues
   */
  void deleteSources(@NonNull Collection<String> sourceIds) throws DatastoreException;

  /**
   * Delete records from trigger table that match provided trigger IDs.
   *
   * @param triggerIds trigger IDs to match
   * @throws DatastoreException database transaction issues
   */
  void deleteTriggers(@NonNull Collection<String> triggerIds) throws DatastoreException;

  /**
   * Delete records from async registration table that match provided async registration IDs.
   *
   * @param asyncRegistrationIds async registration IDs to match
   * @throws DatastoreException database transaction issues
   */
  void deleteAsyncRegistrations(@NonNull List<String> asyncRegistrationIds)
      throws DatastoreException;

  /**
   * Insert a record into the Async Registration Table.
   *
   * @param asyncRegistration a {@link AsyncRegistration} to insert into the Async Registration
   *     table
   */
  void insertAsyncRegistration(@NonNull AsyncRegistration asyncRegistration)
      throws DatastoreException;

  /**
   * Delete a record from the AsyncRegistration table.
   *
   * @param id a {@link String} id of the record to delete from the AsyncRegistration table.
   */
  void deleteAsyncRegistration(@NonNull String id) throws DatastoreException;

  /**
   * Get the record with the earliest request time and a valid retry count.
   *
   * @param retryLimit a long that is used for determining the next valid record to be serviced
   * @param failedOrigins set of origins that have failed during the current run
   */
  AsyncRegistration fetchNextQueuedAsyncRegistration(int retryLimit, Set<Uri> failedOrigins)
      throws DatastoreException;

  /**
   * Insert/Update the supplied {@link KeyValueData} object
   *
   * @param keyValueData a {@link KeyValueData} to be stored/update
   * @throws DatastoreException when insertion fails
   */
  void insertOrUpdateKeyValueData(@NonNull KeyValueData keyValueData) throws DatastoreException;

  /**
   * Returns the {@link KeyValueData} for {key, dataType} pair
   *
   * @param key of the stored data
   * @param dataType of the stored datta
   * @return {@link KeyValueData} object
   */
  KeyValueData getKeyValueData(@NonNull String key, @NonNull DataType dataType)
      throws DatastoreException;

  /**
   * Update the retry count for a record in the Async Registration table.
   *
   * @param asyncRegistration a {@link AsyncRegistration} for which the retryCount will be updated
   */
  void updateRetryCount(@NonNull AsyncRegistration asyncRegistration) throws DatastoreException;

  /**
   * Fetches aggregate reports that match either given source or trigger IDs. If A1 is set of
   * aggregate reports that match any of sourceIds and A2 is set of aggregate reports that match any
   * of triggerIds, then we delete (A1 U A2).
   *
   * @param sourceIds sources to be matched with aggregate reports
   * @param triggerIds triggers to be matched with aggregate reports
   */
  List<AggregateReport> fetchMatchingAggregateReports(
      @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
      throws DatastoreException;

  /**
   * Fetches event reports that match either given source or trigger IDs. If A1 is set of event
   * reports that match any of sourceIds and A2 is set of event reports that match any of
   * triggerIds, then we delete (A1 U A2).
   *
   * @param sourceIds sources to be matched with event reports
   * @param triggerIds triggers to be matched with event reports
   */
  List<EventReport> fetchMatchingEventReports(
      @NonNull Collection<String> sourceIds, @NonNull Collection<String> triggerIds)
      throws DatastoreException;

  /**
   * Get source IDs based on trigger IDs for flexible event API
   *
   * @param triggerIds triggers to be matched with source
   * @return the list of sourced ids
   * @throws DatastoreException throw DatastoreException
   */
  Set<String> fetchFlexSourceIdsFor(@NonNull Collection<String> triggerIds)
      throws DatastoreException;

  /** Deletes the {@link EventReport}s and associated {@link Attribution}s from the datastore. */
  void deleteFlexEventReportsAndAttributions(List<EventReport> eventReports)
      throws DatastoreException;

  /**
   * Returns list of sources matching registrant, publishers and also in the provided time frame. It
   * matches registrant and time range (start & end) irrespective of the {@code matchBehavior}. In
   * the resulting set, if matchBehavior is {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_DELETE}, then it
   * matches origins and domains. In case of {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_PRESERVE}, it
   * returns the records that don't match origins or domain.
   *
   * @param registrant registrant to match
   * @param start event time should be after this instant (inclusive)
   * @param end event time should be after this instant (inclusive)
   * @param origins publisher site match
   * @param domains publisher top level domain matches
   * @param matchBehavior indicates whether to return matching or inversely matching (everything
   *     except matching) data
   * @return list of source IDs
   * @throws DatastoreException database transaction level issues
   */
  List<String> fetchMatchingSources(
      @NonNull Uri registrant,
      @NonNull Instant start,
      @NonNull Instant end,
      @NonNull List<Uri> origins,
      @NonNull List<Uri> domains,
      @MatchBehavior int matchBehavior)
      throws DatastoreException;

  /**
   * Returns list of triggers matching registrant and destinations in the provided time frame. It
   * matches registrant and time range (start & end) irrespective of the {@code matchBehavior}. In
   * the resulting set, if matchBehavior is {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_DELETE}, then it
   * matches origins and domains. In case of {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_PRESERVE}, it
   * returns the records that don't match origins or domain.
   *
   * @param registrant registrant to match
   * @param start trigger time should be after this instant (inclusive)
   * @param end trigger time should be after this instant (inclusive)
   * @param origins destination site match
   * @param domains destination top level domain matches
   * @param matchBehavior indicates whether to return matching or inversely matching (everything
   *     except matching) data
   * @return list of trigger IDs
   * @throws DatastoreException database transaction level issues
   */
  Set<String> fetchMatchingTriggers(
      @NonNull Uri registrant,
      @NonNull Instant start,
      @NonNull Instant end,
      @NonNull List<Uri> origins,
      @NonNull List<Uri> domains,
      @MatchBehavior int matchBehavior)
      throws DatastoreException;

  /**
   * Returns a pair of lists of sources matching registrant, publishers and also in the provided
   * time frame. If 24 hours plus the most recent trigger time of all reports attributed to the
   * source is less than the current time then the source is in the first pair. If greater than or
   * equal to the current time then the source is in the second pair. It matches registrant.
   *
   * @param registrant registrant to match except matching) data
   * @param eventTime time of uninstall event
   * @return pair of lists of source IDs
   * @throws DatastoreException database transaction level issues
   */
  Pair<List<String>, List<String>> fetchMatchingSourcesUninstall(
      @NonNull Uri registrant, long eventTime) throws DatastoreException;

  /**
   * Returns a pair of lists of triggers matching registrant, publishers and also in the provided
   * time frame. If 24 hours plus the most recent trigger time of all reports attributed to the
   * trigger is less than the current time then the trigger is in the first pair. If greater than or
   * equal to the current time then the trigger is in the second pair. It matches registrant.
   *
   * @param registrant registrant to match except matching) data
   * @param eventTime time of uninstall event
   * @return pair of lists of trigger IDs
   * @throws DatastoreException database transaction level issues
   */
  Pair<List<String>, List<String>> fetchMatchingTriggersUninstall(
      @NonNull Uri registrant, long eventTime) throws DatastoreException;

  /**
   * Returns list of async registrations matching registrant and top origins in the provided time
   * frame. It matches registrant and time range (start & end) irrespective of the {@code
   * matchBehavior}. In the resulting set, if matchBehavior is {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_DELETE}, then it
   * matches origins and domains. In case of {@link
   * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_PRESERVE}, it
   * returns the records that don't match origins or domain.
   *
   * @param registrant registrant to match
   * @param start request time should be after this instant (inclusive)
   * @param end request time should be after this instant (inclusive)
   * @param origins top origin site match
   * @param domains top origin top level domain matches
   * @param matchBehavior indicates whether to return matching or inversely matching (everything
   *     except matching) data
   * @return list of async registration IDs
   * @throws DatastoreException database transaction level issues
   */
  List<String> fetchMatchingAsyncRegistrations(
      @NonNull Uri registrant,
      @NonNull Instant start,
      @NonNull Instant end,
      @NonNull List<Uri> origins,
      @NonNull List<Uri> domains,
      @MatchBehavior int matchBehavior)
      throws DatastoreException;

  /**
   * Fetches the XNA relevant sources. It includes sources associated to the trigger's enrollment ID
   * as well as the sources associated to the provided SAN enrollment IDs.
   *
   * @param trigger trigger to match
   * @param xnaEnrollmentIds SAN enrollment IDs to match
   * @return XNA relevant sources
   * @throws DatastoreException when SQLite issue occurs
   */
  List<Source> fetchTriggerMatchingSourcesForXna(
      @NonNull Trigger trigger, @NonNull Collection<String> xnaEnrollmentIds)
      throws DatastoreException;

  /**
   * Insert an entry of source ID with enrollment ID into the {@link
   * MeasurementTables.XnaIgnoredSourcesContract#TABLE}. It means that the provided source should be
   * ignored to be picked up for doing XNA based attribution on the provided enrollment.
   *
   * @param sourceId source ID
   * @param enrollmentId enrollment ID
   */
  void insertIgnoredSourceForEnrollment(@NonNull String sourceId, @NonNull String enrollmentId)
      throws DatastoreException;

  /**
   * Increments Retry Counter for EventReporting Records and return the updated retry count. This is
   * used for Retry Limiting.
   *
   * @param id Primary key id of Record in Measurement Event Report Table.
   * @param reportType KeyValueData.DataType corresponding with Record type being incremented.
   * @return current report count
   */
  int incrementAndGetReportingRetryCount(String id, DataType reportType) throws DatastoreException;

  /**
   * Returns the number of unique AdIds provided by an Ad Tech in web contexts to match with the
   * platform AdID from app contexts for debug key population in reports. It counts distinct AdIDs
   * provided by the AdTech across sources and triggers in the DB.
   *
   * @param enrollmentId enrollmentId of previous source/trigger registrations to check AdId
   *     provided on registration.
   * @return number of unique AdIds the AdTech has provided.
   * @throws DatastoreException when SQLite issue occurs
   */
  long countDistinctDebugAdIdsUsedByEnrollment(@NonNull String enrollmentId)
      throws DatastoreException;

  /**
   * Inserts an entry of app report history with enrollment ID into the {@link
   * MeasurementTables.AppReportHistoryContract#TABLE}. It means that event / aggregate reports for
   * the given app destination have been delivered to the registration origin.
   *
   * @param appDestination app destination
   * @param registrationOrigin source registration origin
   * @param lastReportDeliveredTimestamp last deliver time for the report
   * @throws DatastoreException when SQLite issue occurs.
   */
  void insertOrUpdateAppReportHistory(
      @NonNull Uri appDestination,
      @NonNull Uri registrationOrigin,
      long lastReportDeliveredTimestamp)
      throws DatastoreException;

  /**
   * Insert an entry of {@link AggregateDebugReportRecord} into the {@link
   * MeasurementTables.AggregatableDebugReportBudgetTrackerContract#TABLE} which tracks budget
   * limits for aggregate debug reports.
   *
   * @param aggregateDebugReportRecord
   */
  void insertAggregateDebugReportRecord(AggregateDebugReportRecord aggregateDebugReportRecord)
      throws DatastoreException;

  /**
   * Returns the number of unique navigation sources by reporting origin and registration id.
   *
   * @param reportingOrigin the reporting origin to match.
   * @param registrationId the registration id to match.
   * @return the number of matched navigation sources.
   * @throws DatastoreException
   */
  long countNavigationSourcesPerReportingOrigin(
      @NonNull Uri reportingOrigin, @NonNull String registrationId) throws DatastoreException;

  /**
   * Let matchingSources be unexpired sources that match the provided publisher, publisher type
   * destination surface type and enrollmentId. Pick and return the sources that have the lowest
   * destination priority value or secondarily the least recently used destination excluding the
   * provided list of destinations.
   *
   * @param publisher publisher to match
   * @param publisherType publisher surface type, i.e. app/web to match
   * @param enrollmentId matching enrollment
   * @param excludedDestinations destinations to exclude while matching
   * @param destinationType destination type app/web
   * @param windowEndTime selected sources' expiry needs to be greater than this time
   * @return sources with least recently used destination along with the priority value
   * @throws DatastoreException when accessing the DB fails
   */
  Pair<Long, List<String>> fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
      Uri publisher,
      int publisherType,
      String enrollmentId,
      List<Uri> excludedDestinations,
      int destinationType,
      long windowEndTime)
      throws DatastoreException;

  /**
   * Deletes pending aggregate reports for the provided sources. Also delete the attributions that
   * are associated to those reports.
   *
   * @param sourceIds sources to consider to query the pending reports
   * @throws DatastoreException when accessing the DB fails
   */
  void deletePendingAggregateReportsAndAttributionsForSources(List<String> sourceIds)
      throws DatastoreException;

  /**
   * Deletes pending fake event reports for the provided sources. Attributions are not deleted.
   *
   * @param sourceIds sources to consider to query the pending reports
   * @param currentTimeStamp it's practically the current time stamp, we delete only those reports
   *     that have trigger time in future indicating that they are fake
   * @throws DatastoreException when deletion fails
   */
  void deleteFutureFakeEventReportsForSources(List<String> sourceIds, long currentTimeStamp)
      throws DatastoreException;

  /**
   * Return the timestamp of the latest pending report (Event or Aggregate) in the batching window.
   * The batching window is calculated as the earliest report's timestamp + batchWindow. If there
   * are no reports, return null.
   *
   * @param batchWindow Size of the batching window, in ms, starting at the next pending report.
   * @return Latest report's timestamp, in ms, within the batching window.
   * @throws DatastoreException when SQLite issue occurs
   */
  Long getLatestReportTimeInBatchWindow(long batchWindow) throws DatastoreException;

  /**
   * Get total aggregate debug report budget per publisher x after window start time stamp.
   *
   * @param publisher publisher to match
   * @throws DatastoreException when SQLite issue occurs
   */
  int sumAggregateDebugReportBudgetXPublisherXWindow(
      Uri publisher, @EventSurfaceType int publisherType, long windowStartTime)
      throws DatastoreException;

  /**
   * Get total aggregate debug report budget per reporting publisher x origin x after window start
   * time stamp.
   *
   * @param publisher publisher to match
   * @param origin origin to match
   * @throws DatastoreException when SQLite issue occurs
   */
  int sumAggregateDebugReportBudgetXOriginXPublisherXWindow(
      Uri publisher, @EventSurfaceType int publisherType, Uri origin, long windowStartTime)
      throws DatastoreException;
}
