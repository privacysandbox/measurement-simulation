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
import org.json.simple.parser.ParseException;

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
      Source clonedSource =
          new Source.Builder()
              .setId(source.getId())
              .setEventId(source.getEventId())
              .setPublisher(source.getPublisher())
              .setAppDestinations(source.getAppDestinations())
              .setWebDestinations(source.getWebDestinations())
              .setEnrollmentId(source.getEnrollmentId())
              .setEventTime(source.getEventTime())
              .setExpiryTime(source.getExpiryTime())
              .setPriority(source.getPriority())
              .setSourceType(source.getSourceType())
              .setDebugKey(source.getDebugKey())
              .setIsDebugReporting(source.isDebugReporting())
              .setAdIdPermission(source.hasAdIdPermission())
              .setArDebugPermission(source.hasArDebugPermission())
              .setRegistrationId(source.getRegistrationId())
              .setParentId(source.getParentId())
              .setEventReportDedupKeys(new ArrayList<>(source.getEventReportDedupKeys()))
              .setAggregateReportDedupKeys(new ArrayList<>(source.getAggregateReportDedupKeys()))
              .setSharedAggregationKeys(source.getSharedAggregationKeys())
              .setEventReportWindow(source.getEventReportWindow())
              .setAggregatableReportWindow(source.getAggregatableReportWindow())
              .setStatus(source.getStatus())
              .setRegistrant(source.getRegistrant())
              .setAttributionMode(source.getAttributionMode())
              .setInstallAttributionWindow(source.getInstallAttributionWindow())
              .setInstallCooldownWindow(source.getInstallCooldownWindow())
              .setInstallTime(source.getInstallTime())
              .setFilterData(source.getFilterDataString())
              .setAggregateSource(source.getAggregateSource())
              .build();
      clonedSourceData.add(clonedSource);
    }
    return clonedSourceData;
  }

  private List<Trigger> cloneTriggerData(final List<Trigger> inputTriggerData) {
    List<Trigger> clonedTriggerData = new ArrayList<>();
    for (Trigger trigger : inputTriggerData) {
      Trigger clonedTrigger =
          new Trigger.Builder()
              .setId(trigger.getId())
              .setAttributionDestination(trigger.getAttributionDestination())
              .setDestinationType(trigger.getDestinationType())
              .setEnrollmentId(trigger.getEnrollmentId())
              .setStatus(trigger.getStatus())
              .setIsDebugReporting(trigger.isDebugReporting())
              .setAdIdPermission(trigger.hasAdIdPermission())
              .setArDebugPermission(trigger.hasArDebugPermission())
              .setEventTriggers(trigger.getEventTriggers())
              .setTriggerTime(trigger.getTriggerTime())
              .setRegistrant(trigger.getRegistrant())
              .setAttributionConfig(trigger.getAttributionConfig())
              .setAdtechBitMapping(trigger.getAdtechKeyMapping())
              .setAggregateDeduplicationKeys(trigger.getAggregateDeduplicationKeys())
              .setAggregateTriggerData(trigger.getAggregateTriggerData())
              .setAggregateValues(trigger.getAggregateValues())
              .setFilters(trigger.getFilters())
              .setNotFilters(trigger.getNotFilters())
              .build();
      clonedTriggerData.add(clonedTrigger);
    }
    return clonedTriggerData;
  }

  private void storeAllowedSourceData(
      IMeasurementDAO measurementDAO, List<Source> inputSourceData) {
    for (Source source : inputSourceData) {
      if (measurementDAO.canStoreSource(source)) {
        measurementDAO.insertSource(source);
      }
    }
  }

  private void storeAllowedTriggerData(
      IMeasurementDAO measurementDAO, List<Trigger> inputTriggerData) {
    for (Trigger trigger : inputTriggerData) {
      if (measurementDAO.canStoreTrigger(trigger)) {
        measurementDAO.insertTrigger(trigger);
      }
    }
  }

  /**
   * Create an IMeasurementDAO using copies of the supplied lists.
   *
   * @param inputSourceData
   * @param inputTriggerData
   * @return IMeasurementDAO instance that contains a copy of the supplied lists.
   */
  private IMeasurementDAO createMeasurementDAO(
      final List<Source> inputSourceData, final List<Trigger> inputTriggerData)
      throws ParseException {
    MeasurementDAO measurementDAO = new MeasurementDAO();

    // Need to clone input source and trigger data to modify their state.
    // Apache beam does not allow to modify any processing element as it could be used in the
    // downstream PTransform.
    List<Source> clonedSourceData = this.cloneSourceData(inputSourceData);
    List<Trigger> clonedTriggerData = this.cloneTriggerData(inputTriggerData);

    // Beam pipeline does not guarantee the ordering of elements, so we need to sort them
    // again based on event times
    clonedSourceData.sort(Comparator.comparingLong(Source::getEventTime));
    clonedTriggerData.sort(Comparator.comparingLong(Trigger::getTriggerTime));

    // store allowed source and trigger data
    this.storeAllowedSourceData(measurementDAO, clonedSourceData);
    this.storeAllowedTriggerData(measurementDAO, clonedTriggerData);

    return measurementDAO;
  }

  public List<JSONObject> runSimulation(
      final List<Source> inputSourceData, final List<Trigger> inputTriggerData)
      throws ParseException {
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

  public List<JSONObject> runSimulation(IMeasurementDAO measurementDAO) throws ParseException {
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
