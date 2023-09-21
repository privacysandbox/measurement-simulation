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

import com.google.measurement.noising.SourceNoiseHandler;
import com.google.measurement.util.Web;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.avro.reflect.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

class UserSimulation implements Serializable {
  String userId;
  String outputDirectory;
  SourceNoiseHandler mSourceNoiseHandler;
  DebugReportApi mDebugReportApi;

  public UserSimulation(String userId, String outputDirectory) {
    this(userId, outputDirectory, new SourceNoiseHandler(new Flags()));
  }

  public UserSimulation(
      String userId, String outputDirectory, SourceNoiseHandler sourceNoiseHandler) {
    this.userId = userId;
    this.outputDirectory = outputDirectory;
    mSourceNoiseHandler = sourceNoiseHandler;
    mDebugReportApi = new DebugReportApi(new Flags());
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
              .setAttributedTriggers(getClonedAttributedTriggers(source.getAttributedTriggers()))
              .setTriggerSpecs(source.getTriggerSpecs())
              .setMaxEventLevelReports(source.getMaxEventLevelReports())
              .setEventAttributionStatus(source.getEventAttributionStatus())
              .setPrivacyParameters(source.encodePrivacyParametersToJSONString())
              .build();
      clonedSourceData.add(clonedSource);
    }
    return clonedSourceData;
  }

  @Nullable
  private List<AttributedTrigger> getClonedAttributedTriggers(List<AttributedTrigger> copyFrom) {
    List<AttributedTrigger> copy = new ArrayList<>();
    if (copyFrom == null) {
      return copy;
    }
    for (AttributedTrigger trigger : copyFrom) {
      copy.add(AttributedTrigger.copy(trigger));
    }
    return copy;
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
        List<EventReport> eventReports = generateFakeEventReports(source);
        if (!eventReports.isEmpty()) {
          mDebugReportApi.scheduleSourceNoisedDebugReport(source, measurementDAO);
        }
        measurementDAO.insertSource(source);
        for (EventReport report : eventReports) {
          measurementDAO.insertEventReport(report);
        }

        // We want to account for attribution if fake report generation was considered
        // based on the probability. In that case the attribution mode will be NEVER
        // (empty fake reports state) or FALSELY (non-empty fake reports).
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
          // Attribution rate limits for app and web destinations are counted
          // separately, so add a fake report entry for each type of destination if
          // non-null.
          if (!Objects.isNull(source.getAppDestinations())) {
            for (URI destination : source.getAppDestinations()) {
              measurementDAO.insertAttribution(createFakeAttributionRateLimit(source, destination));
            }
          }

          if (!Objects.isNull(source.getWebDestinations())) {
            for (URI destination : source.getWebDestinations()) {
              measurementDAO.insertAttribution(createFakeAttributionRateLimit(source, destination));
            }
          }
        }
      }
    }
  }

  /**
   * {@link Attribution} generated from here will only be used for fake report attribution.
   *
   * @param source source to derive parameters from
   * @param destination destination for attribution
   * @return a fake {@link Attribution}
   */
  private Attribution createFakeAttributionRateLimit(Source source, URI destination) {
    Optional<URI> topLevelPublisher =
        getTopLevelPublisher(source.getPublisher(), source.getPublisherType());

    if (!topLevelPublisher.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "insertAttributionRateLimit: getSourceAndDestinationTopPrivateDomains"
                  + " failed. Publisher: %s; Attribution destination: %s",
              source.getPublisher().toString(), destination));
    }

    return new Attribution.Builder()
        .setSourceSite(topLevelPublisher.get().toString())
        .setSourceOrigin(source.getPublisher().toString())
        .setDestinationSite(destination.toString())
        .setDestinationOrigin(destination.toString())
        .setEnrollmentId(source.getEnrollmentId())
        .setTriggerTime(source.getEventTime())
        .setRegistrant(source.getRegistrant().toString())
        .setSourceId(source.getId())
        // Intentionally kept it as null because it's a fake attribution
        .setTriggerId(null)
        .build();
  }

  private static Optional<URI> getTopLevelPublisher(URI topOrigin, EventSurfaceType publisherType) {
    return publisherType == EventSurfaceType.APP
        ? Optional.of(topOrigin)
        : Web.topPrivateDomainAndScheme(topOrigin);
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
      final List<Source> inputSourceData, final List<Trigger> inputTriggerData) {
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

  private List<EventReport> generateFakeEventReports(Source source) {
    List<Source.FakeReport> fakeReports =
        mSourceNoiseHandler.assignAttributionModeAndGenerateFakeReports(source);
    return fakeReports.stream()
        .map(
            fakeReport ->
                new EventReport.Builder()
                    .setSourceEventId(source.getEventId())
                    .setReportTime(fakeReport.getReportingTime())
                    .setTriggerData(fakeReport.getTriggerData())
                    .setAttributionDestinations(fakeReport.getDestinations())
                    .setEnrollmentId(source.getEnrollmentId())
                    // The query for attribution check is from
                    // (triggerTime - 30 days) to triggerTime and max expiry is
                    // 30 days, so it's safe to choose triggerTime as source
                    // event time so that it gets considered when the query is
                    // fired for attribution rate limit check.
                    .setTriggerTime(source.getEventTime())
                    .setTriggerPriority(0L)
                    .setTriggerDedupKey(null)
                    .setSourceType(source.getSourceType())
                    .setStatus(EventReport.Status.PENDING)
                    .setRandomizedTriggerRate(
                        mSourceNoiseHandler.getRandomAttributionProbability(source))
                    .setRegistrationOrigin(source.getRegistrationOrigin())
                    .build())
        .collect(Collectors.toList());
  }

  public List<JSONObject> runSimulation(
      final List<Source> inputSourceData,
      final List<Trigger> inputTriggerData,
      final List<ExtensionEvent> inputExtensionEventData)
      throws ParseException {
    IMeasurementDAO measurementDAO = createMeasurementDAO(inputSourceData, inputTriggerData);

    List<Object> combinedData = new ArrayList<>(measurementDAO.getPendingTriggers());
    if (!inputExtensionEventData.isEmpty()) {
      combinedData.addAll(inputExtensionEventData);
    }

    Collections.sort(
        combinedData,
        new Comparator<Object>() {
          @Override
          public int compare(Object o1, Object o2) {
            long o1Value, o2Value;
            if (o1 instanceof Trigger) {
              o1Value = ((Trigger) o1).getTriggerTime();
            } else {
              o1Value = ((ExtensionEvent) o1).getTimestamp();
            }

            if (o2 instanceof Trigger) {
              o2Value = ((Trigger) o2).getTriggerTime();
            } else {
              o2Value = ((ExtensionEvent) o2).getTimestamp();
            }
            return Long.compare(o1Value, o2Value);
          }
        });

    return runSimulation(measurementDAO, combinedData);
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

  private void writeDebugEventReportsToFile(Path userDirectory, IMeasurementDAO measurementDAO) {
    try {
      List<String> reports =
          measurementDAO.getAllDebugReports().stream()
              .map(r -> r.toJSON().toJSONString())
              .collect(Collectors.toList());
      if (reports.size() > 0) {
        Files.createDirectories(userDirectory);
        Path debugReportsFilePath = userDirectory.resolve("debug_event_reports.json");
        Files.deleteIfExists(debugReportsFilePath); // Empty file if one already exists
        for (String report : reports) {
          Files.writeString(
              debugReportsFilePath,
              report + "\n",
              StandardOpenOption.APPEND,
              StandardOpenOption.CREATE);
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to write debug event report to file with error:");
      e.printStackTrace();
    }
  }

  public List<JSONObject> runSimulation(IMeasurementDAO measurementDAO, List<Object> combinedData)
      throws ParseException {
    AttributionJobHandler attributionJobHandler =
        new AttributionJobHandler(measurementDAO, combinedData);
    attributionJobHandler.performPendingAttributions();
    Path userDirectory = Path.of(outputDirectory, userId);
    writeEventReportsToFile(userDirectory, measurementDAO);
    // Debug event reports
    writeDebugEventReportsToFile(userDirectory, measurementDAO);

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
