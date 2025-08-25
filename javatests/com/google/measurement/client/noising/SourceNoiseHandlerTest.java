/*
 * Copyright (C) 2023 Google LLC
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

package com.google.measurement.client.noising;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.measurement.client.Uri;
import com.google.measurement.client.Flags;
import com.google.measurement.client.PrivacyParams;
import com.google.measurement.client.Source;
import com.google.measurement.client.SourceFixture;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SourceNoiseHandlerTest {

  private static final String EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START =
      "{'start_time': 86400000, 'end_times': [172800000, 432000000, 604800000, 864000000,"
          + " 1728000000]}";

  private Flags mFlags;
  private SourceNoiseHandler mSourceNoiseHandler;
  private EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

  @Before
  public void setup() {
    mFlags = mock(Flags.class);
    doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
        .when(mFlags)
        .getMeasurementVtcConfigurableMaxEventReportsCount();
    doReturn(Flags.MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS)
        .when(mFlags)
        .getMeasurementEventReportsVtcEarlyReportingWindows();
    doReturn(Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS)
        .when(mFlags)
        .getMeasurementEventReportsCtcEarlyReportingWindows();
    doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
        .when(mFlags)
        .getMeasurementMaxReportStatesPerSourceRegistration();
    doReturn(Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON).when(mFlags).getMeasurementPrivacyEpsilon();
    mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(mFlags);
    mSourceNoiseHandler =
        spy(
            new SourceNoiseHandler(
                mFlags, mEventReportWindowCalcDelegate, new ImpressionNoiseUtil()));
  }

  @Test
  public void fakeReports_flexEventReport_generatesFromStaticReportStates() {
    Source source = SourceFixture.getValidSourceWithFlexEventReportWithFewerState();
    // Force increase the probability of random attribution.
    doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int falseCount = 0;
    int neverCount = 0;
    int truthCount = 0;
    int triggerTimeNotEqualSourceTimeCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        falseCount++;
        assertNotEquals(0, fakeReports.size());
        for (Source.FakeReport fakeReport : fakeReports) {
          if (fakeReport.getTriggerTime() != source.getEventTime()) {
            triggerTimeNotEqualSourceTimeCount++;
          }
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
    assertEquals(0, triggerTimeNotEqualSourceTimeCount);
  }

  @Test
  public void fakeReports_flexEventReport_setsTriggerTime() throws JSONException {
    doReturn(true).when(mFlags).getMeasurementEnableFakeReportTriggerTime();
    TriggerSpecs triggerSpecs =
        SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
    long baseTime = System.currentTimeMillis();
    Source source =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(baseTime)
            .setTriggerSpecs(triggerSpecs)
            .build();
    // Force increase the probability of random attribution.
    doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int falseCount = 0;
    int neverCount = 0;
    int truthCount = 0;
    int triggerTimeGreaterThanSourceTimeCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        falseCount++;
        assertNotEquals(0, fakeReports.size());
        for (Source.FakeReport fakeReport : fakeReports) {
          if (fakeReport.getTriggerTime() > source.getEventTime()) {
            triggerTimeGreaterThanSourceTimeCount++;
          }
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
    assertNotEquals(0, triggerTimeGreaterThanSourceTimeCount);
  }

  @Test
  public void fakeReports_flexEventReport_correctlyOrdersTriggerSummaryBucket()
      throws JSONException {
    doReturn(true).when(mFlags).getMeasurementEnableFakeReportTriggerTime();
    TriggerSpecs triggerSpecs =
        SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
    long baseTime = System.currentTimeMillis();
    Source source =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(baseTime)
            .setTriggerSpecs(triggerSpecs)
            .build();
    // Force increase the probability of random attribution.
    doReturn(1D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int orderedSummaryBucketsCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        assertNotEquals(0, fakeReports.size());

        // Group reports by trigger data, then sort by summary bucket.
        fakeReports.sort(
            Comparator.comparing(Source.FakeReport::getTriggerData)
                .thenComparing(fakeReport -> fakeReport.getTriggerSummaryBucket().first));

        for (int j = 1; j < fakeReports.size(); j++) {
          Source.FakeReport prev = fakeReports.get(j - 1);
          Source.FakeReport curr = fakeReports.get(j);

          // Assert summary buckets are sequential and report time is ordered for each
          // group of trigger data.
          if (prev.getTriggerData().equals(curr.getTriggerData())) {
            assertEquals(
                Long.valueOf(prev.getTriggerSummaryBucket().second + 1L),
                curr.getTriggerSummaryBucket().first);
            assertTrue(prev.getReportingTime() <= curr.getReportingTime());
            orderedSummaryBucketsCount += 1;
          }
        }
      }
    }
    assertNotEquals(0, orderedSummaryBucketsCount);
  }

  @Test
  public void fakeReports_flexEventReport_ordersTriggerSummaryBucket_attributionScopeOff()
      throws JSONException {
    doReturn(false).when(mFlags).getMeasurementEnableFakeReportTriggerTime();
    TriggerSpecs triggerSpecs =
        SourceFixture.getValidTriggerSpecsValueSumWithStartTime(TimeUnit.HOURS.toMillis(5));
    long baseTime = System.currentTimeMillis();
    Source source =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
            .setEventTime(baseTime)
            .setTriggerSpecs(triggerSpecs)
            .build();
    // Force increase the probability of random attribution.
    doReturn(1D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int orderedSummaryBucketsCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        assertNotEquals(0, fakeReports.size());

        // Group reports by trigger data, then sort by summary bucket.
        fakeReports.sort(
            Comparator.comparing(Source.FakeReport::getTriggerData)
                .thenComparing(fakeReport -> fakeReport.getTriggerSummaryBucket().first));

        for (int j = 1; j < fakeReports.size(); j++) {
          Source.FakeReport prev = fakeReports.get(j - 1);
          Source.FakeReport curr = fakeReports.get(j);

          // Assert summary buckets are sequential and report time is ordered for each
          // group of trigger data.
          if (prev.getTriggerData().equals(curr.getTriggerData())) {
            assertEquals(
                Long.valueOf(prev.getTriggerSummaryBucket().second + 1L),
                curr.getTriggerSummaryBucket().first);
            assertTrue(prev.getReportingTime() <= curr.getReportingTime());
            orderedSummaryBucketsCount += 1;
          }
        }
      }
    }
    assertNotEquals(0, orderedSummaryBucketsCount);
  }

  @Test
  public void fakeReports_flexLiteEventReport_setsTriggerTime() {
    doReturn(true).when(mFlags).getMeasurementEnableFakeReportTriggerTime();
    long baseTime = System.currentTimeMillis();
    Source source =
        SourceFixture.getMinimalValidSourceWithAttributionScope()
            .setEventReportWindows(EVENT_REPORT_WINDOWS_5_WINDOWS_WITH_START)
            .setExpiryTime(baseTime + TimeUnit.DAYS.toMillis(30))
            .setEventTime(baseTime)
            .build();
    // Force increase the probability of random attribution.
    doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int falseCount = 0;
    int neverCount = 0;
    int truthCount = 0;
    int triggerTimeGreaterThanSourceTimeCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        falseCount++;
        assertNotEquals(0, fakeReports.size());
        for (Source.FakeReport fakeReport : fakeReports) {
          if (fakeReport.getTriggerTime() > source.getEventTime()) {
            triggerTimeGreaterThanSourceTimeCount++;
          }
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
    assertNotEquals(0, triggerTimeGreaterThanSourceTimeCount);
  }

  @Test
  public void impressionNoiseParamGeneration() {
    long eventTime = System.currentTimeMillis();
    Source eventSource30dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource30dExpiry));

    Source eventSource7dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource7dExpiry));

    Source eventSource2dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 1,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource2dExpiry));

    Source navigationSource30dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 3,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource30dExpiry));

    Source navigationSource7dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource7dExpiry));

    Source navigationSource2dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2dExpiry));
  }

  @Test
  public void impressionNoiseParamGeneration_flexLiteAPI() {
    long eventTime = System.currentTimeMillis();
    Source eventSource2Windows =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .setMaxEventLevelReports(2)
            .setEventReportWindows("{ 'end_times': [3600, 7200]}")
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource2Windows));

    Source eventSource2Windows2Destinations =
        SourceFixture.getMinimalValidSourceBuilder()
            .setWebDestinations(List.of(Uri.parse("https://example.test")))
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .setMaxEventLevelReports(2)
            .setEventReportWindows("{ 'end_times': [3600, 7200]}")
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 2),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource2Windows2Destinations));

    Source eventSource1Window =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventTime(eventTime)
            .setMaxEventLevelReports(2)
            .setEventReportWindows("{'end_times': [3600]}")
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource1Window));

    Source navigationSource3Windows =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .setMaxEventLevelReports(3)
            .setEventReportWindows("{'end_times': [3600, 7200, 86400]}")
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 3,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource3Windows));

    Source navigationSource2Window =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setEventTime(eventTime)
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
            .setMaxEventLevelReports(3)
            .setEventReportWindows("{'end_times': [3600, 7200]}")
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2Window));
  }

  @Test
  public void impressionNoiseParamGeneration_withInstallAttribution() {
    long eventTime = System.currentTimeMillis();

    Source eventSource30dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource30dExpiry));

    Source eventSource7dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource7dExpiry));

    Source eventSource2dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSource2dExpiry));

    Source navigationSource30dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 3,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource30dExpiry));

    Source navigationSource7dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource7dExpiry));

    Source navigationSource2dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.NAVIGATION)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 3,
            /* triggerDataCardinality= */ 8,
            /* reportingWindowCount= */ 1,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(navigationSource2dExpiry));
    Source eventSourceWith2Destinations30dExpiry =
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
            .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
            .setEventTime(eventTime)
            .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
            .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(30))
            .build();
    assertEquals(
        new ImpressionNoiseParams(
            /* reportCount= */ 2,
            /* triggerDataCardinality= */ 2,
            /* reportingWindowCount= */ 2,
            /* destinationMultiplier */ 1),
        mSourceNoiseHandler.getImpressionNoiseParams(eventSourceWith2Destinations30dExpiry));
  }

  @Test
  public void testGetRandomizedTriggerRateWithFlexSource() {
    // Number of states: 5
    // Epsilon: 14
    // Flip probability: (5) / ((e^14) + 5 - 1) = .0000004157629766763622
    Source source = SourceFixture.getValidSource();
    assertEquals(
        .000004157629766763622, mSourceNoiseHandler.getRandomizedSourceResponsePickRate(source), 0);
  }

  @Test
  public void testGetRandomizedTriggerRateWithFullFlexSource() {
    // Number of states: 5
    // Epsilon: 3
    // Flip probability: (5) / ((e^3) + 5 - 1) = 0.207593
    Source source = SourceFixture.getValidFullFlexSourceWithNonDefaultEpsilon();
    assertEquals(0.207593, mSourceNoiseHandler.getRandomizedSourceResponsePickRate(source), 0);
  }

  @Test
  public void testFakeReportGeneration() {
    long expiry = System.currentTimeMillis();
    // Single (App) destination, EVENT type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
            .setWebDestinations(null)
            .setEventReportWindow(expiry)
            .build(),
        PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

    // Single (App) destination, NAVIGATION type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
            .setWebDestinations(null)
            .setEventReportWindow(expiry)
            .build(),
        PrivacyParams.getNavigationTriggerDataCardinality());

    // Single (Web) destination, EVENT type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventReportWindow(expiry)
            .setAppDestinations(null)
            .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
            .build(),
        PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

    // Single (Web) destination, NAVIGATION type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventReportWindow(expiry)
            .setAppDestinations(null)
            .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
            .build(),
        PrivacyParams.getNavigationTriggerDataCardinality());

    // Both destinations set, EVENT type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventReportWindow(expiry)
            .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
            .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
            .build(),
        PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

    // Both destinations set, NAVIGATION type
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventReportWindow(expiry)
            .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
            .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
            .build(),
        PrivacyParams.getNavigationTriggerDataCardinality());

    // App destination with cooldown window
    verifyAlgorithmicFakeReportGeneration(
        SourceFixture.getMinimalValidSourceBuilder()
            .setSourceType(Source.SourceType.EVENT)
            .setEventReportWindow(expiry)
            .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
            .setWebDestinations(null)
            .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
            .build(),
        PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);
  }

  private void verifyAlgorithmicFakeReportGeneration(Source source, int expectedCardinality) {
    // Force increase the probability of random attribution.
    doReturn(0.50D).when(mSourceNoiseHandler).getRandomizedSourceResponsePickRate(source);
    int falseCount = 0;
    int neverCount = 0;
    int truthCount = 0;
    for (int i = 0; i < 500; i++) {
      List<Source.FakeReport> fakeReports =
          mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
      if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
        falseCount++;
        assertNotEquals(0, fakeReports.size());
        for (Source.FakeReport report : fakeReports) {
          assertTrue(
              source.getEventReportWindow() + TimeUnit.HOURS.toMillis(1)
                  >= report.getReportingTime());
          Long triggerData = report.getTriggerData().getValue();
          assertTrue(0 <= triggerData && triggerData < expectedCardinality);
          assertNull(report.getTriggerSummaryBucket());
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
}
