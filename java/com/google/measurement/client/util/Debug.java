/*
 * Copyright 2022 Google LLC
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

import com.google.measurement.client.EventSurfaceType;
import com.google.measurement.client.Source;
import com.google.measurement.client.Trigger;

/** Debug-related utilities for measurement. */
public final class Debug {

  private Debug() {}

  /**
   * Utility method to determine attribution debug report permission.
   *
   * @param source the {@code Source}
   * @param trigger the {@code Trigger}
   * @param sourceDebugKey the source debug key
   * @param triggerDebugKey the trigger debug key
   * @return whether the parameter configuration permits an attribution debug report
   */
  public static boolean isAttributionDebugReportPermitted(
      Source source, Trigger trigger, UnsignedLong sourceDebugKey, UnsignedLong triggerDebugKey) {
    if (source.getPublisherType() == EventSurfaceType.WEB
        && trigger.getDestinationType() == EventSurfaceType.WEB) {
      return sourceDebugKey != null && triggerDebugKey != null;
    } else {
      return sourceDebugKey != null || triggerDebugKey != null;
    }
  }
}
