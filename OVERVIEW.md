# Test data with the Measurement Simulation Library

As you implement the Attribution Reporting API in your tech stack, it is
important to get an idea of how your attribution data will look after the
integration. With the Measurement Simulation Library, you can work with a
simplified mock environment, allowing you to measure the impact of the
Attribution Reporting API early so you don't need to wait until your integration
is finished to begin testing.

The Measurement Simulation Library allows you to understand the impact of your
integration by presenting historical data as if it were collected by the
Attribution Reporting API. This allows you to compare your historical conversion
numbers with Measurement Simulation Library results to see how reporting
accuracy might change. You can also use the Measurement Simulation Library to
experiment with different aggregation key structures and batching strategies,
and train your optimization models on Measurement Simulation Library reports to
compare projected performance with models based on current data.

To adequately test the Attribution Reporting API's privacy and security
guarantees, ad tech partners can run event-level and aggregatable reports on
simulated measurement data to evaluate how Attribution Reporting APIs will
present measurement data, privacy effectiveness, and how well that data fits
into reports and models.

The Measurement Simulation Library allows you to test the following:

- Aggregation keys
- Aggregation windows
- Reporting windows for event reports
- Conversion metadata in event reports
- Conversion rate limits, click-level and user-level restrictions
- Noise levels and thresholds

As you conduct your Measurement Simulation Library testing, keep in mind key
concepts such as how seasonality may impact your aggregation key and batching
strategies, how you will translate different noising results to your customers
and how much customization you will allow. Experimenting with the variability of
the Attribution Reporting API will allow you to deliver the best possible
results when it is time to roll out your integration.

**Note**: Use the Simulation Library with [Noise Lab][1]. Noise Lab
provides a good introduction to summary reports and the impact of noising. The
Simulation Library offers a more advanced set of configurations and allows you
to understand the impact of noising as well as other API functionality on
historic data.

**Note**: All attribution logic in the Simulation Library is based on the
Android Attribution Reporting API, including all [attribution paths][2].

## How it works

The Measurement Simulation Library takes an ad tech's historical dataset as a
single offline batch and runs a simulation on the local machine. The Measurement
Simulation Library divides data based on the `user_id` field, which represents
different users and simulates the client side behavior for each user in
parallel.

The Measurement Simulation Library generates event reports for each user and
writes these reports in an output directory. Aggregatable reports from each user
are also generated and then combined into daily batches and sent to the local
instance of the aggregation service data plane, which in turn generates summary
reports.

**Batching strategy**: The Measurement Simulation Library provides a default
daily batching strategy for each advertiser. However, ad techs can configure
their batching strategy based on what works best for their use case. For
example, some ad techs may want to batch weekly or monthly, or based on source
registration time. To introduce a new batching strategy, see the code in
`java/com/google/measurement/adtech/BatchAggregatableReports.java`.

**Testing features**: Measurement Simulation Library data is processed as
plaintext. There is no enforced privacy budget when using the Measurement
Simulation Library, so ad techs can run it multiple times on the same dataset.
Ad techs may tweak the privacy parameters for both event and aggregate APIs.

## Setup

The Measurement Simulation Library is a lightweight, standalone library that can
be installed on your local machine. It does not depend on the Android and/or
Chrome platform, has no database dependencies for the storage and processing of
data, and does not encrypt or decrypt data. It runs a local instance of the
[Aggregation Service][3] on your machine and does not require an AWS
account.

Visit the [Measurement Simulation Library README][4] on GitHub for
Measurement Simulation Library installation instructions.

### Data structure and processing

The Measurement Simulation Library uses [Apache Beam][5] to read the
input data (source and trigger data along with metadata to perform aggregation),
processes the data grouped by "user id" and calls an attribution reporting
simulation algorithm for each user ID in parallel. Once the event and
aggregatable reports are created for each user, the Beam library combines the
aggregatable reports and groups them into daily batches, and these batches are
sent to the local aggregation service.

