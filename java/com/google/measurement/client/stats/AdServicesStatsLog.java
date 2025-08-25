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

public class AdServicesStatsLog {
  public static final int AD_SERVICES_MESUREMENT_REPORTS_UPLOADED = 436;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR = 2010;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR = 2011;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR = 2012;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR = 2013;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER =
          2006;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR = 2007;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR = 2008;
  public static final int AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT = 2;

  public static final int AD_SERVICES_MEASUREMENT_REGISTRATIONS = 512;

  public static final int AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE =
      2004;

  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE = 2005;

  public static final int AD_SERVICES_MEASUREMENT_ATTRIBUTION = 674;

  public static final int AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION = 673;
  public static final int AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID = 2015;
  public static final int
      AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_ERROR = 2016;

  public static final int AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION = 864;

  // Values for AdServicesMeasurementRegistrations.type
  public static final int AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__UNKNOWN_REGISTRATION = 0;
  public static final int AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE = 1;
  public static final int AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER = 2;

  public static final int AD_SERVICES_MEASUREMENT_WIPEOUT = 676;
}
