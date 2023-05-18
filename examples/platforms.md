# Web and App Conversions

Source events, with Web and App based attribution destinations, can be registered on both Web and App platforms.

For example, here is how the following combinations can be simulated:

- **Web to Web**: an Attribution Source has a web destination and a Trigger that occurs on the web platform is attributed to that Source.
    - The Attribution Source's `web_destination` would be set to a URL.
      - See the `web_attribution_source.json` file in `data` for an example.
    - The Trigger's `registrant` is a URL signifying where the conversion took place.
    - The Trigger's `attribution_destination` would be set to the same URL as the Source's `web_destination`.
    - The Trigger's `destination_type` would be set to `WEB`.
      - See the `web_trigger.json` file in `data` for an example.
- **App to Web**: an Attribution Source has a web destination and a Trigger that occurs on an app is attributed to that Source.
    - The Attribution Source's `web_destination` would be set to a URL.
      - See the `web_attribution_source.json` file in `data` for an example.
    - The Trigger's `registrant` is an app package signifying where the conversion took place.
    - The Trigger's `attribution_destination` would be set to the same URL as the Source's `web_destination`.
    - The Trigger's `destination_type` would be set to `WEB`.
      - See the `app_trigger.json` file in `data` for an example.
- **Web to App**: an Attribution Source has an app destination and a Trigger that occurs on the web platform is attributed to that Source.
    - The Attribution Source's `destination` would be set to an app package.
      - See the `app_attribution_source.json` file in `data` for an example.
    - The Trigger's `registrant` is a URL signifying where the conversion took place.
    - The Trigger's `attribution_destination` would be set to the same app package as the Source's `destination`.
    - The Trigger's `destination_type` would be set to `APP`.
      - See the `web_trigger.json` file in `data` for an example.
- **App to App**: an Attribution Source has an app destination and a Trigger that occurs on an app is attributed to that Source.
    - The Attribution Source's `destination` would be set to an app package.
      - See the `app_attribution_source.json` file in `data` for an example.
    - The Trigger's `registrant` is an app package signifying where the conversion took place.
    - The Trigger's `attribution_destination` would be set to the same app package as the Source's `destination`.
    - The Trigger's `destination_type` would be set to `APP`.
      - See the `app_trigger.json` file in `data` for an example.

# Simulating API Choice

In addition to simulating different combinations of Attribution Sources and Triggers
for App and Web, the Measurement Simulation Library also supports simulating processing on
different platforms, i.e. Web and OS. This is meant to demonstrate how attribution on the
two platforms can occur independently, consuming separate privacy budgets and rate limits.

Each source and trigger event log has the `api_choice` field which can take on the values `WEB` or `OS`.
Simply change the log's `api_choice` to dictate which platform to process the log on.

By default, any log without an `api_choice` will be processed on the `OS` platform.

The `WEB` platform **only** supports Web to Web attributions, so any logs that have
app destinations, but an `api_choice` of `WEB` will be ignored. 