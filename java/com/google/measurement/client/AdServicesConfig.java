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

package com.google.measurement.client;

import java.util.concurrent.TimeUnit;

/**
 * Hard Coded Configs for AdServices.
 *
 * <p>Relevant constants have been copied over to this simulation library
 */
public class AdServicesConfig {

  public static long MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

  public static long MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(24);
  public static long MEASUREMENT_DELETE_EXPIRED_WINDOW_MS = TimeUnit.DAYS.toMillis(30);

  /**
   * Returns the min time period (in millis) between each expired-record deletion maintenance job
   * run.
   */
  public static long getMeasurementDeleteExpiredJobPeriodMs() {
    return MEASUREMENT_DELETE_EXPIRED_JOB_PERIOD_MS;
  }

  public static long MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
      TimeUnit.HOURS.toMillis(24);

  public static long MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS =
      TimeUnit.HOURS.toMillis(4);

  public static long MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS =
      TimeUnit.HOURS.toMillis(24);

  public static String getMeasurementDefaultAggregationCoordinatorOrigin() {
    return FlagsFactory.getFlags().getMeasurementDefaultAggregationCoordinatorOrigin();
  }

  public static String getMeasurementAggregationCoordinatorOriginList() {
    return FlagsFactory.getFlags().getMeasurementAggregationCoordinatorOriginList();
  }

  public static String getMeasurementAggregationCoordinatorPath() {
    return FlagsFactory.getFlags().getMeasurementAggregationCoordinatorPath();
  }
}
