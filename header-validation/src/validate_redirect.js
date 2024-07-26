const {
    State,
    optional,
    string,
    array,
    isValidLocation,
    isValidAttributionReportingRedirect,
    formatKeys,
  } = require('./base');
  
  function validateRedirect(redirect, flagValues) {
    try {
      redirectJSON = JSON.parse(redirect);
      redirectJSON = formatKeys(redirectJSON);
    } catch (err) {
      return {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []};
    }
  
    const state = new State();
    let jsonSpec = {};
  
    jsonSpec["location"] = optional(string(isValidLocation));
    jsonSpec["attribution-reporting-redirect"] = optional(array(isValidAttributionReportingRedirect, flagValues));
    jsonSpec["attribution-reporting-redirect-config"] = optional(string());
  
    state.validate(redirectJSON, jsonSpec, flagValues);
    return state.result();
  }
  
  module.exports = {
    validateRedirect
  };