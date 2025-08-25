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

package com.google.measurement.client;

import static com.google.measurement.client.FlagsConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Flags {
  private Map<String, String> mFlagsMap;

  public Flags() {
    mFlagsMap = new HashMap<>();
  }

  public void setFlag(String key, String value) {
    mFlagsMap.put(key, value);
  }

  public void clearFlags() {
    mFlagsMap.clear();
  }

  /**
   * Global PP API Kill Switch. This overrides all other killswitches. The default value is false
   * which means the PP API is enabled. This flag is used for emergency turning off the whole PP
   * API.
   */
  // Starting M-2023-05, global kill switch is enabled in the binary. Prior to this (namely in
  // M-2022-11), the value of this flag in the binary was false.
  boolean GLOBAL_KILL_SWITCH = true;

  String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";

  public boolean getGlobalKillSwitch() {
    return getBooleanFlag(KEY_GLOBAL_KILL_SWITCH, GLOBAL_KILL_SWITCH);
  }

  // MEASUREMENT Killswitches

  /**
   * Measurement Kill Switch. This overrides all specific measurement kill switch. The default value
   * is {@code false} which means that Measurement is enabled.
   *
   * <p>This flag is used for emergency turning off the whole Measurement API.
   */
  boolean MEASUREMENT_KILL_SWITCH = false;

  String KEY_MEASUREMENT_KILL_SWITCH = "measurement_kill_switch";

  /**
   * @deprecated - TODO(b/325074749): remove once all methods that call it are unit-tested and
   *     changed to use !getMeasurementEnabled()
   */
  @Deprecated
  @VisibleForTesting
  public boolean getLegacyMeasurementKillSwitch() {
    return getBooleanFlag(
        KEY_MEASUREMENT_KILL_SWITCH, getGlobalKillSwitch() || MEASUREMENT_KILL_SWITCH);
  }

  /**
   * Default early reporting windows for CTC type source. Derived from {@link
   * com.android.adservices.service.measurement.PrivacyParams#NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS}.
   */
  public static String MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS =
      String.join(
          ",",
          Long.toString(TimeUnit.DAYS.toSeconds(2)),
          Long.toString(TimeUnit.DAYS.toSeconds(7)));

  public static boolean MEASUREMENT_ENABLE_DEBUG_REPORT = true;
  public static boolean MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT = true;
  public static boolean MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT = true;
  public static int MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION = 10;

  /** Disable early reporting windows configurability by public. */
  public static boolean MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS = false;

  /** Enable feature to unify destinations for event reports by public. */
  public static boolean MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS = true;

  public static long MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION = (1L << 32) - 1L;

  /**
   * public early reporting windows for VTC type source. Derived from {@link
   * PrivacyParams#EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS}.
   */
  public static String MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS = "";

  public static int MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

  public long MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT = 5L;

  /** Returns whether verbose debug report generation is enabled. */
  public boolean getMeasurementEnableDebugReport() {
    return getBooleanFlag(KEY_MEASUREMENT_ENABLE_DEBUG_REPORT, MEASUREMENT_ENABLE_DEBUG_REPORT);
  }

  /** Returns whether source debug report generation is enabled. */
  public boolean getMeasurementEnableSourceDebugReport() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT, MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT);
  }

  /** Returns whether trigger debug report generation is enabled. */
  public boolean getMeasurementEnableTriggerDebugReport() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT, MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT);
  }

  /**
   * Returns max distinct enrollments for attribution per { Advertiser X Publisher X TimePeriod }.
   */
  public int getMeasurementMaxDistinctEnrollmentsInAttribution() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION,
        MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION);
  }

  /** Returns true if event reporting windows configurability is enabled, false otherwise. */
  public boolean getMeasurementEnableConfigurableEventReportingWindows() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS,
        MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS);
  }

  /**
   * Returns true if event reporting destinations are enabled to be reported in a coarse manner,
   * i.e. both app and web destinations are merged into a single array in the event report.
   */
  public boolean getMeasurementEnableCoarseEventReportDestinations() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS,
        MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS);
  }

  /**
   * Returns configured comma separated early VTC based source's event reporting windows in seconds.
   */
  public String getMeasurementEventReportsVtcEarlyReportingWindows() {
    return getStringFlag(
        KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS,
        MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS);
  }

  /**
   * Returns configured comma separated early CTC based source's event reporting windows in seconds.
   */
  public String getMeasurementEventReportsCtcEarlyReportingWindows() {
    return getStringFlag(
        KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS,
        MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
  }

  /**
   * Returns maximum attributions per rate limit window. Rate limit unit: (Source Site, Destination
   * Site, Reporting Site, Window).
   */
  public int getMeasurementMaxAttributionPerRateLimitWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
        MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
  }

  /** Returns the limit to the number of unique AdIDs attempted to match for debug keys. */
  public long getMeasurementPlatformDebugAdIdMatchingLimit() {
    return getLongFlag(
        KEY_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT,
        MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT);
  }

  /** Returns max repot states per source registration */
  public long getMeasurementMaxReportStatesPerSourceRegistration() {
    return getLongFlag(
        KEY_MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION,
        MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
  }

  /** Flag for enabling measurement event level epsilon in source */
  public static boolean MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE = false;

  public boolean getMeasurementEnableEventLevelEpsilonInSource() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE,
        MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE);
  }

  /** public max allowed number of event reports. */
  public static int DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT = 1;

  /** Returns the public max allowed number of event reports. */
  public int getMeasurementVtcConfigurableMaxEventReportsCount() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT,
        DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
  }

  public static boolean MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE = false;

  /** Returns true when attribution scope is enabled. */
  public boolean getMeasurementEnableAttributionScope() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE, MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE);
  }

  public static float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT = 6.5F;

  /** Returns max information gain in Flexible Event API for Event sources */
  public float getMeasurementFlexApiMaxInformationGainEvent() {
    return getFloatFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT,
        MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
  }

  public static float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION = 11.5F;

  /** Returns max information gain in Flexible Event API for Navigation sources */
  public float getMeasurementFlexApiMaxInformationGainNavigation() {
    return getFloatFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION,
        MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);
  }

  public static float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT = 6.5F;

  /** Returns max information gain for Flexible Event, dual destination Event sources */
  public float getMeasurementFlexApiMaxInformationGainDualDestinationEvent() {
    return getFloatFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT,
        MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);
  }

  public static float MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION = 11.5F;

  /** Returns max information gain for Flexible Event, dual destination Navigation sources */
  public float getMeasurementFlexApiMaxInformationGainDualDestinationNavigation() {
    return getFloatFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION,
        MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION);
  }

  public static float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION = 11.55F;

  /** Returns max information gain for navigation sources with attribution scopes. */
  public float getMeasurementAttributionScopeMaxInfoGainNavigation() {
    return getFloatFlag(
        KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION,
        MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION);
  }

  public static float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION =
      11.55F;

  /**
   * Returns max information gain for navigation sources with dual destination and attribution
   * scopes.
   */
  public float getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation() {
    return getFloatFlag(
        KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION,
        MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION);
  }

  public static float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT = 6.5F;

  /** Returns max information gain for event sources with attribution scopes. */
  public float getMeasurementAttributionScopeMaxInfoGainEvent() {
    return getFloatFlag(
        KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT,
        MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT);
  }

  public static float MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT = 6.5F;

  /**
   * Returns max information gain for event sources with dual destination and attribution scopes.
   */
  public float getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent() {
    return getFloatFlag(
        KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT,
        MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT);
  }

  public static float DEFAULT_MEASUREMENT_PRIVACY_EPSILON = 14f;

  public float getMeasurementPrivacyEpsilon() {
    return getFloatFlag(
        KEY_DEFAULT_MEASUREMENT_PRIVACY_EPSILON, DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
  }

  public static boolean MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER = false;

  /** Returns true if lookback window filter is enabled else false. */
  public boolean getMeasurementEnableLookbackWindowFilter() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER, MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER);
  }

  public static boolean MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME = false;

  /** Returns true if fake report trigger time is enabled. */
  public boolean getMeasurementEnableFakeReportTriggerTime() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME,
        MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME);
  }

  /** Default value for Measurement flexible event reporting API */
  public static boolean MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED = false;

  /** Returns whether to enable Measurement flexible event reporting API */
  public boolean getMeasurementFlexibleEventReportingApiEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED,
        MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED);
  }

  /* The list of origins for creating a URL used to fetch public encryption keys for
  aggregatable reports. AWS is the current default. */
  public static String MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN =
      "https://publickeyservice.msmt.aws.privacysandboxservices.com";

  /**
   * Returns the default origin for creating the URI used to fetch public encryption keys for
   * aggregatable reports.
   */
  public String getMeasurementDefaultAggregationCoordinatorOrigin() {
    return getStringFlag(
        KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
        MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN);
  }

  public static int DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID = 25;

  /** Maximum number of bytes allowed in an aggregate key ID. */
  public int getMeasurementMaxBytesPerAttributionAggregateKeyId() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID,
        DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID);
  }

  public static int DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET = 20;

  /** Maximum number of filter maps allowed in an attribution filter set. */
  public int getMeasurementMaxFilterMapsPerFilterSet() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET,
        DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET);
  }

  /** Default value for whether header error debug report is enabled. */
  public static boolean MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT = false;

  /** Returns whether header error debug report generation is enabled. */
  public boolean getMeasurementEnableHeaderErrorDebugReport() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT,
        MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT);
  }

  public static int DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS = 50;

  /** Maximum number of attribution filters allowed for a source. */
  public int getMeasurementMaxAttributionFilters() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS,
        DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS);
  }

  public int DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING = 25;

  /** Maximum number of bytes allowed in an attribution filter string. */
  public int getMeasurementMaxBytesPerAttributionFilterString() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING,
        DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING);
  }

  public static int DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER = 50;

  /** Maximum number of values allowed in an attribution filter. */
  public int getMeasurementMaxValuesPerAttributionFilter() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER,
        DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER);
  }

  public static int MEASUREMENT_MAX_REGISTRATION_REDIRECTS = 20;

  /** Returns the number of maximum registration redirects allowed. */
  public int getMeasurementMaxRegistrationRedirects() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS, MEASUREMENT_MAX_REGISTRATION_REDIRECTS);
  }

  public static long MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
      TimeUnit.DAYS.toSeconds(1);

  /** Min expiration value in seconds for attribution reporting register source. */
  public long getMeasurementMinReportingRegisterSourceExpirationInSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
        MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
  }

  public static long MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
      TimeUnit.DAYS.toSeconds(30);

  /**
   * Max expiration value in seconds for attribution reporting register source. This value is also
   * the default if no expiration was specified.
   */
  public long getMeasurementMaxReportingRegisterSourceExpirationInSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
        MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
  }

  public static long MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS =
      TimeUnit.HOURS.toSeconds(1);

  /** Returns minimum event report window */
  public long getMeasurementMinimumEventReportWindowInSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS,
        MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS);
  }

  public static long MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS =
      TimeUnit.HOURS.toSeconds(1);

  /** Returns minimum aggregatable report window */
  public long getMeasurementMinimumAggregatableReportWindowInSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS,
        MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS);
  }

  public static long MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(30);

  /** Maximum limit of duration to determine attribution for a verified installation. */
  public long getMeasurementMaxInstallAttributionWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW, MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW);
  }

  public static long MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(1);

  /** Minimum limit of duration to determine attribution for a verified installation. */
  public long getMeasurementMinInstallAttributionWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW, MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW);
  }

  public static long MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW = TimeUnit.DAYS.toSeconds(30);

  /** Maximum acceptable install cooldown period. */
  public long getMeasurementMaxPostInstallExclusivityWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW,
        MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
  }

  public long MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW = 0L;

  /** Default and minimum value for cooldown period of source which led to installation. */
  public long getMeasurementMinPostInstallExclusivityWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
        MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW);
  }

  public static boolean MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION = false;

  /** Returns whether to enable reinstall reattribution. */
  public boolean getMeasurementEnableReinstallReattribution() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION, MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION);
  }

  public static long MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS =
      TimeUnit.DAYS.toSeconds(90);

  /** Maximum limit of duration to determine reattribution for a verified installation. */
  public long getMeasurementMaxReinstallReattributionWindowSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS,
        MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS);
  }

  /**
   * Default blocklist of the enrollments for whom debug key insertion based on AdID matching is
   * blocked.
   */
  public String DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST = "*";

  /**
   * Blocklist of the enrollments for whom debug key insertion based on AdID matching is blocked.
   */
  public String getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist() {
    return getStringFlag(
        KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST,
        DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST);
  }

  /**
   * Default allowlist of the enrollments for whom debug key insertion based on join key matching is
   * allowed.
   */
  public String DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST = "";

  /**
   * Allowlist of the enrollments for whom debug key insertion based on join key matching is
   * allowed.
   */
  public String getMeasurementDebugJoinKeyEnrollmentAllowlist() {
    return getStringFlag(
        KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST,
        DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST);
  }

  /** Privacy Params */
  public static int MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION = 3;

  /** Max distinct web destinations in a source registration. */
  public int getMeasurementMaxDistinctWebDestinationsInSourceRegistration() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION,
        MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION);
  }

  /** Default value for Measurement trigger data matching */
  public static boolean MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING = true;

  /** Returns whether to enable Measurement trigger data matching */
  public boolean getMeasurementEnableTriggerDataMatching() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING, MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING);
  }

  public static int MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS = 20;

  /** Returns max event reports in Flexible Event API */
  public int getMeasurementFlexApiMaxEventReports() {
    return getIntFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS, MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS);
  }

  /** Default value for Measurement V1 source trigger data */
  public static boolean MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA = false;

  /** Returns whether to enable Measurement V1 source trigger data */
  public boolean getMeasurementEnableV1SourceTriggerData() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA, MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA);
  }

  public static boolean MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY = true;

  /** Enable/disable shared_debug_key processing from source RBR. */
  public boolean getMeasurementEnableSharedSourceDebugKey() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY, MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY);
  }

  public static boolean MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS = false;

  /**
   * Enable deletion of reports along with FIFO destinations. In practice it's a sub flag to {@link
   * #getMeasurementEnableSourceDestinationLimitPriority()}
   */
  public boolean getMeasurementEnableFifoDestinationsDeleteAggregateReports() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS,
        MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS);
  }

  public static boolean MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY = false;

  public boolean getMeasurementEnableSourceDestinationLimitPriority() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY,
        MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY);
  }

  public static boolean MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD = false;

  public boolean getMeasurementEnableSourceDestinationLimitAlgorithmField() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD,
        MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD);
  }

  /**
   * Default destination limit algorithm configuration. LIFO (0) by default, can be configured as
   * FIFO (1).
   */
  public static int MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM = 0;

  public int getMeasurementDefaultSourceDestinationLimitAlgorithm() {
    return getIntFlag(
        KEY_MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM,
        MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM);
  }

  public static boolean MEASUREMENT_ENABLE_XNA = false;

  /** Returns whether XNA should be used for eligible sources. */
  public boolean getMeasurementEnableXNA() {
    return getBooleanFlag(KEY_MEASUREMENT_ENABLE_XNA, MEASUREMENT_ENABLE_XNA);
  }

  public static boolean MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA = true;

  /** Enable/disable shared_filter_data_keys processing from source RBR. */
  public boolean getMeasurementEnableSharedFilterDataKeysXNA() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA,
        MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA);
  }

  public static boolean MEASUREMENT_ENABLE_PREINSTALL_CHECK = false;

  /** Returns true when pre-install check is enabled. */
  public boolean getMeasurementEnablePreinstallCheck() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_PREINSTALL_CHECK, MEASUREMENT_ENABLE_PREINSTALL_CHECK);
  }

  public static int MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE = 20;

  /** Returns max number of attribution scopes per source. */
  public int getMeasurementMaxAttributionScopesPerSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE,
        MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE);
  }

  public static int MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH = 50;

  /** Returns max length of attribution scope. */
  public int getMeasurementMaxAttributionScopeLength() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH, MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH);
  }

  public static int MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY = 32;

  /** Returns max trigger data cardinality in Flexible Event API */
  public int getMeasurementFlexApiMaxTriggerDataCardinality() {
    return getIntFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY,
        MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY);
  }

  public static int MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS = 5;

  /** Returns max event report windows in Flexible Event API */
  public int getMeasurementFlexApiMaxEventReportWindows() {
    return getIntFlag(
        KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS,
        MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS);
  }

  /**
   * Default value for if events will be registered as a source of attribution in addition to being
   * reported.
   */
  public static boolean FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED = false;

  /**
   * Returns if events will be registered as a source of attribution in addition to being reported.
   *
   * <p>This, unlocked by the short-term integration between Protected Audience (PA) and
   * Measurement's ARA, enables the {@link
   * android.adservices.adselection.AdSelectionManager#reportEvent} API to report an event and
   * register it as source of attribution, using a single API call, unified under the hood.
   *
   * <ul>
   *   <li>When enabled, by default: ARA will report and register the event.
   *   <li>When enabled, with fallback: PA will report the event and ARA will register the event.
   *   <li>When disabled, when {@link android.adservices.adselection.AdSelectionManager#reportEvent}
   *       is called, only PA will report the event.
   * </ul>
   */
  public boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
    return getBooleanFlag(
        KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED,
        FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED);
  }

  public static boolean DISABLE_MEASUREMENT_ENROLLMENT_CHECK = false;

  /** Returns {@code true} if the Measurement APIs should disable the ad tech enrollment check */
  public boolean isDisableMeasurementEnrollmentCheck() {
    return getBooleanFlag(
        KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK, DISABLE_MEASUREMENT_ENROLLMENT_CHECK);
  }

  /** Maximum number of aggregation keys allowed during source registration. */
  public static int MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION = 50;

  /** Returns maximum number of aggregation keys allowed during source registration. */
  public int getMeasurementMaxAggregateKeysPerSourceRegistration() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION,
        MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION);
  }

  public static long MEASUREMENT_DB_SIZE_LIMIT = (1024 * 1024) * 10; // 10 MBs

  public long getMeasurementDbSizeLimit() {
    return getLongFlag(KEY_MEASUREMENT_DB_SIZE_LIMIT, MEASUREMENT_DB_SIZE_LIMIT);
  }

  public static boolean MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED = true;

  /** Returns Whether to limit number of Retries for Measurement Reports */
  public boolean getMeasurementReportingRetryLimitEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED, MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED);
  }

  public static int MEASUREMENT_REPORT_RETRY_LIMIT = 3;

  /** Returns Maximum number of Retries for Measurement Reportss */
  public int getMeasurementReportingRetryLimit() {
    return getIntFlag(KEY_MEASUREMENT_REPORT_RETRY_LIMIT, MEASUREMENT_REPORT_RETRY_LIMIT);
  }

  public long DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW =
      TimeUnit.MINUTES.toMillis(2);

  /** Maximum window for a delayed source to be considered valid instead of missed. */
  public long getMeasurementMaxDelayedSourceRegistrationWindow() {
    return getLongFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW,
        DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW);
  }

  public static long MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS = TimeUnit.DAYS.toMillis(30);

  /**
   * Rate limit window for (Source Site, Destination Site, Reporting Site, Window) privacy unit. 30
   * days.
   */
  public long getMeasurementRateLimitWindowMilliseconds() {
    return getLongFlag(
        KEY_MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS, MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS);
  }

  /** Default value for Null Aggregate Report feature flag. */
  public static boolean MEASUREMENT_NULL_AGGREGATE_REPORT_ENABLED = false;

  /** Null Aggregate Report feature flag. */
  public boolean getMeasurementNullAggregateReportEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_NULL_AGGREGATE_REPORT_ENABLED, MEASUREMENT_NULL_AGGREGATE_REPORT_ENABLED);
  }

  /**
   * Disable measurement datastore to throw {@link
   * com.android.adservices.data.measurement.DatastoreException} when it occurs by default.
   */
  public static boolean MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION = false;

  /**
   * If enabled, measurement DatastoreManager can throw DatastoreException wrapped in an unchecked
   * exception.
   */
  public boolean getMeasurementEnableDatastoreManagerThrowDatastoreException() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION,
        MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION);
  }

  /** Set the sampling rate to 100% for unknown exceptions to be re-thrown. */
  public static float MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE = 1.0f;

  /** Sampling rate to decide whether to throw unknown exceptions for measurement. */
  public float getMeasurementThrowUnknownExceptionSamplingRate() {
    return getFloatFlag(
        KEY_MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE,
        MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE);
  }

  public static boolean MEASUREMENT_ENABLE_AGGREGATABLE_REPORT_PAYLOAD_PADDING = false;

  /** Returns true if aggregatable report padding is enabled else false. */
  public boolean getMeasurementEnableAggregatableReportPayloadPadding() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_AGGREGATABLE_REPORT_PAYLOAD_PADDING,
        MEASUREMENT_ENABLE_AGGREGATABLE_REPORT_PAYLOAD_PADDING);
  }

  public static int DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION = 100;

  /** Max number of {@link Trigger} to process per job for {@link AttributionJobService} */
  public int getMeasurementMaxAttributionsPerInvocation() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION,
        DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION);
  }

  /** Default Measurement source deactivation after filtering feature flag. */
  public static boolean MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING = false;

  /** Returns whether Measurement source deactivation after filtering feature is enabled. */
  public boolean getMeasurementEnableSourceDeactivationAfterFiltering() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING,
        MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING);
  }

  /** Default maximum Aggregate Reports per destination */
  public static int MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION = 1024;

  /** Returns maximum Aggregate Reports per publisher */
  public int getMeasurementMaxAggregateReportsPerDestination() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION,
        MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION);
  }

  /** Maximum Aggregate Reports per source. */
  public static int MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE = 20;

  /** Returns maximum Aggregate Reports per source. */
  public int getMeasurementMaxAggregateReportsPerSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE,
        MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE);
  }

  /** Default value for Optional Source Registration Time feature flag. */
  public static boolean MEASUREMENT_SOURCE_REGISTRATION_TIME_OPTIONAL_FOR_AGG_REPORTS_ENABLED =
      false;

  /** Returns true if source registration time is optional for aggregatable reports. */
  public boolean getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_SOURCE_REGISTRATION_TIME_OPTIONAL_FOR_AGG_REPORTS_ENABLED,
        MEASUREMENT_SOURCE_REGISTRATION_TIME_OPTIONAL_FOR_AGG_REPORTS_ENABLED);
  }

  /** Default value for null report rate excluding source registration time. */
  public static float MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME = .05f;

  /**
   * Returns the rate at which null aggregate reports are generated whenever the trigger is
   * configured to exclude the source registration time and there is no matching source.
   */
  public float getMeasurementNullAggReportRateExclSourceRegistrationTime() {
    return getFloatFlag(
        KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME,
        MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME);
  }

  /** Default value for null aggregate report rate including source registration time. */
  public static float MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME = .008f;

  /**
   * Returns the rate at which null aggregate reports are generated whenever an actual aggregate
   * report is successfully generated.
   */
  public float getMeasurementNullAggReportRateInclSourceRegistrationTime() {
    return getFloatFlag(
        KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME,
        MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME);
  }

  /** Default Measurement ARA parsing alignment v1 feature flag. */
  public static boolean MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1 = true;

  /** Returns whether Measurement ARA deduplication alignment v1 feature is enabled. */
  public boolean getMeasurementEnableAraDeduplicationAlignmentV1() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1,
        MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1);
  }

  /** Default maximum Event Reports per destination */
  public static int MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION = 1024;

  /** Returns maximum Event Reports per destination */
  public int getMeasurementMaxEventReportsPerDestination() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION,
        MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION);
  }

  public static int MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

  /**
   * Returns maximum event attributions per rate limit window. Rate limit unit: (Source Site,
   * Destination Site, Reporting Site, Window).
   */
  public int getMeasurementMaxEventAttributionPerRateLimitWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
        MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
  }

  public static int MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

  /**
   * Returns maximum aggregate attributions per rate limit window. Rate limit unit: (Source Site,
   * Destination Site, Reporting Site, Window).
   */
  public int getMeasurementMaxAggregateAttributionPerRateLimitWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW,
        MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW);
  }

  boolean MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT = false;

  /**
   * Enables separate debug report types for event and aggregate attribution rate limit violations.
   */
  public boolean getMeasurementEnableSeparateDebugReportTypesForAttributionRateLimit() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT,
        MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT);
  }

  public static int MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION = 10;

  /**
   * Returns max distinct reporting origins for attribution per { Advertiser X Publisher X
   * TimePeriod }.
   */
  public int getMeasurementMaxDistinctReportingOriginsInAttribution() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION,
        MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION);
  }

  /**
   * Default aggregate report delay. Derived from {@link
   * com.android.adservices.service.measurement.PrivacyParams#AGGREGATE_REPORT_MIN_DELAY} and {@link
   * com.android.adservices.service.measurement.PrivacyParams#AGGREGATE_REPORT_DELAY_SPAN}.
   */
  public static String MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG =
      String.join(
          ",",
          Long.toString(TimeUnit.MINUTES.toMillis(0L)),
          Long.toString(TimeUnit.MINUTES.toMillis(10L)));

  public static String KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG =
      "measurement_aggregate_report_delay_config";

  /**
   * Returns configured comma separated aggregate report min delay and aggregate report delay span.
   */
  public String getMeasurementAggregateReportDelayConfig() {
    return getStringFlag(
        KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG, MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG);
  }

  public static long MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW = TimeUnit.DAYS.toMillis(1);

  /** Minimum time window after which reporting origin can be migrated */
  public long getMeasurementMinReportingOriginUpdateWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW,
        MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW);
  }

  /**
   * Default value for delaying reporting job service so that reports can be batched. Values are in
   * milliseconds.
   */
  public static long MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS =
      TimeUnit.MINUTES.toMillis(30);

  /**
   * Returns ms to defer transmission of reports in {@link
   * com.android.adservices.service.measurement.reporting.ReportingJobService}
   */
  public long getMeasurementReportingJobServiceBatchWindowMillis() {
    return getLongFlag(
        KEY_MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS,
        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS);
  }

  /** The maximum allowable length of a trigger context id. */
  public static int MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID = 64;

  /** Return the maximum allowable length of a trigger context id. */
  public int getMeasurementMaxLengthOfTriggerContextId() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID,
        MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID);
  }

  /**
   * The client app packages that are allowed to invoke web context APIs, i.e. {@link
   * android.adservices.measurement.MeasurementManager#registerWebSource} and {@link
   * android.adservices.measurement.MeasurementManager#deleteRegistrations}. App packages that do
   * not belong to the list will be responded back with an error response.
   */
  public static String WEB_CONTEXT_CLIENT_ALLOW_LIST = "";

  public String getWebContextClientAppAllowList() {
    return getStringFlag(KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, WEB_CONTEXT_CLIENT_ALLOW_LIST);
  }

  public static boolean MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT = false;

  /** Returns true when the new trigger registration header size limitation are applied. */
  public boolean getMeasurementEnableUpdateTriggerHeaderLimit() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT,
        MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT);
  }

  public static long MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES = 250 * 1024; // 250 kB

  /** Returns max allowed size in bytes for trigger registrations header. */
  public long getMaxTriggerRegistrationHeaderSizeBytes() {
    return getLongFlag(
        KEY_MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES, MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES);
  }

  public static boolean MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS = false;

  /*
   * Returns whether filtering in Trigger's AGGREGATABLE_VALUES is allowed.
   */
  public boolean getMeasurementEnableAggregateValueFilters() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS, MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS);
  }

  public static boolean MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED = true;

  /** Returns true if aggregation coordinator origin is enabled. */
  public boolean getMeasurementAggregationCoordinatorOriginEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED,
        MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED);
  }

  /**
   * Default list(comma-separated) of origins for creating a URL used to fetch public encryption
   * keys for aggregatable reports.
   */
  public static String MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST =
      "https://publickeyservice.msmt.aws.privacysandboxservices.com,"
          + "https://publickeyservice.msmt.gcp.privacysandboxservices.com";

  /**
   * Returns a string which is a comma separated list of origins used to fetch public encryption
   * keys for aggregatable reports.
   */
  public String getMeasurementAggregationCoordinatorOriginList() {
    return getStringFlag(
        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
        MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST);
  }

  /** Flag to enable context id for triggers */
  public static boolean MEASUREMENT_ENABLE_TRIGGER_CONTEXT_ID = false;

  /** Returns true if trigger context id is enabled. */
  public boolean getMeasurementEnableTriggerContextId() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_TRIGGER_CONTEXT_ID, MEASUREMENT_ENABLE_TRIGGER_CONTEXT_ID);
  }

  /** Maximum number of aggregation keys allowed during trigger registration. */
  public static int MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION = 50;

  /** Returns maximum number of aggregation keys allowed during trigger registration. */
  public int getMeasurementMaxAggregateKeysPerTriggerRegistration() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION,
        MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION);
  }

  public static int MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE = 65536;

  /**
   * L1, the maximum sum of the contributions (values) across all buckets for a given source event.
   */
  public int getMeasurementMaxSumOfAggregateValuesPerSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE,
        MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE);
  }

  public static int DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION = 50;

  /** Maximum number of aggregate deduplication keys allowed during trigger registration. */
  public int getMeasurementMaxAggregateDeduplicationKeysPerRegistration() {
    return getIntFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION,
        DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION);
  }

  /** Flag for enabling measurement registrations using ODP */
  public static boolean MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION = false;

  /** Return true if measurement registrations through ODP is enabled */
  public boolean getMeasurementEnableOdpWebTriggerRegistration() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION,
        MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION);
  }

  public static int MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST = 5;

  /** Returns the number of maximum retires per registration request. */
  public int getMeasurementMaxRetriesPerRegistrationRequest() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST,
        MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST);
  }

  public static long DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT = 100L;

  /** Returns debug keys hash limit. */
  public long getMeasurementDebugJoinKeyHashLimit() {
    return getLongFlag(
        KEY_DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT,
        DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT);
  }

  public static long MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES = 16 * 1024; // 16 kB

  /**
   * Returns max allowed size in bytes for response based registrations payload of an individual
   * source/trigger registration.
   */
  public long getMaxResponseBasedRegistrationPayloadSizeBytes() {
    return getLongFlag(
        KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES, MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES);
  }

  public static int MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION = 100;

  /** Returns the number of maximum registration per job invocation. */
  public int getMeasurementMaxRegistrationsPerJobInvocation() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION,
        MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION);
  }

  public static boolean MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK = false;

  /**
   * Returns true if validation is enabled for one navigation per reporting origin per registration.
   */
  public boolean getMeasurementEnableNavigationReportingOriginCheck() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK,
        MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK);
  }

  public static int MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE = 100;

  /**
   * Returns max distinct advertisers with pending impressions per { Publisher X Enrollment X
   * TimePeriod }.
   */
  public int getMeasurementMaxDistinctDestinationsInActiveSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE,
        MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
  }

  /** Default maximum sources per publisher */
  public static int MEASUREMENT_MAX_SOURCES_PER_PUBLISHER = 4096;

  /** Returns maximum sources per publisher */
  public int getMeasurementMaxSourcesPerPublisher() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER, MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
  }

  public static int MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW = 200;

  /**
   * Returns the maximum number of distinct destination sites per source site X enrollment per
   * minute rate limit window.
   */
  public int getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW,
        MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW);
  }

  public static boolean MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT = true;

  /** Returns {@code true} if Measurement destination rate limit is enabled. */
  public boolean getMeasurementEnableDestinationRateLimit() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT, MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT);
  }

  public static long MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW = TimeUnit.MINUTES.toMillis(1);

  /** Returns the duration that controls the rate-limiting window for destinations per minute. */
  public long getMeasurementDestinationRateLimitWindow() {
    return getLongFlag(
        KEY_MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW, MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW);
  }

  /**
   * Returns the maximum number of distinct destination sites per source site X enrollment per day
   * rate limit.
   */
  public static int MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT = 100;

  public int getMeasurementDestinationPerDayRateLimit() {
    return getIntFlag(
        KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT, MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT);
  }

  public static boolean MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW = false;

  /** Returns true, if rate-limiting window for destinations per day is enabled. */
  public boolean getMeasurementEnableDestinationPerDayRateLimitWindow() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW,
        MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW);
  }

  public static long MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS =
      TimeUnit.DAYS.toMillis(1);

  /** Returns the duration that controls the per day rate-limiting window for destinations. */
  public long getMeasurementDestinationPerDayRateLimitWindowInMs() {
    return getLongFlag(
        KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS,
        MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS);
  }

  public static int MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW = 1;

  /**
   * Returns the maximum number of reporting origins per source site, reporting site,
   * reporting-origin-update-window counted per source registration.
   */
  public int getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW,
        MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW);
  }

  public static int MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE = 100;

  /**
   * Max distinct reporting origins with source registration per { Publisher X Advertiser X
   * TimePeriod }.
   */
  public int getMeasurementMaxDistinctRepOrigPerPublXDestInSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE,
        MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE);
  }

  /** Default maximum triggers per destination */
  public static int MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION = 1024;

  /** Returns maximum triggers per destination */
  public int getMeasurementMaxTriggersPerDestination() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION, MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION);
  }

  public static int MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW = 50;

  /**
   * Returns the maximum number of distinct destination sites per source site per rate limit window.
   */
  public int getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW,
        MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW);
  }

  /** Default Measurement app package name logging flag. */
  public static boolean MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING = true;

  /** Returns whether Measurement app package name logging is enabled. */
  public boolean getMeasurementEnableAppPackageNameLogging() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING,
        MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING);
  }

  /* The default value for whether the trigger debugging availability signal is enabled for event
  or aggregate reports. */
  public static boolean MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL = false;

  /**
   * Returns whether the trigger debugging availability signal is enabled for event or aggregate
   * reports.
   */
  public boolean getMeasurementEnableTriggerDebugSignal() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL, MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL);
  }

  /** Disable measurement report to be deleted if any unrecoverable exception occurs. */
  public static boolean MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION = false;

  /** If enabled, measurement reports will get deleted if any unrecoverable exception occurs. */
  public boolean getMeasurementEnableReportDeletionOnUnrecoverableException() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION,
        MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION);
  }

  /**
   * Disable measurement reporting jobs to throw {@link org.json.JSONException} exception by
   * default.
   */
  public static boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION = false;

  /** If enabled, measurement reporting jobs will throw {@link org.json.JSONException}. */
  public boolean getMeasurementEnableReportingJobsThrowJsonException() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION,
        MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION);
  }

  /** Disable measurement aggregate reporting jobs to throw {@code CryptoException} by default. */
  public static boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION = false;

  /** If enabled, measurement aggregate reporting job will throw {@code CryptoException}. */
  public boolean getMeasurementEnableReportingJobsThrowCryptoException() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION,
        MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION);
  }

  /** Disable measurement reporting jobs to throw unaccounted exceptions by default. */
  public static boolean MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION = false;

  /**
   * If enabled, measurement reporting jobs will throw unaccounted e.g. unexpected unchecked
   * exceptions.
   */
  public boolean getMeasurementEnableReportingJobsThrowUnaccountedException() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION,
        MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION);
  }

  public static boolean MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL = false;

  /** Returns whether to enable uninstall report feature. */
  public boolean getMeasurementEnableMinReportLifespanForUninstall() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL,
        MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL);
  }

  /** Returns whether a click event should be verified before a registration request. */
  public static boolean MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED = true;

  public boolean getMeasurementIsClickVerificationEnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED, MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);
  }

  /**
   * Measurement Rollback Kill Switch. The default value is false which means the rollback handling
   * on measurement service start is enabled. This flag is used for emergency turning off
   * measurement rollback data deletion handling.
   */
  public static boolean MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH = false;

  /**
   * Returns the kill switch value for Measurement rollback deletion handling. The rollback deletion
   * handling will be disabled if the Global Kill Switch, Measurement Kill Switch or the Measurement
   * rollback deletion Kill Switch value is true.
   */
  public boolean getMeasurementRollbackDeletionKillSwitch() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH,
        getLegacyMeasurementKillSwitch() || MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH);
  }

  /**
   * Kill Switch for storing Measurement Rollback data in App Search for Android S. The default
   * value is false which means storing the rollback handling data in App Search is enabled. This
   * flag is used for emergency turning off measurement rollback data deletion handling on Android
   * S.
   */
  public static boolean MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH = false;

  /**
   * Returns the kill switch value for storing Measurement rollback deletion handling data in App
   * Search. The rollback deletion handling on Android S will be disabled if this kill switch value
   * is true.
   */
  public boolean getMeasurementRollbackDeletionAppSearchKillSwitch() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH,
        MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH);
  }

  /** Flag for storing Measurement Rollback data in External Storage for Android R. */
  public static boolean MEASUREMENT_ROLLBACK_DELETION_R_ENABLED = !SdkLevel.isAtLeastS();

  /**
   * Returns whether storing Measurement rollback deletion handling data in AdServices external
   * storage is enabled. Rollback deletion handling on Android R will be disabled if this value is
   * false.
   */
  public boolean getMeasurementRollbackDeletionREnabled() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ROLLBACK_DELETION_R_ENABLED, MEASUREMENT_ROLLBACK_DELETION_R_ENABLED);
  }

  /**
   * Default whether to limit logging for enrollment metrics to avoid performance issues. This
   * includes not logging data that requires database queries and downloading MDD files.
   */
  public static boolean ENROLLMENT_ENABLE_LIMITED_LOGGING = false;

  /** Returns whether enrollment logging should be limited. */
  public boolean getEnrollmentEnableLimitedLogging() {
    return getBooleanFlag(KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING, ENROLLMENT_ENABLE_LIMITED_LOGGING);
  }

  public static long MEASUREMENT_DATA_EXPIRY_WINDOW_MS = TimeUnit.DAYS.toMillis(37);

  /** Returns the data expiry window in milliseconds. */
  public long getMeasurementDataExpiryWindowMs() {
    return getLongFlag(KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS, MEASUREMENT_DATA_EXPIRY_WINDOW_MS);
  }

  public static long DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS =
      TimeUnit.DAYS.toMillis(28);

  /** Maximum aggregate report upload retry window. */
  public long getMeasurementMaxAggregateReportUploadRetryWindowMs() {
    return getLongFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
        DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS);
  }

  /* The float to control the probability to set trigger debugging availability signal for fake
  event reports. */
  public static float MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS = 0.5F;

  /**
   * Returns the possibility of trigger debugging availability signal being true for fake event
   * reports.
   */
  public float getMeasurementTriggerDebugSignalProbabilityForFakeReports() {
    return getFloatFlag(
        KEY_MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS,
        MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS);
  }

  /* The default value for whether the trigger debugging availability signal is enabled for event
  reports that have coarse_event_report_destinations = true. */
  public static boolean MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION =
      false;

  /**
   * Returns whether the trigger debugging availability signal is enabled for event reports that
   * have coarse_event_report_destinations = true.
   */
  public boolean getMeasurementEnableEventTriggerDebugSignalForCoarseDestination() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION,
        MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION);
  }

  public static long DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS =
      TimeUnit.DAYS.toMillis(28);

  /** Maximum event report upload retry window. */
  public long getMeasurementMaxEventReportUploadRetryWindowMs() {
    return getLongFlag(
        KEY_DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
        DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS);
  }

  /**
   * The suffix that is appended to the aggregation coordinator origin for retrieving the encryption
   * keys.
   */
  public static String MEASUREMENT_AGGREGATION_COORDINATOR_PATH =
      ".well-known/aggregation-service/v1/public-keys";

  /** Returns the URL for fetching public encryption keys for aggregatable reports. */
  public String getMeasurementAggregationCoordinatorPath() {
    return getStringFlag(
        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH, MEASUREMENT_AGGREGATION_COORDINATOR_PATH);
  }

  /** Disable maximum number of aggregatable reports per source by default. */
  public static boolean MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE = false;

  /**
   * Returns true if maximum number of aggregatable reports per source is enabled, false otherwise.
   */
  public boolean getMeasurementEnableMaxAggregateReportsPerSource() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE,
        MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE);
  }

  /** Flag for enabling measurement aggregate debug reporting */
  public static boolean MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING = false;

  /** Returns whether measurement aggregate debug reporting is enabled. */
  public boolean getMeasurementEnableAggregateDebugReporting() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING,
        MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING);
  }

  public static long MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS =
      TimeUnit.DAYS.toSeconds(1);

  /** Minimum time a report can stay on the device after app uninstall. */
  public long getMeasurementMinReportLifespanForUninstallSeconds() {
    return getLongFlag(
        KEY_MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS,
        MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS);
  }

  public static int MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE = 5;

  /** Returns maximum number of aggregatable debug reports allowed per source. */
  public int getMeasurementMaxAdrCountPerSource() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE, MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE);
  }

  public static long MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS = TimeUnit.DAYS.toMillis(1);

  /**
   * Returns aggregatable debug reporting budget consumption tracking window length in milliseconds.
   */
  public long getMeasurementAdrBudgetWindowLengthMillis() {
    return getLongFlag(
        KEY_MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS,
        MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS);
  }

  public static int MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW = 65536; // = 2^16

  /** Returns aggregatable debug reporting budget allocated per origin per publisher per window */
  public int getMeasurementAdrBudgetOriginXPublisherXWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW,
        MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW);
  }

  public static int MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW = 1048576; // = 2^20

  /** Returns aggregatable debug reporting budget allocated per publisher per window */
  public int getMeasurementAdrBudgetPublisherXWindow() {
    return getIntFlag(
        KEY_MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW,
        MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW);
  }

  public static boolean MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING = false;

  public boolean getMeasurementEnableFlexibleContributionFiltering() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING,
        MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING);
  }

  public static int MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES = 1;

  public int getMeasurementDefaultFilteringIdMaxBytes() {
    return getIntFlag(
        KEY_MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES, MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES);
  }

  public static int MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES = 8;

  public int getMeasurementMaxFilteringIdMaxBytes() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES, MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES);
  }

  /** Default value for Measurement aggregate contribution budget capacity */
  public static boolean MEASUREMENT_ENABLE_AGGREGATE_CONTRIBUTION_BUDGET_CAPACITY = false;

  /** Returns whether to enable Measurement aggregate contribution budget capacity */
  public boolean getMeasurementEnableAggregateContributionBudgetCapacity() {
    return getBooleanFlag(
        KEY_MEASUREMENT_ENABLE_AGGREGATE_CONTRIBUTION_BUDGET_CAPACITY,
        MEASUREMENT_ENABLE_AGGREGATE_CONTRIBUTION_BUDGET_CAPACITY);
  }

  public static int MEASUREMENT_MAX_AGGREGATABLE_BUCKETS_PER_SOURCE_REGISTRATION = 20;

  /** Returns max size of an attribution source's aggregatable attribution bucket budget list. */
  public int getMeasurementMaxAggregatableBucketsPerSourceRegistration() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_AGGREGATABLE_BUCKETS_PER_SOURCE_REGISTRATION,
        MEASUREMENT_MAX_AGGREGATABLE_BUCKETS_PER_SOURCE_REGISTRATION);
  }

  public static int MEASUREMENT_MAX_LENGTH_PER_AGGREGATABLE_BUCKET = 50;

  /** Returns max length of an attribution source's aggregatable bucket budget's key. */
  public int getMeasurementMaxLengthPerAggregatableBucket() {
    return getIntFlag(
        KEY_MEASUREMENT_MAX_LENGTH_PER_AGGREGATABLE_BUCKET,
        MEASUREMENT_MAX_LENGTH_PER_AGGREGATABLE_BUCKET);
  }

  private boolean getBooleanFlag(String key, boolean defaultValue) {
    if (mFlagsMap.containsKey(key)) {
      return Boolean.parseBoolean(mFlagsMap.get(key));
    }

    return defaultValue;
  }

  private int getIntFlag(String key, int defaultValue) {
    if (mFlagsMap.containsKey(key)) {
      return Integer.parseInt(mFlagsMap.get(key));
    }

    return defaultValue;
  }

  private long getLongFlag(String key, long defaultValue) {
    if (mFlagsMap.containsKey(key)) {
      return Long.parseLong(mFlagsMap.get(key));
    }

    return defaultValue;
  }

  private float getFloatFlag(String key, float defaultValue) {
    if (mFlagsMap.containsKey(key)) {
      return Float.parseFloat(mFlagsMap.get(key));
    }

    return defaultValue;
  }

  private String getStringFlag(String key, String defaultValue) {
    return mFlagsMap.getOrDefault(key, defaultValue);
  }
}
