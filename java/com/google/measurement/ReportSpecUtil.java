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

import com.google.measurement.util.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Contain static methods for report specification */
public class ReportSpecUtil {
  /** The JSON keys for flexible event report API input */
  public interface FlexEventReportJsonKeys {
    String VALUE = "value";
    String PRIORITY = "priority";
    String TRIGGER_TIME = "trigger_time";
    String TRIGGER_DATA = "trigger_data";
    String FLIP_PROBABILITY = "flip_probability";
    String END_TIMES = "end_times";
    String START_TIME = "start_time";
    String SUMMARY_WINDOW_OPERATOR = "summary_window_operator";
    String EVENT_REPORT_WINDOWS = "event_report_windows";
    String SUMMARY_BUCKETS = "summary_buckets";
  }

  /**
   * Process incoming report, including updating the current attributed value and generate the
   * report should be deleted and number of new report to be generated
   *
   * @param reportSpec the Report Specification that process the incoming report
   * @param bucketIncrements number of the bucket increments
   * @param proposedEventReport incoming event report
   * @param currentReports existing reports retrieved from DB
   * @return The pair consist 1) The event reports should be deleted. Return empty list if no
   *     deletion is needed. 2) number of reports need to be created
   */
  public static Pair<List<EventReport>, Integer> processIncomingReport(
      ReportSpec reportSpec,
      int bucketIncrements,
      EventReport proposedEventReport,
      List<EventReport> currentReports) {
    if (bucketIncrements == 0
        || bucketIncrements + currentReports.size() <= reportSpec.getMaxReports()) {
      // No competing condition.
      return new Pair<>(new ArrayList<>(), bucketIncrements);
    }
    long triggerTime = proposedEventReport.getTriggerTime();
    reportSpec.insertAttributedTrigger(proposedEventReport);
    List<EventReport> pendingEventReports =
        currentReports.stream()
            .filter((r) -> r.getReportTime() > triggerTime)
            .collect(Collectors.toList());
    int numDeliveredReport =
        (int) currentReports.stream().filter((r) -> r.getReportTime() <= triggerTime).count();

    for (EventReport report : currentReports) {
      if (Objects.equals(report.getTriggerData(), proposedEventReport.getTriggerData())
          && report.getTriggerPriority() < proposedEventReport.getTriggerPriority()) {
        report.setTriggerPriority(proposedEventReport.getTriggerPriority());
      }
    }

    for (int i = 0; i < bucketIncrements; i++) {
      pendingEventReports.add(proposedEventReport);
    }

    List<EventReport> sortedEventReports =
        pendingEventReports.stream()
            .sorted(
                Comparator.comparing(EventReport::getReportTime, Comparator.reverseOrder())
                    .thenComparingLong(EventReport::getTriggerPriority)
                    .thenComparing(EventReport::getTriggerTime, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    int numOfNewReportGenerated = bucketIncrements;
    List<EventReport> result = new ArrayList<>();
    while (sortedEventReports.size() > reportSpec.getMaxReports() - numDeliveredReport
        && sortedEventReports.size() > 0) {
      EventReport lowestPriorityEventReport = sortedEventReports.remove(0);
      if (lowestPriorityEventReport.equals(proposedEventReport)) {
        // the new report fall into deletion set. New report count reduce 1 and no need to
        // add to the report to be deleted.
        numOfNewReportGenerated--;
      } else {
        result.add(lowestPriorityEventReport);
      }
    }
    return new Pair<>(result, numOfNewReportGenerated);
  }

  /**
   * @param reportSpec the report specification to process the report
   * @param proposedEventReport the incoming event report
   * @return number of bucket generated
   */
  public static int countBucketIncrements(ReportSpec reportSpec, EventReport proposedEventReport) {
    UnsignedLong proposedTriggerData = proposedEventReport.getTriggerData();
    List<Long> summaryWindows = getSummaryBucketsForTriggerData(reportSpec, proposedTriggerData);
    if (summaryWindows == null) {
      return 0;
    }
    long currentValue = reportSpec.findCurrentAttributedValue(proposedTriggerData);
    long incomingValue = proposedEventReport.getTriggerValue();
    // current value has already reached to the top of the bucket
    if (currentValue >= summaryWindows.get(summaryWindows.size() - 1)) {
      return 0;
    }

    int currentBucket = -1;
    int newBucket = -1;
    for (int i = 0; i < summaryWindows.size(); i++) {
      if (currentValue >= summaryWindows.get(i)) {
        currentBucket = i;
      }
      if (currentValue + incomingValue >= summaryWindows.get(i)) {
        newBucket = i;
      }
    }
    return newBucket - currentBucket;
  }

  /**
   * @param index the index of the summary bucket
   * @param summaryBuckets the summary bucket
   * @return return single summary bucket of the index
   */
  public static Pair<Long, Long> getSummaryBucketFromIndex(int index, List<Long> summaryBuckets) {
    return new Pair<>(
        summaryBuckets.get(index),
        index < summaryBuckets.size() - 1
            ? summaryBuckets.get(index + 1) - 1
            : Integer.MAX_VALUE - 1);
  }

  /**
   * @param reportSpec the report specification to process the report
   * @param deletingEventReport the report proposed to be deleted
   * @return number of bucket eliminated
   */
  public static int numDecrementingBucket(ReportSpec reportSpec, EventReport deletingEventReport) {
    UnsignedLong proposedEventReportDataType = deletingEventReport.getTriggerData();
    List<Long> summaryWindows =
        getSummaryBucketsForTriggerData(reportSpec, proposedEventReportDataType);
    if (summaryWindows == null) {
      return 0;
    }
    long currentValue = reportSpec.findCurrentAttributedValue(proposedEventReportDataType);
    long incomingValue = reportSpec.getTriggerValue(deletingEventReport.getTriggerId());
    // current value doesn't reach the 1st bucket
    if (currentValue < summaryWindows.get(0)) {
      return 0;
    }

    int currentBucket = -1;
    int newBucket = -1;
    for (int i = 0; i < summaryWindows.size(); i++) {
      if (currentValue >= summaryWindows.get(i)) {
        currentBucket = i;
      }
      if (currentValue - incomingValue >= summaryWindows.get(i)) {
        newBucket = i;
      }
    }
    return currentBucket - newBucket;
  }

  /**
   * Calculates the reporting time based on the {@link Trigger} time for flexible event report API
   *
   * @param reportSpec the report specification to be processed
   * @param sourceRegistrationTime source registration time
   * @param triggerTime trigger time
   * @param triggerData the trigger data
   * @return the reporting time
   */
  public static long getFlexEventReportingTime(
      ReportSpec reportSpec,
      long sourceRegistrationTime,
      long triggerTime,
      UnsignedLong triggerData) {
    if (triggerTime < sourceRegistrationTime) {
      return -1;
    }
    if (triggerTime
        < findReportingStartTimeForTriggerData(reportSpec, triggerData) + sourceRegistrationTime) {
      return -1;
    }

    List<Long> reportingWindows = findReportingEndTimesForTriggerData(reportSpec, triggerData);
    for (Long window : reportingWindows) {
      if (triggerTime <= window + sourceRegistrationTime) {
        return sourceRegistrationTime + window + TimeUnit.MINUTES.toMillis(60L);
      }
    }
    // If trigger time is larger than all window end time, it means the trigger has expired.
    return -1;
  }

  private static Long findReportingStartTimeForTriggerData(
      ReportSpec reportSpec, UnsignedLong triggerData) {
    for (TriggerSpec triggerSpec : reportSpec.getTriggerSpecs()) {
      if (triggerSpec.getTriggerData().contains(triggerData)) {
        return triggerSpec.getEventReportWindowsStart();
      }
    }
    return 0L;
  }

  private static List<Long> findReportingEndTimesForTriggerData(
      ReportSpec reportSpec, UnsignedLong triggerData) {
    for (TriggerSpec triggerSpec : reportSpec.getTriggerSpecs()) {
      if (triggerSpec.getTriggerData().contains(triggerData)) {
        return triggerSpec.getEventReportWindowsEnd();
      }
    }
    return new ArrayList<>();
  }

  /**
   * @param triggerData the trigger data
   * @return the summary bucket for specific trigger data
   */
  public static List<Long> getSummaryBucketsForTriggerData(
      ReportSpec reportSpec, UnsignedLong triggerData) {
    for (TriggerSpec triggerSpec : reportSpec.getTriggerSpecs()) {
      if (triggerSpec.getTriggerData().contains(triggerData)) {
        return triggerSpec.getSummaryBucket();
      }
    }
    return null;
  }
}
