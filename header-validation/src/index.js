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

const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');
const {validateRedirect} = require('./validate_redirect');
const {flagValues} = require('./parameters');
const { validateOdpTrigger } = require('./validate_odp_trigger');

// Initialize page elements
const validationForm = document.getElementById('validation-form');
const inputTextbox = document.getElementById('input-text');
const outputTextbox = document.getElementById('effective');
const headerOptions = validationForm.elements.namedItem('header');
const copyButton = document.getElementById('linkify');
const errorList = document.getElementById('errors');
const warningList = document.getElementById('warnings');
const noteList = document.getElementById('notes');
const successDiv = document.getElementById('success');
const sourceTypeOptionsContainer = document.getElementById('source-type-options');
const sourceTypeOptions = validationForm.elements.namedItem('source-type');

// Add listeners
validationForm.addEventListener('input', validate);
copyButton.addEventListener('click', copyLink);

// Define functions
function loadUrlParameters() {
  let params = new URLSearchParams(location.search);
  let paramsJSON = params.get('json');
  let paramsHeader = params.get('header');
  let paramsSourceType = params.get('source-type');

  if (paramsJSON) {
    inputTextbox.value = paramsJSON;
  }
  if (paramsHeader) {
    headerOptions.value = paramsHeader;
  }
  if (paramsSourceType) {
    sourceTypeOptions.value = paramsSourceType;
  }
}

async function copyLink() {
  const url = new URL(location.toString());
  url.search = '';
  url.searchParams.set('header', headerOptions.value);
  url.searchParams.set('json', inputTextbox.value);
  if (url.searchParams.get('header') === 'source') {
    url.searchParams.set('source-type', sourceTypeOptions.value);
  }
  await navigator.clipboard.writeText(url.toString());
}

function populateUIList(element, items) {
  element.replaceChildren([]);
  items.forEach(function (item) {
    let listItem = document.createElement('li');
    if (typeof item === "string") {
      listItem.textContent = item;
    } else {
      listItem.textContent = item.msg + ': ' + item.path;
    }
    element.appendChild(listItem);
  })
}

function validate() {
  // Enable/Disable source type option based on header type
  sourceTypeOptionsContainer.disabled = (headerOptions.value != 'source'); 

  // Validate input
  let metadata = {
    flags: flagValues,
    header_options: {
      header_type: headerOptions.value,
      source_type: null
    },
    expected_value: null
  };
  let result;
  let output;
  if (headerOptions.value === 'source') {
    metadata.header_options.source_type = sourceTypeOptions.value;
    output = validateSource(inputTextbox.value, metadata);
    result = output.result;
  } else if (headerOptions.value === 'trigger') {
    output = validateTrigger(inputTextbox.value, metadata);
    result = output.result;
  } else if (headerOptions.value === 'redirect') {
    output = validateRedirect(inputTextbox.value, metadata);
    result = output.result;
  } else if (headerOptions.value === 'odp-trigger') {
    output = validateOdpTrigger(inputTextbox.value, metadata);
    result = output.result;
  }

  // Show results
  successDiv.textContent = (result.errors.length === 0) ? 'The header is valid.' : '';
  populateUIList(errorList, result.errors);
  populateUIList(warningList, result.warnings);
  populateUIList(noteList, []);
  if (result.errors.length === 0) {
    outputTextbox.textContent = JSON.stringify(output.expected_value,  null, 2);
  } else {
    outputTextbox.textContent = "";
  }
}

// Load initial state of page
loadUrlParameters();
validate();