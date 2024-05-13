const uint64Regex = /^[0-9]+$/;
const int64Regex = /^-?[0-9]+$/;

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
    return 'null';
  }
  if (typeof value === 'boolean') {
    return 'boolean'
  }
  if (typeof value === 'number') {
    return 'number'
  }
  if (typeof value === 'string') {
    return 'string'
  }
  if (Array.isArray(value)) {
    return 'array'
  }
  if (value !== null && typeof value === 'object' && value.constructor === Object) {
    return 'object'
  }
  return ''
}


function required(fn = () => {}) {
  return (state, key, object) => {
    if (key in object) {
      fn(state, object[key]);
      return;
    }
    state.error('Missing required field');
    return;
  }
}

function exclude(fn = () => {}) {
  return (state, key, object) => {
    if (!(key in object)) {
      fn(state, object[key]);
      return;
    }
    state.error('Field should be excluded');
    return;
  }
}

function optional(fn = () => {}) {
  return (state, key, object) => {
    if (key in object) {
      fn(state, object[key]);
      return;
    }
  }
}

function string(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'string') {
      fn(state, value);
      return;
    }
    state.error('Must be a string');
  }
}

function optString(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'string') {
      fn(state, value);
      return;
    } else if (getDataType(value) != 'null') {
      fn(state, String(value));
      return;
    }
    state.error('Must be a string or able to cast to string');
  }
}

function optStringFallback(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'string') {
      fn(state, value);
    } else if (getDataType(value) != 'null') {
      fn(state, String(value));
    } else {
      fn(state, "");
    }
  }
}

function object(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'object') {
      fn(state, value);
      return;
    }
    state.error('Must be an object');
  }
}

function objectWithFlags(fn = () => {}, flags) {
  return (state, value) => {
    if (getDataType(value) == 'object') {
      fn(state, value, flags);
      return;
    }
    state.error('Must be an object');
  }
}

function array(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'array') {
      fn(state, value);
      return;
    }
    state.error('Must be an array');
  }
}

function boolean(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'boolean') {
      fn(state, value);
      return;
    }
    state.error('Must be a boolean');
  }
}

function optBoolean(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'boolean') {
      fn(state, value);
      return;
    } else if (getDataType(value) == 'string') {
      if (value.toLowerCase() == 'true') {
        fn(state, true);
        return;
      } else if (value.toLowerCase() == 'false') {
        fn(state, false);
        return;
      }
    }
    state.error('Must be a boolean');
  }
}

function optBooleanFallback(fn = () => {}) {
  return (state, value) => {
    if (getDataType(value) == 'boolean') {
      fn(state, value);
      return;
    } else if (getDataType(value) == 'string') {
      if (value == 'true') {
        fn(state, true);
        return;
      } else if (value == 'false') {
        fn(state, false);
        return;
      }
    }
    fn(state, false);
    return;
  }
}

function isValidFilterData(state, value, flags) {
  if (flags["feature-ara-parsing-alignment-v1"] && 'source_type' in value) {
    state.error('filter: source_type is not allowed');
    return;
  }
  if (Object.keys(value).length > flags["max_attribution_filters"]) {
    state.error('exceeded max attribution filters');
    return;
  }
  for (key in value) {
    let keyByteSize = new Blob([key]).size;
    if (keyByteSize > flags["max_bytes_per_attribution_filter_string"]) {
      state.error('exceeded max bytes per attribution filter string');
      return;
    }
    if (key === "_lookback_window" && flags["feature-lookback-window-filter"]) {
      state.error('filter: _lookback_window is not allowed');
      return;
    }
    if (key.startsWith("_")) {
      state.error('filter can not start with underscore');
      return;
    }

    filterValues = value[key];
    if (!Array.isArray(filterValues)) {
      state.error('filter value must be an array');
      return;
    }
    if(filterValues.length > flags["max_values_per_attribution_filter"]) {
      state.error('exceeded max values per attribution filter');
      return;
    }
    for(let i = 0; i < filterValues.length; i++) {
      let filterValue = filterValues[i];
      if (getDataType(filterValue) != 'string') {
        state.error('filter values must be strings');
        return;
      }
      let valueByteSize = new Blob([filterValue]).size;
      if (valueByteSize > flags["max_bytes_per_attribution_filter_string"]) {
        state.error('exceeded max bytes per attribution filter value string');
        return;
      }
    }
  }
}

const uint64 = string((state, value) => {
  if (!uint64Regex.test(value)) {
    state.error(`Must be a uint64 (must match ${uint64Regex})`);
    return;
  }

  const max = 2n ** 64n - 1n;
  if (BigInt(value) > max) {
    state.error('Must fit in an unsigned 64-bit integer')
  }
});

const optUint64 = optString((state, value) => {
  if (!uint64Regex.test(value)) {
    state.error(`Must be a uint64 (must match ${uint64Regex})`);
    return;
  }

  const max = 2n ** 64n - 1n;
  if (BigInt(value) > max) {
    state.error('Must fit in an unsigned 64-bit integer')
  }
});

const int64 = string((state, value) => {
  if (!int64Regex.test(value)) {
    state.error(`Must be an int64 (must match ${int64Regex})`);
    return;
  }

  const max = 2n ** (64n - 1n) - 1n;
  const min = (-2n) ** (64n - 1n);
  value = BigInt(value);
  if (value < min || value > max) {
    state.error('Must fit in a signed 64-bit integer')
  }
});

const optInt64 = optString((state, value) => {
  if (!int64Regex.test(value)) {
    state.error(`Must be an int64 (must match ${int64Regex})`);
    return;
  }

  const max = 2n ** (64n - 1n) - 1n;
  const min = (-2n) ** (64n - 1n);
  value = BigInt(value);
  if (value < min || value > max) {
    state.error('Must fit in a signed 64-bit integer')
  }
});

const isValidUri = optString((state, value) => {
  try {
    let url = new URL(value);
  } catch (err) {
    state.error('URI is not valid');
  }
});

const isValidAppDestinationHost = optString((state, value) => {
  let url;
  try {
    url = new URL(value);
  } catch (err) {
    return
  }
  
  if (url.protocol != 'android-app:') {
      state.error('app URI host is invalid');
  }
});

const isValidTriggerMatchingData = optString((state, value) => {
  if(value.toLowerCase() != 'modulus' && value.toLowerCase() != 'exact') {
    state.error("value must be 'exact' or 'modulus' (case-insensitive)");
    return;
  }
});


module.exports = {
  State,
  boolean,
  optBoolean,
  optBooleanFallback,
  uint64,
  optUint64,
  int64,
  optInt64,
  optString,
  optStringFallback,
  object,
  objectWithFlags,
  array,
  required,
  optional,
  exclude,
  getDataType,
  isValidUri,
  isValidAppDestinationHost,
  isValidFilterData,
  isValidTriggerMatchingData
};
