/*
 * Copyright (C) 2023 Google LLC
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

package com.google.measurement.client.registration;

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Uri;
import java.util.Objects;

public class RegistrationRequestFixture {
  public static RegistrationRequest getInvalidRegistrationRequest(
      int registrationType,
      @NonNull Uri registrationUri,
      @NonNull String appPackageName,
      @NonNull String sdkPackageName) {
    Objects.requireNonNull(registrationUri);
    Objects.requireNonNull(appPackageName);
    Objects.requireNonNull(sdkPackageName);

    RegistrationRequest.Builder builder =
        new RegistrationRequest.Builder(
            registrationType, registrationUri, appPackageName, sdkPackageName);
    builder.setRequestTime(0L);
    builder.setAdIdPermissionGranted(false);
    builder.setAdIdValue("false");
    return builder.build();
    // Parcel parcel = Parcel.obtain();
    // parcel.writeInt(registrationType);
    // registrationUri.writeToParcel(parcel, 0);
    // parcel.writeString(appPackageName);
    // parcel.writeString(sdkPackageName);
    // /* mInputEvent */ parcel.writeBoolean(false);
    // /* mRequestTime */ parcel.writeLong(0L);
    // /* mIsAdIdPermissionGranted */ parcel.writeBoolean(false);
    // /* mAdIdValue */ parcel.writeBoolean(false);
    // parcel.setDataPosition(0);
    //
    // return RegistrationRequest.CREATOR.createFromParcel(parcel);
  }
}
