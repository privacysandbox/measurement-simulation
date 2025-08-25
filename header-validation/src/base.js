/**
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const uint64Regex = /^[0-9]+$/;
const int64Regex = /^-?[0-9]+$/;
const hexadecimalRegex = /^[0-9a-fA-F]+$/;
const alphaNumericRegex = /^[a-zA-Z0-9-_]+$/;
const debugReportTypes = [
  "unspecified",
  "source-destination-limit",
  "source-destination-rate-limit",
  "source-destination-per-day-rate-limit",
  "source-noised",
  "source-storage-limit",
  "source-success",
  "source-unknown-error",
  "source-flexible-event-report-value-error",
  "source-max-event-states-limit",
  "source-scopes-channel-capacity-limit",
  "source-channel-capacity-limit",
  "source-attribution-scope-info-gain-limit",
  "source-destination-global-rate-limit",
  "source-destination-limit-replaced",
  "source-reporting-origin-limit",
  "source-reporting-origin-per-site-limit",
  "source-trigger-state-cardinality-limit",
  "trigger-aggregate-deduplicated",
  "trigger-aggregate-insufficient-budget",
  "trigger-aggregate-no-contributions",
  "trigger-aggregate-report-window-passed",
  "trigger-attributions-per-source-destination-limit",
  "trigger-event-attributions-per-source-destination-limit",
  "trigger-aggregate-attributions-per-source-destination-limit",
  "trigger-event-deduplicated",
  "trigger-event-excessive-reports",
  "trigger-event-low-priority",
  "trigger-event-no-matching-configurations",
  "trigger-event-noise",
  "trigger-event-report-window-passed",
  "trigger-no-matching-filter-data",
  "trigger-no-matching-source",
  "trigger-reporting-origin-limit",
  "trigger-event-storage-limit",
  "trigger-unknown-error",
  "trigger-aggregate-storage-limit",
  "trigger-aggregate-excessive-reports",
  "trigger-event-report-window-not-started",
  "trigger-event-no-matching-trigger-data",
  "header-parsing-error",
  "trigger-aggregate-insufficient-named-budget"];

class State {
  constructor() {
    this.path = [];
    this.errors = [];
    this.warnings = [];
  }

  error(msg) {
    this.errors.push({
      path: [...this.path],
      msg,
      formattedError: `${msg}: \`${[...this.path]}\``,
      });
  }

  warning(msg) {
    this.warnings.push({
      path: [...this.path],
      msg,
      formattedWarning: `${msg}: \`${[...this.path]}\``,
      });
  }

  scope(scope, fn) {
    this.path.push(scope);
    fn();
    this.path.pop();
  }

  result() {
    return {errors: this.errors, warnings: this.warnings};
  }

  validate(obj, checks) {
    Object.entries(checks).forEach(([key, check]) => {
      this.scope(key, () => check(this, key, obj));
    });
  }
}

function getDataType(value) {
  if (value === null) {
    return "null";
  }
  if (typeof value === "boolean") {
    return "boolean";
  }
  if (typeof value === "number") {
    return "number"
  }
  if (typeof value === "string") {
    return "string";
  }
  if (Array.isArray(value)) {
    return "array";
  }
  if (value !== null && typeof value === "object" && value.constructor === Object) {
    return "object";
  }
  return "";
}

function optional(fn = () => {}, metadata) {
  return (state, key, object) => {
    key = key.toLowerCase();
    if (key in object) {
      if (object[key] !== null) {
        result = (metadata === undefined) ? fn(state, object[key]) : fn(state, object[key], metadata);
        return;
      }
    }
  }
}

function string(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    }
    state.error("must be a string");
  }
}

function optString(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    } else if (getDataType(value) === "object") {
      result = (metadata === undefined) ? fn(state, JSON.stringify(value)) : fn(state, JSON.stringify(value), metadata);
      return;
    } else if (getDataType(value) !== "null") {
      result = (metadata === undefined) ? fn(state, String(value)) : fn(state, String(value), metadata);
      return;
    }
    state.error("must be a string or able to cast to string");
  }
}

function optStringFallback(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
    } else if (getDataType(value) === "object") {
      result = (metadata === undefined) ? fn(state, JSON.stringify(value)) : fn(state, JSON.stringify(value), metadata);
    } else if (getDataType(value) !== "null") {
      result = (metadata === undefined) ? fn(state, String(value)) : fn(state, String(value), metadata);
    } else {
      result = (metadata === undefined) ? fn(state, "") : fn(state, "", metadata);
    }
  }
}

function getOptStringFallback(value) {
  if (getDataType(value) === "string") {
    return value;
  } else if (getDataType(value) === "object") {
    return JSON.stringify(value);
  } else if (getDataType(value) !== "null") {
    return String(value);
  }
  return "";
}

function object(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "object") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    }
    state.error("must be an object");
  }
}

function optObject(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "object") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    } else if (getDataType(value) === "string") {
      try {
        if (getDataType(JSON.parse(value)) === "object") {
          result = (metadata === undefined) ? fn(state, JSON.parse(value)) : fn(state, JSON.parse(value), metadata);
          return;
        }
      } catch (err) {
        return state.error("must be an object or able to cast to an object");
      }
    }
    state.error("must be an object or able to cast to an object");
  }
}

function array(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "array") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    }
    state.error("must be an array");
  }
}

function optArray(fn = () => {}, requiredType, metadata) {
  return (state, value) => {
    if (getDataType(value) === "array") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    }

    if (requiredType !== null) {
      if (getDataType(value) === requiredType) {
        result = (metadata === undefined) ? fn(state, [value]) : fn(state, [value], metadata);
        return;
      }
      state.error("must be a " + requiredType + " or an array of " + requiredType);
      return;
    } else {
      if (getDataType(value) !== "null") {
        result = (metadata === undefined) ? fn(state, [value]) : fn(state, [value], metadata);
        return;
      }
      state.error("must be a array or able to cast to array");
      return;
    }
  }
}

function boolean(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    }
    state.error("must be a boolean");
  }
}

function optBoolean(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    } else if (getDataType(value) === "string") {
      if (value.toLowerCase() === "true") {
        result = (metadata === undefined) ? fn(state, true) : fn(state, true, metadata);
        return;
      } else if (value.toLowerCase() === "false") {
        result = (metadata === undefined) ? fn(state, false) : fn(state, false, metadata);
        return;
      }
    }
    state.error("must be a boolean");
  }
}

function optBooleanFallback(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
      return;
    } else if (getDataType(value) === "string") {
      if (value === "true") {
        result = (metadata === undefined) ? fn(state, true) : fn(state, true, metadata);
        return;
      } else if (value === "false") {
        result = (metadata === undefined) ? fn(state, false) : fn(state, false, metadata);
        return;
      }
    }
    result = (metadata === undefined) ? fn(state, false) : fn(state, false, metadata);
    return;
  }
}

function uint32(fn = () => {}, metadata) {
  return (state, value) => {
    if (!uint64Regex.test(value)) {
      state.error(`must be an uint32 (must match ${uint64Regex})`);
      return;
    }

    const max = 2n ** 32n - 1n;
    if (BigInt(value) > max) {
      state.error("must fit in an unsigned 32-bit integer");
      return;
    }
    result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
  }
}

function uint64(fn = () => {}, metadata) {
  return (state, value) => {
    if (!uint64Regex.test(value)) {
      state.error(`must be an uint64 (must match ${uint64Regex})`);
      return;
    }

    const max = 2n ** 64n - 1n;
    if (BigInt(value) > max) {
      state.error("must fit in an unsigned 64-bit integer");
      return;
    }
    result = (metadata === undefined) ? fn(state, Number(value)) : fn(state, Number(value), metadata);
  }
}

function int32(fn = () => {}, metadata) {
  return (state, value) => {
    if (!int64Regex.test(value)) {
      state.error(`must be an int32 (must match ${int64Regex})`);
      return;
    }

    const max = 2n ** (32n - 1n) - 1n;
    const min = (-2n) ** (32n - 1n);
    if (BigInt(value) < min || BigInt(value) > max) {
      state.error("must fit in a signed 32-bit integer");
      return;
    }
    result = (metadata === undefined) ? fn(state, value) : fn(state, value, metadata);
  }
}

function int64(fn = () => {}, metadata) {
  return (state, value) => {
    if (!int64Regex.test(value)) {
      state.error(`must be an int64 (must match ${int64Regex})`);
      return;
    }

    const max = 2n ** (64n - 1n) - 1n;
    const min = (-2n) ** (64n - 1n);
    if (BigInt(value) < min || BigInt(value) > max) {
      state.error("must fit in a signed 64-bit integer");
      return;
    }
    result = (metadata === undefined) ? fn(state, Number(value)) : fn(state, Number(value), metadata);
  }
}

function isUInt32(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!uint64Regex.test(value)) {
    state.error(fieldId + `must be an uint32 (must match ${uint64Regex})`);
    return false;
  }

  const max = 2n ** 32n - 1n;
  if (BigInt(value) > max) {
    state.error(fieldId + "must fit in an unsigned 32-bit integer");
    return false;
  }
  return true;
}

function isUInt64(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!uint64Regex.test(value)) {
    state.error(fieldId + `must be an uint64 (must match ${uint64Regex})`);
    return false;
  }

  const max = 2n ** 64n - 1n;
  if (BigInt(value) > max) {
    state.error(fieldId + "must fit in an unsigned 64-bit integer");
    return false;
  }
  return true;
}

function isUInt64WithWarning(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!uint64Regex.test(value)) {
    state.warning(fieldId + `must be an uint64 (must match ${uint64Regex})`);
    return false;
  }

  const max = 2n ** 64n - 1n;
  if (BigInt(value) > max) {
    state.warning(fieldId + "must fit in an unsigned 64-bit integer");
    return false;
  }
  return true;
}

function isInt32(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!int64Regex.test(value)) {
    state.error(fieldId + `must be an int32 (must match ${int64Regex})`);
    return false;
  }

  const max = 2n ** (32n - 1n) - 1n;
  const min = (-2n) ** (32n - 1n);
  if (BigInt(value) < min || BigInt(value) > max) {
    state.error(fieldId + "must fit in a signed 32-bit integer");
    return false;
  }
  return true;
}

function isInt64(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!int64Regex.test(value)) {
    state.error(fieldId + `must be an int64 (must match ${int64Regex})`);
    return false;
  }

  const max = 2n ** (64n - 1n) - 1n;
  const min = (-2n) ** (64n - 1n);
  if (BigInt(value) < min || BigInt(value) > max) {
    state.error(fieldId + "must fit in a signed 64-bit integer");
    return false;
  }
  return true;
}

function optInt(fn = () => {}, metadata) {
  return (state, value) => {
    if (getDataType(value) === "number" || getDataType(value) === "string") {
      result = (metadata === undefined) ? fn(state, parseInt(value)) : fn(state, parseInt(value), metadata);
      return;
    }
    state.error("must be an integer or able to cast to an integer");
  }
}

function addQuotes(str, char) {
  if (str === "") {
    return str;
  }
  str = str.trim();
  if (!str.startsWith(char)) {
    str = char + str;
  }
  if (!str.endsWith(char)) {
    str = str + char;
  }
  return str + " ";
}

function isValidURL(value, state, fieldId) {
    fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
    try {
      return new URL(value);
    } catch (err) {
      state.error(fieldId + "invalid URL format");
    }
}

function isValidSourceFilterData(state, value, metadata) {
  metadata.flags["can_include_lookback_window"] = false;
  metadata.flags["should_check_filter_size"] = true;
  if ("source_type" in value) {
    state.error("filter: source_type is not allowed");
    return;
  }
  isValidFilterData(state, value, metadata);
  if (state.errors.length === 0) {
    metadata.expected_value["filter_data"] = JSON.stringify(value);
  }
}

function isValidFilterDataParent(state, value, metadata, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (value.length > metadata.flags["max_filter_maps_per_filter_set"]) {
    state.error(fieldId + "array length exceeds the max filter maps per filter set limit");
    return;
  }
  for (let i = 0; i < value.length; i++) {
    isValidFilterData(state, value[i], metadata, fieldId);
    if (state.errors.length > 0) {
      return;
    }
  }
}

function isValidFilterData(state, value, metadata, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (Object.keys(value).length > metadata.flags["max_attribution_filters"]) {
    state.error(fieldId + "exceeded max attribution filters");
    return;
  }
  for (key in value) {
    keyString = addQuotes(key, "'");
    if (metadata.flags["should_check_filter_size"]) {
      let keyByteSize = new Blob([key]).size;
      if (keyByteSize > metadata.flags["max_bytes_per_attribution_filter_string"]) {
        state.error(keyString + "exceeded max bytes per attribution filter string");
        return;
      }
    }
    if (key === "_lookback_window" && metadata.flags["feature-lookback-window-filter"]) {
      if (!metadata.flags["can_include_lookback_window"]) {
        state.error(fieldId + "filter: _lookback_window is not allowed");
        return;
      }
      if(!isInt64(value[key], state, key)) {
        return;
      }
      if(Number(value[key]) < 0) {
        state.error("lookback_window must be a positive number");
        return;
      }
      value[key] = value[key];
      continue;
    }
    if (key.startsWith("_")) {
      state.error(keyString + "filter can not start with underscore");
      return;
    }

    filterValues = value[key];
    if (getDataType(filterValues) !== "array") {
      state.error(keyString + "filter value must be an array");
      return;
    }
    if(filterValues.length > metadata.flags["max_values_per_attribution_filter"]) {
      state.error(keyString + "exceeded max values per attribution filter");
      return;
    }
    for (let i = 0; i < filterValues.length; i++) {
      let filterValue = filterValues[i];
      if (getDataType(filterValue) !== "string") {
        state.error(keyString + "filter values must be strings");
        return;
      }
      let valueByteSize = new Blob([filterValue]).size;
      if (valueByteSize > metadata.flags["max_bytes_per_attribution_filter_string"]) {
        state.error(addQuotes(filterValue, "'") + "exceeded max bytes per attribution filter value string");
        return;
      }
    }
  }
}

function isValidAttributionConfigFilterData(state, value, metadata) {
  for (key in value) {
    let keyString = addQuotes(key, "'");
    if (key === "_lookback_window" && metadata.flags["feature-lookback-window-filter"]) {
      if (getDataType(value[key]) === "null") {
        state.error(keyString + "must be a string or able to cast to string");
        return;
      }
      if(!isInt64(String(value[key]), state, key)) {
        return;
      }
      value[key] = Number(value[key]);
    } else {
      if (getDataType(value[key]) !== "array") {
        state.error(keyString + "filter value must be an array");
        return;
      }
      let filterValues = value[key];
      for (let i = 0; i < filterValues.length; i++) {
        let filterValue = filterValues[i];
        if (getDataType(filterValue) === "null") {
          state.error(keyString + "filter values must be string or able to cast to string");
          return;
        }
        filterValues[i] = String(filterValue);
      }
      value[key] = filterValues;
    }
  }
}

function isValidWebDestinationHost(state, value, metadata) {
  for (element in value) {
    if (getDataType(element) !== "string") {
      state.error("must be a string or an array of strings");
      return;
    }
  }

  if (value.length > metadata.flags["max_distinct_web_destinations_in_source_registration"]) {
    state.error("exceeded max distinct web destinations");
    return;
  }
  if (value.length === 0) {
    state.error("no web destinations present");
    return;
  }

  let webDestinationsArr = [];
  for (let i = 0; i < value.length; i++) {
    let url = isValidURL(value[i], state);
    if (state.errors.length > 0) {
      return;
    }

    if (!url.protocol) {
      state.error("URL is missing scheme/protocol");
      return;
    }
    if (!url.hostname) {
      state.error("URL is missing hostname/domain");
      return;
    }

    let formattedURL = "";
    if (url.protocol === "https:" && (url.hostname === "127.0.0.1" || url.hostname === "localhost")) {
      formattedURL = url.protocol + "//" + url.hostname;
      isValidURL(formattedURL, state);
      if (state.errors.length > 0) {
        return;
      }
    } else {
      // Standardize dot characters and remove trailing dot if present.
      let formattedHostName = url.hostname.toLowerCase().replace(".\u3002\uFF0E\uFF61",".");
      if (formattedHostName.charAt(formattedHostName.length - 1) === ".") {
        formattedHostName = formattedHostName.substring(0, formattedHostName.length - 1);
      }

      if (formattedHostName.length >  metadata.flags["max_web_destination_hostname_character_length"]) {
        state.error("URL hostname/domain exceeds max character length");
        return;
      }

      let hostNameParts = formattedHostName.split(".");
      if (hostNameParts.length > metadata.flags["max_web_destination_hostname_parts"]) {
        state.error("exceeded the max number of URL hostname parts");
        return;
      }

      for (let j = 0; j < hostNameParts.length; j++) {
        let hostNamePart = hostNameParts[j];
        if (hostNamePart.length < metadata.flags["min_web_destination_hostname_part_character_length"] || hostNamePart.length > metadata.flags["max_web_destination_hostname_part_character_length"]) {
          state.error("URL hostname part character length must be in the range of 1-63");
          return;
        }
        if (!hostNamePart.match(alphaNumericRegex)) {
          state.error("URL hostname part character length must alphanumeric, hyphen, or underscore");
          return;
        }

        invalidCharacters = "-_";
        if (invalidCharacters.includes(hostNamePart.charAt(0)) || invalidCharacters.includes(hostNamePart.charAt(hostNamePart.length - 1))) {
          state.error("invalid URL hostname part starting/ending character (hypen/underscore)");
          return;
        }

        if (j === (hostNameParts.length - 1) && hostNamePart.charAt(0).match(uint64Regex)) {
          state.error("last hostname part can not start with a number");
          return;
        }
      }
      formattedURL = url.protocol + "//" + formattedHostName;
      isValidURL(formattedURL, state);
      if (state.errors.length > 0) {
        return;
      }
    }
    webDestinationsArr.push(formattedURL);
  }
  metadata.expected_value["web_destination"] = [...new Set(webDestinationsArr)];
}

function isValidAggregationKeys(state, value, metadata) {
  if (Object.keys(value).length > metadata.flags["max_aggregate_keys_per_source_registration"]) {
    state.error("exceeded max number of aggregation keys per source registration");
    return;
  }

  for (aggregateKey in value) {
    isValidAggregateKeyId(state, aggregateKey, metadata);
    if (state.errors.length > 0) {
      return;
    }

    let aggregateValue = getOptStringFallback(value[aggregateKey]);
    isValidAggregateKeyPiece(state, aggregateValue, metadata);
    if (state.errors.length > 0) {
      return;
    }
  }
  metadata.expected_value["aggregation_keys"] = JSON.stringify(value);
}

function isValidAggregateKeyId(state, value, metadata, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (value === null || value === "") {
    state.error(fieldId + "null or empty aggregate key string");
    return;
  }

  let keyByteSize = new Blob([value]).size;
  if (keyByteSize > metadata.flags["max_bytes_per_attribution_aggregate_key_id"]) {
    state.error(fieldId + "exceeded max bytes per attribution aggregate key id string");
    return;
  }
}

function isValidAggregateKeyPiece(state, value, metadata, fieldId, index) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  indexStr = (index === undefined) ? "" : "element at index: " + index + " ";
  if (value === null || value === "") {
    state.error(fieldId + indexStr + "key piece must not be null or empty string");
    return;
  }

  if (!value.startsWith("0x") && !value.startsWith("0X")) {
    state.error(fieldId + indexStr + "key piece must start with '0x' or '0X'");
    return;
  }

  let valueByteSize = new Blob([value]).size;
  if (valueByteSize < metadata.flags["min_bytes_per_aggregate_value"] || valueByteSize > metadata.flags["max_bytes_per_aggregate_value"]) {
    state.error(fieldId + indexStr + "key piece string size must be in the byte range (3 bytes - 34 bytes)");
    return;
  }

  if(!value.substring(2).match(hexadecimalRegex)) {
    state.error(fieldId + indexStr + "key piece must be hexadecimal");
    return;
  }
}

function isValidSourceNamedBudgets(state, value, metadata) {
  if (Object.keys(value).length > metadata.flags["max_named_budgets_per_source_registration"]) {
    state.error("exceeded max number of named budgets per source registration");
    return;
  }

  for (name in value) {
    if (name.length > metadata.flags["max_length_per_budget_name"]) {
      state.error(addQuotes(name, "'") + "exceeded max length per budget name");
      return;
    }

    let budget = value[name];
    isValidAggregatableBudget(state, budget, metadata);
    if (state.errors.length > 0) {
      return;
    }
  }
  metadata.expected_value["named_budgets"] = JSON.stringify(value);
}

function isValidAggregatableBudget(state, value, metadata) {
  if (getDataType(value) !== "number") {
    state.error("aggregatable budget value must be a number");
    return;
  }
  if(!isInt64(value, state)) {
    return;
  }
  if(value < 0) {
    state.error("aggregatable budget value must be greater than or equal to 0");
    return;
  }
  if(value > metadata.flags["max_sum_of_aggregate_values_per_source"]) {
     state.error("aggregatable budget value exceeds the max sum of aggregate values per source");
     return;
  }
}

function isValidTriggerMatchingData(state, value, metadata) {
  if(value.toLowerCase() !== "modulus" && value.toLowerCase() !== "exact") {
    state.error("value must be 'exact' or 'modulus' (case-insensitive)");
    return;
  }
  metadata.expected_value["trigger_data_matching"] = value.toUpperCase();
}

function isValidSourceAttributionScopes(state, value, metadata) {
  if (isNull(value, "limit") || isNull(value, "values") ) {
    state.error("`limit` and `values` keys should be present");
    return;
  }

  let attributionScopeLimit = value["limit"];
  if (getDataType(attributionScopeLimit) !== "number") {
    state.error("`limit` must be numeric");
    return;
  }
  if (!isInt64(attributionScopeLimit, state, "limit")) {
    return;
  }
  
  let attributionScopeValues = value["values"];
  if (getDataType(attributionScopeValues) !== "array") {
    state.error("`values` must be an array");
      return;
  }
  for (let i = 0; i < attributionScopeValues.length; i++) {
    if (getDataType(attributionScopeValues[i]) !== "string") {
      state.error("`values` must be an array of strings");
      return;
    }
  }

  if (attributionScopeValues.length > metadata.flags["max_attribution_scopes_per_source"]) {
    state.error("`values` length exceeds the max number of scopes per source");
    return;
  }

  for (let i = 0; i < attributionScopeValues.length; i++) {
    let scope = attributionScopeValues[i];
    if (scope.length > metadata.flags["max_attribution_scope_string_length"]) {
      state.error("`values` element at index: " + i + " exceeded max scope string length");
      return;
    }
  }

  let maxEventStates = 3;
  if (!isNull(value, "max_event_states") ) {
    maxEventStates = value["max_event_states"];
    if (getDataType(maxEventStates) !== "number") {
      state.error("`max_event_states` must be numeric");
      return;
    }
    if (!isInt64(maxEventStates, state, "max_event_states")) {
      return;
    }
    if (maxEventStates <= 0) {
      state.error("`max_event_states` must be greater than 0");
      return;
    }
  
    if (maxEventStates > metadata.flags["max_report_states_per_source_registration"]) {
      state.error("`max_event_states` exceeds max report states per source registration");
      return;
    }
  }

  if (attributionScopeLimit <= 0) {
    state.error("`limit` must be greater than zero");
    return;
  }
  if (attributionScopeValues.length > attributionScopeLimit) {
    state.error("`value` array size exceeds the provided `limit`");
    return;
  }
  if (attributionScopeValues.length === 0) {
    state.error("`value` array must not be empty");
    return;
  }
  metadata.expected_value["attribution_scopes"] = attributionScopeValues;
  metadata.expected_value["attribution_scope_limit"] = attributionScopeLimit;
  metadata.expected_value["max_event_states"] = maxEventStates;
}

function isValidTriggerAttributionScopes(state, value, metadata) {
  isValidStringArray(state, value, metadata);
  if (state.errors.length > 0) {
    return;
  }
  metadata.expected_value["attribution_scopes"] = JSON.stringify(value);
}

function isValidAppDestinationHost(state, value, metadata) {
  let url = isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
  if (url.protocol !== "android-app:") {
      state.error("app URL host/scheme is invalid");
  }
  metadata.expected_value["destination"] = [value];
}

function isValidXNetworkKeyMapping(state, value, metadata) {
  for (key in value) {
    xNetworkValue = value[key];

    if (xNetworkValue === null) {
      state.error("all values must be non-null");
      return;
    }

    if (getDataType(xNetworkValue) !== "string") {
      state.error("all values must be strings");
      return;
    }
    
    if (!xNetworkValue.startsWith("0x")) {
      state.error("all values must start with 0x");
      return;
    }
  }
  metadata.expected_value["x_network_key_mapping"] = JSON.stringify(value);
}

function isValidAggregationCoordinatorOrigin(state, value, metadata) {
  if (value === "") {
    state.error("value must be non-empty");
    return;
  }
  isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
  metadata.expected_value["aggregation_coordinator_origin"] = value;
}

function isValidAggregatableSourceRegistrationTime(state, value, metadata) {
  if ((value.toUpperCase() !== "INCLUDE") && (value.toUpperCase() !== "EXCLUDE")) {
    state.error("must equal 'INCLUDE' or 'EXCLUDE' (case-insensitive)");
    return;
  }
  metadata.flags["aggregatable_source_registration_time_value"] = value.toUpperCase();
  metadata.expected_value["aggregatable_source_registration_time"] = value.toUpperCase();
}

function isValidTriggerContextId(state, value, metadata) {
  if (metadata.flags["aggregatable_source_registration_time_value"] === "INCLUDE") {
    state.error("aggregatable_source_registration_time must not have the value 'INCLUDE'");
    return;
  }

  if (value.length > metadata.flags["max_trigger_context_id_string_length"]) {
    state.error("max string length exceeded");
    return;
  }
  metadata.expected_value["trigger_context_id"] = value;
}

function isValidEventTriggerData(state, value, metadata) {
  metadata.flags["can_include_lookback_window"] = true;
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }
  let eventTriggerDataArr = [];
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    let eventTriggerData = {"trigger_data": 0};
    for (objKey in obj) {
      if (objKey === "trigger_data" && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "string") {
          state.error("'trigger_data' must be a string");
          return;
        }
        if (!isUInt64(obj[objKey], state, objKey)) {
          return;
        }
        eventTriggerData[objKey] = Number(obj[objKey]);
      }
      if (objKey === "priority" && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "string") {
          state.error("'priority' must be a string");
          return;
        }
        if (!isInt64(obj[objKey], state, objKey)) {
          return;
        }
        eventTriggerData[objKey] = Number(obj[objKey]);
      }
      if (objKey === "value" && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "number") {
          state.error("'value' must be a number");
          return;
        }
        if (!isInt64(obj[objKey], state, objKey)) {
          return;
        }
        if (obj[objKey] < 1) {
          state.error("'value' must be greater than 0");
          return;
        }
        if (obj[objKey] > metadata.flags["max_bucket_threshold"]) {
          state.error("'value' exceeds max threshold of " + metadata.flags["max_bucket_threshold"]);
          return;
        }
        eventTriggerData[objKey] = obj[objKey];
      }
      if (objKey === "deduplication_key" && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "string") {
          state.error("'deduplication_key' must be a string");
          return;
        }
        if (!isUInt64(obj[objKey], state, objKey)) {
          return;
        }
        eventTriggerData[objKey] = Number(obj[objKey]);
      }
      if ((objKey === "filters" || objKey === "not_filters") && obj[objKey] !== null) {
        let arr = maybeWrapFilters(obj[objKey], state, objKey);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, metadata, objKey);
        if (state.errors.length > 0) {
          return;
        }
        eventTriggerData[objKey] = arr;
      }
    }
    eventTriggerDataArr.push(eventTriggerData);
  }
  metadata.expected_value["event_trigger_data"] = JSON.stringify(eventTriggerDataArr);
}

function isValidAggregatableTriggerData(state, value, metadata) {
  metadata.flags["can_include_lookback_window"] = true;
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }

  for (let i = 0; i < value.length; i++) {
    let keyPiece = "";
    if ("key_piece" in value[i]) {
      keyPiece = getOptStringFallback(value[i]["key_piece"]);
    }
    isValidAggregateKeyPiece(state, keyPiece, metadata);
    if (state.errors.length > 0) {
      return;
    }
    for (objKey in value[i]) {
      if (objKey === "source_keys" && value[i][objKey] !== null) {
        if (getDataType(value[i][objKey]) !== "array") {
          state.error("'source_keys' must be an array");
          return;
        }
        if (metadata.flags["should_check_filter_size"]) {
          if (value[i][objKey].length > metadata.flags["max_aggregate_keys_per_trigger_registration"]) {
            state.error("'source_keys' array size exceeds max aggregate keys per trigger registration limit");
            return;
          }
        }
        for (let j = 0; j < value[i][objKey].length; j++) {
          if (getDataType(value[i][objKey][j]) !== "string") {
            state.error("each element in 'source_keys' must be a string");
            return;
          }
          isValidAggregateKeyId(state, value[i][objKey][j], metadata, objKey);
          if (state.errors.length > 0) {
            return;
          }
        }
      }
      if (isNull(value[i], "source_keys")) {
        value[i]["source_keys"] = [];
      }
      if ((objKey === "filters" || objKey === "not_filters") && value[i][objKey] !== null) {
        let arr = maybeWrapFilters(value[i][objKey], state, objKey);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, metadata, objKey);
        if (state.errors.length > 0) {
          return;
        }
        value[i][objKey] = arr;
      }
      if (objKey === "x_network_data" && value[i][objKey] !== null) {
        if (getDataType(value[i][objKey]) !== "object") {
          state.error("'x_network_data' must be an object");
          return;
        }
      }
    }
  }
  metadata.expected_value["aggregatable_trigger_data"] = JSON.stringify(value);
}

function isValidAggregatableValues(state, value, metadata) {
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  if(metadata.flags["should_check_filter_size"]) {
    if (Object.keys(value).length > metadata.flags["max_aggregate_keys_per_trigger_registration"]) {
      state.error("exceeds max aggregate keys per trigger registration");
      return;
    }
  }
  for (aggregateKey in value) {
    isValidAggregateKeyId(state, aggregateKey, metadata);
    if (state.errors.length > 0) {
      return;
    }
    if (getDataType(value[aggregateKey]) !== "number") {
      state.error("aggregate key value must be a number");
      return;
    }
    if (!isInt32(value[aggregateKey], state)) {
      return;
    }
    if(value[aggregateKey] < 1) {
      state.error("aggregate key value must be greater than 0");
      return;
    }
    if(value[aggregateKey] > metadata.flags["max_sum_of_aggregate_values_per_source"]) {
      state.error("aggregate key value exceeds the max sum of aggregate values per source");
      return;
    }
  }
  metadata.expected_value["aggregatable_values"] = JSON.stringify(value);
}

function isValidTriggerFilters(state, value, metadata) {
  isValidFilters(state, value, metadata, "filters");
}

function isValidTriggerNotFilters(state, value, metadata) {
  isValidFilters(state, value, metadata, "not_filters");
}

function isValidFilters(state, value, metadata, fieldId) {
  metadata.flags["can_include_lookback_window"] = true;
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  let arr = maybeWrapFilters(value, state);
  if (state.errors.length > 0) {
    return;
  }
  isValidFilterDataParent(state, arr, metadata);
  if (state.errors.length > 0) {
    return;
  }
  metadata.expected_value[fieldId] = JSON.stringify(arr);
}

function isValidAggregatableDeduplicationKey(state, value, metadata) {
  metadata.flags["can_include_lookback_window"] = true;
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  if (value.length > metadata.flags["max_aggregate_deduplication_keys_per_registration"]) {
    state.error("exceeds max aggregate deduplication keys per registration limit");
    return;
  }
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }
  let aggregatableDeduplicationKeyArr = [];
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    let aggregatableDeduplicationKeyObj = {};
    for (objKey in obj) {
      if (objKey === "deduplication_key"  && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "string") {
          state.error(addQuotes(objKey, "'") + "must be a string");
          return;
        }
        if (!isUInt64(obj[objKey], state, objKey)) {
          return;
        }
        aggregatableDeduplicationKeyObj[objKey] = Number(obj[objKey]);
      }
      if ((objKey === "filters" || objKey === "not_filters") && obj[objKey] !== null) {
        let arr = maybeWrapFilters(obj[objKey], state, objKey);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, metadata, objKey);
        if (state.errors.length > 0) {
          return;
        }
        aggregatableDeduplicationKeyObj[objKey] = arr;
      }
    }
    aggregatableDeduplicationKeyArr.push(aggregatableDeduplicationKeyObj);
  }
  metadata.expected_value["aggregatable_deduplication_keys"] = JSON.stringify(aggregatableDeduplicationKeyArr);
}

function isValidTriggerNamedBudgets(state, value, metadata) {
  metadata.flags["can_include_lookback_window"] = true;
  metadata.flags["should_check_filter_size"] = !metadata.flags["feature-enable-update-trigger-header-limit"];
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }
  let namedBudgetsArr = [];
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    let namedBudgetObj = {};
    for (objKey in obj) {
      if (objKey === "name"  && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "string") {
          state.error(addQuotes(objKey, "'") + "must be a string");
          return;
        }
        namedBudgetObj[objKey] = obj[objKey];
      }
      if ((objKey === "filters" || objKey === "not_filters") && obj[objKey] !== null) {
        let arr = maybeWrapFilters(obj[objKey], state, objKey);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, metadata, objKey);
        if (state.errors.length > 0) {
          return;
        }
        namedBudgetObj[objKey] = arr;
      }
    }
    namedBudgetsArr.push(namedBudgetObj);
  }
  metadata.expected_value["named_budgets"] = JSON.stringify(namedBudgetsArr);
}

function isValidAttributionConfig(state, value, metadata) {
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }

  let minExpiry = metadata.flags["min_reporting_register_source_expiration_in_seconds"];
  let maxExpiry = metadata.flags["max_reporting_register_source_expiration_in_seconds"];
  let attributionConfigArr = [];
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    let attributionConfigObj = {};
    if (isNull(obj, "source_network")) {
      state.error("'source_network' must be present and non-null in each element/object of the array");
      return;
    }
    for (objKey in obj) {
      if (objKey === "source_network") {
        if (getDataType(obj[objKey]) === "null") {
          state.error(addQuotes(objKey, "'") + "must be a string or able to cast to string");
          return;
        }
        attributionConfigObj[objKey] = String(obj[objKey]);
      }
      if (objKey === "source_priority_range" && obj[objKey] !== null) {
        if (getDataType(obj[objKey]) !== "object") {
          state.error(addQuotes(objKey, "'") + "must be an object");
          return;
        }
        if (!("start" in obj[objKey]) || !("end" in obj[objKey])) {
          state.error(addQuotes(objKey, "'") + "both keys ('start','end') must be present");
          return;
        }
        if (obj[objKey]["start"] === null || obj[objKey]["end"] === null) {
          state.error(addQuotes(objKey, "'") + "both key values (start, end) must be string or able to cast to string");
          return;
        }
        if (!isInt64(obj[objKey]["start"], state, "start") || !isInt64(obj[objKey]["end"], state, "end")) {
          return;
        }
        attributionConfigObj[objKey] = {"start": Number(obj[objKey]["start"]), "end": Number(obj[objKey]["end"])};
      }
      if ((objKey === "source_filters" || objKey === "source_not_filters" || objKey === "filter_data") && obj[objKey] !== null) {
        let arr = maybeWrapFilters(obj[objKey], state, objKey);
        if (state.errors.length > 0) {
          return;
        }
        for (let i = 0; i < arr.length; i++) {
          isValidAttributionConfigFilterData(state, arr[i], metadata);
          if (state.errors.length > 0) {
            return;
          }
        }
        attributionConfigObj[objKey] = arr;
      }
      if ((objKey === "source_expiry_override" || objKey === "expiry") && obj[objKey] !== null) {
        if (!isInt64(obj[objKey], state, objKey)) {
          return;
        }

        let expiryValue = Number(obj[objKey]);
        if (expiryValue < minExpiry) {
          expiryValue = minExpiry;
        } else if (expiryValue > maxExpiry) {
          expiryValue = maxExpiry;
        }
        attributionConfigObj[objKey] = expiryValue;
      }
      if ((objKey === "priority" || objKey === "post_install_exclusivity_window") && obj[objKey] !== null) {
        if (!isInt64(obj[objKey], state, objKey)) {
          return;
        }
        attributionConfigObj[objKey] = Number(obj[objKey]);
      }
    }
    attributionConfigArr.push(attributionConfigObj);
  }
  metadata.expected_value["attribution_config"] = JSON.stringify(attributionConfigArr);
}

function isValidExpiry(state, value, metadata) {
  let minExpiry = metadata.flags["min_reporting_register_source_expiration_in_seconds"];
  let maxExpiry = metadata.flags["max_reporting_register_source_expiration_in_seconds"];
  if (value < minExpiry) {
    value = minExpiry;
  } else if (value > maxExpiry) {
    value = maxExpiry;
  }
  if (metadata.header_options.source_type === "event") {
    // round to nearest day
    value = roundUp(value, minExpiry);
  }
  metadata.expected_value["expiry"] = value * 1000;
  metadata.expected_value["aggregatable_report_window"] = value * 1000; // sec -> millisec
}

function isValidEventReportWindow(state, value, metadata) {
  let minEventReportWindow = metadata.flags["minimum_event_report_window_in_seconds"];
  let maxEventReportWindow = metadata.flags["max_reporting_register_source_expiration_in_seconds"];
  let expiryInSeconds = metadata.expected_value["expiry"] / 1000;
  if (value < minEventReportWindow) {
    value = minEventReportWindow;
  } else if (value > maxEventReportWindow) {
    value = maxEventReportWindow;
  }
  value = Math.min(expiryInSeconds, value);
  metadata.expected_value["event_report_window"] = value * 1000; // sec -> millisec
  metadata.flags["effective_expiry"] = metadata.expected_value["event_report_window"];
}

function isValidAggregatableReportWindow(state, value, metadata) {
  let minAggregatableReportWindow = metadata.flags["minimum_aggregatable_report_window_in_seconds"];
  let maxAggregatableReportWindow = metadata.flags["max_reporting_register_source_expiration_in_seconds"];
  let expiryInSeconds = metadata.expected_value["expiry"] / 1000;
  if (value < minAggregatableReportWindow) {
    value = minAggregatableReportWindow;
  } else if (value > maxAggregatableReportWindow) {
    value = maxAggregatableReportWindow;
  }
  value = Math.min(expiryInSeconds, value);
  metadata.expected_value["aggregatable_report_window"] = value * 1000; // sec -> millisec
}

function isValidInstallAttributionWindow(state, value, metadata) {
  let minInstallAttributionWindow = metadata.flags["min_install_attribution_window"];
  let maxInstallAttributionWindow = metadata.flags["max_install_attribution_window"];
  if (value < minInstallAttributionWindow) {
    value = minInstallAttributionWindow;
  } else if (value > maxInstallAttributionWindow) {
    value = maxInstallAttributionWindow;
  }
  metadata.expected_value["install_attribution_window"] = value * 1000; // sec -> millisec
}

function isValidPostInstallExclusivityWindow(state, value, metadata) {
  let minPostInstallExclusivityWindow = metadata.flags["min_post_install_exclusivity_window"];
  let maxPostInstallExclusivityWindow = metadata.flags["max_post_install_exclusivity_window"];
  if (value < minPostInstallExclusivityWindow) {
    value = minPostInstallExclusivityWindow;
  } else if (value > maxPostInstallExclusivityWindow) {
    value = maxPostInstallExclusivityWindow;
  }
  metadata.expected_value["post_install_exclusivity_window"] = value * 1000; // sec -> millisec
}

function isValidReinstallReattributionWindow(state, value, metadata) {
  let minReinstallReattributionWindow = 0;
  let maxReinstallReattributionWindow = metadata.flags["max_reinstall_reattribution_window_seconds"];
  if (value < minReinstallReattributionWindow) {
    value = minReinstallReattributionWindow;
  } else if (value > maxReinstallReattributionWindow) {
    value = maxReinstallReattributionWindow;
  }
  metadata.expected_value["reinstall_reattribution_window"] = value * 1000; // sec -> millisec
}

function isValidDebugAdId(state, value, metadata) {
  metadata.expected_value["debug_ad_id"] = value;
}

function isValidDebugJoinKey(state, value, metadata) {
  metadata.expected_value["debug_join_key"] = value;
}

function isValidSourceEventId(state, value, metadata) {
  metadata.expected_value["source_event_id"] = value;
}

function isValidPriority(state, value, metadata) {
  metadata.expected_value["priority"] = value;
}

function isValidDebugKey(state, value, metadata) {
  if (getDataType(value) !== "string") {
    state.warning("must be a string");
    return;
  }
  if (!isUInt64WithWarning(value, state)) {
    return;
  }
  metadata.expected_value["debug_key"] = value;
}

function isValidDebugReporting(state, value, metadata) {
  metadata.expected_value["debug_reporting"] = value;
}

function isValidCoarseEventReportDestinations(state, value, metadata) {
  metadata.expected_value["coarse_event_report_destinations"] = value;
}

function isValidSharedDebugKey(state, value, metadata) {
  metadata.expected_value["shared_debug_key"] = value;
}

function isValidSharedAggregationKeys(state, value, metadata) {
  metadata.expected_value["shared_aggregation_keys"] = JSON.stringify(value);
}

function isValidSharedFilterDataKeys(state, value, metadata) {
  metadata.expected_value["shared_filter_data_keys"] = JSON.stringify(value);
}

function isValidDropSourceIfInstalled(state, value, metadata) {
  metadata.expected_value["drop_source_if_installed"] = value;
}

function isValidMaxEventLevelReports(state, value, metadata) {
  if (getDataType(value) !== "number") {
    state.error("must be numeric");
    return;
  }
  if (!isInt64(value, state)) {
    return;
  }
  if (value < 0 || value > metadata.flags["flex_api_max_event_reports"]) {
    state.error("must be in the range of 0-" + metadata.flags["flex_api_max_event_reports"]);
    return;
  }
  metadata.expected_value["max_event_level_reports"] = value;
}

function isValidEventReportWindows(state, value, metadata) {
  let eventReportWindowsObj = {"start_time": 0};
  let expiryInSeconds = metadata.expected_value["expiry"] / 1000;
  if (!isNull(value, "start_time")) {
    if (getDataType(value["start_time"]) !== "number") {
      state.error("'start_time' must be numeric");
      return;
    }
    if (!isInt64(value["start_time"], state, "start_time")) {
      return;
    }
    if ((value["start_time"] < 0) || (value["start_time"] > expiryInSeconds)) {
      state.error("'start_time' must be in range of 0-" + expiryInSeconds);
      return;
    }
    eventReportWindowsObj["start_time"] = value["start_time"] * 1000;
  }

  if (!("end_times" in value)) {
    state.error("'end_times' key is required");
    return;
  }
  let endTimes = value["end_times"];
  if (getDataType(endTimes) !== "array") {
    state.error("'end_times' must be an array");
    return;
  }
  for (let i = 0; i < endTimes.length; i++) {
    if (getDataType(endTimes[i]) !== "number") {
      state.error("'end_times' array elements must be numeric");
      return;
    }
    if (!isInt64(endTimes[i], state, "end_times")) {
      state.errors.pop();
      state.error("`end_time` must be an array of int64");
      return;
    }
  }
  if (endTimes.length < 1 || endTimes.length > metadata.flags["flex_api_max_event_report_windows"]) {
    state.error("'end_times' array size must be in range of 1-" + metadata.flags["flex_api_max_event_report_windows"]);
    return;
  }
  let lastEndTime = endTimes[endTimes.length - 1];
  if (lastEndTime < 0) {
    state.error("'end_times' last element must be 0 or greater");
    return;
  }
  if (lastEndTime < metadata.flags["minimum_event_report_window_in_seconds"]) {
    lastEndTime = metadata.flags["minimum_event_report_window_in_seconds"];
    state.warning("'end_times' last element is below the minimum allowed value. The value will be set to the allowed minimum event report window value in seconds(" + metadata.flags["minimum_event_report_window_in_seconds"] +")");
  } else if (lastEndTime > expiryInSeconds) {
    lastEndTime = expiryInSeconds;
    state.warning("'end_times' last element exceeds the maximum allow value. The value will be set to 'expiry in seconds': " + expiryInSeconds);
  }
  endTimes[endTimes.length - 1] = lastEndTime;
  if (endTimes.length > 1) {
    let firstEndTime = endTimes[0];
    if (firstEndTime < 0) {
      state.error("'end_times' first element must be 0 or greater");
      return;
    }
    if (firstEndTime < metadata.flags["minimum_event_report_window_in_seconds"]) {
      endTimes[0] = metadata.flags["minimum_event_report_window_in_seconds"]
      state.warning("'end_times' first element is below the minimum allowed value. The value will be set to 'minimum event report window in seconds`: " + metadata.flags["minimum_event_report_window_in_seconds"]);
    }
  }
  if ((eventReportWindowsObj["start_time"] / 1000) >= endTimes[0]) {
    state.error("'end_times' first element must be greater than 'start_time'");
    return;
  }
  if (!isStrictAscending(endTimes, state)) {
    return;
  }
  eventReportWindowsObj["end_times"] = endTimes.map((ele) => ele * 1000);
  metadata.expected_value["event_report_windows"] = JSON.stringify(eventReportWindowsObj);
}

function isValidTriggerData(state, value, metadata) {
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "number") {
      state.error("array elements must be numeric");
      return;
    }
    if (!isInt64(value[i], state)) {
      state.errors.pop();
      state.error("must be an array of int64");
      return;
    }
  }
  for (let i = 0; i < value.length; i++) {
    if (!isUInt64(value[i], state)) {
      state.errors.pop();
      state.error("must be an array of uint64");
      return;
    }
  }
  let triggerDataList = [];
  for (let i = 0; i < value.length; i++) {
    if (triggerDataList.includes(value[i])) {
      state.error("duplicate array elements are not allowed (index: " + i + ")");
      return;
    }
    if (value[i] > metadata.flags["max_trigger_data_value"]) {
      state.error("array element (index: " + i + ") exceeds the max allowed trigger data value of " + metadata.flags["max_trigger_data_value"]);
      return;
    }
    triggerDataList.push(value[i]);
  }
  if (triggerDataList.length > metadata.flags["flex_api_max_trigger_data_cardinality"]) {
    state.error("array length exceeds the max trigger data cardinality of " + metadata.flags["flex_api_max_trigger_data_cardinality"]);
    return;
  }

  if (metadata.flags['feature-trigger-data-matching']
        && metadata.expected_value["trigger_data_matching"] === "MODULUS"
        && !isContiguousStartingAtZero(triggerDataList, state)) {
          return;
  }
  metadata.expected_value["trigger_data"] = triggerDataList;
}

function isValidAggregateDebugReportWithBudget(state, value, metadata) {
  if (isNull(value, "budget")) {
    state.error("`budget` key must be present");
    return;
  }
  if (getDataType(value["budget"]) !== "number") {
    state.error("`budget` must be numeric");
    return;
  }
  if (!isInt64(value["budget"], state, "budget")) {
    return;
  }
  if (value["budget"] <= 0 || value["budget"] > metadata.flags["max_sum_of_aggregate_values_per_source"]) {
    state.error("`budget` must be range of 1-" + metadata.flags["max_sum_of_aggregate_values_per_source"]);
    return;
  }
  let result = getValidAggregateDebugReportWithoutBudget(state, value, metadata);
  if (state.errors.length > 0) {
    return;
  }
  result["budget"] = value["budget"];
  metadata.expected_value["aggregatable_debug_reporting"] = JSON.stringify(result);
}

function isValidAggregateDebugReportWithoutBudget(state, value, metadata) {
  let result = getValidAggregateDebugReportWithoutBudget(state, value, metadata);
  if (state.errors.length > 0) {
    return;
  }
  metadata.expected_value["aggregatable_debug_reporting"] = JSON.stringify(result);
}

function getValidAggregateDebugReportWithoutBudget(state, value, metadata) {
  let aggregatableDebugReport = {};
  let keyPiece = "";
  if ("key_piece" in value) {
    keyPiece = getOptStringFallback(value["key_piece"]);
  }
  isValidAggregateKeyPiece(state, keyPiece, metadata);
  if (state.errors.length > 0) {
    return;
  }
  aggregatableDebugReport["key_piece"] = value["key_piece"];
  if (!isNull(value, "aggregation_coordinator_origin")) {
    if (getDataType(value["aggregation_coordinator_origin"]) !== "string") {
      state.error("`aggregation_coordinator_origin` must be a string");
      return;
    }
    if (value["aggregation_coordinator_origin"].length === 0) {
      state.error("`aggregation_coordinator_origin` must be non-empty");
      return;
    }
    let parsedOrigin = isValidURL(value["aggregation_coordinator_origin"], state, "aggregation_coordinator_origin");
    if (state.errors.length > 0) {
      return;
    }
    aggregatableDebugReport["aggregation_coordinator_origin"] = parsedOrigin;
  }
  if (!isNull(value, "debug_data")) {
    if (getDataType(value["debug_data"]) !== "array") {
      state.error("`debug_data` must be an array");
      return;
    }
    let parsedDebugData = getValidAggregateDebugReportingData(state, value, metadata);
    if (state.errors.length > 0) {
      return;
    }
    aggregatableDebugReport["debug_data"] = parsedDebugData;
  }
  return aggregatableDebugReport;
}

function getValidAggregateDebugReportingData(state, value, metadata) {
  let existingReportTypes = [];
  let parsedDebugDataArr = [];
  let debugDataArr = value["debug_data"];
  for (let i = 0; i < debugDataArr.length; i++) {
    let debugDataObj = debugDataArr[i];
    if (getDataType(debugDataObj) !== "object") {
      state.error("`debug_data` element at index: " + i + " must be an object");
      return;
    }
    if (isNull(debugDataObj, "key_piece") || isNull(debugDataObj, "types") || isNull(debugDataObj, "value")) {
      state.error("`debug_data` element at index: " + i + " requires keys (`key_piece`, `types`, `value`) to be present and non-null");
      return;
    }
    let debugDataKeyPiece = getOptStringFallback(debugDataObj["key_piece"]);
    isValidAggregateKeyPiece(state, debugDataKeyPiece, metadata, "debug_data", i);
    if (state.errors.length > 0) {
      return;
    }
    let maxAggregateDebugDataValue = metadata.flags["max_sum_of_aggregate_values_per_source"];
    if (!isNull(value, "budget")) {
      maxAggregateDebugDataValue = value["budget"];
    }
    if (debugDataObj["value"] <= 0 || debugDataObj["value"] > maxAggregateDebugDataValue) {
      state.error("debug_data element at index: " + i + " `value` must be in range 1-" + maxAggregateDebugDataValue);
      return;
    }
    let types = debugDataObj["types"];
    if (getDataType(types) !== "array") {
      state.error("debug_data element at index: " + i + " `types` must be an array");
      return;
    }
    isValidStringArray(state, types, metadata);
    if (state.errors.length > 0) {
      return;
    }
    if (types.length === 0) {
      state.error("debug_data element at index: " + i + " `types` must non-empty");
      return;
    }
    let parsedTypes = [];
    for (j = 0; j < types.length; j++) {
      if (existingReportTypes.includes(types[j])) {
        state.error("debug_data element at index: " + i + " duplicate report types are not allow within the same debug data object or across multiple debug data objects");
        return;
      }
      if (debugReportTypes.includes(types[j].toLowerCase())) {
        parsedTypes.push(types[j].toLowerCase());
      }
      existingReportTypes.push(types[j]);
    }
    parsedDebugDataArr.push({
      "key_piece": debugDataObj["key_piece"],
      "value": debugDataObj["value"],
      "types": parsedTypes
    });
  }
  return parsedDebugDataArr;
}

function isValidLocation(state, value, metadata) {
  url = isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
  behavior = "AS_IS";
  if (metadata.expected_value["attribution-reporting-redirect-config"] !== null) {
    if (metadata.expected_value["attribution-reporting-redirect-config"].toLowerCase() === "redirect-302-to-well-known") {
      behavior = "LOCATION_TO_WELL_KNOWN";
      url = formatWellKnownURL(value);
    }
  }
  metadata.expected_value["location"] = [{
    uri: url.toString(),
    redirect_behavior: behavior
  }];
}

function isValidAttributionReportingRedirect(state, value, metadata) {
  if (value.length > metadata.flags["max_registration_redirects"]) {
    state.warning("max allowed reporting redirects: " + metadata.flags["max_registration_redirects"] + ", all other reporting redirects will be ignored");
  }

  let arr = [];
  for (let i = 0; i < Math.min(value.length, metadata.flags["max_registration_redirects"]); i++) {
    if (getDataType(value[i]) !== "string") {
      state.error("must be an array of strings");
      return;
    }
    url = isValidURL(value[i], state);
    if (state.errors.length > 0) {
      return;
    }
    arr.push({
      uri: value[i],
      redirect_behavior: "AS_IS"
    });
  }
  metadata.expected_value["attribution-reporting-redirect"] = arr;
}

function isValidAttributionReportingRedirectConfig(state, value, metadata) {
  metadata.expected_value["attribution-reporting-redirect-config"] = value;
}

function isValidOdpService(state, value, metadata) {
  let forwardSlashIndex = value.indexOf('/');
  if (forwardSlashIndex < 0 || (forwardSlashIndex+1) >= value.length) {
    state.error("Invalid format. '/' must be present and not the last character");
    return;
  }
  let packageName = value.substring(0, forwardSlashIndex);
  let className = value.substring(forwardSlashIndex + 1);
  if (className.length > 0 && className.charAt(0) === '.') {
    className = packageName + className;
  }
  metadata.expected_value["service"] = packageName + "/" + className;
}

function isValidOdpCertDigest(state, value, metadata) {
  metadata.expected_value["certDigest"] = value;
}

function isValidOdpData(state, value, metadata) {
  metadata.expected_value["data"] = JSON.stringify(stringToBytes(value));
}

function maybeWrapFilters(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (getDataType(value) !== "array" && getDataType(value) !== "object") {
    state.error(fieldId + "must be an object or an array");
    return;
  }
  if (getDataType(value) === "array") {
    for (let i = 0; i < value.length; i++) {
      if (getDataType(value[i]) !== "object") {
        state.error(fieldId + "must be an array of object(s)");
        return;
      }
    }
  }
  let arr = (getDataType(value) === "object") ? [value] : value;
  return arr;
}

function formatKeys(json) {
  newJSON = {};
  for (key in json) {
    newJSON[key.toLowerCase()] = json[key];
  }
  return newJSON;
}

function roundUp(input, base) {
  remainder = input % base;
  shouldRoundUp = (remainder >= (base / 2.0)) || (input == remainder);
  return input - remainder + (shouldRoundUp ? base : 0);
}

function isStrictAscending(arr, state) {
  if (arr.length < 2) {
    return true;
  }
  for (let i = 1; i < arr.length; i++) {
    if (arr[i] <= arr[i - 1]) {
        state.error("'end_times' (" + arr + ") must be in strictly ascending order (index: " + i + ")");
        return false;
    }
  }
  return true;
}

function formatWellKnownURL(url) {
  wellKnownPathSegment = ".well-known/attribution-reporting/register-redirect";
  wellKnownQueryParam = "302_url";
  return url + "/" + wellKnownPathSegment + "?" + wellKnownQueryParam + "=" + encodeURIComponent(url);
}

function isContiguousStartingAtZero(arr, state) {
  let upperBound = arr.length - 1;
  for (let i = 0; i < arr.length; i++) {
    if (arr[i] > upperBound) {
      state.error("array must be contiguous (array element (index: " + i + ") cannot exceed: " + (arr.length - 1) + " (array length - 1)");
      return false;
    }
  }
  return true;
}

function stringToBytes(value) {
  let byteArr = [];
  for (let i = 0; i < value.length; i++) {
    byteArr.push(value.charCodeAt(i));
  }
  return byteArr;
}

function isValidStringArray(state, value, metadata) {
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "string") {
      state.error("must be an array of strings");
      return;
    }
  }

  if (value.length > metadata.flags["max_32_bit_integer"]) {
    state.error("exceeds max array size");
    return;
  }

  for (let i = 0; i < value.length; i++) {
    let str = value[i];
    if (str.length > metadata.flags["max_32_bit_integer"]) {
      state.error("element at index: " + i + " exceeds max string length");
      return;
    }
  }
}

function isNull(obj, key) {
  return (!(key in obj) || obj[key] === null);
}

module.exports = {
  State,
  boolean,
  optBoolean,
  optBooleanFallback,
  uint64,
  int64,
  optInt,
  string,
  optString,
  optStringFallback,
  object,
  optObject,
  array,
  optArray,
  optional,
  getDataType,
  formatKeys,
  isValidAppDestinationHost,
  isValidWebDestinationHost,
  isValidSourceFilterData,
  isValidTriggerMatchingData,
  isValidAggregationKeys,
  isValidSourceAttributionScopes,
  isValidTriggerAttributionScopes,
  isValidXNetworkKeyMapping,
  isValidAggregationCoordinatorOrigin,
  isValidAggregatableSourceRegistrationTime,
  isValidTriggerContextId,
  isValidEventTriggerData,
  isValidAggregatableTriggerData,
  isValidAggregatableValues,
  isValidTriggerFilters,
  isValidTriggerNotFilters,
  isValidAggregatableDeduplicationKey,
  isValidAttributionConfig,
  isValidExpiry,
  isValidEventReportWindow,
  isValidAggregatableReportWindow,
  isValidLocation,
  isValidAttributionReportingRedirect,
  isValidAttributionReportingRedirectConfig,
  isValidInstallAttributionWindow,
  isValidPostInstallExclusivityWindow,
  isValidReinstallReattributionWindow,
  isValidDebugAdId,
  isValidDebugJoinKey,
  isValidSourceEventId,
  isValidPriority,
  isValidDebugKey,
  isValidDebugReporting,
  isValidCoarseEventReportDestinations,
  isValidSharedDebugKey,
  isValidSharedAggregationKeys,
  isValidSharedFilterDataKeys,
  isValidDropSourceIfInstalled,
  isValidSourceNamedBudgets,
  isValidTriggerNamedBudgets,
  isValidMaxEventLevelReports,
  isValidEventReportWindows,
  isValidTriggerData,
  isValidAggregateDebugReportWithBudget,
  isValidAggregateDebugReportWithoutBudget,
  isValidOdpService,
  isValidOdpCertDigest,
  isValidOdpData
};
