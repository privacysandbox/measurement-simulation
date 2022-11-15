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

from datetime import datetime
import unittest
import os

from python.constants import PY4J_VERSION
from python.simulation_config import SimulationConfig
from python.simulation_runner_wrapper import SimulationRunnerWrapper

isoformat = "%Y-%m-%d"


class TestEndToEnd(unittest.TestCase):
  def test_run_simulation(self):
    test_src = f"{os.environ['TEST_SRCDIR']}/__main__"
    py4j_jar_bin = f"{test_src}/external/deps/pypi__py4j/" \
                   f"py4j-{PY4J_VERSION}.data/data/share/py4j/" \
                   f"py4j{PY4J_VERSION}.jar"
    classpath = f"{test_src}/SimulationRunner_deploy.jar"

    runner = SimulationRunnerWrapper(py4j_jar_path=py4j_jar_bin,
                                     classpath=classpath)
    input_dir = f"{test_src}/testdata"
    domain_avro_file = f"{test_src}/testdata/domain.avro"
    simulation_config = SimulationConfig(
        input_directory=input_dir,
        domain_avro_file=domain_avro_file,
        source_start_date=datetime.strptime("2022-01-15", isoformat).date(),
        source_end_date=datetime.strptime("2022-01-15", isoformat).date(),
        trigger_start_date=datetime.strptime("2022-01-15", isoformat).date(),
        trigger_end_date=datetime.strptime("2022-01-15", isoformat).date(),
    )

    success = runner.run(simulation_config)
    self.assertEqual(success, True)


if __name__ == '__main__':
  unittest.main()
