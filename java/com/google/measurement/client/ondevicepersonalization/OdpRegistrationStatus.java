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

public class OdpRegistrationStatus {
  /** Enums are tied to the AdservicesMeasurementProcessOdpRegistration atom */
  public enum RegistrationType {
    UNKNOWN(0),
    TRIGGER(1);

    private final int mValue;

    RegistrationType(int value) {
      mValue = value;
    }

    public int getValue() {
      return mValue;
    }
  }

  public enum RegistrationStatus {
    UNKNOWN(0),
    SUCCESS(1),
    ODP_UNAVAILABLE(2),
    INVALID_HEADER_FORMAT(3),
    MISSING_REQUIRED_HEADER_FIELD(4),
    INVALID_HEADER_FIELD_VALUE(5),
    INVALID_ENROLLMENT(6),
    HEADER_SIZE_LIMIT_EXCEEDED(7),
    PARSING_EXCEPTION(8);

    private final int mValue;

    RegistrationStatus(int value) {
      mValue = value;
    }

    public int getValue() {
      return mValue;
    }
  }

  private RegistrationType mRegistrationType;
  private RegistrationStatus mRegistrationStatus;

  public OdpRegistrationStatus() {
    this(RegistrationType.UNKNOWN, RegistrationStatus.UNKNOWN);
  }

  public OdpRegistrationStatus(
      RegistrationType registrationType, RegistrationStatus registrationStatus) {
    mRegistrationType = registrationType;
    mRegistrationStatus = registrationStatus;
  }

  /** Get the type of ODP registration that occurred. */
  public RegistrationType getRegistrationType() {
    return mRegistrationType;
  }

  /** Set the type of ODP registration that occurred. */
  public void setRegistrationType(RegistrationType type) {
    mRegistrationType = type;
  }

  /** Get the type of ODP registration that occurred. */
  public RegistrationStatus getRegistrationStatus() {
    return mRegistrationStatus;
  }

  /** Set the type of ODP registration that occurred. */
  public void setRegistrationStatus(RegistrationStatus status) {
    mRegistrationStatus = status;
  }
}
