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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.google.measurement.client.AndroidTimeSource;
import com.google.measurement.client.Context;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.EventReport;
import com.google.measurement.client.Flags;
import com.google.measurement.client.Uri;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.util.concurrent.TimeUnit;

/** A wrapper class to expose a constructor for EventReportingJobHandler in testing. */
public class EventReportingJobHandlerWrapper {
  public static Object[] spyPerformScheduledPendingReportsInWindow(
      DatastoreManager datastoreManager,
      long windowStartTime,
      long windowEndTime,
      boolean isDebugInstance,
      Flags flags,
      Context context)
      throws IOException, JSONException {

    // Mock TimeSource
    AndroidTimeSource mTimeSource = Mockito.spy(new AndroidTimeSource());

    // Set up event reporting job handler spy
    EventReportingJobHandler eventReportingJobHandler =
        Mockito.spy(
            new EventReportingJobHandler(datastoreManager, flags, context, mTimeSource)
                .setIsDebugInstance(isDebugInstance));
    Mockito.doReturn(200).when(eventReportingJobHandler).makeHttpPostRequest(any(), any(), any());

    Mockito.doReturn(windowEndTime + TimeUnit.HOURS.toMillis(2))
        .when(mTimeSource)
        .currentTimeMillis();

    // Perform event reports and capture arguments
    eventReportingJobHandler.performScheduledPendingReportsInWindow(windowStartTime, windowEndTime);
    ArgumentCaptor<Uri> eventDestination = ArgumentCaptor.forClass(Uri.class);
    ArgumentCaptor<EventReport> eventReport = ArgumentCaptor.forClass(EventReport.class);
    verify(eventReportingJobHandler, atLeast(0)).createReportJsonPayload(eventReport.capture());
    ArgumentCaptor<JSONObject> eventPayload = ArgumentCaptor.forClass(JSONObject.class);
    verify(eventReportingJobHandler, atLeast(0))
        .makeHttpPostRequest(eventDestination.capture(), eventPayload.capture(), any());

    eventReportingJobHandler.performScheduledPendingReportsInWindow(windowStartTime, windowEndTime);

    // Collect actual reports
    return new Object[] {
      eventReport.getAllValues(), eventDestination.getAllValues(), eventPayload.getAllValues()
    };
  }
}
