const uint64Regex = /^[0-9]+$/;
const int64Regex = /^-?[0-9]+$/;
const hexadecimalRegex = /^[0-9a-fA-F]+$/;
const alphaNumericRegex = /^[a-zA-Z0-9-_]+$/;

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

function optional(fn = () => {}, flags) {
  return (state, key, object) => {
    if (key in object) {
      if (object[key] !== null) {
        result = (flags === undefined) ? fn(state, object[key]) : fn(state, object[key], flags);
        return;
      }
    }
  }
}

function string(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    }
    state.error("must be a string");
  }
}

function optString(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    } else if (getDataType(value) !== "null") {
      result = (flags === undefined) ? fn(state, String(value)) : fn(state, String(value), flags);
      return;
    }
    state.error("must be a string or able to cast to string");
  }
}

function optStringFallback(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "string") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
    } else if (getDataType(value) !== "null") {
      result = (flags === undefined) ? fn(state, String(value)) : fn(state, String(value), flags);
    } else {
      result = (flags === undefined) ? fn(state, "") : fn(state, "", flags);
    }
  }
}

function object(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "object") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    }
    state.error("must be an object");
  }
}

function array(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "array") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    }
    state.error("must be an array");
  }
}

function optArray(fn = () => {}, requiredType, flags) {
  return (state, value) => {
    if (getDataType(value) === "array") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    }

    if (requiredType !== null) {
      if (getDataType(value) === requiredType) {
        result = (flags === undefined) ? fn(state, [value]) : fn(state, [value], flags);
        return;
      }
      state.error("must be a " + requiredType + " or an array of " + requiredType);
      return;
    } else {
      if (getDataType(value) !== "null") {
        result = (flags === undefined) ? fn(state, [value]) : fn(state, [value], flags);
        return;
      }
      state.error("must be a array or able to cast to array");
      return;
    }
  }
}

function boolean(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    }
    state.error("must be a boolean");
  }
}

function optBoolean(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    } else if (getDataType(value) === "string") {
      if (value.toLowerCase() === "true") {
        result = (flags === undefined) ? fn(state, true) : fn(state, true, flags);
        return;
      } else if (value.toLowerCase() === "false") {
        result = (flags === undefined) ? fn(state, false) : fn(state, false, flags);
        return;
      }
    }
    state.error("must be a boolean");
  }
}

function optBooleanFallback(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) === "boolean") {
      result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
      return;
    } else if (getDataType(value) === "string") {
      if (value === "true") {
        result = (flags === undefined) ? fn(state, true) : fn(state, true, flags);
        return;
      } else if (value === "false") {
        result = (flags === undefined) ? fn(state, false) : fn(state, false, flags);
        return;
      }
    }
    result = (flags === undefined) ? fn(state, false) : fn(state, false, flags);
    return;
  }
}

function uint32(fn = () => {}, flags) {
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
    result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
  }
}

function uint64(fn = () => {}, flags) {
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
    result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
  }
}

function int32(fn = () => {}, flags) {
  return (state, value) => {
    if (!int64Regex.test(value)) {
      state.error(`must be an int32 (must match ${int64Regex})`);
      return;
    }

    const max = 2n ** (32n - 1n) - 1n;
    const min = (-2n) ** (32n - 1n);
    value = BigInt(value);
    if (value < min || value > max) {
      state.error("must fit in a signed 32-bit integer");
      return;
    }
    result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
  }
}

