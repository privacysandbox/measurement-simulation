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
import java.net.URI;
import java.util.List;
import java.util.UUID;

public final class EventReportFixture {
  private EventReportFixture() {}

  public static EventReport.Builder getBaseEventReportBuild() {
    return new EventReport.Builder()
        .setSourceEventId(new UnsignedLong(21L))
        .setEnrollmentId("enrollment-id")
        .setAttributionDestinations(List.of(URI.create("https://bar.test")))
        .setTriggerTime(1000L)
        .setTriggerDedupKey(new UnsignedLong(3L))
        .setReportTime(2000L)
        .setStatus(EventReport.Status.PENDING)
        .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
        .setSourceType(Source.SourceType.NAVIGATION)
        .setSourceDebugKey(new UnsignedLong(237865L))
        .setTriggerDebugKey(new UnsignedLong(928762L))
        .setSourceId(UUID.randomUUID().toString())
        .setTriggerId(UUID.randomUUID().toString())
        .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test"))
        .setTriggerSummaryBucket("2,3");
  }
}
