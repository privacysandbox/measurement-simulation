# Attribution Reporting Simulation Library

The Attribution Reporting Simulation Library allows you to examine the impact of the Attribution Reporting API by taking historical data and presenting it as if it were collected by the API in real-time. This allows you to compare historical conversion numbers with Attribution Reporting Simulation Library results to see how reporting accuracy would change. You can also use the Simulation Library to experiment with different aggregation key structures and batching strategies, and train optimization models on Simulation Library reports to compare projected performance with models based on current data.

The Attribution Reporting Simulation Library provides a simplified mock environment, allowing you to test parameters and evaluate how the API can satisfy ad tech measurement use cases while making minimal investments in local infrastructure and resources.

This document shows you how to get up and running with the Attribution Reporting Simulation Library. For more details, see [OVERVIEW.md](OVERVIEW.md)

# Build & Run

This repository depends on Bazel 7.3.2 with JDK 21 and Python 3.8. The following environment variables should be set in your local environment (the exact location will depend on your environment):

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
PATH="path/to/java/jdk-21/bin:$PATH"
python=/usr/local/bin/python
```


### Simulation CLI arguments
The library uses general simulation related arguments which can be passed in the CLI. Following is the list of such arguments:

| Argument                           | Description                                                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| runfilesDirectory                  | Directory to PipelineRunner runfiles.                                                                                               |
| inputDirectory                     | Directory to input files. Must be an absolute path to a directory. All json files will be ingested from that directory. The names of the files will indicate the user-id to the simulation. |
| eventReportsOutputDirectory        | Directory to write event reports to. Must be an absolute path to a directory. All event reports for a user will be written to a single file. The filename will be the user id. Default: /tmp/event_reports/ |
| aggregatableReportsOutputDirectory | Directory to write aggregatable reports to. Must be an absolute path to a directory. The format will be avro. These files will be used as input to the Aggregation Service LocalTestingTool. Default: /tmp/aggregatable_reports/ |
| aggregationResultsDirectory        | Directory to write aggregation results to. Must be an absolute path to a directory. Default: /tmp/aggregation_results/                |


### Configuring Privacy parameters

Privacy parameters for the Event and Aggregate APIs are configured directly within the Java source code.

- **Event API Noising:** Parameters are managed in `java/com/google/measurement/client/Flags.java`, mirroring the AdServices code in the Android codebase.
- **Aggregation Service:** Arguments for the aggregation service are set in `java/com/google/measurement/pipeline/AggregationServiceTransform.java`.

To change these parameters, you will need to modify the respective Java files and rebuild the project.

You can run the simulation library as a standalone Python library, imported as a Python module, or as a JAR:

### Standalone Python library
1. Navigate to the root directory of the library and run `bazel build //...` to build the repo.
2. Execute it by running `bazel run -- //python:main`, followed by the desired arguments, e.g. `--input_directory=<path_to_testdata>`.

### Import as Python module
1. Copy your script that contains the existing pipeline to the Python directory.
2. Import the Attribution Reporting Simulation Library and instantiate the simulation runner:

    ```
    from pipeline_runner_wrapper import PipelineRunnerWrapper
    from pipeline_config import PipelineConfig

    config = PipelineConfig(input_directory=<path_to_testdata>, ...)
    runner = PipelineRunnerWrapper(pipeline_config=config)
    ```

3. Finally, execute the simulation by calling
    ```
    runner.run()
    ```

### Run the JAR directly
Execute the simulation by running `bazel run -- //:PipelineRunner` followed by desired arguments, e.g. `--inputDirectory=<path_to_testdata>`.


### Sample run
```
$ bazel run -- //:PipelineRunner --inputDirectory=<path_to_simulation_library>/testdata/ --eventReportsOutputDirectory=<path_to_output_directory>/event_reports --aggregatableReportsOutputDirectory=<path_to_output_directory>/aggregatable_reports --aggregationResultsDirectory=<path_to_output_directory>/aggregation_results
```

After the successful run, you should see the following files and directories in the output directory:
- event_reports
  - <user_id>.json - Event reports for each user.
- aggregatable_reports
  - Several .avro files - These are aggregatable batches that are sent to the aggregation service as input.
- aggregation_results
  - For each .avro file in aggregatable_reports, an output directory will be created with the same name which contains:
    - output.json - Output aggregate report
    - result_info.json

### Reading from the aggregatable report avro files
You can download the Avro tools jar 1.12.0 [here](https://downloads.apache.org/avro/stable/java/avro-tools-1.12.0.jar). To read the avro file in human-readable json format, run:
```
java -jar avro-tools-1.12.0.jar tojson <output_avro_file>
```

You can see output similar to:
```
{"payload":"¢ddata¢evalueD\u0000\u0000\u0000fbucketP\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0005Y¢evalueD\u0000\u0000\u0006fbucketP\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\n
ioperationihistogram","key_id":"ea91d481-1482-4975-8c63-635fb866b4c8","shared_info":"{\"api\":\"attribution-reporting\",\"attribution_destination\":\"android-app:\\/\\/com.advertiser\",\"report_id\":\"f2c53430-dccc-4844-9dc7-53301c58f0dd\",\"reporting_origin\":\"https:\\/\\/reporter.com\",\"scheduled_report_time\":\"800000858\",\"source_registration_time\":\"799977600\",\"version\":\"0.1\"}"}
```

### Memory Considerations
The simulation library requires the entire data to be in memory in order to run. If you get Java Heap space out of memory exception, you can increase the upper bound on the Java heap memory by specifying `-Xmx` option while running the library. For instance, if you want to set the maximum heap memory to 128GB, you can specify it as:
```
$ bazel run --jvmopt="-Xmx128g" -- //:PipelineRunner --inputDirectory=<path_to_simulation_library>/testdata/ --eventReportsOutputDirectory=<path_to_output_directory>/event_reports --aggregatableReportsOutputDirectory=<path_to_output_directory>/aggregatable_reports --aggregationResultsDirectory=<path_to_output_directory>/aggregation_results
```


# Contribution

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.
