/*
 * Copyright (C) 2024 Google LLC
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

import com.google.measurement.client.E2EAbstractTest.TestFormatJsonMapping;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads source and trigger registrations from Chromium interop tests. */
public class InteropTestReader {
  // 'n' for null
  private static final List<Character> sDelimiters = List.of('"', '{', 'n');
  private static final List<Character> sSpaceChars = List.of(' ', '\t', '\n');

  private final String mJson;
  private List<Integer> mSourceRegistrationIndexes;
  private List<Integer> mTriggerRegistrationIndexes;

  InteropTestReader(String json) {
    mJson = json;
  }

  /** Provides the next source registration. */
  public String getNextSourceRegistration() {
    if (mSourceRegistrationIndexes == null) {
      getSourceRegistrationIndexes();
    }
    return getRegistration(
        mJson, mSourceRegistrationIndexes.remove(mSourceRegistrationIndexes.size() - 1));
  }

  /** Provides the next trigger registration. */
  public String getNextTriggerRegistration() {
    if (mTriggerRegistrationIndexes == null) {
      getTriggerRegistrationIndexes();
    }
    return getRegistration(
        mJson, mTriggerRegistrationIndexes.remove(mTriggerRegistrationIndexes.size() - 1));
  }

  private void getSourceRegistrationIndexes() {
    mSourceRegistrationIndexes = new ArrayList<>();
    getRegistrationIndexes(
        mJson, TestFormatJsonMapping.SOURCE_REGISTRATION_HEADER, mSourceRegistrationIndexes);
  }

  private void getTriggerRegistrationIndexes() {
    mTriggerRegistrationIndexes = new ArrayList<>();
    getRegistrationIndexes(
        mJson, TestFormatJsonMapping.TRIGGER_REGISTRATION_HEADER, mTriggerRegistrationIndexes);
  }

  private static void getRegistrationIndexes(
      String json, String headerName, List<Integer> accumulator) {
    Pattern headerPattern = Pattern.compile(String.format("\"%s\"\\s*:", headerName));
    Matcher headerMatcher = headerPattern.matcher(json);
    while (headerMatcher.find()) {
      // Advance after the colon
      int i = headerMatcher.end() + 1;
      while (!sDelimiters.contains(json.charAt(i))) {
        i += 1;
      }
      accumulator.add(i);
    }
    Collections.reverse(accumulator);
  }

  private static String getRegistration(String json, int i) {
    if (json.charAt(i) == '{') {
      return getJsonObject(json, i);
    } else if (json.charAt(i) == '"') {
      return getDeEscapedString(json, i + 1);
    }
    return null;
  }

  private static String getDeEscapedString(String json, int index) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = index; i < json.length(); ) {
      if (json.charAt(i) == '"') {
        return stringBuilder.toString();
      }
      if (json.charAt(i) == '\\' && i + 1 < json.length() && json.charAt(i + 1) == '"') {
        stringBuilder.append("\"");
        i += 2;
      } else {
        stringBuilder.append(json.charAt(i));
        i += 1;
      }
    }
    return stringBuilder.toString();
  }

  private static String getJsonObject(String json, int index) {
    StringBuilder stringBuilder = new StringBuilder();
    Deque<Character> stack = new ArrayDeque<>();
    boolean inString = false;
    for (int i = index; i < json.length(); i++) {
      if (json.charAt(i) == '"') {
        if (json.charAt(i - 1) != '\\' && inString) {
          inString = false;
        } else {
          inString = true;
        }
      }
      if (!inString && sSpaceChars.contains(json.charAt(i))) {
        continue;
      }
      if (json.charAt(i) == '{') {
        stack.addLast('{');
      } else if (json.charAt(i) == '}') {
        stack.removeLast();
      }
      stringBuilder.append(json.charAt(i));
      if (stack.isEmpty()) {
        return stringBuilder.toString();
      }
    }
    return stringBuilder.toString();
  }
}
