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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.measurement.Trigger.Status;
import com.google.measurement.aggregation.AggregateAttributionData;
import com.google.measurement.aggregation.AggregateHistogramContribution;
import com.google.measurement.aggregation.AggregateReport;
import com.google.measurement.util.UnsignedLong;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AttributionJobHandlerTest {
  @Mock IMeasurementDAO mMeasurementDao;

  @Mock Source mockedSource;

  private static final URI APP_DESTINATION = URI.create("android-app://com.example.app");
  private static final URI PUBLISHER = URI.create("android-app://publisher.app");
  private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
  private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
  private static final String EVENT_TRIGGERS =
      "[\n"
          + "{\n"
          + "  \"trigger_data\": 5,\n"
          + "  \"priority\": 123,\n"
          + "  \"deduplication_key\": 2,\n"
          + "  \"filters\": [{\n"
          + "    \"source_type\": [\"event\"],\n"
          + "    \"key_1\": [\"value_1\"] \n"
          + "   }]\n"
          + "}"
          + "]\n";
  private static final String AGGREGATE_DEDUPLICATION_KEYS_1 =
      "[{\"deduplication_key\": \"10\" }" + "]";

  private static Trigger createAPendingTrigger() throws ParseException {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("triggerId1")
        .setStatus(Trigger.Status.PENDING)
        .setEventTriggers(EVENT_TRIGGERS)
        .build();
  }

  DatastoreManager mDatastoreManager;
  AttributionJobHandler mHandler;

  @Before
  public void before() {
    mHandler = new AttributionJobHandler(mMeasurementDao);
  }

  @Test
  public void shouldIgnoreNonPendingTrigger() throws ParseException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.IGNORED)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldIgnoreIfNoSourcesFound() throws ParseException {
    Trigger trigger = createAPendingTrigger();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
    mHandler.performPendingAttributions();

    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldRejectBasedOnDedupKey() throws ParseException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"event\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setEventReportDedupKeys(Arrays.asList(new UnsignedLong(1L), new UnsignedLong(2L)))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow() throws ParseException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[{\"trigger_data\":1," + "\"filters\":[{\"source_type\": [\"event\"]}]" + "}]")
            .setTriggerTime(triggerTime)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime - 1)
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow_secondTrigger()
      throws ParseException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\": 1}]")
            .setTriggerTime(triggerTime)
            .build();
    Trigger trigger2 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId2")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"trigger_data\": 0}]")
            .setTriggerTime(triggerTime + 1)
            .build();
    List<Trigger> triggers = new ArrayList<>();
    triggers.add(trigger1);
    triggers.add(trigger2);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime)
            .build());
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Arrays.asList(trigger1, trigger2));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);

    mHandler.performPendingAttributions();

    // Verify trigger status updates.
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger1.getId()).getStatus());
    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger2.getId()).getStatus());

    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao, times(1)).insertEventReport(reportArg.capture());
    List<EventReport> newReportArgs = reportArg.getAllValues();
    assertEquals(1, newReportArgs.size());
    assertEquals(
        newReportArgs.get(0).getTriggerData(),
        triggers.get(0).parseEventTriggers().get(0).getTriggerData());
  }

  @Test
  public void shouldNotCreateEventReportAfterEventReportWindow_prioritisedSource()
      throws ParseException {
    String eventTriggers =
        "[{\"trigger_data\": 5,"
            + "\"priority\": 123,"
            + "\"filters\":[{\"key_1\":[\"value_1\"]}]"
            + "}]";
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .setTriggerTime(triggerTime)
            .build();
    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime)
            .build();
    // Second source has higher priority but the event report window ends before trigger time
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime + 1000)
            .setEventReportWindow(triggerTime - 1)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    assertEquals(1, matchingSourceList.size());
    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded() throws ParseException {
    Trigger trigger = createAPendingTrigger();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(105L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao).getAttributionsPerRateLimitWindow(source, trigger);
    assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    assertEquals(Trigger.Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotAddIfAdTechPrivacyBoundExceeded() throws ParseException {
    Trigger trigger = createAPendingTrigger();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            any(), any(), any(), anyLong(), anyLong()))
        .thenReturn(10);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao)
        .countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            any(), any(), any(), anyLong(), anyLong());
    assertSame(Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldIgnoreForMaxReportsPerSource() throws ParseException {
    Trigger trigger = createAPendingTrigger();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport2 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport3 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    assertEquals(Trigger.Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotReplaceHighPriorityReports() throws ParseException {
    String eventTriggers =
        "[\n"
            + "{\n"
            + "  \"trigger_data\": 5,\n"
            + "  \"priority\": 100,\n"
            + "  \"deduplication_key\": 2\n"
            + "}"
            + "]\n";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setEventTriggers(eventTriggers)
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(200L)
            .build();
    EventReport eventReport2 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    EventReport eventReport3 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    assertEquals(Trigger.Status.IGNORED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldDoSimpleAttribution() throws ParseException {
    Trigger trigger = createAPendingTrigger();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    mHandler.performPendingAttributions();
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(2L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldIgnoreLowPrioritySourceWhileAttribution() throws ParseException {
    String eventTriggers =
        "[\n"
            + "{\n"
            + "  \"trigger_data\": 5,\n"
            + "  \"priority\": 123,\n"
            + "  \"deduplication_key\": 2,\n"
            + "  \"filters\": [{\n"
            + "    \"key_1\": [\"value_1\"] \n"
            + "   }]\n"
            + "}"
            + "]\n";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(eventTriggers)
            .build();
    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setId("source1")
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(1L)
            .build();
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setId("source2")
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(2L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao).updateSourceStatus(eq(List.of(source1)), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(2L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldReplaceLowPriorityReportWhileAttribution() throws ParseException {
    String eventTriggers =
        "[\n"
            + "{\n"
            + "  \"trigger_data\": 5,\n"
            + "  \"priority\": 200,\n"
            + "  \"deduplication_key\": 2\n"
            + "}"
            + "]\n";
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setEventTriggers(eventTriggers)
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source = spy(Source.class);
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(100L)
            .setReportTime(5L)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .build();
    EventReport eventReport2 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.DELIVERED)
            .setReportTime(5L)
            .setAttributionDestinations(source.getAppDestinations())
            .build();
    EventReport eventReport3 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.DELIVERED)
            .setReportTime(5L)
            .setAttributionDestinations(source.getAppDestinations())
            .build();
    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(source.getReportingTime(anyLong(), any(EventSurfaceType.class))).thenReturn(5L);
    when(source.getEventReportDedupKeys()).thenReturn(new ArrayList<>());
    when(source.getAttributionMode()).thenReturn(Source.AttributionMode.TRUTHFULLY);
    when(source.getPublisherType()).thenReturn(EventSurfaceType.APP);
    when(source.getPublisher()).thenReturn(PUBLISHER);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao).deleteEventReport(eventReport1);
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldPerformMultipleAttributions() throws ParseException {
    Trigger trigger1 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .build();
    Trigger trigger2 =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId2")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 2\n"
                    + "}"
                    + "]\n")
            .build();
    List<Trigger> triggers = new ArrayList<>();
    triggers.add(trigger1);
    triggers.add(trigger2);
    List<Source> matchingSourceList1 = new ArrayList<>();
    matchingSourceList1.add(
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build());
    List<Source> matchingSourceList2 = new ArrayList<>();
    matchingSourceList2.add(
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .build());
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Arrays.asList(trigger1, trigger2));
    when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
    when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
    when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);

    mHandler.performPendingAttributions();

    // Verify trigger status updates.
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger1.getId()).getStatus());
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger2.getId()).getStatus());
    // Verify source dedup key updates.
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao, times(2)).updateSourceEventReportDedupKeys(sourceArg.capture());
    List<Source> dedupArgs = sourceArg.getAllValues();
    for (int i = 0; i < dedupArgs.size(); i++) {
      assertEquals(
          dedupArgs.get(i).getEventReportDedupKeys(),
          Collections.singletonList(triggers.get(i).parseEventTriggers().get(0).getDedupKey()));
    }
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao, times(2)).insertEventReport(reportArg.capture());
    List<EventReport> newReportArgs = reportArg.getAllValues();
    for (int i = 0; i < newReportArgs.size(); i++) {
      assertEquals(
          newReportArgs.get(i).getTriggerDedupKey(),
          triggers.get(i).parseEventTriggers().get(0).getDedupKey());
    }
  }

  @Test
  public void shouldAttributeToInstallAttributedSource() throws ParseException {
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(5);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("trigger1")
            .setStatus(Trigger.Status.PENDING)
            .setTriggerTime(triggerTime)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 2\n"
                    + "}"
                    + "]\n")
            .build();
    // Lower priority and older source.
    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setInstallAttributed(true)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(triggerTime)
            .setAggregatableReportWindow(triggerTime)
            .build();
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setId("source2")
            .setEventId(new UnsignedLong(2L))
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source2);
    matchingSourceList.add(source1);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    verify(mMeasurementDao).updateSourceStatus(eq(List.of(source2)), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    assertEquals(source2.getEventId(), matchingSourceList.get(0).getEventId());
    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(source1.getEventId(), sourceArg.getValue().getEventId());
    assertEquals(
        Collections.singletonList(new UnsignedLong(2L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldNotAttributeToOldInstallAttributedSource() throws ParseException {
    // Setup
    long eventTime = System.currentTimeMillis();
    long triggerTime = eventTime + TimeUnit.DAYS.toMillis(10);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("trigger1")
            .setStatus(Trigger.Status.PENDING)
            .setTriggerTime(triggerTime)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 2\n"
                    + "}"
                    + "]\n")
            .build();
    // Lower Priority. Install cooldown Window passed.
    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setId("source1")
            .setEventId(new UnsignedLong(1L))
            .setPriority(100L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setInstallAttributed(true)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(3))
            .setEventTime(eventTime - TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(triggerTime)
            .setAggregatableReportWindow(triggerTime)
            .build();
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setId("source2")
            .setEventId(new UnsignedLong(2L))
            .setPriority(200L)
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(eventTime)
            .setEventReportWindow(triggerTime)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source2);
    matchingSourceList.add(source1);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    // Execution
    mHandler.performPendingAttributions();
    trigger.setStatus(Trigger.Status.ATTRIBUTED);
    // Assertion
    verify(mMeasurementDao).updateSourceStatus(eq(List.of(source1)), eq(Source.Status.IGNORED));
    assertEquals(1, matchingSourceList.size());
    assertEquals(source1.getEventId(), matchingSourceList.get(0).getEventId());

    assertSame(Status.ATTRIBUTED, mMeasurementDao.getTrigger(trigger.getId()).getStatus());
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(source2.getEventId(), sourceArg.getValue().getEventId());
    assertEquals(
        Collections.singletonList(new UnsignedLong(2L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldNotGenerateReportForAttributionModeFalsely() throws ParseException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.FALSELY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();

    assertSame(Status.IGNORED, trigger.getStatus());
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotGenerateReportForAttributionModeNever() throws ParseException {
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.NEVER)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();

    assertSame(Status.IGNORED, trigger.getStatus());
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReport() throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("key_piece", "0x400");

    JSONArray campaignCounts = new JSONArray();
    campaignCounts.add("campaignCounts");
    jsonObject1.put("source_keys", campaignCounts);
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");

    JSONArray sourceKeys = new JSONArray();
    sourceKeys.add("geoValue");
    sourceKeys.add("noMatch");
    jsonObject2.put("source_keys", sourceKeys);
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setId("sourceId1")
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
            .build();
    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    assertEquals(32768 + 1644, sourceArg.getValue().getAggregateContributions());
  }

  @Test
  public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReportWithDedupKey()
      throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("key_piece", "0x400");

    JSONArray campaignCounts = new JSONArray();
    campaignCounts.add("campaignCounts");
    jsonObject1.put("source_keys", campaignCounts);

    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");

    JSONArray sourceKeys = new JSONArray();
    sourceKeys.add("geoValue");
    sourceKeys.add("noMatch");
    jsonObject2.put("source_keys", sourceKeys);

    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setAggregateDeduplicationKeys(AGGREGATE_DEDUPLICATION_KEYS_1)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setId("sourceId1")
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
            .build();
    AggregateReport expectedAggregateReport =
        new AggregateReport.Builder()
            .setApiVersion("0.1")
            .setAttributionDestination(trigger.getAttributionDestination())
            .setDebugCleartextPayload(
                "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":\"1369\",\"value\":32768},"
                    + "{\"bucket\":\"2693\",\"value\":1644}]}")
            .setEnrollmentId(source.getEnrollmentId())
            .setPublisher(source.getRegistrant())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .setAggregateAttributionData(
                new AggregateAttributionData.Builder()
                    .setContributions(
                        Arrays.asList(
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("1369"))
                                .setValue(32768)
                                .build(),
                            new AggregateHistogramContribution.Builder()
                                .setKey(new BigInteger("2693"))
                                .setValue(1644)
                                .build()))
                    .build())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));

    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateContributions(sourceArg.capture());
    ArgumentCaptor<AggregateReport> aggregateReportCaptor =
        ArgumentCaptor.forClass(AggregateReport.class);
    verify(mMeasurementDao).insertAggregateReport(aggregateReportCaptor.capture());
    assertAggregateReportsEqual(expectedAggregateReport, aggregateReportCaptor.getValue());
    assertEquals(32768 + 1644, sourceArg.getValue().getAggregateContributions());
    ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceAggregateReportDedupKeys(sourceCaptor.capture());
    assertEquals(1, sourceCaptor.getValue().getAggregateReportDedupKeys().size());
    assertEquals(
        new UnsignedLong(10L), sourceCaptor.getValue().getAggregateReportDedupKeys().get(0));
  }

  @Test
  public void shouldNotGenerateAggregateReportWhenExceedingAggregateContributionsLimit()
      throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();

    JSONArray campaignCounts = new JSONArray();
    campaignCounts.add("campaignCounts");

    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", campaignCounts);

    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");

    JSONArray sourceKeys = new JSONArray();
    sourceKeys.add("geoValue");
    sourceKeys.add("noMatch");
    jsonObject2.put("source_keys", sourceKeys);

    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);

    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .setEventTriggers(EVENT_TRIGGERS)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData("{\"product\":[\"1234\",\"2345\"]}")
            .setAggregateContributions(65536 - 32768 - 1644 + 1)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    mHandler.performPendingAttributions();
    verify(mMeasurementDao, never()).updateSourceAggregateContributions(any());
    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }

  @Test
  public void performAttributions_triggerFilterSet_commonKeysDontIntersect_ignoreTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"]}, {\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1_x\", \"value_2_x\"],\n"
                    + "  \"key_2\": [\"value_1_x\", \"value_2_x\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    assertSame(Status.IGNORED, trigger.getStatus());
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void performAttributions_triggerNotFilters_commonKeysIntersect_ignoreTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setNotFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2_x\"],\n"
                    + "  \"key_2\": [\"value_1_x\", \"value_2\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    assertSame(Status.IGNORED, trigger.getStatus());
    verify(mMeasurementDao, never()).updateSourceEventReportDedupKeys(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void performAttributions_triggerNotFilterSet_commonKeysDontIntersect_attributeTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setNotFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                    + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();

    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_triggerNotFiltersWithCommonKeysDontIntersect_attributeTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setNotFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22_x\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_topLevelFilterSetMatch_attributeTrigger() throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11_x\", \"value_12\"]}, {\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_triggerSourceFiltersWithCommonKeysIntersect_attributeTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .setDebugKey(SOURCE_DEBUG_KEY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    assertEquals(SOURCE_DEBUG_KEY, sourceArg.getValue().getDebugKey());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_sourceKey()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .setDebugKey(SOURCE_DEBUG_KEY)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    assertEquals(SOURCE_DEBUG_KEY, sourceArg.getValue().getDebugKey());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_commonKeysIntersect_attributeTrigger_debugApi_triggerKey()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .setDebugKey(TRIGGER_DEBUG_KEY)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12_x\"],\n"
                    + "  \"key_2\": [\"value_21_x\", \"value_22\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_triggerSourceFiltersWithNoCommonKeys_attributeTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_11\", \"value_12\"],\n"
                    + "  \"key_2\": [\"value_21\", \"value_22\"]\n"
                    + "}]\n")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1x\": [\"value_11_x\", \"value_12_x\"],\n"
                    + "  \"key_2x\": [\"value_21_x\", \"value_22_x\"]\n"
                    + "}\n")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao).updateSourceEventReportDedupKeys(sourceArg.capture());
    assertEquals(
        Collections.singletonList(new UnsignedLong(1L)),
        sourceArg.getValue().getEventReportDedupKeys());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttributions_eventLevelFilters_filterSet_attributeFirstMatchingTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"key_1\": [\"unmatched\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"filters\": [{\n"
                    + "    \"ignored\": [\"ignored\"]}, {\n"
                    + "    \"key_1\": [\"unmatched\"]}, {\n"
                    + "    \"key_1\": [\"matched\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"matched\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(source.getRandomAttributionProbability())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).updateSourceEventReportDedupKeys(source);
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_eventLevelFilters_attributeFirstMatchingTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"key_1\": [\"value_1_x\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"filters\": [{\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(source.getRandomAttributionProbability())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).updateSourceEventReportDedupKeys(source);
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_filterSet_eventLevelNotFilters_attributeFirstMatchingTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"key_1\": [\"value_1\"]}, {\n"
                    + "    \"key_2\": [\"value_2\"]}, {\n"
                    + "    \"key_1\": [\"matches_when_negated\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(source.getRandomAttributionProbability())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).updateSourceEventReportDedupKeys(source);
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_eventLevelNotFilters_attributeFirstMatchingTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"not_filters\": [{\n"
                    + "    \"key_1\": [\"value_1_x\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(source.getSourceType())
            .setRandomizedTriggerRate(source.getRandomAttributionProbability())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).updateSourceEventReportDedupKeys(source);
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_eventLevelFiltersWithSourceType_attributeFirstMatchingTrigger()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"event\"], \n"
                    + "    \"dummy_key\": [\"dummy_value\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"navigation\"], \n"
                    + "    \"dummy_key\": [\"dummy_value\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setId("sourceId")
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    EventReport expectedEventReport =
        new EventReport.Builder()
            .setTriggerPriority(3L)
            .setTriggerDedupKey(new UnsignedLong(3L))
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerTime(234324L)
            .setSourceEventId(source.getEventId())
            .setStatus(EventReport.Status.PENDING)
            .setAttributionDestinations(source.getAppDestinations())
            .setEnrollmentId(source.getEnrollmentId())
            .setReportTime(
                source.getReportingTime(trigger.getTriggerTime(), trigger.getDestinationType()))
            .setSourceType(Source.SourceType.NAVIGATION)
            .setRandomizedTriggerRate(source.getRandomAttributionProbability())
            .setSourceId(source.getId())
            .setTriggerId(trigger.getId())
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).updateSourceEventReportDedupKeys(source);
    verify(mMeasurementDao).insertEventReport(eq(expectedEventReport));
  }

  @Test
  public void performAttributions_filterSet_eventLevelFiltersFailToMatch_aggregateReportOnly()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"product\": [\"value_11\"]}, {\n"
                    + "    \"key_1\": [\"value_11\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"filters\": [{\n"
                    + "    \"product\": [\"value_21\"]}, {\n"
                    + "    \"key_1\": [\"value_21\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\"product\":[\"1234\", \"2345\"]," + "\"key_1\": [\"value_1_y\", \"value_2_y\"]}")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao).insertAggregateReport(any());
  }

  @Test
  public void performAttributions_eventLevelFiltersFailToMatch_generateAggregateReportOnly()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 2,\n"
                    + "  \"priority\": 2,\n"
                    + "  \"deduplication_key\": 2,\n"
                    + "  \"filters\": [{\n"
                    + "    \"key_1\": [\"value_11\"] \n"
                    + "   }]\n"
                    + "},"
                    + "{\n"
                    + "  \"trigger_data\": 3,\n"
                    + "  \"priority\": 3,\n"
                    + "  \"deduplication_key\": 3,\n"
                    + "  \"filters\": [{\n"
                    + "    \"key_1\": [\"value_21\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\"product\":[\"1234\",\"2345\"], \"key_1\": " + "[\"value_1_y\", \"value_2_y\"]}")
            .setId("sourceId")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao, never()).insertEventReport(any());
    verify(mMeasurementDao).insertAggregateReport(any());
  }

  @Test
  public void performAttribution_aggregateReportsExceedsLimit_insertsOnlyEventReport()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1024;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    // Execution
    mHandler.performPendingAttributions();
    // Assertions
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void performAttribution_eventReportsExceedsLimit_insertsOnlyAggregateReport()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1024;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    // Execution
    mHandler.performPendingAttributions();
    // Assertion
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void performAttribution_aggregateAndEventReportsExceedsLimit_noReportInsertion()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    int numAggregateReportPerDestination = 1024;
    int numEventReportPerDestination = 1024;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    // Execution
    mHandler.performPendingAttributions();
    // Assertion
    assertSame(Status.IGNORED, trigger.getStatus());

    verify(mMeasurementDao, never()).insertAggregateReport(any());
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void performAttribution_aggregateAndEventReportsDoNotExceedLimit_ReportInsertion()
      throws ParseException {
    // Setup
    Trigger trigger =
        TriggerFixture.getValidTriggerBuilder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers(
                "[\n"
                    + "{\n"
                    + "  \"trigger_data\": 5,\n"
                    + "  \"priority\": 123,\n"
                    + "  \"deduplication_key\": 1\n"
                    + "}"
                    + "]\n")
            .setFilters(
                "[{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}]\n")
            .setTriggerTime(234324L)
            .setAggregateTriggerData(buildAggregateTriggerData().toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        SourceFixture.getValidSourceBuilder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
            .setFilterData(
                "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n")
            .setEventReportWindow(234324L)
            .setAggregatableReportWindow(234324L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
    int numAggregateReportPerDestination = 1023;
    int numEventReportPerDestination = 1023;
    when(mMeasurementDao.getNumAggregateReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numAggregateReportPerDestination);
    when(mMeasurementDao.getNumEventReportsPerDestination(
            trigger.getAttributionDestination(), trigger.getDestinationType()))
        .thenReturn(numEventReportPerDestination);
    // Execution
    mHandler.performPendingAttributions();
    // Assertion
    assertSame(Status.ATTRIBUTED, trigger.getStatus());

    verify(mMeasurementDao).insertAggregateReport(any());
    verify(mMeasurementDao).insertEventReport(any());
  }

  private String buildMatchingFilterData() {
    JSONArray subDomain = new JSONArray();
    subDomain.add("electronics.megastore");
    JSONObject filterMap = new JSONObject();
    filterMap.put("conversion_subdomain", subDomain);
    return filterMap.toString();
  }

  private JSONArray buildAggregateTriggerData() throws ParseException {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    jsonObject1.put("key_piece", "0x400");

    JSONArray campaignCounts = new JSONArray();
    campaignCounts.add("campaignCounts");
    jsonObject1.put("source_keys", campaignCounts);
    jsonObject1.put("filters", createFilterJSONArray());
    jsonObject1.put("not_filters", createFilterJSONArray());
    JSONObject jsonObject2 = new JSONObject();
    jsonObject2.put("key_piece", "0xA80");

    JSONArray newkeys = new JSONArray();
    newkeys.add("geoValue");
    newkeys.add("noMatch");
    jsonObject2.put("source_keys", newkeys);
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);
    return triggerDatas;
  }

  private JSONArray createFilterJSONArray() {
    JSONObject filterMap = new JSONObject();
    JSONArray subDomain = new JSONArray();
    subDomain.add("electronics.megastore");
    filterMap.put("conversion_subdomain", subDomain);

    JSONArray product = new JSONArray();
    product.add("1234");
    product.add("2345");

    filterMap.put("product", product);
    JSONArray filterSet = new JSONArray();
    filterSet.add(filterMap);
    return filterSet;
  }

  private void assertAggregateReportsEqual(
      AggregateReport expectedReport, AggregateReport actualReport) {
    // Avoids checking report time because there is randomization
    assertEquals(expectedReport.getApiVersion(), actualReport.getApiVersion());
    assertEquals(
        expectedReport.getAttributionDestination(), actualReport.getAttributionDestination());
    assertEquals(expectedReport.getEnrollmentId(), actualReport.getEnrollmentId());
    assertEquals(expectedReport.getPublisher(), actualReport.getPublisher());
    assertEquals(expectedReport.getSourceId(), actualReport.getSourceId());
    assertEquals(expectedReport.getTriggerId(), actualReport.getTriggerId());
    assertEquals(
        expectedReport.getAggregateAttributionData(), actualReport.getAggregateAttributionData());
    assertEquals(expectedReport.getSourceDebugKey(), actualReport.getSourceDebugKey());
    assertEquals(expectedReport.getTriggerDebugKey(), actualReport.getTriggerDebugKey());
  }
}
