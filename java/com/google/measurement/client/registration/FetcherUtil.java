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
package com.google.measurement.client.registration;

import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;

import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.AllowLists;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.FilterMap;
import com.google.measurement.client.Flags;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.stats.MeasurementRegistrationResponseStats;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.Source;
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebAddresses;
import com.google.measurement.client.aggregation.AggregateDebugReportData.AggregateDebugReportDataHeaderContract;
import com.google.measurement.client.aggregation.AggregateDebugReporting.AggregateDebugReportingHeaderContract;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.util.UnsignedLong;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Common handling for Response Based Registration
 *
 * @hide
 */
public class FetcherUtil {
  static final Pattern HEX_PATTERN = Pattern.compile("\\p{XDigit}+");
  static final String DEFAULT_HEX_STRING = "0x0";
  public static final BigInteger BIG_INTEGER_LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
  public static final BigDecimal BIG_DECIMAL_INT_MAX_VALUE = BigDecimal.valueOf(Integer.MAX_VALUE);
  public static final BigDecimal BIG_DECIMAL_INT_MIN_VALUE = BigDecimal.valueOf(Integer.MIN_VALUE);

  /**
   * Determine all redirects.
   *
   * <p>Generates a map of: (redirectType, List&lt;Uri&gt;)
   */
  static Map<AsyncRegistration.RedirectType, List<Uri>> parseRedirects(
      @NonNull Map<String, List<String>> headers) {
    Map<AsyncRegistration.RedirectType, List<Uri>> uriMap = new HashMap<>();
    uriMap.put(AsyncRegistration.RedirectType.LOCATION, parseLocationRedirects(headers));
    uriMap.put(AsyncRegistration.RedirectType.LIST, parseListRedirects(headers));
    return uriMap;
  }

  /** Check HTTP response codes that indicate a redirect. */
  static boolean isRedirect(int responseCode) {
    return (responseCode / 100) == 3;
  }

  /** Check HTTP response code for success. */
  static boolean isSuccess(int responseCode) {
    return (responseCode / 100) == 2;
  }

