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

var flagValues = {
  "feature-attribution-scopes": true,
  "feature-trigger-data-matching": true,
  "feature-coarse-event-report-destination": true,
  "feature-shared-source-debug-key": true,
  "feature-xna": true,
  "feature-shared-filter-data-keys": true,
  "feature-preinstall-check": true,
  "feature-enable-update-trigger-header-limit": true,
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

module.exports = {
  flagValues
};