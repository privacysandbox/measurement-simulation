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

package com.google.measurement.client;

import com.google.measurement.client.data.ContentValues;
import com.google.measurement.client.data.Cursor;

/** ContentProvider for monitoring changes to {@link Trigger}. */
public class TriggerContentProvider {
  /**
   * Gets the Trigger URI of this content provider
   *
   * @return the trigger uri, which can be different based on the Android version (S- vs. T+)
   */
  public static Uri getTriggerUri() {
    String authority =
        SdkLevel.isAtLeastT()
            ? "com.android.adservices.provider.trigger"
            : "com.android.ext.adservices.provider.trigger";
    return Uri.parse("content://" + authority);
  }

  public boolean onCreate() {
    return true;
  }

  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    throw new UnsupportedOperationException();
  }

  public String getType(Uri uri) {
    throw new UnsupportedOperationException();
  }

  public Uri insert(Uri uri, ContentValues values) {
    // TODO: Choose an integration option after Trigger datastore changes are available.
    // 1) Call ContentProvider after insert in DAO
    // Sample Code:
    // Uri triggerUri = TriggerContentProvider.getTriggerUri();
    // ContentProviderClient contentProviderClient = ctx.getContentResolver()
    //        .acquireContentProviderClient(triggerUri)
    //        .insert(triggerUri, null);
    // 2) Call ContentProvider for inserting during registration and call DAO from here.
    // Sample Code:
    // MeasurementDAO.getInstance(getContext()).insertTrigger(triggerObject);
    Uri triggerUri = getTriggerUri();
    // getContext().getContentResolver().notifyChange(triggerUri, null);
    return triggerUri;
  }

  public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }
}
