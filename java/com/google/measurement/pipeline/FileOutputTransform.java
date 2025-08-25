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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.FileIO;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.WriteFilesResult;
import org.apache.beam.sdk.transforms.Contextful;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * A PTransform for writing out a PCollection of KV to disk. The name of the file will be the key of
 * the KV and the contents will be the value.
 */
public class FileOutputTransform
    extends PTransform<PCollection<KV<String, String>>, WriteFilesResult<String>> {

  private final String outputDir;

  public FileOutputTransform(String outputDir) {
    this.outputDir = outputDir;
    try {
      Files.createDirectories(Paths.get(outputDir));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public WriteFilesResult<String> expand(PCollection<KV<String, String>> input) {
    return input.apply(
        "WriteToFile",
        FileIO.<String, KV<String, String>>writeDynamic()
            .by(KV::getKey)
            .via(Contextful.fn(KV::getValue), TextIO.sink())
            .to(outputDir)
            .withDestinationCoder(StringUtf8Coder.of())
            .withNaming(
                key -> (window, pane, numShards, shardIndex, compression) -> key + ".json"));
  }
}
