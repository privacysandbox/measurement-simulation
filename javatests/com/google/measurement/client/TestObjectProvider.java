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

package com.google.measurement.client;

import static org.mockito.Mockito.spy;

import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.deletion.MeasurementDataDeleter;
import com.google.measurement.client.attribution.AttributionJobHandlerWrapper;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.registration.AsyncRegistrationQueueRunner;
import com.google.measurement.client.registration.AsyncSourceFetcher;
import com.google.measurement.client.registration.AsyncTriggerFetcher;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.stats.AdServicesLoggerImpl;

class TestObjectProvider {
  static AttributionJobHandlerWrapper getAttributionJobHandler(
      DatastoreManager datastoreManager, Flags flags) {
    return new AttributionJobHandlerWrapper(
        datastoreManager,
        flags,
        new DebugReportApi(
            ApplicationProvider.getApplicationContext(),
            flags,
            new EventReportWindowCalcDelegate(flags),
            new SourceNoiseHandler(flags)),
        new EventReportWindowCalcDelegate(flags),
        new SourceNoiseHandler(flags),
        AdServicesLoggerImpl.getInstance(),
        new AggregateDebugReportApi(flags));
  }

  static MeasurementImpl getMeasurementImpl(
      DatastoreManager datastoreManager,
      ClickVerifier clickVerifier,
      MeasurementDataDeleter measurementDataDeleter,
      ContentResolver contentResolver) {
    return spy(
        new MeasurementImpl(
            null,
            FlagsFactory.getFlags(),
            datastoreManager,
            clickVerifier,
            measurementDataDeleter,
            contentResolver));
  }

  static AsyncRegistrationQueueRunner getAsyncRegistrationQueueRunner(
      SourceNoiseHandler sourceNoiseHandler,
      DatastoreManager datastoreManager,
      AsyncSourceFetcher asyncSourceFetcher,
      AsyncTriggerFetcher asyncTriggerFetcher,
      DebugReportApi debugReportApi,
      AggregateDebugReportApi aggregateDebugReportApi,
      Flags flags) {
    return new AsyncRegistrationQueueRunner(
        ApplicationProvider.getApplicationContext(),
        new MockContentResolver(),
        asyncSourceFetcher,
        asyncTriggerFetcher,
        datastoreManager,
        debugReportApi,
        aggregateDebugReportApi,
        sourceNoiseHandler,
        flags);
  }
}
