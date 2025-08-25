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

package com.google.measurement.client.stats;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import org.json.JSONException;

public class MeasurementLogger {

  // TODO: perform logging
  public void e(String s) {}

  public void d(String s) {}

  public void d(Exception e, String s) {}

  public void e(Exception e, String s) {}

  public void v(String format, Object... params) {}

  @FormatMethod
  public int d(@FormatString String format, Object... params) {
    return 0;
  }

  @FormatMethod
  public void e(Exception e, @FormatString String format, Object... params) {}

  @FormatMethod
  public void e(@FormatString String format, Object... params) {}

  public void w(String s) {}
}
