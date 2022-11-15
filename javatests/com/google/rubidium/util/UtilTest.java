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

package com.google.rubidium.util;

import java.time.LocalDate;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UtilTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void getPathsInDateRangeTest() {
    LocalDate startDate = Util.parseStringDate("2022-01-15");
    LocalDate endDate = Util.parseStringDate("2022-01-15");
    String inputDir = "testdata";
    String fileName = "trigger.json";

    List<String> paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    Assert.assertEquals(paths.size(), 1);
    Assert.assertEquals(paths.get(0), "testdata/2022/01/15/trigger.json");

    endDate = Util.parseStringDate("2022-01-16");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    Assert.assertEquals(paths.size(), 2);
    Assert.assertEquals(paths.get(0), "testdata/2022/01/15/trigger.json");
    Assert.assertEquals(paths.get(1), "testdata/2022/01/16/trigger.json");

    endDate = Util.parseStringDate("2022-01-25");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    Assert.assertEquals(paths.size(), 4);
    Assert.assertEquals(paths.get(0), "testdata/2022/01/15/trigger.json");
    Assert.assertEquals(paths.get(1), "testdata/2022/01/16/trigger.json");
    Assert.assertEquals(paths.get(2), "testdata/2022/01/19/trigger.json");
    Assert.assertEquals(paths.get(3), "testdata/2022/01/24/trigger.json");

    startDate = Util.parseStringDate("2022-01-25");
    endDate = Util.parseStringDate("2022-02-04");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    Assert.assertEquals(paths.size(), 1);
    Assert.assertEquals(paths.get(0), "testdata/2022/02/04/trigger.json");

    startDate = Util.parseStringDate("2022-01-13");
    endDate = Util.parseStringDate("2022-01-14");
    paths = Util.getPathsInDateRange(startDate, endDate, inputDir, fileName);
    Assert.assertEquals(paths.size(), 0);
  }

  @Test
  public void validateFilenamesTest() {
    Util.validateFilenames("A.json", "B.json");
  }
}
