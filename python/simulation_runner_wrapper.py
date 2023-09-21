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

""" Entrypoint to the Simulator.

This module serves as the main entry point to the Simulator, written in Java.
A py4j gateway is instantiated and commands are proxied to the JVM through
that gateway.

  Typical usage example:

  simulation_runner = SimulationRunnerWrapper()
  simulation_runner.run(simulation_config)
"""

from py4j.java_gateway import JavaGateway
from py4j.java_collections import ListConverter
import os
import sys

from simulation_config import SimulationConfig
from constants import PY4J_VERSION, PY4J_GATEWAY_PORT


def get_bin_dir() -> str:
  file_dir = os.path.dirname(__file__)
  # BUILD_WORKSPACE_DIRECTORY is an environment variable provided by Bazel
  # for the purposes of having a consistent reference point in the
  # directory tree. When this script is referenced from another script
  # that's not executed with Bazel, that environment variable does not
  # exist, hence we use the directory that this file resides in as a
  # reference point.
  workspace_dir = os.environ.get('BUILD_WORKSPACE_DIRECTORY',
                                 f'{file_dir}/..')
  bin_dir = f"{workspace_dir}/bazel-out/k8-fastbuild/bin"

  return bin_dir


def get_default_py4j_jar_path(bin_dir: str) -> str:
  py4j_jar_path = f"{bin_dir}/python/" \
                  f"main.runfiles/deps/pypi__py4j/" \
                  f"py4j-{PY4J_VERSION}.data/data/share/py4j/" \
                  f"py4j{PY4J_VERSION}.jar"

  return py4j_jar_path


class SimulationRunnerWrapper:
  """ Class for instantiating the gateway between python and Java.

  This class will be responsible for instantiating the py4j gateway
  that allows other modules, as well as the command line, to pass arguments
  from python to Java. There are two critical inputs to py4j: the location of
  the py4j JAR and the location of the SimulationRunner JAR. The
  SimulationRunner JAR supplies the JVM with classes from the Java project. The
  constructor allows consumers to specify these values, but will provide
  defaults assuming 2 use cases: 1.) The wrapper is consumed from a script
  executed from the command line with `bazel run` or 2.) the wrapper is
  imported from another script that will have knowledge of this project's
  directory structure.

  Attributes:
    gateway: The py4j gateway.
    runner: The python object that represents the Java SimulationRunner.

  """

  def __init__(self,
      py4j_jar_path: str = None,
      classpath: str = None):
    """Instantiate the gateway and the SimulationRunner"""
    bin_dir = get_bin_dir()
    if py4j_jar_path is None:
      py4j_jar_path = get_default_py4j_jar_path(bin_dir)
    if classpath is None:
      classpath = f"{bin_dir}/SimulationRunner_deploy.jar"

    # The -Xlog:os+container=error java opt is necessary because of Java's
    # behavior when executing inside a container. Java will print warning
    # messages to STDOUT instead of STDERR thereby breaking py4j processes that
    # read information from STDOUT.
    self.gateway = JavaGateway.launch_gateway(jarpath=py4j_jar_path,
                                              classpath=classpath,
                                              port=PY4J_GATEWAY_PORT,
                                              javaopts=
                                              ["-Xlog:os+container=error"],
                                              die_on_exit=True,
                                              redirect_stdout=sys.stdout,
                                              redirect_stderr=sys.stderr)
    self.runner = self.gateway.jvm.com.google.measurement.SimulationRunner()

  def run(self, simulation_config: SimulationConfig = None) -> bool:
    """Forwards arguments to the SimulationRunner

    Args:
      simulation_config: An instance of SimulationConfig.

    Returns:
      True if arguments had no errors and simulation was successful.
    """


    args = []
    if simulation_config is not None:
      args += simulation_config.to_java_args()

    # The JVM has no understanding of python objects, so `args` must be
    # converted to a collection that the JVM does understand.
    java_args = ListConverter().convert(args, self.gateway._gateway_client)
    success = self.runner.run(java_args)

    return success
