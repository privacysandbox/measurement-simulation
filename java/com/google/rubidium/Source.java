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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.rubidium.aggregation.AggregatableAttributionSource;
import com.google.rubidium.aggregation.AggregateFilterData;
import com.google.rubidium.noising.ImpressionNoiseParams;
import com.google.rubidium.noising.ImpressionNoiseUtil;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.avro.reflect.Nullable;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/** POJO for Source. */
@DefaultCoder(AvroCoder.class)
public class Source implements Serializable {
  private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

  /** Multiplier is 1, when only one destination needs to be considered. */
  public static final int SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 1;

  /**
   * Double-folds the number of states in order to allocate half to app destination and half to web
   * destination for fake reports generation.
   */
  public static final int DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 2;

  private String mId;
  private long mEventId;
  private URI mPublisher;
  EventSurfaceType mPublisherType;
  @Nullable private URI mAppDestination;
  @Nullable private URI mWebDestination;
  private String mEnrollmentId;
  private URI mRegistrant;
  private SourceType mSourceType;
  private long mPriority;
  private Status mStatus;
  private long mEventTime;
  private long mExpiryTime;
  private List<Long> mDedupKeys;
  private AttributionMode mAttributionMode;
  private long mInstallAttributionWindow;
  private long mInstallCooldownWindow;
  @Nullable private Long mDebugKey;
  private boolean mIsInstallAttributed;
  @Nullable private String mAggregateFilterData;
  @Nullable private String mAggregateSource;
  private int mAggregateContributions;
  @Nullable private AggregatableAttributionSource mAggregatableAttributionSource;

  public enum Status {
    ACTIVE,
    IGNORED
  }

  public enum AttributionMode {
    UNASSIGNED,
    TRUTHFULLY,
    NEVER,
    FALSELY,
  }

  public enum SourceType {
    EVENT("event"),
    NAVIGATION("navigation");
    private final String mValue;

    SourceType(String value) {
      this.mValue = value;
    }

    public String getValue() {
      return mValue;
    }
  }

  private Source() {
    mDedupKeys = new ArrayList<>();
    mStatus = Status.ACTIVE;
    mSourceType = SourceType.EVENT;
    // Making this default explicit since it anyway would occur on an uninitialised int field.
    mPublisherType = EventSurfaceType.APP;
    mAttributionMode = AttributionMode.UNASSIGNED;
    mIsInstallAttributed = false;
  }

  /** Class for storing fake report data. */
  public static class FakeReport {
    private final long mTriggerData;
    private final long mReportingTime;
    private final URI mDestination;

