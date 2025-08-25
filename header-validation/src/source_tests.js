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

const sourceTestCases = [
    {
        name: "App Destination Present | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) App Destination | Invalid",
        flags: {},
        json: "{\"destination\":null}",
        result: {
            valid: false,
            errors: ["at least one field must be present and non-null: `destination or web_destination`"],
            warnings: []
        }
    },
    {
        name: "App Destination Missing Scheme | Invalid",
        flags: {},
        json: "{\"destination\":\"com.myapps\"}",
        result: {
            valid: false,
            errors: ["invalid URL format: `destination`"],
            warnings: []
        }
    },
    {
        name: "App Destination Invalid Host | Invalid",
        flags: {},
        json: "{\"destination\":\"http://com.myapps\"}",
        result: {
            valid: false,
            errors: ["app URL host/scheme is invalid: `destination`"],
            warnings: []
        }
    },
    {
        name: "(String) Web Destination Present | Valid",
        flags: {},
        json: "{\"web_destination\":\"https://web-destination.test\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array of String) Web Destination Present | Valid",
        flags: {},
        json: "{\"web_destination\":[\"https://web-destination.test\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non String/String Array) Web Destination Present | Invalid",
        flags: {},
        json: "{\"web_destination\":{\"url\":\"https://web-destination.test\"}}",
        result: {
            valid: false,
            errors: ["must be a string or an array of string: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Null) Web Destination Present | Invalid",
        flags: {},
        json: "{\"web_destination\":null}",
        result: {
            valid: false,
            errors: ["at least one field must be present and non-null: `destination or web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Number of Web Destinations Limit Exceeded) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://web-destination1.test\", \"https://web-destination2.test\"]}",
        result: {
            valid: false,
            errors: ["exceeded max distinct web destinations: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(No Web Destinations) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[]}",
        result: {
            valid: false,
            errors: ["no web destinations present: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Web Destination Format) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"web-destination.test\"]}",
        result: {
            valid: false,
            errors: ["invalid URL format: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Local Host Example 1) Web Destination | Valid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://127.0.0.1:8080\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Local Host Example 2) Web Destination | Valid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://localhost:8080\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Trailing Dot) Web Destination | Valid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://web-destination1.com.\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Max Hostname Length) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 5
        },
        json: "{\"web_destination\":[\"https://abc.com\"]}",
        result: {
            valid: false,
            errors: ["URL hostname/domain exceeds max character length: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Max Hostname Parts) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_parts": 3
        },
        json: "{\"web_destination\":[\"https://abc.def.ghi.jkl\"]}",
        result: {
            valid: false,
            errors: ["exceeded the max number of URL hostname parts: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Min Hostname Part Length) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1,
            "min_web_destination_hostname_part_character_length": 2,
            "max_web_destination_hostname_part_character_length": 5
        },
        json: "{\"web_destination\":[\"https://abc.d.efg\"]}",
        result: {
            valid: false,
            errors: ["URL hostname part character length must be in the range of 1-63: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Max Hostname Part Length) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1,
            "min_web_destination_hostname_part_character_length": 2,
            "max_web_destination_hostname_part_character_length": 5
        },
        json: "{\"web_destination\":[\"https://abc.defghi.jkl\"]}",
        result: {
            valid: false,
            errors: ["URL hostname part character length must be in the range of 1-63: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Hostname Part Character) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://abc!d.com\"]}",
        result: {
            valid: false,
            errors: ["URL hostname part character length must alphanumeric, hyphen, or underscore: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Hostname Part Starting Character) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://ab._cd.com\"]}",
        result: {
            valid: false,
            errors: ["invalid URL hostname part starting/ending character (hypen/underscore): `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Hostname Part Ending Character) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://ab.cd-.com\"]}",
        result: {
            valid: false,
            errors: ["invalid URL hostname part starting/ending character (hypen/underscore): `web_destination`"],
            warnings: []
        }
    },
    {
        name: "(Valid Last Hostname Part Starting Character) Web Destination | Valid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 1
        },
        json: "{\"web_destination\":[\"https://ab.1cde.com\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Invalid Last Hostname Part Starting Character) Web Destination | Invalid",
        flags: {
            "max_distinct_web_destinations_in_source_registration": 2
        },
        json: "{\"web_destination\":[\"https://127.0.0.1:8080\", \"https://ab.cde.1com\"]}",
        result: {
            valid: false,
            errors: ["last hostname part can not start with a number: `web_destination`"],
            warnings: []
        }
    },
    {
        name: "App and Web Destination Missing | Invalid",
        flags: {},
        json: "{\"source_event_id\":\"1234\"}",
        result: {
            valid: false,
            errors: ["at least one field must be present and non-null: `destination or web_destination`"],
            warnings: []
        }
    },
    {
        name: "App and Web Destination Null | Invalid",
        flags: {},
        json: "{\"destination\": null, \"web_destination\": null, \"source_event_id\":\"1234\"}",
        result: {
            valid: false,
            errors: ["at least one field must be present and non-null: `destination or web_destination`"],
            warnings: []
        }
    },
    {
        name: "(String) Source Event ID | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"1234\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Source Event ID | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":1234}",
        result: {
            valid: false,
            errors: ["must be a string: `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Source Event ID | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Source Event ID | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(String) Expiry | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Expiry | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Expiry | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `expiry`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Expiry | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `expiry`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit) Expected Value - Expiry",
        source_type: "event",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(Upper Limit) Expected Value - Expiry",
        source_type: "event",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 2592000000,
            "aggregatable_report_window": 2592000000
        }
    },
    {
        name: "(Round Up - Null Source Type) Expected Value - Expiry",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":129600}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 129600000,
            "aggregatable_report_window": 129600000
        }
    },
    {
        name: "(Round Up - Navigation Source Type) Expected Value - Expiry",
        source_type: "navigation",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":129600}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 129600000,
            "aggregatable_report_window": 129600000
        }
    },
    {
        name: "(Round Up - Event Source Type) Expected Value - Expiry",
        source_type: "event",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":129600}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 172800000,
            "aggregatable_report_window": 172800000
        }
    },
    {
        name: "(String) Event Report Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Event Report Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Event Report Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"-2000\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Event Report Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit) Expected Value - Event Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"event_report_window\":0}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "event_report_window": 3600000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(In-Range - Expiry is Min) Expected Value - Event Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"event_report_window\":86401}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "event_report_window": 86400000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(In-Range - Expiry is Not Min) Expected Value - Event Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"event_report_window\":3601}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "event_report_window": 3601000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(Upper Limit - Expiry is Min) Expected Value - Event Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"event_report_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "event_report_window": 86400000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(Upper Limit - Expiry is Not Min) Expected Value - Event Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "event_report_window": 2592000000
        }
    },
    {
        name: "(String) Aggregatable Report Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Aggregatable Report Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Aggregatable Report Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"-2000\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Aggregatable Report Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit) Expected Value - Aggregatable Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"aggregatable_report_window\":0}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "aggregatable_report_window": 3600000
        }
    },
    {
        name: "(In-Range - Expiry is Min) Expected Value - Aggregatable Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"aggregatable_report_window\":86401}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(In-Range - Expiry is Not Min) Expected Value - Aggregatable Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"aggregatable_report_window\":3601}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "aggregatable_report_window": 3601000
        }
    },
    {
        name: "(Upper Limit - Expiry is Min) Expected Value - Aggregatable Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":0, \"aggregatable_report_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry": 86400000,
            "aggregatable_report_window": 86400000
        }
    },
    {
        name: "(Upper Limit - Expiry is Not Min) Expected Value - Aggregatable Report Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "aggregatable_report_window": 2592000000
        }
    },
    {
        name: "(String) Priority | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Priority | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":1}",
        result: {
            valid: false,
            errors: ["must be a string: `priority`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Priority | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"-1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Priority | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `priority`"],
            warnings: []
        }
    },
    {
        name: "(Boolean) Debug Reporting | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_reporting\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Reporting | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_reporting\":\"true\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Debug Reporting | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_reporting\":99}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":1000}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be a string: `debug_key`"]
        }
    },
    {
        name: "(Negative) Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"-1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"]
        }
    },
    {
        name: "(Non-Numeric) Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"true\"}",
        result: {
            valid: true,
            errors: [],
            warnings: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"]
        }
    },
    {
        name: "(String) Install Attribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":\"86300\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Install Attribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":86300}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Install Attribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":\"-86300\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Install Attribution Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `install_attribution_window`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit) Expected Value - Install Attribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":0}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "install_attribution_window": 86400000
        }
    },
    {
        name: "(Upper Limit) Expected Value - Install Attribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "install_attribution_window": 2592000000
        }
    },
    {
        name: "(In-Range) Expected Value - Install Attribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":86401}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "install_attribution_window": 86401000
        }
    },
    {
        name: "(String) Post Install Exclusivity Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":\"987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Post Install Exclusivity Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":987654}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Post Install Exclusivity Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":\"-987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Post Install Exclusivity Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `post_install_exclusivity_window`"],
            warnings: []
        }

    },
    {
        name: "(Lower Limit) Expected Value - Post Install Exclusivity Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":-1}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "post_install_exclusivity_window": 0
        }
    },
    {
        name: "(Upper Limit) Expected Value - Post Install Exclusivity Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "post_install_exclusivity_window": 2592000000
        }
    },
    {
        name: "(In-Range) Expected Value - Post Install Exclusivity Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":1}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "post_install_exclusivity_window": 1000
        }
    },
    {
        name: "(Object) Filter Data | Valid",
        flags: {
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Filter Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":[\"A\"]}",
        result: {
            valid: false,
            errors: ["must be an object: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('source_type' Filter is Present) Filter Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"source_type\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["filter: source_type is not allowed: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\"], \"filter_2\":[\"B\"], \"filter_3\":[\"C\"]}}",
        result: {
            valid: false,
            errors: ["exceeded max attribution filters: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Byte Size Limit Exceeded) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter string: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Filter is Present) Filter Data + Lookback Window Filter | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"_lookback_window\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["filter: _lookback_window is not allowed: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Filter is Present + Flag Disabled) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"_lookback_window\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["'_lookback_window' filter can not start with underscore: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Starts with Underscore) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\"], \"_filter_2\":[\"B\"]}}",
        result: {
            valid: false,
            errors: ["'_filter_2' filter can not start with underscore: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":{\"name\": \"A\"}}}",
        result: {
            valid: false,
            errors: ["'filter_1' filter value must be an array: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", \"B\", \"C\"]}}",
        result: {
            valid: false,
            errors: ["'filter_1' exceeded max values per attribution filter: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", 2]}}",
        result: {
            valid: false,
            errors: ["'filter_1' filter values must be strings: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded) Filter Data | Invalid",
        flags: {
            "max_attribution_filters": 2,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2,
            "should_check_filter_size": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}}",
        result: {
            valid: false,
            errors: ["'ABCDEFGHIJKLMNOPQRSTUVWXYZ' exceeded max bytes per attribution filter value string: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(String) Debug Ad ID | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_ad_id\":\"11756\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Ad ID | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_ad_id\":11756}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Join Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_join_key\":\"66784\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Join Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_join_key\":66784}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Disabled) Trigger Data Matching | Invalid",
        flags: {
            "feature-trigger-data-matching": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"invalid\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String - Enum Value) Trigger Data Matching | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"MODuLus\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String - Unknown Value) Trigger Data Matching | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"invalid\"}",
        result: {
            valid: false,
            errors: ["value must be 'exact' or 'modulus' (case-insensitive): `trigger_data_matching`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Trigger Data Matching | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":true}",
        result: {
            valid: false,
            errors: ["value must be 'exact' or 'modulus' (case-insensitive): `trigger_data_matching`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Coarse Event Report Destinations | Valid",
        flags: {
            "feature-coarse-event-report-destination": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":99}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Boolean) Coarse Event Report Destinations | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Coarse Event Report Destinations | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":\"truE\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Coarse Event Report Destinations | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":99}",
        result: {
            valid: false,
            errors: ["must be a boolean: `coarse_event_report_destinations`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Shared Debug Key | Valid",
        flags: {
            "feature-shared-source-debug-key": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"-1234\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Shared Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Shared Debug Key | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Shared Debug Key | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `shared_debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Shared Debug Key | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `shared_debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Drop Source If Installed | Valid",
        flags: {
            "feature-preinstall-check": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":99}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Boolean) Drop Source If Installed | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Drop Source If Installed | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":\"truE\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Drop Source If Installed | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":99}",
        result: {
            valid: false,
            errors: ["must be a boolean: `drop_source_if_installed`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Shared Aggregation Keys | Valid",
        flags: {
            "feature-xna": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_aggregation_keys\":{}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array) Shared Aggregation Keys | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_aggregation_keys\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Shared Aggregation Keys | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_aggregation_keys\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `shared_aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Shared Filter Data Keys | Valid",
        flags: {
            "feature-shared-filter-data-keys": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_filter_data_keys\":{}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Array) Shared Filter Data Keys | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_filter_data_keys\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Shared Filter Data Keys | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_filter_data_keys\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `shared_filter_data_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Aggregation Keys | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Max Keys Exceeded) Aggregation Keys | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"key1\":\"value1\", \"key2\":\"value2\"}}",
        result: {
            valid: false,
            errors: ["exceeded max number of aggregation keys per source registration: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Empty String) Aggregation Keys | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"\":\"value\"}}",
        result: {
            valid: false,
            errors: ["null or empty aggregate key string: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Max Aggregation Key Byte Size Exceeded) Aggregation Keys | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abcd\":\"value\"}}",
        result: {
            valid: false,
            errors: ["exceeded max bytes per attribution aggregate key id string: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Null) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":null}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Empty String) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"\"}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Starting Characters) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"1x6E2\"}}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Min Bytes Size) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0X\"}}",
        result: {
            valid: false,
            errors: ["key piece string size must be in the byte range (3 bytes - 34 bytes): `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Max Bytes Size) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0Xa1B2C3d4E5f6\"}}",
        result: {
            valid: false,
            errors: ["key piece string size must be in the byte range (3 bytes - 34 bytes): `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non Hexademical) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0x22g3c\"}}",
        result: {
            valid: false,
            errors: ["key piece must be hexadecimal: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Missing Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\"]}}",
        result: {
            valid: false,
            errors: ["`limit` and `values` keys should be present: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Missing Values) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"limit\":1}}",
        result: {
            valid: false,
            errors: ["`limit` and `values` keys should be present: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\"], \"limit\":\"1\"}}",
        result: {
            valid: false,
            errors: ["`limit` must be numeric: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Decimal Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\"], \"limit\":1.5}}",
        result: {
            valid: false,
            errors: ["'limit' must be an int64 (must match /^-?[0-9]+$/): `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Values Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":{}, \"limit\":1}}",
        result: {
            valid: false,
            errors: ["`values` must be an array: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Value Array Element Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", 2], \"limit\":1}}",
        result: {
            valid: false,
            errors: ["`values` must be an array of strings: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Value Array Size Exceeds Max Scopes Per Source) Attribution Scopes | Invalid",
        flags: {
            "max_attribution_scopes_per_source": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\", \"3\"], \"limit\":1}}",
        result: {
            valid: false,
            errors: ["`values` length exceeds the max number of scopes per source: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Value Array Element Exceeds Max Scope String Length) Attribution Scopes | Invalid",
        flags: {
            "max_attribution_scope_string_length": 5
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\", \"123456\"], \"limit\":1}}",
        result: {
            valid: false,
            errors: ["`values` element at index: 2 exceeded max scope string length: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric Max Event States) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":1, \"max_event_states\":\"1\"}}",
        result: {
            valid: false,
            errors: ["`max_event_states` must be numeric: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Decimal Max Event States) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":1, \"max_event_states\":1.5}}",
        result: {
            valid: false,
            errors: ["'max_event_states' must be an int64 (must match /^-?[0-9]+$/): `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Zero Max Event States) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":1, \"max_event_states\":0}}",
        result: {
            valid: false,
            errors: ["`max_event_states` must be greater than 0: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Negative Max Event States) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":1, \"max_event_states\":-1}}",
        result: {
            valid: false,
            errors: ["`max_event_states` must be greater than 0: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Max Event States Exceeds Max Report States Per Source) Attribution Scopes | Invalid",
        flags: {
            "max_report_states_per_source_registration": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":1, \"max_event_states\":4}}",
        result: {
            valid: false,
            errors: ["`max_event_states` exceeds max report states per source registration: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Zero Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":0, \"max_event_states\":3}}",
        result: {
            valid: false,
            errors: ["`limit` must be greater than zero: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Negative Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\"], \"limit\":-1, \"max_event_states\":3}}",
        result: {
            valid: false,
            errors: ["`limit` must be greater than zero: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Value Array Size Exceeds Provided Limit) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[\"1\", \"2\", \"3\"], \"limit\":2, \"max_event_states\":3}}",
        result: {
            valid: false,
            errors: ["`value` array size exceeds the provided `limit`: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Empty Value Array) Attribution Scopes | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{\"values\":[], \"limit\":2, \"max_event_states\":3}}",
        result: {
            valid: false,
            errors: ["`value` array must not be empty: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Flag Disabled) Reinstall Reattribution Window| Valid",
        flags: {
            "feature-enable-reinstall-reattribution": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"true\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Reinstall Reattribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Reinstall Reattribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":987654}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Reinstall Reattribution Window | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"-987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Reinstall Reattribution Window | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `reinstall_reattribution_window`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit) Expected Value - Reinstall Reattribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":-1}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "reinstall_reattribution_window": 0
        }
    },
    {
        name: "(Upper Limit) Expected Value - Reinstall Reattribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":9999999}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "reinstall_reattribution_window": 7776000000
        }
    },
    {
        name: "(In-Range) Expected Value - Reinstall Reattribution Window",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":1}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "reinstall_reattribution_window": 1000
        }
    },
    {
        name: "(Enable Flag Off) Named Budgets | Valid",
        flags: {
            "feature-aggregatable-named-budgets": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\": {\"budget1\": -1000}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Named Budgets | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Max Named Budgets Exceeded) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"key1\":100, \"key2\":200}}",
        result: {
            valid: false,
            errors: ["exceeded max number of named budgets per source registration: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Empty String) Named Budgets | Valid",
        flags: {
            "max_named_budgets_per_source_registration": 1
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"\":100}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Max Length Per Budget Name Exceeded ) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abcd\":500}}",
        result: {
            valid: false,
            errors: ["'abcd' exceeded max length per budget name: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Null Budget) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abc\":null}}",
        result: {
            valid: false,
            errors: ["aggregatable budget value must be a number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non Number Budget) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abc\":\"100\"}}",
        result: {
            valid: false,
            errors: ["aggregatable budget value must be a number: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Negative Budget) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abc\":-300}}",
        result: {
            valid: false,
            errors: ["aggregatable budget value must be greater than or equal to 0: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Budget Over Max Capacity) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3,
            "max_sum_of_aggregate_values_per_source": 20
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abc\":21}}",
        result: {
            valid: false,
            errors: ["aggregatable budget value exceeds the max sum of aggregate values per source: `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non int64 Budget) Named Budgets | Invalid",
        flags: {
            "max_named_budgets_per_source_registration": 1,
            "max_length_per_budget_name": 3
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"named_budgets\":{\"abc\":15.58}}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `named_budgets`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Max Event Level Reports | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"max_event_level_reports\":\"5.0\"}",
        result: {
            valid: false,
            errors: ["must be numeric: `max_event_level_reports`"],
            warnings: []
        }
    },
    {
        name: "(Decimal) Max Event Level Reports | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"max_event_level_reports\":5.1}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `max_event_level_reports`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit Exceeded) Max Event Level Reports | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"max_event_level_reports\":-1}",
        result: {
            valid: false,
            errors: ["must be in the range of 0-20: `max_event_level_reports`"],
            warnings: []
        }
    },
    {
        name: "(Upper Limit Exceeded) Max Event Level Reports | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"max_event_level_reports\":21}",
        result: {
            valid: false,
            errors: ["must be in the range of 0-20: `max_event_level_reports`"],
            warnings: []
        }
    },
    {
        name: "(Integer) Max Event Level Reports | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"max_event_level_reports\":0}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "Event Report Window & Event Report Windows Both Present | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"2000\", \"event_report_windows\":{\"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ['Only one field must be present: `event_report_window or event_report_windows`'],
            warnings: []
        }
    }, 
    {
        name: "(Non-Object) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":true}",
        result: {
            valid: false,
            errors: ["must be an object or able to cast to an object: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Non-Object String) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":\"[]\"}",
        result: {
            valid: false,
            errors: ["must be an object or able to cast to an object: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric `start_time`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\":\"10\", \"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ["'start_time' must be numeric: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Decimal `start_time`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\":10.1, \"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ["'start_time' must be an int64 (must match /^-?[0-9]+$/): `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Lower Limit `start_time`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\":-1, \"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ["'start_time' must be in range of 0-2592000: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Default Upper Limit `start_time`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 2592001, \"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ["'start_time' must be in range of 0-2592000: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Explicit Upper Limit `start_time`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"86400\", \"event_report_windows\":{\"start_time\":86401, \"end_times\":[3600]}}",
        result: {
            valid: false,
            errors: ["'start_time' must be in range of 0-86400: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Missing `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10}}",
        result: {
            valid: false,
            errors: ["'end_times' key is required: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":3600}}",
        result: {
            valid: false,
            errors: ["'end_times' must be an array: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric Elements `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[3600, 3601, false]}}",
        result: {
            valid: false,
            errors: ["'end_times' array elements must be numeric: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Decimals Elements `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[3600, 3601, 3602.1]}}",
        result: {
            valid: false,
            errors: ["`end_time` must be an array of int64: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Exceed Array Size Limit `end_times`) Event Report Windows | Invalid",
        flags: {
            "flex_api_max_event_report_windows": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[3600, 3601, 3602]}}",
        result: {
            valid: false,
            errors: ["'end_times' array size must be in range of 1-2: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Single Item List - Negative Element `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[-1]}}",
        result: {
            valid: false,
            errors: ["'end_times' last element must be 0 or greater: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Negative Last Element `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[10, -1]}}",
        result: {
            valid: false,
            errors: ["'end_times' last element must be 0 or greater: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Negative First Element `end_times`) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 10, \"end_times\":[-1, 3600]}}",
        result: {
            valid: false,
            errors: ["'end_times' first element must be 0 or greater: `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(`end_times` First Element (Default Value) is Equal to Start Time) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3600, \"end_times\":[10, 3700]}}",
        result: {
            valid: false,
            errors: ["'end_times' first element must be greater than 'start_time': `event_report_windows`"],
            warnings: ["'end_times' first element is below the minimum allowed value. The value will be set to 'minimum event report window in seconds`: 3600: `event_report_windows`"]
        }
    },
    {
        name: "(`end_times` First Element (Explicit Value) is Equal to Start Time) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3600, \"end_times\":[3600, 3700]}}",
        result: {
            valid: false,
            errors: ["'end_times' first element must be greater than 'start_time': `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(`end_times` First Element (Default Value) is Less Than Start Time) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3601, \"end_times\":[10, 3700]}}",
        result: {
            valid: false,
            errors: ["'end_times' first element must be greater than 'start_time': `event_report_windows`"],
            warnings: ["'end_times' first element is below the minimum allowed value. The value will be set to 'minimum event report window in seconds`: 3600: `event_report_windows`"]
        }
    },
    {
        name: "(`end_times` First Element (Explicit Value) is Less Than Start Time) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3601, \"end_times\":[3600, 3700]}}",
        result: {
            valid: false,
            errors: ["'end_times' first element must be greater than 'start_time': `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(`end_times` Not Ascending Order - First Element Lower Limit) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 0, \"end_times\":[0, 3599, 3601]}}",
        result: {
            valid: false,
            errors: ["'end_times' (3600,3599,3601) must be in strictly ascending order (index: 1): `event_report_windows`"],
            warnings: ["'end_times' first element is below the minimum allowed value. The value will be set to 'minimum event report window in seconds`: 3600: `event_report_windows`"]
        }
    },
    {
        name: "(`end_times` Not Ascending Order) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3600, \"end_times\":[3602, 3603, 3601]}}",
        result: {
            valid: false,
            errors: ["'end_times' (3602,3603,3601) must be in strictly ascending order (index: 2): `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(`end_times` Not Ascending Order - Duplicates) Event Report Windows | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 3601, \"end_times\":[3602, 3603, 3603]}}",
        result: {
            valid: false,
            errors: ["'end_times' (3602,3603,3603) must be in strictly ascending order (index: 2): `event_report_windows`"],
            warnings: []
        }
    },
    {
        name: "(Object) Event Report Windows | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"end_times\":[3600]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Object String) Event Report Windows | Valid",
        flags: {},
        json: '{"destination":"android-app://com.myapps", "event_report_windows":"{\\"end_times\\":[3600]}"}',
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(`end_times` Last Element Lower Limit) Expected Value - Event Report Windows | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 0, \"end_times\":[3599]}}",
        result: {
            valid: true,
            errors: [],
            warnings: ["'end_times' last element is below the minimum allowed value. The value will be set to the allowed minimum event report window value in seconds(3600): `event_report_windows`"]
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "event_report_windows": "{\"start_time\":0,\"end_times\":[3600000]}"
        }
    },
    {
        name: "(`end_times` Last Element Upper Limit (Explicit Expiry)) Expected Value - Event Report Windows | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":86400, \"event_report_windows\":{\"start_time\": 0, \"end_times\":[3600, 86401]}}",
        result: {
            valid: true,
            errors: [],
            warnings: ["'end_times' last element exceeds the maximum allow value. The value will be set to 'expiry in seconds': 86400: `event_report_windows`"]
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "expiry":"86400000",
            "aggregatable_report_window":"86400000",
            "event_report_windows": "{\"start_time\":0,\"end_times\":[3600000,86400000]}"
        }
    },
    {
        name: "(`end_times` Last Element Upper Limit (Default Expiry)) Expected Value - Event Report Windows | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 0, \"end_times\":[3600, 2592001]}}",
        result: {
            valid: true,
            errors: [],
            warnings: ["'end_times' last element exceeds the maximum allow value. The value will be set to 'expiry in seconds': 2592000: `event_report_windows`"]
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "event_report_windows": "{\"start_time\":0,\"end_times\":[3600000,2592000000]}"
        }
    },
    {
        name: "(`end_times` First Element Lower Limit) Expected Value - Event Report Windows | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_windows\":{\"start_time\": 0, \"end_times\":[0, 3601]}}",
        result: {
            valid: true,
            errors: [],
            warnings: ["'end_times' first element is below the minimum allowed value. The value will be set to 'minimum event report window in seconds`: 3600: `event_report_windows`"]
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "event_report_windows": "{\"start_time\":0,\"end_times\":[3600000,3601000]}"
        }
    },
    {
        name: "Trigger Data & Trigger Specs Both Present | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2], \"trigger_specs\":[1,2]}",
        result: {
            valid: false,
            errors: ['Only one field must be present: `trigger_data or trigger_specs`'],
            warnings: []
        }
    },
    {
        name: "(Disabled) Trigger Data | Invalid",
        flags: {
            "feature-enable-v1-source-trigger-data": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":\"invalid\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":true}",
        result: {
            valid: false,
            errors: ["must be an array: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,3,false]}",
        result: {
            valid: false,
            errors: ["array elements must be numeric: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Decimal) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,3,4.1]}",
        result: {
            valid: false,
            errors: ["must be an array of int64: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,-3]}",
        result: {
            valid: false,
            errors: ["must be an array of uint64: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Duplicates) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,3,3]}",
        result: {
            valid: false,
            errors: ["duplicate array elements are not allowed (index: 3): `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Max Trigger Data Value) Trigger Data | Invalid",
        flags: {
            "max_trigger_data_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,3,11,4]}",
        result: {
            valid: false,
            errors: ["array element (index: 3) exceeds the max allowed trigger data value of 10: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Exceeds Max Trigger Data Length) Trigger Data | Invalid",
        flags: {
            "flex_api_max_trigger_data_cardinality": 4
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[1,2,3,11,4]}",
        result: {
            valid: false,
            errors: ["array length exceeds the max trigger data cardinality of 4: `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Contiguous) Trigger Data | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[0,1,3]}",
        result: {
            valid: false,
            errors: ["array must be contiguous (array element (index: 2) cannot exceed: 2 (array length - 1): `trigger_data`"],
            warnings: []
        }
    },
    {
        name: "(EXACT Matching Trigger Data Ignores Non-Contiguous) Trigger Data | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"exact\", \"trigger_data\":[0,1,3]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Matching Trigger Data Disabled. Ignore Non-Contiguous) Trigger Data | Valid",
        flags: {
            "feature-trigger-data-matching": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data\":[0,1,3]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":[]}",
        result: {
            valid: false,
            errors: ["must be an object: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Budget) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\"}}",
        result: {
            valid: false,
            errors: ["`budget` key must be present: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric Budget) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": \"65536\"}}",
        result: {
            valid: false,
            errors: ["`budget` must be numeric: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Decimal Budget) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65535.5}}",
        result: {
            valid: false,
            errors: ["'budget' must be an int64 (must match /^-?[0-9]+$/): `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Lower Limit Budget) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 0}}",
        result: {
            valid: false,
            errors: ["`budget` must be range of 1-65536: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Upper Limit Budget) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65537}}",
        result: {
            valid: false,
            errors: ["`budget` must be range of 1-65536: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"budget\": 65536}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": null, \"budget\": 65536}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"\", \"budget\": 65536}}",
        result: {
            valid: false,
            errors: ["key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": 123, \"budget\": 65536}}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Key Piece) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"1x3\", \"budget\": 65536}}",
        result: {
            valid: false,
            errors: ["key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":123}}",
        result: {
            valid: false,
            errors: ["`aggregation_coordinator_origin` must be a string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"\"}}",
        result: {
            valid: false,
            errors: ["`aggregation_coordinator_origin` must be non-empty: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Origin) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"abc\"}}",
        result: {
            valid: false,
            errors: ["'aggregation_coordinator_origin' invalid URL format: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Debug Data) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":{}}}",
        result: {
            valid: false,
            errors: ["`debug_data` must be an array: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Object Debug Data Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": [\"source-noised\"]},[]]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 1 must be an object: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": [\"source-noised\"]},{}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 1 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": null, \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Types Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Types Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 65536, \"types\": null}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Missing Debug Data Value Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Null Debug Data Value Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": null, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["`debug_data` element at index: 0 requires keys (`key_piece`, `types`, `value`) to be present and non-null: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": 1, \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"\", \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must not be null or empty string: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Invalid Debug Data Key Piece Element) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"1x3\", \"value\": 65536, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["'debug_data' element at index: 0 key piece must start with '0x' or '0X': `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Value Exceeds Lower Limit) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 65536, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 0, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `value` must be in range 1-65536: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Value Exceeds Upper Limit) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 301, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `value` must be in range 1-300: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Debug Data Types) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": {}}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `types` must be an array: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Empty Debug Data Types) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": []}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 `types` must non-empty: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Duplicates In Within The Same Objects) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\", \"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 0 duplicate report types are not allow within the same debug data object or across multiple debug data objects: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Duplicates In Across Different Objects) Aggregatable Debug Report | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-noised\"]}]}}",
        result: {
            valid: false,
            errors: ["debug_data element at index: 1 duplicate report types are not allow within the same debug data object or across multiple debug data objects: `aggregatable_debug_reporting`"],
            warnings: []
        }
    },
    {
        name: "(Debug Data Types - Case Insensitive) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\"]}]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "aggregatable_debug_reporting":"{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}],\"budget\":300}"
        }
    },
    {
        name: "(Debug Data Types - Ignore Unknown Report Type) Aggregatable Debug Report | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\",\"fake-report-type\"]}]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "destination": ["android-app://com.myapps"],
            "aggregatable_debug_reporting":"{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}],\"budget\":300}"
        }
    },
    {
        name: "Null Top-Level Key Values | Valid",
        flags: {},
        json: "{"
                + "\"destination\":\"android-app://com.myapps\","
                + "\"web_destination\":null,"
                + "\"source_event_id\":null,"
                + "\"expiry\":null,"
                + "\"event_report_window\":null,"
                + "\"aggregatable_report_window\":null,"
                + "\"priority\":null,"
                + "\"debug_key\":null,"
                + "\"debug_reporting\":null,"
                + "\"debug_ad_id\":null,"
                + "\"debug_join_key\":null,"
                + "\"install_attribution_window\":null,"
                + "\"post_install_exclusivity_window\":null,"
                + "\"reinstall_reattribution_window\":null,"
                + "\"filter_data\":null,"
                + "\"aggregation_keys\":null,"
                + "\"attribution_scopes\":null,"
                + "\"trigger_data_matching\":null,"
                + "\"coarse_event_report_destinations\":null,"
                + "\"shared_debug_key\":null,"
                + "\"shared_aggregation_keys\":null,"
                + "\"shared_filter_data_keys\":null,"
                + "\"drop_source_if_installed\":null,"
                + "\"named_budgets\":null,"
                + "\"max_event_level_reports\":null,"
                + "\"event_report_windows\": null,"
                + "\"trigger_data\": null,"
                + "\"aggregatable_debug_reporting\": null"
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
        json: "{\"DESTINATION\":\"com.myapps\"}",
        result: {
            valid: false,
            errors: ["invalid URL format: `destination`"],
            warnings: []
        }
    },
    {
        name: "Expected Value - Default | Valid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "source_event_id": 0,
            "debug_key": null,
            "destination": ["android-app://com.myapps"],
            "expiry": 2592000000,
            "event_report_window": null,
            "aggregatable_report_window": 2592000000,
            "priority": 0,
            "install_attribution_window": 2592000000,
            "post_install_exclusivity_window": 0,
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
            "aggregatable_debug_reporting":null
        }
    },
    {
        name: "Expected Value - Populated Fields (With Event Report Window)",
        flags: {},
        json: "{" +
                    "\"source_event_id\": \"1234\"," +
                    "\"debug_key\": \"1000\"," +
                    "\"destination\": \"android-app://com.myapps\"," +
                    "\"expiry\": \"86401\"," +
                    "\"event_report_window\": \"3601\"," +
                    "\"aggregatable_report_window\": \"3601\"," +
                    "\"priority\": \"1\"," +
                    "\"install_attribution_window\": \"86401\"," +
                    "\"post_install_exclusivity_window\": \"1\"," +
                    "\"reinstall_reattribution_window\": \"1\"," +
                    "\"filter_data\": {\"filter_1\":[\"A\", \"B\"]}," +
                    "\"web_destination\": \"https://web-destination.com\"," +
                    "\"aggregation_keys\": {\"abc\":\"0x22\"}," +
                    "\"shared_aggregation_keys\": [\"1\", 2]," +
                    "\"debug_reporting\": \"true\"," +
                    "\"debug_join_key\": 100," +
                    "\"debug_ad_id\": 200," +
                    "\"coarse_event_report_destinations\": \"TRUE\"," +
                    "\"shared_debug_key\": \"300\"," +
                    "\"shared_filter_data_keys\": [\"3\", 4]," +
                    "\"drop_source_if_installed\": \"true\"," +
                    "\"trigger_data_matching\": \"EXACT\"," +
                    "\"attribution_scopes\": {\"values\":[\"a\", \"b\", \"c\"], \"limit\":4, \"max_event_states\":3}," +
                    "\"named_budgets\": {\"budget1\": 1000}," +
                    "\"max_event_level_reports\": 11," +
                    "\"trigger_data\": [0,1,2]," +
                    "\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\"]}]}" +
                "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "source_event_id": 1234,
            "debug_key": 1000,
            "destination": ["android-app://com.myapps"],
            "expiry": 86401000,
            "event_report_window": 3601000,
            "aggregatable_report_window": 3601000,
            "priority": 1,
            "install_attribution_window": 86401000,
            "post_install_exclusivity_window": 1000,
            "reinstall_reattribution_window": 1000,
            "filter_data": "{\"filter_1\":[\"A\",\"B\"]}",
            "web_destination": ["https://web-destination.com"],
            "aggregation_keys": "{\"abc\":\"0x22\"}",
            "shared_aggregation_keys": "[\"1\",2]",
            "debug_reporting": true,
            "debug_join_key": "100",
            "debug_ad_id": "200",
            "coarse_event_report_destinations": true,
            "shared_debug_key": 300,
            "shared_filter_data_keys": "[\"3\",4]",
            "drop_source_if_installed": true,
            "trigger_data_matching": "EXACT",
            "attribution_scopes": ["a", "b", "c"],
            "attribution_scope_limit": 4,
            "max_event_states": 3,
            "named_budgets": "{\"budget1\":1000}",
            "max_event_level_reports": 11,
            "event_report_windows": null,
            "trigger_data": [0,1,2],
            "aggregatable_debug_reporting": "{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}],\"budget\":300}"
        }
    },
    {
        name: "Expected Value - Populated Fields (With Event Report Windows)",
        flags: {},
        json: "{" +
                    "\"source_event_id\": \"1234\"," +
                    "\"debug_key\": \"1000\"," +
                    "\"destination\": \"android-app://com.myapps\"," +
                    "\"expiry\": \"86401\"," +
                    "\"aggregatable_report_window\": \"3601\"," +
                    "\"priority\": \"1\"," +
                    "\"install_attribution_window\": \"86401\"," +
                    "\"post_install_exclusivity_window\": \"1\"," +
                    "\"reinstall_reattribution_window\": \"1\"," +
                    "\"filter_data\": {\"filter_1\":[\"A\", \"B\"]}," +
                    "\"web_destination\": \"https://web-destination.com\"," +
                    "\"aggregation_keys\": {\"abc\":\"0x22\"}," +
                    "\"shared_aggregation_keys\": [\"1\", 2]," +
                    "\"debug_reporting\": \"true\"," +
                    "\"debug_join_key\": 100," +
                    "\"debug_ad_id\": 200," +
                    "\"coarse_event_report_destinations\": \"TRUE\"," +
                    "\"shared_debug_key\": \"300\"," +
                    "\"shared_filter_data_keys\": [\"3\", 4]," +
                    "\"drop_source_if_installed\": \"true\"," +
                    "\"trigger_data_matching\": \"EXACT\"," +
                    "\"attribution_scopes\": {\"values\":[\"a\", \"b\", \"c\"], \"limit\":4, \"max_event_states\":3}," +
                    "\"named_budgets\": {\"budget1\": 1000}," +
                    "\"max_event_level_reports\": 12," +
                    "\"event_report_windows\": {\"start_time\": 1, \"end_times\":[3601]}," +
                    "\"trigger_data\": [0,1,2]," +
                    "\"aggregatable_debug_reporting\":{\"key_piece\": \"0x3\", \"budget\": 300, \"aggregation_coordinator_origin\":\"https://cloud.coordination.test\", \"debug_data\":[{\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"source-NOISED\"]}, {\"key_piece\": \"0x3\", \"value\": 300, \"types\": [\"trigger-Event-Low-priority\"]}]}" +
                "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        },
        expected_value: {
            "source_event_id": 1234,
            "debug_key": 1000,
            "destination": ["android-app://com.myapps"],
            "expiry": 86401000,
            "event_report_window": null,
            "aggregatable_report_window": 3601000,
            "priority": 1,
            "install_attribution_window": 86401000,
            "post_install_exclusivity_window": 1000,
            "reinstall_reattribution_window": 1000,
            "filter_data": "{\"filter_1\":[\"A\",\"B\"]}",
            "web_destination": ["https://web-destination.com"],
            "aggregation_keys": "{\"abc\":\"0x22\"}",
            "shared_aggregation_keys": "[\"1\",2]",
            "debug_reporting": true,
            "debug_join_key": "100",
            "debug_ad_id": "200",
            "coarse_event_report_destinations": true,
            "shared_debug_key": 300,
            "shared_filter_data_keys": "[\"3\",4]",
            "drop_source_if_installed": true,
            "trigger_data_matching": "EXACT",
            "attribution_scopes": ["a", "b", "c"],
            "attribution_scope_limit": 4,
            "max_event_states": 3,
            "named_budgets": "{\"budget1\":1000}",
            "max_event_level_reports": 12,
            "event_report_windows": "{\"start_time\":1000,\"end_times\":[3601000]}",
            "trigger_data": [0,1,2],
            "aggregatable_debug_reporting": "{\"key_piece\":\"0x3\",\"aggregation_coordinator_origin\":\"https://cloud.coordination.test/\",\"debug_data\":[{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"source-noised\"]},{\"key_piece\":\"0x3\",\"value\":300,\"types\":[\"trigger-event-low-priority\"]}],\"budget\":300}"
        }
    }
]

module.exports = {
    sourceTestCases
};