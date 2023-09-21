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
import com.google.common.collect.ImmutableMultiset;
import com.google.measurement.aggregation.AggregatableAttributionSource;
import com.google.measurement.util.UnsignedLong;
import com.google.measurement.util.Util;
import com.google.measurement.util.Validation;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
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
  private Long mEventReportWindow;
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
  @Nullable private String mDebugJoinKey;
  @Nullable private String mPlatformAdId;
  @Nullable private String mDebugAdId;
  private URI mRegistrationOrigin;
  @Nullable private List<AttributedTrigger> mAttributedTriggers;
  @Nullable private ReportSpec mFlexEventReportSpec;
  @Nullable private String mTriggerSpecsString;
  @Nullable private Integer mMaxEventLevelReports;
  @Nullable private String mEventAttributionStatusString;
  @Nullable private String mPrivacyParametersString = null;
  private boolean mCoarseEventReportDestinations;
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
   * @return the list of attributed triggers
   */
  @Nullable
  public List<AttributedTrigger> getAttributedTriggers() {
    return mAttributedTriggers;
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
        && Objects.equals(mEventReportWindow, source.mEventReportWindow)
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
        && Objects.equals(mDebugJoinKey, source.mDebugJoinKey)
        && Objects.equals(mPlatformAdId, source.mPlatformAdId)
        && Objects.equals(mDebugAdId, source.mDebugAdId)
        && Objects.equals(mRegistrationOrigin, source.mRegistrationOrigin)
        && Objects.equals(mFlexEventReportSpec, source.mFlexEventReportSpec)
        && Objects.equals(mAttributedTriggers, source.mAttributedTriggers)
        && mCoarseEventReportDestinations == source.mCoarseEventReportDestinations
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
        mInstallTime,
        mPlatformAdId,
        mDebugAdId,
        mRegistrationOrigin,
        mDebugAdId,
        mDebugJoinKey,
        mFlexEventReportSpec,
        mCoarseEventReportDestinations);
  }

  @VisibleForTesting
  public void setAttributionMode(AttributionMode attributionMode) {
    mAttributionMode = attributionMode;
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
  public Long getEventReportWindow() {
    return mEventReportWindow;
  }

  /**
   * Time when {@link Source} event report window will expire. (Appends the Event Time to window)
   */
  public Long getProcessedEventReportWindow() {
    if (mEventReportWindow == null) {
      return null;
    }
    // TODO(b/290098169): Cleanup after a few releases
    // Handling cases where ReportWindow is already stored as mEventTime + mEventReportWindow
    if (mEventReportWindow > mEventTime) {
      return mEventReportWindow;
    } else {
      return mEventTime + mEventReportWindow;
    }
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

  /** Get event attribution status string */
  public String getEventAttributionStatus() {
    return mEventAttributionStatusString;
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
   * Check whether the parameter of flexible event API is valid or not. Currently, only max
   * information gain is checed
   *
   * @return whether the parameters of flexible are valid
   */
  public static boolean isFlexEventApiValueValid(ReportSpec reportSpec, SourceType type) {
    if (reportSpec == null) {
      return true;
    }
    double informationGainThreshold =
        type == SourceType.NAVIGATION
            ? PrivacyParams.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_NAVIGATION
            : PrivacyParams.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_EVENT;

    return (reportSpec.getInformationGain() <= informationGainThreshold);
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

  /**
   * Returns join key that should be matched with trigger's join key at the time of generating
   * reports.
   */
  @Nullable
  public String getDebugJoinKey() {
    return mDebugJoinKey;
  }

  /**
   * Returns SHA256 hash of AdID from getAdId() on app registration concatenated with enrollment ID,
   * to be matched with a web trigger's {@link Trigger#getDebugAdId()} value at the time of
   * generating reports.
   */
  @Nullable
  public String getPlatformAdId() {
    return mPlatformAdId;
  }

  /**
   * Returns SHA256 hash of AdID from registration response on web registration concatenated with
   * enrollment ID, to be matched with an app trigger's {@link Trigger#getPlatformAdId()} value at
   * the time of generating reports.
   */
  @Nullable
  public String getDebugAdId() {
    return mDebugAdId;
  }

  /**
   * Indicates whether event report for this source should be generated with the destinations where
   * the conversion occurred or merge app and web destinations. Set to true of both app and web
   * destination should be merged into the array of event report.
   */
  public boolean getCoarseEventReportDestinations() {
    return mCoarseEventReportDestinations;
  }

  /** Returns max bucket increments */
  public Integer getMaxEventLevelReports() {
    return mMaxEventLevelReports;
  }

  /** Returns registration origin used to register the source */
  public URI getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Returns trigger specs */
  public String getTriggerSpecs() {
    return mTriggerSpecsString;
  }

  /** Returns flex event report spec */
  public ReportSpec getFlexEventReportSpec() {
    return mFlexEventReportSpec;
  }

  /** Returns privacy parameters */
  public String getPrivacyParameters() {
    return mPrivacyParametersString;
  }

  /** See {@link Source#getAppDestinations()} */
  public void setAppDestinations(@Nullable List<URI> appDestinations) {
    mAppDestinations = appDestinations;
  }

  /** See {@link Source#getWebDestinations()} */
  public void setWebDestinations(@Nullable List<URI> webDestinations) {
    mWebDestinations = webDestinations;
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

  /** Returns true if the source has app destination(s), false otherwise. */
  public boolean hasAppDestinations() {
    return mAppDestinations != null && mAppDestinations.size() > 0;
  }

  /** Returns true if the source has web destination(s), false otherwise. */
  public boolean hasWebDestinations() {
    return mWebDestinations != null && mWebDestinations.size() > 0;
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

  /** Parses the event attribution status string. */
  @VisibleForTesting
  public void parseEventAttributionStatus() {
    JSONParser parser = new JSONParser();
    JSONArray eventAttributionStatus = new JSONArray();
    try {
      eventAttributionStatus = (JSONArray) parser.parse(mEventAttributionStatusString);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    for (Object attributionStatus : eventAttributionStatus) {
      JSONObject json = (JSONObject) attributionStatus;
      mAttributedTriggers.add(new AttributedTrigger(json));
    }
  }

  /** Build the attributed triggers list from the raw string */
  public void buildAttributedTriggers() {
    if (mAttributedTriggers == null) {
      mAttributedTriggers = new ArrayList<>();
      if (mEventAttributionStatusString != null && !mEventAttributionStatusString.isEmpty()) {
        parseEventAttributionStatus();
      }
    }
  }

  /** See {@link Source#getAttributedTriggers()} */
  public void setAttributedTriggers(List<AttributedTrigger> attributedTriggers) {
    mAttributedTriggers = attributedTriggers;
  }

  /**
   * Returns the RBR provided or default value for max_event_level_reports
   *
   * @param sourceType Source's Type
   * @param maxEventLevelReports RBR parsed value for max_event_level_reports
   */
  public static Integer getOrDefaultMaxEventLevelReports(
      SourceType sourceType, @Nullable Integer maxEventLevelReports) {
    if (maxEventLevelReports == null) {
      maxEventLevelReports =
          sourceType == Source.SourceType.NAVIGATION
              ? PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS
              : PrivacyParams.EVENT_SOURCE_MAX_REPORTS;
    }
    return maxEventLevelReports;
  }

  /**
   * Returns parsed or default value of event report windows.
   *
   * @param eventReportWindows string to be parsed
   * @param sourceType Source's Type
   * @param expiryDelta relative expiry value
   * @return parsed or default value
   */
  @Nullable
  public static List<Pair<Long, Long>> getOrDefaultEventReportWindows(
      @Nullable String eventReportWindows, SourceType sourceType, long expiryDelta) {
    if (eventReportWindows == null) {
      return getDefaultEventReportWindows(expiryDelta, sourceType);
    }
    return parseEventReportWindows(eventReportWindows, Logger.getLogger(Source.class.getName()));
  }

  public static List<Pair<Long, Long>> parseEventReportWindows(
      String eventReportWindows, Logger logger) {
    List<Pair<Long, Long>> result = new ArrayList<>();
    JSONParser jsonParser = new JSONParser();
    try {
      JSONObject jsonObject = (JSONObject) jsonParser.parse(eventReportWindows);
      long startDuration = 0;
      if (jsonObject.containsKey("start_time")) {
        startDuration = Util.parseJsonLong(jsonObject, "start_time");
      }
      JSONParser parser = new JSONParser();
      JSONArray endTimesJSON = (JSONArray) parser.parse((String) jsonObject.get("end_times"));

      for (Object o : endTimesJSON) {
        long endDuration = (Long) o;
        Pair<Long, Long> window = new Pair<>(startDuration, endDuration);
        result.add(window);
        startDuration = endDuration;
      }
    } catch (ParseException e) {
      logger.info("Invalid JSON encountered: event_report_windows");
      return null;
    }
    return result;
  }

  private static List<Pair<Long, Long>> getDefaultEventReportWindows(
      long expiryDelta, SourceType sourceType) {
    List<Pair<Long, Long>> result = new ArrayList<>();
    List<Long> defaultEarlyWindows =
        EventReportWindowCalcDelegate.getDefaultEarlyReportingWindows(sourceType, false);
    List<Long> earlyWindows =
        new EventReportWindowCalcDelegate(new Flags())
            .getConfiguredOrDefaultEarlyReportingWindows(sourceType, defaultEarlyWindows);
    long windowStart = 0;
    for (long earlyWindow : earlyWindows) {
      if (earlyWindow >= expiryDelta) {
        continue;
      }
      result.add(new Pair<>(windowStart, earlyWindow));
      windowStart = earlyWindow;
    }
    result.add(new Pair<>(windowStart, expiryDelta));
    return result;
  }

  /** Build the flexible event report API from the raw string */
  public void buildFlexibleEventReportApi() {
    buildAttributedTriggers();
    if (mFlexEventReportSpec == null) {
      mFlexEventReportSpec =
          new ReportSpec(
              mTriggerSpecsString,
              getOrDefaultMaxEventLevelReports(mSourceType, mMaxEventLevelReports),
              this,
              mPrivacyParametersString);
    }
  }

  /**
   * Encode the result of privacy parameters computed based on input parameters to JSON
   *
   * @return String encoded the privacy parameters
   */
  public String encodePrivacyParametersToJSONString() {
    if (mFlexEventReportSpec == null) {
      return null;
    }
    return mFlexEventReportSpec.encodePrivacyParametersToJSONString();
  }

  /** Builder for {@link Source}. */
  public static final class Builder {
    private final Source mBuilding;

    public Builder() {
      mBuilding = new Source();
      mBuilding.mEventReportWindow = 0L;
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
      builder.setDebugJoinKey(copyFrom.mDebugJoinKey);
      builder.setPlatformAdId(copyFrom.mPlatformAdId);
      builder.setDebugAdId(copyFrom.mDebugAdId);
      builder.setRegistrationOrigin(copyFrom.mRegistrationOrigin);
      builder.setAttributedTriggers(copyFrom.mAttributedTriggers);
      builder.setFlexEventReportSpec(copyFrom.mFlexEventReportSpec);
      builder.setCoarseEventReportDestinations(copyFrom.mCoarseEventReportDestinations);
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
      Validation.validateURI(publisher);
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
    public Builder setEventReportWindow(Long eventReportWindow) {
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
      Validation.validateURI(registrant);
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

    /** See {@link Source#getDebugJoinKey()} */
    public Builder setDebugJoinKey(@Nullable String debugJoinKey) {
      mBuilding.mDebugJoinKey = debugJoinKey;
      return this;
    }

    /** See {@link Source#getPlatformAdId()} */
    public Builder setPlatformAdId(@Nullable String platformAdId) {
      mBuilding.mPlatformAdId = platformAdId;
      return this;
    }

    /** See {@link Source#getDebugAdId()} */
    public Builder setDebugAdId(@Nullable String debugAdId) {
      mBuilding.mDebugAdId = debugAdId;
      return this;
    }

    /** See {@link Source#getRegistrationOrigin()} */
    public Builder setRegistrationOrigin(URI registrationOrigin) {
      mBuilding.mRegistrationOrigin = registrationOrigin;
      return this;
    }

    /** See {@link Source#getAttributedTriggers()} */
    public Builder setAttributedTriggers(List<AttributedTrigger> attributedTriggers) {
      mBuilding.mAttributedTriggers = attributedTriggers;
      return this;
    }

    /** See {@link Source#getPrivacyParameters()} */
    public Builder setPrivacyParameters(@Nullable String privacyParameters) {
      mBuilding.mPrivacyParametersString = privacyParameters;
      return this;
    }

    /** See {@link Source#getFlexEventReportSpec()} */
    public Builder setFlexEventReportSpec(@Nullable ReportSpec flexEventReportSpec) {
      mBuilding.mFlexEventReportSpec = flexEventReportSpec;
      return this;
    }

    /** Build flex event report spec from strings */
    public Builder buildInitialFlexEventReportSpec() {
      // TODO(b/290100712): Refactor to remove this method
      if (mBuilding.mTriggerSpecsString == null || mBuilding.mTriggerSpecsString.isEmpty()) {
        return this;
      }
      mBuilding.mFlexEventReportSpec =
          new ReportSpec(
              mBuilding.mTriggerSpecsString,
              getOrDefaultMaxEventLevelReports(
                  mBuilding.mSourceType, mBuilding.mMaxEventLevelReports),
              null);
      return this;
    }

    /** See {@link Source#getTriggerSpecs()} */
    public Builder setTriggerSpecs(String triggerSpecs) {
      mBuilding.mTriggerSpecsString = triggerSpecs;
      return this;
    }

    public Builder setEventAttributionStatus(@Nullable String eventAttributionStatus) {
      mBuilding.mEventAttributionStatusString = eventAttributionStatus;
      return this;
    }

    /** See {@link Source#getCoarseEventReportDestinations()} */
    public Builder setCoarseEventReportDestinations(boolean coarseEventReportDestinations) {
      mBuilding.mCoarseEventReportDestinations = coarseEventReportDestinations;
      return this;
    }

    /** See {@link Source#getMaxEventLevelReports()} */
    public Builder setMaxEventLevelReports(@Nullable Integer maxEventLevelReports) {
      mBuilding.mMaxEventLevelReports = maxEventLevelReports;
      return this;
    }

    public Builder setApiChoice(ApiChoice apiChoice) {
      mBuilding.mApiChoice = apiChoice;
      return this;
    }

    /** Checks if the source has valid information gain */
    public boolean hasValidInformationGain() {
      return isFlexEventApiValueValid(mBuilding.mFlexEventReportSpec, mBuilding.mSourceType);
    }

    /** Build the {@link Source}. */
    public Source build() {
      Validation.validateNonNull(
          mBuilding.mPublisher,
          mBuilding.mEnrollmentId,
          mBuilding.mRegistrant,
          mBuilding.mSourceType,
          mBuilding.mAggregateReportDedupKeys,
          mBuilding.mEventReportDedupKeys);
      return mBuilding;
    }
  }
}
