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

package com.google.measurement.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Class for keeping track of aggregate contribution buckets */
public class AggregateContributionBuckets {
  private Map<String, BucketContributionAndCapacity> mBucketNameToAggregateContributionAndCapacity =
      new HashMap<>();

  private static class BucketContributionAndCapacity {
    public int mAggregateContribution;
    public int mBucketCapacity;

    BucketContributionAndCapacity(int capacity) {
      mAggregateContribution = 0;
      mBucketCapacity = capacity;
    }

    @Override
    public int hashCode() {
      return Objects.hash(mAggregateContribution, mBucketCapacity);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof BucketContributionAndCapacity)) {
        return false;
      }
      BucketContributionAndCapacity bucketContributionAndCapacity =
          (BucketContributionAndCapacity) obj;
      return bucketContributionAndCapacity.mAggregateContribution == mAggregateContribution
          && bucketContributionAndCapacity.mBucketCapacity == mBucketCapacity;
    }
  }

  /**
   * @return the bucket's budget
   */
  public Optional<Integer> maybeGetBucketCapacity(String bucketName) {
    if (mBucketNameToAggregateContributionAndCapacity.containsKey(bucketName)) {
      return Optional.of(
          mBucketNameToAggregateContributionAndCapacity.get(bucketName).mBucketCapacity);
    }
    return Optional.empty();
  }

  /**
   * @return the aggregate contribution towards the bucket
   */
  public Optional<Integer> maybeGetBucketContribution(String bucketName) {
    if (mBucketNameToAggregateContributionAndCapacity.containsKey(bucketName)) {
      return Optional.of(
          mBucketNameToAggregateContributionAndCapacity.get(bucketName).mAggregateContribution);
    }
    return Optional.empty();
  }

  /**
   * @return whether the contribution was successfully added to the bucket or not
   */
  public boolean addToBucket(String bucketName, int contribution) {
    if (mBucketNameToAggregateContributionAndCapacity.get(bucketName).mAggregateContribution
            + contribution
        > mBucketNameToAggregateContributionAndCapacity.get(bucketName).mBucketCapacity) {
      return false;
    }

    // Add contribution to mAggregateContributionsPerBucket for the bucket.
    mBucketNameToAggregateContributionAndCapacity.get(bucketName).mAggregateContribution +=
        contribution;
    return true;
  }

  /** Set capacity for the bucket's aggregate contributions */
  public void createCapacityBucket(String bucketName, int maxBudget) {
    BucketContributionAndCapacity bucketContributionAndCapacity =
        new BucketContributionAndCapacity(maxBudget);

    mBucketNameToAggregateContributionAndCapacity.put(bucketName, bucketContributionAndCapacity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mBucketNameToAggregateContributionAndCapacity);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AggregateContributionBuckets)) {
      return false;
    }
    AggregateContributionBuckets aggregateContributionBuckets = (AggregateContributionBuckets) obj;
    return Objects.equals(
        mBucketNameToAggregateContributionAndCapacity,
        aggregateContributionBuckets.mBucketNameToAggregateContributionAndCapacity);
  }
}
