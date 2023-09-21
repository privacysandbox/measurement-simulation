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

import com.google.measurement.InputFileProcessor.AttributionSourceJsonMapperDoFn;
import com.google.measurement.InputFileProcessor.ExtensionEventJsonMapperDoFn;
import com.google.measurement.InputFileProcessor.TriggerJsonMapperDoFn;
import com.google.measurement.util.Util;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.FileIO.ReadableFile;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;

public class DataProcessor {
  private static final Logger logger = Logger.getLogger(DataProcessor.class.getName());

  /**
   * Given a PCollection of Source objects keyed by UserId and a choice of which API platform to
   * target, return the PCollection filtered to all logs that have that API choice. Note: any
   * records intended for the Web platform, but have app destinations, will be filtered out as the
   * Web platform does not handle app destinations in the actual Attribution Reporting API.
   *
   * @param sourceMap PCollection of Source objects keyed by UserId.
   * @param apiChoice the API platform to filter to.
   * @return sourceMap filtered to records where api choice is apiChoice.
   */
  public static PCollection<KV<String, Source>> filterSourceMap(
      PCollection<KV<String, Source>> sourceMap, ApiChoice apiChoice) {
    return sourceMap
        .apply(Filter.by(x -> x.getValue().getApiChoice().equals(apiChoice)))
        .apply(
            Filter.by(
                x ->
                    !(x.getValue().getApiChoice() == ApiChoice.WEB
                        && x.getValue().getAppDestinations() != null
                        && !x.getValue().getAppDestinations().isEmpty())));
  }

  /**
   * Given a PCollection of Trigger objects keyed by UserId and a choice of which API platform to
   * target, return the PCollection filtered to all logs that have that API choice.
   *
   * @param triggerMap PCollection of Trigger objects keyed by UserId.
   * @param apiChoice the API platform to filter to.
   * @return triggerMap filtered to records where api choice is apiChoice.
   */
  public static PCollection<KV<String, Trigger>> filterTriggerMap(
      PCollection<KV<String, Trigger>> triggerMap, ApiChoice apiChoice) {
    return triggerMap.apply(Filter.by(x -> x.getValue().getApiChoice().equals(apiChoice)));
  }

  /**
   * Build a list of pairs of userId and Source objects from the input data.
   *
   * @param p Beam pipeline object
   * @param options Simulation related configuration parameters
   * @return KV object with key as userId and Value as Source object.
   */
  public static PCollection<KV<String, Source>> buildUserToSourceMap(
      Pipeline p, SimulationConfig options) {
    PCollection<ReadableFile> files = getAttributionSourceFiles(p, options);
    String attributionSourceFileType = Util.getFileType(options.getAttributionSourceFileName());
    if (Objects.equals(attributionSourceFileType, "json")) {
      return files.apply(TextIO.readFiles()).apply(ParDo.of(new AttributionSourceJsonMapperDoFn()));
    }

    throw new IllegalArgumentException(
        "Invalid file type for attributionSourceFileName. Acceptable file format is json");
  }

  /**
   * Build a list of pairs of userId and Trigger objects from the input data.
   *
   * @param p Beam pipeline object
   * @param options Simulation related configuration parameters
   * @return KV object with key as userId and Value as Trigger object.
   */
  public static PCollection<KV<String, Trigger>> buildUserToTriggerMap(
      Pipeline p, SimulationConfig options) {
    PCollection<ReadableFile> files = getTriggerFiles(p, options);
    String triggerFileType = Util.getFileType(options.getAttributionSourceFileName());
    if (Objects.equals(triggerFileType, "json")) {
      return files.apply(TextIO.readFiles()).apply(ParDo.of(new TriggerJsonMapperDoFn()));
    }

    throw new IllegalArgumentException(
        "Invalid file type for triggerFileName. Acceptable file format is json");
  }

  /**
   * Build a list of pairs of userId and ExtensionEvent objects from the input data.
   *
   * @param p Beam pipeline object
   * @param options Simulation related configuration parameters
   * @return KV object with key as userId and Value as ExtensionEvent object.
   */
  public static PCollection<KV<String, ExtensionEvent>> buildUserToExtensionEventMap(
      Pipeline p, SimulationConfig options) {
    try {
      PCollection<ReadableFile> files = getExtensionEventFiles(p, options);
      String extensionEventFileType = Util.getFileType(options.getExtensionEventFileName());
      if (Objects.equals(extensionEventFileType, "json")) {
        return files.apply(TextIO.readFiles()).apply(ParDo.of(new ExtensionEventJsonMapperDoFn()));
      }
    } catch (Exception e) {
      logger.info("No extension events found in the given time interval.");
      HashMap<String, ExtensionEvent> userExtensionEventMap = new HashMap();
      return p.apply(
          Create.of(userExtensionEventMap)
              .withCoder(KvCoder.of(StringUtf8Coder.of(), AvroCoder.of(ExtensionEvent.class))));
    }

    throw new IllegalArgumentException(
        "Invalid file type for extensionEventFileName. Acceptable file format is json");
  }

