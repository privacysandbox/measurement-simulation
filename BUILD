load("@com_github_bazelbuild_buildtools//buildifier:def.bzl", "buildifier")

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
        "java/com/google/measurement/AdServicesConfig.java",
        "java/com/google/measurement/AdtechUrl.java",
        "java/com/google/measurement/ApiChoice.java",
        "java/com/google/measurement/Attribution.java",
        "java/com/google/measurement/AttributionConfig.java",
        "java/com/google/measurement/AttributionJobHandler.java",
        "java/com/google/measurement/Constants.java",
        "java/com/google/measurement/DatastoreManager.java",
        "java/com/google/measurement/EventReport.java",
        "java/com/google/measurement/EventSurfaceType.java",
        "java/com/google/measurement/EventTrigger.java",
        "java/com/google/measurement/FetcherUtil.java",
        "java/com/google/measurement/FilterMap.java",
        "java/com/google/measurement/IMeasurementDAO.java",
        "java/com/google/measurement/MeasurementDAO.java",
        "java/com/google/measurement/PrivacyParams.java",
        "java/com/google/measurement/Source.java",
        "java/com/google/measurement/SourceProcessor.java",
        "java/com/google/measurement/SystemHealthParams.java",
        "java/com/google/measurement/Trigger.java",
        "java/com/google/measurement/TriggerProcessor.java",
        "java/com/google/measurement/UserSimulation.java",
        "java/com/google/measurement/XNetworkData.java",
        "java/com/google/measurement/aggregation/AggregatableAttributionSource.java",
        "java/com/google/measurement/aggregation/AggregatableAttributionTrigger.java",
        "java/com/google/measurement/aggregation/AggregateAttributionData.java",
        "java/com/google/measurement/aggregation/AggregateCborConverter.java",
        "java/com/google/measurement/aggregation/AggregateDeduplicationKey.java",
        "java/com/google/measurement/aggregation/AggregateHistogramContribution.java",
        "java/com/google/measurement/aggregation/AggregatePayload.java",
        "java/com/google/measurement/aggregation/AggregatePayloadGenerator.java",
        "java/com/google/measurement/aggregation/AggregateReport.java",
        "java/com/google/measurement/aggregation/AggregateTriggerData.java",
        "java/com/google/measurement/noising/Combinatorics.java",
        "java/com/google/measurement/noising/ImpressionNoiseParams.java",
        "java/com/google/measurement/noising/ImpressionNoiseUtil.java",
        "java/com/google/measurement/util/BaseUriExtractor.java",
        "java/com/google/measurement/util/Filter.java",
        "java/com/google/measurement/util/MathUtils.java",
        "java/com/google/measurement/util/Web.java",
    ],
    deps = [
        ":Util",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_annotations",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_avro_avro",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_library(
    name = "AggregationArgs",
    srcs = [
        "java/com/google/measurement/aggregation/AggregationArgs.java",
    ],
    data = [":config"],
    deps = [":Util"],
)

java_test(
    name = "SourceProcessorTest",
    srcs = ["javatests/com/google/measurement/SourceProcessorTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_library(
    name = "SourceFixture",
    srcs = ["javatests/com/google/measurement/SourceFixture.java"],
    deps = [
        ":ClientDevice",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_library(
    name = "TriggerFixture",
    srcs = ["javatests/com/google/measurement/TriggerFixture.java"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "SourceTest",
    srcs = ["javatests/com/google/measurement/SourceTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":SourceFixture",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "TriggerProcessorTest",
    srcs = ["javatests/com/google/measurement/TriggerProcessorTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_google_guava_guava",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "TriggerTest",
    srcs = [
        "javatests/com/google/measurement/TriggerTest.java",
        "javatests/com/google/measurement/WebUtil.java",
    ],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":TriggerFixture",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "EventReportTest",
    srcs = ["javatests/com/google/measurement/EventReportTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":SourceFixture",
        ":TriggerFixture",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "FilterMapTest",
    srcs = ["javatests/com/google/measurement/FilterMapTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "MeasurementDAOTest",
    srcs = ["javatests/com/google/measurement/MeasurementDAOTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":Util",
    ],
)

java_test(
    name = "AttributionJobHandlerTest",
    srcs = ["javatests/com/google/measurement/AttributionJobHandlerTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        ":SourceFixture",
        ":TriggerFixture",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "CombinatoricsTest",
    srcs = [
        "javatests/com/google/measurement/noising/CombinatoricsTest.java",
    ],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "ImpressionNoiseUtilTest",
    srcs = [
        "javatests/com/google/measurement/noising/ImpressionNoiseUtilTest.java",
    ],
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
        "javatests/com/google/measurement/aggregation/AggregateAttributionDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatableAttributionSourceTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregatableAttributionSourceTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatableAttributionTriggerTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregatableAttributionTriggerTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateCborConverterTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregateCborConverterTest.java",
    ],
    deps = [
        ":ClientDevice",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "AggregateDeduplicationKeyTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregateDeduplicationKeyTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":Util",
    ],
)

java_test(
    name = "AggregateHistogramContributionTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregateHistogramContributionTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatePayloadGeneratorTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregatePayloadGeneratorTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":SourceFixture",
        ":TriggerFixture",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "AggregatePayloadTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregatePayloadTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateTriggerDataTest",
    srcs = [
        "javatests/com/google/measurement/aggregation/AggregateTriggerDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "FetcherUtilTest",
    srcs = ["javatests/com/google/measurement/FetcherUtilTest.java"],
    data = [":config"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_library(
    name = "Util",
    srcs = [
        "java/com/google/measurement/util/UnsignedLong.java",
        "java/com/google/measurement/util/Util.java",
    ],
    deps = [
        "@maven//:com_google_guava_guava",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_library(
    name = "DataProcessor",
    srcs = [
        "java/com/google/measurement/DataProcessor.java",
        "java/com/google/measurement/RunSimulationPerUser.java",
        "java/com/google/measurement/SimulationConfig.java",
    ],
    deps = [
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
        "java/com/google/measurement/Constants.java",
        "java/com/google/measurement/InputFileProcessor.java",
    ],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_test(
    name = "DataProcessorTest",
    srcs = ["javatests/com/google/measurement/DataProcessorTest.java"],
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
        "java/com/google/measurement/adtech/BatchAggregatableReports.java",
        "java/com/google/measurement/adtech/LocalAggregationRunner.java",
        "java/com/google/measurement/adtech/ProcessBatch.java",
    ],
    data = [
        ":domain",
        ":reports",
    ],
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
        "javatests/com/google/measurement/adtech/BatchAggregatableReportsTest.java",
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
        "javatests/com/google/measurement/adtech/LocalAggregationRunnerTest.java",
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
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "BaseUriExtractorTest",
    srcs = ["javatests/com/google/measurement/util/BaseUriExtractorTest.java"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "FilterTest",
    srcs = ["javatests/com/google/measurement/util/FilterTest.java"],
    deps = [
        ":ClientDevice",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "UtilTest",
    srcs = ["javatests/com/google/measurement/util/UtilTest.java"],
    data = ["testdata"],
    deps = [
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
    ],
)

java_test(
    name = "WebTest",
    srcs = ["javatests/com/google/measurement/util/WebTest.java"],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "UnsignedLongTest",
    srcs = ["javatests/com/google/measurement/util/UnsignedLongTest.java"],
    deps = [
        ":ClientDevice",
        ":Util",
    ],
)

java_test(
    name = "UserSimulationTest",
    srcs = ["javatests/com/google/measurement/UserSimulationTest.java"],
    deps = [
        ":ClientDevice",
        ":Util",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_binary(
    name = "SimulationRunner",
    srcs = [
        "java/com/google/measurement/SimulationRunner.java",
    ],
    main_class = "com.google.measurement.SimulationRunner",
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
    srcs = ["javatests/com/google/measurement/RunE2ETest.java"],
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

# Built from v0.9.0 of aggregate-service.
java_import(
    name = "AggregationWorker",
    jars = [
        "lib/LocalTestingTool_0.9.0.jar",
    ],
)

filegroup(
    name = "domain",
    srcs = glob(["domain/**/*"]),
    visibility = ["//python:__pkg__"],
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

filegroup(
    name = "reports",
    srcs = glob(["reports.avsc"]),
    visibility = ["//python:__pkg__"],
)
