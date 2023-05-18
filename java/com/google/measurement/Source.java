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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.measurement.aggregation.AggregatableAttributionSource;
import com.google.measurement.noising.ImpressionNoiseParams;
import com.google.measurement.noising.ImpressionNoiseUtil;
import com.google.measurement.util.UnsignedLong;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.avro.reflect.Nullable;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
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
  private UnsignedLong mEventId;
  private URI mPublisher;
  private EventSurfaceType mPublisherType;
  @Nullable private List<URI> mAppDestinations;
  @Nullable private List<URI> mWebDestinations;
  private String mEnrollmentId;
  private URI mRegistrant;
  private SourceType mSourceType;
  private long mPriority;
  private Status mStatus;
  private long mEventTime;
  private long mExpiryTime;
  private long mEventReportWindow;
  private long mAggregatableReportWindow;
  private List<UnsignedLong> mAggregateReportDedupKeys;
  private List<UnsignedLong> mEventReportDedupKeys;
  private AttributionMode mAttributionMode;
  private long mInstallAttributionWindow;
  private long mInstallCooldownWindow;
  @Nullable private UnsignedLong mDebugKey;
  private boolean mIsInstallAttributed;
  private boolean mIsDebugReporting;
  @Nullable private String mFilterDataString;
  @Nullable private FilterMap mFilterData;
  @Nullable private String mAggregateSource;
  private int mAggregateContributions;
  @Nullable private Optional<AggregatableAttributionSource> mAggregatableAttributionSource;
  private boolean mAdIdPermission;
  private boolean mArDebugPermission;
  @Nullable private String mRegistrationId;
  @Nullable private String mSharedAggregationKeys;
  @Nullable private Long mInstallTime;
  @Nullable private String mParentId;
  private ApiChoice mApiChoice;

  public enum Status {
    ACTIVE,
    IGNORED,
    MARKED_TO_DELETE
  }

  public enum AttributionMode {
    UNASSIGNED,
    TRUTHFULLY,
    NEVER,
    FALSELY
  }

  public enum SourceType {
    EVENT("event"),
    NAVIGATION("navigation");
    private final String mValue;

    SourceType(String value) {
      mValue = value;
    }

    public String getValue() {
      return mValue;
    }
  }

  public Source() {
    mEventReportDedupKeys = new ArrayList<>();
    mAggregateReportDedupKeys = new ArrayList<>();
    mStatus = Status.ACTIVE;
    mSourceType = SourceType.EVENT;
    // Making this default explicit since it anyway would occur on an uninitialised int field.
    mPublisherType = EventSurfaceType.APP;
    mAttributionMode = AttributionMode.UNASSIGNED;
    mIsInstallAttributed = false;
    mIsDebugReporting = false;
  }

  /** Class for storing fake report data. */
  public static class FakeReport {
    private final UnsignedLong mTriggerData;
    private final long mReportingTime;
    private final List<URI> mDestinations;

    public FakeReport(UnsignedLong triggerData, long reportingTime, List<URI> destinations) {
      mTriggerData = triggerData;
      mReportingTime = reportingTime;
      mDestinations = destinations;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FakeReport)) return false;
      FakeReport that = (FakeReport) o;
      return Objects.equals(mTriggerData, that.mTriggerData)
          && mReportingTime == that.mReportingTime
          && Objects.equals(mDestinations, that.mDestinations);
    }

    @Override
    public int hashCode() {
      return Objects.hash(mTriggerData, mReportingTime, mDestinations);
    }

    public long getReportingTime() {
      return mReportingTime;
    }

    public UnsignedLong getTriggerData() {
      return mTriggerData;
    }

    public List<URI> getDestinations() {
      return mDestinations;
    }
  }

  ImpressionNoiseParams getImpressionNoiseParams() {
    int destinationMultiplier =
        (mAppDestinations != null && mWebDestinations != null)
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
      if (mEventReportWindow <= window) {
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
        : mEventReportWindow + ONE_HOUR_IN_MILLIS;
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
    if (mWebDestinations != null && isInstallDetectionEnabled()) {
      return mSourceType == SourceType.EVENT
          ? PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
          : PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
    }
    // Both destinations are set but install attribution isn't supported
    if (mAppDestinations != null && mWebDestinations != null) {
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
    return mInstallCooldownWindow > 0 && mAppDestinations != null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Source)) {
      return false;
    }
    Source source = (Source) obj;
    return Objects.equals(mPublisher, source.mPublisher)
        && mPublisherType == source.mPublisherType
        && areEqualNullableDestinations(mAppDestinations, source.mAppDestinations)
        && areEqualNullableDestinations(mWebDestinations, source.mWebDestinations)
        && Objects.equals(mEnrollmentId, source.mEnrollmentId)
        && mPriority == source.mPriority
        && mStatus == source.mStatus
        && mExpiryTime == source.mExpiryTime
        && mEventReportWindow == source.mEventReportWindow
        && mAggregatableReportWindow == source.mAggregatableReportWindow
        && mEventTime == source.mEventTime
        && mAdIdPermission == source.mAdIdPermission
        && mArDebugPermission == source.mArDebugPermission
        && Objects.equals(mEventId, source.mEventId)
        && Objects.equals(mDebugKey, source.mDebugKey)
        && mSourceType == source.mSourceType
        && Objects.equals(mEventReportDedupKeys, source.mEventReportDedupKeys)
        && Objects.equals(mAggregateReportDedupKeys, source.mAggregateReportDedupKeys)
        && Objects.equals(mRegistrant, source.mRegistrant)
        && mAttributionMode == source.mAttributionMode
        && mIsDebugReporting == source.mIsDebugReporting
        && Objects.equals(mFilterDataString, source.mFilterDataString)
        && Objects.equals(mAggregateSource, source.mAggregateSource)
        && mAggregateContributions == source.mAggregateContributions
        && Objects.equals(mAggregatableAttributionSource, source.mAggregatableAttributionSource)
        && Objects.equals(mRegistrationId, source.mRegistrationId)
        && Objects.equals(mSharedAggregationKeys, source.mSharedAggregationKeys)
        && Objects.equals(mParentId, source.mParentId)
        && Objects.equals(mInstallTime, source.mInstallTime)
        && mApiChoice == source.mApiChoice;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mId,
        mPublisher,
        mPublisherType,
        mAppDestinations,
        mWebDestinations,
        mEnrollmentId,
        mPriority,
        mStatus,
        mExpiryTime,
        mEventReportWindow,
        mAggregatableReportWindow,
        mEventTime,
        mEventId,
        mSourceType,
        mEventReportDedupKeys,
        mAggregateReportDedupKeys,
        mFilterDataString,
        mAggregateSource,
        mAggregateContributions,
        mAggregatableAttributionSource,
        mDebugKey,
        mAdIdPermission,
        mArDebugPermission,
        mRegistrationId,
        mSharedAggregationKeys,
        mInstallTime);
  }

  /**
   * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s event report
   * window and trigger destination type.
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
      if (triggerTime <= window) {
        return window + ONE_HOUR_IN_MILLIS;
      }
    }
    return mEventReportWindow + ONE_HOUR_IN_MILLIS;
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
                          new UnsignedLong(Long.valueOf(reportConfig[0])),
                          getReportingTimeForNoising(reportConfig[1]),
                          resolveFakeReportDestinations(reportConfig[2])))
              .collect(Collectors.toList());
    }
    mAttributionMode = fakeReports.isEmpty() ? AttributionMode.NEVER : AttributionMode.FALSELY;
    return fakeReports;
  }

  /**
   * Retrieve the attribution destinations corresponding to their destination type.
   *
   * @return a list of URIs.
   */
  public List<URI> getAttributionDestinations(EventSurfaceType destinationType) {
    return destinationType == EventSurfaceType.APP ? mAppDestinations : mWebDestinations;
  }

  /** Unique identifier for the {@link Source}. */
  public String getId() {
    return mId;
  }

  /** Identifier provided by the registrant. */
  public UnsignedLong getEventId() {
    return mEventId;
  }

  /** Priority of the {@link Source}. */
  public long getPriority() {
    return mPriority;
  }

  /** Ad Tech enrollment ID */
  public String getEnrollmentId() {
    return mEnrollmentId;
  }

  /** URI which registered the {@link Source}. */
  public URI getPublisher() {
    return mPublisher;
  }

  /** The publisher type (e.g., app or web) {@link Source}. */
  public EventSurfaceType getPublisherType() {
    return mPublisherType;
  }

  /** URIs for the {@link Trigger}'s app destinations. */
  @Nullable
  public List<URI> getAppDestinations() {
    return mAppDestinations;
  }

  /** URIs for the {@link Trigger}'s web destinations. */
  @Nullable
  public List<URI> getWebDestinations() {
    return mWebDestinations;
  }

  /** Type of {@link Source}. Values: Event, Navigation. */
  public SourceType getSourceType() {
    return mSourceType;
  }

  /** Time when {@link Source} will expire. */
  public long getExpiryTime() {
    return mExpiryTime;
  }

  /** Time when {@link Source} event report window will expire. */
  public long getEventReportWindow() {
    return mEventReportWindow;
  }

  /** Time when {@link Source} aggregate report window will expire. */
  public long getAggregatableReportWindow() {
    return mAggregatableReportWindow;
  }

  /** Debug key of {@link Source}. */
  public @Nullable UnsignedLong getDebugKey() {
    return mDebugKey;
  }

  /** Time the event occurred. */
  public long getEventTime() {
    return mEventTime;
  }

  /** Is Ad ID Permission Enabled. */
  public boolean hasAdIdPermission() {
    return mAdIdPermission;
  }

  /** Is Ar Debug Permission Enabled. */
  public boolean hasArDebugPermission() {
    return mArDebugPermission;
  }

  /** List of dedup keys for the attributed {@link Trigger}. */
  public List<UnsignedLong> getEventReportDedupKeys() {
    return mEventReportDedupKeys;
  }

  public void setEventReportDedupKeys(List<UnsignedLong> eventReportDedupKeys) {
    mEventReportDedupKeys = eventReportDedupKeys;
  }

  /** List of dedup keys used for generating Aggregate Reports. */
  public List<UnsignedLong> getAggregateReportDedupKeys() {
    return mAggregateReportDedupKeys;
  }

  public void setAggregateReportDedupKeys(List<UnsignedLong> aggregateReportDedupKeys) {
    mAggregateReportDedupKeys = aggregateReportDedupKeys;
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

  /** Is Ad Tech Opt-in to Debug Reporting {@link Source}. */
  public boolean isDebugReporting() {
    return mIsDebugReporting;
  }

  /**
   * Returns aggregate filter data string used for aggregation. aggregate filter data json is a
   * JSONObject in Attribution-Reporting-Register-Source header. Example:
   * Attribution-Reporting-Register-Source: { // some other fields. "filter_data" : {
   * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "2345"], "ctid": ["id"],
   * ...... } }
   */
  public String getFilterDataString() {
    return mFilterDataString;
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
  public Optional<AggregatableAttributionSource> getAggregatableAttributionSource()
      throws ParseException {
    if (mAggregatableAttributionSource == null) {
      if (mAggregateSource == null) {
        mAggregatableAttributionSource = Optional.empty();
        return mAggregatableAttributionSource;
      }
      JSONParser parser = new JSONParser();
      JSONObject jsonObject = (JSONObject) parser.parse(mAggregateSource);
      TreeMap<String, BigInteger> aggregateSourceMap = new TreeMap<>();
      for (Object key : jsonObject.keySet()) {
        // Remove "0x" prefix.
        String hexString = ((String) jsonObject.get((String) key)).substring(2);
        BigInteger bigInteger = new BigInteger(hexString, 16);
        aggregateSourceMap.put((String) key, bigInteger);
      }
      AggregatableAttributionSource aggregatableAttributionSource =
          new AggregatableAttributionSource.Builder()
              .setAggregatableSource(aggregateSourceMap)
              .setFilterMap(getFilterData())
              .build();
      mAggregatableAttributionSource = Optional.of(aggregatableAttributionSource);
    }
    return mAggregatableAttributionSource;
  }

  /** Returns the registration id. */
  @Nullable
  public String getRegistrationId() {
    return mRegistrationId;
  }

  /** Returns the shared aggregation keys of the source. */
  @Nullable
  public String getSharedAggregationKeys() {
    return mSharedAggregationKeys;
  }

  /** Returns the install time of the source which is the same value as event time. */
  @Nullable
  public Long getInstallTime() {
    return mInstallTime;
  }

  /** Set app install time to the {@link Source}. */
  public void setInstallTime(Long installTime) {
    mInstallTime = installTime;
  }

  public ApiChoice getApiChoice() {
    return mApiChoice;
  }

  /** Set app install attribution to the {@link Source}. */
  public void setInstallAttributed(boolean isInstallAttributed) {
    mIsInstallAttributed = isInstallAttributed;
  }

  /**
   * @return if it's a derived source, returns the ID of the source it was created from. If it is
   *     null, it is an original source.
   */
  @Nullable
  public String getParentId() {
    return mParentId;
  }

  /** Set the status. */
  public void setStatus(Status status) {
    mStatus = status;
  }

  /** Set the aggregate contributions value. */
  public void setAggregateContributions(int aggregateContributions) {
    mAggregateContributions = aggregateContributions;
  }

  public void setApiChoice(ApiChoice apiChoice) {
    mApiChoice = apiChoice;
  }

  /**
   * Generates AggregatableFilterData from aggregate filter string in Source, including an entry for
   * source type.
   */
  public FilterMap getFilterData() throws ParseException {
    if (mFilterData != null) {
      return mFilterData;
    }
    if (mFilterDataString == null || mFilterDataString.isEmpty()) {
      mFilterData = new FilterMap.Builder().build();
    } else {
      JSONParser parser = new JSONParser();
      mFilterData =
          new FilterMap.Builder()
              .buildFilterData((JSONObject) parser.parse(mFilterDataString))
              .build();
    }
    mFilterData
        .getAttributionFilterMap()
        .put("source_type", Collections.singletonList(mSourceType.getValue()));
    return mFilterData;
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
                    new UnsignedLong(Long.valueOf(reportConfig[0])),
                    getReportingTimeForNoising(reportConfig[1]),
                    resolveFakeReportDestinations(reportConfig[2])))
        .collect(Collectors.toList());
  }

  private boolean isVtcDualDestinationModeWithPostInstallEnabled() {
    return mSourceType == SourceType.EVENT
        && mWebDestinations != null
        && isInstallDetectionEnabled();
  }

  /**
   * Either both app and web destinations can be available or one of them will be available. When
   * both destinations are available, we double the number of states at noise generation to be able
   * to randomly choose one of them for fake report creation. We don't add the multiplier when only
   * one of them is available. In that case, choose the one that's non-null.
   *
   * @param destinationIdentifier destination identifier, can be 0 (app) or 1 (web)
   * @return app or web destination {@link URI}
   */
  private List<URI> resolveFakeReportDestinations(int destinationIdentifier) {
    if (mAppDestinations != null && mWebDestinations != null) {
      // It could be a direct destinationIdentifier == 0 check, but
      return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
          ? mAppDestinations
          : mWebDestinations;
    }
    return mAppDestinations != null ? mAppDestinations : mWebDestinations;
  }

  private static boolean areEqualNullableDestinations(
      List<URI> destinations, List<URI> otherDestinations) {
    if (destinations == null && otherDestinations == null) {
      return true;
    } else if (destinations == null || otherDestinations == null) {
      return false;
    } else {
      return ImmutableMultiset.copyOf(destinations)
          .equals(ImmutableMultiset.copyOf(otherDestinations));
    }
  }

  /** Builder for {@link Source}. */
  public static final class Builder {
    private final Source mBuilding;

    public Builder() {
      mBuilding = new Source();
    }

    /**
     * Copy builder.
     *
     * @param copyFrom copy from source
     * @return copied source
     */
    public static Builder from(Source copyFrom) {
      Builder builder = new Builder();
      builder.setId(copyFrom.mId);
      builder.setRegistrationId(copyFrom.mRegistrationId);
      builder.setAggregateSource(copyFrom.mAggregateSource);
      builder.setExpiryTime(copyFrom.mExpiryTime);
      builder.setAppDestinations(copyFrom.mAppDestinations);
      builder.setWebDestinations(copyFrom.mWebDestinations);
      builder.setSharedAggregationKeys(copyFrom.mSharedAggregationKeys);
      builder.setEventId(copyFrom.mEventId);
      builder.setRegistrant(copyFrom.mRegistrant);
      builder.setEventTime(copyFrom.mEventTime);
      builder.setPublisher(copyFrom.mPublisher);
      builder.setPublisherType(copyFrom.mPublisherType);
      builder.setInstallCooldownWindow(copyFrom.mInstallCooldownWindow);
      builder.setInstallAttributed(copyFrom.mIsInstallAttributed);
      builder.setInstallAttributionWindow(copyFrom.mInstallAttributionWindow);
      builder.setSourceType(copyFrom.mSourceType);
      builder.setAdIdPermission(copyFrom.mAdIdPermission);
      builder.setAggregateContributions(copyFrom.mAggregateContributions);
      builder.setArDebugPermission(copyFrom.mArDebugPermission);
      builder.setAttributionMode(copyFrom.mAttributionMode);
      builder.setDebugKey(copyFrom.mDebugKey);
      builder.setEventReportDedupKeys(copyFrom.mEventReportDedupKeys);
      builder.setAggregateReportDedupKeys(copyFrom.mAggregateReportDedupKeys);
      builder.setEventReportWindow(copyFrom.mEventReportWindow);
      builder.setAggregatableReportWindow(copyFrom.mAggregatableReportWindow);
      builder.setEnrollmentId(copyFrom.mEnrollmentId);
      builder.setFilterData(copyFrom.mFilterDataString);
      builder.setInstallTime(copyFrom.mInstallTime);
      builder.setIsDebugReporting(copyFrom.mIsDebugReporting);
      builder.setPriority(copyFrom.mPriority);
      builder.setStatus(copyFrom.mStatus);
      return builder;
    }

    /** See {@link Source#getId()}. */
    public Builder setId(String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link Source#getEventId()}. */
    public Builder setEventId(UnsignedLong eventId) {
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

    /** See {@link Source#getAppDestinations()}. */
    public Builder setAppDestinations(List<URI> appDestinations) {
      Optional.ofNullable(appDestinations)
          .ifPresent(
              uris -> {
                if (uris.size() > 1) {
                  throw new IllegalArgumentException("Received more than one app destination");
                }
              });
      mBuilding.mAppDestinations = appDestinations;
      return this;
    }

    /** See {@link Source#getWebDestinations()}. */
    public Builder setWebDestinations(@Nullable List<URI> webDestinations) {
      mBuilding.mWebDestinations = webDestinations;
      return this;
    }

    /** See {@link Source#getEnrollmentId()}. */
    public Builder setEnrollmentId(String enrollmentId) {
      mBuilding.mEnrollmentId = enrollmentId;
      return this;
    }

    /** See {@link Source#hasAdIdPermission()} */
    public Source.Builder setAdIdPermission(boolean adIdPermission) {
      mBuilding.mAdIdPermission = adIdPermission;
      return this;
    }

    /** See {@link Source#hasArDebugPermission()} */
    public Source.Builder setArDebugPermission(boolean arDebugPermission) {
      mBuilding.mArDebugPermission = arDebugPermission;
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

    /** See {@link Source#getEventReportWindow()}. */
    public Builder setEventReportWindow(long eventReportWindow) {
      mBuilding.mEventReportWindow = eventReportWindow;
      return this;
    }

    /** See {@link Source#getAggregatableReportWindow()}. */
    public Builder setAggregatableReportWindow(long aggregateReportWindow) {
      mBuilding.mAggregatableReportWindow = aggregateReportWindow;
      return this;
    }

    /** See {@link Source#getPriority()}. */
    public Builder setPriority(long priority) {
      mBuilding.mPriority = priority;
      return this;
    }

    /** See {@link Source#getDebugKey()}. */
    public Builder setDebugKey(@Nullable UnsignedLong debugKey) {
      mBuilding.mDebugKey = debugKey;
      return this;
    }

    /** See {@link Source#isDebugReporting()}. */
    public Builder setIsDebugReporting(boolean isDebugReporting) {
      mBuilding.mIsDebugReporting = isDebugReporting;
      return this;
    }

    /** See {@link Source#getSourceType()}. */
    public Builder setSourceType(SourceType sourceType) {
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link Source#getEventReportDedupKeys()}. */
    public Builder setEventReportDedupKeys(@Nullable List<UnsignedLong> mEventReportDedupKeys) {
      mBuilding.mEventReportDedupKeys = mEventReportDedupKeys;
      return this;
    }

    /** See {@link Source#getAggregateReportDedupKeys()}. */
    public Builder setAggregateReportDedupKeys(
        @Nullable List<UnsignedLong> mAggregateReportDedupKeys) {
      mBuilding.mAggregateReportDedupKeys = mAggregateReportDedupKeys;
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

    /** See {@link Source#getFilterDataString()}. */
    public Builder setFilterData(String filterMap) {
      mBuilding.mFilterDataString = filterMap;
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

    /** See {@link Source#getRegistrationId()} */
    public Builder setRegistrationId(String registrationId) {
      mBuilding.mRegistrationId = registrationId;
      return this;
    }

    /** See {@link Source#getSharedAggregationKeys()} */
    public Builder setSharedAggregationKeys(String sharedAggregationKeys) {
      mBuilding.mSharedAggregationKeys = sharedAggregationKeys;
      return this;
    }

    /** See {@link Source#getInstallTime()} */
    public Builder setInstallTime(Long installTime) {
      mBuilding.mInstallTime = installTime;
      return this;
    }

    /** See {@link Source#getParentId()} */
    public Builder setParentId(String parentId) {
      mBuilding.mParentId = parentId;
      return this;
    }

    /** See {@link Source#getAggregatableAttributionSource()} */
    public Builder setAggregatableAttributionSource(
        AggregatableAttributionSource aggregatableAttributionSource) {
      mBuilding.mAggregatableAttributionSource = Optional.ofNullable(aggregatableAttributionSource);
      return this;
    }

    public Builder setApiChoice(ApiChoice apiChoice) {
      mBuilding.mApiChoice = apiChoice;
      return this;
    }

    /** Build the {@link Source}. */
    public Source build() {
      if (mBuilding.mAppDestinations == null && mBuilding.mWebDestinations == null) {
        throw new IllegalArgumentException("At least one destination is required");
      }
      return mBuilding;
    }
  }
}
