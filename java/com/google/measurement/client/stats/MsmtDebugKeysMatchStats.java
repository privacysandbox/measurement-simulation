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

package com.google.measurement.client.stats;

/** Class for AD_SERVICES_MEASUREMENT_DEBUG_KEYS_MATCH atom. */
public class MsmtDebugKeysMatchStats {

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
   * @return true, if debug join keys match between source and trigger.
   */
  public boolean isMatched() {
    return false;
  }

  /**
   * @return Hashed value of debug join key.
   */
  public long getDebugJoinKeyHashedValue() {
    return 0;
  }

  /**
   * @return Hash limit used to hash the debug join key value.
   */
  public long getDebugJoinKeyHashLimit() {
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
    return new MsmtDebugKeysMatchStats.Builder();
  }

  /** Builder class for {@link MsmtDebugKeysMatchStats}. */
  public static class Builder {
    /** Set Ad-tech enrollment ID. */
    public Builder setAdTechEnrollmentId(String value) {
      return this;
    }

    /** Set attribution type. */
    public Builder setAttributionType(int value) {
      return this;
    }

    /** Set to true, if debug join keys match between source and trigger. */
    public Builder setMatched(boolean value) {
      return this;
    }

    /** Set debug join key hashed value. */
    public Builder setDebugJoinKeyHashedValue(long value) {
      return this;
    }

    /** Set limit of debug join key hashed value is calculated. */
    public Builder setDebugJoinKeyHashLimit(long value) {
      return this;
    }

    /** Set source registrant. */
    public Builder setSourceRegistrant(String value) {
      return this;
    }

    /** build for {@link MsmtDebugKeysMatchStats}. */
    public MsmtDebugKeysMatchStats build() {
      return null;
    }
  }
}
