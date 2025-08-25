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
    optional,
    string,
    array,
    isValidLocation,
    isValidAttributionReportingRedirect,
    isValidAttributionReportingRedirectConfig,
    formatKeys,
  } = require('./base');

  function initializeExpectedValues() {
    let expectedValues = {
      "location": null,
      "attribution-reporting-redirect": null,
      "attribution-reporting-redirect-config": null
    };
    return expectedValues;
  }
  
  function validateRedirect(redirect, metadata) {
    try {
      redirectJSON = JSON.parse(redirect);
      redirectJSON = formatKeys(redirectJSON);
    } catch (err) {
      return {
        result: {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []},
        expected_value: {}
      };
    }
  
    const state = new State();
    let jsonSpec = {};
  
    metadata.expected_value = initializeExpectedValues(redirectJSON);
    jsonSpec["attribution-reporting-redirect-config"] = optional(string(isValidAttributionReportingRedirectConfig, metadata));
    jsonSpec["attribution-reporting-redirect"] = optional(array(isValidAttributionReportingRedirect, metadata));
    jsonSpec["location"] = optional(string(isValidLocation, metadata));
  
    state.validate(redirectJSON, jsonSpec);
    return {
      result: state.result(),
      expected_value: metadata.expected_value
    };
  }
  
  module.exports = {
    validateRedirect
  };