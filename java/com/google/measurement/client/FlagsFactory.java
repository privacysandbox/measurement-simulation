/*
 * Copyright (C) 2024 Google LLC
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

package com.google.measurement.client;

/** Factory class to create AdServices Flags */
public final class FlagsFactory {
  private static Flags mFlags;

  /** Ad Services Flags backed by Phenotype/Heterodyne. */
  public static Flags getFlags() {
    if (mFlags == null) {
      mFlags = new Flags();
    }

    return mFlags;
  }

  private FlagsFactory() {
    throw new UnsupportedOperationException("provides only static methods");
  }
}
