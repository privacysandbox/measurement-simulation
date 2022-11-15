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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.rubidium.Source.AttributionMode;
import com.google.rubidium.Source.SourceType;
import com.google.rubidium.Trigger.Status;
import com.google.rubidium.aggregation.AggregateAttributionData;
import com.google.rubidium.aggregation.AggregateHistogramContribution;
import com.google.rubidium.aggregation.AggregateReport;
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AttributionJobHandlerTest {
  @Mock IMeasurementDAO mMeasurementDao;

  @Mock Source mockedSource;

  @Test
  public void shouldIgnoreNonPendingTrigger() {
    Trigger trigger =
        new Trigger.Builder().setId("triggerId1").setStatus(Trigger.Status.IGNORED).build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertSame(mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Status.IGNORED);
  }

  @Test
  public void shouldIgnoreIfNoSourcesFound() {
    Trigger trigger =
        new Trigger.Builder().setId("triggerId1").setStatus(Trigger.Status.PENDING).build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertSame(mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Status.IGNORED);
  }

  @Test
  public void shouldRejectBasedOnDedupKey() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 2}]")
            .build();
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setDedupKeys(Arrays.asList(1L, 2L))
            .build();
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertEquals(
        mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Trigger.Status.IGNORED);
  }

  @Test
  public void shouldNotAddIfRateLimitExceeded() {
    Trigger trigger =
        new Trigger.Builder().setId("triggerId1").setStatus(Trigger.Status.PENDING).build();
    Source source =
        new Source.Builder().setAppDestination(URI.create("https://www.example2.com/d1")).build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(105L);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    verify(mMeasurementDao).getAttributionsPerRateLimitWindow(source, trigger);
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertEquals(
        mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Trigger.Status.IGNORED);
  }

  @Test
  public void shouldIgnoreForMaxReportsPerSource() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
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
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertEquals(
        mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Trigger.Status.IGNORED);
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldNotReplaceHighPriorityReports() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setStatus(Trigger.Status.PENDING)
            .build();
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(200)
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
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertNotNull(mMeasurementDao.getTrigger(trigger.getId()));
    Assert.assertEquals(
        mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Trigger.Status.IGNORED);
    verify(mMeasurementDao, never()).insertEventReport(any());
  }

  @Test
  public void shouldDoSimpleAttribution() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 1, \"trigger_data\": 100}]")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .build();
    Source source =
        new Source.Builder()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setWebDestination(URI.create("https://www.example2.com/d1"))
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());

    ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);

    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();

    verify(mMeasurementDao).updateSourceDedupKeys(sourceArg.capture());
    verify(mMeasurementDao).insertEventReport(any());
    Assert.assertEquals(sourceArg.getValue().getDedupKeys(), Collections.singletonList(1L));
  }

  @Test
  public void shouldIgnoreLowPrioritySourceWhileAttribution() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 2, \"trigger_data\": 100}]")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .build();
    Source source1 =
        new Source.Builder()
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setWebDestination(URI.create("https://www.example2.com/d1"))
            .setAttributionMode(AttributionMode.TRUTHFULLY)
            .setPriority(100L)
            .setEventTime(1L)
            .build();
    Source source2 =
        new Source.Builder()
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setWebDestination(URI.create("https://www.example2.com/d1"))
            .setAttributionMode(AttributionMode.TRUTHFULLY)
            .setPriority(200L)
            .setEventTime(2L)
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source1);
    matchingSourceList.add(source2);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);

    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();

    verify(mMeasurementDao).updateSourceStatus(matchingSourceList, Source.Status.IGNORED);
    Assert.assertEquals(1, matchingSourceList.size());
    verify(mMeasurementDao).updateSourceDedupKeys(sourceArg.capture());
    Assert.assertEquals(sourceArg.getValue().getDedupKeys(), Collections.singletonList(2L));
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldReplaceLowPriorityReportWhileAttribution() {
    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 1, \"trigger_data\": 100}]")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(mockedSource);
    EventReport eventReport1 =
        new EventReport.Builder()
            .setStatus(EventReport.Status.PENDING)
            .setTriggerPriority(100)
            .setReportTime(5L)
            .build();
    EventReport eventReport2 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).setReportTime(5L).build();
    EventReport eventReport3 =
        new EventReport.Builder().setStatus(EventReport.Status.DELIVERED).setReportTime(5L).build();

    List<EventReport> matchingReports = new ArrayList<>();
    matchingReports.add(eventReport1);
    matchingReports.add(eventReport2);
    matchingReports.add(eventReport3);

    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getSourceEventReports(mockedSource)).thenReturn(matchingReports);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    when(mockedSource.getReportingTime(anyLong(), any(EventSurfaceType.class))).thenReturn(5L);
    when(mockedSource.getAttributionMode()).thenReturn(Source.AttributionMode.TRUTHFULLY);
    when(mockedSource.getTriggerDataCardinality())
        .thenReturn(PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);
    when(mockedSource.getPublisher()).thenReturn((URI.create("https://www.example1.com/s1")));
    when(mockedSource.getSourceType()).thenReturn(SourceType.EVENT);
    when(mockedSource.getMaxReportCount(any(EventSurfaceType.class))).thenReturn(5);

    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    Assert.assertSame(mMeasurementDao.getTrigger(trigger.getId()).getStatus(), Status.ATTRIBUTED);
    verify(mMeasurementDao).insertEventReport(any());
  }

  @Test
  public void shouldPerformMultipleAttributions() {
    Trigger trigger1 =
        new Trigger.Builder()
            .setId("triggerId1")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 1, \"trigger_data\": 100}]")
            .build();
    Trigger trigger2 =
        new Trigger.Builder()
            .setId("triggerId2")
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .setStatus(Trigger.Status.PENDING)
            .setEventTriggers("[{\"deduplication_key\": 2, \"trigger_data\": 100}]")
            .build();
    List<Trigger> triggers = new ArrayList<>();
    triggers.add(trigger1);
    triggers.add(trigger2);
    List<Source> matchingSourceList1 = new ArrayList<>();
    matchingSourceList1.add(
        new Source.Builder()
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setWebDestination(URI.create("https://www.example2.com/d1"))
            .setAttributionMode(AttributionMode.TRUTHFULLY)
            .build());
    List<Source> matchingSourceList2 = new ArrayList<>();
    matchingSourceList2.add(
        new Source.Builder()
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setWebDestination(URI.create("https://www.example2.com/d1"))
            .setAttributionMode(AttributionMode.TRUTHFULLY)
            .build());
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Arrays.asList(trigger1, trigger2));
    when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
    when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);

    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();

    // Verify trigger status updates.
    ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
    List<Trigger> statusArgs = triggerArg.getAllValues();
    for (int i = 0; i < statusArgs.size(); i++) {
      Assert.assertEquals(Trigger.Status.ATTRIBUTED, statusArgs.get(i).getStatus());
      Assert.assertEquals(triggers.get(i).getId(), statusArgs.get(i).getId());
    }
    // Verify source dedup key updates.
    ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
    verify(mMeasurementDao, times(2)).updateSourceDedupKeys(sourceArg.capture());
    List<Source> dedupArgs = sourceArg.getAllValues();
    for (int i = 0; i < dedupArgs.size(); i++) {
      String triggerData =
          String.format(
              "[{\"deduplication_key\": %1d, \"trigger_data\": 100}]",
              dedupArgs.get(i).getDedupKeys().get(0));
      Assert.assertEquals(triggers.get(i).getEventTriggers(), triggerData);
    }
    // Verify new event report insertion.
    ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
    verify(mMeasurementDao, times(2)).insertEventReport(reportArg.capture());
    List<EventReport> newReportArgs = reportArg.getAllValues();
    for (int i = 0; i < newReportArgs.size(); i++) {
      String triggerData =
          String.format(
              "[{\"deduplication_key\": %1d, \"trigger_data\": 100}]",
              newReportArgs.get(i).getTriggerDedupKey());
      Assert.assertEquals(triggerData, triggers.get(i).getEventTriggers());
    }
  }

  @Test
  public void shouldDoSimpleAttributionGenerateUnencryptedAggregateReport() {
    JSONArray triggerDatas = new JSONArray();
    JSONObject jsonObject1 = new JSONObject();
    JSONArray sourceKeys = new JSONArray();
    sourceKeys.add("campaignCounts");
    jsonObject1.put("key_piece", "0x400");
    jsonObject1.put("source_keys", sourceKeys);

    JSONObject jsonObject2 = new JSONObject();
    sourceKeys = new JSONArray();
    sourceKeys.addAll(Arrays.asList("geoValue", "noMatch"));
    jsonObject2.put("key_piece", "0xA80");
    jsonObject2.put("source_keys", sourceKeys);
    triggerDatas.add(jsonObject1);
    triggerDatas.add(jsonObject2);

    Trigger trigger =
        new Trigger.Builder()
            .setId("triggerId1")
            .setStatus(Trigger.Status.PENDING)
            .setRegistrant(URI.create("https://www.example1.com/s1"))
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setEventTriggers("[{\"deduplication_keys\": 1, \"trigger_data\": 100}]")
            .setAggregateTriggerData(triggerDatas.toString())
            .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
            .build();
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setAggregateSource(
                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                    + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]")
            .setAggregateFilterData("{\"product\":[\"1234\",\"2345\"]}")
            .build();
    when(mMeasurementDao.getPendingTriggers()).thenReturn(Collections.singletonList(trigger));
    List<Source> matchingSourceList = new ArrayList<>();
    matchingSourceList.add(source);
    when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
    when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
    AttributionJobHandler attributionService = new AttributionJobHandler(mMeasurementDao);
    attributionService.performPendingAttributions();
    verify(mMeasurementDao).insertAggregateReport(any());
  }

  @Test
  public void shouldCreateAggregateReport() throws ParseException {
    final URI PUBLISHER = URI.create("http://example.com");
    final URI ATTRIBUTION_DEST = URI.create("http://example.com/r1");
    final long SRC_EVENT_TIME = 1010;
    final long SRC_EVENT_DAY =
        Math.floorDiv(SRC_EVENT_TIME, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
    AggregateAttributionData COMP_AGGATTR_DATA =
        new AggregateAttributionData.Builder()
            .setContributions(
                List.of(
                    new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(0xFFFFFFFFFFFFFFL))
                        .setValue(123)
                        .build()))
            .build();

    Source source =
        new Source.Builder()
            .setPublisher(PUBLISHER)
            .setRegistrant(PUBLISHER)
            .setAppDestination(ATTRIBUTION_DEST)
            .setEventTime(SRC_EVENT_TIME)
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setAggregateFilterData("{}")
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(ATTRIBUTION_DEST)
            .setRegistrant(PUBLISHER)
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"]}]")
            .setAggregateValues("{\"campaignCounts\": 32768,\"geoValue\": 1664, \"myId1\": 123}")
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be sorted

    final ArgumentCaptor<AggregateReport> captor = ArgumentCaptor.forClass(AggregateReport.class);

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao).insertAggregateReport(captor.capture());
    final AggregateReport aggregateReport = captor.getValue();
    Assert.assertNotNull(aggregateReport);

    Assert.assertEquals(aggregateReport.getPublisher(), PUBLISHER);
    Assert.assertEquals(aggregateReport.getAttributionDestination(), ATTRIBUTION_DEST);
    Assert.assertEquals(aggregateReport.getSourceRegistrationTime(), SRC_EVENT_DAY);
    Assert.assertTrue(aggregateReport.getScheduledReportTime() > trigger.getTriggerTime());
    Assert.assertEquals(aggregateReport.getEnrollmentId(), source.getEnrollmentId());
    Assert.assertEquals(
        aggregateReport.getDebugCleartextPayload(),
        aggregateReport.generateDebugPayload(COMP_AGGATTR_DATA.getContributions()));
    Assert.assertEquals(aggregateReport.getAggregateAttributionData(), COMP_AGGATTR_DATA);
    Assert.assertEquals(aggregateReport.getStatus(), AggregateReport.Status.PENDING);
  }

  @Test
  public void shouldNotCreateAggregateReport() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setAggregateFilterData("{}")
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"]}]")
            .setAggregateValues(
                "{\"campaignCounts\": 32768,\"geoValue\": 1664}") // no myId1 = no aggregation
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(
            new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be processed

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }

  @Test
  public void triggerFilterAllowCreateAggregateReport() {
    Source source =
        new Source.Builder()
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setAggregateFilterData("{\"f1\": [\"c\", \"d\", \"e\"]}")
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("http://example1.com/s1"))
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"],"
                    + " \"filters\": {\"f1\": [\"a\", \"b\", \"c\"]}}]")
            .setAggregateValues("{\"campaignCounts\": 32768,\"geoValue\": 1664, \"myId1\": 123}")
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(
            new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be processed

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao).insertAggregateReport(any());
  }

  @Test
  public void triggerFilterPreventCreateAggregateReport() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setAggregateFilterData(
                "{\"f1\": [\"d\", \"e\", \"f\"]}") // No matching values for matching key = no
            // aggregation
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setRegistrant(URI.create("http://example1.com/s1"))
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"],"
                    + " \"filters\": {\"f1\": [\"a\", \"b\", \"c\"]}}]")
            .setAggregateValues("{\"campaignCounts\": 32768,\"geoValue\": 1664, \"myId1\": 123}")
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(
            new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be processed

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }

  @Test
  public void triggerNotFilterAllowCreateAggregateReport() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setAggregateFilterData("{\"f1\": [\"d\", \"e\", \"f\"]}")
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setRegistrant(URI.create("http://example1.com/s1"))
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"],"
                    + " \"not_filters\": {\"f1\": [\"a\", \"b\", \"c\"]}}]")
            .setAggregateValues("{\"campaignCounts\": 32768,\"geoValue\": 1664, \"myId1\": 123}")
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(
            new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be processed

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao).insertAggregateReport(any());
  }

  @Test
  public void triggerNotFilterPreventCreateAggregateReport() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://www.example2.com/d1"))
            .setAggregateSource("[{\"id\": \"myId1\", \"key_piece\": \"0xFFFFFFFFFFFFFF\"}]")
            .setPublisher(URI.create("https://www.example1.com/s1"))
            .setAggregateFilterData(
                "{\"f1\": [\"c\", \"d\", \"e\"]}") // Matching values for matching key = no
            // aggregation
            .build();
    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://www.example2.com/d1"))
            .setAggregateTriggerData(
                "[{\"key_piece\": \"0x400\", \"source_keys\": [\"campaignCounts\", \"myId1\"],"
                    + " \"not_filters\": {\"f1\": [\"a\", \"b\", \"c\"]}}]")
            .setAggregateValues("{\"campaignCounts\": 32768,\"geoValue\": 1664, \"myId1\": 123}")
            .build();

    when(mMeasurementDao.getPendingTriggers()).thenReturn(List.of(trigger));
    when(mMeasurementDao.getMatchingActiveSources(trigger))
        .thenReturn(
            new ArrayList<>(List.of(source))); // List.of() is immutable, cannot be processed

    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(mMeasurementDao);
    attributionJobHandler.performPendingAttributions();

    verify(mMeasurementDao, never()).insertAggregateReport(any());
  }
}
