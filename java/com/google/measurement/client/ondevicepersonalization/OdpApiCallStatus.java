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

public class OdpApiCallStatus {
  /** Enums are tied to the AdservicesMeasurementNotifyRegistrationToOdp atom */
  public enum ApiCallStatus {
    UNKNOWN(0),
    SUCCESS(1),
    FAILURE(2);

    private final int mValue;

    ApiCallStatus(int value) {
      mValue = value;
    }

    public int getValue() {
      return mValue;
    }
  }

  private long mLatency;
  private ApiCallStatus mApiCallStatus;

  public OdpApiCallStatus() {
    mLatency = 0;
    mApiCallStatus = ApiCallStatus.UNKNOWN;
  }

  /** Get the API call latency. */
  public long getLatency() {
    return mLatency;
  }

  /** Set the API call latency. */
  public void setLatency(long latency) {
    mLatency = latency;
  }

  /** Get the status of the API call. */
  public ApiCallStatus getApiCallStatus() {
    return mApiCallStatus;
  }

  /** Set the status of the API call. */
  public void setApiCallStatus(ApiCallStatus apiCallStatus) {
    mApiCallStatus = apiCallStatus;
  }
}