  private static PCollection<ReadableFile> getAttributionSourceFiles(
      Pipeline p, SimulationConfig options) {
    String inputDir = options.getInputDirectory();
    String attributionSourceFileName = options.getAttributionSourceFileName();
    LocalDate sourceStartDate = Util.parseStringDate(options.getSourceStartDate());
    LocalDate sourceEndDate = Util.parseStringDate(options.getSourceEndDate());
    List<String> paths =
        Util.getPathsInDateRange(
            sourceStartDate, sourceEndDate, inputDir, attributionSourceFileName);
    return p.apply(Create.of(paths)).apply(FileIO.matchAll()).apply(FileIO.readMatches());
  }

  private static PCollection<ReadableFile> getTriggerFiles(Pipeline p, SimulationConfig options) {
    String inputDir = options.getInputDirectory();
    String triggerFileName = options.getTriggerFileName();
    LocalDate triggerStartDate = Util.parseStringDate(options.getTriggerStartDate());
    LocalDate triggerEndDate = Util.parseStringDate(options.getTriggerEndDate());
    List<String> paths =
        Util.getPathsInDateRange(triggerStartDate, triggerEndDate, inputDir, triggerFileName);
    return p.apply(Create.of(paths)).apply(FileIO.matchAll()).apply(FileIO.readMatches());
  }

  private static PCollection<ReadableFile> getExtensionEventFiles(
      Pipeline p, SimulationConfig options) {
    String inputDir = options.getInputDirectory();
    String extensionEventFileName = options.getExtensionEventFileName();
    LocalDate extensionEventStartDate = Util.parseStringDate(options.getExtensionEventStartDate());
    LocalDate extensionEventEndDate = Util.parseStringDate(options.getExtensionEventEndDate());
    List<String> paths =
        Util.getPathsInDateRange(
            extensionEventStartDate, extensionEventEndDate, inputDir, extensionEventFileName);
    return p.apply(Create.of(paths)).apply(FileIO.matchAll()).apply(FileIO.readMatches());
  }

  /**
   * Join Source and Trigger data based on the userId
   *
   * @param userToAdtechSourceData PCollection of key-value pairs of userIds and Source objects
   * @param userToAdtechTriggerData PCollection of key-value pairs of userIds and Trigger objects
   * @param sourceTag Tag to identify Source object
   * @param triggerTag Tag to identify Trigger object
   * @return PCollection of joined data with key as userId and value as list of Source and Trigger
   *     objects which belong to that userId
   */
  public static PCollection<KV<String, CoGbkResult>> joinSourceAndTriggerData(
      PCollection<KV<String, Source>> userToAdtechSourceData,
      PCollection<KV<String, Trigger>> userToAdtechTriggerData,
      TupleTag<Source> sourceTag,
      TupleTag<Trigger> triggerTag) {
    return KeyedPCollectionTuple.of(sourceTag, userToAdtechSourceData)
        .and(triggerTag, userToAdtechTriggerData)
        .apply(CoGroupByKey.create());
  }

  /**
   * Join Source, Trigger and ExtensionEvent data based on the userId
   *
   * @param userToAdtechSourceData PCollection of key-value pairs of userIds and Source objects
   * @param userToAdtechTriggerData PCollection of key-value pairs of userIds and Trigger objects
   * @param userToAdtechExtensionEventData PCollection of key-value pairs of userIds and
   *     ExtensionEvent objects
   * @param sourceTag Tag to identify Source object
   * @param triggerTag Tag to identify Trigger object
   * @param extensionEventTupleTag Tag to identify ExtensionEvent object
   * @return PCollection of joined data with key as userId and value as list of Source, Trigger and
   *     ExtensionEvent objects which belong to that userId
   */
  public static PCollection<KV<String, CoGbkResult>> joinUserIdData(
      PCollection<KV<String, Source>> userToAdtechSourceData,
      PCollection<KV<String, Trigger>> userToAdtechTriggerData,
      PCollection<KV<String, ExtensionEvent>> userToAdtechExtensionEventData,
      TupleTag<Source> sourceTag,
      TupleTag<Trigger> triggerTag,
      TupleTag<ExtensionEvent> extensionEventTupleTag) {
    return KeyedPCollectionTuple.of(sourceTag, userToAdtechSourceData)
        .and(triggerTag, userToAdtechTriggerData)
        .and(extensionEventTupleTag, userToAdtechExtensionEventData)
        .apply(CoGroupByKey.create());
  }
}
