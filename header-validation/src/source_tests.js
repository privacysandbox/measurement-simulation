const sourceTestCases = [
    {
        name: "App Destination Present | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `destination`"],
            warnings: []
        }
    },
    {
        name: "App Destination Missing Scheme | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"com.myapps\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "App Destination Invalid Host | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"http://com.myapps\"}",
        result: {
            valid: false,
            errors: ["app URI host is invalid: `destination`"],
            warnings: []
        }
    },
    {
        name: "Web Destination Present | Valid",
        type: "app",
        flags: {},
        json: "{\"web_destination\":\"https://web-destination.test\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "App and Web Destination Missing | Invalid",
        type: "app",
        flags: {},
        json: "{\"source_event_id\":\"1234\"}",
        result: {
            valid: false,
            errors: ["At least one field must be present: `destination or web_destination`"],
            warnings: []
        }
    },
    {
        name: "(String) Source Event ID + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"1234\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Source Event ID + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":1234}",
        result: {
            valid: false,
            errors: ["Must be a string: `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Source Event ID + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Source Event ID + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(String) Source Event ID | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"1234\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Source Event ID | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":1234}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Source Event ID | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Source Event ID | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Source Event ID | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"source_event_id\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `source_event_id`"],
            warnings: []
        }
    },
    {
        name: "(String) Expiry + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Expiry + v1 Alignment | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Expiry + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `expiry`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Expiry + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `expiry`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Expiry + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `expiry`"],
            warnings: []
        }
    },
    {
        name: "(String) Expiry | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Expiry | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Expiry | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `expiry`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Expiry | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"-1234\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Expiry | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"expiry\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `expiry`"],
            warnings: []
        }
    },
    {
        name: "(String) Event Report Window + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Event Report Window + v1 Alignment | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Event Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Event Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"-2000\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Event Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(String) Event Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Event Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Event Report Window | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Event Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"-2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Event Report Window | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"event_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `event_report_window`"],
            warnings: []
        }
    },
    {
        name: "(String) Aggregatable Report Window + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Aggregatable Report Window + v1 Alignment | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Aggregatable Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Aggregatable Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"-2000\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Aggregatable Report Window + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(String) Aggregatable Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Aggregatable Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":2000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Aggregatable Report Window | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Aggregatable Report Window | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"-2000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Aggregatable Report Window | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"aggregatable_report_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `aggregatable_report_window`"],
            warnings: []
        }
    },
    {
        name: "(String) Priority + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Priority + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":1}",
        result: {
            valid: false,
            errors: ["Must be a string: `priority`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Priority + v1 Alignment | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"-1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Priority + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `priority`"],
            warnings: []
        }
    },
    {
        name: "(String) Priority | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Priority | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":1}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Priority | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `priority`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Priority | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"-1\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Priority | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"priority\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `priority`"],
            warnings: []
        }
    },
    {
        name: "(Boolean) Debug Reporting | Valid",
        type: "app",
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
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_reporting\":99}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Debug Reporting | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_reporting\":null}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Key + v1 Alignment| Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Key + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":1000}",
        result: {
            valid: false,
            errors: ["Must be a string: `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Debug Key + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"-1000\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Debug Key + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(String) Debug Key | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"1000\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Debug Key | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":1000}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Debug Key | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Debug Key | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"-1000\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Debug Key | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_key\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `debug_key`"],
            warnings: []
        }
    },
    {
        name: "(String) Install Attribution Window | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":86300}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Install Attribution Window | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `install_attribution_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Install Attribution Window | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"install_attribution_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `install_attribution_window`"],
            warnings: []
        }
    },
    {
        name: "(String) Post Install Exclusivity Window | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":987654}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Post Install Exclusivity Window | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `post_install_exclusivity_window`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Post Install Exclusivity Window | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"post_install_exclusivity_window\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be an int64 (must match /^-?[0-9]+$/): `post_install_exclusivity_window`"],
            warnings: []
        }

    },
    {
        name: "(Object) Filter Data | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
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
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":[\"A\"]}",
        result: {
            valid: false,
            errors: ["Must be an object: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('source_type' Filter is Present) Filter Data + v1 Alignment | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"source_type\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["filter: source_type is not allowed: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('source_type' Filter is Present) Filter Data | Valid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"source_type\":[\"A\"]}}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Number of Filters Limit Exceeded) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
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
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"ABCDEFGHIJKLMNOPQRSTUVWXYZ\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["exceeded max bytes per attribution filter string: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Filter is Present) Filter Data + Lookback Window Filter | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"_lookback_window\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["filter: _lookback_window is not allowed: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "('_lookback_window' Filter is Present) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"_lookback_window\":[\"A\"]}}",
        result: {
            valid: false,
            errors: ["filter can not start with underscore: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter String Starts with Underscore) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\"], \"_filter_2\":[\"B\"]}}",
        result: {
            valid: false,
            errors: ["filter can not start with underscore: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-Array Filter Value) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":{\"name\": \"A\"}}}",
        result: {
            valid: false,
            errors: ["filter value must be an array: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Number of Values Per Filter Limit Exceeded) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", \"B\", \"C\"]}}",
        result: {
            valid: false,
            errors: ["exceeded max values per attribution filter: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Non-String Filter Value) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", 2]}}",
        result: {
            valid: false,
            errors: ["filter values must be strings: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(Filter Value String Byte Size Limit Exceeded) Filter Data | Invalid",
        type: "app",
        flags: {
            "feature-ara-parsing-alignment-v1": true,
            "max_attribution_filters": 2,
            "max_bytes_per_attribution_filter_string": 25,
            "feature-lookback-window-filter": false,
            "max_values_per_attribution_filter": 2
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"filter_data\":{\"filter_1\":[\"A\", \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\"]}}",
        result: {
            valid: false,
            errors: ["exceeded max bytes per attribution filter value string: `filter_data`"],
            warnings: []
        }
    },
    {
        name: "(String) Debug Ad ID | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_ad_id\":11756}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Debug Ad ID | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_ad_id\":null}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String) Debug Join Key | Valid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_join_key\":66784}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Debug Join Key | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"debug_join_key\":null}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(String - Enum Value) Trigger Data Matching | Valid",
        type: "app",
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
        type: "app",
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
        type: "app",
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
        name: "(Null) Trigger Data Matching | Invalid",
        type: "app",
        flags: {
            "feature-trigger-data-matching": true
        },
        json: "{\"destination\":\"android-app://com.myapps\", \"trigger_data_matching\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `trigger_data_matching`"],
            warnings: []
        }
    },
    {
        name: "(Boolean) Coarse Event Report Destinations | Valid",
        type: "app",
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
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":99}",
        result: {
            valid: false,
            errors: ["Must be a boolean: `coarse_event_report_destinations`"],
            warnings: []
        }
    },
    {
        name: "(Null) Coarse Event Report Destinations | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"coarse_event_report_destinations\":null}",
        result: {
            valid: false,
            errors: ["Must be a boolean: `coarse_event_report_destinations`"],
            warnings: []
        }
    },
    {
        name: "(String) Shared Debug Keys | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_keys\":\"100\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Shared Debug Keys | Valid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_keys\":100}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Shared Debug Keys | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_keys\":null}",
        result: {
            valid: false,
            errors: ["Must be a string or able to cast to string: `shared_debug_keys`"],
            warnings: []
        }
    },
    {
        name: "(Negative) Shared Debug Keys | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_keys\":\"-1234\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `shared_debug_keys`"],
            warnings: []
        }
    },
    {
        name: "(Non-Numeric) Shared Debug Keys | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_debug_keys\":\"true\"}",
        result: {
            valid: false,
            errors: ["Must be a uint64 (must match /^[0-9]+$/): `shared_debug_keys`"],
            warnings: []
        }
    },
    {
        name: "(Boolean) Drop Source If Installed | Valid",
        type: "app",
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
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":99}",
        result: {
            valid: false,
            errors: ["Must be a boolean: `drop_source_if_installed`"],
            warnings: []
        }
    },
    {
        name: "(Null) Drop Source If Installed | Invalid",
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"drop_source_if_installed\":null}",
        result: {
            valid: false,
            errors: ["Must be a boolean: `drop_source_if_installed`"],
            warnings: []
        }
    },
    {
        name: "(Array) Shared Aggregation Keys | Invalid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_aggregation_keys\":{}}",
        result: {
            valid: false,
            errors: ["Must be an array: `shared_aggregation_keys`"],
            warnings: []
        }
    },
    {
        name: "(Array) Shared Filter Data Keys | Invalid",
        type: "app",
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
        type: "app",
        flags: {},
        json: "{\"destination\":\"android-app://com.myapps\", \"shared_filter_data_keys\":{}}",
        result: {
            valid: false,
            errors: ["Must be an array: `shared_filter_data_keys`"],
            warnings: []
        }
    }
]

module.exports = {
    sourceTestCases
  };