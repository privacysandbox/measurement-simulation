/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.measurement;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.PipelineOptions;

public interface SimulationConfig extends PipelineOptions {
  String getInputDirectory();

  void setInputDirectory(String inputDirectory);

  String getOutputDirectory();

  void setOutputDirectory(String outputDirectory);

  String getSourceStartDate();

  void setSourceStartDate(String sourceStartDate);

  String getSourceEndDate();

  void setSourceEndDate(String sourceEndDate);

  @Default.String("attribution_source.json")
  String getAttributionSourceFileName();

  void setAttributionSourceFileName(String attributionSourceFileName);

  String getTriggerStartDate();

  void setTriggerStartDate(String triggerStartDate);

  String getTriggerEndDate();

  void setTriggerEndDate(String triggerEndDate);

  @Default.String("trigger.json")
  String getTriggerFileName();

  void setTriggerFileName(String triggerFileName);
}
