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

import static com.google.measurement.client.E2EAbstractTest.getInputEvent;
import static com.google.measurement.client.E2EAbstractTest.getUriConfigsMap;
import static com.google.measurement.client.E2EAbstractTest.getUriToResponseHeadersMap;
import static com.google.measurement.client.E2EAbstractTest.hasAdIdPermission;

import com.google.measurement.client.registration.SourceRegistrationRequest;
import com.google.measurement.client.registration.SourceRegistrationRequestInternal;
import com.google.measurement.client.Uri;
import com.google.measurement.client.E2EAbstractTest.TestFormatJsonMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RegisterListSources implements Action {
  public final SourceRegistrationRequestInternal mRegistrationRequest;
  public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
  public final Map<String, List<UriConfig>> mUriConfigsMap;
  public final long mTimestamp;
  // Used in interop tests
  public final String mPublisher;
  public final boolean mAdIdPermission;

  public RegisterListSources(JSONObject obj) throws JSONException {
    JSONObject regParamsJson = obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);
    JSONArray registrationUris =
        regParamsJson.getJSONArray(TestFormatJsonMapping.REGISTRATION_URIS_KEY);

    String packageName =
        regParamsJson.optString(
            TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY,
            TestFormatJsonMapping.ATTRIBUTION_SOURCE_DEFAULT);

    mPublisher = regParamsJson.optString(TestFormatJsonMapping.SOURCE_TOP_ORIGIN_URI_KEY);

    SourceRegistrationRequest request =
        new SourceRegistrationRequest.Builder(createRegistrationUris(registrationUris))
            .setInputEvent(
                regParamsJson
                        .getString(TestFormatJsonMapping.INPUT_EVENT_KEY)
                        .equals(TestFormatJsonMapping.SOURCE_VIEW_TYPE)
                    ? null
                    : getInputEvent())
            .build();

    mRegistrationRequest =
        new SourceRegistrationRequestInternal.Builder(
                request,
                packageName,
                /* sdkPackageName= */ "",
                /* bootRelativeRequestTime= */ 2000L)
            .setAdIdValue(regParamsJson.optString(TestFormatJsonMapping.PLATFORM_AD_ID))
            .build();
    mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj);
    mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
    mAdIdPermission = hasAdIdPermission(obj);
    mUriConfigsMap = getUriConfigsMap(obj);
  }

  private List<Uri> createRegistrationUris(JSONArray jsonArray) throws JSONException {
    List<Uri> registrationUris = new ArrayList<Uri>();
    for (int i = 0; i < jsonArray.length(); i++) {
      registrationUris.add(Uri.parse(jsonArray.getString(i)));
    }
    return registrationUris;
  }

  @Override
  public long getComparable() {
    return mTimestamp;
  }

  public String getPublisher() {
    return mPublisher;
  }
}
