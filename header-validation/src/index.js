const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');

// Initialize page elements
const validationForm = document.getElementById('validation-form');
const inputTextbox = document.getElementById('input-text');
const headerOptions = validationForm.elements.namedItem('header');
const copyButton = document.getElementById('linkify');
const errorList = document.getElementById('errors');
const warningList = document.getElementById('warnings');
const noteList = document.getElementById('notes');
const successDiv = document.getElementById('success');

// Add listeners
validationForm.addEventListener('input', validate);
copyButton.addEventListener('click', copyLink);


// Define functions
async function copyLink() {
  const url = new URL(location.toString());
  url.search = '';
  url.searchParams.set('header', headerOptions.value);
  url.searchParams.set('json', inputTextbox.value);
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
  // Fetch Flag Values
  let flagValues = {};
  flagValues['feature-lookback-window-filter'] = true;
  flagValues['feature-trigger-data-matching'] = true;
  flagValues['feature-coarse-event-report-destination'] = true;
  flagValues['feature-shared-source-debug-key'] = true;
  flagValues['feature-xna'] = true;
  flagValues['feature-shared-filter-data-keys'] = true;
  flagValues['feature-preinstall-check'] = true;
  flagValues['feature-attribution-scopes'] = false;
  flagValues['feature-aggregation-coordinator-origin'] = true;
  flagValues['feature-source-registration-time-optional-for-agg-reports'] = false;
  flagValues['feature-trigger-context-id'] = false
  flagValues['feature-enable-update-trigger-header-limit'] = false;
  flagValues['feature-enable-reinstall-reattribution'] = true;
  flagValues['max_attribution_filters'] = 50;
  flagValues['max_bytes_per_attribution_filter_string'] = 25;
  flagValues['max_values_per_attribution_filter'] = 50;
  flagValues["max_distinct_web_destinations_in_source_registration"] = 3;
  flagValues["max_web_destination_hostname_character_length"] = 253;
  flagValues["max_web_destination_hostname_parts"] = 127;
  flagValues["min_web_destination_hostname_part_character_length"] = 1;
  flagValues["max_web_destination_hostname_part_character_length"] = 63;
  flagValues["max_aggregate_keys_per_source_registration"] = 50;
  flagValues["max_bytes_per_attribution_aggregate_key_id"] = 25;
  flagValues["min_bytes_per_aggregate_value"] = 3;
  flagValues["max_bytes_per_aggregate_value"] = 34;
  flagValues["max_attribution_scopes_per_source"] = 20;
  flagValues["max_attribution_scope_string_length"] = 50;
  flagValues["max_report_states_per_source_registration"] = (1n << 32n) - 1n;
  flagValues["max_trigger_context_id_string_length"] = 64;
  flagValues["max_bucket_threshold"] = (1n << 32n) - 1n;
  flagValues["max_filter_maps_per_filter_set"] = 20;
  flagValues["max_aggregate_keys_per_trigger_registration"] = 50;
  flagValues["max_sum_of_aggregate_values_per_source"] = 65536;
  flagValues["max_aggregate_deduplication_keys_per_registration"] = 50;
  flagValues['header_type'] = headerOptions.value;

  // Validate input
  let result;
  if (headerOptions.value === 'source') {
    result = validateSource(inputTextbox.value, flagValues);
  } else if (headerOptions.value === 'trigger') {
    result = validateTrigger(inputTextbox.value, flagValues);
  }

  // Show results
  successDiv.textContent = (result.errors.length === 0 && result.warnings.length === 0) ? 'The header is valid.' : '';
  populateUIList(errorList, result.errors);
  populateUIList(warningList, result.warnings);
  populateUIList(noteList, []);
}

// Load initial state of page
validate();