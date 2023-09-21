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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TupleTag;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class RunSimulationPerUser extends DoFn<KV<String, CoGbkResult>, List<JSONObject>>
    implements Serializable {
  private TupleTag<Source> sourceTag;
  private TupleTag<Trigger> triggerTag;
  private TupleTag<ExtensionEvent> extensionEventTag;
  private String outputDirectory;

  public RunSimulationPerUser(
      TupleTag<Source> sourceTag,
      TupleTag<Trigger> triggerTag,
      TupleTag<ExtensionEvent> extensionEventTag,
      String outputDirectory) {
    this.sourceTag = sourceTag;
    this.triggerTag = triggerTag;
    this.extensionEventTag = extensionEventTag;
    this.outputDirectory = outputDirectory;
  }

  @ProcessElement
  public void processElement(ProcessContext c) throws ParseException {
    KV<String, CoGbkResult> element = c.element();
    String userId = element.getKey();
    CoGbkResult userData = element.getValue();
    List<Source> userSourceData = (List<Source>) userData.getAll(this.sourceTag);
    List<Trigger> userTriggerData = (List<Trigger>) userData.getAll(this.triggerTag);

    List<ExtensionEvent> userExtensionEventData = new ArrayList<>();
    try {
      userExtensionEventData = (List<ExtensionEvent>) userData.getAll(this.extensionEventTag);
    } catch (IllegalArgumentException e) {
      // Ignore as this means there were no Extension events in the input data and/or
      // extensionEventTupleTag was not found
    }

    List<JSONObject> aggregatePayloads =
        new UserSimulation(userId, outputDirectory)
            .runSimulation(userSourceData, userTriggerData, userExtensionEventData);
    c.output(aggregatePayloads);
  }
}
