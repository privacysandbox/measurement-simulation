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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

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
