/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.measurement.client;

public class FlagsConstants {
  public static final String KEY_DEFAULT_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT =
      "default_measurement_debug_join_key_hash_limit";
  public static final String
      KEY_DEFAULT_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION =
          "default_measurement_max_aggregate_deduplication_keys_per_registration";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS =
      "default_measurement_max_aggregate_report_upload_retry_window_ms";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_ATTRIBUTION_FILTERS =
      "default_measurement_max_attribution_filters";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION =
      "default_measurement_max_attributions_per_invocation";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID =
      "default_measurement_max_bytes_per_attribution_aggregate_key_id";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING =
      "default_measurement_max_bytes_per_attribution_filter_string";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW =
      "default_measurement_max_delayed_source_registration_window";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS =
      "default_measurement_max_event_report_upload_retry_window_ms";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET =
      "default_measurement_max_filter_maps_per_filter_set";
  public static final String KEY_DEFAULT_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER =
      "default_measurement_max_values_per_attribution_filter";
  public static final String KEY_DEFAULT_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST =
      "default_measurement_platform_debug_ad_id_matching_blocklist";
  public static final String KEY_DEFAULT_MEASUREMENT_PRIVACY_EPSILON =
      "default_measurement_privacy_epsilon";
  public static final String KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK =
      "disable_measurement_enrollment_check";
  public static final String KEY_ENROLLMENT_ENABLE_LIMITED_LOGGING =
      "enrollment_enable_limited_logging";
  public static final String KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED =
      "fledge_measurement_report_and_register_event_api_enabled";
  public static final String KEY_MAX_RESPONSE_BASED_REGISTRATION_SIZE_BYTES =
      "max_response_based_registration_size_bytes";
  public static final String KEY_MAX_TRIGGER_REGISTRATION_HEADER_SIZE_BYTES =
      "max_trigger_registration_header_size_bytes";
  public static final String KEY_DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT =
      "measurement_vtc_configurable_max_event_reports_count";
  public static final String KEY_MEASUREMENT_DEBUG_KEY_AD_ID_MATCHING_ENROLLMENT_BLOCKLIST =
      "measurement_debug_key_ad_id_matching_enrollment_blocklist";
  public static final String KEY_MEASUREMENT_ADR_BUDGET_PER_ORIGIN_PUBLISHER_WINDOW =
      "Measurement__adr_budget_per_origin_publisher_window";
  public static final String KEY_MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW =
      "Measurement__adr_budget_per_publisher_window";
  public static final String KEY_MEASUREMENT_ADR_BUDGET_WINDOW_LENGTH_MILLIS =
      "measurement_adr_budget_window_length_millis";
  public static final String KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG =
      "measurement_aggregate_report_delay_config";
  public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_ENABLED =
      "measurement_aggregation_coordination_origin_enabled";
  public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST =
      "measurement_aggregation_coordinator_origin_list";
  public static final String KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH =
      "measurement_aggregation_coordinator_path";
  public static final String KEY_MEASUREMENT_ENABLE_MAX_AGGREGATE_REPORTS_PER_SOURCE =
      "measurement_enable_max_aggregate_reports_per_source";
  public static final String
      KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT =
          "measurement_attribution_scope_max_info_gain_dual_destination_event";
  public static final String
      KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_NAVIGATION =
          "measurement_attribution_scope_max_info_gain_dual_destination_navigation";
  public static final String KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT =
      "measurement_attribution_scope_max_info_gain_event";
  public static final String KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION =
      "measurement_attribution_scope_max_info_gain_navigation";
  public static final String KEY_MEASUREMENT_DATA_EXPIRY_WINDOW_MS =
      "measurement_data_expiry_window_ms";
  public static final String KEY_MEASUREMENT_DB_SIZE_LIMIT = "measurement_db_size_limit";
  public static final String KEY_MEASUREMENT_DEBUG_JOIN_KEY_ENROLLMENT_ALLOWLIST =
      "measurement_debug_join_key_enrollment_allowlist";
  public static final String KEY_MEASUREMENT_DEBUG_JOIN_KEY_HASH_LIMIT =
      "measurement_debug_join_key_hash_limit";
  public static final String KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN =
      "measurement_default_aggregation_coordinator_origin";
  public static final String KEY_MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM =
      "measurement_default_destination_limit_algorithm";
  public static final String KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT =
      "measurement_destination_per_day_rate_limit";
  public static final String KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS =
      "measurement_destination_per_day_rate_limit_window_in_ms";
  public static final String KEY_MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW =
      "measurement_destination_rate_limit_window";
  public static final String KEY_MEASUREMENT_ENABLE_AGGREGATABLE_REPORT_PAYLOAD_PADDING =
      "measurement_enable_aggregatable_report_payload_padding";

