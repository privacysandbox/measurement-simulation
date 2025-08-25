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
    formatKeys,
    isValidOdpService,
    isValidOdpCertDigest,
    isValidOdpData
  } = require('./base');
  
  function initializeExpectedValues() {
    let expectedValues = {
      "service": null,
      "certDigest": null,
      "data": null
    };
    return expectedValues;
  }
  
  function validateOdpTrigger(odpTrigger, metadata) {
    try {
      odpTriggerJSON = JSON.parse(odpTrigger);
      odpTriggerJSON = formatKeys(odpTriggerJSON);
    } catch (err) {
      return {
        result: {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []},
        expected_value: {}
      };
    }
  
    const state = new State();
    let jsonSpec = {};
    metadata.expected_value = initializeExpectedValues();

    if (metadata.flags['feature-enable-odp-web-trigger-registration']) {
      // service check
      if (!('service' in odpTriggerJSON) || odpTriggerJSON['service'] === null) {
        return {
          result: {errors: [{
          path: ["service"],
          msg: "must be present and non-null",
          formattedError: "must be present and non-null: `service`"}], warnings: []},
          expected_value: {}
        };
      }
      jsonSpec["service"] = optional(string(isValidOdpService, metadata));
      jsonSpec["certDigest"] = optional(string(isValidOdpCertDigest, metadata));
      jsonSpec["data"] = optional(string(isValidOdpData, metadata));
    }
  
    state.validate(odpTriggerJSON, jsonSpec);
    return {
      result: state.result(),
      expected_value: metadata.expected_value
    };
  }
  
  module.exports = {
    validateOdpTrigger
  };