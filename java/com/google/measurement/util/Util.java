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

import static com.google.common.io.Files.getFileExtension;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.simple.JSONObject;

public class Util {

  public static int parseJsonInt(JSONObject jsonObject, String key) {
    if (!jsonObject.containsKey(key)) {
      throw new IllegalArgumentException("Key: " + key + ", not found in jsonObject");
    }

    Object value = jsonObject.get(key);
    int integer;
    if (value instanceof String) {
      integer = Integer.parseInt((String) value);
    } else if (value instanceof Integer) {
      integer = (int) value;
    } else if (value instanceof Long) {
      integer = Math.toIntExact((long) value);
    } else {
      throw new IllegalArgumentException(
          "Value of: " + value + ", for key: " + key + ", is not a String, int, or long.");
    }

    return integer;
  }

  public static long parseJsonLong(JSONObject jsonObject, String key) {
    if (!jsonObject.containsKey(key)) {
      throw new IllegalArgumentException("Key: " + key + ", not found in jsonObject");
    }

    Object value = jsonObject.get(key);
    long l;
    if (value instanceof String) {
      l = Long.parseLong((String) value);
    } else if (value instanceof Integer) {
      l = (long) (int) value;
    } else if (value instanceof Long) {
      l = (long) value;
    } else {
      throw new IllegalArgumentException(
          "Value of: " + value + ", for key: " + key + ", is not a String, int, or long.");
    }

    return l;
  }

  public static UnsignedLong parseJsonUnsignedLong(JSONObject jsonObject, String key) {
    if (!jsonObject.containsKey(key)) {
      throw new IllegalArgumentException("Key: " + key + ", not found in jsonObject");
    }

    Object value = jsonObject.get(key);
    UnsignedLong unsignedLong;

    if (value instanceof String) {
      unsignedLong = new UnsignedLong((String) value);
    } else if (value instanceof Integer) {
      unsignedLong = new UnsignedLong(Long.valueOf((int) value));
    } else if (value instanceof Long) {
      unsignedLong = new UnsignedLong((long) value);
    } else {
      throw new IllegalArgumentException(
          "Value of: " + value + ", for key: " + key + ", is not a String, int, or long.");
    }

    return unsignedLong;
  }

  /**
   * Given a string that is to be used within a Unix filepath, sanitize that string so that illegal
   * characters don't prevent creation of the filepath.
   *
   * @param filename the string to be sanitized.
   * @return filename sanitized.
   */
  public static String sanitizeFilename(String filename) {
    Set<Character> illegalChars = new HashSet<>(Arrays.asList('.', '/', '*', ':', '~'));

    StringBuilder output = new StringBuilder();
    for (int i = 0; i < filename.length(); i++) {
      Character c = filename.charAt(i);
      if (illegalChars.contains(c)) {
        output.append('-');
      } else {
        output.append(c);
      }
    }

    return output.toString();
  }

  public static long roundDownToDay(long timestamp) {
    return roundDownToDay(timestamp, 0);
  }

  public static long roundDownToDay(long timestamp, long offset) {
    return Math.floorDiv(timestamp + offset, TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
  }

  public static LocalDate parseStringDate(String inDate) {
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("y-M-d");
    return LocalDate.parse(inDate, dateFormat);
  }

  public static String getDateInUnixMillis(long inputTime) {
    // Create instant of time from input unix time in milliseconds
    Instant instant = Instant.ofEpochMilli(inputTime);
    LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    // Create date in the format "yyyy-MM-dd" from time in UTC zone.
    return DateTimeFormatter.ofPattern("y-MM-dd").format(localDateTime);
  }

  public static String getFileType(String fileName) {
    return getFileExtension(fileName);
  }

  /**
   * Get all file paths in subdirectories of inputDir given that it is structured like so,
   * inputDir/yyyy/mm/dd/fileName, between startDate and endDate, inclusive.
   *
   * @param startDate LocalDate, the first date to search from
   * @param endDate LocalDate, the last date to search to
   * @param inputDir String, base directory to search from
   * @param fileName String, filename at bottom of directory to search for
   * @return List of Strings that are filepaths
   */
  public static List<String> getPathsInDateRange(
      LocalDate startDate, LocalDate endDate, String inputDir, String fileName) {
    List<String> paths = new ArrayList<>();
    for (LocalDate curDate = startDate; !curDate.isAfter(endDate); curDate = curDate.plusDays(1)) {
      String curDateStr = curDate.format(DateTimeFormatter.ofPattern("y/MM/dd"));
      Path filePath = Paths.get(inputDir, curDateStr, fileName);

      if (Files.exists(filePath)) {
        paths.add(filePath.toString());
      }
    }

    return paths;
  }

  public static String generateRandomString() {
    UUID uuid = UUID.randomUUID();
    return uuid.toString().replaceAll("_", "");
  }

  public static void loadProperties(Properties properties, String fileName) throws IOException {
    FileInputStream in = new FileInputStream(fileName);
    properties.load(in);
    in.close();
  }

  public static void validateFilenames(String attributionSourceFileName, String triggerFilename) {
    if (!getFileType(attributionSourceFileName).equals(getFileType(triggerFilename))) {
      throw new IllegalArgumentException(
          "attributionSourceFileName and triggerFileName must have the same file extension");
    }
  }
}
