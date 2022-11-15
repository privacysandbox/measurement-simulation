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

import com.google.rubidium.Trigger.Status;
import com.google.rubidium.util.BaseUriExtractor;
import com.google.rubidium.util.Web;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class MeasurementDAOTest {

  private static final String PAYLOAD =
      "{\"operation\":\"histogram\","
          + "\"data\":[{\"bucket\":1369,\"value\":32768},"
          + "{\"bucket\":3461,\"value\":1664}]}";
  private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
  private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);
  private static final URI APP_TWO_SOURCES = URI.create("android-app://com.example1.two-sources");
  private static final URI APP_ONE_SOURCE = URI.create("android-app://com.example2.one-source");
  private static final URI APP_NO_SOURCE = URI.create("android-app://com.example3.no-sources");
  private static final URI APP_TWO_TRIGGERS = URI.create("android-app://com.example1.two-triggers");
  private static final URI APP_ONE_TRIGGER = URI.create("android-app://com.example1.one-trigger");
  private static final URI APP_NO_TRIGGERS = URI.create("android-app://com.example1.no-triggers");
  private static final URI INSTALLED_PACKAGE = URI.create("android-app://com.example.installed");

  private static final List<Source> SOURCES_LIST =
      List.of(
          new Source.Builder()
              .setId("S1")
              .setAppDestination(URI.create("https://example.com/aD"))
              .setRegistrant(APP_TWO_SOURCES)
              .build(),
          new Source.Builder()
              .setId("S2")
              .setAppDestination(URI.create("https://example.com/aD"))
              .setRegistrant(APP_TWO_SOURCES)
              .build(),
          new Source.Builder()
              .setId("S3")
              .setAppDestination(URI.create("https://example.com/aD"))
              .setRegistrant(APP_ONE_SOURCE)
              .build());

  private static final List<Trigger> TRIGGERS_LIST =
      List.of(
          new Trigger.Builder().setId("T1").setRegistrant(APP_TWO_TRIGGERS).build(),
          new Trigger.Builder().setId("T2").setRegistrant(APP_TWO_TRIGGERS).build(),
          new Trigger.Builder().setId("T3").setRegistrant(APP_ONE_TRIGGER).build());

  @Test
  public void GetPendingTriggersTest() {
    IMeasurementDAO measurementDAO =
        new MeasurementDAO(
            Arrays.asList(),
            Arrays.asList(
                new Trigger.Builder().setId("A").setStatus(Status.PENDING).build(),
                new Trigger.Builder().setId("B").setStatus(Status.ATTRIBUTED).build(),
                new Trigger.Builder().setId("C").setStatus(Status.IGNORED).build()));

    List<Trigger> result = measurementDAO.getPendingTriggers();
    Assert.assertTrue(result.size() == 1 && result.get(0).getId().equals("A"));
  }

  @Test
  public void GetMatchingActiveSources_OneFound() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("attrDest"))
            .setEnrollmentId("reportTo")
            .setExpiryTime(1000)
            .setStatus(Source.Status.ACTIVE)
            .build();

    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("attrDest"))
            .setEnrollmentId("reportTo")
            .setTriggerTime(900) // Less than Source's expiry time
            .build();

    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(source), List.of(trigger));
    Assert.assertTrue(measurementDAO.getMatchingActiveSources(trigger).size() == 1);
  }

  @Test
  public void GetMatchingActiveSources_NoneFound() {
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("attrDest"))
            .setEnrollmentId("reportToDifferent")
            .setExpiryTime(1000)
            .setStatus(Source.Status.ACTIVE)
            .build();

    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("attrDest"))
            .setEnrollmentId("reportTo")
            .setTriggerTime(900) // Less than Source's expiry time
            .build();

    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(source), List.of(trigger));
    Assert.assertTrue(measurementDAO.getMatchingActiveSources(trigger).size() == 0);
  }

  @Test
  public void GetSourceEventReportsTest() {
    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(), List.of());
    measurementDAO.insertEventReport(new EventReport.Builder().setSourceId(100).build());
    measurementDAO.insertEventReport(new EventReport.Builder().setSourceId(100).build());
    measurementDAO.insertEventReport(new EventReport.Builder().setSourceId(200).build());

    Source searchSource =
        new Source.Builder()
            .setEventId(100)
            .setAppDestination(URI.create("https://example.com/aD"))
            .build();
    Assert.assertTrue(measurementDAO.getSourceEventReports(searchSource).size() == 2);
  }

  @Test
  public void GetAttributionsPerRateLimitWindow_FullFilterMatch() {
    // Should return an item
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("https://source.com"))
            .build();

    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://dest.com"))
            .setEnrollmentId("reportTo")
            .setTriggerTime(1100)
            .setRegistrant(URI.create("android-app://com.example.sample"))
            .build();

    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(), List.of());
    measurementDAO.insertAttribution(createAttribution(source, trigger));

    Assert.assertTrue(measurementDAO.getAttributionsPerRateLimitWindow(source, trigger) == 1);
  }

  @Test
  public void GetAttributionsPerRateLimitWindow_PartialFilterMatch() {
    // Should not return any items
    Source source =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("https://source.com"))
            .build();

    Source searchSource =
        new Source.Builder()
            .setAppDestination(URI.create("https://example.com/aD"))
            .setPublisher(URI.create("https://differentSource.com"))
            .build();

    Trigger trigger =
        new Trigger.Builder()
            .setAttributionDestination(URI.create("https://dest.com"))
            .setEnrollmentId("reportTo")
            .setRegistrant(URI.create("android-app://com.example.sample"))
            .setTriggerTime(1100)
            .build();

    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(), List.of());
    measurementDAO.insertAttribution(createAttribution(source, trigger));

    Assert.assertTrue(measurementDAO.getAttributionsPerRateLimitWindow(searchSource, trigger) == 0);
  }

  @Test
  public void testGetNumSourcesPerRegistrant() {
    IMeasurementDAO measurementDAO = new MeasurementDAO(SOURCES_LIST, TRIGGERS_LIST);
    Assert.assertEquals(2, measurementDAO.getNumSourcesPerRegistrant(APP_TWO_SOURCES));
    Assert.assertEquals(1, measurementDAO.getNumSourcesPerRegistrant(APP_ONE_SOURCE));
    Assert.assertEquals(0, measurementDAO.getNumSourcesPerRegistrant(APP_NO_SOURCE));
  }

  @Test
  public void testGetNumTriggersPerRegistrant() {
    IMeasurementDAO measurementDAO = new MeasurementDAO(SOURCES_LIST, TRIGGERS_LIST);
    Assert.assertEquals(2, measurementDAO.getNumTriggersPerRegistrant(APP_TWO_TRIGGERS));
    Assert.assertEquals(1, measurementDAO.getNumTriggersPerRegistrant(APP_ONE_TRIGGER));
    Assert.assertEquals(0, measurementDAO.getNumTriggersPerRegistrant(APP_NO_TRIGGERS));
  }

  @Test
  public void testInstallAttribution_selectHighestPriority() {
    long currentTimestamp = System.currentTimeMillis();
    List<Source> iaSourceList =
        List.of(
            createSourceForIATest("IA1", currentTimestamp, 100, -1, false),
            createSourceForIATest("IA2", currentTimestamp, 50, -1, false));

    IMeasurementDAO measurementDAO = new MeasurementDAO(iaSourceList, List.of());

    measurementDAO.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
    Assert.assertTrue(iaSourceList.get(0).isInstallAttributed()); // Higher priority
    Assert.assertFalse(iaSourceList.get(1).isInstallAttributed());
  }

  @Test
  public void testInstallAttribution_selectLatest() {
    long currentTimestamp = System.currentTimeMillis();
    List<Source> iaSourceList =
        List.of(
            createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
            createSourceForIATest("IA2", currentTimestamp, -1, 5, false));

    IMeasurementDAO measurementDAO = new MeasurementDAO(iaSourceList, List.of());

    measurementDAO.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
    Assert.assertFalse(iaSourceList.get(0).isInstallAttributed());
    Assert.assertTrue(iaSourceList.get(1).isInstallAttributed()); // Higher priority via timestamp
  }

  @Test
  public void testInstallAttribution_ignoreNewerSources() {
    long currentTimestamp = System.currentTimeMillis();
    List<Source> iaSourceList =
        List.of(
            createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
            createSourceForIATest("IA2", currentTimestamp, -1, 5, false));

    IMeasurementDAO measurementDAO = new MeasurementDAO(iaSourceList, List.of());

    // Should select id=IA1 (first item) as it is the only valid choice.
    // id=IA2 is newer than the evenTimestamp of install event.
    measurementDAO.doInstallAttribution(
        INSTALLED_PACKAGE, currentTimestamp - TimeUnit.DAYS.toMillis(7));
    Assert.assertTrue(iaSourceList.get(0).isInstallAttributed());
    Assert.assertFalse(iaSourceList.get(1).isInstallAttributed());
  }

  @Test
  public void testInstallAttribution_noValidSource() {
    long currentTimestamp = System.currentTimeMillis();
    List<Source> iaSourceList =
        List.of(
            createSourceForIATest("IA1", currentTimestamp, 10, 10, true),
            createSourceForIATest("IA2", currentTimestamp, 10, 11, true));

    IMeasurementDAO measurementDAO = new MeasurementDAO(iaSourceList, List.of());

    // Should not update any sources.
    measurementDAO.doInstallAttribution(INSTALLED_PACKAGE, currentTimestamp);
    Assert.assertFalse(iaSourceList.get(0).isInstallAttributed());
    Assert.assertFalse(iaSourceList.get(1).isInstallAttributed());
  }

  @Test
  public void testUndoInstallAttribution_noMarkedSource() {
    long currentTimestamp = System.currentTimeMillis();
    Source source = createSourceForIATest("IA1", currentTimestamp, 10, 10, false);
    source.setInstallAttributed(true);

    IMeasurementDAO measurementDAO = new MeasurementDAO(List.of(source), List.of());
    measurementDAO.undoInstallAttribution(INSTALLED_PACKAGE);

    Assert.assertFalse(source.isInstallAttributed());
  }

  @Test
  public void testGetSourceEventReports() {
    List<Source> sourceList =
        List.of(
            new Source.Builder()
                .setId("1")
                .setAppDestination(URI.create("https://example.com/aD"))
                .setEventId(3)
                .build(),
            new Source.Builder()
                .setId("2")
                .setAppDestination(URI.create("https://example.com/aD"))
                .setEventId(4)
                .build());

    // Should match with source 1
    List<EventReport> reportList1 =
        List.of(
            new EventReport.Builder().setId("1").setSourceId(3).build(),
            new EventReport.Builder().setId("7").setSourceId(3).build());

    // Should match with source 2
    List<EventReport> reportList2 =
        List.of(
            new EventReport.Builder().setId("3").setSourceId(4).build(),
            new EventReport.Builder().setId("8").setSourceId(4).build());

    // Should not match with any source
    List<EventReport> reportList3 =
        List.of(
            new EventReport.Builder().setId("2").setSourceId(5).build(),
            new EventReport.Builder().setId("4").setSourceId(6).build(),
            new EventReport.Builder().setId("5").setSourceId(1).build(),
            new EventReport.Builder().setId("6").setSourceId(2).build());

    IMeasurementDAO measurementDAO = new MeasurementDAO(sourceList, List.of());
    Stream.of(reportList1, reportList2, reportList3)
        .flatMap(Collection::stream) // for each eventReport, insert into measurementDAO
        .forEach(eventReport -> measurementDAO.insertEventReport(eventReport));

    List<EventReport> report1Result = measurementDAO.getSourceEventReports(sourceList.get(0));
    report1Result.forEach(eventReport -> Assert.assertTrue(reportList1.contains(eventReport)));
    Assert.assertTrue(reportList1.size() == report1Result.size());

    List<EventReport> report2Result = measurementDAO.getSourceEventReports(sourceList.get(1));
    report2Result.forEach(eventReport -> Assert.assertTrue(reportList2.contains(eventReport)));
    Assert.assertTrue(reportList2.size() == report2Result.size());
  }

  @Test
  public void testGetMatchingActiveSources() {

    String enrollmentId = "https://www.example.xyz";
    URI attributionDestination = URI.create("android-app://com.example.abc");
    Source s1 =
        new Source.Builder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setStatus(Source.Status.ACTIVE)
            .setEnrollmentId(enrollmentId)
            .setAppDestination(attributionDestination)
            .build();
    Source s2 =
        new Source.Builder()
            .setId("2")
            .setEventTime(10)
            .setExpiryTime(50)
            .setStatus(Source.Status.ACTIVE)
            .setEnrollmentId(enrollmentId)
            .setAppDestination(attributionDestination)
            .build();
    Source s3 =
        new Source.Builder()
            .setId("3")
            .setEventTime(20)
            .setExpiryTime(50)
            .setStatus(Source.Status.ACTIVE)
            .setEnrollmentId(enrollmentId)
            .setAppDestination(attributionDestination)
            .build();
    Source s4 =
        new Source.Builder()
            .setId("4")
            .setEventTime(30)
            .setExpiryTime(50)
            .setStatus(Source.Status.ACTIVE)
            .setEnrollmentId(enrollmentId)
            .setAppDestination(attributionDestination)
            .build();
    List<Source> sources = Arrays.asList(s1, s2, s3, s4);

    IMeasurementDAO measurementDAO = new MeasurementDAO(sources, List.of());

    Function<Trigger, List<Source>> runFunc =
        trigger -> {
          List<Source> result = measurementDAO.getMatchingActiveSources(trigger);
          result.sort(Comparator.comparing(Source::getId));
          return result;
        };
    // Trigger Time > s1's eventTime and < s1's expiryTime
    // Trigger Time > s2's eventTime and < s2's expiryTime
    // Trigger Time < s3's eventTime
    // Trigger Time < s4's eventTime
    // Expected: Match with s1 and s2
    Trigger trigger1MatchSource1And2 =
        new Trigger.Builder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(attributionDestination)
            .build();
    List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
    Assert.assertEquals(2, result1.size());
    Assert.assertEquals(s1.getId(), result1.get(0).getId());
    Assert.assertEquals(s2.getId(), result1.get(1).getId());
    // Trigger Time > s1's eventTime and = s1's expiryTime
    // Trigger Time > s2's eventTime and < s2's expiryTime
    // Trigger Time = s3's eventTime
    // Trigger Time < s4's eventTime
    // Expected: Match with s1 and s2
    Trigger trigger2MatchSource1And2 =
        new Trigger.Builder()
            .setTriggerTime(20)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(attributionDestination)
            .build();
    List<Source> result2 = runFunc.apply(trigger2MatchSource1And2);
    Assert.assertEquals(2, result2.size());
    Assert.assertEquals(s1.getId(), result2.get(0).getId());
    Assert.assertEquals(s2.getId(), result2.get(1).getId());
    // Trigger Time > s1's expiryTime
    // Trigger Time > s2's eventTime and < s2's expiryTime
    // Trigger Time > s3's eventTime and < s3's expiryTime
    // Trigger Time < s4's eventTime
    // Expected: Match with s2 and s3
    Trigger trigger3MatchSource2And3 =
        new Trigger.Builder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(attributionDestination)
            .build();
    List<Source> result3 = runFunc.apply(trigger3MatchSource2And3);
    Assert.assertEquals(2, result3.size());
    Assert.assertEquals(s2.getId(), result3.get(0).getId());
    Assert.assertEquals(s3.getId(), result3.get(1).getId());
    // Trigger Time > s1's expiryTime
    // Trigger Time > s2's eventTime and < s2's expiryTime
    // Trigger Time > s3's eventTime and < s3's expiryTime
    // Trigger Time > s4's eventTime and < s4's expiryTime
    // Expected: Match with s2, s3 and s4
    Trigger trigger4MatchSource1And2And3 =
        new Trigger.Builder()
            .setTriggerTime(31)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(attributionDestination)
            .build();
    List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
    Assert.assertEquals(3, result4.size());
    Assert.assertEquals(s2.getId(), result4.get(0).getId());
    Assert.assertEquals(s3.getId(), result4.get(1).getId());
    Assert.assertEquals(s4.getId(), result4.get(2).getId());
  }

  @Test
  public void GetTriggerTest() {
    Trigger searchTrigger = new Trigger.Builder().setId("A").build();
    IMeasurementDAO measurementDAO =
        new MeasurementDAO(
            List.of(),
            List.of(
                searchTrigger,
                new Trigger.Builder().setId("B").build(),
                new Trigger.Builder().setId("C").build()));

    Assert.assertTrue(measurementDAO.getTrigger("A").equals(searchTrigger));
  }

  @Test
  public void GetTriggerTest_NoneFound() {
    // Returns null
    IMeasurementDAO measurementDAO =
        new MeasurementDAO(
            List.of(),
            List.of(
                new Trigger.Builder().setId("A").build(),
                new Trigger.Builder().setId("B").build(),
                new Trigger.Builder().setId("C").build()));

    Assert.assertTrue(measurementDAO.getTrigger("D") == null);
  }

  private Source createSourceForIATest(
      String id, long currentTime, long priority, int eventTimePastDays, boolean expiredIAWindow) {
    return new Source.Builder()
        .setId(id)
        .setPublisher(URI.create("android-app://com.example.sample"))
        .setRegistrant(URI.create("android-app://com.example.sample"))
        .setEnrollmentId("https://example.com")
        .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
        .setAppDestination(INSTALLED_PACKAGE)
        .setEventTime(
            currentTime - TimeUnit.DAYS.toMillis(eventTimePastDays == -1 ? 10 : eventTimePastDays))
        .setPriority(priority == -1 ? 100 : priority)
        .build();
  }

  private Attribution createAttribution(Source source, Trigger trigger) {
    Optional<URI> publisherBaseURI =
        extractBaseURI(source.getPublisher(), source.getPublisherType());
    URI destination = trigger.getAttributionDestination();
    Optional<URI> destinationBaseURI = extractBaseURI(destination, trigger.getDestinationType());
    String publisherTopPrivateDomain = publisherBaseURI.get().toString();
    String triggerDestinationTopPrivateDomain = destinationBaseURI.get().toString();
    return new Attribution.Builder()
        .setSourceSite(publisherTopPrivateDomain)
        .setSourceOrigin(BaseUriExtractor.getBaseUri(source.getPublisher()).toString())
        .setDestinationSite(triggerDestinationTopPrivateDomain)
        .setDestinationOrigin(BaseUriExtractor.getBaseUri(destination).toString())
        .setEnrollmentId(trigger.getEnrollmentId())
        .setTriggerTime(trigger.getTriggerTime())
        .setRegistrant(trigger.getRegistrant().toString())
        .build();
  }

  private static Optional<URI> extractBaseURI(URI uri, EventSurfaceType eventSurfaceType) {
    return eventSurfaceType == EventSurfaceType.APP
        ? Optional.of(BaseUriExtractor.getBaseUri(uri))
        : Web.topPrivateDomainAndScheme(uri);
  }
}