function int64(fn = () => {}, flags) {
  return (state, value) => {
    if (!int64Regex.test(value)) {
      state.error(`must be an int64 (must match ${int64Regex})`);
      return;
    }

    const max = 2n ** (64n - 1n) - 1n;
    const min = (-2n) ** (64n - 1n);
    value = BigInt(value);
    if (value < min || value > max) {
      state.error("must fit in a signed 64-bit integer");
      return;
    }
    result = (flags === undefined) ? fn(state, value) : fn(state, value, flags);
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

function isInt32(value, state, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  value = (getDataType(value) === "number") ? String(value) : value;
  if (!int64Regex.test(value)) {
    state.error(fieldId + `must be an int32 (must match ${int64Regex})`);
    return false;
  }

  const max = 2n ** (32n - 1n) - 1n;
  const min = (-2n) ** (32n - 1n);
  value = BigInt(value);
  if (value < min || value > max) {
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
  value = BigInt(value);
  if (value < min || value > max) {
    state.error(fieldId + "must fit in a signed 64-bit integer");
    return false;
  }
  return true;
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

function isValidURL(value, state) {
    try {
      return new URL(value);
    } catch (err) {
      state.error("invalid URL format");
    }
}

function isValidSourceFilterData(state, value, flags) {
  flags["can_include_lookback_window"] = false;
  flags["should_check_filter_size"] = true;
  if ("source_type" in value) {
    state.error("filter: source_type is not allowed");
    return;
  }
  isValidFilterData(state, value, flags)
}

function isValidFilterDataParent(state, value, flags, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (value.length > flags["max_filter_maps_per_filter_set"]) {
    state.error(fieldId + "array length exceeds the max filter maps per filter set limit");
    return;
  }
  for (let i = 0; i < value.length; i++) {
    isValidFilterData(state, value[i], flags, fieldId);
    if (state.errors.length > 0) {
      return;
    }
  }
}

function isValidFilterData(state, value, flags, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (Object.keys(value).length > flags["max_attribution_filters"]) {
    state.error(fieldId + "exceeded max attribution filters");
    return;
  }
  for (key in value) {
    keyString = addQuotes(key, "'");
    if (flags["should_check_filter_size"]) {
      let keyByteSize = new Blob([key]).size;
      if (keyByteSize > flags["max_bytes_per_attribution_filter_string"]) {
        state.error(keyString + "exceeded max bytes per attribution filter string");
        return;
      }
    }
    if (key === "_lookback_window" && flags["feature-lookback-window-filter"]) {
      if (!flags["can_include_lookback_window"]) {
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
      return;
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
    if(filterValues.length > flags["max_values_per_attribution_filter"]) {
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
      if (valueByteSize > flags["max_bytes_per_attribution_filter_string"]) {
        state.error(addQuotes(filterValue, "'") + "exceeded max bytes per attribution filter value string");
        return;
      }
    }
  }
}

function isValidAttributionConfigFilterData(state, value, flags) {
  for (key in value) {
    let keyString = addQuotes(key, "'");
    if (key === "_lookback_window" && flags["feature-lookback-window-filter"]) {
      if (getDataType(value[key]) === "null") {
        state.error(keyString + "must be a string or able to cast to string");
        return;
      }
      if(!isInt64(String(value[key]), state, key)) {
        return;
      }
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
      }
    }
  }
}

function isValidWebDestinationHost(state, value, flags) {
  for (element in value) {
    if (getDataType(element) !== "string") {
      state.error("must be a string or an array of strings");
      return;
    }
  }

  if (value.length > flags["max_distinct_web_destinations_in_source_registration"]) {
    state.error("exceeded max distinct web destinations");
    return;
  }
  if (value.length === 0) {
    state.error("no web destinations present");
    return;
  }

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
    if (url.protocol === "https:" && (url.hostname === "127.0.0.1" || url.hostname === "localhost")) {
      return;
    }

    // Standardize dot characters and remove trailing dot if present.
    let formattedHostName = url.hostname.toLowerCase().replace(".\u3002\uFF0E\uFF61",".");
    if (formattedHostName.charAt(formattedHostName.length - 1) === ".") {
      formattedHostName = formattedHostName.substring(0, formattedHostName.length - 1);
    }

    if (formattedHostName.length >  flags["max_web_destination_hostname_character_length"]) {
      state.error("URL hostname/domain exceeds max character length");
      return;
    }

    let hostNameParts = formattedHostName.split(".");
    if (hostNameParts.length > flags["max_web_destination_hostname_parts"]) {
      state.error("exceeded the max number of URL hostname parts");
      return;
    }

    for (let j = 0; j < hostNameParts.length; j++) {
      let hostNamePart = hostNameParts[j];
      if (hostNamePart.length < flags["min_web_destination_hostname_part_character_length"] || hostNamePart.length > flags["max_web_destination_hostname_part_character_length"]) {
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
  }
}

function isValidAggregationKeys(state, value, flags) {
  if (Object.keys(value).length > flags["max_aggregate_keys_per_source_registration"]) {
    state.error("exceeded max number of aggregation keys per source registration");
    return;
  }

  for (aggregateKey in value) {
    isValidAggregateKeyId(state, aggregateKey, flags);
    if (state.errors.length > 0) {
      return;
    }

    let aggregateValue = value[aggregateKey];
    if (getDataType(aggregateValue) !== "string" && getDataType(aggregateValue) !== "null") {
      aggregateValue = String(aggregateValue);
    }

    isValidAggregateKeyPiece(state, aggregateValue, flags);
  }
}

function isValidAggregateKeyId(state, value, flags, fieldId) {
  fieldId = (fieldId === undefined) ? "" : addQuotes(fieldId, "'");
  if (value === null || value === "") {
    state.error(fieldId + "null or empty aggregate key string");
    return;
  }

  let keyByteSize = new Blob([value]).size;
  if (keyByteSize > flags["max_bytes_per_attribution_aggregate_key_id"]) {
    state.error(fieldId + "exceeded max bytes per attribution aggregate key id string");
    return;
  }
}

function isValidAggregateKeyPiece(state, value, flags) {
  if (value === null || value === "") {
    state.error("key piece value must not be null or empty string");
    return;
  }

  if (!value.startsWith("0x") && !value.startsWith("0X")) {
    state.error("key piece value must start with '0x' or '0X'");
    return;
  }

  let valueByteSize = new Blob([value]).size;
  if (valueByteSize < flags["min_bytes_per_aggregate_value"] || valueByteSize > flags["max_bytes_per_aggregate_value"]) {
    state.error("key piece value string size must be in the byte range (3 bytes - 34 bytes)");
    return;
  }

  if(!value.substring(2).match(hexadecimalRegex)) {
    state.error("key piece values must be hexadecimal");
    return;
  }
}

function isValidTriggerMatchingData(state, value) {
  if(value.toLowerCase() !== "modulus" && value.toLowerCase() !== "exact") {
    state.error("value must be 'exact' or 'modulus' (case-insensitive)");
    return;
  }
}

function isValidMaxEventStates(state, value, flags) {
  if (value <= 0) {
    state.error("must be greater than 0");
  }

  if (value > flags["max_report_states_per_source_registration"]) {
    state.error("exceeds max report states per source registration");
  }
}

function isValidAttributionScopeLimit(state, value, flags) {
  if (value <= 0) {
    state.error("must be greater than 0");
  }
  flags["attribution_scope_limit_value"] = value;
}

function isValidAttributionScopes(state, value, flags) {
  if (flags["header_type"] === "source") {
    if (!flags["attribution_scope_limit_present"] && value.length > 0) {
      state.error("attribution scopes array must be empty if attribution scope limit is not present");
      return;
    }

    if (!flags["attribution_scope_limit_present"] && flags["max_event_states_present"]) {
      state.error("max event states must not be present if attribution scope limit is not present");
      return;
    }

    if (flags["attribution_scope_limit_present"] && value.length > flags["attribution_scope_limit_value"]) {
      state.error("attribution scopes array size exceeds the provided attribution_scope_limit");
      return;
    }
  }

  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "string") {
      state.error("must be an array of strings");
      return;
    }
  }

  if (value.length > flags["max_attribution_scopes_per_source"]) {
    state.error("exceeded max number of scopes per source");
    return;
  }

  for (let i = 0; i < value.length; i++) {
    let scope = value[i];
    if (scope.length > flags["max_attribution_scope_string_length"]) {
      state.error("exceeded max scope string length");
      return;
    }
  }
}

function isValidAppDestinationHost(state, value) {
  let url = isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
  
  if (url.protocol !== "android-app:") {
      state.error("app URL host/scheme is invalid");
  }
}

function isValidXNetworkKeyMapping(state, value) {
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
}

function isValidAggregationCoordinatorOrigin(state, value) {
  if (value === "") {
    state.error("value must be non-empty");
    return;
  }
  isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
}

function isValidAggregatableSourceRegistrationTime(state, value, flags) {
  if ((value.toUpperCase() !== "INCLUDE") && (value.toUpperCase() !== "EXCLUDE")) {
    state.error("must equal 'INCLUDE' or 'EXCLUDE' (case-insensitive)");
    return;
  }
  flags["aggregatable_source_registration_time_value"] = value.toUpperCase();
}

function isValidTriggerContextId(state, value, flags) {
  if (flags["aggregatable_source_registration_time_value"] === "INCLUDE") {
    state.error("aggregatable_source_registration_time must not have the value 'INCLUDE'");
    return;
  }

  if (value.length > flags["max_trigger_context_id_string_length"]) {
    state.error("max string length exceeded");
    return;
  }
}

function isValidEventTriggerData(state, value, flags) {
  flags["can_include_lookback_window"] = true;
  flags["should_check_filter_size"] = !flags["feature-enable-update-trigger-header-limit"];
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }

  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    for (key in obj) {
      if (key === "trigger_data" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "string") {
          state.error("'trigger_data' must be a string");
          return;
        }
        if (!isUInt64(obj[key], state, key)) {
          return;
        }
      }
      if (key === "priority" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "string") {
          state.error("'priority' must be a string");
          return;
        }
        if (!isInt64(obj[key], state, key)) {
          return;
        }
      }
      if (key === "value" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "number") {
          state.error("'value' must be a number");
          return;
        }
        if (!isInt64(obj[key], state, key)) {
          return;
        }
        if (obj[key] < 1) {
          state.error("'value' must be greater than 0");
          return;
        }
        if (obj[key] > flags["max_bucket_threshold"]) {
          state.error("'value' exceeds max threshold of " + flags["max_bucket_threshold"]);
          return;
        }
      }
      if (key === "deduplication_key" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "string") {
          state.error("'deduplication_key' must be a string");
          return;
        }
        if (!isUInt64(obj[key], state, key)) {
          return;
        }
      }
      if ((key === "filters" || key === "not_filters") && obj[key] !== null) {
        let arr = maybeWrapFilters(obj[key], state, key);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, flags, key);
      }
    }
  }
}

