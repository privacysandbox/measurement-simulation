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

"""Tests for pipeline_runner_wrapper.py."""

import unittest
from unittest import mock

from python.pipeline_config import PipelineConfig
from python.pipeline_runner_wrapper import PipelineRunnerWrapper


class TestPipelineRunnerWrapper(unittest.TestCase):
  """Tests for the PipelineRunnerWrapper class."""

  def test_init(self):
    """Tests that the __init__ method sets the classpath correctly."""
    mock_config = mock.MagicMock(spec=PipelineConfig)
    mock_config.bin_directory = "/path/to/bin"

    wrapper = PipelineRunnerWrapper(mock_config)

    self.assertEqual(wrapper.classpath,
                     "/path/to/bin/PipelineRunner_deploy.jar")

  @mock.patch("python.pipeline_runner_wrapper._get_pipeline_runner")
  @mock.patch.object(PipelineRunnerWrapper, "_start_jvm")
  def test_run(self, mock_start_jvm, mock_get_runner):
    """Tests that the run method calls the correct methods."""
    mock_config = mock.MagicMock(spec=PipelineConfig)
    mock_config.bin_directory = "/path/to/bin"
    mock_config.to_java_args.return_value = ["--arg1", "--arg2"]

    mock_runner = mock.MagicMock()
    mock_get_runner.return_value = mock_runner

    wrapper = PipelineRunnerWrapper(mock_config)
    wrapper.run()

    mock_start_jvm.assert_called_once()
    mock_get_runner.assert_called_once()
    mock_runner.main.assert_called_once_with(["--arg1", "--arg2"])


if __name__ == "__main__":
  unittest.main()
