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
            errors: ["invalid app URI format: `destination`"],
            warnings: []
        }
    },
    {
        name: "App Destination Invalid Host | Invalid",
        flags: {},
        json: "{\"destination\":\"http://com.myapps\"}",
        result: {
            valid: false,
            errors: ["app URI host/scheme is invalid: `destination`"],
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
            "max_web_destination_hostname_character_length": 253,
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
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
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
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
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
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
            "min_web_destination_hostname_part_character_length": 1,
            "max_web_destination_hostname_part_character_length": 63
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
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
            "min_web_destination_hostname_part_character_length": 1,
            "max_web_destination_hostname_part_character_length": 63
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
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
            "min_web_destination_hostname_part_character_length": 1,
            "max_web_destination_hostname_part_character_length": 63
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
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
            "min_web_destination_hostname_part_character_length": 1,
            "max_web_destination_hostname_part_character_length": 63
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
            "max_distinct_web_destinations_in_source_registration": 1,
            "max_web_destination_hostname_character_length": 253,
            "max_web_destination_hostname_parts": 127,
            "min_web_destination_hostname_part_character_length": 1,
            "max_web_destination_hostname_part_character_length": 63
        },
        json: "{\"web_destination\":[\"https://ab.cde.1com\"]}",
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
        name: "(Non-String) Debug Key | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":1000}",
        result: {
            valid: false,
            errors: ["must be a string: `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Debug Key | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"-1000\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Debug Key | Invalid",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": true,
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
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
            "max_bytes_per_attribution_filter_string": 25,
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
        flags: {
            "feature-trigger-data-matching": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"MODuLus\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String - Unknown Value) Trigger Data Matching | Invalid",
        flags: {
            "feature-trigger-data-matching": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":\"invalid\"}",
        result: {
            valid: false,
            errors: ["value must be 'exact' or 'modulus' (case-insensitive): `trigger_data_matching`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Trigger Data Matching | Invalid",
        flags: {
            "feature-trigger-data-matching": true
        },
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
        flags: {
            "feature-coarse-event-report-destination": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Coarse Event Report Destinations | Valid",
        flags: {
            "feature-coarse-event-report-destination": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":\"truE\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Coarse Event Report Destinations | Invalid",
        flags: {
            "feature-coarse-event-report-destination": true
        },
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
        flags: {
            "feature-shared-source-debug-key": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Shared Debug Key | Valid",
        flags: {
            "feature-shared-source-debug-key": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Shared Debug Key | Invalid",
        flags: {
            "feature-shared-source-debug-key": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_key\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["must be an uint64 (must match /^[0-9]+$/): `shared_debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Shared Debug Key | Invalid",
        flags: {
            "feature-shared-source-debug-key": true
        },
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
        flags: {
            "feature-preinstall-check": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":true}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Drop Source If Installed | Valid",
        flags: {
            "feature-preinstall-check": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":\"truE\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Boolean) Drop Source If Installed | Invalid",
        flags: {
            "feature-preinstall-check": true
        },
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
        flags: {
            "feature-xna": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_aggregation_keys\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Shared Aggregation Keys | Invalid",
        flags: {
            "feature-xna": true
        },
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
        flags: {
            "feature-shared-filter-data-keys": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_filter_data_keys\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Shared Filter Data Keys | Invalid",
        flags: {
            "feature-shared-filter-data-keys": true
        },
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
            errors: ["key piece value must not be null or empty string: `aggregation_keys`"],
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
            errors: ["key piece value must not be null or empty string: `aggregation_keys`"],
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
            errors: ["key piece value must start with '0x' or '0X': `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Min Bytes Size) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0X\"}}",
        result: {
            valid: false,
            errors: ["key piece value string size must be in the byte range (3 bytes - 34 bytes): `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Max Bytes Size) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0Xa1B2C3d4E5f6\"}}",
        result: {
            valid: false,
            errors: ["key piece value string size must be in the byte range (3 bytes - 34 bytes): `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non Hexademical) Aggregation Value | Invalid",
        flags: {
            "max_aggregate_keys_per_source_registration": 1,
            "max_bytes_per_attribution_aggregate_key_id": 3,
            "min_bytes_per_aggregate_value": 3,
            "max_bytes_per_aggregate_value": 10
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregation_keys\":{\"abc\":\"0x22g3c\"}}",
        result: {
            valid: false,
            errors: ["key piece values must be hexadecimal: `aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Disabled) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": false,
            "header_type": "source"
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
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Missing Limit - Non-Empty Array) Attribution Scopes | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\"]}",
        result: {
            valid: false,
            errors: ["attribution scopes array must be empty if attribution scope limit is not present: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Missing Limit - Max Event States Present) Attribution Scopes | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"max_event_states\":\"2\"}",
        result: {
            valid: false,
            errors: ["max event states must not be present if attribution scope limit is not present: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Missing Limit - Empty Array) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Limit Present - Empty Array) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"5\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Limit Present - Exceeds Limit) Attribution Scopes | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\", \"b\", \"c\"], \"attribution_scope_limit\":\"2\"}",
        result: {
            valid: false,
            errors: ["attribution scopes array size exceeds the provided attribution_scope_limit: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Array) Attribution Scopes | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\", 1, \"b\"], \"attribution_scope_limit\":\"5\"}",
        result: {
            valid: false,
            errors: ["must be an array of strings: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max Number of Scopes Per Source) Attribution Scopes | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "max_attribution_scopes_per_source": 2,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"a\", \"b\", \"c\"], \"attribution_scope_limit\":\"5\"}",
        result: {
            valid: false,
            errors: ["exceeded max number of scopes per source: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Exceeded Max String Length Per Scope) Attribution Scopes | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "max_attribution_scopes_per_source": 2,
            "max_attribution_scope_string_length": 5,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[\"123456\"], \"attribution_scope_limit\":\"5\"}",
        result: {
            valid: false,
            errors: ["exceeded max scope string length: `attribution_scopes`"],
            warnings: []
        }
    },
    {
        name: "(Zero) Attribution Scope Limit | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"0\"}",
        result: {
            valid: false,
            errors: ["must be greater than 0: `attribution_scope_limit`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Attribution Scope Limit | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":2}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Attribution Scope Limit | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"2\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Zero) Max Event States | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"2\", \"max_event_states\":\"0\"}",
        result: {
            valid: false,
            errors: ["must be greater than 0: `max_event_states`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Max Event States | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"2\", \"max_event_states\":3}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Max Event States | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"2\", \"max_event_states\":\"3\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Exceeds Max Report States Per Source Registration) Max Event States | Invalid",
        flags: {
            "feature-attribution-scopes": true,
            "max_report_states_per_source_registration": ((1n << 2n) - 1n),
            "header_type": "source"
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"attribution_scopes\":[], \"attribution_scope_limit\":\"2\", \"max_event_states\":\"4\"}",
        result: {
            valid: false,
            errors: ["exceeds max report states per source registration: `max_event_states`"],
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
        flags: {
            "feature-enable-reinstall-reattribution": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Reinstall Reattribution Window | Valid",
        flags: {
            "feature-enable-reinstall-reattribution": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":987654}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Negative) Reinstall Reattribution Window | Valid",
        flags: {
            "feature-enable-reinstall-reattribution": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"-987654\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Reinstall Reattribution Window | Invalid",
        flags: {
            "feature-enable-reinstall-reattribution": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"reinstall_reattribution_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["must be an int64 (must match /^-?[0-9]+$/): `reinstall_reattribution_window`"],
            warnings: []
        }
    },
    {
        name: "Null Top-Level Key Values | Valid",
        flags: {
            "feature-attribution-scopes": true,
            "feature-trigger-data-matching": true,
            "feature-coarse-event-report-destination": true,
            "feature-shared-source-debug-key": true,
            "feature-xna": true,
            "feature-shared-filter-data-keys": true,
            "feature-preinstall-check": true,
            "feature-enable-update-trigger-header-limit": false,
            "feature-lookback-window-filter": true
        },
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
                + "\"attribution_scope_limit\":null,"
                + "\"max_event_states\":null,"
                + "\"trigger_data_matching\":null,"
                + "\"coarse_event_report_destinations\":null,"
                + "\"shared_debug_key\":null,"
                + "\"shared_aggregation_keys\":null,"
                + "\"shared_filter_data_keys\":null,"
                + "\"drop_source_if_installed\":null"
            + "}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    }
]

module.exports = {
    sourceTestCases
};