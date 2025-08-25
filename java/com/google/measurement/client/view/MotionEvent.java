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

package com.google.measurement.client.view;

import com.google.measurement.client.InputEvent;

public class MotionEvent {
  public static final int ACTION_BUTTON_PRESS = 11;

  public static final class PointerCoords {}

  public static final class PointerProperties {}

  public static InputEvent obtain(
      long downTime,
      long eventTime,
      int action,
      int pointerCount,
      PointerProperties[] pointerProperties,
      PointerCoords[] pointerCoords,
      int metaState,
      int buttonState,
      float xPrecision,
      float yPrecision,
      int deviceId,
      int edgeFlags,
      int source,
      int flags) {
    return new InputEvent();
  }
}
