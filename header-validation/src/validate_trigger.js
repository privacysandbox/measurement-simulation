const {
  State,
  boolean,
  optBoolean,
  optBooleanFallback,
  uint64,
  optUint64,
  int64,
  isValidUri,
  optInt64,
  string,
  optString,
  optStringFallback,
  object,
  objectWithFlags,
  array,
  required,
  optional,
  exclude,
  getDataType,
} = require('./base');

function validateTrigger(trigger, registrationType, flagValues) {
  try {
    triggerJSON = JSON.parse(trigger)
  } catch (err) {
    return {errors: [err instanceof Error ? err.toString() : 'unknown error'], warnings: []};
  }

  const state = new State();
  let jsonSpec = {};

  jsonSpec["destination"] = optional(isValidUri);

  state.validate(triggerJSON, jsonSpec, flagValues);
  return state.result();
}

module.exports = {
  validateTrigger
};