function isValidAggregatableTriggerData(state, value, flags) {
  flags["can_include_lookback_window"] = true;
  flags["should_check_filter_size"] = !flags["feature-enable-update-trigger-header-limit"];
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }

  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    if (!("key_piece" in obj)) {
      state.error("'key_piece' must be present in each element/object of the array");
      return;
    }
    for (key in obj) {
      if (key === "key_piece") {
        isValidAggregateKeyPiece(state, obj[key], flags);
        if (state.errors.length > 0) {
          return;
        }
      }
      if (key === "source_keys" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "array") {
          state.error("'source_keys' must be an array");
          return;
        }
        if (flags["should_check_filter_size"]) {
          if (obj[key].length > flags["max_aggregate_keys_per_trigger_registration"]) {
            state.error("'source_keys' array size exceeds max aggregate keys per trigger registration limit");
            return;
          }
        }
        for (let j = 0; j < obj[key].length; j++) {
          if (getDataType(obj[key][j]) !== "string") {
            state.error("each element in 'source_keys' must be a string");
            return;
          }
          isValidAggregateKeyId(state, obj[key][j], flags, key);
          if (state.errors.length > 0) {
            return;
          }
        }
      }
      if ((key === "filters" || key === "not_filters") && obj[key] !== null) {
        let arr = maybeWrapFilters(obj[key], state, key);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, flags, key);
        if (state.errors.length > 0) {
          return;
        }
      }
      if (key === "x_network_data" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "object") {
          state.error("'x_network_data' must be an object");
          return;
        }
      }
    }
  }
}

