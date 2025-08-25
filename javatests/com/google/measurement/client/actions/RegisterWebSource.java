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
import static com.google.measurement.client.E2EAbstractTest.hasArDebugPermission;

import com.google.measurement.client.WebSourceParams;
import com.google.measurement.client.registration.WebSourceRegistrationRequest;
import com.google.measurement.client.registration.WebSourceRegistrationRequestInternal;
import com.google.measurement.client.Uri;
import com.google.measurement.client.E2EAbstractTest.TestFormatJsonMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RegisterWebSource implements Action {
  public final WebSourceRegistrationRequestInternal mRegistrationRequest;
  public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
  public final Map<String, List<UriConfig>> mUriConfigsMap;
  public final long mTimestamp;
  public final boolean mAdIdPermission;

  public RegisterWebSource(JSONObject obj) throws JSONException {
    JSONObject regParamsJson = obj.getJSONObject(TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);
    // We use a specified source_params list for registration URLs rather than relying on the
    // request responses list in the test JSON because some testing consists of having these
    // params differ from redirect sequences that are elements in the responses list.
    JSONArray sourceParamsArray =
        regParamsJson.getJSONArray(TestFormatJsonMapping.SOURCE_PARAMS_REGISTRATIONS_KEY);
    Uri appDestination =
        getNullableStringUri(regParamsJson, TestFormatJsonMapping.SOURCE_APP_DESTINATION_URI_KEY);
    Uri webDestination =
        getNullableStringUri(regParamsJson, TestFormatJsonMapping.SOURCE_WEB_DESTINATION_URI_KEY);
    Uri verifiedDestination =
        getNullableStringUri(
            regParamsJson, TestFormatJsonMapping.SOURCE_VERIFIED_DESTINATION_URI_KEY);

    String packageName = regParamsJson.optString(TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY);

    WebSourceRegistrationRequest registrationRequest =
        new WebSourceRegistrationRequest.Builder(
                createSourceParams(sourceParamsArray, obj),
                Uri.parse(regParamsJson.getString(TestFormatJsonMapping.SOURCE_TOP_ORIGIN_URI_KEY)))
            .setInputEvent(
                regParamsJson
                        .getString(TestFormatJsonMapping.INPUT_EVENT_KEY)
                        .equals(TestFormatJsonMapping.SOURCE_VIEW_TYPE)
                    ? null
                    : getInputEvent())
            .setAppDestination(appDestination)
            .setWebDestination(webDestination)
            .setVerifiedDestination(verifiedDestination)
            .build();

    mRegistrationRequest =
        new WebSourceRegistrationRequestInternal.Builder(
                registrationRequest,
                packageName,
                /* sdkPackageName= */ "",
                /* requestTime= */ 2000L)
            .build();

    mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj);
    mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
    mAdIdPermission = hasAdIdPermission(obj);
    mUriConfigsMap = getUriConfigsMap(obj);
  }

  @Override
  public long getComparable() {
    return mTimestamp;
  }

  private List<WebSourceParams> createSourceParams(JSONArray sourceParamsArray, JSONObject obj)
      throws JSONException {
    List<WebSourceParams> sourceParamsList = new ArrayList<>(sourceParamsArray.length());

    for (int i = 0; i < sourceParamsArray.length(); i++) {
      JSONObject sourceParams = sourceParamsArray.getJSONObject(i);
      sourceParamsList.add(
          new WebSourceParams.Builder(
                  Uri.parse(sourceParams.getString(TestFormatJsonMapping.REGISTRATION_URI_KEY)))
              .setDebugKeyAllowed(hasArDebugPermission(obj))
              .build());
    }

    return sourceParamsList;
  }

  private Uri getNullableStringUri(JSONObject regParamsJson, String key) throws JSONException {
    return regParamsJson.isNull(key) ? null : Uri.parse(regParamsJson.getString(key));
  }
}
