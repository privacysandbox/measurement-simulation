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

import java.util.concurrent.TimeUnit;

public class Flags {

  boolean MEASUREMENT_ENABLE_DEBUG_REPORT = true;
  boolean MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT = true;
  boolean MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT = true;
  int MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION = 10;

  /** Disable early reporting windows configurability by default. */
  boolean MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS = false;

  /** Enable feature to unify destinations for event reports by default. */
  boolean DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS = true;

  /**
   * Default early reporting windows for VTC type source. Derived from {@link
   * PrivacyParams#EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS}.
   */
  String MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS = "";

  /**
   * Default early reporting windows for CTC type source. Derived from {@link
   * PrivacyParams#NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS}.
   */
  String MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS =
      String.join(
          ",",
          Long.toString(TimeUnit.DAYS.toSeconds(2)),
          Long.toString(TimeUnit.DAYS.toSeconds(7)));

  int MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

  long DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT = 5L;

  /** Returns whether verbose debug report generation is enabled. */
  public boolean getMeasurementEnableDebugReport() {
    return MEASUREMENT_ENABLE_DEBUG_REPORT;
  }

  /** Returns whether source debug report generation is enabled. */
  public boolean getMeasurementEnableSourceDebugReport() {
    return MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT;
  }

  /** Returns whether trigger debug report generation is enabled. */
  public boolean getMeasurementEnableTriggerDebugReport() {
    return MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT;
  }

  /**
   * Returns max distinct enrollments for attribution per { Advertiser X Publisher X TimePeriod }.
   */
  public int getMeasurementMaxDistinctEnrollmentsInAttribution() {
    return MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION;
  }

  /** Returns true if event reporting windows configurability is enabled, false otherwise. */
  public boolean getMeasurementEnableConfigurableEventReportingWindows() {
    return MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS;
  }

  /**
   * Returns true if event reporting destinations are enabled to be reported in a coarse manner,
   * i.e. both app and web destinations are merged into a single array in the event report.
   */
  public boolean getMeasurementEnableCoarseEventReportDestinations() {
    return DEFAULT_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS;
  }

  /**
   * Returns configured comma separated early VTC based source's event reporting windows in seconds.
   */
  public String getMeasurementEventReportsVtcEarlyReportingWindows() {
    return MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS;
  }

  /**
   * Returns configured comma separated early CTC based source's event reporting windows in seconds.
   */
  public String getMeasurementEventReportsCtcEarlyReportingWindows() {
    return MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS;
  }

  /**
   * Returns maximum attributions per rate limit window. Rate limit unit: (Source Site, Destination
   * Site, Reporting Site, Window).
   */
  public int getMeasurementMaxAttributionPerRateLimitWindow() {
    return MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
  }

  /** Returns the limit to the number of unique AdIDs attempted to match for debug keys. */
  public long getMeasurementPlatformDebugAdIdMatchingLimit() {
    return DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT;
  }
}
