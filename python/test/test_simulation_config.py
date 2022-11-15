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
from datetime import datetime
from pyfakefs.fake_filesystem_unittest import TestCase
from python.simulation_config import SimulationConfig

input_dir = "/input"
domain_avro_file = "/input/domain.avro"
output_dir = "/output"
isoformat = "%Y-%m-%d"


def build_config(input_directory=input_dir, domain_avro_file=domain_avro_file,
    output_directory=output_dir,
    source_start_date=datetime.strptime("2022-01-15", isoformat).date(),
    source_end_date=datetime.strptime("2022-01-15", isoformat).date(),
    trigger_start_date=datetime.strptime("2022-01-15", isoformat).date(),
    trigger_end_date=datetime.strptime("2022-01-15", isoformat).date(),
    attribution_source_file_name="attribution_source.json",
    trigger_file_name="trigger.json"
):
  simulation_config = SimulationConfig(
      input_directory=input_directory,
      domain_avro_file=domain_avro_file,
      output_directory=output_directory,
      source_start_date=source_start_date,
      source_end_date=source_end_date,
      trigger_start_date=trigger_start_date,
      trigger_end_date=trigger_end_date,
      attribution_source_file_name=attribution_source_file_name,
      trigger_file_name=trigger_file_name)

  return simulation_config


class TestSimulationConfig(TestCase):
  def setUp(self) -> None:
    self.setUpPyfakefs()
    self.fs.create_dir(input_dir)
    self.fs.create_dir(output_dir)

  def test_config_is_valid(self):
    simulation_config = build_config()
    self.assertEqual(len(simulation_config.errors), 0)

  def test_source_start_date_missing(self):
    with self.assertRaises(ValueError) as e:
      build_config(source_start_date="")

    self.assertEqual("Argument source_start_date with value  is "
                     "not a valid date", str(e.exception))

  def test_source_start_date_is_none(self):
    with self.assertRaises(ValueError) as e:
      build_config(source_start_date=None)

    self.assertEqual("Please provide a valid value for Argument "
                     "source_start_date", str(e.exception))

  def test_multiple_bad_dates(self):
    with self.assertRaises(ValueError) as e:
      build_config(source_start_date="", source_end_date=None)

    error1 = "Argument source_start_date with value  is not a valid date"
    error2 = "Please provide a valid value for Argument source_end_date"

    self.assertEqual("\n".join([error1, error2]), str(e.exception))

  def test_attribution_source_file_name_wrong_filetype(self):
    with self.assertRaises(ValueError) as e:
      build_config(attribution_source_file_name="test.txt")

    self.assertEqual("Argument attribution_source_file_name is not in an "
                     "acceptable format. Acceptable format is json"
                     , str(e.exception))

  def test_attribution_source_file_name_wrong_and_date_wrong(self):
    with self.assertRaises(ValueError) as e:
      build_config(source_start_date="",
                   attribution_source_file_name="test.txt")
    error1 = "Argument source_start_date with value  is not a valid date"
    error2 = "Argument attribution_source_file_name is not in an " \
             "acceptable format. Acceptable format is json"

    self.assertEqual("\n".join([error1, error2]), str(e.exception))

  def test_input_directory_not_absolute(self):
    with self.assertRaises(ValueError) as e:
      build_config(input_directory="input/")

    self.assertEqual("Argument input_directory is not an absolute file path",
                     str(e.exception))

  def test_input_directory_not_exists(self):
    with self.assertRaises(ValueError) as e:
      build_config(input_directory="/nonexistent")

    self.assertEqual("Argument input_directory with directory /nonexistent "
                     "does not exist", str(e.exception))


if __name__ == '__main__':
  unittest.main()