The Measurement Simulation Library supports JSON for input data and event report
output. Aggregate reports support both JSON and Avro formats. All event reports
for a user ID are written in a single JSON file, as
`<output_directory>/<platform>/<user_id>/event_reports.json`.

### Security and privacy considerations

As you design your solution's security and privacy features, consider the
following points:

- The Measurement Simulation Library uses the same noising mechanisms as the
  Attribution Reporting API.
- All data is processed as plaintext for testing purposes.
- The Measurement Simulation Library does not depend on Android and/or Chrome
  and cloud provider enclaves, which means that you can run it end to end on
  your own infrastructure without requiring any underlying data to be sent
  outside of your organization.
- This tool provides flexibility for ad techs to tweak privacy parameters to
  understand how the privacy-preserving functionality affects output reports.

### Input parameters

**apiChoice input parameter**

We recently introduced functionality that allows developers to specify which
client Attribution Reporting API they would like to use. This resembles the
behavior of the real API, where you can specify whether to use Android or Chrome
Attribution for each event. This will be particularly important for ad techs
looking to test app-to-web, web-to-app attribution, or use the Android
Attribution API for web-to-web (in a mobile or app browser).

For each record used in the Simulation Library, developers will populate the
field `api_choice` with either `WEB` or `OS`, depending on which API they want
to use to process and batch the input data accordingly.

**Source and trigger data input parameters**

The sample input source and trigger data shown below demonstrates the types of
information that is consumed by the Measurement Simulation Library. Any
additional data is left unchanged and not processed. The library expects ad
techs to provide both source and trigger info and aggregation-related metadata
in the same input files.

Example source data:

```
{
  "user_id":"U1",
  "source_event_id":1,
  "source_type":"EVENT",
  "publisher":"https://www.example1.com/s1",
  "web_destination":"https://www.example2.com/d1",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642218050000,
  "expiry":2592000,
  "priority":100,
  "registrant":"https://www.example3.com/e1",
  "dedup_keys":[],
  "install_attribution_window":86400,
  "post_install_exclusivity_window":172800,
  "filter_data":{
    "type":["1", "2", "3", "4"],
    "ctid":["id"]
  },
  "aggregation_keys":[
    {
      "myId":"0xFFFFFFFFFFFFFF"
    }
  ],
  "api_choice": "OS"
}
{
  "user_id":"U1",
  "source_event_id":2,
  "source_type":"EVENT",
  "publisher":"https://www.example1.com/s2",
  "web_destination":"https://www.example2.com/d2",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642235602000,
  "expiry":2592000,
  "priority":100,
  "registrant":"https://www.example3.com/e1",
  "dedup_keys":[],
  "install_attribution_window":86400,
  "post_install_exclusivity_window":172800,
  "filter_data":{
    "type":["7", "8", "9", "10"],
    "ctid":["id"]
  },
  "aggregation_keys":[
    {
      "campaignCounts":"0x159"
    },
    {
      "geoValue":"0x5"
    }
  ],
  "api_choice": "OS"
}
{
  "user_id":"U2",
  "source_event_id":3,
  "source_type":"NAVIGATION",
  "publisher":"https://www.example1.com/s3",
  "web_destination":"https://www.example2.com/d3",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642249235000,
  "expiry":2592000,
  "priority":100,
  "registrant":"https://www.example3.com/e1",
  "dedup_keys":[],
  "install_attribution_window":86400,
  "post_install_exclusivity_window":172800,
  "filter_data":{
    "type":["1", "2", "3", "4"],
    "ctid":["id"]
  },
  "aggregation_keys":[
    {
      "myId3":"0xFFFFFFFFFFFFFFFF"
    }
  ],
  "api_choice": "OS"
}
```

Example trigger data:

```
{
  "user_id":"U1",
  "attribution_destination":"https://www.example2.com/d1",
  "destination_type":"WEB",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642271444000,
  "event_trigger_data":[
    {
      "trigger_data":1000,
      "priority":100,
      "deduplication_key":1
    }
  ],
  "registrant":"http://example1.com/4",
  "aggregatable_trigger_data":[
    {
      "key_piece":"0x400",
      "source_keys":["campaignCounts"],
      "filters":{
        "Key_1":["value_1", "value_2"],
        "Key_2":["value_1", "value_2"]
      }
    }
  ],
  "aggregatable_values":{
    "campaignCounts":32768,
    "geoValue":1664
  },
  "filters":{
     "key_1":["value_1", "value_2"],
     "key_2":["value_1", "value_2"]
  },
  "api_choice": "OS"
}
{
  "user_id":"U1",
  "attribution_destination":"https://www.example2.com/d3",
  "destination_type":"WEB",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642273950000,
  "event_trigger_data":[
    {
      "trigger_data":1000,
      "priority":100,
      "deduplication_key":1
    }
  ],
  "registrant":"http://example1.com/4",
  "aggregatable_trigger_data":[
    {
      "key_piece":"0x400",
      "source_keys":[
        "campaignCounts"
      ],
      "not_filters":{
        "Key_1x":["value_1", "value_2"],
        "Key_2x":["value_1", "value_2"]
      }
    }
  ],
  "aggregatable_values":{
    "campaignCounts":32768,
    "geoValue":1664
  },
  "filters":{
     "key_1":["value_1", "value_2"],
     "key_2":["value_1", "value_2"]
  },
  "api_choice": "OS"
}
{
  "user_id":"U2",
  "attribution_destination":"https://www.example2.com/d3",
  "destination_type":"WEB",
  "enrollment_id":"https://www.example3.com/r1",
  "timestamp":1642288930000,
  "event_trigger_data":[
    {
      "trigger_data":1000,
      "priority":100,
      "deduplication_key":1
    }
  ],
  "registrant":"http://example1.com/4",
  "aggregatable_trigger_data":[
    {
      "key_piece":"0x400",
      "source_keys":[
        "campaignCounts"
      ],
      "filters":{
        "Key_1":["value_1", "value_2"],
        "Key_2":["value_1", "value_2"]
      }
    }
  ],
  "aggregatable_values":{
    "campaignCounts":32768,
    "geoValue":1664
  },
  "filters":{
     "key_1":["value_1", "value_2"],
     "key_2":["value_1", "value_2"]
  },
  "api_choice": "OS"
}
```

_Web and App destinations_

The example input data above used web destinations, signified by the
`web_destination` field in the Source input, the `destination_type` of `WEB` in
the Trigger input, and by the web URL, `https://www.example2.com/d1`. However,
the Measurement Simulation Library can also process app destinations for ads
that appear in Android apps. Simply modify the Source input to use a
`destination` field instead of `web_destination` and supply an app package name
instead of a URL, like `android-app://com.app.example`. In order for a Trigger
to be attributable to a Source with an app destination, change the
`destination_type` from `WEB` to `APP` and ensure the `attribution_destination`
field matches the `destination` field of the Source.

### Client-side behavior

The Measurement Simulation Library mirrors the Privacy Sandbox's [Attribution
Reporting API][6] logic, and uses the same privacy parameters to generate client
side output.

### Aggregate API behavior

The Measurement Simulation Library runs a local instance of the actual
aggregation service (LocalTestingTool) which works with unencrypted aggregatable
reports and allows ad techs to consume unlimited privacy budget.

**Domain file generation**

The LocalTestingTool has a flag that specifies where to locate a domain.avro
file. This file is meant to specify which aggregation keys to perform
aggregation on. Presently, The Measurement Simulation Library will bypass this
flag so that users do not need to specify it, thus simplifying testing. However,
understanding the usage of this file and how it is created will be necessary for
using the actual Aggregation API. Aggregation API will not work without this
file.

