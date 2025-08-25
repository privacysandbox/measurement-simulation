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

package com.google.measurement.client;

import com.google.measurement.client.aggregation.AggregatableAttributionSource;
import com.google.measurement.client.aggregation.AggregateDebugReporting;
import com.google.measurement.client.noising.Combinatorics;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.util.UnsignedLong;
import com.google.measurement.client.util.Validation;

import com.google.common.collect.ImmutableMultiset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** POJO for Source. */
public class Source {

  public static final long DEFAULT_MAX_EVENT_STATES = 3L;

  private String mId;
  private UnsignedLong mEventId;
  private Uri mPublisher;
  @EventSurfaceType private int mPublisherType;
  private List<Uri> mAppDestinations;
  private List<Uri> mWebDestinations;
  private String mEnrollmentId;
  private Uri mRegistrant;
  private SourceType mSourceType;
  private long mPriority;
  @Status private int mStatus;
  private long mEventTime;
  private long mExpiryTime;
  @Nullable private Long mEventReportWindow;
  @Nullable private String mEventReportWindows;
  private long mAggregatableReportWindow;
  private long mReinstallReattributionWindow;
  private List<UnsignedLong> mAggregateReportDedupKeys;
  private List<UnsignedLong> mEventReportDedupKeys;
  @AttributionMode private int mAttributionMode;
  private long mInstallAttributionWindow;
  private long mInstallCooldownWindow;
  @Nullable private UnsignedLong mDebugKey;
  private boolean mIsInstallAttributed;
  private boolean mIsDebugReporting;
  private String mFilterDataString;
  @Nullable private String mSharedFilterDataKeys;
  private FilterMap mFilterData;
  private String mAggregateSource;
  private int mAggregateContributions;
  private Optional<AggregatableAttributionSource> mAggregatableAttributionSource;
  private boolean mAdIdPermission;
  private boolean mArDebugPermission;
  @Nullable private String mRegistrationId;
  @Nullable private String mSharedAggregationKeys;
  @Nullable private Long mInstallTime;
  @Nullable private String mParentId;
  @Nullable private String mDebugJoinKey;
  @Nullable private Set<UnsignedLong> mTriggerData;
  @Nullable private List<AttributedTrigger> mAttributedTriggers;
  @Nullable private TriggerSpecs mTriggerSpecs;
  @Nullable private String mTriggerSpecsString;
  @Nullable private Long mNumStates;
  @Nullable private Double mFlipProbability;
  @Nullable private Integer mMaxEventLevelReports;
  @Nullable private String mEventAttributionStatusString;
  @Nullable private String mPrivacyParametersString = null;
  private TriggerDataMatching mTriggerDataMatching;
  @Nullable private String mPlatformAdId;
  @Nullable private String mDebugAdId;
  private Uri mRegistrationOrigin;
  private boolean mCoarseEventReportDestinations;
  @Nullable private UnsignedLong mSharedDebugKey;
  private List<Pair<Long, Long>> mParsedEventReportWindows;
  private boolean mDropSourceIfInstalled;
  @Nullable private List<String> mAttributionScopes;
  @Nullable private Long mAttributionScopeLimit;
  @Nullable private Long mMaxEventStates;
  private long mDestinationLimitPriority;
  @Nullable private DestinationLimitAlgorithm mDestinationLimitAlgorithm;
  @Nullable private Double mEventLevelEpsilon;
  @Nullable private String mAggregateDebugReportingString;
  @Nullable private AggregateDebugReporting mAggregateDebugReporting;
  private int mAggregateDebugReportContributions;
  @Nullable private AggregateContributionBuckets mAggregateContributionBuckets;

  /**
   * Parses and returns the event_report_windows Returns null if parsing fails or if there is no
   * windows provided by user.
   */
  @Nullable
  public List<Pair<Long, Long>> parsedProcessedEventReportWindows() {
    if (mParsedEventReportWindows != null) {
      return mParsedEventReportWindows;
    }

    if (mEventReportWindows == null) {
      return null;
    }

    List<Pair<Long, Long>> rawWindows = parseEventReportWindows(mEventReportWindows);
    if (rawWindows == null) {
      return null;
    }
    // Append Event Time
    mParsedEventReportWindows = new ArrayList<>();

    for (Pair<Long, Long> window : rawWindows) {
      mParsedEventReportWindows.add(
          new Pair<>(mEventTime + window.first, mEventTime + window.second));
    }
    return mParsedEventReportWindows;
  }

  /**
   * Returns parsed or default value of event report windows (can be used during {@code Source}
   * construction since the method does not require a {@code Source} object).
   *
   * @param eventReportWindows string to be parsed
   * @param sourceType Source's Type
   * @param expiryDelta relative expiry value
   * @param flags Flags
   * @return parsed or default value
   */
  @Nullable
  public static List<Pair<Long, Long>> getOrDefaultEventReportWindowsForFlex(
      @Nullable JSONObject eventReportWindows,
      @NonNull SourceType sourceType,
      long expiryDelta,
      @NonNull Flags flags) {
    if (eventReportWindows == null) {
      return getDefaultEventReportWindowsForFlex(expiryDelta, sourceType, flags);
    }
    return parseEventReportWindows(eventReportWindows);
  }

  /** Parses the provided eventReportWindows. Returns null if parsing fails */
  @Nullable
  public static List<Pair<Long, Long>> parseEventReportWindows(@NonNull String eventReportWindows) {
    try {
      JSONObject jsonObject = new JSONObject(eventReportWindows);
      return parseEventReportWindows(jsonObject);
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Invalid JSON encountered: event_report_windows");
      return null;
    }
  }

  /** Parses the provided eventReportWindows. Returns null if parsing fails */
  @Nullable
  public static List<Pair<Long, Long>> parseEventReportWindows(@NonNull JSONObject jsonObject) {
    List<Pair<Long, Long>> result = new ArrayList<>();
    try {
      long startTime = 0L;
      if (!jsonObject.isNull("start_time")) {
        startTime = jsonObject.getLong("start_time");
      }
      JSONArray endTimesJSON = jsonObject.getJSONArray("end_times");

      for (int i = 0; i < endTimesJSON.length(); i++) {
        long endTime = endTimesJSON.getLong(i);
        Pair<Long, Long> window = Pair.create(startTime, endTime);
        result.add(window);
        startTime = endTime;
      }
    } catch (JSONException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Invalid JSON encountered: event_report_windows");
      return null;
    }
    return result;
  }

  private static List<Pair<Long, Long>> getDefaultEventReportWindowsForFlex(
      long expiryDelta, SourceType sourceType, Flags flags) {
    List<Pair<Long, Long>> result = new ArrayList<>();
    // Obtain default early report windows without regard to install-related behaviour.
    List<Long> defaultEarlyWindowEnds =
        EventReportWindowCalcDelegate.getDefaultEarlyReportingWindowEnds(sourceType, false);
    List<Long> earlyWindowEnds =
        new EventReportWindowCalcDelegate(flags)
            .getConfiguredOrDefaultEarlyReportingWindowEnds(sourceType, defaultEarlyWindowEnds);
    long windowStart = 0;
    for (long earlyWindowEnd : earlyWindowEnds) {
      if (earlyWindowEnd >= expiryDelta) {
        break;
      }
      result.add(Pair.create(windowStart, earlyWindowEnd));
      windowStart = earlyWindowEnd;
    }
    result.add(Pair.create(windowStart, expiryDelta));
    return result;
  }

