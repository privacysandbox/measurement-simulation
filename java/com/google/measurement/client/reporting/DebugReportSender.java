/*
 * Copyright (C) 2022 Google LLC
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

package com.google.measurement.client.reporting;

import com.google.measurement.client.Context;
import com.google.measurement.client.Uri;
import com.google.measurement.client.VisibleForTesting;
import java.net.MalformedURLException;
import java.net.URL;
import org.json.JSONArray;

/** Class to construct the full reporting url specific to debug reports. */
public class DebugReportSender {
  public static final String DEBUG_REPORT_URI_PATH =
      ".well-known/attribution-reporting/debug/verbose";

  public DebugReportSender(Context mContext) {}

  public int sendReport(Uri adTechDomain, JSONArray debugReportPayload) {
    return 0;
  }
}
