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

import static com.google.measurement.PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.google.measurement.PrivacyParams.EVENT_SOURCE_MAX_REPORTS;
import static com.google.measurement.PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.google.measurement.PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS;
import static com.google.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.google.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
import static com.google.measurement.PrivacyParams.MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS;
import static com.google.measurement.PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.google.measurement.PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Does event report window related calculations, e.g. count, reporting time. */
public class EventReportWindowCalcDelegate {
  private static final Logger logger =
      Logger.getLogger(EventReportWindowCalcDelegate.class.getName());
  private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
  private static final String EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER = ",";
  private final Flags mFlags;

  public EventReportWindowCalcDelegate(Flags flags) {
    mFlags = flags;
  }

  /**
   * Max reports count based on conversion destination type and installation state.
   *
   * @return maximum number of reports allowed
   * @param isInstallCase is app installed
   */
  public int getMaxReportCount(Source source, boolean isInstallCase) {
    if (isInstallCase) {
      return source.getSourceType() == Source.SourceType.EVENT
          ? INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
          : INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
    }
    return source.getSourceType() == Source.SourceType.EVENT
        ? EVENT_SOURCE_MAX_REPORTS
        : NAVIGATION_SOURCE_MAX_REPORTS;
  }

  /**
   * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
   * trigger destination type.
   *
   * @return the reporting time
   */
  public long getReportingTime(Source source, long triggerTime, EventSurfaceType destinationType) {
    if (triggerTime < source.getEventTime()) {
      return -1;
    }
    // Cases where source could have both web and app destinations, there if the trigger
    // destination is an app and it was installed, then installState should be considered true.
    boolean isAppInstalled = isAppInstalled(source, destinationType);
    List<Pair<Long, Long>> earlyReportingWindows = getEarlyReportingWindows(source, isAppInstalled);
    for (Pair<Long, Long> window : earlyReportingWindows) {
      if (isWithinWindow(triggerTime, window)) {
        return window.second + PrivacyParams.EVENT_REPORT_DELAY;
      }
    }
    Pair<Long, Long> finalWindow = getFinalReportingWindow(source, earlyReportingWindows);
    if (isWithinWindow(triggerTime, finalWindow)) {
      return finalWindow.second + PrivacyParams.EVENT_REPORT_DELAY;
    }
    return source.getEventReportWindow().longValue() + PrivacyParams.EVENT_REPORT_DELAY;
  }

  private boolean isWithinWindow(long time, Pair<Long, Long> window) {
    // TODO Need to update the E2E test cases to change to exclusive
    return window.first <= time && time <= window.second;
  }

  private Pair<Long, Long> getFinalReportingWindow(
      Source source, List<Pair<Long, Long>> earlyWindows) {
    long secondToLastWindowEnd =
        !earlyWindows.isEmpty() ? earlyWindows.get(earlyWindows.size() - 1).second : 0;
    if (source.getProcessedEventReportWindow() != null) {
      return new Pair<>(secondToLastWindowEnd, source.getProcessedEventReportWindow());
    }
    return new Pair<>(secondToLastWindowEnd, source.getExpiryTime());
  }

  /**
   * Return reporting time by index for noising based on the index
   *
   * @param windowIndex index of the reporting window for which
   * @return reporting time in milliseconds
   */
  public long getReportingTimeForNoising(Source source, int windowIndex, boolean isInstallCase) {
    List<Pair<Long, Long>> earlyWindows = getEarlyReportingWindows(source, isInstallCase);
    Pair<Long, Long> finalWindow = getFinalReportingWindow(source, earlyWindows);
    return windowIndex < earlyWindows.size()
        ? earlyWindows.get(windowIndex).second + PrivacyParams.EVENT_REPORT_DELAY
        : finalWindow.second + PrivacyParams.EVENT_REPORT_DELAY;
  }

  /**
   * Returns effective, i.e. the ones that occur before {@link Source#getEventReportWindow()}, event
   * reporting windows count for noising cases.
   *
   * @param source source for which the count is requested
   * @param isInstallCase true of cooldown window was specified
   */
  public int getReportingWindowCountForNoising(Source source, boolean isInstallCase) {
    // Early Count + expiry
    return getEarlyReportingWindows(source, isInstallCase).size() + 1;
  }

  /**
   * Returns reporting time for noising with flex event API.
   *
   * @param windowIndex window index corresponding to which the reporting time should be returned
   * @param triggerDataIndex trigger data state index
   * @param reportSpec flex event report spec
   */
  public long getReportingTimeForNoisingFlexEventAPI(
      int windowIndex, int triggerDataIndex, ReportSpec reportSpec) {
    return reportSpec.getWindowEndTime(triggerDataIndex, windowIndex) + ONE_HOUR_IN_MILLIS;
  }

