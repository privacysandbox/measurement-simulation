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

package com.google.measurement.client.reporting;

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Context;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Pair;

import com.google.measurement.client.Nullable;

import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.Flags;
import com.google.measurement.client.WebAddresses;
import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.util.ReportUtil;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.VisibleForTesting;

import java.util.HashMap;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {

  /** Define different verbose debug report types. */
  public enum Type {
    UNSPECIFIED("unspecified"),
    SOURCE_DESTINATION_LIMIT("source-destination-limit"),
    SOURCE_DESTINATION_RATE_LIMIT("source-destination-rate-limit"),
    SOURCE_DESTINATION_PER_DAY_RATE_LIMIT("source-destination-per-day-rate-limit"),
    SOURCE_NOISED("source-noised"),
    SOURCE_STORAGE_LIMIT("source-storage-limit"),
    SOURCE_SUCCESS("source-success"),
    SOURCE_UNKNOWN_ERROR("source-unknown-error"),
    SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR("source-flexible-event-report-value-error"),
    SOURCE_MAX_EVENT_STATES_LIMIT("source-max-event-states-limit"),
    SOURCE_SCOPES_CHANNEL_CAPACITY_LIMIT("source-scopes-channel-capacity-limit"),
    SOURCE_CHANNEL_CAPACITY_LIMIT("source-channel-capacity-limit"),
    SOURCE_ATTRIBUTION_SCOPE_INFO_GAIN_LIMIT("source-attribution-scope-info-gain-limit"),
    SOURCE_DESTINATION_GLOBAL_RATE_LIMIT("source-destination-global-rate-limit"),
    SOURCE_DESTINATION_LIMIT_REPLACED("source-destination-limit-replaced"),
    SOURCE_REPORTING_ORIGIN_LIMIT("source-reporting-origin-limit"),
    SOURCE_REPORTING_ORIGIN_PER_SITE_LIMIT("source-reporting-origin-per-site-limit"),
    SOURCE_TRIGGER_STATE_CARDINALITY_LIMIT("source-trigger-state-cardinality-limit"),

    TRIGGER_AGGREGATE_DEDUPLICATED("trigger-aggregate-deduplicated"),
    TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET("trigger-aggregate-insufficient-budget"),
    TRIGGER_AGGREGATE_NO_CONTRIBUTIONS("trigger-aggregate-no-contributions"),
    TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED("trigger-aggregate-report-window-passed"),
    TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT(
        "trigger-attributions-per-source-destination-limit"),
    TRIGGER_EVENT_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT(
        "trigger-event-attributions-per-source-destination-limit"),
    TRIGGER_AGGREGATE_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT(
        "trigger-aggregate-attributions-per-source-destination-limit"),
    TRIGGER_EVENT_DEDUPLICATED("trigger-event-deduplicated"),
    TRIGGER_EVENT_EXCESSIVE_REPORTS("trigger-event-excessive-reports"),
    TRIGGER_EVENT_LOW_PRIORITY("trigger-event-low-priority"),
    TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS("trigger-event-no-matching-configurations"),
    TRIGGER_EVENT_NOISE("trigger-event-noise"),
    TRIGGER_EVENT_REPORT_WINDOW_PASSED("trigger-event-report-window-passed"),
    TRIGGER_NO_MATCHING_FILTER_DATA("trigger-no-matching-filter-data"),
    TRIGGER_NO_MATCHING_SOURCE("trigger-no-matching-source"),
    TRIGGER_REPORTING_ORIGIN_LIMIT("trigger-reporting-origin-limit"),
    TRIGGER_EVENT_STORAGE_LIMIT("trigger-event-storage-limit"),
    TRIGGER_UNKNOWN_ERROR("trigger-unknown-error"),
    TRIGGER_AGGREGATE_STORAGE_LIMIT("trigger-aggregate-storage-limit"),
    TRIGGER_AGGREGATE_EXCESSIVE_REPORTS("trigger-aggregate-excessive-reports"),
    TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED("trigger-event-report-window-not-started"),
    TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA("trigger-event-no-matching-trigger-data"),
    HEADER_PARSING_ERROR("header-parsing-error");

    private final String mValue;

    Type(String value) {
      mValue = value;
    }

    public String getValue() {
      return mValue;
    }

    /** get enum type from string value */
    public static Optional<Type> findByValue(String value) {
      for (Type type : values()) {
        if (type.getValue().equalsIgnoreCase(value)) {
          return Optional.of(type);
        }
      }
      return Optional.empty();
    }
  }

  /** Defines different verbose debug report body parameters. */
  @VisibleForTesting
  public interface Body {
    String ATTRIBUTION_DESTINATION = "attribution_destination";
    String LIMIT = "limit";
    String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
    String SCHEDULED_REPORT_TIME = "scheduled_report_time";
    String SOURCE_DEBUG_KEY = "source_debug_key";
    String SOURCE_EVENT_ID = "source_event_id";
    String SOURCE_SITE = "source_site";
    String SOURCE_TYPE = "source_type";
    String TRIGGER_DATA = "trigger_data";
    String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    String SOURCE_DESTINATION_LIMIT = "source_destination_limit";
    String CONTEXT_SITE = "context_site";
    String HEADER = "header";
    String VALUE = "value";
    String ERROR = "error";
  }

  private enum PermissionState {
    GRANTED,
    DENIED,
    NONE
  }

  private final Context mContext;
  private final Flags mFlags;
  private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
  private final SourceNoiseHandler mSourceNoiseHandler;

  public DebugReportApi(Context context, Flags flags) {
    this(context, flags, new EventReportWindowCalcDelegate(flags), new SourceNoiseHandler(flags));
  }

  @VisibleForTesting
  public DebugReportApi(
      Context context,
      Flags flags,
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      SourceNoiseHandler sourceNoiseHandler) {
    mContext = context;
    mFlags = flags;
    mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    mSourceNoiseHandler = sourceNoiseHandler;
  }

  /** Schedules the Source Destination limit Debug Report */
  public void scheduleSourceDestinationLimitDebugReport(
      Source source, String limit, IMeasurementDao dao) {
    scheduleSourceDestinationLimitDebugReport(source, limit, Type.SOURCE_DESTINATION_LIMIT, dao);
  }

  /** Schedules the Source Destination rate-limit Debug Report */
  public void scheduleSourceDestinationPerMinuteRateLimitDebugReport(
      Source source, String limit, IMeasurementDao dao) {
    scheduleSourceDestinationLimitDebugReport(
        source, limit, Type.SOURCE_DESTINATION_RATE_LIMIT, dao);
  }

  /** Schedules the Source Destination per day rate-limit Debug Report */
  public void scheduleSourceDestinationPerDayRateLimitDebugReport(
      Source source, String limit, IMeasurementDao dao) {
    scheduleSourceDestinationLimitDebugReport(
        source, limit, Type.SOURCE_DESTINATION_PER_DAY_RATE_LIMIT, dao);
  }

  /** Schedules Source Attribution Scope Debug Report */
  public DebugReportApi.Type scheduleAttributionScopeDebugReport(
      Source source, Source.AttributionScopeValidationResult result, IMeasurementDao dao) {
    DebugReportApi.Type type = null;
    Map<String, String> additionalBodyParams = new HashMap<>();
    switch (result) {
      case VALID -> {
        // No-op.
      }
      case INVALID_MAX_EVENT_STATES_LIMIT -> {
        type = Type.SOURCE_MAX_EVENT_STATES_LIMIT;
        additionalBodyParams.put(Body.LIMIT, String.valueOf(source.getMaxEventStates()));
      }
      case INVALID_INFORMATION_GAIN_LIMIT -> {
        type = Type.SOURCE_SCOPES_CHANNEL_CAPACITY_LIMIT;
        additionalBodyParams.put(
            Body.LIMIT, String.valueOf(source.getAttributionScopeInfoGainThreshold(mFlags)));
      }
    }
    if (type == null) {
      return null;
    }
    scheduleSourceReport(source, type, additionalBodyParams, dao);
    return type;
  }

  /** Determines if scheduling the Trigger Report is allowed */
  private boolean isTriggerReportAllowed(
      Trigger trigger, DebugReportApi.Type type, @Nullable Source source) {
    Objects.requireNonNull(trigger, "trigger cannot be null");
    Objects.requireNonNull(type, "type cannot be null");

    if (isTriggerDebugFlagDisabled(type)) {
      return false;
    }
    if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
      return false;
    }

    if (!isSourceAndTriggerPermissionsGranted(source, trigger)) {
      LoggerFactory.getMeasurementLogger().d("Skipping trigger debug report %s", type);
      return false;
    }

    return true;
  }

  /**
   * Schedules trigger-no-matching-source and trigger-unknown-error debug reports when trigger
   * doesn't have related source.
   */
  public void scheduleTriggerNoMatchingSourceDebugReport(
      Trigger trigger, IMeasurementDao dao, DebugReportApi.Type type) throws DatastoreException {
    if (!isTriggerReportAllowed(trigger, type, /* source= */ null)) {
      return;
    }
    Pair<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(null, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBody(null, trigger, null, debugKeyPair, true),
        trigger.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        trigger.getRegistrant(),
        dao);
  }

  /** Schedules Trigger Debug Reports with/without limit, pass in Type for different types. */
  public void scheduleTriggerDebugReport(
      Source source,
      Trigger trigger,
      @Nullable String limit,
      IMeasurementDao dao,
      DebugReportApi.Type type)
      throws DatastoreException {
    if (!isTriggerReportAllowed(trigger, type, source)) {
      return;
    }
    Pair<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBody(source, trigger, limit, debugKeyPair, false),
        source.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        source.getRegistrant(),
        dao);
  }

  /**
   * Schedules Trigger Debug Report with all body fields, Used for trigger-low-priority report and
   * trigger-event-excessive-reports.
   */
  public void scheduleTriggerDebugReportWithAllFields(
      Source source,
      Trigger trigger,
      UnsignedLong triggerData,
      IMeasurementDao dao,
      DebugReportApi.Type type)
      throws DatastoreException {
    if (!isTriggerReportAllowed(trigger, type, source)) {
      return;
    }
    Pair<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBodyWithAllFields(source, trigger, triggerData, debugKeyPair),
        source.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        source.getRegistrant(),
        dao);
  }

  /** Schedules the Source Destination limit type Debug Report */
  private void scheduleSourceDestinationLimitDebugReport(
      Source source, String limit, Type type, IMeasurementDao dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_DESTINATION_LIMIT)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_DESTINATION_LIMIT)) {
      return;
    }
    try {
      JSONObject body = new JSONObject();
      body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
      body.put(Body.ATTRIBUTION_DESTINATION, generateSourceDestinations(source));
      body.put(Body.SOURCE_SITE, generateSourceSite(source));
      body.put(Body.LIMIT, limit);
      if (getAdIdPermissionFromSource(source) == PermissionState.GRANTED
          || getArDebugPermissionFromSource(source) == PermissionState.GRANTED) {
        body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
      }
      scheduleReport(
          type,
          body,
          source.getEnrollmentId(),
          source.getRegistrationOrigin(),
          source.getRegistrant(),
          dao);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Json error in debug report %s", type);
    }
  }

  /** Schedule header parsing and validation errors verbose debug reports. */
  public void scheduleHeaderErrorReport(
      Uri topOrigin,
      Uri registrationOrigin,
      Uri registrant,
      String headerName,
      String enrollmentId,
      @Nullable String originalHeader,
      IMeasurementDao dao) {
    try {
      JSONObject body = new JSONObject();
      body.put(Body.CONTEXT_SITE, topOrigin);
      body.put(Body.HEADER, headerName);
      body.put(Body.VALUE, originalHeader == null ? "null" : originalHeader);
      scheduleReport(
          Type.HEADER_PARSING_ERROR, body, enrollmentId, registrationOrigin, registrant, dao);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Json error in debug report %s", Type.HEADER_PARSING_ERROR);
    }
  }

  /**
   * Schedules the Source Debug Report to be sent
   *
   * @param source The source
   * @param type The type of the debug report
   * @param additionalBodyParams Additional parameters to add to the body of the debug report
   * @param dao Measurement DAO
   */
  public void scheduleSourceReport(
      Source source,
      Type type,
      @Nullable Map<String, String> additionalBodyParams,
      IMeasurementDao dao) {
    Objects.requireNonNull(source, "source cannot be null");
    Objects.requireNonNull(type, "type cannot be null");
    Objects.requireNonNull(dao, "dao cannot be null");
    String enrollmentId = source.getEnrollmentId();
    Objects.requireNonNull(enrollmentId, "source enrollmentId cannot be null");
    JSONObject body = generateSourceDebugReportBody(source, additionalBodyParams);
    Objects.requireNonNull(body, "debug report body cannot be null");

    if (isSourceDebugFlagDisabled(type)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), type)) {
      return;
    }
    if (!isSourcePermissionGranted(source)) {
      LoggerFactory.getMeasurementLogger().d("Skipping source debug report %s", type);
      return;
    }
    if (body.length() == 0) {
      LoggerFactory.getMeasurementLogger().d("Empty debug report found %s", type);
      return;
    }
    if (enrollmentId.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Empty enrollment found %s", type);
      return;
    }

    DebugReport debugReport =
        new DebugReport.Builder()
            .setId(UUID.randomUUID().toString())
            .setType(type.getValue())
            .setBody(body)
            .setEnrollmentId(enrollmentId)
            .setRegistrationOrigin(source.getRegistrationOrigin())
            .setInsertionTime(System.currentTimeMillis())
            .setRegistrant(source.getRegistrant())
            .build();
    try {
      dao.insertDebugReport(debugReport);
    } catch (DatastoreException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Failed to insert source debug report %s", type);
    }

    VerboseDebugReportingJobService.scheduleIfNeeded(mContext, /* forceSchedule= */ false);
  }

  /**
   * Schedules the Debug Report to be sent
   *
   * @param type The type of the debug report
   * @param body The body of the debug report
   * @param enrollmentId Ad Tech enrollment ID
   * @param registrationOrigin Reporting origin of the report
   * @param dao Measurement DAO
   * @param registrant App Registrant
   */
  private void scheduleReport(
      Type type,
      JSONObject body,
      String enrollmentId,
      Uri registrationOrigin,
      @Nullable Uri registrant,
      IMeasurementDao dao) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(body);
    Objects.requireNonNull(enrollmentId);
    Objects.requireNonNull(dao);
    if (body.length() == 0) {
      LoggerFactory.getMeasurementLogger().d("Empty debug report found %s", type);
      return;
    }
    if (enrollmentId.isEmpty()) {
      LoggerFactory.getMeasurementLogger().d("Empty enrollment found %s", type);
      return;
    }

    DebugReport debugReport =
        new DebugReport.Builder()
            .setId(UUID.randomUUID().toString())
            .setType(type.getValue())
            .setBody(body)
            .setEnrollmentId(enrollmentId)
            .setRegistrationOrigin(registrationOrigin)
            .setInsertionTime(System.currentTimeMillis())
            .setRegistrant(registrant)
            .build();
    try {
      dao.insertDebugReport(debugReport);
    } catch (DatastoreException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Failed to insert debug report %s", type);
    }

    VerboseDebugReportingJobService.scheduleIfNeeded(mContext, /* forceSchedule= */ false);
  }

  /** Get AdIdPermission State from Source */
  private PermissionState getAdIdPermissionFromSource(Source source) {
    if (source.getPublisherType() == EventSurfaceType.APP) {
      if (source.hasAdIdPermission()) {
        return PermissionState.GRANTED;
      } else {
        LoggerFactory.getMeasurementLogger().d("Source doesn't have AdId permission");
        return PermissionState.DENIED;
      }
    }
    return PermissionState.NONE;
  }

  /** Get ArDebugPermission State from Source */
  private PermissionState getArDebugPermissionFromSource(Source source) {
    if (source.getPublisherType() == EventSurfaceType.WEB) {
      if (source.hasArDebugPermission()) {
        return PermissionState.GRANTED;
      } else {
        LoggerFactory.getMeasurementLogger().d("Source doesn't have ArDebug permission");
        return PermissionState.DENIED;
      }
    }
    return PermissionState.NONE;
  }

  private PermissionState getAdIdPermissionFromTrigger(Trigger trigger) {
    if (trigger.getDestinationType() == EventSurfaceType.APP) {
      if (trigger.hasAdIdPermission()) {
        return PermissionState.GRANTED;
      } else {
        LoggerFactory.getMeasurementLogger().d("Trigger doesn't have AdId permission");
        return PermissionState.DENIED;
      }
    }
    return PermissionState.NONE;
  }

  private PermissionState getArDebugPermissionFromTrigger(Trigger trigger) {
    if (trigger.getDestinationType() == EventSurfaceType.WEB) {
      if (trigger.hasArDebugPermission()) {
        return PermissionState.GRANTED;
      } else {
        LoggerFactory.getMeasurementLogger().d("Trigger doesn't have ArDebug permission");
        return PermissionState.DENIED;
      }
    }
    return PermissionState.NONE;
  }

  /**
   * Check AdId and ArDebug permissions for both source and trigger. Return true if all of them are
   * not in {@link PermissionState#DENIED} state.
   */
  private boolean isSourceAndTriggerPermissionsGranted(@Nullable Source source, Trigger trigger) {
    return source == null
        ? isTriggerPermissionGranted(trigger)
        : (isSourcePermissionGranted(source) && isTriggerPermissionGranted(trigger));
  }

  private boolean isSourcePermissionGranted(Source source) {
    return getAdIdPermissionFromSource(source) != PermissionState.DENIED
        && getArDebugPermissionFromSource(source) != PermissionState.DENIED;
  }

  private boolean isTriggerPermissionGranted(Trigger trigger) {
    return getAdIdPermissionFromTrigger(trigger) != PermissionState.DENIED
        && getArDebugPermissionFromTrigger(trigger) != PermissionState.DENIED;
  }

  /** Get is Ad tech not op-in and log */
  private boolean isAdTechNotOptIn(boolean optIn, DebugReportApi.Type type) {
    if (!optIn) {
      LoggerFactory.getMeasurementLogger().d("Ad-tech not opt-in. Skipping debug report %s", type);
    }
    return !optIn;
  }

  /** Generates source debug report body */
  private JSONObject generateSourceDebugReportBody(
      Source source, @Nullable Map<String, String> additionalBodyParams) {
    JSONObject body = new JSONObject();
    try {
      body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
      body.put(Body.ATTRIBUTION_DESTINATION, generateSourceDestinations(source));
      body.put(Body.SOURCE_SITE, generateSourceSite(source));
      body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
      if (additionalBodyParams != null) {
        for (Map.Entry<String, String> entry : additionalBodyParams.entrySet()) {
          body.put(entry.getKey(), entry.getValue());
        }
      }

    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Json error while generating source debug report body.");
    }
    return body;
  }

  private static Object generateSourceDestinations(Source source) throws JSONException {
    List<Uri> destinations = new ArrayList<>();
    Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
    List<Uri> webDestinations = source.getWebDestinations();
    if (webDestinations != null) {
      for (Uri webDestination : webDestinations) {
        Optional<Uri> webUri = WebAddresses.topPrivateDomainAndScheme(webDestination);
        webUri.ifPresent(destinations::add);
      }
    }
    return ReportUtil.serializeAttributionDestinations(destinations);
  }

  private static Uri generateSourceSite(Source source) {
    if (source.getPublisherType() == EventSurfaceType.APP) {
      return source.getPublisher();
    } else {
      return WebAddresses.topPrivateDomainAndScheme(source.getPublisher()).orElse(null);
    }
  }

  /** Generates trigger debug report body */
  private JSONObject generateTriggerDebugReportBody(
      @Nullable Source source,
      @NonNull Trigger trigger,
      @Nullable String limit,
      @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair,
      boolean isTriggerNoMatchingSource) {
    JSONObject body = new JSONObject();
    try {
      body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
      body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
      if (isTriggerNoMatchingSource) {
        return body;
      }
      body.put(Body.LIMIT, limit);
      body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
      body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
      body.put(Body.SOURCE_SITE, generateSourceSite(source));
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Json error while generating trigger debug report body.");
    }
    return body;
  }

  /**
   * Generates trigger debug report body with all fields in event-level attribution report. Used for
   * trigger-low-priority, trigger-event-excessive-reports debug reports.
   */
  private JSONObject generateTriggerDebugReportBodyWithAllFields(
      @NonNull Source source,
      @NonNull Trigger trigger,
      @Nullable UnsignedLong triggerData,
      @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair) {
    JSONObject body = new JSONObject();
    try {
      body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
      body.put(
          Body.SCHEDULED_REPORT_TIME,
          String.valueOf(
              TimeUnit.MILLISECONDS.toSeconds(
                  mEventReportWindowCalcDelegate.getReportingTime(
                      source, trigger.getTriggerTime(), trigger.getDestinationType()))));
      body.put(Body.SOURCE_EVENT_ID, source.getEventId());
      body.put(Body.SOURCE_TYPE, source.getSourceType().getValue());
      body.put(Body.RANDOMIZED_TRIGGER_RATE, mSourceNoiseHandler.getRandomizedTriggerRate(source));
      if (triggerData != null) {
        body.put(Body.TRIGGER_DATA, triggerData.toString());
      }
      if (debugKeyPair.first != null) {
        body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
      }
      if (debugKeyPair.second != null) {
        body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
      }
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Json error while generating trigger debug report body with all fields.");
    }
    return body;
  }

  /** Checks flags for source debug reports. */
  private boolean isSourceDebugFlagDisabled(DebugReportApi.Type type) {
    if (!mFlags.getMeasurementEnableDebugReport()
        || !mFlags.getMeasurementEnableSourceDebugReport()) {
      LoggerFactory.getMeasurementLogger().d("Source flag is disabled for %s debug report", type);
      return true;
    }
    return false;
  }

  /** Checks flags for trigger debug reports. */
  private boolean isTriggerDebugFlagDisabled(DebugReportApi.Type type) {
    if (!mFlags.getMeasurementEnableDebugReport()
        || !mFlags.getMeasurementEnableTriggerDebugReport()) {
      LoggerFactory.getMeasurementLogger().d("Trigger flag is disabled for %s debug report", type);
      return true;
    }
    return false;
  }
}
