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

const redirectTestCases = [
    {
        name: "(Non-String) Location | Invalid",
        flags: {},
        json: "{\"location\":1}",
        result: {
            valid: false,
            errors: ["must be a string: `location`"],
            warnings: []
        }
    },
    {
        name: "(Non-URL) Location | Invalid",
        flags: {},
        json: "{\"location\":\"abc\"}",
        result: {
            valid: false,
            errors: ["invalid URL format: `location`"],
            warnings: []
        }
    },
    {
        name: "Location | Valid",
        flags: {},
        json: "{\"location\":\"https://web-destination.test\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-Array) Attribution Reporting Redirect | Invalid",
        flags: {},
        json: "{\"attribution-reporting-redirect\":{}}",
        result: {
            valid: false,
            errors: ["must be an array: `attribution-reporting-redirect`"],
            warnings: []
        }
    },
    {
        name: "(Array of Non-Strings) Attribution Reporting Redirect | Invalid",
        flags: {
            "max_registration_redirects": 2
        },
        json: "{\"attribution-reporting-redirect\":[\"https://web-destination-1.test\", 2]}",
        result: {
            valid: false,
            errors: ["must be an array of strings: `attribution-reporting-redirect`"],
            warnings: []
        }
    },
    {
        name: "(Array of Non-Strings - Ignore Out-of-Range Elements) Attribution Reporting Redirect | Valid",
        flags: {
            "max_registration_redirects": 1
        },
        json: "{\"attribution-reporting-redirect\":[\"https://web-destination-1.test\", 2]}",
        result: {
            valid: true,
            errors: [],
            warnings: ["max allowed reporting redirects: 1, all other reporting redirects will be ignored: `attribution-reporting-redirect`"]
        }
    },
    {
        name: "(Non-URLs) Attribution Reporting Redirect | Invalid",
        flags: {
            "max_registration_redirects": 3
        },
        json: "{\"attribution-reporting-redirect\":[\"https://web-destination-1.test\", \"https://web-destination-1.test\", \"3\", \"4\"]}",
        result: {
            valid: false,
            errors: ["invalid URL format: `attribution-reporting-redirect`"],
            warnings: ["max allowed reporting redirects: 3, all other reporting redirects will be ignored: `attribution-reporting-redirect`"]
        }
    },
    {
        name: "Attribution Reporting Redirect | Valid",
        flags: {
            "max_registration_redirects": 2
        },
        json: "{\"attribution-reporting-redirect\":[\"https://web-destination-1.test\", \"https://web-destination-2.test\"]}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Non-String) Attribution Reporting Redirect Config | Invalid",
        flags: {},
        json: "{\"attribution-reporting-redirect-config\":1}",
        result: {
            valid: false,
            errors: ["must be a string: `attribution-reporting-redirect-config`"],
            warnings: []
        }
    },
    {
        name: "Attribution Reporting Redirect Config | Valid",
        flags: {},
        json: "{\"attribution-reporting-redirect-config\":\"redirect-302-to-well-known\"}",
        result: {
            valid: true,
            errors: [],
            warnings: []
        }
    },
    {
        name: "(Null) Top-Level Key Values | Valid",
        flags: {},
        json: "{"
                + "\"location\":null,"
                + "\"attribution-reporting-redirect\":null,"
                + "\"attribution-reporting-redirect-config\":null"
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
        json: "{"
                + "\"Location\":\"https://web-destination.test\","
                + "\"Attribution-Reporting-Redirect\":[\"invalid-url\"],"
                + "\"attribution-reporting-redirect-Config\":\"redirect-302-to-well-known\""
            + "}",
        result: {
            valid: false,
            errors: ["invalid URL format: `attribution-reporting-redirect`"],
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
            "location": null,
            "attribution-reporting-redirect": null,
            "attribution-reporting-redirect-config": null
        }
    },
    {
        name: "Expected Value - Redirect Headers",
        flags: {
            "max_registration_redirects": 2
        },
        json: "{"
                + "\"Location\":\"https://web-destination-1.test\","
                + "\"Attribution-Reporting-Redirect\":[\"https://web-destination-2.test\", \"https://web-destination-3.test\", 0],"
                + "\"Attribution-Reporting-Redirect-Config\":\"redirect-302-to-well-known\""
            + "}",
        result: {
            valid: true,
            errors: [],
            warnings: ["max allowed reporting redirects: 2, all other reporting redirects will be ignored: `attribution-reporting-redirect`"]
        },
        expected_value: {
            "location": [{
                uri: "https://web-destination-1.test/.well-known/attribution-reporting/register-redirect?302_url=https%3A%2F%2Fweb-destination-1.test",
                redirect_behavior: "LOCATION_TO_WELL_KNOWN"
            }],
            "attribution-reporting-redirect": [
                {
                    uri: "https://web-destination-2.test",
                    redirect_behavior: "AS_IS"
                },
                {
                    uri: "https://web-destination-3.test",
                    redirect_behavior: "AS_IS"
                }
            ],
            "attribution-reporting-redirect-config": "redirect-302-to-well-known"
        }
    }
]

module.exports = {
    redirectTestCases
};