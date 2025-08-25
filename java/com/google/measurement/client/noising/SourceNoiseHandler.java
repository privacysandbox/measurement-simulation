/*
 * Copyright (C) 2024 Google LLC
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

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Uri;
import com.google.measurement.client.Pair;

import com.google.measurement.client.Flags;
import com.google.measurement.client.Source;
import com.google.measurement.client.TriggerSpecs;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Generates noised reports for the provided source. */
public class SourceNoiseHandler {
  private static final int PROBABILITY_DECIMAL_POINTS_LIMIT = 7;

  private final Flags mFlags;
  private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
  private final ImpressionNoiseUtil mImpressionNoiseUtil;

  public SourceNoiseHandler(@NonNull Flags flags) {
    mFlags = flags;
    mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(flags);
    mImpressionNoiseUtil = new ImpressionNoiseUtil();
  }

  @VisibleForTesting
  public SourceNoiseHandler(
      @NonNull Flags flags,
      @NonNull EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      @NonNull ImpressionNoiseUtil impressionNoiseUtil) {
    mFlags = flags;
    mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    mImpressionNoiseUtil = impressionNoiseUtil;
  }

  /** Multiplier is 1, when only one destination needs to be considered. */
  public static final int SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 1;

  /**
   * Double-folds the number of states in order to allocate half to app destination and half to web
   * destination for fake reports generation.
   */
  public static final int DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 2;

