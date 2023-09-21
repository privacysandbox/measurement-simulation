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

import static com.google.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.measurement.aggregation.AggregateReport;
import com.google.measurement.aggregation.AggregateReportFixture;
import com.google.measurement.noising.SourceNoiseHandler;
import com.google.measurement.util.UnsignedLong;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MeasurementDAOTest {
  private static final URI APP_TWO_SOURCES = URI.create("android-app://com.example1.two-sources");
  private static final URI APP_ONE_SOURCE = URI.create("android-app://com.example2.one-source");
  private static final String DEFAULT_ENROLLMENT_ID = "enrollment-id";
  private static final URI APP_TWO_PUBLISHER =
      URI.create("android-app://com.publisher2.two-sources");
  private static final URI APP_ONE_PUBLISHER =
      URI.create("android-app://com.publisher1.one-source");
  private static final URI APP_NO_PUBLISHER = URI.create("android-app://com.publisher3.no-sources");
  private static final URI APP_BROWSER = URI.create("android-app://com.example1.browser");
  private static final URI WEB_ONE_DESTINATION = WebUtil.validUri("https://www.example1.test");
  private static final URI WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN =
      WebUtil.validUri("https://store.example1.test");
  private static final URI WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2 =
      WebUtil.validUri("https://foo.example1.test");
  private static final URI WEB_TWO_DESTINATION = WebUtil.validUri("https://www.example2.test");
  private static final URI WEB_TWO_DESTINATION_WITH_PATH =
      WebUtil.validUri("https://www.example2.test/ad/foo");
  private static final URI APP_ONE_DESTINATION =
      URI.create("android-app://com.example1.one-trigger");
  private static final URI APP_TWO_DESTINATION =
      URI.create("android-app://com.example1.two-triggers");
  private static final URI APP_THREE_DESTINATION =
      URI.create("android-app://com.example1.three-triggers");
  private static final URI APP_THREE_DESTINATION_PATH1 =
      URI.create("android-app://com.example1.three-triggers/path1");
  private static final URI APP_THREE_DESTINATION_PATH2 =
      URI.create("android-app://com.example1.three-triggers/path2");
  private static final URI APP_NO_TRIGGERS = URI.create("android-app://com.example1.no-triggers");
  private static final URI INSTALLED_PACKAGE = URI.create("android-app://com.example.installed");
  private static final URI WEB_PUBLISHER_ONE = WebUtil.validUri("https://not.example.test");
  private static final URI WEB_PUBLISHER_TWO = WebUtil.validUri("https://notexample.test");
  // Differs from WEB_PUBLISHER_ONE by scheme.
  private static final URI WEB_PUBLISHER_THREE = WebUtil.validUri("http://not.example.test");
  private static final URI APP_DESTINATION = URI.create("android-app://com.destination.example");
  private static final URI REGISTRATION_ORIGIN = WebUtil.validUri("https://subdomain.example.test");
  // Fake ID count for initializing triggers.
  private int mValueId = 1;
  private Flags mFlags;
  public static final URI REGISTRATION_ORIGIN_2 =
      WebUtil.validUri("https://subdomain_2.example.test");

  @Before
  public void before() {
    mFlags = new Flags();
  }

  @Test
  public void testInsertTrigger() {
    Trigger validTrigger = TriggerFixture.getValidTrigger();
    IMeasurementDAO dao = new MeasurementDAO();
    dao.insertTrigger(validTrigger);
    Trigger trigger = dao.getTrigger(validTrigger.getId());
    assertNotNull(trigger);
    assertNotNull(trigger.getId());
    assertEquals(validTrigger.getAttributionDestination(), trigger.getAttributionDestination());
    assertEquals(validTrigger.getDestinationType(), trigger.getDestinationType());
    assertEquals(validTrigger.getEnrollmentId(), trigger.getEnrollmentId());
    assertEquals(validTrigger.getRegistrant(), trigger.getRegistrant());
    assertEquals(validTrigger.getTriggerTime(), trigger.getTriggerTime());
    assertEquals(validTrigger.getEventTriggers(), trigger.getEventTriggers());
    assertEquals(validTrigger.getAttributionConfig(), trigger.getAttributionConfig());
    assertEquals(validTrigger.getAdtechKeyMapping(), trigger.getAdtechKeyMapping());
    assertEquals(validTrigger.getPlatformAdId(), trigger.getPlatformAdId());
    assertEquals(validTrigger.getDebugAdId(), trigger.getDebugAdId());
    assertEquals(validTrigger.getRegistrationOrigin(), trigger.getRegistrationOrigin());
  }

  @Test
  public void testGetNumSourcesPerPublisher_publisherTypeApp() {
    IMeasurementDAO dao = new MeasurementDAO();
    setupSourceAndTriggerData(dao);
    assertEquals(2, dao.getNumSourcesPerPublisher(APP_TWO_PUBLISHER, EventSurfaceType.APP));
    assertEquals(1, dao.getNumSourcesPerPublisher(APP_ONE_PUBLISHER, EventSurfaceType.APP));
    assertEquals(0, dao.getNumSourcesPerPublisher(APP_NO_PUBLISHER, EventSurfaceType.APP));
  }

  @Test
  public void testGetNumSourcesPerPublisher_publisherTypeWeb() {
    IMeasurementDAO dao = new MeasurementDAO();
    setupSourceDataForPublisherTypeWeb(dao);
    assertEquals(1, dao.getNumSourcesPerPublisher(WEB_PUBLISHER_ONE, EventSurfaceType.WEB));

    assertEquals(2, dao.getNumSourcesPerPublisher(WEB_PUBLISHER_TWO, EventSurfaceType.WEB));

    assertEquals(1, dao.getNumSourcesPerPublisher(WEB_PUBLISHER_THREE, EventSurfaceType.WEB));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_atWindow() {
    IMeasurementDAO measurementDAO = new MeasurementDAO();
    URI sourceSite = URI.create("android-app://publisher.app");
    URI appDestination = URI.create("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentEnrollments(
            4, appDestination, 5000000001L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution, measurementDAO);
    }
    String excludedEnrollmentId = "enrollment-id-0";
    assertEquals(
        Integer.valueOf(3),
        measurementDAO.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            sourceSite, appDestination, excludedEnrollmentId, 5000000000L, 6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_beyondWindow() {
    IMeasurementDAO measurementDAO = new MeasurementDAO();
    URI sourceSite = URI.create("android-app://publisher.app");
    URI appDestination = URI.create("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentEnrollments(
            4, appDestination, 5000000000L, sourceSite, registrant);
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution, measurementDAO);
    }
    String excludedEnrollmentId = "enrollment-id-0";
    assertEquals(
        Integer.valueOf(0),
        measurementDAO.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            sourceSite, appDestination, excludedEnrollmentId, 5000000000L, 6000000000L));
  }

  @Test
  public void singleAppTrigger_triggersPerDestination_returnsOne() {
    IMeasurementDAO measurementDAO = new MeasurementDAO();
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_ONE_DESTINATION, APP_ONE_DESTINATION));
    addTriggersToDatabase(triggerList, measurementDAO);
    assertEquals(
        1L, measurementDAO.getNumTriggersPerDestination(APP_ONE_DESTINATION, EventSurfaceType.APP));
  }

  @Test
  public void multipleAppTriggers_similarUris_triggersPerDestination() {
    IMeasurementDAO measurementDAO = new MeasurementDAO();
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
    triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
    addTriggersToDatabase(triggerList, measurementDAO);
    assertEquals(
        2L, measurementDAO.getNumTriggersPerDestination(APP_TWO_DESTINATION, EventSurfaceType.APP));
  }

  @Test
  public void noAppTriggers_triggersPerDestination_returnsNone() {
    IMeasurementDAO measurementDAO = new MeasurementDAO();
    assertEquals(
        0L, measurementDAO.getNumTriggersPerDestination(APP_NO_TRIGGERS, EventSurfaceType.APP));
  }

  @Test
  public void multipleAppTriggers_differentPaths_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION));
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH1));
    triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH2));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(APP_THREE_DESTINATION, EventSurfaceType.APP));

    // Try the same thing, but use the app uri with path to find number of triggers.
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            APP_THREE_DESTINATION_PATH1, EventSurfaceType.APP));
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            APP_THREE_DESTINATION_PATH2, EventSurfaceType.APP));
    URI unseenAppThreePath = URI.create("android-app://com.example1.three-triggers/path3");
    assertEquals(
        3L, measurementDao.getNumTriggersPerDestination(unseenAppThreePath, EventSurfaceType.APP));
  }

  @Test
  public void singleWebTrigger_triggersPerDestination_returnsOne() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    assertEquals(
        1L, measurementDao.getNumTriggersPerDestination(WEB_ONE_DESTINATION, EventSurfaceType.WEB));
  }

  @Test
  public void webTriggerMultipleSubDomains_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN));
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    assertEquals(
        3L, measurementDao.getNumTriggersPerDestination(WEB_ONE_DESTINATION, EventSurfaceType.WEB));

    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN, EventSurfaceType.WEB));
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2, EventSurfaceType.WEB));

    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            WebUtil.validUri("https://new-subdomain.example1.test"), EventSurfaceType.WEB));

    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            WebUtil.validUri("https://example1.test"), EventSurfaceType.WEB));
  }

  @Test
  public void webTriggerWithoutSubdomains_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    URI webDestinationWithoutSubdomain = WebUtil.validUri("https://example1.test");
    URI webDestinationWithoutSubdomainPath1 = WebUtil.validUri("https://example1.test/path1");
    URI webDestinationWithoutSubdomainPath2 = WebUtil.validUri("https://example1.test/path2");
    URI webDestinationWithoutSubdomainPath3 = WebUtil.validUri("https://example1.test/path3");
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomain));
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath1));
    triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath2));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            webDestinationWithoutSubdomain, EventSurfaceType.WEB));
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            webDestinationWithoutSubdomainPath1, EventSurfaceType.WEB));
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            webDestinationWithoutSubdomainPath2, EventSurfaceType.WEB));
    assertEquals(
        3L,
        measurementDao.getNumTriggersPerDestination(
            webDestinationWithoutSubdomainPath3, EventSurfaceType.WEB));
  }

  @Test
  public void webTriggerDifferentPaths_triggersPerDestination_returnsAllMatching() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_TWO_DESTINATION));
    triggerList.add(createWebTrigger(WEB_TWO_DESTINATION_WITH_PATH));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    assertEquals(
        2L, measurementDao.getNumTriggersPerDestination(WEB_TWO_DESTINATION, EventSurfaceType.WEB));
    assertEquals(
        2L,
        measurementDao.getNumTriggersPerDestination(
            WEB_TWO_DESTINATION_WITH_PATH, EventSurfaceType.WEB));
  }

  @Test
  public void noMathingWebTriggers_triggersPerDestination_returnsZero() {
    List<Trigger> triggerList = new ArrayList<>();
    triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    addTriggersToDatabase(triggerList, measurementDao);
    URI differentScheme = WebUtil.validUri("http://www.example1.test");
    assertEquals(
        0, measurementDao.getNumTriggersPerDestination(differentScheme, EventSurfaceType.WEB));

    URI notMatchingUrl2 = WebUtil.validUri("https://www.not-example1.test");
    assertEquals(
        0, measurementDao.getNumTriggersPerDestination(notMatchingUrl2, EventSurfaceType.WEB));
    URI notMatchingUrl = WebUtil.validUri("https://www.not-example-1.test");
    assertEquals(
        0, measurementDao.getNumTriggersPerDestination(notMatchingUrl, EventSurfaceType.WEB));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_appDestination() {
    URI sourceSite = URI.create("android-app://publisher.app");
    URI webDestination = WebUtil.validUri("https://web-destination.test");
    URI appDestination = URI.create("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations1 =
        getAttributionsWithDifferentEnrollments(
            4, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithAppDestinations2 =
        getAttributionsWithDifferentEnrollments(
            2, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations =
        getAttributionsWithDifferentEnrollments(
            2, webDestination, 5500000000L, sourceSite, registrant);
    List<Attribution> attributionsOutOfWindow =
        getAttributionsWithDifferentEnrollments(
            10, appDestination, 50000000000L, sourceSite, registrant);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Attribution attribution : attributionsWithAppDestinations1) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsWithAppDestinations2) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsWithWebDestinations) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsOutOfWindow) {
      insertAttribution(attribution, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-0";
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            sourceSite, appDestination, excludedEnrollmentId, 4000000000L, 6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_webDestination() {
    URI sourceSite = URI.create("android-app://publisher.app");
    URI webDestination = WebUtil.validUri("https://web-destination.test");
    URI appDestination = URI.create("android-app://destination.app");
    String registrant = "android-app://registrant.app";
    List<Attribution> attributionsWithAppDestinations =
        getAttributionsWithDifferentEnrollments(
            2, appDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations1 =
        getAttributionsWithDifferentEnrollments(
            4, webDestination, 5000000000L, sourceSite, registrant);
    List<Attribution> attributionsWithWebDestinations2 =
        getAttributionsWithDifferentEnrollments(
            2, webDestination, 5500000000L, sourceSite, registrant);
    List<Attribution> attributionsOutOfWindow =
        getAttributionsWithDifferentEnrollments(
            10, webDestination, 50000000000L, sourceSite, registrant);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Attribution attribution : attributionsWithAppDestinations) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsWithWebDestinations1) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsWithWebDestinations2) {
      insertAttribution(attribution, measurementDao);
    }
    for (Attribution attribution : attributionsOutOfWindow) {
      insertAttribution(attribution, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-3";
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            sourceSite, webDestination, excludedEnrollmentId, 4000000000L, 6000000000L));
    ;
  }

  @Test
  public void testCountDistinctDestinationsPerPublisherInActiveSource_atWindow() {
    URI publisher = URI.create("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000001L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.APP,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctDestinationsPerPublisherInActiveSource_expiredSource() {
    URI publisher = URI.create("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000001L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> expiredSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            6,
            true,
            true,
            4500000001L,
            6000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE,
            REGISTRATION_ORIGIN);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : expiredSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.APP,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctDestinationsPerPublisherInActiveSource_beyondWindow() {
    URI publisher = URI.create("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(0),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.APP,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctDestinationsPerPublisherInActiveSource_appPublisher() {
    URI publisher = URI.create("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.APP,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4000000000L,
            6000000000L));
  }

  // (Testing countDistinctDestinationsPerPublisherInActiveSource)
  @Test
  public void testCountDistinctDestinations_appPublisher_enrollmentMismatch() {
    URI publisher = URI.create("android-app://publisher.app");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(0),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.APP,
            "unmatched-enrollment-id",
            excludedDestinations,
            EventSurfaceType.WEB,
            4000000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctDestinationsPerPublisherInActiveSource_webPublisher_exactMatch() {
    URI publisher = WebUtil.validUri("https://publisher.test");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.WEB,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4000000000L,
            6000000000L));
  }

  // (Testing countDistinctDestinationsPerPublisherXEnrollmentInActiveSource)
  @Test
  public void testCountDistinctDestinations_webPublisher_doesNotMatchDifferentScheme() {
    URI publisher = WebUtil.validUri("https://publisher.test");
    URI publisherWithDifferentScheme = WebUtil.validUri("http://publisher.test");
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentDestinations(
            4,
            true,
            true,
            4500000000L,
            publisherWithDifferentScheme,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            2,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations = List.of(WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(2),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.WEB,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4000000000L,
            6000000000L));
  }

  // countDistinctDestinationsPerPublisherXEnrollmentInActiveSource
  @Test
  public void countDistinctDestinationsPerPublisher_webPublisher_multipleDestinations() {
    URI publisher = WebUtil.validUri("https://publisher.test");
    // One source with multiple destinations
    Source activeSourceWithAppAndWebDestinations =
        getSourceWithDifferentDestinations(
            3,
            true,
            true,
            4500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentDestinations(
            2,
            true,
            false,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentDestinations(
            1,
            false,
            true,
            5500000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            50000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentDestinations(
            10,
            true,
            true,
            5000000000L,
            publisher,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    insertSource(activeSourceWithAppAndWebDestinations, measurementDao);
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    List<URI> excludedDestinations =
        List.of(
            WebUtil.validUri("https://web-destination-1.test"),
            WebUtil.validUri("https://web-destination-2.test"));
    assertEquals(
        Integer.valueOf(1),
        measurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
            publisher,
            EventSurfaceType.WEB,
            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
            excludedDestinations,
            EventSurfaceType.WEB,
            4000000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_atWindow() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations, 4500000001L, publisher, Source.Status.ACTIVE);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    assertEquals(
        Integer.valueOf(1),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.APP,
            appDestinations,
            excludedEnrollmentId,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_beyondWindow() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    assertEquals(
        Integer.valueOf(0),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.APP,
            appDestinations,
            excludedEnrollmentId,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_expiredSource() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations, 4500000001L, publisher, Source.Status.ACTIVE);
    List<Source> expiredSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            4,
            appDestinations,
            webDestinations,
            4500000000L,
            6000000000L,
            publisher,
            Source.Status.ACTIVE);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : expiredSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    assertEquals(
        Integer.valueOf(1),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.APP,
            appDestinations,
            excludedEnrollmentId,
            4500000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_appDestination() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, null, webDestinations, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentEnrollments(
            10, appDestinations, webDestinations, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentEnrollments(
            3, appDestinations, webDestinations, 5000000000L, publisher, Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    assertEquals(
        Integer.valueOf(2),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.APP,
            appDestinations,
            excludedEnrollmentId,
            4000000000L,
            6000000000L));
  }

  @Test
  public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_webDestination() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations = List.of(WebUtil.validUri("https://web-destination.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, null, webDestinations, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentEnrollments(
            10, appDestinations, webDestinations, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentEnrollments(
            3, appDestinations, webDestinations, 5000000000L, publisher, Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-22";
    assertEquals(
        Integer.valueOf(3),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.WEB,
            webDestinations,
            excludedEnrollmentId,
            4000000000L,
            6000000000L));
  }

  // countDistinctEnrollmentsPerPublisherXDestinationInSource
  @Test
  public void countDistinctEnrollmentsPerPublisher_webDestination_multipleDestinations() {
    URI publisher = URI.create("android-app://publisher.app");
    List<URI> webDestinations1 = List.of(WebUtil.validUri("https://web-destination-1.test"));
    List<URI> webDestinations2 =
        List.of(
            WebUtil.validUri("https://web-destination-1.test"),
            WebUtil.validUri("https://web-destination-2.test"));
    List<URI> appDestinations = List.of(URI.create("android-app://destination.app"));
    List<Source> activeSourcesWithAppAndWebDestinations =
        getSourcesWithDifferentEnrollments(
            3, appDestinations, webDestinations1, 4500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithAppDestinations =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, null, 5000000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesWithWebDestinations =
        getSourcesWithDifferentEnrollments(
            2, null, webDestinations2, 5500000000L, publisher, Source.Status.ACTIVE);
    List<Source> activeSourcesOutOfWindow =
        getSourcesWithDifferentEnrollments(
            10, appDestinations, webDestinations2, 50000000000L, publisher, Source.Status.ACTIVE);
    List<Source> ignoredSources =
        getSourcesWithDifferentEnrollments(
            2, appDestinations, webDestinations1, 5000000000L, publisher, Source.Status.IGNORED);
    IMeasurementDAO measurementDao = new MeasurementDAO();
    for (Source source : activeSourcesWithAppAndWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithAppDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesWithWebDestinations) {
      insertSource(source, measurementDao);
    }
    for (Source source : activeSourcesOutOfWindow) {
      insertSource(source, measurementDao);
    }
    for (Source source : ignoredSources) {
      insertSource(source, measurementDao);
    }
    String excludedEnrollmentId = "enrollment-id-1";
    assertEquals(
        Integer.valueOf(2),
        measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
            publisher,
            EventSurfaceType.WEB,
            webDestinations2,
            excludedEnrollmentId,
            4000000000L,
            6000000000L));
  }

  @Test
  public void getNumAggregateReportsPerDestination_returnsExpected() {
    List<AggregateReport> reportsWithPlainDestination =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-1.test"), 1));
    List<AggregateReport> reportsWithPlainAndSubDomainDestination =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-2.test"), 2),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-2.test"), 3));
    List<AggregateReport> reportsWithPlainAndPathDestination =
        Arrays.asList(
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-3.test"), 4),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-3.test/abcd"), 5));
    List<AggregateReport> reportsWithAll3Types =
        Arrays.asList(
            generateMockAggregateReport(WebUtil.validUrl("https://destination-4.test"), 6),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-4.test"), 7),
            generateMockAggregateReport(
                WebUtil.validUrl("https://subdomain.destination-4.test/abcd"), 8));
    List<AggregateReport> reportsWithAndroidAppDestination =
        Arrays.asList(generateMockAggregateReport("android-app://destination-5.app", 9));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    Stream.of(
            reportsWithPlainDestination,
            reportsWithPlainAndSubDomainDestination,
            reportsWithPlainAndPathDestination,
            reportsWithAll3Types,
            reportsWithAndroidAppDestination)
        .flatMap(Collection::stream)
        .forEach(
            aggregateReport -> {
              measurementDao.insertAggregateReport(aggregateReport);
            });
    List<String> attributionDestinations1 = createWebDestinationVariants(1);
    List<String> attributionDestinations2 = createWebDestinationVariants(2);
    List<String> attributionDestinations3 = createWebDestinationVariants(3);
    List<String> attributionDestinations4 = createWebDestinationVariants(4);
    List<String> attributionDestinations5 = createAppDestinationVariants(5);
    // expected query return values for attribution destination variants
    List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
    List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
    List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
    assertAggregateReportCount(
        attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts, measurementDao);
    assertAggregateReportCount(
        attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts, measurementDao);
    assertAggregateReportCount(
        attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts, measurementDao);
    assertAggregateReportCount(
        attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts, measurementDao);
    assertAggregateReportCount(
        attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts, measurementDao);
  }

  @Test
  public void getNumEventReportsPerDestination_returnsExpected() {
    List<EventReport> reportsWithPlainDestination =
        Arrays.asList(generateMockEventReport(WebUtil.validUrl("https://destination-1.test"), 1));
    List<EventReport> reportsWithPlainAndSubDomainDestination =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://destination-2.test"), 2),
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-2.test"), 3));
    List<EventReport> reportsWithPlainAndPathDestination =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-3.test"), 4),
            generateMockEventReport(
                WebUtil.validUrl("https://subdomain.destination-3.test/abcd"), 5));
    List<EventReport> reportsWithAll3Types =
        Arrays.asList(
            generateMockEventReport(WebUtil.validUrl("https://destination-4.test"), 6),
            generateMockEventReport(WebUtil.validUrl("https://subdomain.destination-4.test"), 7),
            generateMockEventReport(
                WebUtil.validUrl("https://subdomain.destination-4.test/abcd"), 8));
    List<EventReport> reportsWithAndroidAppDestination =
        Arrays.asList(generateMockEventReport("android-app://destination-5.app", 9));
    IMeasurementDAO measurementDao = new MeasurementDAO();
    Stream.of(
            reportsWithPlainDestination,
            reportsWithPlainAndSubDomainDestination,
            reportsWithPlainAndPathDestination,
            reportsWithAll3Types,
            reportsWithAndroidAppDestination)
        .flatMap(Collection::stream)
        .forEach(
            eventReport -> {
              measurementDao.insertEventReport(eventReport);
            });
    List<String> attributionDestinations1 = createWebDestinationVariants(1);
    List<String> attributionDestinations2 = createWebDestinationVariants(2);
    List<String> attributionDestinations3 = createWebDestinationVariants(3);
    List<String> attributionDestinations4 = createWebDestinationVariants(4);
    List<String> attributionDestinations5 = createAppDestinationVariants(5);
    // expected query return values for attribution destination variants
    List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
    List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
    List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
    List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
    assertEventReportCount(
        attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts, measurementDao);
    assertEventReportCount(
        attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts, measurementDao);
    assertEventReportCount(
        attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts, measurementDao);
    assertEventReportCount(
        attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts, measurementDao);
    assertEventReportCount(
        attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts, measurementDao);
  }

  @Test
  public void testGetSourceEventReports() {
    List<Source> sourceList =
        Arrays.asList(
            SourceFixture.getValidSourceBuilder()
                .setId("1")
                .setEventId(new UnsignedLong(3L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("2")
                .setEventId(new UnsignedLong(4L))
                .setEnrollmentId("1")
                .build(),
            // Should always be ignored
            SourceFixture.getValidSourceBuilder()
                .setId("3")
                .setEventId(new UnsignedLong(4L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("15")
                .setEventId(new UnsignedLong(15L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("16")
                .setEventId(new UnsignedLong(16L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("20")
                .setEventId(new UnsignedLong(20L))
                .setEnrollmentId("2")
                .build());
    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder().setId("101").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("102").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("201").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("202").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("1001").setEnrollmentId("2").build());
    // Should match with source 1
    List<EventReport> reportList1 = new ArrayList<>();
    reportList1.add(
        new EventReport.Builder()
            .setId("1")
            .setSourceEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(0).getAppDestinations())
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList1.add(
        new EventReport.Builder()
            .setId("7")
            .setSourceEventId(new UnsignedLong(3L))
            .setEnrollmentId("1")
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    // Should match with source 2
    List<EventReport> reportList2 = new ArrayList<>();
    reportList2.add(
        new EventReport.Builder()
            .setId("3")
            .setSourceEventId(new UnsignedLong(4L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("201")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList2.add(
        new EventReport.Builder()
            .setId("8")
            .setSourceEventId(new UnsignedLong(4L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("202")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    List<EventReport> reportList3 = new ArrayList<>();
    // Should not match with any source
    reportList3.add(
        new EventReport.Builder()
            .setId("2")
            .setSourceEventId(new UnsignedLong(5L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("15")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("4")
            .setSourceEventId(new UnsignedLong(6L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("16")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("5")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("15")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("6")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("20")
            .setTriggerId("1001")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    IMeasurementDAO measurementDao = new MeasurementDAO();
    sourceList.forEach(source -> measurementDao.insertSource(source));
    triggers.forEach(trigger -> measurementDao.insertTrigger(trigger));
    Stream.of(reportList1, reportList2, reportList3)
        .flatMap(Collection::stream)
        .forEach((eventReport -> measurementDao.insertEventReport(eventReport)));
    assertEquals(reportList1, measurementDao.getSourceEventReports(sourceList.get(0)));
    assertEquals(reportList2, measurementDao.getSourceEventReports(sourceList.get(1)));
  }

  @Test
  public void getSourceEventReports_sourcesWithSameEventId_haveSeparateEventReportsMatch() {
    List<Source> sourceList =
        Arrays.asList(
            SourceFixture.getValidSourceBuilder()
                .setId("1")
                .setEventId(new UnsignedLong(1L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("2")
                .setEventId(new UnsignedLong(1L))
                .setEnrollmentId("1")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("3")
                .setEventId(new UnsignedLong(2L))
                .setEnrollmentId("2")
                .build(),
            SourceFixture.getValidSourceBuilder()
                .setId("4")
                .setEventId(new UnsignedLong(2L))
                .setEnrollmentId("2")
                .build());
    List<Trigger> triggers =
        Arrays.asList(
            TriggerFixture.getValidTriggerBuilder().setId("101").setEnrollmentId("2").build(),
            TriggerFixture.getValidTriggerBuilder().setId("102").setEnrollmentId("2").build());
    // Should match with source 1
    List<EventReport> reportList1 = new ArrayList<>();
    reportList1.add(
        new EventReport.Builder()
            .setId("1")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(0).getAppDestinations())
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList1.add(
        new EventReport.Builder()
            .setId("2")
            .setSourceEventId(new UnsignedLong(1L))
            .setEnrollmentId("1")
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceType(sourceList.get(0).getSourceType())
            .setSourceId("1")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    // Should match with source 2
    List<EventReport> reportList2 = new ArrayList<>();
    reportList2.add(
        new EventReport.Builder()
            .setId("3")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList2.add(
        new EventReport.Builder()
            .setId("4")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("1")
            .setAttributionDestinations(sourceList.get(1).getAppDestinations())
            .setSourceType(sourceList.get(1).getSourceType())
            .setSourceId("2")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    // Match with source3
    List<EventReport> reportList3 = new ArrayList<>();
    reportList3.add(
        new EventReport.Builder()
            .setId("5")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("2")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("3")
            .setTriggerId("101")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    reportList3.add(
        new EventReport.Builder()
            .setId("6")
            .setSourceEventId(new UnsignedLong(2L))
            .setEnrollmentId("2")
            .setSourceType(Source.SourceType.EVENT)
            .setAttributionDestinations(List.of(APP_DESTINATION))
            .setSourceId("3")
            .setTriggerId("102")
            .setRegistrationOrigin(REGISTRATION_ORIGIN)
            .build());
    IMeasurementDAO measurementDao = new MeasurementDAO();
    sourceList.forEach(source -> measurementDao.insertSource(source));
    triggers.forEach(trigger -> measurementDao.insertTrigger(trigger));
    Stream.of(reportList1, reportList2, reportList3)
        .flatMap(Collection::stream)
        .forEach((eventReport -> measurementDao.insertEventReport(eventReport)));
    assertEquals(reportList1, measurementDao.getSourceEventReports(sourceList.get(0)));
    assertEquals(reportList2, measurementDao.getSourceEventReports(sourceList.get(1)));
    assertEquals(reportList3, measurementDao.getSourceEventReports(sourceList.get(2)));
  }

  @Test
  public void testGetMatchingActiveSources() {
    IMeasurementDAO measurementDao = new MeasurementDAO();
    String enrollmentId = "enrollment-id";
    URI appDestination = URI.create("android-app://com.example.abc");
    URI webDestination = WebUtil.validUri("https://example.test");
    URI webDestinationWithSubdomain = WebUtil.validUri("https://xyz.example.test");
    Source sApp1 =
        SourceFixture.getValidSourceBuilder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp2 =
        SourceFixture.getValidSourceBuilder()
            .setId("2")
            .setEventTime(10)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp3 =
        SourceFixture.getValidSourceBuilder()
            .setId("3")
            .setEventTime(20)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sApp4 =
        SourceFixture.getValidSourceBuilder()
            .setId("4")
            .setEventTime(30)
            .setExpiryTime(50)
            .setAppDestinations(List.of(appDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb5 =
        SourceFixture.getValidSourceBuilder()
            .setId("5")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb6 =
        SourceFixture.getValidSourceBuilder()
            .setId("6")
            .setEventTime(10)
            .setExpiryTime(50)
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb7 =
        SourceFixture.getValidSourceBuilder()
            .setId("7")
            .setEventTime(10)
            .setExpiryTime(20)
            .setAppDestinations(List.of(appDestination))
            .setWebDestinations(List.of(webDestination))
            .setEnrollmentId(enrollmentId)
            .build();
    List<Source> sources = Arrays.asList(sApp1, sApp2, sApp3, sApp4, sWeb5, sWeb6, sAppWeb7);
    sources.forEach(source -> measurementDao.insertSource(source));
    Function<Trigger, List<Source>> runFunc =
        trigger -> {
          List<Source> result = measurementDao.getMatchingActiveSources(trigger);
          result.sort(Comparator.comparing(Source::getId));
          return result;
        };
    // Trigger Time > sApp1's eventTime and < sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time < sApp3's eventTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
    // Expected: Match with sApp1, sApp2, sAppWeb7
    Trigger trigger1MatchSource1And2 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
    assertEquals(3, result1.size());
    assertEquals(sApp1.getId(), result1.get(0).getId());
    assertEquals(sApp2.getId(), result1.get(1).getId());
    assertEquals(sAppWeb7.getId(), result1.get(2).getId());
    // Trigger Time > sApp1's eventTime and = sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time = sApp3's eventTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's eventTime and = sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3
    Trigger trigger2MatchSource127 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(20)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result2 = runFunc.apply(trigger2MatchSource127);
    assertEquals(2, result2.size());
    assertEquals(sApp2.getId(), result2.get(0).getId());
    assertEquals(sApp3.getId(), result2.get(1).getId());
    // Trigger Time > sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
    // Trigger Time < sApp4's eventTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3
    Trigger trigger3MatchSource237 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result3 = runFunc.apply(trigger3MatchSource237);
    assertEquals(2, result3.size());
    assertEquals(sApp2.getId(), result3.get(0).getId());
    assertEquals(sApp3.getId(), result3.get(1).getId());
    // Trigger Time > sApp1's expiryTime
    // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
    // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
    // Trigger Time > sApp4's eventTime and < sApp4's expiryTime
    // sApp5 and sApp6 don't have app destination
    // Trigger Time > sAppWeb7's expiryTime
    // Expected: Match with sApp2, sApp3 and sApp4
    Trigger trigger4MatchSource1And2And3 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(31)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(appDestination)
            .setDestinationType(EventSurfaceType.APP)
            .build();
    List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
    assertEquals(3, result4.size());
    assertEquals(sApp2.getId(), result4.get(0).getId());
    assertEquals(sApp3.getId(), result4.get(1).getId());
    assertEquals(sApp4.getId(), result4.get(2).getId());
    // sApp1, sApp2, sApp3, sApp4 don't have web destination
    // Trigger Time > sWeb5's eventTime and < sApp5's expiryTime
    // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
    // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
    // Expected: Match with sApp5, sApp6, sAppWeb7
    Trigger trigger5MatchSource567 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(12)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestination)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    List<Source> result5 = runFunc.apply(trigger5MatchSource567);
    assertEquals(3, result1.size());
    assertEquals(sWeb5.getId(), result5.get(0).getId());
    assertEquals(sWeb6.getId(), result5.get(1).getId());
    assertEquals(sAppWeb7.getId(), result5.get(2).getId());
    // sApp1, sApp2, sApp3, sApp4 don't have web destination
    // Trigger Time > sWeb5's expiryTime
    // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
    // Trigger Time > sWeb7's expiryTime
    // Expected: Match with sApp6 only
    Trigger trigger6MatchSource67 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestinationWithSubdomain)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    List<Source> result6 = runFunc.apply(trigger6MatchSource67);
    assertEquals(1, result6.size());
    assertEquals(sWeb6.getId(), result6.get(0).getId());
  }

  @Test
  public void testGetMatchingActiveSources_multipleDestinations() {
    IMeasurementDAO measurementDao = new MeasurementDAO();
    String enrollmentId = "enrollment-id";
    URI webDestination1 = WebUtil.validUri("https://example.test");
    URI webDestination1WithSubdomain = WebUtil.validUri("https://xyz.example.test");
    URI webDestination2 = WebUtil.validUri("https://example2.test");
    Source sWeb1 =
        SourceFixture.getValidSourceBuilder()
            .setId("1")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination1))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sWeb2 =
        SourceFixture.getValidSourceBuilder()
            .setId("2")
            .setEventTime(10)
            .setExpiryTime(50)
            .setWebDestinations(List.of(webDestination1, webDestination2))
            .setEnrollmentId(enrollmentId)
            .build();
    Source sAppWeb3 =
        SourceFixture.getValidSourceBuilder()
            .setId("3")
            .setEventTime(10)
            .setExpiryTime(20)
            .setWebDestinations(List.of(webDestination1))
            .setEnrollmentId(enrollmentId)
            .build();
    List<Source> sources = Arrays.asList(sWeb1, sWeb2, sAppWeb3);
    sources.forEach(source -> measurementDao.insertSource(source));
    Function<Trigger, List<Source>> getMatchingSources =
        trigger -> {
          List<Source> result = measurementDao.getMatchingActiveSources(trigger);
          result.sort(Comparator.comparing(Source::getId));
          return result;
        };
    Trigger triggerMatchSourceWeb2 =
        TriggerFixture.getValidTriggerBuilder()
            .setTriggerTime(21)
            .setEnrollmentId(enrollmentId)
            .setAttributionDestination(webDestination1WithSubdomain)
            .setDestinationType(EventSurfaceType.WEB)
            .build();
    List<Source> result = getMatchingSources.apply(triggerMatchSourceWeb2);
    assertEquals(1, result.size());
    assertEquals(sWeb2.getId(), result.get(0).getId());
  }

  private static Source getSourceWithDifferentDestinations(
      int numDestinations,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      URI publisher,
      String enrollmentId,
      Source.Status sourceStatus) {
    List<URI> appDestinations = null;
    List<URI> webDestinations = null;
    if (hasAppDestinations) {
      appDestinations = new ArrayList<>();
      appDestinations.add(URI.create("android-app://com.app-destination"));
    }
    if (hasWebDestinations) {
      webDestinations = new ArrayList<>();
      for (int i = 0; i < numDestinations; i++) {
        webDestinations.add(URI.create("https://web-destination-" + String.valueOf(i) + ".com"));
      }
    }
    long expiryTime =
        eventTime + TimeUnit.SECONDS.toMillis(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return new Source.Builder()
        .setEventId(new UnsignedLong(0L))
        .setEventTime(eventTime)
        .setExpiryTime(expiryTime)
        .setPublisher(publisher)
        .setAppDestinations(appDestinations)
        .setWebDestinations(webDestinations)
        .setEnrollmentId(enrollmentId)
        .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
        .setStatus(sourceStatus)
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .build();
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      URI publisher,
      String enrollmentId,
      Source.Status sourceStatus,
      URI registrationOrigin) {
    long expiryTime =
        eventTime + TimeUnit.SECONDS.toMillis(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentDestinations(
        numSources,
        hasAppDestinations,
        hasWebDestinations,
        eventTime,
        expiryTime,
        publisher,
        enrollmentId,
        sourceStatus,
        registrationOrigin);
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      URI publisher,
      String enrollmentId,
      Source.Status sourceStatus) {
    long expiryTime =
        eventTime + TimeUnit.SECONDS.toMillis(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentDestinations(
        numSources,
        hasAppDestinations,
        hasWebDestinations,
        eventTime,
        expiryTime,
        publisher,
        enrollmentId,
        sourceStatus,
        REGISTRATION_ORIGIN);
  }

  private static List<Source> getSourcesWithDifferentDestinations(
      int numSources,
      boolean hasAppDestinations,
      boolean hasWebDestinations,
      long eventTime,
      long expiryTime,
      URI publisher,
      String enrollmentId,
      Source.Status sourceStatus,
      URI registrationOrigin) {
    List<Source> sources = new ArrayList<>();
    for (int i = 0; i < numSources; i++) {
      Source.Builder sourceBuilder =
          new Source.Builder()
              .setEventId(new UnsignedLong(0L))
              .setEventTime(eventTime)
              .setExpiryTime(expiryTime)
              .setPublisher(publisher)
              .setEnrollmentId(enrollmentId)
              .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
              .setStatus(sourceStatus)
              .setRegistrationOrigin(registrationOrigin);
      if (hasAppDestinations) {
        sourceBuilder.setAppDestinations(
            List.of(URI.create("android-app://app-destination-" + String.valueOf(i))));
      }
      if (hasWebDestinations) {
        sourceBuilder.setWebDestinations(
            List.of(URI.create("https://web-destination-" + String.valueOf(i) + ".com")));
      }
      sources.add(sourceBuilder.build());
    }
    return sources;
  }

  private static List<Source> getSourcesWithDifferentEnrollments(
      int numSources,
      List<URI> appDestinations,
      List<URI> webDestinations,
      long eventTime,
      URI publisher,
      Source.Status sourceStatus) {
    long expiryTime =
        eventTime + TimeUnit.SECONDS.toMillis(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    return getSourcesWithDifferentEnrollments(
        numSources,
        appDestinations,
        webDestinations,
        eventTime,
        expiryTime,
        publisher,
        sourceStatus);
  }

  private static List<Source> getSourcesWithDifferentEnrollments(
      int numSources,
      List<URI> appDestinations,
      List<URI> webDestinations,
      long eventTime,
      long expiryTime,
      URI publisher,
      Source.Status sourceStatus) {
    List<Source> sources = new ArrayList<>();
    for (int i = 0; i < numSources; i++) {
      Source.Builder sourceBuilder =
          new Source.Builder()
              .setEventId(new UnsignedLong(0L))
              .setEventTime(eventTime)
              .setExpiryTime(expiryTime)
              .setPublisher(publisher)
              .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
              .setStatus(sourceStatus)
              .setAppDestinations(getNullableUriList(appDestinations))
              .setWebDestinations(getNullableUriList(webDestinations))
              .setEnrollmentId("enrollment-id-" + i)
              .setRegistrationOrigin(REGISTRATION_ORIGIN);
      sources.add(sourceBuilder.build());
    }
    return sources;
  }

  private static List<Attribution> getAttributionsWithDifferentEnrollments(
      int numAttributions,
      URI destinationSite,
      long triggerTime,
      URI sourceSite,
      String registrant) {
    List<Attribution> attributions = new ArrayList<>();
    for (int i = 0; i < numAttributions; i++) {
      Attribution.Builder attributionBuilder =
          new Attribution.Builder()
              .setTriggerTime(triggerTime)
              .setSourceSite(sourceSite.toString())
              .setSourceOrigin(sourceSite.toString())
              .setDestinationSite(destinationSite.toString())
              .setDestinationOrigin(destinationSite.toString())
              .setEnrollmentId("enrollment-id-" + i)
              .setRegistrant(registrant);
      attributions.add(attributionBuilder.build());
    }
    return attributions;
  }

  private static void insertAttribution(Attribution attribution, IMeasurementDAO dao) {
    dao.insertAttribution(attribution);
  }

  private static Attribution createAttributionWithSourceAndTriggerIds(
      String attributionId, String sourceId, String triggerId) {
    return new Attribution.Builder()
        .setId(attributionId)
        .setTriggerTime(0L)
        .setSourceSite("android-app://source.app")
        .setSourceOrigin("android-app://source.app")
        .setDestinationSite("android-app://destination.app")
        .setDestinationOrigin("android-app://destination.app")
        .setEnrollmentId("enrollment-id-")
        .setRegistrant("android-app://registrant.app")
        .setSourceId(sourceId)
        .setTriggerId(triggerId)
        .build();
  }

  private static void insertSource(Source source, IMeasurementDAO dao) {
    insertSource(source, UUID.randomUUID().toString(), dao);
  }

  // This is needed because MeasurementDao::insertSource inserts a default value for status.
  private static void insertSource(Source source, String sourceId, IMeasurementDAO dao) {
    dao.insertSource(source);
  }

  private static String getNullableUriString(List<URI> uriList) {
    return Optional.ofNullable(uriList).map(uris -> uris.get(0).toString()).orElse(null);
  }

  private Source.Builder createSourceBuilder() {
    return new Source.Builder()
        .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
        .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
        .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
        .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
        .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
        .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
        .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
        .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
        .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
        .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
        .setInstallAttributionWindow(SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
        .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
        .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
        .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
        .setFilterData(SourceFixture.ValidSourceParams.buildFilterData())
        .setIsDebugReporting(true)
        .setRegistrationId(UUID.randomUUID().toString())
        .setSharedAggregationKeys(SHARED_AGGREGATE_KEYS)
        .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
        .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
  }

  private AggregateReport createAggregateReportForSourceAndTrigger(Source source, Trigger trigger) {
    return createAggregateReportForSourceAndTrigger(UUID.randomUUID().toString(), source, trigger);
  }

  private EventReport createEventReportForSourceAndTrigger(Source source, Trigger trigger)
      throws ParseException {
    return createEventReportForSourceAndTrigger(UUID.randomUUID().toString(), source, trigger);
  }

  private AggregateReport createAggregateReportForSourceAndTrigger(
      String reportId, Source source, Trigger trigger) {
    return AggregateReportFixture.getValidAggregateReportBuilder()
        .setId(reportId)
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .build();
  }

  private EventReport createEventReportForSourceAndTrigger(
      String reportId, Source source, Trigger trigger) throws ParseException {
    return new EventReport.Builder()
        .setId(reportId)
        .populateFromSourceAndTrigger(
            source,
            trigger,
            trigger.parseEventTriggers(true).get(0),
            new HashMap<>(),
            new EventReportWindowCalcDelegate(mFlags),
            new SourceNoiseHandler(mFlags),
            source.getAttributionDestinations(trigger.getDestinationType()))
        .setSourceEventId(source.getEventId())
        .setSourceId(source.getId())
        .setTriggerId(trigger.getId())
        .build();
  }

  private DebugReport createDebugReport() {
    return new DebugReport.Builder()
        .setId("reportId")
        .setType("trigger-event-deduplicated")
        .setBody(
            " {\n"
                + "      \"attribution_destination\":"
                + " \"https://destination.example\",\n"
                + "      \"source_event_id\": \"45623\"\n"
                + "    }")
        .setEnrollmentId("1")
        .setRegistrationOrigin(REGISTRATION_ORIGIN)
        .build();
  }

  private void setupSourceAndTriggerData(IMeasurementDAO dao) {
    List<Source> sourcesList = new ArrayList<>();
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("S1")
            .setRegistrant(APP_TWO_SOURCES)
            .setPublisher(APP_TWO_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("S2")
            .setRegistrant(APP_TWO_SOURCES)
            .setPublisher(APP_TWO_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("S3")
            .setRegistrant(APP_ONE_SOURCE)
            .setPublisher(APP_ONE_PUBLISHER)
            .setPublisherType(EventSurfaceType.APP)
            .build());
    for (Source source : sourcesList) {
      dao.insertSource(source);
    }
    List<Trigger> triggersList = new ArrayList<>();
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T1")
            .setRegistrant(APP_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T2")
            .setRegistrant(APP_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T3")
            .setRegistrant(APP_ONE_DESTINATION)
            .build());
    // Add web triggers.
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T4")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T5")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T7")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T8")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_TWO_DESTINATION)
            .build());
    triggersList.add(
        TriggerFixture.getValidTriggerBuilder()
            .setId("T9")
            .setRegistrant(APP_BROWSER)
            .setAttributionDestination(WEB_TWO_DESTINATION_WITH_PATH)
            .build());
    for (Trigger trigger : triggersList) {
      dao.insertTrigger(trigger);
    }
  }

  private Trigger createWebTrigger(URI attributionDestination) {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("ID" + mValueId++)
        .setAttributionDestination(attributionDestination)
        .setRegistrant(APP_BROWSER)
        .build();
  }

  private Trigger createAppTrigger(URI registrant, URI destination) {
    return TriggerFixture.getValidTriggerBuilder()
        .setId("ID" + mValueId++)
        .setAttributionDestination(destination)
        .setRegistrant(registrant)
        .build();
  }

  private void addTriggersToDatabase(List<Trigger> triggersList, IMeasurementDAO dao) {
    for (Trigger trigger : triggersList) {
      dao.insertTrigger(trigger);
    }
  }

  private void setupSourceDataForPublisherTypeWeb(IMeasurementDAO dao) {
    List<Source> sourcesList = new ArrayList<>();
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("W1")
            .setPublisher(WEB_PUBLISHER_ONE)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("W21")
            .setPublisher(WEB_PUBLISHER_TWO)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("W22")
            .setPublisher(WEB_PUBLISHER_TWO)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    sourcesList.add(
        SourceFixture.getValidSourceBuilder()
            .setId("S3")
            .setPublisher(WEB_PUBLISHER_THREE)
            .setPublisherType(EventSurfaceType.WEB)
            .build());
    for (Source source : sourcesList) {
      dao.insertSource(source);
    }
  }

  private Source.Builder createSourceForIATest(
      String id,
      long currentTime,
      long priority,
      int eventTimePastDays,
      boolean expiredIAWindow,
      String enrollmentId) {
    return new Source.Builder()
        .setId(id)
        .setPublisher(URI.create("android-app://com.example.sample"))
        .setRegistrant(URI.create("android-app://com.example.sample"))
        .setEnrollmentId(enrollmentId)
        .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
        .setAppDestinations(List.of(INSTALLED_PACKAGE))
        .setEventTime(
            currentTime - TimeUnit.DAYS.toMillis(eventTimePastDays == -1 ? 10 : eventTimePastDays))
        .setPriority(priority == -1 ? 100 : priority)
        .setRegistrationOrigin(REGISTRATION_ORIGIN);
  }

  private AggregateReport generateMockAggregateReport(String attributionDestination, int id) {
    return new AggregateReport.Builder()
        .setId(String.valueOf(id))
        .setAttributionDestination(URI.create(attributionDestination))
        .build();
  }

  private EventReport generateMockEventReport(String attributionDestination, int id) {
    return new EventReport.Builder()
        .setId(String.valueOf(id))
        .setAttributionDestinations(List.of(URI.create(attributionDestination)))
        .build();
  }

  private void assertAggregateReportCount(
      List<String> attributionDestinations,
      EventSurfaceType destinationType,
      List<Integer> expectedCounts,
      IMeasurementDAO measurementDao) {
    IntStream.range(0, attributionDestinations.size())
        .forEach(
            i ->
                Assert.assertEquals(
                    (int) expectedCounts.get(i),
                    measurementDao.getNumAggregateReportsPerDestination(
                        URI.create(attributionDestinations.get(i)), destinationType)));
  }

  private void assertEventReportCount(
      List<String> attributionDestinations,
      EventSurfaceType destinationType,
      List<Integer> expectedCounts,
      IMeasurementDAO dao) {
    IntStream.range(0, attributionDestinations.size())
        .forEach(
            i ->
                Assert.assertEquals(
                    (int) expectedCounts.get(i),
                    dao.getNumEventReportsPerDestination(
                        URI.create(attributionDestinations.get(i)), destinationType)));
  }

  private List<String> createAppDestinationVariants(int destinationNum) {
    return Arrays.asList(
        "android-app://subdomain.destination-" + destinationNum + ".app/abcd",
        "android-app://subdomain.destination-" + destinationNum + ".app",
        "android-app://destination-" + destinationNum + ".app/abcd",
        "android-app://destination-" + destinationNum + ".app",
        "android-app://destination-" + destinationNum + ".ap");
  }

  private List<String> createWebDestinationVariants(int destinationNum) {
    return Arrays.asList(
        "https://subdomain.destination-" + destinationNum + ".com/abcd",
        "https://subdomain.destination-" + destinationNum + ".com",
        "https://destination-" + destinationNum + ".com/abcd",
        "https://destination-" + destinationNum + ".com",
        "https://destination-" + destinationNum + ".co");
  }

  private static List<URI> getNullableUriList(List<URI> uris) {
    return uris == null ? null : uris;
  }
}
