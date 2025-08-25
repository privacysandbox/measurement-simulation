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

const test = require('node:test');
const assert = require('assert');
const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');
const {validateOdpTrigger} = require('./validate_odp_trigger');
const {validateRedirect} = require('./validate_redirect');
const {sourceTestCases} = require('./source_tests');
const {triggerTestCases} = require('./trigger_tests');
const {redirectTestCases} = require('./redirect_tests');
const {odpTriggerTestCases} = require('./odp_trigger_tests');
var defaultFlags = {
  "feature-attribution-scopes": true,
  "feature-trigger-data-matching": true,
  "feature-coarse-event-report-destination": true,
  "feature-shared-source-debug-key": true,
  "feature-xna": true,
  "feature-shared-filter-data-keys": true,
  "feature-preinstall-check": true,
  "feature-enable-update-trigger-header-limit": false,
  "feature-lookback-window-filter": true,
  "feature-enable-reinstall-reattribution": true,
  "feature-aggregatable-named-budgets": true,
  "feature-source-registration-time-optional-for-agg-reports": true,
  "feature-aggregation-coordinator-origin": true,
  "feature-trigger-context-id": true,
  "feature-enable-v1-source-trigger-data": true,
  "feature-enable-odp-web-trigger-registration": true,
  "feature-enable-aggregate-debug-reporting": true,
  "max_attribution_filters": 50,
  "max_bytes_per_attribution_filter_string": 25,
  "max_values_per_attribution_filter": 50,
  "max_distinct_web_destinations_in_source_registration": 3,
  "max_web_destination_hostname_character_length": 253,
  "max_web_destination_hostname_parts": 127,
  "min_web_destination_hostname_part_character_length": 1,
  "max_web_destination_hostname_part_character_length": 63,
  "max_aggregate_keys_per_source_registration": 50,
  "max_bytes_per_attribution_aggregate_key_id": 25,
  "min_bytes_per_aggregate_value": 3,
  "max_bytes_per_aggregate_value": 34,
  "max_attribution_scopes_per_source": 20,
  "max_attribution_scope_string_length": 50,
  "max_report_states_per_source_registration": (1n << 32n) - 1n,
  "max_trigger_context_id_string_length": 64,
  "max_bucket_threshold": (1n << 32n) - 1n,
  "max_filter_maps_per_filter_set": 20,
  "max_aggregate_keys_per_trigger_registration": 50,
  "max_sum_of_aggregate_values_per_source": 65536,
  "max_aggregate_deduplication_keys_per_registration": 50,
  "min_reporting_register_source_expiration_in_seconds": 1 * 24 * 60 * 60, // 1 day -> secs
  "max_reporting_register_source_expiration_in_seconds": 30 * 24 * 60 * 60, // 30 days -> secs
  "minimum_event_report_window_in_seconds": 1 * 60 * 60, // 1 hour -> secs
  "minimum_aggregatable_report_window_in_seconds": 1 * 60 * 60, // 1 hour -> secs
  "min_install_attribution_window": 1 * 24 * 60 * 60, // 1 day -> secs
  "max_install_attribution_window": 30 * 24 * 60 * 60, // 30 days -> secs
  "min_post_install_exclusivity_window": 0,
  "max_post_install_exclusivity_window": 30 * 24 * 60 * 60, // 30 days -> secs
  "max_reinstall_reattribution_window_seconds": 90 * 24 * 60 * 60, // 90 days -> secs
  "max_registration_redirects": 20,
  "max_length_per_budget_name": 25,
  "max_named_budgets_per_source_registration": 25,
  "flex_api_max_event_reports": 20,
  "max_trigger_data_value": (1n << 32n) - 1n,
  "flex_api_max_trigger_data_cardinality": 32,
  "max_32_bit_integer": Math.pow(2, 31) - 1
};
var defaultExpectedValuesSource = {
  "source_event_id": 0,
  "debug_key": null,
  "destination": null,
  "expiry": (defaultFlags["max_reporting_register_source_expiration_in_seconds"] * 1000),
  "event_report_window": null,
  "aggregatable_report_window": (defaultFlags["max_reporting_register_source_expiration_in_seconds"] * 1000),
  "priority": 0,
  "install_attribution_window": (defaultFlags["max_install_attribution_window"] * 1000),
  "post_install_exclusivity_window": (defaultFlags["min_post_install_exclusivity_window"] * 1000),
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
var defaultExpectedValuesTrigger = {
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
  "aggregatable_source_registration_time": defaultFlags['feature-source-registration-time-optional-for-agg-reports'] ? "EXCLUDE" : "INCLUDE",
  "trigger_context_id": null,
  "attribution_scopes": null,
  "named_budgets": null,
  "aggregatable_debug_reporting": null
};
var defaultExpectedValuesOdpTrigger = {
  "service": null,
  "certDigest": null,
  "data": null
};
var defaultExpectedValuesRedirect = {
  "location": null,
  "attribution-reporting-redirect": null,
  "attribution-reporting-redirect-config": null
};

function overrideValues(oldValues, overrideValues) {
  let newValues = {};
  for (key in oldValues) {
    newValues[key] = oldValues[key];
  }
  for (key in overrideValues) {
    newValues[key] = overrideValues[key];
  }
  return newValues;
}

test('Source Header Validation Tests', async (t) => {
    for (testCase of sourceTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            json = testCase.json;
            let metadata = {
              flags: overrideValues(defaultFlags, testCase.flags),
              header_options: {
                header_type: "source",
                source_type: testCase?.source_type
              },
              expected_value: null
            };

            // Test
            output = validateSource(json, metadata);
            result = output.result;
            expected = output.expected_value;

            // Assert
            isValid = (result.errors.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            if (testCase?.expected_value !== undefined) {
              assert.deepEqual(/* actual */ expected, /* expected */ overrideValues(defaultExpectedValuesSource, testCase.expected_value));
            }
        })
    }
})

