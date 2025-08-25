# Android Attribution Reporting Header Validation

The Android Attribution Reporting Header Validation tool validates the header responses related to Attribution Reporting API. It can be used to validate app based registrations or web registrations that are delegated to Android.

This document shows how to set up and run the tool.

Interactive form is deployed at: https://privacysandbox.github.io/measurement-simulation/validate-headers

*Note: If you want to validate web registrations for Chrome, please refer to the Chrome tool at: https://wicg.github.io/attribution-reporting-api/validate-headers*

Select the type of response you want to validate:
1. Attribution-Reporting-Register-Source,
2. Attribution-Reporting-Register-Trigger
3. Attribution-Reporting-Redirect
4. Attribution-Reporting-Redirect-Config
5. Location

`Validation Result` section shows any errors or warnings if your data is invalid.

## Local Setup

Run the following command from the `header-validation` sub-directory:

```
npm install && npm run build && npm run test
```

Ensure that the above command runs successfully.

Option 1 (Web Browser): To run the header validation tool UI, open `validate-headers.html` in your web browser.

Option 2 (CLI): To run the header validation tool in your terminal, run the command:
```
npm run header -- <flags>
```
Examples:

1.
```
npm run header -- --input {\"destination\":\"android-app://example.com\"} --headerType source --sourceType navigation
```
This will print out:
```
> header-validation@1.0.0 header
> node ./src/cmd.js --input {"destination":"android-app://example.com"} --headerType source --sourceType navigation

{
  "errors": [],
  "warnings": [],
  "value": {
    "source_event_id": 0,
    "debug_key": null,
    "destination": [
      "android-app://example.com"
    ],
    "expiry": 2592000000,
    "event_report_window": null,
    "aggregatable_report_window": 2592000000,
    "priority": 0,
    "install_attribution_window": 2592000000,
    "post_install_exclusivity_window": 0,
    "reinstall_reattribution_window": 0,
    "filter_data": null,
    "web_destination": null,
    "aggregation_keys": null,
    "shared_aggregation_keys": null,
    "debug_reporting": false,
    "debug_join_key": null,
    "debug_ad_id": null,
    "coarse_event_report_destinations": false,
    "shared_debug_key": null,
    "shared_filter_data_keys": null,
    "drop_source_if_installed": false,
    "trigger_data_matching": "MODULUS",
    "attribution_scopes": null,
    "attribution_scope_limit": null,
    "max_event_states": 3,
    "named_budgets": null,
    "max_event_level_reports": null,
    "event_report_windows": null,
    "trigger_data": null
  }
}
```
Exit code check (0 - Valid Header, 1 - Invalid Header):
```
echo $?
> 0
```
2.
`test.json` contents:
```
{
    "event_trigger_data": [{
        "trigger_data" : "1",
        "priority": "2",
        "value": 1,
        "deduplication_key": "13",
        "filters":[
            {
                "_lookback_window":"0",
                "filter_1":["A", "B"]
            },
            {
                "filter_2":["C", "D"]
            }
        ],
        "not_filters":[
            {
                "_lookback_window":1,
                "filter_1":["E", true]
            }
        ]
    }]
}
```
```
npm run header -- --file ./src/test.json  --headerType trigger --failOnWarnings
```
This will print out:
```
> header-validation@1.0.0 header
> node ./src/cmd.js --file ./src/test.json --headerType trigger --failOnWarnings

{
  "errors": [
    {
      "path": [
        "event_trigger_data"
      ],
      "msg": "'filter_1' filter values must be strings",
      "formattedError": "'filter_1' filter values must be strings: `event_trigger_data`"
    }
  ],
  "warnings": []
}
```
Exit code check (0 - Valid Header, 1 - Invalid Header):
```
echo $?
> 1
```
3.
```
npm run header -- --file - --headerType trigger --silent
```
This will print out:
```
> header-validation@1.0.0 header
> node ./src/cmd.js --file - --headerType trigger

{"debug_reporting":"true"} // provide input then "Enter" then press "Ctrl+D"
```
This will not print any output since the `silent` option was added.

Exit code check (0 - Valid Header, 1 - Invalid Header):
```
echo $?
> 0
```
To see all the available command line options run:
```
npm run header -- -h or npm run header -- --help
```

This will print out:
```
Attribution Reporting Header Validator

Parses an Attribution-Reporting-Register-Source or Attribution-Reporting-Register-Trigger header using Rubidium's vendor-specific values. Prints errors, warnings, notes, and the effective value (i.e. with defaults populated) to stdout in JSON format. Exits with 1 if validation fails, 0 otherwise.

Options

  --input          | string                    | Input to parse.
  --file           | file path                 | File containing input to parse. File must be a .json to parse successfully. Read from stdin if \'-\' is provided.
  --headerType     | (source|trigger|redirect) | The type of header that is being validated.
  --sourceType     | (event|navigation)        | The type of source header that is being validated.
  --silent         |                           | Suppress output.
  --failOnWarnings |                           | Validation will fail if there are parsing warnings or parsing errors.
  -h, --help       |                           | Prints this usage guide.
```

## Feedback

If you have any feedback while using the Header Validation tool, please
[let us know][1].

[1]: https://issuetracker.google.com/issues/new?component=1116743&template=1629474
