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

const triggerTestCases = [
    {
        name: "(String) Debug Key | Valid",
        flags: {},
        json: "{\"debug_key\":\"1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Key | Invalid",
        flags: {},
        json: "{\"debug_key\":1000}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be a string: `debug_key`"]
        }
    },
    {
        name: "(Negative) Debug Key | Invalid",
        flags: {},
        json: "{\"debug_key\":\"-1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"]
        }
    },
    {
        name: "(Non-Numeric) Debug Key | Invalid",
        flags: {},
        json: "{\"debug_key\":\"true\"}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"]
        }
    },
    {
        name: "(String) Debug Join Key | Valid",
        flags: {},
        type: "app",
        json: "{\"debug_join_key\":\"66784\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Join Key | Valid",
        flags: {},
        type: "app",
        json: "{\"debug_join_key\":66784}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Boolean) Debug Reporting | Valid",
        flags: {},
        json: "{\"debug_reporting\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Reporting | Valid",
        flags: {},
        json: "{\"debug_reporting\":\"true\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Debug Reporting | Valid",
        flags: {},
        json: "{\"debug_reporting\":99}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Ad ID | Valid",
        flags: {},
        json: "{\"debug_ad_id\":\"11756\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Ad ID | Valid",
        flags: {},
        json: "{\"debug_ad_id\":11756}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Disabled) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Array) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\", 1, \"b\"]}",
        result: {
            valid: false,
            errors: ["must be an array of strings: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Number of Scopes Per Source) Attribution Scopes | Invalid",
        flags: {
            "max_32_bit_integer": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\", \"b\", \"c\"]}",
        result: {
            valid: false,
            errors: ["exceeds max array size: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max String Length Per Scope) Attribution Scopes | Invalid",
        flags: {
            "max_32_bit_integer": 5
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"12345\",\"1234\",\"123456\"]}",
        result: {
            valid: false,
            errors: ["element at index: 2 exceeds max string length: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) X Network Key Mapping | Valid",
        flags: {
            "feature-xna": false
        },
        json: "{\"x_network_key_mapping\":[1]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) X Network Key Mapping | Invalid",
        flags: {},
        json: "{\"x_network_key_mapping\":[1]}",
        result: {
            valid: false,
            errors: ["must be an object: `x_network_key_mapping`"],
            warnings: []
        }
    },
    {
        name: "(Null Value) X Network Key Mapping | Invalid",
        flags: {},
        json: "{\"x_network_key_mapping\":{\"key1\":\"0x1\", \"key2\":null}}",
        result: {
            valid: false,
            errors: ["all values must be non-null: `x_network_key_mapping`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Value) X Network Key Mapping | Invalid",
        flags: {},
        json: "{\"x_network_key_mapping\":{\"key1\":\"0x1\", \"key2\":2}}",
        result: {
            valid: false,
            errors: ["all values must be strings: `x_network_key_mapping`"],
            warnings: []
        }
    },
    {
        name: "(Does Not Start With 0x) X Network Key Mapping | Invalid",
        flags: {},
        json: "{\"x_network_key_mapping\":{\"key1\":\"0x1\", \"key2\":\"1x1\"}}",
        result: {
            valid: false,
            errors: ["all values must start with 0x: `x_network_key_mapping`"],
            warnings: []
        }
    },
    {
        name: "X Network Key Mapping | Valid",
        flags: {},
        json: "{\"x_network_key_mapping\":{\"key1\":\"0x1\", \"key2\":\"0x2\"}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Disabled) Aggregation Coordinator Origin | Valid",
        flags: {
            "feature-aggregation-coordinator-origin": false
        },
        json: "{\"aggregation_coordinator_origin\":false}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Aggregation Coordinator Origin | Valid",
        flags: {},
        json: "{\"aggregation_coordinator_origin\":\"https://valid.cloud.coordination.test\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Aggregation Coordinator Origin | Invalid",
        flags: {},
        json: "{\"aggregation_coordinator_origin\":false}",
        result: {
            valid: false,
            errors: ["invalid URL format: `aggregation_coordinator_origin`"],
            warnings: []
        }
    },
    {
        name: "(Invalid URL - Missing Scheme) Aggregation Coordinator Origin | Invalid",
        flags: {},
        json: "{\"aggregation_coordinator_origin\":\"web-destination.test\"}",
        result: {
            valid: false,
            errors: ["invalid URL format: `aggregation_coordinator_origin`"],
            warnings: []
        }
    },
    {
        name: "(Empty String) Aggregation Coordinator Origin | Invalid",
        flags: {},
        json: "{\"aggregation_coordinator_origin\":\"\"}",
        result: {
            valid: false,
            errors: ["value must be non-empty: `aggregation_coordinator_origin`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Aggregatable Source Registration Time | Valid",
        flags: {
            "feature-source-registration-time-optional-for-agg-reports": false
        },
        json: "{\"aggregatable_source_registration_time\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String - Case-Insensitive) Aggregatable Source Registration Time | Valid",
        flags: {},
        json: "{\"aggregatable_source_registration_time\":\"iNCLUDe\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Aggregatable Source Registration Time | Invalid",
        flags: {},
        json: "{\"aggregatable_source_registration_time\":true}",
        result: {
            valid: false,
            errors: ["must equal 'INCLUDE' or 'EXCLUDE' (case-insensitive): `aggregatable_source_registration_time`"],
            warnings: []
        }
    },
    {
        name: "(Not INCLUDE or EXCLUDE) Aggregatable Source Registration Time | Invalid",
        flags: {},
        json: "{\"aggregatable_source_registration_time\":\"INVALID\"}",
        result: {
            valid: false,
            errors: ["must equal 'INCLUDE' or 'EXCLUDE' (case-insensitive): `aggregatable_source_registration_time`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Trigger Context ID | Valid",
        flags: {
            "feature-trigger-context-id": false
        },
        json: "{\"trigger_context_id\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Trigger Context ID | Invalid",
        flags: {},
        json: "{\"trigger_context_id\":true}",
        result: {
            valid: false,
            errors: ["must be a string: `trigger_context_id`"],
            warnings: []
        }
    },
    {
        name: "(Aggregatable Source Registration Time Not Present) Trigger Context ID | Valid",
        flags: {},
        json: "{\"trigger_context_id\":\"1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Aggregatable Source Registration Time - INCLUDE) Trigger Context ID | Invalid",
        flags: {},
        json: "{\"trigger_context_id\":\"1\", \"aggregatable_source_registration_time\":\"inCLUDE\"}",
        result: {
            valid: false,
            errors: ["aggregatable_source_registration_time must not have the value 'INCLUDE': `trigger_context_id`"],
            warnings: []
        }
    },
    {
        name: "(Aggregatable Source Registration Time - EXCLUDE) Trigger Context ID | Valid",
        flags: {},
        json: "{\"trigger_context_id\":\"1\", \"aggregatable_source_registration_time\":\"EXCLUDE\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Exceeds Max String Length) Trigger Context ID | Invalid",
        flags: {
            "max_trigger_context_id_string_length": 5
        },
        json: "{\"trigger_context_id\":\"123456\", \"aggregatable_source_registration_time\":\"EXCLUDE\"}",
        result: {
            valid: false,
            errors: ["max string length exceeded: `trigger_context_id`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Object Array) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[1, 2]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String `trigger_data`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"trigger_data\":1}]}",
        result: {
            valid: false,
            errors: ["'trigger_data' must be a string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String `trigger_data`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"trigger_data\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'trigger_data' must be an uint64 (must match /^[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Negative `trigger_data`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"trigger_data\":\"-1\"}]}",
        result: {
            valid: false,
            errors: ["'trigger_data' must be an uint64 (must match /^[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Positive `trigger_data`) Event Trigger Data | Valid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"trigger_data\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String `priority`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"priority\":1}]}",
        result: {
            valid: false,
            errors: ["'priority' must be a string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String `priority`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"priority\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'priority' must be an int64 (must match /^-?[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Numeric String `priority`) Event Trigger Data | Valid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"priority\":\"-1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric `value`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"value\":\"2\"}]}",
        result: {
            valid: false,
            errors: ["'value' must be a number: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Signed Long `value`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"value\":-9e100}]}",
        result: {
            valid: false,
            errors: ["'value' must be an int64 (must match /^-?[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Min Exceeded `value`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"value\":0}]}",
        result: {
            valid: false,
            errors: ["'value' must be greater than 0: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Max Exceeded `value`) Event Trigger Data | Invalid",
        flags: {
            "max_bucket_threshold": ((1n << 2n) - 1n)
        },
        json: "{\"event_trigger_data\":[{\"value\":4}]}",
        result: {
            valid: false,
            errors: ["'value' exceeds max threshold of 3: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Valid `value`) Event Trigger Data | Valid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"value\":1}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String `deduplication_key`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"deduplication_key\":1}]}",
        result: {
            valid: false,
            errors: ["'deduplication_key' must be a string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String `deduplication_key`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"deduplication_key\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'deduplication_key' must be an uint64 (must match /^[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Negative `deduplication_key`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"deduplication_key\":\"-1\"}]}",
        result: {
            valid: false,
            errors: ["'deduplication_key' must be an uint64 (must match /^[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Positive `deduplication_key`) Event Trigger Data | Valid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"deduplication_key\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `filters`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"filters\":10}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an object or an array: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `not_filters`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"not_filters\":10}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an object or an array: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `filters`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an array of object(s): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `not_filters`) Event Trigger Data | Invalid",
        flags: {},
        json: "{\"event_trigger_data\":[{\"not_filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an array of object(s): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' array length exceeds the max filter maps per filter set limit: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' array length exceeds the max filter maps per filter set limit: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filters' exceeded max attribution filters: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}}]}",
        result: {
            valid: false,
            errors: ["'not_filters' exceeded max attribution filters: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `not_filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `not_filters`) Event Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `event_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Array `filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array `not_filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `not_filters`) Event Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"event_trigger_data\":[{\"not_filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Trigger Data Missing) Expected Value - Event Trigger Data",
        flags: {
            "max_bucket_threshold": (1n << 32n) - 1n,
            "max_filter_maps_per_filter_set": 20,
            "max_attribution_filters": 50,
            "max_values_per_attribution_filter": 50,
            "max_bytes_per_attribution_filter_string": 25
            
        },
        json: "{\"event_trigger_data\":[{\"priority\":\"-1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "event_trigger_data": "[{\"trigger_data\":0,\"priority\":-1}]"
        }
    },
    {
        name: "(Non-Array) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Object Array) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":[1, 2]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Missing `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":[{\"source_keys\":1}]}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Null `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":null}]}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Empty String `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"\"}]}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Empty String `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {},
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"\"}]}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Starting Characters `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"1x6E2\"}]}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Min Bytes Size `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0X\"}]}",
        result: {
            valid: false,
            errors: ["key piece string size must be in the byte range (3 bytes - 34 bytes): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Max Bytes Size `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0Xa1B2C3d4E5f6\"}]}",
        result: {
            valid: false,
            errors: ["key piece string size must be in the byte range (3 bytes - 34 bytes): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non Hexademical `key_piece`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x22g3c\"}]}",
        result: {
            valid: false,
            errors: ["key piece must be hexadecimal: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array `source_keys`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":{}}]}",
        result: {
            valid: false,
            errors: ["'source_keys' must be an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Should Check Filter Size - Exceeds Limit `source_keys`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":[\"1\",\"2\",\"3\"]}]}",
        result: {
            valid: false,
            errors: ["'source_keys' array size exceeds max aggregate keys per trigger registration limit: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Should Not Check Filter Size - Exceeds Limit `source_keys`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "feature-enable-update-trigger-header-limit": true,
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":[\"1\",\"2\",\"3\"]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String Element `source_keys`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":[\"1\",2]}]}",
        result: {
            valid: false,
            errors: ["each element in 'source_keys' must be a string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Empty String Element `source_keys`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":[\"\"]}]}",
        result: {
            valid: false,
            errors: ["'source_keys' null or empty aggregate key string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Key Byte Size Exceeded `source_keys`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"source_keys\":[\"abcd\"]}]}",
        result: {
            valid: false,
            errors: ["'source_keys' exceeded max bytes per attribution aggregate key id string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":10}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an object or an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":10}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an object or an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an array of object(s): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an array of object(s): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' array length exceeds the max filter maps per filter set limit: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' array length exceeds the max filter maps per filter set limit: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filters' exceeded max attribution filters: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' exceeded max attribution filters: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `not_filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `not_filters`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Array `filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array `not_filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `not_filters`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"not_filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object `x_network_data`) Aggregatable Trigger Data | Invalid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"x_network_data\":[]}]}",
        result: {
            valid: false,
            errors: ["'x_network_data' must be an object: `aggregatable_trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Object `x_network_data`) Aggregatable Trigger Data | Valid",
        flags: {
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"aggregatable_trigger_data\":[{\"key_piece\":\"0x1\", \"x_network_data\":{}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Aggregatable Values | Invalid",
        flags: {},
        json: "{\"aggregatable_values\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Object) Aggregatable Values | Valid",
        flags: {},
        json: "{\"aggregatable_values\":{}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_values\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Empty String Element) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2
        },
        json: "{\"aggregatable_values\":{\"\":1}}",
        result: {
            valid: false,
            errors: ["null or empty aggregate key string: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Key Byte Size Exceeded) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_values\":{\"abcd\":1}}",
        result: {
            valid: false,
            errors: ["exceeded max bytes per attribution aggregate key id string: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_values\":{\"abc\":\"1\"}}",
        result: {
            valid: false,
            errors: ["aggregate key value must be a number: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Non-Integer) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_values\":{\"abc\":1.2}}",
        result: {
            valid: false,
            errors: ["must be an int32 (must match /^-?[0-9]+$/): `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Integer 64 Bit) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_values\":{\"abc\":2147483648}}",
        result: {
            valid: false,
            errors: ["must fit in a signed 32-bit integer: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Zero) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"aggregatable_values\":{\"abc\":0}}",
        result: {
            valid: false,
            errors: ["aggregate key value must be greater than 0: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Max Sum of Aggregate Values Per Source) Aggregatable Values | Invalid",
        flags: {
            "max_aggregate_keys_per_trigger_registration": 2,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "max_sum_of_aggregate_values_per_source": 10
        },
        json: "{\"aggregatable_values\":{\"abc\":11}}",
        result: {
            valid: false,
            errors: ["aggregate key value exceeds the max sum of aggregate values per source: `aggregatable_values`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object) Filters | Invalid",
        flags: {},
        json: "{\"filters\":10}",
        result: {
            valid: false,
            errors: ["must be an object or an array: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects) Filters | Invalid",
        flags: {},
        json: "{\"filters\":[{}, [], {}]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `filters`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"filters\":[{}, {}, {}]}",
        result: {
            valid: false,
            errors: ["array length exceeds the max filter maps per filter set limit: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}",
        result: {
            valid: false,
            errors: ["exceeded max attribution filters: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"filters\":[{\"_lookback_window\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"_lookback_window\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"_lookback_window\":\"-2\"}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"_lookback_window\":-2}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid) Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"_lookback_window\":\"0\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"_filter_1\":[\"0\"]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"filters\":[{\"filter_1\":{\"name\": \"A\"}}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"filters\":[{\"filter_1\":[\"A\", 2]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded) Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `filters`"],
            warnings: []
        }
    },
    {
        name: "(Array) Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"filters\":[{\"filter_1\":[\"A\", \"123\"]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object) Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"filters\":{\"filter_1\":[\"A\", \"123\"]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object) Not Filters | Invalid",
        flags: {},
        json: "{\"not_filters\":10}",
        result: {
            valid: false,
            errors: ["must be an object or an array: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects) Not Filters | Invalid",
        flags: {},
        json: "{\"not_filters\":[{}, [], {}]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"not_filters\":[{}, {}, {}]}",
        result: {
            valid: false,
            errors: ["array length exceeds the max filter maps per filter set limit: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}",
        result: {
            valid: false,
            errors: ["exceeded max attribution filters: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"not_filters\":[{\"_lookback_window\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"_lookback_window\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `not_filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"_lookback_window\":\"-2\"}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"_lookback_window\":-2}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid) Not Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"_lookback_window\":\"0\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"_filter_1\":[\"0\"]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":{\"name\": \"A\"}}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":[\"A\", 2]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded) Not Filters | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `not_filters`"],
            warnings: []
        }
    },
    {
        name: "(Array) Not Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"not_filters\":[{\"filter_1\":[\"A\", \"123\"]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object) Not Filters | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"not_filters\":{\"filter_1\":[\"A\", \"123\"]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Aggregatable Deduplication Keys | Invalid",
        flags: {},
        json: "{\"aggregatable_deduplication_keys\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Exceed Max Aggregate Deduplication Keys) Aggregatable Deduplication Keys  | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[1, 2, 3]}",
        result: {
            valid: false,
            errors: ["exceeds max aggregate deduplication keys per registration limit: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Object Array) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[1, 2]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-String `deduplication_key`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"deduplication_key\":1}]}",
        result: {
            valid: false,
            errors: ["'deduplication_key' must be a string: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Negative `deduplication_key`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"deduplication_key\":\"-1\"}]}",
        result: {
            valid: false,
            errors: ["'deduplication_key' must be an uint64 (must match /^[0-9]+$/): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Positive `deduplication_key`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"deduplication_key\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":10}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an object or an array: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":10}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an object or an array: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an array of object(s): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an array of object(s): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' array length exceeds the max filter maps per filter set limit: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' array length exceeds the max filter maps per filter set limit: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filters' exceeded max attribution filters: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' exceeded max attribution filters: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `not_filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `not_filters`) Aggregatable Deduplication Keys | Invalid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `aggregatable_deduplication_keys`"],
            warnings: []
        }
    },
    {
        name: "(Array `filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array `not_filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `not_filters`) Aggregatable Deduplication Keys | Valid",
        flags: {
            "max_aggregate_deduplication_keys_per_registration": 2,
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"aggregatable_deduplication_keys\":[{\"not_filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Array of Non-Objects) Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{},{},[],{}]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Missing Required Key 'source_network') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"trigger_network\":\"A\"}]}",
        result: {
            valid: false,
            errors: ["'source_network' must be present and non-null in each element/object of the array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Null Value for Required Key 'source_network') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":null}]}",
        result: {
            valid: false,
            errors: ["'source_network' must be present and non-null in each element/object of the array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Required Key 'source_network' Present) Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":false}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String Required Key 'source_network' Present) Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object 'source_priority_range') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":[]}]}",
        result: {
            valid: false,
            errors: ["'source_priority_range' must be an object: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Object Missing 'start' and 'end' Keys 'source_priority_range') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{}}]}",
        result: {
            valid: false,
            errors: ["'source_priority_range' both keys ('start','end') must be present: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Null 'start' and 'end' Keys 'source_priority_range') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":null, \"end\":null}}]}",
        result: {
            valid: false,
            errors: ["'source_priority_range' both key values (start, end) must be string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Object Missing 'start' Key 'source_priority_range') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"end\":1}}]}",
        result: {
            valid: false,
            errors: ["'source_priority_range' both keys ('start','end') must be present: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Object Missing 'end' Key 'source_priority_range') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":1}}]}",
        result: {
            valid: false,
            errors: ["'source_priority_range' both keys ('start','end') must be present: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Can't be Casted to a Number) Objects Key 'start') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":false, \"end\":\"2\"}}]}",
        result: {
            valid: false,
            errors: ["'start' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Can't be Casted to a Number) Objects Key 'end') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":1, \"end\":\"true\"}}]}",
        result: {
            valid: false,
            errors: ["'end' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Numeric) Both Objects Keys ('start' & 'end') Present 'source_priority_range') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":1, \"end\":2}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "((Numeric-String) Both Objects Keys ('start' & 'end') Present 'source_priority_range') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_priority_range\":{\"start\":\"1\", \"end\":\"2\"}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-(Array/Object) 'source_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":1}]}",
        result: {
            valid: false,
            errors: ["'source_filters' must be an object or an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Object) 'source_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":{}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "((Array) 'source_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array of Objects 'source_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{},[],{}]}]}",
        result: {
            valid: false,
            errors: ["'source_filters' must be an array of object(s): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-Array Filter Value 'source_filters') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"filter_1\":1}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Null Filter Value Array Elements 'source_filters') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"filter_1\":[null]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-String Filter Value Array Elements 'source_filters') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"filter_1\":[true]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | String Filter Value Array Elements 'source_filters') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"filter_1\":[\"true\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null Lookback Window 'source_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"_lookback_window\":null}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be a string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String Lookback Window 'source_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric String Lookback Window 'source_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"_lookback_window\":\"-1\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric Lookback Window 'source_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_filters\":[{\"_lookback_window\":-1}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-(Array/Object) 'source_not_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":1}]}",
        result: {
            valid: false,
            errors: ["'source_not_filters' must be an object or an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Object) 'source_not_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":{}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "((Array) 'source_not_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array of Objects 'source_not_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{},[],{}]}]}",
        result: {
            valid: false,
            errors: ["'source_not_filters' must be an array of object(s): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-Array Filter Value 'source_not_filters') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"filter_1\":1}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Null Filter Value Array Elements 'source_not_filters') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"filter_1\":[null]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-String Filter Value Array Elements 'source_not_filters') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"filter_1\":[true]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | String Filter Value Array Elements 'source_not_filters') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"filter_1\":[\"true\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null Lookback Window 'source_not_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"_lookback_window\":null}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be a string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String Lookback Window 'source_not_filters') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric String Lookback Window 'source_not_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"_lookback_window\":\"-1\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric Lookback Window 'source_not_filters') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_not_filters\":[{\"_lookback_window\":-1}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-(Array/Object) 'filter_data') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":1}]}",
        result: {
            valid: false,
            errors: ["'filter_data' must be an object or an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "((Object) 'filter_data') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":{}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "((Array) 'filter_data') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array of Objects 'filter_data') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{},[],{}]}]}",
        result: {
            valid: false,
            errors: ["'filter_data' must be an array of object(s): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-Array Filter Value 'filter_data') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"filter_1\":1}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Null Filter Value Array Elements 'filter_data') Attribution Config | Invalid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"filter_1\":[null]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Non-String Filter Value Array Elements 'filter_data') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"filter_1\":[true]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | String Filter Value Array Elements 'filter_data') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"filter_1\":[\"true\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Lookback Filter Flag Disabled | Lookback Window Key Present 'filter_data') Attribution Config | Valid",
        flags: {
            "feature-lookback-window-filter": false
        },
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"_lookback_window\":[\"true\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null Lookback Window 'filter_data') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"_lookback_window\":null}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be a string or able to cast to string: `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric String Lookback Window 'filter_data') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric String Lookback Window 'filter_data') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"_lookback_window\":\"-1\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric Lookback Window 'filter_data') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"filter_data\":[{\"_lookback_window\":-1}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric 'source_expiry_override') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_expiry_override\":\"true\"}]}",
        result: {
            valid: false,
            errors: ["'source_expiry_override' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric-String 'source_expiry_override') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_expiry_override\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric 'source_expiry_override') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_expiry_override\":1}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric 'priority') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"priority\":\"true\"}]}",
        result: {
            valid: false,
            errors: ["'priority' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric-String 'priority') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"priority\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric 'priority') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"priority\":1}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric 'expiry') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"expiry\":\"true\"}]}",
        result: {
            valid: false,
            errors: ["'expiry' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric-String 'expiry') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"expiry\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric 'expiry') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"expiry\":1}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric 'post_install_exclusivity_window') Attribution Config | Invalid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"post_install_exclusivity_window\":\"true\"}]}",
        result: {
            valid: false,
            errors: ["'post_install_exclusivity_window' must be an int64 (must match /^-?[0-9]+$/): `attribution_config`"],
            warnings: []
        }
    },
    {
        name: "(Numeric-String 'post_install_exclusivity_window') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"post_install_exclusivity_window\":\"1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Numeric 'post_install_exclusivity_window') Attribution Config | Valid",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"post_install_exclusivity_window\":1}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Lower Limit 'source_expiry_override') Expected Value - Attribution Config",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_expiry_override\":\"-1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "attribution_config": "[{\"source_network\":\"A\",\"source_expiry_override\":86400}]"
        }
    },
    {
        name: "(Upper Limit 'source_expiry_override') Expected Value - Attribution Config",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"source_expiry_override\":\"2592001\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "attribution_config": "[{\"source_network\":\"A\",\"source_expiry_override\":2592000}]"
        }
    },
    {
        name: "(Lower Limit 'expiry') Expected Value - Attribution Config",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"expiry\":\"-1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "attribution_config": "[{\"source_network\":\"A\",\"expiry\":86400}]"
        }
    },
    {
        name: "(Upper Limit 'expiry') Expected Value - Attribution Config",
        flags: {},
        json: "{\"attribution_config\":[{\"source_network\":\"A\", \"expiry\":\"2592001\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "attribution_config": "[{\"source_network\":\"A\",\"expiry\":2592000}]"
        }
    },
    {
        name: "(Non-Array) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Enable Flag Off) Named Budgets | Valid",
        flags: {
            "feature-aggregatable-named-budgets": false
        },
        json: "{\"named_budgets\":[{\"name\":\"budget1\"}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object Array) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[1, 2]}",
        result: {
            valid: false,
            errors: ["must be an array of object(s): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-String `name`) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[{\"name\":1}]}",
        result: {
            valid: false,
            errors: ["'name' must be a string: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `filters`) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[{\"filters\":10}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an object or an array: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array/Non-Object `not_filters`) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[{\"not_filters\":10}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an object or an array: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `filters`) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[{\"filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' must be an array of object(s): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Not an Array of Objects `not_filters`) Named Budgets | Invalid",
        flags: {},
        json: "{\"named_budgets\":[{\"not_filters\":[{}, [], {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' must be an array of object(s): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'filters' array length exceeds the max filter maps per filter set limit: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Filter Maps Per Filter Set `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{}, {}, {}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' array length exceeds the max filter maps per filter set limit: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filters' exceeded max attribution filters: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'not_filters' exceeded max attribution filters: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Flag Disabled `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Non-Numeric String `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_lookback_window\":\"A\"}]}]}",
        result: {
            valid: false,
            errors: ["'_lookback_window' must be an int64 (must match /^-?[0-9]+$/): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative String `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_lookback_window\":\"-2\"}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Negative Number `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_lookback_window\":-2}]}]}",
        result: {
            valid: false,
            errors: ["lookback_window must be a positive number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `filters`) Named Budgets | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Valid `not_filters`) Named Budgets | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_lookback_window\":\"0\"}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Filter Key Starts with Underscore `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"_filter_1\":[\"0\"]}]}]}",
        result: {
            valid: false,
            errors: ["'_filter_1' filter can not start with underscore: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":{\"name\": \"A\"}}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"B\", \"C\"]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":[\"A\", 2]}]}]}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded `not_filters`) Named Budgets | Invalid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}]}]}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Array `filters`) Named Budgets | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array `not_filters`) Named Budgets| Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":[{\"filter_1\":[\"A\", \"123\"]}]}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `filters`) Named Budgets | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object `not_filters`) Named Budgets | Valid",
        flags: {
            "max_filter_maps_per_filter_set": 2,
            "max_attribution_filters": 2,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"named_budgets\":[{\"not_filters\":{\"filter_1\":[\"A\", \"123\"]}}]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Missing Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": null}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"\"}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": 123}}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"1x3\"}}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":123}}",
        result: {
            valid: false,
            errors: ["`aggregation_coordinator_origin` must be a string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"\"}}",
        result: {
            valid: false,
            errors: ["`aggregation_coordinator_origin` must be non-empty: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"abc\"}}",
        result: {
            valid: false,
            errors: ["'aggregation_coordinator_origin' invalid URL format: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Debug Data) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":{}}}",
        result: {
            valid: false,
            errors: ["`debug_data` must be an array: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object Debug Data Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": [\"source-noised\"]},[]]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 1 must be an object: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": [\"source-noised\"]},{}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 1 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": null, \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Types Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Types Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": null}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Value Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Value Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": null, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": 1, \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"\", \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"1x3\", \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Value Exceeds Lower Limit) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 0, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `value` must be in range 1-65536: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Value Exceeds Upper Limit) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65537, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `value` must be in range 1-65536: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Debug Data Types) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": {}}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `types` must be an array: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Types) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": []}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `types` must non-empty: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Duplicates In Within The Same Objects) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\", \"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 duplicate report types are not allow within the same debug data object or across multiple debug data objects: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Duplicates In Across Different Objects) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 1 duplicate report types are not allow within the same debug data object or across multiple debug data objects: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Case Insensitive) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\"]}]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "aggregatable_debug_reporting":"{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}]}"
        }
    },
    {
        name: "(Debug Data Types - Ignore Unknown Report Type) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\", \"fake-report-type\"]}]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "aggregatable_debug_reporting":"{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}]}"
        }
    },
    {
        name: "(Null) Top-Level Key Values | Valid",
        flags: {},
        json: "{"
                + "\"debug_key\":null,"
                + "\"debug_join_key\":null,"
                + "\"debug_reporting\":null,"
                + "\"debug_ad_id\":null,"
                + "\"event_trigger_data\":null,"
                + "\"aggregatable_trigger_data\":null,"
                + "\"aggregatable_values\":null,"
                + "\"filters\":null,"
                + "\"not_filters\":null,"
                + "\"aggregatable_deduplication_keys\":null,"
                + "\"attribution_scopes\":null,"
                + "\"x_network_key_mapping\":null,"
                + "\"aggregation_coordinator_origin\":null,"
                + "\"aggregatable_source_registration_time\":null,"
                + "\"trigger_context_id\":null,"
                + "\"attribution_config\":null,"
                + "\"named_budgets\":null,"
                + "\"aggregatable_debug_reporting\": null"
            + "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) 2nd-Level Key Values | Valid",
        flags: {},
        json: "{"
                + "\"debug_key\":null,"
                + "\"debug_join_key\":null,"
                + "\"debug_reporting\":null,"
                + "\"debug_ad_id\":null,"
                + "\"event_trigger_data\":[{"
                    + "\"trigger_data\":null,"
                    + "\"priority\":null,"
                    + "\"value\":null,"
                    + "\"deduplication_key\":null,"
                    + "\"filters\":null,"
                    + "\"not_filters\":null"
                + "}],"
                + "\"aggregatable_trigger_data\":[{"
                    + "\"key_piece\":\"0x1\","
                    + "\"source_keys\":null,"
                    + "\"filters\":null,"
                    + "\"not_filters\":null,"
                    + "\"x_network_data\":null"
                + "}],"
                + "\"aggregatable_values\":null,"
                + "\"filters\":null,"
                + "\"not_filters\":null,"
                + "\"aggregatable_deduplication_keys\":[{"
                    + "\"deduplication_key\":null,"
                    + "\"filters\":null,"
                    + "\"not_filters\":null"
                + "}],"
                + "\"attribution_scopes\":null,"
                + "\"x_network_key_mapping\":null,"
                + "\"aggregation_coordinator_origin\":null,"
                + "\"aggregatable_source_registration_time\":null,"
                + "\"trigger_context_id\":null,"
                + "\"attribution_config\":[{"
                    + "\"source_network\":\"A\","
                    + "\"source_priority_range\":null,"
                    + "\"source_filters\":null,"
                    + "\"source_not_filters\":null,"
                    + "\"filter_data\":null,"
                    + "\"source_expiry_override\":null,"
                    + "\"priority\":null,"
                    + "\"expiry\":null,"
                    + "\"post_install_exclusivity_window\":null"
                + "}],"
                + "\"named_budgets\":[{"
                    + "\"name\":null,"
                    + "\"filters\":null,"
                    + "\"not_filters\":null"
                + "}],"
                + "\"aggregatable_debug_reporting\":{"
                    + "\"key_piece\":\"0x1\","
                    + "\"aggregation_coordinator_origin\":null,"
                    + "\"debug_data\":null"
                + "}"
            + "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "Case Insensitive - Process Error | Invalid",
        flags: {},
        json: "{\"X_NETWORK_KEY_Mapping\":[1]}",
        result: {
            valid: false,
            errors: ["must be an object: `x_network_key_mapping`"],
            warnings: []
        }
    },
    {
        name: "Expected Value - Default",
        flags: {},
        json: "{}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
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
            "aggregatable_source_registration_time": "EXCLUDE",
            "trigger_context_id": null,
            "attribution_scopes": null,
            "named_budgets": null,
            "aggregatable_debug_reporting":null
        }
    },
    {
        name: "Expected Value - Populated Fields",
        flags: {},
        json: "{"
                + "\"attribution_config\": [{"
                    + "\"source_network\":1,"
                    + "\"source_priority_range\":{\"start\":\"1\", \"end\":\"2\"},"
                    + "\"source_filters\":[{\"_lookback_window\":\"-1\"}, {\"_lookback_window\":\"-3\", \"filter_1\":[\"A\", 2]}],"
                    + "\"source_not_filters\":{\"filter_1\":[\"A\", 2, true], \"_lookback_window\":-1},"
                    + "\"source_expiry_override\":\"86401\","
                    + "\"priority\":\"2\","
                    + "\"expiry\":\"86402\","
                    + "\"filter_data\":[{\"_lookback_window\":-2, \"filter_1\":[1, 2]}],"
                    + "\"post_install_exclusivity_window\":\"1\","
                    + "\"extra_key\":\"1\""
                + "}],"
                + "\"event_trigger_data\": [{"
                    + "\"trigger_data\":\"1\","
                    + "\"priority\":\"-1\","
                    + "\"value\":1,"
                    + "\"deduplication_key\":\"1\","
                    + "\"filters\":[{\"_lookback_window\":\"0\", \"filter_1\":[\"A\", \"B\"]}, {\"filter_2\":[\"C\", \"D\"]}],"
                    + "\"not_filters\":{\"_lookback_window\":1, \"filter_3\":[\"E\", \"F\"]},"
                    + "\"extra_key\":\"1\""
                + "}],"
                + "\"filters\":[{\"_lookback_window\":\"2\", \"filter_4\":[\"G\"]}, {\"filter_5\":[\"H\", \"I\"]}],"
                + "\"not_filters\": {\"_lookback_window\":3, \"filter_6\":[\"J\", \"K\"]},"
                + "\"aggregatable_trigger_data\": [{"
                    + "\"key_piece\":\"0x1\","
                    + "\"source_keys\":[\"1\",\"2\"],"
                    + "\"filters\":[{\"_lookback_window\":4, \"filter_7\":[\"L\"]}, {\"filter_8\":[\"M\", \"N\"]}],"
                    + "\"not_filters\":{\"_lookback_window\":\"5\", \"filter_9\":[\"O\", \"P\"]},"
                    + "\"extra_key\":1"
                + "}],"
                + "\"aggregatable_values\":{\"abc\":10},"
                + "\"aggregatable_deduplication_keys\": [{"
                    + "\"deduplication_key\":\"3\","
                    + "\"filters\":[{\"_lookback_window\":6, \"filter_10\":[\"Q\"]}, {\"filter_11\":[\"R\", \"S\"]}],"
                    + "\"not_filters\":{\"_lookback_window\":\"7\", \"filter_12\":[\"T\", \"U\"]},"
                    + "\"extra_key\":1"
                + "}],"
                + "\"named_budgets\": [{"
                    + "\"name\":\"budget1\","
                    + "\"filters\":[{\"_lookback_window\":8, \"filter_13\":[\"V\"]}, {\"filter_14\":[\"W\", \"X\"]}],"
                    + "\"not_filters\":{\"_lookback_window\":\"9\", \"filter_15\":[\"Y\", \"Z\"]},"
                    + "\"extra_key\":1"
                + "}],"
                + "\"debug_key\": \"1000\","
                + "\"debug_reporting\": \"true\","
                + "\"debug_join_key\": 100,"
                + "\"debug_ad_id\": 200,"
                + "\"x_network_key_mapping\": {\"key1\":\"0x1\", \"key2\":\"0x2\"},"
                + "\"aggregation_coordinator_origin\": \"https://valid.cloud.coordination.test\","
                + "\"aggregatable_source_registration_time\": \"exclude\","
                + "\"trigger_context_id\":\"1\","
                + "\"attribution_scopes\": [\"a\", \"b\", \"c\"],"
                + "\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\"]}]}"
            + "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "attribution_config": "[{"
                + "\"source_network\":\"1\","
                + "\"source_priority_range\":{\"start\":1,\"end\":2},"
                + "\"source_filters\":[{\"_lookback_window\":-1},{\"_lookback_window\":-3,\"filter_1\":[\"A\",\"2\"]}],"
                + "\"source_not_filters\":[{\"filter_1\":[\"A\",\"2\",\"true\"],\"_lookback_window\":-1}],"
                + "\"source_expiry_override\":86401,"
                + "\"priority\":2,"
                + "\"expiry\":86402,"
                + "\"filter_data\":[{\"_lookback_window\":-2,\"filter_1\":[\"1\",\"2\"]}],"
                + "\"post_install_exclusivity_window\":1"
            + "}]",
            "event_trigger_data": "[{"
                + "\"trigger_data\":1,"
                + "\"priority\":-1,"
                + "\"value\":1,"
                + "\"deduplication_key\":1,"
                + "\"filters\":[{\"_lookback_window\":\"0\",\"filter_1\":[\"A\",\"B\"]},{\"filter_2\":[\"C\",\"D\"]}],"
                + "\"not_filters\":[{\"_lookback_window\":1,\"filter_3\":[\"E\",\"F\"]}]"
            + "}]",
            "filters": "[{\"_lookback_window\":\"2\",\"filter_4\":[\"G\"]},{\"filter_5\":[\"H\",\"I\"]}]",
            "not_filters": "[{\"_lookback_window\":3,\"filter_6\":[\"J\",\"K\"]}]",
            "aggregatable_trigger_data": "[{"
                + "\"key_piece\":\"0x1\","
                + "\"source_keys\":[\"1\",\"2\"],"
                + "\"filters\":[{\"_lookback_window\":4,\"filter_7\":[\"L\"]},{\"filter_8\":[\"M\",\"N\"]}],"
                + "\"not_filters\":[{\"_lookback_window\":\"5\",\"filter_9\":[\"O\",\"P\"]}],"
                + "\"extra_key\":1"
            + "}]",
            "aggregatable_values": "{\"abc\":10}",
            "aggregatable_deduplication_keys": "[{"
                + "\"deduplication_key\":3,"
                + "\"filters\":[{\"_lookback_window\":6,\"filter_10\":[\"Q\"]},{\"filter_11\":[\"R\",\"S\"]}],"
                + "\"not_filters\":[{\"_lookback_window\":\"7\",\"filter_12\":[\"T\",\"U\"]}]"
            + "}]",
            "named_budgets": "[{"
                + "\"name\":\"budget1\","
                + "\"filters\":[{\"_lookback_window\":8,\"filter_13\":[\"V\"]},{\"filter_14\":[\"W\",\"X\"]}],"
                + "\"not_filters\":[{\"_lookback_window\":\"9\",\"filter_15\":[\"Y\",\"Z\"]}]"
            + "}]",
            "debug_key": 1000,
            "debug_reporting": true,
            "x_network_key_mapping": "{\"key1\":\"0x1\",\"key2\":\"0x2\"}",
            "debug_join_key": "100",
            "debug_ad_id": "200",
            "aggregation_coordinator_origin": "https://valid.cloud.coordination.test",
            "aggregatable_source_registration_time": "EXCLUDE",
            "trigger_context_id": "1",
            "attribution_scopes": "[\"a\",\"b\",\"c\"]",
            "aggregatable_debug_reporting": "{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}]}"
        }
    }
]

module.exports = {
    triggerTestCases
};