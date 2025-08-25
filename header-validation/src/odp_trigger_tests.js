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

const odpTriggerTestCases = [
    {
        name: "(Missing Required Field) Service | Invalid",
        flags: {},
        json: "{\"certDigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: false,
            errors: ["must be present and non-null: `service`"],
            warnings: []
        }
    },
    {
        name: "(Null) Service | Invalid",
        flags: {},
        json: "{\"service\":null, \"certDigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: false,
            errors: ["must be present and non-null: `service`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Service | Invalid",
        flags: {},
        json: "{\"service\":1, \"certDigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: false,
            errors: ["must be a string: `service`"],
            warnings: []
        }
    },
    {
        name: "(Missing Forward Slash) Service | Invalid",
        flags: {},
        json: "{\"service\":\"name\", \"certDigest\":\"abc\", \"data\":\"123\"}",
        result: {
            valid: false,
            errors: ["Invalid format. '/' must be present and not the last character: `service`"],
            warnings: []
        }
    },
    {
        name: "(Last Character Forward Slash) Service | Invalid",
        flags: {},
        json: "{\"service\":\"name/\", \"certDigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: false,
            errors: ["Invalid format. '/' must be present and not the last character: `service`"],
            warnings: []
        }
    },
    {
        name: "(Class Name First Character Dot) Service | Valid",
        flags: {},
        json: "{\"service\":\"package/.class\", \"certDigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: true,
            errors: [],
            warnings: [],
        },
        expected_value: {
            "service": "package/package.class",
            "certDigest": "abc",
            "data": "[100,101,102]"
        }
    },
    {
        name: "(Non-String) certDigest | Invalid",
        flags: {},
        json: "{\"service\":\"package/class\", \"certDigest\":true, \"data\":\"def\"}",
        result: {
            valid: false,
            errors: ["must be a string: `certDigest`"],
            warnings: []
        }
    },
    {
        name: "(Non-String) Data | Invalid",
        flags: {},
        json: "{\"service\":\"package/class\", \"certDigest\":\"abc\", \"data\":123}",
        result: {
            valid: false,
            errors: ["must be a string: `data`"],
            warnings: []
        }
    },
    {
        name: "Expected Values | Valid",
        flags: {},
        json: "{\"service\":\"package/class\", \"CERTdigest\":\"abc\", \"data\":\"def\"}",
        result: {
            valid: true,
            errors: [],
            warnings: [],
        },
        expected_value: {
            "service": "package/class",
            "certDigest": "abc",
            "data": "[100,101,102]"
        }
    }
]

module.exports = {
    odpTriggerTestCases
};