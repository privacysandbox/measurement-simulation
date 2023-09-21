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

import com.google.measurement.noising.SourceNoiseHandler;
import com.google.measurement.util.ReportUtil;
import com.google.measurement.util.UnsignedLong;
import com.google.measurement.util.Web;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {
  private static final Logger logger = Logger.getLogger(DebugReportApi.class.getName());

  /** Define different verbose debug report types. */
  public interface Type {
    String SOURCE_DESTINATION_LIMIT = "source-destination-limit";
    String SOURCE_NOISED = "source-noised";
    String SOURCE_STORAGE_LIMIT = "source-storage-limit";
    String SOURCE_SUCCESS = "source-success";
    String SOURCE_UNKNOWN_ERROR = "source-unknown-error";
    String TRIGGER_AGGREGATE_DEDUPLICATED = "trigger-aggregate-deduplicated";
    String TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET = "trigger-aggregate-insufficient-budget";
    String TRIGGER_AGGREGATE_NO_CONTRIBUTIONS = "trigger-aggregate-no-contributions";
    String TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED = "trigger-aggregate-report-window-passed";
    String TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT =
        "trigger-attributions-per-source-destination-limit";
    String TRIGGER_EVENT_DEDUPLICATED = "trigger-event-deduplicated";
    String TRIGGER_EVENT_EXCESSIVE_REPORTS = "trigger-event-excessive-reports";
    String TRIGGER_EVENT_LOW_PRIORITY = "trigger-event-low-priority";
    String TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS = "trigger-event-no-matching-configurations";
    String TRIGGER_EVENT_NOISE = "trigger-event-noise";
    String TRIGGER_EVENT_REPORT_WINDOW_PASSED = "trigger-event-report-window-passed";
    String TRIGGER_NO_MATCHING_FILTER_DATA = "trigger-no-matching-filter-data";
    String TRIGGER_NO_MATCHING_SOURCE = "trigger-no-matching-source";
    String TRIGGER_REPORTING_ORIGIN_LIMIT = "trigger-reporting-origin-limit";
    String TRIGGER_EVENT_STORAGE_LIMIT = "trigger-event-storage-limit";
    String TRIGGER_UNKNOWN_ERROR = "trigger-unknown-error";
    String TRIGGER_AGGREGATE_STORAGE_LIMIT = "trigger-aggregate-storage-limit";
  }

  /** Defines different verbose debug report body parameters. */
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
  }

  private enum PermissionState {
    GRANTED,
    DENIED,
    NONE
  }

  private final Flags mFlags;
  private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
  private final SourceNoiseHandler mSourceNoiseHandler;

  public DebugReportApi(Flags flags) {
    this(flags, new EventReportWindowCalcDelegate(flags), new SourceNoiseHandler(flags));
  }

  DebugReportApi(
      Flags flags,
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      SourceNoiseHandler sourceNoiseHandler) {
    mFlags = flags;
    mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    mSourceNoiseHandler = sourceNoiseHandler;
  }

  /** Schedules the Source Success Debug Report */
  public void scheduleSourceSuccessDebugReport(Source source, IMeasurementDAO dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_SUCCESS)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_SUCCESS)) {
      return;
    }
    if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
        || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
      logger.info(String.format("Skipping debug report %s", Type.SOURCE_SUCCESS));
      return;
    }
    scheduleReport(
        Type.SOURCE_SUCCESS,
        generateSourceDebugReportBody(source, null),
        source.getEnrollmentId(),
        source.getRegistrationOrigin(),
        dao);
  }

  /** Schedules the Source Destination limit Debug Report */
  public void scheduleSourceDestinationLimitDebugReport(
      Source source, String limit, IMeasurementDAO dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_DESTINATION_LIMIT)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_DESTINATION_LIMIT)) {
      return;
    }
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
        Type.SOURCE_DESTINATION_LIMIT,
        body,
        source.getEnrollmentId(),
        source.getRegistrationOrigin(),
        dao);
  }

  /** Schedules the Source Noised Debug Report */
  public void scheduleSourceNoisedDebugReport(Source source, IMeasurementDAO dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_NOISED)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_NOISED)) {
      return;
    }
    if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
        || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
      logger.info(String.format("Skipping debug report %s", Type.SOURCE_NOISED));
      return;
    }
    scheduleReport(
        Type.SOURCE_NOISED,
        generateSourceDebugReportBody(source, null),
        source.getEnrollmentId(),
        source.getRegistrationOrigin(),
        dao);
  }

  /** Schedules Source Storage Limit Debug Report */
  public void scheduleSourceStorageLimitDebugReport(
      Source source, String limit, IMeasurementDAO dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_STORAGE_LIMIT)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_STORAGE_LIMIT)) {
      return;
    }
    if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
        || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
      logger.info(String.format("Skipping debug report %s", Type.SOURCE_STORAGE_LIMIT));
      return;
    }
    scheduleReport(
        Type.SOURCE_STORAGE_LIMIT,
        generateSourceDebugReportBody(source, limit),
        source.getEnrollmentId(),
        source.getRegistrationOrigin(),
        dao);
  }

  /** Schedules the Source Unknown Error Debug Report */
  public void scheduleSourceUnknownErrorDebugReport(Source source, IMeasurementDAO dao) {
    if (isSourceDebugFlagDisabled(Type.SOURCE_UNKNOWN_ERROR)) {
      return;
    }
    if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_UNKNOWN_ERROR)) {
      return;
    }
    if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
        || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
      logger.info(String.format("Skipping debug report %s", Type.SOURCE_UNKNOWN_ERROR));
      return;
    }
    scheduleReport(
        Type.SOURCE_UNKNOWN_ERROR,
        generateSourceDebugReportBody(source, null),
        source.getEnrollmentId(),
        source.getRegistrationOrigin(),
        dao);
  }

  /**
   * Schedules trigger-no-matching-source and trigger-unknown-error debug reports when trigger
   * doesn't have related source.
   */
  public void scheduleTriggerNoMatchingSourceDebugReport(
      Trigger trigger, IMeasurementDAO dao, String type) {
    if (isTriggerDebugFlagDisabled(type)) {
      return;
    }
    if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
      return;
    }
    if (getAdIdPermissionFromTrigger(trigger) == PermissionState.DENIED
        || getArDebugPermissionFromTrigger(trigger) == PermissionState.DENIED) {
      logger.info(String.format("Skipping trigger debug report %s", type));
      return;
    }
    Map<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(null, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBody(null, trigger, null, debugKeyPair, true),
        trigger.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        dao);
  }

  /** Schedules Trigger Debug Reports with/without limit, pass in Type for different types. */
  public void scheduleTriggerDebugReport(
      Source source, Trigger trigger, String limit, IMeasurementDAO dao, String type) {
    if (isTriggerDebugFlagDisabled(type)) {
      return;
    }
    if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
      return;
    }
    if (getAdIdPermissionFromTrigger(trigger) == PermissionState.DENIED
        || getArDebugPermissionFromTrigger(trigger) == PermissionState.DENIED) {
      logger.info(String.format("Skipping trigger debug report %s", type));
      return;
    }
    Map<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBody(source, trigger, limit, debugKeyPair, false),
        source.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        dao);
  }

  /**
   * Schedules Trigger Debug Report with all body fields, Used for trigger-low-priority report and
   * trigger-event-excessive-reports.
   */
  public void scheduleTriggerDebugReportWithAllFields(
      Source source, Trigger trigger, UnsignedLong triggerData, IMeasurementDAO dao, String type) {
    if (isTriggerDebugFlagDisabled(type)) {
      return;
    }
    if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
      return;
    }
    if (getAdIdPermissionFromTrigger(trigger) == PermissionState.DENIED
        || getArDebugPermissionFromTrigger(trigger) == PermissionState.DENIED) {
      logger.info(String.format("Skipping trigger debug report %s", type));
      return;
    }
    Map<UnsignedLong, UnsignedLong> debugKeyPair =
        new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
    scheduleReport(
        type,
        generateTriggerDebugReportBodyWithAllFields(source, trigger, triggerData, debugKeyPair),
        source.getEnrollmentId(),
        trigger.getRegistrationOrigin(),
        dao);
  }

  /**
   * Schedules the Debug Report to be sent
   *
   * @param type The type of the debug report
   * @param body The body of the debug report
   * @param enrollmentId Ad Tech enrollment ID
   * @param registrationOrigin Reporting origin of the report
   * @param dao Measurement DAO
   */
  private void scheduleReport(
      String type,
      JSONObject body,
      String enrollmentId,
      URI registrationOrigin,
      IMeasurementDAO dao) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(body);
    Objects.requireNonNull(enrollmentId);
    Objects.requireNonNull(dao);
    if (type.isEmpty() || body.size() == 0) {
      logger.info(String.format("Empty debug report found %s", type));
      return;
    }
    if (enrollmentId.isEmpty()) {
      logger.info(String.format("Empty enrollment found %s", type));
      return;
    }
    DebugReport debugReport =
        new DebugReport.Builder()
            .setId(UUID.randomUUID().toString())
            .setType(type)
            .setBody(body)
            .setEnrollmentId(enrollmentId)
            .setRegistrationOrigin(registrationOrigin)
            .build();
    dao.insertDebugReport(debugReport);
  }

  /** Get AdIdPermission State from Source */
  private PermissionState getAdIdPermissionFromSource(Source source) {
    if (source.getPublisherType() == EventSurfaceType.APP) {
      if (source.hasAdIdPermission()) {
        return PermissionState.GRANTED;
      } else {
        logger.info("Source doesn't have AdId permission");
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
        logger.info("Source doesn't have ArDebug permission");
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
        logger.info("Trigger doesn't have AdId permission");
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
        logger.info("Trigger doesn't have ArDebug permission");
        return PermissionState.DENIED;
      }
    }
    return PermissionState.NONE;
  }

  /** Get is Ad tech not op-in and log */
  private boolean isAdTechNotOptIn(boolean optIn, String type) {
    if (!optIn) {
      logger.info(String.format("Ad-tech not opt-in. Skipping debug report %s", type));
    }
    return !optIn;
  }

  /** Generates source debug report body */
  private JSONObject generateSourceDebugReportBody(Source source, String limit) {
    JSONObject body = new JSONObject();
    body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
    body.put(Body.ATTRIBUTION_DESTINATION, generateSourceDestinations(source));
    body.put(Body.SOURCE_SITE, generateSourceSite(source));
    body.put(Body.LIMIT, limit);
    body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
    return body;
  }

  private static Object generateSourceDestinations(Source source) {
    List<URI> destinations = new ArrayList<>();
    Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
    List<URI> webDestinations = source.getWebDestinations();
    if (webDestinations != null) {
      for (URI webDestination : webDestinations) {
        Optional<URI> webUri = Web.topPrivateDomainAndScheme(webDestination);
        webUri.ifPresent(destinations::add);
      }
    }
    return ReportUtil.serializeAttributionDestinations(destinations);
  }

  private static URI generateSourceSite(Source source) {
    if (source.getPublisherType() == EventSurfaceType.APP) {
      return source.getPublisher();
    } else {
      return Web.topPrivateDomainAndScheme(source.getPublisher()).orElse(null);
    }
  }

  /** Generates trigger debug report body */
  private JSONObject generateTriggerDebugReportBody(
      Source source,
      Trigger trigger,
      String limit,
      Map<UnsignedLong, UnsignedLong> debugKeyPair,
      boolean isTriggerNoMatchingSource) {
    JSONObject body = new JSONObject();
    UnsignedLong sourceKey = debugKeyPair.keySet().iterator().next();
    body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
    body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.get(sourceKey));
    if (isTriggerNoMatchingSource) {
      return body;
    }
    body.put(Body.LIMIT, limit);
    body.put(Body.SOURCE_DEBUG_KEY, sourceKey);
    body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
    body.put(Body.SOURCE_SITE, generateSourceSite(source));
    return body;
  }

  /**
   * Generates trigger debug report body with all fields in event-level attribution report. Used for
   * trigger-low-priority, trigger-event-excessive-reports debug reports.
   */
  private JSONObject generateTriggerDebugReportBodyWithAllFields(
      Source source,
      Trigger trigger,
      UnsignedLong triggerData,
      Map<UnsignedLong, UnsignedLong> debugKeyPair) {
    JSONObject body = new JSONObject();
    body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
    body.put(
        Body.SCHEDULED_REPORT_TIME,
        String.valueOf(
            TimeUnit.MILLISECONDS.toSeconds(
                mEventReportWindowCalcDelegate.getReportingTime(
                    source, trigger.getTriggerTime(), trigger.getDestinationType()))));
    body.put(Body.SOURCE_EVENT_ID, source.getEventId());
    body.put(Body.SOURCE_TYPE, source.getSourceType().getValue());
    body.put(
        Body.RANDOMIZED_TRIGGER_RATE, mSourceNoiseHandler.getRandomAttributionProbability(source));
    if (triggerData != null) {
      body.put(Body.TRIGGER_DATA, triggerData.toString());
    }
    UnsignedLong sourceKey = debugKeyPair.keySet().iterator().next();
    if (sourceKey != null) {
      body.put(Body.SOURCE_DEBUG_KEY, sourceKey);
    }
    if (debugKeyPair.get(sourceKey) != null) {
      body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.get(sourceKey));
    }
    return body;
  }

  /** Checks flags for source debug reports. */
  private boolean isSourceDebugFlagDisabled(String type) {
    if (!mFlags.getMeasurementEnableDebugReport()
        || !mFlags.getMeasurementEnableSourceDebugReport()) {
      logger.info(String.format("Source flag is disabled for %s debug report", type));
      return true;
    }
    return false;
  }

  /** Checks flags for trigger debug reports. */
  private boolean isTriggerDebugFlagDisabled(String type) {
    if (!mFlags.getMeasurementEnableDebugReport()
        || !mFlags.getMeasurementEnableTriggerDebugReport()) {
      logger.info(String.format("Trigger flag is disabled for %s debug report", type));
      return true;
    }
    return false;
  }
}
