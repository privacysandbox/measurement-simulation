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

"""Class for handling configuration arguments to the Simulation Runner.

Arguments specific to the configuration of the SimulationRunner are set here.
Validation occurs at assignment and errors are collected for all assigned
attributes.

  Typical usage example:

  simulation_config = SimulationConfig(input_directory="/inputs/")
"""

from datetime import date
import os.path
import re
from typing import List, Optional
import json

pattern = re.compile(r".+\.(json)", re.IGNORECASE)
isoformat = "%Y-%m-%d"


class SimulationConfig:
  """Class for handling configuration arguments to the Simulation Runner.

  Ingests and validates arguments. The following are expected:
    All directories are absolute paths.
    All dates are datetime.date objects.
    File format is json.

  Also contains logic for mapping attributes to command line strings that the
  Java SimulationRunner expects.

  Attributes:
    input_directory: The top level directory of where the
    SimulationRunner will get its inputs (Triggers and Sources). Starting from
    this directory the structure should follow year/month/day,
    i.e. 2022/01/15. Must be an absolute path.
    domain_avro_file: The file path for the input domain file that would be
    used for Aggregation.
    source_start_date: The first date of attribution source events.
    source_end_date: The last date of attribution source events, should come on
    or after source_start_date.
    trigger_start_date: The first date of trigger events.
    trigger_end_date: The last date of trigger events, should come on or
    after trigger_start_date.
    output_directory: The directory that will hold results from the simulation.
    attribution_source_file_name: The file name that will be used to identify
    the files that hold attribution source events. All attribution source event
    files at the bottom most level of the input directory must have this
    filename.
    trigger_file_name: The file name that will be used to identify
    the files that hold trigger events. All trigger event files at the
    bottom most level of the input directory must have this filename.
  """

  def __init__(self,
      input_directory: str,
      domain_avro_file: str,
      source_start_date: date,
      source_end_date: date,
      trigger_start_date: date,
      trigger_end_date: date,
      output_directory: str = None,
      attribution_source_file_name: str = "attribution_source.json",
      trigger_file_name: str = "trigger.json"):
    """Perform validation and populate attributes"""
    self.errors = []

    if input_directory is None:
      self.errors.append("Please provide a valid value for Argument "
                         "input_directory")
    else:
      self.input_directory = self.validate_directory(input_directory,
                                                     "input_directory")
    if domain_avro_file is None:
      self.errors.append("Please provide a valid value for Argument "
                         "domain_avro_file")
    else:
      self.domain_avro_file = self.validate_filepath(domain_avro_file,
                                                     "domain_avro_file")
    if output_directory is not None:
      self.output_directory = self.validate_directory(output_directory,
                                                      "output_directory")
    else:
      self.output_directory = output_directory
    self.source_start_date = self.validate_date(source_start_date,
                                                "source_start_date")
    self.source_end_date = self.validate_date(source_end_date,
                                              "source_end_date")
    self.trigger_start_date = self.validate_date(trigger_start_date,
                                                 "trigger_start_date")
    self.trigger_end_date = self.validate_date(trigger_end_date,
                                               "trigger_end_date")
    self.attribution_source_file_name = self.validate_file_name(
        attribution_source_file_name, "attribution_source_file_name")
    self.trigger_file_name = self.validate_file_name(trigger_file_name,
                                                     "trigger_file_name")
    if len(self.errors) > 0:
      errors = "\n".join(list(self.errors))
      raise ValueError(errors)

  def validate_directory(self, directory: str, arg: str) -> Optional[str]:
    """ Validate passed directory string

    Ensure paths are absolute and exist.

    Args:
      directory: The path to validate.
      arg: The name of the attribute that the value will be assigned to.
      Used for reporting validation errors.

    Returns:
      `directory` if validation passes, otherwise None.
    """
    if not os.path.isabs(directory):
      self.errors.append(f"Argument {arg} is not an absolute file path")
      return
    if not os.path.isdir(directory):
      self.errors.append(f"Argument {arg} with directory {directory}"
                         f" does not exist")
      return

    return directory

  def validate_filepath(self, filepath: str, arg: str) -> Optional[str]:
    """ Validate passed filepath string

    Ensure paths are absolute and exist.

    Args:
      filepath: The path to validate.
      arg: The name of the attribute that the value will be assigned to.
      Used for reporting validation errors.

    Returns:
      `filepath` if validation passes, otherwise None.
    """
    if not os.path.isabs(filepath):
      self.errors.append(f"Argument {arg} is not an absolute file path")
      return

    return filepath

  def validate_date(self, dt: date, arg: str) -> Optional[date]:
    """Validate passed date

    Date must be a datetime.date object.

    Args:
      dt: The date to validate
      arg: The name of the attribute that the value will be assigned to.
      Used for reporting validation errors.

    Returns:
      `date` if validation passes, otherwise None.
    """
    if dt is None:
      self.errors.append(f"Please provide a valid value for Argument {arg}")
      return
    if not isinstance(dt, date):
      self.errors.append(f"Argument {arg} with value {dt} "
                         f"is not a valid date")
      return

    return dt

  def validate_file_name(self, file_name: str, arg: str) -> str:
    """Validate passed file name

    Acceptable file format is json.

    Args:
      file_name: File name to validate
      arg: The name of the attribute that the value will be assigned to.
      Used for reporting validation errors.

    Returns:
      `file_name` if validation passes, otherwise None.
    """
    if re.search(pattern, file_name) is not None:
      return file_name
    else:
      self.errors.append(f"Argument {arg} is not in an acceptable format."
                         f" Acceptable format is json")

  def validate_dict_string(self, mydict: str, arg: str) -> str:
    """Validate if string can be converted to a dict

    Args:
      mydict: String to check if it's a dictionary in string form

    Returns:
      `mydict` if valiation passes, otherwise None
    """
    try:
      json.loads(mydict)
      return mydict
    except json.decoder.JSONDecodeError:
      self.errors.append(f"Argument {arg} not in an acceptable format. "
                         f"Must be parseable as json.")

  def to_java_args(self) -> List[str]:
    """ Convert class attributes to command line flags

    Command line flags are defined in
    java/com/google/rubidium/SimulationConfig.java.

    Returns:
      List of strings, each element is a flag with its value appended.
    """
    java_args = [
        f"--sourceStartDate={self.source_start_date.strftime(isoformat)}",
        f"--sourceEndDate={self.source_end_date.strftime(isoformat)}",
        f"--triggerStartDate={self.trigger_start_date.strftime(isoformat)}",
        f"--triggerEndDate={self.trigger_end_date.strftime(isoformat)}",
        f"--inputDirectory={self.input_directory}",
        f"--domainAvroFile={self.domain_avro_file}",
        f"--outputDirectory={self.output_directory}",
        f"--attributionSourceFileName={self.attribution_source_file_name}",
        f"--triggerFileName={self.trigger_file_name}"
    ]

    return java_args