function isValidAggregatableValues(state, value, flags) {
  flags["should_check_filter_size"] = !flags["feature-enable-update-trigger-header-limit"];
  if(flags["should_check_filter_size"]) {
    if (Object.keys(value).length > flags["max_aggregate_keys_per_trigger_registration"]) {
      state.error("exceeds max aggregate keys per trigger registration");
      return;
    }
  }
  for (aggregateKey in value) {
    isValidAggregateKeyId(state, aggregateKey, flags);
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
    if(value[aggregateKey] > flags["max_sum_of_aggregate_values_per_source"]) {
      state.error("aggregate key value exceeds the max sum of aggregate values per source");
      return;
    }
  }
}

function isValidTriggerFilters(state, value, flags) {
  flags["can_include_lookback_window"] = true;
  flags["should_check_filter_size"] = !flags["feature-enable-update-trigger-header-limit"];
  let arr = maybeWrapFilters(value, state);
  if (state.errors.length > 0) {
    return;
  }
  isValidFilterDataParent(state, arr, flags);
}

function isValidAggregatableDeduplicationKey(state, value, flags) {
  flags["can_include_lookback_window"] = true;
  flags["should_check_filter_size"] = !flags["feature-enable-update-trigger-header-limit"];
  if (value.length > flags["max_aggregate_deduplication_keys_per_registration"]) {
    state.error("exceeds max aggregate deduplication keys per registration limit");
    return;
  }
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    for (key in obj) {
      if (key === "deduplication_key"  && obj[key] !== null) {
        if (getDataType(obj[key]) !== "string") {
          state.error(addQuotes(key, "'") + "must be a string");
          return;
        }
        if (!isUInt64(obj[key], state, key)) {
          return;
        }
      }
      if ((key === "filters" || key === "not_filters") && obj[key] !== null) {
        let arr = maybeWrapFilters(obj[key], state, key);
        if (state.errors.length > 0) {
          return;
        }
        isValidFilterDataParent(state, arr, flags, key);
        if (state.errors.length > 0) {
          return;
        }
      }
    }
  }
}

