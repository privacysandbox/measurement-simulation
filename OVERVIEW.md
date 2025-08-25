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
Simulation Library ingests all JSON files from the provided input directory. Each file represents a user, and the file name is used as the user ID. The library then simulates the client-side behavior for each user in parallel.

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
`java/com/google/measurement/pipeline/KeyByDestinationAndDayTransform.java`.

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
input data from a directory of JSON files. Each file's name is treated as a "user id", and its content should be a JSON object containing "sources" and "triggers" arrays. The data is processed, and an attribution reporting simulation algorithm is called for each user ID in parallel. Once the event and aggregatable reports are created for each user, the Beam library combines the aggregatable reports and groups them into daily batches, and these batches are sent to the local aggregation service.

The Measurement Simulation Library supports JSON for input data and event report
output. Aggregate reports support both JSON and Avro formats. All event reports
for a user ID are written in a single JSON file in the configured event reports output directory.

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

The sample input data shown below demonstrates the types of
information that is consumed by the Measurement Simulation Library. The library expects a directory of JSON files, where each file name is a user ID, and the content is a JSON object with "sources" and "triggers" arrays.

Example input file `user1.json`:
```json
{
  "sources": [
    {
      "registration_request": {
        "source_type": "navigation",
        "registrant": "com.publisher"
      },
      "responses": [
        {
          "url": "https://reporter.test",
          "response": {
            "Attribution-Reporting-Register-Source": {
              "source_event_id": "1",
              "destination": "android-app://com.advertiser",
              "aggregation_keys": {
                "campaignCounts": "0x159",
                "geoValue": "0x5"
              }
            }
          }
        }
      ],
      "timestamp": "800000000001"
    }
  ],
  "triggers": [
    {
      "registration_request": {
        "registrant": "com.advertiser"
      },
      "responses": [
        {
          "url": "https://reporter.test",
          "response": {
            "Attribution-Reporting-Register-Trigger": {
              "aggregatable_trigger_data": [
                {
                  "key_piece": "0x400",
                  "source_keys": ["campaignCounts"]
                },
                {
                  "key_piece": "0xA80",
                  "source_keys": ["geoValue"]
                }
              ],
              "aggregatable_values": {
                "campaignCounts": 32768,
                "geoValue": 1664
              }
            }
          }
        }
      ],
      "timestamp": "800000600001"
    }
  ]
}
```

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
