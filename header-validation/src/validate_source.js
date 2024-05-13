const {
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
  isValidAppDestinationHost,
  isValidFilterData,
  isValidTriggerMatchingData
} = require('./base');

function validateSource(source, registrationType, flagValues) {
  try {
    sourceJSON = JSON.parse(source)
  } catch (err) {
    return {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []};
  }

  const state = new State();
  let jsonSpec = {};

  // destination check
  if (!('destination' in sourceJSON) && !('web_destination' in sourceJSON)) {
    return {errors: [{
      path: ["destination or web_destination"],
      msg: "At least one field must be present",
      formattedError: "At least one field must be present: `destination or web_destination`"}], warnings: []};
  }
  jsonSpec["destination"] = optional(isValidAppDestinationHost);
  jsonSpec["web_destination"] = optional();

  // define fields that are dependent on the feature-ara-parsing-alignment-v1 flag
  if (flagValues['feature-ara-parsing-alignment-v1']) {
    jsonSpec["source_event_id"] = optional(uint64);
    jsonSpec["expiry"] = optional(optUint64);
    jsonSpec["event_report_window"] = optional(optUint64);
    jsonSpec["aggregatable_report_window"] = optional(optUint64);
    jsonSpec["priority"] = optional(int64);
    jsonSpec["debug_key"] = optional(uint64);
  } else {
    jsonSpec["source_event_id"] = optional(optUint64);
    jsonSpec["expiry"] = optional(optInt64);
    jsonSpec["event_report_window"] = optional(optInt64);
    jsonSpec["aggregatable_report_window"] = optional(optInt64);
    jsonSpec["priority"] = optional(optInt64);
    jsonSpec["debug_key"] = optional(optUint64);
  }

  // define fields that are dependent on the feature-trigger-data-matching flag
  if (flagValues['feature-trigger-data-matching']) {
    jsonSpec["trigger_data_matching"] = optional(isValidTriggerMatchingData)
  }

  // define fields that have no dependencies
  jsonSpec["debug_reporting"] = optional(optBooleanFallback())
  jsonSpec["install_attribution_window"] = optional(optInt64)
  jsonSpec["post_install_exclusivity_window"] = optional(optInt64)
  jsonSpec["debug_ad_id"] = optional(optStringFallback())
  jsonSpec["debug_join_key"] = optional(optStringFallback())
  jsonSpec["coarse_event_report_destinations"] = optional(optBoolean())
  jsonSpec["shared_debug_keys"] = optional(optUint64);
  jsonSpec["drop_source_if_installed"] = optional(optBoolean())
  jsonSpec["shared_aggregation_keys"] = optional(array())
  jsonSpec["shared_filter_data_keys"] = optional(array())

  // define filter data spec
  jsonSpec["filter_data"] = optional(objectWithFlags(isValidFilterData, flagValues))

  state.validate(sourceJSON, jsonSpec, flagValues);
  return state.result();
}

module.exports = {
  validateSource
};