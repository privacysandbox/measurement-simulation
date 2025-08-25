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

package com.google.measurement.client.registration;

import com.google.measurement.client.Uri;

/** ContentProvider for monitoring changes to {@link AsyncRegistration}. */
public class AsyncRegistrationContentProvider {
  /**
   * Gets the Trigger URI of this content provider
   *
   * @return the trigger uri, which can be different based on the Android version (S- vs. T+)
   */
  public static Uri getTriggerUri() {
    return null;
  }
}
