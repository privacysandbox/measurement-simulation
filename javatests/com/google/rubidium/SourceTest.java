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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.google.rubidium.aggregation.AggregatableAttributionSource;
import com.google.rubidium.noising.ImpressionNoiseParams;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class SourceTest {
  private static final double DOUBLE_MAX_DELTA = 0.000000001D;

  @Test
  public void testDefaults() {
    Source source =
        new Source.Builder().setAppDestination(URI.create("https://example.com/aD")).build();
    assertEquals(0, source.getDedupKeys().size());
    assertEquals(Source.Status.ACTIVE, source.getStatus());
    assertEquals(Source.SourceType.EVENT, source.getSourceType());
    assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
  }

  @Test
  public void testEqualsPass() {
    assertEquals(
        new Source.Builder().setAppDestination(URI.create("https://example.com/aD")).build(),
        new Source.Builder().setAppDestination(URI.create("https://example.com/aD")).build());
    JSONArray aggregateSource = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("id", "campaignCounts");
    jsonObject1.put("key_piece", "0x159");
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("id", "geoValue");
    jsonObject2.put("key_piece", "0x5");
    aggregateSource.add(jsonObject1);
    aggregateSource.add(jsonObject2);
    JSONObject aggregateFilterData = new JSONObject();
    aggregateFilterData.put("conversion_subdomain", Arrays.asList("electronics.megastore"));
    aggregateFilterData.put("product", Arrays.asList("1234", "2345"));
    assertEquals(
        new Source.Builder()
            .setEnrollmentId("https://example.com")
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("https://example.com/aS"))
            .setId("1")
            .setEventId(2L)
            .setPriority(3L)
            .setEventTime(5L)
            .setExpiryTime(5L)
            .setDedupKeys(LongStream.range(0, 2).boxed().collect(Collectors.toList()))
            .setStatus(Source.Status.ACTIVE)
            .setSourceType(Source.SourceType.EVENT)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateFilterData(aggregateFilterData.toString())
            .setAggregateSource(aggregateSource.toString())
            .build(),
        new Source.Builder()
            .setEnrollmentId("https://example.com")
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("https://example.com/aS"))
            .setId("1")
            .setEventId(2L)
            .setPriority(3L)
            .setEventTime(5L)
            .setExpiryTime(5L)
            .setDedupKeys(LongStream.range(0, 2).boxed().collect(Collectors.toList()))
            .setStatus(Source.Status.ACTIVE)
            .setSourceType(Source.SourceType.EVENT)
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .setAggregateFilterData(aggregateFilterData.toString())
            .setAggregateSource(aggregateSource.toString())
            .build());
  }

  @Test
  public void testEqualsFail() {
    assertNotEquals(
        new Source.Builder()
            .setEventId(1)
            .setAppDestination(URI.create("https://example.com/aD"))
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventId(2)
            .build());
    assertNotEquals(
        new Source.Builder().setAppDestination(URI.create("1")).build(),
        new Source.Builder().setAppDestination(URI.create("2")).build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEnrollmentId(("1"))
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEnrollmentId(("2"))
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("1"))
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("2"))
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPriority(1L)
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPriority(2L)
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(1L)
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(2L)
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setExpiryTime(1L)
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setExpiryTime(2L)
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.EVENT)
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.NAVIGATION)
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setStatus(Source.Status.ACTIVE)
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setStatus(Source.Status.IGNORED)
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setDedupKeys(LongStream.range(0, 2).boxed().collect(Collectors.toList()))
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setDedupKeys(LongStream.range(1, 3).boxed().collect(Collectors.toList()))
            .build());
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setRegistrant(URI.create("android-app://com.example.abc"))
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setRegistrant(URI.create("android-app://com.example.xyz"))
            .build());
    JSONArray aggregateSource1 = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("id", "campaignCounts");
    jsonObject1.put("key_piece", "0x159");
    aggregateSource1.add(jsonObject1);
    JSONArray aggregateSource2 = new JSONArray();
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("id", "geoValue");
    jsonObject2.put("key_piece", "0x5");
    aggregateSource2.add(jsonObject2);
    assertNotEquals(
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setAggregateSource(aggregateSource1.toString())
            .build(),
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setAggregateSource(aggregateSource2.toString())
            .build());
  }

  @Test
  public void testGetReportingTimeEvent() {
    long triggerTime = System.currentTimeMillis();
    long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
    long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
    Source source =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setExpiryTime(expiryTime)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(sourceEventTime)
            .build();
    assertEquals(
        expiryTime + TimeUnit.HOURS.toMillis(1),
        source.getReportingTime(triggerTime, EventSurfaceType.APP));
  }

  @Test
  public void testGetReportingTimeNavigationFirst() {
    long triggerTime = System.currentTimeMillis();
    long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
    long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
    Source source =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(sourceExpiryTime)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(sourceEventTime)
            .build();
    assertEquals(
        sourceEventTime
            + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
            + TimeUnit.HOURS.toMillis(1),
        source.getReportingTime(triggerTime, EventSurfaceType.APP));
  }

  @Test
  public void testGetReportingTimeNavigationSecond() {
    long triggerTime = System.currentTimeMillis();
    long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
    long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
    Source source =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(sourceExpiryTime)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(sourceEventTime)
            .build();
    assertEquals(
        sourceEventTime
            + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
            + TimeUnit.HOURS.toMillis(1),
        source.getReportingTime(triggerTime, EventSurfaceType.APP));
  }

  @Test
  public void testGetReportingTimeNavigationSecondExpiry() {
    long triggerTime = System.currentTimeMillis();
    long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
    long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
    Source source =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(sourceExpiryTime)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(sourceEventTime)
            .build();
    assertEquals(
        sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
        source.getReportingTime(triggerTime, EventSurfaceType.APP));
  }

  @Test
  public void testGetReportingTimeNavigationLast() {
    long triggerTime = System.currentTimeMillis();
    long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
    long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
    Source source =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setExpiryTime(sourceExpiryTime)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(sourceEventTime)
            .build();
    assertEquals(
        sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
        source.getReportingTime(triggerTime, EventSurfaceType.APP));
  }

  @Test
  public void testTriggerDataCardinality() {
    Source eventSource =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.EVENT)
            .build();
    assertEquals(
        PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY, eventSource.getTriggerDataCardinality());
    Source navigationSource =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();
    assertEquals(
        PrivacyParams.NAVIGATION_TRIGGER_DATA_CARDINALITY,
        navigationSource.getTriggerDataCardinality());
  }

  @Test
  public void testMaxReportCount() {
    Source eventSource =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .build();
    assertEquals(
        PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
        eventSource.getMaxReportCount(EventSurfaceType.APP));
    Source navigationSource =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();
    assertEquals(
        PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
        navigationSource.getMaxReportCount(EventSurfaceType.APP));
  }

  @Test
  public void testRandomAttributionProbability() {
    Source eventSource =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .build();
    assertEquals(
        PrivacyParams.EVENT_NOISE_PROBABILITY,
        eventSource.getRandomAttributionProbability(),
        DOUBLE_MAX_DELTA);
    Source navigationSource =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setSourceType(Source.SourceType.NAVIGATION)
            .build();
    assertEquals(
        PrivacyParams.NAVIGATION_NOISE_PROBABILITY,
        navigationSource.getRandomAttributionProbability(),
        DOUBLE_MAX_DELTA);
    Source eventSourceWithInstallAttribution =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(1)
            .build();
    assertEquals(
        PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
        eventSourceWithInstallAttribution.getRandomAttributionProbability(),
        DOUBLE_MAX_DELTA);
    Source navigationSourceWithInstallAttribution =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(1)
            .build();
    assertEquals(
        PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
        navigationSourceWithInstallAttribution.getRandomAttributionProbability(),
        DOUBLE_MAX_DELTA);
  }

  @Test
  public void testFakeReportGeneration() {
    long expiry = System.currentTimeMillis();
    Source source =
        spy(
            new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setAppDestination(URI.create("https://example.com/aD"))
                .setExpiryTime(expiry)
                .build());
    // Increase the probability of random attribution.
    doReturn(0.50D).when(source).getRandomAttributionProbability();
    int falseCount = 0;
    int neverCount = 0;
    int truthCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports = source.assignAttributionModeAndGenerateFakeReports();
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        falseCount++;
        assertNotEquals(0, fakeReports.size());
        for (Source.FakeReport report : fakeReports) {
          assertTrue(expiry + TimeUnit.HOURS.toMillis(1) >= report.getReportingTime());
          assertTrue(report.getTriggerData() < source.getTriggerDataCardinality());
        }
      } else if (source.getAttributionMode() == Source.AttributionMode.NEVER) {
        neverCount++;
        assertEquals(0, fakeReports.size());
      } else {
        truthCount++;
      }
    }
    assertNotEquals(0, falseCount);
    assertNotEquals(0, neverCount);
    assertNotEquals(0, truthCount);
  }

  @Test
  public void impressionNoiseParamGeneration() {
    long eventTime = System.currentTimeMillis();
    Source eventSource30dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        eventSource30dExpiry.getImpressionNoiseParams());
    Source eventSource7dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        eventSource7dExpiry.getImpressionNoiseParams());
    Source eventSource2dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        eventSource2dExpiry.getImpressionNoiseParams());
    Source navigationSource30dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 3,
            /* destinationMultiplier= */ 1),
        navigationSource30dExpiry.getImpressionNoiseParams());
    Source navigationSource7dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier= */ 1),
        navigationSource7dExpiry.getImpressionNoiseParams());
    Source navigationSource2dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        navigationSource2dExpiry.getImpressionNoiseParams());
  }

  @Test
  public void impressionNoiseParamGeneration_withInstallAttribution() {
    long eventTime = System.currentTimeMillis();
    Source eventSource30dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier= */ 1),
        eventSource30dExpiry.getImpressionNoiseParams());
    Source eventSource7dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier= */ 1),
        eventSource7dExpiry.getImpressionNoiseParams());
    Source eventSource2dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        eventSource2dExpiry.getImpressionNoiseParams());
    Source navigationSource30dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 3,
            /* destinationMultiplier= */ 1),
        navigationSource30dExpiry.getImpressionNoiseParams());
    Source navigationSource7dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier= */ 1),
        navigationSource7dExpiry.getImpressionNoiseParams());
    Source navigationSource2dExpiry =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier= */ 1),
        navigationSource2dExpiry.getImpressionNoiseParams());
  }

  @Test
  public void reportingTimeByIndex_event() {
    long eventTime = System.currentTimeMillis();
    long oneHourInMillis = TimeUnit.HOURS.toMillis(1);
    // Expected: 1 window at expiry
    Source eventSource10d =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
        eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
        eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 1));
    // Expected: 1 window at expiry
    Source eventSource7d =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        eventSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        eventSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));
    // Expected: 1 window at expiry
    Source eventSource2d =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
  }

  @Test
  public void reportingTimeByIndex_eventWithInstallAttribution() {
    long eventTime = System.currentTimeMillis();
    long oneHourInMillis = TimeUnit.HOURS.toMillis(1);
    // Expected: 2 windows at 2d, expiry(10d)
    Source eventSource10d =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
        eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 1));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
        eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 2));
    // Expected: 1 window at 2d(expiry)
    Source eventSource2d =
        new Source.Builder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
  }

  @Test
  public void reportingTimeByIndex_navigation() {
    long eventTime = System.currentTimeMillis();
    long oneHourInMillis = TimeUnit.HOURS.toMillis(1);
    // Expected: 3 windows at 2d, 7d & expiry(20d)
    Source navigationSource20d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 1));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 2));
    // Expected: 2 windows at 2d & expiry(7d)
    Source navigationSource7d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 2));
    // Expected: 1 window at 2d(expiry)
    Source navigationSource2d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
  }

  @Test
  public void reportingTimeByIndex_navigationWithInstallAttribution() {
    long eventTime = System.currentTimeMillis();
    long oneHourInMillis = TimeUnit.HOURS.toMillis(1);
    // Expected: 3 windows at 2d, 7d & expiry(20d)
    Source navigationSource20d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 1));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
        navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 2));
    // Expected: 2 windows at 2d & expiry(7d)
    Source navigationSource7d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
        navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 2));
    // Expected: 1 window at 2d(expiry)
    Source navigationSource2d =
        new Source.Builder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setAppDestination(URI.create("https://example.com/aD"))
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
    assertEquals(
        eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
        navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
  }

  @Test
  public void testParseAggregateSource() throws ParseException {
    JSONArray aggregatableSource = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("id", "campaignCounts");
    jsonObject1.put("key_piece", "0x159");
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("id", "geoValue");
    jsonObject2.put("key_piece", "0x5");
    aggregatableSource.add(jsonObject1);
    aggregatableSource.add(jsonObject2);
    JSONObject filterData = new JSONObject();
    JSONArray conversionsubdomainArray = new JSONArray();
    conversionsubdomainArray.add("electronics.megastore");
    JSONArray productArray = new JSONArray();
    productArray.addAll(Arrays.asList("1234", "2345"));
    filterData.put("conversion_subdomain", conversionsubdomainArray);
    filterData.put("product", productArray);
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setAggregateSource(aggregatableSource.toString())
            .setAggregateFilterData(filterData.toString())
            .build();
    Optional<AggregatableAttributionSource> aggregatableAttributionSource =
        source.parseAggregateSource();
    assertTrue(aggregatableAttributionSource.isPresent());
    AggregatableAttributionSource aggregateSource = aggregatableAttributionSource.get();
    assertEquals(aggregateSource.getAggregatableSource().size(), 2);
    assertEquals(aggregateSource.getAggregatableSource().get("campaignCounts").longValue(), 345L);
    assertEquals(aggregateSource.getAggregatableSource().get("geoValue").longValue(), 5L);
    assertEquals(aggregateSource.getAggregateFilterData().getAttributionFilterMap().size(), 2);
  }
}
