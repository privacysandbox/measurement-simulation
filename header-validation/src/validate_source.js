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
  optBoolean,
  optBooleanFallback,
  uint64,
  int64,
  string,
  optString,
  optStringFallback,
  object,
  optObject,
  array,
  optArray,
  optional,
  formatKeys,
  isValidAppDestinationHost,
  isValidWebDestinationHost,
  isValidSourceFilterData,
  isValidTriggerMatchingData,
  isValidAggregationKeys,
  isValidSourceAttributionScopes,
  isValidExpiry,
  isValidEventReportWindow,
  isValidAggregatableReportWindow,
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
  isValidMaxEventLevelReports,
  isValidEventReportWindows,
  isValidTriggerData,
  isValidAggregateDebugReportWithBudget
} = require('./base');

function initializeExpectedValues(flags) {
  let expectedValues = {
    "source_event_id": 0,
    "debug_key": null,
    "destination": null,
    "expiry": (flags["max_reporting_register_source_expiration_in_seconds"] * 1000),
    "event_report_window": null,
    "aggregatable_report_window": (flags["max_reporting_register_source_expiration_in_seconds"] * 1000),
    "priority": 0,
    "install_attribution_window": (flags["max_install_attribution_window"] * 1000),
    "post_install_exclusivity_window": (flags["min_post_install_exclusivity_window"] * 1000),
    "reinstall_reattribution_window": 0,
    "filter_data": null,
    "web_destination": null,
    "aggregation_keys": null,
    "shared_aggregation_keys": null,
    "debug_reporting": false,
    "debug_join_key": null,
    "debug_ad_id": null,
    "coarse_event_report_destinations": false,
    "shared_debug_key": null,
    "shared_filter_data_keys": null,
    "drop_source_if_installed": false,
    "trigger_data_matching": "MODULUS",
    "attribution_scopes": null,
    "attribution_scope_limit": null,
    "max_event_states": 3,
    "named_budgets": null,
    "max_event_level_reports": null,
    "event_report_windows": null,
    "trigger_data": null,
    "aggregatable_debug_reporting": null
  };
  return expectedValues;
}

