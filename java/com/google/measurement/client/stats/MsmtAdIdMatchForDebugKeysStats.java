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

/** Class for AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS atom. */
public class MsmtAdIdMatchForDebugKeysStats {
  /**
   * @return Ad-tech enrollment ID.
   */
  public String getAdTechEnrollmentId() {
    return null;
  }

  /**
   * @return Attribution type.
   */
  public int getAttributionType() {
    return 0;
  }

  /**
   * @return true, if the debug AdID provided by the Ad-tech on web registration matches the
   *     platform AdID value.
   */
  public boolean isMatched() {
    return false;
  }

  /**
   * @return Number of unique AdIDs an Ad-tech has provided for matching.
   */
  public long getNumUniqueAdIds() {
    return 0;
  }

  /**
   * @return Limit on number of unique AdIDs an Ad-tech is allowed.
   */
  public long getNumUniqueAdIdsLimit() {
    return 0;
  }

  /**
   * @return source registrant.
   */
  public String getSourceRegistrant() {
    return null;
  }

  /**
   * @return generic builder.
   */
  public static Builder builder() {
    return new MsmtAdIdMatchForDebugKeysStats.Builder();
  }

  /** Builder class for {@link MsmtAdIdMatchForDebugKeysStats} */
  public static class Builder {
    /** Set Ad-tech enrollment ID. */
    public Builder setAdTechEnrollmentId(String value) {
      return this;
    }

    /** Set attribution type. */
    public Builder setAttributionType(int value) {
      return this;
    }

    /**
     * Set to true, if the debug AdID provided by the Ad-tech on web registration matches the
     * platform AdID value.
     */
    public Builder setMatched(boolean value) {
      return this;
    }

    /** Set number of unique AdIDs an Ad-tech has provided for matching. */
    public Builder setNumUniqueAdIds(long value) {
      return this;
    }

    /** Set limit on number of unique AdIDs an Ad-tech is allowed. */
    public Builder setNumUniqueAdIdsLimit(long value) {
      return this;
    }

    /** Set source registrant. */
    public Builder setSourceRegistrant(String value) {
      return this;
    }

    /** build for {@link MsmtAdIdMatchForDebugKeysStats} */
    public MsmtAdIdMatchForDebugKeysStats build() {
      return null;
    }
  }
}
