const {
  State,
  optional,
  uint64,
  string,
  array,
  optBooleanFallback,
  optString,
  optStringFallback,
  object,
  isValidAttributionScopes,
  isValidXNetworkKeyMapping,
  isValidAggregationCoordinatorOrigin,
  isValidAggregatableSourceRegistrationTime,
  isValidTriggerContextId,
  isValidEventTriggerData,
  isValidAggregatableTriggerData,
  isValidAggregatableValues,
  isValidTriggerFilters,
  isValidAggregatableDeduplicationKey,
  isValidAttributionConfig
} = require('./base');

function validateTrigger(trigger, flagValues) {
  try {
    triggerJSON = JSON.parse(trigger);
  } catch (err) {
    return {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []};
  }

  const state = new State();
  let jsonSpec = {};

  jsonSpec["debug_key"] = optional(string(uint64()));
  jsonSpec["debug_join_key"] = optional(optStringFallback());
  jsonSpec["debug_reporting"] = optional(optBooleanFallback());
  jsonSpec["debug_ad_id"] = optional(optStringFallback());
  jsonSpec["event_trigger_data"] = optional(array(isValidEventTriggerData, flagValues));
  jsonSpec["aggregatable_trigger_data"] = optional(array(isValidAggregatableTriggerData, flagValues));
  jsonSpec["aggregatable_values"] = optional(object(isValidAggregatableValues, flagValues));
  jsonSpec["filters"] = optional(isValidTriggerFilters, flagValues);
  jsonSpec["not_filters"] = optional(isValidTriggerFilters, flagValues);
  jsonSpec["aggregatable_deduplication_keys"] = optional(array(isValidAggregatableDeduplicationKey, flagValues));

  if (flagValues['feature-attribution-scopes']) {
    jsonSpec["attribution_scopes"] = optional(array(isValidAttributionScopes, flagValues));
  }
  if (flagValues['feature-xna']) {
    jsonSpec["x_network_key_mapping"] = optional(object(isValidXNetworkKeyMapping));
    jsonSpec["attribution_config"] = optional(array(isValidAttributionConfig, flagValues));
  }
  if (flagValues['feature-aggregation-coordinator-origin']) {
    jsonSpec["aggregation_coordinator_origin"] = optional(optString(isValidAggregationCoordinatorOrigin));
  }
  if (flagValues['feature-source-registration-time-optional-for-agg-reports']) {
    jsonSpec["aggregatable_source_registration_time"] = optional(optStringFallback(isValidAggregatableSourceRegistrationTime, flagValues));
  }
  if (flagValues['feature-trigger-context-id']) {
    jsonSpec["trigger_context_id"] = optional(string(isValidTriggerContextId, flagValues));
  }

  state.validate(triggerJSON, jsonSpec, flagValues);
  return state.result();
}

module.exports = {
  validateTrigger
};