function validateSource(source, metadata) {
  try {
    sourceJSON = JSON.parse(source);
    sourceJSON = formatKeys(sourceJSON);
  } catch (err) {
    return {
      result: {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []},
      expected_value: {}
    };
  }

  const state = new State();
  let jsonSpec = {};

  // destination check
  if ((!('destination' in sourceJSON) || sourceJSON['destination'] === null) && (!('web_destination' in sourceJSON) || sourceJSON['web_destination'] === null)) {
    return {
      result: {errors: [{
      path: ["destination or web_destination"],
      msg: "At least one field must be present and non-null",
      formattedError: "at least one field must be present and non-null: `destination or web_destination`"}], warnings: []},
      expected_value: {}
    };
  }
  
  // event report window check
  if ((('event_report_window' in sourceJSON) && sourceJSON['event_report_window'] !== null) && (('event_report_windows' in sourceJSON) && sourceJSON['event_report_windows'] !== null)) {
    return {
      result: {errors: [{
      path: ["event_report_window or event_report_windows"],
      msg: "Only one field must be present",
      formattedError: "Only one field must be present: `event_report_window or event_report_windows`"}], warnings: []},
      expected_value: {}
    };
  }

  metadata.expected_value = initializeExpectedValues(metadata.flags);
  jsonSpec["destination"] = optional(optString(isValidAppDestinationHost, metadata));
  jsonSpec["source_event_id"] = optional(string(uint64(isValidSourceEventId, metadata)));
  jsonSpec["expiry"] = optional(optString(uint64(isValidExpiry, metadata))); // must be parsed before dependent fields
  jsonSpec["event_report_window"] = optional(optString(uint64(isValidEventReportWindow, metadata)));
  jsonSpec["aggregatable_report_window"] = optional(optString(uint64(isValidAggregatableReportWindow, metadata)));
  jsonSpec["priority"] = optional(string(int64(isValidPriority, metadata)));
  jsonSpec["debug_key"] = optional(isValidDebugKey, metadata);
  jsonSpec["debug_reporting"] = optional(optBooleanFallback(isValidDebugReporting, metadata));
  jsonSpec["install_attribution_window"] = optional(optString(int64(isValidInstallAttributionWindow, metadata)));
  jsonSpec["post_install_exclusivity_window"] = optional(optString(int64(isValidPostInstallExclusivityWindow, metadata)));
  jsonSpec["debug_ad_id"] = optional(optStringFallback(isValidDebugAdId, metadata));
  jsonSpec["debug_join_key"] = optional(optStringFallback(isValidDebugJoinKey, metadata));
  jsonSpec["web_destination"] = optional(optArray(isValidWebDestinationHost, 'string', metadata));
  jsonSpec["filter_data"] = optional(object(isValidSourceFilterData, metadata));
  jsonSpec["aggregation_keys"] = optional(object(isValidAggregationKeys, metadata));
  jsonSpec["max_event_level_reports"] = optional(isValidMaxEventLevelReports, metadata);
  jsonSpec["event_report_windows"] = optional(optString(optObject(isValidEventReportWindows, metadata)));

  if (metadata.flags['feature-attribution-scopes']) {
    jsonSpec["attribution_scopes"] = optional(object(isValidSourceAttributionScopes, metadata));
  }
  if (metadata.flags['feature-trigger-data-matching']) {
    jsonSpec["trigger_data_matching"] = optional(optString(isValidTriggerMatchingData, metadata));
  }
  if (metadata.flags['feature-coarse-event-report-destination']) {
    jsonSpec["coarse_event_report_destinations"] = optional(optBoolean(isValidCoarseEventReportDestinations, metadata));
  }
  if (metadata.flags['feature-shared-source-debug-key']) {
    jsonSpec["shared_debug_key"] = optional(optString(uint64(isValidSharedDebugKey, metadata)));
  }
  if (metadata.flags['feature-xna']) {
    jsonSpec["shared_aggregation_keys"] = optional(array(isValidSharedAggregationKeys, metadata));
  }
  if (metadata.flags['feature-shared-filter-data-keys']) {
    jsonSpec["shared_filter_data_keys"] = optional(array(isValidSharedFilterDataKeys, metadata));
  }
  if (metadata.flags['feature-preinstall-check']) {
    jsonSpec["drop_source_if_installed"] = optional(optBoolean(isValidDropSourceIfInstalled, metadata));
  }
  if(metadata.flags['feature-enable-reinstall-reattribution']) {
    jsonSpec["reinstall_reattribution_window"] = optional(optString(int64(isValidReinstallReattributionWindow, metadata)));
  }
  if(metadata.flags['feature-aggregatable-named-budgets']) {
    jsonSpec["named_budgets"] = optional(object(isValidSourceNamedBudgets, metadata));
  }
  if (metadata.flags['feature-enable-aggregate-debug-reporting']) {
    jsonSpec["aggregatable_debug_reporting"] = optional(object(isValidAggregateDebugReportWithBudget, metadata));
  }
  if(metadata.flags['feature-enable-v1-source-trigger-data']) {
    // trigger data and specs check
    if ((('trigger_data' in sourceJSON) && sourceJSON['trigger_data'] !== null) && (('trigger_specs' in sourceJSON) && sourceJSON['trigger_specs'] !== null)) {
      return {
        result: {errors: [{
        path: ["trigger_data or trigger_specs"],
        msg: "Only one field must be present",
        formattedError: "Only one field must be present: `trigger_data or trigger_specs`"}], warnings: []},
        expected_value: {}
      };
    }
    jsonSpec["trigger_data"] = optional(array(isValidTriggerData, metadata));
  }

  state.validate(sourceJSON, jsonSpec);
  return {
    result: state.result(),
    expected_value: metadata.expected_value
  };
}

module.exports = {
  validateSource
};