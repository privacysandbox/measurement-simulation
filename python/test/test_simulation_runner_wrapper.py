# Copyright 2022 Google LLC
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

import unittest
import os
import sys
from python.simulation_runner_wrapper import SimulationRunnerWrapper
from python.constants import PY4J_VERSION, PY4J_GATEWAY_PORT
from unittest.mock import patch


@patch("python.simulation_runner_wrapper.JavaGateway.launch_gateway")
class TestSimulationRunnerWrapper(unittest.TestCase):
  def test_init_with_arguments(self, mock_gateway):
    SimulationRunnerWrapper(py4j_jar_path="py4j_jar_path",
                            classpath="classpath")

    mock_gateway.assert_called_with(jarpath='py4j_jar_path',
                                    classpath='classpath',
                                    port=PY4J_GATEWAY_PORT,
                                    javaopts=["-Xlog:os+container=error"],
                                    die_on_exit=True,
                                    redirect_stdout=sys.stdout,
                                    redirect_stderr=sys.stderr)

  def test_init_without_arguments(self, mock_gateway):
    mock_workspace_dir = "workspace"
    os.environ["BUILD_WORKSPACE_DIRECTORY"] = mock_workspace_dir
    py4j_jar_path = f'{mock_workspace_dir}/bazel-out/k8-fastbuild/bin/python' \
                    f'/main.runfiles/deps/pypi__py4j/py4j-{PY4J_VERSION}.data' \
                    f'/data/share/py4j/py4j{PY4J_VERSION}.jar'
    classpath = f"{mock_workspace_dir}/bazel-out/k8-fastbuild/bin" \
                f"/SimulationRunner_deploy.jar"
    SimulationRunnerWrapper()

    mock_gateway.assert_called_with(jarpath=py4j_jar_path,
                                    classpath=classpath,
                                    port=PY4J_GATEWAY_PORT,
                                    javaopts=["-Xlog:os+container=error"],
                                    die_on_exit=True,
                                    redirect_stdout=sys.stdout,
                                    redirect_stderr=sys.stderr)

  def test_init_without_py4j_jarpath(self, mock_gateway):
    mock_workspace_dir = "workspace"
    os.environ["BUILD_WORKSPACE_DIRECTORY"] = mock_workspace_dir
    py4j_jar_path = f'{mock_workspace_dir}/bazel-out/k8-fastbuild/bin/python' \
                    f'/main.runfiles/deps/pypi__py4j/py4j-{PY4J_VERSION}.data' \
                    f'/data/share/py4j/py4j{PY4J_VERSION}.jar'

    SimulationRunnerWrapper(classpath="classpath")

    mock_gateway.assert_called_with(jarpath=py4j_jar_path,
                                    classpath="classpath",
                                    port=PY4J_GATEWAY_PORT,
                                    javaopts=["-Xlog:os+container=error"],
                                    die_on_exit=True,
                                    redirect_stdout=sys.stdout,
                                    redirect_stderr=sys.stderr)

  def test_init_without_classpath(self, mock_gateway):
    mock_workspace_dir = "workspace"
    os.environ["BUILD_WORKSPACE_DIRECTORY"] = mock_workspace_dir
    classpath = f"{mock_workspace_dir}/bazel-out/k8-fastbuild/bin" \
                f"/SimulationRunner_deploy.jar"

    SimulationRunnerWrapper(py4j_jar_path="py4j_jar_path")

    mock_gateway.assert_called_with(jarpath="py4j_jar_path",
                                    classpath=classpath,
                                    port=PY4J_GATEWAY_PORT,
                                    javaopts=["-Xlog:os+container=error"],
                                    die_on_exit=True,
                                    redirect_stdout=sys.stdout,
                                    redirect_stderr=sys.stderr)


if __name__ == '__main__':
  unittest.main()
