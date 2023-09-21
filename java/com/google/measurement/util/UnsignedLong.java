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

package com.google.measurement.util;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

public final class UnsignedLong implements Comparable<UnsignedLong>, Serializable {
  private final Long mValue;

  public UnsignedLong(Long value) {
    if (value == null) {
      throw new IllegalArgumentException("Received null values.");
    }
    mValue = value;
  }

  public UnsignedLong(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Received null values.");
    }
    mValue = Long.parseUnsignedLong(value);
  }

  public UnsignedLong() {
    mValue = 0L;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mValue);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof UnsignedLong)) {
      return false;
    }
    UnsignedLong other = (UnsignedLong) obj;
    return Objects.equals(mValue, other.getValue());
  }

  @Override
  public int compareTo(UnsignedLong other) {
    if (mValue >= 0L) {
      if (other.getValue() >= 0L) {
        return Long.compare(mValue, other.getValue());
      } else {
        return -1;
      }
    } else {
      if (other.getValue() >= 0L) {
        return 1;
      } else {
        return Long.compare(mValue, other.getValue());
      }
    }
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(mValue);
  }

  public Long getValue() {
    return mValue;
  }

  /**
   * Returns the {@code BigInteger} mod operation of the unsigned value and modulus as an {@code
   * UnsignedLong}.
   */
  public UnsignedLong mod(int modulus) {
    BigInteger truncated =
        new BigInteger(Long.toUnsignedString(mValue)).mod(BigInteger.valueOf(modulus));
    return new UnsignedLong(truncated.toString());
  }
}
