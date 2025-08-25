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

package com.google.measurement.client.data;

import com.google.measurement.client.Context;
import com.google.measurement.client.EnrollmentUtil;
import com.google.measurement.client.Flags;
import com.google.measurement.client.stats.NoOpLoggerImpl;
import com.google.measurement.client.Uri;
import java.util.HashMap;
import java.util.Map;

public class EnrollmentDao {
  private Map<String, EnrollmentData> mData;
  private static EnrollmentDao mSingleton;

  public EnrollmentDao(
      Context applicationContext,
      MeasurementDbHelper sharedDbHelperForTest,
      Flags mFlags,
      boolean b,
      NoOpLoggerImpl noOpLogger,
      EnrollmentUtil instance) {
    mData = new HashMap<>();
  }

  public EnrollmentDao() {
    mData = new HashMap<>();
  }

  public static EnrollmentDao getInstance() {
    if (mSingleton == null) {
      mSingleton = new EnrollmentDao();
    }

    return mSingleton;
  }

  public EnrollmentData getEnrollmentDataFromMeasurementUrl(Uri uri) {
    return mData.get(uri.toString());
  }

  public EnrollmentData getEnrollmentData(String enrollmentId) {
    return null;
  }

  public void delete(String enrollmentId) {}

  public boolean insert(EnrollmentData enrollmentData) {
    mData.put(enrollmentData.getAttributionSourceRegistrationUrl().get(0), enrollmentData);
    mData.put(enrollmentData.getAttributionTriggerRegistrationUrl().get(0), enrollmentData);
    return true;
  }
}
