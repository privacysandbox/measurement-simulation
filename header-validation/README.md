# Android Attribution Reporting Header Validation

The Android Attribution Reporting Header Validation tool validates the header responses related to Attribution Reporting API. It can be used to validate app based registrations or web registrations that are delegated to Android.

This document shows how to set up and run the tool.

Interactive form is deployed at: https://privacysandbox.github.io/measurement-simulation/validate-headers

*Note: If you want to validate web registrations for Chrome, please refer to the Chrome tool at: https://wicg.github.io/attribution-reporting-api/validate-headers*

Select the type of response you want to validate:
1. Attribution-Reporting-Register-Source, or
2. Attribution-Reporting-Register-Trigger

`Validation Result` section shows any errors or warnings if your data is invalid.

## Local Setup

Run the following command from the `header-validation` sub-directory:

```sh
npm install && npm run build && npm run test
```

Ensure that the above command runs successfully. Now, open `validate-headers.html` on your web browser and you should be able to validate your header spec locally.

## Feedback

If you have any feedback while using the Header Validation tool, please
[let us know][1].

[1]: https://issuetracker.google.com/issues/new?component=1116743&template=1629474
