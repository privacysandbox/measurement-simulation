const {validateSource} = require('./validate_source');
const {validateTrigger} = require('./validate_trigger');

// Initialize page elements
const validationForm = document.getElementById('validation-form');
const inputTextbox = document.getElementById('input-text');
const headerOptions = validationForm.elements.namedItem('header');
const featureLookbackWindowFilter = validationForm.elements.namedItem('feature-lookback-window-filter');
const featureTriggerDataMatching = validationForm.elements.namedItem('feature-trigger-data-matching');
const featureCoarseEventReportDestinations = validationForm.elements.namedItem('feature-coarse-event-report-destination');
const featureSharedSourceDebugKey = validationForm.elements.namedItem('feature-shared-source-debug-key');
const featureXNA = validationForm.elements.namedItem('feature-xna');
const featureSharedFilterDataKeys = validationForm.elements.namedItem('feature-shared-filter-data-keys');
const featurePreinstallCheck = validationForm.elements.namedItem('feature-preinstall-check');
const featureAttributionScopes = validationForm.elements.namedItem('feature-attribution-scopes');
const featureAggregationCoordinatorOrigin = validationForm.elements.namedItem('feature-aggregation-coordinator-origin');
const featureSourceRegistrationTimeOptionalForAggReports = validationForm.elements.namedItem('feature-source-registration-time-optional-for-agg-reports');
const featureTriggerContextId = validationForm.elements.namedItem('feature-trigger-context-id');
const featureEnableUpdateTriggerHeaderLimit = validationForm.elements.namedItem('feature-enable-update-trigger-header-limit');
const featureEnableReinstallReattribution = validationForm.elements.namedItem('feature-enable-reinstall-reattribution');
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
  url.searchParams.set('feature-lookback-window-filter', featureLookbackWindowFilter.checked.toString());
  url.searchParams.set('feature-trigger-data-matching', featureTriggerDataMatching.checked.toString());
  url.searchParams.set('feature-coarse-event-report-destination', featureCoarseEventReportDestinations.checked.toString());
  url.searchParams.set('feature-shared-source-debug-key', featureSharedSourceDebugKey.checked.toString());
  url.searchParams.set('feature-xna', featureXNA.checked.toString());
  url.searchParams.set('feature-shared-filter-data-keys', featureSharedFilterDataKeys.checked.toString());
  url.searchParams.set('feature-preinstall-check', featurePreinstallCheck.checked.toString());
  url.searchParams.set('feature-attribution-scopes', featureAttributionScopes.checked.toString());
  url.searchParams.set('feature-aggregation-coordinator-origin', featureAggregationCoordinatorOrigin.checked.toString());
  url.searchParams.set('feature-source-registration-time-optional-for-agg-reports', featureSourceRegistrationTimeOptionalForAggReports.checked.toString());
  url.searchParams.set('feature-trigger-context-id', featureTriggerContextId.checked.toString());
  url.searchParams.set('feature-enable-update-trigger-header-limit', featureEnableUpdateTriggerHeaderLimit.checked.toString());
  url.searchParams.set('feature-enable-reinstall-reattribution', featureEnableReinstallReattribution.checked.toString());
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
  flagValues['feature-lookback-window-filter'] = featureLookbackWindowFilter.checked;
  flagValues['feature-trigger-data-matching'] = featureTriggerDataMatching.checked;
  flagValues['feature-coarse-event-report-destination'] = featureCoarseEventReportDestinations.checked;
  flagValues['feature-shared-source-debug-key'] = featureSharedSourceDebugKey.checked;
  flagValues['feature-xna'] = featureXNA.checked;
  flagValues['feature-shared-filter-data-keys'] = featureSharedFilterDataKeys.checked;
  flagValues['feature-preinstall-check'] = featurePreinstallCheck.checked;
  flagValues['feature-attribution-scopes'] = featureAttributionScopes.checked;
  flagValues['feature-aggregation-coordinator-origin'] = featureAggregationCoordinatorOrigin.checked;
  flagValues['feature-source-registration-time-optional-for-agg-reports'] = featureSourceRegistrationTimeOptionalForAggReports.checked;
  flagValues['feature-trigger-context-id'] = featureTriggerContextId.checked;
  flagValues['feature-enable-update-trigger-header-limit'] = featureEnableUpdateTriggerHeaderLimit.checked;
  flagValues['feature-enable-reinstall-reattribution'] = featureEnableReinstallReattribution.checked;
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
  flagValues["max_filter_maps_per_filter_set"] = 5;
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