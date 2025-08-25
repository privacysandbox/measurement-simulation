/*
 * Copyright 2025 Google LLC
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

package com.google.measurement.pipeline;

import static org.apache.beam.sdk.io.Compression.GZIP;

import java.io.IOException;
import java.nio.channels.Channels;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.FileIO.ReadableFile;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;

/**
 * A PTransform for ingesting Simulation inputs located at the inputFilePattern. A PCollection of KV
 * are returned where the key is the filename, without extension, and the value are the file
 * contents.
 */
public class FileInputTransform extends PTransform<PBegin, PCollection<KV<String, String>>> {

  private final String inputFilePattern;

  public FileInputTransform(String inputFilePattern) {
    this.inputFilePattern = inputFilePattern;
  }

  @Override
  public PCollection<KV<String, String>> expand(PBegin input) {
    PCollection<ReadableFile> files =
        input
            .apply("ReadInputFiles", FileIO.match().filepattern(inputFilePattern))
            .apply(FileIO.readMatches().withCompression(GZIP));

    return files.apply(
        "ExtractFilenameAndContent",
        MapElements.via(
            new SimpleFunction<>() {
              @Override
              public KV<String, String> apply(ReadableFile file) {
                String filename = file.getMetadata().resourceId().getFilename();
                String key =
                    filename.substring(0, filename.lastIndexOf(".")); // remove file extension
                try {
                  String content = new String(Channels.newInputStream(file.open()).readAllBytes());
                  return KV.of(key, content);
                } catch (IOException e) {
                  throw new RuntimeException("Error reading file: " + filename, e);
                }
              }
            }));
  }
}
