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

package com.google.measurement.client.registration;

import com.google.measurement.client.InputEvent;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Uri;
import java.util.List;
import java.util.Objects;

/** Class to hold input to measurement source registration calls. */
public final class SourceRegistrationRequest {
  private static final int REGISTRATION_URIS_MAX_COUNT = 20;

  /** Registration URIs to fetch sources. */
  @NonNull private final List<Uri> mRegistrationUris;

  /**
   * User Interaction {@link InputEvent} used by the AttributionReporting API to distinguish clicks
   * from views. It will be an {@link InputEvent} object (for a click event) or null (for a view
   * event).
   */
  @Nullable private final InputEvent mInputEvent;

  private SourceRegistrationRequest(@NonNull Builder builder) {
    mRegistrationUris = builder.mRegistrationUris;
    mInputEvent = builder.mInputEvent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SourceRegistrationRequest)) return false;
    SourceRegistrationRequest that = (SourceRegistrationRequest) o;
    return Objects.equals(mRegistrationUris, that.mRegistrationUris)
    /* && Objects.equals(mInputEvent, that.mInputEvent)*/ ;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mRegistrationUris /*, mInputEvent */);
  }

  /** Registration URIs to fetch sources. */
  @NonNull
  public List<Uri> getRegistrationUris() {
    return mRegistrationUris;
  }

  /**
   * User Interaction {@link InputEvent} used by the AttributionReporting API to distinguish clicks
   * from views. It will be an {@link InputEvent} object (for a click event) or null (for a view
   * event)
   */
  @Nullable
  public InputEvent getInputEvent() {
    return mInputEvent;
  }

  /** Builder for {@link SourceRegistrationRequest}. */
  public static final class Builder {
    /** Registration {@link Uri}s to fetch sources. */
    @NonNull private final List<Uri> mRegistrationUris;

    /**
     * User Interaction InputEvent used by the attribution reporting API to distinguish clicks from
     * views.
     */
    @Nullable private InputEvent mInputEvent;

    /**
     * Builder constructor for {@link SourceRegistrationRequest}.
     *
     * @param registrationUris source registration {@link Uri}s
     * @throws IllegalArgumentException if the scheme for one or more of the {@code
     *     registrationUris} is not HTTPS
     */
    public Builder(@NonNull List<Uri> registrationUris) {
      Objects.requireNonNull(registrationUris);
      if (registrationUris.isEmpty() || registrationUris.size() > REGISTRATION_URIS_MAX_COUNT) {
        throw new IllegalArgumentException(
            String.format(
                "Requests should have at least 1 and at most %d URIs." + " Request has %d URIs.",
                REGISTRATION_URIS_MAX_COUNT, registrationUris.size()));
      }
      for (Uri registrationUri : registrationUris) {
        if (registrationUri.getScheme() == null
            || !registrationUri.getScheme().equalsIgnoreCase("https")) {
          throw new IllegalArgumentException("registrationUri must have an HTTPS scheme");
        }
      }
      mRegistrationUris = registrationUris;
    }

    /**
     * Setter corresponding to {@link #getInputEvent()}.
     *
     * @param inputEvent User Interaction {@link InputEvent} used by the AttributionReporting API to
     *     distinguish clicks from views. It will be an {@link InputEvent} object (for a click
     *     event) or null (for a view event)
     * @return builder
     */
    @NonNull
    public Builder setInputEvent(@Nullable InputEvent inputEvent) {
      mInputEvent = inputEvent;
      return this;
    }

    /** Pre-validates parameters and builds {@link SourceRegistrationRequest}. */
    @NonNull
    public SourceRegistrationRequest build() {
      return new SourceRegistrationRequest(this);
    }
  }
}
