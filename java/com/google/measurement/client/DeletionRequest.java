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

package com.google.measurement.client;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class DeletionRequest {
  /**
   * Deletion modes for matched records.
   *
   * @hide
   */
  @IntDef(value = {DELETION_MODE_ALL, DELETION_MODE_EXCLUDE_INTERNAL_DATA})
  @Retention(RetentionPolicy.SOURCE)
  public @interface DeletionMode {}

  /**
   * Matching Behaviors for params.
   *
   * @hide
   */
  @IntDef(value = {MATCH_BEHAVIOR_DELETE, MATCH_BEHAVIOR_PRESERVE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface MatchBehavior {}

  /** Deletion mode to delete all data associated with the selected records. */
  public static final int DELETION_MODE_ALL = 0;

  /**
   * Deletion mode to delete all data except the internal data (e.g. rate limits) for the selected
   * records.
   */
  public static final int DELETION_MODE_EXCLUDE_INTERNAL_DATA = 1;

  /** Match behavior option to delete the supplied params (Origin/Domains). */
  public static final int MATCH_BEHAVIOR_DELETE = 0;

  /**
   * Match behavior option to preserve the supplied params (Origin/Domains) and delete everything
   * else.
   */
  public static final int MATCH_BEHAVIOR_PRESERVE = 1;
}
