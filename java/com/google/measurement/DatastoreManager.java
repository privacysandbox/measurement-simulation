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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores {@link Source} and {@link Trigger} instances, and provides methods of searching for and
 * iterating over them.
 */
public class DatastoreManager {
  private List<Source> sources;
  private List<Trigger> triggers;
  private List<EventReport> eventReports;
  private List<Attribution> attributions;
  private List<AggregateReport> aggregateReports; // List of AggregateReports
  private List<DebugReport> debugReports;

  public DatastoreManager() {
    this.sources = new ArrayList<>();
    this.triggers = new ArrayList<>();
    eventReports = new ArrayList<>();
    attributions = new ArrayList<>();
    aggregateReports = new ArrayList<>();
    debugReports = new ArrayList<>();
  }

  // Getters/Modifiers Methods

  public void insertSource(Source source) {
    this.sources.add(source);
  }

  public List<Source> getSources() {
    return sources;
  }

  public void insertTrigger(Trigger trigger) {
    this.triggers.add(trigger);
  }

  public List<Trigger> getTriggers() {
    return triggers;
  }

  public List<EventReport> getEventReports() {
    return eventReports;
  }

  public List<Attribution> getAttributions() {
    return attributions;
  }

  public List<AggregateReport> getAggregateReports() {
    return aggregateReports;
  }

  public List<DebugReport> getDebugReports() {
    return debugReports;
  }

  public void updateSourceStatus(List<Source> sources, Status status) {
    List<String> ids = sources.stream().map(Source::getId).collect(Collectors.toList());
    sources.stream().filter((s) -> ids.contains(s.getId())).forEach((s) -> s.setStatus(status));
  }

  public void updateSourceAggregateContributions(Source source) {
    sources.stream()
        .filter((s) -> s.getId().equals(source.getId()))
        .forEach((s) -> s.setAggregateContributions(source.getAggregateContributions()));
  }

  public void updateSourceEventReportDedupKeys(Source source) {
    sources.stream()
        .filter((s) -> s.getId().equals(source.getId()))
        .forEach((s) -> s.setEventReportDedupKeys(source.getEventReportDedupKeys()));
  }

  public void updateSourceAttributedTriggers(Source source) {
    sources.stream()
        .filter((s) -> s.getId().equals(source.getId()))
        .forEach((s) -> s.setAttributedTriggers(source.getAttributedTriggers()));
  }

  public void updateSourceAggregateReportDedupKeys(Source source) {
    sources.stream()
        .filter((s) -> s.getId().equals(source.getId()))
        .forEach((s) -> s.setAggregateReportDedupKeys(source.getAggregateReportDedupKeys()));
  }

  public void updateEventReportSummaryBucket(String eventReportId, Pair<Long, Long> summaryBucket) {
    eventReports.stream()
        .filter((s) -> s.getId().equals(eventReportId))
        .forEach((s) -> s.setTriggerSummaryBucket(summaryBucket));
  }

  public void insertEventReport(EventReport report) {
    eventReports.add(report);
  }

  public void insertAttribution(Attribution attribution) {
    attributions.add(attribution);
  }

  public void removeEventReport(EventReport report) {
    eventReports.remove(report);
  }

  public void removeAttributionRateLimit(Attribution attribution) {
    attributions.remove(attribution);
  }

  public List<EventReport> getEventReportsByRegistrant(String registrant) {
    return eventReports.stream()
        .filter(
            e ->
                sources.stream() // Search for EventReports, Source.eventId == EventReport.sourceId
                    .filter(s -> s.getId().equals(e.getSourceId()))
                    .anyMatch(s -> s.getRegistrant().toString().equals(registrant))
            // Filter for where such a Source exists that fulfils the above
            )
        .collect(Collectors.toList());
  }

  @SafeVarargs
  public final void removeSourcesByFilter(Predicate<Source>... filters) {
    Stream<Source> sourceStream = sources.stream();
    for (Predicate<Source> filter : filters) sourceStream = sourceStream.filter(filter);
    List<Source> sourcesToRemove = sourceStream.collect(Collectors.toList());
    sources.removeAll(sourcesToRemove);
  }

  @SafeVarargs
  public final void removeTriggersByFilter(Predicate<Trigger>... filters) {
    Stream<Trigger> triggerStream = triggers.stream();
    for (Predicate<Trigger> filter : filters) triggerStream = triggerStream.filter(filter);
    List<Trigger> triggersToRemove = triggerStream.collect(Collectors.toList());
    triggers.removeAll(triggersToRemove);
  }

  @SafeVarargs
  public final void removeEventReportsByFilter(Predicate<EventReport>... filters) {
    Stream<EventReport> eventReportStream = eventReports.stream();
    for (Predicate<EventReport> filter : filters)
      eventReportStream = eventReportStream.filter(filter);
    List<EventReport> eventReportsToRemove = eventReportStream.collect(Collectors.toList());
    eventReports.removeAll(eventReportsToRemove);
  }

  @SafeVarargs
  public final void removeAttributionRateLimitsByFilter(Predicate<Attribution>... filters) {
    Stream<Attribution> attributionRateLimitStream = attributions.stream();
    for (Predicate<Attribution> filter : filters)
      attributionRateLimitStream = attributionRateLimitStream.filter(filter);
    List<Attribution> attributionRateLimitsToRemove =
        attributionRateLimitStream.collect(Collectors.toList());
    attributions.removeAll(attributionRateLimitsToRemove);
  }
}
