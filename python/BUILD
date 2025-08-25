# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@deps//:requirements.bzl", "requirement")
load(
    "@rules_python//python:defs.bzl",
    "py_binary",
    "py_library",
    "py_test",
)

py_library(
    name = "pipeline_config_lib",
    srcs = [
        "pipeline_config.py",
    ],
)

py_library(
    name = "pipeline_runner_wrapper",
    srcs = ["pipeline_runner_wrapper.py"],
    data = [
        "//:PipelineRunner_deploy.jar",
    ],
    imports = ["."],
    deps = [
        ":pipeline_config_lib",
        requirement("jpype1"),
    ],
)

py_binary(
    name = "main",
    srcs = ["main.py"],
    deps = [":pipeline_runner_wrapper"],
)

py_test(
    name = "test_pipeline_config",
    srcs = ["test/test_pipeline_config.py"],
    deps = [
        ":pipeline_config_lib",
        requirement("pyfakefs"),
    ],
)

py_test(
    name = "test_pipeline_runner_wrapper",
    srcs = ["test/test_pipeline_runner_wrapper.py"],
    deps = [
        ":pipeline_runner_wrapper",
        requirement("pyfakefs"),
    ],
)

py_test(
    name = "test_e2e",
    srcs = ["test/test_e2e.py"],
    data = [
        "//:AggregatableReportConverter_deploy.jar",
        "//:LocalTestingTool",
        "//:MockRunner_deploy.jar",
        "//:PipelineRunner_deploy.jar",
        "//:testdata",
    ],
    deps = [
        ":pipeline_config_lib",
        ":pipeline_runner_wrapper",
        requirement("pyfakefs"),
    ],
)
