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

package com.google.measurement.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.json.simple.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void parseJsonLongTest() {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("intNumber", 1);
    long expected = 1L;
    long actual = Util.parseJsonLong(jsonObject, "intNumber");
    assertEquals(expected, actual);

    jsonObject.put("longNumber", 2L);
    expected = 2L;
    actual = Util.parseJsonLong(jsonObject, "longNumber");
    assertEquals(expected, actual);

    jsonObject.put("longerNumber", 2147483648L);
    expected = 2147483648L;
    actual = Util.parseJsonLong(jsonObject, "longerNumber");
    assertEquals(expected, actual);

    jsonObject.put("stringNumber", "2");
    expected = 2L;
    actual = Util.parseJsonLong(jsonObject, "stringNumber");
    assertEquals(expected, actual);

    jsonObject.put("stringLargeNumber", "2147483648");
    expected = 2147483648L;
    actual = Util.parseJsonLong(jsonObject, "stringLargeNumber");
    assertEquals(expected, actual);
  }

  @Test
  public void parseJsonLongTest_illegalArguments() {
    JSONObject jsonObject = new JSONObject();
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonLong(jsonObject, "not_present"));

    assertEquals("Key: not_present, not found in jsonObject", e.getMessage());

    jsonObject.put("present", null);
    e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonLong(jsonObject, "present"));

    assertEquals(
        "Value of: null, for key: present, is not a String, int, or long.", e.getMessage());

    double d = 0.1;
    jsonObject.put("double", d);
    e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonLong(jsonObject, "double"));

    assertEquals("Value of: 0.1, for key: double, is not a String, int, or long.", e.getMessage());
  }

  @Test
  public void parseJsonIntTest() {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("intNumber", 1);
    int expected = 1;
    int actual = Util.parseJsonInt(jsonObject, "intNumber");
    assertEquals(expected, actual);

    jsonObject.put("stringNumber", "2");
    expected = 2;
    actual = Util.parseJsonInt(jsonObject, "stringNumber");
    assertEquals(expected, actual);
  }

  @Test
  public void parseJsonIntTest_illegalArguments() {
    JSONObject jsonObject = new JSONObject();
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonInt(jsonObject, "not_present"));

    assertEquals("Key: not_present, not found in jsonObject", e.getMessage());

    jsonObject.put("present", null);
    e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonInt(jsonObject, "present"));

    assertEquals(
        "Value of: null, for key: present, is not a String, int, or long.", e.getMessage());

    double d = 0.1;
    jsonObject.put("double", d);
    e = assertThrows(IllegalArgumentException.class, () -> Util.parseJsonInt(jsonObject, "double"));

    assertEquals("Value of: 0.1, for key: double, is not a String, int, or long.", e.getMessage());
  }

  @Test
  public void parseJsonUnsignedLongTest() {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("intNumber", 1);
    UnsignedLong expected = new UnsignedLong(1L);
    UnsignedLong actual = Util.parseJsonUnsignedLong(jsonObject, "intNumber");
    assertEquals(expected, actual);

    jsonObject.put("intNumberZero", 0);
    expected = new UnsignedLong(0L);
    actual = Util.parseJsonUnsignedLong(jsonObject, "intNumberZero");
    assertEquals(expected, actual);

    jsonObject.put("longNumber", 4294967296L);
    expected = new UnsignedLong(4294967296L);
    actual = Util.parseJsonUnsignedLong(jsonObject, "longNumber");
    assertEquals(expected, actual);

    jsonObject.put("negNumber", -1);
    expected = new UnsignedLong("18446744073709551615");
    actual = Util.parseJsonUnsignedLong(jsonObject, "negNumber");
    assertEquals(expected, actual);

    jsonObject.put("stringNumber", "2");
    expected = new UnsignedLong(2L);
    actual = Util.parseJsonUnsignedLong(jsonObject, "stringNumber");
    assertEquals(expected, actual);

    // 18446744073709551615 = (2^64) - 1
    jsonObject.put("stringLargeNumber", "18446744073709551615");
    expected = new UnsignedLong("18446744073709551615");
    actual = Util.parseJsonUnsignedLong(jsonObject, "stringLargeNumber");
    assertEquals(expected, actual);
  }

  @Test
  public void parseJsonUnsignedLongTest_illegalArguments() {
    JSONObject jsonObject = new JSONObject();
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Util.parseJsonUnsignedLong(jsonObject, "not_present"));

    assertEquals("Key: not_present, not found in jsonObject", e.getMessage());

    jsonObject.put("present", null);
    e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Util.parseJsonUnsignedLong(jsonObject, "present"));

    assertEquals(
        "Value of: null, for key: present, is not a String, int, or long.", e.getMessage());

    double d = 0.1;
    jsonObject.put("double", d);
    e =
        assertThrows(
            IllegalArgumentException.class, () -> Util.parseJsonUnsignedLong(jsonObject, "double"));

    assertEquals("Value of: 0.1, for key: double, is not a String, int, or long.", e.getMessage());
  }

  @Test
  public void sanitizeFilenameTest() {
    String illegalFilename = "/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890.*:~/";
    String expected = "-abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890-----";
    String actual = Util.sanitizeFilename(illegalFilename);

    assertEquals(expected, actual);
  }

  @Test
  public void roundDownToDayTest() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    LocalDateTime dateTime =
        LocalDateTime.parse("2022-01-15 00:00:01", formatter)
            .atOffset(ZoneOffset.UTC)
            .toLocalDateTime();

    // 2022-01-15T00:00:01Z -> 1642204801000
    long timestamp = dateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
    long timestampRounded = Util.roundDownToDay(timestamp);

    assertEquals(1642204800000L, timestampRounded);
  }

  @Test
  public void getPathsInDateRangeTest() {
    LocalDate startDate = Util.parseStringDate("2022-01-15");
    LocalDate endDate = Util.parseStringDate("2022-01-15");
    String inputDir = "testdata";
    String fileName = "trigger.json";

    List<String> paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    assertEquals(1, paths.size());
    assertEquals("testdata/2022/01/15/trigger.json", paths.get(0));

    endDate = Util.parseStringDate("2022-01-16");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    assertEquals(2, paths.size());
    assertEquals("testdata/2022/01/15/trigger.json", paths.get(0));
    assertEquals("testdata/2022/01/16/trigger.json", paths.get(1));

    endDate = Util.parseStringDate("2022-01-25");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    assertEquals(4, paths.size());
    assertEquals("testdata/2022/01/15/trigger.json", paths.get(0));
    assertEquals("testdata/2022/01/16/trigger.json", paths.get(1));
    assertEquals("testdata/2022/01/19/trigger.json", paths.get(2));
    assertEquals("testdata/2022/01/24/trigger.json", paths.get(3));

    startDate = Util.parseStringDate("2022-01-25");
    endDate = Util.parseStringDate("2022-02-04");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    assertEquals(1, paths.size());
    assertEquals("testdata/2022/02/04/trigger.json", paths.get(0));

    startDate = Util.parseStringDate("2022-01-13");
    endDate = Util.parseStringDate("2022-01-14");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    assertEquals(0, paths.size());
  }

  @Test
  public void validateFilenamesTest() {
    Util.validateFilenames("A.json", "B.json");
  }
}
