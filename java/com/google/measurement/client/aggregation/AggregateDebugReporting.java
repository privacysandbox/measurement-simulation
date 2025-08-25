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
import com.google.measurement.client.Uri;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** POJO for AggregateDebugReporting. */
public class AggregateDebugReporting {

  private final int mBudget;
  private final BigInteger mKeyPiece;
  private final List<AggregateDebugReportData> mAggregateDebugReportDataList;
  private final Uri mAggregationCoordinatorOrigin;

  private AggregateDebugReporting(@NonNull AggregateDebugReporting.Builder builder) {
    mBudget = builder.mBudget;
    mKeyPiece = builder.mKeyPiece;
    mAggregateDebugReportDataList = builder.mAggregateDebugReportDataList;
    mAggregationCoordinatorOrigin = builder.mAggregationCoordinatorOrigin;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateDebugReporting aggregateDebugReporting)) {
      return false;
    }
    return mBudget == aggregateDebugReporting.mBudget
        && Objects.equals(mKeyPiece, aggregateDebugReporting.mKeyPiece)
        && Objects.equals(
            mAggregateDebugReportDataList, aggregateDebugReporting.mAggregateDebugReportDataList)
        && Objects.equals(
            mAggregationCoordinatorOrigin, aggregateDebugReporting.mAggregationCoordinatorOrigin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mBudget, mKeyPiece, mAggregateDebugReportDataList, mAggregationCoordinatorOrigin);
  }

  /** Returns the value of the aggregate_debug_report key budget. */
  public int getBudget() {
    return mBudget;
  }

  /** Returns the value of the aggregate_debug_report key key_piece. */
  public BigInteger getKeyPiece() {
    return mKeyPiece;
  }

  /** Returns the value of the aggregate_debug_report key data. */
  public List<AggregateDebugReportData> getAggregateDebugReportDataList() {
    return mAggregateDebugReportDataList;
  }

  /** Returns the value of the aggregate_debug_report key aggregation_coordinator_origin. */
  public Uri getAggregationCoordinatorOrigin() {
    return mAggregationCoordinatorOrigin;
  }

  /** Builder for {@link AggregateDebugReporting}. */
  public static final class Builder {
    private int mBudget;
    private BigInteger mKeyPiece;
    private List<AggregateDebugReportData> mAggregateDebugReportDataList;
    private Uri mAggregationCoordinatorOrigin;

    public Builder(
        int budget,
        BigInteger keyPiece,
        List<AggregateDebugReportData> aggregateDebugReportDataList,
        Uri aggregationCoordinatorOrigin) {
      mBudget = budget;
      mKeyPiece = keyPiece;
      mAggregateDebugReportDataList = aggregateDebugReportDataList;
      mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
    }

    public Builder(JSONObject aggregateDebugReporting) throws JSONException {
      if (!aggregateDebugReporting.isNull(AggregateDebugReportingHeaderContract.BUDGET)) {
        mBudget = aggregateDebugReporting.getInt(AggregateDebugReportingHeaderContract.BUDGET);
      }
      if (!aggregateDebugReporting.isNull(AggregateDebugReportingHeaderContract.KEY_PIECE)) {
        String hexString =
            aggregateDebugReporting
                .getString(AggregateDebugReportingHeaderContract.KEY_PIECE)
                .substring(2);
        mKeyPiece = new BigInteger(hexString, 16);
      }
      if (!aggregateDebugReporting.isNull(AggregateDebugReportingHeaderContract.DEBUG_DATA)) {
        mAggregateDebugReportDataList = new ArrayList<>();
        JSONArray aggregateDebugReportDataArray =
            aggregateDebugReporting.getJSONArray(AggregateDebugReportingHeaderContract.DEBUG_DATA);
        for (int i = 0; i < aggregateDebugReportDataArray.length(); i++) {
          mAggregateDebugReportDataList.add(
              new AggregateDebugReportData.Builder(aggregateDebugReportDataArray.getJSONObject(i))
                  .build());
        }
      }
      if (!aggregateDebugReporting.isNull(
          AggregateDebugReportingHeaderContract.AGGREGATION_COORDINATOR_ORIGIN)) {
        mAggregationCoordinatorOrigin =
            Uri.parse(
                aggregateDebugReporting.getString(
                    AggregateDebugReportingHeaderContract.AGGREGATION_COORDINATOR_ORIGIN));
      }
    }

    /** Build the {@link AggregateDebugReporting} */
    @NonNull
    public AggregateDebugReporting build() {
      return new AggregateDebugReporting(this);
    }
  }

  public interface AggregateDebugReportingHeaderContract {
    String BUDGET = "budget";
    String KEY_PIECE = "key_piece";
    String DEBUG_DATA = "debug_data";
    String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
  }
}
