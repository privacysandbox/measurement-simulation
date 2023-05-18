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
import datetime
import argparse
from simulation_runner_wrapper import SimulationRunnerWrapper
from simulation_config import SimulationConfig


def main():
  parser = argparse.ArgumentParser(description='Arguments to the Simulation.')
  parser.add_argument("--source_start_date",
                      type=datetime.date.fromisoformat,
                      dest="source_start_date")
  parser.add_argument("--source_end_date",
                      type=datetime.date.fromisoformat,
                      dest="source_end_date")
  parser.add_argument("--trigger_start_date",
                      type=datetime.date.fromisoformat,
                      dest="trigger_start_date")
  parser.add_argument("--trigger_end_date",
                      type=datetime.date.fromisoformat,
                      dest="trigger_end_date")
  parser.add_argument("--input_directory",
                      dest="input_directory")
  parser.add_argument("--output_directory",
                      dest="output_directory")
  parser.add_argument("--attribution_source_file_name",
                      default="attribution_source.json",
                      dest="attribution_source_file_name")
  parser.add_argument("--trigger_file_name",
                      default="trigger.json",
                      dest="trigger_file_name")

  args = vars(parser.parse_args())
  simulation_config = SimulationConfig(input_directory=
                                       args['input_directory'],
                                       output_directory=
                                       args['output_directory'],
                                       source_start_date=
                                       args['source_start_date'],
                                       source_end_date=
                                       args['source_end_date'],
                                       trigger_start_date=
                                       args['trigger_start_date'],
                                       trigger_end_date=
                                       args['trigger_end_date'],
                                       attribution_source_file_name=
                                       args['attribution_source_file_name'],
                                       trigger_file_name=
                                       args['trigger_file_name'],
                                       )
  runner = SimulationRunnerWrapper()
  runner.run(simulation_config=simulation_config)


if __name__ == "__main__":
  main()
