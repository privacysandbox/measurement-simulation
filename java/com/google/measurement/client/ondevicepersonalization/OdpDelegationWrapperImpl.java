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

import com.google.measurement.client.registration.AsyncRegistration;
import java.util.List;
import java.util.Map;

public class OdpDelegationWrapperImpl implements IOdpDelegationWrapper {

  public OdpDelegationWrapperImpl(
      OnDevicePersonalizationSystemEventManager odpSystemEventManager) {}

  @Override
  public void registerOdpTrigger(
      AsyncRegistration asyncRegistration,
      Map<String, List<String>> headers,
      boolean isValidEnrollment) {}

  @Override
  public void logOdpRegistrationMetrics(OdpRegistrationStatus odpRegistrationStatus) {}

  private interface OdpTriggerHeaderContract {
    String HEADER_ODP_REGISTER_TRIGGER = "Odp-Register-Trigger";
    String ODP_SERVICE = "service";
    String ODP_CERT_DIGEST = "certDigest";
    String ODP_DATA = "data";
  }
}