  /** Validates both string type and unsigned long parsing */
  public static Optional<UnsignedLong> extractUnsignedLong(JSONObject obj, String key) {
    try {
      Object maybeValue = obj.get(key);
      if (!(maybeValue instanceof String)) {
        return Optional.empty();
      }
      return Optional.of(new UnsignedLong((String) maybeValue));
    } catch (JSONException | NumberFormatException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "extractUnsignedLong: caught exception. Key: %s", key);
      return Optional.empty();
    }
  }

  /** Validates both string type and long parsing */
  public static Optional<Long> extractLongString(JSONObject obj, String key) {
    try {
      Object maybeValue = obj.get(key);
      if (!(maybeValue instanceof String)) {
        return Optional.empty();
      }
      return Optional.of(Long.parseLong((String) maybeValue));
    } catch (JSONException | NumberFormatException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "extractLongString: caught exception. Key: %s", key);
      return Optional.empty();
    }
  }

  /** Validates an integral number */
  public static boolean is64BitInteger(Object obj) {
    return (obj instanceof Integer) || (obj instanceof Long);
  }

  /** Validates both number type and long parsing */
  public static Optional<Long> extractLong(JSONObject obj, String key) {
    try {
      Object maybeValue = obj.get(key);
      if (!is64BitInteger(maybeValue)) {
        return Optional.empty();
      }
      return Optional.of(Long.parseLong(String.valueOf(maybeValue)));
    } catch (JSONException | NumberFormatException e) {
      LoggerFactory.getMeasurementLogger().e(e, "extractLong: caught exception. Key: %s", key);
      return Optional.empty();
    }
  }

  private static boolean isIntegral(BigDecimal value) {
    // Simplified check using scale only
    return value.stripTrailingZeros().scale() <= 0;
  }

  /** Extract value of a numeric integral from JSONObject. */
  public static Optional<BigDecimal> extractIntegralValue(JSONObject obj, String key) {
    try {
      Object maybeObject = obj.get(key);
      Optional<BigDecimal> maybeIntegralValue = extractIntegralValue(maybeObject);
      if (maybeIntegralValue.isPresent()) {
        return maybeIntegralValue;
      }
    } catch (JSONException | NumberFormatException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "extractIntegralValue: caught exception. Key: %s", key);
      return Optional.empty();
    }
    return Optional.empty();
  }

  /** Extract value of a numeric integral Object. */
  public static Optional<BigDecimal> extractIntegralValue(Object maybeIntegralValue) {
    if (!(maybeIntegralValue instanceof Number)) {
      LoggerFactory.getMeasurementLogger()
          .e(
              "extractIntegralValue: non numeric object given: %s",
              String.valueOf(maybeIntegralValue));
      return Optional.empty();
    }

    BigDecimal bd = new BigDecimal(maybeIntegralValue.toString());
    if (!isIntegral(bd)) {
      LoggerFactory.getMeasurementLogger()
          .e(
              "extractIntegralValue: non integral value found: %s",
              String.valueOf(maybeIntegralValue));
      return Optional.empty();
    }

    return Optional.of(bd);
  }

  /** Extract value of an int from a map. */
  public static Optional<Integer> extractIntegralInt(JSONObject map, String id) {
    Optional<BigDecimal> maybeBigDecimal = FetcherUtil.extractIntegralValue(map, id);
    if (maybeBigDecimal.isEmpty()) {
      LoggerFactory.getMeasurementLogger()
          .d("extractIntegralInt: value for" + " bucket %s is not an integer.", id);
      return Optional.empty();
    }
    BigDecimal integralValue = maybeBigDecimal.get();
    if (integralValue.compareTo(BIG_DECIMAL_INT_MAX_VALUE) > 0
        || integralValue.compareTo(BIG_DECIMAL_INT_MIN_VALUE) < 0) {
      LoggerFactory.getMeasurementLogger()
          .d("extractIntegralInt: value is larger than int. %s", integralValue);
      return Optional.empty();
    }

    return Optional.of(integralValue.intValue());
  }

  private static boolean isValidLookbackWindow(JSONObject obj) {
    Optional<BigDecimal> bd = extractIntegralValue(obj, FilterMap.LOOKBACK_WINDOW);
    if (bd.isEmpty()) {
      return false;
    }

    BigDecimal lookbackWindowValue = bd.get();
    if (lookbackWindowValue.compareTo(BigDecimal.ZERO) <= 0) {
      LoggerFactory.getMeasurementLogger()
          .e(
              "isValidLookbackWindow: non positive lookback window found: %s",
              lookbackWindowValue.toString());
      return false;
    }

    return true;
  }

  /** Extract string from an obj with max length. */
  public static Optional<String> extractString(Object obj, int maxLength) {
    if (!(obj instanceof String)) {
      LoggerFactory.getMeasurementLogger().e("obj should be a string.");
      return Optional.empty();
    }
    String stringValue = (String) obj;
    if (stringValue.length() > maxLength) {
      LoggerFactory.getMeasurementLogger()
          .e("Length of string value should be non-empty and smaller than " + maxLength);
      return Optional.empty();
    }
    return Optional.of(stringValue);
  }

  /** Extract list of strings from an obj with max array size and max string length. */
  public static Optional<List<String>> extractStringArray(
      JSONObject json, String key, int maxArraySize, int maxStringLength) throws JSONException {
    JSONArray jsonArray = json.getJSONArray(key);
    if (jsonArray.length() > maxArraySize) {
      LoggerFactory.getMeasurementLogger()
          .e("Json array size should not be greater " + "than " + maxArraySize);
      return Optional.empty();
    }
    List<String> strings = new ArrayList<>();
    for (int i = 0; i < jsonArray.length(); ++i) {
      Optional<String> string = FetcherUtil.extractString(jsonArray.get(i), maxStringLength);
      if (string.isEmpty()) {
        return Optional.empty();
      }
      strings.add(string.get());
    }
    return Optional.of(strings);
  }

  /** Validate aggregate key ID. */
  static boolean isValidAggregateKeyId(String id) {
    return id != null
        && !id.isEmpty()
        && id.getBytes(StandardCharsets.UTF_8).length
            <= FlagsFactory.getFlags().getMeasurementMaxBytesPerAttributionAggregateKeyId();
  }

  /** Validate aggregate deduplication key. */
  static boolean isValidAggregateDeduplicationKey(String deduplicationKey) {
    if (deduplicationKey == null || deduplicationKey.isEmpty()) {
      return false;
    }
    try {
      Long.parseUnsignedLong(deduplicationKey);
    } catch (NumberFormatException exception) {
      return false;
    }
    return true;
  }

  /** Validate aggregate key-piece. */
  static boolean isValidAggregateKeyPiece(String keyPiece, Flags flags) {
    if (keyPiece == null || keyPiece.isEmpty()) {
      return false;
    }
    int length = keyPiece.getBytes(StandardCharsets.UTF_8).length;
    if (!(keyPiece.startsWith("0x") || keyPiece.startsWith("0X"))) {
      return false;
    }
    // Key-piece is restricted to a maximum of 128 bits and the hex strings therefore have
    // at most 32 digits.
    if (length < 3 || length > 34) {
      return false;
    }
    if (!HEX_PATTERN.matcher(keyPiece.substring(2)).matches()) {
      return false;
    }
    return true;
  }

  /** Validate attribution filters JSONArray. */
  static boolean areValidAttributionFilters(
      @NonNull JSONArray filterSet,
      Flags flags,
      boolean canIncludeLookbackWindow,
      boolean shouldCheckFilterSize)
      throws JSONException {
    if (shouldCheckFilterSize
        && filterSet.length() > FlagsFactory.getFlags().getMeasurementMaxFilterMapsPerFilterSet()) {
      return false;
    }
    for (int i = 0; i < filterSet.length(); i++) {
      if (!areValidAttributionFilters(
          filterSet.optJSONObject(i), flags, canIncludeLookbackWindow, shouldCheckFilterSize)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Parses header error debug report opt-in info from "Attribution-Reporting-Info" header. The
   * header is a structured header and only supports dictionary format. Check HTTP [RFC8941]
   * Section3.2 for details.
   *
   * <p>Examples of this type of header:
   *
   * <ul>
   *   <li>"Attribution-Reporting-Info":“report-header-errors=?0"
   *   <li>"Attribution-Reporting-Info": “report-header-errors,chrome-param=value"
   *   <li>"Attribution-Reporting-Info": "report-header-errors=?1;chrome-param=value,
   *       report-header-errors=?0"
   * </ul>
   *
   * <p>The header may contain information that is only used in Chrome. Android will ignore it and
   * be less strict in parsing in the current version. When "report-header-errors" value can't be
   * extracted, Android will skip sending the debug report instead of dropping the whole
   * registration.
   */
  public static boolean isHeaderErrorDebugReportEnabled(
      @Nullable List<String> attributionInfoHeaders, Flags flags) {
    if (attributionInfoHeaders == null || attributionInfoHeaders.size() == 0) {
      return false;
    }
    if (!flags.getMeasurementEnableDebugReport()
        || !flags.getMeasurementEnableHeaderErrorDebugReport()) {
      LoggerFactory.getMeasurementLogger().d("Debug report is disabled for header errors.");
      return false;
    }

    // When there are multiple headers or the same key appears multiple times, find the last
    // appearance and get the value.
    for (int i = attributionInfoHeaders.size() - 1; i >= 0; i--) {
      String[] parsed = attributionInfoHeaders.get(i).split("[,;]+");
      for (int j = parsed.length - 1; j >= 0; j--) {
        String parsedStr = parsed[j].trim();
        if (parsedStr.equals("report-header-errors")
            || parsedStr.equals("report-header-errors=?1")) {
          return true;
        } else if (parsedStr.equals("report-header-errors=?0")) {
          return false;
        }
      }
    }
    // Skip sending the debug report when the key is not found.
    return false;
  }

  /** Validate attribution filters JSONObject. */
  static boolean areValidAttributionFilters(
      JSONObject filtersObj,
      Flags flags,
      boolean canIncludeLookbackWindow,
      boolean shouldCheckFilterSize)
      throws JSONException {
    if (filtersObj == null) {
      return false;
    }
    if (shouldCheckFilterSize
        && filtersObj.length() > FlagsFactory.getFlags().getMeasurementMaxAttributionFilters()) {
      return false;
    }

    Iterator<String> keys = filtersObj.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (shouldCheckFilterSize
          && key.getBytes(StandardCharsets.UTF_8).length
              > FlagsFactory.getFlags().getMeasurementMaxBytesPerAttributionFilterString()) {
        return false;
      }
      // Process known reserved keys that start with underscore first, then invalidate on
      // catch-all.
      if (flags.getMeasurementEnableLookbackWindowFilter()
          && FilterMap.LOOKBACK_WINDOW.equals(key)) {
        if (!canIncludeLookbackWindow || !isValidLookbackWindow(filtersObj)) {
          return false;
        }
        continue;
      }
      // Invalidate catch-all reserved prefix.
      if (key.startsWith(FilterMap.RESERVED_PREFIX)) {
        return false;
      }
      JSONArray values = filtersObj.optJSONArray(key);
      if (values == null) {
        return false;
      }
      if (shouldCheckFilterSize
          && values.length()
              > FlagsFactory.getFlags().getMeasurementMaxValuesPerAttributionFilter()) {
        return false;
      }
      for (int i = 0; i < values.length(); i++) {
        Object value = values.get(i);
        if (!(value instanceof String)) {
          return false;
        }
        if (shouldCheckFilterSize
            && ((String) value).getBytes(StandardCharsets.UTF_8).length
                > FlagsFactory.getFlags().getMeasurementMaxBytesPerAttributionFilterString()) {
          return false;
        }
      }
    }
    return true;
  }

  static Optional<String> getValidAggregateDebugReportingWithBudget(
      JSONObject aggregateDebugReporting, Flags flags) throws JSONException {
    int budget = 0;
    if (!aggregateDebugReporting.isNull(AggregateDebugReportingHeaderContract.BUDGET)) {
      Optional<BigDecimal> optionalBudget =
          extractIntegralValue(
              aggregateDebugReporting, AggregateDebugReportingHeaderContract.BUDGET);
      // If the budget is invalid number or not in range, fallback to 0
      int maxSumAggValuesPerSource = flags.getMeasurementMaxSumOfAggregateValuesPerSource();
      budget =
          optionalBudget
              .filter(val -> val.compareTo(BigDecimal.ZERO) >= 0)
              .filter(val -> val.compareTo(new BigDecimal(maxSumAggValuesPerSource)) <= 0)
              .map(BigDecimal::intValue)
              .orElse(0);
    }
    Optional<JSONObject> validAggregateDebugReporting =
        getValidAggregateDebugReportingWithoutBudget(aggregateDebugReporting, flags, budget);
    if (validAggregateDebugReporting.isPresent()) {
      validAggregateDebugReporting.get().put(AggregateDebugReportingHeaderContract.BUDGET, budget);
    }
    return validAggregateDebugReporting.map(JSONObject::toString);
  }

  static Optional<String> getValidAggregateDebugReportingWithoutBudget(
      JSONObject aggregateDebugReporting, Flags flags) throws JSONException {
    return getValidAggregateDebugReportingWithoutBudget(
            aggregateDebugReporting, flags, flags.getMeasurementMaxSumOfAggregateValuesPerSource())
        .map(JSONObject::toString);
  }

  private static Optional<JSONObject> getValidAggregateDebugReportingWithoutBudget(
      JSONObject aggregateDebugReporting, Flags flags, int maxAggregateDebugDataValue)
      throws JSONException {
    JSONObject validAggregateDebugReporting = new JSONObject();
    String keyPiece =
        aggregateDebugReporting.optString(AggregateDebugReportingHeaderContract.KEY_PIECE);
    if (keyPiece.isEmpty()) {
      keyPiece = DEFAULT_HEX_STRING;
    }
    if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece, flags)) {
      LoggerFactory.getMeasurementLogger().d("Aggregate debug reporting key-piece is invalid.");
      return Optional.empty();
    }
    validAggregateDebugReporting.put(AggregateDebugReportingHeaderContract.KEY_PIECE, keyPiece);

    if (!aggregateDebugReporting.isNull(
        AggregateDebugReportingHeaderContract.AGGREGATION_COORDINATOR_ORIGIN)) {
      String origin =
          aggregateDebugReporting.getString(
              AggregateDebugReportingHeaderContract.AGGREGATION_COORDINATOR_ORIGIN);
      String allowlist = flags.getMeasurementAggregationCoordinatorOriginList();
      if (origin.isEmpty() || !isAllowlisted(allowlist, origin)) {
        LoggerFactory.getMeasurementLogger()
            .d("Aggregate debug reporting aggregation coordinator origin is invalid.");
        return Optional.empty();
      }
      validAggregateDebugReporting.put(
          AggregateDebugReportingHeaderContract.AGGREGATION_COORDINATOR_ORIGIN, Uri.parse(origin));
    }
    if (!aggregateDebugReporting.isNull(AggregateDebugReportingHeaderContract.DEBUG_DATA)) {
      Set<String> existingReportTypes = new HashSet<>();
      Optional<JSONArray> maybeValidDebugDataArr =
          getValidAggregateDebugReportingData(
              aggregateDebugReporting.getJSONArray(
                  AggregateDebugReportingHeaderContract.DEBUG_DATA),
              existingReportTypes,
              flags,
              maxAggregateDebugDataValue);
      if (maybeValidDebugDataArr.isEmpty()) {
        return Optional.empty();
      }
      validAggregateDebugReporting.put(
          AggregateDebugReportingHeaderContract.DEBUG_DATA, maybeValidDebugDataArr.get());
    }
    return Optional.of(validAggregateDebugReporting);
  }

  private static Optional<JSONArray> getValidAggregateDebugReportingData(
      JSONArray debugDataArr,
      Set<String> existingReportTypes,
      Flags flags,
      int maxAggregateDebugDataValue)
      throws JSONException {
    JSONArray validDebugDataArr = new JSONArray();
    for (int i = 0; i < debugDataArr.length(); i++) {
      JSONObject debugDataObj = debugDataArr.getJSONObject(i);
      JSONObject validDebugDataObj = new JSONObject();
      if (debugDataObj.isNull(AggregateDebugReportDataHeaderContract.KEY_PIECE)
          || debugDataObj.isNull(AggregateDebugReportDataHeaderContract.VALUE)
          || debugDataObj.isNull(AggregateDebugReportDataHeaderContract.TYPES)) {
        LoggerFactory.getMeasurementLogger()
            .d("Aggregate debug reporting data is missing required keys.");
        return Optional.empty();
      }

      String debugDatakeyPiece =
          debugDataObj.optString(AggregateDebugReportDataHeaderContract.KEY_PIECE);
      if (!FetcherUtil.isValidAggregateKeyPiece(debugDatakeyPiece, flags)) {
        LoggerFactory.getMeasurementLogger()
            .d("Aggregate debug reporting data key-piece is invalid.");
        return Optional.empty();
      }
      validDebugDataObj.put(AggregateDebugReportDataHeaderContract.KEY_PIECE, debugDatakeyPiece);

      Optional<BigDecimal> optionalValue =
          extractIntegralValue(debugDataObj, AggregateDebugReportDataHeaderContract.VALUE);
      if (optionalValue.isEmpty()) {
        LoggerFactory.getMeasurementLogger().d("Aggregate debug data value is invalid.");
        return Optional.empty();
      }
      BigDecimal value = optionalValue.get();
      if (value.compareTo(BigDecimal.ZERO) <= 0
          || value.compareTo(new BigDecimal(maxAggregateDebugDataValue)) > 0) {
        LoggerFactory.getMeasurementLogger().d("Aggregate debug reporting data value is invalid.");
        return Optional.empty();
      }
      validDebugDataObj.put(AggregateDebugReportDataHeaderContract.VALUE, value.intValue());

      Optional<List<String>> maybeDebugDataTypes =
          FetcherUtil.extractStringArray(
              debugDataObj,
              AggregateDebugReportDataHeaderContract.TYPES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE);
      if (maybeDebugDataTypes.isEmpty()) {
        LoggerFactory.getMeasurementLogger()
            .d("Aggregate debug reporting data type must not be empty.");
        return Optional.empty();
      }
      List<String> debugDataTypesList = maybeDebugDataTypes.get();
      List<String> validDebugDataTypes = new ArrayList<>();
      for (String debugDataType : debugDataTypesList) {
        Optional<DebugReportApi.Type> maybeType = DebugReportApi.Type.findByValue(debugDataType);
        // Ignore the type if not recognized
        if (maybeType.isPresent()) {
          if (existingReportTypes.contains(maybeType.get().getValue())) {
            LoggerFactory.getMeasurementLogger()
                .d(
                    "duplicate aggregate debug reporting data types within the"
                        + " same object or across multiple objects are not"
                        + " allowed.");
            return Optional.empty();
          }
          validDebugDataTypes.add(maybeType.get().getValue());
          existingReportTypes.add(maybeType.get().getValue());
        }
      }
      validDebugDataObj.put(
          AggregateDebugReportDataHeaderContract.TYPES, new JSONArray(validDebugDataTypes));

      validDebugDataArr.put(validDebugDataObj);
    }
    return Optional.of(validDebugDataArr);
  }

  private static boolean isAllowlisted(String allowlist, String origin) {
    if (AllowLists.doesAllowListAllowAll(allowlist)) {
      return true;
    }
    Set<String> elements = new HashSet<>(AllowLists.splitAllowList(allowlist));
    return elements.contains(origin);
  }

  static String getSourceRegistrantToLog(AsyncRegistration asyncRegistration) {
    if (asyncRegistration.isSourceRequest()) {
      return asyncRegistration.getRegistrant().toString();
    }

    return "";
  }

  static void emitHeaderMetrics(
      long headerSizeLimitBytes,
      AdServicesLogger logger,
      AsyncRegistration asyncRegistration,
      AsyncFetchStatus asyncFetchStatus,
      @Nullable String enrollmentId) {
    long headerSize = asyncFetchStatus.getResponseSize();
    String adTechDomain = null;

    if (headerSize > headerSizeLimitBytes) {
      adTechDomain =
          WebAddresses.topPrivateDomainAndScheme(asyncRegistration.getRegistrationUri())
              .map(Uri::toString)
              .orElse(null);
    }

    logger.logMeasurementRegistrationsResponseSize(
        new MeasurementRegistrationResponseStats.Builder(
                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                getRegistrationType(asyncRegistration),
                headerSize,
                getSourceType(asyncRegistration),
                getSurfaceType(asyncRegistration),
                getStatus(asyncFetchStatus),
                getFailureType(asyncFetchStatus),
                asyncFetchStatus.getRegistrationDelay(),
                getSourceRegistrantToLog(asyncRegistration),
                asyncFetchStatus.getRetryCount(),
                asyncFetchStatus.isRedirectOnly(),
                asyncFetchStatus.isPARequest(),
                asyncFetchStatus.getNumDeletedEntities(),
                asyncFetchStatus.isEventLevelEpsilonConfigured(),
                asyncFetchStatus.isTriggerAggregatableValueFiltersConfigured())
            .setAdTechDomain(adTechDomain)
            .build(),
        enrollmentId);
  }

  private static List<Uri> parseListRedirects(Map<String, List<String>> headers) {
    List<Uri> redirects = new ArrayList<>();
    List<String> field = headers.get(AsyncRedirects.REDIRECT_LIST_HEADER_KEY);
    int maxRedirects = FlagsFactory.getFlags().getMeasurementMaxRegistrationRedirects();
    if (field != null) {
      for (int i = 0; i < Math.min(field.size(), maxRedirects); i++) {
        redirects.add(Uri.parse(field.get(i)));
      }
    }
    return redirects;
  }

  private static List<Uri> parseLocationRedirects(Map<String, List<String>> headers) {
    List<Uri> redirects = new ArrayList<>();
    List<String> field = headers.get(AsyncRedirects.REDIRECT_LOCATION_HEADER_KEY);
    if (field != null && !field.isEmpty()) {
      redirects.add(Uri.parse(field.get(0)));
      if (field.size() > 1) {
        LoggerFactory.getMeasurementLogger()
            .e("Expected one Location redirect only, others ignored!");
      }
    }
    return redirects;
  }

  public static long calculateHeadersCharactersLength(Map<String, List<String>> headers) {
    long size = 0;
    for (String headerKey : headers.keySet()) {
      if (headerKey != null) {
        size = size + headerKey.length();
        List<String> headerValues = headers.get(headerKey);
        if (headerValues != null) {
          for (String headerValue : headerValues) {
            size = size + headerValue.length();
          }
        }
      }
    }

    return size;
  }

  private static int getRegistrationType(AsyncRegistration asyncRegistration) {
    if (asyncRegistration.isSourceRequest()) {
      return RegistrationEnumsValues.TYPE_SOURCE;
    } else if (asyncRegistration.isTriggerRequest()) {
      return RegistrationEnumsValues.TYPE_TRIGGER;
    } else {
      return RegistrationEnumsValues.TYPE_UNKNOWN;
    }
  }

  private static int getSourceType(AsyncRegistration asyncRegistration) {
    if (asyncRegistration.getSourceType() == Source.SourceType.EVENT) {
      return RegistrationEnumsValues.SOURCE_TYPE_EVENT;
    } else if (asyncRegistration.getSourceType() == Source.SourceType.NAVIGATION) {
      return RegistrationEnumsValues.SOURCE_TYPE_NAVIGATION;
    } else {
      return RegistrationEnumsValues.SOURCE_TYPE_UNKNOWN;
    }
  }

  private static int getSurfaceType(AsyncRegistration asyncRegistration) {
    if (asyncRegistration.isAppRequest()) {
      return RegistrationEnumsValues.SURFACE_TYPE_APP;
    } else if (asyncRegistration.isWebRequest()) {
      return RegistrationEnumsValues.SURFACE_TYPE_WEB;
    } else {
      return RegistrationEnumsValues.SURFACE_TYPE_UNKNOWN;
    }
  }

  private static int getStatus(AsyncFetchStatus asyncFetchStatus) {
    if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.SUCCESS
        || (asyncFetchStatus.getResponseStatus() == AsyncFetchStatus.ResponseStatus.SUCCESS
            && (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.UNKNOWN
                || asyncFetchStatus.getEntityStatus()
                    == AsyncFetchStatus.EntityStatus.HEADER_MISSING))) {
      // successful source/trigger fetching/parsing and successful redirects (with no header)
      return RegistrationEnumsValues.STATUS_SUCCESS;
    } else if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.UNKNOWN
        && asyncFetchStatus.getResponseStatus() == AsyncFetchStatus.ResponseStatus.UNKNOWN) {
      return RegistrationEnumsValues.STATUS_UNKNOWN;
    } else {
      return RegistrationEnumsValues.STATUS_FAILURE;
    }
  }

  private static int getFailureType(AsyncFetchStatus asyncFetchStatus) {
    if (asyncFetchStatus.getResponseStatus() == AsyncFetchStatus.ResponseStatus.NETWORK_ERROR) {
      return RegistrationEnumsValues.FAILURE_TYPE_NETWORK;
    } else if (asyncFetchStatus.getResponseStatus()
        == AsyncFetchStatus.ResponseStatus.INVALID_URL) {
      return RegistrationEnumsValues.FAILURE_TYPE_INVALID_URL;
    } else if (asyncFetchStatus.getResponseStatus()
        == AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE) {
      return RegistrationEnumsValues.FAILURE_TYPE_SERVER_UNAVAILABLE;
    } else if (asyncFetchStatus.getResponseStatus()
        == AsyncFetchStatus.ResponseStatus.HEADER_SIZE_LIMIT_EXCEEDED) {
      return RegistrationEnumsValues.FAILURE_TYPE_HEADER_SIZE_LIMIT_EXCEEDED;
    } else if (asyncFetchStatus.getEntityStatus()
        == AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT) {
      return RegistrationEnumsValues.FAILURE_TYPE_ENROLLMENT;
    } else if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.VALIDATION_ERROR
        || asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.PARSING_ERROR
        || asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.HEADER_ERROR) {
      return RegistrationEnumsValues.FAILURE_TYPE_PARSING;
    } else if (asyncFetchStatus.getEntityStatus() == AsyncFetchStatus.EntityStatus.STORAGE_ERROR) {
      return RegistrationEnumsValues.FAILURE_TYPE_STORAGE;
    } else if (asyncFetchStatus.isRedirectError()) {
      return RegistrationEnumsValues.FAILURE_TYPE_REDIRECT;
    } else {
      return RegistrationEnumsValues.FAILURE_TYPE_UNKNOWN;
    }
  }

  /** AdservicesMeasurementRegistrations atom enum values. */
  public interface RegistrationEnumsValues {
    int TYPE_UNKNOWN = 0;
    int TYPE_SOURCE = 1;
    int TYPE_TRIGGER = 2;
    int SOURCE_TYPE_UNKNOWN = 0;
    int SOURCE_TYPE_EVENT = 1;
    int SOURCE_TYPE_NAVIGATION = 2;
    int SURFACE_TYPE_UNKNOWN = 0;
    int SURFACE_TYPE_WEB = 1;
    int SURFACE_TYPE_APP = 2;
    int STATUS_UNKNOWN = 0;
    int STATUS_SUCCESS = 1;
    int STATUS_FAILURE = 2;
    int FAILURE_TYPE_UNKNOWN = 0;
    int FAILURE_TYPE_PARSING = 1;
    int FAILURE_TYPE_NETWORK = 2;
    int FAILURE_TYPE_ENROLLMENT = 3;
    int FAILURE_TYPE_REDIRECT = 4;
    int FAILURE_TYPE_STORAGE = 5;
    int FAILURE_TYPE_HEADER_SIZE_LIMIT_EXCEEDED = 7;
    int FAILURE_TYPE_SERVER_UNAVAILABLE = 8;
    int FAILURE_TYPE_INVALID_URL = 9;
  }

  /** Schedules an header error verbose debug report. */
  public static void sendHeaderErrorDebugReport(
      boolean isEnabled,
      DebugReportApi debugReportApi,
      DatastoreManager datastoreManager,
      Uri topOrigin,
      Uri registrationOrigin,
      Uri registrant,
      String headerName,
      String enrollmentId,
      @Nullable String originalHeaderString) {
    if (isEnabled) {
      datastoreManager.runInTransaction(
          (dao) -> {
            debugReportApi.scheduleHeaderErrorReport(
                topOrigin,
                registrationOrigin,
                registrant,
                headerName,
                enrollmentId,
                originalHeaderString,
                dao);
          });
    }
  }
}
