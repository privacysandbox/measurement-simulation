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

package com.google.rubidium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Unit tests for {@link EventReport} */
public final class EventReportTest {
  private static final double DOUBLE_MAX_DELTA = 0.0000001D;

  private EventReport createExample() {
    return new EventReport.Builder()
        .setId("1")
        .setSourceId(21)
        .setEnrollmentId("http://foo.com")
        .setAttributionDestination(URI.create("http://bar.com"))
        .setTriggerTime(1000L)
        .setTriggerData(8L)
        .setTriggerPriority(2L)
        .setTriggerDedupKey(3L)
        .setReportTime(2000L)
        .setStatus(EventReport.Status.PENDING)
        .setSourceType(Source.SourceType.NAVIGATION)
        .build();
  }

  @Test
  public void testCreation() throws Exception {
    EventReport eventReport = createExample();
    assertEquals("1", eventReport.getId());
    assertEquals(21, eventReport.getSourceId());
    assertEquals("http://foo.com", eventReport.getEnrollmentId());
    assertEquals("http://bar.com", eventReport.getAttributionDestination().toString());
    assertEquals(1000L, eventReport.getTriggerTime());
    assertEquals(8L, eventReport.getTriggerData());
    assertEquals(2L, eventReport.getTriggerPriority());
    assertEquals(Long.valueOf(3), eventReport.getTriggerDedupKey());
    assertEquals(2000L, eventReport.getReportTime());
    assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
    assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
  }

  @Test
  public void testDefaults() throws Exception {
    EventReport eventReport = new EventReport.Builder().build();
    assertEquals(0L, eventReport.getSourceId());
    assertNull(eventReport.getEnrollmentId());
    assertNull(eventReport.getAttributionDestination());
    assertEquals(0L, eventReport.getTriggerTime());
    assertEquals(0L, eventReport.getTriggerData());
    assertEquals(0L, eventReport.getTriggerPriority());
    assertNull(eventReport.getTriggerDedupKey());
    assertEquals(0L, eventReport.getReportTime());
    assertNull(eventReport.getSourceType());
  }

  private Source createSourceForTest(
      long eventTime, Source.SourceType sourceType, boolean isInstallAttributable) {
    return new Source.Builder()
        .setEventId(10)
        .setSourceType(sourceType)
        .setInstallCooldownWindow(isInstallAttributable ? 100 : 0)
        .setEventTime(eventTime)
        .setEnrollmentId("https://example-adtech1.com")
        .setAppDestination(URI.create("android-app://example1.app"))
        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
        .build();
  }

  private Trigger createTriggerForTest(long eventTime) {
    return new Trigger.Builder()
        .setTriggerTime(eventTime)
        .setEnrollmentId("https://example-adtech2.com")
        .setAttributionDestination(URI.create("android-app://example2.app"))
        .setEventTriggers("Data: 4")
        .build();
  }

  @Test
  public void testPopulateFromSourceAndTrigger_event() {
    long baseTime = System.currentTimeMillis();
    Source source = createSourceForTest(baseTime, Source.SourceType.EVENT, false);
    Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));

    EventTrigger eventTrigger =
        new EventTrigger.Builder().setTriggerData(4L).setTriggerPriority(1L).build();

    EventReport report =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(source, trigger, eventTrigger)
            .build();
    // Truncated data 4 % 2 = 0
    assertEquals(0, report.getTriggerData());
    assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
    assertEquals(source.getEventId(), report.getSourceId());
    assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
    assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
    assertEquals(source.getSourceType(), report.getSourceType());
    assertEquals(
        PrivacyParams.EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
  }

  @Test
  public void testPopulateFromSourceAndTrigger_eventWithInstallAttribution() {
    long baseTime = System.currentTimeMillis();
    Source source = createSourceForTest(baseTime, Source.SourceType.EVENT, true);
    Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));
    EventTrigger eventTrigger =
        new EventTrigger.Builder().setTriggerData(4L).setTriggerPriority(1L).build();
    EventReport report =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(source, trigger, eventTrigger)
            .build();
    assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
    assertEquals(source.getEventId(), report.getSourceId());
    assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
    assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
    assertEquals(source.getSourceType(), report.getSourceType());
    assertEquals(
        PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
        report.getRandomizedTriggerRate(),
        DOUBLE_MAX_DELTA);
  }

  @Test
  public void testPopulateFromSourceAndTrigger_navigation() {
    long baseTime = System.currentTimeMillis();
    Source source = createSourceForTest(baseTime, Source.SourceType.NAVIGATION, false);
    Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));
    EventTrigger eventTrigger =
        new EventTrigger.Builder().setTriggerData(4L).setTriggerPriority(1L).build();
    EventReport report =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(source, trigger, eventTrigger)
            .build();
    assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
    assertEquals(source.getEventId(), report.getSourceId());
    assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
    assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
    assertEquals(source.getSourceType(), report.getSourceType());
    assertEquals(
        PrivacyParams.NAVIGATION_NOISE_PROBABILITY,
        report.getRandomizedTriggerRate(),
        DOUBLE_MAX_DELTA);
  }

  @Test
  public void testPopulateFromSourceAndTrigger_navigationWithInstallAttribution() {
    long baseTime = System.currentTimeMillis();
    Source source = createSourceForTest(baseTime, Source.SourceType.NAVIGATION, true);
    Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));
    EventTrigger eventTrigger =
        new EventTrigger.Builder().setTriggerData(4L).setTriggerPriority(1L).build();
    EventReport report =
        new EventReport.Builder()
            .populateFromSourceAndTrigger(source, trigger, eventTrigger)
            .build();
    assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
    assertEquals(source.getEventId(), report.getSourceId());
    assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
    assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
    assertEquals(source.getSourceType(), report.getSourceType());
    assertEquals(
        PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
        report.getRandomizedTriggerRate(),
        DOUBLE_MAX_DELTA);
  }
}
