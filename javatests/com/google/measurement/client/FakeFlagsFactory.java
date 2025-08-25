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

/**
 * Provides {@link Flags} implementations that override some common values that are used in tests.
 */
public final class FakeFlagsFactory {

  /**
   * @deprecated TODO(b/332723427): each API should use its own fake factory.
   */
  @Deprecated
  public static Flags getFlagsForTest() {
    // Use the Flags that has constant values.
    return new Flags();
  }

  private FakeFlagsFactory() {
    throw new UnsupportedOperationException("provides only static methods");
  }
}
