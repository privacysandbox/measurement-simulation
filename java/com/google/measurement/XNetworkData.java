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

package com.google.measurement;

import com.google.measurement.util.UnsignedLong;
import java.util.Objects;
import java.util.Optional;
import org.json.simple.JSONObject;

/** POJO for XNetworkData. */
public class XNetworkData {
  private final Optional<UnsignedLong> mKeyOffset;

  private XNetworkData(XNetworkData.Builder builder) {
    mKeyOffset = builder.mKeyOffset;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof XNetworkData)) {
      return false;
    }
    XNetworkData xNetworkData = (XNetworkData) obj;
    return Objects.equals(mKeyOffset, xNetworkData.mKeyOffset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mKeyOffset);
  }

  /** Returns the value of keyOffset as Long */
  public Optional<UnsignedLong> getKeyOffset() {
    return mKeyOffset;
  }

  /**
   * Serializes the object as JSONObject.
   *
   * @return serialized JSONObject
   */
  public JSONObject serializeAsJson() {
    JSONObject xNetworkDataJson = new JSONObject();
    if (mKeyOffset.isPresent()) {
      xNetworkDataJson.put(XNetworkDataContract.KEY_OFFSET, mKeyOffset.get());
    }
    return xNetworkDataJson;
  }

  /** Builder for {@link XNetworkData}. */
  public static final class Builder {
    private Optional<UnsignedLong> mKeyOffset;

    public Builder() {
      mKeyOffset = Optional.empty();
    }

    public Builder(JSONObject jsonObject) {
      if (jsonObject.containsKey(XNetworkDataContract.KEY_OFFSET)) {
        long keyOffset = (long) jsonObject.get(XNetworkDataContract.KEY_OFFSET);
        mKeyOffset = Optional.of(new UnsignedLong(keyOffset));
      }
    }

    /** See {@link XNetworkData#getKeyOffset()}. */
    public Builder setKeyOffset(UnsignedLong keyOffset) {
      mKeyOffset = Optional.ofNullable(keyOffset);
      return this;
    }

    /** Build the {@link XNetworkData}. */
    public XNetworkData build() {
      return new XNetworkData(this);
    }
  }

  /** Constants related to XNetworkData. */
  public interface XNetworkDataContract {
    String KEY_OFFSET = "key_offset";
  }
}
