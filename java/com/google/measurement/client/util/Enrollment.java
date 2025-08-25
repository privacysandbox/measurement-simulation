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

package com.google.measurement.client.util;

import com.google.measurement.client.Context;
import com.google.measurement.client.data.EnrollmentDao;
import com.google.measurement.client.data.EnrollmentData;
import com.google.measurement.client.Flags;
import com.google.measurement.client.Uri;
import java.util.Optional;

public class Enrollment {

  public static Optional<String> getValidEnrollmentId(
      Uri registrationUri,
      Object authority,
      EnrollmentDao mEnrollmentDao,
      Context mContext,
      Flags mFlags) {
    EnrollmentData enrollmentData =
        mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(registrationUri);
    if (enrollmentData != null) {
      return Optional.of(enrollmentData.getEnrollmentId());
    }

    return Optional.empty();
  }
}
