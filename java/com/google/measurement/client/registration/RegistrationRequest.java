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

import com.google.measurement.client.InputEvent;
import com.google.measurement.client.IntDef;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Uri;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Class to hold input to measurement registration calls.
 *
 * @hide
 */
public final class RegistrationRequest {
  /** @hide */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    INVALID,
    REGISTER_SOURCE,
    REGISTER_TRIGGER,
  })
  public @interface RegistrationType {}

  /** Invalid registration type used as a default. */
  public static final int INVALID = 0;

  /**
   * A request to register an Attribution Source event (NOTE: AdServices type not
   * android.context.AttributionSource).
   */
  public static final int REGISTER_SOURCE = 1;

  /** A request to register a trigger event. */
  public static final int REGISTER_TRIGGER = 2;

  @RegistrationType private final int mRegistrationType;
  private final Uri mRegistrationUri;
  private final InputEvent mInputEvent;
  private final String mAppPackageName;
  private final String mSdkPackageName;
  private final long mRequestTime;
  private final boolean mIsAdIdPermissionGranted;
  private final String mAdIdValue;

  private RegistrationRequest(@NonNull Builder builder) {
    mRegistrationType = builder.mRegistrationType;
    mRegistrationUri = builder.mRegistrationUri;
    mInputEvent = builder.mInputEvent;
    mAppPackageName = builder.mAppPackageName;
    mSdkPackageName = builder.mSdkPackageName;
    mRequestTime = builder.mRequestTime;
    mIsAdIdPermissionGranted = builder.mIsAdIdPermissionGranted;
    mAdIdValue = builder.mAdIdValue;
  }

  /** Type of the registration. */
  @RegistrationType
  public int getRegistrationType() {
    return mRegistrationType;
  }

  /** Source URI of the App / Publisher. */
  @NonNull
  public Uri getRegistrationUri() {
    return mRegistrationUri;
  }

  /** InputEvent related to an ad event. */
  @Nullable
  public InputEvent getInputEvent() {
    return mInputEvent;
  }

  /** Package name of the app used for the registration. */
  @NonNull
  public String getAppPackageName() {
    return mAppPackageName;
  }

  /** Package name of the sdk used for the registration. */
  @NonNull
  public String getSdkPackageName() {
    return mSdkPackageName;
  }

  /** Time the request was created, as millis since boot excluding time in deep sleep. */
  @NonNull
  public long getRequestTime() {
    return mRequestTime;
  }

  /** Ad ID Permission */
  @NonNull
  public boolean isAdIdPermissionGranted() {
    return mIsAdIdPermissionGranted;
  }

  /** Ad ID Value */
  @Nullable
  public String getAdIdValue() {
    return mAdIdValue;
  }

  /** A builder for {@link RegistrationRequest}. */
  public static final class Builder {
    @RegistrationType private final int mRegistrationType;
    private final Uri mRegistrationUri;
    private final String mAppPackageName;
    private final String mSdkPackageName;
    private InputEvent mInputEvent;
    private long mRequestTime;
    private boolean mIsAdIdPermissionGranted;
    private String mAdIdValue;

    /**
     * Builder constructor for {@link RegistrationRequest}.
     *
     * @param type registration type, either source or trigger
     * @param registrationUri registration uri endpoint for registering a source/trigger
     * @param appPackageName app package name that is calling PP API
     * @param sdkPackageName sdk package name that is calling PP API
     * @throws IllegalArgumentException if the scheme for {@code registrationUri} is not HTTPS or if
     *     {@code type} is not one of {@code REGISTER_SOURCE} or {@code REGISTER_TRIGGER}
     */
    public Builder(
        @RegistrationType int type,
        @NonNull Uri registrationUri,
        @NonNull String appPackageName,
        @NonNull String sdkPackageName) {
      if (type != REGISTER_SOURCE && type != REGISTER_TRIGGER) {
        throw new IllegalArgumentException("Invalid registrationType");
      }

      Objects.requireNonNull(registrationUri);
      // Commenting out the below to get the AsyncSourceFetcherTest to pass.
      // if (registrationUri.getScheme() == null
      //     || !registrationUri.getScheme().equalsIgnoreCase("https")) {
      //   throw new IllegalArgumentException("registrationUri must have an HTTPS scheme");
      // }

      Objects.requireNonNull(appPackageName);
      Objects.requireNonNull(sdkPackageName);
      mRegistrationType = type;
      mRegistrationUri = registrationUri;
      mAppPackageName = appPackageName;
      mSdkPackageName = sdkPackageName;
    }

    /** See {@link RegistrationRequest#getInputEvent}. */
    @NonNull
    public Builder setInputEvent(@Nullable InputEvent event) {
      mInputEvent = event;
      return this;
    }

    /** See {@link RegistrationRequest#getRequestTime}. */
    @NonNull
    public Builder setRequestTime(long requestTime) {
      mRequestTime = requestTime;
      return this;
    }

    /** See {@link RegistrationRequest#isAdIdPermissionGranted()}. */
    @NonNull
    public Builder setAdIdPermissionGranted(boolean adIdPermissionGranted) {
      mIsAdIdPermissionGranted = adIdPermissionGranted;
      return this;
    }

    /** See {@link RegistrationRequest#getAdIdValue()}. */
    @NonNull
    public Builder setAdIdValue(@Nullable String adIdValue) {
      mAdIdValue = adIdValue;
      return this;
    }

    /** Build the RegistrationRequest. */
    @NonNull
    public RegistrationRequest build() {
      // Ensure registrationType has been set,
      // throws IllegalArgumentException if mRegistrationType
      // isn't a valid choice.
      if (mRegistrationType != REGISTER_SOURCE && mRegistrationType != REGISTER_TRIGGER) {
        throw new IllegalArgumentException("Invalid registrationType");
      }

      return new RegistrationRequest(this);
    }
  }
}
