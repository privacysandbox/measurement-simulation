/*
 * Copyright (C) 2022 Google LLC
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

package com.google.measurement.client.registration;

import com.google.measurement.client.NonNull;
import java.util.Objects;

/**
 * Internal trigger registration request object to communicate from {@link MeasurementManager} to
 * {@link IMeasurementService}.
 *
 * @hide
 */
public class WebTriggerRegistrationRequestInternal {
  /** Holds input to measurement trigger registration calls from web context. */
  @NonNull private final WebTriggerRegistrationRequest mTriggerRegistrationRequest;

  /** Holds app package info of where the request is coming from. */
  @NonNull private final String mAppPackageName;

  /** Holds sdk package info of where the request is coming from. */
  @NonNull private final String mSdkPackageName;

  /** AD ID Permission Granted. */
  private final boolean mIsAdIdPermissionGranted;

  private WebTriggerRegistrationRequestInternal(@NonNull Builder builder) {
    mTriggerRegistrationRequest = builder.mTriggerRegistrationRequest;
    mAppPackageName = builder.mAppPackageName;
    mSdkPackageName = builder.mSdkPackageName;
    mIsAdIdPermissionGranted = builder.mIsAdIdPermissionGranted;
  }

  /** Getter for {@link #mTriggerRegistrationRequest}. */
  public WebTriggerRegistrationRequest getTriggerRegistrationRequest() {
    return mTriggerRegistrationRequest;
  }

  /** Getter for {@link #mAppPackageName}. */
  public String getAppPackageName() {
    return mAppPackageName;
  }

  /** Getter for {@link #mSdkPackageName}. */
  public String getSdkPackageName() {
    return mSdkPackageName;
  }

  /** Getter for {@link #mIsAdIdPermissionGranted}. */
  public boolean isAdIdPermissionGranted() {
    return mIsAdIdPermissionGranted;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WebTriggerRegistrationRequestInternal)) return false;
    WebTriggerRegistrationRequestInternal that = (WebTriggerRegistrationRequestInternal) o;
    return Objects.equals(mTriggerRegistrationRequest, that.mTriggerRegistrationRequest)
        && Objects.equals(mAppPackageName, that.mAppPackageName)
        && Objects.equals(mSdkPackageName, that.mSdkPackageName)
        && Objects.equals(mIsAdIdPermissionGranted, that.mIsAdIdPermissionGranted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mTriggerRegistrationRequest, mAppPackageName, mSdkPackageName, mIsAdIdPermissionGranted);
  }

  /** Builder for {@link WebTriggerRegistrationRequestInternal}. */
  public static final class Builder {
    /** External trigger registration request from client app SDK. */
    @NonNull private final WebTriggerRegistrationRequest mTriggerRegistrationRequest;

    /** Package name of the app used for the registration. Used to determine the registrant. */
    @NonNull private final String mAppPackageName;

    /** Package name of the sdk used for the registration. */
    @NonNull private final String mSdkPackageName;

    /** AD ID Permission Granted. */
    private boolean mIsAdIdPermissionGranted;

    /**
     * Builder constructor for {@link WebTriggerRegistrationRequestInternal}.
     *
     * @param triggerRegistrationRequest external trigger registration request
     * @param appPackageName app package name that is calling PP API
     * @param sdkPackageName sdk package name that is calling PP API
     */
    public Builder(
        @NonNull WebTriggerRegistrationRequest triggerRegistrationRequest,
        @NonNull String appPackageName,
        @NonNull String sdkPackageName) {
      Objects.requireNonNull(triggerRegistrationRequest);
      Objects.requireNonNull(appPackageName);
      Objects.requireNonNull(sdkPackageName);
      mTriggerRegistrationRequest = triggerRegistrationRequest;
      mAppPackageName = appPackageName;
      mSdkPackageName = sdkPackageName;
    }

    /** Pre-validates parameters and builds {@link WebTriggerRegistrationRequestInternal}. */
    @NonNull
    public WebTriggerRegistrationRequestInternal build() {
      return new WebTriggerRegistrationRequestInternal(this);
    }

    /** See {@link WebTriggerRegistrationRequestInternal#isAdIdPermissionGranted()}. */
    public Builder setAdIdPermissionGranted(boolean isAdIdPermissionGranted) {
      mIsAdIdPermissionGranted = isAdIdPermissionGranted;
      return this;
    }
  }
}
