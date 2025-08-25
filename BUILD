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
    srcs = glob(
        ["java/com/google/measurement/client/**"],
        exclude = ["java/com/google/measurement/client/MockRunner.java"],
    ),
    deps = [
        ":auto",
        ":org_json",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_auto_value_auto_value",
        "@maven//:com_google_auto_value_auto_value_annotations",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_guava_guava_annotations",
        "@maven//:org_xerial_sqlite_jdbc",
    ],
)

java_plugin(
    name = "auto_plugin",
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    deps = ["@com_google_auto_value_auto_value//jar"],
)

java_library(
    name = "auto",
    exported_plugins = [":auto_plugin"],
    exports = ["@com_google_auto_value_auto_value//jar"],
)

java_library(
    name = "org_json",
    srcs = glob(["java/org/json/**"]),
)

java_binary(
    name = "MockRunner",
    srcs = glob([
        "java/com/google/measurement/MockRunnerMain.java",
    ]),
    main_class = "com.google.measurement.MockRunnerMain",
    visibility = ["//python:__pkg__"],
    deps = [
        ":MockRunner_lib",
    ],
)

java_library(
    name = "MockRunner_lib",
    srcs = glob([
        "java/com/google/measurement/client/MockRunner.java",
        "javatests/com/google/measurement/client/actions/**/*",
    ]),
    deps = [
        ":ClientDevice",
        ":E2EMock",
        ":org_json",
    ],
)

java_test(
    name = "AsyncSourceFetcherTest",
    srcs = glob([
        "javatests/com/google/measurement/client/registration/*",
        "javatests/com/google/measurement/client/AdServicesExtendedMockitoTestCase.java",
        "javatests/com/google/measurement/client/ExpectErrorLogUtilCall.java",
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SetErrorLogUtilDefaultParams.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
        "javatests/com/google/measurement/client/TriggerSpecsUtil.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/data/DbTestUtil.java",
    ]),
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "AsyncTriggerFetcherTest",
    srcs = glob([
        "javatests/com/google/measurement/client/registration/*",
        "javatests/com/google/measurement/client/AdServicesExtendedMockitoTestCase.java",
        "javatests/com/google/measurement/client/ExpectErrorLogUtilCall.java",
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SetErrorLogUtilDefaultParams.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
        "javatests/com/google/measurement/client/TriggerSpecsUtil.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/data/DbTestUtil.java",
    ]),
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "AsyncRegistrationQueueRunnerTest",
    srcs = glob([
        "javatests/com/google/measurement/client/registration/*",
        "javatests/com/google/measurement/client/AdServicesExtendedMockitoTestCase.java",
        "javatests/com/google/measurement/client/ExpectErrorLogUtilCall.java",
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SetErrorLogUtilDefaultParams.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
        "javatests/com/google/measurement/client/TriggerSpecsUtil.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/data/DbTestUtil.java",
    ]),
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "MeasurementDaoTest",
    srcs = glob([
        "javatests/com/google/measurement/client/data/*",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/registration/AsyncRegistrationFixture.java",
        "javatests/com/google/measurement/client/reporting/EventReportFixture.java",
        "javatests/com/google/measurement/client/aggregation/AggregateReportFixture.java",
    ]),
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "AttributionJobHandlerTest",
    srcs = glob([
        "javatests/com/google/measurement/client/attribution/AttributionJobHandlerTest.java",
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
    ]),
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_library(
    name = "E2EMock",
    srcs = glob([
        "javatests/com/google/measurement/client/E2EAbstractMockTest.java",
        "javatests/com/google/measurement/client/E2EAbstractTest.java",
        "javatests/com/google/measurement/client/E2EMockTest.java",
        "javatests/com/google/measurement/client/InteropTestReader.java",
        "javatests/com/google/measurement/client/MockContentResolver.java",
        "javatests/com/google/measurement/client/TestObjectProvider.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/aggregation/AggregateCryptoFixture.java",
        "javatests/com/google/measurement/client/data/DbTestUtil.java",
        "javatests/com/google/measurement/client/attribution/AttributionJobHandlerWrapper.java",
        "javatests/com/google/measurement/client/reporting/AggregateReportingJobHandlerWrapper.java",
        "javatests/com/google/measurement/client/reporting/DebugReportingJobHandlerWrapper.java",
        "javatests/com/google/measurement/client/reporting/EventReportingJobHandlerWrapper.java",
        "javatests/com/google/measurement/client/actions/**/*",
    ]),
    data = ["e2e_tests"],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "E2EMockTest",
    size = "large",
    test_class = "com.google.measurement.client.E2EMockTest",
    runtime_deps = [":E2EMock"],
)

java_test(
    name = "MockRunnerTest",
    srcs = glob(["javatests/com/google/measurement/MockRunnerTest.java"]),
    deps = [
        ":ClientDevice",
        ":E2EMock",
        ":MockRunner_lib",
        ":org_json",
        "@maven//:com_google_truth_truth",
    ],
)

java_test(
    name = "CombinatoricsTest",
    srcs = [
        "javatests/com/google/measurement/client/noising/CombinatoricsTest.java",
    ],
    deps = [
        ":ClientDevice",
        "@maven//:com_google_guava_guava",
    ],
)

java_test(
    name = "ImpressionNoiseUtilTest",
    srcs = [
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerSpecsUtil.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/noising/ImpressionNoiseUtilTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_googlecode_json_simple_json_simple",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "SourceNoiseHandlerTest",
    srcs = [
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/noising/SourceNoiseHandlerTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "AggregateAttributionDataTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregateAttributionDataTest.java",
    ],
    deps = [":ClientDevice"],
)

#
java_test(
    name = "AggregatableAttributionSourceTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregatableAttributionSourceTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregatableAttributionTriggerTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregatableAttributionTriggerTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "AggregateDeduplicationKeyTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregateDeduplicationKeyTest.java",
    ],
    deps = [
        ":ClientDevice",
    ],
)

java_test(
    name = "AggregateHistogramContributionTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregateHistogramContributionTest.java",
    ],
    deps = [
        "org_json",
        ":ClientDevice",
    ],
)