  /**
   * Checks if the source has a valid number of report states and sets the value. mNumStates will
   * not be set if invalid.
   *
   * @param flags flag values
   */
  public boolean validateAndSetNumReportStates(Flags flags) {
    if (mTriggerSpecs == null) {
      long reportStateCountLimit = flags.getMeasurementMaxReportStatesPerSourceRegistration();
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate =
          new EventReportWindowCalcDelegate(flags);
      int reportingWindowCountForNoising =
          eventReportWindowCalcDelegate.getReportingWindowCountForNoising(this);

      int numStars = eventReportWindowCalcDelegate.getMaxReportCount(this);
      int numBars =
          getTriggerDataCardinality()
              * reportingWindowCountForNoising
              * getDestinationTypeMultiplier(flags);

      long numStates = Combinatorics.getNumberOfStarsAndBarsSequences(numStars, numBars);
      if (numStates > reportStateCountLimit) {
        return false;
      }

      setNumStates(numStates);

      return true;
    } else {
      return mTriggerSpecs.hasValidReportStateCount(this, flags);
    }
  }

  /**
   * Verifies whether the source contains valid attribution scope values.
   *
   * @param flags flag values
   */
  public AttributionScopeValidationResult validateAttributionScopeValues(Flags flags) {
    if (!flags.getMeasurementEnableAttributionScope() || getAttributionScopeLimit() == null) {
      return AttributionScopeValidationResult.VALID;
    }
    if (getMaxEventStates() == null) {
      throw new IllegalStateException(
          "maxEventStates should be set if attributionScopeLimit is set.");
    }
    Long numStates =
        mTriggerSpecs == null ? getNumStates(flags) : mTriggerSpecs.getNumStates(this, flags);
    if (numStates == null || numStates == 0L) {
      throw new IllegalStateException(
          "Num states should be validated before validating max event states");
    }
    if (getSourceType() == SourceType.EVENT && numStates > getMaxEventStates()) {
      return AttributionScopeValidationResult.INVALID_MAX_EVENT_STATES_LIMIT;
    }
    if (!hasValidAttributionScopeInformationGain(flags, numStates)) {
      return AttributionScopeValidationResult.INVALID_INFORMATION_GAIN_LIMIT;
    }
    return AttributionScopeValidationResult.VALID;
  }

  /**
   * Checks if the source has valid information gain
   *
   * @param flags flag values
   */
  public boolean hasValidInformationGain(@NonNull Flags flags) {
    if (mTriggerSpecs != null) {
      return isFlexEventApiValueValid(flags);
    }
    return isFlexLiteApiValueValid(flags);
  }

  /**
   * @param flags flag values
   * @return the information gain threshold for a single attribution source
   */
  public float getInformationGainThreshold(Flags flags) {
    if (getDestinationTypeMultiplier(flags) == 2) {
      return mSourceType == SourceType.EVENT
          ? flags.getMeasurementFlexApiMaxInformationGainDualDestinationEvent()
          : flags.getMeasurementFlexApiMaxInformationGainDualDestinationNavigation();
    }
    return mSourceType == SourceType.EVENT
        ? flags.getMeasurementFlexApiMaxInformationGainEvent()
        : flags.getMeasurementFlexApiMaxInformationGainNavigation();
  }

  /**
   * @param flags flag values
   * @return the information gain threshold for attribution scopes.
   */
  public double getAttributionScopeInfoGainThreshold(Flags flags) {
    if (getDestinationTypeMultiplier(flags) == 2) {
      return mSourceType == SourceType.EVENT
          ? flags.getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent()
          : flags.getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation();
    }
    return mSourceType == SourceType.EVENT
        ? flags.getMeasurementAttributionScopeMaxInfoGainEvent()
        : flags.getMeasurementAttributionScopeMaxInfoGainNavigation();
  }

  /** Retrieves the default epsilon or epsilon defined from Source. */
  public double getConditionalEventLevelEpsilon(Flags flags) {
    if (flags.getMeasurementEnableEventLevelEpsilonInSource() && getEventLevelEpsilon() != null) {
      return getEventLevelEpsilon();
    }
    return flags.getMeasurementPrivacyEpsilon();
  }

  private boolean isFlexLiteApiValueValid(Flags flags) {
    return Combinatorics.getInformationGain(getNumStates(flags), getFlipProbability(flags))
        <= getInformationGainThreshold(flags);
  }

  private boolean hasValidAttributionScopeInformationGain(Flags flags, long numStates) {
    if (!flags.getMeasurementEnableAttributionScope() || getAttributionScopeLimit() == null) {
      return true;
    }
    return Combinatorics.getMaxInformationGainWithAttributionScope(
            numStates, getAttributionScopeLimit(), getMaxEventStates())
        <= getAttributionScopeInfoGainThreshold(flags);
  }

  private void buildPrivacyParameters(Flags flags) {
    if (mTriggerSpecs != null) {
      // Flex source has num states set during registration but not during attribution; also
      // num states is only needed for information gain calculation, which is handled during
      // registration. We set flip probability here for use in noising during registration and
      // availability for debug reporting during attribution.
      setFlipProbability(mTriggerSpecs.getFlipProbability(this, flags));
      return;
    }
    double epsilon = getConditionalEventLevelEpsilon(flags);
    setFlipProbability(Combinatorics.getFlipProbability(getNumStates(flags), epsilon));
  }

  /** Should source report coarse destinations */
  public boolean shouldReportCoarseDestinations(Flags flags) {
    return flags.getMeasurementEnableCoarseEventReportDestinations()
        && hasCoarseEventReportDestinations();
  }

  /** Returns the number of destination types to use in privacy computations. */
  public int getDestinationTypeMultiplier(Flags flags) {
    return !shouldReportCoarseDestinations(flags) && hasAppDestinations() && hasWebDestinations()
        ? SourceNoiseHandler.DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER
        : SourceNoiseHandler.SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER;
  }

  /** Returns true is manual event reporting windows are set otherwise false; */
  public boolean hasManualEventReportWindows() {
    return getEventReportWindows() != null;
  }

