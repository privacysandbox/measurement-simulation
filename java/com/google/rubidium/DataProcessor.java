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

import com.google.rubidium.InputFileProcessor.AttributionSourceJsonMapperDoFn;
import com.google.rubidium.InputFileProcessor.TriggerJsonMapperDoFn;
import com.google.rubidium.util.Util;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.FileIO.ReadableFile;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;

public class DataProcessor {

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
}
