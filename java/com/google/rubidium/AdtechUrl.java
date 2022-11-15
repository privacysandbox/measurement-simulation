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

import java.util.Objects;

/** POJO for AdtechUrl. */
public class AdtechUrl {

  private String mPostbackUrl;
  private String mAdtechId;

  private AdtechUrl() {
    mPostbackUrl = null;
    mAdtechId = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AdtechUrl)) {
      return false;
    }
    AdtechUrl adtechUrl = (AdtechUrl) obj;
    return Objects.equals(mPostbackUrl, adtechUrl.mPostbackUrl)
        && Objects.equals(mAdtechId, adtechUrl.mAdtechId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mPostbackUrl, mAdtechId);
  }

  /** Unique identifier for a {@link AdtechUrl}. */
  public String getPostbackUrl() {
    return mPostbackUrl;
  }

  /** Ad tech id for the postback url. */
  public String getAdtechId() {
    return mAdtechId;
  }

  /** Builder for {@link AdtechUrl}. */
  public static final class Builder {
    private final AdtechUrl mBuilding;

    public Builder() {
      mBuilding = new AdtechUrl();
    }

    /** See {@link AdtechUrl#getPostbackUrl()}. */
    public Builder setPostbackUrl(String postbackUrl) {
      mBuilding.mPostbackUrl = postbackUrl;
      return this;
    }

    /** See {@link AdtechUrl#getAdtechId()}. */
    public Builder setAdtechId(String adtechId) {
      mBuilding.mAdtechId = adtechId;
      return this;
    }

    /** Build the {@link AdtechUrl}. */
    public AdtechUrl build() {
      return mBuilding;
    }
  }
}
