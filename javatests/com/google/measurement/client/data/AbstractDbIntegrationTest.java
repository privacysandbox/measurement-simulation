/*
 * Copyright (C) 2023 Google LLC
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
import com.google.measurement.client.EventReport;
import com.google.measurement.client.KeyValueData;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;
import com.google.measurement.client.Uri;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.aggregation.AggregateReport;
import com.google.measurement.client.registration.AsyncRegistration;
import com.google.measurement.client.reporting.DebugReport;
import com.google.measurement.client.util.UnsignedLong;
// import com.android.modules.utils.testing.TestableDeviceConfig;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Abstract class for parameterized tests that check the database state of measurement tables
 * against expected output after an action is run.
 *
 * <p>Consider @RunWith(Parameterized.class)
 */
public abstract class AbstractDbIntegrationTest {
  /** Inserts a Source record into the given database. */
  public static void insertToDb(Source source, SQLiteDatabase db) throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.SourceContract.ID, source.getId());
    values.put(
        MeasurementTables.SourceContract.EVENT_ID, getNullableUnsignedLong(source.getEventId()));
    values.put(MeasurementTables.SourceContract.SOURCE_TYPE, source.getSourceType().toString());
    values.put(MeasurementTables.SourceContract.PUBLISHER, source.getPublisher().toString());
    values.put(MeasurementTables.SourceContract.PUBLISHER_TYPE, source.getPublisherType());
    values.put(MeasurementTables.SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
    values.put(MeasurementTables.SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
    values.put(MeasurementTables.SourceContract.STATUS, source.getStatus());
    values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
    values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
    values.put(MeasurementTables.SourceContract.EVENT_REPORT_WINDOW, source.getEventReportWindow());
    values.put(
        MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
        source.getAggregatableReportWindow());
    values.put(MeasurementTables.SourceContract.PRIORITY, source.getPriority());
    values.put(MeasurementTables.SourceContract.REGISTRANT, source.getRegistrant().toString());
    values.put(
        MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
        source.getInstallAttributionWindow());
    values.put(
        MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
        source.getInstallCooldownWindow());
    values.put(
        MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
        source.isInstallAttributed() ? 1 : 0);
    values.put(MeasurementTables.SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
    values.put(
        MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
        source.getAggregateContributions());
    values.put(MeasurementTables.SourceContract.FILTER_DATA, source.getFilterDataString());
    values.put(MeasurementTables.SourceContract.DEBUG_REPORTING, source.isDebugReporting());
    values.put(MeasurementTables.SourceContract.INSTALL_TIME, source.getInstallTime());
    values.put(MeasurementTables.SourceContract.REGISTRATION_ID, source.getRegistrationId());
    values.put(
        MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
        source.getSharedAggregationKeys());
    values.put(
        MeasurementTables.SourceContract.SHARED_FILTER_DATA_KEYS, source.getSharedFilterDataKeys());
    values.put(
        MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
        source.getRegistrationOrigin().toString());
    values.put(
        MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
        listToCommaSeparatedString(source.getAggregateReportDedupKeys()));
    // Flex API
    values.put(MeasurementTables.SourceContract.TRIGGER_SPECS, source.getTriggerSpecsString());
    values.put(
        MeasurementTables.SourceContract.EVENT_ATTRIBUTION_STATUS,
        source.getEventAttributionStatus());
    long row = db.insert(MeasurementTables.SourceContract.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("Source insertion failed");
    }
  }

  private static String listToCommaSeparatedString(List<UnsignedLong> list) {
    return list.stream().map(UnsignedLong::toString).collect(Collectors.joining(","));
  }

  /** Inserts a SourceDestination into the given database. */
  public static void insertToDb(SourceDestination sourceDest, SQLiteDatabase db)
      throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.SourceDestination.SOURCE_ID, sourceDest.getSourceId());
    values.put(MeasurementTables.SourceDestination.DESTINATION, sourceDest.getDestination());
    values.put(
        MeasurementTables.SourceDestination.DESTINATION_TYPE, sourceDest.getDestinationType());
    long row = db.insert(MeasurementTables.SourceDestination.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("SourceDestination insertion failed");
    }
  }

  /** Inserts a Trigger record into the given database. */
  public static void insertToDb(Trigger trigger, SQLiteDatabase db) throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.TriggerContract.ID, trigger.getId());
    values.put(
        MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
        trigger.getAttributionDestination().toString());
    values.put(MeasurementTables.TriggerContract.DESTINATION_TYPE, trigger.getDestinationType());
    values.put(
        MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
        trigger.getAggregateTriggerData());
    values.put(
        MeasurementTables.TriggerContract.AGGREGATE_VALUES, trigger.getAggregateValuesString());
    values.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
    values.put(MeasurementTables.TriggerContract.STATUS, trigger.getStatus());
    values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
    values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, trigger.getEventTriggers());
    values.put(MeasurementTables.TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
    values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
    values.put(MeasurementTables.TriggerContract.NOT_FILTERS, trigger.getNotFilters());
    values.put(
        MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG, trigger.getAttributionConfig());
    values.put(
        MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING, trigger.getAdtechKeyMapping());
    values.put(
        MeasurementTables.TriggerContract.REGISTRATION_ORIGIN,
        trigger.getRegistrationOrigin().toString());
    values.put(
        MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
        trigger.getAggregatableSourceRegistrationTimeConfig().name());
    long row = db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("Trigger insertion failed");
    }
  }

  /** Inserts an EventReport record into the given database. */
  public static void insertToDb(EventReport report, SQLiteDatabase db) throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.EventReportContract.ID, report.getId());
    values.put(
        MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
        report.getSourceEventId().getValue());
    values.put(MeasurementTables.EventReportContract.ENROLLMENT_ID, report.getEnrollmentId());
    values.put(
        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
        String.join(
            " ",
            report.getAttributionDestinations().stream()
                .map(Uri::toString)
                .collect(Collectors.toList())));
    values.put(MeasurementTables.EventReportContract.REPORT_TIME, report.getReportTime());
    values.put(
        MeasurementTables.EventReportContract.TRIGGER_DATA, report.getTriggerData().getValue());
    values.put(MeasurementTables.EventReportContract.TRIGGER_PRIORITY, report.getTriggerPriority());
    values.put(
        MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
        getNullableUnsignedLong(report.getTriggerDedupKey()));
    values.put(MeasurementTables.EventReportContract.TRIGGER_TIME, report.getTriggerTime());
    values.put(MeasurementTables.EventReportContract.STATUS, report.getStatus());
    values.put(
        MeasurementTables.EventReportContract.SOURCE_TYPE, report.getSourceType().toString());
    values.put(
        MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
        report.getRandomizedTriggerRate());
    values.put(MeasurementTables.EventReportContract.SOURCE_ID, report.getSourceId());
    values.put(MeasurementTables.EventReportContract.TRIGGER_ID, report.getTriggerId());
    values.put(
        MeasurementTables.EventReportContract.REGISTRATION_ORIGIN,
        report.getRegistrationOrigin().toString());
    long row = db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("EventReport insertion failed");
    }
  }

  /** Inserts an Attribution record into the given database. */
  public static void insertToDb(Attribution attribution, SQLiteDatabase db) throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.AttributionContract.ID, attribution.getId());
    values.put(MeasurementTables.AttributionContract.SCOPE, attribution.getScope());
    values.put(MeasurementTables.AttributionContract.SOURCE_SITE, attribution.getSourceSite());
    values.put(MeasurementTables.AttributionContract.SOURCE_ORIGIN, attribution.getSourceOrigin());
    values.put(
        MeasurementTables.AttributionContract.DESTINATION_SITE, attribution.getDestinationSite());
    values.put(
        MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
        attribution.getDestinationOrigin());
    values.put(MeasurementTables.AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
    values.put(MeasurementTables.AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
    values.put(MeasurementTables.AttributionContract.REGISTRANT, attribution.getRegistrant());
    values.put(MeasurementTables.AttributionContract.SOURCE_ID, attribution.getSourceId());
    values.put(MeasurementTables.AttributionContract.TRIGGER_ID, attribution.getTriggerId());
    values.put(MeasurementTables.AttributionContract.REPORT_ID, attribution.getReportId());
    long row = db.insert(MeasurementTables.AttributionContract.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("Attribution insertion failed");
    }
  }

  /** Inserts an AggregateReport record into the given database. */
  public static void insertToDb(AggregateReport aggregateReport, SQLiteDatabase db)
      throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
    values.put(
        MeasurementTables.AggregateReport.PUBLISHER, aggregateReport.getPublisher().toString());
    values.put(
        MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
        aggregateReport.getAttributionDestination().toString());
    values.put(
        MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
        aggregateReport.getSourceRegistrationTime());
    values.put(
        MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
        aggregateReport.getScheduledReportTime());
    values.put(MeasurementTables.AggregateReport.ENROLLMENT_ID, aggregateReport.getEnrollmentId());
    values.put(
        MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
        aggregateReport.getDebugCleartextPayload());
    values.put(
        MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
        aggregateReport.getDebugReportStatus());
    values.put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus());
    values.put(MeasurementTables.AggregateReport.API_VERSION, aggregateReport.getApiVersion());
    values.put(MeasurementTables.AggregateReport.SOURCE_ID, aggregateReport.getSourceId());
    values.put(MeasurementTables.AggregateReport.TRIGGER_ID, aggregateReport.getTriggerId());
    values.put(
        MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
        aggregateReport.getRegistrationOrigin().toString());
    values.put(
        MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
        aggregateReport.getAggregationCoordinatorOrigin().toString());
    values.put(MeasurementTables.AggregateReport.IS_FAKE_REPORT, aggregateReport.isFakeReport());
    values.put(
        MeasurementTables.AggregateReport.DEDUP_KEY,
        aggregateReport.getDedupKey() != null ? aggregateReport.getDedupKey().getValue() : null);
    long row = db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("AggregateReport insertion failed");
    }
  }

  /** Inserts an AggregateEncryptionKey record into the given database. */
  public static void insertToDb(AggregateEncryptionKey aggregateEncryptionKey, SQLiteDatabase db)
      throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.AggregateEncryptionKey.ID, aggregateEncryptionKey.getId());
    values.put(MeasurementTables.AggregateEncryptionKey.KEY_ID, aggregateEncryptionKey.getKeyId());
    values.put(
        MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY, aggregateEncryptionKey.getPublicKey());
    values.put(MeasurementTables.AggregateEncryptionKey.EXPIRY, aggregateEncryptionKey.getExpiry());
    values.put(
        MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
        aggregateEncryptionKey.getAggregationCoordinatorOrigin().toString());
    long row = db.insert(MeasurementTables.AggregateEncryptionKey.TABLE, null, values);
    if (row == -1) {
      throw new SQLiteException("AggregateEncryptionKey insertion failed.");
    }
  }

  /** Inserts an AsyncRegistration record into the given database. */
  private static void insertToDb(AsyncRegistration asyncRegistration, SQLiteDatabase db)
      throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.AsyncRegistrationContract.ID, asyncRegistration.getId());
    values.put(
        MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
        asyncRegistration.getRegistrationUri().toString());
    values.put(
        MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
        getNullableUriString(asyncRegistration.getWebDestination()));
    values.put(
        MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
        getNullableUriString(asyncRegistration.getVerifiedDestination()));
    values.put(
        MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
        getNullableUriString(asyncRegistration.getOsDestination()));
    values.put(
        MeasurementTables.AsyncRegistrationContract.REGISTRANT,
        getNullableUriString(asyncRegistration.getRegistrant()));
    values.put(
        MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
        getNullableUriString(asyncRegistration.getTopOrigin()));
    values.put(
        MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
        asyncRegistration.getSourceType() == null
            ? null
            : asyncRegistration.getSourceType().ordinal());
    values.put(
        MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
        asyncRegistration.getRequestTime());
    values.put(
        MeasurementTables.AsyncRegistrationContract.RETRY_COUNT, asyncRegistration.getRetryCount());
    values.put(
        MeasurementTables.AsyncRegistrationContract.TYPE, asyncRegistration.getType().ordinal());
    values.put(
        MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
        asyncRegistration.getDebugKeyAllowed());
    values.put(
        MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
        asyncRegistration.hasAdIdPermission());
    values.put(
        MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
        asyncRegistration.getRegistrationId());
    values.put(
        MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID,
        asyncRegistration.getPlatformAdId());
    values.put(
        MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
        asyncRegistration.getRedirectBehavior().name());
    long rowId =
        db.insert(
            MeasurementTables.AsyncRegistrationContract.TABLE, /* nullColumnHack= */ null, values);
    if (rowId == -1) {
      throw new SQLiteException("Async Registration insertion failed.");
    }
  }

  public static void insertToDb(KeyValueData keyValueData, SQLiteDatabase db)
      throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.KeyValueDataContract.KEY, keyValueData.getKey());
    values.put(MeasurementTables.KeyValueDataContract.VALUE, keyValueData.getValue());
    values.put(
        MeasurementTables.KeyValueDataContract.DATA_TYPE, keyValueData.getDataType().toString());
    long rowId =
        db.insert(MeasurementTables.KeyValueDataContract.TABLE, /* nullColumnHack= */ null, values);
    if (rowId == -1) {
      throw new SQLiteException("KeyValueData insertion failed.");
    }
  }

  public static void insertToDb(DebugReport debugReport, SQLiteDatabase db) throws SQLiteException {
    ContentValues values = new ContentValues();
    values.put(MeasurementTables.DebugReportContract.ID, debugReport.getId());
    values.put(MeasurementTables.DebugReportContract.BODY, debugReport.getBody().toString());
    values.put(MeasurementTables.DebugReportContract.TYPE, debugReport.getType());
    values.put(
        MeasurementTables.DebugReportContract.REGISTRANT,
        getNullableUriString(debugReport.getRegistrant()));
    long rowId =
        db.insert(MeasurementTables.DebugReportContract.TABLE, /* nullColumnHack= */ null, values);
    if (rowId == -1) {
      throw new SQLiteException("DebugReport insertion failed.");
    }
  }

  private static Long getNullableUnsignedLong(UnsignedLong ulong) {
    return Optional.ofNullable(ulong).map(UnsignedLong::getValue).orElse(null);
  }

  private static String getNullableUriString(Uri uri) {
    return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
  }
}
