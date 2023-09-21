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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.measurement.util.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class ReportSpecTest {

  private static final long BASE_TIME = System.currentTimeMillis();
  private static final String PRIVACY_PARAMETERS_JSON = "{\"flip_probability\" :0.0024}";

  @Test
  public void equals_constructorThreeParameters_returnsTrue() {
    // Assertion
    assertEquals(
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null),
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null));
  }

  @Test
  public void equals_constructorThreeParameters_maxBucketIncrementsDifferent_returnsFalse() {
    // Assertion
    assertNotEquals(
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null),
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 4, null));
  }

  @Test
  public void equals_constructorThreeParameters_triggerSpecContentDifferent_returnsFalse() {
    assertNotEquals(
        new ReportSpec(SourceFixture.getTriggerSpecValueCountJSONTwoTriggerSpecs(), 3, null),
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null));
  }

  @Test
  public void constructorThreeParameters_completeExpectation_success() {
    ReportSpec testObject =
        new ReportSpec(SourceFixture.getTriggerSpecValueCountJSONTwoTriggerSpecs(), 3, null);

    // Assertion
    assertEquals(3, testObject.getMaxReports());
    assertEquals(2, testObject.getTriggerSpecs().length);
    assertEquals(
        new ArrayList<>(
            Arrays.asList(new UnsignedLong(1L), new UnsignedLong(2L), new UnsignedLong(3L))),
        testObject.getTriggerSpecs()[0].getTriggerData());
    assertEquals(
        new ArrayList<>(
            Arrays.asList(
                new UnsignedLong(4L),
                new UnsignedLong(5L),
                new UnsignedLong(6L),
                new UnsignedLong(7L))),
        testObject.getTriggerSpecs()[1].getTriggerData());
    assertEquals(
        new ArrayList<>(
            Arrays.asList(
                TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30))),
        testObject.getTriggerSpecs()[0].getEventReportWindowsEnd());
    assertEquals(
        new ArrayList<>(Collections.singletonList(TimeUnit.DAYS.toMillis(3))),
        testObject.getTriggerSpecs()[1].getEventReportWindowsEnd());
  }

  @Test
  public void equals_fourParamConstructor_returnsTrue() {
    JSONArray existingAttributes = new JSONArray();
    JSONObject triggerRecord1 = new JSONObject();
    triggerRecord1.put("trigger_id", "100");
    triggerRecord1.put("value", 2L);
    triggerRecord1.put("priority", 1L);
    triggerRecord1.put("trigger_time", BASE_TIME);
    triggerRecord1.put("trigger_data", new UnsignedLong(1L).toString());
    triggerRecord1.put("dedup_key", new UnsignedLong(34567L).toString());

    JSONObject triggerRecord2 = new JSONObject();
    triggerRecord2.put("trigger_id", "200");
    triggerRecord2.put("value", 3L);
    triggerRecord2.put("priority", 4L);
    triggerRecord2.put("trigger_time", BASE_TIME + 100);
    triggerRecord2.put("trigger_data", new UnsignedLong(1L).toString());
    triggerRecord2.put("dedup_key", new UnsignedLong(45678L).toString());
    existingAttributes.add(triggerRecord1);
    existingAttributes.add(triggerRecord2);

    Source source =
        SourceFixture.getValidSourceBuilder()
            .setEventAttributionStatus(existingAttributes.toString())
            .setAttributedTriggers(null)
            .build();
    source.buildAttributedTriggers();
    // Assertion
    ReportSpec reportSpec1 =
        new ReportSpec(
            SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(),
            3,
            source,
            PRIVACY_PARAMETERS_JSON);
    ReportSpec reportSpec2 =
        new ReportSpec(
            SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(),
            3,
            source,
            PRIVACY_PARAMETERS_JSON);
    assertEquals(reportSpec1, reportSpec2);
    assertNotNull(source.getAttributedTriggers());
    assertEquals(2, source.getAttributedTriggers().size());
  }

  @Test
  public void equals_fourParamConstructorFromRawJSON_returnsTrue() {
    String triggerSpecsString = SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline();
    // Assertion
    assertEquals(
        new ReportSpec(triggerSpecsString, 3, null, PRIVACY_PARAMETERS_JSON),
        new ReportSpec(triggerSpecsString, 3, null, PRIVACY_PARAMETERS_JSON));
  }

  @Test
  public void equals_twoParamConstructorFromRawJSON_returnsTrue() {
    String triggerSpecsString = SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline();
    // Assertion
    assertEquals(
        new ReportSpec(triggerSpecsString, 3, null), new ReportSpec(triggerSpecsString, 3, null));
  }

  @Test
  public void equals_twoParamConstructorFromRawJSONInvalidJSON_throws() {
    String triggerSpecsString =
        "[{\"trigger_data\": [1, 2, b],"
            + "\"event_report_windows\": { "
            + "\"start_time\": \"0\", "
            + String.format(
                "\"end_times\": [%s, %s, %s]}, ",
                TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(30))
            + "\"summary_window_operator\": \"count\", "
            + "\"summary_buckets\": [1, 2, 3, 4]}]";
    // Assertion
    assertNull(new ReportSpec(triggerSpecsString, 3, null).getTriggerSpecs());
  }

  @Test
  public void equals_fourParamConstructor_differentAttributions_returnsFalse() {
    JSONArray existingAttributes1 = new JSONArray();
    JSONArray existingAttributes2 = new JSONArray();

    JSONObject triggerRecord1 = new JSONObject();
    triggerRecord1.put("trigger_id", "100");
    triggerRecord1.put("value", 2L);
    triggerRecord1.put("priority", 1L);
    triggerRecord1.put("trigger_time", BASE_TIME);
    triggerRecord1.put("trigger_data", new UnsignedLong(1L).toString());
    triggerRecord1.put("dedup_key", new UnsignedLong(34567L).toString());

    JSONObject triggerRecord2 = new JSONObject();
    triggerRecord2.put("trigger_id", "200");
    triggerRecord2.put("value", 3L);
    triggerRecord2.put("priority", 4L);
    triggerRecord2.put("trigger_time", BASE_TIME + 100);
    triggerRecord2.put("trigger_data", new UnsignedLong(1L).toString());
    triggerRecord2.put("dedup_key", new UnsignedLong(45678L).toString());
    existingAttributes1.add(triggerRecord1);
    existingAttributes2.add(triggerRecord2);

    Source source1 =
        SourceFixture.getValidSourceBuilder()
            .setEventAttributionStatus(existingAttributes1.toJSONString())
            .setAttributedTriggers(null)
            .build();
    source1.buildAttributedTriggers();
    Source source2 =
        SourceFixture.getValidSourceBuilder()
            .setEventAttributionStatus(existingAttributes2.toJSONString())
            .setAttributedTriggers(null)
            .build();
    source2.buildAttributedTriggers();

    ReportSpec reportSpec1 =
        new ReportSpec(
            SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(),
            3,
            source1,
            PRIVACY_PARAMETERS_JSON);
    ReportSpec reportSpec2 =
        new ReportSpec(
            SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(),
            3,
            source2,
            PRIVACY_PARAMETERS_JSON);
    assertNotNull(source1.getAttributedTriggers());
    assertEquals(1, source1.getAttributedTriggers().size());
    // Assertion
    assertNotEquals(reportSpec1, reportSpec2);
  }

  @Test
  public void encodeTriggerSpecsToJson_equal() {
    ReportSpec testObject1 =
        new ReportSpec(SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(), 3, null);

    String encodedJSON = testObject1.encodeTriggerSpecsToJson();
    JSONArray tmp = new JSONArray();
    JSONParser parser = new JSONParser();
    try {
      tmp = (JSONArray) parser.parse(encodedJSON);
    } catch (ParseException e) {
      fail();
    }
    ReportSpec testObject2 = new ReportSpec(tmp.toJSONString(), 3, null);
    // Assertion
    assertEquals(testObject1, testObject2);
  }

  @Test
  public void getPrivacyParamsForComputation_equal() {
    ReportSpec testObject =
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null);
    // Assertion
    assertEquals(3, testObject.getPrivacyParamsForComputation()[0][0]);
    assertArrayEquals(new int[] {3, 3, 3}, testObject.getPrivacyParamsForComputation()[1]);
    assertArrayEquals(new int[] {4, 4, 4}, testObject.getPrivacyParamsForComputation()[2]);
  }

  @Test
  public void getPrivacyParamsForComputationV2_equal() {
    ReportSpec testObject = SourceFixture.getValidReportSpecCountBased();
    // Assertion
    assertEquals(3, testObject.getPrivacyParamsForComputation()[0][0]);
    assertArrayEquals(new int[] {2, 2}, testObject.getPrivacyParamsForComputation()[1]);
    assertArrayEquals(new int[] {2, 2}, testObject.getPrivacyParamsForComputation()[2]);
  }

  @Test
  public void getNumberState_equal() {
    ReportSpec testObject =
        new ReportSpec(SourceFixture.getTriggerSpecCountEncodedJSONValidBaseline(), 3, null);
    // Assertion
    assertEquals(
        220, testObject.getNumberState()); // Privacy parameter is {3, {3, 3, 3}, {4, 4, 4}}
  }

  @Test
  public void getTriggerDataValue_equal() {
    ReportSpec reportSpec =
        new ReportSpec(SourceFixture.getTriggerSpecValueCountJSONTwoTriggerSpecs(), 3, null);
    // Assertion
    assertEquals(new UnsignedLong(1L), reportSpec.getTriggerDataValue(0));
    assertEquals(new UnsignedLong(3L), reportSpec.getTriggerDataValue(2));
    assertEquals(new UnsignedLong(5L), reportSpec.getTriggerDataValue(4));
    assertEquals(new UnsignedLong(7L), reportSpec.getTriggerDataValue(6));
  }

  @Test
  public void insertAttributedTrigger_threeTriggerDataTypes_findsAccumulatedValues() {
    ReportSpec testReportSpec =
        new ReportSpec(
            SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(),
            2,
            SourceFixture.getValidSource());
    EventReport existingReport_1 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(3L)
            .setTriggerValue(5L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_2 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(3L)
            .setTriggerValue(6L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    EventReport existingReport_3 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(5L)
            .setTriggerValue(3L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_4 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerPriority(5L)
            .setTriggerValue(100L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    // Assertion
    testReportSpec.insertAttributedTrigger(existingReport_1);
    assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

    testReportSpec.insertAttributedTrigger(existingReport_2);
    assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
    assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

    testReportSpec.insertAttributedTrigger(existingReport_3);
    assertEquals(8L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
    assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

    testReportSpec.insertAttributedTrigger(existingReport_4);
    assertEquals(8L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
    assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));
    assertEquals(100L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(3L)));
  }

  @Test
  public void deleteFromAttributedValue_singleEntry_deletes() {
    ReportSpec testReportSpec =
        new ReportSpec(
            SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(),
            2,
            SourceFixture.getValidSource());
    EventReport existingReport_1 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(3L)
            .setTriggerValue(5L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_2 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(3L)
            .setTriggerValue(6L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    EventReport existingReport_3 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerPriority(5L)
            .setTriggerValue(3L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_3_dup =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(3L))
            .setTriggerPriority(5L)
            .setTriggerValue(3L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .setId("12345")
            .build();

    // Assertion
    testReportSpec.insertAttributedTrigger(existingReport_1);
    assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

    testReportSpec.deleteFromAttributedValue(existingReport_1);
    assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

    testReportSpec.insertAttributedTrigger(existingReport_2);
    testReportSpec.deleteFromAttributedValue(existingReport_2);
    assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));

    testReportSpec.insertAttributedTrigger(existingReport_3);
    testReportSpec.insertAttributedTrigger(existingReport_3_dup);
    assertEquals(0L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
  }

  @Test
  public void deleteFromAttributedValue_multipleEntries_deletesForTriggerType() {
    ReportSpec testReportSpec =
        new ReportSpec(
            SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(),
            2,
            SourceFixture.getValidSource());
    EventReport existingReport_1 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(3L)
            .setTriggerValue(5L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_2 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(4L)
            .setTriggerValue(6L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    EventReport existingReport_3 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(5L)
            .setTriggerValue(11L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    // Assertion
    testReportSpec.insertAttributedTrigger(existingReport_1);
    assertEquals(5L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

    testReportSpec.insertAttributedTrigger(existingReport_2);
    testReportSpec.insertAttributedTrigger(existingReport_3);
    assertEquals(16L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));

    assertTrue(testReportSpec.deleteFromAttributedValue(existingReport_1));
    assertEquals(11L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(1L)));
    assertEquals(6L, testReportSpec.findCurrentAttributedValue(new UnsignedLong(2L)));
  }

  @Test
  public void deleteFromAttributedValue_noTriggerRecord_returnsFalse() {
    ReportSpec testReportSpec =
        new ReportSpec(
            SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(),
            2,
            SourceFixture.getValidSource());
    EventReport existingReport_1 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(3L)
            .setTriggerValue(5L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();
    EventReport existingReport_2 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(2L))
            .setTriggerPriority(4L)
            .setTriggerValue(6L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    EventReport existingReport_3 =
        EventReportFixture.getBaseEventReportBuild()
            .setTriggerData(new UnsignedLong(1L))
            .setTriggerPriority(5L)
            .setTriggerValue(11L)
            .setReportTime(BASE_TIME + TimeUnit.DAYS.toMillis(2))
            .build();

    // Assertion
    testReportSpec.insertAttributedTrigger(existingReport_1);
    assertTrue(testReportSpec.deleteFromAttributedValue(existingReport_1));
    assertFalse(testReportSpec.deleteFromAttributedValue(existingReport_2));
    assertFalse(testReportSpec.deleteFromAttributedValue(existingReport_3));
  }

  @Test
  public void containsTriggerData_variousTriggerDataTypes_correctlyDetermines() {
    ReportSpec testReportSpec =
        new ReportSpec(SourceFixture.getTriggerSpecValueCountJSONTwoTriggerSpecs(), 3, null);
    // Assertion
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(1L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(2L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(3L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(4L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(5L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(6L)));
    assertTrue(testReportSpec.containsTriggerData(new UnsignedLong(7L)));
    assertFalse(testReportSpec.containsTriggerData(new UnsignedLong(0L)));
    assertFalse(testReportSpec.containsTriggerData(new UnsignedLong(8L)));
  }
}
