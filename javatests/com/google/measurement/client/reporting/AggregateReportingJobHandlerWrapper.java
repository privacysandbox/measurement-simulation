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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.AndroidTimeSource;
import com.google.measurement.client.ApplicationProvider;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.Flags;
import com.google.measurement.client.Uri;
import com.google.measurement.client.aggregation.AggregateCryptoFixture;
import com.google.measurement.client.aggregation.AggregateEncryptionKey;
import com.google.measurement.client.aggregation.AggregateEncryptionKeyManager;
import com.google.measurement.client.aggregation.AggregateReport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.util.concurrent.TimeUnit;

/** A wrapper class to expose a constructor for AggregateReportingJobHandler in testing. */
public class AggregateReportingJobHandlerWrapper {
  public static Object[] spyPerformScheduledPendingReportsInWindow(
      DatastoreManager datastoreManager,
      long windowStartTime,
      long windowEndTime,
      boolean isDebugInstance,
      Flags flags)
      throws IOException, JSONException {
    // Setup encryption manager to return valid public keys
    ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
    AggregateEncryptionKeyManager mockEncryptionManager =
        Mockito.mock(AggregateEncryptionKeyManager.class);
    AdServicesLogger mockLogger = Mockito.mock(AdServicesLogger.class);
    when(mockEncryptionManager.getAggregateEncryptionKeys(any(), captorNumberOfKeys.capture()))
        .thenAnswer(
            invocation -> {
              List<AggregateEncryptionKey> keys = new ArrayList<>();
              for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                keys.add(AggregateCryptoFixture.getKey());
              }
              return keys;
            });

    // Mock TimeSource
    AndroidTimeSource mTimeSource = Mockito.spy(new AndroidTimeSource());

    // Set up aggregate reporting job handler spy
    AggregateReportingJobHandler aggregateReportingJobHandler =
        Mockito.spy(
            new AggregateReportingJobHandler(
                    datastoreManager,
                    mockEncryptionManager,
                    flags,
                    mockLogger,
                    ApplicationProvider.getApplicationContext(),
                    mTimeSource)
                .setIsDebugInstance(isDebugInstance));
    Mockito.doReturn(200)
        .when(aggregateReportingJobHandler)
        .makeHttpPostRequest(any(), any(), any(), anyString());

    Mockito.doReturn(windowEndTime + TimeUnit.HOURS.toMillis(2))
        .when(mTimeSource)
        .currentTimeMillis();

    // Perform aggregate reports and capture arguments
    aggregateReportingJobHandler.performScheduledPendingReportsInWindow(
        windowStartTime, windowEndTime);

    ArgumentCaptor<Uri> aggregateDestination = ArgumentCaptor.forClass(Uri.class);
    ArgumentCaptor<JSONObject> aggregatePayload = ArgumentCaptor.forClass(JSONObject.class);
    verify(aggregateReportingJobHandler, atLeast(0))
        .makeHttpPostRequest(
            aggregateDestination.capture(), aggregatePayload.capture(), any(), anyString());

    ArgumentCaptor<AggregateReport> aggregateReport =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(aggregateReportingJobHandler, atLeast(0))
        .createReportJsonPayload(aggregateReport.capture(), any(), any());

    // Collect actual reports
    return new Object[] {
      aggregateReport.getAllValues(),
      aggregateDestination.getAllValues(),
      aggregatePayload.getAllValues()
    };
  }
}
