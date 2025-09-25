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

package com.google.measurement.client.stats;

import com.google.measurement.client.NonNull;
import java.util.Objects;

/** class for measurement attribution stats. */
public class MeasurementAttributionStats {
  private int mCode;
  private int mSourceType;
  private int mSurfaceType;
  private int mResult;
  private int mFailureType;
  private boolean mIsSourceDerived;
  private boolean mIsInstallAttribution;
  private long mAttributionDelay;
  private String mSourceRegistrant;
  private int mAggregateReportCount;
  private int mNullAggregateReportCount;
  private int mAggregateDebugReportCount;
  private int mEventReportCount;
  private int mEventDebugReportCount;

  public MeasurementAttributionStats() {}

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MeasurementAttributionStats)) {
      return false;
    }
    MeasurementAttributionStats measurementAttributionStats = (MeasurementAttributionStats) obj;
    return mCode == measurementAttributionStats.getCode()
        && mSourceType == measurementAttributionStats.getSourceType()
        && mSurfaceType == measurementAttributionStats.getSurfaceType()
        && mResult == measurementAttributionStats.getResult()
        && mFailureType == measurementAttributionStats.getFailureType()
        && mIsSourceDerived == measurementAttributionStats.isSourceDerived()
        && mIsInstallAttribution == measurementAttributionStats.isInstallAttribution()
        && mAttributionDelay == measurementAttributionStats.getAttributionDelay()
        && Objects.equals(mSourceRegistrant, measurementAttributionStats.getSourceRegistrant())
        && mAggregateReportCount == measurementAttributionStats.getAggregateReportCount()
        && mNullAggregateReportCount == measurementAttributionStats.getNullAggregateReportCount()
        && mAggregateDebugReportCount == measurementAttributionStats.getAggregateDebugReportCount()
        && mEventReportCount == measurementAttributionStats.getEventReportCount()
        && mEventDebugReportCount == measurementAttributionStats.getEventDebugReportCount();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mCode,
        mSourceType,
        mSurfaceType,
        mResult,
        mFailureType,
        mIsSourceDerived,
        mIsInstallAttribution,
        mAttributionDelay,
        mSourceRegistrant,
        mAggregateReportCount,
        mNullAggregateReportCount,
        mAggregateDebugReportCount,
        mEventReportCount,
        mEventDebugReportCount);
  }

  public int getCode() {
    return mCode;
  }

  public int getSourceType() {
    return mSourceType;
  }

  public int getSurfaceType() {
    return mSurfaceType;
  }

  public int getResult() {
    return mResult;
  }

  public int getFailureType() {
    return mFailureType;
  }

  public boolean isSourceDerived() {
    return mIsSourceDerived;
  }

  public boolean isInstallAttribution() {
    return mIsInstallAttribution;
  }

  public long getAttributionDelay() {
    return mAttributionDelay;
  }

  public String getSourceRegistrant() {
    return mSourceRegistrant;
  }

  public int getAggregateReportCount() {
    return mAggregateReportCount;
  }

  public int getNullAggregateReportCount() {
    return mNullAggregateReportCount;
  }

  public int getAggregateDebugReportCount() {
    return mAggregateDebugReportCount;
  }

  public int getEventReportCount() {
    return mEventReportCount;
  }

  public int getEventDebugReportCount() {
    return mEventDebugReportCount;
  }

  /** Builder for {@link MeasurementAttributionStats}. */
  public static final class Builder {
    private final MeasurementAttributionStats mBuilding;

    public Builder() {
      mBuilding = new MeasurementAttributionStats();
    }

    /** See {@link MeasurementAttributionStats#getCode()} . */
    public @NonNull Builder setCode(int code) {
      mBuilding.mCode = code;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getSourceType()} . */
    public @NonNull Builder setSourceType(int sourceType) {
      mBuilding.mSourceType = sourceType;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getSurfaceType()} . */
    public @NonNull Builder setSurfaceType(int surfaceType) {
      mBuilding.mSurfaceType = surfaceType;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getResult()} . */
    public @NonNull Builder setResult(int result) {
      mBuilding.mResult = result;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getFailureType()} . */
    public @NonNull Builder setFailureType(int failureType) {
      mBuilding.mFailureType = failureType;
      return this;
    }

    /** See {@link MeasurementAttributionStats#isSourceDerived()} . */
    public @NonNull Builder setSourceDerived(boolean isSourceDerived) {
      mBuilding.mIsSourceDerived = isSourceDerived;
      return this;
    }

    /** See {@link MeasurementAttributionStats#isSourceDerived()} . */
    public @NonNull Builder setInstallAttribution(boolean isInstallAttribution) {
      mBuilding.mIsInstallAttribution = isInstallAttribution;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getAttributionDelay()} . */
    public @NonNull Builder setAttributionDelay(long attributionDelay) {
      mBuilding.mAttributionDelay = attributionDelay;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getSourceRegistrant()} . */
    public @NonNull Builder setSourceRegistrant(String sourceRegistrant) {
      mBuilding.mSourceRegistrant = sourceRegistrant;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getAggregateReportCount()} . */
    public @NonNull Builder setAggregateReportCount(int aggregateReportCount) {
      mBuilding.mAggregateReportCount = aggregateReportCount;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getNullAggregateReportCount()} . */
    public @NonNull Builder setNullAggregateReportCount(int nullAggregateReportCount) {
      mBuilding.mNullAggregateReportCount = nullAggregateReportCount;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getAggregateDebugReportCount()} . */
    public @NonNull Builder setAggregateDebugReportCount(int aggregateDebugReportCount) {
      mBuilding.mAggregateDebugReportCount = aggregateDebugReportCount;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getEventReportCount()} . */
    public @NonNull Builder setEventReportCount(int eventReportCount) {
      mBuilding.mEventReportCount = eventReportCount;
      return this;
    }

    /** See {@link MeasurementAttributionStats#getEventDebugReportCount()} . */
    public @NonNull Builder setEventDebugReportCount(int eventDebugReportCount) {
      mBuilding.mEventDebugReportCount = eventDebugReportCount;
      return this;
    }

    /** Build the {@link MeasurementAttributionStats}. */
    public @NonNull MeasurementAttributionStats build() {
      return mBuilding;
    }
  }
}
