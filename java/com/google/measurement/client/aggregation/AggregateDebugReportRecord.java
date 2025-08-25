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

package com.google.measurement.client.aggregation;

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Uri;
import com.google.measurement.client.util.Validation;
import java.util.Objects;

/** POJO for AggregateDebugReportData. */
public class AggregateDebugReportRecord {

  private final long mReportGenerationTime;
  private final Uri mTopLevelRegistrant;
  private final Uri mRegistrantApp;
  private final Uri mRegistrationOrigin;
  @Nullable private final String mSourceId;
  @Nullable private final String mTriggerId;
  private final int mContributions;

  private AggregateDebugReportRecord(@NonNull AggregateDebugReportRecord.Builder builder) {
    mReportGenerationTime = builder.mReportGenerationTime;
    mTopLevelRegistrant = builder.mTopLevelRegistrant;
    mRegistrantApp = builder.mRegistrantApp;
    mRegistrationOrigin = builder.mRegistrationOrigin;
    mSourceId = builder.mSourceId;
    mTriggerId = builder.mTriggerId;
    mContributions = builder.mContributions;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateDebugReportRecord aggregateDebugReportRecord)) {
      return false;
    }
    return mReportGenerationTime == aggregateDebugReportRecord.mReportGenerationTime
        && Objects.equals(mTopLevelRegistrant, aggregateDebugReportRecord.mTopLevelRegistrant)
        && Objects.equals(mRegistrantApp, aggregateDebugReportRecord.mRegistrantApp)
        && Objects.equals(mRegistrationOrigin, aggregateDebugReportRecord.mRegistrationOrigin)
        && Objects.equals(mSourceId, aggregateDebugReportRecord.mSourceId)
        && Objects.equals(mTriggerId, aggregateDebugReportRecord.mTriggerId)
        && mContributions == aggregateDebugReportRecord.mContributions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mReportGenerationTime,
        mTopLevelRegistrant,
        mRegistrantApp,
        mRegistrationOrigin,
        mSourceId,
        mTriggerId,
        mContributions);
  }

  /** Returns the value of report generation time. */
  public long getReportGenerationTime() {
    return mReportGenerationTime;
  }

  /** Returns the value of top level registrant. */
  public Uri getTopLevelRegistrant() {
    return mTopLevelRegistrant;
  }

  /** Returns the value of registrant app. */
  public Uri getRegistrantApp() {
    return mRegistrantApp;
  }

  /** Returns the value of registrant origin. */
  public Uri getRegistrationOrigin() {
    return mRegistrationOrigin;
  }

  /** Returns the value of source id. */
  public String getSourceId() {
    return mSourceId;
  }

  /** Returns the value of trigger id. */
  public String getTriggerId() {
    return mTriggerId;
  }

  /** Returns the value of contributions. */
  public int getContributions() {
    return mContributions;
  }

  /** Builder for {@link AggregateDebugReportRecord}. */
  public static final class Builder {
    private long mReportGenerationTime;
    private Uri mTopLevelRegistrant;
    private Uri mRegistrantApp;
    private Uri mRegistrationOrigin;
    @Nullable private String mSourceId;
    @Nullable private String mTriggerId;
    private final int mContributions;

    public Builder(
        long reportGenerationTime,
        Uri topLevelRegistrant,
        Uri registrantApp,
        Uri registrationOrigin,
        int contributions) {
      mReportGenerationTime = reportGenerationTime;
      mTopLevelRegistrant = topLevelRegistrant;
      mRegistrantApp = registrantApp;
      mRegistrationOrigin = registrationOrigin;
      mContributions = contributions;
    }

    /** See {@link AggregateDebugReportRecord#getSourceId()}. */
    @NonNull
    public Builder setSourceId(@Nullable String sourceId) {
      mSourceId = sourceId;
      return this;
    }

    /** See {@link AggregateDebugReportRecord#getTriggerId()}. */
    @NonNull
    public Builder setTriggerId(@Nullable String triggerId) {
      mTriggerId = triggerId;
      return this;
    }

    /** Build the {@link AggregateDebugReportRecord} */
    public AggregateDebugReportRecord build() {
      Validation.validateNonNull(mTopLevelRegistrant, mRegistrantApp, mRegistrationOrigin);
      return new AggregateDebugReportRecord(this);
    }
  }
}
