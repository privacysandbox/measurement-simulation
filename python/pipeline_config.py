# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# you may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Class for handling configuration arguments to the Pipeline Runner.

Arguments specific to the configuration of the PipelineRunner are set here.
Validation occurs at assignment and errors are collected for all assigned
attributes.

Typical usage example:
pipeline_config = PipelineConfig(input_directory="/inputs/")
"""

import os
from typing import List, Optional


DIRECTORY_ARGS = {
    "bin": "bin_directory",
    "input": "input_directory",
    "event_reports": "event_reports_output_directory",
    "aggregatable_reports": "aggregatable_reports_output_directory",
    "aggregation_results": "aggregation_results_directory"
}


def _get_bin_dir() -> str:
  """Gets the binary directory for the current environment.

  Returns:
    The path to the binary directory.
  """
  file_dir = os.path.dirname(__file__)
  # BUILD_WORKSPACE_DIRECTORY is an environment variable provided by Bazel
  # for the purposes of having a consistent reference point in the
  # directory tree. When this script is referenced from another script
  # that's not executed with Bazel, that environment variable does not
  # exist, hence we use the directory that this file resides in as a
  # reference point.
  workspace_dir = os.environ.get("BUILD_WORKSPACE_DIRECTORY", f"{file_dir}/..")
  if "TEST_SRCDIR" in os.environ:
    return workspace_dir
  return f"{workspace_dir}/bazel-out/k8-fastbuild/bin"


class PipelineConfig:
  """Class for handling configuration arguments to the Pipeline Runner.

  Ingests and validates arguments. The following are expected:
    All directories are absolute paths.
  Also contains logic for mapping attributes to command line strings that the
  Java PipelineRunner expects.
  Attributes:
    bin_directory: The directory containing the PipelineRunner binary.
    input_directory: The top level directory of where the
    PipelineRunner will get its inputs.
    event_reports_output_directory: The directory that will hold event reports.
    aggregatable_reports_output_directory: The directory that will hold
    aggregatable reports.
    aggregation_results_directory: The directory that will hold aggregation
    results.
    errors: A list of validation errors.
  """

  def __init__(self, **kwargs):
    """Initializes the PipelineConfig and validates arguments.

    Args:
      **kwargs: A dictionary of arguments.
    Raises:
      ValueError: If any of the directory arguments are invalid.
    """

    self.errors = []

    self.bin_directory: str = _get_bin_dir()
    self.input_directory = self.validate_directory(
        kwargs.get(DIRECTORY_ARGS["input"], ""), DIRECTORY_ARGS["input"])
    self.event_reports_output_directory = self.validate_directory(
        kwargs.get(DIRECTORY_ARGS["event_reports"], ""),
        DIRECTORY_ARGS["event_reports"])
    self.aggregatable_reports_output_directory = self.validate_directory(
        kwargs.get(DIRECTORY_ARGS["aggregatable_reports"], ""),
        DIRECTORY_ARGS["aggregatable_reports"])
    self.aggregation_results_directory = self.validate_directory(
        kwargs.get(DIRECTORY_ARGS["aggregation_results"], ""),
        DIRECTORY_ARGS["aggregation_results"])

    if self.errors:
      errors = "\n" + "\n".join(list(self.errors))
      raise ValueError(errors)

  def validate_directory(self, path: str, arg: str) -> Optional[str]:
    """Validate passed directory string.

    Ensure paths are absolute and exist.
    Args:
      path: The provided path
      arg: The directory argument name
    Returns:
      The provided path if validation passes, otherwise None.
    """

    if path is None or not os.path.isabs(path):
      self.errors.append(f"Argument {arg} is not an absolute file path")
      return
    if not os.path.isdir(path):
      self.errors.append(f"Argument {arg} with directory {path}"
                         f" does not exist")
      return

    return path

  def to_java_args(self) -> List[str]:
    """Convert class attributes to command line flags.

    Command line flags are defined in
    java/com/google/measurement/pipeline/PipelineOptions.java.
    Returns:
      List of strings, each element is a flag with its value appended.
    """
    runfiles_directory = (
        self.bin_directory if "TEST_SRCDIR" in os.environ
        else f"{self.bin_directory}/PipelineRunner.runfiles/_main")

    java_args = [
        f"--runfilesDirectory={runfiles_directory}",
        f"--inputDirectory={self.input_directory}",
        f"--eventReportsOutputDirectory={self.event_reports_output_directory}",
        f"--aggregatableReportsOutputDirectory={self.aggregatable_reports_output_directory}",
        f"--aggregationResultsDirectory={self.aggregation_results_directory}"
    ]

    return java_args