function isValidAttributionConfig(state, value, flags) {
  for (let i = 0; i < value.length; i++) {
    if (getDataType(value[i]) !== "object") {
      state.error("must be an array of object(s)");
      return;
    }
  }
  for (let i = 0; i < value.length; i++) {
    let obj = value[i];
    if (!("source_network" in obj) || obj["source_network"] === null) {
      state.error("'source_network' must be present and non-null in each element/object of the array");
      return;
    }
    for (key in obj) {
      if (key === "source_network") {
        if (getDataType(obj[key]) === "null") {
          state.error(addQuotes(key, "'") + "must be a string or able to cast to string");
          return;
        }
      }
      if (key === "source_priority_range" && obj[key] !== null) {
        if (getDataType(obj[key]) !== "object") {
          state.error(addQuotes(key, "'") + "must be an object");
          return;
        }
        if (!("start" in obj[key]) || !("end" in obj[key])) {
          state.error(addQuotes(key, "'") + "both keys ('start','end') must be present");
          return;
        }
        if (obj[key]["start"] === null || obj[key]["end"] === null) {
          state.error(addQuotes(key, "'") + "both key values (start, end) must be string or able to cast to string");
          return;
        }
        if (!isInt64(obj[key]["start"], state, "start") || !isInt64(obj[key]["end"], state, "end")) {
          return;
        }
      }
      if ((key === "source_filters" || key === "source_not_filters" || key === "filter_data") && obj[key] !== null) {
        let arr = maybeWrapFilters(obj[key], state, key);
        if (state.errors.length > 0) {
          return;
        }
        for (let i = 0; i < arr.length; i++) {
          isValidAttributionConfigFilterData(state, arr[i], flags);
          if (state.errors.length > 0) {
            return;
          }
        }
      }
      if ((key === "source_expiry_override" || key === "priority" || key === "expiry" || key === "post_install_exclusivity_window") && obj[key] !== null) {
        if (!isInt64(obj[key], state, key)) {
          return;
        }
      }
    }
  }
}

function isValidLocation(state, value) {
  isValidURL(value, state);
  if (state.errors.length > 0) {
    return;
  }
}

function isValidAttributionReportingRedirect(state, value, flags) {
  if (value.length > flags["max_registration_redirects"]) {
    state.warning("max allowed reporting redirects: " + flags["max_registration_redirects"] + ", all other reporting redirects will be ignored");
  }

  for (let i = 0; i < Math.min(value.length, flags["max_registration_redirects"]); i++) {
    if (getDataType(value[i]) !== "string") {
      state.error("must be an array of strings");
      return;
    }
    isValidURL(value[i], state);
    if (state.errors.length > 0) {
      return;
    }
  }
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


module.exports = {
  State,
  boolean,
  optBoolean,
  optBooleanFallback,
  uint64,
  int64,
  string,
  optString,
  optStringFallback,
  object,
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
  isValidAttributionScopes,
  isValidAttributionScopeLimit,
  isValidMaxEventStates,
  isValidXNetworkKeyMapping,
  isValidAggregationCoordinatorOrigin,
  isValidAggregatableSourceRegistrationTime,
  isValidTriggerContextId,
  isValidEventTriggerData,
  isValidAggregatableTriggerData,
  isValidAggregatableValues,
  isValidTriggerFilters,
  isValidAggregatableDeduplicationKey,
  isValidAttributionConfig,
  isValidLocation,
  isValidAttributionReportingRedirect
};
