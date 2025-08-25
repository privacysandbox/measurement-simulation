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

package com.google.measurement.client.actions;

import static com.google.measurement.client.E2EAbstractTest.getFirstUrl;
import static com.google.measurement.client.E2EAbstractTest.getUriConfigsMap;
import static com.google.measurement.client.E2EAbstractTest.getUriToResponseHeadersMap;
import static com.google.measurement.client.E2EAbstractTest.hasAdIdPermission;
import static com.google.measurement.client.E2EAbstractTest.hasArDebugPermission;

import com.google.measurement.client.InteropTestReader;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.registration.RegistrationRequest;
import com.google.measurement.client.Uri;
import com.google.measurement.client.E2EAbstractTest.TestFormatJsonMapping;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public final class RegisterTrigger implements Action {
  public final RegistrationRequest mRegistrationRequest;
  public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
  public final Map<String, List<UriConfig>> mUriConfigsMap;
  public final long mTimestamp;
  // Used in interop tests
  public final String mDestination;
  public final boolean mAdIdPermission;
  public final boolean mArDebugPermission;

  public RegisterTrigger(JSONObject obj) throws JSONException {
    this(obj, null);
  }

  public RegisterTrigger(JSONObject obj, @Nullable InteropTestReader interopTestReader)
      throws JSONException {
    JSONObject regParamsJson = obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);

    String packageName =
        regParamsJson.optString(
            TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY,
            TestFormatJsonMapping.ATTRIBUTION_SOURCE_DEFAULT);

    mDestination = regParamsJson.optString(TestFormatJsonMapping.CONTEXT_ORIGIN_URI_KEY);

    mRegistrationRequest =
        new RegistrationRequest.Builder(
                RegistrationRequest.REGISTER_TRIGGER,
                Uri.parse(getFirstUrl(obj)),
                packageName,
                /* sdkPackageName= */ "")
            .setAdIdValue(regParamsJson.optString(TestFormatJsonMapping.PLATFORM_AD_ID))
            .build();

    mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj, interopTestReader);
    mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
    mAdIdPermission = hasAdIdPermission(obj);
    mArDebugPermission = hasArDebugPermission(obj);
    mUriConfigsMap = getUriConfigsMap(obj);
  }

  @Override
  public long getComparable() {
    return mTimestamp;
  }

  public String getDestination() {
    return mDestination;
  }
}