  public static final String KEY_MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING =
      "Measurement__enable_aggregate_debug_reporting";
  public static final String KEY_MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS =
      "measurement_enable_aggregate_value_filters";
  public static final String KEY_MEASUREMENT_ENABLE_APP_PACKAGE_NAME_LOGGING =
      "measurement_enable_app_package_name_logging";
  public static final String KEY_MEASUREMENT_ENABLE_ARA_DEDUPLICATION_ALIGNMENT_V1 =
      "measurement_enable_ara_deduplication_alignment_v1";
  public static final String KEY_MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE =
      "measurement_enable_attribution_scope";
  public static final String KEY_MEASUREMENT_ENABLE_COARSE_EVENT_REPORT_DESTINATIONS =
      "measurement_enable_coarse_event_report_destinations";
  public static final String KEY_MEASUREMENT_ENABLE_CONFIGURABLE_EVENT_REPORTING_WINDOWS =
      "measurement_enable_configurable_event_reporting_windows";
  public static final String KEY_MEASUREMENT_ENABLE_DATASTORE_MANAGER_THROW_DATASTORE_EXCEPTION =
      "measurement_enable_datastore_manager_throw_datastore_exception";
  public static final String KEY_MEASUREMENT_ENABLE_DEBUG_REPORT =
      "measurement_enable_debug_report";

  public static final String KEY_MEASUREMENT_ENABLE_DELETE_REPORTS_ON_UNRECOVERABLE_EXCEPTION =
      "measurement_enable_delete_reports_on_unrecoverable_exception";
  public static final String KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_ALGORITHM_FIELD =
      "measurement_enable_destination_limit_algorithm_field";
  public static final String KEY_MEASUREMENT_ENABLE_DESTINATION_LIMIT_PRIORITY =
      "measurement_enable_source_destination_limit_priority";
  public static final String KEY_MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW =
      "measurement_enable_destination_per_day_rate_limit_window";
  public static final String KEY_MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT =
      "measurement_enable_destination_rate_limit";
  public static final String KEY_MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE =
      "measurement_enable_event_level_epsilon_in_source";
  public static final String
      KEY_MEASUREMENT_ENABLE_EVENT_TRIGGER_DEBUG_SIGNAL_FOR_COARSE_DESTINATION =
          "measurement_enable_event_trigger_debug_signal_for_coarse_destination";
  public static final String KEY_MEASUREMENT_ENABLE_FAKE_REPORT_TRIGGER_TIME =
      "measurement_enable_fake_report_trigger_time";
  public static final String KEY_MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS =
      "measurement_enable_fifo_destinations_delete_aggregate_reports";
  public static final String KEY_MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING =
      "measurement_enable_flexible_contribution_filtering";
  public static final String KEY_MEASUREMENT_DEFAULT_FILTERING_ID_MAX_BYTES =
      "measurement_default_filtering_id_max_bytes";
  public static final String KEY_MEASUREMENT_MAX_FILTERING_ID_MAX_BYTES =
      "measurement_max_filtering_id_max_bytes";

  public static final String KEY_MEASUREMENT_MAX_AGGREGATABLE_BUCKETS_PER_SOURCE_REGISTRATION =
      "measurement_max_aggregatable_buckets_per_source_registration";

  public static final String KEY_MEASUREMENT_MAX_LENGTH_PER_AGGREGATABLE_BUCKET =
      "measurement_max_length_per_aggregatable_bucket";

