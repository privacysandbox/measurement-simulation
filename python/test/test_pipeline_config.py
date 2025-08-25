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

"""Tests for pipeline_config.py."""

import unittest

from pyfakefs.fake_filesystem_unittest import TestCase
from python.pipeline_config import _get_bin_dir
from python.pipeline_config import PipelineConfig


input_dir = "/input"
event_reports_output_dir = "/output/event_reports"
aggregatable_reports_output_dir = "/output/aggregatable_reports"
aggregation_results_dir = "/output/aggregation_results"


def build_config(
    input_directory=input_dir,
    event_reports_output_directory=event_reports_output_dir,
    aggregatable_reports_output_directory=aggregatable_reports_output_dir,
    aggregation_results_directory=aggregation_results_dir):
  """Builds a PipelineConfig object for testing."""
  pipeline_config = PipelineConfig(
      input_directory=input_directory,
      event_reports_output_directory=event_reports_output_directory,
      aggregatable_reports_output_directory=
      aggregatable_reports_output_directory,
      aggregation_results_directory=aggregation_results_directory)

  return pipeline_config


class TestPipelineConfig(TestCase):
  """Tests for the PipelineConfig class."""

  def setUp(self) -> None:
    """Sets up the test environment."""
    self.setUpPyfakefs()
    self.fs.create_dir(input_dir)
    self.fs.create_dir(event_reports_output_dir)
    self.fs.create_dir(aggregatable_reports_output_dir)
    self.fs.create_dir(aggregation_results_dir)

  def test_config_is_valid(self):
    """Tests that a valid config is created with no errors."""
    pipeline_config = build_config()
    self.assertFalse(pipeline_config.errors)

  def test_input_directory_not_absolute(self):
    """Tests that a ValueError is raised for a non-absolute input directory."""
    with self.assertRaises(ValueError) as e:
      build_config(input_directory="input/")

    self.assertEqual("Argument input_directory is not an absolute file path",
                     str(e.exception).strip())

  def test_input_directory_not_exists(self):
    """Tests that a ValueError is raised for a nonexistent input directory."""
    with self.assertRaises(ValueError) as e:
      build_config(input_directory="/nonexistent")

    self.assertEqual(
        "Argument input_directory with directory /nonexistent does not exist",
        str(e.exception).strip())

  def test_to_java_args(self):
    """Tests that the to_java_args method returns the correct arguments."""
    pipeline_config = build_config()
    java_args = pipeline_config.to_java_args()

    self.assertIn(f"--inputDirectory={input_dir}", java_args)
    self.assertIn(
        f"--eventReportsOutputDirectory={event_reports_output_dir}",
        java_args)
    self.assertIn(
        f"--aggregatableReportsOutputDirectory="
        f"{aggregatable_reports_output_dir}", java_args)
    self.assertIn(
        f"--aggregationResultsDirectory={aggregation_results_dir}",
        java_args)

  def test_bin_directory_exists(self):
    """Tests that the bin_directory attribute is set correctly."""
    pipeline_config = build_config()
    self.assertIsNotNone(pipeline_config.bin_directory)
    self.assertEqual(pipeline_config.bin_directory, _get_bin_dir())


if __name__ == "__main__":
  unittest.main()