  /**
   * Assign attribution mode based on random rate and generate fake reports if needed. Should only
   * be called for a new Source.
   *
   * @return fake reports to be stored in the datastore.
   */
  public List<Source.FakeReport> assignAttributionModeAndGenerateFakeReports(
      @NonNull Source source) {
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    double value = getRandomDouble(rand);
    if (value >= getRandomizedSourceResponsePickRate(source)) {
      source.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
      return null;
    }

    List<Source.FakeReport> fakeReports = new ArrayList<>();
    TriggerSpecs triggerSpecs = source.getTriggerSpecs();

    if (triggerSpecs == null) {
      // There will at least be one (app or web) destination available
      ImpressionNoiseParams noiseParams = getImpressionNoiseParams(source);
      fakeReports =
          mImpressionNoiseUtil.selectRandomStateAndGenerateReportConfigs(noiseParams, rand).stream()
              .map(
                  reportConfig -> {
                    long triggerTime = source.getEventTime();
                    long reportingTime =
                        mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                            source, reportConfig[1]);
                    if (mFlags.getMeasurementEnableFakeReportTriggerTime()) {
                      Pair<Long, Long> reportingAndTriggerTime =
                          mEventReportWindowCalcDelegate.getReportingAndTriggerTimeForNoising(
                              source, reportConfig[1]);
                      triggerTime = reportingAndTriggerTime.first;
                      reportingTime = reportingAndTriggerTime.second;
                    }
                    return new Source.FakeReport(
                        new UnsignedLong(Long.valueOf(reportConfig[0])),
                        reportingTime,
                        triggerTime,
                        resolveFakeReportDestinations(source, reportConfig[2]),
                        /* triggerSummaryBucket= */ null);
                  })
              .collect(Collectors.toList());
    } else {
      int destinationTypeMultiplier = source.getDestinationTypeMultiplier(mFlags);
      List<int[]> fakeReportConfigs =
          mImpressionNoiseUtil.selectFlexEventReportRandomStateAndGenerateReportConfigs(
              triggerSpecs, destinationTypeMultiplier, rand);

      // Group configurations by trigger data, ordered by window index.
      fakeReportConfigs.sort(
          (config1, config2) -> {
            UnsignedLong triggerData1 = triggerSpecs.getTriggerDataFromIndex(config1[0]);
            UnsignedLong triggerData2 = triggerSpecs.getTriggerDataFromIndex(config2[0]);

            if (triggerData1.equals(triggerData2)) {
              return Integer.valueOf(config1[1]).compareTo(Integer.valueOf(config2[1]));
            }

            return triggerData1.compareTo(triggerData2);
          });

      int bucketIndex = -1;
      UnsignedLong currentTriggerData = null;
      List<Long> buckets = new ArrayList<>();

      for (int[] reportConfig : fakeReportConfigs) {
        UnsignedLong triggerData = triggerSpecs.getTriggerDataFromIndex(reportConfig[0]);

        // A new group of trigger data ordered by report index.
        if (!triggerData.equals(currentTriggerData)) {
          buckets = triggerSpecs.getSummaryBucketsForTriggerData(triggerData);
          bucketIndex = 0;
          currentTriggerData = triggerData;
          // The same trigger data, the next report ordered by window index will have the next
          // trigger summary bucket.
        } else {
          bucketIndex += 1;
        }

        Pair<Long, Long> triggerSummaryBucket =
            TriggerSpecs.getSummaryBucketFromIndex(bucketIndex, buckets);
        long triggerTime = source.getEventTime();
        long reportingTime =
            mEventReportWindowCalcDelegate.getReportingTimeForNoisingFlexEventApi(
                reportConfig[1], reportConfig[0], source);

        if (mFlags.getMeasurementEnableFakeReportTriggerTime()) {
          Pair<Long, Long> reportingAndTriggerTime =
              mEventReportWindowCalcDelegate.getReportingAndTriggerTimeForNoisingFlexEventApi(
                  reportConfig[1], reportConfig[0], source);
          triggerTime = reportingAndTriggerTime.first;
          reportingTime = reportingAndTriggerTime.second;
        }

        fakeReports.add(
            new Source.FakeReport(
                currentTriggerData,
                reportingTime,
                triggerTime,
                resolveFakeReportDestinations(source, reportConfig[2]),
                triggerSummaryBucket));
      }
    }
    @Source.AttributionMode
    int attributionMode =
        fakeReports.isEmpty() ? Source.AttributionMode.NEVER : Source.AttributionMode.FALSELY;
    source.setAttributionMode(attributionMode);
    return fakeReports;
  }

  @VisibleForTesting
  public double getRandomizedSourceResponsePickRate(Source source) {
    // Methods on Source and EventReportWindowCalcDelegate that calculate flip probability for
    // the source rely on reporting windows and max reports that are obtained with consideration
    // to install-state and its interaction with configurable report windows and configurable
    // max reports.
    return source.getFlipProbability(mFlags);
  }

  /**
   * @return Probability of selecting random state for attribution
   */
  public double getRandomizedTriggerRate(@NonNull Source source) {
    return convertToDoubleAndLimitDecimal(getRandomizedSourceResponsePickRate(source));
  }

  private double convertToDoubleAndLimitDecimal(double probability) {
    return BigDecimal.valueOf(probability)
        .setScale(PROBABILITY_DECIMAL_POINTS_LIMIT, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /**
   * Either both app and web destinations can be available or one of them will be available. When
   * both destinations are available, we double the number of states at noise generation to be able
   * to randomly choose one of them for fake report creation. We don't add the multiplier when only
   * one of them is available. In that case, choose the one that's non-null.
   *
   * @param destinationIdentifier destination identifier, can be 0 (app) or 1 (web)
   * @return app or web destination {@link Uri}
   */
  private List<Uri> resolveFakeReportDestinations(Source source, int destinationIdentifier) {
    if (source.shouldReportCoarseDestinations(mFlags)) {
      ImmutableList.Builder<Uri> destinations = new ImmutableList.Builder<>();
      Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
      Optional.ofNullable(source.getWebDestinations()).ifPresent(destinations::addAll);
      return destinations.build();
    }

    if (source.hasAppDestinations() && source.hasWebDestinations()) {
      return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
          ? source.getAppDestinations()
          : source.getWebDestinations();
    }

    return source.hasAppDestinations() ? source.getAppDestinations() : source.getWebDestinations();
  }

  @VisibleForTesting
  ImpressionNoiseParams getImpressionNoiseParams(Source source) {
    int destinationTypeMultiplier = source.getDestinationTypeMultiplier(mFlags);
    return new ImpressionNoiseParams(
        mEventReportWindowCalcDelegate.getMaxReportCount(source),
        source.getTriggerDataCardinality(),
        mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(source),
        destinationTypeMultiplier);
  }

  /** Return a random double */
  @VisibleForTesting
  public double getRandomDouble(ThreadLocalRandom rand) {
    return rand.nextDouble();
  }
}
