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

const { readFileSync } = require('fs');
const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');
const {validateRedirect} = require('./validate_redirect');
const commandLineUsage = require('command-line-usage');
const commandLineArgs = require('command-line-args');
const {flagValues} = require('./parameters');

const HEADER_TYPES = ['source','trigger','redirect'];
const SOURCE_TYPES = ['event','navigation'];

// Helper functions
function parseHeaderType(str) {
    if (HEADER_TYPES.includes(str.toLowerCase())) {
        return str.toLowerCase();
    }
    throw new Error('unknown header type');
}

function parseSourceType(str) {
  if (SOURCE_TYPES.includes(str.toLowerCase())) {
    return str.toLowerCase();
  }
  throw new Error('unknown source type');
}

// Define and parse command line arguments
const optionDefinition = [
  { name: 'input', type: String, description: 'Input to parse.'},
  { name: 'file', type: String, typeLabel: 'file path', description: 'File containing input to parse. File must be a .json to parse successfully. Read from stdin if \'-\' is provided.'},
  { name: 'headerType', type: parseHeaderType, typeLabel: '(source|trigger|redirect)', description: 'The type of header that is being validated.' },
  { name: 'sourceType', type: parseSourceType, typeLabel: '(event|navigation)', description: 'The type of source header that is being validated.' },
  { name: 'silent', type: Boolean, description: 'Suppress output.' },
  { name: 'failOnWarnings', type: Boolean, description: 'Validation will fail if there are parsing warnings or parsing errors.' },
  { name: 'help', alias: 'h', type: Boolean, description: 'Prints this usage guide.' }
];
const helpSections = [
    {
        header: 'Attribution Reporting Header Validator',
        content: [
          "Parses an Attribution-Reporting-Register-Source or Attribution-Reporting-Register-Trigger header using Rubidium's vendor-specific values.",
          'Prints errors, warnings, notes, and the effective value (i.e. with defaults populated) to stdout in JSON format.',
          'Exits with 1 if validation fails, 0 otherwise.',
        ].join('\n\n'),
    },
    {
      header: 'Options',
      optionList: optionDefinition
    },
];
const options = commandLineArgs(optionDefinition);

// Validate command line arguments
if (options.help !== undefined) {
    console.log(commandLineUsage(helpSections));
    return;
}
if (!options.file && !options.input) {
    throw new TypeError('exactly one of --file or --input must be present');
}
if (options.file) {
    // read from stdin if '-' is provided for this option
    options.input = readFileSync(options.file === '-' ? 0 : options.file, {
        encoding: 'utf8',
    });
}
if (!options.headerType) {
    throw new TypeError('--headerType must be present');
}
if (options.headerType === "source" && !options.sourceType) {
    throw new TypeError('--sourceType must be present if header type is source');
}

// Run validation logic
let output;
let metadata = {
    flags: flagValues,
    header_options: {
        header_type: options.headerType,
        source_type: null
    },
    expected_value: null
};
if (options.headerType === 'source') {
    metadata.header_options.source_type = options.sourceType;
    output = validateSource(options.input, metadata);
} else if (options.headerType === 'trigger') {
    output = validateTrigger(options.input, metadata);
} else if (options.headerType === 'redirect') {
    output = validateRedirect(options.input, metadata);
}

// Show results
let formattedOutput = {
    "errors": output.result.errors,
    "warnings": output.result.warnings
};
if (output.result.errors.length > 0 || (options.failOnWarnings && output.result.warnings.length > 0)) {
    if (!options.silent) {
        console.log(JSON.stringify(formattedOutput,  null, 2));
    }
    process.exit(1);
} else {
    if (!options.silent) {
        formattedOutput["value"] = output.expected_value;
        console.log(JSON.stringify(formattedOutput,  null, 2));
    }
}
