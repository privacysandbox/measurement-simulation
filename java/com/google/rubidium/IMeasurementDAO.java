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

import com.google.rubidium.Source.Status;
import com.google.rubidium.aggregation.AggregateReport;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public interface IMeasurementDAO {

  List<Trigger> getPendingTriggers();

  List<Source> getMatchingActiveSources(Trigger trigger);

  List<EventReport> getSourceEventReports(Source source);

  List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime);

  List<String> getPendingEventReportIdsForGivenApp(String appName);

  long getAttributionsPerRateLimitWindow(Source source, Trigger trigger);

  long getNumSourcesPerRegistrant(URI registrant);

  long getNumTriggersPerRegistrant(URI registrant);

  void doInstallAttribution(URI uri, long eventTimestamp);

  void undoInstallAttribution(URI uri);

  Trigger getTrigger(String triggerId);

  void deleteEventReport(EventReport report);

  void insertEventReport(EventReport report);

  void updateSourceStatus(List<Source> sources, Status status);

  void updateSourceAggregateContributions(Source source);

  void updateSourceDedupKeys(Source source);

  void insertAttribution(Attribution attribution);

  void deleteAppRecords(String uri);

  int countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
      URI sourceSite,
      URI destinationSite,
      String excludedEnrollmentId,
      long windowStartTime,
      long windowEndTime);

  void deleteExpiredRecords();

  void deleteMeasurementData(String registrant, String origin, Instant start, Instant end);

  void insertAggregateReport(AggregateReport aggregateReport);

  List<AggregateReport> getAllAggregateReports();

  List<String> getPendingAggregateReportIdsInWindow(long windowStartTime, long windowEndTime);

  List<String> getPendingAggregateReportIdsForGivenApp(String appName);

  List<EventReport> getAllEventReports();
}