  public static final String KEY_MEASUREMENT_ENABLE_AGGREGATE_CONTRIBUTION_BUDGET_CAPACITY =
      "measurement_enable_aggregate_contribution_budget_capacity";
  public static final String KEY_MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT =
      "measurement_enable_header_error_debug_report";
  public static final String KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER =
      "measurement_enable_lookback_window_filter";
  public static final String KEY_MEASUREMENT_ENABLE_MIN_REPORT_LIFESPAN_FOR_UNINSTALL =
      "Measurement__enable_min_report_lifespan_for_uninstall";
  public static final String KEY_MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK =
      "measurement_enable_navigation_reporting_origin_check";
  public static final String KEY_MEASUREMENT_ENABLE_ODP_WEB_TRIGGER_REGISTRATION =
      "measurement_enable_odp_web_trigger_registration";
  public static final String KEY_MEASUREMENT_ENABLE_PREINSTALL_CHECK =
      "measurement_enable_preinstall_check";
  public static final String KEY_MEASUREMENT_ENABLE_REINSTALL_REATTRIBUTION =
      "measurement_enable_reinstall_reattribution";
  public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_CRYPTO_EXCEPTION =
      "measurement_enable_reporting_jobs_throw_crypto_exception";
  public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_JSON_EXCEPTION =
      "measurement_enable_reporting_jobs_throw_json_exception";
  public static final String KEY_MEASUREMENT_ENABLE_REPORTING_JOBS_THROW_UNACCOUNTED_EXCEPTION =
      "measurement_enable_reporting_jobs_throw_unaccounted_exception";
  public static final String
      KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT =
          "measurement_enable_separate_debug_report_types_for_attribution_rate_limit";
  public static final String KEY_MEASUREMENT_ENABLE_SHARED_FILTER_DATA_KEYS_XNA =
      "measurement_enable_shared_filter_data_keys_xna";
  public static final String KEY_MEASUREMENT_ENABLE_SHARED_SOURCE_DEBUG_KEY =
      "measurement_enable_shared_source_debug_key";
  public static final String KEY_MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING =
      "measurement_enable_source_deactivation_after_filtering";
  public static final String KEY_MEASUREMENT_ENABLE_SOURCE_DEBUG_REPORT =
      "measurement_enable_source_debug_report";
  public static final String KEY_MEASUREMENT_ENABLE_TRIGGER_CONTEXT_ID =
      "measurement_enable_trigger_context_id";
  public static final String KEY_MEASUREMENT_ENABLE_TRIGGER_DATA_MATCHING =
      "measurement_enable_trigger_data_matching";
  public static final String KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_REPORT =
      "measurement_enable_trigger_debug_report";
  public static final String KEY_MEASUREMENT_ENABLE_TRIGGER_DEBUG_SIGNAL =
      "measurement_enable_trigger_debug_signal";
  public static final String KEY_MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT =
      "measurement_enable_update_trigger_registration_header_limit";
  public static final String KEY_MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA =
      "measurement_enable_v1_source_trigger_data";
  public static final String KEY_MEASUREMENT_ENABLE_XNA = "measurement_enable_xna";
  public static final String KEY_MEASUREMENT_ENROLLMENT_CHECK = "measurement_enrollment_check";
  public static final String KEY_MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS =
      "measurement_event_reports_ctc_early_reporting_windows";
  public static final String KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS =
      "measurement_event_reports_vtc_early_reporting_windows";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORTS =
      "measurement_flex_api_max_event_reports";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_EVENT_REPORT_WINDOWS =
      "measurement_flex_api_max_event_report_windows";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT =
      "measurement_flex_api_max_information_gain_dual_destination_event";
  public static final String
      KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION =
          "measurement_flex_api_max_information_gain_dual_destination_navigation";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT =
      "measurement_flex_api_max_information_gain_event";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION =
      "measurement_flex_api_max_information_gain_navigation";
  public static final String KEY_MEASUREMENT_FLEX_API_MAX_TRIGGER_DATA_CARDINALITY =
      "measurement_flex_api_max_trigger_data_cardinality";
  public static final String KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED =
      "measurement_flexible_event_reporting_api_enabled";
  public static final String KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED =
      "measurement_is_click_verification_enabled";
  public static final String KEY_MEASUREMENT_KILL_SWITCH = "measurement_kill_switch";
  public static final String KEY_MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE =
      "Measurement__max_adr_count_per_source";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_ATTRIBUTION_PER_RATE_LIMIT_WINDOW =
      "measurement_max_aggregate_attribution_per_rate_limit_window";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION =
      "measurement_max_aggregate_deduplication_keys_per_registration";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION =
      "measurement_max_aggregate_keys_per_source_registration";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_TRIGGER_REGISTRATION =
      "measurement_max_aggregate_keys_per_trigger_registration";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION =
      "measurement_max_aggregate_reports_per_destination";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE =
      "measurement_max_aggregate_reports_per_source";
  public static final String KEY_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS =
      "measurement_max_aggregate_report_upload_retry_window_ms";
  public static final String KEY_MEASUREMENT_MAX_ATTRIBUTION_FILTERS =
      "measurement_max_attribution_filters";
  public static final String KEY_MEASUREMENT_MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW =
      "measurement_max_attribution_per_rate_limit_window";
  public static final String KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPE_LENGTH =
      "measurement_max_attribution_scope_length";
  public static final String KEY_MEASUREMENT_MAX_ATTRIBUTION_SCOPES_PER_SOURCE =
      "measurement_max_attribution_scopes_per_source";
  public static final String KEY_MEASUREMENT_MAX_ATTRIBUTIONS_PER_INVOCATION =
      "measurement_max_attributions_per_invocation";
  public static final String KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID =
      "measurement_max_bytes_per_attribution_aggregate_key_id";
  public static final String KEY_MEASUREMENT_MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING =
      "measurement_max_bytes_per_attribution_filter_string";
  public static final String KEY_MEASUREMENT_MAX_DELAYED_SOURCE_REGISTRATION_WINDOW =
      "measurement_max_delayed_source_registration_window";
  public static final String KEY_MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW =
      "measurement_max_destinations_per_publisher_per_rate_limit_window";
  public static final String
      KEY_MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW =
          "measurement_max_dest_per_publisher_x_enrollment_per_rate_limit_window";
  public static final String KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE =
      "measurement_max_distinct_destinations_in_active_source";
  public static final String KEY_MEASUREMENT_MAX_DISTINCT_ENROLLMENTS_IN_ATTRIBUTION =
      "measurement_max_distinct_enrollments_in_attribution";
  public static final String KEY_MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE =
      "measurement_max_distinct_reporting_origins_in_source";
  public static final String KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION =
      "measurement_max_distinct_enrollments_in_attribution";
  public static final String KEY_MEASUREMENT_MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION =
      "measurement_max_distinct_web_destinations_in_source_registration";
  public static final String KEY_MEASUREMENT_MAX_EVENT_ATTRIBUTION_PER_RATE_LIMIT_WINDOW =
      "measurement_max_event_attribution_per_rate_limit_window";
  public static final String KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION =
      "measurement_max_event_reports_per_destination";
  public static final String KEY_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS =
      "measurement_max_event_report_upload_retry_window_ms";
  public static final String KEY_MEASUREMENT_MAX_FILTER_MAPS_PER_FILTER_SET =
      "measurement_max_filter_maps_per_filter_set";
  public static final String KEY_MEASUREMENT_MAX_INSTALL_ATTRIBUTION_WINDOW =
      "measurement_max_install_attribution_window";
  public static final String KEY_MEASUREMENT_MAX_LENGTH_OF_TRIGGER_CONTEXT_ID =
      "measurement_max_length_of_trigger_context_id";
  public static final String KEY_MEASUREMENT_MAX_POST_INSTALL_EXCLUSIVITY_WINDOW =
      "measurement_max_post_install_exclusivity_window";
  public static final String KEY_MEASUREMENT_MAX_REGISTRATION_REDIRECTS =
      "measurement_max_registration_redirects";
  public static final String KEY_MEASUREMENT_MAX_REGISTRATIONS_PER_JOB_INVOCATION =
      "measurement_max_registrations_per_job_invocation";
  public static final String KEY_MEASUREMENT_MAX_REINSTALL_REATTRIBUTION_WINDOW_SECONDS =
      "measurement_max_reinstall_reattribution_window_seconds";
  public static final String
      KEY_MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW =
          "measurement_max_reporting_origins_per_source_reporting_site_per_window";
  public static final String KEY_MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
      "measurement_max_reporting_register_source_expiration_in_seconds";
  public static final String KEY_MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION =
      "measurement_max_report_states_per_source_registration";
  public static final String KEY_MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST =
      "measurement_max_retries_per_registration_request";
  public static final String KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER =
      "measurement_max_sources_per_publisher";
  public static final String KEY_MEASUREMENT_MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE =
      "measurement_max_sum_of_aggregate_values_per_source";
  public static final String KEY_MEASUREMENT_MAX_TRIGGERS_PER_DESTINATION =
      "measurement_max_triggers_per_destination";
  public static final String KEY_MEASUREMENT_MAX_VALUES_PER_ATTRIBUTION_FILTER =
      "measurement_max_values_per_attribution_filter";
  public static final String KEY_MEASUREMENT_MINIMUM_AGGREGATABLE_REPORT_WINDOW_IN_SECONDS =
      "measurement_minimum_aggregatable_report_window_in_seconds";
  public static final String KEY_MEASUREMENT_MINIMUM_EVENT_REPORT_WINDOW_IN_SECONDS =
      "measurement_minimum_event_report_window_in_seconds";
  public static final String KEY_MEASUREMENT_MIN_INSTALL_ATTRIBUTION_WINDOW =
      "measurement_min_install_attribution_window";
  public static final String KEY_MEASUREMENT_MIN_POST_INSTALL_EXCLUSIVITY_WINDOW =
      "measurement_min_post_install_exclusivity_window";
  public static final String KEY_MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW =
      "measurement_min_reporting_origin_update_window";
  public static final String KEY_MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
      "measurement_min_reporting_register_source_expiration_in_seconds";
  public static final String KEY_MEASUREMENT_MIN_REPORT_LIFESPAN_FOR_UNINSTALL_SECONDS =
      "Measurement__min_report_lifespan_for_uninstall_seconds";
  public static final String KEY_MEASUREMENT_NULL_AGGREGATE_REPORT_ENABLED =
      "measurement_null_aggregate_report_enabled";
  public static final String KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME =
      "measurement_null_agg_report_rate_excl_source_registration_time";
  public static final String KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME =
      "measurement_null_agg_report_rate_incl_source_registration_time";
  public static final String KEY_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_BLOCKLIST =
      "measurement_platform_debug_ad_id_matching_blocklist";
  public static final String KEY_MEASUREMENT_PLATFORM_DEBUG_AD_ID_MATCHING_LIMIT =
      "measurement_platform_debug_ad_id_matching_limit";
  public static final String KEY_MEASUREMENT_PRIVACY_EPSILON = "measurement_privacy_epsilon";
  public static final String KEY_MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS =
      "measurement_rate_limit_window_milliseconds";
  public static final String KEY_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED =
      "measurement_report_and_register_event_api_enabled";
  public static final String KEY_MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS =
      "measurement_reporting_job_service_batch_window_millis";
  public static final String KEY_MEASUREMENT_REPORT_RETRY_LIMIT = "measurement_report_retry_limit";
  public static final String KEY_MEASUREMENT_REPORT_RETRY_LIMIT_ENABLED =
      "measurement_report_retry_limit_enabled";
  public static final String KEY_MEASUREMENT_ROLLBACK_DELETION_APP_SEARCH_KILL_SWITCH =
      "measurement_rollback_deletion_app_search_kill_switch";
  public static final String KEY_MEASUREMENT_ROLLBACK_DELETION_KILL_SWITCH =
      "measurement_rollback_deletion_kill_switch";
  public static final String KEY_MEASUREMENT_ROLLBACK_DELETION_R_ENABLED =
      "measurement_rollback_deletion_r_enabled";
  public static final String
      KEY_MEASUREMENT_SOURCE_REGISTRATION_TIME_OPTIONAL_FOR_AGG_REPORTS_ENABLED =
          "measurement_source_registration_time_optional_for_agg_reports_enabled";
  public static final String KEY_MEASUREMENT_THROW_UNKNOWN_EXCEPTION_SAMPLING_RATE =
      "measurement_throw_unknown_exception_sampling_rate";
  public static final String KEY_MEASUREMENT_TRIGGER_DEBUG_SIGNAL_PROBABILITY_FOR_FAKE_REPORTS =
      "measurement_trigger_debug_signal_probability_for_fake_reports";
  public static final String KEY_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT =
      "measurement_vtc_configurable_max_event_reports_count";
  public static final String KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST = "web_context_client_allow_list";
}
