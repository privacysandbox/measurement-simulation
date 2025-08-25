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

package com.google.measurement.client.ondevicepersonalization;

import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION;

import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.registration.AsyncRegistration;
import com.google.measurement.client.stats.AdServicesLogger;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.stats.MeasurementOdpRegistrationStats;
import com.google.measurement.client.VisibleForTesting;
import java.util.List;
import java.util.Map;

public class NoOdpDelegationWrapper implements IOdpDelegationWrapper {
  private final AdServicesLogger mLogger;

  public NoOdpDelegationWrapper() {
    this(AdServicesLoggerImpl.getInstance());
  }

  @VisibleForTesting
  public NoOdpDelegationWrapper(AdServicesLogger logger) {
    mLogger = logger;
  }

  @Override
  public void registerOdpTrigger(
      AsyncRegistration asyncRegistration,
      Map<String, List<String>> headers,
      boolean isValidEnrollment) {
    LoggerFactory.getMeasurementLogger().d("registerOdpTrigger: ODP is not available");
    logOdpRegistrationMetrics(
        new OdpRegistrationStatus(
            OdpRegistrationStatus.RegistrationType.TRIGGER,
            OdpRegistrationStatus.RegistrationStatus.ODP_UNAVAILABLE));
  }

  @Override
  public void logOdpRegistrationMetrics(OdpRegistrationStatus odpRegistrationStatus) {
    mLogger.logMeasurementOdpRegistrations(
        new MeasurementOdpRegistrationStats.Builder()
            .setCode(AD_SERVICES_MEASUREMENT_PROCESS_ODP_REGISTRATION)
            .setRegistrationType(odpRegistrationStatus.getRegistrationType().getValue())
            .setRegistrationStatus(odpRegistrationStatus.getRegistrationStatus().getValue())
            .build());
  }
}