  @IntDef(value = {Status.ACTIVE, Status.IGNORED, Status.MARKED_TO_DELETE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Status {
    int ACTIVE = 0;
    int IGNORED = 1;
    int MARKED_TO_DELETE = 2;
  }

  @IntDef(
      value = {
        AttributionMode.UNASSIGNED,
        AttributionMode.TRUTHFULLY,
        AttributionMode.NEVER,
        AttributionMode.FALSELY,
      })
  @Retention(RetentionPolicy.SOURCE)
  public @interface AttributionMode {
    int UNASSIGNED = 0;
    int TRUTHFULLY = 1;
    int NEVER = 2;
    int FALSELY = 3;
  }

  public enum DestinationLimitAlgorithm {
    LIFO,
    FIFO
  }

  /** The choice of the summary operator with the reporting window */
  public enum TriggerDataMatching {
    MODULUS,
    EXACT
  }

  /** The validation result attribution scope values. */
  public enum AttributionScopeValidationResult {
    VALID(true),
    INVALID_MAX_EVENT_STATES_LIMIT(false),
    INVALID_INFORMATION_GAIN_LIMIT(false);

    private final boolean mIsValid;

    AttributionScopeValidationResult(boolean isValid) {
      mIsValid = isValid;
    }

    public boolean isValid() {
      return mIsValid;
    }
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

    public int getIntValue() {
      return this.equals(SourceType.NAVIGATION) ? 1 : 0;
    }
  }

  private Source() {
    mEventReportDedupKeys = new ArrayList<>();
    mAggregateReportDedupKeys = new ArrayList<>();
    mStatus = Status.ACTIVE;
    mSourceType = SourceType.EVENT;
    // Making this default explicit since it anyway would occur on an uninitialised int field.
    mPublisherType = EventSurfaceType.APP;
    mAttributionMode = AttributionMode.UNASSIGNED;
    mTriggerDataMatching = TriggerDataMatching.MODULUS;
    mIsInstallAttributed = false;
    mIsDebugReporting = false;
  }

  /** Class for storing fake report data. */
  public static class FakeReport {
    private final UnsignedLong mTriggerData;
    private final long mReportingTime;
    private final long mTriggerTime;
    private final List<Uri> mDestinations;
    private final Pair<Long, Long> mTriggerSummaryBucket;

    public FakeReport(
        UnsignedLong triggerData,
        long reportingTime,
        long triggerTime,
        List<Uri> destinations,
        @Nullable Pair<Long, Long> triggerSummaryBucket) {
      mTriggerData = triggerData;
      mReportingTime = reportingTime;
      mDestinations = destinations;
      mTriggerTime = triggerTime;
      mTriggerSummaryBucket = triggerSummaryBucket;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FakeReport)) return false;
      FakeReport that = (FakeReport) o;
      return Objects.equals(mTriggerData, that.mTriggerData)
          && mReportingTime == that.mReportingTime
          && mTriggerTime == that.mTriggerTime
          && Objects.equals(mDestinations, that.mDestinations)
          && Objects.equals(mTriggerSummaryBucket, that.mTriggerSummaryBucket);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          mTriggerData, mReportingTime, mTriggerTime, mDestinations, mTriggerSummaryBucket);
    }

    public long getReportingTime() {
      return mReportingTime;
    }

    public long getTriggerTime() {
      return mTriggerTime;
    }

    public UnsignedLong getTriggerData() {
      return mTriggerData;
    }

    public List<Uri> getDestinations() {
      return mDestinations;
    }

