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

package com.google.measurement.client.data;

public class DatabaseUtils {

  public static String sqlEscapeString(String value) {
    StringBuilder escaper = new StringBuilder();

    DatabaseUtils.appendEscapedSQLString(escaper, value);

    return escaper.toString();
  }

  /**
   * Appends an SQL string to the given StringBuilder, including the opening and closing single
   * quotes. Any single quotes internal to sqlString will be escaped.
   *
   * <p>This method is deprecated because we want to encourage everyone to use the "?" binding form.
   * However, when implementing a ContentProvider, one may want to add WHERE clauses that were not
   * provided by the caller. Since "?" is a positional form, using it in this case could break the
   * caller because the indexes would be shifted to accomodate the ContentProvider's internal
   * bindings. In that case, it may be necessary to construct a WHERE clause manually. This method
   * is useful for those cases.
   *
   * @param sb the StringBuilder that the SQL string will be appended to
   * @param sqlString the raw string to be appended, which may contain single quotes
   */
  public static void appendEscapedSQLString(StringBuilder sb, String sqlString) {
    sb.append('\'');
    int length = sqlString.length();
    for (int i = 0; i < length; i++) {
      char c = sqlString.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i == length - 1) {
          continue;
        }
        if (Character.isLowSurrogate(sqlString.charAt(i + 1))) {
          // add them both
          sb.append(c);
          sb.append(sqlString.charAt(i + 1));
          continue;
        } else {
          // this is a lone surrogate, skip it
          continue;
        }
      }
      if (Character.isLowSurrogate(c)) {
        continue;
      }
      if (c == '\'') {
        sb.append('\'');
      }
      sb.append(c);
    }
    sb.append('\'');
  }

  /**
   * Utility method to run the query on the db and return the value in the first column of the first
   * row.
   */
  public static long longForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
    return db.execQueryForLong(query, selectionArgs);
  }

  /**
   * Query the table for the number of rows in the table.
   *
   * @param db the database the table is in
   * @param table the name of the table to query
   * @param selection A filter declaring which rows to return, formatted as an SQL WHERE clause
   *     (excluding the WHERE itself). Passing null will count all rows for the given table
   * @param selectionArgs You may include ?s in selection, which will be replaced by the values from
   *     selectionArgs, in order that they appear in the selection. The values will be bound as
   *     Strings.
   * @return the number of rows in the table filtered by the selection
   */
  public static long queryNumEntries(
      SQLiteDatabase db, String table, String selection, String[] selectionArgs) {
    String s = (!(selection == null || selection.isEmpty())) ? " where " + selection : "";
    return longForQuery(db, "select count(*) from " + table + s, selectionArgs);
  }

  /**
   * Query the table for the number of rows in the table.
   *
   * @param db the database the table is in
   * @param table the name of the table to query
   * @param selection A filter declaring which rows to return, formatted as an SQL WHERE clause
   *     (excluding the WHERE itself). Passing null will count all rows for the given table
   * @return the number of rows in the table filtered by the selection
   */
  public static long queryNumEntries(SQLiteDatabase db, String table, String selection) {
    return queryNumEntries(db, table, selection, null);
  }

  /**
   * Query the table for the number of rows in the table.
   *
   * @param db the database the table is in
   * @param table the name of the table to query
   * @return the number of rows in the table
   */
  public static long queryNumEntries(SQLiteDatabase db, String table) {
    return queryNumEntries(db, table, null, null);
  }

  /**
   * Prints the contents of a Cursor's current row to a StringBuilder.
   *
   * @param cursor the cursor to print
   * @param sb the StringBuilder to print to
   */
  public static void dumpCurrentRow(Cursor cursor, StringBuilder sb) {
    String[] cols = cursor.getColumnNames();
    sb.append(cursor.getPosition()).append(" {\n");
    int length = cols.length;
    for (int i = 0; i < length; i++) {
      String value;
      try {
        value = cursor.getString(i);
      } catch (SQLiteException e) {
        // assume that if the getString threw this exception then the column is not
        // representable by a string, e.g. it is a BLOB.
        value = "<unprintable>";
      }
      sb.append("   ").append(cols[i]).append('=').append(value).append('\n');
    }
    sb.append("}\n");
  }

  /**
   * Dump the contents of a Cursor's current row to a String.
   *
   * @param cursor the cursor to print
   * @return a String that contains the dumped cursor row
   */
  public static String dumpCurrentRowToString(Cursor cursor) {
    StringBuilder sb = new StringBuilder();
    dumpCurrentRow(cursor, sb);
    return sb.toString();
  }
}
