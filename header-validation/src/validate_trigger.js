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

const {
  State,
  optional,
  string,
  array,
  optBooleanFallback,
  optString,
  optStringFallback,
  object,
  formatKeys,
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
  isValidDebugKey,
  isValidDebugJoinKey,
  isValidDebugReporting,
  isValidDebugAdId,
  isValidTriggerNamedBudgets,
  isValidAggregateDebugReportWithoutBudget
} = require('./base');

function initializeExpectedValues(flags) {
  let expectedValues = {
    "attribution_config": null,
    "event_trigger_data": "[]",
    "filters": null,
    "not_filters": null,
    "aggregatable_trigger_data": null,
    "aggregatable_values": null,
    "aggregatable_deduplication_keys": null,
    "debug_key": null,
    "debug_reporting": false,
    "x_network_key_mapping": null,
    "debug_join_key": null,
    "debug_ad_id": null,
    "aggregation_coordinator_origin": null,
    "aggregatable_source_registration_time": flags['feature-source-registration-time-optional-for-agg-reports'] ? "EXCLUDE" : "INCLUDE",
    "trigger_context_id": null,
    "attribution_scopes": null,
    "named_budgets": null,
    "aggregatable_debug_reporting": null
  };
  return expectedValues;
}

function validateTrigger(trigger, metadata) {
  try {
    triggerJSON = JSON.parse(trigger);
    triggerJSON = formatKeys(triggerJSON);
  } catch (err) {
    return {
      result: {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []},
      expected_value: {}
    };
  }

  const state = new State();
  let jsonSpec = {};

  metadata.expected_value = initializeExpectedValues(metadata.flags);
  jsonSpec["debug_key"] = optional(isValidDebugKey, metadata);
  jsonSpec["debug_join_key"] = optional(optStringFallback(isValidDebugJoinKey, metadata));
  jsonSpec["debug_reporting"] = optional(optBooleanFallback(isValidDebugReporting, metadata));
  jsonSpec["debug_ad_id"] = optional(optStringFallback(isValidDebugAdId, metadata));
  jsonSpec["event_trigger_data"] = optional(array(isValidEventTriggerData, metadata));
  jsonSpec["aggregatable_trigger_data"] = optional(array(isValidAggregatableTriggerData, metadata));
  jsonSpec["aggregatable_values"] = optional(object(isValidAggregatableValues, metadata));
  jsonSpec["filters"] = optional(isValidTriggerFilters, metadata);
  jsonSpec["not_filters"] = optional(isValidTriggerNotFilters, metadata);
  jsonSpec["aggregatable_deduplication_keys"] = optional(array(isValidAggregatableDeduplicationKey, metadata));

  if (metadata.flags['feature-attribution-scopes']) {
    jsonSpec["attribution_scopes"] = optional(array(isValidTriggerAttributionScopes, metadata));
  }
  if (metadata.flags['feature-xna']) {
    jsonSpec["x_network_key_mapping"] = optional(object(isValidXNetworkKeyMapping, metadata));
    jsonSpec["attribution_config"] = optional(array(isValidAttributionConfig, metadata));
  }
  if (metadata.flags['feature-aggregation-coordinator-origin']) {
    jsonSpec["aggregation_coordinator_origin"] = optional(optString(isValidAggregationCoordinatorOrigin, metadata));
  }
  if (metadata.flags['feature-source-registration-time-optional-for-agg-reports']) {
    jsonSpec["aggregatable_source_registration_time"] = optional(optStringFallback(isValidAggregatableSourceRegistrationTime, metadata));
  }
  if (metadata.flags['feature-trigger-context-id']) {
    jsonSpec["trigger_context_id"] = optional(string(isValidTriggerContextId, metadata));
  }
  if (metadata.flags['feature-aggregatable-named-budgets']) {
    jsonSpec["named_budgets"] = optional(array(isValidTriggerNamedBudgets, metadata));
  }
  if (metadata.flags['feature-enable-aggregate-debug-reporting']) {
    jsonSpec["aggregatable_debug_reporting"] = optional(object(isValidAggregateDebugReportWithoutBudget, metadata));
  }

  state.validate(triggerJSON, jsonSpec);
  return {
    result: state.result(),
    expected_value: metadata.expected_value
  };
}

module.exports = {
  validateTrigger
};