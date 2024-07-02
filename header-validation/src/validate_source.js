const {
  State,
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
  formatKeys,
  isValidAppDestinationHost,
  isValidWebDestinationHost,
  isValidSourceFilterData,
  isValidTriggerMatchingData,
  isValidAggregationKeys,
  isValidAttributionScopes,
  isValidAttributionScopeLimit,
  isValidMaxEventStates
} = require('./base');

function validateSource(source, flagValues) {
  try {
    sourceJSON = JSON.parse(source);
    sourceJSON = formatKeys(sourceJSON);
  } catch (err) {
    return {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []};
  }

  const state = new State();
  let jsonSpec = {};

  // destination check
  if ((!('destination' in sourceJSON) || sourceJSON['destination'] === null) && (!('web_destination' in sourceJSON) || sourceJSON['web_destination'] === null)) {
    return {errors: [{
      path: ["destination or web_destination"],
      msg: "At least one field must be present and non-null",
      formattedError: "at least one field must be present and non-null: `destination or web_destination`"}], warnings: []};
  }

  jsonSpec["destination"] = optional(optString(isValidAppDestinationHost));
  jsonSpec["web_destination"] = optional();
  jsonSpec["source_event_id"] = optional(string(uint64()));
  jsonSpec["expiry"] = optional(optString(uint64()));
  jsonSpec["event_report_window"] = optional(optString(uint64()));
  jsonSpec["aggregatable_report_window"] = optional(optString(uint64()));
  jsonSpec["priority"] = optional(string(int64()));
  jsonSpec["debug_key"] = optional(string(uint64()));
  jsonSpec["debug_reporting"] = optional(optBooleanFallback());
  jsonSpec["install_attribution_window"] = optional(optString(int64()));
  jsonSpec["post_install_exclusivity_window"] = optional(optString(int64()));
  jsonSpec["debug_ad_id"] = optional(optStringFallback());
  jsonSpec["debug_join_key"] = optional(optStringFallback());

  // flag dependent fields
  jsonSpec["web_destination"] = optional(optArray(isValidWebDestinationHost, 'string', flagValues));
  jsonSpec["filter_data"] = optional(object(isValidSourceFilterData, flagValues));
  jsonSpec["aggregation_keys"] = optional(object(isValidAggregationKeys, flagValues));
  if (flagValues['feature-attribution-scopes']) {
    flagValues['max_event_states_present'] = ('max_event_states' in sourceJSON);
    flagValues['attribution_scope_limit_present'] = ('attribution_scope_limit' in sourceJSON);
    jsonSpec["attribution_scope_limit"] = optional(optString(int64(isValidAttributionScopeLimit, flagValues)));
    jsonSpec["max_event_states"] = optional(optString(int64(isValidMaxEventStates, flagValues)));
    jsonSpec["attribution_scopes"] = optional(array(isValidAttributionScopes, flagValues));
  }

  if (flagValues['feature-trigger-data-matching']) {
    jsonSpec["trigger_data_matching"] = optional(optString(isValidTriggerMatchingData));
  }
  if (flagValues['feature-coarse-event-report-destination']) {
    jsonSpec["coarse_event_report_destinations"] = optional(optBoolean());
  }
  if (flagValues['feature-shared-source-debug-key']) {
    jsonSpec["shared_debug_key"] = optional(optString(uint64()));
  }
  if (flagValues['feature-xna']) {
    jsonSpec["shared_aggregation_keys"] = optional(array());
  }
  if (flagValues['feature-shared-filter-data-keys']) {
    jsonSpec["shared_filter_data_keys"] = optional(array());
  }
  if (flagValues['feature-preinstall-check']) {
    jsonSpec["drop_source_if_installed"] = optional(optBoolean());
  }
  if(flagValues['feature-enable-reinstall-reattribution']) {
    jsonSpec["reinstall_reattribution_window"] = optional(optString(int64()));
  }

  state.validate(sourceJSON, jsonSpec, flagValues);
  return state.result();
}

module.exports = {
  validateSource
};