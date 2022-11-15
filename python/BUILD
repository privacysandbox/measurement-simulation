load("@deps//:requirements.bzl", "requirement")
load(
    "@rules_python//python:defs.bzl",
    "py_binary",
    "py_library",
    "py_test",
)

py_library(
    name = "params",
    srcs = [
        "simulation_config.py",
    ],
)

py_library(
    name = "constants",
    srcs = ["constants.py"],
)

py_library(
    name = "simulation_runner_wrapper",
    srcs = ["simulation_runner_wrapper.py"],
    data = ["//:SimulationRunner_deploy.jar"],
    imports = ["."],
    deps = [
        ":constants",
        ":params",
        requirement("py4j"),
    ],
)

py_binary(
    name = "main",
    srcs = ["main.py"],
    deps = [":simulation_runner_wrapper"],
)

py_test(
    name = "test_simulation_config",
    srcs = ["test/test_simulation_config.py"],
    deps = [
        ":params",
        requirement("pyfakefs"),
    ],
)

py_test(
    name = "test_simulation_runner_wrapper",
    srcs = ["test/test_simulation_runner_wrapper.py"],
    deps = [
        ":constants",
        ":simulation_runner_wrapper",
        requirement("py4j"),
    ],
)

py_test(
    name = "test_e2e",
    srcs = ["test/test_e2e.py"],
    data = [
        "//:config",
        "//:testdata",
    ],
    deps = [
        ":constants",
        ":params",
        ":simulation_runner_wrapper",
    ],
)
