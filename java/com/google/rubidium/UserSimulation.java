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

package com.google.rubidium;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;

class UserSimulation implements Serializable {
  String userId;
  String outputDirectory;

  public UserSimulation(String userId, String outputDirectory) {
    this.userId = userId;
    this.outputDirectory = outputDirectory;
  }

  private List<Source> cloneSourceData(final List<Source> inputSourceData) {
    List<Source> clonedSourceData = new ArrayList<>();
    for (Source source : inputSourceData) {
      List<Long> clonedDedupKeys = new ArrayList<>(source.getDedupKeys());
      clonedSourceData.add(
          new Source.Builder()
              .setId(source.getId())
              .setEventId(source.getEventId())
              .setPublisher(source.getPublisher())
              .setAppDestination(source.getAppDestination())
              .setWebDestination(source.getWebDestination())
              .setEnrollmentId(source.getEnrollmentId())
              .setEventTime(source.getEventTime())
              .setExpiryTime(source.getExpiryTime())
              .setPriority(source.getPriority())
              .setSourceType(source.getSourceType())
              .setDedupKeys(clonedDedupKeys)
              .setStatus(source.getStatus())
              .setRegistrant(source.getRegistrant())
              .setAttributionMode(source.getAttributionMode())
              .setInstallAttributionWindow(source.getInstallAttributionWindow())
              .setInstallCooldownWindow(source.getInstallCooldownWindow())
              .setAggregateFilterData(source.getAggregateFilterData())
              .setAggregateSource(source.getAggregateSource())
              .setAggregatableAttributionSource(source.getAggregatableAttributionSource())
              .build());
    }
    return clonedSourceData;
  }

  private List<Trigger> cloneTriggerData(final List<Trigger> inputTriggerData) {
    List<Trigger> clonedTriggerData = new ArrayList<>();
    for (Trigger trigger : inputTriggerData) {
      clonedTriggerData.add(
          new Trigger.Builder()
              .setId(trigger.getId())
              .setAttributionDestination(trigger.getAttributionDestination())
              .setDestinationType(trigger.getDestinationType())
              .setEnrollmentId(trigger.getEnrollmentId())
              .setStatus(trigger.getStatus())
              .setEventTriggers(trigger.getEventTriggers())
              .setTriggerTime(trigger.getTriggerTime())
              .setRegistrant(trigger.getRegistrant())
              .setAggregateTriggerData(trigger.getAggregateTriggerData())
              .setAggregateValues(trigger.getAggregateValues())
              .setFilters(trigger.getFilters())
              .setAggregatableAttributionTrigger(trigger.getAggregatableAttributionTrigger())
              .build());
    }
    return clonedTriggerData;
  }

  /**
   * Create an IMeasurementDAO using copies of the supplied lists.
   *
   * @param inputSourceData
   * @param inputTriggerData
   * @return IMeasurementDAO instance that contains a copy of the supplied lists.
   */
  private IMeasurementDAO createMeasurementDAO(
      final List<Source> inputSourceData, final List<Trigger> inputTriggerData) {
    // Need to clone input source and trigger data to modify their state.
    // Apache beam does not allow to modify any processing element as it could be used in the
    // downstream PTransform.
    List<Source> clonedSourceData = this.cloneSourceData(inputSourceData);
    List<Trigger> clonedTriggerData = this.cloneTriggerData(inputTriggerData);

    // Beam pipeline does not guarantee the ordering of elements, so we need to sort them
    // again based on event times
    clonedSourceData.sort(Comparator.comparingLong(Source::getEventTime));
    clonedTriggerData.sort(Comparator.comparingLong(Trigger::getTriggerTime));

    return new MeasurementDAO(clonedSourceData, clonedTriggerData);
  }

  public List<JSONObject> runSimulation(
      final List<Source> inputSourceData, final List<Trigger> inputTriggerData) {
    return runSimulation(createMeasurementDAO(inputSourceData, inputTriggerData));
  }

  private void writeEventReportsToFile(Path userDirectory, IMeasurementDAO measurementDAO) {
    try {
      List<String> reports =
          measurementDAO.getAllEventReports().stream()
              .map(r -> r.toJsonObject().toJSONString())
              .collect(Collectors.toList());
      if (reports.size() > 0) {
        Files.createDirectories(userDirectory);
        Path eventReportsFilePath = userDirectory.resolve("event_reports.json");
        Files.deleteIfExists(eventReportsFilePath); // Empty file if one already exists
        for (String report : reports) {
          Files.writeString(
              eventReportsFilePath,
              report + "\n",
              StandardOpenOption.APPEND,
              StandardOpenOption.CREATE);
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to write event report to file with error:");
      e.printStackTrace();
    }
  }

  public List<JSONObject> runSimulation(IMeasurementDAO measurementDAO) {
    AttributionJobHandler attributionJobHandler = new AttributionJobHandler(measurementDAO);
    attributionJobHandler.performPendingAttributions();
    Path userDirectory = Path.of(outputDirectory, userId);
    writeEventReportsToFile(userDirectory, measurementDAO);

    List<JSONObject> aggregatableReports =
        measurementDAO.getAllAggregateReports().stream()
            .map(
                r -> {
                  try {
                    return r.toJson();
                  } catch (Exception e) {
                    System.err.println(
                        "Failed to convert aggregatable report to JSON with error: "
                            + e.getMessage());
                    return null;
                  }
                })
            .collect(Collectors.toList());
    return aggregatableReports;
  }
}
