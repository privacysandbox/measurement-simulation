const {validateSource} = require('./validate_source')
const {validateTrigger} = require('./validate_trigger')

// Initialize page elements
const validationForm = document.getElementById('validation-form')
const inputTextbox = document.getElementById('input-text')
const headerOptions = validationForm.elements.namedItem('header')
const sourceTypeOptionsContainer = document.getElementById('source-type-options')
const sourceTypeOptions = validationForm.elements.namedItem('source-type')
const registrationOptions = validationForm.elements.namedItem('registration-type')
const featureAraParsingAlignmentV1 = validationForm.elements.namedItem('feature-ara-parsing-alignment-v1')
const featureLookbackWindowFilter = validationForm.elements.namedItem('feature-lookback-window-filter')
const featureTriggerDataMatching = validationForm.elements.namedItem('feature-trigger-data-matching')
const copyButton = document.getElementById('linkify')
const errorList = document.getElementById('errors')
const warningList = document.getElementById('warnings')
const noteList = document.getElementById('notes')
const successDiv = document.getElementById('success')

// Add listeners
validationForm.addEventListener('input', validate)
copyButton.addEventListener('click', copyLink)


// Define functions
async function copyLink() {
  const url = new URL(location.toString())
  url.search = ''
  url.searchParams.set('header', headerOptions.value)
  url.searchParams.set('json', inputTextbox.value)
  if (url.searchParams.get('header') === 'source') {
    url.searchParams.set('source-type', sourceTypeOptions.value)
  }
  url.searchParams.set('feature-ara-parsing-alignment-v1', featureAraParsingAlignmentV1.checked.toString())
  url.searchParams.set('feature-lookback-window-filter', featureLookbackWindowFilter.checked.toString())
  url.searchParams.set('feature-trigger-data-matching', featureTriggerDataMatching.checked.toString())
  await navigator.clipboard.writeText(url.toString())
}

function populateUIList(element, items) {
  element.replaceChildren([])
  items.forEach(function (item) {
    let listItem = document.createElement('li')
    if (typeof item === "string") {
      listItem.textContent = item
    } else {
      listItem.textContent = item.msg + ': ' + item.path
    }
    element.appendChild(listItem)
  })
}

function validate() {
  // Enable/Disable source type option based on header type
  sourceTypeOptionsContainer.disabled = (headerOptions.value != 'source') 

  // Fetch Flag Values
  let flagValues = {}
  flagValues['feature-ara-parsing-alignment-v1'] = featureAraParsingAlignmentV1.checked
  flagValues['feature-lookback-window-filter'] = featureLookbackWindowFilter.checked
  flagValues['feature-trigger-data-matching'] = featureTriggerDataMatching.checked
  flagValues['max_attribution_filters'] = 50
  flagValues['max_bytes_per_attribution_filter_string'] = 25
  flagValues['max_values_per_attribution_filter'] = 50;

  // Validate input
  let result;
  if (sourceTypeOptionsContainer.disabled) {
    result = validateTrigger(inputTextbox.value, registrationOptions.value, flagValues)
  } else {
    result = validateSource(inputTextbox.value, registrationOptions.value, flagValues)
  }

  // Show results
  successDiv.textContent = (result.errors.length === 0 && result.warnings.length === 0) ? 'The header is valid.' : ''
  populateUIList(errorList, result.errors)
  populateUIList(warningList, result.warnings)
  populateUIList(noteList, [])
}

// Load initial state of page
validate()