test('Trigger Header Validation Tests', async (t) => {
    for (testCase of triggerTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            json = testCase.json;
            let metadata = {
              flags: overrideValues(defaultFlags, testCase.flags),
              header_options: {
                header_type: "trigger",
                source_type: null
              },
              expected_value: null
            };

            // Test
            output = validateTrigger(json, metadata);
            result = output.result;
            expected = output.expected_value;

            // Assert
            isValid = (result.errors.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            if (testCase?.expected_value !== undefined) {
              assert.deepEqual(/* actual */ expected, /* expected */ overrideValues(defaultExpectedValuesTrigger, testCase.expected_value));
            }
        })
    }
})

test('ODP Trigger Header Validation Tests', async (t) => {
  for (testCase of odpTriggerTestCases) {
      await t.test(testCase.name, (t) => {
          // Setup
          json = testCase.json;
          let metadata = {
            flags: overrideValues(defaultFlags, testCase.flags),
            header_options: {
              header_type: "odp-trigger",
              source_type: null
            },
            expected_value: null
          };

          // Test
          output = validateOdpTrigger(json, metadata);
          result = output.result;
          expected = output.expected_value;

          // Assert
          isValid = (result.errors.length === 0);
          assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
          assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
          assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
          if (testCase?.expected_value !== undefined) {
            assert.deepEqual(/* actual */ expected, /* expected */ overrideValues(defaultExpectedValuesOdpTrigger, testCase.expected_value));
          }
      })
  }
})

test('Redirect Headers Validation Tests', async (t) => {
    for (testCase of redirectTestCases) {
        await t.test(testCase.name, (t) => {
            // Setup
            json = testCase.json;
            let metadata = {
              flags: overrideValues(defaultFlags, testCase.flags),
              header_options: {
                header_type: "redirect",
                source_type: null
              },
              expected_value: null
            };

            // Test
            output = validateRedirect(json, metadata);
            result = output.result;
            expected = output.expected_value;


            // Assert
            isValid = (result.errors.length === 0);
            assert.equal(/* actual */ isValid, /* expected */ testCase.result.valid);
            assert.deepEqual(/* actual */ result.warnings.map(warning => warning.formattedWarning), /* expected */ testCase.result.warnings);
            assert.deepEqual(/* actual */ result.errors.map(error => error.formattedError), /* expected */ testCase.result.errors);
            if (testCase?.expected_value !== undefined) {
              assert.deepEqual(/* actual */ expected, /* expected */ overrideValues(defaultExpectedValuesRedirect, testCase.expected_value));
            }
        })
    }
})