load("@com_github_bazelbuild_buildtools//buildifier:def.bzl", "buildifier")
load("@rules_proto//proto:defs.bzl", "proto_library")

buildifier(
    name = "buildifier_check",
    mode = "check",
)

buildifier(
    name = "buildifier_fix",
    mode = "fix",
)

java_library(
    name = "ClientDevice",
    srcs = [
        "java/com/google/rubidium/AdServicesConfig.java",
        "java/com/google/rubidium/AdtechUrl.java",
        "java/com/google/rubidium/Attribution.java",
        "java/com/google/rubidium/AttributionJobHandler.java",
        "java/com/google/rubidium/Constants.java",
        "java/com/google/rubidium/DatastoreManager.java",
        "java/com/google/rubidium/EventReport.java",
        "java/com/google/rubidium/EventSurfaceType.java",
        "java/com/google/rubidium/EventTrigger.java",
        "java/com/google/rubidium/FetcherUtil.java",
        "java/com/google/rubidium/FilterUtil.java",
        "java/com/google/rubidium/IMeasurementDAO.java",
        "java/com/google/rubidium/MeasurementDAO.java",
        "java/com/google/rubidium/PrivacyParams.java",
        "java/com/google/rubidium/Source.java",
        "java/com/google/rubidium/SourceProcessor.java",
        "java/com/google/rubidium/SystemHealthParams.java",
        "java/com/google/rubidium/Trigger.java",
        "java/com/google/rubidium/TriggerProcessor.java",
        "java/com/google/rubidium/UserSimulation.java",
        "java/com/google/rubidium/aggregation/AggregatableAttributionSource.java",
        "java/com/google/rubidium/aggregation/AggregatableAttributionTrigger.java",
        "java/com/google/rubidium/aggregation/AggregateAttributionData.java",
        "java/com/google/rubidium/aggregation/AggregateCborConverter.java",
        "java/com/google/rubidium/aggregation/AggregateFilterData.java",
        "java/com/google/rubidium/aggregation/AggregateHistogramContribution.java",
        "java/com/google/rubidium/aggregation/AggregatePayload.java",
        "java/com/google/rubidium/aggregation/AggregatePayloadGenerator.java",
        "java/com/google/rubidium/aggregation/AggregateReport.java",
        "java/com/google/rubidium/aggregation/AggregateTriggerData.java",
        "java/com/google/rubidium/noising/Combinatorics.java",
        "java/com/google/rubidium/noising/ImpressionNoiseParams.java",
        "java/com/google/rubidium/noising/ImpressionNoiseUtil.java",
        "java/com/google/rubidium/util/BaseUriExtractor.java",
        "java/com/google/rubidium/util/Filter.java",
        "java/com/google/rubidium/util/Web.java",
    ],
    deps = [
        ":Util",
        ":input_data_java_proto",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_annotations",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_avro_avro",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_library(
    name = "AggregationArgs",
    srcs = [
        "java/com/google/rubidium/aggregation/AggregationArgs.java",
    ],
    data = [":config"],
    deps = [":Util"],
)

java_test(
    name = "SourceProcessorTest",
    srcs = ["javatests/com/google/rubidium/SourceProcessorTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":input_data_java_proto",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "SourceTest",
    srcs = ["javatests/com/google/rubidium/SourceTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "TriggerProcessorTest",
    srcs = ["javatests/com/google/rubidium/TriggerProcessorTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":input_data_java_proto",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "TriggerTest",
    srcs = ["javatests/com/google/rubidium/TriggerTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "EventReportTest",
    srcs = ["javatests/com/google/rubidium/EventReportTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "MeasurementDAOTest",
    srcs = ["javatests/com/google/rubidium/MeasurementDAOTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "AttributionJobHandlerTest",
    srcs = ["javatests/com/google/rubidium/AttributionJobHandlerTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "AggregateAttributionDataTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregateAttributionDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatableAttributionSourceTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregatableAttributionSourceTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatableAttributionTriggerTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregatableAttributionTriggerTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateCborConverterTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregateCborConverterTest.java",
    ],
    deps = [
        ":ClientDevice",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "AggregateFilterDataTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregateFilterDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateHistogramContributionTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregateHistogramContributionTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatePayloadGeneratorTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregatePayloadGeneratorTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatePayloadTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregatePayloadTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateTriggerDataTest",
    srcs = [
        "javatests/com/google/rubidium/aggregation/AggregateTriggerDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "FetcherUtilTest",
    srcs = ["javatests/com/google/rubidium/FetcherUtilTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_library(
    name = "Util",
    srcs = [
        "java/com/google/rubidium/util/Util.java",
    ],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "DataProcessor",
    srcs = [
        "java/com/google/rubidium/DataProcessor.java",
        "java/com/google/rubidium/RunSimulationPerUser.java",
        "java/com/google/rubidium/SimulationConfig.java",
    ],
    deps = [
        ":AggregationArgs",
        ":ClientDevice",
        ":InputFileProcessor",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_library(
    name = "InputFileProcessor",
    srcs = [
        "java/com/google/rubidium/Constants.java",
        "java/com/google/rubidium/InputFileProcessor.java",
    ],
    deps = [
        ":ClientDevice",
        ":input_data_java_proto",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_test(
    name = "DataProcessorTest",
    srcs = ["javatests/com/google/rubidium/DataProcessorTest.java"],
    data = [
        "testdata",
        ":config",
    ],
    deps = [
        ":ClientDevice",
        ":DataProcessor",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_library(
    name = "AggregateReport",
    srcs = [
        "java/com/google/rubidium/adtech/BatchAggregatableReports.java",
        "java/com/google/rubidium/adtech/LocalAggregationRunner.java",
        "java/com/google/rubidium/adtech/ProcessBatch.java",
    ],
    data = ["reports.avsc"],
    deps = [
        ":AggregationArgs",
        ":AggregationWorker",
        ":Util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_avro_avro",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_test(
    name = "BatchAggregatableReportsTest",
    srcs = [
        "javatests/com/google/rubidium/adtech/BatchAggregatableReportsTest.java",
    ],
    deps = [
        ":AggregateReport",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "LocalAggregationRunnerTest",
    srcs = [
        "javatests/com/google/rubidium/adtech/LocalAggregationRunnerTest.java",
    ],
    data = ["testdata"],
    deps = [
        ":AggregateReport",
        ":AggregationArgs",
        ":AggregationWorker",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "BaseUriExtractorTest",
    srcs = ["javatests/com/google/rubidium/util/BaseUriExtractorTest.java"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "FilterTest",
    srcs = ["javatests/com/google/rubidium/util/FilterTest.java"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "UtilTest",
    srcs = ["javatests/com/google/rubidium/util/UtilTest.java"],
    data = ["testdata"],
    deps = [
        ":Util",
    ],
)

java_test(
    name = "WebTest",
    srcs = ["javatests/com/google/rubidium/util/WebTest.java"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "UserSimulationTest",
    srcs = ["javatests/com/google/rubidium/UserSimulationTest.java"],
    deps = [
        ":ClientDevice",
        "@maven//:org_mockito_mockito_core",
    ],
)

proto_library(
    name = "input_data_proto",
    srcs = ["proto/input_data.proto"],
    deps = ["@com_github_protocolbuffers_protobuf//:struct_proto"],
)

java_proto_library(
    name = "input_data_java_proto",
    deps = [":input_data_proto"],
)

java_binary(
    name = "SimulationRunner",
    srcs = [
        "java/com/google/rubidium/SimulationRunner.java",
    ],
    data = [
        ":config",
    ],
    main_class = "com.google.rubidium.SimulationRunner",
    visibility = ["//python:__pkg__"],
    deps = [
        ":AggregateReport",
        ":ClientDevice",
        ":DataProcessor",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_test(
    name = "RunE2ETest",
    srcs = ["javatests/com/google/rubidium/RunE2ETest.java"],
    data = [
        "e2e_tests",
        ":config",
    ],
    deps = [
        ":AggregateReport",
        ":AggregationArgs",
        ":ClientDevice",
        ":DataProcessor",
        ":SimulationRunner",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_avro_avro",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

# Built from v0.4.0 of aggregate-service.
java_import(
    name = "AggregationWorker",
    jars = [
        "lib/LocalTestingTool_deploy.jar",
    ],
)

filegroup(
    name = "testdata",
    srcs = glob(["testdata/**/*"]),
    visibility = ["//python:__pkg__"],
)

filegroup(
    name = "config",
    srcs = glob(["config/*"]),
    visibility = ["//python:__pkg__"],
)
