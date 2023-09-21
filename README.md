# Attribution Reporting Simulation Library

The Attribution Reporting Simulation Library allows you to examine the impact of the Attribution Reporting API by taking historical data and presenting it as if it were collected by the API in real-time. This allows you to compare historical conversion numbers with Attribution Reporting Simulation Library results to see how reporting accuracy would change. You can also use the Simulation Library to experiment with different aggregation key structures and batching strategies, and train optimization models on Simulation Library reports to compare projected performance with models based on current data.

The Attribution Reporting Simulation Library provides a simplified mock environment, allowing you to test parameters and evaluate how the API can satisfy ad tech measurement use cases while making minimal investments in local infrastructure and resources.

This document shows you how to get up and running with the Attribution Reporting Simulation Library. For more details, see [OVERVIEW.md](OVERVIEW.md)

# Build & Run

This repository depends on Bazel 4.2.2 with JDK 11 and Python 3.8. The following environment variables should be set in your local environment (the exact location will depend on your environment):

```
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
python=/usr/local/bin/python
```


### Simulation CLI arguments
The library uses general simulation related arguments which can be passed in the CLI. Following is the list of such arguments:

| Running Python wrapper       | Running java code         | Description                                                                                                                         |
|------------------------------|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| input_directory              | inputDirectory            | The top level directory of where the library will get its inputs                                                                    |
| output_directory             | outputDirectory           | The directory that will hold results from the simulation                                                                            |
| source_start_date            | sourceStartDate           | The first date of attribution source events                                                                                         |
| source_end_date              | sourceEndDate             | The last date of attribution source events, should come on or after source_start_date                                               |
| attribution_source_file_name | attributionSourceFileName | The file name that will be used to identify the files that hold attribution source events. Default value: "attribution_source.json" |
| trigger_start_date           | triggerStartDate          | The first date of trigger events                                                                                                    |
| trigger_end_date             | triggerEndDate            | The last date of trigger events, should come on or after trigger_start_date                                                         |
| trigger_file_name            | triggerFileName           | The file name that will be used to identify the files that hold trigger events. Default value: "trigger.json"                       |
| extension_event_start_date   | extensionEventStartDate   | The first date of install/uninstall events                                                                                          |
| extension_event_end_date     | extensionEventEndDate     | The last date of install/uninstall events, should come on or after extension_event_start_date                                       |
| extension_event_file_name    | extensionEventFileName    | The file name that will be used to identify the files that hold install/uninstall events. Default value: "extension.json"           |


### Configuring Privacy parameters

The library allows you to configure the privacy params for both Event and Aggregate API. These params are located in the library's `config` directory:

1. AggregationArgs.properties: Configurable params for Aggregation service.
2. PrivacyParams.properties: Configurable noising params for Event API.

Simply modify the params in these properties file and run the library.

You can run the simulation library as a standalone Python library, imported as a Python module, or as a JAR:

### Standalone Python library
1. Navigate to the root directory of the library and run `bazel build //...` to build the repo.
2. Execute it by running `bazel run -- //python:main`, followed by the desired arguments, e.g. `--input_directory=<path_to_testdata>`.

### Import as Python module
1. Copy your script that contains the existing pipeline to the Python directory.
2. Import the Attribution Reporting Simulation Library and instantiate the simulation runner:

    ```
    from simulation_runner_wrapper import SimulationRunnerWrapper
    simulation_runner = SimulationRunnerWrapper()
    ```
3. Instantiate the `SimulationConfig` class to provide command-line arguments:

    ```
    from simulation_config import SimulationConfig
    config = SimulationConfig(input_directory=<path_to_testdata>, source_start_date="2022-10-20", ...)
    ```

4. Finally, execute the simulation by calling
    ```
    simulation_runner.run(simulation_config=config)
    ```

### Run the JAR directly
Execute the simulation by running `bazel run -- //:SimulationRunner` followed by desired arguments, e.g. `--inputDirectory=<path_to_testdata>`.


### Sample run
```
$ bazel run -- //:SimulationRunner --sourceStartDate=2022-01-15 --sourceEndDate=2022-01-16 --triggerStartDate=2022-01-15 --triggerEndDate=2022-02-06 --inputDirectory=<path_to_simulation_library>/testdata/ --outputDirectory=<path_to_output_directory>
```

After the successful run, you should see the following files and directories in the output directory:
- input_batches
  - Several .avro files - These are aggregatable batches that are sent to the aggregation service as input.

- For each .avro file, the following will also be generated:
  - <input_avro_file_name>/output.avro - Output aggregate report
  - <input_avro_file_name>/result_info.json

- OS/U1/event_reports.json - Event reports for the user "U1" using logs for the OS platform
- OS/U2/event_reports.json - Event reports for the user "U2" using logs for the OS platform

### Reading from the output avro files
You can download the Avro tools jar 1.11.1 [here](https://downloads.apache.org/avro/stable/java/avro-tools-1.11.1.jar). To read the avro file in human-readable json format, run:
```
java -jar avro-tools-1.11.1.jar tojson <output_avro_file>
```

You can see the output as:
```
{"bucket": "key1", "metric": <value1>}
{"bucket": "key2", "metric": <value2>}
```


# Contribution

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.
