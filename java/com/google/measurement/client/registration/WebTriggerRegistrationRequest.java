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
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebTriggerParams;
import java.util.List;
import java.util.Objects;

/** Class to hold input to measurement trigger registration calls from web context. */
public final class WebTriggerRegistrationRequest {
  private static final int WEB_TRIGGER_PARAMS_MAX_COUNT = 80;

  /** Registration info to fetch sources. */
  @NonNull private final List<WebTriggerParams> mWebTriggerParams;

  /** Destination {@link Uri}. */
  @NonNull private final Uri mDestination;

  private WebTriggerRegistrationRequest(@NonNull Builder builder) {
    mWebTriggerParams = builder.mWebTriggerParams;
    mDestination = builder.mDestination;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WebTriggerRegistrationRequest)) return false;
    WebTriggerRegistrationRequest that = (WebTriggerRegistrationRequest) o;
    return Objects.equals(mWebTriggerParams, that.mWebTriggerParams)
        && Objects.equals(mDestination, that.mDestination);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mWebTriggerParams, mDestination);
  }

  /** Getter for trigger params. */
  @NonNull
  public List<WebTriggerParams> getTriggerParams() {
    return mWebTriggerParams;
  }

  /** Getter for destination. */
  @NonNull
  public Uri getDestination() {
    return mDestination;
  }

  /** Builder for {@link WebTriggerRegistrationRequest}. */
  public static final class Builder {
    /**
     * Registration info to fetch triggers. Maximum 80 registrations allowed at once, to be in sync
     * with Chrome platform.
     */
    @NonNull private List<WebTriggerParams> mWebTriggerParams;

    /** Top level origin of publisher app. */
    @NonNull private final Uri mDestination;

    /**
     * Builder constructor for {@link WebTriggerRegistrationRequest}.
     *
     * @param webTriggerParams contains trigger registration parameters, the list should not be
     *     empty
     * @param destination trigger destination {@link Uri}
     */
    public Builder(@NonNull List<WebTriggerParams> webTriggerParams, @NonNull Uri destination) {
      Objects.requireNonNull(webTriggerParams);
      if (webTriggerParams.isEmpty() || webTriggerParams.size() > WEB_TRIGGER_PARAMS_MAX_COUNT) {
        throw new IllegalArgumentException(
            "web trigger params size is not within bounds, size: " + webTriggerParams.size());
      }

      Objects.requireNonNull(destination);
      if (destination.getScheme() == null) {
        throw new IllegalArgumentException("Destination origin must have a scheme.");
      }
      mWebTriggerParams = webTriggerParams;
      mDestination = destination;
    }

    /** Pre-validates parameters and builds {@link WebTriggerRegistrationRequest}. */
    @NonNull
    public WebTriggerRegistrationRequest build() {
      return new WebTriggerRegistrationRequest(this);
    }
  }
}
