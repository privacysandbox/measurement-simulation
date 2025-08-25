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

"""Entrypoint to the python wrapper when executed from the command line.

Note: users should not execute this script directly with python,
  i.e., `python main.py`
but should instead execute with Bazel,
  i.e., `bazel run main -- --flags`
"""

import argparse

from pipeline_config import PipelineConfig
from pipeline_runner_wrapper import PipelineRunnerWrapper


def main():
  """Parses command line arguments and runs the pipeline."""

  parser = argparse.ArgumentParser(description="Arguments to the Pipeline.")
  parser.add_argument("--input_directory",
                      dest="input_directory")
  parser.add_argument("--event_reports_output_directory",
                      dest="event_reports_output_directory")
  parser.add_argument("--aggregatable_reports_output_directory",
                      dest="aggregatable_reports_output_directory")
  parser.add_argument("--aggregation_results_directory",
                      dest="aggregation_results_directory")

  args = vars(parser.parse_args())

  pipeline_config = PipelineConfig(
      input_directory=args["input_directory"],
      event_reports_output_directory=args["event_reports_output_directory"],
      aggregatable_reports_output_directory=
      args["aggregatable_reports_output_directory"],
      aggregation_results_directory=args["aggregation_results_directory"])

  runner = PipelineRunnerWrapper(pipeline_config)
  runner.run()


if __name__ == "__main__":
  main()
