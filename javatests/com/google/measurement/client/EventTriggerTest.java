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

package com.google.measurement.client;

import static org.junit.Assert.*;

import com.google.measurement.client.util.UnsignedLong;
import java.util.List;
import java.util.Set;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

public class EventTriggerTest {
  private static JSONObject sFilterMap1;
  private static JSONObject sFilterMap2;
  private static JSONObject sNotFilterMap1;
  private static JSONObject sNotFilterMap2;

  static {
    try {
      JSONParser parser = new JSONParser();
      sFilterMap1 =
          (JSONObject)
              parser.parse(
                  "{\n"
                      + "    \"source_type\": [\"navigation\"],\n"
                      + "    \"key_1\": [\"value_1\"] \n"
                      + "   }\n");
      sFilterMap2 =
          (JSONObject)
              parser.parse(
                  "{\n"
                      + "    \"source_type\": [\"EVENT\"],\n"
                      + "    \"key_1\": [\"value_1\"] \n"
                      + "   }\n");
      sNotFilterMap1 =
          (JSONObject)
              parser.parse(
                  "{\n"
                      + "    \"not_source_type\": [\"EVENT\"],\n"
                      + "    \"not_key_1\": [\"value_1\"] \n"
                      + "   }\n");
      sNotFilterMap2 =
          (JSONObject)
              parser.parse(
                  "{\n"
                      + "    \"not_source_type\": [\"navigation\"],\n"
                      + "    \"not_key_1\": [\"value_1\"] \n"
                      + "   }\n");
    } catch (ParseException e) {
      fail();
    }
  }

  @Test
  public void testDefaults() throws Exception {
    com.google.measurement.client.EventTrigger eventTrigger =
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L)).build();
    assertEquals(0L, eventTrigger.getTriggerPriority());
    assertNotNull(eventTrigger.getTriggerData());
    assertNull(eventTrigger.getDedupKey());
    assertFalse(eventTrigger.getFilterSet().isPresent());
    assertFalse(eventTrigger.getNotFilterSet().isPresent());
  }

  @Test
  public void test_equals_pass() {
    com.google.measurement.client.EventTrigger eventTrigger1 =
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(101L))
            .setTriggerPriority(1L)
            .setTriggerValue(100L)
            .setDedupKey(new UnsignedLong(1001L))
            .setFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sFilterMap1)
                        .build()))
            .setNotFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sNotFilterMap1)
                        .build()))
            .build();
    com.google.measurement.client.EventTrigger eventTrigger2 =
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(101L))
            .setTriggerPriority(1L)
            .setTriggerValue(100L)
            .setDedupKey(new UnsignedLong(1001L))
            .setFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sFilterMap1)
                        .build()))
            .setNotFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sNotFilterMap1)
                        .build()))
            .build();
    assertEquals(eventTrigger1, eventTrigger2);
  }

  @Test
  public void test_equals_fail() throws Exception {
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setTriggerPriority(1L)
            .build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setTriggerPriority(2L)
            .build());
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(1L)).build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(2L)).build());
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setDedupKey(new UnsignedLong(1L))
            .build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setDedupKey(new UnsignedLong(2L))
            .build());
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setTriggerValue(1L)
            .build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setTriggerValue(2L)
            .build());
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sFilterMap1)
                        .build()))
            .build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sFilterMap2)
                        .build()))
            .build());
    assertNotEquals(
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setNotFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sNotFilterMap1)
                        .build()))
            .build(),
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(0L))
            .setNotFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sNotFilterMap2)
                        .build()))
            .build());
  }

  @Test
  public void testHashCode_equals() throws Exception {
    com.google.measurement.client.EventTrigger eventTrigger1 = createExample();
    com.google.measurement.client.EventTrigger eventTrigger2 = createExample();
    Set<com.google.measurement.client.EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
    Set<com.google.measurement.client.EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
    assertEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
    assertEquals(eventTrigger1, eventTrigger2);
    assertEquals(eventTriggerSet1, eventTriggerSet2);
  }

  @Test
  public void testHashCode_notEquals() throws Exception {
    com.google.measurement.client.EventTrigger eventTrigger1 = createExample();
    com.google.measurement.client.EventTrigger eventTrigger2 =
        new com.google.measurement.client.EventTrigger.Builder(new UnsignedLong(101L))
            .setTriggerPriority(2L)
            .setDedupKey(new UnsignedLong(1001L))
            .setFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sFilterMap1)
                        .build()))
            .setNotFilterSet(
                List.of(
                    new com.google.measurement.client.FilterMap.Builder()
                        .buildFilterData(sNotFilterMap1)
                        .build()))
            .build();
    Set<com.google.measurement.client.EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
    Set<com.google.measurement.client.EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
    assertNotEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
    assertNotEquals(eventTrigger1, eventTrigger2);
    assertNotEquals(eventTriggerSet1, eventTriggerSet2);
  }

  @Test
  public void eventTriggerBuilder_nullTriggerData_fail() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new com.google.measurement.client.EventTrigger.Builder(null)
                .setTriggerPriority(124L)
                .setDedupKey(new UnsignedLong(101L))
                .build());
  }

  private com.google.measurement.client.EventTrigger createExample() {
    return new EventTrigger.Builder(new UnsignedLong(101L))
        .setTriggerPriority(1L)
        .setDedupKey(new UnsignedLong(1001L))
        .setFilterSet(
            List.of(
                new com.google.measurement.client.FilterMap.Builder()
                    .buildFilterData(sFilterMap1)
                    .build()))
        .setNotFilterSet(List.of(new FilterMap.Builder().buildFilterData(sNotFilterMap1).build()))
        .build();
  }
}
