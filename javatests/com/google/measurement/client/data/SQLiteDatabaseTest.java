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

package com.google.measurement.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.sql.SQLException;
import org.junit.Before;
import org.junit.Test;

public final class SQLiteDatabaseTest {

  SQLiteDatabase mSqLiteDatabase;

  @Before
  public void before() throws SQLException {
    mSqLiteDatabase = new SQLiteDatabase(new GlobalCursorCache());
    String sql =
        "CREATE TABLE IF NOT EXISTS tempTable ("
            + "	id INTEGER PRIMARY KEY,"
            + "	name text NOT NULL,"
            + " age int"
            + ");";
    mSqLiteDatabase.createTables(sql);
  }

  @Test
  public void testInsert_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    int rows = mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);
    assertEquals(1, rows);
  }

  @Test
  public void testUpdateNoWhereClause_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    ContentValues updateValues = new ContentValues();
    updateValues.put("name", "updateTemp");

    int rows = mSqLiteDatabase.update("tempTable", updateValues, null, null);
    assertEquals(2, rows);
  }

  @Test
  public void testUpdateNoWhereClause_fail() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    ContentValues updateValues = new ContentValues();
    updateValues.put("wrongCol", "temp");

    assertThrows(
        SQLException.class,
        () -> {
          int rows = mSqLiteDatabase.update("tempTable", updateValues, null, null);
          assertEquals(0, rows);
        });
  }

  @Test
  public void testUpdateWhereClause_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    ContentValues updateValues = new ContentValues();
    updateValues.put("name", "whereTemp");

    String whereClause = "name is ?";
    String[] whereArgs = new String[1];
    whereArgs[0] = "temp2";

    int rows = mSqLiteDatabase.update("tempTable", updateValues, whereClause, whereArgs);
    assertEquals(1, rows);
  }

  @Test
  public void testQuery_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    String[] columns = {"name", "age"};
    Cursor cursor = mSqLiteDatabase.query("tempTable", columns, null, null, null, null, null, null);

    int count = 0;
    String[] names = {"temp", "temp2"};
    int[] ages = {10, 20};
    while (cursor.moveToNext()) {
      assertEquals(names[count], cursor.getString(1));
      assertEquals(ages[count], cursor.getInt(2));
      ++count;
    }
    assertEquals(2, count);
  }

  @Test
  public void testQueryLimit_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    String[] columns = {"name", "age"};
    Cursor cursor = mSqLiteDatabase.query("tempTable", columns, null, null, null, null, null, "1");

    int count = 0;
    String[] names = {"temp"};
    int[] ages = {10};
    while (cursor.moveToNext()) {
      assertEquals(names[count], cursor.getString(1));
      assertEquals(ages[count], cursor.getInt(2));
      ++count;
    }
    assertEquals(1, count);
  }

  @Test
  public void testQuerySelection_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    String[] columns = {"name", "age"};
    String[] selectionArgs = {"10"};
    Cursor cursor =
        mSqLiteDatabase.query(
            "tempTable", columns, "age = ?", selectionArgs, null, null, null, null);

    int count = 0;
    String[] names = {"temp"};
    int[] ages = {10};
    while (cursor.moveToNext()) {
      assertEquals(names[count], cursor.getString(1));
      assertEquals(ages[count], cursor.getInt(2));
      ++count;
    }
    assertEquals(1, count);
  }

  @Test
  public void testQueryDelete_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    int rows = mSqLiteDatabase.delete("tempTable", null, null);
    assertEquals(2, rows);
  }

  @Test
  public void testQueryDeleteWithWhere_success() throws SQLException {
    ContentValues values = new ContentValues();
    values.put("name", "temp");
    values.put("age", 10);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values);

    ContentValues values2 = new ContentValues();
    values2.put("name", "temp2");
    values2.put("age", 20);
    mSqLiteDatabase.insert("tempTable", /* nullColumnHack= */ null, values2);

    String whereClause = "age < ?";
    String[] whereArgs = {"15"};
    int rows = mSqLiteDatabase.delete("tempTable", whereClause, whereArgs);
    assertEquals(1, rows);

    // verify that there is 1 row remaining for temp2
    String[] columns = {"name", "age"};
    Cursor cursor = mSqLiteDatabase.query("tempTable", columns, null, null, null, null, null, null);

    int count = 0;
    String[] names = {"temp2"};
    int[] ages = {20};
    while (cursor.moveToNext()) {
      assertEquals(names[count], cursor.getString(1));
      assertEquals(ages[count], cursor.getInt(2));
      ++count;
    }
    assertEquals(1, count);
  }
}
