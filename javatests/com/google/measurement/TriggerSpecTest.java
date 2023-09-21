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

package com.google.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.google.measurement.TriggerSpec.SummaryOperatorType;
import com.google.measurement.util.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;

public class TriggerSpecTest {
  public static JSONObject getJson(
      int[] triggerData,
      int eventReportWindowsStart,
      long[] eventReportWindowsEnd,
      String summaryWindowOperator,
      int[] summaryBucket) {
    JSONObject json = new JSONObject();
    if (triggerData != null) {
      JSONArray jsonTriggerData = new JSONArray();
      for (int data : triggerData) {
        jsonTriggerData.add((long) data);
      }
      json.put("trigger_data", jsonTriggerData);
    }
    JSONObject windows = new JSONObject();
    if (eventReportWindowsStart != 0) {
      windows.put("start_time", eventReportWindowsStart);
    }
    if (eventReportWindowsEnd != null) {
      JSONArray jsonEndTimes = new JSONArray();
      for (Long data : eventReportWindowsEnd) {
        jsonEndTimes.add(data);
      }
      windows.put("end_times", jsonEndTimes);
      json.put("event_report_windows", windows);
    }
    if (summaryWindowOperator != null) {
      json.put("summary_window_operator", summaryWindowOperator);
    }
    if (summaryBucket != null) {
      JSONArray jsonSummaryBucket = new JSONArray();
      for (int data : summaryBucket) {
        jsonSummaryBucket.add((long) data);
      }
      json.put("summary_buckets", jsonSummaryBucket);
    }
    return json;
  }

  public static JSONObject getValidBaselineTestCase() {
    int[] triggerData = {1, 2, 3};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {
      TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
    };
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1, 2, 3, 4};
    return getJson(
        triggerData,
        eventReportWindowsStart,
        eventReportWindowsEnd,
        summaryWindowOperator,
        summaryBucket);
  }

  @Test
  public void testEqualsPass() {
    // Assertion
    assertEquals(
        new TriggerSpec.Builder(getValidBaselineTestCase()).build(),
        new TriggerSpec.Builder(getValidBaselineTestCase()).build());
  }

  @Test
  public void testEqualsWithDefaultValuePass() {
    // Assertion
    JSONObject json = getValidBaselineTestCase();
    json.remove("summary_window_operator");
    assertEquals(
        new TriggerSpec.Builder(json).build(),
        new TriggerSpec.Builder(getValidBaselineTestCase()).build());
  }

  @Test
  public void testEqualsFail() {
    JSONArray triggerData = new JSONArray();
    for (long i = 1; i <= 4; ++i) {
      triggerData.add(i);
    }

    JSONArray triggerData2 = new JSONArray();
    for (long i = 1; i <= 3; ++i) {
      triggerData2.add(i);
    }

    JSONObject data1 = getValidBaselineTestCase();
    data1.put("trigger_data", triggerData);
    JSONObject data2 = getValidBaselineTestCase();
    data2.put("trigger_data", triggerData2);
    JSONObject data3 = getValidBaselineTestCase();
    data3.put("summary_window_operator", SummaryOperatorType.VALUE_SUM.toString());

    // Assertion
    assertNotEquals(new TriggerSpec.Builder(data1).build(), new TriggerSpec.Builder(data2).build());
    assertNotEquals(
        new TriggerSpec.Builder(data3).build(),
        new TriggerSpec.Builder(getValidBaselineTestCase()).build());
  }

  @Test
  public void triggerSpecBuilder_invalidSummaryOperator_throws() {
    // Setup
    int[] triggerData = {1, 2, 3};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {
      TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
    };
    String summaryWindowOperator = "count_typo";
    int[] summaryBucket = {1, 2, 3, 4};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertThrows(IllegalArgumentException.class, () -> new TriggerSpec.Builder(JSONInput).build());
  }

  @Test
  public void triggerSpecBuilder_validJson_success() {
    // Setup
    int[] triggerData = {1, 2, 3};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {
      TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
    };
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1, 2, 3, 4};
    JSONObject json =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Execution
    TriggerSpec testObject = new TriggerSpec.Builder(json).build();
    // Assertion
    List<UnsignedLong> expectedTriggerData = new ArrayList<>();
    for (int n : triggerData) {
      expectedTriggerData.add(new UnsignedLong((long) n));
    }
    assertEquals(expectedTriggerData, testObject.getTriggerData());
    assertEquals(eventReportWindowsStart, testObject.getEventReportWindowsStart());
    List<Long> expectedEventReportWindowsEnd =
        Arrays.asList(
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30));
    assertEquals(expectedEventReportWindowsEnd, testObject.getEventReportWindowsEnd());
    assertEquals(testObject.getSummaryWindowOperator().name().toLowerCase(), summaryWindowOperator);
    List<Long> expectedSummaryBuckets = Arrays.asList(1L, 2L, 3L, 4L);
    assertEquals(expectedSummaryBuckets, testObject.getSummaryBucket());
  }

  @Test
  public void triggerSpecBuilder_nonStrictIncreaseSummaryBuckets_throws() {
    // Setup
    int[] triggerData = {1, 2, 3};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {
      TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30)
    };
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1, 2, 2, 4};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertThrows(IllegalArgumentException.class, () -> new TriggerSpec.Builder(JSONInput).build());
  }

  @Test
  public void triggerSpecBuilder_nonStrictIncreaseReportWindowEnd_throws() {
    // Setup
    int[] triggerData = {1, 2, 3};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {
      TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(30)
    };
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1, 2, 2, 4};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertThrows(IllegalArgumentException.class, () -> new TriggerSpec.Builder(JSONInput).build());
  }

  @Test
  public void triggerSpecBuilder_exceedReportDataCardinalityLimitation_throws() {
    // Setup
    int[] triggerData = new int[PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY + 1];
    for (int i = 0; i < triggerData.length; i++) {
      triggerData[i] = i;
    }
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = {TimeUnit.DAYS.toMillis(2)};
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertThrows(IllegalArgumentException.class, () -> new TriggerSpec.Builder(JSONInput).build());
  }

  @Test
  public void triggerSpecBuilder_exceedReportWindowLimitation_throws() {
    // Setup
    int[] triggerData = {1};
    int eventReportWindowsStart = 0;
    long[] eventReportWindowsEnd = new long[PrivacyParams.MAX_FLEXIBLE_EVENT_REPORTING_WINDOWS + 1];
    for (int i = 0; i < eventReportWindowsEnd.length; i++) {
      eventReportWindowsEnd[i] = TimeUnit.DAYS.toMillis(i + 1);
    }
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertThrows(IllegalArgumentException.class, () -> new TriggerSpec.Builder(JSONInput).build());
  }

  @Test
  public void triggerSpecBuilder_autoCorrectionNegStartTime_equal() {
    // Setup
    int[] triggerData = {1, 2, 3, 4, 5, 6, 7, 8};
    int eventReportWindowsStart = -1;
    long[] eventReportWindowsEnd = {TimeUnit.DAYS.toMillis(2)};
    String summaryWindowOperator = "count";
    int[] summaryBucket = {1};
    JSONObject JSONInput =
        getJson(
            triggerData,
            eventReportWindowsStart,
            eventReportWindowsEnd,
            summaryWindowOperator,
            summaryBucket);
    // Assertion
    assertEquals(0, new TriggerSpec.Builder(JSONInput).build().getEventReportWindowsStart());
  }
}
