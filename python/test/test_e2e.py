# Copyright 2024 Google LLC
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
"""End-to-end tests for the RBM simulation pipeline."""

import os
import pathlib
import shutil
import tempfile
import unittest

from pipeline_config import PipelineConfig
from pipeline_runner_wrapper import PipelineRunnerWrapper


class TestEndToEnd(unittest.TestCase):
  """Tests for the end-to-end pipeline."""

  def setUp(self):
    super().setUp()
    self.temp_dir = tempfile.mkdtemp()

  def tearDown(self):
    super().tearDown()
    shutil.rmtree(self.temp_dir)

  def test_run_pipeline(self):
    """Tests that the pipeline runs without errors."""
    workspace_dir = os.environ.get(
        "BUILD_WORKSPACE_DIRECTORY",
        str(pathlib.Path(__file__).parent.parent.parent))
    input_dir = f"{workspace_dir}/java/com/google/measurement/pipeline/testdata"
    event_reports_output_directory = f"{self.temp_dir}/event"
    aggregatable_reports_output_directory = f"{self.temp_dir}/aggregatable"
    aggregation_results_directory = f"{self.temp_dir}/aggregation"

    os.mkdir(event_reports_output_directory)
    os.mkdir(aggregatable_reports_output_directory)
    os.mkdir(aggregation_results_directory)

    pipeline_config_instance = PipelineConfig(
        input_directory=input_dir,
        event_reports_output_directory=event_reports_output_directory,
        aggregatable_reports_output_directory=
        aggregatable_reports_output_directory,
        aggregation_results_directory=aggregation_results_directory)

    runner = PipelineRunnerWrapper(
        pipeline_config_instance)
    runner.run()

    self.assertGreater(len(os.listdir(event_reports_output_directory)), 0)
    self.assertGreater(
        len(os.listdir(aggregatable_reports_output_directory)), 0)
    self.assertGreater(len(os.listdir(aggregation_results_directory)), 0)


if __name__ == "__main__":
  unittest.main()