    @Nullable
    public Pair<Long, Long> getTriggerSummaryBucket() {
      return mTriggerSummaryBucket;
    }
  }

  /**
   * Range of trigger metadata: [0, cardinality).
   *
   * @return Cardinality of {@link Trigger} metadata
   */
  public int getTriggerDataCardinality() {
    if (getTriggerSpecs() != null) {
      return getTriggerSpecs().getTriggerDataCardinality();
    }
    if (getTriggerData() != null) {
      return getTriggerData().size();
    }
    return mSourceType == SourceType.EVENT
        ? PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY
        : PrivacyParams.getNavigationTriggerDataCardinality();
  }

  /**
   * @return the list of trigger data
   */
  @Nullable
  public Set<UnsignedLong> getTriggerData() {
    return mTriggerData;
  }

  /**
   * @return the list of attributed triggers
   */
  @Nullable
  public List<AttributedTrigger> getAttributedTriggers() {
    return mAttributedTriggers;
  }

  /**
   * @return all the attributed trigger IDs
   */
  public List<String> getAttributedTriggerIds() {
    List<String> result = new ArrayList<>();
    for (AttributedTrigger attributedTrigger : mAttributedTriggers) {
      result.add(attributedTrigger.getTriggerId());
    }
    return result;
  }

  /**
   * @return the JSON encoded current trigger status
   */
  @NonNull
  public String attributedTriggersToJson() {
    JSONArray jsonArray = new JSONArray();
    for (AttributedTrigger trigger : mAttributedTriggers) {
      jsonArray.put(trigger.encodeToJson());
    }
    return jsonArray.toString();
  }

  /**
   * @return the JSON encoded current trigger status
   */
  @NonNull
  public String attributedTriggersToJsonFlexApi() {
    JSONArray jsonArray = new JSONArray();
    for (AttributedTrigger trigger : mAttributedTriggers) {
      jsonArray.put(trigger.encodeToJsonFlexApi());
    }
    return jsonArray.toString();
  }

  /**
   * @return the flex event trigger specification
   */
  @Nullable
  public TriggerSpecs getTriggerSpecs() {
    return mTriggerSpecs;
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
        && Objects.equals(mEventReportWindows, source.mEventReportWindows)
        && Objects.equals(mAggregatableReportWindow, source.mAggregatableReportWindow)
        && mEventTime == source.mEventTime
        && mReinstallReattributionWindow == source.mReinstallReattributionWindow
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
        && Objects.equals(mSharedFilterDataKeys, source.mSharedFilterDataKeys)
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
        && mCoarseEventReportDestinations == source.mCoarseEventReportDestinations
        && Objects.equals(mTriggerData, source.mTriggerData)
        && Objects.equals(mAttributedTriggers, source.mAttributedTriggers)
        && Objects.equals(mTriggerSpecs, source.mTriggerSpecs)
        && Objects.equals(mTriggerSpecsString, source.mTriggerSpecsString)
        && Objects.equals(mMaxEventLevelReports, source.mMaxEventLevelReports)
        && Objects.equals(mEventAttributionStatusString, source.mEventAttributionStatusString)
        && Objects.equals(mPrivacyParametersString, source.mPrivacyParametersString)
        && Objects.equals(mTriggerDataMatching, source.mTriggerDataMatching)
        && Objects.equals(mSharedDebugKey, source.mSharedDebugKey)
        && mDropSourceIfInstalled == source.mDropSourceIfInstalled
        && Objects.equals(mAttributionScopes, source.mAttributionScopes)
        && Objects.equals(mAttributionScopeLimit, source.mAttributionScopeLimit)
        && Objects.equals(mMaxEventStates, source.mMaxEventStates)
        && mDestinationLimitPriority == source.mDestinationLimitPriority
        && Objects.equals(mDestinationLimitAlgorithm, source.mDestinationLimitAlgorithm)
        && Objects.equals(mEventLevelEpsilon, source.mEventLevelEpsilon)
        && Objects.equals(mAggregateDebugReportingString, source.mAggregateDebugReportingString)
        && mAggregateDebugReportContributions == source.mAggregateDebugReportContributions
        && Objects.equals(mAggregateContributionBuckets, source.mAggregateContributionBuckets);
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
        mEventReportWindows,
        mAggregatableReportWindow,
        mReinstallReattributionWindow,
        mEventTime,
        mEventId,
        mSourceType,
        mEventReportDedupKeys,
        mAggregateReportDedupKeys,
        mFilterDataString,
        mSharedFilterDataKeys,
        mAggregateSource,
        mAggregateContributions,
        mAggregatableAttributionSource,
        mDebugKey,
        mAdIdPermission,
        mArDebugPermission,
        mRegistrationId,
        mSharedAggregationKeys,
        mInstallTime,
        mDebugJoinKey,
        mPlatformAdId,
        mDebugAdId,
        mRegistrationOrigin,
        mDebugJoinKey,
        mTriggerData,
        mAttributedTriggers,
        mTriggerSpecs,
        mTriggerSpecsString,
        mTriggerDataMatching,
        mMaxEventLevelReports,
        mEventAttributionStatusString,
        mPrivacyParametersString,
        mCoarseEventReportDestinations,
        mSharedDebugKey,
        mDropSourceIfInstalled,
        mAttributionScopes,
        mAttributionScopeLimit,
        mMaxEventStates,
        mDestinationLimitPriority,
        mDestinationLimitAlgorithm,
        mEventLevelEpsilon,
        mAggregateDebugReportingString,
        mAggregateDebugReportContributions,
        mAggregateContributionBuckets);
  }

  public void setAttributionMode(@AttributionMode int attributionMode) {
    mAttributionMode = attributionMode;
  }

  /**
   * Retrieve the attribution destinations corresponding to their destination type.
   *
   * @return a list of Uris.
   */
  public List<Uri> getAttributionDestinations(@EventSurfaceType int destinationType) {
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

  /** Uri which registered the {@link Source}. */
  public Uri getPublisher() {
    return mPublisher;
  }

  /** The publisher type (e.g., app or web) {@link Source}. */
  @EventSurfaceType
  public int getPublisherType() {
    return mPublisherType;
  }

  /** Uris for the {@link Trigger}'s app destinations. */
  @Nullable
  public List<Uri> getAppDestinations() {
    return mAppDestinations;
  }

  /** Uris for the {@link Trigger}'s web destinations. */
  @Nullable
  public List<Uri> getWebDestinations() {
    return mWebDestinations;
  }

  /** Returns combined list of web and app attribution destinations. */
  public List<Pair<Integer, String>> getAllAttributionDestinations() {
    List<Pair<Integer, String>> destinations = new ArrayList<>();
    if (hasAppDestinations()) {
      for (Uri appDestination : getAppDestinations()) {
        destinations.add(Pair.create(EventSurfaceType.APP, appDestination.toString()));
      }
    }
    if (hasWebDestinations()) {
      for (Uri webDestination : getWebDestinations()) {
        destinations.add(Pair.create(EventSurfaceType.WEB, webDestination.toString()));
      }
    }
    return destinations;
  }

  /** Type of {@link Source}. Values: Event, Navigation. */
  public SourceType getSourceType() {
    return mSourceType;
  }

  /** Time when {@link Source} will expire. */
  public long getExpiryTime() {
    return mExpiryTime;
  }

  /** Returns Event report window */
  public Long getEventReportWindow() {
    return mEventReportWindow;
  }

  /** Returns reinstall reattribution window */
  public long getReinstallReattributionWindow() {
    return mReinstallReattributionWindow;
  }

  /**
   * Time when {@link Source} event report window will expire. (Appends the Event Time to window)
   */
  public long getEffectiveEventReportWindow() {
    if (mEventReportWindow == null) {
      return getExpiryTime();
    }
    // TODO(b/290098169): Cleanup after a few releases
    // Handling cases where ReportWindow is already stored as mEventTime + mEventReportWindow
    if (mEventReportWindow > mEventTime) {
      return mEventReportWindow;
    } else {
      return mEventTime + mEventReportWindow;
    }
  }

  /** JSON string for event report windows */
  public String getEventReportWindows() {
    return mEventReportWindows;
  }

  /** Time when {@link Source} aggregate report window will expire. */
  public long getAggregatableReportWindow() {
    return mAggregatableReportWindow;
  }

  /** Debug key of {@link Source}. */
  @Nullable
  public UnsignedLong getDebugKey() {
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

  /** List of dedup keys used for generating Aggregate Reports. */
  public List<UnsignedLong> getAggregateReportDedupKeys() {
    return mAggregateReportDedupKeys;
  }

  /** Current status of the {@link Source}. */
  @Status
  public int getStatus() {
    return mStatus;
  }

  /** Registrant of this source, primarily an App. */
  public Uri getRegistrant() {
    return mRegistrant;
  }

  /** Selected mode for attribution. Values: Truthfully, Never, Falsely. */
  @AttributionMode
  public int getAttributionMode() {
    return mAttributionMode;
  }

  /** Specification for trigger matching behaviour. Values: Modulus, Exact. */
  public TriggerDataMatching getTriggerDataMatching() {
    return mTriggerDataMatching;
  }

  /** Attribution window for install events. */
  public long getInstallAttributionWindow() {
    return mInstallAttributionWindow;
  }

  /** Cooldown for attributing post-install {@link Trigger} events. */
  public long getInstallCooldownWindow() {
    return mInstallCooldownWindow;
  }

  /** Check if install detection is enabled for the source. */
  public boolean isInstallDetectionEnabled() {
    return getInstallCooldownWindow() > 0 && hasAppDestinations();
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
   * information gain is check because of the computation is complicated. Other straightforward
   * value errors will be show in the debug log using LogUtil
   *
   * @return whether the parameters of flexible are valid
   */
  @VisibleForTesting
  public boolean isFlexEventApiValueValid(Flags flags) {
    return mTriggerSpecs.getInformationGain(this, flags) <= getInformationGainThreshold(flags);
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
   * Returns the shared filter data keys of the source as a unique list of strings. Example:
   * ["click_duration", "campaign_type"]
   */
  @Nullable
  public String getSharedFilterDataKeys() {
    return mSharedFilterDataKeys;
  }

  /** Returns the epsilon value set by source. */
  @Nullable
  public Double getEventLevelEpsilon() {
    return mEventLevelEpsilon;
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
  public Optional<AggregatableAttributionSource> getAggregatableAttributionSource(
      @NonNull Trigger trigger, Flags flags) throws JSONException {
    return flags.getMeasurementEnableLookbackWindowFilter()
        ? getAggregatableAttributionSourceV2(trigger)
        : getAggregatableAttributionSource();
  }

  private Optional<AggregatableAttributionSource> getAggregatableAttributionSource()
      throws JSONException {
    if (mAggregatableAttributionSource == null) {
      if (mAggregateSource == null) {
        mAggregatableAttributionSource = Optional.empty();
        return mAggregatableAttributionSource;
      }
      JSONObject jsonObject = new JSONObject(mAggregateSource);
      TreeMap<String, BigInteger> aggregateSourceMap = new TreeMap<>();
      Iterator<String> keys = jsonObject.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        // Remove "0x" prefix.
        String hexString = jsonObject.getString(key).substring(2);
        BigInteger bigInteger = new BigInteger(hexString, 16);
        aggregateSourceMap.put(key, bigInteger);
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

  private Optional<AggregatableAttributionSource> getAggregatableAttributionSourceV2(
      @NonNull Trigger trigger) throws JSONException {
    if (mAggregateSource == null) {
      return Optional.empty();
    }
    JSONObject jsonObject = new JSONObject(mAggregateSource);
    TreeMap<String, BigInteger> aggregateSourceMap = new TreeMap<>();
    for (String key : jsonObject.keySet()) {
      // Remove "0x" prefix.
      String hexString = jsonObject.getString(key).substring(2);
      BigInteger bigInteger = new BigInteger(hexString, 16);
      aggregateSourceMap.put(key, bigInteger);
    }
    return Optional.of(
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregateSourceMap)
            .setFilterMap(getFilterData(trigger))
            .build());
  }

  /** Returns the registration id. */
  @Nullable
  public String getRegistrationId() {
    return mRegistrationId;
  }

  /**
   * Returns the shared aggregation keys of the source as a unique list of strings. Example:
   * [“campaignCounts”]
   */
  @Nullable
  public String getSharedAggregationKeys() {
    return mSharedAggregationKeys;
  }

  /** Returns the install time of the source which is the same value as event time. */
  @Nullable
  public Long getInstallTime() {
    return mInstallTime;
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
   * Returns actual platform AdID from getAdId() on app source registration, to be matched with a
   * web trigger's {@link Trigger#getDebugAdId()} value at the time of generating reports.
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
  public boolean hasCoarseEventReportDestinations() {
    return mCoarseEventReportDestinations;
  }

  /** Returns registration origin used to register the source */
  public Uri getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Returns trigger specs */
  public String getTriggerSpecsString() {
    return mTriggerSpecsString;
  }

  /**
   * Returns the number of report states for the source (used only for computation and not stored in
   * the datastore)
   */
  private Long getNumStates(Flags flags) {
    if (mNumStates == null) {
      validateAndSetNumReportStates(flags);
    }
    return mNumStates;
  }

  /** Returns flip probability (used only for computation and not stored in the datastore) */
  public Double getFlipProbability(Flags flags) {
    if (mFlipProbability == null) {
      buildPrivacyParameters(flags);
    }
    return mFlipProbability;
  }

  /** Returns max bucket increments */
  public Integer getMaxEventLevelReports() {
    return mMaxEventLevelReports;
  }

  /**
   * Returns the RBR provided or default value for max_event_level_reports
   *
   * @param sourceType Source's Type
   * @param maxEventLevelReports RBR parsed value for max_event_level_reports
   * @param flags Flag values
   */
  @NonNull
  public static Integer getOrDefaultMaxEventLevelReports(
      @NonNull SourceType sourceType,
      @Nullable Integer maxEventLevelReports,
      @NonNull Flags flags) {
    if (maxEventLevelReports == null) {
      return sourceType == Source.SourceType.NAVIGATION
          ? PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS
          : flags.getMeasurementVtcConfigurableMaxEventReportsCount();
    }
    return maxEventLevelReports;
  }

  /** Returns event attribution status of current source */
  public String getEventAttributionStatus() {
    return mEventAttributionStatusString;
  }

  /** Returns privacy parameters */
  public String getPrivacyParameters() {
    return mPrivacyParametersString;
  }

  /** See {@link Source#getAppDestinations()} */
  public void setAppDestinations(@Nullable List<Uri> appDestinations) {
    mAppDestinations = appDestinations;
  }

  /** See {@link Source#getWebDestinations()} */
  public void setWebDestinations(@Nullable List<Uri> webDestinations) {
    mWebDestinations = webDestinations;
  }

  /** Set app install attribution to the {@link Source}. */
  public void setInstallAttributed(boolean isInstallAttributed) {
    mIsInstallAttributed = isInstallAttributed;
  }

  /** Set the number of report states for the {@link Source}. */
  private void setNumStates(long numStates) {
    mNumStates = numStates;
  }

  /** Set flip probability for the {@link Source}. */
  private void setFlipProbability(double flipProbability) {
    mFlipProbability = flipProbability;
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
  public void setStatus(@Status int status) {
    mStatus = status;
  }

  /** Set the aggregate contributions value. */
  public void setAggregateContributions(int aggregateContributions) {
    mAggregateContributions = aggregateContributions;
  }

  /** Set the aggregate debug contributions value. */
  public void setAggregateDebugContributions(int aggregateDebugContributions) {
    mAggregateDebugReportContributions = aggregateDebugContributions;
  }

  /**
   * Generates AggregatableFilterData from aggregate filter string in Source, including entries for
   * source type and duration from source to trigger if lookback window filter is enabled.
   */
  public FilterMap getFilterData(@NonNull Trigger trigger, Flags flags) throws JSONException {
    return flags.getMeasurementEnableLookbackWindowFilter()
        ? getFilterData(trigger)
        : getFilterData();
  }

  private FilterMap getFilterData() throws JSONException {
    if (mFilterData != null) {
      return mFilterData;
    }

    if (mFilterDataString == null || mFilterDataString.isEmpty()) {
      mFilterData = new FilterMap.Builder().build();
    } else {
      mFilterData =
          new FilterMap.Builder().buildFilterData(new JSONObject(mFilterDataString)).build();
    }
    mFilterData
        .getAttributionFilterMap()
        .put("source_type", Collections.singletonList(mSourceType.getValue()));
    return mFilterData;
  }

  private FilterMap getFilterData(@NonNull Trigger trigger) throws JSONException {
    FilterMap.Builder builder = new FilterMap.Builder();
    if (mFilterDataString != null && !mFilterDataString.isEmpty()) {
      builder.buildFilterDataV2(new JSONObject(mFilterDataString));
    }
    builder
        .addStringListValue("source_type", Collections.singletonList(mSourceType.getValue()))
        .addLongValue(
            FilterMap.LOOKBACK_WINDOW,
            TimeUnit.MILLISECONDS.toSeconds(trigger.getTriggerTime() - mEventTime));
    return builder.build();
  }

  private <V> Map<String, V> extractSharedFilterMapFromJson(Map<String, V> attributionFilterMap)
      throws JSONException {
    Map<String, V> sharedAttributionFilterMap = new HashMap<>();
    JSONArray sharedFilterDataKeysArray = new JSONArray(mSharedFilterDataKeys);
    for (int i = 0; i < sharedFilterDataKeysArray.length(); ++i) {
      String filterKey = sharedFilterDataKeysArray.getString(i);
      if (attributionFilterMap.containsKey(filterKey)) {
        sharedAttributionFilterMap.put(filterKey, attributionFilterMap.get(filterKey));
      }
    }
    return sharedAttributionFilterMap;
  }

  /**
   * Generates AggregatableFilterData from aggregate filter string in Source, including entries for
   * source type and duration from source to trigger if lookback window filter is enabled.
   */
  public FilterMap getSharedFilterData(@NonNull Trigger trigger, Flags flags) throws JSONException {
    FilterMap filterMap = getFilterData(trigger, flags);
    if (mSharedFilterDataKeys == null) {
      return filterMap;
    }
    if (flags.getMeasurementEnableLookbackWindowFilter()) {
      return new FilterMap.Builder()
          .setAttributionFilterMapWithLongValue(
              extractSharedFilterMapFromJson(filterMap.getAttributionFilterMapWithLongValue()))
          .build();
    } else {
      return new FilterMap.Builder()
          .setAttributionFilterMap(
              extractSharedFilterMapFromJson(filterMap.getAttributionFilterMap()))
          .build();
    }
  }

  @Nullable
  public UnsignedLong getSharedDebugKey() {
    return mSharedDebugKey;
  }

  /** Returns true if the source should be dropped when the app is already installed. */
  public boolean shouldDropSourceIfInstalled() {
    return mDropSourceIfInstalled;
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
      List<Uri> destinations, List<Uri> otherDestinations) {
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
  public void parseEventAttributionStatus() throws JSONException {
    JSONArray eventAttributionStatus = new JSONArray(mEventAttributionStatusString);
    for (int i = 0; i < eventAttributionStatus.length(); i++) {
      JSONObject json = eventAttributionStatus.getJSONObject(i);
      mAttributedTriggers.add(new AttributedTrigger(json));
    }
  }

  /** Build the attributed triggers list from the raw string */
  public void buildAttributedTriggers() throws JSONException {
    if (mAttributedTriggers == null) {
      mAttributedTriggers = new ArrayList<>();
      if (mEventAttributionStatusString != null && !mEventAttributionStatusString.isEmpty()) {
        parseEventAttributionStatus();
      }
    }
  }

  /** Build the trigger specs from the raw string */
  public void buildTriggerSpecs() throws JSONException {
    buildAttributedTriggers();
    if (mTriggerSpecs == null) {
      mTriggerSpecs =
          new TriggerSpecs(
              mTriggerSpecsString,
              getOrDefaultMaxEventLevelReports(
                  mSourceType, mMaxEventLevelReports, FlagsFactory.getFlags()),
              this,
              mPrivacyParametersString);
    }
  }

  /** Returns the attribution scopes attached to the source. */
  @Nullable
  public List<String> getAttributionScopes() {
    return mAttributionScopes;
  }

  /** Sets the attribution scopes. */
  public void setAttributionScopes(@Nullable List<String> attributionScopes) {
    mAttributionScopes = attributionScopes;
  }

  /** Returns the attribution scope limit for the source. It should be positive. */
  @Nullable
  public Long getAttributionScopeLimit() {
    return mAttributionScopeLimit;
  }

  /** Returns max number of event states. It should be positive. */
  @Nullable
  public Long getMaxEventStates() {
    return mMaxEventStates;
  }

  /**
   * Priority of app and web destinations on this source. An incoming or existing source is
   * rejected, if the long-term destination limit is exceeded, based on this value - higher values
   * are retained.
   */
  public long getDestinationLimitPriority() {
    return mDestinationLimitPriority;
  }

  /**
   * Algorithm to use for long term destination limiting. FIFO - remove the lowest priority source,
   * LIFO - reject the incoming source. It does not need to be persisted in the database as we need
   * it only at the time of registration.
   */
  @Nullable
  public DestinationLimitAlgorithm getDestinationLimitAlgorithm() {
    return mDestinationLimitAlgorithm;
  }

  /** Returns the aggregate debug reporting object as a string */
  @Nullable
  public String getAggregateDebugReportingString() {
    return mAggregateDebugReportingString;
  }

  /** Returns the aggregate debug reporting object as a string */
  @Nullable
  public AggregateDebugReporting getAggregateDebugReportingObject() throws JSONException {
    if (mAggregateDebugReportingString == null) {
      return null;
    }
    if (mAggregateDebugReporting == null) {
      mAggregateDebugReporting =
          new AggregateDebugReporting.Builder(new JSONObject(mAggregateDebugReportingString))
              .build();
    }
    return mAggregateDebugReporting;
  }

  /** Returns the aggregate debug reporting contributions */
  public int getAggregateDebugReportContributions() {
    return mAggregateDebugReportContributions;
  }

  /**
   * @return the aggregate contribution object
   */
  @Nullable
  public AggregateContributionBuckets getAggregateContributionBuckets() {
    return mAggregateContributionBuckets;
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
      builder.setReinstallReattributionWindow(copyFrom.mReinstallReattributionWindow);
      builder.setSourceType(copyFrom.mSourceType);
      builder.setAdIdPermission(copyFrom.mAdIdPermission);
      builder.setAggregateContributions(copyFrom.mAggregateContributions);
      builder.setArDebugPermission(copyFrom.mArDebugPermission);
      builder.setAttributionMode(copyFrom.mAttributionMode);
      builder.setDebugKey(copyFrom.mDebugKey);
      builder.setEventReportDedupKeys(copyFrom.mEventReportDedupKeys);
      builder.setAggregateReportDedupKeys(copyFrom.mAggregateReportDedupKeys);
      builder.setEventReportWindow(copyFrom.mEventReportWindow);
      builder.setEventReportWindows(copyFrom.mEventReportWindows);
      builder.setMaxEventLevelReports(copyFrom.mMaxEventLevelReports);
      builder.setAggregatableReportWindow(copyFrom.mAggregatableReportWindow);
      builder.setEnrollmentId(copyFrom.mEnrollmentId);
      builder.setFilterDataString(copyFrom.mFilterDataString);
      builder.setSharedFilterDataKeys(copyFrom.mSharedFilterDataKeys);
      builder.setInstallTime(copyFrom.mInstallTime);
      builder.setIsDebugReporting(copyFrom.mIsDebugReporting);
      builder.setPriority(copyFrom.mPriority);
      builder.setStatus(copyFrom.mStatus);
      builder.setDebugJoinKey(copyFrom.mDebugJoinKey);
      builder.setPlatformAdId(copyFrom.mPlatformAdId);
      builder.setDebugAdId(copyFrom.mDebugAdId);
      builder.setRegistrationOrigin(copyFrom.mRegistrationOrigin);
      builder.setAttributedTriggers(copyFrom.mAttributedTriggers);
      builder.setTriggerSpecs(copyFrom.mTriggerSpecs);
      builder.setTriggerDataMatching(copyFrom.mTriggerDataMatching);
      builder.setTriggerData(copyFrom.mTriggerData);
      builder.setCoarseEventReportDestinations(copyFrom.mCoarseEventReportDestinations);
      builder.setSharedDebugKey(copyFrom.mSharedDebugKey);
      builder.setDropSourceIfInstalled(copyFrom.mDropSourceIfInstalled);
      builder.setAttributionScopes(copyFrom.mAttributionScopes);
      builder.setAttributionScopeLimit(copyFrom.mAttributionScopeLimit);
      builder.setMaxEventStates(copyFrom.mMaxEventStates);
      builder.setDestinationLimitPriority(copyFrom.mDestinationLimitPriority);
      builder.setDestinationLimitAlgorithm(copyFrom.mDestinationLimitAlgorithm);
      builder.setEventLevelEpsilon(copyFrom.mEventLevelEpsilon);
      builder.setAggregateDebugReportingString(copyFrom.mAggregateDebugReportingString);
      builder.setAggregateDebugReportContributions(copyFrom.mAggregateDebugReportContributions);
      builder.setAggregateContributionBuckets(copyFrom.mAggregateContributionBuckets);
      return builder;
    }

    /** See {@link Source#getId()}. */
    @NonNull
    public Builder setId(@NonNull String id) {
      mBuilding.mId = id;
      return this;
    }

    /** See {@link Source#getEventId()}. */
    @NonNull
    public Builder setEventId(UnsignedLong eventId) {
      mBuilding.mEventId = eventId;
      return this;
    }

    /** See {@link Source#getPublisher()}. */
    @NonNull
    public Builder setPublisher(@NonNull Uri publisher) {
      Validation.validateUri(publisher);
      mBuilding.mPublisher = publisher;
      return this;
    }

    /** See {@link Source#getPublisherType()}. */
    @NonNull
    public Builder setPublisherType(@EventSurfaceType int publisherType) {
      mBuilding.mPublisherType = publisherType;
      return this;
    }

    /** See {@link Source#getAppDestinations()}. */
    @NonNull
    public Builder setAppDestinations(@Nullable List<Uri> appDestinations) {
      Optional.ofNullable(appDestinations)
          .ifPresent(
              uris -> {
                Validation.validateNotEmpty(uris);
                if (uris.size() > 1) {
                  throw new IllegalArgumentException("Received more than one app destination");
                }
                Validation.validateUri(uris.toArray(new Uri[0]));
              });
      mBuilding.mAppDestinations = appDestinations;
      return this;
    }

    /** See {@link Source#getWebDestinations()}. */
    @NonNull
    public Builder setWebDestinations(@Nullable List<Uri> webDestinations) {
      Optional.ofNullable(webDestinations)
          .ifPresent(
              uris -> {
                Validation.validateNotEmpty(uris);
                Validation.validateUri(uris.toArray(new Uri[0]));
              });
      mBuilding.mWebDestinations = webDestinations;
      return this;
    }

    /** See {@link Source#getEnrollmentId()}. */
    @NonNull
    public Builder setEnrollmentId(@NonNull String enrollmentId) {
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
    @NonNull
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

    /** See {@link Source#getEventReportWindows()} ()}. */
    public Builder setEventReportWindows(String eventReportWindows) {
      mBuilding.mEventReportWindows = eventReportWindows;
      return this;
    }

    /** See {@link Source#getAggregatableReportWindow()}. */
    public Builder setAggregatableReportWindow(Long aggregateReportWindow) {
      mBuilding.mAggregatableReportWindow = aggregateReportWindow;
      return this;
    }

    /** See {@link Source#getReinstallReattributionWindow()}. */
    public Builder setReinstallReattributionWindow(Long reinstallReattributionWindow) {
      mBuilding.mReinstallReattributionWindow = reinstallReattributionWindow;
      return this;
    }

    /** See {@link Source#getPriority()}. */
    @NonNull
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
    @NonNull
    public Builder setSourceType(@NonNull SourceType sourceType) {
      Validation.validateNonNull(sourceType);
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link Source#getEventReportDedupKeys()}. */
    @NonNull
    public Builder setEventReportDedupKeys(@Nullable List<UnsignedLong> mEventReportDedupKeys) {
      mBuilding.mEventReportDedupKeys = mEventReportDedupKeys;
      return this;
    }

    /** See {@link Source#getAggregateReportDedupKeys()}. */
    @NonNull
    public Builder setAggregateReportDedupKeys(
        @NonNull List<UnsignedLong> mAggregateReportDedupKeys) {
      mBuilding.mAggregateReportDedupKeys = mAggregateReportDedupKeys;
      return this;
    }

    /** See {@link Source#getStatus()}. */
    @NonNull
    public Builder setStatus(@Status int status) {
      mBuilding.mStatus = status;
      return this;
    }

    /** See {@link Source#getRegistrant()} */
    @NonNull
    public Builder setRegistrant(@NonNull Uri registrant) {
      Validation.validateUri(registrant);
      mBuilding.mRegistrant = registrant;
      return this;
    }

    /** See {@link Source#getAttributionMode()} */
    @NonNull
    public Builder setAttributionMode(@AttributionMode int attributionMode) {
      mBuilding.mAttributionMode = attributionMode;
      return this;
    }

    /** See {@link Source#getTriggerDataMatching()} */
    @NonNull
    public Builder setTriggerDataMatching(TriggerDataMatching triggerDataMatching) {
      mBuilding.mTriggerDataMatching = triggerDataMatching;
      return this;
    }

    /** See {@link Source#getInstallAttributionWindow()} */
    @NonNull
    public Builder setInstallAttributionWindow(long installAttributionWindow) {
      mBuilding.mInstallAttributionWindow = installAttributionWindow;
      return this;
    }

    /** See {@link Source#getInstallCooldownWindow()} */
    @NonNull
    public Builder setInstallCooldownWindow(long installCooldownWindow) {
      mBuilding.mInstallCooldownWindow = installCooldownWindow;
      return this;
    }

    /** See {@link Source#isInstallAttributed()} */
    @NonNull
    public Builder setInstallAttributed(boolean installAttributed) {
      mBuilding.mIsInstallAttributed = installAttributed;
      return this;
    }

    /** See {@link Source#getFilterDataString()}. */
    public Builder setFilterDataString(@Nullable String filterMap) {
      mBuilding.mFilterDataString = filterMap;
      return this;
    }

    /** See {@link Source#getSharedFilterDataKeys()}. */
    public Builder setSharedFilterDataKeys(@Nullable String sharedFilterDataKeys) {
      mBuilding.mSharedFilterDataKeys = sharedFilterDataKeys;
      return this;
    }

    /** See {@link Source#getEventLevelEpsilon()} ()}. */
    public Builder setEventLevelEpsilon(@Nullable Double eventLevelEpsilon) {
      mBuilding.mEventLevelEpsilon = eventLevelEpsilon;
      return this;
    }

    /** See {@link Source#getAggregateSource()} */
    @NonNull
    public Builder setAggregateSource(@Nullable String aggregateSource) {
      mBuilding.mAggregateSource = aggregateSource;
      return this;
    }

    /** See {@link Source#getAggregateContributions()} */
    @NonNull
    public Builder setAggregateContributions(int aggregateContributions) {
      mBuilding.mAggregateContributions = aggregateContributions;
      return this;
    }

    /** See {@link Source#getRegistrationId()} */
    @NonNull
    public Builder setRegistrationId(@Nullable String registrationId) {
      mBuilding.mRegistrationId = registrationId;
      return this;
    }

    /** See {@link Source#getSharedAggregationKeys()} */
    @NonNull
    public Builder setSharedAggregationKeys(@Nullable String sharedAggregationKeys) {
      mBuilding.mSharedAggregationKeys = sharedAggregationKeys;
      return this;
    }

    /** See {@link Source#getInstallTime()} */
    @NonNull
    public Builder setInstallTime(@Nullable Long installTime) {
      mBuilding.mInstallTime = installTime;
      return this;
    }

    /** See {@link Source#getParentId()} */
    @NonNull
    public Builder setParentId(@Nullable String parentId) {
      mBuilding.mParentId = parentId;
      return this;
    }

    /** See {@link Source#getAggregatableAttributionSource()} */
    @NonNull
    public Builder setAggregatableAttributionSource(
        @Nullable AggregatableAttributionSource aggregatableAttributionSource) {
      mBuilding.mAggregatableAttributionSource = Optional.ofNullable(aggregatableAttributionSource);
      return this;
    }

    /** See {@link Source#getDebugJoinKey()} */
    @NonNull
    public Builder setDebugJoinKey(@Nullable String debugJoinKey) {
      mBuilding.mDebugJoinKey = debugJoinKey;
      return this;
    }

    /** See {@link Source#getPlatformAdId()} */
    @NonNull
    public Builder setPlatformAdId(@Nullable String platformAdId) {
      mBuilding.mPlatformAdId = platformAdId;
      return this;
    }

    /** See {@link Source#getDebugAdId()} */
    @NonNull
    public Builder setDebugAdId(@Nullable String debugAdId) {
      mBuilding.mDebugAdId = debugAdId;
      return this;
    }

    /** See {@link Source#getRegistrationOrigin()} ()} */
    @NonNull
    public Builder setRegistrationOrigin(Uri registrationOrigin) {
      mBuilding.mRegistrationOrigin = registrationOrigin;
      return this;
    }

    /** See {@link Source#getTriggerData()} */
    @NonNull
    public Builder setTriggerData(@NonNull Set<UnsignedLong> triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    /** See {@link Source#getAttributedTriggers()} */
    @NonNull
    public Builder setAttributedTriggers(@NonNull List<AttributedTrigger> attributedTriggers) {
      mBuilding.mAttributedTriggers = attributedTriggers;
      return this;
    }

    /** See {@link Source#getTriggerSpecs()} */
    @NonNull
    public Builder setTriggerSpecs(@Nullable TriggerSpecs triggerSpecs) {
      mBuilding.mTriggerSpecs = triggerSpecs;
      return this;
    }

    /** See {@link Source#hasCoarseEventReportDestinations()} */
    @NonNull
    public Builder setCoarseEventReportDestinations(boolean coarseEventReportDestinations) {
      mBuilding.mCoarseEventReportDestinations = coarseEventReportDestinations;
      return this;
    }

    /** See {@link Source#getTriggerSpecsString()} */
    @NonNull
    public Builder setTriggerSpecsString(@Nullable String triggerSpecsString) {
      mBuilding.mTriggerSpecsString = triggerSpecsString;
      return this;
    }

    /** See {@link Source#getMaxEventLevelReports()} */
    @NonNull
    public Builder setMaxEventLevelReports(@Nullable Integer maxEventLevelReports) {
      mBuilding.mMaxEventLevelReports = maxEventLevelReports;
      return this;
    }

    /** See {@link Source#getEventAttributionStatus()} */
    @NonNull
    public Builder setEventAttributionStatus(@Nullable String eventAttributionStatus) {
      mBuilding.mEventAttributionStatusString = eventAttributionStatus;
      return this;
    }

    /** See {@link Source#getPrivacyParameters()} */
    @NonNull
    public Builder setPrivacyParameters(@Nullable String privacyParameters) {
      mBuilding.mPrivacyParametersString = privacyParameters;
      return this;
    }

    /** See {@link Source#getSharedDebugKey()}. */
    @NonNull
    public Builder setSharedDebugKey(@Nullable UnsignedLong sharedDebugKey) {
      mBuilding.mSharedDebugKey = sharedDebugKey;
      return this;
    }

    /** See {@link Source#shouldDropSourceIfInstalled()}. */
    @NonNull
    public Builder setDropSourceIfInstalled(boolean dropSourceIfInstalled) {
      mBuilding.mDropSourceIfInstalled = dropSourceIfInstalled;
      return this;
    }

    /** See {@link Source#getAttributionScopes()}. */
    @NonNull
    public Builder setAttributionScopes(@Nullable List<String> attributionScopes) {
      mBuilding.mAttributionScopes = attributionScopes;
      return this;
    }

    /** See {@link Source#getAttributionScopeLimit()}. */
    @NonNull
    public Builder setAttributionScopeLimit(@Nullable Long attributionScopeLimit) {
      mBuilding.mAttributionScopeLimit = attributionScopeLimit;
      return this;
    }

    /** See {@link Source#getMaxEventStates()}. */
    @NonNull
    public Builder setMaxEventStates(@Nullable Long maxEventStates) {
      mBuilding.mMaxEventStates = maxEventStates;
      return this;
    }

    /** See {@link Source#getDestinationLimitPriority()}. */
    @NonNull
    public Builder setDestinationLimitPriority(long destinationLimitPriority) {
      mBuilding.mDestinationLimitPriority = destinationLimitPriority;
      return this;
    }

    /** See {@link Source#getDestinationLimitAlgorithm()}. */
    @NonNull
    public Builder setDestinationLimitAlgorithm(
        @Nullable DestinationLimitAlgorithm destinationLimitAlgorithm) {
      mBuilding.mDestinationLimitAlgorithm = destinationLimitAlgorithm;
      return this;
    }

    /** See {@link Source#getAggregateDebugReportingString()}. */
    @NonNull
    public Builder setAggregateDebugReportingString(
        @Nullable String aggregateDebugReportingString) {
      mBuilding.mAggregateDebugReportingString = aggregateDebugReportingString;
      return this;
    }

    /** See {@link Source#getAggregateDebugReportContributions()}. */
    @NonNull
    public Builder setAggregateDebugReportContributions(int aggregateDebugReportingContributions) {
      mBuilding.mAggregateDebugReportContributions = aggregateDebugReportingContributions;
      return this;
    }

    /** See {@link Source#getAggregateContributionBuckets()}. */
    @NonNull
    public Builder setAggregateContributionBuckets(
        @Nullable AggregateContributionBuckets aggregateContributionBuckets) {
      mBuilding.mAggregateContributionBuckets = aggregateContributionBuckets;
      return this;
    }

    /** Build the {@link Source}. */
    @NonNull
    public Source build() {
      Validation.validateNonNull(
          mBuilding.mPublisher,
          mBuilding.mEnrollmentId,
          mBuilding.mRegistrant,
          mBuilding.mSourceType,
          mBuilding.mAggregateReportDedupKeys,
          mBuilding.mEventReportDedupKeys,
          mBuilding.mRegistrationOrigin);

      return mBuilding;
    }
  }
}
