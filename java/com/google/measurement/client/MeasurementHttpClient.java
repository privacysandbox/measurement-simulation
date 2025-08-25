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

package com.google.measurement.client;

import java.net.URL;
import java.net.URLConnection;

/**
 * TODO: this class will be the interface between the input text data and the Source/Trigger
 * creation logic. Instead of performing actual HTTP calls, this class will simply return the text
 * data so that it can be parsed into Sources/Triggers.
 */
public class MeasurementHttpClient {

  public MeasurementHttpClient(Context context) {}

  public URLConnection setup(URL url) {
    return null;
  }
}