  private boolean isAppInstalled(Source source, EventSurfaceType destinationType) {
    return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
  }

  /**
   * If the flag is enabled and the specified report windows are valid, picks from flag controlled
   * configurable early reporting windows. Otherwise, falls back to the statical {@link
   * com.google.measurement.PrivacyParams} values. It curtails the windows that occur after {@link
   * Source#getProcessedEventReportWindow()} because they would effectively be unusable.
   */
  private List<Pair<Long, Long>> getEarlyReportingWindows(Source source, boolean installState) {
    List<Long> earlyWindows;
    List<Long> defaultEarlyWindows =
        getDefaultEarlyReportingWindows(source.getSourceType(), installState);
    earlyWindows =
        getConfiguredOrDefaultEarlyReportingWindows(source.getSourceType(), defaultEarlyWindows);
    // Add source event time to windows
    earlyWindows =
        earlyWindows.stream().map((x) -> source.getEventTime() + x).collect(Collectors.toList());

    List<Pair<Long, Long>> windowList = new ArrayList<>();
    long windowStart = 0;
    Pair<Long, Long> finalWindow =
        getFinalReportingWindow(source, createStartEndWindow(earlyWindows));
    for (long windowEnd : earlyWindows) {
      if (finalWindow.second <= windowEnd) {
        continue;
      }
      windowList.add(new Pair<>(windowStart, windowEnd));
      windowStart = windowEnd;
    }
    return ImmutableList.copyOf(windowList);
  }

  private List<Pair<Long, Long>> createStartEndWindow(List<Long> windowEnds) {
    List<Pair<Long, Long>> windows = new ArrayList<>();
    long start = 0;
    for (Long end : windowEnds) {
      windows.add(new Pair<>(start, end));
      start = end;
    }
    return windows;
  }

  /**
   * Returns the default early reporting windows
   *
   * @param sourceType Source's Type
   * @param installState Install State of the source
   * @return a list of windows
   */
  public static List<Long> getDefaultEarlyReportingWindows(
      Source.SourceType sourceType, boolean installState) {
    long[] earlyWindows;
    if (installState) {
      earlyWindows =
          sourceType == Source.SourceType.EVENT
              ? INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
              : INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
    } else {
      earlyWindows =
          sourceType == Source.SourceType.EVENT
              ? EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
              : NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
    }
    return asList(earlyWindows);
  }

  private static List<Long> asList(long[] values) {
    final List<Long> list = new ArrayList<>();
    for (Long value : values) {
      list.add(value);
    }
    return list;
  }

  public List<Long> getConfiguredOrDefaultEarlyReportingWindows(
      Source.SourceType sourceType, List<Long> defaultEarlyWindows) {

    if (mFlags == null || !mFlags.getMeasurementEnableConfigurableEventReportingWindows()) {
      return defaultEarlyWindows;
    }
    String earlyReportingWindowsString = pickEarlyReportingWindowsConfig(mFlags, sourceType);
    if (earlyReportingWindowsString == null) {
      logger.info("Invalid configurable early reporting windows; null");
      return defaultEarlyWindows;
    }
    if (earlyReportingWindowsString.isEmpty()) {
      // No early reporting windows specified. It needs to be handled separately because
      // splitting an empty string results into an array containing a single element,
      // i.e. "". We want to handle it as an array having no element.
      if (Source.SourceType.EVENT.equals(sourceType)) {
        // We need to add a reporting window at 2d for post-install case. Non-install case
        // has no early reporting window by default.
        return defaultEarlyWindows;
      }
      return Collections.emptyList();
    }
    ImmutableList.Builder<Long> earlyWindows = new ImmutableList.Builder<>();
    String[] split = earlyReportingWindowsString.split(EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER);
    if (split.length > MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS) {
      logger.info(
          "Invalid configurable early reporting window; more than allowed size: "
              + MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS);
      return defaultEarlyWindows;
    }
    for (String window : split) {
      try {
        earlyWindows.add(TimeUnit.SECONDS.toMillis(Long.parseLong(window)));
      } catch (NumberFormatException e) {
        logger.info("Configurable early reporting window parsing failed.");
        return defaultEarlyWindows;
      }
    }
    return earlyWindows.build();
  }

  private String pickEarlyReportingWindowsConfig(Flags flags, Source.SourceType sourceType) {
    return sourceType == Source.SourceType.EVENT
        ? flags.getMeasurementEventReportsVtcEarlyReportingWindows()
        : flags.getMeasurementEventReportsCtcEarlyReportingWindows();
  }
}
