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
import com.google.measurement.client.util.Validation;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** POJO for AggregateDebugReportData. */
public class AggregateDebugReportData {

  private final Set<String> mReportType;
  private final BigInteger mKeyPiece;
  private final int mValue;

  private AggregateDebugReportData(@NonNull AggregateDebugReportData.Builder builder) {
    mReportType = builder.mReportType;
    mKeyPiece = builder.mKeyPiece;
    mValue = builder.mValue;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateDebugReportData aggregateDebugReportData)) {
      return false;
    }
    return Objects.equals(mReportType, aggregateDebugReportData.mReportType)
        && Objects.equals(mKeyPiece, aggregateDebugReportData.mKeyPiece)
        && mValue == aggregateDebugReportData.mValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mReportType, mKeyPiece, mValue);
  }

  /** Returns the value of key report_type. */
  public Set<String> getReportType() {
    return mReportType;
  }

  /** Returns the value of key key_piece. */
  public BigInteger getKeyPiece() {
    return mKeyPiece;
  }

  /** Returns the value of key value. */
  public int getValue() {
    return mValue;
  }

  /** Builder for {@link AggregateDebugReportData}. */
  public static final class Builder {
    private Set<String> mReportType;
    private BigInteger mKeyPiece;
    private int mValue;

    public Builder(Set<String> reportType, BigInteger keyPiece, int value) {
      mReportType = reportType;
      mKeyPiece = keyPiece;
      mValue = value;
    }

    public Builder(JSONObject aggregateDebugReportData) throws JSONException {
      if (!aggregateDebugReportData.isNull(AggregateDebugReportDataHeaderContract.TYPES)) {
        mReportType = new HashSet<>();
        JSONArray reportTypeList =
            aggregateDebugReportData.getJSONArray(AggregateDebugReportDataHeaderContract.TYPES);
        for (int j = 0; j < reportTypeList.length(); j++) {
          String reportTypeString = reportTypeList.getString(j);
          mReportType.add(reportTypeString);
        }
      }
      if (!aggregateDebugReportData.isNull(AggregateDebugReportDataHeaderContract.KEY_PIECE)) {
        String hexString =
            aggregateDebugReportData
                .getString(AggregateDebugReportDataHeaderContract.KEY_PIECE)
                .substring(2);
        mKeyPiece = new BigInteger(hexString, 16);
      }
      if (!aggregateDebugReportData.isNull(AggregateDebugReportDataHeaderContract.VALUE)) {
        mValue = aggregateDebugReportData.getInt(AggregateDebugReportDataHeaderContract.VALUE);
      }
    }

    /** Build the {@link AggregateDebugReportData} */
    @NonNull
    public AggregateDebugReportData build() {
      Validation.validateNonNull(mReportType, mKeyPiece);
      return new AggregateDebugReportData(this);
    }
  }

  public interface AggregateDebugReportDataHeaderContract {
    String TYPES = "types";
    String KEY_PIECE = "key_piece";
    String VALUE = "value";
  }
}
