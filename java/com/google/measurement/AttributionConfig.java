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

import static com.google.measurement.AttributionConfig.AttributionConfigContract.END;
import static com.google.measurement.AttributionConfig.AttributionConfigContract.PRIORITY;
import static com.google.measurement.AttributionConfig.AttributionConfigContract.SOURCE_FILTERS;
import static com.google.measurement.AttributionConfig.AttributionConfigContract.START;
import static com.google.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.google.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

import com.google.measurement.util.Filter;
import com.google.measurement.util.MathUtils;
import com.google.measurement.util.Util;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** POJO for AttributionConfig. */
public class AttributionConfig {
  private final String mSourceAdtech;
  private final Map<Long, Long> mSourcePriorityRange;
  private final List<FilterMap> mSourceFilters;
  private final List<FilterMap> mSourceNotFilters;
  private final Long mSourceExpiryOverride;
  private final Long mPriority;
  private final Long mExpiry;
  private final List<FilterMap> mFilterData;
  private final Long mPostInstallExclusivityWindow;

  private AttributionConfig(AttributionConfig.Builder builder) {
    mSourceAdtech = builder.mSourceAdtech;
    mSourcePriorityRange = builder.mSourcePriorityRange;
    mSourceFilters = builder.mSourceFilters;
    mSourceNotFilters = builder.mSourceNotFilters;
    mSourceExpiryOverride = builder.mSourceExpiryOverride;
    mPriority = builder.mPriority;
    mExpiry = builder.mExpiry;
    mFilterData = builder.mFilterData;
    mPostInstallExclusivityWindow = builder.mPostInstallExclusivityWindow;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AttributionConfig)) {
      return false;
    }
    AttributionConfig attributionConfig = (AttributionConfig) obj;
    return Objects.equals(mSourceAdtech, attributionConfig.mSourceAdtech)
        && Objects.equals(mSourcePriorityRange, attributionConfig.mSourcePriorityRange)
        && Objects.equals(mSourceFilters, attributionConfig.mSourceFilters)
        && Objects.equals(mSourceNotFilters, attributionConfig.mSourceNotFilters)
        && Objects.equals(mSourceExpiryOverride, attributionConfig.mSourceExpiryOverride)
        && Objects.equals(mPriority, attributionConfig.mPriority)
        && Objects.equals(mExpiry, attributionConfig.mExpiry)
        && Objects.equals(mFilterData, attributionConfig.mFilterData)
        && Objects.equals(
            mPostInstallExclusivityWindow, attributionConfig.mPostInstallExclusivityWindow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mSourceAdtech,
        mSourcePriorityRange,
        mSourceFilters,
        mSourceNotFilters,
        mSourceExpiryOverride,
        mPriority,
        mExpiry,
        mFilterData,
        mPostInstallExclusivityWindow);
  }

  /** Returns the source adtech as String. */
  public String getSourceAdtech() {
    return mSourceAdtech;
  }

  /**
   * Returns source priority range JSONObject as a Map of Long values. example:
   * "source_priority_range": { “start”: 100, “end”: 1000 }
   */
  public Map<Long, Long> getSourcePriorityRange() {
    return mSourcePriorityRange;
  }

  /**
   * Returns source filter JSONObject as List of FilterMap. example: "source_filters": {
   * "campaign_type": ["install"], "source_type": ["navigation"] }
   */
  public List<FilterMap> getSourceFilters() {
    return mSourceFilters;
  }

  /**
   * Returns source not filter JSONObject as List of FilterMap. example: "source_not_filters": {
   * "campaign_type": ["install"] }
   */
  public List<FilterMap> getSourceNotFilters() {
    return mSourceNotFilters;
  }

  /**
   * Returns source expiry override as long. Source registration time + source expiry override <
   * trigger time.
   */
  public Long getSourceExpiryOverride() {
    return mSourceExpiryOverride;
  }

  /** Returns the derived priority of the source as long */
  public Long getPriority() {
    return mPriority;
  }

  /** Returns the derived source expiry in {@link java.util.concurrent.TimeUnit#SECONDS} as long. */
  public Long getExpiry() {
    return mExpiry;
  }

  /**
   * Returns the derived filter data of the source as List of FilterMap. example: "filter_data": {
   * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "234"] }
   */
  public List<FilterMap> getFilterData() {
    return mFilterData;
  }

  /** Returns the derived post install exclusivity window as long. */
  public Long getPostInstallExclusivityWindow() {
    return mPostInstallExclusivityWindow;
  }

  /**
   * Serializes the object as JSON. This is consistent with the format that is received in the
   * response headers as well as stored as is in the database.
   *
   * @return serialized JSON object
   */
  public JSONObject serializeAsJson() {
    JSONObject attributionConfig = new JSONObject();
    attributionConfig.put(AttributionConfigContract.SOURCE_NETWORK, mSourceAdtech);
    if (mSourcePriorityRange != null) {
      JSONObject sourcePriorityRange = new JSONObject();
      Long key = mSourcePriorityRange.keySet().iterator().next();
      sourcePriorityRange.put(START, key);
      sourcePriorityRange.put(END, mSourcePriorityRange.get(key));
      attributionConfig.put(AttributionConfigContract.SOURCE_PRIORITY_RANGE, sourcePriorityRange);
    }
    if (mSourceFilters != null) {
      attributionConfig.put(SOURCE_FILTERS, Filter.serializeFilterSet(mSourceFilters));
    }
    if (mSourceNotFilters != null) {
      attributionConfig.put(
          AttributionConfigContract.SOURCE_NOT_FILTERS,
          Filter.serializeFilterSet(mSourceNotFilters));
    }
    if (mSourceExpiryOverride != null) {
      attributionConfig.put(
          AttributionConfigContract.SOURCE_EXPIRY_OVERRIDE, mSourceExpiryOverride);
    }
    if (mPriority != null) {
      attributionConfig.put(PRIORITY, mPriority);
    }
    if (mExpiry != null) {
      attributionConfig.put(AttributionConfigContract.EXPIRY, mExpiry);
    }
    if (mFilterData != null) {
      attributionConfig.put(
          AttributionConfigContract.FILTER_DATA, Filter.serializeFilterSet(mFilterData));
    }
    if (mPostInstallExclusivityWindow != null) {
      attributionConfig.put(
          AttributionConfigContract.POST_INSTALL_EXCLUSIVITY_WINDOW, mPostInstallExclusivityWindow);
    }
    return attributionConfig;
  }

  /** Builder for {@link AttributionConfig}. */
  public static final class Builder {
    private String mSourceAdtech;
    private Map<Long, Long> mSourcePriorityRange;
    private List<FilterMap> mSourceFilters;
    private List<FilterMap> mSourceNotFilters;
    private Long mSourceExpiryOverride;
    private Long mPriority;
    private Long mExpiry;
    private List<FilterMap> mFilterData;
    private Long mPostInstallExclusivityWindow;

    public Builder() {}

    /**
     * Parses the string serialized json object under an {@link AttributionConfig}.
     *
     * @throws IllegalArgumentException if JSON parsing fails
     */
    public Builder(JSONObject attributionConfigsJson) throws IllegalArgumentException {
      if (attributionConfigsJson == null) {
        throw new IllegalArgumentException(
            "AttributionConfig.Builder: Empty or null attributionConfigsJson");
      }
      if (!attributionConfigsJson.containsKey(AttributionConfigContract.SOURCE_NETWORK)) {
        throw new IllegalArgumentException(
            "AttributionConfig.Builder: Required field source_network is not present.");
      }
      mSourceAdtech = (String) attributionConfigsJson.get(AttributionConfigContract.SOURCE_NETWORK);
      if (attributionConfigsJson.containsKey(AttributionConfigContract.SOURCE_PRIORITY_RANGE)) {
        JSONObject sourcePriorityRangeJson =
            (JSONObject)
                attributionConfigsJson.get(AttributionConfigContract.SOURCE_PRIORITY_RANGE);
        mSourcePriorityRange = new HashMap<>();
        mSourcePriorityRange.put(
            Util.parseJsonLong(sourcePriorityRangeJson, START),
            Util.parseJsonLong(sourcePriorityRangeJson, END));
      }
      if (attributionConfigsJson.containsKey(SOURCE_FILTERS)) {
        JSONArray filterSet = Filter.maybeWrapFilters(attributionConfigsJson, SOURCE_FILTERS);
        mSourceFilters = Filter.deserializeFilterSet(filterSet);
      }
      if (attributionConfigsJson.containsKey(AttributionConfigContract.SOURCE_NOT_FILTERS)) {
        JSONArray filterSet =
            Filter.maybeWrapFilters(
                attributionConfigsJson, AttributionConfigContract.SOURCE_NOT_FILTERS);
        mSourceNotFilters = Filter.deserializeFilterSet(filterSet);
      }
      if (attributionConfigsJson.containsKey(AttributionConfigContract.SOURCE_EXPIRY_OVERRIDE)) {
        long override =
            Util.parseJsonLong(
                attributionConfigsJson, AttributionConfigContract.SOURCE_EXPIRY_OVERRIDE);
        mSourceExpiryOverride =
            MathUtils.extractValidNumberInRange(
                override,
                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
      }
      if (attributionConfigsJson.containsKey(PRIORITY)) {
        mPriority = Util.parseJsonLong(attributionConfigsJson, PRIORITY);
      }
      if (attributionConfigsJson.containsKey(AttributionConfigContract.EXPIRY)) {
        long expiry = Util.parseJsonLong(attributionConfigsJson, AttributionConfigContract.EXPIRY);
        mExpiry =
            MathUtils.extractValidNumberInRange(
                expiry,
                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
      }
      if (attributionConfigsJson.containsKey(AttributionConfigContract.FILTER_DATA)) {
        JSONArray filterSet =
            Filter.maybeWrapFilters(attributionConfigsJson, AttributionConfigContract.FILTER_DATA);
        mFilterData = Filter.deserializeFilterSet(filterSet);
      }
      if (attributionConfigsJson.containsKey(
          AttributionConfigContract.POST_INSTALL_EXCLUSIVITY_WINDOW)) {
        mPostInstallExclusivityWindow =
            Util.parseJsonLong(
                attributionConfigsJson, AttributionConfigContract.POST_INSTALL_EXCLUSIVITY_WINDOW);
      }
    }

    /** See {@link AttributionConfig#getSourceAdtech()} */
    public Builder setSourceAdtech(String sourceAdtech) {
      Objects.requireNonNull(sourceAdtech);
      mSourceAdtech = sourceAdtech;
      return this;
    }

    /** See {@link AttributionConfig#getSourcePriorityRange()} */
    public Builder setSourcePriorityRange(Map<Long, Long> sourcePriorityRange) {
      mSourcePriorityRange = sourcePriorityRange;
      return this;
    }

    /** See {@link AttributionConfig#getSourceFilters()} */
    public Builder setSourceFilters(List<FilterMap> sourceFilters) {
      mSourceFilters = sourceFilters;
      return this;
    }

    /** See {@link AttributionConfig#getSourceNotFilters()} */
    public Builder setSourceNotFilters(List<FilterMap> sourceNotFilters) {
      mSourceNotFilters = sourceNotFilters;
      return this;
    }

    /** See {@link AttributionConfig#getSourceExpiryOverride()} */
    public Builder setSourceExpiryOverride(Long sourceExpiryOverride) {
      mSourceExpiryOverride = sourceExpiryOverride;
      return this;
    }

    /** See {@link AttributionConfig#getPriority()} */
    public Builder setPriority(Long priority) {
      mPriority = priority;
      return this;
    }

    /** See {@link AttributionConfig#getExpiry()} */
    public Builder setExpiry(Long expiry) {
      mExpiry = expiry;
      return this;
    }

    /** See {@link AttributionConfig#getFilterData()} */
    public Builder setFilterData(List<FilterMap> filterData) {
      mFilterData = filterData;
      return this;
    }

    /** See {@link AttributionConfig#getPostInstallExclusivityWindow()} */
    public Builder setPostInstallExclusivityWindow(Long postInstallExclusivityWindow) {
      mPostInstallExclusivityWindow = postInstallExclusivityWindow;
      return this;
    }

    /** Build the {@link AttributionConfig}. */
    public AttributionConfig build() {
      Objects.requireNonNull(mSourceAdtech);
      return new AttributionConfig(this);
    }
  }

  /** Attribution Config field keys. */
  public interface AttributionConfigContract {
    String SOURCE_NETWORK = "source_network";
    String SOURCE_PRIORITY_RANGE = "source_priority_range";
    String SOURCE_FILTERS = "source_filters";
    String SOURCE_NOT_FILTERS = "source_not_filters";
    String SOURCE_EXPIRY_OVERRIDE = "source_expiry_override";
    String PRIORITY = "priority";
    String EXPIRY = "expiry";
    String FILTER_DATA = "filter_data";
    String POST_INSTALL_EXCLUSIVITY_WINDOW = "post_install_exclusivity_window";
    String START = "start";
    String END = "end";
  }
}