java_test(
    name = "AggregatePayloadGeneratorTest",
    srcs = [
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerFixture.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/aggregation/AggregatePayloadGeneratorTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "AggregatePayloadTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregatePayloadTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "AggregateTriggerDataTest",
    srcs = [
        "javatests/com/google/measurement/client/aggregation/AggregateTriggerDataTest.java",
    ],
    deps = [":ClientDevice"],
)

java_test(
    name = "FetcherUtilTest",
    srcs = [
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/registration/FetcherUtilTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mockito_mockito_inline",
    ],
)

java_test(
    name = "BaseUriExtractorTest",
    srcs = ["javatests/com/google/measurement/client/util/BaseUriExtractorTest.java"],
    deps = [
        ":ClientDevice",
        ":org_json",
    ],
)

java_test(
    name = "DebugReportTest",
    srcs = [
        "javatests/com/google/measurement/client/WebUtil.java",
        "javatests/com/google/measurement/client/reporting/DebugReportTest.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
    ],
)

java_test(
    name = "FilterTest",
    srcs = ["javatests/com/google/measurement/client/util/FilterTest.java"],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "TriggerSpecTest",
    srcs = [
        "javatests/com/google/measurement/client/FakeFlagsFactory.java",
        "javatests/com/google/measurement/client/SourceFixture.java",
        "javatests/com/google/measurement/client/TriggerSpecTest.java",
        "javatests/com/google/measurement/client/WebUtil.java",
    ],
    deps = [
        ":ClientDevice",
        ":org_json",
    ],
)

java_test(
    name = "UnsignedLongTest",
    srcs = ["javatests/com/google/measurement/client/util/UnsignedLongTest.java"],
    deps = [":ClientDevice"],
)

java_library(
    name = "AggregatableReportConverterLib",
    srcs = ["java/com/google/measurement/util/AggregatableReportConverter.java"],
    resources = [":reports"],
    deps = [
        ":ClientDevice",
        ":org_json",
        "@maven//:org_apache_avro_avro",
    ],
)

java_test(
    name = "AggregatableReportConverterTest",
    srcs = ["javatests/com/google/measurement/util/AggregatableReportConverterTest.java"],
    data = [":reports"],
    deps = [
        ":AggregatableReportConverterLib",
        ":ClientDevice",
        ":org_json",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_truth_truth",
        "@maven//:org_apache_avro_avro",
    ],
)

java_test(
    name = "AggregatableReportConverterMainTest",
    srcs = [
        "java/com/google/measurement/util/AggregatableReportConverterMain.java",
        "javatests/com/google/measurement/util/AggregatableReportConverterMainTest.java",
    ],
    deps = [
        ":AggregatableReportConverterLib",
        ":org_json",
        "@maven//:co_nstant_in_cbor",
        "@maven//:com_google_truth_truth",
        "@maven//:org_apache_avro_avro",
    ],
)

java_binary(
    name = "AggregatableReportConverter",
    srcs = ["java/com/google/measurement/util/AggregatableReportConverterMain.java"],
    main_class = "com.google.measurement.util.AggregatableReportConverterMain",
    visibility = ["//python:__pkg__"],
    deps = [
        ":AggregatableReportConverterLib",
        ":org_json",
        "@maven//:org_apache_avro_avro",
    ],
)

java_binary(
    name = "PipelineRunner",
    srcs = [
        "java/com/google/measurement/pipeline/PipelineRunner.java",
    ],
    data = [
        ":AggregatableReportConverter_deploy.jar",
        ":LocalTestingTool",
        ":MockRunner_deploy.jar",
        ":testdata",
    ],
    main_class = "com.google.measurement.pipeline.PipelineRunner",
    visibility = ["//python:__pkg__"],
    deps = [
        ":AggregatableReportConverter",
        ":MockRunner",
        ":PipelineLib",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_library(
    name = "PipelineLib",
    srcs = glob(
        [
            "java/com/google/measurement/pipeline/**/*",
        ],
        exclude = ["java/com/google/measurement/pipeline/testdata/**/*"],
    ),
    deps = [
        ":AggregatableReportConverterLib",
        ":org_json",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
    ],
)

java_test(
    name = "FileInputTransformTest",
    srcs = [
        "javatests/com/google/measurement/pipeline/FileInputTransformTest.java",
    ],
    deps = [
        ":PipelineLib",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "FileOutputTransformTest",
    srcs = [
        "javatests/com/google/measurement/pipeline/FileOutputTransformTest.java",
    ],
    deps = [
        ":PipelineLib",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "PipelineRunnerTest",
    srcs = ["javatests/com/google/measurement/pipeline/PipelineRunnerTest.java"],
    deps = [
        ":PipelineLib",
        ":PipelineRunner",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "ExtractJsonFieldTransformTest",
    srcs = ["javatests/com/google/measurement/pipeline/ExtractJsonFieldTransformTest.java"],
    deps = [
        ":PipelineLib",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "GroupToJsonArrayTransformTest",
    srcs = ["javatests/com/google/measurement/pipeline/GroupToJsonArrayTransformTest.java"],
    deps = [
        ":PipelineLib",
        ":org_json",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "KeyByDestinationAndDayTransformTest",
    srcs = ["javatests/com/google/measurement/pipeline/KeyByDestinationAndDayTransformTest.java"],
    deps = [
        ":PipelineLib",
        ":org_json",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "JarExecutorTransformTest",
    srcs = ["javatests/com/google/measurement/pipeline/JarExecutorTransformTest.java"],
    data = [":Echo_deploy.jar"],
    deps = [
        ":Echo_deploy.jar",
        ":PipelineLib",
        "@bazel_tools//tools/runfiles:java-runfiles",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_test(
    name = "AggregationServiceTransformTest",
    srcs = ["javatests/com/google/measurement/pipeline/AggregationServiceTransformTest.java"],
    data = [":Echo_deploy.jar"],
    deps = [
        ":Echo_deploy.jar",
        ":PipelineLib",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:org_apache_beam_beam_runners_direct_java",
        "@maven//:org_apache_beam_beam_sdks_java_core",
        "@maven//:org_hamcrest_hamcrest_core",
        "@maven//:org_hamcrest_hamcrest_library",
    ],
)

java_binary(
    name = "Echo",
    srcs = ["javatests/com/google/measurement/pipeline/Echo.java"],
    main_class = "com.google.measurement.pipeline.Echo",
)

# Built from v1.0.2 of aggregate-service.
java_import(
    name = "AggregationWorker",
    jars = [
        "lib/LocalTestingTool_1.0.2.jar",
    ],
)

filegroup(
    name = "LocalTestingTool",
    srcs = ["lib/LocalTestingTool_1.0.2.jar"],
    visibility = ["//python:__pkg__"],
)

filegroup(
    name = "domain",
    srcs = glob(["domain/**/*"]),
    visibility = ["//python:__pkg__"],
)

filegroup(
    name = "testdata",
    srcs = glob(["java/com/google/measurement/pipeline/testdata/**/*"]),
    visibility = ["//python:__pkg__"],
)

filegroup(
    name = "config",
    srcs = glob(["config/*"]),
    visibility = ["//python:__pkg__"],
)

filegroup(
    name = "reports",
    srcs = glob(["java/com/google/measurement/util/reports.avsc"]),
)