    public FakeReport(long triggerData, long reportingTime, URI destination) {
      this.mTriggerData = triggerData;
      this.mReportingTime = reportingTime;
      this.mDestination = destination;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FakeReport)) return false;
      FakeReport that = (FakeReport) o;
      return mTriggerData == that.mTriggerData
          && mReportingTime == that.mReportingTime
          && Objects.equals(mDestination, that.mDestination);
    }

    @Override
    public int hashCode() {
      return Objects.hash(mTriggerData, mReportingTime, mDestination);
    }

    public long getReportingTime() {
      return mReportingTime;
    }

    public long getTriggerData() {
      return mTriggerData;
    }

    public URI getDestination() {
      return mDestination;
    }
  }

  ImpressionNoiseParams getImpressionNoiseParams() {
    int destinationMultiplier =
        (mAppDestination != null && mWebDestination != null)
            ? DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER
            : SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER;
    return new ImpressionNoiseParams(
        getMaxReportCountInternal(isInstallDetectionEnabled()),
        getTriggerDataCardinality(),
        getReportingWindowCountForNoising(),
        destinationMultiplier);
  }

  private ImmutableList<Long> getEarlyReportingWindows(boolean installState) {
    long[] earlyWindows;
    if (installState) {
      earlyWindows =
          mSourceType == SourceType.EVENT
              ? PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
              : PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
    } else {
      earlyWindows =
          mSourceType == SourceType.EVENT
              ? PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
              : PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
    }
    List<Long> windowList = new ArrayList<>();
    for (long windowDelta : earlyWindows) {
      long window = mEventTime + windowDelta;
      if (mExpiryTime <= window) {
        continue;
      }
      windowList.add(window);
    }
    return ImmutableList.copyOf(windowList);
  }

  /**
   * Return reporting time by index for noising based on the index
   *
   * @param windowIndex index of the reporting window for which
   * @return reporting time in milliseconds
   */
  public long getReportingTimeForNoising(int windowIndex) {
    List<Long> windowList = getEarlyReportingWindows(isInstallDetectionEnabled());
    return windowIndex < windowList.size()
        ? windowList.get(windowIndex) + ONE_HOUR_IN_MILLIS
        : mExpiryTime + ONE_HOUR_IN_MILLIS;
  }

  @VisibleForTesting
  int getReportingWindowCountForNoising() {
    // Early Count + expiry
    return getEarlyReportingWindows(isInstallDetectionEnabled()).size() + 1;
  }

  /**
   * Range of trigger metadata: [0, cardinality).
   *
   * @return Cardinality of {@link Trigger} metadata
   */
  public int getTriggerDataCardinality() {
    return mSourceType == SourceType.EVENT
        ? PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY
        : PrivacyParams.NAVIGATION_TRIGGER_DATA_CARDINALITY;
  }

  /**
   * Max reports count based on conversion destination type and installation state.
   *
   * @param destinationType conversion destination type
   * @return maximum number of reports allowed
   */
  public int getMaxReportCount(EventSurfaceType destinationType) {
    boolean isInstallCase = destinationType == EventSurfaceType.APP && mIsInstallAttributed;
    return getMaxReportCountInternal(isInstallCase);
  }

  private int getMaxReportCountInternal(boolean isInstallCase) {
    if (isInstallCase) {
      return mSourceType == SourceType.EVENT
          ? PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
          : PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
    }
    return mSourceType == SourceType.EVENT
        ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS
        : PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
  }

  /**
   * @return Probability of selecting random state for attribution
   */
  public double getRandomAttributionProbability() {
    // Both destinations are set and install attribution is supported
    if (mWebDestination != null && isInstallDetectionEnabled()) {
      return mSourceType == SourceType.EVENT
          ? PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
          : PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
    }
    // Both destinations are set but install attribution isn't supported
    if (mAppDestination != null && mWebDestination != null) {
      return mSourceType == SourceType.EVENT
          ? PrivacyParams.DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
          : PrivacyParams.DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
    }
    // App destination is set and install attribution is supported
    if (isInstallDetectionEnabled()) {
      return mSourceType == SourceType.EVENT
          ? PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY
          : PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY;
    }
    // One of the destinations is available without install attribution support
    return mSourceType == SourceType.EVENT
        ? PrivacyParams.EVENT_NOISE_PROBABILITY
        : PrivacyParams.NAVIGATION_NOISE_PROBABILITY;
  }

  private boolean isInstallDetectionEnabled() {
    return mInstallCooldownWindow > 0 && mAppDestination != null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Source)) {
      return false;
    }
    Source source = (Source) obj;
    return Objects.equals(mPublisher, source.mPublisher)
        && mPublisherType == source.mPublisherType
        && Objects.equals(mAppDestination, source.mAppDestination)
        && Objects.equals(mWebDestination, source.mWebDestination)
        && Objects.equals(mEnrollmentId, source.mEnrollmentId)
        && mPriority == source.mPriority
        && mStatus == source.mStatus
        && mExpiryTime == source.mExpiryTime
        && mEventTime == source.mEventTime
        && mEventId == source.mEventId
        && Objects.equals(mDebugKey, source.mDebugKey)
        && mSourceType == source.mSourceType
        && Objects.equals(mDedupKeys, source.mDedupKeys)
        && Objects.equals(mRegistrant, source.mRegistrant)
        && mAttributionMode == source.mAttributionMode
        && Objects.equals(mAggregateFilterData, source.mAggregateFilterData)
        && Objects.equals(mAggregateSource, source.mAggregateSource)
        && mAggregateContributions == source.mAggregateContributions
        && Objects.equals(mAggregatableAttributionSource, source.mAggregatableAttributionSource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mId,
        mPublisher,
        mPublisherType,
        mAppDestination,
        mWebDestination,
        mEnrollmentId,
        mPriority,
        mStatus,
        mExpiryTime,
        mEventTime,
        mEventId,
        mSourceType,
        mDedupKeys,
        mAggregateFilterData,
        mAggregateSource,
        mAggregateContributions,
        mAggregatableAttributionSource,
        mDebugKey);
  }

  /**
   * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
   * trigger destination type.
   *
   * @return the reporting time
   */
  public long getReportingTime(long triggerTime, EventSurfaceType destinationType) {
    if (triggerTime < mEventTime) {
      return -1;
    }
    // Cases where source could have both web and app destinations, there if the trigger
    // destination is an app and it was installed, then installState should be considered true.
    boolean isAppInstalled = destinationType == EventSurfaceType.APP && mIsInstallAttributed;
    List<Long> reportingWindows = getEarlyReportingWindows(isAppInstalled);
    for (Long window : reportingWindows) {
      if (triggerTime < window) {
        return window + ONE_HOUR_IN_MILLIS;
      }
    }
    return mExpiryTime + ONE_HOUR_IN_MILLIS;
  }

  @VisibleForTesting
  void setAttributionMode(AttributionMode attributionMode) {
    mAttributionMode = attributionMode;
  }

  /**
   * Assign attribution mode based on random rate and generate fake reports if needed. Should only
   * be called for a new Source.
   *
   * @return fake reports to be stored in the datastore.
   */
  public List<FakeReport> assignAttributionModeAndGenerateFakeReports() {
    Random rand = new Random();
    double value = rand.nextDouble();
    if (value > getRandomAttributionProbability()) {
      mAttributionMode = AttributionMode.TRUTHFULLY;
      return Collections.emptyList();
    }
    List<FakeReport> fakeReports;
    if (isVtcDualDestinationModeWithPostInstallEnabled()) {
      // Source is 'EVENT' type, both app and web destination are set and install exclusivity
      // window is provided. Pick one of the static reporting states randomly.
      fakeReports = generateVtcDualDestinationPostInstallFakeReports();
    } else {
      // There will at least be one (app or web) destination available
      ImpressionNoiseParams noiseParams = getImpressionNoiseParams();
      fakeReports =
          ImpressionNoiseUtil.selectRandomStateAndGenerateReportConfigs(noiseParams, rand).stream()
              .map(
                  reportConfig ->
                      new FakeReport(
                          reportConfig[0],
                          getReportingTimeForNoising(reportConfig[1]),
                          resolveFakeReportDestination(reportConfig[2])))
              .collect(Collectors.toList());
    }
    mAttributionMode = fakeReports.isEmpty() ? AttributionMode.NEVER : AttributionMode.FALSELY;
    return fakeReports;
  }

  /** Unique identifier for the {@link Source}. */
  public String getId() {
    return mId;
  }

  /** Identifier provided by the registrant. */
  public long getEventId() {
    return mEventId;
  }

  /** Priority of the {@link Source}. */
  public long getPriority() {
    return mPriority;
  }

  /** AdTech enrollment ID */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** String which registered the {@link Source}. */
  public URI getPublisher() {
    return mPublisher;
  }

  /** The publisher type (e.g., app or web) {@link Source}. */
  public EventSurfaceType getPublisherType() {
    return mPublisherType;
  }

  /** String for the {@link Trigger}'s app destination. */
  @Nullable
  public URI getAppDestination() {
    return mAppDestination;
  }

  /** String for the {@link Trigger}'s web destination. */
  @Nullable
  public URI getWebDestination() {
    return mWebDestination;
  }

  /** Type of {@link Source}. Values: Event, Navigation. */
  public SourceType getSourceType() {
    return mSourceType;
  }

  /** Time when {@link Source} will expiry. */
  public long getExpiryTime() {
    return mExpiryTime;
  }

  /** Debug key of {@link Source}. */
  public @Nullable Long getDebugKey() {
    return mDebugKey;
  }

  /** Time the event occurred. */
  public long getEventTime() {
    return mEventTime;
  }

  /** List of dedup keys for the attributed {@link Trigger}. */
  public List<Long> getDedupKeys() {
    return mDedupKeys;
  }

  /** Set list of dedup keys for the attributed {@link Trigger}. */
  public void setDedupKeys(List<Long> dedupKeys) {
    mDedupKeys = dedupKeys;
  }

  /** Current status of the {@link Source}. */
  public Status getStatus() {
    return mStatus;
  }

  /** Registrant of this source, primarily an App. */
  public URI getRegistrant() {
    return mRegistrant;
  }

  /** Selected mode for attribution. Values: Truthfully, Never, Falsely. */
  public AttributionMode getAttributionMode() {
    return mAttributionMode;
  }

  /** Attribution window for install events. */
  public long getInstallAttributionWindow() {
    return mInstallAttributionWindow;
  }

  /** Cooldown for attributing post-install {@link Trigger} events. */
  public long getInstallCooldownWindow() {
    return mInstallCooldownWindow;
  }

  /** Is an App-install attributed to the {@link Source}. */
  public boolean isInstallAttributed() {
    return mIsInstallAttributed;
  }

  /**
   * Returns aggregate filter data string used for aggregation. aggregate filter data json is a
   * JSONObject in Attribution-Reporting-Register-Source header. Example:
   * Attribution-Reporting-Register-Source: { // some other fields. "filter_data" : {
   * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "2345"], "ctid": ["id"],
   * ...... } }
   */
  public String getAggregateFilterData() {
    return mAggregateFilterData;
  }

  /**
   * Returns aggregate source string used for aggregation. aggregate source json is a JSONArray.
   * Example: [{ // Generates a "0x159" key piece (low order bits of the key) named //
   * "campaignCounts" "id": "campaignCounts", "key_piece": "0x159", // User saw ad from campaign 345
   * (out of 511) }, { // Generates a "0x5" key piece (low order bits of the key) named "geoValue"
   * "id": "geoValue", // Source-side geo region = 5 (US), out of a possible ~100 regions.
   * "key_piece": "0x5", }]
   */
  public String getAggregateSource() {
    return mAggregateSource;
  }

  /** Returns the current sum of values the source contributed to aggregatable reports. */
  public int getAggregateContributions() {
    return mAggregateContributions;
  }

  /**
   * Returns the AggregatableAttributionSource object, which is constructed using the aggregate
   * source string and aggregate filter data string in Source.
   */
  public AggregatableAttributionSource getAggregatableAttributionSource() {
    return mAggregatableAttributionSource;
  }

  /** Set app install attribution to the {@link Source}. */
  public void setInstallAttributed(boolean isInstallAttributed) {
    mIsInstallAttributed = isInstallAttributed;
  }

  /** Set the status. */
  public void setStatus(Status status) {
    mStatus = status;
  }

  /** Set the aggregate contributions value. */
  public void setAggregateContributions(int aggregateContributions) {
    mAggregateContributions = aggregateContributions;
  }

  /**
   * Generates AggregatableAttributionSource from aggregate source string and aggregate filter data
   * string in Source.
   */
  public Optional<AggregatableAttributionSource> parseAggregateSource()
      throws ParseException, NumberFormatException {
    if (this.mAggregateSource == null) {
      return Optional.empty();
    }
    JSONParser parser = new JSONParser();
    JSONArray jsonArray = (JSONArray) parser.parse(this.mAggregateSource);
    Map<String, BigInteger> aggregateSourceMap = new HashMap<>();
    for (int i = 0; i < jsonArray.size(); i++) {
      JSONObject jsonObject = (JSONObject) jsonArray.get(i);
      String id = (String) jsonObject.get("id");
      String hexString = (String) jsonObject.get("key_piece");
      if (hexString.startsWith("0x")) {
        hexString = hexString.substring(2);
      }
      BigInteger bigInteger = new BigInteger(hexString, 16);
      aggregateSourceMap.put(id, bigInteger);
    }
    return Optional.of(
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregateSourceMap)
            .setAggregateFilterData(
                new AggregateFilterData.Builder()
                    .buildAggregateFilterData((JSONObject) parser.parse(this.mAggregateFilterData))
                    .build())
            .build());
  }

  private List<FakeReport> generateVtcDualDestinationPostInstallFakeReports() {
    int[][][] fakeReportsConfig =
        ImpressionNoiseUtil.DUAL_DESTINATION_POST_INSTALL_FAKE_REPORT_CONFIG;
    int randomIndex = new Random().nextInt(fakeReportsConfig.length);
    int[][] reportsConfig = fakeReportsConfig[randomIndex];
    return Arrays.stream(reportsConfig)
        .map(
            reportConfig ->
                new FakeReport(
                    reportConfig[0],
                    getReportingTimeForNoising(reportConfig[1]),
                    resolveFakeReportDestination(reportConfig[2])))
        .collect(Collectors.toList());
  }

  private boolean isVtcDualDestinationModeWithPostInstallEnabled() {
    return mSourceType == SourceType.EVENT
        && mWebDestination != null
        && isInstallDetectionEnabled();
  }

  /**
   * Either both app and web destinations can be available or one of them will be available. When
   * both destinations are available, we double the number of states at noise generation to be able
   * to randomly choose one of them for fake report creation. We don't add the multiplier when only
   * one of them is available. In that case, choose the one that's non-null.
   *
   * @param destinationIdentifier destination identifier, can be 0 (app) or 1 (web)
   * @return app or web destination {@link String}
   */
  private URI resolveFakeReportDestination(int destinationIdentifier) {
    if (mAppDestination != null && mWebDestination != null) {
      // It could be a direct destinationIdentifier == 0 check, but
      return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
          ? mAppDestination
          : mWebDestination;
    }
    return mAppDestination != null ? mAppDestination : mWebDestination;
  }

  /** Builder for {@link Source}. */
  public static final class Builder {
    private final Source mBuilding;

    public Builder() {
      mBuilding = new Source();
    }

    /** See {@link Source#getId()}. */
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link Source#getEventId()}. */
    public Builder setEventId(long eventId) {
      mBuilding.mEventId = eventId;
      return this;
    }

    /** See {@link Source#getPublisher()}. */
    public Builder setPublisher(URI publisher) {
      mBuilding.mPublisher = publisher;
      return this;
    }

    /** See {@link Source#getPublisherType()}. */
    public Builder setPublisherType(EventSurfaceType publisherType) {
      mBuilding.mPublisherType = publisherType;
      return this;
    }

    /** See {@link Source#getAppDestination()}. */
    public Builder setAppDestination(URI appDestination) {
      mBuilding.mAppDestination = appDestination;
      return this;
    }

    /** See {@link Source#getWebDestination()}. */
    public Builder setWebDestination(URI webDestination) {
      mBuilding.mWebDestination = webDestination;
      return this;
    }

    /** See {@link Source#getEnrollmentId()} ()}. */
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link Source#getEventId()}. */
    public Builder setEventTime(long eventTime) {
      mBuilding.mEventTime = eventTime;
      return this;
    }

    /** See {@link Source#getExpiryTime()}. */
    public Builder setExpiryTime(long expiryTime) {
      mBuilding.mExpiryTime = expiryTime;
      return this;
    }

    /** See {@link Source#getPriority()}. */
    public Builder setPriority(long priority) {
      mBuilding.mPriority = priority;
      return this;
    }

    /** See {@link Source#getDebugKey()} ()}. */
    public Builder setDebugKey(Long debugKey) {
      mBuilding.mDebugKey = debugKey;
      return this;
    }

    /** See {@link Source#getSourceType()}. */
    public Builder setSourceType(SourceType sourceType) {
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link Source#getDedupKeys()}. */
    public Builder setDedupKeys(List<Long> dedupKeys) {
      mBuilding.mDedupKeys = dedupKeys;
      return this;
    }

    /** See {@link Source#getStatus()}. */
    public Builder setStatus(Status status) {
      mBuilding.mStatus = status;
      return this;
    }

    /** See {@link Source#getRegistrant()} */
    public Builder setRegistrant(URI registrant) {
      mBuilding.mRegistrant = registrant;
      return this;
    }

    /** See {@link Source#getAttributionMode()} */
    public Builder setAttributionMode(AttributionMode attributionMode) {
      mBuilding.mAttributionMode = attributionMode;
      return this;
    }

    /** See {@link Source#getInstallAttributionWindow()} */
    public Builder setInstallAttributionWindow(long installAttributionWindow) {
      mBuilding.mInstallAttributionWindow = installAttributionWindow;
      return this;
    }

    /** See {@link Source#getInstallCooldownWindow()} */
    public Builder setInstallCooldownWindow(long installCooldownWindow) {
      mBuilding.mInstallCooldownWindow = installCooldownWindow;
      return this;
    }

    /** See {@link Source#isInstallAttributed()} */
    public Builder setInstallAttributed(boolean installAttributed) {
      mBuilding.mIsInstallAttributed = installAttributed;
      return this;
    }

    /** See {@link Source#getAggregateFilterData()}. */
    public Builder setAggregateFilterData(String aggregateFilterData) {
      mBuilding.mAggregateFilterData = aggregateFilterData;
      return this;
    }

    /** See {@link Source#getAggregateSource()} */
    public Builder setAggregateSource(String aggregateSource) {
      mBuilding.mAggregateSource = aggregateSource;
      return this;
    }

    /** See {@link Source#getAggregateContributions()} */
    public Builder setAggregateContributions(int aggregateContributions) {
      mBuilding.mAggregateContributions = aggregateContributions;
      return this;
    }

    /** See {@link Source#getAggregatableAttributionSource()} */
    public Builder setAggregatableAttributionSource(
        AggregatableAttributionSource aggregatableAttributionSource) {
      mBuilding.mAggregatableAttributionSource = aggregatableAttributionSource;
      return this;
    }

    /** Build the {@link Source}. */
    public Source build() {
      if (mBuilding.mAppDestination == null && mBuilding.mWebDestination == null) {
        throw new IllegalArgumentException("At least one destination is required");
      }
      return mBuilding;
    }
  }
}
