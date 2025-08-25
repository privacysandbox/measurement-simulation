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

"""Entrypoint to the Pipeline.

This module serves as the main entry point to the Pipeline, written in Java.
A jpype JVM is instantiated and commands are proxied to the JVM.

Typical usage example:
pipeline_runner = PipelineRunnerWrapper(pipeline_config)
pipeline_runner.run()
"""

import jpype
import jpype.imports
from pipeline_config import PipelineConfig


def _get_pipeline_runner():
  """Gets the Java PipelineRunner instance.

  Returns:
    A Java PipelineRunner instance.
  """
  from com.google.measurement.pipeline import PipelineRunner

  return PipelineRunner()


class PipelineRunnerWrapper:
  """Class for instantiating the JVM and running the pipeline.

  This class is responsible for starting the JVM and passing arguments
  from python to the Java PipelineRunner.
  Attributes:
    config: A PipelineConfig object containing the pipeline configuration.
    classpath: The classpath for the JVM.
  """

  def __init__(self, pipeline_config: PipelineConfig):
    """Initializes the PipelineRunnerWrapper.

    Args:
      pipeline_config: A PipelineConfig object containing the pipeline
        configuration.
    """

    self.config = pipeline_config

    self.classpath = (
        f"{pipeline_config.bin_directory}/PipelineRunner_deploy.jar")

  def _start_jvm(self):
    """Starts the JVM."""
    # The -Xlog:os+container=error java opt is necessary because of Java's
    # behavior when executing inside a container. Java will print warning
    # messages to STDOUT instead of STDERR, which could break processes that
    # read information from STDOUT.
    jpype.startJVM(
        "-Xlog:os+container=error",
        classpath=[self.classpath])

  def run(self) -> None:
    """Runs the Java PipelineRunner.

    Returns:
      True if the pipeline runs successfully, False otherwise.
    """

    self._start_jvm()

    _get_pipeline_runner().main(self.config.to_java_args())
