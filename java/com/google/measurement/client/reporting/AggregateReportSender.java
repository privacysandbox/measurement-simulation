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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import org.json.JSONObject;

/** Class to construct the full reporting url specific to aggregate reports. */
public class AggregateReportSender {
  public static final String AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
      ".well-known/attribution-reporting/report-aggregate-attribution";

  public static final String DEBUG_AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
      ".well-known/attribution-reporting/debug/report-aggregate-attribution";

  public AggregateReportSender(Context context, String reportUriPath) {}

  public void sendReportWithExtraHeaders(
      Uri adTechDomain, JSONObject aggregateReportBody, Map<String, String> stringStringMap) {}

  public int sendReport(Uri adTechDomain, JSONObject aggregateReportBody) {
    return 0;
  }

  public int sendReportWithHeaders(
      Uri adTechDomain, JSONObject aggregateReportBody, Map<String, String> headers) {
    return 0;
  }
}