Instructions for how to create the file can be found in the Aggregation Service
github repo:
[https://github.com/privacysandbox/aggregation-service/blob/main/docs/COLLECTING.md#process-avro-batch-files][7]

To decide which buckets to include in the domain file see the [definition for
Aggregation keys (buckets)][8].

Should you decide to create `domain.avro` files, The Measurement Simulation
Library will fetch those files just before invocation of the LocalTestingTool
using the following steps:

1. Identify the batch key of the batch. The batch key is the key returned from
  the batching strategy.
1. Sanitize the batch key. The following characters are turned into a hyphen:
  period(.), forward-slash (/), asterisk (*), colon (:), tilde (~).
1. Locate the batch's domain file. All domain files will be under the `domain`
  subdirectory. Under the `domain` subdirectory, batch specific subdirectories
  should be named after the batch key. Under the batch key subdirectories should
  be a `domain.avro` file.

### Client-side output

**Client-side, event-level report formatting**: The Measurement Simulation
Library produces event reports in the same JSON format as Privacy Sandbox
production reports:

```
{
  "attribution_destination": String,
  "scheduled_report_time": String,
  "source_event_id": long,
  "trigger_data": long,
  "report_id": String,
  "source_type": "EVENT/NAVIGATION",
  "randomized_trigger_rate": double,
}
```

**Do developers need to transform or sanitize this output in any way to prepare
for sending to a test server?**

The client generates a list of aggregatable reports for each `user_id` and then
aggregates them as a single flat list in the Measurement Simulation Library. Ad
techs can provide their own [batching strategy][9] to generate
different batches of the aggregatable reports and each batch is independently
processed by the aggregation service.

The Measurement Simulation Library provides a [daily batching
strategy][9] as a default strategy to help you generate these
batches.

### Server-side output

**Server-side, aggregate report formatting**

Since you are running an actual aggregation server locally, the output aggregate
reports follow the following Avro format:

```
{
  "type":"record",
  "name":"AggregatedFact",
  "fields":[
    {
      "name":"bucket",
      "type":"bytes",
      "doc":"Histogram bucket used in aggregation. 128-bit integer encoded as a 16-byte big-endian bytestring. Leading 0-bits will be left out."
    },
    {
      "name":"metric",
      "type":"long",
      "doc":"Metric associated with the bucket"
    }
  ]
}
```

**How can developers interpret the results?**

The result of the output aggregate reports is formatted as a list of `<bucket,
metric>` where `bucket` is a 128-bit key and `metric` is the corresponding
value.

### Testing

You should understand the impact of the Attribution Reporting API on your
solutions before you finish your Privacy Sandbox integration. Here are some
points to consider when testing with the Measurement Simulation Library:

- Are there specific use cases or measurement workflows that developers should
  try out using the Measurement Simulation Library?
- Compare historical conversion numbers with Measurement Simulation Library
  results to see how reporting accuracy is affected.
- Experiment with different aggregation key structures and batching strategies.
- Train optimization models on Measurement Simulation Library reports to compare
  projected performance with models based on current data.
- Think about seasonality. Do you need to adjust your aggregation keys and
  batching strategies to account for low or high conversion seasons?
- Think about how you intend to translate different noising results to your
  customers, and how much customization you can allow.
- Consider how you intend to use event-level and aggregate reports together.

### Feedback

If you have any feedback while using the Measurement Simulation Library, please
[let us know][10].

[1]: https://developer.chrome.com/docs/privacy-sandbox/summary-reports/design-decisions/
[2]: /design-for-safety/privacy-sandbox/attribution#app-web-based-trigger-paths
[3]: https://developer.chrome.com/en/docs/privacy-sandbox/aggregation-service/
[4]: https://github.com/privacysandbox/measurement-simulation/blob/main/README.md
[5]: https://beam.apache.org/
[6]: /design-for-safety/privacy-sandbox/attribution
[7]: https://github.com/privacysandbox/aggregation-service/blob/main/docs/COLLECTING.md#process-avro-batch-files
[8]: https://docs.google.com/document/d/1bU0a_njpDcRd9vDR0AJjwJjrf3Or8vAzyfuK8JZDEfo/edit#heading=h.yftw2hlnfyr4
[9]: https://github.com/privacysandbox/measurement-simulation/blob/main/java/com/google/measurement/adtech/BatchAggregatableReports.java
[10]: https://issuetracker.google.com/issues/new?component=1116743&template=1629474
